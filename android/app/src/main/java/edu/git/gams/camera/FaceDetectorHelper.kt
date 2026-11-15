package edu.git.gams.camera

import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

sealed class FaceStatus {
    object NoFace : FaceStatus()
    object MultipleFaces : FaceStatus()
    object OffCenter : FaceStatus()
    data class Ready(val face: Face) : FaceStatus()
}

class FaceDetectorHelper(
    private val onFaceStatusChanged: (FaceStatus) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .build()
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(image)
            .addOnSuccessListener { faces ->
                val status = evaluateFaces(faces, image.width, image.height)
                onFaceStatusChanged(status)
            }
            .addOnFailureListener {
                onFaceStatusChanged(FaceStatus.NoFace)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun evaluateFaces(faces: List<Face>, frameWidth: Int, frameHeight: Int): FaceStatus {
        if (faces.isEmpty()) return FaceStatus.NoFace
        if (faces.size > 1) return FaceStatus.MultipleFaces

        val face = faces[0]
        val box: Rect = face.boundingBox

        // Check if face is centered in the inner 70% of the frame
        val centerX = box.centerX()
        val centerY = box.centerY()

        val minX = frameWidth * 0.15
        val maxX = frameWidth * 0.85
        val minY = frameHeight * 0.15
        val maxY = frameHeight * 0.85

        val isCentered = centerX.toDouble() in minX..maxX && centerY.toDouble() in minY..maxY
        val isBigEnough = box.width() > frameWidth * 0.25

        return if (isCentered && isBigEnough) {
            FaceStatus.Ready(face)
        } else {
            FaceStatus.OffCenter
        }
    }
}
