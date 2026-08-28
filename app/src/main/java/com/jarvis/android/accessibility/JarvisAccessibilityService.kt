package com.jarvis.android.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jarvis.agent.tool.ToolDefinition
import com.jarvis.agent.tool.ToolRegistry
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JarvisAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isEnabled.value = true
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON
            notificationTimeout = 100
        }
        serviceInfo = info

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val buttonController = accessibilityButtonController
            buttonController.registerAccessibilityButtonCallback(object : android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: android.accessibilityservice.AccessibilityButtonController?) {
                    super.onClicked(controller)
                    toggleOverlay()
                }

                override fun onAvailabilityChanged(
                    controller: android.accessibilityservice.AccessibilityButtonController?,
                    available: Boolean
                ) {
                    super.onAvailabilityChanged(controller, available)
                }
            })
        }
    }

    fun toggleOverlay() {
        try {
            if (com.jarvis.android.overlay.JarvisFloatingOrbService.isRunning) {
                val stopIntent = Intent(this, com.jarvis.android.overlay.JarvisFloatingOrbService::class.java)
                stopService(stopIntent)
            } else {
                val startIntent = Intent(this, com.jarvis.android.overlay.JarvisFloatingOrbService::class.java)
                startService(startIntent)
            }
        } catch (_: Exception) {}
    }

    var currentPackageName: String = "unknown"
        private set

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()
        if (!pkg.isNullOrBlank()) {
            currentPackageName = pkg
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        _isEnabled.value = false
    }

    fun performGlobal(action: Int): Boolean = performGlobalAction(action)
    fun back(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun notificationShade(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun findTextOnScreen(): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val textList = mutableListOf<String>()
        extractText(root, textList)
        return textList
    }

    fun getScreenText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        collectText(root, sb, 0)
        return sb.toString().trim()
    }

    private fun collectText(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (depth > 40) return
        val text = node.text?.toString()
        val content = node.contentDescription?.toString()
        when {
            !text.isNullOrBlank() -> sb.append(text).append('\n')
            !content.isNullOrBlank() -> sb.append(content).append('\n')
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectText(it, sb, depth + 1) }
        }
    }

    fun clickText(text: String): Boolean {
        val node = rootInActiveWindow ?: return false
        val found = dfFind(node, text) ?: return false
        return found.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun setTextInField(marker: String, newText: String): Boolean {
        val node = rootInActiveWindow ?: return false
        val field = dfFindEditable(node, marker) ?: dfFindEditable(node, "") ?: return false
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun clickAt(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float): Boolean {
        val path = Path().apply { moveTo(fromX, fromY); lineTo(toX, toY) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 400))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun dfFind(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text, true) == true ||
            node.contentDescription?.toString()?.contains(text, true) == true
        ) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                dfFind(child, text)?.let { return it }
            }
        }
        return null
    }

    private fun dfFindEditable(node: AccessibilityNodeInfo, hint: String): AccessibilityNodeInfo? {
        if (node.isEditable) {
            val cur = node.text?.toString()
            if (hint.isBlank() || cur?.contains(hint, true) == true) return node
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                dfFindEditable(child, hint)?.let { return it }
            }
        }
        return null
    }

    private fun extractText(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) {
            list.add(text)
        }
        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrBlank() && desc != text) {
            list.add(desc)
        }
        for (i in 0 until node.childCount) {
            extractText(node.getChild(i), list)
        }
    }

    fun getStructuredScreenData(): List<Map<String, Any>> {
        val root = rootInActiveWindow ?: return emptyList()
        val elements = mutableListOf<Map<String, Any>>()
        collectStructuredNodes(root, elements, 0)
        return elements
    }

    private fun collectStructuredNodes(node: AccessibilityNodeInfo, list: MutableList<Map<String, Any>>, depth: Int) {
        if (depth > 30) return
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        if (!text.isNullOrBlank() || !desc.isNullOrBlank() || node.isClickable || node.isEditable) {
            list.add(
                mapOf(
                    "text" to (text ?: ""),
                    "contentDescription" to (desc ?: ""),
                    "clickable" to node.isClickable,
                    "editable" to node.isEditable,
                    "className" to (node.className?.toString() ?: "")
                )
            )
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectStructuredNodes(it, list, depth + 1) }
        }
    }

    fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findScrollableNode(root) ?: root
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        return scrollable.performAction(action)
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findScrollableNode(child)?.let { return it }
            }
        }
        return null
    }

    fun clickElementByDescription(desc: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val found = dfFindByDescription(root, desc) ?: return false
        return found.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun dfFindByDescription(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString()?.contains(desc, true) == true) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                dfFindByDescription(child, desc)?.let { return it }
            }
        }
        return null
    }

    companion object {
        var instance: JarvisAccessibilityService? = null
            private set

        private val _isEnabled = MutableStateFlow(false)
        val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

        fun isServiceRunning(): Boolean = instance != null

        fun openSettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        fun registerTools() {
            // 1. screen_read
            ToolRegistry.register(
                ToolDefinition(
                    id = "screen_read",
                    name = "Read Screen Structure",
                    description = "Returns structured information about visible UI elements, text, content descriptions, clickable and editable states.",
                    category = "SCREEN",
                    riskLevel = RiskLevel.LEVEL_0
                ) { _, _ ->
                    val service = instance
                    if (service == null) {
                        ToolExecutionResult(
                            toolId = "screen_read",
                            success = false,
                            data = null,
                            error = "PERMISSION_REQUIRED: Jarvis Accessibility Service is not enabled."
                        )
                    } else {
                        val elements = service.getStructuredScreenData()
                        ToolExecutionResult(
                            toolId = "screen_read",
                            success = true,
                            data = mapOf("elements" to elements),
                            verificationDetails = "SUCCESS: Read ${elements.size} structured screen elements."
                        )
                    }
                }
            )

            // 2. find_text
            ToolRegistry.register(
                ToolDefinition(
                    id = "find_text",
                    name = "Find Text on Screen",
                    description = "Searches for specific text or content description on the active screen.",
                    category = "SCREEN",
                    riskLevel = RiskLevel.LEVEL_0
                ) { _, args ->
                    val service = instance
                    val query = args["query"]?.toString() ?: args["text"]?.toString() ?: ""
                    if (service == null) {
                        ToolExecutionResult(
                            toolId = "find_text",
                            success = false,
                            data = null,
                            error = "PERMISSION_REQUIRED: Accessibility Service disabled."
                        )
                    } else {
                        val texts = service.findTextOnScreen()
                        val found = texts.any { it.contains(query, true) }
                        ToolExecutionResult(
                            toolId = "find_text",
                            success = found,
                            data = mapOf("query" to query, "found" to found, "matches" to texts.filter { it.contains(query, true) }),
                            verificationDetails = if (found) "SUCCESS: Found '$query' on screen." else "NOT_FOUND: '$query' not found."
                        )
                    }
                }
            )

            // 3. click_element
            ToolRegistry.register(
                ToolDefinition(
                    id = "click_element",
                    name = "Click Screen Element",
                    description = "Finds and clicks an interactive UI element by text or content description.",
                    category = "DEVICE",
                    riskLevel = RiskLevel.LEVEL_1
                ) { _, args ->
                    val service = instance
                    val target = args["text"]?.toString() ?: args["target"]?.toString() ?: ""
                    if (service == null) {
                        ToolExecutionResult(
                            toolId = "click_element",
                            success = false,
                            data = null,
                            error = "PERMISSION_REQUIRED: Accessibility Service disabled."
                        )
                    } else {
                        val clickedText = service.clickText(target)
                        val clickedDesc = if (!clickedText) service.clickElementByDescription(target) else true
                        val success = clickedText || clickedDesc
                        ToolExecutionResult(
                            toolId = "click_element",
                            success = success,
                            data = mapOf("target" to target),
                            verificationDetails = if (success) "SUCCESS: Clicked element '$target'." else "NOT_FOUND: Element '$target' could not be clicked."
                        )
                    }
                }
            )

            // 4. type_text
            ToolRegistry.register(
                ToolDefinition(
                    id = "type_text",
                    name = "Type Text into Field",
                    description = "Enters text into an editable input field identified by hint or marker.",
                    category = "DEVICE",
                    riskLevel = RiskLevel.LEVEL_1
                ) { _, args ->
                    val service = instance
                    val marker = args["marker"]?.toString() ?: args["hint"]?.toString() ?: ""
                    val text = args["text"]?.toString() ?: args["content"]?.toString() ?: ""
                    if (service == null) {
                        ToolExecutionResult(
                            toolId = "type_text",
                            success = false,
                            data = null,
                            error = "PERMISSION_REQUIRED: Accessibility Service disabled."
                        )
                    } else {
                        val success = service.setTextInField(marker, text)
                        ToolExecutionResult(
                            toolId = "type_text",
                            success = success,
                            data = mapOf("marker" to marker, "text" to text),
                            verificationDetails = if (success) "SUCCESS: Typed '$text' into field." else "FAILED: Could not find editable field for '$marker'."
                        )
                    }
                }
            )

            // 5. scroll
            ToolRegistry.register(
                ToolDefinition(
                    id = "scroll",
                    name = "Scroll Screen",
                    description = "Scrolls the screen forward or backward.",
                    category = "DEVICE",
                    riskLevel = RiskLevel.LEVEL_1
                ) { _, args ->
                    val service = instance
                    val direction = args["direction"]?.toString()?.lowercase() ?: "forward"
                    val forward = direction != "backward" && direction != "back"
                    if (service == null) {
                        ToolExecutionResult(
                            toolId = "scroll",
                            success = false,
                            data = null,
                            error = "PERMISSION_REQUIRED: Accessibility Service disabled."
                        )
                    } else {
                        val success = service.scroll(forward)
                        ToolExecutionResult(
                            toolId = "scroll",
                            success = success,
                            data = mapOf("direction" to direction),
                            verificationDetails = if (success) "SUCCESS: Scrolled $direction." else "FAILED: Screen is not scrollable or scroll failed."
                        )
                    }
                }
            )

            // 6. tap
            ToolRegistry.register(
                ToolDefinition(
                    id = "tap",
                    name = "Coordinate Tap",
                    description = "Performs a tap gesture at specific screen coordinates (x, y).",
                    category = "DEVICE",
                    riskLevel = RiskLevel.LEVEL_1
                ) { _, args ->
                    val service = instance
                    val x = args["x"]?.toString()?.toFloatOrNull() ?: 0f
                    val y = args["y"]?.toString()?.toFloatOrNull() ?: 0f
                    if (service == null) {
                        ToolExecutionResult(
                            toolId = "tap",
                            success = false,
                            data = null,
                            error = "PERMISSION_REQUIRED: Accessibility Service disabled."
                        )
                    } else {
                        val success = service.clickAt(x, y)
                        ToolExecutionResult(
                            toolId = "tap",
                            success = success,
                            data = mapOf("x" to x, "y" to y),
                            verificationDetails = if (success) "SUCCESS: Tapped at ($x, $y)." else "FAILED: Coordinate tap failed."
                        )
                    }
                }
            )

            // 7. swipe
            ToolRegistry.register(
                ToolDefinition(
                    id = "swipe",
                    name = "Gesture Swipe",
                    description = "Performs a swipe gesture from (fromX, fromY) to (toX, toY).",
                    category = "DEVICE",
                    riskLevel = RiskLevel.LEVEL_1
                ) { _, args ->
                    val service = instance
                    val fx = args["fromX"]?.toString()?.toFloatOrNull() ?: 0f
                    val fy = args["fromY"]?.toString()?.toFloatOrNull() ?: 0f
                    val tx = args["toX"]?.toString()?.toFloatOrNull() ?: 0f
                    val ty = args["toY"]?.toString()?.toFloatOrNull() ?: 0f
                    if (service == null) {
                        ToolExecutionResult(
                            toolId = "swipe",
                            success = false,
                            data = null,
                            error = "PERMISSION_REQUIRED: Accessibility Service disabled."
                        )
                    } else {
                        val success = service.swipe(fx, fy, tx, ty)
                        ToolExecutionResult(
                            toolId = "swipe",
                            success = success,
                            data = mapOf("fromX" to fx, "fromY" to fy, "toX" to tx, "toY" to ty),
                            verificationDetails = if (success) "SUCCESS: Swiped from ($fx, $fy) to ($tx, $ty)." else "FAILED: Swipe gesture failed."
                        )
                    }
                }
            )

            // 8. press_back
            ToolRegistry.register(
                ToolDefinition(
                    id = "press_back",
                    name = "Press Back",
                    description = "Performs global back action.",
                    category = "DEVICE",
                    riskLevel = RiskLevel.LEVEL_1
                ) { _, _ ->
                    val service = instance
                    if (service == null) {
                        ToolExecutionResult(toolId = "press_back", success = false, data = null, error = "PERMISSION_REQUIRED")
                    } else {
                        val success = service.back()
                        ToolExecutionResult(toolId = "press_back", success = success, data = null, verificationDetails = if (success) "SUCCESS: Pressed back." else "FAILED")
                    }
                }
            )

            // 9. press_home
            ToolRegistry.register(
                ToolDefinition(
                    id = "press_home",
                    name = "Press Home",
                    description = "Performs global home action.",
                    category = "DEVICE",
                    riskLevel = RiskLevel.LEVEL_1
                ) { _, _ ->
                    val service = instance
                    if (service == null) {
                        ToolExecutionResult(toolId = "press_home", success = false, data = null, error = "PERMISSION_REQUIRED")
                    } else {
                        val success = service.home()
                        ToolExecutionResult(toolId = "press_home", success = success, data = null, verificationDetails = if (success) "SUCCESS: Pressed home." else "FAILED")
                    }
                }
            )

            // 10. open_recents
            ToolRegistry.register(
                ToolDefinition(
                    id = "open_recents",
                    name = "Open Recents",
                    description = "Opens recent apps overview.",
                    category = "DEVICE",
                    riskLevel = RiskLevel.LEVEL_1
                ) { _, _ ->
                    val service = instance
                    if (service == null) {
                        ToolExecutionResult(toolId = "open_recents", success = false, data = null, error = "PERMISSION_REQUIRED")
                    } else {
                        val success = service.recents()
                        ToolExecutionResult(toolId = "open_recents", success = success, data = null, verificationDetails = if (success) "SUCCESS: Opened recents." else "FAILED")
                    }
                }
            )

            // Keep legacy device_navigate_global as well
            ToolRegistry.register(
                ToolDefinition(
                    id = "device_navigate_global",
                    name = "Global System Navigation",
                    description = "Performs global navigation actions: back, home, recents, or notifications.",
                    category = "DEVICE",
                    riskLevel = RiskLevel.LEVEL_1
                ) { _, args ->
                    val service = instance
                    val actionStr = args["action"]?.toString()?.lowercase() ?: "home"
                    if (service == null) {
                        ToolExecutionResult(
                            toolId = "device_navigate_global",
                            success = false,
                            data = null,
                            error = "Accessibility service required for global navigation."
                        )
                    } else {
                        val globalAction = when (actionStr) {
                            "back" -> GLOBAL_ACTION_BACK
                            "home" -> GLOBAL_ACTION_HOME
                            "recents" -> GLOBAL_ACTION_RECENTS
                            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
                            else -> GLOBAL_ACTION_HOME
                        }
                        val success = service.performGlobal(globalAction)
                        ToolExecutionResult(
                            toolId = "device_navigate_global",
                            success = success,
                            data = mapOf("action" to actionStr),
                            verificationDetails = "Navigation action '$actionStr' performed."
                        )
                    }
                }
            )
        }
    }
}
