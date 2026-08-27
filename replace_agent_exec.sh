#!/bin/bash
sed -i '103,118c\        // 2. Query Provider Router via AgentExecutor\n        val agentResult = agentExecutor.executeTask(systemPrompt, history, input, null, onChunk)\n        memoryManager.addConversation("jarvis", agentResult.reply)\n        return@withContext agentResult\n    }' app/src/main/java/com/jarvis/agent/ai/JarvisAIEngine.kt
