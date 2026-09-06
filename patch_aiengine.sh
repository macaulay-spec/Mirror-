#!/bin/bash
cat << 'INNER_EOF' > replacement.txt
                val nodes = a11y.getStructuredScreenData().take(20)
                val textSummary = if (nodes.isNotEmpty()) {
                    nodes.joinToString("\n                ") { n -> 
                        val txt = n["text"] as? String ?: ""
                        val desc = n["contentDescription"] as? String ?: ""
                        val clickable = n["clickable"] as? Boolean ?: false
                        val cx = n["centerX"] as? Float ?: 0f
                        val cy = n["centerY"] as? Float ?: 0f
                        "- ${if (txt.isNotBlank()) "Text: '$txt'" else "Desc: '$desc'"} [Clickable: $clickable, coords: ($cx, $cy)]"
                    }
                } else "No readable elements found"
INNER_EOF

sed -i '/val screenTexts = a11y.findTextOnScreen().take(25)/{
    r replacement.txt
    d
}' app/src/main/java/com/jarvis/agent/ai/JarvisAIEngine.kt
sed -i '/val textSummary = if (screenTexts.isNotEmpty()) screenTexts.joinToString(" | ") else "No text found"/d' app/src/main/java/com/jarvis/agent/ai/JarvisAIEngine.kt

