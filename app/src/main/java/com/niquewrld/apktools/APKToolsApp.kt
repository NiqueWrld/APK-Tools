package com.niquewrld.apktools

import android.app.Application

class APKToolsApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    companion object {
        lateinit var instance: APKToolsApp
            private set
    }
}
