import {httpsURL} from './security.js';
export const VIDSRC_ORIGIN='https://vidsrc.mov';
export const VIDSRC_FEEDS=[{name:'Latest movies',path:'/vapi/movie/new'},{name:'Recently added movies',path:'/vapi/movie/add'},{name:'Latest TV shows',path:'/vapi/tv/new'},{name:'Latest episodes',path:'/vapi/episode/latest'}];
function bad(message){throw Object.assign(new Error(message),{status:400})}
export function buildVidSrcURL(input){
 if(!input||typeof input!=='object'||Array.isArray(input))bad('Provider details are required');
 const type=input.mediaType,id=String(input.mediaId??'').trim();
 if(!['movie','tv'].includes(type))bad('Choose Movie or TV episode');
 if(!/^(tt\d{7,10}|[1-9]\d{0,9})$/.test(id))bad('Enter an IMDb ID such as tt17048514 or a positive numeric TMDB ID');
 let path='/embed/movie/'+id;
 if(type==='tv'){
  const season=String(input.season??''),episode=String(input.episode??'');
  if(!/^\d{1,4}$/.test(season)||!/^\d{1,4}$/.test(episode)||Number(episode)<1)bad('TV episodes require a season (0–9999) and episode (1–9999)');
  path='/embed/tv/'+id+'/'+Number(season)+'/'+Number(episode);
 }
 const url=new URL(path,VIDSRC_ORIGIN),subtitle=String(input.subtitlesUrl??'').trim();
 if(subtitle){if(subtitle.length>4096||!httpsURL(subtitle))bad('Subtitle JSON must be a public HTTPS URL without embedded credentials');url.searchParams.set('sub.info',subtitle)}
 return url.href;
}
export async function checkVidSrcFeeds(){
 const feeds=await Promise.all(VIDSRC_FEEDS.map(async feed=>{try{const r=await fetch(VIDSRC_ORIGIN+feed.path,{redirect:'manual',headers:{Accept:'application/json'},signal:AbortSignal.timeout(8000)});const type=r.headers.get('Content-Type')||'';await r.body?.cancel();return {...feed,status:r.status,json:r.ok&&type.toLowerCase().includes('application/json')}}catch{return {...feed,status:null,json:false,error:'Provider unreachable or timed out'}}}));
 return {checkedAt:new Date().toISOString(),feeds};
}
