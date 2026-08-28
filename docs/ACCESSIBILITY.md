# Accessibility Service

Jarvis uses `JarvisAccessibilityService` to interact with the Android UI on behalf of the user.

## Capabilities
- Read the active application's screen elements.
- Extract node metadata: `packageName`, `className`, `text`, `contentDescription`, `isClickable`, `bounds`, etc.
- Perform semantic actions: `Click`, `Scroll`, `Type`, `Global Back/Home/Recents`.

## Workflow
The Agent receives screen context, plans the required UI action, executes the matching accessibility tool (`click_element`, `scroll_screen`, etc.), and verifies success by examining the screen again.
