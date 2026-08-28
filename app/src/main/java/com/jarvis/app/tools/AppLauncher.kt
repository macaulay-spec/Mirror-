package com.jarvis.app.tools

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Resolves "open X" to a real installed app and launches it.
 *
 * Why this exists: the previous path did `label.contains(query)` and took the FIRST hit.
 * `String.contains("")` is true for everything, so an empty/unmatched query launched
 * whichever app PackageManager happened to return first. This version scores every
 * launchable app and only launches a genuine best match — or nothing at all.
 */
object AppLauncher {

    data class Result(val success: Boolean, val message: String, val packageName: String? = null)

    /** Common names and nicknames -> preferred packages, in priority order. */
    private val nicknames: Map<String, List<String>> = mapOf(
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "whatsapp business" to listOf("com.whatsapp.w4b"),
        "telegram" to listOf("org.telegram.messenger"),
        "instagram" to listOf("com.instagram.android"),
        "facebook" to listOf("com.facebook.katana", "com.facebook.lite"),
        "messenger" to listOf("com.facebook.orca", "com.facebook.mlite"),
        "chrome" to listOf("com.android.chrome"),
        "google" to listOf("com.google.android.googlequicksearchbox", "com.android.chrome"),
        "browser" to listOf("com.android.chrome", "org.mozilla.firefox", "com.android.browser"),
        "youtube" to listOf("com.google.android.youtube"),
        "spotify" to listOf("com.spotify.music"),
        "audiomack" to listOf("com.audiomack"),
        "boomplay" to listOf("com.boomplay.android"),
        "maps" to listOf("com.google.android.apps.maps"),
        "gmail" to listOf("com.google.android.gm"),
        "play store" to listOf("com.android.vending"),
        "twitter" to listOf("com.twitter.android"),
        "x" to listOf("com.twitter.android"),
        "tiktok" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        "snapchat" to listOf("com.snapchat.android"),
        "camera" to listOf("com.android.camera", "com.android.camera2"),
        "settings" to listOf("com.android.settings"),
        "calculator" to listOf("com.google.android.calculator", "com.android.calculator2"),
        "music" to listOf("com.google.android.music", "com.spotify.music"),
        "files" to listOf("com.google.android.apps.nbu.files", "com.android.documentsui"),
        "photos" to listOf("com.google.android.apps.photos"),
        "drive" to listOf("com.google.android.apps.docs"),
        "calendar" to listOf("com.google.android.calendar"),
        "contacts" to listOf("com.android.contacts", "com.google.android.contacts"),
        "messages" to listOf("com.google.android.apps.messaging", "com.android.messaging"),
        "bank" to listOf("com.kuda.mobile", "com.flutter.moniepoint", "com.palmpay.android", "com.opay")
    )

    /** Words that carry no meaning when the user says "open the Chrome app please". */
    private val noise = setOf("open", "launch", "start", "please", "app", "application", "the", "my", "for", "me")

    fun launch(context: Context, rawQuery: String): Result {
        val lowerRaw = rawQuery.lowercase().trim()

        // Check if query is a compound search command (e.g., "chrome and search for a book" or "search for a book")
        if (lowerRaw.contains("search for ") || lowerRaw.contains("search ") || (lowerRaw.contains("chrome") && lowerRaw.contains("book"))) {
            val query = if (lowerRaw.contains("search for ")) lowerRaw.substringAfter("search for ").trim()
                        else if (lowerRaw.contains("search ")) lowerRaw.substringAfter("search ").trim()
                        else rawQuery.removePrefix("open").removePrefix("chrome").trim()
            if (query.isNotBlank()) {
                val searchIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8")))
                searchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return try {
                    context.startActivity(searchIntent)
                    Result(true, "Searching Google for '$query'.")
                } catch (_: Exception) {
                    Result(false, "Could not open browser for search.")
                }
            }
        }

        val query = normalize(rawQuery)
        if (query.isBlank()) return Result(false, "Which app should I open?")

        // 1. Known nickname ("chrome", "the bank app" -> bank)
        val known = nicknames[query]
            ?: nicknames.entries.firstOrNull { entry -> query.contains(entry.key) }?.value
        if (known != null) {
            for (pkg in known) {
                if (startPackage(context, pkg)) {
                    val label = appLabel(context, pkg) ?: query
                    return Result(true, "Opened $label.", pkg)
                }
            }
        }

        // 2. A full package name was given
        val looksLikePackage = query.contains(".") && !query.contains(" ")
        if (looksLikePackage && startPackage(context, query)) {
            return Result(true, "Opened ${appLabel(context, query) ?: query}.", query)
        }

        // 3. Standard system intents
        systemIntent(query)?.let { intent ->
            if (intent.resolveActivity(context.packageManager) != null) {
                return try {
                    context.startActivity(intent)
                    Result(true, "Opened $query.")
                } catch (_: Exception) {
                    Result(false, "I couldn't open $query.")
                }
            }
        }

        // 4. Best-scoring installed launcher
        val best = bestMatch(context, query)
        if (best != null && startPackage(context, best.first)) {
            return Result(true, "Opened ${best.second}.", best.first)
        }

        // 5. Fallback: If not found as an app, search web
        val searchIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=" + java.net.URLEncoder.encode(rawQuery, "UTF-8")))
        searchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(searchIntent)
            Result(true, "Opened search for '$rawQuery'.")
        } catch (_: Exception) {
            Result(false, "I couldn't find an app called \"$rawQuery\" on this phone.")
        }
    }

    fun resolve(context: Context, rawQuery: String): String? {
        val query = normalize(rawQuery)
        if (query.isBlank()) return null
        val known = nicknames[query]
            ?: nicknames.entries.firstOrNull { entry -> query.contains(entry.key) }?.value
        known?.firstOrNull { isInstalled(context, it) }?.let { return it }
        if (looksLikePackage(query) && isInstalled(context, query)) return query
        return bestMatch(context, query)?.first
    }

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getApplicationInfo(pkg, 0) != null
    } catch (_: Exception) {
        false
    }

    fun appLabel(context: Context, pkg: String): String? = try {
        val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
        context.packageManager.getApplicationLabel(appInfo).toString()
    } catch (_: Exception) {
        null
    }

    private fun looksLikePackage(query: String) = query.contains(".") && !query.contains(" ")

    private fun bestMatch(context: Context, query: String): Pair<String, String>? {
        val pm = context.packageManager
        val launchables = try {
            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
            )
        } catch (_: Exception) {
            return null
        }

        var best: Pair<String, String>? = null
        var bestScore = 0
        for (ri in launchables) {
            val pkg = ri.activityInfo.packageName
            val label = ri.loadLabel(pm).toString()
            val score = score(label, pkg, query)
            if (score > bestScore) {
                bestScore = score
                best = pkg to label
            }
        }
        return if (bestScore >= 40) best else null
    }

    /**
     * 100 exact · 85 prefix · 80 whole word inside the label · 65 substring ·
     * 55 package tail prefix · 40 package substring. Anything below 40 is not a match.
     */
    private fun score(label: String, pkg: String, query: String): Int {
        val l = label.lowercase()
        val p = pkg.lowercase()
        val q = query.lowercase()
        return when {
            l == q || p == q -> 100
            l.startsWith(q) -> 85
            l.split(" ").any { it == q } -> 80
            l.contains(q) -> 65
            p.substringAfterLast('.').startsWith(q) -> 55
            p.contains(q) -> 40
            else -> 0
        }
    }

    private fun systemIntent(q: String): Intent? {
        val intent = when {
            q.contains("camera") -> Intent("android.media.action.IMAGE_CAPTURE")
            q.contains("setting") -> Intent(Settings.ACTION_SETTINGS)
            q.contains("calculator") -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALCULATOR)
            q.contains("map") -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS)
            q.contains("music") -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
            q.contains("browser") || q.contains("internet") -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER)
            q.contains("calendar") -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
            q.contains("contact") -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS)
            q.contains("gallery") || q.contains("photo") -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY)
            else -> null
        }
        return intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun startPackage(context: Context, pkg: String): Boolean = try {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }

    private fun normalize(raw: String): String {
        var text = raw.lowercase().trim()
        text = text.trim(*charArrayOf('.', '!', '?', ','))
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() && it !in noise }
        return words.joinToString(" ").trim()
    }
}
