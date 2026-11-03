package com.example.system

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.core.content.ContextCompat

data class SystemSecurityCapabilities(
    val isDeviceAdminActive: Boolean = false,
    val isAccessibilityServiceEnabled: Boolean = false,
    val isNotificationListenerEnabled: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val canDrawOverlays: Boolean = false,
    val hasUsageStatsAccess: Boolean = false,
    val isBootReceiverEnabled: Boolean = true,
    val isGuardianServiceRunning: Boolean = false,
    val biometricStrongAvailable: Boolean = false,
    val biometricWeakOrFaceAvailable: Boolean = false,
    val biometricStatusLabel: String = "Checking...",
    val isDirectBootProtected: Boolean = true
)

object SystemPermissionHelper {

    fun getDeviceAdminComponent(context: Context): ComponentName {
        return ComponentName(context, AegisDeviceAdminReceiver::class.java)
    }

    fun isDeviceAdminActive(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return false
        return runCatching {
            dpm.isAdminActive(getDeviceAdminComponent(context))
        }.getOrDefault(false)
    }

    fun buildEnableDeviceAdminIntent(context: Context): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getDeviceAdminComponent(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Enable Aegis Device Administrator to prevent unauthorized uninstallation of Aegis Lock and allow instant screen locking."
            )
        }
    }

    fun revokeDeviceAdmin(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        runCatching {
            dpm?.removeActiveAdmin(getDeviceAdminComponent(context))
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponentName = ComponentName(context, AegisAccessibilityService::class.java)
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }

    fun buildAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(context, AegisNotificationListenerService::class.java)
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(flat)
        while (colonSplitter.hasNext()) {
            val cn = ComponentName.unflattenFromString(colonSplitter.next())
            if (cn != null && cn == expectedComponent) {
                return true
            }
        }
        return false
    }

    fun buildNotificationListenerSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else {
            true
        }
    }

    @Suppress("BatteryLife")
    fun buildIgnoreBatteryOptimizationsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Marks the Home Screen Gate as locked, launches the Phone Unlock Gate screen,
     * and calls DevicePolicyManager.lockNow() if Device Admin is active.
     */
    fun lockPhoneScreenNow(context: Context) {
        AppLockMonitorService.setHomeScreenGatePending(true)
        val lockScreenIntent = Intent(context, LockOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(LockOverlayActivity.EXTRA_TARGET_PACKAGE, context.packageName)
            putExtra(LockOverlayActivity.EXTRA_TARGET_APP_NAME, "Phone Unlock Gate (Home Screen)")
            putExtra(LockOverlayActivity.EXTRA_IS_PHONE_LOCK_SCREEN, true)
        }
        runCatching {
            context.startActivity(lockScreenIntent)
        }
        if (isDeviceAdminActive(context)) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            runCatching {
                dpm?.lockNow()
            }
        }
    }

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun buildOverlayPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun buildUsageStatsPermissionIntent(context: Context): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    fun isBootReceiverEnabled(context: Context): Boolean {
        val cn = ComponentName(context, BootCompletedReceiver::class.java)
        val state = context.packageManager.getComponentEnabledSetting(cn)
        return state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ||
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }

    fun setBootReceiverEnabled(context: Context, enabled: Boolean) {
        val cn = ComponentName(context, BootCompletedReceiver::class.java)
        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        context.packageManager.setComponentEnabledSetting(
            cn,
            newState,
            PackageManager.DONT_KILL_APP
        )
    }

    fun inspectCapabilities(context: Context): SystemSecurityCapabilities {
        val bm = BiometricManager.from(context)
        val strongResult = bm.canAuthenticate(Authenticators.BIOMETRIC_STRONG)
        val weakResult = bm.canAuthenticate(Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK)

        val strongReady = strongResult == BiometricManager.BIOMETRIC_SUCCESS
        val weakReady = weakResult == BiometricManager.BIOMETRIC_SUCCESS

        val bioLabel = when (weakResult) {
            BiometricManager.BIOMETRIC_SUCCESS ->
                "Real Phone Biometrics Enrolled & Ready (Fingerprint / Face)"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "No Fingerprint/Face Enrolled in Phone Settings Yet"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                "No Hardware Biometric Sensor Detected on This Device"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "Biometric Hardware Temporarily Busy"
            else -> "Biometric Sensor Status ($weakResult)"
        }

        return SystemSecurityCapabilities(
            isDeviceAdminActive = isDeviceAdminActive(context),
            isAccessibilityServiceEnabled = isAccessibilityServiceEnabled(context),
            isNotificationListenerEnabled = isNotificationListenerEnabled(context),
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(context),
            hasCameraPermission = hasCameraPermission(context),
            canDrawOverlays = canDrawOverlays(context),
            hasUsageStatsAccess = hasUsageStatsPermission(context),
            isBootReceiverEnabled = isBootReceiverEnabled(context),
            isGuardianServiceRunning = AppLockMonitorService.isServiceRunning.value,
            biometricStrongAvailable = strongReady,
            biometricWeakOrFaceAvailable = weakReady,
            biometricStatusLabel = bioLabel,
            isDirectBootProtected = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
        )
    }

    fun startOrStopGuardianService(context: Context, start: Boolean) {
        val intent = Intent(context, AppLockMonitorService::class.java)
        runCatching {
            if (start) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.stopService(intent)
            }
        }
    }
}
