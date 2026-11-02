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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.SecurityVaultState
import com.example.system.SystemSecurityCapabilities
import com.example.ui.theme.ClassicDangerRed
import com.example.ui.theme.ClassicPrimaryBlue
import com.example.ui.theme.ClassicSuccessGreen
import com.example.ui.theme.ClassicWarningAmber

@Composable
fun SystemControlScreen(
    vaultState: SecurityVaultState,
    capabilities: SystemSecurityCapabilities,
    onRequestDeviceAdmin: () -> Unit,
    onRevokeDeviceAdmin: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestUsageStatsPermission: () -> Unit,
    onUpdatePersistencePolicies: (autoBoot: Boolean, preventUninstall: Boolean, relockScreenOff: Boolean) -> Unit,
    onTriggerBootVerification: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("system_control_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Device Administrator (Prevent Uninstall)
        item {
            ClassicPermissionCard(
                title = "Prevent Uninstall (Device Administrator)",
                subtitle = if (capabilities.isDeviceAdminActive) "Enabled • Uninstall is blocked" else "Not enabled",
                description = "Stops anyone from uninstalling this app without your permission.",
                icon = Icons.Default.Shield,
                isGranted = capabilities.isDeviceAdminActive,
                buttonText = if (capabilities.isDeviceAdminActive) "Manage Device Admin" else "Enable Device Admin",
                onClick = onRequestDeviceAdmin,
                secondaryButtonText = if (capabilities.isDeviceAdminActive) "Turn Off" else null,
                onSecondaryClick = if (capabilities.isDeviceAdminActive) onRevokeDeviceAdmin else null,
                testTagPrefix = "device_admin"
            )
        }

        // 2. Display Over Other Apps
        item {
            ClassicPermissionCard(
                title = "Display Over Other Apps",
                subtitle = if (capabilities.canDrawOverlays) "Allowed" else "Permission needed",
                description = "Required to show the lock screen on top of locked apps when they are opened.",
                icon = Icons.Default.Layers,
                isGranted = capabilities.canDrawOverlays,
                buttonText = if (capabilities.canDrawOverlays) "Permission Granted" else "Allow Display Over Apps",
                onClick = onRequestOverlayPermission,
                testTagPrefix = "overlay_permission"
            )
        }

        // 3. Usage Access
        item {
            ClassicPermissionCard(
                title = "Usage Access (Detect Opened Apps)",
                subtitle = if (capabilities.hasUsageStatsAccess) "Allowed" else "Permission needed",
                description = "Allows App Lock to detect when a locked app is launched.",
                icon = Icons.Default.SettingsApplications,
                isGranted = capabilities.hasUsageStatsAccess,
                buttonText = if (capabilities.hasUsageStatsAccess) "Permission Granted" else "Allow Usage Access",
                onClick = onRequestUsageStatsPermission,
                testTagPrefix = "usage_access"
            )
        }

        // 4. Phone Restart & Lock Settings
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
                                Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                tint = ClassicPrimaryBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Phone Restart & Auto-Lock",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Your password and locked apps stay active after restart",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    ClassicSwitchRow(
                        title = "Start Automatically After Phone Restart",
                        subtitle = "Keeps apps locked immediately when phone turns back on",
                        checked = vaultState.autoStartOnBoot,
                        onCheckedChange = {
                            onUpdatePersistencePolicies(
                                it,
                                vaultState.preventUninstallShield,
                                vaultState.relockOnScreenOff
                            )
                        },
                        testTag = "auto_boot_switch"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outline
                    )

                    ClassicSwitchRow(
                        title = "Lock Apps When Screen Turns Off",
                        subtitle = "Requires password again after locking the phone screen",
                        checked = vaultState.relockOnScreenOff,
                        onCheckedChange = {
                            onUpdatePersistencePolicies(
                                vaultState.autoStartOnBoot,
                                vaultState.preventUninstallShield,
                                it
                            )
                        },
                        testTag = "relock_screen_off_switch"
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassicPermissionCard(
    title: String,
    subtitle: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    buttonText: String,
    onClick: () -> Unit,
    secondaryButtonText: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
    testTagPrefix: String
) {
    val badgeColor = if (isGranted) ClassicSuccessGreen else ClassicWarningAmber

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
                        imageVector = icon,
                        contentDescription = null,
                        tint = ClassicPrimaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isGranted) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ClassicSuccessGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = badgeColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isGranted) ClassicSuccessGreen else ClassicPrimaryBlue
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("${testTagPrefix}_primary_button")
                ) {
                    Text(buttonText, fontWeight = FontWeight.SemiBold)
                }

                if (secondaryButtonText != null && onSecondaryClick != null) {
                    OutlinedButton(
                        onClick = onSecondaryClick,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ClassicDangerRed.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("${testTagPrefix}_secondary_button")
                    ) {
                        Text(secondaryButtonText, color = ClassicDangerRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassicSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ClassicPrimaryBlue
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
