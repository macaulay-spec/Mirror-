package com.jarvis.app.dialogue

import com.jarvis.app.memory.PersonEntity

class EntityMemory {
    var lastContact: PersonEntity? = null
    var lastApp: String? = null
    var lastNumberType: String? = null
    var lastMessageContent: String? = null

    fun clear() {
        lastContact = null
        lastApp = null
        lastNumberType = null
        lastMessageContent = null
    }
}
