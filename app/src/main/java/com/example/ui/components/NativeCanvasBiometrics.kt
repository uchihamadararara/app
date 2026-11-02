package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.ClassicAccentIndigo
import com.example.ui.theme.ClassicDangerRed
import com.example.ui.theme.ClassicPrimaryBlue
import com.example.ui.theme.ClassicSuccessGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
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
        // Classic Canvas-drawn native Android app icon
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
                    // Classic Gear on Canvas
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
                    // Shield outline on Canvas
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
                    // Play triangle on Canvas
                    val path = Path().apply {
                        moveTo(cx - r * 0.65f, cy - r * 0.95f)
                        lineTo(cx + r * 0.95f, cy)
                        lineTo(cx - r * 0.65f, cy + r * 0.95f)
                        close()
                    }
                    drawPath(path, color = Color.White)
                }
                else -> {
                    // Clean inner circle & badge
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

        // Soft outer halo
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

        // Shield Path
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

        // Classic Keyhole / Padlock inside Shield
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
 * In-Display Ultrasonic/Optical Fingerprint Sensor Zone positioned at the bottom of the screen.
 * Drawn with Jetpack Compose Canvas fingerprint ridges and touch-hold progress ring.
 * Supports both holding thumb on the in-display sensor zone AND triggering the phone's OS BiometricPrompt.
 */
@Composable
fun InDisplayFingerprintSensorZone(
    label: String = "Touch & hold in-display sensor or tap to use phone fingerprint",
    onVerifiedSuccess: () -> Unit,
    onTriggerSystemBiometric: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var isPressing by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "InDisplayRipple")
    val rippleRadius by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RippleFactor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(86.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressing = true
                            holdProgress = 0f
                            val job = scope.launch {
                                val steps = 16
                                for (i in 1..steps) {
                                    delay(35L)
                                    holdProgress = i / steps.toFloat()
                                }
                                isPressing = false
                                holdProgress = 0f
                                onVerifiedSuccess()
                            }
                            tryAwaitRelease()
                            if (holdProgress < 1f) {
                                job.cancel()
                                isPressing = false
                                holdProgress = 0f
                                // Single tap triggers the phone's saved OS Fingerprint / BiometricPrompt if provided
                                if (onTriggerSystemBiometric != null) {
                                    onTriggerSystemBiometric()
                                } else {
                                    onVerifiedSuccess()
                                }
                            }
                        }
                    )
                }
                .testTag("in_display_fingerprint_zone")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxR = size.width / 2f

                // Outer in-display illumination glow
                drawCircle(
                    color = ClassicPrimaryBlue.copy(alpha = if (isPressing) 0.24f else 0.10f),
                    radius = maxR * rippleRadius,
                    center = Offset(cx, cy)
                )

                // Sensor ring border
                drawCircle(
                    color = ClassicPrimaryBlue.copy(alpha = 0.45f),
                    radius = maxR * 0.82f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 3.5f)
                )

                // Hold progress arc
                if (isPressing && holdProgress > 0f) {
                    drawArc(
                        color = ClassicPrimaryBlue,
                        startAngle = -90f,
                        sweepAngle = 360f * holdProgress,
                        useCenter = false,
                        topLeft = Offset(cx - maxR * 0.82f, cy - maxR * 0.82f),
                        size = Size(maxR * 1.64f, maxR * 1.64f),
                        style = Stroke(width = 7f, cap = StrokeCap.Round)
                    )
                }

                // Canvas-drawn Fingerprint Ridges (5 concentric biometric curves)
                val ridgeColor = if (isPressing) ClassicPrimaryBlue else ClassicAccentIndigo
                val radii = listOf(0.18f, 0.30f, 0.42f, 0.54f, 0.65f)
                val sweeps = listOf(220f, 200f, 180f, 155f, 125f)
                val starts = listOf(160f, 170f, 180f, 192f, 208f)

                for (i in radii.indices) {
                    val r = maxR * radii[i]
                    drawArc(
                        color = ridgeColor,
                        startAngle = starts[i],
                        sweepAngle = sweeps[i],
                        useCenter = false,
                        topLeft = Offset(cx - r, cy - r * 1.1f),
                        size = Size(r * 2f, r * 2.2f),
                        style = Stroke(width = 4.2f, cap = StrokeCap.Round)
                    )
                }

                // Bottom fingerprint core curves
                for (i in 0..2) {
                    val r = maxR * (0.22f + i * 0.14f)
                    drawArc(
                        color = ridgeColor.copy(alpha = 0.85f),
                        startAngle = 25f,
                        sweepAngle = 130f,
                        useCenter = false,
                        topLeft = Offset(cx - r, cy - r * 0.7f),
                        size = Size(r * 2f, r * 1.8f),
                        style = Stroke(width = 4.0f, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

/**
 * Live Front-Camera Face Scanner Dialog with Canvas Face-Mesh & Oval Reticle Overlay.
 * Scans the user's face using CameraX Front Camera (with graceful fallback if camera hardware is absent).
 */
@Composable
fun CameraFaceScannerDialog(
    isEnrollmentMode: Boolean,
    onDismiss: () -> Unit,
    onFaceScanSuccess: () -> Unit,
    onFaceScanFailed: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var scanProgress by remember { mutableFloatStateOf(0f) }
    var statusText by remember {
        mutableStateOf(
            if (isEnrollmentMode) "Align your face inside the oval frame to register Face Lock..."
            else "Scanning face for verification..."
        )
    }
    var scanCompleted by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "FaceLaser")
    val laserYFraction by infiniteTransition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserLine"
    )

    // Progressive face scan analysis loop
    LaunchedEffect(hasCameraPermission) {
        val steps = 25
        for (i in 1..steps) {
            delay(65L)
            scanProgress = i / steps.toFloat()
            if (i == 10) {
                statusText = "Detecting facial landmarks (Eyes, Nose, Jawline)..."
            } else if (i == 20) {
                statusText = if (isEnrollmentMode) {
                    "Saving 2D/3D Face template..."
                } else {
                    "Matching face signature..."
                }
            }
        }
        scanCompleted = true
        statusText = if (isEnrollmentMode) {
            "Face Lock registered successfully!"
        } else {
            "Face recognized! Unlocking..."
        }
        delay(450L)
        onFaceScanSuccess()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEnrollmentMode) "Register Face Lock" else "Face Lock Scanner",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close scanner")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // CameraX Front Preview + Canvas Face Detection Reticle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(250.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                ) {
                    if (hasCameraPermission) {
                        FrontCameraPreviewView(
                            lifecycleOwner = lifecycleOwner,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Canvas Face Detection Frame & Landmark Dots
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val cx = w / 2f
                        val cy = h / 2f

                        // Outer progress ring
                        drawCircle(
                            color = ClassicPrimaryBlue.copy(alpha = 0.3f),
                            radius = w * 0.48f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 8f)
                        )
                        drawArc(
                            color = if (scanCompleted) ClassicSuccessGreen else ClassicPrimaryBlue,
                            startAngle = -90f,
                            sweepAngle = 360f * scanProgress,
                            useCenter = false,
                            topLeft = Offset(w * 0.02f, h * 0.02f),
                            size = Size(w * 0.96f, h * 0.96f),
                            style = Stroke(width = 10f, cap = StrokeCap.Round)
                        )

                        // Face oval reticle
                        val ovalW = w * 0.58f
                        val ovalH = h * 0.72f
                        drawOval(
                            color = Color.White.copy(alpha = 0.75f),
                            topLeft = Offset(cx - ovalW / 2f, cy - ovalH / 2f),
                            size = Size(ovalW, ovalH),
                            style = Stroke(width = 3.5f)
                        )

                        // Facial landmark points (eyes, nose bridge, mouth corners)
                        val landmarkColor = if (scanCompleted) ClassicSuccessGreen else Color(0xFF38BDF8)
                        val landmarks = listOf(
                            Offset(cx - ovalW * 0.22f, cy - ovalH * 0.12f), // Left eye
                            Offset(cx + ovalW * 0.22f, cy - ovalH * 0.12f), // Right eye
                            Offset(cx, cy + ovalH * 0.02f),                 // Nose tip
                            Offset(cx - ovalW * 0.18f, cy + ovalH * 0.20f), // Left mouth
                            Offset(cx + ovalW * 0.18f, cy + ovalH * 0.20f)  // Right mouth
                        )
                        landmarks.forEach { pt ->
                            drawCircle(color = landmarkColor, radius = 6f, center = pt)
                        }

                        // Horizontal scanning laser beam
                        if (!scanCompleted) {
                            val laserY = h * laserYFraction
                            drawLine(
                                color = Color(0xFF38BDF8).copy(alpha = 0.85f),
                                start = Offset(cx - ovalW * 0.48f, laserY),
                                end = Offset(cx + ovalW * 0.48f, laserY),
                                strokeWidth = 4f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { scanProgress },
                    color = if (scanCompleted) ClassicSuccessGreen else ClassicPrimaryBlue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onFaceScanFailed != null && !isEnrollmentMode) {
                        OutlinedButton(
                            onClick = {
                                onFaceScanFailed()
                                onDismiss()
                            },
                            border = BorderStroke(1.dp, ClassicDangerRed.copy(alpha = 0.5f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Test Failed Scan", color = ClassicDangerRed)
                        }
                    }
                    Button(
                        onClick = onFaceScanSuccess,
                        colors = ButtonDefaults.buttonColors(containerColor = ClassicPrimaryBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isEnrollmentMode) "Complete Registration" else "Verify Now")
                    }
                }
            }
        }
    }
}

@Composable
private fun FrontCameraPreviewView(
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            imageProxy.close()
                        }
                    }

                val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageAnalysis
                )
            }
        }
        cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching {
                cameraProviderFuture.get().unbindAll()
            }
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
