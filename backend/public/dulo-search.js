export function duloSearchURL(value){
 const query=String(value||'').trim();
 if(!query||query.length>120)throw new Error('Enter a movie or show name using 1–120 characters.');
 const url=new URL('https://dulo.mov/search');url.searchParams.set('q',query);return url.href;
}
