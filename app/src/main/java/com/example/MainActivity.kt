package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.LockedAppEntity
import com.example.system.BiometricAuthHelper
import com.example.system.SystemPermissionHelper
import com.example.ui.AegisViewModel
import com.example.ui.components.LockScreenOverlayContent
import com.example.ui.screens.AppVaultScreen
import com.example.ui.screens.AuthAndCooldownScreen
import com.example.ui.screens.IntruderLogsScreen
import com.example.ui.screens.LockSetupWizardScreen
import com.example.ui.screens.SystemControlScreen
import com.example.ui.theme.ClassicPrimaryBlue
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

enum class AegisTab(
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    VAULT(R.string.tab_apps, Icons.Filled.Shield, Icons.Outlined.Shield, "nav_tab_vault"),
    AUTH(R.string.tab_auth, Icons.Filled.Fingerprint, Icons.Outlined.Fingerprint, "nav_tab_auth"),
    SYSTEM(R.string.tab_system, Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings, "nav_tab_system"),
    LOGS(R.string.tab_logs, Icons.Filled.History, Icons.Outlined.History, "nav_tab_logs")
}

class MainActivity : FragmentActivity() {

    private val viewModel: AegisViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                runCatching {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
                }
            }
        }

        setContent {
            MyApplicationTheme(darkTheme = false) {
                AegisMainApp(
                    activity = this@MainActivity,
                    viewModel = viewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSystemCapabilities()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AegisMainApp(
    activity: FragmentActivity,
    viewModel: AegisViewModel
) {
    val apps by viewModel.allApps.collectAsStateWithLifecycle()
    val logs by viewModel.securityLogs.collectAsStateWithLifecycle()
    val vaultState by viewModel.vaultState.collectAsStateWithLifecycle()
    val capabilities by viewModel.systemCapabilities.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val isAppSessionUnlocked by viewModel.isAppSessionUnlocked.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(AegisTab.VAULT) }
    var activeOverlayTargetApp by remember { mutableStateOf<LockedAppEntity?>(null) }
    var isPhoneLockPreview by remember { mutableStateOf(false) }
    var isEditingLockMethod by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshSystemCapabilities()
    }

    val settingsIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshSystemCapabilities()
    }

    // BackHandler for secondary screens and overlays
    BackHandler(
        enabled = activeOverlayTargetApp != null || isEditingLockMethod || currentTab != AegisTab.VAULT
    ) {
        when {
            activeOverlayTargetApp != null -> {
                activeOverlayTargetApp = null
                isPhoneLockPreview = false
            }
            isEditingLockMethod -> isEditingLockMethod = false
            else -> currentTab = AegisTab.VAULT
        }
    }

    // 1. First-Launch Setup Wizard (No default password is set until user chooses one!)
    if (!vaultState.isSetupCompleted || isEditingLockMethod) {
        LockSetupWizardScreen(
            onSavePin = { pin, len ->
                viewModel.saveInitialOrUpdatedPin(pin, len)
                isEditingLockMethod = false
            },
            onSavePattern = { pattern ->
                viewModel.saveInitialOrUpdatedPattern(pattern)
                isEditingLockMethod = false
            },
            onSaveMixedPassword = { password ->
                viewModel.saveInitialOrUpdatedMixedPassword(password)
                isEditingLockMethod = false
            },
            onCancelIfEditing = if (vaultState.isSetupCompleted) {
                { isEditingLockMethod = false }
            } else {
                null
            }
        )
        return
    }

    // 2. Gate Aegis Lock app itself OR show Full-Screen Phone/App Lock Screen
    if (!isAppSessionUnlocked || activeOverlayTargetApp != null) {
        val target = activeOverlayTargetApp ?: LockedAppEntity(
            packageName = activity.packageName,
            appName = "Aegis Lock",
            category = "System",
            isSystemCritical = true,
            isLocked = true
        )
        val showAsPhoneLock = isPhoneLockPreview || !isAppSessionUnlocked

        LockScreenOverlayContent(
            targetAppName = if (showAsPhoneLock) "Phone Lock Screen" else target.appName,
            targetPackageName = target.packageName,
            vaultState = vaultState,
            isPhoneLockScreen = showAsPhoneLock,
            onVerifyPin = { pin -> viewModel.repository.prefsManager.verifyPin(pin) },
            onVerifyPattern = { pattern -> viewModel.repository.prefsManager.verifyPattern(pattern) },
            onVerifyMixedPassword = { pwd -> viewModel.repository.prefsManager.verifyMixedPassword(pwd) },
            onAuthSuccess = { method ->
                scope.launch {
                    viewModel.repository.logAuthEvent(
                        success = true,
                        targetAppName = if (showAsPhoneLock) "Phone Lock Screen" else target.appName,
                        targetPackage = target.packageName,
                        authMethod = method
                    )
                    viewModel.markAppSessionUnlocked(true)
                    activeOverlayTargetApp = null
                    isPhoneLockPreview = false
                }
            },
            onAuthFailure = { method ->
                scope.launch {
                    viewModel.repository.logAuthEvent(
                        success = false,
                        targetAppName = if (showAsPhoneLock) "Phone Lock Screen" else target.appName,
                        targetPackage = target.packageName,
                        authMethod = method
                    )
                }
            },
            onTriggerHardwareBiometric = {
                BiometricAuthHelper.authenticate(
                    activity = activity,
                    title = if (showAsPhoneLock) "Unlock Phone" else "Unlock ${target.appName}",
                    subtitle = "Verify Fingerprint or Face",
                    onSuccess = {
                        scope.launch {
                            viewModel.repository.logAuthEvent(
                                success = true,
                                targetAppName = if (showAsPhoneLock) "Phone Lock Screen" else target.appName,
                                targetPackage = target.packageName,
                                authMethod = "BIOMETRIC"
                            )
                            viewModel.markAppSessionUnlocked(true)
                            activeOverlayTargetApp = null
                            isPhoneLockPreview = false
                        }
                    },
                    onFailureAttempt = {
                        scope.launch {
                            viewModel.repository.logAuthEvent(
                                success = false,
                                targetAppName = if (showAsPhoneLock) "Phone Lock Screen" else target.appName,
                                targetPackage = target.packageName,
                                authMethod = "BIOMETRIC"
                            )
                        }
                    },
                    onHardwareUnavailableOrNotEnrolled = { msg ->
                        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                    }
                )
            },
            onCooldownExpired = {
                viewModel.refreshSystemCapabilities()
            },
            onExitToHomeScreen = if (!isAppSessionUnlocked) {
                null
            } else {
                {
                    activeOverlayTargetApp = null
                    isPhoneLockPreview = false
                }
            }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            val sample = apps.firstOrNull { it.isLocked }
                                ?: LockedAppEntity(
                                    packageName = "com.android.settings",
                                    appName = "Settings",
                                    category = "System",
                                    isSystemCritical = true,
                                    isLocked = true
                                )
                            activeOverlayTargetApp = sample
                        },
                        modifier = Modifier.testTag("topbar_test_lock_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Preview Lock Screen",
                            tint = ClassicPrimaryBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                AegisTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = stringResource(tab.titleRes)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(tab.titleRes),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ClassicPrimaryBlue,
                            selectedTextColor = ClassicPrimaryBlue,
                            indicatorColor = ClassicPrimaryBlue.copy(alpha = 0.12f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = bannerMessage != null) {
                bannerMessage?.let { msg ->
                    Surface(
                        color = ClassicPrimaryBlue.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ClassicPrimaryBlue.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = ClassicPrimaryBlue,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.dismissBannerMessage() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = ClassicPrimaryBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (currentTab) {
                    AegisTab.VAULT -> {
                        AppVaultScreen(
                            apps = apps,
                            vaultState = vaultState,
                            capabilities = capabilities,
                            searchQuery = searchQuery,
                            selectedCategory = selectedCategory,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onCategorySelect = viewModel::selectCategory,
                            onToggleMasterShield = viewModel::toggleMasterShield,
                            onTogglePhoneScreenLock = viewModel::togglePhoneScreenLock,
                            onLockPhoneScreenNow = {
                                isPhoneLockPreview = true
                                activeOverlayTargetApp = LockedAppEntity(
                                    packageName = activity.packageName,
                                    appName = "Phone Lock Screen",
                                    category = "System",
                                    isSystemCritical = true,
                                    isLocked = true
                                )
                                viewModel.lockPhoneScreenNow()
                            },
                            onToggleAppLock = viewModel::toggleAppLock,
                            onLockAllCriticalSystemApps = viewModel::lockAllCriticalSystemApps,
                            onTestLockScreen = { app ->
                                isPhoneLockPreview = false
                                activeOverlayTargetApp = app
                            },
                            onNavigateToSystemControl = {
                                currentTab = AegisTab.SYSTEM
                            }
                        )
                    }

                    AegisTab.AUTH -> {
                        AuthAndCooldownScreen(
                            vaultState = vaultState,
                            capabilities = capabilities,
                            onOpenChangePasswordWizard = {
                                isEditingLockMethod = true
                            },
                            onUpdateBiometricSettings = viewModel::updateBiometricSettings,
                            onAuthenticateWithBiometricPrompt = { modality, onVerified, onFallbackNeeded ->
                                BiometricAuthHelper.authenticateForSettingsModality(
                                    activity = activity,
                                    modality = modality,
                                    onVerified = { msg ->
                                        onVerified(msg)
                                        Toast.makeText(
                                            activity,
                                            "${modality.title}: $msg",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onError = { err ->
                                        Toast.makeText(activity, err, Toast.LENGTH_SHORT).show()
                                    },
                                    onFallbackRequired = { reason ->
                                        onFallbackNeeded(reason)
                                        Toast.makeText(activity, reason, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            onOpenOsBiometricEnrollment = {
                                runCatching {
                                    val enrollIntent = BiometricAuthHelper.buildBiometricEnrollIntent()
                                    settingsIntentLauncher.launch(enrollIntent)
                                }
                            },
                            onResetCooldownAndStrikes = viewModel::resetCooldownAndStrikes,
                            onOpenLiveLockScreenTest = {
                                val sample = apps.firstOrNull { it.isLocked }
                                    ?: LockedAppEntity(
                                        packageName = "com.android.settings",
                                        appName = "Settings",
                                        category = "System",
                                        isSystemCritical = true,
                                        isLocked = true
                                    )
                                activeOverlayTargetApp = sample
                            }
                        )
                    }

                    AegisTab.SYSTEM -> {
                        SystemControlScreen(
                            vaultState = vaultState,
                            capabilities = capabilities,
                            onRequestDeviceAdmin = {
                                runCatching {
                                    val intent = SystemPermissionHelper.buildEnableDeviceAdminIntent(activity)
                                    deviceAdminLauncher.launch(intent)
                                }
                            },
                            onRevokeDeviceAdmin = {
                                SystemPermissionHelper.revokeDeviceAdmin(activity)
                                viewModel.refreshSystemCapabilities()
                            },
                            onRequestOverlayPermission = {
                                runCatching {
                                    val intent = SystemPermissionHelper.buildOverlayPermissionIntent(activity)
                                    settingsIntentLauncher.launch(intent)
                                }
                            },
                            onRequestUsageStatsPermission = {
                                runCatching {
                                    val intent = SystemPermissionHelper.buildUsageStatsPermissionIntent(activity)
                                    settingsIntentLauncher.launch(intent)
                                }
                            },
                            onUpdatePersistencePolicies = viewModel::updatePersistencePolicies,
                            onTriggerBootVerification = viewModel::triggerBootCompletedVerification,
                            onRefreshStatus = viewModel::refreshSystemCapabilities
                        )
                    }

                    AegisTab.LOGS -> {
                        IntruderLogsScreen(
                            logs = logs,
                            onClearLogs = viewModel::clearSecurityLogs
                        )
                    }
                }
            }
        }
    }
}
