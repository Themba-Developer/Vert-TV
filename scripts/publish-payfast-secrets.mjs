// Upload ignored editor credentials through stdin; never print their values.
import { readFile } from 'node:fs/promises';
import { parseEnv } from 'node:util';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const backend=fileURLToPath(new URL('../backend/',import.meta.url));
const values=parseEnv(await readFile(backend+'.dev.vars','utf8'));
const names=['PAYFAST_MERCHANT_ID','PAYFAST_MERCHANT_KEY','PAYFAST_PASSPHRASE'];
if(names.some(k=>!values[k]?.trim())) throw new Error('Fill all three Payfast fields in backend/.dev.vars first.');
if(values.PAYFAST_SANDBOX!=='true') throw new Error('This helper is for sandbox credentials. Configure production deliberately after sandbox verification.');
const child=spawn('npx',['wrangler','secret','bulk'],{cwd:backend,stdio:['pipe','inherit','inherit'],shell:false});
child.stdin.end(JSON.stringify(Object.fromEntries(names.map(k=>[k,values[k]]))));
child.on('exit',code=>{process.exitCode=code??1;});
