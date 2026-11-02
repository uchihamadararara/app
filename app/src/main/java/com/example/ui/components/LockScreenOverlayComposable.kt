package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LockAuthMode
import com.example.data.SecurityVaultState
import com.example.ui.screens.ClassicNumericPad
import com.example.ui.theme.ClassicDangerRed
import com.example.ui.theme.ClassicPrimaryBlue
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.delay
import kotlin.math.hypot

enum class ActiveInputTab {
    PIN,
    PATTERN,
    MIXED
}

@Composable
fun LockScreenOverlayContent(
    targetAppName: String,
    targetPackageName: String,
    vaultState: SecurityVaultState,
    isPhoneLockScreen: Boolean = false,
    onVerifyPin: (String) -> Boolean,
    onVerifyPattern: (List<Int>) -> Boolean,
    onVerifyMixedPassword: (String) -> Boolean = { false },
    onAuthSuccess: (method: String) -> Unit,
    onAuthFailure: (method: String) -> Unit,
    onTriggerHardwareBiometric: () -> Unit,
    onCooldownExpired: () -> Unit,
    onExitToHomeScreen: (() -> Unit)? = null
) {
    val context = LocalContext.current

    var currentInputTab by remember(vaultState.activeAuthMode) {
        mutableStateOf(
            when (vaultState.activeAuthMode) {
                LockAuthMode.PATTERN, LockAuthMode.BIOMETRIC_AND_PATTERN, LockAuthMode.PATTERN_ONLY -> ActiveInputTab.PATTERN
                LockAuthMode.MIXED_PASSWORD -> ActiveInputTab.MIXED
                else -> ActiveInputTab.PIN
            }
        )
    }

    var enteredPin by remember { mutableStateOf("") }
    var enteredMixed by remember { mutableStateOf("") }
    var showMixedText by remember { mutableStateOf(false) }
    var feedbackMessage by remember(isPhoneLockScreen, targetAppName) {
        mutableStateOf(
            if (isPhoneLockScreen) "Unlock your phone"
            else "Enter your password to unlock $targetAppName"
        )
    }
    var isErrorFeedback by remember { mutableStateOf(false) }
    var showFaceCameraDialog by remember { mutableStateOf(false) }

    // 5-Attempt -> 30-Second Fixed Cooldown Timer & Live Clock
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val remainingCooldownMs = (vaultState.cooldownUntilEpochMs - nowMs).coerceAtLeast(0L)
    val isInCooldown = remainingCooldownMs > 0L
    val remainingSeconds = ((remainingCooldownMs + 999L) / 1000L).toInt()

    val clockTimeStr = remember(nowMs / 1000L) {
        java.text.SimpleDateFormat("hh:mm", java.util.Locale.getDefault()).format(java.util.Date(nowMs))
    }
    val clockDateStr = remember(nowMs / 60_000L) {
        java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault()).format(java.util.Date(nowMs))
    }

    LaunchedEffect(vaultState.cooldownUntilEpochMs, isPhoneLockScreen) {
        while (true) {
            nowMs = System.currentTimeMillis()
            if (vaultState.cooldownUntilEpochMs in 1..nowMs) {
                onCooldownExpired()
                feedbackMessage = "Cooldown finished. Please enter your password."
                isErrorFeedback = false
            }
            delay(250L)
        }
    }

    if (showFaceCameraDialog) {
        CameraFaceScannerDialog(
            isEnrollmentMode = false,
            onDismiss = { showFaceCameraDialog = false },
            onFaceScanSuccess = {
                showFaceCameraDialog = false
                vibrateDevice(context, 40L)
                onAuthSuccess("FACE_LOCK")
            },
            onFaceScanFailed = {
                vibrateDevice(context, 130L)
                isErrorFeedback = true
                onAuthFailure("FACE_LOCK")
                val nextAttempt = vaultState.failedAttempts + 1
                feedbackMessage = if (nextAttempt % 5 == 0) {
                    "5 wrong attempts. Try again in 30 seconds."
                } else {
                    "Face not recognized (${nextAttempt % 5}/5 attempts)"
                }
            }
        )
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
                .widthIn(max = 480.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section: Either Classic Phone Lock Screen Clock OR App Icon Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = ClassicPrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPhoneLockScreen) "Phone Screen Locked" else "App Lock",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (onExitToHomeScreen != null) {
                        IconButton(
                            onClick = onExitToHomeScreen,
                            modifier = Modifier.testTag("close_lock_overlay_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (isPhoneLockScreen) {
                    // Classic Android Lock Screen Clock & Date
                    Text(
                        text = clockTimeStr,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = clockDateStr,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    NativeAppIconAvatar(
                        packageName = targetPackageName,
                        appName = targetAppName,
                        size = 64.dp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = targetAppName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = feedbackMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isErrorFeedback || isInCooldown) ClassicDangerRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (vaultState.failedAttempts % 5 > 0 && !isInCooldown) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Wrong attempts: ${vaultState.failedAttempts % 5} / 5",
                        style = MaterialTheme.typography.labelMedium,
                        color = ClassicDangerRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Middle section: Cooldown timer OR PIN / Pattern / Mixed input
            if (isInCooldown) {
                ClassicCooldownCard(
                    remainingSeconds = remainingSeconds,
                    remainingMs = remainingCooldownMs
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Optional switcher if user wants to switch between PIN / Pattern / Mixed
                    val availableTabs = buildList {
                        if (vaultState.pinHash.isNotEmpty() || currentInputTab == ActiveInputTab.PIN) add(ActiveInputTab.PIN to "PIN")
                        if (vaultState.patternHash.isNotEmpty() || currentInputTab == ActiveInputTab.PATTERN) add(ActiveInputTab.PATTERN to "Pattern")
                        if (vaultState.mixedPasswordHash.isNotEmpty() || currentInputTab == ActiveInputTab.MIXED) add(ActiveInputTab.MIXED to "Password")
                    }

                    if (availableTabs.size > 1) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            availableTabs.forEach { (tab, label) ->
                                FilterChip(
                                    selected = currentInputTab == tab,
                                    onClick = { currentInputTab = tab },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ClassicPrimaryBlue.copy(alpha = 0.14f),
                                        selectedLabelColor = ClassicPrimaryBlue
                                    )
                                )
                            }
                        }
                    }

                    // Face Unlock button if enabled
                    if (vaultState.faceUnlockEnabled) {
                        OutlinedButton(
                            onClick = { showFaceCameraDialog = true },
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, ClassicPrimaryBlue.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .testTag("quick_face_unlock_button")
                        ) {
                            Icon(
                                Icons.Default.Face,
                                contentDescription = "Face Lock",
                                tint = ClassicPrimaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlock with Face Scan", color = ClassicPrimaryBlue)
                        }
                    }

                    when (currentInputTab) {
                        ActiveInputTab.PIN -> {
                            val pinLen = vaultState.pinLength.coerceIn(4, 6)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                for (i in 0 until pinLen) {
                                    val filled = i < enteredPin.length
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
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

                            Spacer(modifier = Modifier.height(10.dp))

                            ClassicNumericPad(
                                onDigit = { digit ->
                                    if (enteredPin.length < pinLen) {
                                        vibrateDevice(context, 18L)
                                        enteredPin += digit
                                        if (enteredPin.length == pinLen) {
                                            if (onVerifyPin(enteredPin)) {
                                                vibrateDevice(context, 40L)
                                                enteredPin = ""
                                                isErrorFeedback = false
                                                onAuthSuccess("PIN")
                                            } else {
                                                vibrateDevice(context, 130L)
                                                enteredPin = ""
                                                isErrorFeedback = true
                                                onAuthFailure("PIN")
                                                val nextAttempt = vaultState.failedAttempts + 1
                                                feedbackMessage = if (nextAttempt % 5 == 0) {
                                                    "5 wrong attempts! Locked for 30 seconds."
                                                } else {
                                                    "Wrong PIN (${nextAttempt % 5}/5 attempts)"
                                                }
                                            }
                                        }
                                    }
                                },
                                onBackspace = {
                                    if (enteredPin.isNotEmpty()) {
                                        enteredPin = enteredPin.dropLast(1)
                                    }
                                },
                                onClear = { enteredPin = "" }
                            )
                        }

                        ActiveInputTab.PATTERN -> {
                            PatternLockSection(
                                hintText = "Draw your unlock pattern",
                                showTestButtons = false,
                                onPatternComplete = { nodes ->
                                    if (nodes.size < 4) {
                                        isErrorFeedback = true
                                        feedbackMessage = "Connect at least 4 dots"
                                    } else if (onVerifyPattern(nodes)) {
                                        vibrateDevice(context, 40L)
                                        isErrorFeedback = false
                                        onAuthSuccess("PATTERN")
                                    } else {
                                        vibrateDevice(context, 130L)
                                        isErrorFeedback = true
                                        onAuthFailure("PATTERN")
                                        val nextAttempt = vaultState.failedAttempts + 1
                                        feedbackMessage = if (nextAttempt % 5 == 0) {
                                            "5 wrong attempts! Locked for 30 seconds."
                                        } else {
                                            "Wrong Pattern (${nextAttempt % 5}/5 attempts)"
                                        }
                                    }
                                }
                            )
                        }

                        ActiveInputTab.MIXED -> {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(18.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = enteredMixed,
                                        onValueChange = { enteredMixed = it },
                                        label = { Text("Enter Mixed Password") },
                                        singleLine = true,
                                        visualTransformation = if (showMixedText) {
                                            VisualTransformation.None
                                        } else {
                                            PasswordVisualTransformation()
                                        },
                                        trailingIcon = {
                                            IconButton(onClick = { showMixedText = !showMixedText }) {
                                                Icon(
                                                    imageVector = if (showMixedText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = "Show password"
                                                )
                                            }
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
                                            .testTag("mixed_unlock_input")
                                    )

                                    Button(
                                        onClick = {
                                            if (onVerifyMixedPassword(enteredMixed)) {
                                                vibrateDevice(context, 40L)
                                                enteredMixed = ""
                                                isErrorFeedback = false
                                                onAuthSuccess("MIXED_PASSWORD")
                                            } else {
                                                vibrateDevice(context, 130L)
                                                enteredMixed = ""
                                                isErrorFeedback = true
                                                onAuthFailure("MIXED_PASSWORD")
                                                val nextAttempt = vaultState.failedAttempts + 1
                                                feedbackMessage = if (nextAttempt % 5 == 0) {
                                                    "5 wrong attempts! Locked for 30 seconds."
                                                } else {
                                                    "Wrong Password (${nextAttempt % 5}/5 attempts)"
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ClassicPrimaryBlue),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("mixed_unlock_submit_button")
                                    ) {
                                        Text("Unlock", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom section: In-Display Ultrasonic Fingerprint Sensor right at the bottom of the display!
            if (!isInCooldown && (vaultState.fingerprintEnabled || vaultState.biometricEnabled)) {
                InDisplayFingerprintSensorZone(
                    label = "Touch & hold in-display fingerprint sensor or tap to use phone fingerprint",
                    onVerifiedSuccess = {
                        vibrateDevice(context, 45L)
                        onAuthSuccess("FINGERPRINT")
                    },
                    onTriggerSystemBiometric = onTriggerHardwareBiometric
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ClassicCooldownCard(
    remainingSeconds: Int,
    remainingMs: Long
) {
    val progress = (remainingMs.toFloat() / 30_000f).coerceIn(0f, 1f)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, ClassicDangerRed.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("cooldown_lockout_card")
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = ClassicDangerRed.copy(alpha = 0.15f),
                    strokeWidth = 8.dp
                )
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = ClassicDangerRed,
                    strokeWidth = 8.dp
                )
                Text(
                    text = "${remainingSeconds}s",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    color = ClassicDangerRed
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Try Again in $remainingSeconds Seconds",
                style = MaterialTheme.typography.titleMedium,
                color = ClassicDangerRed,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "You entered 5 wrong attempts. Password entry is locked for 30 seconds.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PatternLockSection(
    onPatternComplete: (List<Int>) -> Unit,
    hintText: String = "Draw unlock pattern",
    showTestButtons: Boolean = false
) {
    val selectedNodes = remember { mutableStateListOf<Int>() }
    var currentTouchOffset by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    fun getNodeCenter(index: Int, size: IntSize): Offset {
        val row = index / 3
        val col = index % 3
        val cellW = size.width / 3f
        val cellH = size.height / 3f
        return Offset(
            x = col * cellW + cellW / 2f,
            y = row * cellH + cellH / 2f
        )
    }

    fun findHitNode(touch: Offset, size: IntSize): Int? {
        if (size.width == 0 || size.height == 0) return null
        val hitRadius = (size.width / 6f).coerceAtLeast(36f)
        for (i in 0..8) {
            val center = getNodeCenter(i, size)
            if (hypot(touch.x - center.x, touch.y - center.y) <= hitRadius) {
                return i
            }
        }
        return null
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = hintText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            selectedNodes.clear()
                            currentTouchOffset = startOffset
                            findHitNode(startOffset, canvasSize)?.let { node ->
                                selectedNodes.add(node)
                            }
                        },
                        onDrag = { change, _ ->
                            currentTouchOffset = change.position
                            findHitNode(change.position, canvasSize)?.let { node ->
                                if (!selectedNodes.contains(node)) {
                                    selectedNodes.add(node)
                                }
                            }
                        },
                        onDragEnd = {
                            currentTouchOffset = null
                            if (selectedNodes.isNotEmpty()) {
                                val finished = selectedNodes.toList()
                                selectedNodes.clear()
                                onPatternComplete(finished)
                            }
                        },
                        onDragCancel = {
                            currentTouchOffset = null
                            selectedNodes.clear()
                        }
                    )
                }
                .testTag("pattern_lock_canvas")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val intSz = IntSize(size.width.toInt(), size.height.toInt())
                if (selectedNodes.size >= 2) {
                    for (i in 0 until selectedNodes.size - 1) {
                        val start = getNodeCenter(selectedNodes[i], intSz)
                        val end = getNodeCenter(selectedNodes[i + 1], intSz)
                        drawLine(
                            color = ClassicPrimaryBlue,
                            start = start,
                            end = end,
                            strokeWidth = 9f,
                            cap = StrokeCap.Round
                        )
                    }
                }
                if (selectedNodes.isNotEmpty() && currentTouchOffset != null) {
                    val lastCenter = getNodeCenter(selectedNodes.last(), intSz)
                    drawLine(
                        color = ClassicPrimaryBlue.copy(alpha = 0.55f),
                        start = lastCenter,
                        end = currentTouchOffset!!,
                        strokeWidth = 7f,
                        cap = StrokeCap.Round
                    )
                }
                for (i in 0..8) {
                    val center = getNodeCenter(i, intSz)
                    val isSelected = selectedNodes.contains(i)
                    if (isSelected) {
                        drawCircle(
                            color = ClassicPrimaryBlue.copy(alpha = 0.18f),
                            radius = 32f,
                            center = center
                        )
                    }
                    drawCircle(
                        color = if (isSelected) ClassicPrimaryBlue else Color(0xFF94A3B8),
                        radius = if (isSelected) 14f else 10f,
                        center = center
                    )
                }
            }
        }
    }
}

private fun vibrateDevice(context: Context, durationMs: Long) {
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v?.vibrate(durationMs)
            }
        }
    }
}
