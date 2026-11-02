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
 * Persistent Foreground Guardian Service.
 * 1. Monitors screen-off and screen-on events (ACTION_SCREEN_OFF / ACTION_SCREEN_ON) to
 *    display the custom Phone Lock Screen (showWhenLocked = true) when Phone Screen Lock is enabled.
 * 2. Monitors foreground app launches via UsageStatsManager to lock individual protected apps.
 */
class AppLockMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private lateinit var prefsManager: SecurityPreferencesManager

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            prefsManager.refreshState()
            val state = prefsManager.state.value

            if (action == Intent.ACTION_SCREEN_OFF) {
                if (state.relockOnScreenOff) {
                    clearSessionUnlockedApps()
                }
                if (state.isSetupCompleted && state.masterShieldEnabled && state.phoneScreenLockEnabled) {
                    launchPhoneScreenLock()
                }
            } else if (action == Intent.ACTION_SCREEN_ON) {
                if (state.isSetupCompleted && state.masterShieldEnabled && state.phoneScreenLockEnabled) {
                    launchPhoneScreenLock()
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
        startForegroundMonitoringLoop()
        return START_STICKY
    }

    private fun launchPhoneScreenLock() {
        val lockIntent = Intent(this, LockOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            putExtra(LockOverlayActivity.EXTRA_TARGET_PACKAGE, packageName)
            putExtra(LockOverlayActivity.EXTRA_TARGET_APP_NAME, "Phone Lock Screen")
            putExtra(LockOverlayActivity.EXTRA_IS_PHONE_LOCK_SCREEN, true)
        }
        runCatching {
            startActivity(lockIntent)
        }
    }

    private fun startForegroundMonitoringLoop() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive) {
                val state = prefsManager.state.value
                if (state.isSetupCompleted &&
                    state.masterShieldEnabled &&
                    SystemPermissionHelper.hasUsageStatsPermission(this@AppLockMonitorService)
                ) {
                    val topPkg = queryTopForegroundPackage()
                    if (!topPkg.isNullOrBlank() && topPkg != packageName) {
                        val lockedSet = prefsManager.getLockedPackageSet()
                        if (lockedSet.contains(topPkg) && !sessionUnlockedPackages.contains(topPkg)) {
                            launchLockInterceptor(topPkg)
                        }
                    }
                }
                delay(450L)
            }
        }
    }

    private fun queryTopForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 10_000L
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
        if (targetPackage == lastInterceptedPackage && (now - lastInterceptTimeMs) < 1500L) {
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
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
        private var lastInterceptedPackage: String? = null
        private var lastInterceptTimeMs: Long = 0L

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
