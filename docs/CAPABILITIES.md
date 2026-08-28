# Capabilities

Jarvis has an extensible `ToolRegistry` consisting of natural language actions:

- **Device Controls**: Battery status, Location, Connectivity, Time, Storage.
- **Applications**: Launch applications via implicit/explicit intents.
- **Communication**: Draft/Send SMS messages.
- **Notifications**: Read recent notifications, reply inline, dismiss notifications.
- **Web**: Search the web, open and read web pages.
- **Memory**: Store persistent facts and user preferences via Room DB.
- **Calendar**: Create events via Intent.

Tools report success or failure, allowing the AgentExecutor to verify execution and continue planning.
