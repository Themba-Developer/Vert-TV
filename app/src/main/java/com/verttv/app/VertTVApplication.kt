package com.verttv.app

class VertTVApplication : android.app.Application() {
    override fun onCreate(){super.onCreate();AdBlockEngine.initialize(this)}
}
