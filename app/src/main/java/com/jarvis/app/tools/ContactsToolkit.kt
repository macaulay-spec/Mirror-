package com.jarvis.app.tools

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class Contact(val name: String, val phone: String)

class ContactsToolkit(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun search(query: String): Contact? {
        if (!hasPermission()) return null
        val cr: ContentResolver = context.contentResolver
        val uri: Uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val proj = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val sel = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        try {
            cr.query(uri, proj, sel, arrayOf("%$query%"), null)?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0)
                    val num = c.getString(1)
                    return Contact(name, num)
                }
            }
        } catch (_: Exception) { }
        return null
    }

    fun dial(number: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openChatIntent(pkg: String, number: String, body: String): Boolean {
        return try {
            val uri = when (pkg) {
                "com.whatsapp" -> "https://wa.me/$number"
                "org.telegram.messenger" -> "https://t.me/+$number"
                else -> "sms:$number"
            }
            val intent = if (uri.startsWith("sms")) {
                Intent(Intent.ACTION_SENDTO, Uri.parse(uri)).apply { putExtra("sms_body", body) }
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
