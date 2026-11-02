package com.example.system

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AegisDatabase
import com.example.data.AegisRepository
import com.example.data.SecurityPreferencesManager
import com.example.ui.components.LockScreenOverlayContent
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

/**
 * Full-Screen System Lock & Phone Lock Screen Activity.
 * - Renders directly over the Android Keyguard / Phone Lock Screen (setShowWhenLocked(true)).
 * - Also intercepts locked apps when launched in the foreground.
 */
class LockOverlayActivity : FragmentActivity() {

    private lateinit var repository: AegisRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenWindowFlags()
        enableEdgeToEdge()

        val db = AegisDatabase.getInstance(applicationContext)
        val prefs = SecurityPreferencesManager(applicationContext)
        repository = AegisRepository(applicationContext, db.aegisDao(), prefs)

        val targetPackage = intent?.getStringExtra(EXTRA_TARGET_PACKAGE) ?: packageName
        val isPreviewMode = intent?.getBooleanExtra(EXTRA_IS_PREVIEW_MODE, false) ?: false
        val isPhoneLockScreen = intent?.getBooleanExtra(EXTRA_IS_PHONE_LOCK_SCREEN, false) ?: false

        // Block back button from bypassing the lock screen
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isPreviewMode) {
                        finish()
                    } else if (!isPhoneLockScreen) {
                        navigateToLauncherHome()
                    }
                    // When isPhoneLockScreen == true, back button does nothing until unlocked!
                }
            }
        )

        setContent {
            MyApplicationTheme(darkTheme = false) {
                val vaultState by repository.vaultStateFlow.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                var resolvedAppName by remember {
                    mutableStateOf(
                        if (isPhoneLockScreen) {
                            "Phone Lock Screen"
                        } else {
                            intent?.getStringExtra(EXTRA_TARGET_APP_NAME) ?: targetPackage
                        }
                    )
                }

                LaunchedEffect(targetPackage, isPhoneLockScreen) {
                    if (!isPhoneLockScreen) {
                        val entity = db.aegisDao().getAppByPackage(targetPackage)
                        if (entity != null) {
                            resolvedAppName = entity.appName
                        }
                        repository.recordInterceptedLaunch(targetPackage, resolvedAppName)
                    }
                }

                LockScreenOverlayContent(
                    targetAppName = resolvedAppName,
                    targetPackageName = targetPackage,
                    vaultState = vaultState,
                    isPhoneLockScreen = isPhoneLockScreen,
                    onVerifyPin = { pin -> repository.prefsManager.verifyPin(pin) },
                    onVerifyPattern = { pattern -> repository.prefsManager.verifyPattern(pattern) },
                    onVerifyMixedPassword = { pwd -> repository.prefsManager.verifyMixedPassword(pwd) },
                    onAuthSuccess = { method ->
                        scope.launch {
                            repository.logAuthEvent(
                                success = true,
                                targetAppName = resolvedAppName,
                                targetPackage = targetPackage,
                                authMethod = method
                            )
                            if (!isPhoneLockScreen) {
                                AppLockMonitorService.markPackageUnlockedForSession(targetPackage)
                            }
                            finish()
                        }
                    },
                    onAuthFailure = { method ->
                        scope.launch {
                            repository.logAuthEvent(
                                success = false,
                                targetAppName = resolvedAppName,
                                targetPackage = targetPackage,
                                authMethod = method
                            )
                        }
                    },
                    onTriggerHardwareBiometric = {
                        BiometricAuthHelper.authenticate(
                            activity = this@LockOverlayActivity,
                            title = if (isPhoneLockScreen) "Unlock Phone" else "Unlock $resolvedAppName",
                            subtitle = "Scan Fingerprint or Face",
                            onSuccess = {
                                scope.launch {
                                    repository.logAuthEvent(
                                        success = true,
                                        targetAppName = resolvedAppName,
                                        targetPackage = targetPackage,
                                        authMethod = "BIOMETRIC"
                                    )
                                    if (!isPhoneLockScreen) {
                                        AppLockMonitorService.markPackageUnlockedForSession(targetPackage)
                                    }
                                    finish()
                                }
                            },
                            onFailureAttempt = {
                                scope.launch {
                                    repository.logAuthEvent(
                                        success = false,
                                        targetAppName = resolvedAppName,
                                        targetPackage = targetPackage,
                                        authMethod = "BIOMETRIC"
                                    )
                                }
                            },
                            onHardwareUnavailableOrNotEnrolled = {}
                        )
                    },
                    onCooldownExpired = {
                        repository.prefsManager.clearExpiredCooldownIfNeeded()
                    },
                    onExitToHomeScreen = if (isPhoneLockScreen && !isPreviewMode) {
                        null
                    } else {
                        {
                            if (isPreviewMode) {
                                finish()
                            } else {
                                navigateToLauncherHome()
                            }
                        }
                    }
                )
            }
        }
    }

    private fun configureLockScreenWindowFlags() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
                val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                km?.requestDismissKeyguard(this, null)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                )
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun navigateToLauncherHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
        const val EXTRA_TARGET_APP_NAME = "extra_target_app_name"
        const val EXTRA_IS_PREVIEW_MODE = "extra_is_preview_mode"
        const val EXTRA_IS_PHONE_LOCK_SCREEN = "extra_is_phone_lock_screen"
    }
}
