import sys

with open("app/src/main/java/com/jarvis/agent/ai/JarvisAIEngine.kt", "r") as f:
    lines = f.readlines()

new_lines = []
i = 0
while i < len(lines):
    if "if (deterministicResult != null) {" in lines[i]:
        new_lines.append(lines[i])
        i += 1
        # skip lines until we hit the recordToolExecution close
        while i < len(lines) and not "result = deterministicResult" in lines[i]:
            new_lines.append(lines[i])
            i += 1
        new_lines.append(lines[i])
        i += 1
        new_lines.append(lines[i]) # ")"
        i += 1
        new_lines.append("""            val reply = if (deterministicResult.success) {
                deterministicResult.verificationDetails ?: "Action executed successfully."
            } else {
                deterministicResult.error ?: "Action failed."
            }
            memoryManager.addConversation("jarvis", reply)
            return@withContext JarvisEngineResult(
                reply = reply,
                state = if (deterministicResult.success) JarvisState.SUCCESS else JarvisState.ERROR,
                toolResult = deterministicResult
            )
        }
""")
        break
    else:
        new_lines.append(lines[i])
        i += 1

while i < len(lines):
    if "// 2. Query Provider Router via AgentExecutor" in lines[i]:
        new_lines.append(lines[i])
        i += 1
        new_lines.append(lines[i]) # val agentResult
        i += 1
        new_lines.append(lines[i]) # memoryManager...
        i += 1
        new_lines.append(lines[i]) # return...
        i += 1
        new_lines.append(lines[i]) # }
        i += 1
        break
    i += 1

while i < len(lines):
    new_lines.append(lines[i])
    i += 1

with open("app/src/main/java/com/jarvis/agent/ai/JarvisAIEngine.kt", "w") as f:
    f.writelines(new_lines)

