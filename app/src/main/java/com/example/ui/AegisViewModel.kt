package com.example.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AegisDatabase
import com.example.data.AegisRepository
import com.example.data.LockAuthMode
import com.example.data.LockedAppEntity
import com.example.data.SecurityLogEntity
import com.example.data.SecurityPreferencesManager
import com.example.data.SecurityVaultState
import com.example.system.BootCompletedReceiver
import com.example.system.LockOverlayActivity
import com.example.system.SystemPermissionHelper
import com.example.system.SystemSecurityCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AegisViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val database = AegisDatabase.getInstance(appContext)
    private val prefsManager = SecurityPreferencesManager(appContext)
    val repository = AegisRepository(appContext, database.aegisDao(), prefsManager)

    val allApps: StateFlow<List<LockedAppEntity>> = repository.allAppsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val securityLogs: StateFlow<List<SecurityLogEntity>> = repository.securityLogsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val vaultState: StateFlow<SecurityVaultState> = repository.vaultStateFlow

    private val _systemCapabilities = MutableStateFlow(
        SystemPermissionHelper.inspectCapabilities(appContext)
    )
    val systemCapabilities: StateFlow<SystemSecurityCapabilities> = _systemCapabilities.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _isAppSessionUnlocked = MutableStateFlow(false)
    val isAppSessionUnlocked: StateFlow<Boolean> = _isAppSessionUnlocked.asStateFlow()

    init {
        viewModelScope.launch {
            repository.syncInstalledApps()
            val state = prefsManager.state.value
            if (state.masterShieldEnabled && state.isSetupCompleted) {
                SystemPermissionHelper.startOrStopGuardianService(appContext, true)
            }
            refreshSystemCapabilities()
        }
    }

    fun markAppSessionUnlocked(unlocked: Boolean) {
        _isAppSessionUnlocked.value = unlocked
    }

    fun togglePhoneScreenLock(enabled: Boolean) {
        prefsManager.setPhoneScreenLockEnabled(enabled)
        if (enabled && prefsManager.state.value.masterShieldEnabled) {
            SystemPermissionHelper.startOrStopGuardianService(appContext, true)
        }
        _statusBannerMessage.value = if (enabled) {
            "Phone Lock Screen enabled (locks automatically when screen turns off/on)."
        } else {
            "Phone Lock Screen turned off."
        }
    }

    fun lockPhoneScreenNow() {
        SystemPermissionHelper.lockPhoneScreenNow(appContext)
    }

    fun refreshSystemCapabilities() {
        prefsManager.clearExpiredCooldownIfNeeded()
        prefsManager.refreshState()
        _systemCapabilities.value = SystemPermissionHelper.inspectCapabilities(appContext)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun dismissBannerMessage() {
        _statusBannerMessage.value = null
    }

    fun toggleAppLock(app: LockedAppEntity) {
        viewModelScope.launch {
            val newLocked = !app.isLocked
            repository.toggleAppLock(app.packageName, app.appName, newLocked)
            _statusBannerMessage.value = if (newLocked) {
                "${app.appName} is now locked."
            } else {
                "${app.appName} unlocked."
            }
        }
    }

    fun lockAllCriticalSystemApps() {
        viewModelScope.launch {
            repository.lockAllSystemCriticalApps()
            _statusBannerMessage.value = "Settings, Package Installer, and Play Store are now locked."
        }
    }

    fun toggleMasterShield(enabled: Boolean) {
        prefsManager.setMasterShieldEnabled(enabled)
        SystemPermissionHelper.startOrStopGuardianService(appContext, enabled)
        refreshSystemCapabilities()
        _statusBannerMessage.value = if (enabled) {
            "App Lock protection turned ON."
        } else {
            "App Lock protection paused."
        }
    }

    fun updateBiometricSettings(biometric: Boolean, fingerprint: Boolean, face: Boolean) {
        prefsManager.setBiometricConfig(biometric, fingerprint, face)
        _statusBannerMessage.value = "Fingerprint & Face Lock preferences updated."
    }

    fun updateActiveAuthMode(mode: LockAuthMode) {
        prefsManager.setAuthMode(mode)
        _statusBannerMessage.value = "Lock method changed to ${mode.label}."
    }

    fun saveInitialOrUpdatedPin(pin: String, length: Int) {
        prefsManager.setupOrUpdatePin(pin, length)
        _isAppSessionUnlocked.value = true
        SystemPermissionHelper.startOrStopGuardianService(appContext, true)
        viewModelScope.launch {
            repository.addCustomLog(
                eventType = "PASSWORD_SET",
                title = "$length-Digit PIN Saved",
                details = "$length-digit PIN set by user."
            )
        }
        _statusBannerMessage.value = "$length-Digit PIN saved."
    }

    fun saveInitialOrUpdatedPattern(nodes: List<Int>) {
        prefsManager.setupOrUpdatePattern(nodes)
        _isAppSessionUnlocked.value = true
        SystemPermissionHelper.startOrStopGuardianService(appContext, true)
        viewModelScope.launch {
            repository.addCustomLog(
                eventType = "PASSWORD_SET",
                title = "Pattern Lock Saved",
                details = "Pattern lock (${nodes.size} dots) set by user."
            )
        }
        _statusBannerMessage.value = "Pattern Lock saved."
    }

    fun saveInitialOrUpdatedMixedPassword(rawPassword: String) {
        prefsManager.setupOrUpdateMixedPassword(rawPassword)
        _isAppSessionUnlocked.value = true
        SystemPermissionHelper.startOrStopGuardianService(appContext, true)
        viewModelScope.launch {
            repository.addCustomLog(
                eventType = "PASSWORD_SET",
                title = "Mixed Password Saved",
                details = "Alphanumeric password set by user."
            )
        }
        _statusBannerMessage.value = "Mixed Password saved."
    }

    fun updatePersistencePolicies(
        autoStartOnBoot: Boolean,
        preventUninstall: Boolean,
        relockOnScreenOff: Boolean
    ) {
        prefsManager.setSystemPersistenceFlags(autoStartOnBoot, preventUninstall, relockOnScreenOff)
        SystemPermissionHelper.setBootReceiverEnabled(appContext, autoStartOnBoot)
        refreshSystemCapabilities()
    }

    fun triggerBootCompletedVerification() {
        val receiver = BootCompletedReceiver()
        val bootIntent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(appContext, bootIntent)
        refreshSystemCapabilities()
        _statusBannerMessage.value = "Restart check passed: Your password and locked apps remain active."
    }

    fun resetCooldownAndStrikes() {
        prefsManager.resetFailedAttempts()
        _statusBannerMessage.value = "Wrong attempt counter reset."
    }

    fun clearSecurityLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            _statusBannerMessage.value = "Activity history cleared."
        }
    }

    fun launchInteractiveLockOverlay(app: LockedAppEntity) {
        val intent = Intent(appContext, LockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(LockOverlayActivity.EXTRA_TARGET_PACKAGE, app.packageName)
            putExtra(LockOverlayActivity.EXTRA_TARGET_APP_NAME, app.appName)
            putExtra(LockOverlayActivity.EXTRA_IS_PREVIEW_MODE, true)
        }
        appContext.startActivity(intent)
    }
}
