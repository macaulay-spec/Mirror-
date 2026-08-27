package com.jarvis.app.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class JarvisNotification(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val sender: String = "",
    val fullContent: String = "",
    val key: String,
    val timestamp: Long,
    val hasReplyAction: Boolean = false,
    var isActive: Boolean = true
)

object NotificationRepository {
    private const val MAX_HISTORY = 50
    private val _all = MutableStateFlow<List<JarvisNotification>>(emptyList())
    val all: StateFlow<List<JarvisNotification>> = _all

    fun updateActive(activeItems: List<JarvisNotification>) {
        val current = _all.value.toMutableList()
        
        // Mark all existing as inactive
        current.forEachIndexed { index, n -> current[index] = n.copy(isActive = false) }
        
        // Add or update active items
        activeItems.forEach { activeItem ->
            val existingIndex = current.indexOfFirst { it.key == activeItem.key }
            if (existingIndex >= 0) {
                current[existingIndex] = activeItem
            } else {
                current.add(activeItem)
            }
        }
        
        // Sort by timestamp descending and keep only the latest MAX_HISTORY items
        current.sortByDescending { it.timestamp }
        _all.value = current.take(MAX_HISTORY)
    }

    fun clear() { _all.value = emptyList() }
    
    fun byApp(label: String): List<JarvisNotification> =
        _all.value.filter { it.appLabel.equals(label, ignoreCase = true) || it.packageName.contains(label, true) }
        
    fun latest(): JarvisNotification? = _all.value.maxByOrNull { it.timestamp }
    
    fun getByKey(key: String): JarvisNotification? = _all.value.find { it.key == key }
}
