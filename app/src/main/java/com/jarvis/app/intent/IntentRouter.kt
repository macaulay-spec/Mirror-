package com.jarvis.app.intent

object IntentRouter {

    fun route(utterance: String): Intent {
        val lower = utterance.lowercase().trim()
        val tokens = lower.split(Regex("\\s+"))

        // Calls
        if (lower.startsWith("call ") || lower.startsWith("dial ") || lower.startsWith("phone ")) {
            val contact = lower.removePrefix("call ").removePrefix("dial ").removePrefix("phone ").trim()
            if (contact.isNotBlank()) {
                // Extract number type if present
                var type: String? = null
                var cleanContact = contact
                if (contact.endsWith(" on mobile")) { type = "mobile"; cleanContact = contact.removeSuffix(" on mobile") }
                else if (contact.endsWith(" on home")) { type = "home"; cleanContact = contact.removeSuffix(" on home") }
                else if (contact.endsWith(" on work")) { type = "work"; cleanContact = contact.removeSuffix(" on work") }
                return Intent.CallPerson(contact = cleanContact, numberType = type)
            }
        }

        // Messaging
        if (lower.startsWith("text ") || lower.startsWith("message ") || lower.startsWith("send a message to ")) {
            val afterVerb = lower
                .removePrefix("send a message to ")
                .removePrefix("text ")
                .removePrefix("message ")
                .trim()
            
            // basic parsing: "text [contact] [that/saying] [body]"
            val parts = afterVerb.split(" that ", " saying ", limit = 2)
            if (parts.size == 2) {
                return Intent.SendMessage(contact = parts[0].trim(), body = parts[1].trim())
            }
            return Intent.SendMessage(contact = afterVerb)
        }

        if (lower == "read my messages" || lower == "read messages" || lower.startsWith("do i have any messages")) {
            return Intent.ReadMessages()
        }

        // Open Apps
        if (lower.startsWith("open ") || lower.startsWith("launch ")) {
            val app = lower.removePrefix("open ").removePrefix("launch ").trim()
            return Intent.OpenApp(appName = app)
        }

        // Toggles
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            val state = if (lower.contains("on")) true else if (lower.contains("off")) false else null
            return Intent.ToggleSetting("wifi", state)
        }
        if (lower.contains("bluetooth")) {
            val state = if (lower.contains("on")) true else if (lower.contains("off")) false else null
            return Intent.ToggleSetting("bluetooth", state)
        }
        if (lower.contains("flashlight") || lower.contains("torch")) {
            val state = if (lower.contains("on")) true else if (lower.contains("off")) false else null
            return Intent.ToggleSetting("flashlight", state)
        }

        // Volume
        if (lower.contains("volume")) {
            val direction = if (lower.contains("up") || lower.contains("increase")) "up"
                            else if (lower.contains("down") || lower.contains("decrease")) "down"
                            else if (lower.contains("mute")) "mute"
                            else null
            return Intent.SetVolume(direction = direction)
        }

        // Navigate
        if (lower.startsWith("take me to ") || lower.startsWith("navigate to ")) {
            val dest = lower.removePrefix("take me to ").removePrefix("navigate to ").trim()
            return Intent.Navigate(destination = dest)
        }

        // Fallback to LLM
        return Intent.Unknown(utterance)
    }
}
