package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.SecurityVaultState
import com.example.system.BiometricAuthHelper
import com.example.system.BiometricModality
import com.example.system.SystemSecurityCapabilities
import com.example.ui.components.InDisplayFingerprintSensorZone
import com.example.ui.theme.ClassicDangerRed
import com.example.ui.theme.ClassicPrimaryBlue
import com.example.ui.theme.ClassicSuccessGreen
import com.example.ui.theme.ClassicWarningAmber
import kotlinx.coroutines.delay

@Composable
fun AuthAndCooldownScreen(
    vaultState: SecurityVaultState,
    capabilities: SystemSecurityCapabilities,
    onOpenChangePasswordWizard: () -> Unit,
    onUpdateBiometricSettings: (biometric: Boolean, fingerprint: Boolean, face: Boolean) -> Unit,
    onAuthenticateWithBiometricPrompt: (
        modality: BiometricModality,
        onVerified: (String) -> Unit,
        onFallbackNeeded: (String) -> Unit
    ) -> Unit,
    onOpenOsBiometricEnrollment: () -> Unit,
    onResetCooldownAndStrikes: () -> Unit,
    onOpenLiveLockScreenTest: () -> Unit
) {
    val context = LocalContext.current
    val biometricDiagnostics = remember(capabilities) {
        BiometricAuthHelper.getDiagnostics(context)
    }

    var biometricPromptFeedback by remember { mutableStateOf<String?>(null) }
    var isFeedbackSuccess by remember { mutableStateOf(true) }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val remainingCooldownMs = (vaultState.cooldownUntilEpochMs - nowMs).coerceAtLeast(0L)
    val isInCooldown = remainingCooldownMs > 0L
    val remainingSeconds = ((remainingCooldownMs + 999L) / 1000L).toInt()

    LaunchedEffect(vaultState.cooldownUntilEpochMs) {
        while (vaultState.cooldownUntilEpochMs > System.currentTimeMillis()) {
            nowMs = System.currentTimeMillis()
            delay(200L)
        }
        nowMs = System.currentTimeMillis()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("auth_and_cooldown_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Real Android Hardware BiometricPrompt Status Banner
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(
                    1.dp,
                    if (biometricDiagnostics.canAuthenticateAny) {
                        ClassicSuccessGreen.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (biometricDiagnostics.canAuthenticateAny) {
                                        ClassicSuccessGreen.copy(alpha = 0.12f)
                                    } else {
                                        ClassicPrimaryBlue.copy(alpha = 0.10f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = if (biometricDiagnostics.canAuthenticateAny) {
                                    ClassicSuccessGreen
                                } else {
                                    ClassicPrimaryBlue
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "100% Real System BiometricPrompt",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = biometricDiagnostics.statusTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (biometricDiagnostics.canAuthenticateAny) {
                                    ClassicSuccessGreen
                                } else {
                                    ClassicWarningAmber
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = biometricDiagnostics.statusDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (biometricPromptFeedback != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = if (isFeedbackSuccess) {
                                ClassicSuccessGreen.copy(alpha = 0.10f)
                            } else {
                                ClassicWarningAmber.copy(alpha = 0.12f)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isFeedbackSuccess) ClassicSuccessGreen else ClassicWarningAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = biometricPromptFeedback!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isFeedbackSuccess) ClassicSuccessGreen else ClassicWarningAmber
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onAuthenticateWithBiometricPrompt(
                                    BiometricModality.FINGERPRINT_AND_FACE,
                                    { msg ->
                                        biometricPromptFeedback = "$msg • Hardware Fingerprint & Face enabled!"
                                        isFeedbackSuccess = true
                                        onUpdateBiometricSettings(true, true, true)
                                    },
                                    { fallbackMsg ->
                                        biometricPromptFeedback = fallbackMsg
                                        isFeedbackSuccess = false
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ClassicPrimaryBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_native_biometric_prompt_button")
                        ) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Verify Hardware Biometrics", fontWeight = FontWeight.SemiBold)
                        }

                        if (biometricDiagnostics.needsOsEnrollment) {
                            OutlinedButton(
                                onClick = onOpenOsBiometricEnrollment,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, ClassicPrimaryBlue.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("enroll_os_biometrics_button")
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = ClassicPrimaryBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Enroll in OS", color = ClassicPrimaryBlue)
                            }
                        }
                    }
                }
            }
        }

        // 2. Password / PIN / Pattern Type Card
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ClassicPrimaryBlue.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = ClassicPrimaryBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Password & Lock Type",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Active: ${vaultState.activeAuthMode.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ClassicPrimaryBlue
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Switch between 4-Digit PIN, 6-Digit PIN, Pattern Lock, or Mixed Password (full keyboard with letters, numbers & symbols).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onOpenChangePasswordWizard,
                            colors = ButtonDefaults.buttonColors(containerColor = ClassicPrimaryBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("change_password_type_button")
                        ) {
                            Text("Change Lock Type", fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = onOpenLiveLockScreenTest,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_lock_screen_from_auth_button")
                        ) {
                            Text("Test Lock Screen")
                        }
                    }
                }
            }
        }

        // 3. Real Hardware Fingerprint Authentication
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ClassicPrimaryBlue.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = ClassicPrimaryBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Fingerprint Authentication",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (vaultState.fingerprintEnabled) {
                                        "Active • Uses phone's real hardware fingerprint sensor"
                                    } else {
                                        "Verify with your phone's registered fingerprint"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (vaultState.fingerprintEnabled) {
                                        ClassicSuccessGreen
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        Switch(
                            checked = vaultState.fingerprintEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    onAuthenticateWithBiometricPrompt(
                                        BiometricModality.FINGERPRINT,
                                        { msg ->
                                            biometricPromptFeedback = "Hardware Fingerprint verified ($msg)"
                                            isFeedbackSuccess = true
                                            onUpdateBiometricSettings(
                                                true,
                                                true,
                                                vaultState.faceUnlockEnabled
                                            )
                                        },
                                        { fallbackMsg ->
                                            biometricPromptFeedback = fallbackMsg
                                            isFeedbackSuccess = false
                                        }
                                    )
                                } else {
                                    onUpdateBiometricSettings(
                                        vaultState.faceUnlockEnabled,
                                        false,
                                        vaultState.faceUnlockEnabled
                                    )
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ClassicPrimaryBlue
                            ),
                            modifier = Modifier.testTag("fingerprint_toggle_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    InDisplayFingerprintSensorZone(
                        label = "Place your finger on the blank sensor spot below — the Fingerprint icon & animation will play on touch",
                        hideUntilTouched = true,
                        onTriggerSystemBiometric = {
                            onAuthenticateWithBiometricPrompt(
                                BiometricModality.FINGERPRINT,
                                { msg ->
                                    biometricPromptFeedback = "Hardware Fingerprint authenticated ($msg)"
                                    isFeedbackSuccess = true
                                    onUpdateBiometricSettings(
                                        true,
                                        true,
                                        vaultState.faceUnlockEnabled
                                    )
                                },
                                { fallbackMsg ->
                                    biometricPromptFeedback = fallbackMsg
                                    isFeedbackSuccess = false
                                }
                            )
                        }
                    )
                }
            }
        }

        // 4. Real Hardware Face Authentication (OS BiometricPrompt Face Unlock)
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ClassicPrimaryBlue.copy(alpha = 0.10f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Face,
                                    contentDescription = null,
                                    tint = ClassicPrimaryBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Face Authentication",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (vaultState.faceUnlockEnabled) {
                                        "Active • Uses phone's real OS Face Unlock"
                                    } else {
                                        "Verify with your phone's system Face Unlock"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (vaultState.faceUnlockEnabled) {
                                        ClassicSuccessGreen
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        Switch(
                            checked = vaultState.faceUnlockEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    onAuthenticateWithBiometricPrompt(
                                        BiometricModality.FACE,
                                        { msg ->
                                            biometricPromptFeedback = "System Face Unlock verified ($msg)"
                                            isFeedbackSuccess = true
                                            onUpdateBiometricSettings(
                                                true,
                                                vaultState.fingerprintEnabled,
                                                true
                                            )
                                        },
                                        { fallbackMsg ->
                                            biometricPromptFeedback = fallbackMsg
                                            isFeedbackSuccess = false
                                        }
                                    )
                                } else {
                                    onUpdateBiometricSettings(
                                        vaultState.fingerprintEnabled,
                                        vaultState.fingerprintEnabled,
                                        false
                                    )
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ClassicPrimaryBlue
                            ),
                            modifier = Modifier.testTag("face_unlock_toggle_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onAuthenticateWithBiometricPrompt(
                                    BiometricModality.FACE,
                                    { msg ->
                                        biometricPromptFeedback = "System Face BiometricPrompt verified ($msg)"
                                        isFeedbackSuccess = true
                                        onUpdateBiometricSettings(
                                            true,
                                            vaultState.fingerprintEnabled,
                                            true
                                        )
                                    },
                                    { fallbackMsg ->
                                        biometricPromptFeedback = fallbackMsg
                                        isFeedbackSuccess = false
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ClassicPrimaryBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("biometric_prompt_face_button")
                        ) {
                            Icon(
                                Icons.Default.Face,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Verify System Face", fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = onOpenOsBiometricEnrollment,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ClassicPrimaryBlue.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scan_register_face_button")
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = ClassicPrimaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Phone Face Settings",
                                color = ClassicPrimaryBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 5. 5 Attempts -> 30 Seconds Cooldown Status
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(
                    1.dp,
                    if (isInCooldown) ClassicDangerRed else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isInCooldown) ClassicDangerRed.copy(alpha = 0.12f)
                                    else ClassicPrimaryBlue.copy(alpha = 0.10f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                tint = if (isInCooldown) ClassicDangerRed else ClassicPrimaryBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "30-Second Cooldown Protection",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isInCooldown) {
                                    "Cooldown active: ${remainingSeconds}s remaining"
                                } else {
                                    "Locks for 30 seconds after every 5 wrong attempts (${vaultState.failedAttempts % 5}/5 used)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isInCooldown) ClassicDangerRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (vaultState.failedAttempts > 0 || isInCooldown) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline
                        )
                        OutlinedButton(
                            onClick = onResetCooldownAndStrikes,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("reset_cooldown_button")
                        ) {
                            Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Wrong Attempt Counter")
                        }
                    }
                }
            }
        }
    }
}
