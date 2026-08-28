package com.jarvis.agent.dialogue

/**
 * What JARVIS remembers about the current conversation, so pronouns work:
 * "call her back", "send it again", "open it".
 */
class EntityMemory {

    @Volatile var lastPersonName: String? = null
        private set

    @Volatile var lastPersonNumber: String? = null
        private set

    @Volatile var lastMessage: String? = null
        private set

    @Volatile var lastApp: String? = null
        private set

    fun notePerson(name: String?, number: String? = null) {
        if (!name.isNullOrBlank()) lastPersonName = name
        if (!number.isNullOrBlank()) lastPersonNumber = number
    }

    fun noteMessage(text: String?) {
        if (!text.isNullOrBlank()) lastMessage = text
    }

    fun noteApp(app: String?) {
        if (!app.isNullOrBlank()) lastApp = app
    }

    fun clear() {
        lastPersonName = null
        lastPersonNumber = null
        lastMessage = null
        lastApp = null
    }
}
