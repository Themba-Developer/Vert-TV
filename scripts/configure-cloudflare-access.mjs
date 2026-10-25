// Reads an ignored, narrowly scoped setup token; never prints it or uploads it to the Worker.
import {readFile,writeFile} from 'node:fs/promises';
import {parseEnv} from 'node:util';
const root=new URL('../',import.meta.url);
const local=parseEnv(await readFile(new URL('backend/.dev.vars',root),'utf8'));
const setupToken=local.CLOUDFLARE_ACCESS_SETUP_TOKEN?.trim();
if(!setupToken)throw new Error('Fill CLOUDFLARE_ACCESS_SETUP_TOKEN in backend/.dev.vars first.');
const configPath=new URL('backend/wrangler.jsonc',root);
const config=JSON.parse(await readFile(configPath,'utf8'));
const api='https://api.cloudflare.com/client/v4/accounts/084057023053f1d2723e614fdf90162a/access/apps';
async function call(url,body){
 const response=await fetch(url,{method:body?'POST':'GET',headers:{Authorization:'Bearer '+setupToken,'Content-Type':'application/json'},...(body?{body:JSON.stringify(body)}:{})});
 const data=await response.json();
 if(!response.ok||!data.success)throw new Error('Access setup failed: '+response.status+' '+JSON.stringify(data.errors||[]));
 return data.result;
}
const paths=['vert-tv.mahlangu843.workers.dev/admin','vert-tv.mahlangu843.workers.dev/api/admin'];
const apps=await call(api);
let app=apps.find(a=>a.name==='Vert TV Admin'&&a.domain===paths[0]);
if(!app)app=await call(api,{name:'Vert TV Admin',type:'self_hosted',domain:paths[0],self_hosted_domains:paths,session_duration:'24h',app_launcher_visible:true,policies:[{name:'Vert TV administrator',decision:'allow',include:[{email:{email:'vertcorpgroup@gmail.com'}},{email:{email:'mahlangu843@gmail.com'}}],precedence:1}]});
if(!app.aud)throw new Error('Cloudflare did not return an application audience.');
config.vars.CF_ACCESS_AUD=app.aud;
await writeFile(configPath,JSON.stringify(config,null,2)+'\n');
console.log('Admin Access application is configured. Audience saved to backend/wrangler.jsonc. Deploy the Worker next.');
