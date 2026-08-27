package com.jarvis.app.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.util.Locale

class LocationToolkit(private val context: Context) {

    fun lastKnown(): String {
        return try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) "Need location permission first."
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            val loc = providers.asSequence().mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time } ?: return "Location not available yet. Open a maps app once."
            val geocoder = Geocoder(context, Locale.getDefault())
            val address = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            val place = listOfNotNull(address?.locality, address?.adminArea).distinct().joinToString(", ")
            "You're at ${loc.latitude}, ${loc.longitude}${if (place.isNotBlank()) " ($place)" else ""}."
        } catch (e: Exception) {
            "Couldn't read location: ${e.message}"
        }
    }

    fun openLocationSettings() {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
