package com.example.system

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators

data class SystemSecurityCapabilities(
    val isDeviceAdminActive: Boolean = false,
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
                "Enable Aegis Device Administrator to prevent unauthorized uninstallation of Aegis Lock and protect your locked apps."
            )
        }
    }

    fun revokeDeviceAdmin(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        runCatching {
            dpm?.removeActiveAdmin(getDeviceAdminComponent(context))
        }
    }

    /**
     * Uses DevicePolicyManager.lockNow() (if Device Admin is active) to lock the hardware screen
     * and launches the full-screen Aegis Phone Lock Screen (with showWhenLocked = true).
     */
    fun lockPhoneScreenNow(context: Context) {
        val lockScreenIntent = Intent(context, LockOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(LockOverlayActivity.EXTRA_TARGET_PACKAGE, context.packageName)
            putExtra(LockOverlayActivity.EXTRA_TARGET_APP_NAME, "Phone Lock Screen")
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
                "Hardware Ready (Fingerprint & Face Biometrics Enrolled)"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "Sensor Present • No Fingerprint/Face Enrolled in OS Settings"
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                "No Biometric Sensor Detected on Emulator/Device (Interactive Biometric Verification Active)"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "Biometric Hardware Busy / Temporarily Unavailable"
            else -> "Biometric API Ready (Fallback + Hardware Mode)"
        }

        return SystemSecurityCapabilities(
            isDeviceAdminActive = isDeviceAdminActive(context),
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
