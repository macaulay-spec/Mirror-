package com.jarvis.agent.tool

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object WebTools {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun search(context: Context, query: String): ToolExecutionResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@withContext ToolExecutionResult("web_search", false, null, "Query is empty.")
        }

        try {
            // Instant answer lookup via DuckDuckGo public API
            val ddgUrl = "https://api.duckduckgo.com/?q=${Uri.encode(cleanQuery)}&format=json&no_html=1&skip_disambig=1"
            val request = Request.Builder()
                .url(ddgUrl)
                .header("User-Agent", "JarvisAndroid/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val abstractText = json.optString("AbstractText", "").takeIf { it.isNotBlank() }
                    val answer = json.optString("Answer", "").takeIf { it.isNotBlank() }
                    val resultText = answer ?: abstractText

                    if (!resultText.isNullOrBlank()) {
                        return@withContext ToolExecutionResult(
                            toolId = "web_search",
                            success = true,
                            data = mapOf("query" to cleanQuery, "result" to resultText),
                            verificationDetails = resultText
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback: Launch browser/search intent on device
        try {
            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(android.app.SearchManager.QUERY, cleanQuery)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(searchIntent)
            ToolExecutionResult(
                toolId = "web_search",
                success = true,
                data = mapOf("query" to cleanQuery),
                verificationDetails = "Opened web search for '$cleanQuery'."
            )
        } catch (_: Exception) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(cleanQuery)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                ToolExecutionResult(
                    toolId = "web_search",
                    success = true,
                    data = mapOf("query" to cleanQuery),
                    verificationDetails = "Launched browser search for '$cleanQuery'."
                )
            } catch (e2: Exception) {
                ToolExecutionResult("web_search", false, null, "Failed to launch search: ${e2.localizedMessage}")
            }
        }
    }

    suspend fun open(context: Context, url: String): ToolExecutionResult = withContext(Dispatchers.IO) {
        val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ToolExecutionResult(
                toolId = "web_open",
                success = true,
                data = mapOf("url" to cleanUrl),
                verificationDetails = "Navigated to $cleanUrl."
            )
        } catch (e: Exception) {
            ToolExecutionResult("web_open", false, null, "Failed to open URL: ${e.localizedMessage}")
        }
    }

    fun register(registry: ToolRegistry) {
        registry.register(
            ToolDefinition(
                id = "web_extract",
                name = "Web Open / Search",
                description = "Opens a web page URL or performs web query. Pass 'url' or 'query'.",
                category = "WEB",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val url = args["url"]?.toString()
                val query = args["query"]?.toString()
                if (!url.isNullOrBlank()) open(context, url)
                else if (!query.isNullOrBlank()) search(context, query)
                else ToolExecutionResult("web_extract", false, null, "URL or query required.")
            }
        )
    }
}
