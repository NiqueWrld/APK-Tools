package com.apkanalyser

import android.app.Application

class APKAnalyserApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
    
    companion object {
        lateinit var instance: APKAnalyserApp
            private set
    }
}
