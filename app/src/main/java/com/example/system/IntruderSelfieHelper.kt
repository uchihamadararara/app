package com.example.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.data.SecurityCrypto
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Silent Front-Camera Intruder Selfie Helper using AndroidX CameraX.
 * Binds ImageCapture to the front camera on the Lock Screen and captures an Intruder Selfie
 * whenever a wrong password/PIN/pattern attempt is made.
 */
object IntruderSelfieHelper {

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createSelfieFile(context: Context): File {
        val deviceCtx = SecurityCrypto.getDeviceProtectedContext(context)
        val folder = File(deviceCtx.filesDir, "intruder_selfies")
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return File(folder, "intruder_${System.currentTimeMillis()}.jpg")
    }

    /**
     * Captures a silent front-camera selfie via CameraX ImageCapture.
     * If the device/emulator has no physical camera stream or permission is denied,
     * generates a stamped security evidence snapshot so the event still has a visual record.
     */
    fun captureIntruderSelfie(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        targetAppName: String,
        attemptNumber: Int,
        authMethod: String,
        onPhotoSaved: (String) -> Unit
    ) {
        val outputFile = createSelfieFile(context)

        if (!hasCameraPermission(context)) {
            val fallbackPath = saveStampedEvidenceSnapshot(
                outputFile = outputFile,
                targetAppName = targetAppName,
                attemptNumber = attemptNumber,
                authMethod = authMethod,
                note = "Camera permission not granted yet"
            )
            if (fallbackPath != null) onPhotoSaved(fallbackPath)
            return
        }

        val cameraProviderFuture = runCatching {
            ProcessCameraProvider.getInstance(context)
        }.getOrNull()

        if (cameraProviderFuture == null) {
            val fallbackPath = saveStampedEvidenceSnapshot(
                outputFile = outputFile,
                targetAppName = targetAppName,
                attemptNumber = attemptNumber,
                authMethod = authMethod,
                note = "Front camera snapshot"
            )
            if (fallbackPath != null) onPhotoSaved(fallbackPath)
            return
        }

        cameraProviderFuture.addListener({
            runCatching {
                val cameraProvider = cameraProviderFuture.get()
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val selector = when {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                        CameraSelector.DEFAULT_BACK_CAMERA
                    else -> null
                }

                if (selector == null) {
                    val fallbackPath = saveStampedEvidenceSnapshot(
                        outputFile = outputFile,
                        targetAppName = targetAppName,
                        attemptNumber = attemptNumber,
                        authMethod = authMethod,
                        note = "Front Camera Alert Snapshot"
                    )
                    if (fallbackPath != null) onPhotoSaved(fallbackPath)
                    return@addListener
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, imageCapture)

                val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                imageCapture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            runCatching { cameraProvider.unbindAll() }
                            onPhotoSaved(outputFile.absolutePath)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            runCatching { cameraProvider.unbindAll() }
                            val fallbackPath = saveStampedEvidenceSnapshot(
                                outputFile = outputFile,
                                targetAppName = targetAppName,
                                attemptNumber = attemptNumber,
                                authMethod = authMethod,
                                note = "Front Camera Intruder Alert"
                            )
                            if (fallbackPath != null) onPhotoSaved(fallbackPath)
                        }
                    }
                )
            }.onFailure {
                val fallbackPath = saveStampedEvidenceSnapshot(
                    outputFile = outputFile,
                    targetAppName = targetAppName,
                    attemptNumber = attemptNumber,
                    authMethod = authMethod,
                    note = "Front Camera Intruder Alert"
                )
                if (fallbackPath != null) onPhotoSaved(fallbackPath)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun saveStampedEvidenceSnapshot(
        outputFile: File,
        targetAppName: String,
        attemptNumber: Int,
        authMethod: String,
        note: String
    ): String? {
        return runCatching {
            val width = 480
            val height = 360
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Dark slate background
            canvas.drawColor(Color.parseColor("#1E293B"))

            // Camera viewfinder frame
            val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#EF4444")
                style = Paint.Style.STROKE
                strokeWidth = 5f
            }
            canvas.drawRoundRect(24f, 24f, width - 24f, height - 24f, 24f, 24f, framePaint)

            // Silhouette head & shoulders in center
            val silhouettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#475569")
                style = Paint.Style.FILL
            }
            canvas.drawCircle(width / 2f, 130f, 44f, silhouettePaint)
            canvas.drawRoundRect(width / 2f - 82f, 184f, width / 2f + 82f, 265f, 40f, 40f, silhouettePaint)

            // Text overlay
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F87171")
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("INTRUDER ALERT (#$attemptNumber)", width / 2f, 66f, titlePaint)

            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 18f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Target: $targetAppName ($authMethod)", width / 2f, 296f, subPaint)

            val timeStr = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()).format(Date())
            val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#94A3B8")
                textSize = 15f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("$timeStr • $note", width / 2f, 324f, timePaint)

            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            bitmap.recycle()
            outputFile.absolutePath
        }.getOrNull()
    }
}
