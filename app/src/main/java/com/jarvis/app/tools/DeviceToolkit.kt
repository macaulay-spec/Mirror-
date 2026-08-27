package com.jarvis.app.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.KeyEvent

class DeviceToolkit(private val context: Context) {

    private enum class MediaAction {
        TOGGLE, NEXT, PREV
    }

    fun launchPackage(pkg: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }

    fun fuzzyLaunch(query: String): Boolean {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(0)
        val match = apps.firstOrNull {
            it.loadLabel(pm).toString().contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        } ?: return false
        return launchPackage(match.packageName)
    }

    fun batteryStatus(): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return "Battery $level%. If charging: $charging."
    }

    fun connectivity(): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return "No active network."
        val caps = cm.getNetworkCapabilities(net)
        val wifi = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
        val cellular = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val ethernet = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true
        return when {
            wifi -> "Connected to Wi-Fi."
            cellular -> "Connected to mobile data."
            ethernet -> "Connected via Ethernet."
            else -> "Connected."
        }
    }

    fun toggleWifi(on: Boolean): String {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wm.isWifiEnabled = on
            "Wi-Fi turned ${if (on) "on" else "off"}."
        } catch (e: Exception) {
            "Could not change Wi-Fi: ${e.message}"
        }
    }

    fun volume(kind: String, amount: Int): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = when (kind.lowercase()) {
            "music", "media" -> AudioManager.STREAM_MUSIC
            "alarm" -> AudioManager.STREAM_ALARM
            "ring", "ringtone" -> AudioManager.STREAM_RING
            else -> AudioManager.STREAM_MUSIC
        }
        val max = am.getStreamMaxVolume(stream)
        am.setStreamVolume(stream, amount.coerceIn(0, max), 0)
        return "Volume set to $amount."
    }

    fun brightness(percent: Int): String {
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val value = (percent / 100f).coerceIn(0f, 1f) * 255f
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value.toInt())
            "Brightness set to ${percent.coerceIn(0, 100)}%."
        } catch (e: Exception) {
            "Brightness needs the system setting permission."
        }
    }

    fun dnd(on: Boolean): String {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.setInterruptionFilter(
                if (on) android.app.NotificationManager.INTERRUPTION_FILTER_NONE
                else android.app.NotificationManager.INTERRUPTION_FILTER_ALL
            )
            if (on) "Do Not Disturb on." else "Do Not Disturb off."
        } catch (e: Exception) {
            "DND needs notification access."
        }
    }

    fun flashlight(on: Boolean): String {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "No flashlight."
            cm.setTorchMode(id, on)
            if (on) "Flashlight on." else "Flashlight off."
        } catch (e: Exception) {
            "Flashlight unavailable: ${e.message}"
        }
    }

    fun media(action: String): String {
        val key = when (action.lowercase()) {
            "next", "skip", "forward" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous", "back" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "play", "pause", "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        try {
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
            return "Media ${action.lowercase()}."
        } catch (e: Exception) {
            return "Media control failed: ${e.message}"
        }
    }

    fun openSmsApp(phone: String?, body: String) {
        val uri = if (!phone.isNullOrBlank()) "sms:$phone" else "sms:"
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(uri)).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Opens the default browser (Chrome if available) with the search query. */
    fun openSearch(query: String): Boolean {
        return try {
            val url = "https://www.google.com/search?q=${android.net.Uri.encode(query)}"
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Prefer Chrome if installed.
            val chrome = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                `package` = "com.android.chrome"
            }
            if (chrome.resolveActivity(context.packageManager) != null) {
                context.startActivity(chrome)
            } else {
                context.startActivity(browserIntent)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun sendSms(phone: String, body: String): String {
        return try {
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED
            ) return "Need SMS permission first."
            SmsManager.getDefault().sendTextMessage(phone, null, body, null, null)
            "Sent to $phone."
        } catch (e: Exception) {
            "SMS failed: ${e.message}"
        }
    }

    fun timeNow(): String {
        val f = SimpleDateFormat("EEEE, MMM d h:mm a", Locale.getDefault())
        return "It's " + f.format(Date())
    }

    fun storage(): String {
        val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val free = stat.availableBytes / (1024.0 * 1024 * 1024)
        val total = stat.totalBytes / (1024.0 * 1024 * 1024)
        return "Storage: %.1f GB free of %.1f GB.".format(free, total)
    }
}
