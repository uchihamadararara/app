package com.example.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.AegisDatabase
import com.example.data.SecurityLogEntity
import com.example.data.SecurityPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Masks notifications from locked apps so their private message/sender content is never exposed.
 * When a notification arrives for a locked app, the original notification is dismissed and
 * replaced with a clean notification showing ONLY "New notification".
 */
class AegisNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefsManager: SecurityPreferencesManager

    override fun onCreate() {
        super.onCreate()
        prefsManager = SecurityPreferencesManager(applicationContext)
        ensureMaskedChannel(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val statusBarNotif = sbn ?: return
        val pkg = statusBarNotif.packageName ?: return

        // Ignore our own masked notifications or ongoing foreground service notifications
        if (pkg == packageName || statusBarNotif.isOngoing) return

        val state = prefsManager.state.value
        if (!state.masterShieldEnabled || !state.isSetupCompleted || !state.notificationPrivacyEnabled) {
            return
        }

        val lockedSet = prefsManager.getLockedPackageSet()
        if (!lockedSet.contains(pkg)) return

        // 1. Cancel the original notification so private message text / sender name is hidden
        runCatching {
            cancelNotification(statusBarNotif.key)
        }

        // 2. Resolve the app's display name (e.g., "WhatsApp", "Messages")
        val appLabel = resolveAppName(applicationContext, pkg)

        // 3. Post a clean notification showing ONLY "New notification"
        postMaskedNewNotification(
            context = applicationContext,
            targetPackage = pkg,
            targetAppName = appLabel
        )

        // 4. Record in Security Logs
        serviceScope.launch {
            runCatching {
                val db = AegisDatabase.getInstance(applicationContext)
                db.aegisDao().insertSecurityLog(
                    SecurityLogEntity(
                        eventType = "NOTIFICATION_MASKED",
                        targetAppName = appLabel,
                        targetPackage = pkg,
                        authMethod = "NOTIFICATION_SHIELD",
                        details = "Hidden private notification content • Displayed only 'New notification'"
                    )
                )
            }
        }
    }

    companion object {
        private const val MASKED_CHANNEL_ID = "aegis_masked_locked_app_notifications"

        fun ensureMaskedChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return
                val channel = NotificationChannel(
                    MASKED_CHANNEL_ID,
                    "Locked App Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Shows 'New notification' when a locked app receives a message"
                }
                nm.createNotificationChannel(channel)
            }
        }

        fun resolveAppName(context: Context, pkg: String): String {
            return runCatching {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            }.getOrDefault(pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() })
        }

        /**
         * Posts a clean notification for a locked app showing ONLY "New notification".
         * Tapping it opens LockOverlayActivity for that locked app.
         */
        fun postMaskedNewNotification(
            context: Context,
            targetPackage: String,
            targetAppName: String
        ) {
            ensureMaskedChannel(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val lockIntent = Intent(context, LockOverlayActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(LockOverlayActivity.EXTRA_TARGET_PACKAGE, targetPackage)
                putExtra(LockOverlayActivity.EXTRA_TARGET_APP_NAME, targetAppName)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                targetPackage.hashCode(),
                lockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, MASKED_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(targetAppName)
                .setContentText("New notification")
                .setSubText("Locked by Aegis Lock")
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            runCatching {
                nm.notify(targetPackage.hashCode(), notification)
            }
        }
    }
}
