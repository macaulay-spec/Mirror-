package com.jarvis.app.people

import android.content.Context
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import com.jarvis.app.memory.AppDatabase
import com.jarvis.app.memory.PersonEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The people JARVIS knows.
 *
 * Seeded automatically from the address book — names, every number, nicknames and the
 * phone's own "relation" field (mother, father, spouse...). JARVIS only ever asks about a
 * person it genuinely cannot resolve, and then once, with a picker.
 */
object PeopleGraph {

    data class PhoneNumber(val value: String, val type: String)

    data class Match(
        val person: PersonEntity,
        val score: Int,
        val numbers: List<PhoneNumber>
    )

    private val RELATION_WORDS = mapOf(
        "mum" to "mother", "mom" to "mother", "mummy" to "mother", "mommy" to "mother",
        "mother" to "mother", "father" to "father", "dad" to "father", "daddy" to "father",
        "wife" to "wife", "husband" to "husband", "brother" to "brother", "bro" to "brother",
        "sister" to "sister", "sis" to "sister", "son" to "son", "daughter" to "daughter",
        "friend" to "friend", "boss" to "boss", "girlfriend" to "girlfriend",
        "boyfriend" to "boyfriend"
    )

    private val FILLER = setOf("my", "the", "please", "up", "call", "text", "message", "sms", "to", "a")

    // ------------------------------------------------------------------ sync

    /** Imports the address book. Safe to call repeatedly; learned nicknames survive. */
    suspend fun syncFromContacts(context: Context): Int = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) return@withContext 0

        val db = AppDatabase.get(context)
        val existing = db.personDao().all().associateBy { it.lookupKey }.toMutableMap()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.LABEL
        )

        val grouped = LinkedHashMap<String, Triple<String, MutableList<PhoneNumber>, String>>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val number = c.getString(1) ?: continue
                    val type = c.getInt(2)
                    val key = c.getString(3) ?: "$name|$number"
                    val entry = grouped.getOrPut(key) { Triple(name, mutableListOf(), key) }
                    entry.second.add(PhoneNumber(number, typeLabel(type, c.getString(4))))
                }
            }
        } catch (_: Exception) {
            return@withContext 0
        }

        var imported = 0
        for ((key, triple) in grouped) {
            val (name, numbers) = triple
            val numbersJson = JSONArray()
            numbers.distinctBy { it.value }.forEach {
                numbersJson.put(JSONObject().put("number", it.value).put("type", it.type))
            }
            val previous = existing[key]
            val person = PersonEntity(
                id = previous?.id ?: 0,
                displayName = name,
                lookupKey = key,
                phoneNumbers = numbersJson.toString(),
                nicknames = previous?.nicknames ?: "[]",
                relationship = previous?.relationship?.takeIf { it.isNotBlank() }
                    ?: inferRelationship(name),
                preferredChannel = previous?.preferredChannel ?: "",
                notes = previous?.notes ?: "",
                lastContactedAt = previous?.lastContactedAt ?: 0,
                timesContacted = previous?.timesContacted
            )
            db.personDao().insert(person)
            imported++
        }
        imported
    }

    /** The phone's own relation field, when people actually filled it in. */
    private fun inferRelationship(displayName: String): String {
        val words = displayName.lowercase().split(Regex("[^a-z]+")).filter { it.isNotBlank() }
        for (word in words) {
            RELATION_WORDS[word]?.let { return it }
        }
        return ""
    }

    // --------------------------------------------------------------- resolve

    /** Finds who the user meant. Returns ranked matches; caller asks if ambiguous. */
    suspend fun resolve(context: Context, spoken: String): List<Match> = withContext(Dispatchers.IO) {
        val query = normalize(spoken)
        if (query.isBlank()) return@withContext emptyList()

        val people = AppDatabase.get(context).personDao().all()
        val matches = ArrayList<Match>()

        // Relationship request ("call my wife")
        val relationship = RELATION_WORDS[query]
            ?: RELATION_WORDS.entries.firstOrNull { query.contains(it.key) }?.value
        if (relationship != null) {
            people.filter { it.relationship.equals(relationship, ignoreCase = true) }
                .forEach { matches.add(Match(it, 90, numbers(it))) }
        }

        for (person in people) {
            val score = scorePerson(person, query)
            if (score > 0) matches.add(Match(person, score, numbers(person)))
        }

        matches.sortedByDescending { it.score }
            .distinctBy { it.person.id }
    }

    private fun scorePerson(person: PersonEntity, query: String): Int {
        val name = person.displayName.lowercase()
        val nameWords = name.split(Regex("\\s+")).filter { it.isNotBlank() }
        val nicknames = jsonArray(person.nicknames).map { it.lowercase() }

        return when {
            nicknames.any { it == query } -> 100
            name == query -> 95
            nicknames.any { it.startsWith(query) || query.contains(it) } -> 88
            name.startsWith(query) -> 82
            nameWords.any { it == query } -> 78
            nameWords.any { it.startsWith(query) } -> 72
            name.contains(query) -> 65
            else -> 0
        }
    }

    // ----------------------------------------------------------------- learn

    suspend fun learnNickname(context: Context, spoken: String, person: PersonEntity) {
        withContext(Dispatchers.IO) {
            val nick = normalize(spoken)
            if (nick.isBlank()) return@withContext
            val list = jsonArray(person.nicknames).toMutableList()
            if (!list.any { it.equals(nick, ignoreCase = true) }) list.add(nick)
            val updated = person.copy(nicknames = JSONArray(list).toString())
            AppDatabase.get(context).personDao().update(updated)
        }
    }

    suspend fun setRelationship(context: Context, person: PersonEntity, relationship: String) {
        withContext(Dispatchers.IO) {
            AppDatabase.get(context).personDao().update(
                person.copy(relationship = normalize(relationship))
            )
        }
    }

    suspend fun noteContacted(context: Context, person: PersonEntity) {
        withContext(Dispatchers.IO) {
            AppDatabase.get(context).personDao().update(
                person.copy(
                    lastContactedAt = System.currentTimeMillis(),
                    timesContacted = person.timesContacted + 1
                )
            )
        }
    }

    suspend fun allPeople(context: Context): List<PersonEntity> =
        withContext(Dispatchers.IO) { AppDatabase.get(context).personDao().all() }

    // ----------------------------------------------------------------- utils

    fun numbers(person: PersonEntity): List<PhoneNumber> {
        val out = ArrayList<PhoneNumber>()
        return try {
            val arr = org.json.JSONArray(person.phoneNumbers)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val number = obj.optString("number")
                if (number.isNotBlank()) {
                    out.add(PhoneNumber(number, obj.optString("type", "mobile")))
                }
            }
            out
        } catch (_: Exception) {
            out
        }
    }

    private fun jsonArray(raw: String): List<String> {
        val out = ArrayList<String>()
        return try {
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) out.add(arr.optString(i))
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun normalize(raw: String): String {
        val words = raw.lowercase().trim().split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in FILLER }
        return words.joinToString(" ").trim()
    }

    private fun typeLabel(type: Int, label: String?): String = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "home"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "work"
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "main"
        else -> label?.lowercase() ?: "other"
    }
}
