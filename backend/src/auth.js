import {RECOVERY_QUESTIONS,recoveryMaterial} from './recovery.js';
import {hash,equal} from './security.js';
export const ADMIN_EMAILS=new Set(['vertcorpgroup@gmail.com','mahlangu843@gmail.com']);
const encoder=new TextEncoder();
const fail=(status,message)=>{throw Object.assign(new Error(message),{status})};
export const normalizeEmail=value=>String(value||'').trim().toLowerCase();
export function validPassword(password){return typeof password==='string'&&password.length>=12&&password.length<=128}
export function originCheck(request){if(request.headers.get('Sec-Fetch-Site')==='cross-site')fail(403,'Cross-origin request denied');const origin=request.headers.get('Origin');if(origin&&origin!==new URL(request.url).origin)fail(403,'Cross-origin request denied')}
async function derive(password,salt,pepper){
 if(!pepper)fail(503,'Account authentication is not configured');
 const hmac=await crypto.subtle.importKey('raw',encoder.encode(pepper),{name:'HMAC',hash:'SHA-256'},false,['sign']);
 const material=await crypto.subtle.sign('HMAC',hmac,encoder.encode(password));
 const key=await crypto.subtle.importKey('raw',material,'PBKDF2',false,['deriveBits']);
 const bytes=await crypto.subtle.deriveBits({name:'PBKDF2',salt:encoder.encode(salt),iterations:100000,hash:'SHA-512'},key,512);
 return Array.from(new Uint8Array(bytes),b=>b.toString(16).padStart(2,'0')).join('');
}
export async function passwordHash(password,pepper){const salt=crypto.randomUUID();return 'pbkdf2-sha512-hmac-v1$'+salt+'$'+await derive(password,salt,pepper)}
export async function verifyPassword(password,stored,pepper){const [version,salt,expected]=String(stored).split('$');if(version!=='pbkdf2-sha512-hmac-v1'||!salt||!expected)return false;return equal(await derive(password,salt,pepper),expected)}
export function sessionToken(request){const header=request.headers.get('Authorization');if(header?.startsWith('Bearer '))return header.slice(7);return request.headers.get('Cookie')?.split(';').map(s=>s.trim()).find(s=>s.startsWith('__Host-vert_session='))?.slice('__Host-vert_session='.length)||null}
export async function currentUser(request,env){const secret=sessionToken(request);if(!secret)return null;return await env.DB.prepare('SELECT u.id,u.email,u.role,u.payment_exempt,a.active,EXISTS(SELECT 1 FROM account_recovery r WHERE r.user_id=u.id) AS recovery_configured FROM sessions s JOIN users u ON u.id=s.user_id JOIN activations a ON a.id=u.id WHERE s.token_hash=? AND s.expires_at>?').bind(hash(secret),Date.now()).first()}
export const publicUser=u=>({id:u.id,email:u.email,role:u.role,active:!!u.active||!!u.payment_exempt,paymentExempt:!!u.payment_exempt,recoveryConfigured:!!u.recovery_configured});
async function issueSession(user,env,tv){const secret=crypto.randomUUID()+crypto.randomUUID();const seconds=tv?180*86400:user.role==='admin'?12*3600:30*86400;await env.DB.prepare('INSERT INTO sessions(token_hash,user_id,expires_at) VALUES(?,?,?)').bind(hash(secret),user.id,Date.now()+seconds*1000).run();return {secret,seconds}}
async function limitedBody(request){const reader=request.body?.getReader();if(!reader)fail(400,'Request body required');let size=0,chunks=[];for(;;){const {value,done}=await reader.read();if(done)break;size+=value.length;if(size>8192){await reader.cancel();fail(413,'Request too large')}chunks.push(value)}const merged=new Uint8Array(size);let offset=0;for(const c of chunks){merged.set(c,offset);offset+=c.length}try{const value=JSON.parse(new TextDecoder().decode(merged));if(!value||typeof value!=='object'||Array.isArray(value))fail(400,'JSON object required');return value}catch{fail(400,'Invalid JSON')}}
async function rateLimit(request,env,email){const now=Date.now(),window=10*60000;for(const [key,limit] of [[hash('ip:'+request.headers.get('CF-Connecting-IP')),40],[hash('email:'+email),10]]){const row=await env.DB.prepare('INSERT INTO auth_rate_limits(key,window_start,attempts) VALUES(?,?,1) ON CONFLICT(key) DO UPDATE SET attempts=CASE WHEN window_start<=? THEN 1 ELSE attempts+1 END,window_start=CASE WHEN window_start<=? THEN ? ELSE window_start END RETURNING attempts').bind(key,now,now-window,now-window,now).first();if(row.attempts>limit)fail(429,'Too many attempts. Please try again in 10 minutes.')}}
async function recoveryLimit(request,env,email){
 const now=Date.now(),window=15*60000;
 for(const [key,limit] of [[hash('recovery-ip:'+request.headers.get('CF-Connecting-IP')),20],[hash('recovery-email:'+email),5]]){
  const row=await env.DB.prepare('INSERT INTO auth_rate_limits(key,window_start,attempts) VALUES(?,?,1) ON CONFLICT(key) DO UPDATE SET attempts=CASE WHEN window_start<=? THEN 1 ELSE attempts+1 END,window_start=CASE WHEN window_start<=? THEN ? ELSE window_start END RETURNING attempts').bind(key,now,now-window,now-window,now).first();
  if(row.attempts>limit)fail(429,'Too many recovery attempts. Please try again in 15 minutes.');
 }
}
export async function authRoute(request,env,path){
 const json=(body,status=200,headers={})=>Response.json(body,{status,headers:{'Cache-Control':'no-store',...headers}});
 if(path==='/api/auth/recovery/questions'&&request.method==='GET')return json({questions:RECOVERY_QUESTIONS});
 if(path==='/api/auth/me'){const user=await currentUser(request,env);if(!user)fail(401,'Please sign in');return json({user:publicUser(user)})}
 if(request.method!=='POST')fail(405,'POST required');originCheck(request);
 if(path==='/api/auth/logout'){const secret=sessionToken(request);if(secret)await env.DB.prepare('DELETE FROM sessions WHERE token_hash=?').bind(hash(secret)).run();return json({ok:true},200,{'Set-Cookie':'__Host-vert_session=; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=0'})}
 if(path==='/api/auth/password/change'){
  const signed=await currentUser(request,env);if(!signed)fail(401,'Please sign in');
  const b=await limitedBody(request);await rateLimit(request,env,'password-change:'+signed.email);
  if(!validPassword(b.currentPassword)||!validPassword(b.newPassword))fail(400,'Use passwords of 12–128 characters');
  if(b.newPassword!==b.confirmPassword)fail(400,'New passwords must match');
  if(b.currentPassword===b.newPassword)fail(400,'Choose a different new password');
  const row=await env.DB.prepare('SELECT password_hash FROM users WHERE id=?').bind(signed.id).first();
  if(!row||!await verifyPassword(b.currentPassword,row.password_hash,env.AUTH_PEPPER))fail(401,'Current password is incorrect');
  const next=await passwordHash(b.newPassword,env.AUTH_PEPPER);
  const results=await env.DB.batch([
   env.DB.prepare('UPDATE users SET password_hash=? WHERE id=? AND password_hash=?').bind(next,signed.id,row.password_hash),
   env.DB.prepare('DELETE FROM sessions WHERE user_id=? AND EXISTS(SELECT 1 FROM users WHERE id=? AND password_hash=?)').bind(signed.id,signed.id,next)
  ]);
  if(Number(results[0].meta?.changes??results[0].changes)===0)fail(409,'Account changed. Please try again.');
  return json({ok:true},200,{'Set-Cookie':'__Host-vert_session=; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=0'});
 }
 if(['/api/auth/recovery/setup','/api/auth/recovery/reset'].includes(path)){
  const b=await limitedBody(request),setup=path.endsWith('/setup');
  const signed=setup?await currentUser(request,env):null;
  if(setup&&!signed)fail(401,'Please sign in');
  const email=setup?signed.email:normalizeEmail(b.email);
  if(email.length>254||!/^\S+@[^\s@]+\.[^\s@]+$/.test(email))fail(400,'Enter a valid email');
  await recoveryLimit(request,env,email);
  const material=recoveryMaterial(b.answers);
  if(setup){
   if(!validPassword(b.currentPassword))fail(400,'Enter your current password');
   const row=await env.DB.prepare('SELECT password_hash FROM users WHERE id=?').bind(signed.id).first();
   if(!row||!await verifyPassword(b.currentPassword,row.password_hash,env.AUTH_PEPPER))fail(401,'Current password is incorrect');
   const answersHash=await passwordHash(material,env.AUTH_PEPPER);
   const result=await env.DB.prepare('INSERT INTO account_recovery(user_id,answers_hash,updated_at) SELECT id,?,? FROM users WHERE id=? AND password_hash=? ON CONFLICT(user_id) DO UPDATE SET answers_hash=excluded.answers_hash,updated_at=excluded.updated_at').bind(answersHash,Date.now(),signed.id,row.password_hash).run();
   if(Number(result.meta?.changes??result.changes)===0)fail(409,'Account changed. Sign in again and retry.');
   return json({ok:true});
  }
  if(!validPassword(b.newPassword))fail(400,'Use a password of 12–128 characters');
  const row=await env.DB.prepare('SELECT u.id,u.password_hash,r.answers_hash FROM users u LEFT JOIN account_recovery r ON r.user_id=u.id WHERE u.email=?').bind(email).first();
  const stored=row?.answers_hash||'pbkdf2-sha512-hmac-v1$missing-recovery$'+'0'.repeat(128);
  if(!await verifyPassword(material,stored,env.AUTH_PEPPER)||!row?.answers_hash)fail(401,'Unable to reset password. Check your email and all three answers. Recovery must have been set up previously.');
  const next=await passwordHash(b.newPassword,env.AUTH_PEPPER);
  const results=await env.DB.batch([
   env.DB.prepare('UPDATE users SET password_hash=? WHERE id=? AND password_hash=? AND EXISTS(SELECT 1 FROM account_recovery WHERE user_id=? AND answers_hash=?)').bind(next,row.id,row.password_hash,row.id,row.answers_hash),
   env.DB.prepare('DELETE FROM sessions WHERE user_id=? AND EXISTS(SELECT 1 FROM users WHERE id=? AND password_hash=?)').bind(row.id,row.id,next)
  ]);
  if(Number(results[0].meta?.changes??results[0].changes)===0)fail(409,'Account changed. Please try again.');
  return json({ok:true},200,{'Set-Cookie':'__Host-vert_session=; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=0'});
 }
 if(!['/api/auth/signup','/api/auth/login'].includes(path))fail(404,'Not found');
 const b=await limitedBody(request),email=normalizeEmail(b.email);
 if(email.length>254||!/^\S+@[^\s@]+\.[^\s@]+$/.test(email))fail(400,'Enter a valid email');
 await rateLimit(request,env,email);
 if(!validPassword(b.password))fail(400,'Use a password of 12–128 characters');
 let user=await env.DB.prepare('SELECT u.*,a.active,EXISTS(SELECT 1 FROM account_recovery r WHERE r.user_id=u.id) AS recovery_configured FROM users u JOIN activations a ON a.id=u.id WHERE u.email=?').bind(email).first();
 if(path==='/api/auth/signup'){
  if(user)fail(409,'An account already exists. Please sign in.');
  if(normalizeEmail(b.confirmEmail)!==email)fail(400,'Email addresses must match');
  const answers=recoveryMaterial(b.answers);
  const privileged=ADMIN_EMAILS.has(email);
  if(privileged&&(!env.EXEMPT_CODE_HASH||!equal(hash(String(b.setupCode||'').trim()),env.EXEMPT_CODE_HASH)))fail(403,'Enter the private admin setup code to register this reserved email');
  const id=crypto.randomUUID(),password_hash=await passwordHash(b.password,env.AUTH_PEPPER);
  const answersHash=await passwordHash(answers,env.AUTH_PEPPER);
  let active=privileged?1:0;
  // A saved legacy paid token can prove an existing device entitlement during account creation.
  const legacy=sessionToken(request);if(!active&&legacy){const prior=await env.DB.prepare('SELECT email,active FROM activations WHERE token_hash=?').bind(hash(legacy)).first();if(prior?.active&&prior.email.toLowerCase()===email)active=1;}
  try{await env.DB.batch([env.DB.prepare('INSERT INTO users(id,email,password_hash,role,payment_exempt) VALUES(?,?,?,?,?)').bind(id,email,password_hash,privileged?'admin':'user',privileged?1:0),env.DB.prepare('INSERT INTO activations(id,email,token_hash,active) VALUES(?,?,?,?)').bind(id,email,hash(crypto.randomUUID()+crypto.randomUUID()),active),env.DB.prepare('INSERT INTO account_recovery(user_id,answers_hash,updated_at) VALUES(?,?,?)').bind(id,answersHash,Date.now())])}catch(e){if(String(e.message).includes('UNIQUE'))fail(409,'An account already exists. Please sign in.');throw e;}
  user={id,email,role:privileged?'admin':'user',payment_exempt:privileged?1:0,active,recovery_configured:1};
 }else{
  // Spend equivalent hashing work for missing users to reduce account-existence timing signals.
  const stored=user?.password_hash||'pbkdf2-sha512-hmac-v1$missing-account$'+'0'.repeat(128);
  if(!await verifyPassword(b.password,stored,env.AUTH_PEPPER)||!user)fail(401,'Email or password is incorrect');
 }
 const tv=b.client==='tv',{secret,seconds}=await issueSession(user,env,tv);
 return json({user:publicUser(user),...(tv?{token:secret}:{})},path.endsWith('/signup')?201:200,{'Set-Cookie':`__Host-vert_session=${secret}; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=${seconds}`});
}
