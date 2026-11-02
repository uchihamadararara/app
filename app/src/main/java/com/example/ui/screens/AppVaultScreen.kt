package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.LockedAppEntity
import com.example.data.SecurityVaultState
import com.example.system.SystemSecurityCapabilities
import com.example.ui.components.ClassicShieldCanvasGraphic
import com.example.ui.components.NativeAppIconAvatar
import com.example.ui.theme.ClassicPrimaryBlue
import com.example.ui.theme.ClassicSuccessGreen

@Composable
fun AppVaultScreen(
    apps: List<LockedAppEntity>,
    vaultState: SecurityVaultState,
    capabilities: SystemSecurityCapabilities,
    searchQuery: String,
    selectedCategory: String,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    onToggleMasterShield: (Boolean) -> Unit,
    onTogglePhoneScreenLock: (Boolean) -> Unit = {},
    onLockPhoneScreenNow: () -> Unit = {},
    onToggleAppLock: (LockedAppEntity) -> Unit,
    onLockAllCriticalSystemApps: () -> Unit,
    onTestLockScreen: (LockedAppEntity) -> Unit,
    onNavigateToSystemControl: () -> Unit
) {
    val categories = listOf(
        "All",
        "Locked",
        "System",
        "Social & Mail",
        "Media",
        "Payment"
    )

    val filteredApps = remember(apps, searchQuery, selectedCategory) {
        apps.filter { app ->
            val matchesQuery = searchQuery.isBlank() ||
                app.appName.contains(searchQuery, ignoreCase = true)
            val matchesCat = when (selectedCategory) {
                "All" -> true
                "Locked" -> app.isLocked
                "System" -> app.isSystemCritical || app.category.contains("System", ignoreCase = true)
                else -> app.category.equals(selectedCategory, ignoreCase = true)
            }
            matchesQuery && matchesCat
        }
    }

    val lockedCount = remember(apps) { apps.count { it.isLocked } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_vault_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Clean Classic Native Status Header Card (with Canvas Shield & Phone Lock Screen Control)
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ClassicShieldCanvasGraphic(
                            isProtected = vaultState.masterShieldEnabled,
                            modifier = Modifier.size(62.dp)
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (vaultState.masterShieldEnabled) "App & Screen Lock ON" else "Protection Paused",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$lockedCount apps locked • ${vaultState.activeAuthMode.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = vaultState.masterShieldEnabled,
                            onCheckedChange = onToggleMasterShield,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ClassicPrimaryBlue
                            ),
                            modifier = Modifier.testTag("master_shield_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Phone Lock Screen Row
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Phone Lock Screen",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Locks phone screen when screen turns off/on",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = vaultState.phoneScreenLockEnabled,
                                onCheckedChange = onTogglePhoneScreenLock,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = ClassicPrimaryBlue
                                ),
                                modifier = Modifier.testTag("phone_screen_lock_switch")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onLockPhoneScreenNow,
                            colors = ButtonDefaults.buttonColors(containerColor = ClassicPrimaryBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("test_live_lock_screen_button")
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lock Phone Now", fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = onNavigateToSystemControl,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_system_control_button")
                        ) {
                            Text("Permissions", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        // 2. Search & Category Filter
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search apps...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ClassicPrimaryBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_apps_input")
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { category ->
                        val selected = category == selectedCategory
                        FilterChip(
                            selected = selected,
                            onClick = { onCategorySelect(category) },
                            label = { Text(category) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ClassicPrimaryBlue.copy(alpha = 0.14f),
                                selectedLabelColor = ClassicPrimaryBlue
                            ),
                            modifier = Modifier.testTag("category_chip_$category")
                        )
                    }
                }
            }
        }

        // 3. Classic Native Android App Rows
        items(filteredApps, key = { it.packageName }) { app ->
            ClassicAppItemRow(
                app = app,
                onToggleLock = { onToggleAppLock(app) },
                onOpenLockPreview = { onTestLockScreen(app) }
            )
        }
    }
}

@Composable
private fun ClassicAppItemRow(
    app: LockedAppEntity,
    onToggleLock: () -> Unit,
    onOpenLockPreview: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (app.isLocked) ClassicPrimaryBlue.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (app.isLocked) {
                    onOpenLockPreview()
                } else {
                    onToggleLock()
                }
            }
            .testTag("app_item_card_${app.packageName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NativeAppIconAvatar(
                packageName = app.packageName,
                appName = app.appName,
                size = 46.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (app.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (app.isLocked) ClassicSuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (app.isLocked) "Locked • Tap to test" else app.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (app.isLocked) ClassicSuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Switch(
                checked = app.isLocked,
                onCheckedChange = { onToggleLock() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ClassicPrimaryBlue
                ),
                modifier = Modifier.testTag("lock_switch_${app.packageName}")
            )
        }
    }
}
