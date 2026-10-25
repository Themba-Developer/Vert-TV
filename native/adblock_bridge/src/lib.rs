// SPDX-License-Identifier: MPL-2.0
use adblock::{Engine, lists::{FilterSet, ParseOptions}, request::Request};
use jni::{JNIEnv, objects::{JObject, JString}, sys::{jboolean, jstring, JNI_FALSE, JNI_TRUE}};
use std::{panic::{catch_unwind, AssertUnwindSafe}, sync::{Mutex, OnceLock}};

static ENGINE: OnceLock<Mutex<Option<Engine>>> = OnceLock::new();
fn state() -> &'static Mutex<Option<Engine>> { ENGINE.get_or_init(|| Mutex::new(None)) }
fn engine_from(rules: &str) -> Engine {
    let mut filters=FilterSet::new(false);
    filters.add_filter_list(rules.to_owned(),ParseOptions::default());
    Engine::new_with_filter_set(filters)
}
fn blocks(engine: &Engine,url: &str,source: &str,kind: &str,method: &str) -> bool {
    if url.len()>16384 || source.len()>16384 { return false; }
    Request::new(url,source,kind,method).map(|request|engine.check_network_request(&request).should_block()).unwrap_or(false)
}
fn strings(json: &str) -> Vec<String> {
    if json.len()>100_000 { return vec![]; }
    serde_json::from_str::<Vec<String>>(json).unwrap_or_default().into_iter().filter(|s|s.len()<=200).take(512).collect()
}
fn cosmetics(engine: &Engine,url: &str,classes: &str,ids: &str) -> String {
    if url.len()>16384 { return "[]".into(); }
    let resources=engine.url_cosmetic_resources(url);
    let mut selectors:Vec<String>=resources.hide_selectors.into_iter().collect();
    if !resources.generichide {
        selectors.extend(engine.hidden_class_id_selectors(strings(classes),strings(ids),&resources.exceptions));
    }
    selectors.sort(); selectors.dedup();
    selectors.retain(|s|s.len()<=2048);
    selectors.truncate(512);
    serde_json::to_string(&selectors).unwrap_or_else(|_|"[]".into())
}
fn read(env: &mut JNIEnv<'_>,value: &JString<'_>) -> Option<String> { env.get_string(value).ok().map(String::from) }

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_verttv_app_NativeAdblock_initialize(mut env: JNIEnv,_object: JObject,rules: JString) -> jboolean {
    catch_unwind(AssertUnwindSafe(|| {
        let Some(text)=read(&mut env,&rules) else {return JNI_FALSE};
        if text.len()>12*1024*1024 || text.lines().count()<10 {return JNI_FALSE;}
        let engine=engine_from(&text);
        if let Ok(mut guard)=state().lock(){*guard=Some(engine);JNI_TRUE}else{JNI_FALSE}
    })).unwrap_or(JNI_FALSE)
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_verttv_app_NativeAdblock_shouldBlock(mut env: JNIEnv,_object: JObject,url: JString,source: JString,kind: JString,method: JString) -> jboolean {
    catch_unwind(AssertUnwindSafe(|| {
        let (Some(url),Some(source),Some(kind),Some(method))=(read(&mut env,&url),read(&mut env,&source),read(&mut env,&kind),read(&mut env,&method)) else{return JNI_FALSE};
        if let Ok(guard)=state().lock(){if let Some(engine)=guard.as_ref(){if blocks(engine,&url,&source,&kind,&method){return JNI_TRUE}}}
        JNI_FALSE
    })).unwrap_or(JNI_FALSE)
}
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_verttv_app_NativeAdblock_cosmeticRules(mut env: JNIEnv,_object: JObject,url: JString,classes: JString,ids: JString) -> jstring {
    let result=catch_unwind(AssertUnwindSafe(|| {
        let (Some(url),Some(classes),Some(ids))=(read(&mut env,&url),read(&mut env,&classes),read(&mut env,&ids)) else{return "[]".into()};
        if let Ok(guard)=state().lock(){if let Some(engine)=guard.as_ref(){return cosmetics(engine,&url,&classes,&ids)}}
        "[]".into()
    })).unwrap_or_else(|_|"[]".into());
    env.new_string(result).map(|s|s.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn engine_is_safe_for_webview_threads(){fn send_sync<T:Send+Sync>(){}send_sync::<Engine>();}
    #[test] fn request_rules_keep_exceptions_and_resource_types(){
        let engine=engine_from("||ads.example^\n@@||ads.example/allowed.js$script\n||metrics.example^$third-party\n||video.example/ads/$media");
        assert!(blocks(&engine,"https://ads.example/banner.js","https://dulo.mov","script","get"));
        assert!(!blocks(&engine,"https://ads.example/allowed.js","https://dulo.mov","script","get"));
        assert!(blocks(&engine,"https://metrics.example/pixel","https://dulo.mov","image","get"));
        assert!(!blocks(&engine,"https://metrics.example/pixel","https://metrics.example","image","get"));
        assert!(blocks(&engine,"https://video.example/ads/intro.mp4","https://dulo.mov","media","get"));
        assert!(!blocks(&engine,"https://video.example/movie.mp4","https://dulo.mov","media","get"));
    }
    #[test] fn cosmetics_apply_domain_and_generic_exceptions(){
        let engine=engine_from("example.com##.sponsor\n##.ad-slot\nexample.com#@#.ad-slot");
        let selectors:Vec<String>=serde_json::from_str(&cosmetics(&engine,"https://example.com","[\"ad-slot\"]","[]")).unwrap();
        assert!(selectors.contains(&".sponsor".into()));assert!(!selectors.contains(&".ad-slot".into()));
        assert!(cosmetics(&engine,"https://other.example","[\"ad-slot\"]","[]").contains(".ad-slot"));
    }
    #[test] fn bundled_lists_block_ads_but_allow_app_payment_and_provider_pages(){
        let rules=[include_str!("../../../app/src/main/assets/adblock/easylist.txt"),include_str!("../../../app/src/main/assets/adblock/easyprivacy.txt"),include_str!("../../../app/src/main/assets/adblock/vert-rules.txt")].join("\n");
        let engine=engine_from(&rules);
        assert!(blocks(&engine,"https://googleads.g.doubleclick.net/pagead/id","https://dulo.mov","xmlhttprequest","get"));
        for (url,kind) in [("https://api.themoviedb.org/3/movie/popular","xmlhttprequest"),("https://image.tmdb.org/t/p/w342/poster.jpg","image")]{assert!(!blocks(&engine,url,"https://dulo.mov",kind,"get"),"Catalogue request must load: {url}");}
        for url in ["https://dulo.mov/","https://dulo.mov/watch/movie/609681","https://www.payfast.co.za/eng/process","https://payment.payfast.io/eng/process/payment/test","https://vert-tv.mahlangu843.workers.dev/api/get_links.json"] {
            assert!(!blocks(&engine,url,url,"document","get"),"Must allow {url}");
        }
    }
}
