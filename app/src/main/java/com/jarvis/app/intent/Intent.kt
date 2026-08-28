package com.jarvis.app.intent

sealed interface Intent {
    data class CallPerson(
        var contact: String? = null,
        var numberType: String? = null,
        var confirm: Boolean = false
    ) : Intent

    data class SendMessage(
        var contact: String? = null,
        var body: String? = null,
        var confirm: Boolean = false
    ) : Intent

    data class OpenApp(
        var appName: String? = null,
        var nickname: String? = null
    ) : Intent

    data class SetVolume(
        var level: Int? = null,
        var direction: String? = null // "up", "down", "mute"
    ) : Intent

    data class Navigate(
        var destination: String? = null,
        var saveAs: String? = null
    ) : Intent

    data class ToggleSetting(
        var setting: String, // "wifi", "bluetooth", "flashlight", "dnd"
        var state: Boolean? = null // true for on, false for off, null for toggle
    ) : Intent
    
    data class ReadMessages(
        var appFilter: String? = null
    ) : Intent

    data class Unknown(val raw: String) : Intent // Hand off to LLM
}
