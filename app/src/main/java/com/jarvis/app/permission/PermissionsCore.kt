package com.jarvis.app.permission

import android.content.Context
import com.jarvis.android.permissions.PermissionAndSetupHelper

/**
 * Compatibility bridge redirecting to the central [PermissionAndSetupHelper].
 */
object PermissionsCore {

    fun runtimePermissions(): List<String> =
        PermissionAndSetupHelper.REQUIRED_PERMISSIONS.toList()

    fun isGranted(context: Context, permission: String): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            permission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    // ---- Special / system permission states ----
    fun notificationAccessEnabled(context: Context): Boolean =
        PermissionAndSetupHelper.hasNotificationListener(context)

    fun accessibilityEnabled(context: Context): Boolean =
        PermissionAndSetupHelper.hasAccessibilityService(context)

    fun accessibilityEnabled(): Boolean =
        com.jarvis.android.accessibility.JarvisAccessibilityService.isServiceRunning()

    fun overlayEnabled(context: Context): Boolean =
        PermissionAndSetupHelper.hasOverlay(context)

    fun writeSettingsEnabled(context: Context): Boolean =
        PermissionAndSetupHelper.hasWriteSettings(context)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        PermissionAndSetupHelper.hasBatteryOptimizationExemption(context)

    fun usageAccessEnabled(context: Context): Boolean =
        PermissionAndSetupHelper.hasUsageAccess(context)

    // ---- Settings Launchers ----
    fun openNotificationSettings(context: Context) =
        PermissionAndSetupHelper.openNotificationListenerSettings(context)

    fun openAccessibilitySettings(context: Context) =
        PermissionAndSetupHelper.openAccessibilitySettings(context)

    fun openOverlaySettings(context: Context) =
        PermissionAndSetupHelper.openOverlaySettings(context)

    fun openWriteSettings(context: Context) =
        PermissionAndSetupHelper.openWriteSettings(context)

    fun openUsageSettings(context: Context) =
        PermissionAndSetupHelper.openUsageAccessSettings(context)

    fun openBatterySettings(context: Context) =
        PermissionAndSetupHelper.openBatteryOptimizationSettings(context)

    fun openBatteryExemption(context: Context) =
        PermissionAndSetupHelper.openBatteryOptimizationSettings(context)

    fun openLocationSettings(context: Context) =
        PermissionAndSetupHelper.openLocationSettings(context)

    fun openAppDetails(context: Context) =
        PermissionAndSetupHelper.openAppDetails(context)

    fun openManageStorage(context: Context) =
        PermissionAndSetupHelper.openAllFilesAccessSettings(context)
}
