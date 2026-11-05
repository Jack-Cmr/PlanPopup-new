package com.planpop.app.util

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class PermissionStatus(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val overlay: Boolean,
    val batteryUnrestricted: Boolean,
    val fullScreenIntent: Boolean
) {
    val allCoreGranted: Boolean
        get() = notifications && exactAlarms && overlay && fullScreenIntent
}

fun Context.readPermissionStatus(): PermissionStatus {
    val notificationRuntimeGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    val notifications = notificationRuntimeGranted &&
        NotificationManagerCompat.from(this).areNotificationsEnabled()

    val alarmManager = getSystemService(AlarmManager::class.java)
    val exactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        alarmManager.canScheduleExactAlarms()

    val overlay = Settings.canDrawOverlays(this)

    val powerManager = getSystemService(PowerManager::class.java)
    val batteryUnrestricted = powerManager.isIgnoringBatteryOptimizations(packageName)

    val fullScreenIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    } else {
        true
    }

    return PermissionStatus(
        notifications = notifications,
        exactAlarms = exactAlarms,
        overlay = overlay,
        batteryUnrestricted = batteryUnrestricted,
        fullScreenIntent = fullScreenIntent
    )
}

fun Context.openNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    launchSettings(intent)
}

fun Context.openExactAlarmSettings() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchSettings(intent)
    } else {
        openAppDetails()
    }
}

fun Context.openOverlaySettings() {
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
        data = Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    launchSettings(intent)
}

fun Context.openBatteryOptimizationSettings() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchSettings(intent)
    } else {
        openAppDetails()
    }
}

fun Context.openFullScreenIntentSettings() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchSettings(intent)
    } else {
        openAppDetails()
    }
}

fun Context.openAppDetails() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    launchSettings(intent)
}

private fun Context.launchSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: RuntimeException) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: RuntimeException) {
            // Nothing else we can safely open on this OEM build.
        }
    }
}
