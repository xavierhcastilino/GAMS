package edu.git.gams.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

@Composable
fun CameraXPreview(
    modifier: Modifier = Modifier,
    faceStatus: FaceStatus,
    onFaceStatusChanged: (FaceStatus) -> Unit,
    onImageCaptureReady: (ImageCapture) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                onImageCaptureReady(imageCapture)

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, FaceDetectorHelper(onFaceStatusChanged))
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture,
                        imageAnalyzer
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )

    // Visual face guide overlay circle
    Canvas(modifier = modifier) {
        val strokeColor = when (faceStatus) {
            is FaceStatus.Ready -> Color(0xFF00E676)   // Green when aligned
            is FaceStatus.OffCenter -> Color(0xFFFFD600) // Yellow when off-center
            else -> Color(0xFF9E9E9E)                   // Gray when no face
        }

        val radius = size.minDimension * 0.38f
        drawCircle(
            color = strokeColor,
            radius = radius,
            center = Offset(size.width / 2f, size.height / 2f),
            style = Stroke(width = 6f)
        )
    }
}

/**
 * Captures selfie, downscales to ~640px, and returns clean base64 string (FR-7).
 */
fun captureAndProcessSelfie(
    imageCapture: ImageCapture,
    context: Context,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                try {
                    val buffer = imageProxy.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)

                    val rotation = imageProxy.imageInfo.rotationDegrees
                    val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                    // Rotate and mirror for front camera
                    val matrix = Matrix().apply {
                        postRotate(rotation.toFloat())
                        postScale(-1f, 1f) // Mirror horizontal
                    }
                    val rotatedBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)

                    // Resize to max 640px dimension
                    val maxDim = 640
                    val scale = (maxDim.toFloat() / rotatedBitmap.width.coerceAtLeast(rotatedBitmap.height)).coerceAtMost(1f)
                    val targetWidth = (rotatedBitmap.width * scale).toInt()
                    val targetHeight = (rotatedBitmap.height * scale).toInt()

                    val resizedBitmap = Bitmap.createScaledBitmap(rotatedBitmap, targetWidth, targetHeight, true)

                    val outputStream = ByteArrayOutputStream()
                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
                    val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

                    imageProxy.close()
                    onSuccess(base64)
                } catch (e: Exception) {
                    imageProxy.close()
                    onError("Failed to process photo: ${e.message}")
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onError("Camera capture failed: ${exception.message}")
            }
        }
    )
}
