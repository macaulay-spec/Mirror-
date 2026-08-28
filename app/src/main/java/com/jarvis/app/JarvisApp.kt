package com.jarvis.app

import android.app.Application
import com.jarvis.agent.tool.ToolRegistration
import com.jarvis.app.config.ApiConfig
import com.jarvis.app.config.AssistantPrefs
import com.jarvis.app.people.PeopleGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class JarvisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Load persisted custom API key and provider
        ApiConfig.load(this)
        AssistantPrefs.load(this)

        // Register all core device, accessibility, and system tools
        ToolRegistration.registerAll(this)

        // Import the address book so JARVIS already knows who your people are.
        // It only ever asks about a person it genuinely cannot resolve.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { PeopleGraph.syncFromContacts(this@JarvisApp) }
        }
    }
}
