package com.applens.inspector

import android.app.Application
import com.applens.inspector.data.ShizukuBridge

class AppLensApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuBridge.init(this)
    }
}
