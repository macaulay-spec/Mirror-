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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.app.memory.AppDatabase
import com.jarvis.app.memory.PersonEntity
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.GlassCard
import com.jarvis.core.ui.HudBackground
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Memory & People — v3 carbon copy of mockup 05.
 *
 * "Your People": horizontally scrolling contact cards (initials avatar in a
 * glowing ring, relationship chip, notes) backed by the real PeopleGraph
 * Room table. "What JARVIS remembers": a timeline of memory entries with a
 * cyan connector line and delete actions, backed by the real memory table.
 */
@Composable
fun MemoryPeopleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()

    val people by db.personDao().allFlow().collectAsState(initial = emptyList())
    val memories by db.memoryDao().all().collectAsState(initial = emptyList())

    val dateFormat = remember { SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.VoidBlack)
    ) {
        HudBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = JarvisColors.TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Memory",
                    color = JarvisColors.TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ── Your People ────────────────────────────────────────
                item {
                    Text(
                        text = "Your People",
                        color = JarvisColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                if (people.isEmpty()) {
                    item {
                        GlassCard(
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = JarvisColors.SurfaceGlass
                        ) {
                            Text(
                                text = "Contacts will appear here once JARVIS can read them. Grant Contacts access in Settings.",
                                color = JarvisColors.TextMuted,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                } else {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(people, key = { it.id }) { person ->
                                PersonCard(person)
                            }
                        }
                    }
                }

                // ── What JARVIS remembers ─────────────────────────────
                item {
                    Text(
                        text = "What JARVIS remembers",
                        color = JarvisColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                    )
                }

                if (memories.isEmpty()) {
                    item {
                        GlassCard(
                            shape = RoundedCornerShape(16.dp),
                            backgroundColor = JarvisColors.SurfaceGlass
                        ) {
                            Text(
                                text = "Nothing yet. Tell JARVIS to \"remember\" something and it will show up here.",
                                color = JarvisColors.TextMuted,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                } else {
                    items(memories, key = { it.id }) { memory ->
                        MemoryRow(
                            text = memory.content,
                            type = memory.type,
                            timestamp = dateFormat.format(Date(memory.createdAt)),
                            onDelete = {
                                scope.launch {
                                    try { db.memoryDao().delete(memory) } catch (_: Exception) {}
                                }
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(18.dp)) }
            }
        }
    }
}

// ── Cards ──────────────────────────────────────────────────────────────────

@Composable
private fun PersonCard(person: PersonEntity) {
    GlassCard(
        shape = RoundedCornerShape(18.dp),
        backgroundColor = JarvisColors.SurfaceGlassElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .width(150.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Initials avatar in a glowing ring
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(JarvisColors.SurfaceGlass)
                    .border(1.dp, JarvisColors.Presence.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initialsOf(person.displayName),
                    color = JarvisColors.Presence,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = person.displayName,
                color = JarvisColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (person.relationship.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(JarvisColors.Presence.copy(alpha = 0.12f))
                        .border(0.5.dp, JarvisColors.Presence.copy(alpha = 0.35f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = person.relationship.replaceFirstChar { it.uppercase() },
                        color = JarvisColors.Presence,
                        fontSize = 10.sp
                    )
                }
            }
            if (person.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = person.notes,
                    color = JarvisColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MemoryRow(
    text: String,
    type: String,
    timestamp: String,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cyan connector dot + line
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(JarvisColors.Presence)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(JarvisColors.Hairline)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))

        GlassCard(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            backgroundColor = JarvisColors.SurfaceGlassCyan
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = text,
                        color = JarvisColors.TextPrimary,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "$timestamp · ${type.replaceFirstChar { it.uppercase() }}",
                        color = JarvisColors.TextMuted,
                        fontSize = 10.sp
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Forget",
                        tint = JarvisColors.TextMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

private fun initialsOf(name: String): String =
    name.split(Regex("\\s+"))
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .take(2)
        .joinToString("")
        .ifBlank { "?" }
