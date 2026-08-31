package com.jarvis.feature.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.GlassCard

/**
 * Memory Screen — view and manage JARVIS memories.
 *
 * Design: calm, precise, alive.
 * - List of memories with timestamps
 * - Search/filter capability
 * - Add new memory button
 * - Delete memory option
 */
@Composable
fun MemoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }

    // Sample memories (in real app, this would come from Room DB)
    val memories = remember {
        listOf(
            MemoryItem(
                id = "1",
                content = "You prefer concise answers.",
                category = "Preference",
                timestamp = "2h ago"
            ),
            MemoryItem(
                id = "2",
                content = "You're studying Computer Science.",
                category = "Fact",
                timestamp = "5d ago"
            ),
            MemoryItem(
                id = "3",
                content = "Your name is Macaulay.",
                category = "Context",
                timestamp = "1w ago"
            ),
            MemoryItem(
                id = "4",
                content = "You like dark mode.",
                category = "Preference",
                timestamp = "2w ago"
            )
        )
    }

    val filteredMemories = remember(searchQuery) {
        if (searchQuery.isBlank()) memories
        else memories.filter {
            it.content.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0B0F17),
                        Color(0xFF0E1420),
                        Color(0xFF0B0F17)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = JarvisColors.TextSecondary,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { onBack() }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Memory",
                    color = JarvisColors.TextPrimary,
                    fontSize = 24.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "JARVIS remembers what matters.",
                color = JarvisColors.TextSecondary,
                fontSize = 15.sp,
                fontFamily = FontFamily.Default
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Search bar
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = JarvisColors.TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search memories...",
                                color = JarvisColors.TextMuted,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Default
                            )
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = JarvisColors.TextPrimary,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Default
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(JarvisColors.Presence),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Memory tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Preferences", "Facts", "Context").forEach { tab ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(JarvisColors.SurfaceGlass)
                            .border(0.5.dp, JarvisColors.Hairline, RoundedCornerShape(14.dp))
                            .clickable { searchQuery = if (tab == "All") "" else tab }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = tab,
                            color = if (searchQuery == tab || (tab == "All" && searchQuery.isEmpty()))
                                JarvisColors.Presence else JarvisColors.TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Default
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Memory list
            if (filteredMemories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No memories found.",
                        color = JarvisColors.TextMuted,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Default
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredMemories) { memory ->
                        MemoryItem(
                            memory = memory,
                            onDelete = { /* TODO: delete from DB */ }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Add memory button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(JarvisColors.Presence)
                    .clickable { /* TODO: open add memory dialog */ }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = JarvisColors.VoidBlack,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add memory",
                        color = JarvisColors.VoidBlack,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

private data class MemoryItem(
    val id: String,
    val content: String,
    val category: String,
    val timestamp: String
)

@Composable
private fun MemoryItem(
    memory: MemoryItem,
    onDelete: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Category badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(getCategoryColor(memory.category).copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = memory.category,
                    color = getCategoryColor(memory.category),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = memory.content,
                    color = JarvisColors.TextPrimary,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Default,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = memory.timestamp,
                    color = JarvisColors.TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Default
                )
            }

            // Delete button
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = JarvisColors.TextMuted,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onDelete() }
            )
        }
    }
}

@Composable
private fun getCategoryColor(category: String): Color {
    return when (category) {
        "Preference" -> JarvisColors.Presence
        "Fact" -> JarvisColors.Warmth
        "Context" -> JarvisColors.StateSuccess
        else -> JarvisColors.TextSecondary
    }
}
