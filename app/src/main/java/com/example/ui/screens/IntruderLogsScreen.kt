package com.example.ui.screens

import android.Manifest
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.SecurityLogEntity
import com.example.data.SecurityVaultState
import com.example.system.SystemSecurityCapabilities
import com.example.ui.theme.ClassicDangerRed
import com.example.ui.theme.ClassicPrimaryBlue
import com.example.ui.theme.ClassicSuccessGreen
import com.example.ui.theme.ClassicWarningAmber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun IntruderLogsScreen(
    logs: List<SecurityLogEntity>,
    vaultState: SecurityVaultState,
    capabilities: SystemSecurityCapabilities,
    onToggleIntruderSelfie: (Boolean) -> Unit,
    onRefreshCapabilities: () -> Unit,
    onClearLogs: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    var selectedSelfieLog by remember { mutableStateOf<SecurityLogEntity?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefreshCapabilities()
    }

    if (selectedSelfieLog != null) {
        val log = selectedSelfieLog!!
        val bmp = remember(log.photoPath) {
            log.photoPath?.let { path ->
                if (File(path).exists()) BitmapFactory.decodeFile(path)?.asImageBitmap() else null
            }
        }
        Dialog(onDismissRequest = { selectedSelfieLog = null }) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Intruder Selfie Evidence",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${log.targetAppName} • ${formatter.format(Date(log.timestamp))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { selectedSelfieLog = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "Intruder Selfie",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = log.details,
                        style = MaterialTheme.typography.bodySmall,
                        color = ClassicDangerRed
                    )
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("intruder_logs_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Intruder Selfie Configuration Card
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
                                    imageVector = Icons.Default.CameraFront,
                                    contentDescription = null,
                                    tint = ClassicPrimaryBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Intruder Selfie (Front Camera)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (vaultState.intruderSelfieEnabled && capabilities.hasCameraPermission) {
                                        "Active • Secretly snaps photo on wrong password"
                                    } else if (vaultState.intruderSelfieEnabled) {
                                        "Enabled • Allow Camera permission for real front-cam photo"
                                    } else {
                                        "Turned off"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (vaultState.intruderSelfieEnabled && capabilities.hasCameraPermission) {
                                        ClassicSuccessGreen
                                    } else {
                                        ClassicWarningAmber
                                    }
                                )
                            }
                        }

                        Switch(
                            checked = vaultState.intruderSelfieEnabled,
                            onCheckedChange = onToggleIntruderSelfie,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ClassicPrimaryBlue
                            ),
                            modifier = Modifier.testTag("intruder_selfie_switch")
                        )
                    }

                    if (!capabilities.hasCameraPermission) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ClassicPrimaryBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("grant_camera_for_intruder_button")
                        ) {
                            Icon(
                                Icons.Default.CameraFront,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Allow Camera for Intruder Selfie", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // 2. Recent Activity Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Security & Intruder History (${logs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (logs.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClearLogs,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("clear_logs_button")
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Clear",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        if (logs.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = ClassicPrimaryBlue,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Activity Yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Intruder selfies, masked notifications, and unlock events will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(logs, key = { it.id }) { log ->
                val isWarning = log.eventType == "COOLDOWN_TRIGGERED" || log.eventType == "FAILED_ATTEMPT"
                val isMaskedNotif = log.eventType == "NOTIFICATION_MASKED"
                val accent = when (log.eventType) {
                    "COOLDOWN_TRIGGERED" -> ClassicDangerRed
                    "FAILED_ATTEMPT" -> ClassicWarningAmber
                    "APP_UNLOCKED" -> ClassicSuccessGreen
                    else -> ClassicPrimaryBlue
                }

                val selfieBitmap = remember(log.photoPath) {
                    log.photoPath?.let { path ->
                        val file = File(path)
                        if (file.exists()) {
                            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
                        } else {
                            null
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("security_log_item_${log.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(accent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when {
                                        isWarning -> Icons.Default.WarningAmber
                                        isMaskedNotif -> Icons.Default.NotificationsOff
                                        else -> Icons.Default.CheckCircle
                                    },
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = log.targetAppName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = formatter.format(Date(log.timestamp)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = log.details,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (selfieBitmap != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Image(
                                bitmap = selfieBitmap,
                                contentDescription = "Captured Intruder Selfie",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedSelfieLog = log }
                            )
                        }
                    }
                }
            }
        }
    }
}
