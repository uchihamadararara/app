package com.example.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.AegisDatabase
import com.example.data.SecurityLogEntity
import com.example.data.SecurityPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Direct-Boot Aware Boot Completed Receiver.
 * Restores AppLockMonitorService and launches the Phone Lock Screen on device restart.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val prefs = SecurityPreferencesManager(context)
            prefs.recordBootRestoration(System.currentTimeMillis())

            val state = prefs.state.value
            if (state.masterShieldEnabled && state.autoStartOnBoot) {
                runCatching {
                    val serviceIntent = Intent(context, AppLockMonitorService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            }

            if (state.isSetupCompleted && state.masterShieldEnabled && state.phoneScreenLockEnabled) {
                runCatching {
                    val lockIntent = Intent(context, LockOverlayActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                        putExtra(LockOverlayActivity.EXTRA_TARGET_PACKAGE, context.packageName)
                        putExtra(LockOverlayActivity.EXTRA_TARGET_APP_NAME, "Phone Lock Screen")
                        putExtra(LockOverlayActivity.EXTRA_IS_PHONE_LOCK_SCREEN, true)
                    }
                    context.startActivity(lockIntent)
                }
            }

            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val dao = AegisDatabase.getInstance(context).aegisDao()
                    dao.insertSecurityLog(
                        SecurityLogEntity(
                            eventType = "BOOT_RESTORED",
                            targetAppName = "Phone Restart Protection",
                            targetPackage = context.packageName,
                            authMethod = "SYSTEM",
                            details = "Phone restarted ($action). Password & Lock Screen protection restored."
                        )
                    )
                }
            }
        }
    }
}
