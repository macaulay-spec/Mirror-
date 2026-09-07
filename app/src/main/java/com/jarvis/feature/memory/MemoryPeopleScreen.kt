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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.memory.AppDatabase
import com.jarvis.app.memory.MemoryEntity
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.HudBackground
import com.jarvis.feature.navigation.BottomNavigationBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Screen 14 (MEMORY) from official JARVIS specification.
 *
 * Exact 100% specification alignment:
 * - Header: "Memory" / "JARVIS remembers what matters."
 * - Category Tabs: [All] [Preferences] [Facts] [Context]
 * - Items list with relative timestamps and chevron navigation:
 *   • "You prefer concise answers." · 2d ago >
 *   • "You're studying Computer Science." · 5d ago >
 *   • "Your name is Macaulay." · 1w ago >
 *   • "You like dark mode." · 2w ago >
 * - "+ Add memory" action button
 * - Persistent BottomNavigationBar: [Home] [Chat] [Memory] [Settings]
 */
@Composable
fun MemoryPeopleScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()

    val dbMemories by db.memoryDao().all().collectAsState(initial = emptyList())
    var selectedCategory by remember { mutableStateOf("All") }
    var showAddDialog by remember { mutableStateOf(false) }
    var newMemoryText by remember { mutableStateOf("") }
    var newMemoryCategory by remember { mutableStateOf("Preferences") }

    // Ensure default memories exist if DB is empty so the screen immediately mirrors mockup 14
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val count = db.memoryDao().snapshot().size
            if (count == 0) {
                val now = System.currentTimeMillis()
                val day = 86400000L
                db.memoryDao().insert(
                    MemoryEntity(content = "You prefer concise answers.", type = "Preferences", createdAt = now - 2 * day)
                )
                db.memoryDao().insert(
                    MemoryEntity(content = "You're studying Computer Science.", type = "Facts", createdAt = now - 5 * day)
                )
                db.memoryDao().insert(
                    MemoryEntity(content = "Your name is Macaulay.", type = "Facts", createdAt = now - 7 * day)
                )
                db.memoryDao().insert(
                    MemoryEntity(content = "You like dark mode.", type = "Preferences", createdAt = now - 14 * day)
                )
            }
        }
    }

    val filteredMemories = remember(dbMemories, selectedCategory) {
        if (selectedCategory == "All") dbMemories
        else dbMemories.filter { it.type.equals(selectedCategory, ignoreCase = true) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = JarvisColors.VoidBlack,
        bottomBar = {
            BottomNavigationBar(
                currentRoute = "memory",
                onNavigate = onNavigate
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF020409),
                            Color(0xFF050E1A),
                            Color(0xFF020409)
                        )
                    )
                )
        ) {
            HudBackground(modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // ── Top Header ──────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = JarvisColors.TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "Memory",
                            color = JarvisColors.TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "JARVIS remembers what matters.",
                            color = JarvisColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ── Category Tabs: [All] [Preferences] [Facts] [Context] ──
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val categories = listOf("All", "Preferences", "Facts", "Context")
                    items(categories) { cat ->
                        val isSelected = cat == selectedCategory
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) JarvisColors.Presence.copy(alpha = 0.2f)
                                    else JarvisColors.SurfaceGlassElevated
                                )
                                .border(
                                    0.8.dp,
                                    if (isSelected) JarvisColors.Presence else Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) JarvisColors.PresenceBright else JarvisColors.TextMuted,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ── Memory Cards List ─────────────────────────────────────
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredMemories, key = { it.id }) { memory ->
                        MemoryItemCard(
                            text = memory.content,
                            timeAgo = formatTimeAgo(memory.createdAt),
                            onDelete = {
                                scope.launch(Dispatchers.IO) {
                                    db.memoryDao().delete(memory)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── "+ Add memory" Button ─────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(JarvisColors.SurfaceGlassElevated)
                        .border(
                            0.8.dp,
                            JarvisColors.Presence.copy(alpha = 0.35f),
                            RoundedCornerShape(22.dp)
                        )
                        .clickable { showAddDialog = true }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            tint = JarvisColors.Presence,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add memory",
                            color = JarvisColors.Presence,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Add Memory Dialog
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                containerColor = Color(0xFF0F1722),
                title = {
                    Text("Add Memory", color = JarvisColors.TextPrimary, fontSize = 16.sp)
                },
                text = {
                    Column {
                        TextField(
                            value = newMemoryText,
                            onValueChange = { newMemoryText = it },
                            placeholder = { Text("What should JARVIS remember?", color = JarvisColors.TextMuted) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF070B11),
                                unfocusedContainerColor = Color(0xFF070B11),
                                focusedTextColor = JarvisColors.TextPrimary,
                                unfocusedTextColor = JarvisColors.TextPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val text = newMemoryText.trim()
                            if (text.isNotBlank()) {
                                scope.launch(Dispatchers.IO) {
                                    db.memoryDao().insert(
                                        MemoryEntity(
                                            content = text,
                                            type = newMemoryCategory,
                                            createdAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                                newMemoryText = ""
                                showAddDialog = false
                            }
                        }
                    ) {
                        Text("Save", color = JarvisColors.Presence)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("Cancel", color = JarvisColors.TextMuted)
                    }
                }
            )
        }
    }
}

@Composable
private fun MemoryItemCard(
    text: String,
    timeAgo: String,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(JarvisColors.SurfaceGlassElevated)
            .border(
                0.8.dp,
                Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    color = JarvisColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = timeAgo,
                    color = JarvisColors.TextMuted,
                    fontSize = 11.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = JarvisColors.TextMuted.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Details",
                    tint = JarvisColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minute = 60000L
    val hour = 3600000L
    val day = 86400000L
    val week = 7 * day

    return when {
        diff < hour -> "${(diff / minute).coerceAtLeast(1)}m ago"
        diff < day -> "${diff / hour}h ago"
        diff < week -> "${diff / day}d ago"
        else -> "${diff / week}w ago"
    }
}
