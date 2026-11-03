package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ClassicAccentIndigo
import com.example.ui.theme.ClassicPrimaryBlue
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Loads the real installed Android app icon via PackageManager, or draws a clean
 * classic native Android squircle icon on a Compose Canvas if the package is virtual.
 */
@Composable
fun NativeAppIconAvatar(
    packageName: String,
    appName: String,
    size: Dp = 46.dp
) {
    val context = LocalContext.current
    val realIconBitmap: ImageBitmap? = remember(packageName) {
        runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bmp = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val width = drawable.intrinsicWidth.coerceAtLeast(96)
                val height = drawable.intrinsicHeight.coerceAtLeast(96)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = AndroidCanvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
            bmp.asImageBitmap()
        }.getOrNull()
    }

    if (realIconBitmap != null) {
        Image(
            bitmap = realIconBitmap,
            contentDescription = appName,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
        )
    } else {
        val bgColors = remember(packageName) {
            when {
                packageName.contains("settings") -> listOf(Color(0xFF475569), Color(0xFF1E293B))
                packageName.contains("installer") -> listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))
                packageName.contains("vending") -> listOf(Color(0xFF0EA5E9), Color(0xFF0284C7))
                packageName.contains("photos") -> listOf(Color(0xFFF59E0B), Color(0xFFEC4899))
                packageName.contains("whatsapp") -> listOf(Color(0xFF22C55E), Color(0xFF15803D))
                packageName.contains("gm") -> listOf(Color(0xFFEF4444), Color(0xFFB91C1C))
                packageName.contains("paisa") || packageName.contains("pay") -> listOf(Color(0xFF1A56DB), Color(0xFF1E3A8A))
                packageName.contains("chrome") -> listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                else -> listOf(Color(0xFF4F46E5), Color(0xFF3730A3))
            }
        }

        Canvas(modifier = Modifier.size(size)) {
            drawRoundRect(
                brush = Brush.linearGradient(bgColors),
                cornerRadius = CornerRadius(size.toPx() * 0.26f, size.toPx() * 0.26f)
            )
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val r = this.size.width * 0.24f

            when {
                packageName.contains("settings") -> {
                    drawCircle(
                        color = Color.White,
                        radius = r * 0.85f,
                        center = Offset(cx, cy),
                        style = Stroke(width = r * 0.35f)
                    )
                    for (angleDeg in 0 until 360 step 45) {
                        val rad = Math.toRadians(angleDeg.toDouble())
                        val x1 = cx + (r * 0.85f) * cos(rad).toFloat()
                        val y1 = cy + (r * 0.85f) * sin(rad).toFloat()
                        val x2 = cx + (r * 1.25f) * cos(rad).toFloat()
                        val y2 = cy + (r * 1.25f) * sin(rad).toFloat()
                        drawLine(
                            color = Color.White,
                            start = Offset(x1, y1),
                            end = Offset(x2, y2),
                            strokeWidth = r * 0.3f,
                            cap = StrokeCap.Round
                        )
                    }
                }
                packageName.contains("installer") -> {
                    val path = Path().apply {
                        moveTo(cx, cy - r * 1.15f)
                        lineTo(cx + r, cy - r * 0.65f)
                        lineTo(cx + r, cy + r * 0.2f)
                        quadraticTo(cx + r * 0.8f, cy + r, cx, cy + r * 1.25f)
                        quadraticTo(cx - r * 0.8f, cy + r, cx - r, cy + r * 0.2f)
                        lineTo(cx - r, cy - r * 0.65f)
                        close()
                    }
                    drawPath(path, color = Color.White)
                }
                packageName.contains("vending") -> {
                    val path = Path().apply {
                        moveTo(cx - r * 0.65f, cy - r * 0.95f)
                        lineTo(cx + r * 0.95f, cy)
                        lineTo(cx - r * 0.65f, cy + r * 0.95f)
                        close()
                    }
                    drawPath(path, color = Color.White)
                }
                else -> {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.92f),
                        radius = r * 0.95f,
                        center = Offset(cx, cy),
                        style = Stroke(width = r * 0.28f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = r * 0.36f,
                        center = Offset(cx, cy)
                    )
                }
            }
        }
    }
}

/**
 * Classic Native Android Security Shield drawn entirely with Jetpack Compose Canvas.
 */
@Composable
fun ClassicShieldCanvasGraphic(
    isProtected: Boolean,
    modifier: Modifier = Modifier.size(72.dp)
) {
    val primaryColor = if (isProtected) ClassicPrimaryBlue else Color(0xFF64748B)
    val secondaryColor = if (isProtected) ClassicAccentIndigo else Color(0xFF94A3B8)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        drawCircle(
            color = primaryColor.copy(alpha = 0.10f),
            radius = w * 0.48f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = primaryColor.copy(alpha = 0.16f),
            radius = w * 0.38f,
            center = Offset(cx, cy)
        )

        val shieldW = w * 0.50f
        val shieldH = h * 0.58f
        val top = cy - shieldH * 0.52f
        val bottom = cy + shieldH * 0.52f

        val shieldPath = Path().apply {
            moveTo(cx, top)
            lineTo(cx + shieldW / 2f, top + shieldH * 0.20f)
            lineTo(cx + shieldW / 2f, top + shieldH * 0.58f)
            quadraticTo(cx + shieldW * 0.42f, bottom - shieldH * 0.08f, cx, bottom)
            quadraticTo(cx - shieldW * 0.42f, bottom - shieldH * 0.08f, cx - shieldW / 2f, top + shieldH * 0.58f)
            lineTo(cx - shieldW / 2f, top + shieldH * 0.20f)
            close()
        }

        drawPath(
            path = shieldPath,
            brush = Brush.verticalGradient(listOf(primaryColor, secondaryColor))
        )

        val lockW = shieldW * 0.38f
        val lockH = shieldH * 0.28f
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(cx - lockW / 2f, cy - lockH * 0.1f),
            size = Size(lockW, lockH),
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawArc(
            color = Color.White,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - lockW * 0.32f, cy - lockH * 0.68f),
            size = Size(lockW * 0.64f, lockH * 0.8f),
            style = Stroke(width = w * 0.042f, cap = StrokeCap.Round)
        )
    }
}

/**
 * Android 16 / OxygenOS / ColorOS style minimal Face Recognition status icon at the top of the Lock Screen.
 * Pulses gently when `isScanning` is true.
 */
@Composable
fun LockScreenFaceStatusIcon(
    isScanning: Boolean,
    tint: Color = Color.White,
    modifier: Modifier = Modifier.size(36.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "FaceScanPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = if (isScanning) 1.12f else 0.96f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FacePulseScale"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = (size.minDimension / 2f) * 0.78f * pulseScale

        if (isScanning) {
            drawCircle(
                color = tint.copy(alpha = 0.22f),
                radius = r * 1.28f,
                center = Offset(cx, cy)
            )
        }

        // Outer circular face ring
        drawCircle(
            color = tint,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 5f)
        )

        // Left & right eyes
        drawCircle(
            color = tint,
            radius = 2.6f,
            center = Offset(cx - r * 0.34f, cy - r * 0.22f)
        )
        drawCircle(
            color = tint,
            radius = 2.6f,
            center = Offset(cx + r * 0.34f, cy - r * 0.22f)
        )

        // Nose L-line in center
        drawLine(
            color = tint,
            start = Offset(cx, cy - r * 0.18f),
            end = Offset(cx, cy + r * 0.08f),
            strokeWidth = 3.6f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(cx, cy + r * 0.08f),
            end = Offset(cx + r * 0.14f, cy + r * 0.08f),
            strokeWidth = 3.6f,
            cap = StrokeCap.Round
        )

        // Subtle smile arc
        drawArc(
            color = tint,
            startAngle = 30f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(cx - r * 0.42f, cy - r * 0.10f),
            size = Size(r * 0.84f, r * 0.62f),
            style = Stroke(width = 3.8f, cap = StrokeCap.Round)
        )
    }
}

/**
 * In-Display Fingerprint Sensor Zone:
 * - When `hideUntilTouched = true`: The fingerprint icon is completely hidden by default.
 *   As soon as the user places their finger on the bottom sensor zone (`onPress`), the
 *   fingerprint icon + optical scanning animation immediately appear and play while invoking
 *   the real hardware `BiometricPrompt`.
 * - When `hideUntilTouched = false` (used on Raise-to-Wake AOD and Wallpaper Lock Screen):
 *   The fingerprint icon is visible so the user knows the exact sensor location, and placing
 *   their finger plays the glowing optical scan animation and triggers `BiometricPrompt`.
 */
@Composable
fun InDisplayFingerprintSensorZone(
    label: String = "",
    hideUntilTouched: Boolean = false,
    onDarkBackground: Boolean = false,
    onTriggerSystemBiometric: () -> Unit
) {
    val context = LocalContext.current
    var isFingerPressedOrAnimating by remember { mutableStateOf(false) }

    // Keep the optical scan animation playing briefly when tapped so the user sees the full optical burst
    LaunchedEffect(isFingerPressedOrAnimating) {
        if (isFingerPressedOrAnimating) {
            delay(1600L)
            isFingerPressedOrAnimating = false
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "InDisplayOpticalScan")
    val opticalWave by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "OpticalWave"
    )
    val idleBreath by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "IdleBreath"
    )

    val showFingerprintIcon = !hideUntilTouched || isFingerPressedOrAnimating
    val baseTint = if (onDarkBackground) Color.White else ClassicPrimaryBlue
    val activeGlowTint = if (onDarkBackground) Color(0xFF38BDF8) else ClassicPrimaryBlue

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp)
    ) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = if (onDarkBackground) {
                    Color.White.copy(alpha = 0.78f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Touch target area at the bottom in-display sensor location
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(74.dp)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            // Immediately show fingerprint icon + play optical animation + trigger hardware BiometricPrompt!
                            isFingerPressedOrAnimating = true
                            vibrateShort(context, 24L)
                            onTriggerSystemBiometric()
                            tryAwaitRelease()
                        }
                    )
                }
                .testTag("in_display_fingerprint_zone")
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = showFingerprintIcon,
                enter = fadeIn(tween(150)) + scaleIn(tween(180), initialScale = 0.72f),
                exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.72f)
            ) {
                Canvas(modifier = Modifier.size(66.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = size.width / 2f

                    if (isFingerPressedOrAnimating) {
                        // Active Optical In-Display Fingerprint Burst Animation
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    activeGlowTint.copy(alpha = 0.55f * (1f - opticalWave * 0.5f)),
                                    activeGlowTint.copy(alpha = 0.20f),
                                    Color.Transparent
                                ),
                                center = Offset(cx, cy),
                                radius = maxR * (0.9f + opticalWave * 0.35f)
                            ),
                            radius = maxR * (0.9f + opticalWave * 0.35f),
                            center = Offset(cx, cy)
                        )

                        // Expanding optical scan ring
                        drawCircle(
                            color = activeGlowTint.copy(alpha = (1f - opticalWave).coerceIn(0f, 1f)),
                            radius = maxR * (0.55f + opticalWave * 0.45f),
                            center = Offset(cx, cy),
                            style = Stroke(width = 4.5f)
                        )
                    } else {
                        // Subtle ambient halo when visible on Raise-to-Wake or Wallpaper Lock Screen
                        drawCircle(
                            color = baseTint.copy(alpha = 0.14f),
                            radius = maxR * 0.88f * idleBreath,
                            center = Offset(cx, cy)
                        )
                    }

                    // Outer sensor ring
                    drawCircle(
                        color = if (isFingerPressedOrAnimating) activeGlowTint else baseTint.copy(alpha = 0.75f),
                        radius = maxR * 0.78f,
                        center = Offset(cx, cy),
                        style = Stroke(width = if (isFingerPressedOrAnimating) 4.2f else 3.2f)
                    )

                    // Fingerprint ridges
                    val ridgeColor = if (isFingerPressedOrAnimating) {
                        if (onDarkBackground) Color.White else ClassicPrimaryBlue
                    } else {
                        baseTint.copy(alpha = 0.92f)
                    }
                    val radii = listOf(0.18f, 0.30f, 0.42f, 0.54f, 0.65f)
                    val sweeps = listOf(220f, 200f, 180f, 155f, 125f)
                    val starts = listOf(160f, 170f, 180f, 192f, 208f)

                    for (i in radii.indices) {
                        val r = maxR * radii[i]
                        val animatedSweep = if (isFingerPressedOrAnimating) {
                            sweeps[i] * (0.65f + 0.35f * sin((opticalWave * 2 * Math.PI + i).toFloat()).coerceIn(0f, 1f))
                        } else {
                            sweeps[i]
                        }
                        drawArc(
                            color = ridgeColor,
                            startAngle = starts[i],
                            sweepAngle = animatedSweep,
                            useCenter = false,
                            topLeft = Offset(cx - r, cy - r * 1.1f),
                            size = Size(r * 2f, r * 2.2f),
                            style = Stroke(
                                width = if (isFingerPressedOrAnimating) 4.6f else 3.8f,
                                cap = StrokeCap.Round
                            )
                        )
                    }

                    for (i in 0..2) {
                        val r = maxR * (0.22f + i * 0.14f)
                        drawArc(
                            color = ridgeColor.copy(alpha = 0.88f),
                            startAngle = 25f,
                            sweepAngle = 130f,
                            useCenter = false,
                            topLeft = Offset(cx - r, cy - r * 0.7f),
                            size = Size(r * 2f, r * 1.8f),
                            style = Stroke(
                                width = if (isFingerPressedOrAnimating) 4.4f else 3.6f,
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }
            }
        }
    }
}

private fun vibrateShort(context: Context, durationMs: Long) {
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
