package com.jarvis.feature.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.core.model.JarvisVisualState
import com.jarvis.core.theme.JarvisColors
import com.jarvis.core.ui.GlassCard
import com.jarvis.core.ui.JarvisCore

data class WebSearchResult(
    val title: String,
    val source: String,
    val time: String,
    val snippet: String
)

/**
 * Web Search Screen — shows search results from JARVIS web search.
 * Matches design: search results with source cards.
 */
@Composable
fun WebSearchScreen(
    query: String,
    results: List<WebSearchResult> = emptyList(),
    onResultClick: (WebSearchResult) -> Unit = {},
    onViewAll: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    var isSearching by remember { mutableStateOf(true) }
    var displayResults by remember { mutableStateOf(emptyList<WebSearchResult>()) }
    
    LaunchedEffect(results) {
        if (results.isNotEmpty()) {
            isSearching = false
            displayResults = results
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.VoidBlack)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Search query
        Text(
            text = "Search the web for",
            color = JarvisColors.TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = query,
            color = JarvisColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Orb in searching state
        JarvisCore(
            state = if (isSearching) JarvisVisualState.Thinking else JarvisVisualState.Idle,
            size = 80
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // JARVIS response
        Text(
            text = "JARVIS",
            color = JarvisColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (isSearching) "Searching the web..." else "Top results",
            color = JarvisColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Results list
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(displayResults) { result ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        // Source and time
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = result.source,
                                color = JarvisColors.Presence,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = result.time,
                                color = JarvisColors.TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Title
                        Text(
                            text = result.title,
                            color = JarvisColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Snippet
                        Text(
                            text = result.snippet,
                            color = JarvisColors.TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // View all results button
        if (!isSearching && displayResults.isNotEmpty()) {
            GlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "View all results",
                        color = JarvisColors.Presence,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
