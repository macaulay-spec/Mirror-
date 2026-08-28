package com.jarvis.app.tools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Resolves a spoken name or nickname ("mumsi", "tunde") to real phone numbers.
 *
 * Returns every number the contact has, so the caller can disambiguate
 * ("home or mobile?") instead of silently guessing.
 */
object ContactsResolver {

    data class Candidate(
        val name: String,
        val number: String,
        val typeLabel: String
    ) {
        val lastFour: String get() = number.takeLast(4)
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    fun find(context: Context, query: String): List<Candidate> {
        if (query.isBlank() || !hasPermission(context)) return emptyList()

        val found = LinkedHashMap<String, Candidate>()
        val normalized = query.trim().lowercase()

        // 1. Match on display name
        queryPhones(context) { name, number, type ->
            if (name.lowercase().contains(normalized)) {
                found[number] = Candidate(name, number, typeLabel(type))
            }
        }

        // 2. Fall back to nicknames ("mumsi", "t-boy")
        if (found.isEmpty()) {
            val nicknames = nicknamesFor(context, normalized)
            if (nicknames.isNotEmpty()) {
                queryPhones(context) { name, number, type ->
                    if (nicknames.any { name.equals(it, ignoreCase = true) }) {
                        found[number] = Candidate(name, number, typeLabel(type))
                    }
                }
            }
        }

        return found.values.toList()
    }

    /** Recent calls, most recent first — powers "who called me?" and "call her back". */
    fun recentCalls(context: Context, limit: Int = 10): List<CallRecord> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) !=
            PackageManager.PERMISSION_GRANTED
        ) return emptyList()

        val out = ArrayList<CallRecord>()
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE
        )
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, projection, null, null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    out.add(
                        CallRecord(
                            number = c.getString(0) ?: continue,
                            name = c.getString(1),
                            type = callTypeLabel(c.getInt(2)),
                            whenMs = c.getLong(3)
                        )
                    )
                }
                out
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    data class CallRecord(val number: String, val name: String?, val type: String, val whenMs: Long)

    private fun queryPhones(context: Context, onEach: (name: String, number: String, type: Int) -> Unit) {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE
        )
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val number = c.getString(1) ?: continue
                    onEach(name, number, c.getInt(2))
                }
            }
        } catch (_: Exception) { }
    }

    private fun nicknamesFor(context: Context, query: String): List<String> {
        val out = ArrayList<String>()
        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Nickname.NAME,
            ContactsContract.Data.DISPLAY_NAME
        )
        val selection = "${ContactsContract.Data.MIMETYPE} = ?"
        val args = arrayOf(ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE)
        return try {
            context.contentResolver.query(uri, projection, selection, args, null)?.use { c ->
                while (c.moveToNext()) {
                    val nick = c.getString(0) ?: continue
                    val display = c.getString(1) ?: continue
                    if (nick.lowercase().contains(query)) out.add(display)
                }
                out
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun typeLabel(type: Int): String = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "home"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "work"
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "main"
        else -> "other"
    }

    private fun callTypeLabel(type: Int): String = when (type) {
        CallLog.Calls.INCOMING_TYPE -> "incoming"
        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
        CallLog.Calls.MISSED_TYPE -> "missed"
        else -> "other"
    }
}
