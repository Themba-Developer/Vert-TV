import { createHash } from 'node:crypto';
export const hash = value => createHash('sha256').update(value).digest('hex');
export const encode = value => encodeURIComponent(String(value).trim()).replace(/[!'()*]/g, c => '%' + c.charCodeAt(0).toString(16).toUpperCase()).replace(/%20/g, '+');
export function canonical(params) { return [...params].filter(([k,v]) => k !== 'signature' && v !== '').map(([k,v]) => `${k}=${encode(v)}`).join('&'); }
export function signature(params, passphrase = '') { return createHash('md5').update(canonical(params) + (passphrase ? `&passphrase=${encode(passphrase)}` : '')).digest('hex'); }
export function equal(a,b) { if(typeof a !== 'string' || typeof b !== 'string' || a.length !== b.length) return false; let n=0; for(let i=0;i<a.length;i++) n |= a.charCodeAt(i)^b.charCodeAt(i); return n===0; }
export function httpsURL(value) { try { const u=new URL(value); return u.protocol==='https:' && !u.username && !u.password; } catch { return false; } }
export const escapeHTML = value => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
