import {httpsURL} from './security.js';
export const TWOEMBED_API='https://api.2embed.cc';
export const TWOEMBED_PLAYER='https://www.2embed.cc';
const bad=message=>{throw Object.assign(new Error(message),{status:400})};
const upstream=message=>{throw Object.assign(new Error(message),{status:502})};
export function mediaID(value){const id=String(value??'').trim();if(!/^(tt\d{7,10}|[1-9]\d{0,9})$/.test(id))bad('A valid IMDb or TMDB ID is required');return id}
function typeOf(type){if(!['movie','tv'].includes(type))bad('Choose movie or TV');return type}
export function episodeNumber(value,minimum=0){const text=String(value??'');if(!/^\d{1,4}$/.test(text)||Number(text)<minimum)bad('Invalid season or episode number');return Number(text)}
export function buildTwoEmbedURL(input){const type=typeOf(input?.mediaType),id=mediaID(input?.mediaId);if(type==='movie')return TWOEMBED_PLAYER+'/embed/'+id;return TWOEMBED_PLAYER+'/embedtv/'+id+'&s='+episodeNumber(input.season)+'&e='+episodeNumber(input.episode,1)}
const safeText=(value,max)=>typeof value==='string'?value.trim().slice(0,max):'';
function posterURL(value){if(typeof value!=='string')return null;const input=value.startsWith('/')?'https://image.tmdb.org/t/p/w342'+value:value;if(!httpsURL(input))return null;const url=new URL(input);if(url.hostname==='image.tmdb.org')url.pathname=url.pathname.replace('/t/p/original/','/t/p/w342/');return url.href}
export function normalizeTwoEmbed(record,type,fallbackID){typeOf(type);if(!record||typeof record!=='object')return null;let id;for(const candidate of [record.imdb_id,record.tmdb_id,record.id,fallbackID]){try{id=mediaID(candidate);break}catch{}}
 const title=safeText(type==='tv'?(record.name||record.title):record.title,200);if(!id||!title)return null;
 const year=String(record.year||record.first_air_year||record.release_date||record.first_air_date||'').match(/^\d{4}/)?.[0]||'';
 const genres=Array.isArray(record.genres)?record.genres.map(g=>safeText(typeof g==='string'?g:g?.name,60)).filter(Boolean).slice(0,15):[];
 const seasons=type==='tv'&&Array.isArray(record.seasons)?record.seasons.filter(s=>Number.isInteger(s.season_number)&&s.season_number>=0&&s.season_number<=9999).slice(0,200).map(s=>({number:s.season_number,name:safeText(s.name,100)||'Season '+s.season_number,episodeCount:Number.isInteger(s.episode_count)?s.episode_count:null})):[];
 const rating=Number(record.vote_average);return {provider:'2embed',type,id,title,year,poster:posterURL(record.poster||record.poster_path),overview:safeText(record.overview||record.plot,2000),genres,seasons,rating:Number.isFinite(rating)&&rating>0&&rating<=10?rating:null};
}
async function providerJSON(path,params){const url=new URL(path,TWOEMBED_API);for(const [key,value] of Object.entries(params))url.searchParams.set(key,String(value));const request=new Request(url,{headers:{Accept:'application/json'}});const cache=globalThis.caches?.default;const cached=cache?await cache.match(request):null;if(cached)return cached.json();
 let response;try{response=await fetch(request,{redirect:'manual',signal:AbortSignal.timeout(12000)})}catch{upstream('2Embed metadata is unreachable or timed out')}
 if(!response.ok){await response.body?.cancel();upstream('2Embed metadata returned HTTP '+response.status)}
 if(!(response.headers.get('Content-Type')||'').toLowerCase().includes('application/json')){await response.body?.cancel();upstream('2Embed returned a non-JSON response')}
 const reader=response.body.getReader(),chunks=[];let size=0;for(;;){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>4*1024*1024){await reader.cancel();upstream('2Embed metadata response is too large')}chunks.push(value)}const bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length}let data;try{data=JSON.parse(new TextDecoder().decode(bytes))}catch{upstream('2Embed returned invalid JSON')}
 if(!data||typeof data!=='object'||data.success===false)upstream('2Embed did not return usable metadata');if(cache)await cache.put(request,Response.json(data,{headers:{'Cache-Control':'public,max-age=300'}}));return data;
}
function idQuery(id){id=mediaID(id);return {[id.startsWith('tt')?'imdb_id':'tmdb_id']:id}}
export async function twoEmbedMetadata(type,id){typeOf(type);const data=await providerJSON('/'+type,idQuery(id));const item=normalizeTwoEmbed(data,type,id);if(!item)upstream('2Embed metadata is missing a title or valid ID');return item}
export async function twoEmbedCatalogue(input){const type=typeOf(input.mediaType),mode=input.mode||'trending',page=Number(input.page||1);if(!Number.isInteger(page)||page<1||page>500)bad('Page must be between 1 and 500');let endpoint,params={page};if(mode==='search'){const query=safeText(input.query,150);if(!query)bad('Enter a search term');endpoint=type==='tv'?'/searchtv':'/search';params.q=query}else if(mode==='similar'){endpoint=type==='tv'?'/similartv':'/similar';Object.assign(params,idQuery(input.mediaId))}else if(mode==='trending'){endpoint=type==='tv'?'/trendingtv':'/trending';params.time_window='day'}else bad('Invalid catalogue mode');
 const data=await providerJSON(endpoint,params);if(!Array.isArray(data.results))upstream('2Embed catalogue has no results array');return {page,totalPages:Math.min(500,Math.max(1,Number(data.total_pages)||1)),items:data.results.slice(0,40).map(x=>normalizeTwoEmbed(x,type)).filter(Boolean)};
}
export async function twoEmbedSeason(id,season){season=episodeNumber(season);const data=await providerJSON('/season',{...idQuery(id),season});if(!Array.isArray(data.episodes))upstream('2Embed returned no episode list');return {season,episodes:data.episodes.slice(0,1000).filter(e=>Number.isInteger(e.episode_number)&&e.episode_number>0&&e.episode_number<=9999).map(e=>({number:e.episode_number,name:safeText(e.name,200)||'Episode '+e.episode_number,overview:safeText(e.overview,1000),still:posterURL(e.still),url:buildTwoEmbedURL({mediaType:'tv',mediaId:id,season,episode:e.episode_number})}))};}
export function readMedia(value){try{const data=JSON.parse(value||'null');return data&&data.provider==='2embed'&&['movie','tv'].includes(data.type)?data:null}catch{return null}}
export function defaultTVSeason(item){return item.seasons?.find(s=>s.number===1)?.number??item.seasons?.find(s=>s.number>0)?.number??item.seasons?.[0]?.number??1}

export function catalogueItem(media){return {id:'2embed-live-'+media.type+'-'+media.id,title:media.title,category:media.type==='tv'?'TV Shows':'Movies',kind:'link',url:buildTwoEmbedURL({mediaType:media.type,mediaId:media.id,season:defaultTVSeason(media),episode:1}),media};}
export function isDuloURL(value){try{const host=new URL(value).hostname;return host==='dulo.mov'||host.endsWith('.dulo.mov')}catch{return false}}
