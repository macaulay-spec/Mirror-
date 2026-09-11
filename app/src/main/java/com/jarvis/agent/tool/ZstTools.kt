package com.jarvis.agent.tool

import android.content.Context
import com.jarvis.app.config.ApiConfig
import com.jarvis.core.model.RiskLevel
import com.jarvis.core.model.ToolExecutionResult
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * ZST Labs multi-utility integration suite.
 * Connects JARVIS to live financial, security, networking, developer, and life tools.
 */
object ZstTools {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun registerAll() {
        registerCryptoPrices()
        registerCurrencyConvert()
        registerTempEmail()
        registerCheckTempEmail()
        registerGithubLookup()
        registerPackageLookup()
        registerDnsLookup()
        registerSslCheck()
        registerPasswordGenerator()
        registerMathEval()
        registerStackOverflowSearch()
        registerBookSearch()
        registerFootballLive()
        registerEplStandings()
        registerFindRecipe()
        registerIpLookup()
        registerRandomFact()
    }

    // ------------------------------------------------------------ Crypto
    private fun registerCryptoPrices() {
        ToolRegistry.register(
            ToolDefinition(
                id = "crypto_prices",
                name = "Crypto Prices",
                description = "Fetches live cryptocurrency prices, market caps, and 24-hour price changes for major coins (BTC, ETH, SOL, etc.).",
                category = "FINANCE",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val limit = (args["limit"]?.toString()?.toIntOrNull() ?: 5).coerceIn(1, 20)
                val symbol = args["symbol"]?.toString()?.trim()?.uppercase()

                val endpoint = if (!symbol.isNullOrBlank()) {
                    "crypto?symbol=$symbol"
                } else {
                    "crypto?limit=$limit"
                }

                val json = fetchJson(endpoint)
                    ?: return@ToolDefinition errorResult("crypto_prices", "Unable to fetch live cryptocurrency data from provider.")

                val data = json.optJSONObject("data") ?: json
                val coins = data.optJSONArray("coins") ?: JSONArray()

                val formattedList = mutableListOf<String>()
                for (i in 0 until coins.length()) {
                    val coin = coins.optJSONObject(i) ?: continue
                    val name = coin.optString("name")
                    val sym = coin.optString("symbol")
                    val price = coin.optDouble("priceUsd", coin.optDouble("price", 0.0))
                    val change24h = coin.optDouble("changePercent24Hr", coin.optDouble("change24h", 0.0))
                    val sign = if (change24h >= 0) "+" else ""
                    formattedList.add("$name ($sym): $${String.format(java.util.Locale.US, "%,.2f", price)} ($sign${String.format(java.util.Locale.US, "%.2f", change24h)}%)")
                }

                val summary = if (formattedList.isNotEmpty()) {
                    formattedList.joinToString("\n")
                } else {
                    "No crypto assets found for the requested query."
                }

                ToolExecutionResult(
                    toolId = "crypto_prices",
                    success = true,
                    data = mapOf("summary" to summary, "coins_count" to formattedList.size),
                    verificationDetails = summary
                )
            }
        )
    }

    // ------------------------------------------------------------ Currency
    private fun registerCurrencyConvert() {
        ToolRegistry.register(
            ToolDefinition(
                id = "currency_convert",
                name = "Convert Currency",
                description = "Converts money between global currencies (e.g. USD to EUR, NGN, GBP, CAD) with real-time foreign exchange rates.",
                category = "FINANCE",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val from = (args["from"]?.toString() ?: args["base"]?.toString() ?: "USD").trim().uppercase().take(3)
                val to = (args["to"]?.toString() ?: args["target"]?.toString() ?: "EUR").trim().uppercase().take(3)
                val amount = args["amount"]?.toString()?.replace(",", "")?.toDoubleOrNull() ?: 1.0

                val endpoint = "currency/convert?from=$from&to=$to&amount=$amount"
                val json = fetchJson(endpoint)

                if (json != null) {
                    val data = json.optJSONObject("data") ?: json
                    val converted = data.optDouble("converted", 0.0)
                    val rate = data.optDouble("rate", 0.0)

                    if (converted > 0.0 || rate > 0.0) {
                        val formatted = String.format(java.util.Locale.US, "%,.2f %s = %,.2f %s (Rate: 1 %s = %.4f %s)", amount, from, converted, to, from, rate, to)
                        return@ToolDefinition ToolExecutionResult(
                            toolId = "currency_convert",
                            success = true,
                            data = mapOf("formatted" to formatted, "rate" to rate, "converted" to converted),
                            verificationDetails = formatted
                        )
                    }
                }

                // Fallback to KnowledgeTools currency conversion if needed
                errorResult("currency_convert", "Could not convert $amount from $from to $to right now.")
            }
        )
    }

    // ------------------------------------------------------------ Temp Email
    private fun registerTempEmail() {
        ToolRegistry.register(
            ToolDefinition(
                id = "generate_temp_email",
                name = "Generate Burner Email",
                description = "Generates a disposable, temporary email address complete with an access token to check for incoming messages or verification codes.",
                category = "UTILITY",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, _ ->
                val json = fetchJson("tempgen/generate")
                    ?: return@ToolDefinition errorResult("generate_temp_email", "Failed to generate temporary email address.")

                val data = json.optJSONObject("data") ?: json
                val email = data.optString("email")
                val token = data.optString("token")
                val expires = data.optInt("expiresInMinutes", 60)

                if (email.isBlank()) {
                    return@ToolDefinition errorResult("generate_temp_email", "Temporary email service returned an empty address.")
                }

                val details = "Generated Burner Email: $email (Valid for ~$expires min).\nToken saved for checking inbox."
                ToolExecutionResult(
                    toolId = "generate_temp_email",
                    success = true,
                    data = mapOf(
                        "email" to email,
                        "token" to token,
                        "expiresInMinutes" to expires
                    ),
                    verificationDetails = details
                )
            }
        )
    }

    private fun registerCheckTempEmail() {
        ToolRegistry.register(
            ToolDefinition(
                id = "check_temp_email",
                name = "Check Burner Email Inbox",
                description = "Checks for incoming emails and OTP verification codes on a temporary email address using its token.",
                category = "UTILITY",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val token = args["token"]?.toString()?.trim()
                if (token.isNullOrBlank()) {
                    return@ToolDefinition errorResult("check_temp_email", "Token is required to check temporary email inbox.")
                }

                val endpoint = "tempgen/inbox?token=" + URLEncoder.encode(token, "UTF-8")
                val json = fetchJson(endpoint)
                    ?: return@ToolDefinition errorResult("check_temp_email", "Unable to retrieve inbox messages.")

                val data = json.optJSONObject("data") ?: json
                val messages = data.optJSONArray("messages") ?: JSONArray()

                if (messages.length() == 0) {
                    return@ToolDefinition ToolExecutionResult(
                        toolId = "check_temp_email",
                        success = true,
                        data = mapOf("count" to 0),
                        verificationDetails = "Inbox is currently empty. No messages received yet."
                    )
                }

                val messageSummaries = mutableListOf<String>()
                for (i in 0 until messages.length()) {
                    val m = messages.optJSONObject(i) ?: continue
                    val from = m.optString("from")
                    val subject = m.optString("subject")
                    val intro = m.optString("intro", m.optString("text", ""))
                    messageSummaries.add("From: $from\nSubject: $subject\nPreview: $intro")
                }

                val details = "Found ${messages.length()} message(s):\n\n" + messageSummaries.joinToString("\n\n---\n\n")
                ToolExecutionResult(
                    toolId = "check_temp_email",
                    success = true,
                    data = mapOf("count" to messages.length(), "messages" to details),
                    verificationDetails = details
                )
            }
        )
    }

    // ------------------------------------------------------------ GitHub
    private fun registerGithubLookup() {
        ToolRegistry.register(
            ToolDefinition(
                id = "github_lookup",
                name = "GitHub Profile Lookup",
                description = "Looks up a software developer's profile on GitHub, including public repositories, followers, bio, and company.",
                category = "DEVELOPER",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val username = (args["username"] ?: args["user"] ?: args["q"])?.toString()?.trim()
                if (username.isNullOrBlank()) {
                    return@ToolDefinition errorResult("github_lookup", "Please specify a GitHub username.")
                }

                val endpoint = "stalker/github?username=" + URLEncoder.encode(username, "UTF-8")
                val json = fetchJson(endpoint)
                    ?: return@ToolDefinition errorResult("github_lookup", "Could not find GitHub user '$username'.")

                val data = json.optJSONObject("data") ?: json
                val name = data.optString("name", username)
                val bio = data.optString("bio", "No bio provided")
                val repos = data.optInt("public_repos", data.optInt("repos", 0))
                val followers = data.optInt("followers", 0)
                val following = data.optInt("following", 0)
                val company = data.optString("company", "N/A")
                val location = data.optString("location", "N/A")
                val url = data.optString("html_url", "https://github.com/$username")

                val summary = """
                    GitHub Profile: $name (@$username)
                    Bio: $bio
                    Public Repos: $repos | Followers: $followers | Following: $following
                    Company: $company | Location: $location
                    URL: $url
                """.trimIndent()

                ToolExecutionResult(
                    toolId = "github_lookup",
                    success = true,
                    data = mapOf("username" to username, "summary" to summary, "url" to url),
                    verificationDetails = summary
                )
            }
        )
    }

    // ------------------------------------------------------------ Package Lookup
    private fun registerPackageLookup() {
        ToolRegistry.register(
            ToolDefinition(
                id = "package_lookup",
                name = "NPM/PyPI Package Lookup",
                description = "Looks up an NPM or PyPI software library package to check its latest version, license, description, and author.",
                category = "DEVELOPER",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val pkg = (args["package_name"] ?: args["package"] ?: args["name"])?.toString()?.trim()
                val registry = (args["registry"] ?: args["type"])?.toString()?.trim()?.lowercase() ?: "npm"

                if (pkg.isNullOrBlank()) {
                    return@ToolDefinition errorResult("package_lookup", "Please specify a package name.")
                }

                val endpoint = if (registry == "pypi" || registry == "python") {
                    "stalker/pypi?package=" + URLEncoder.encode(pkg, "UTF-8")
                } else {
                    "stalker/npm?package=" + URLEncoder.encode(pkg, "UTF-8")
                }

                val json = fetchJson(endpoint)
                    ?: return@ToolDefinition errorResult("package_lookup", "Package '$pkg' was not found on $registry.")

                val data = json.optJSONObject("data") ?: json
                val name = data.optString("name", pkg)
                val version = data.optString("version", "unknown")
                val desc = data.optString("description", data.optString("summary", "No description"))
                val license = data.optString("license", "Unknown")
                val author = data.optString("author", "N/A")

                val summary = """
                    Package: $name ($registry)
                    Version: $version | License: $license
                    Author: $author
                    Description: $desc
                """.trimIndent()

                ToolExecutionResult(
                    toolId = "package_lookup",
                    success = true,
                    data = mapOf("package" to name, "version" to version, "registry" to registry),
                    verificationDetails = summary
                )
            }
        )
    }

    // ------------------------------------------------------------ DNS Lookup
    private fun registerDnsLookup() {
        ToolRegistry.register(
            ToolDefinition(
                id = "dns_lookup",
                name = "DNS Lookup",
                description = "Queries DNS records (A, AAAA, MX, NS) for any domain name.",
                category = "DEVELOPER",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val domain = (args["domain"] ?: args["host"] ?: args["url"])?.toString()?.trim()
                    ?.removePrefix("https://")?.removePrefix("http://")?.trimEnd('/')
                if (domain.isNullOrBlank()) {
                    return@ToolDefinition errorResult("dns_lookup", "Please specify a domain name.")
                }

                val type = args["type"]?.toString()?.trim()?.uppercase() ?: "A"
                val endpoint = "tools/dns?domain=" + URLEncoder.encode(domain, "UTF-8") + "&type=$type"
                val json = fetchJson(endpoint)
                    ?: return@ToolDefinition errorResult("dns_lookup", "DNS query failed for '$domain'.")

                val data = json.optJSONObject("data") ?: json
                val recordsA = data.optJSONArray("A")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList()
                val recordsMx = data.optJSONArray("MX")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList()

                val summary = "DNS Records for $domain:\n" +
                    (if (recordsA.isNotEmpty()) "A: ${recordsA.joinToString(", ")}\n" else "") +
                    (if (recordsMx.isNotEmpty()) "MX: ${recordsMx.joinToString(", ")}\n" else "") +
                    "Status: Resolved successfully."

                ToolExecutionResult(
                    toolId = "dns_lookup",
                    success = true,
                    data = mapOf("domain" to domain, "recordsA" to recordsA, "recordsMx" to recordsMx),
                    verificationDetails = summary
                )
            }
        )
    }

    // ------------------------------------------------------------ SSL Check
    private fun registerSslCheck() {
        ToolRegistry.register(
            ToolDefinition(
                id = "ssl_check",
                name = "SSL Certificate Check",
                description = "Inspects the SSL/TLS certificate security grade, validity, and warnings for a website.",
                category = "DEVELOPER",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val domain = (args["domain"] ?: args["host"])?.toString()?.trim()
                    ?.removePrefix("https://")?.removePrefix("http://")?.trimEnd('/')
                if (domain.isNullOrBlank()) {
                    return@ToolDefinition errorResult("ssl_check", "Please specify a domain name.")
                }

                val endpoint = "tools/ssl?domain=" + URLEncoder.encode(domain, "UTF-8")
                val json = fetchJson(endpoint)
                    ?: return@ToolDefinition errorResult("ssl_check", "SSL certificate check failed for '$domain'.")

                val data = json.optJSONObject("data") ?: json
                val grade = data.optString("grade", "A")
                val status = data.optString("status", "Valid")
                val warnings = data.optBoolean("hasWarnings", false)

                val summary = "SSL Certificate for $domain: Status: $status | Grade: $grade | Warnings: ${if (warnings) "Yes" else "None"}"

                ToolExecutionResult(
                    toolId = "ssl_check",
                    success = true,
                    data = mapOf("domain" to domain, "grade" to grade, "status" to status),
                    verificationDetails = summary
                )
            }
        )
    }

    // ------------------------------------------------------------ Password Generator
    private fun registerPasswordGenerator() {
        ToolRegistry.register(
            ToolDefinition(
                id = "generate_password",
                name = "Generate Password",
                description = "Generates a cryptographically strong, randomized password with custom length and symbol configuration.",
                category = "UTILITY",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val length = (args["length"]?.toString()?.toIntOrNull() ?: 16).coerceIn(8, 64)
                val symbols = args["symbols"]?.toString()?.toBooleanStrictOrNull() ?: true

                val endpoint = "tools/password?length=$length&symbols=$symbols"
                val json = fetchJson(endpoint)
                    ?: return@ToolDefinition errorResult("generate_password", "Failed to generate password from provider.")

                val data = json.optJSONObject("data") ?: json
                val password = data.optString("password", data.optString("result", ""))

                if (password.isBlank()) {
                    return@ToolDefinition errorResult("generate_password", "Provider returned empty password.")
                }

                ToolExecutionResult(
                    toolId = "generate_password",
                    success = true,
                    data = mapOf("password" to password, "length" to length),
                    verificationDetails = "Generated secure password ($length chars): $password"
                )
            }
        )
    }

    // ------------------------------------------------------------ Math Evaluator
    private fun registerMathEval() {
        ToolRegistry.register(
            ToolDefinition(
                id = "math_eval",
                name = "Math Expression Calculator",
                description = "Evaluates mathematical expressions, including arithmetic, square roots, percentages, and powers.",
                category = "UTILITY",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val expr = (args["expression"] ?: args["expr"] ?: args["query"])?.toString()?.trim()
                if (expr.isNullOrBlank()) {
                    return@ToolDefinition errorResult("math_eval", "Please specify a mathematical expression.")
                }

                val endpoint = "math/calculate?expr=" + URLEncoder.encode(expr, "UTF-8")
                val json = fetchJson(endpoint)
                    ?: return@ToolDefinition errorResult("math_eval", "Could not calculate expression '$expr'.")

                val data = json.optJSONObject("data") ?: json
                val result = data.opt("result")?.toString() ?: data.opt("rounded")?.toString() ?: "N/A"

                val summary = "Expression: $expr = $result"
                ToolExecutionResult(
                    toolId = "math_eval",
                    success = true,
                    data = mapOf("expression" to expr, "result" to result),
                    verificationDetails = summary
                )
            }
        )
    }

    // ------------------------------------------------------------ StackOverflow
    private fun registerStackOverflowSearch() {
        ToolRegistry.register(
            ToolDefinition(
                id = "stackoverflow_search",
                name = "StackOverflow Search",
                description = "Searches StackOverflow for developer questions, programming solutions, and answered bugs.",
                category = "DEVELOPER",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val query = (args["query"] ?: args["q"])?.toString()?.trim()
                if (query.isNullOrBlank()) {
                    return@ToolDefinition errorResult("stackoverflow_search", "Please provide a programming query.")
                }

                val endpoint = "search/stackoverflow?q=" + URLEncoder.encode(query, "UTF-8")
                val json = fetchJson(endpoint)
                    ?: return@ToolDefinition errorResult("stackoverflow_search", "StackOverflow search failed for '$query'.")

                val data = json.optJSONObject("data") ?: json
                val questions = data.optJSONArray("questions") ?: JSONArray()

                val formatted = mutableListOf<String>()
                for (i in 0 until minOf(questions.length(), 4)) {
                    val q = questions.optJSONObject(i) ?: continue
                    val title = q.optString("title")
                    val isAnswered = q.optBoolean("is_answered", false)
                    val score = q.optInt("score", 0)
                    val link = q.optString("link")
                    formatted.add("- $title (Score: $score, Answered: $isAnswered)\n  Link: $link")
                }

                val summary = if (formatted.isNotEmpty()) {
                    "StackOverflow Solutions for \"$query\":\n" + formatted.joinToString("\n")
                } else {
                    "No StackOverflow results found for \"$query\"."
                }

                ToolExecutionResult(
                    toolId = "stackoverflow_search",
                    success = true,
                    data = mapOf("query" to query, "results" to summary),
                    verificationDetails = summary
                )
            }
        )
    }

    // ------------------------------------------------------------ Book Search
    private fun registerBookSearch() {
        ToolRegistry.register(
            ToolDefinition(
                id = "book_search",
                name = "Book Search",
                description = "Searches for literature, books, author names, publishers, and book summaries.",
                category = "INFORMATION",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val query = (args["query"] ?: args["q"])?.toString()?.trim()
                if (query.isNullOrBlank()) {
                    return@ToolDefinition errorResult("book_search", "Please specify a book title or author.")
                }

                val endpoint = "search/book?q=" + URLEncoder.encode(query, "UTF-8")
                val json = fetchJson(endpoint)
                    ?: return@ToolDefinition errorResult("book_search", "Could not find books matching '$query'.")

                val data = json.optJSONObject("data") ?: json
                val books = data.optJSONArray("books") ?: JSONArray()

                val formatted = mutableListOf<String>()
                for (i in 0 until minOf(books.length(), 3)) {
                    val b = books.optJSONObject(i) ?: continue
                    val title = b.optString("title")
                    val authors = b.optString("authors", b.optJSONArray("authors")?.join(", ") ?: "Unknown")
                    val desc = b.optString("description", "").take(140)
                    formatted.add("• \"$title\" by $authors\n  $desc...")
                }

                val summary = if (formatted.isNotEmpty()) {
                    "Book Search Results for \"$query\":\n" + formatted.joinToString("\n\n")
                } else {
                    "No books found for \"$query\"."
                }

                ToolExecutionResult(
                    toolId = "book_search",
                    success = true,
                    data = mapOf("query" to query, "summary" to summary),
                    verificationDetails = summary
                )
            }
        )
    }

    // ------------------------------------------------------------ Football Live & EPL
    private fun registerFootballLive() {
        ToolRegistry.register(
            ToolDefinition(
                id = "football_live_scores",
                name = "Football Live Scores",
                description = "Fetches live football (soccer) scores, matches, and scoreboard for Premier League, Champions League, La Liga, etc.",
                category = "INFORMATION",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val league = (args["league"] ?: args["competition"])?.toString()?.trim() ?: "eng.1"
                val endpoint = "football/scoreboard?league=" + URLEncoder.encode(league, "UTF-8")
                val json = fetchJson(endpoint)
                    ?: return@ToolDefinition errorResult("football_live_scores", "Unable to fetch live scoreboard.")

                val data = json.optJSONObject("data") ?: json
                val matches = data.optJSONArray("matches") ?: data.optJSONArray("events") ?: JSONArray()

                val formatted = mutableListOf<String>()
                for (i in 0 until minOf(matches.length(), 6)) {
                    val m = matches.optJSONObject(i) ?: continue
                    val home = m.optString("homeTeam", m.optJSONObject("home")?.optString("name", "Home"))
                    val away = m.optString("awayTeam", m.optJSONObject("away")?.optString("name", "Away"))
                    val score = m.optString("score", "${m.optInt("homeScore", 0)} - ${m.optInt("awayScore", 0)}")
                    val status = m.optString("status", "FT")
                    formatted.add("$home $score $away ($status)")
                }

                val summary = if (formatted.isNotEmpty()) {
                    "Live Football Scores ($league):\n" + formatted.joinToString("\n")
                } else {
                    "No live matches currently in progress for $league."
                }

                ToolExecutionResult(
                    toolId = "football_live_scores",
                    success = true,
                    data = mapOf("league" to league, "summary" to summary),
                    verificationDetails = summary
                )
            }
        )
    }

    private fun registerEplStandings() {
        ToolRegistry.register(
            ToolDefinition(
                id = "epl_standings",
                name = "Premier League Standings",
                description = "Retrieves current English Premier League table, standings, points, and goal differences.",
                category = "INFORMATION",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, _ ->
                val json = fetchJson("sports/football/epl/standings")
                    ?: return@ToolDefinition errorResult("epl_standings", "Unable to load Premier League standings.")

                val data = json.optJSONObject("data") ?: json
                val teams = data.optJSONArray("standings") ?: data.optJSONArray("table") ?: JSONArray()

                val formatted = mutableListOf<String>()
                for (i in 0 until minOf(teams.length(), 6)) {
                    val t = teams.optJSONObject(i) ?: continue
                    val pos = t.optInt("position", i + 1)
                    val name = t.optString("team", t.optJSONObject("team")?.optString("name", "Team"))
                    val pts = t.optInt("points", 0)
                    val played = t.optInt("played", 0)
                    formatted.add("$pos. $name - $pts pts (Played: $played)")
                }

                val summary = if (formatted.isNotEmpty()) {
                    "Top Premier League Standings:\n" + formatted.joinToString("\n")
                } else {
                    "Premier League table is updating or currently unavailable."
                }

                ToolExecutionResult(
                    toolId = "epl_standings",
                    success = true,
                    data = mapOf("summary" to summary),
                    verificationDetails = summary
                )
            }
        )
    }

    // ------------------------------------------------------------ Recipe
    private fun registerFindRecipe() {
        ToolRegistry.register(
            ToolDefinition(
                id = "find_recipe",
                name = "Find Recipe",
                description = "Searches for culinary recipes, required ingredients, and cooking instructions for meals and desserts.",
                category = "INFORMATION",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val query = (args["query"] ?: args["q"] ?: args["dish"])?.toString()?.trim()

                val endpoint = if (!query.isNullOrBlank()) {
                    "recipes/search?q=" + URLEncoder.encode(query, "UTF-8")
                } else {
                    "recipes/random"
                }

                val json = fetchJson(endpoint)
                    ?: return@ToolDefinition errorResult("find_recipe", "Could not find recipe.")

                val data = json.optJSONObject("data") ?: json
                val recipe = if (data.has("recipes")) {
                    data.optJSONArray("recipes")?.optJSONObject(0) ?: data
                } else {
                    data
                }

                val name = recipe.optString("name", recipe.optString("strMeal", "Recipe"))
                val category = recipe.optString("category", recipe.optString("strCategory", "General"))
                val instructions = recipe.optString("instructions", recipe.optString("strInstructions", "")).take(500)

                val summary = "Recipe: $name ($category)\nInstructions:\n$instructions..."

                ToolExecutionResult(
                    toolId = "find_recipe",
                    success = true,
                    data = mapOf("name" to name, "category" to category, "instructions" to instructions),
                    verificationDetails = summary
                )
            }
        )
    }

    // ------------------------------------------------------------ IP Lookup
    private fun registerIpLookup() {
        ToolRegistry.register(
            ToolDefinition(
                id = "ip_lookup",
                name = "IP Geolocation Lookup",
                description = "Looks up IP geolocation details including country, city, region, ISP, and timezone.",
                category = "INFORMATION",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val ip = args["ip"]?.toString()?.trim()
                val endpoint = if (!ip.isNullOrBlank()) {
                    "ip?ip=" + URLEncoder.encode(ip, "UTF-8")
                } else {
                    "ip/me"
                }

                val json = fetchJson(endpoint)
                    ?: return@ToolDefinition errorResult("ip_lookup", "Unable to perform IP lookup.")

                val data = json.optJSONObject("data") ?: json
                val resolvedIp = data.optString("ip", ip ?: "Self")
                val country = data.optString("country", "Unknown")
                val city = data.optString("city", "Unknown")
                val region = data.optString("region", "Unknown")
                val isp = data.optString("isp", data.optString("org", "N/A"))

                val summary = "IP Geolocation for $resolvedIp:\nLocation: $city, $region, $country\nISP / Org: $isp"

                ToolExecutionResult(
                    toolId = "ip_lookup",
                    success = true,
                    data = mapOf("ip" to resolvedIp, "country" to country, "city" to city),
                    verificationDetails = summary
                )
            }
        )
    }

    // ------------------------------------------------------------ Random Fact
    private fun registerRandomFact() {
        ToolRegistry.register(
            ToolDefinition(
                id = "random_fact",
                name = "Random Fact",
                description = "Fetches a fascinating, verified trivia fact across science, history, nature, or technology.",
                category = "INFORMATION",
                riskLevel = RiskLevel.LEVEL_0
            ) { _, args ->
                val category = args["category"]?.toString()?.trim()
                val endpoint = if (!category.isNullOrBlank()) {
                    "facts/random?category=" + URLEncoder.encode(category, "UTF-8")
                } else {
                    "facts/random"
                }

                val json = fetchJson(endpoint)
                    ?: return@ToolDefinition errorResult("random_fact", "Unable to fetch random fact.")

                val data = json.optJSONObject("data") ?: json
                val fact = data.optString("text", data.optString("fact", "Did you know? Light travels at ~300,000 km/s."))
                val cat = data.optString("category", category ?: "general")

                val summary = "Fact ($cat): $fact"

                ToolExecutionResult(
                    toolId = "random_fact",
                    success = true,
                    data = mapOf("fact" to fact, "category" to cat),
                    verificationDetails = summary
                )
            }
        )
    }

    // ------------------------------------------------------------ Helpers
    private fun fetchJson(path: String): JSONObject? {
        val url = if (path.startsWith("http")) path else "${ApiConfig.ZST_BASE_URL}/$path"
        val request = Request.Builder()
            .url(url)
            .header("x-api-key", ApiConfig.ZST_API_KEY)
            .header("User-Agent", "Mozilla/5.0 (Android; JARVIS Assistant)")
            .header("Accept", "application/json")
            .get()
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                JSONObject(body)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun errorResult(toolId: String, msg: String): ToolExecutionResult =
        ToolExecutionResult(
            toolId = toolId,
            success = false,
            data = null,
            error = msg
        )
}
