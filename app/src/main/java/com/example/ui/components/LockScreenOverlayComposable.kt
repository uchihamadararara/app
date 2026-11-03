package com.example.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.LockAuthMode
import com.example.data.SecurityCrypto
import com.example.data.SecurityVaultState
import com.example.system.BiometricAuthHelper
import com.example.ui.screens.ClassicNumericPad
import com.example.ui.theme.ClassicDangerRed
import com.example.ui.theme.ClassicPrimaryBlue
import com.example.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

enum class ActiveInputTab {
    PIN,
    PATTERN,
    MIXED
}

/**
 * Real Native Android 16 3-Stage Lock Screen Flow (Zero fake/demo buttons on the lock screen):
 * 1. RAISE_TO_WAKE_AOD: Pure black Ambient AOD screen when phone is flat/idle. Shows ONLY
 *    Face icon, Date, large Clock, and the visible In-Display Fingerprint icon at the bottom.
 *    Raising the phone (detected via hardware Accelerometer) or tapping wakes to WALLPAPER_LOCK.
 * 2. WALLPAPER_LOCK: Full-bleed lock screen wallpaper (matching the user's lock screen) with
 *    top Face icon, Date, large 10:35 Clock, and visible In-Display Fingerprint icon at the bottom.
 * 3. SWIPED_UP_AUTH: Triggered by swiping up on the Lock Screen (or directly when opening a locked app):
 *    - Visible fingerprint icon disappears (`hideUntilTouched = true`), appearing with optical animation ONLY when touched.
 *    - Automatic Face Detection starts immediately.
 *    - Displays the user's configured PIN (4/6 digit), Pattern, or Mixed Password input.
 *    - Swiping down returns to WALLPAPER_LOCK just like native Android.
 */
enum class PhoneLockScreenStage {
    RAISE_TO_WAKE_AOD,
    WALLPAPER_LOCK,
    SWIPED_UP_AUTH
}

@Composable
fun LockScreenOverlayContent(
    targetAppName: String,
    targetPackageName: String,
    vaultState: SecurityVaultState,
    isPhoneLockScreen: Boolean = false,
    startInAmbientAod: Boolean = false,
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

    var lockStage by remember(isPhoneLockScreen, startInAmbientAod) {
        mutableStateOf(
            when {
                !isPhoneLockScreen -> PhoneLockScreenStage.SWIPED_UP_AUTH
                startInAmbientAod -> PhoneLockScreenStage.RAISE_TO_WAKE_AOD
                else -> PhoneLockScreenStage.WALLPAPER_LOCK
            }
        )
    }

    // Load custom wallpaper if configured in Security settings, otherwise use R.drawable.default_lock_wallpaper
    val customWallpaperBitmap: ImageBitmap? = remember(context) {
        val customFile = File(SecurityCrypto.getDeviceProtectedContext(context).filesDir, "custom_lock_wallpaper.jpg")
        if (customFile.exists()) {
            runCatching { BitmapFactory.decodeFile(customFile.absolutePath)?.asImageBitmap() }.getOrNull()
        } else {
            null
        }
    }

    // Real Hardware Accelerometer listener for Android 16 "Raise to Wake":
    // Only active while on the Phone Lock Screen before swiping up, using low-power SENSOR_DELAY_NORMAL
    DisposableEffect(isPhoneLockScreen, lockStage) {
        if (!isPhoneLockScreen || lockStage == PhoneLockScreenStage.SWIPED_UP_AUTH) {
            return@DisposableEffect onDispose {}
        }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            private var flatSamples = 0
            override fun onSensorChanged(event: SensorEvent?) {
                val values = event?.values ?: return
                val x = values[0]
                val y = values[1]
                val z = values[2]
                val magnitude = sqrt(x * x + y * y + z * z)
                val isFlatOnTable = abs(x) < 2.0f && abs(y) < 2.0f && z > 8.8f
                val isRaisedInHand = y > 4.2f && magnitude > 8.8f

                if (lockStage == PhoneLockScreenStage.RAISE_TO_WAKE_AOD && isRaisedInHand) {
                    flatSamples = 0
                    lockStage = PhoneLockScreenStage.WALLPAPER_LOCK
                } else if (lockStage == PhoneLockScreenStage.WALLPAPER_LOCK && isFlatOnTable) {
                    flatSamples++
                    if (flatSamples >= 8) {
                        lockStage = PhoneLockScreenStage.RAISE_TO_WAKE_AOD
                    }
                } else {
                    flatSamples = 0
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (accel != null) {
            sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_NORMAL)
        }
        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

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
    var isFaceAutoScanning by remember { mutableStateOf(false) }
    var feedbackMessage by remember(isPhoneLockScreen, targetAppName, lockStage) {
        mutableStateOf(
            when {
                lockStage == PhoneLockScreenStage.SWIPED_UP_AUTH && isPhoneLockScreen ->
                    "Enter ${vaultState.activeAuthMode.label}"
                isPhoneLockScreen ->
                    ""
                else ->
                    "Enter your password to unlock $targetAppName"
            }
        )
    }
    var isErrorFeedback by remember { mutableStateOf(false) }

    // 5-Attempt -> 30-Second Fixed Cooldown Timer & Live Clock
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val remainingCooldownMs = (vaultState.cooldownUntilEpochMs - nowMs).coerceAtLeast(0L)
    val isInCooldown = remainingCooldownMs > 0L
    val remainingSeconds = ((remainingCooldownMs + 999L) / 1000L).toInt()

    val clockTimeStr = remember(nowMs / 15_000L) {
        java.text.SimpleDateFormat("hh:mm", java.util.Locale.getDefault()).format(java.util.Date(nowMs))
    }
    val clockDateShortStr = remember(nowMs / 60_000L) {
        java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.getDefault()).format(java.util.Date(nowMs))
    }

    // Automatic Face Detection when the user swipes up on the Lock Screen (SWIPED_UP_AUTH)
    // or when a locked app opens!
    LaunchedEffect(lockStage, targetPackageName) {
        if (lockStage == PhoneLockScreenStage.SWIPED_UP_AUTH) {
            val currentCooldown = (vaultState.cooldownUntilEpochMs - System.currentTimeMillis())
            if (currentCooldown <= 0L) {
                isFaceAutoScanning = true
                if ((vaultState.faceUnlockEnabled || vaultState.fingerprintEnabled) &&
                    BiometricAuthHelper.canUseHardwareBiometrics(context)
                ) {
                    delay(200L)
                    onTriggerHardwareBiometric()
                }
            }
        } else {
            isFaceAutoScanning = false
        }
    }

    // Battery-friendly timer: only ticks every 250ms during an active 30-second cooldown;
    // otherwise sleeps for 15 seconds at a time just to keep the clock minute updated!
    LaunchedEffect(vaultState.cooldownUntilEpochMs, isPhoneLockScreen) {
        while (true) {
            nowMs = System.currentTimeMillis()
            val activeCooldown = vaultState.cooldownUntilEpochMs > nowMs
            if (vaultState.cooldownUntilEpochMs in 1..nowMs) {
                onCooldownExpired()
                feedbackMessage = "Cooldown finished. Please enter your password."
                isErrorFeedback = false
            }
            delay(if (activeCooldown) 250L else 15_000L)
        }
    }

    // =========================================================================
    // STAGE 1 & STAGE 2 (Real Native Android 16 Phone Lock Screen — NO demo buttons!)
    // =========================================================================
    if (isPhoneLockScreen && lockStage != PhoneLockScreenStage.SWIPED_UP_AUTH) {
        AnimatedContent(
            targetState = lockStage,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "PhoneLockStages"
        ) { stage ->
            when (stage) {
                // -------------------------------------------------------------
                // STAGE 1: Android 16 "Raise to Wake" / Minimal Ambient Screen
                // Pure black screen showing ONLY Face icon, Date, Time, and
                // the visible In-Display Fingerprint icon at the bottom.
                // -------------------------------------------------------------
                PhoneLockScreenStage.RAISE_TO_WAKE_AOD -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    if (dragAmount < -24f) {
                                        vibrateDevice(context, 20L)
                                        lockStage = PhoneLockScreenStage.SWIPED_UP_AUTH
                                    }
                                }
                            }
                            .clickable {
                                // Tapping wakes from Raise-to-Wake AOD to the Wallpaper Lock Screen
                                lockStage = PhoneLockScreenStage.WALLPAPER_LOCK
                            }
                            .testTag("raise_to_wake_aod_screen")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(top = 46.dp)
                        ) {
                            LockScreenFaceStatusIcon(
                                isScanning = false,
                                tint = Color.White.copy(alpha = 0.78f),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = clockDateShortStr,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = clockTimeStr,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 92.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-2).sp
                                ),
                                color = Color.White.copy(alpha = 0.92f)
                            )
                        }

                        // Bottom In-Display Fingerprint Icon positioned low at the bottom sensor spot
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 18.dp)
                        ) {
                            InDisplayFingerprintSensorZone(
                                label = "",
                                hideUntilTouched = false,
                                onDarkBackground = true,
                                onTriggerSystemBiometric = onTriggerHardwareBiometric
                            )
                        }
                    }
                }

                // -------------------------------------------------------------
                // STAGE 2: Real Android 16 Wallpaper Lock Screen (Zero demo buttons!)
                // Exact layout from the user's lock screen:
                // - Full-bleed Wallpaper
                // - Top center: Face icon (`☺`), Date (`Tue 3 Nov`), Large Clock (`10:35`)
                // - Bottom center: Visible In-Display Fingerprint icon aligned low between Phone & Camera icons
                // - Swiping up anywhere transitions to Stage 3 (SWIPED_UP_AUTH)
                // -------------------------------------------------------------
                PhoneLockScreenStage.WALLPAPER_LOCK -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { _, dragAmount ->
                                    if (dragAmount < -22f) {
                                        vibrateDevice(context, 22L)
                                        lockStage = PhoneLockScreenStage.SWIPED_UP_AUTH
                                    }
                                }
                            }
                            .testTag("wallpaper_lock_screen")
                    ) {
                        if (customWallpaperBitmap != null) {
                            Image(
                                bitmap = customWallpaperBitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.default_lock_wallpaper),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Top Center: Face Icon + Date ("Tue 3 Nov") + Large Bold White Clock ("10:35")
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(top = 46.dp)
                        ) {
                            LockScreenFaceStatusIcon(
                                isScanning = false,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = clockDateShortStr,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = Color.White
                            )

                            Text(
                                text = clockTimeStr,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontSize = 98.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-2).sp
                                ),
                                color = Color.White
                            )
                        }

                        // Bottom In-Display Fingerprint Sensor positioned low between the Phone & Camera icons
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 18.dp)
                        ) {
                            InDisplayFingerprintSensorZone(
                                label = "",
                                hideUntilTouched = false,
                                onDarkBackground = true,
                                onTriggerSystemBiometric = onTriggerHardwareBiometric
                            )
                        }
                    }
                }

                else -> {}
            }
        }
        return
    }

    // =========================================================================
    // STAGE 3: AFTER SWIPING UP (OR APP LOCK SCREEN)
    // - Fingerprint icon is HIDDEN by default (`hideUntilTouched = true`),
    //   and only appears + plays optical animation when the user places their finger on it!
    // - Automatic Face Detection (`isFaceAutoScanning = true`) is active at the top!
    // - Shows PIN (4/6 digit), Pattern, or Mixed Password!
    // - Swiping down on the Phone Lock Screen returns to the Wallpaper Lock Screen!
    // =========================================================================
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(isPhoneLockScreen) {
                if (isPhoneLockScreen) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount > 32f) {
                            lockStage = PhoneLockScreenStage.WALLPAPER_LOCK
                        }
                    }
                }
            }
    ) {
        if (isPhoneLockScreen) {
            if (customWallpaperBitmap != null) {
                Image(
                    bitmap = customWallpaperBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.default_lock_wallpaper),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC0F172A),
                                Color(0xEB0F172A),
                                Color(0xF50F172A)
                            )
                        )
                    )
            )
        }

        val isDarkOverlay = isPhoneLockScreen
        val primaryTextColor = if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onBackground
        val secondaryTextColor = if (isDarkOverlay) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 480.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Automatic Face Detection status & App/Screen Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!isPhoneLockScreen) {
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
                                text = "App Lock",
                                style = MaterialTheme.typography.titleSmall,
                                color = secondaryTextColor
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
                                    tint = secondaryTextColor
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Automatic Face Detection Icon (Pulses automatically after swipe up)
                Box(
                    modifier = Modifier
                        .clickable { onTriggerHardwareBiometric() }
                        .padding(vertical = 6.dp)
                        .testTag("quick_face_unlock_button"),
                    contentAlignment = Alignment.Center
                ) {
                    LockScreenFaceStatusIcon(
                        isScanning = !isInCooldown && isFaceAutoScanning,
                        tint = if (isDarkOverlay) Color.White else ClassicPrimaryBlue,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!isPhoneLockScreen) {
                    NativeAppIconAvatar(
                        packageName = targetPackageName,
                        appName = targetAppName,
                        size = 58.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = targetAppName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = primaryTextColor,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = feedbackMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isErrorFeedback || isInCooldown) Color(0xFFF87171) else secondaryTextColor,
                    textAlign = TextAlign.Center
                )

                if (vaultState.failedAttempts % 5 > 0 && !isInCooldown) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Wrong attempts: ${vaultState.failedAttempts % 5} / 5",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFF87171)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Middle Section: Cooldown Timer OR Configured Lock Input (PIN / Pattern / Mixed)
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
                    val availableTabs = buildList {
                        if (vaultState.pinHash.isNotEmpty() || currentInputTab == ActiveInputTab.PIN) add(ActiveInputTab.PIN to "PIN")
                        if (vaultState.patternHash.isNotEmpty() || currentInputTab == ActiveInputTab.PATTERN) add(ActiveInputTab.PATTERN to "Pattern")
                        if (vaultState.mixedPasswordHash.isNotEmpty() || currentInputTab == ActiveInputTab.MIXED) add(ActiveInputTab.MIXED to "Password")
                    }

                    if (availableTabs.size > 1) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 10.dp)
                        ) {
                            availableTabs.forEach { (tab, label) ->
                                FilterChip(
                                    selected = currentInputTab == tab,
                                    onClick = { currentInputTab = tab },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ClassicPrimaryBlue.copy(alpha = 0.22f),
                                        selectedLabelColor = if (isDarkOverlay) Color.White else ClassicPrimaryBlue,
                                        labelColor = secondaryTextColor
                                    )
                                )
                            }
                        }
                    }

                    when (currentInputTab) {
                        ActiveInputTab.PIN -> {
                            val pinLen = vaultState.pinLength.coerceIn(4, 6)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                for (i in 0 until pinLen) {
                                    val filled = i < enteredPin.length
                                    val dotColor = if (isDarkOverlay) Color.White else ClassicPrimaryBlue
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(if (filled) dotColor else Color.Transparent)
                                            .border(
                                                width = 2.dp,
                                                color = if (filled) dotColor else secondaryTextColor,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

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
                                hintColor = secondaryTextColor,
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

            // Bottom Section:
            // After swiping up (or on App Lock), the fingerprint icon is HIDDEN by default (`hideUntilTouched = true`).
            // As soon as the user touches this bottom zone, the fingerprint icon + optical animation immediately appear & play!
            if (!isInCooldown && (vaultState.fingerprintEnabled || vaultState.faceUnlockEnabled || vaultState.biometricEnabled)) {
                InDisplayFingerprintSensorZone(
                    label = "",
                    hideUntilTouched = true,
                    onDarkBackground = isDarkOverlay,
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
    hintColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            color = hintColor,
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
