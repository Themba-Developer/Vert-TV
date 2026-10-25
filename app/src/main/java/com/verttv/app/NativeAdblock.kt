package com.verttv.app

/** JNI interface to the bundled, pinned Brave adblock-rust engine. */
internal object NativeAdblock {
    val available: Boolean = try { System.loadLibrary("vert_adblock");true } catch (_: UnsatisfiedLinkError) { false } catch (_: SecurityException) { false }
    external fun initialize(rules: String): Boolean
    external fun shouldBlock(url: String,source: String,kind: String,method: String): Boolean
    external fun cosmeticRules(url: String,classes: String,ids: String): String?
}
