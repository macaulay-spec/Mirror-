package com.jarvis.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.agent.orchestrator.AssistantOrchestrator
import com.jarvis.app.memory.AppDatabase
import com.jarvis.app.memory.ChatSessionEntity
import com.jarvis.app.memory.ConversationEntity
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.GlassCard
import com.jarvis.core.ui.HudBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
fun ChatHistoryScreen(
    orchestrator: AssistantOrchestrator? = null,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val scope = rememberCoroutineScope()

    val orchestratorSessions by orchestrator?.sessions?.collectAsState() ?: remember { mutableStateOf(emptyList()) }
    val currentOrchestratorSessionId by orchestrator?.currentSessionId?.collectAsState() ?: remember { mutableStateOf("") }

    var localSessions by remember { mutableStateOf<List<ChatSessionEntity>>(emptyList()) }
    val sessions = if (orchestrator != null) orchestratorSessions else localSessions

    var selectedSessionId by remember { mutableStateOf<String>("") }
    var sessionMessages by remember { mutableStateOf<List<ConversationEntity>>(emptyList()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val reloadSessions: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            val list = db.conversationDao().allSessions()
            withContext(Dispatchers.Main) {
                localSessions = list
                if (selectedSessionId.isEmpty() && list.isNotEmpty()) {
                    selectedSessionId = list.first().sessionId
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (orchestrator != null) {
            orchestrator.loadSessions()
            selectedSessionId = if (currentOrchestratorSessionId.isNotBlank()) {
                currentOrchestratorSessionId
            } else {
                orchestrator.sessions.value.firstOrNull()?.sessionId ?: ""
            }
        } else {
            reloadSessions()
        }
    }

    LaunchedEffect(sessions) {
        if (selectedSessionId.isEmpty() && sessions.isNotEmpty()) {
            selectedSessionId = sessions.first().sessionId
        }
    }

    LaunchedEffect(selectedSessionId) {
        if (selectedSessionId.isNotBlank()) {
            withContext(Dispatchers.IO) {
                val msgs = db.conversationDao().forSession(selectedSessionId)
                withContext(Dispatchers.Main) {
                    sessionMessages = msgs
                }
            }
        } else {
            sessionMessages = emptyList()
        }
    }

    val selectedSession = sessions.find { it.sessionId == selectedSessionId }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0A1520),
                        Color(0xFF0D1B24),
                        Color(0xFF0A1520)
                    )
                )
            )
    ) {
        HudBackground(
            modifier = Modifier.fillMaxSize(),
            glowColor = JarvisColors.Presence
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // ── Top Navigation Bar ──────────────────────────────────────────
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                backgroundColor = JarvisColors.SurfaceGlassElevated
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = JarvisColors.Presence,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "CHAT SESSIONS",
                                color = JarvisColors.TextPrimary,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Default,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = "${sessions.size} saved conversations",
                                color = JarvisColors.TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // + New Chat Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(JarvisColors.Presence.copy(alpha = 0.15f))
                            .border(0.8.dp, JarvisColors.Presence.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .clickable {
                                if (orchestrator != null) {
                                    orchestrator.startNewChat()
                                    selectedSessionId = orchestrator.currentSessionId.value
                                } else {
                                    val newId = UUID.randomUUID().toString()
                                    val newSession = ChatSessionEntity(
                                        sessionId = newId,
                                        title = "New Chat",
                                        createdAt = System.currentTimeMillis()
                                    )
                                    scope.launch(Dispatchers.IO) {
                                        db.conversationDao().insertSession(newSession)
                                        reloadSessions()
                                        withContext(Dispatchers.Main) {
                                            selectedSessionId = newId
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Chat",
                                tint = JarvisColors.Presence,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "NEW",
                                color = JarvisColors.Presence,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Sessions Carousel Navbar ───────────────────────────────────
            if (sessions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sessions.forEach { session ->
                        val isSelected = session.sessionId == selectedSessionId
                        val isCurrentActive = session.sessionId == currentOrchestratorSessionId

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) JarvisColors.SurfaceGlassCyan
                                    else JarvisColors.SurfaceGlass
                                )
                                .border(
                                    width = if (isSelected) 1.2.dp else 0.5.dp,
                                    color = if (isSelected) JarvisColors.Presence else JarvisColors.Hairline,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedSessionId = session.sessionId }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isCurrentActive) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(JarvisColors.Presence)
                                    )
                                }
                                Column {
                                    Text(
                                        text = session.title.ifBlank { "Untitled Session" },
                                        color = if (isSelected) JarvisColors.Presence else JarvisColors.TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 140.dp)
                                    )
                                    Text(
                                        text = formatTimestamp(session.createdAt),
                                        color = JarvisColors.TextMuted,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Selected Session Action Bar ────────────────────────────
                if (selectedSession != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${sessionMessages.size} messages in session",
                            color = JarvisColors.TextSecondary,
                            fontSize = 11.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // "Continue This Chat" in Home
                            if (orchestrator != null && selectedSession.sessionId != currentOrchestratorSessionId) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(JarvisColors.Presence.copy(alpha = 0.12f))
                                        .clickable {
                                            orchestrator.switchSession(selectedSession.sessionId)
                                            onBack()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = "Switch to Chat",
                                            tint = JarvisColors.Presence,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Continue Here",
                                            color = JarvisColors.Presence,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Delete Session Button
                            IconButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Session",
                                    tint = JarvisColors.StateError,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Messages Display ───────────────────────────────────────────
            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = JarvisColors.TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No saved conversations yet.",
                            color = JarvisColors.TextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap '+ NEW' above to start your first session.",
                            color = JarvisColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            } else if (sessionMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = JarvisColors.TextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No messages recorded in this session.",
                            color = JarvisColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 60.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sessionMessages, key = { it.id }) { msg ->
                        val isJarvis = msg.role.equals("jarvis", ignoreCase = true) ||
                                msg.role.equals("assistant", ignoreCase = true)

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isJarvis) Alignment.Start else Alignment.End
                        ) {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isJarvis) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(12.dp)
                                            .clip(RoundedCornerShape(1.dp))
                                            .background(JarvisColors.Presence)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 300.dp)
                                        .clip(
                                            RoundedCornerShape(
                                                topStart = 14.dp,
                                                topEnd = 14.dp,
                                                bottomStart = if (isJarvis) 4.dp else 14.dp,
                                                bottomEnd = if (isJarvis) 14.dp else 4.dp
                                            )
                                        )
                                        .background(
                                            if (isJarvis) JarvisColors.SurfaceGlassCyan
                                            else JarvisColors.SurfaceGlassElevated
                                        )
                                        .border(0.5.dp, JarvisColors.Hairline, RoundedCornerShape(14.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isJarvis) "JARVIS" else "YOU",
                                                color = if (isJarvis) JarvisColors.Presence else JarvisColors.TextMuted,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            )
                                            Text(
                                                text = formatTime(msg.createdAt),
                                                color = JarvisColors.TextMuted,
                                                fontSize = 9.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = msg.text,
                                            color = JarvisColors.TextPrimary,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Delete Confirmation Dialog ─────────────────────────────────────────
    if (showDeleteDialog && selectedSession != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = "Delete Chat Session?",
                    color = JarvisColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${selectedSession.title}'? All messages in this session will be permanently removed.",
                    color = JarvisColors.TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        val idToDelete = selectedSession.sessionId
                        if (orchestrator != null) {
                            orchestrator.deleteSession(idToDelete)
                        } else {
                            scope.launch(Dispatchers.IO) {
                                db.conversationDao().deleteSession(idToDelete)
                                db.conversationDao().clearSession(idToDelete)
                                reloadSessions()
                            }
                        }
                    }
                ) {
                    Text("Delete", color = JarvisColors.StateError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = JarvisColors.TextSecondary)
                }
            },
            containerColor = Color(0xFF142230)
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (_: Exception) {
        ""
    }
}

private fun formatTime(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (_: Exception) {
        ""
    }
}
