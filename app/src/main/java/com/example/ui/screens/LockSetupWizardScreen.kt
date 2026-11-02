package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LockAuthMode
import com.example.ui.components.ClassicShieldCanvasGraphic
import com.example.ui.components.PatternLockSection
import com.example.ui.theme.ClassicDangerRed
import com.example.ui.theme.ClassicPrimaryBlue
import com.example.ui.theme.JetBrainsMonoFamily

@Composable
fun LockSetupWizardScreen(
    onSavePin: (pin: String, length: Int) -> Unit,
    onSavePattern: (List<Int>) -> Unit,
    onSaveMixedPassword: (String) -> Unit,
    onCancelIfEditing: (() -> Unit)? = null
) {
    var selectedType by remember { mutableStateOf<LockAuthMode?>(null) }

    // Step state for confirming credential
    var firstEntry by remember { mutableStateOf("") }
    var secondEntry by remember { mutableStateOf("") }
    var firstPattern by remember { mutableStateOf<List<Int>>(emptyList()) }
    var isConfirmStep by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPasswordText by remember { mutableStateOf(false) }

    fun resetEntries() {
        firstEntry = ""
        secondEntry = ""
        firstPattern = emptyList()
        isConfirmStep = false
        errorMessage = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 500.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (selectedType != null || onCancelIfEditing != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (selectedType != null) {
                                selectedType = null
                                resetEntries()
                            } else {
                                onCancelIfEditing?.invoke()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Text(
                        text = if (selectedType != null) "Choose another lock type" else "Back",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            ClassicShieldCanvasGraphic(
                isProtected = true,
                modifier = Modifier.size(76.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedType == null) {
                Text(
                    text = "Set Up Your App Lock",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Choose how you want to unlock your apps. You can also add Fingerprint and Face Lock anytime.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(22.dp))

                LockTypeOptionCard(
                    icon = Icons.Default.Dialpad,
                    title = "4-Digit PIN",
                    subtitle = "Quick 4-digit numeric passcode",
                    onClick = {
                        resetEntries()
                        selectedType = LockAuthMode.PIN_4
                    },
                    testTag = "setup_option_pin4"
                )

                Spacer(modifier = Modifier.height(12.dp))

                LockTypeOptionCard(
                    icon = Icons.Default.Password,
                    title = "6-Digit PIN",
                    subtitle = "Stronger 6-digit numeric passcode",
                    onClick = {
                        resetEntries()
                        selectedType = LockAuthMode.PIN_6
                    },
                    testTag = "setup_option_pin6"
                )

                Spacer(modifier = Modifier.height(12.dp))

                LockTypeOptionCard(
                    icon = Icons.Default.GridOn,
                    title = "Pattern Lock",
                    subtitle = "Draw a pattern connecting at least 4 dots",
                    onClick = {
                        resetEntries()
                        selectedType = LockAuthMode.PATTERN
                    },
                    testTag = "setup_option_pattern"
                )

                Spacer(modifier = Modifier.height(12.dp))

                LockTypeOptionCard(
                    icon = Icons.Default.Keyboard,
                    title = "Mixed Password (Keyboard)",
                    subtitle = "Use your name, letters, numbers & symbols",
                    onClick = {
                        resetEntries()
                        selectedType = LockAuthMode.MIXED_PASSWORD
                    },
                    testTag = "setup_option_mixed"
                )
            } else {
                // Step 2: Create & Confirm chosen lock type
                when (selectedType) {
                    LockAuthMode.PIN_4, LockAuthMode.PIN_6 -> {
                        val targetLen = if (selectedType == LockAuthMode.PIN_6) 6 else 4
                        val currentValue = if (isConfirmStep) secondEntry else firstEntry

                        Text(
                            text = if (!isConfirmStep) "Create $targetLen-Digit PIN" else "Confirm Your $targetLen-Digit PIN",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMessage ?: if (!isConfirmStep) {
                                "Enter a $targetLen-digit PIN you will remember"
                            } else {
                                "Re-enter the same $targetLen-digit PIN to confirm"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (errorMessage != null) ClassicDangerRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // PIN dots
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (i in 0 until targetLen) {
                                val filled = i < currentValue.length
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(if (filled) ClassicPrimaryBlue else Color.Transparent)
                                        .border(
                                            width = 2.dp,
                                            color = if (filled) ClassicPrimaryBlue else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        ClassicNumericPad(
                            onDigit = { digit ->
                                errorMessage = null
                                if (!isConfirmStep) {
                                    if (firstEntry.length < targetLen) {
                                        firstEntry += digit
                                        if (firstEntry.length == targetLen) {
                                            isConfirmStep = true
                                        }
                                    }
                                } else {
                                    if (secondEntry.length < targetLen) {
                                        secondEntry += digit
                                        if (secondEntry.length == targetLen) {
                                            if (secondEntry == firstEntry) {
                                                onSavePin(firstEntry, targetLen)
                                            } else {
                                                secondEntry = ""
                                                errorMessage = "PIN did not match. Please try again."
                                            }
                                        }
                                    }
                                }
                            },
                            onBackspace = {
                                if (!isConfirmStep) {
                                    if (firstEntry.isNotEmpty()) firstEntry = firstEntry.dropLast(1)
                                } else {
                                    if (secondEntry.isNotEmpty()) secondEntry = secondEntry.dropLast(1)
                                }
                            },
                            onClear = {
                                if (!isConfirmStep) firstEntry = "" else secondEntry = ""
                            }
                        )
                    }

                    LockAuthMode.PATTERN -> {
                        Text(
                            text = if (!isConfirmStep) "Draw Unlock Pattern" else "Draw Pattern Again to Confirm",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMessage ?: if (!isConfirmStep) {
                                "Connect at least 4 dots on the grid"
                            } else {
                                "Repeat your pattern to save it"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (errorMessage != null) ClassicDangerRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        PatternLockSection(
                            hintText = if (!isConfirmStep) "Step 1 of 2: Draw new pattern" else "Step 2 of 2: Confirm pattern",
                            showTestButtons = false,
                            onPatternComplete = { nodes ->
                                if (nodes.size < 4) {
                                    errorMessage = "Please connect at least 4 dots."
                                } else if (!isConfirmStep) {
                                    errorMessage = null
                                    firstPattern = nodes
                                    isConfirmStep = true
                                } else {
                                    if (nodes == firstPattern) {
                                        onSavePattern(nodes)
                                    } else {
                                        errorMessage = "Pattern did not match. Try confirming again."
                                    }
                                }
                            }
                        )

                        if (isConfirmStep) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(onClick = { resetEntries() }) {
                                Text("Redraw First Pattern")
                            }
                        }
                    }

                    else -> {
                        // Mixed Alphanumeric Password (Full Keyboard)
                        Text(
                            text = "Set Mixed Password",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = errorMessage ?: "Use your name, letters, numbers, or symbols (minimum 4 characters)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (errorMessage != null) ClassicDangerRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                OutlinedTextField(
                                    value = firstEntry,
                                    onValueChange = {
                                        errorMessage = null
                                        firstEntry = it
                                    },
                                    label = { Text("Enter Password (e.g. Rahul@123)") },
                                    singleLine = true,
                                    visualTransformation = if (showPasswordText) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { showPasswordText = !showPasswordText }) {
                                            Icon(
                                                imageVector = if (showPasswordText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle password visibility"
                                            )
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Next
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ClassicPrimaryBlue
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("mixed_password_first_input")
                                )

                                OutlinedTextField(
                                    value = secondEntry,
                                    onValueChange = {
                                        errorMessage = null
                                        secondEntry = it
                                    },
                                    label = { Text("Confirm Password") },
                                    singleLine = true,
                                    visualTransformation = if (showPasswordText) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ClassicPrimaryBlue
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("mixed_password_confirm_input")
                                )

                                Button(
                                    onClick = {
                                        if (firstEntry.trim().length < 4) {
                                            errorMessage = "Password must be at least 4 characters."
                                        } else if (firstEntry.trim() != secondEntry.trim()) {
                                            errorMessage = "Passwords do not match."
                                        } else {
                                            onSaveMixedPassword(firstEntry.trim())
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ClassicPrimaryBlue),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("save_mixed_password_button")
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Save Password", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LockTypeOptionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ClassicPrimaryBlue.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = ClassicPrimaryBlue,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ClassicNumericPad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("CLR", "0", "DEL")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (key in row) {
                    Surface(
                        onClick = {
                            when (key) {
                                "CLR" -> onClear()
                                "DEL" -> onBackspace()
                                else -> onDigit(key)
                            }
                        },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shadowElevation = 1.dp,
                        modifier = Modifier
                            .size(70.dp)
                            .testTag("pin_key_$key")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (key == "DEL") {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                            } else {
                                Text(
                                    text = key,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = if (key == "CLR") 13.sp else 24.sp,
                                    color = if (key == "CLR") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
