package com.jarvis.agent.tool

import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionResult
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * David Cyril API Suite Integration for JARVIS.
 * Connects JARVIS to free keyless AI models (Claude Opus 4.6, DeepSeek Thinking, Grok, GPT-4o, Blackbox),
 * AWS Polly & Google TTS voices, Flux & Writecream image generators, sports, live news, burner SMS numbers,
 * lyrics, and OSINT lookup tools.
 */
object DavidCyrilTools {

    private const val BASE_URL = "https://apis.davidcyril.name.ng"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun registerAll() {
        registerClaudeOpus()
        registerDeepSeekThinking()
        registerGrokFast()
        registerBlackboxCode()
        registerGpt4o()
        registerAwsPollyTts()
        registerGoogleTts()
        registerFluxImageGen()
        registerWritecreamImageGen()
        registerTranslate()
        registerSportsScores()
        registerLiveNews()
        registerTempPhoneNumber()
        registerCheckTempSms()
        registerGeniusLyrics()
        registerGithubStalk()
        registerShortenUrl()
        registerWikipediaSummary()
    }

    private fun fetchJson(endpoint: String): JSONObject? {
        return try {
            val url = if (endpoint.startsWith("http")) endpoint else "$BASE_URL/$endpoint"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; JARVIS Agent)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                JSONObject(bodyStr)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun encode(text: String): String {
        return try {
            URLEncoder.encode(text, "UTF-8")
        } catch (e: Exception) {
            text.replace(" ", "%20")
        }
    }

    // ------------------------------------------------------------ AI Models
    private fun registerClaudeOpus() {
        ToolRegistry.register(
            ToolDefinition(
                id = "ask_claude_opus",
                name = "Ask Claude Opus",
                description = "Queries Anthropic's Claude Opus 4.6 model for complex reasoning, creative writing, high-context analysis, and nuanced answers.",
                category = "ASSISTANT",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val prompt = args["prompt"]?.toString() ?: args["query"]?.toString() ?: ""
                if (prompt.isBlank()) {
                    return@ToolDefinition errorResult("ask_claude_opus", "Prompt argument is required.")
                }

                val json = fetchJson("ai/claude-opus-4.6?prompt=${encode(prompt)}")
                    ?: fetchJson("ai/claude-opus-4.5?prompt=${encode(prompt)}")
                    ?: return@ToolDefinition errorResult("ask_claude_opus", "Claude Opus service is currently unavailable.")

                val answer = json.optString("data", json.optString("response", "No answer generated."))

                ToolExecutionResult(
                    toolId = "ask_claude_opus",
                    success = true,
                    data = mapOf("response" to answer, "model" to "claude-opus-4.6"),
                    verificationDetails = answer
                )
            }
        )
    }

    private fun registerDeepSeekThinking() {
        ToolRegistry.register(
            ToolDefinition(
                id = "ask_deepseek_reasoning",
                name = "Ask DeepSeek Thinking",
                description = "Queries DeepSeek V3.2 Thinking model for step-by-step logic, mathematical proofs, algorithm design, and structured problem solving.",
                category = "ASSISTANT",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val prompt = args["prompt"]?.toString() ?: args["query"]?.toString() ?: ""
                if (prompt.isBlank()) {
                    return@ToolDefinition errorResult("ask_deepseek_reasoning", "Prompt argument is required.")
                }

                val json = fetchJson("ai/deepseek-v3.2-thinking?prompt=${encode(prompt)}")
                    ?: fetchJson("ai/deepseek-v4-flash?prompt=${encode(prompt)}")
                    ?: return@ToolDefinition errorResult("ask_deepseek_reasoning", "DeepSeek reasoning service is currently unavailable.")

                val answer = json.optString("data", json.optString("response", "No answer generated."))

                ToolExecutionResult(
                    toolId = "ask_deepseek_reasoning",
                    success = true,
                    data = mapOf("response" to answer, "model" to "deepseek-v3.2-thinking"),
                    verificationDetails = answer
                )
            }
        )
    }

    private fun registerGrokFast() {
        ToolRegistry.register(
            ToolDefinition(
                id = "ask_grok_fast",
                name = "Ask Grok 4.1 Fast",
                description = "Queries xAI's Grok 4.1 Fast model for ultra-rapid, direct, witty, and concise answers.",
                category = "ASSISTANT",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val prompt = args["prompt"]?.toString() ?: args["query"]?.toString() ?: ""
                if (prompt.isBlank()) {
                    return@ToolDefinition errorResult("ask_grok_fast", "Prompt argument is required.")
                }

                val json = fetchJson("ai/grok-4.1-fast?prompt=${encode(prompt)}")
                    ?: return@ToolDefinition errorResult("ask_grok_fast", "Grok service is currently unavailable.")

                val answer = json.optString("data", json.optString("response", "No answer generated."))

                ToolExecutionResult(
                    toolId = "ask_grok_fast",
                    success = true,
                    data = mapOf("response" to answer, "model" to "grok-4.1-fast"),
                    verificationDetails = answer
                )
            }
        )
    }

    private fun registerBlackboxCode() {
        ToolRegistry.register(
            ToolDefinition(
                id = "ask_blackbox_code",
                name = "Ask Blackbox AI",
                description = "Queries Blackbox AI specialized code generator for software development, debugging, Kotlin/Android code snippets, and architecture advice.",
                category = "DEVELOPER",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val query = args["query"]?.toString() ?: args["prompt"]?.toString() ?: ""
                if (query.isBlank()) {
                    return@ToolDefinition errorResult("ask_blackbox_code", "Code query argument is required.")
                }

                val json = fetchJson("blackbox?q=${encode(query)}")
                    ?: return@ToolDefinition errorResult("ask_blackbox_code", "Blackbox AI service is currently unavailable.")

                val response = json.optString("response", json.optString("data", "No code output generated."))

                ToolExecutionResult(
                    toolId = "ask_blackbox_code",
                    success = true,
                    data = mapOf("code_response" to response),
                    verificationDetails = response
                )
            }
        )
    }

    private fun registerGpt4o() {
        ToolRegistry.register(
            ToolDefinition(
                id = "ask_gpt4o",
                name = "Ask GPT-4o",
                description = "Queries OpenAI's GPT-4o model for general intelligence, facts, drafting text, and general conversational tasks.",
                category = "ASSISTANT",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val prompt = args["prompt"]?.toString() ?: args["query"]?.toString() ?: ""
                if (prompt.isBlank()) {
                    return@ToolDefinition errorResult("ask_gpt4o", "Prompt argument is required.")
                }

                val json = fetchJson("ai/gpt-4o?prompt=${encode(prompt)}")
                    ?: return@ToolDefinition errorResult("ask_gpt4o", "GPT-4o service is currently unavailable.")

                val answer = json.optString("data", json.optString("response", "No answer generated."))

                ToolExecutionResult(
                    toolId = "ask_gpt4o",
                    success = true,
                    data = mapOf("response" to answer, "model" to "gpt-4o"),
                    verificationDetails = answer
                )
            }
        )
    }

    // ------------------------------------------------------------ Voice & TTS
    private fun registerAwsPollyTts() {
        ToolRegistry.register(
            ToolDefinition(
                id = "speak_text_aws_polly",
                name = "AWS Polly Studio Speech",
                description = "Converts written text into studio-quality vocal speech using AWS Polly voices (Brian for British JARVIS tone, Matthew, Joanna, Amy, Emma, Joey). Returns an MP3 stream URL.",
                category = "UTILITY",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val text = args["text"]?.toString() ?: ""
                val voice = args["voice"]?.toString()?.ifBlank { "Brian" } ?: "Brian"

                if (text.isBlank()) {
                    return@ToolDefinition errorResult("speak_text_aws_polly", "Text to speak is required.")
                }

                val json = fetchJson("tts?text=${encode(text)}&voice=$voice")
                    ?: return@ToolDefinition errorResult("speak_text_aws_polly", "AWS Polly speech engine unavailable.")

                val audioUrl = json.optString("audioUrl", "")
                if (audioUrl.isBlank()) {
                    return@ToolDefinition errorResult("speak_text_aws_polly", "No audio stream produced.")
                }

                ToolExecutionResult(
                    toolId = "speak_text_aws_polly",
                    success = true,
                    data = mapOf("audio_url" to audioUrl, "voice" to voice, "text" to text),
                    verificationDetails = "Speech audio generated with $voice voice: $audioUrl"
                )
            }
        )
    }

    private fun registerGoogleTts() {
        ToolRegistry.register(
            ToolDefinition(
                id = "speak_text_google_tts",
                name = "Google TTS Speech",
                description = "Converts text to spoken audio via Google Speech API. Returns embedded base64 audio data for immediate in-memory playback.",
                category = "UTILITY",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val text = args["text"]?.toString() ?: ""
                val lang = args["lang"]?.toString()?.ifBlank { "en" } ?: "en"

                if (text.isBlank()) {
                    return@ToolDefinition errorResult("speak_text_google_tts", "Text to speak is required.")
                }

                val json = fetchJson("tts/google?text=${encode(text)}&lang=$lang")
                    ?: return@ToolDefinition errorResult("speak_text_google_tts", "Google TTS engine unavailable.")

                val resultObj = json.optJSONObject("result")
                val base64Audio = resultObj?.optString("audio", "") ?: ""

                if (base64Audio.isBlank()) {
                    return@ToolDefinition errorResult("speak_text_google_tts", "No base64 audio data produced.")
                }

                ToolExecutionResult(
                    toolId = "speak_text_google_tts",
                    success = true,
                    data = mapOf("base64_audio" to base64Audio, "lang" to lang, "text" to text),
                    verificationDetails = "Base64 audio produced for text: $text"
                )
            }
        )
    }

    // ------------------------------------------------------------ Image Gen
    private fun registerFluxImageGen() {
        ToolRegistry.register(
            ToolDefinition(
                id = "generate_image_flux",
                name = "Flux AI Image Generator",
                description = "Generates high-definition artwork, concepts, and diagrams in 1 second using the Flux AI image model.",
                category = "INFORMATION",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val prompt = args["prompt"]?.toString() ?: args["description"]?.toString() ?: ""
                if (prompt.isBlank()) {
                    return@ToolDefinition errorResult("generate_image_flux", "Image description prompt is required.")
                }

                val imageUrl = "$BASE_URL/flux?prompt=${encode(prompt)}"

                ToolExecutionResult(
                    toolId = "generate_image_flux",
                    success = true,
                    data = mapOf("image_url" to imageUrl, "prompt" to prompt, "engine" to "flux"),
                    verificationDetails = "Flux image synthesized for: '$prompt'. Direct URL: $imageUrl"
                )
            }
        )
    }

    private fun registerWritecreamImageGen() {
        ToolRegistry.register(
            ToolDefinition(
                id = "generate_image_writecream",
                name = "Writecream AI Image Generator",
                description = "Generates photorealistic images using Writecream engine and returns a permanent AWS S3 image URL.",
                category = "INFORMATION",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val prompt = args["prompt"]?.toString() ?: args["description"]?.toString() ?: ""
                val ratio = args["ratio"]?.toString()?.ifBlank { "1:1" } ?: "1:1"

                if (prompt.isBlank()) {
                    return@ToolDefinition errorResult("generate_image_writecream", "Image description prompt is required.")
                }

                val json = fetchJson("ai/writecream/image?prompt=${encode(prompt)}&ratio=$ratio")
                    ?: return@ToolDefinition errorResult("generate_image_writecream", "Writecream image engine unavailable.")

                val imageUrl = json.optString("image_url", "")
                if (imageUrl.isBlank()) {
                    return@ToolDefinition errorResult("generate_image_writecream", "No image URL returned.")
                }

                ToolExecutionResult(
                    toolId = "generate_image_writecream",
                    success = true,
                    data = mapOf("image_url" to imageUrl, "prompt" to prompt, "ratio" to ratio),
                    verificationDetails = "Writecream image created: $imageUrl"
                )
            }
        )
    }

    // ------------------------------------------------------------ Tools & Utilities
    private fun registerTranslate() {
        ToolRegistry.register(
            ToolDefinition(
                id = "translate_text",
                name = "Google Translate",
                description = "Translates text into any target language code (e.g. 'fr' for French, 'es' for Spanish, 'de' for German, 'ja' for Japanese, 'zh' for Chinese).",
                category = "UTILITY",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val text = args["text"]?.toString() ?: ""
                val to = args["to"]?.toString()?.ifBlank { "en" } ?: "en"

                if (text.isBlank()) {
                    return@ToolDefinition errorResult("translate_text", "Text to translate is required.")
                }

                val json = fetchJson("tools/translate?text=${encode(text)}&to=$to")
                    ?: return@ToolDefinition errorResult("translate_text", "Translation service unavailable.")

                val translated = json.optString("translated_text", json.optString("result", text))

                ToolExecutionResult(
                    toolId = "translate_text",
                    success = true,
                    data = mapOf("original" to text, "translated" to translated, "target_lang" to to),
                    verificationDetails = "Translation ($to): $translated"
                )
            }
        )
    }

    private fun registerSportsScores() {
        ToolRegistry.register(
            ToolDefinition(
                id = "get_sports_scores",
                name = "Live Sports Scores & Standings",
                description = "Fetches live match scores and standings for Premier League football ('soccer') or NBA basketball ('nba').",
                category = "INFORMATION",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val sport = (args["sport"]?.toString() ?: args["league"]?.toString() ?: "soccer").lowercase()
                val type = (args["type"]?.toString() ?: "scores").lowercase()

                val endpoint = when {
                    sport.contains("nba") -> "sports/nba/scores"
                    type.contains("standing") -> "sports/soccer/standings"
                    else -> "sports/soccer/scores"
                }

                val json = fetchJson(endpoint)
                    ?: return@ToolDefinition errorResult("get_sports_scores", "Sports data service unavailable.")

                val league = json.optString("league", sport.uppercase())
                val games = json.optJSONArray("games")
                val standings = json.optJSONArray("standings")

                val summaryList = mutableListOf<String>()

                if (games != null && games.length() > 0) {
                    for (i in 0 until minOf(games.length(), 10)) {
                        val g = games.optJSONObject(i) ?: continue
                        val name = g.optString("name", g.optString("homeTeam") + " vs " + g.optString("awayTeam"))
                        val status = g.optString("status", "")
                        summaryList.add("Match: $name ($status)")
                    }
                } else if (standings != null && standings.length() > 0) {
                    for (i in 0 until minOf(standings.length(), 10)) {
                        val st = standings.optJSONObject(i) ?: continue
                        val rank = i + 1
                        val team = st.optString("team")
                        val pts = st.optInt("points", 0)
                        summaryList.add("#$rank $team - $pts pts")
                    }
                }

                val summary = if (summaryList.isNotEmpty()) {
                    summaryList.joinToString("\n")
                } else {
                    "No current active games or standings returned for $league."
                }

                ToolExecutionResult(
                    toolId = "get_sports_scores",
                    success = true,
                    data = mapOf("league" to league, "summary" to summary),
                    verificationDetails = summary
                )
            }
        )
    }

    private fun registerLiveNews() {
        ToolRegistry.register(
            ToolDefinition(
                id = "get_live_news",
                name = "Live Global News Headlines",
                description = "Fetches breaking global news headlines from top publishers (BBC News, TechCrunch, Hacker News, or Trending News).",
                category = "INFORMATION",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val source = (args["source"]?.toString() ?: args["category"]?.toString() ?: "tech").lowercase()

                val endpoint = when {
                    source.contains("bbc") -> "news/bbc"
                    source.contains("hacker") -> "news/hackernews"
                    source.contains("trending") -> "news/trending"
                    else -> "news/techcrunch"
                }

                val json = fetchJson(endpoint)
                    ?: return@ToolDefinition errorResult("get_live_news", "News headline service unavailable.")

                val articles = json.optJSONArray("articles") ?: json.optJSONArray("result") ?: JSONArray()
                val headlines = mutableListOf<String>()

                for (i in 0 until minOf(articles.length(), 8)) {
                    val item = articles.optJSONObject(i) ?: continue
                    val title = item.optString("title", item.optString("headline"))
                    val url = item.optString("url", item.optString("link"))
                    if (title.isNotBlank()) {
                        headlines.add("• $title ($url)")
                    }
                }

                val summary = if (headlines.isNotEmpty()) {
                    headlines.joinToString("\n")
                } else {
                    "No breaking news headlines retrieved at this moment."
                }

                ToolExecutionResult(
                    toolId = "get_live_news",
                    success = true,
                    data = mapOf("source" to source, "headlines" to summary),
                    verificationDetails = summary
                )
            }
        )
    }

    private fun registerTempPhoneNumber() {
        ToolRegistry.register(
            ToolDefinition(
                id = "get_temp_phone_number",
                name = "Get Disposable SMS Phone Number",
                description = "Gets a list of temporary virtual mobile numbers to receive online SMS verification codes without exposing your personal phone number.",
                category = "UTILITY",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, _ ->
                val json = fetchJson("tempnumber/receive-smss/numbers")
                    ?: return@ToolDefinition errorResult("get_temp_phone_number", "Disposable number service unavailable.")

                val resultObj = json.optJSONObject("result") ?: json
                val numbersArr = resultObj.optJSONArray("numbers") ?: JSONArray()

                val numberList = mutableListOf<String>()
                for (i in 0 until minOf(numbersArr.length(), 10)) {
                    val numObj = numbersArr.optJSONObject(i) ?: continue
                    val num = numObj.optString("number")
                    val country = numObj.optString("country")
                    numberList.add("$num ($country)")
                }

                val summary = if (numberList.isNotEmpty()) {
                    "Available temporary numbers:\n" + numberList.joinToString("\n")
                } else {
                    "No temporary phone numbers are currently available."
                }

                ToolExecutionResult(
                    toolId = "get_temp_phone_number",
                    success = true,
                    data = mapOf("numbers" to summary),
                    verificationDetails = summary
                )
            }
        )
    }

    private fun registerCheckTempSms() {
        ToolRegistry.register(
            ToolDefinition(
                id = "check_temp_phone_sms",
                name = "Check Disposable SMS Inbox",
                description = "Checks incoming SMS messages and OTP verification codes received on a temporary mobile phone number.",
                category = "UTILITY",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val number = args["number"]?.toString() ?: ""
                if (number.isBlank()) {
                    return@ToolDefinition errorResult("check_temp_phone_sms", "Phone number argument is required.")
                }

                val cleanNum = number.replace("+", "").replace(" ", "")
                val json = fetchJson("tempnumber/receive-smss/inbox?number=$cleanNum")
                    ?: return@ToolDefinition errorResult("check_temp_phone_sms", "Unable to read inbox for $number.")

                val resultObj = json.optJSONObject("result") ?: json
                val messages = resultObj.optJSONArray("messages") ?: JSONArray()

                val smsList = mutableListOf<String>()
                for (i in 0 until minOf(messages.length(), 5)) {
                    val sms = messages.optJSONObject(i) ?: continue
                    val sender = sms.optString("sender", sms.optString("from"))
                    val text = sms.optString("text", sms.optString("message"))
                    val time = sms.optString("time", "")
                    smsList.add("[$time] From $sender: $text")
                }

                val summary = if (smsList.isNotEmpty()) {
                    "Latest incoming SMS messages for $number:\n" + smsList.joinToString("\n---\n")
                } else {
                    "No incoming SMS messages received yet for $number."
                }

                ToolExecutionResult(
                    toolId = "check_temp_phone_sms",
                    success = true,
                    data = mapOf("number" to number, "inbox" to summary),
                    verificationDetails = summary
                )
            }
        )
    }

    private fun registerGeniusLyrics() {
        ToolRegistry.register(
            ToolDefinition(
                id = "search_song_lyrics",
                name = "Genius Song Lyrics Search",
                description = "Searches Genius for song lyrics, artist name, album info, and track metadata.",
                category = "MEDIA",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val query = args["query"]?.toString() ?: args["song"]?.toString() ?: ""
                if (query.isBlank()) {
                    return@ToolDefinition errorResult("search_song_lyrics", "Song name or query argument is required.")
                }

                val json = fetchJson("lyrics/genius?q=${encode(query)}")
                    ?: return@ToolDefinition errorResult("search_song_lyrics", "Genius lyrics service unavailable.")

                val resultObj = json.optJSONObject("result") ?: json.optJSONObject("data") ?: json
                val title = resultObj.optString("title", query)
                val artist = resultObj.optString("artist", "")
                val lyrics = resultObj.optString("lyrics", "Lyrics preview unavailable.")

                val formatted = "Song: $title by $artist\n\n$lyrics"

                ToolExecutionResult(
                    toolId = "search_song_lyrics",
                    success = true,
                    data = mapOf("title" to title, "artist" to artist, "lyrics" to lyrics),
                    verificationDetails = formatted.take(800)
                )
            }
        )
    }

    private fun registerGithubStalk() {
        ToolRegistry.register(
            ToolDefinition(
                id = "stalk_github_profile",
                name = "GitHub Profile Lookup",
                description = "Fetches complete public developer profile info, bio, repository count, followers, and account details for any GitHub user.",
                category = "DEVELOPER",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val username = args["username"]?.toString() ?: args["user"]?.toString() ?: ""
                if (username.isBlank()) {
                    return@ToolDefinition errorResult("stalk_github_profile", "GitHub username argument is required.")
                }

                val json = fetchJson("githubStalk?user=${encode(username)}")
                    ?: return@ToolDefinition errorResult("stalk_github_profile", "GitHub lookup service unavailable.")

                val user = json.optString("username", username)
                val nickname = json.optString("nickname", user)
                val bio = json.optString("bio", "No bio provided")
                val repos = json.optInt("public_repos", 0)
                val followers = json.optInt("followers", 0)
                val following = json.optInt("following", 0)
                val profileUrl = json.optString("url", "https://github.com/$user")

                val summary = "GitHub Profile: $nickname (@$user)\nBio: $bio\nRepos: $repos | Followers: $followers | Following: $following\nURL: $profileUrl"

                ToolExecutionResult(
                    toolId = "stalk_github_profile",
                    success = true,
                    data = mapOf("username" to user, "summary" to summary),
                    verificationDetails = summary
                )
            }
        )
    }

    private fun registerShortenUrl() {
        ToolRegistry.register(
            ToolDefinition(
                id = "shorten_url",
                name = "URL Shortener",
                description = "Shortens any long URL into a compact, shareable short link.",
                category = "UTILITY",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val url = args["url"]?.toString() ?: ""
                if (url.isBlank()) {
                    return@ToolDefinition errorResult("shorten_url", "URL argument is required.")
                }

                val json = fetchJson("tools/shorturl?url=${encode(url)}")
                    ?: return@ToolDefinition errorResult("shorten_url", "URL shortener service unavailable.")

                val resultObj = json.optJSONObject("result") ?: json
                val shortUrl = resultObj.optString("short", resultObj.optString("short_url", ""))

                if (shortUrl.isBlank()) {
                    return@ToolDefinition errorResult("shorten_url", "Failed to generate short URL.")
                }

                ToolExecutionResult(
                    toolId = "shorten_url",
                    success = true,
                    data = mapOf("original" to url, "short_url" to shortUrl),
                    verificationDetails = "Short URL generated: $shortUrl"
                )
            }
        )
    }

    private fun registerWikipediaSummary() {
        ToolRegistry.register(
            ToolDefinition(
                id = "wikipedia_summary",
                name = "Wikipedia Article Search",
                description = "Searches Wikipedia for concise encyclopedic summaries on any topic, person, historical event, science, or concept.",
                category = "INFORMATION",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val query = args["query"]?.toString() ?: args["q"]?.toString() ?: ""
                if (query.isBlank()) {
                    return@ToolDefinition errorResult("wikipedia_summary", "Search query is required.")
                }

                val json = fetchJson("wikipedia?q=${encode(query)}")
                    ?: return@ToolDefinition errorResult("wikipedia_summary", "Wikipedia search service unavailable.")

                val resultObj = json.optJSONObject("result") ?: json
                val pages = resultObj.optJSONArray("pages") ?: JSONArray()

                var extract = ""
                var title = query

                if (pages.length() > 0) {
                    val page = pages.optJSONObject(0)
                    title = page?.optString("title", query) ?: query
                    extract = page?.optString("description", page.optString("extract", "")) ?: ""
                }

                val summary = if (extract.isNotBlank()) {
                    "$title:\n$extract"
                } else {
                    "No Wikipedia summary found for '$query'."
                }

                ToolExecutionResult(
                    toolId = "wikipedia_summary",
                    success = true,
                    data = mapOf("title" to title, "summary" to summary),
                    verificationDetails = summary
                )
            }
        )
    }

    private fun errorResult(toolId: String, message: String) = ToolExecutionResult(
        toolId = toolId,
        success = false,
        data = mapOf("error" to message),
        verificationDetails = message
    )
}
