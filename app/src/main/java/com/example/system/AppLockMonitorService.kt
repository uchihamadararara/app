package com.example.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.SecurityPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Ultra-Low-Battery Guardian Service:
 * 1. Event-Driven Mode (0% CPU / 0% Polling): When `AegisAccessibilityService` is enabled OR
 *    when the phone screen is OFF (`ACTION_SCREEN_OFF`), the background loop is completely paused!
 *    Zero background CPU wakes occur while the phone is idle or in your pocket.
 * 2. Fallback Mode: Only if `AegisAccessibilityService` is NOT enabled AND the screen is currently
 *    ON (`isInteractive == true`), runs a lightweight 1000ms UsageEvents check over a 2.5s window.
 */
class AppLockMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private lateinit var prefsManager: SecurityPreferencesManager

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val state = prefsManager.state.value

            when (action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Immediately stop any background loop when screen turns off -> 0% battery in pocket!
                    monitorJob?.cancel()
                    monitorJob = null

                    if (state.relockOnScreenOff) {
                        clearSessionUnlockedApps()
                    }
                    if (state.isSetupCompleted && state.masterShieldEnabled && state.phoneScreenLockEnabled) {
                        setHomeScreenGatePending(true)
                        launchPhoneScreenLock(startInAmbientAod = true)
                    }
                }

                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    if (state.isSetupCompleted && state.masterShieldEnabled && state.phoneScreenLockEnabled && isHomeScreenGatePending()) {
                        launchPhoneScreenLock(startInAmbientAod = false)
                    }
                    // Only start fallback polling if AccessibilityService is NOT active
                    updateMonitoringMode()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefsManager = SecurityPreferencesManager(this)
        createNotificationChannel()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val lockedCount = prefsManager.getLockedPackageSet().size
        val notification = buildGuardianNotification(lockedCount)

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }

        _isServiceRunning.value = true
        updateMonitoringMode()
        return START_STICKY
    }

    private fun isScreenInteractive(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isInteractive
    }

    /**
     * Ensures 0% polling whenever:
     * - The phone screen is OFF, OR
     * - `AegisAccessibilityService` is enabled (since Accessibility is 100% event-driven!).
     */
    private fun updateMonitoringMode() {
        if (!isScreenInteractive() || SystemPermissionHelper.isAccessibilityServiceEnabled(this)) {
            monitorJob?.cancel()
            monitorJob = null
            return
        }
        if (monitorJob?.isActive == true) return

        monitorJob = serviceScope.launch {
            while (isActive) {
                if (!isScreenInteractive() || SystemPermissionHelper.isAccessibilityServiceEnabled(this@AppLockMonitorService)) {
                    // Switch to 0% CPU event-driven mode immediately
                    break
                }

                val state = prefsManager.state.value
                if (state.isSetupCompleted &&
                    state.masterShieldEnabled &&
                    SystemPermissionHelper.hasUsageStatsPermission(this@AppLockMonitorService)
                ) {
                    val topPkg = queryRecentForegroundPackage()
                    if (!topPkg.isNullOrBlank() && topPkg != packageName && topPkg != "com.android.systemui") {
                        if (state.phoneScreenLockEnabled && isHomeScreenGatePending()) {
                            launchPhoneScreenLock(startInAmbientAod = false)
                        } else {
                            val lockedSet = prefsManager.getLockedPackageSet()
                            if (lockedSet.contains(topPkg) && !isPackageUnlockedInSession(topPkg)) {
                                launchLockInterceptor(topPkg)
                            }
                        }
                    }
                }
                delay(1000L)
            }
        }
    }

    private fun launchPhoneScreenLock(startInAmbientAod: Boolean = false) {
        val lockIntent = Intent(this, LockOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(LockOverlayActivity.EXTRA_TARGET_PACKAGE, packageName)
            putExtra(LockOverlayActivity.EXTRA_TARGET_APP_NAME, "Phone Unlock Gate (Home Screen)")
            putExtra(LockOverlayActivity.EXTRA_IS_PHONE_LOCK_SCREEN, true)
            putExtra(LockOverlayActivity.EXTRA_START_IN_AMBIENT_AOD, startInAmbientAod)
        }
        runCatching {
            startActivity(lockIntent)
        }
    }

    private fun queryRecentForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 2500L
        val usageEvents = usm.queryEvents(beginTime, endTime) ?: return null
        val event = UsageEvents.Event()
        var latestPkg: String? = null
        var latestTimestamp = 0L

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.timeStamp >= latestTimestamp) {
                    latestTimestamp = event.timeStamp
                    latestPkg = event.packageName
                }
            }
        }
        return latestPkg
    }

    private fun launchLockInterceptor(targetPackage: String) {
        val now = System.currentTimeMillis()
        if (targetPackage == lastInterceptedPackage && (now - lastInterceptTimeMs) < 1200L) {
            return
        }
        lastInterceptedPackage = targetPackage
        lastInterceptTimeMs = now

        val lockIntent = Intent(this, LockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(LockOverlayActivity.EXTRA_TARGET_PACKAGE, targetPackage)
            putExtra(LockOverlayActivity.EXTRA_IS_PHONE_LOCK_SCREEN, false)
        }
        runCatching {
            startActivity(lockIntent)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildGuardianNotification(lockedCount: Int): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notif_active_title))
            .setContentText(getString(R.string.notif_active_body, lockedCount))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onDestroy() {
        _isServiceRunning.value = false
        monitorJob?.cancel()
        serviceScope.cancel()
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "aegis_guardian_channel"
        private const val NOTIFICATION_ID = 4101

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val sessionUnlockedPackages = mutableSetOf<String>()
        @Volatile
        private var homeScreenGatePending: Boolean = false
        private var lastInterceptedPackage: String? = null
        private var lastInterceptTimeMs: Long = 0L

        fun setHomeScreenGatePending(pending: Boolean) {
            homeScreenGatePending = pending
        }

        fun isHomeScreenGatePending(): Boolean = homeScreenGatePending

        fun isPackageUnlockedInSession(pkg: String): Boolean {
            synchronized(sessionUnlockedPackages) {
                return sessionUnlockedPackages.contains(pkg)
            }
        }

        fun markPackageUnlockedForSession(pkg: String) {
            synchronized(sessionUnlockedPackages) {
                sessionUnlockedPackages.add(pkg)
            }
        }

        fun clearSessionUnlockedApps() {
            synchronized(sessionUnlockedPackages) {
                sessionUnlockedPackages.clear()
            }
        }
    }
}
