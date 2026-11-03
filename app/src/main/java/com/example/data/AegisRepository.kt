package com.example.data

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class AegisRepository(
    private val context: Context,
    private val dao: AegisDao,
    val prefsManager: SecurityPreferencesManager
) {
    val allAppsFlow: Flow<List<LockedAppEntity>> = dao.observeAllApps()
    val securityLogsFlow: Flow<List<SecurityLogEntity>> = dao.observeSecurityLogs()
    val vaultStateFlow = prefsManager.state

    /**
     * Scans real installed launchable packages on the device via PackageManager and merges
     * critical system protection targets (Settings, Package Installer, Play Store, etc.).
     */
    suspend fun syncInstalledApps() = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = runCatching {
            pm.queryIntentActivities(mainIntent, 0)
        }.getOrDefault(emptyList())

        val discovered = mutableMapOf<String, LockedAppEntity>()

        // 1. System & Common Packages (All unlocked by default until user chooses to lock them)
        val systemCriticalDefaults = listOf(
            LockedAppEntity(
                packageName = "com.android.settings",
                appName = "Settings",
                category = "System",
                isSystemCritical = true,
                isLocked = false
            ),
            LockedAppEntity(
                packageName = "com.google.android.packageinstaller",
                appName = "Package Installer (Prevent Uninstall)",
                category = "System",
                isSystemCritical = true,
                isLocked = false
            ),
            LockedAppEntity(
                packageName = "com.android.vending",
                appName = "Google Play Store",
                category = "System",
                isSystemCritical = true,
                isLocked = false
            ),
            LockedAppEntity(
                packageName = "com.google.android.apps.photos",
                appName = "Photos & Gallery",
                category = "Media",
                isSystemCritical = false,
                isLocked = false
            ),
            LockedAppEntity(
                packageName = "com.google.android.gm",
                appName = "Gmail",
                category = "Social & Mail",
                isSystemCritical = false,
                isLocked = false
            ),
            LockedAppEntity(
                packageName = "com.whatsapp",
                appName = "WhatsApp",
                category = "Social & Mail",
                isSystemCritical = false,
                isLocked = false
            ),
            LockedAppEntity(
                packageName = "com.google.android.apps.messaging",
                appName = "Messages",
                category = "Social & Mail",
                isSystemCritical = false,
                isLocked = false
            ),
            LockedAppEntity(
                packageName = "com.google.android.apps.nbu.paisa.user",
                appName = "Google Pay",
                category = "Payment",
                isSystemCritical = false,
                isLocked = false
            ),
            LockedAppEntity(
                packageName = "com.android.chrome",
                appName = "Chrome",
                category = "Apps",
                isSystemCritical = false,
                isLocked = false
            ),
            LockedAppEntity(
                packageName = "com.google.android.dialer",
                appName = "Phone",
                category = "System",
                isSystemCritical = false,
                isLocked = false
            )
        )

        systemCriticalDefaults.forEach { discovered[it.packageName] = it }

        // 2. Real installed apps from PackageManager
        for (info in resolveInfos) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (pkg == context.packageName) continue
            val label = runCatching {
                info.loadLabel(pm).toString().trim()
            }.getOrDefault(pkg.substringAfterLast('.'))
            if (label.isBlank()) continue

            val existing = discovered[pkg]
            if (existing == null) {
                val category = categorizePackage(pkg, label)
                val isCritical = pkg.contains("settings", ignoreCase = true) ||
                    pkg.contains("installer", ignoreCase = true) ||
                    pkg.contains("vending", ignoreCase = true)
                discovered[pkg] = LockedAppEntity(
                    packageName = pkg,
                    appName = label,
                    category = category,
                    isSystemCritical = isCritical,
                    isLocked = false
                )
            }
        }

        dao.insertAppsIgnoreExisting(discovered.values.toList())
        refreshDirectBootLockedSet()
    }

    private fun categorizePackage(pkg: String, label: String): String {
        val lower = "$pkg $label".lowercase()
        return when {
            lower.contains("setting") || lower.contains("installer") || lower.contains("vending") -> "System Critical"
            lower.contains("pay") || lower.contains("bank") || lower.contains("wallet") || lower.contains("finance") -> "Finance & Banking"
            lower.contains("photo") || lower.contains("gallery") || lower.contains("camera") || lower.contains("video") -> "Media & Vault"
            lower.contains("whatsapp") || lower.contains("messag") || lower.contains("mail") || lower.contains("chat") || lower.contains("telegram") -> "Communication"
            lower.contains("chrome") || lower.contains("browser") || lower.contains("youtube") -> "Internet"
            else -> "Installed App"
        }
    }

    suspend fun toggleAppLock(packageName: String, appName: String, locked: Boolean) {
        dao.setAppLockState(packageName, locked)
        refreshDirectBootLockedSet()
        dao.insertSecurityLog(
            SecurityLogEntity(
                eventType = if (locked) "APP_LOCKED" else "APP_UNLOCKED_POLICY",
                targetAppName = appName,
                targetPackage = packageName,
                authMethod = "SYSTEM",
                details = if (locked) {
                    "Enabled real-time foreground lock shield for $appName"
                } else {
                    "Removed foreground lock requirement for $appName"
                }
            )
        )
    }

    suspend fun setCustomAuthMode(packageName: String, authMode: String) {
        dao.setAppCustomAuthMode(packageName, authMode)
    }

    suspend fun lockAllSystemCriticalApps() {
        val all = dao.getAllAppsOnce()
        for (app in all) {
            if (app.isSystemCritical && !app.isLocked) {
                dao.setAppLockState(app.packageName, true)
            }
        }
        refreshDirectBootLockedSet()
    }

    suspend fun recordInterceptedLaunch(packageName: String, appName: String) {
        dao.incrementInterceptCount(packageName)
        dao.insertSecurityLog(
            SecurityLogEntity(
                eventType = "APP_INTERCEPTED",
                targetAppName = appName,
                targetPackage = packageName,
                authMethod = "SYSTEM",
                details = "Foreground launch intercepted by Aegis Overlay Guardian."
            )
        )
    }

    suspend fun logAuthEvent(
        success: Boolean,
        targetAppName: String,
        targetPackage: String,
        authMethod: String,
        photoPath: String? = null
    ): Triple<Int, Boolean, Int> {
        return if (success) {
            prefsManager.resetFailedAttempts()
            val rowId = dao.insertSecurityLog(
                SecurityLogEntity(
                    eventType = "APP_UNLOCKED",
                    targetAppName = targetAppName,
                    targetPackage = targetPackage,
                    authMethod = authMethod,
                    attemptNumber = 0,
                    details = "Verified via $authMethod. Cooldown counter reset to 0/5."
                )
            ).toInt()
            Triple(0, false, rowId)
        } else {
            val (attempts, triggeredCooldown) = prefsManager.recordFailedAttempt()
            val baseDetails = if (triggeredCooldown) {
                "CRITICAL: 5th failed attempt ($authMethod) on $targetAppName! Enforcing mandatory 30-second system lockout."
            } else {
                "Invalid $authMethod attempt ($attempts/5 before 30s lockout) on $targetAppName."
            }
            val finalDetails = if (!photoPath.isNullOrBlank()) {
                "$baseDetails • Intruder selfie captured."
            } else {
                baseDetails
            }
            val rowId = dao.insertSecurityLog(
                SecurityLogEntity(
                    eventType = if (triggeredCooldown) "COOLDOWN_TRIGGERED" else "FAILED_ATTEMPT",
                    targetAppName = targetAppName,
                    targetPackage = targetPackage,
                    authMethod = authMethod,
                    attemptNumber = attempts,
                    details = finalDetails,
                    photoPath = photoPath
                )
            ).toInt()
            Triple(attempts, triggeredCooldown, rowId)
        }
    }

    suspend fun attachIntruderSelfieToLog(logId: Int, photoPath: String, baseDetails: String) {
        dao.attachIntruderPhotoToLog(
            id = logId,
            photoPath = photoPath,
            details = "$baseDetails • Intruder selfie captured."
        )
    }

    suspend fun addCustomLog(eventType: String, title: String, details: String, photoPath: String? = null) {
        dao.insertSecurityLog(
            SecurityLogEntity(
                eventType = eventType,
                targetAppName = title,
                targetPackage = context.packageName,
                authMethod = "SYSTEM",
                details = details,
                photoPath = photoPath
            )
        )
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(SecurityCrypto.getDeviceProtectedContext(context).filesDir, "intruder_selfies")
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
            }
        }
        dao.clearSecurityLogs()
    }

    private suspend fun refreshDirectBootLockedSet() {
        val lockedSet = dao.getAllAppsOnce()
            .filter { it.isLocked }
            .map { it.packageName }
            .toSet()
        prefsManager.syncLockedPackageSet(lockedSet)
    }
}
