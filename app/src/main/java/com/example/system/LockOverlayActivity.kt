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
 * - Re-evaluates target intent on onNewIntent() so every screen-off/screen-on cycle locks reliably.
 */
class LockOverlayActivity : FragmentActivity() {

    private lateinit var repository: AegisRepository
    private var currentTargetPackage by mutableStateOf("")
    private var currentTargetAppName by mutableStateOf("")
    private var currentIsPreviewMode by mutableStateOf(false)
    private var currentIsPhoneLockScreen by mutableStateOf(false)
    private var currentStartInAmbientAod by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenWindowFlags()
        enableEdgeToEdge()

        val db = AegisDatabase.getInstance(applicationContext)
        val prefs = SecurityPreferencesManager(applicationContext)
        repository = AegisRepository(applicationContext, db.aegisDao(), prefs)

        applyIntentExtras(intent)

        // Block back button from bypassing the lock screen
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (currentIsPreviewMode) {
                        finish()
                    } else if (!currentIsPhoneLockScreen) {
                        navigateToLauncherHome()
                    }
                    // When currentIsPhoneLockScreen == true, back button is completely blocked!
                }
            }
        )

        setContent {
            MyApplicationTheme(darkTheme = false) {
                val vaultState by repository.vaultStateFlow.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()
                var resolvedAppName by remember(currentTargetPackage, currentIsPhoneLockScreen, currentTargetAppName) {
                    mutableStateOf(
                        if (currentIsPhoneLockScreen) {
                            "Phone Lock Screen"
                        } else {
                            currentTargetAppName.ifBlank { currentTargetPackage }
                        }
                    )
                }

                LaunchedEffect(currentTargetPackage, currentIsPhoneLockScreen) {
                    if (!currentIsPhoneLockScreen && currentTargetPackage.isNotBlank()) {
                        val entity = db.aegisDao().getAppByPackage(currentTargetPackage)
                        if (entity != null) {
                            resolvedAppName = entity.appName
                        }
                        repository.recordInterceptedLaunch(currentTargetPackage, resolvedAppName)
                    }
                }

                fun handleFailedAttemptWithIntruderSelfie(authMethod: String) {
                    scope.launch {
                        val (attempts, triggeredCooldown, logId) = repository.logAuthEvent(
                            success = false,
                            targetAppName = resolvedAppName,
                            targetPackage = currentTargetPackage,
                            authMethod = authMethod
                        )
                        if (vaultState.intruderSelfieEnabled) {
                            val baseDetails = if (triggeredCooldown) {
                                "CRITICAL: 5th failed attempt ($authMethod) on $resolvedAppName! Enforcing mandatory 30-second system lockout."
                            } else {
                                "Invalid $authMethod attempt ($attempts/5 before 30s lockout) on $resolvedAppName."
                            }
                            IntruderSelfieHelper.captureIntruderSelfie(
                                context = this@LockOverlayActivity,
                                lifecycleOwner = this@LockOverlayActivity,
                                targetAppName = resolvedAppName,
                                attemptNumber = attempts,
                                authMethod = authMethod,
                                onPhotoSaved = { photoPath ->
                                    scope.launch {
                                        repository.attachIntruderSelfieToLog(logId, photoPath, baseDetails)
                                    }
                                }
                            )
                        }
                    }
                }

                LockScreenOverlayContent(
                    targetAppName = resolvedAppName,
                    targetPackageName = currentTargetPackage,
                    vaultState = vaultState,
                    isPhoneLockScreen = currentIsPhoneLockScreen,
                    startInAmbientAod = currentStartInAmbientAod,
                    onVerifyPin = { pin -> repository.prefsManager.verifyPin(pin) },
                    onVerifyPattern = { pattern -> repository.prefsManager.verifyPattern(pattern) },
                    onVerifyMixedPassword = { pwd -> repository.prefsManager.verifyMixedPassword(pwd) },
                    onAuthSuccess = { method ->
                        scope.launch {
                            repository.logAuthEvent(
                                success = true,
                                targetAppName = resolvedAppName,
                                targetPackage = currentTargetPackage,
                                authMethod = method
                            )
                            if (currentIsPhoneLockScreen) {
                                AppLockMonitorService.setHomeScreenGatePending(false)
                            } else {
                                AppLockMonitorService.markPackageUnlockedForSession(currentTargetPackage)
                            }
                            finish()
                        }
                    },
                    onAuthFailure = { method ->
                        handleFailedAttemptWithIntruderSelfie(method)
                    },
                    onTriggerHardwareBiometric = {
                        BiometricAuthHelper.authenticate(
                            activity = this@LockOverlayActivity,
                            title = if (currentIsPhoneLockScreen) "Unlock Phone" else "Unlock $resolvedAppName",
                            subtitle = "Use your phone's Fingerprint or Face Lock",
                            onSuccess = {
                                scope.launch {
                                    repository.logAuthEvent(
                                        success = true,
                                        targetAppName = resolvedAppName,
                                        targetPackage = currentTargetPackage,
                                        authMethod = "BIOMETRIC"
                                    )
                                    if (currentIsPhoneLockScreen) {
                                        AppLockMonitorService.setHomeScreenGatePending(false)
                                    } else {
                                        AppLockMonitorService.markPackageUnlockedForSession(currentTargetPackage)
                                    }
                                    finish()
                                }
                            },
                            onFailureAttempt = {
                                handleFailedAttemptWithIntruderSelfie("BIOMETRIC")
                            },
                            onHardwareUnavailableOrNotEnrolled = {}
                        )
                    },
                    onCooldownExpired = {
                        repository.prefsManager.clearExpiredCooldownIfNeeded()
                    },
                    onExitToHomeScreen = if (currentIsPhoneLockScreen && !currentIsPreviewMode) {
                        null
                    } else {
                        {
                            if (currentIsPreviewMode) {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntentExtras(intent)
    }

    private fun applyIntentExtras(intent: Intent?) {
        currentTargetPackage = intent?.getStringExtra(EXTRA_TARGET_PACKAGE) ?: packageName
        currentTargetAppName = intent?.getStringExtra(EXTRA_TARGET_APP_NAME) ?: ""
        currentIsPreviewMode = intent?.getBooleanExtra(EXTRA_IS_PREVIEW_MODE, false) ?: false
        currentIsPhoneLockScreen = intent?.getBooleanExtra(EXTRA_IS_PHONE_LOCK_SCREEN, false) ?: false
        currentStartInAmbientAod = intent?.getBooleanExtra(EXTRA_START_IN_AMBIENT_AOD, false) ?: false
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
        const val EXTRA_START_IN_AMBIENT_AOD = "extra_start_in_ambient_aod"
    }
}
