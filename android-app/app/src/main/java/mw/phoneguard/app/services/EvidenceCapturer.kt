package mw.phoneguard.app.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.BatteryManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import mw.phoneguard.app.data.local.EventEntity
import mw.phoneguard.app.data.storage.TokenStore
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class EvidenceCapturer(private val context: Context) {

    private val TAG = "PhoneGuardEvidence"
    private val executor = Executors.newSingleThreadExecutor()

    suspend fun captureAndAttachEvidence(event: EventEntity): EventEntity = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val tokenStore = TokenStore.get(appContext)

        // 1. Capture Location
        val locationData = fetchLocation(appContext)
        val lat = locationData?.first
        val lng = locationData?.second
        val accuracy = locationData?.third

        // 2. Capture Battery
        val battery = fetchBattery(appContext)

        // 3. Capture Photo & ML Kit Face Detection / Cropping
        var captureStatus = "pending_capture"
        var photoSha256: String? = null

        try {
            val photoResult = capturePhotoWithCameraX(appContext)
            if (photoResult != null) {
                val (bitmap, lensFacing) = photoResult
                val processedResult = processImageWithMlKit(appContext, bitmap, lensFacing)
                captureStatus = processedResult.first
                photoSha256 = processedResult.second
            } else {
                captureStatus = "camera_blocked"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture photo evidence: ${e.message}", e)
            captureStatus = "error"
        }

        // 4. Optional Video Evidence
        if (tokenStore.isVideoEvidenceEnabled()) {
            try {
                captureVideoEvidence(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to capture video evidence: ${e.message}", e)
            }
        }

        event.copy(
            lat = lat,
            lng = lng,
            accuracy = accuracy,
            battery = battery,
            captureStatus = captureStatus,
            photoSha256 = photoSha256
        )
    }

    private suspend fun fetchLocation(context: Context): Triple<Double, Double, Double>? = suspendCancellableCoroutine { continuation ->
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            @Suppress("MissingPermission")
            fusedClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    continuation.resume(Triple(location.latitude, location.longitude, location.accuracy.toDouble()))
                } else {
                    continuation.resume(null)
                }
            }.addOnFailureListener {
                continuation.resume(null)
            }
        } catch (e: Exception) {
            continuation.resume(null)
        }
    }

    private fun fetchBattery(context: Context): Int? {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun capturePhotoWithCameraX(context: Context): Pair<Bitmap, Int>? = suspendCancellableCoroutine { continuation ->
        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val frontSelector = CameraSelector.DEFAULT_FRONT_FACING
                    val backSelector = CameraSelector.DEFAULT_BACK_FACING

                    val imageCapture = ImageCapture.Builder().build()
                    val lifecycleOwner = FakeLifecycleOwner()

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, frontSelector, imageCapture)
                    } catch (e: Exception) {
                        Log.w(TAG, "Front camera failed, trying back camera: ${e.message}")
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(lifecycleOwner, backSelector, imageCapture)
                        } catch (e2: Exception) {
                            Log.e(TAG, "Both cameras failed: ${e2.message}")
                            continuation.resume(null)
                            return@addListener
                        }
                    }

                    imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "Image capture failed: ${exception.message}", exception)
                            continuation.resume(null)
                        }

                        override fun onCaptureSuccess(image: ImageProxy) {
                            try {
                                val buffer = image.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                
                                val rotationDegrees = image.imageInfo.rotationDegrees
                                if (rotationDegrees != 0) {
                                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                }

                                image.close()
                                cameraProvider.unbindAll()
                                
                                val lens = CameraSelector.LENS_FACING_FRONT
                                continuation.resume(Pair(bitmap, lens))
                            } catch (e: Exception) {
                                image.close()
                                continuation.resume(null)
                            }
                        }
                    })
                } catch (e: Exception) {
                    continuation.resume(null)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            continuation.resume(null)
        }
    }

    private suspend fun processImageWithMlKit(context: Context, bitmap: Bitmap, lensFacing: Int): Pair<String, String?> = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        val detector = FaceDetection.getClient(options)

        detector.process(image)
            .addOnSuccessListener { faces ->
                try {
                    var finalBitmap = bitmap
                    var captureStatus = "captured"

                    if (faces.isNotEmpty()) {
                        val face = faces[0]
                        val bounds = face.boundingBox
                        val left = maxOf(0, bounds.left - 50)
                        val top = maxOf(0, bounds.top - 50)
                        val right = minOf(bitmap.width, bounds.right + 50)
                        val bottom = minOf(bitmap.height, bounds.bottom + 50)

                        if (right > left && bottom > top) {
                            finalBitmap = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                        }
                        captureStatus = if (lensFacing == CameraSelector.LENS_FACING_FRONT) "captured" else "back_fallback"
                    } else {
                        captureStatus = "no_face"
                    }

                    val file = File(context.filesDir, "captures_${System.currentTimeMillis()}.jpg")
                    val out = FileOutputStream(file)
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    out.flush()
                    out.close()

                    val sha256 = computeSha256(file)
                    continuation.resume(Pair(captureStatus, sha256))
                } catch (e: Exception) {
                    continuation.resume(Pair("captured", null))
                }
            }
            .addOnFailureListener {
                try {
                    val file = File(context.filesDir, "captures_${System.currentTimeMillis()}.jpg")
                    val out = FileOutputStream(file)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    out.flush()
                    out.close()
                    val sha256 = computeSha256(file)
                    continuation.resume(Pair("no_face", sha256))
                } catch (e: Exception) {
                    continuation.resume(Pair("error", null))
                }
            }
    }

    private fun computeSha256(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun captureVideoEvidence(context: Context) {
        Log.i(TAG, "Video evidence capture requested (opt-in enabled)")
    }

    private class FakeLifecycleOwner : androidx.lifecycle.LifecycleOwner {
        private val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this).apply {
            currentState = androidx.lifecycle.Lifecycle.State.RESUMED
        }
        override val lifecycle: androidx.lifecycle.Lifecycle
            get() = lifecycleRegistry
    }
}
