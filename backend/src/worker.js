import { buildTwoEmbedURL, twoEmbedCatalogue, twoEmbedMetadata, twoEmbedSeason, mediaID, episodeNumber, defaultTVSeason, readMedia, catalogueItem, isDuloURL, isTwoEmbedURL } from './twoembed.js';
import { buildVidSrcURL, checkVidSrcFeeds } from './vidsrc.js';
import { ACTIVATION_AMOUNT_CENTS, formatAmount, moneyToCents } from './billing.js';
import { authRoute, currentUser, originCheck, ADMIN_EMAILS } from './auth.js';
import { hash, signature, canonical, equal, httpsURL, escapeHTML } from './security.js';
const json = (value,status=200) => Response.json(value,{status,headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
const fail = (status,message) => { throw Object.assign(new Error(message),{status}); };
const token = () => crypto.randomUUID()+crypto.randomUUID();
async function admin(request,env) {
 const user=await currentUser(request,env);
 if(!user) fail(401,'Please sign in');
 if(user.role!=='admin') fail(403,'Administrator account required');
 return user.id;
}
async function device(request,env,active=false) {
 const account=await currentUser(request,env);
 if(account){if(active && !account.active && !account.payment_exempt)fail(403,'Payment required');return {...account,active:account.active||account.payment_exempt};}
 const bearer=request.headers.get('Authorization')?.replace(/^Bearer /,'');
 const record=bearer && await env.DB.prepare('SELECT * FROM activations WHERE token_hash=?').bind(hash(bearer)).first();
 if(!record) fail(401,'Activation token required'); if(active && !record.active) fail(403,'Payment required'); return record;
}
function payfast(env) { if(!env.PAYFAST_MERCHANT_ID || !env.PAYFAST_MERCHANT_KEY || !env.PAYFAST_PASSPHRASE) fail(503,'Payfast credentials are not configured yet'); return env.PAYFAST_SANDBOX==='true'?'https://sandbox.payfast.co.za':'https://www.payfast.co.za'; }
async function handle(request,env) {
 const u=new URL(request.url), path=u.pathname;
 if(path.startsWith('/api/auth/')) return authRoute(request,env,path);
 if(['/login','/signup','/account','/app'].includes(path)) return env.ASSETS.fetch(new Request(new URL('/auth',u),request));
 if(path==='/api/billing/checkout' && request.method==='POST') {
  originCheck(request);const user=await currentUser(request,env);if(!user)fail(401,'Please sign in');
  if(user.active||user.payment_exempt)return json({active:true});
  payfast(env);
  let order=await env.DB.prepare('SELECT id FROM orders WHERE activation_id=? AND paid=0 AND amount_cents=? ORDER BY rowid DESC LIMIT 1').bind(user.id,ACTIVATION_AMOUNT_CENTS).first();
  if(!order){const id=crypto.randomUUID();await env.DB.prepare('INSERT INTO orders(id,activation_id,user_id,amount_cents) VALUES(?,?,?,?)').bind(id,user.id,user.id,ACTIVATION_AMOUNT_CENTS).run();order={id};}
  const ticket=token();await env.DB.prepare('INSERT INTO checkout_tickets(ticket_hash,order_id,expires_at) VALUES(?,?,?)').bind(hash(ticket),order.id,Date.now()+30*60000).run();
  return json({checkoutUrl:`${env.PUBLIC_BASE_URL}/checkout?ticket=${ticket}`});
 }
 if((path==='/admin'||path==='/admin/')&&!await currentUser(request,env))return Response.redirect(new URL('/login?next=/admin',u),302);
 if(path==='/api/health') return json({ok:true,service:'Vert TV',paymentsConfigured:!!(env.PAYFAST_MERCHANT_ID && env.PAYFAST_MERCHANT_KEY && env.PAYFAST_PASSPHRASE),paymentMode:env.PAYFAST_SANDBOX==='true'?'sandbox':'live',activationAmountCents:ACTIVATION_AMOUNT_CENTS,currency:'ZAR'});
 if(path.startsWith('/api/admin/') || path==='/admin' || path.startsWith('/admin/')) {
  const owner=await admin(request,env); if(!['GET','HEAD','OPTIONS'].includes(request.method))originCheck(request);
  if(path==='/admin' || path==='/admin/') return env.ASSETS.fetch(new Request(new URL('/admin',u),request));
  if(path==='/api/admin/2embed/catalog' && request.method==='GET'){
   const input=Object.fromEntries(u.searchParams);
   if(input.mode==='id')return json({page:1,totalPages:1,items:[await twoEmbedMetadata(input.mediaType,input.mediaId)]});
   return json(await twoEmbedCatalogue(input));
  }
  if(path==='/api/admin/2embed/preview' && request.method==='POST')return json({url:buildTwoEmbedURL(await request.json())});
  if(path==='/api/admin/2embed/import' && request.method==='POST'){
   const body=await request.json();if(!Array.isArray(body.entries)||body.entries.length<1||body.entries.length>10)fail(400,'Select between 1 and 10 titles');
   const entries=body.entries.map(e=>{if(!e||!['movie','tv'].includes(e.mediaType))fail(400,'Invalid media type');return {type:e.mediaType,id:mediaID(e.mediaId)}});
   const category=typeof body.category==='string'?body.category.trim().slice(0,100):'';
   const imported=[],skipped=[],failed=[],queue=[...new Map(entries.map(e=>[e.type+':'+e.id,e])).values()];
   async function consume(){while(queue.length){const entry=queue.shift();try{
    const item=await twoEmbedMetadata(entry.type,entry.id),id='2embed-'+item.type+'-'+item.id;
    if(await env.DB.prepare('SELECT id FROM content WHERE id=?').bind(id).first()){skipped.push({id,title:item.title});continue;}
    const url=buildTwoEmbedURL({mediaType:item.type,mediaId:item.id,season:defaultTVSeason(item),episode:1});
    const saved=await env.DB.prepare('INSERT OR IGNORE INTO content(id,title,category,url,kind,metadata_json) VALUES(?,?,?,?,?,?)').bind(id,item.title,category||(item.type==='tv'?'TV Shows':'Movies'),url,'link',JSON.stringify(item)).run();
    if(Number(saved.meta?.changes??saved.changes)===0)skipped.push({id,title:item.title});else imported.push({id,title:item.title});
   }catch(error){failed.push({id:entry.id,error:error.status?error.message:'Provider import failed'})}}}
   await Promise.all([consume(),consume(),consume()]);return json({imported,skipped,failed});
  }
  if(path==='/api/admin/vidsrc/feeds' && request.method==='GET')return json(await checkVidSrcFeeds());
  if(['/api/admin/vidsrc/preview','/api/admin/vidsrc/publish'].includes(path) && request.method==='POST'){
   const body=await request.json(),url=buildVidSrcURL(body);
   if(path.endsWith('/preview'))return json({url});
   if(typeof body.title!=='string'||!body.title.trim()||typeof body.category!=='string'||!body.category.trim())fail(400,'Title and category are required to publish');
   const id=crypto.randomUUID();await env.DB.prepare('INSERT INTO content(id,title,category,url,kind) VALUES(?,?,?,?,?)').bind(id,body.title.trim().slice(0,200),body.category.trim().slice(0,100),url,'link').run();
   return json({id,url},201);
  }
  if(path==='/api/admin/content' && request.method==='GET') return json({items:(await env.DB.prepare('SELECT * FROM content ORDER BY created_at DESC').all()).results});
  if(path==='/api/admin/content' && request.method==='POST') {
   const b=await request.json(); if(!b.title?.trim() || !b.category?.trim() || !httpsURL(b.url)) fail(400,'Title, category and HTTPS URL required');
   const kind=b.kind??'link';if(!['link','collection'].includes(kind))fail(400,'Choose a single title or library collection');
   const id=crypto.randomUUID(); await env.DB.prepare('INSERT INTO content(id,title,category,url,kind) VALUES(?,?,?,?,?)').bind(id,b.title.trim().slice(0,200),b.category.trim().slice(0,100),b.url,kind).run(); return json({id},201);
  }
  if(path.startsWith('/api/admin/content/') && request.method==='PUT') {
   const b=await request.json(), id=path.split('/').pop();
   const current=await env.DB.prepare('SELECT * FROM content WHERE id=?').bind(id).first(); if(!current) fail(404,'Title not found');
   if(!b.title?.trim() || !b.category?.trim() || (!current.object_key && !httpsURL(b.url))) fail(400,'Title, category and HTTPS URL required');
   const kind=current.object_key?'link':b.kind??current.kind??'link';if(!['link','collection'].includes(kind))fail(400,'Choose a single title or library collection');
   const metadata=kind==='link'&&!current.object_key&&b.url===current.url?current.metadata_json||null:null;
   await env.DB.prepare('UPDATE content SET title=?,category=?,url=?,kind=?,metadata_json=? WHERE id=?').bind(b.title.trim().slice(0,200),b.category.trim().slice(0,100),current.object_key?null:b.url,kind,metadata,id).run();
   return json({ok:true});
  }
  if(path.startsWith('/api/admin/content/') && request.method==='DELETE') { await env.DB.prepare('DELETE FROM content WHERE id=?').bind(path.split('/').pop()).run(); return json({ok:true}); }
  if(path==='/api/admin/uploads' && request.method==='POST') {
   const b=await request.json();
   if(!b.title?.trim() || !b.category?.trim() || !['video/mp4','video/webm'].includes(b.type) || !Number.isSafeInteger(b.size) || b.size<1 || b.size>50*1024**3) fail(400,'Use MP4/WebM, up to 50 GB, with title and category');
   const key=`movies/${crypto.randomUUID()}.${b.type==='video/mp4'?'mp4':'webm'}`;
   const upload=await env.MOVIES.createMultipartUpload(key,{httpMetadata:{contentType:b.type}});
   await env.DB.prepare('INSERT INTO uploads(id,object_key,owner,title,category,content_type,size) VALUES(?,?,?,?,?,?,?)').bind(upload.uploadId,key,owner,b.title.trim().slice(0,200),b.category.trim().slice(0,100),b.type,b.size).run();
   return json({uploadId:upload.uploadId});
  }
  const match=path.match(/^\/api\/admin\/uploads\/([^/]+)(?:\/(complete|abort|[0-9]+))?$/);
  if(match) {
   const row=await env.DB.prepare('SELECT * FROM uploads WHERE id=? AND owner=?').bind(decodeURIComponent(match[1]),owner).first(); if(!row) fail(404,'Upload not found');
   const upload=env.MOVIES.resumeMultipartUpload(row.object_key,row.id);
   if(request.method==='PUT' && /^\d+$/.test(match[2]||'')) {
    const part=Number(match[2]); if(part<1 || part>10000) fail(400,'Invalid part');
    const bytes=await request.arrayBuffer(); if(bytes.byteLength>10*1024**2 || bytes.byteLength<1) fail(400,'Part exceeds 10 MB');
    return json(await upload.uploadPart(part,bytes));
   }
   if(match[2]==='abort' && request.method==='POST') { await upload.abort(); await env.DB.prepare('DELETE FROM uploads WHERE id=?').bind(row.id).run(); return json({ok:true}); }
   if(match[2]==='complete' && request.method==='POST') {
    const {parts}=await request.json(); if(!Array.isArray(parts) || parts.length<1 || parts.length>10000 || parts.some((p,i)=>p.partNumber!==i+1 || typeof p.etag!=='string')) fail(400,'Invalid parts');
    await upload.complete(parts);
    const head=await env.MOVIES.head(row.object_key); if(!head || head.size!==row.size) { await env.MOVIES.delete(row.object_key); fail(400,'Uploaded size mismatch'); }
    await env.DB.batch([env.DB.prepare('INSERT INTO content(id,title,category,object_key) VALUES(?,?,?,?)').bind(crypto.randomUUID(),row.title,row.category,row.object_key),env.DB.prepare('DELETE FROM uploads WHERE id=?').bind(row.id)]); return json({ok:true});
   }
  }
  fail(404,'Admin route not found');
 }
 // The admin document is served only after account-role checks.
 if(path==='/admin.html') fail(404,'Not found');
 if(path==='/api/activation/start' && request.method==='POST') {
  originCheck(request);
  const b=await request.json(), email=String(b.email||'').trim().toLowerCase(); if(email.length>254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) fail(400,'Valid email required');
  if(ADMIN_EMAILS.has(email) || (env.EXEMPT_EMAIL && email===env.EXEMPT_EMAIL.toLowerCase())) {
   if(!b.activationCode) return json({requiresActivationCode:true});
   if(!env.EXEMPT_CODE_HASH || !equal(hash(String(b.activationCode).trim()),env.EXEMPT_CODE_HASH)) fail(403,'Invalid private activation code');
   const id=crypto.randomUUID(), secret=token();
   await env.DB.prepare('INSERT INTO activations(id,email,token_hash,active) VALUES(?,?,?,1)').bind(id,email,hash(secret)).run();
   return json({token:secret,active:true});
  }
  payfast(env);
  const id=crypto.randomUUID(), secret=token(), checkout=token(), order=crypto.randomUUID();
  // A fresh device must pay; knowing an active email does not grant access.
  await env.DB.batch([env.DB.prepare('INSERT INTO activations(id,email,token_hash) VALUES(?,?,?)').bind(id,email,hash(secret)),env.DB.prepare('INSERT INTO orders(id,activation_id,amount_cents) VALUES(?,?,?)').bind(order,id,ACTIVATION_AMOUNT_CENTS)]);
  // Checkout ticket is scoped to checkout only, never a device bearer token.
  await env.DB.prepare('INSERT INTO checkout_tickets(ticket_hash,order_id,expires_at) VALUES(?,?,?)').bind(hash(checkout),order,Date.now()+30*60*1000).run();
  return json({token:secret,checkoutUrl:`${env.PUBLIC_BASE_URL}/checkout?ticket=${checkout}`});
 }
 if(path==='/api/activation/checkout' && request.method==='POST') {
  originCheck(request); const d=await device(request,env);
  if(ADMIN_EMAILS.has(d.email) || (env.EXEMPT_EMAIL && d.email===env.EXEMPT_EMAIL.toLowerCase())) return json({requiresActivationCode:true});
  payfast(env);
  if(d.active)return json({active:true});
  let order=await env.DB.prepare('SELECT id FROM orders WHERE activation_id=? AND paid=0 AND amount_cents=? ORDER BY rowid DESC LIMIT 1').bind(d.id,ACTIVATION_AMOUNT_CENTS).first();
  if(!order){const id=crypto.randomUUID();await env.DB.prepare('INSERT INTO orders(id,activation_id,amount_cents) VALUES(?,?,?)').bind(id,d.id,ACTIVATION_AMOUNT_CENTS).run();order={id};}
  const checkout=token();
  await env.DB.prepare('INSERT INTO checkout_tickets(ticket_hash,order_id,expires_at) VALUES(?,?,?)').bind(hash(checkout),order.id,Date.now()+30*60*1000).run();
  return json({checkoutUrl:`${env.PUBLIC_BASE_URL}/checkout?ticket=${checkout}`});
 }
 if(path==='/api/activation/status') { const d=await device(request,env); return json({active:!!d.active}); }
 if(path==='/checkout') {
  const host=payfast(env), ticket=u.searchParams.get('ticket')||'';
  const order=await env.DB.prepare('SELECT o.id,o.amount_cents,a.email FROM checkout_tickets t JOIN orders o ON o.id=t.order_id JOIN activations a ON a.id=o.activation_id WHERE t.ticket_hash=? AND t.expires_at>?').bind(hash(ticket),Date.now()).first(); if(!order) fail(401,'Checkout expired. Return to the app and retry.');
  const amount=formatAmount(order.amount_cents);
  // Chrome checks form-action against Payfast's hosted-payment redirect as well as the POST destination.
  const formOrigins=env.PAYFAST_SANDBOX==='true'?host:host+' https://payment.payfast.io';
  const p=new URLSearchParams({merchant_id:env.PAYFAST_MERCHANT_ID,merchant_key:env.PAYFAST_MERCHANT_KEY,return_url:env.PUBLIC_BASE_URL+'/payment/return',cancel_url:env.PUBLIC_BASE_URL+'/payment/cancel',notify_url:env.PUBLIC_BASE_URL+'/api/payfast/itn',email_address:order.email,m_payment_id:order.id,amount,item_name:'Vert TV once-off activation'});
  p.set('signature',signature(p,env.PAYFAST_PASSPHRASE));
  return new Response(`<!doctype html><html><head><meta name="viewport" content="width=device-width"></head><body style="background:#090f1b;color:white;font:24px sans-serif;padding:4rem"><h1>Activate Vert TV</h1><p>R${amount} once-off</p><form method="POST" action="${host}/eng/process">${[...p].map(([k,v])=>`<input type="hidden" name="${k}" value="${escapeHTML(v)}">`).join('')}<button style="font-size:24px;padding:20px">Continue to secure Payfast checkout</button></form></body></html>`,{headers:{'Content-Type':'text/html;charset=utf-8','Cache-Control':'no-store','Referrer-Policy':'no-referrer','Content-Security-Policy':`default-src 'none'; style-src 'unsafe-inline'; form-action ${formOrigins}; frame-ancestors 'none'`}});
 }
 if(path==='/api/payfast/itn' && request.method==='POST') {
  const host=payfast(env), raw=await request.text(); if(raw.length>20000) fail(413,'Too large');
  const p=new URLSearchParams(raw); if(new Set([...p.keys()]).size!==[...p.keys()].length) fail(400,'Duplicate fields');
  if(!equal(signature(p,env.PAYFAST_PASSPHRASE),p.get('signature')) || p.get('merchant_id')!==env.PAYFAST_MERCHANT_ID) fail(400,'Invalid ITN signature');
  // Validate source against Payfast DNS using Cloudflare DNS-over-HTTPS.
  const ip=request.headers.get('CF-Connecting-IP'); let validIP=false;
  for(const domain of ['www.payfast.co.za','sandbox.payfast.co.za','w1w.payfast.co.za','w2w.payfast.co.za']) {
   const dns=await fetch(`https://cloudflare-dns.com/dns-query?name=${domain}&type=${ip?.includes(':')?'AAAA':'A'}`,{headers:{accept:'application/dns-json'}});
   const answer=await dns.json(); if(answer.Answer?.some(a=>a.data===ip)) validIP=true;
  }
  if(!validIP) fail(403,'Invalid ITN source');
  const check=await fetch(host+'/eng/query/validate',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:canonical(p)});
  if(!check.ok || (await check.text()).trim()!=='VALID') fail(400,'ITN validation failed');
  const order=await env.DB.prepare('SELECT * FROM orders WHERE id=?').bind(p.get('m_payment_id')).first();
  if(!order || moneyToCents(p.get('amount_gross'))!==order.amount_cents || p.get('payment_status')!=='COMPLETE' || !p.get('pf_payment_id')) fail(400,'Payment mismatch');
  if(order.paid) { if(order.pf_payment_id!==p.get('pf_payment_id')) fail(409,'Payment mismatch'); return new Response('OK'); }
  await env.DB.batch([env.DB.prepare('UPDATE orders SET paid=1,pf_payment_id=? WHERE id=? AND paid=0').bind(p.get('pf_payment_id'),order.id),env.DB.prepare('UPDATE activations SET active=1 WHERE id=?').bind(order.activation_id)]);
  return new Response('OK');
 }
 if(path==='/api/library/activity'){
  const user=await currentUser(request,env);if(!user)fail(401,'Please sign in');if(!user.active&&!user.payment_exempt)fail(403,'Payment required');
  if(request.method==='GET')return json({items:(await env.DB.prepare('SELECT a.content_id AS id,a.loved,a.last_opened AS lastOpened FROM library_activity a JOIN content c ON c.id=a.content_id WHERE a.user_id=? AND c.published=1 ORDER BY a.last_opened DESC LIMIT 500').bind(user.id).all()).results});
  if(request.method!=='POST')fail(405,'POST required');originCheck(request);
  const raw=await request.text();if(raw.length>1024)fail(413,'Request too large');let body;try{body=JSON.parse(raw)}catch{fail(400,'Invalid JSON')}
  if(!body||typeof body.contentId!=='string'||body.contentId.length>100||('loved' in body&&typeof body.loved!=='boolean')||('opened' in body&&body.opened!==true)||(!('loved' in body)&&!body.opened))fail(400,'Choose a library item and action');
  if(!await env.DB.prepare('SELECT id FROM content WHERE id=? AND published=1').bind(body.contentId).first())fail(404,'Library item not found');
  const loved='loved' in body?Number(body.loved):null,opened=body.opened?Date.now():null;
  await env.DB.prepare('INSERT INTO library_activity(user_id,content_id,loved,last_opened) VALUES(?,?,COALESCE(?,0),?) ON CONFLICT(user_id,content_id) DO UPDATE SET loved=COALESCE(?,library_activity.loved),last_opened=COALESCE(?,library_activity.last_opened)').bind(user.id,body.contentId,loved,opened,loved,opened).run();
  return json({ok:true});
 }
 if(path==='/api/catalogue' && request.method==='GET'){
  await device(request,env,true);
  const input=Object.fromEntries(u.searchParams);
  if(!['trending','search',undefined].includes(input.mode))fail(400,'Choose trending or search');
  const result=await twoEmbedCatalogue(input);return json({...result,items:result.items.map(catalogueItem)});
 }
 const catalogueTitle=path.match(/^\/api\/catalogue\/(movie|tv)\/([^/]+)$/);
 if(catalogueTitle && request.method==='GET'){
  await device(request,env,true);return json(catalogueItem(await twoEmbedMetadata(catalogueTitle[1],mediaID(catalogueTitle[2]))));
 }
 if(path==='/api/get_links.json') {
  await device(request,env,true);
  const {results}=await env.DB.prepare('SELECT * FROM content WHERE published=1 ORDER BY CASE WHEN metadata_json IS NOT NULL THEN 0 ELSE 1 END,category,title').all();
  let items=await Promise.all(results.filter(x=>env.CATALOGUE_SOURCE==='2embed'?!isDuloURL(x.url):env.CATALOGUE_SOURCE==='dulo'?readMedia(x.metadata_json)?.provider!=='2embed'&&!isTwoEmbedURL(x.url):true).map(async x=>{
   if(!x.object_key) return {id:x.id,title:x.title,category:x.category,url:x.url,kind:x.kind||'link',media:readMedia(x.metadata_json)};
   const access=token(); await env.DB.prepare('INSERT INTO media_tickets(ticket_hash,content_id,expires_at) VALUES(?,?,?)').bind(hash(access),x.id,Date.now()+6*3600000).run();
   return {id:x.id,title:x.title,category:x.category,url:`${env.PUBLIC_BASE_URL}/watch/${x.id}?ticket=${access}`,kind:'link'};
  }));
  const warnings=[];
  if(env.CATALOGUE_SOURCE==='2embed'){
   const feeds=await Promise.allSettled(['movie','tv'].map(mediaType=>twoEmbedCatalogue({mediaType})));
   const live=feeds.flatMap((feed,index)=>{if(feed.status==='fulfilled')return feed.value.items.map(catalogueItem);warnings.push((index?'TV shows':'Movies')+' are temporarily unavailable. Please retry.');return []});
   const keys=new Set(live.map(x=>x.media.type+':'+x.media.id));items=[...live,...items.filter(x=>!x.media||!keys.has(x.media.type+':'+x.media.id))];
  }
  return json({items,warnings,catalogueSource:env.CATALOGUE_SOURCE||'curated'});
 }
 const episodePath=path.match(/^\/api\/content\/([^/]+)\/episodes$/);
 if(episodePath && request.method==='GET'){
  await device(request,env,true);let id;try{id=decodeURIComponent(episodePath[1])}catch{fail(400,'Invalid content ID')}
  const live=id.match(/^2embed-live-tv-(tt\d{7,10}|[1-9]\d{0,9})$/);
  const record=live?null:await env.DB.prepare('SELECT * FROM content WHERE id=? AND published=1').bind(id).first();
  const details=live?await twoEmbedMetadata('tv',live[1]):readMedia(record?.metadata_json);
  if(details?.type!=='tv')fail(404,'TV series not found');
  const season=episodeNumber(u.searchParams.get('season')??defaultTVSeason(details));
  if(details.seasons?.length&&!details.seasons.some(s=>s.number===season))fail(400,'Choose one of this series’ listed seasons');
  return json({id,title:record?.title||details.title,seasons:details.seasons,...await twoEmbedSeason(details.id,season)});
 }
 const media=path.match(/^\/(watch|media)\/([^/]+)$/);
 if(media) {
  const ticket=u.searchParams.get('ticket')||'';
  const record=await env.DB.prepare('SELECT c.* FROM media_tickets t JOIN content c ON c.id=t.content_id WHERE t.ticket_hash=? AND t.content_id=? AND t.expires_at>? AND c.published=1').bind(hash(ticket),media[2],Date.now()).first(); if(!record) fail(403,'Playback link expired. Refresh the TV library.');
  if(media[1]==='watch') return new Response(`<!doctype html><html><head><meta name="referrer" content="no-referrer"></head><body style="margin:0;background:black"><video style="width:100vw;height:100vh" controls autoplay src="/media/${record.id}?ticket=${encodeURIComponent(ticket)}"></video></body></html>`,{headers:{'Content-Type':'text/html','Cache-Control':'no-store','Content-Security-Policy':"default-src 'none'; media-src 'self'; style-src 'unsafe-inline'"}});
  const object=await env.MOVIES.get(record.object_key,{range:request.headers,onlyIf:request.headers}); if(!object) fail(404,'Movie missing');
  const h=new Headers({'Cache-Control':'private, no-store','Accept-Ranges':'bytes','Referrer-Policy':'no-referrer'}); object.writeHttpMetadata(h); h.set('ETag',object.httpEtag);
  if(!('body' in object)) return new Response(null,{status:412,headers:h});
  let status=200;
  if(object.range && 'offset' in object.range) {status=206;h.set('Content-Range',`bytes ${object.range.offset}-${object.range.offset+object.range.length-1}/${object.size}`);h.set('Content-Length',String(object.range.length));} else h.set('Content-Length',String(object.size));
  return new Response(request.method==='HEAD'?null:object.body,{status,headers:h});
 }
 if(path.startsWith('/payment/')) return Response.redirect(new URL('/account?payment=returned',u),302);
 if(path.startsWith('/api/')) fail(404,'Not found');
 return env.ASSETS.fetch(request);
}
export default {
 async fetch(request,env) { try {return await handle(request,env);} catch(e) {if(!e.status) console.error('Request failed',e.name); return json({error:e.status?e.message:'Service temporarily unavailable'},e.status||500);} },
 async scheduled(event,env) { await env.DB.batch([env.DB.prepare('DELETE FROM checkout_tickets WHERE expires_at<?').bind(Date.now()),env.DB.prepare('DELETE FROM media_tickets WHERE expires_at<?').bind(Date.now()),env.DB.prepare('DELETE FROM sessions WHERE expires_at<?').bind(Date.now()),env.DB.prepare('DELETE FROM auth_rate_limits WHERE window_start<?').bind(Date.now()-86400000)]); }
};
