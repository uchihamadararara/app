package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

enum class LockAuthMode(val label: String, val shortDesc: String) {
    PIN_4("4-Digit PIN", "Classic 4-digit numeric PIN"),
    PIN_6("6-Digit PIN", "High-security 6-digit numeric PIN"),
    PATTERN("Pattern Lock", "Connect at least 4 dots on a 3×3 grid"),
    MIXED_PASSWORD("Mixed Password", "Letters, username, numbers & symbols (Full Keyboard)"),
    BIOMETRIC_AND_PIN("PIN + Biometrics", "4/6-Digit PIN with Fingerprint & Face"),
    BIOMETRIC_AND_PATTERN("Pattern + Biometrics", "Pattern with Fingerprint & Face"),
    PIN_ONLY("PIN Only", "Numeric PIN"),
    PATTERN_ONLY("Pattern Only", "3×3 Pattern")
}

data class SecurityVaultState(
    val isSetupCompleted: Boolean = false,
    val masterShieldEnabled: Boolean = true,
    val biometricEnabled: Boolean = true,
    val faceUnlockEnabled: Boolean = false,
    val fingerprintEnabled: Boolean = false,
    val activeAuthMode: LockAuthMode = LockAuthMode.PIN_4,
    val pinHash: String = "",
    val patternHash: String = "",
    val mixedPasswordHash: String = "",
    val pinLength: Int = 4,
    val failedAttempts: Int = 0,
    val totalLockoutsTriggered: Int = 0,
    val cooldownUntilEpochMs: Long = 0L,
    val maxAttemptsBeforeCooldown: Int = 5,
    val cooldownDurationSeconds: Int = 30,
    val autoStartOnBoot: Boolean = true,
    val preventUninstallShield: Boolean = true,
    val relockOnScreenOff: Boolean = true,
    val phoneScreenLockEnabled: Boolean = true,
    val intruderSelfieEnabled: Boolean = true,
    val notificationPrivacyEnabled: Boolean = true,
    val lastBootRestoredTimestamp: Long = 0L,
    val bootRestoreCount: Int = 0
)

/**
 * Direct-Boot Aware Credential & Cooldown Storage.
 */
object SecurityCrypto {
    private const val PREFS_NAME = "aegis_native_vault_prefs_v2"
    private const val SALT = "AEGIS_NATIVE_SHA256_SALT_v2_"

    fun hashCredential(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((SALT + raw).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun getDeviceProtectedContext(context: Context): Context {
        val appCtx = context.applicationContext ?: context
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appCtx.createDeviceProtectedStorageContext() ?: appCtx
        } else {
            appCtx
        }
    }

    fun getPrefs(context: Context): SharedPreferences {
        return getDeviceProtectedContext(context)
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}

class SecurityPreferencesManager(context: Context) {
    private val prefs: SharedPreferences = SecurityCrypto.getPrefs(context)

    @Volatile
    private var cachedLockedPackages: Set<String> =
        prefs.getStringSet(KEY_LOCKED_PACKAGES_SET, emptySet())?.toSet() ?: emptySet()

    private val _state = MutableStateFlow(readState())
    val state: StateFlow<SecurityVaultState> = _state.asStateFlow()

    // Keep in-memory state synced automatically on preference changes — zero polling or disk lag!
    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_LOCKED_PACKAGES_SET) {
            cachedLockedPackages =
                prefs.getStringSet(KEY_LOCKED_PACKAGES_SET, emptySet())?.toSet() ?: emptySet()
        } else {
            _state.value = readState()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
    }

    fun refreshState() {
        cachedLockedPackages =
            prefs.getStringSet(KEY_LOCKED_PACKAGES_SET, emptySet())?.toSet() ?: emptySet()
        _state.value = readState()
    }

    private fun readState(): SecurityVaultState {
        val modeName = prefs.getString(KEY_AUTH_MODE, LockAuthMode.PIN_4.name)
            ?: LockAuthMode.PIN_4.name
        val authMode = runCatching { LockAuthMode.valueOf(modeName) }
            .getOrDefault(LockAuthMode.PIN_4)

        val pinH = prefs.getString(KEY_PIN_HASH, "") ?: ""
        val patH = prefs.getString(KEY_PATTERN_HASH, "") ?: ""
        val mixH = prefs.getString(KEY_MIXED_HASH, "") ?: ""
        val setupDone = prefs.getBoolean(KEY_SETUP_COMPLETED, false) &&
            (pinH.isNotEmpty() || patH.isNotEmpty() || mixH.isNotEmpty())

        return SecurityVaultState(
            isSetupCompleted = setupDone,
            masterShieldEnabled = prefs.getBoolean(KEY_MASTER_SHIELD, true),
            biometricEnabled = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, true),
            faceUnlockEnabled = prefs.getBoolean(KEY_FACE_ENABLED, false),
            fingerprintEnabled = prefs.getBoolean(KEY_FINGERPRINT_ENABLED, false),
            activeAuthMode = authMode,
            pinHash = pinH,
            patternHash = patH,
            mixedPasswordHash = mixH,
            pinLength = prefs.getInt(KEY_PIN_LENGTH, 4),
            failedAttempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0),
            totalLockoutsTriggered = prefs.getInt(KEY_TOTAL_LOCKOUTS, 0),
            cooldownUntilEpochMs = prefs.getLong(KEY_COOLDOWN_UNTIL, 0L),
            maxAttemptsBeforeCooldown = 5,
            cooldownDurationSeconds = 30,
            autoStartOnBoot = prefs.getBoolean(KEY_AUTO_BOOT, true),
            preventUninstallShield = prefs.getBoolean(KEY_PREVENT_UNINSTALL, true),
            relockOnScreenOff = prefs.getBoolean(KEY_RELOCK_SCREEN_OFF, true),
            phoneScreenLockEnabled = prefs.getBoolean(KEY_PHONE_SCREEN_LOCK, true),
            intruderSelfieEnabled = prefs.getBoolean(KEY_INTRUDER_SELFIE, true),
            notificationPrivacyEnabled = prefs.getBoolean(KEY_NOTIFICATION_PRIVACY, true),
            lastBootRestoredTimestamp = prefs.getLong(KEY_LAST_BOOT_TIMESTAMP, 0L),
            bootRestoreCount = prefs.getInt(KEY_BOOT_RESTORE_COUNT, 0)
        )
    }

    fun setPhoneScreenLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PHONE_SCREEN_LOCK, enabled).apply()
        refreshState()
    }

    fun setIntruderSelfieEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INTRUDER_SELFIE, enabled).apply()
        refreshState()
    }

    fun setNotificationPrivacyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_PRIVACY, enabled).apply()
        refreshState()
    }

    fun verifyPin(rawPin: String): Boolean {
        val currentHash = prefs.getString(KEY_PIN_HASH, "") ?: ""
        if (currentHash.isEmpty()) return false
        return SecurityCrypto.hashCredential(rawPin) == currentHash
    }

    fun verifyPattern(patternNodes: List<Int>): Boolean {
        val serialized = patternNodes.joinToString("-")
        val currentHash = prefs.getString(KEY_PATTERN_HASH, "") ?: ""
        if (currentHash.isEmpty()) return false
        return SecurityCrypto.hashCredential(serialized) == currentHash
    }

    fun verifyMixedPassword(rawPassword: String): Boolean {
        val currentHash = prefs.getString(KEY_MIXED_HASH, "") ?: ""
        if (currentHash.isEmpty()) return false
        return SecurityCrypto.hashCredential(rawPassword.trim()) == currentHash
    }

    fun setupOrUpdatePin(newRawPin: String, length: Int = newRawPin.length) {
        val mode = if (length == 6) LockAuthMode.PIN_6 else LockAuthMode.PIN_4
        prefs.edit()
            .putString(KEY_PIN_HASH, SecurityCrypto.hashCredential(newRawPin))
            .putInt(KEY_PIN_LENGTH, length)
            .putString(KEY_AUTH_MODE, mode.name)
            .putBoolean(KEY_SETUP_COMPLETED, true)
            .apply()
        refreshState()
    }

    fun updatePin(newRawPin: String) {
        setupOrUpdatePin(newRawPin, newRawPin.length)
    }

    fun setupOrUpdatePattern(patternNodes: List<Int>) {
        val serialized = patternNodes.joinToString("-")
        prefs.edit()
            .putString(KEY_PATTERN_HASH, SecurityCrypto.hashCredential(serialized))
            .putString(KEY_AUTH_MODE, LockAuthMode.PATTERN.name)
            .putBoolean(KEY_SETUP_COMPLETED, true)
            .apply()
        refreshState()
    }

    fun updatePattern(patternNodes: List<Int>) {
        setupOrUpdatePattern(patternNodes)
    }

    fun setupOrUpdateMixedPassword(rawPassword: String) {
        prefs.edit()
            .putString(KEY_MIXED_HASH, SecurityCrypto.hashCredential(rawPassword.trim()))
            .putString(KEY_AUTH_MODE, LockAuthMode.MIXED_PASSWORD.name)
            .putBoolean(KEY_SETUP_COMPLETED, true)
            .apply()
        refreshState()
    }

    fun clearAllCredentialsForReSetup() {
        prefs.edit()
            .putBoolean(KEY_SETUP_COMPLETED, false)
            .remove(KEY_PIN_HASH)
            .remove(KEY_PATTERN_HASH)
            .remove(KEY_MIXED_HASH)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_COOLDOWN_UNTIL, 0L)
            .apply()
        refreshState()
    }

    fun recordFailedAttempt(nowEpochMs: Long = System.currentTimeMillis()): Pair<Int, Boolean> {
        val newCount = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val triggeredCooldown = (newCount % 5 == 0)
        val editor = prefs.edit().putInt(KEY_FAILED_ATTEMPTS, newCount)
        if (triggeredCooldown) {
            val cooldownEnd = nowEpochMs + 30_000L
            val lockouts = prefs.getInt(KEY_TOTAL_LOCKOUTS, 0) + 1
            editor.putLong(KEY_COOLDOWN_UNTIL, cooldownEnd)
            editor.putInt(KEY_TOTAL_LOCKOUTS, lockouts)
        }
        editor.apply()
        refreshState()
        return newCount to triggeredCooldown
    }

    fun resetFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_COOLDOWN_UNTIL, 0L)
            .apply()
        refreshState()
    }

    fun clearExpiredCooldownIfNeeded(nowEpochMs: Long = System.currentTimeMillis()) {
        val until = prefs.getLong(KEY_COOLDOWN_UNTIL, 0L)
        if (until in 1..nowEpochMs) {
            prefs.edit().putLong(KEY_COOLDOWN_UNTIL, 0L).apply()
            refreshState()
        }
    }

    fun setMasterShieldEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER_SHIELD, enabled).apply()
        refreshState()
    }

    fun setBiometricConfig(biometric: Boolean, fingerprint: Boolean, face: Boolean) {
        prefs.edit()
            .putBoolean(KEY_BIOMETRIC_ENABLED, biometric)
            .putBoolean(KEY_FINGERPRINT_ENABLED, fingerprint)
            .putBoolean(KEY_FACE_ENABLED, face)
            .apply()
        refreshState()
    }

    fun setAuthMode(mode: LockAuthMode) {
        val editor = prefs.edit().putString(KEY_AUTH_MODE, mode.name)
        if (mode == LockAuthMode.PIN_4) editor.putInt(KEY_PIN_LENGTH, 4)
        if (mode == LockAuthMode.PIN_6) editor.putInt(KEY_PIN_LENGTH, 6)
        editor.apply()
        refreshState()
    }

    fun setSystemPersistenceFlags(
        autoStartOnBoot: Boolean,
        preventUninstall: Boolean,
        relockOnScreenOff: Boolean
    ) {
        prefs.edit()
            .putBoolean(KEY_AUTO_BOOT, autoStartOnBoot)
            .putBoolean(KEY_PREVENT_UNINSTALL, preventUninstall)
            .putBoolean(KEY_RELOCK_SCREEN_OFF, relockOnScreenOff)
            .apply()
        refreshState()
    }

    fun recordBootRestoration(timestampMs: Long = System.currentTimeMillis()) {
        val count = prefs.getInt(KEY_BOOT_RESTORE_COUNT, 0) + 1
        prefs.edit()
            .putLong(KEY_LAST_BOOT_TIMESTAMP, timestampMs)
            .putInt(KEY_BOOT_RESTORE_COUNT, count)
            .apply()
        refreshState()
    }

    fun syncLockedPackageSet(packages: Set<String>) {
        cachedLockedPackages = packages.toSet()
        prefs.edit().putStringSet(KEY_LOCKED_PACKAGES_SET, packages).apply()
    }

    fun getLockedPackageSet(): Set<String> {
        return cachedLockedPackages
    }

    companion object {
        private const val KEY_SETUP_COMPLETED = "is_setup_completed"
        private const val KEY_MASTER_SHIELD = "master_shield_enabled"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_FACE_ENABLED = "face_unlock_enabled"
        private const val KEY_FINGERPRINT_ENABLED = "fingerprint_enabled"
        private const val KEY_AUTH_MODE = "active_auth_mode"
        private const val KEY_PIN_HASH = "pin_sha256_hash"
        private const val KEY_PATTERN_HASH = "pattern_sha256_hash"
        private const val KEY_MIXED_HASH = "mixed_password_sha256_hash"
        private const val KEY_PIN_LENGTH = "pin_length"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts_count"
        private const val KEY_TOTAL_LOCKOUTS = "total_lockouts_triggered"
        private const val KEY_COOLDOWN_UNTIL = "cooldown_until_epoch_ms"
        private const val KEY_AUTO_BOOT = "auto_start_on_boot"
        private const val KEY_PREVENT_UNINSTALL = "prevent_uninstall_shield"
        private const val KEY_RELOCK_SCREEN_OFF = "relock_on_screen_off"
        private const val KEY_PHONE_SCREEN_LOCK = "phone_screen_lock_enabled"
        private const val KEY_INTRUDER_SELFIE = "intruder_selfie_enabled"
        private const val KEY_NOTIFICATION_PRIVACY = "notification_privacy_enabled"
        private const val KEY_LAST_BOOT_TIMESTAMP = "last_boot_restored_timestamp"
        private const val KEY_BOOT_RESTORE_COUNT = "boot_restore_count"
        private const val KEY_LOCKED_PACKAGES_SET = "locked_packages_direct_boot_set"
    }
}
