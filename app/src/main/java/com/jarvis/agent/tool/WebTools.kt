package com.jarvis.agent.tool

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object WebTools {
    // Tighter than the old 10s/15s: search() can now make up to two sequential lookups
    // before falling back to the browser, and a spoken answer that arrives after 25
    // seconds is worse than no answer. Bounded worst case matters more than tolerating a
    // slow first hop.
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Answers a web query with real text.
     *
     * FIX (audit P1-E): this implementation existed but was registered under the id
     * `web_extract`, while the id the model naturally reaches for — `web_search` — was a
     * different tool in `ToolRegistry` that only fired a browser intent and returned
     * "Web search opened for 'X'" with no content. So "who is the president of France?"
     * opened Chrome instead of answering, and the capable code was unreachable under the
     * obvious name. `ToolRegistry.web_search` now delegates here and `web_extract` is
     * gone (replaced by `web_open`, which is all it ever did reliably).
     *
     * Tiers, all keyless:
     *   1. DuckDuckGo Instant Answer — good for definitions, entities, calculations.
     *   2. Wikipedia search + summary — catches the entity questions DDG has no
     *      instant answer for, which is most factual "who/what/when" queries.
     *   3. Browser — last resort, and the result says so explicitly instead of
     *      claiming success with no content.
     */
    suspend fun search(context: Context, query: String): ToolExecutionResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) {
            return@withContext ToolExecutionResult("web_search", false, null, "Query is empty.")
        }

        duckDuckGoAnswer(cleanQuery)?.let { return@withContext answer(cleanQuery, it, "DuckDuckGo") }
        wikipediaSummary(cleanQuery)?.let { return@withContext answer(cleanQuery, it, "Wikipedia") }

        // Nothing could answer it. Open a search and say plainly that JARVIS could not
        // answer -- reporting success with no content is what made this look like a
        // working tool while the model received nothing to speak.
        val opened = openBrowserSearch(context, cleanQuery)
        ToolExecutionResult(
            toolId = "web_search",
            success = opened,
            data = mapOf("query" to cleanQuery, "answered" to false),
            verificationDetails = if (opened) {
                "I could not find a direct answer for '$cleanQuery', so I opened a web search for it."
            } else {
                "No answer found for '$cleanQuery' and no browser available to open a search."
            },
            error = if (opened) null else "Could not answer '$cleanQuery' or open a browser."
        )
    }

    private fun answer(query: String, text: String, source: String) = ToolExecutionResult(
        toolId = "web_search",
        success = true,
        data = mapOf("query" to query, "result" to text, "source" to source, "answered" to true),
        verificationDetails = text
    )

    /** DuckDuckGo Instant Answer. Returns null when there is genuinely nothing. */
    private fun duckDuckGoAnswer(query: String): String? = try {
        val url = "https://api.duckduckgo.com/?q=${Uri.encode(query)}&format=json&no_html=1&skip_disambig=1"
        get(url)?.let { json ->
            listOf(
                json.optString("Answer", ""),
                json.optString("AbstractText", "")
            ).firstOrNull { it.isNotBlank() }?.trim()
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Wikipedia: one search call to resolve the title, one REST call for the summary.
     *
     * Kept as two calls rather than guessing the title from the query, because most
     * spoken queries are not exact page titles ("who is the president of France").
     */
    private fun wikipediaSummary(query: String): String? = try {
        val searchUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search" +
            "&srsearch=${Uri.encode(query)}&srlimit=1&format=json"
        val title = get(searchUrl)
            ?.optJSONObject("query")
            ?.optJSONArray("search")
            ?.optJSONObject(0)
            ?.optString("title")
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/${Uri.encode(title)}"
        val extract = get(summaryUrl)?.optString("extract", "")?.trim()
        extract?.takeIf { it.isNotBlank() && it != "null" }?.let { "$title: $it" }
    } catch (_: Exception) {
        null
    }

    private fun get(url: String): JSONObject? = try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "JarvisAndroid/1.0 (personal assistant; contact: none)")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null
            else JSONObject(response.body?.string() ?: "{}")
        }
    } catch (_: Exception) {
        null
    }

    private fun openBrowserSearch(context: Context, query: String): Boolean {
        try {
            context.startActivity(
                Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(android.app.SearchManager.QUERY, query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            return true
        } catch (_: Exception) {
            // fall through to an explicit browser search URL
        }
        return try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "https://duckduckgo.com/?q=${Uri.encode(query)}".toUri()
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun open(context: Context, url: String): ToolExecutionResult = withContext(Dispatchers.IO) {
        val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
        try {
            val intent = Intent(Intent.ACTION_VIEW, cleanUrl.toUri()).apply {
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

    /**
     * Registers `web_open`.
     *
     * CHANGED (audit P1-E): this used to register `web_extract`, described as "Web Open /
     * Search", which accepted either a url or a query. That made it a second, vaguely
     * named search tool competing with `web_search`, and the model had no reason to pick
     * the one that could actually answer. It is now purely "open this URL"; searching is
     * `web_search` alone.
     */
    fun register(registry: ToolRegistry) {
        registry.register(
            ToolDefinition(
                id = "web_open",
                name = "Open Web Page",
                description = "Opens a specific web page URL in the browser. Pass 'url'. " +
                    "For answering a question use web_search instead.",
                category = "WEB",
                riskLevel = RiskLevel.LEVEL_1
            ) { context, args ->
                val url = args["url"]?.toString()
                if (!url.isNullOrBlank()) {
                    open(context, url)
                } else {
                    ToolExecutionResult("web_open", false, null, "A 'url' is required.")
                }
            }
        )
    }
}
