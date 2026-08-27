package com.jarvis.feature.setup

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PermissionCenterScreen(
    onClose: () -> Unit,
    onRequestPermissions: () -> Unit = {},
    onOpenAccessibility: () -> Unit = {},
    onOpenNotificationListener: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    SetupScreen(
        onClose = onClose,
        onRequestPermissions = onRequestPermissions,
        onOpenAccessibility = onOpenAccessibility,
        onOpenNotificationListener = onOpenNotificationListener,
        modifier = modifier
    )
}
