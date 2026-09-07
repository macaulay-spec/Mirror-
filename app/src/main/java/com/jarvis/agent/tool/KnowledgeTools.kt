package com.jarvis.agent.tool

import android.content.Context
import android.util.Xml
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionResult
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

/**
 * Cloud knowledge & creation tools — all keyless public APIs:
 *  - generate_image : AI image generation (Pollinations, free)
 *  - wikipedia      : knowledge lookups (Wikipedia REST, free)
 *  - news           : live headlines (Google News RSS, free)
 *  - currency       : live exchange rates (open.er-api.com, free)
 *
 * Registered through [ToolRegistration] so the LLM sees them via ToolSchema
 * automatically.
 */
object KnowledgeTools {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    /** Image generation can take 10-40s on first render — dedicated client. */
    private val imageHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    fun registerAll() {
        registerGenerateImage()
        registerWikipedia()
        registerNews()
        registerCurrency()
    }

    // ------------------------------------------------------------ image gen

    private fun registerGenerateImage() {
        ToolRegistry.register(
            ToolDefinition(
                id = "generate_image",
                name = "Generate Image",
                description = "Generates an image from a text description using AI. " +
                    "Returns a URL to the generated image that can be shared or opened. " +
                    "Use whenever the user asks to create, draw, or imagine a picture.",
                category = "INFORMATION",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val prompt = arg(args, "prompt", "description", "text", "query", "q")
                if (prompt.isBlank()) {
                    return@ToolDefinition error("generate_image", "Tell me what to draw — describe the image you want.")
                }

                val seed = (0..999_999).random()
                val url = "https://image.pollinations.ai/prompt/" +
                    URLEncoder.encode(prompt, "UTF-8") +
                    "?width=1024&height=1024&nologo=true&seed=$seed"

                // Pollinations renders on first GET — verify the image actually
                // generates so the returned URL is known-good.
                var verified = false
                try {
                    imageHttp.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                        verified = resp.isSuccessful
                    }
                } catch (_: Exception) {
                    // Non-fatal: the URL stays valid and will render on open.
                }

                ToolExecutionResult(
                    toolId = "generate_image",
                    success = true,
                    data = mapOf(
                        "image_url" to url,
                        "prompt" to prompt,
                        "verified" to verified
                    ),
                    verificationDetails = if (verified) {
                        "Image generated and verified: $url"
                    } else {
                        "Image queued (still rendering, URL is stable): $url"
                    }
                )
            }
        )
    }

    // ------------------------------------------------------------ wikipedia

    private fun registerWikipedia() {
        ToolRegistry.register(
            ToolDefinition(
                id = "wikipedia",
                name = "Look Up Knowledge",
                description = "Looks up a topic on Wikipedia and returns a concise, reliable summary. " +
                    "Use for facts about people, places, companies, events, science, and history.",
                category = "INFORMATION",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val query = arg(args, "query", "topic", "subject", "q", "text")
                if (query.isBlank()) {
                    return@ToolDefinition error("wikipedia", "What should I look up?")
                }

                try {
                    // 1. Search for the best-matching article title
                    val searchUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search" +
                        "&srsearch=" + URLEncoder.encode(query, "UTF-8") +
                        "&format=json&srlimit=1"
                    val searchJson = httpGetString(searchUrl)
                        ?: return@ToolDefinition error("wikipedia", "Couldn't reach Wikipedia.")
                    val results = JSONObject(searchJson).optJSONObject("query")?.optJSONArray("search")
                    if (results == null || results.length() == 0) {
                        return@ToolDefinition error("wikipedia", "I found nothing on \"$query\".")
                    }
                    val title = results.getJSONObject(0).optString("title")

                    // 2. Fetch the clean summary of that article
                    val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/" +
                        URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
                    val summaryJson = httpGetString(summaryUrl)
                        ?: return@ToolDefinition error("wikipedia", "Couldn't load the article for \"$title\".")
                    val summary = JSONObject(summaryJson)
                    val extract = summary.optString("extract")
                    if (extract.isBlank()) {
                        return@ToolDefinition error("wikipedia", "The article for \"$title\" has no summary.")
                    }

                    val link = summary.optJSONObject("content_urls")
                        ?.optJSONObject("desktop")?.optString("page").orEmpty()

                    ToolExecutionResult(
                        toolId = "wikipedia",
                        success = true,
                        data = mapOf(
                            "title" to title,
                            "summary" to extract,
                            "url" to link
                        ),
                        verificationDetails = "Wikipedia summary loaded (${extract.length} chars)"
                    )
                } catch (e: Exception) {
                    error("wikipedia", "Knowledge lookup failed: ${e.message ?: "unknown error"}")
                }
            }
        )
    }

    // ------------------------------------------------------------ news

    private fun registerNews() {
        ToolRegistry.register(
            ToolDefinition(
                id = "news",
                name = "Get News Headlines",
                description = "Fetches current top news headlines, optionally on a topic " +
                    "(technology, business, sports, science, world, entertainment, health). " +
                    "Use when the user asks for the news or what's happening today.",
                category = "INFORMATION",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val topic = arg(args, "topic", "category", "section", "about").trim()

                val rssUrl = if (topic.isBlank()) {
                    "https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en"
                } else {
                    val known = listOf(
                        "technology", "business", "sports", "science",
                        "world", "entertainment", "health"
                    ).firstOrNull { topic.lowercase().contains(it) }
                    if (known != null) {
                        "https://news.google.com/rss/headlines/section/topic/" +
                            known.uppercase() + "?hl=en-US&gl=US&ceid=US:en"
                    } else {
                        "https://news.google.com/rss/search?q=" +
                            URLEncoder.encode(topic, "UTF-8") + "&hl=en-US&gl=US&ceid=US:en"
                    }
                }

                try {
                    val xml = httpGetString(rssUrl)
                        ?: return@ToolDefinition error("news", "Couldn't reach the news service.")
                    val headlines = parseRssTitles(xml).take(8)
                    if (headlines.isEmpty()) {
                        return@ToolDefinition error("news", "No headlines found right now.")
                    }

                    ToolExecutionResult(
                        toolId = "news",
                        success = true,
                        data = mapOf(
                            "topic" to topic.ifBlank { "top stories" },
                            "headlines" to headlines,
                            "formatted" to headlines.mapIndexed { i, h -> "${i + 1}. $h" }.joinToString("\n")
                        ),
                        verificationDetails = "${headlines.size} live headlines loaded"
                    )
                } catch (e: Exception) {
                    error("news", "News fetch failed: ${e.message ?: "unknown error"}")
                }
            }
        )
    }

    /** Minimal streaming RSS parser — extracts <item><title> entries. */
    private fun parseRssTitles(xml: String): List<String> {
        val titles = mutableListOf<String>()
        try {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            var event = parser.eventType
            var inItem = false
            var currentTag = ""
            var text = StringBuilder()
            var title = ""

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        text = StringBuilder()
                        if (parser.name == "item") {
                            inItem = true
                            title = ""
                        }
                    }
                    XmlPullParser.TEXT -> if (inItem && parser.name == null || inItem) {
                        if (currentTag == "title") text.append(parser.text ?: "")
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "title" && inItem && title.isBlank()) {
                            title = text.toString().trim()
                        }
                        if (parser.name == "item" && inItem) {
                            inItem = false
                            if (title.isNotBlank()) titles.add(title)
                        }
                        currentTag = ""
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            // Return whatever was parsed before the failure.
        }
        return titles
    }

    // ------------------------------------------------------------ currency

    private fun registerCurrency() {
        ToolRegistry.register(
            ToolDefinition(
                id = "currency",
                name = "Convert Currency",
                description = "Converts an amount between currencies using live exchange rates. " +
                    "Takes from (e.g. USD), to (e.g. EUR) and amount (default 1).",
                category = "INFORMATION",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val from = arg(args, "from", "from_currency", "base", "source").trim()
                    .uppercase().ifBlank { "USD" }.take(3)
                val to = arg(args, "to", "to_currency", "target", "quote").trim()
                    .uppercase().ifBlank { "EUR" }.take(3)
                val amount = arg(args, "amount", "value", "quantity").trim()
                    .replace(",", "").toDoubleOrNull() ?: 1.0

                try {
                    val url = "https://open.er-api.com/v6/latest/$from"
                    val body = httpGetString(url)
                        ?: return@ToolDefinition error("currency", "Couldn't reach the exchange rate service.")
                    val json = JSONObject(body)
                    if (json.optString("result") != "success") {
                        return@ToolDefinition error("currency", "Unknown currency code \"$from\".")
                    }
                    val rate = json.optJSONObject("rates")?.optDouble(to)
                        ?: return@ToolDefinition error("currency", "Unknown currency code \"$to\".")

                    val converted = amount * rate
                    val formatted = String.format(java.util.Locale.US, "%,.2f %s = %,.2f %s", amount, from, converted, to)

                    ToolExecutionResult(
                        toolId = "currency",
                        success = true,
                        data = mapOf(
                            "formatted" to formatted,
                            "rate" to rate,
                            "amount" to amount,
                            "from" to from,
                            "to" to to
                        ),
                        verificationDetails = "Live rate: 1 $from = $rate $to"
                    )
                } catch (e: Exception) {
                    error("currency", "Currency conversion failed: ${e.message ?: "unknown error"}")
                }
            }
        )
    }

    // ------------------------------------------------------------ helpers

    private fun httpGetString(url: String): String? = try {
        http.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    } catch (_: Exception) {
        null
    }

    private fun arg(args: Map<String, Any?>, vararg keys: String): String =
        keys.firstNotNullOfOrNull { args[it] }?.toString()?.trim() ?: ""

    private fun error(toolId: String, message: String) = ToolExecutionResult(
        toolId = toolId,
        success = false,
        data = null,
        error = message
    )
}
