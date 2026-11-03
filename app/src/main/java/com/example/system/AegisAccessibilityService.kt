package com.example.system

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import com.example.data.SecurityPreferencesManager

/**
 * Zero-Battery Event-Driven System Accessibility Service.
 * - Uses 0% CPU when idle or when the screen is off.
 * - Wakes ONLY when Android WindowManager dispatches TYPE_WINDOW_STATE_CHANGED or screen broadcast.
 * - Performs O(1) in-memory HashSet lookup (zero disk reads or polling).
 */
class AegisAccessibilityService : AccessibilityService() {

    private lateinit var prefsManager: SecurityPreferencesManager
    private var lastLaunchedPkg: String? = null
    private var lastLaunchTimestampMs: Long = 0L

    private val screenAndUnlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val state = prefsManager.state.value
            if (!state.isSetupCompleted || !state.masterShieldEnabled) return

            when (action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (state.relockOnScreenOff) {
                        AppLockMonitorService.clearSessionUnlockedApps()
                    }
                    if (state.phoneScreenLockEnabled) {
                        AppLockMonitorService.setHomeScreenGatePending(true)
                        launchPhoneScreenGate()
                    }
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    if (state.phoneScreenLockEnabled && AppLockMonitorService.isHomeScreenGatePending()) {
                        launchPhoneScreenGate()
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefsManager = SecurityPreferencesManager(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenAndUnlockReceiver, filter)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return

        // Fast O(1) in-memory read — zero disk I/O!
        val state = prefsManager.state.value
        if (!state.isSetupCompleted || !state.masterShieldEnabled) return

        val now = System.currentTimeMillis()
        if (pkg == lastLaunchedPkg && (now - lastLaunchTimestampMs) < 900L) return

        // 1. If the Home Screen Gate is pending after screen-off/unlock:
        if (state.phoneScreenLockEnabled && AppLockMonitorService.isHomeScreenGatePending()) {
            lastLaunchedPkg = pkg
            lastLaunchTimestampMs = now
            launchPhoneScreenGate()
            return
        }

        // 2. If the user opened a locked app (O(1) HashSet check):
        val lockedSet = prefsManager.getLockedPackageSet()
        if (lockedSet.contains(pkg) && !AppLockMonitorService.isPackageUnlockedInSession(pkg)) {
            lastLaunchedPkg = pkg
            lastLaunchTimestampMs = now
            launchAppLockInterceptor(pkg)
        }
    }

    private fun launchPhoneScreenGate() {
        val lockIntent = Intent(this, LockOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(LockOverlayActivity.EXTRA_TARGET_PACKAGE, packageName)
            putExtra(LockOverlayActivity.EXTRA_TARGET_APP_NAME, "Phone Unlock Gate (Home Screen)")
            putExtra(LockOverlayActivity.EXTRA_IS_PHONE_LOCK_SCREEN, true)
        }
        runCatching {
            startActivity(lockIntent)
        }
    }

    private fun launchAppLockInterceptor(targetPackage: String) {
        val lockIntent = Intent(this, LockOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(LockOverlayActivity.EXTRA_TARGET_PACKAGE, targetPackage)
            putExtra(LockOverlayActivity.EXTRA_IS_PHONE_LOCK_SCREEN, false)
        }
        runCatching {
            startActivity(lockIntent)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenAndUnlockReceiver) }
        super.onDestroy()
    }
}
