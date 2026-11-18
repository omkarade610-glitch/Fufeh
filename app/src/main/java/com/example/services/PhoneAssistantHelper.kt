package com.example.services

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat

data class PermissionStatusItem(
    val name: String,
    val permissionKey: String?,
    val stateLabel: String,
    val detail: String,
    val canRequestRuntime: Boolean
)

data class BatteryTelemetry(
    val levelPercent: Int,
    val charging: Boolean
)

object PhoneAssistantHelper {

    fun readBatteryTelemetry(context: Context): BatteryTelemetry {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: -1
        val charging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bm?.isCharging ?: false
        } else {
            false
        }
        return BatteryTelemetry(levelPercent = pct, charging = charging)
    }

    fun hasFlashlight(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }

    fun toggleFlashlight(context: Context, enable: Boolean): Result<Boolean> {
        if (!hasFlashlight(context)) {
            return Result.failure(IllegalStateException("Flashlight hardware is not supported on this device."))
        }
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return Result.failure(IllegalStateException("No camera with flash unit found."))
            cm.setTorchMode(cameraId, enable)
            Result.success(enable)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun inspectPermissions(context: Context): List<PermissionStatusItem> {
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

        val notifItem = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            PermissionStatusItem(
                name = "Notifications",
                permissionKey = Manifest.permission.POST_NOTIFICATIONS,
                stateLabel = if (granted) "Granted" else "Denied",
                detail = "Alerts for reminders and long AI jobs",
                canRequestRuntime = !granted
            )
        } else {
            PermissionStatusItem(
                name = "Notifications",
                permissionKey = null,
                stateLabel = "Not Required",
                detail = "Automatically enabled below Android 13",
                canRequestRuntime = false
            )
        }

        return listOf(
            PermissionStatusItem(
                name = "Microphone (Voice Mode)",
                permissionKey = Manifest.permission.RECORD_AUDIO,
                stateLabel = if (micGranted) "Granted" else "Denied",
                detail = "Requested only when you tap Speak; never records silently",
                canRequestRuntime = !micGranted
            ),
            PermissionStatusItem(
                name = "Camera & Flashlight",
                permissionKey = if (hasCamera) Manifest.permission.CAMERA else null,
                stateLabel = when {
                    !hasCamera -> "Not Supported"
                    camGranted -> "Granted"
                    else -> "Denied"
                },
                detail = "Optional for Camera intent & Phone Assistant torch",
                canRequestRuntime = hasCamera && !camGranted
            ),
            notifItem,
            PermissionStatusItem(
                name = "Android Photo Picker",
                permissionKey = null,
                stateLabel = "Not Required",
                detail = "Zero-permission system photo picker (Play Policy compliant)",
                canRequestRuntime = false
            ),
            PermissionStatusItem(
                name = "Document Picker (SAF)",
                permissionKey = null,
                stateLabel = "Not Required",
                detail = "System Storage Access Framework grants single-file read access",
                canRequestRuntime = false
            )
        )
    }

    fun openUrl(context: Context, url: String) {
        val safeUrl = url.trim()
        if (!safeUrl.startsWith("http://") && !safeUrl.startsWith("https://")) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openAndroidSettings(context: Context, action: String = Settings.ACTION_SETTINGS) {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openAppPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openCalendarEventCreator(context: Context, title: String, description: String) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title.ifBlank { "Jarvis AI Reminder" })
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openAlarmCreator(context: Context, label: String, hour: Int = 9, minute: Int = 0) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Jarvis AI Alarm" })
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openCameraApp(context: Context) {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
