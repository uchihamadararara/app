package com.example.system

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricModality(val title: String, val subtitle: String) {
    FINGERPRINT_AND_FACE(
        title = "Biometric Authentication",
        subtitle = "Authenticate using your device's Fingerprint or Face sensor"
    ),
    FINGERPRINT(
        title = "Fingerprint Authentication",
        subtitle = "Touch the in-display or hardware fingerprint sensor"
    ),
    FACE(
        title = "Face Authentication",
        subtitle = "Look at the front camera to verify your identity"
    )
}

data class BiometricHardwareDiagnostics(
    val canAuthenticateAny: Boolean,
    val canAuthenticateStrong: Boolean,
    val canAuthenticateWeak: Boolean,
    val statusCode: Int,
    val statusTitle: String,
    val statusDescription: String,
    val needsOsEnrollment: Boolean
)

/**
 * Full integration with AndroidX Biometric library (`androidx.biometric:biometric`).
 * Supports Class 3 (`BIOMETRIC_STRONG`) and Class 2 (`BIOMETRIC_WEAK`) authenticators
 * for both Fingerprint and Face authentication via `BiometricPrompt`.
 */
object BiometricAuthHelper {

    private const val ALLOWED_AUTHENTICATORS =
        Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK

    fun getDiagnostics(context: Context): BiometricHardwareDiagnostics {
        val bm = BiometricManager.from(context)
        val combinedResult = bm.canAuthenticate(ALLOWED_AUTHENTICATORS)
        val strongResult = bm.canAuthenticate(Authenticators.BIOMETRIC_STRONG)
        val weakResult = bm.canAuthenticate(Authenticators.BIOMETRIC_WEAK)

        val (title, description, needsEnroll) = when (combinedResult) {
            BiometricManager.BIOMETRIC_SUCCESS -> Triple(
                "BiometricPrompt Ready",
                "Fingerprint and/or Face credentials are enrolled in Android OS and ready for BiometricPrompt.",
                false
            )
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Triple(
                "No Biometrics Enrolled in OS",
                "Biometric hardware is present, but no Fingerprint or Face is enrolled in Android Settings.",
                true
            )
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> Triple(
                "No Hardware Biometric Sensor",
                "This device/emulator does not report a hardware biometric sensor. In-app scanner fallback is active.",
                false
            )
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> Triple(
                "Sensor Temporarily Unavailable",
                "The biometric sensor is currently busy or unavailable. Try again shortly.",
                false
            )
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> Triple(
                "Security Update Required",
                "A system security update is required to use the biometric sensor.",
                false
            )
            else -> Triple(
                "Biometric Status ($combinedResult)",
                "Standard BiometricPrompt fallback mode enabled.",
                false
            )
        }

        return BiometricHardwareDiagnostics(
            canAuthenticateAny = combinedResult == BiometricManager.BIOMETRIC_SUCCESS,
            canAuthenticateStrong = strongResult == BiometricManager.BIOMETRIC_SUCCESS,
            canAuthenticateWeak = weakResult == BiometricManager.BIOMETRIC_SUCCESS,
            statusCode = combinedResult,
            statusTitle = title,
            statusDescription = description,
            needsOsEnrollment = needsEnroll
        )
    }

    fun canUseHardwareBiometrics(context: Context): Boolean {
        return getDiagnostics(context).canAuthenticateAny
    }

    /**
     * Builds an Intent to open Android's system Biometric Enrollment screen
     * (`Settings.ACTION_BIOMETRIC_ENROLL` on Android 11+, or `Settings.ACTION_SECURITY_SETTINGS` on older versions)
     * so users can enroll their Fingerprint or Face in the OS.
     */
    fun buildBiometricEnrollIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(
                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    ALLOWED_AUTHENTICATORS
                )
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    /**
     * Launches the official AndroidX `BiometricPrompt` for Fingerprint and/or Face authentication.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String = "Use Password",
        onSuccess: () -> Unit,
        onFailureAttempt: (String) -> Unit,
        onHardwareUnavailableOrNotEnrolled: (String) -> Unit
    ) {
        val diagnostics = getDiagnostics(activity)
        if (!diagnostics.canAuthenticateAny) {
            onHardwareUnavailableOrNotEnrolled(diagnostics.statusDescription)
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onFailureAttempt("Biometric not recognized. Please try again.")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    onFailureAttempt(errString.toString())
                }
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .setNegativeButtonText(negativeButtonText)
            .setConfirmationRequired(false)
            .build()

        runCatching {
            prompt.authenticate(promptInfo)
        }.onFailure { err ->
            onHardwareUnavailableOrNotEnrolled(
                err.localizedMessage ?: "Unable to launch BiometricPrompt."
            )
        }
    }

    /**
     * Authenticates specifically for enabling or verifying a modality in Security Settings.
     */
    fun authenticateForSettingsModality(
        activity: FragmentActivity,
        modality: BiometricModality,
        onVerified: (String) -> Unit,
        onError: (String) -> Unit,
        onFallbackRequired: (String) -> Unit
    ) {
        authenticate(
            activity = activity,
            title = modality.title,
            subtitle = modality.subtitle,
            negativeButtonText = "Cancel",
            onSuccess = {
                onVerified("Verified via Android BiometricPrompt")
            },
            onFailureAttempt = onError,
            onHardwareUnavailableOrNotEnrolled = onFallbackRequired
        )
    }
}
