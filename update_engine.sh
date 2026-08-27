#!/bin/bash
sed -i 's/val memoryManager = JarvisMemoryManager(context)/val memoryManager = JarvisMemoryManager(context)\n    private val agentExecutor = AgentExecutor(context, providerRouter, memoryManager)/' app/src/main/java/com/jarvis/agent/ai/JarvisAIEngine.kt

sed -i 's/When a user requests an action, analyze the request, invoke the correct tool, inspect the structured result, and respond concisely. If the user refers to the current app or screen context (e.g. "search for John", "send him this"), use the session and accessibility context./To use a tool, you MUST reply with a JSON object ONLY in this exact format:\n                {\n                    "action": "tool_call",\n                    "tool": "tool_name",\n                    "arguments": { "key": "value" },\n                    "expectedResult": "what you expect"\n                }\n                If you are just answering the user or have finished executing tools, reply with:\n                {\n                    "action": "reply",\n                    "message": "your final message to the user"\n                }\n                Analyze the request, use tools if needed, observe the result, and respond concisely./g' app/src/main/java/com/jarvis/agent/ai/JarvisAIEngine.kt

sed -i '102,119c\
        // 2. Query Provider Router via AgentExecutor\
        val agentResult = agentExecutor.executeTask(systemPrompt, history, input, null, onChunk)\
        memoryManager.addConversation("jarvis", agentResult.reply)\
        return@withContext agentResult
' app/src/main/java/com/jarvis/agent/ai/JarvisAIEngine.kt
