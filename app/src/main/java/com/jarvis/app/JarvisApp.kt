package com.jarvis.app

import android.app.Application
import com.jarvis.agent.tool.ToolRegistration
import com.jarvis.app.config.ApiConfig

class JarvisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Load persisted custom API key and provider
        ApiConfig.load(this)

        // Register all core device, accessibility, and system tools
        ToolRegistration.registerAll(this)
    }
}
