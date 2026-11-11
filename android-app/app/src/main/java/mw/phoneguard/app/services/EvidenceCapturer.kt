package mw.phoneguard.app.services

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class EvidenceCapturer(private val context: Context) {

    private val TAG = "PhoneGuardEvidence"

    suspend fun captureAndAttachEvidence(event: EventEntity): EventEntity = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val tokenStore = TokenStore.get(appContext)

        // 1. Capture Location (lat, lng, accuracy) via LocationManager
        val locationData = fetchLocation(appContext)
        val lat = locationData?.latitude
        val lng = locationData?.longitude
        val accuracy = locationData?.accuracy?.toDouble()

        // 2. Capture Battery Level
        val battery = fetchBattery(appContext)

        // 3. Capture Photo Evidence & ML Kit Face Detection / Cropping using CameraX
        var captureStatus = "pending_capture"
        var photoSha256: String? = null

        try {
            val capturesDir = File(appContext.filesDir, "captures")
            if (!capturesDir.exists()) {
                capturesDir.mkdirs()
            }
            val photoFile = File(capturesDir, "${event.id}.jpg")

            val captureResult = capturePhotoWithCameraX(appContext, photoFile)
            if (captureResult != null) {
                val (bitmap, lensFacing) = captureResult
                val processed = detectFaceAndCropAndSave(appContext, bitmap, lensFacing, photoFile)
                captureStatus = processed.first
                photoSha256 = processed.second
            } else {
                captureStatus = "camera_blocked"
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Camera permission denied or blocked: ${e.message}")
            captureStatus = "camera_blocked"
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Camera deferred due to background/service state: ${e.message}")
            captureStatus = "deferred"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture photo evidence: ${e.message}", e)
            captureStatus = "camera_blocked"
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

    // ─────────────────────────────────────────────────────────
    // Location
    // ─────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private suspend fun fetchLocation(context: Context): Location? = suspendCancellableCoroutine { continuation ->
        val resumed = AtomicBoolean(false)
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager == null) {
                if (resumed.compareAndSet(false, true)) continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val gpsLoc = try { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (e: Exception) { null }
            val netLoc = try { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { null }

            val bestLoc = when {
                gpsLoc != null && netLoc != null -> if (gpsLoc.accuracy <= netLoc.accuracy) gpsLoc else netLoc
                gpsLoc != null -> gpsLoc
                else -> netLoc
            }

            if (bestLoc != null) {
                if (resumed.compareAndSet(false, true)) continuation.resume(bestLoc)
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
                        if (resumed.compareAndSet(false, true)) continuation.resume(location)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                }
                val provider = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
                locationManager.requestSingleUpdate(provider, listener, context.mainLooper)

                Handler(context.mainLooper).postDelayed({
                    try { locationManager.removeUpdates(listener) } catch (_: Exception) {}
                    if (resumed.compareAndSet(false, true)) continuation.resume(null)
                }, 4000L)
            }
        } catch (e: Exception) {
            if (resumed.compareAndSet(false, true)) continuation.resume(null)
        }
    }

    // ─────────────────────────────────────────────────────────
    // Battery
    // ─────────────────────────────────────────────────────────

    private fun fetchBattery(context: Context): Int? {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            null
        }
    }

    // ─────────────────────────────────────────────────────────
    // CameraX Stealth Capture (Front / Back fallback)
    // ─────────────────────────────────────────────────────────

    private class ServiceLifecycleOwner : LifecycleOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        init {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        override val lifecycle: Lifecycle get() = lifecycleRegistry
        fun destroy() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }

    private suspend fun capturePhotoWithCameraX(context: Context, outputFile: File): Pair<Bitmap, Int>? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val resumed = AtomicBoolean(false)
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val lifecycleOwner = ServiceLifecycleOwner()

                        val frontSelector = CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                            .build()

                        val imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()

                        val hasFront = cameraProvider.hasCamera(frontSelector)
                        val cameraSelector = if (hasFront) frontSelector else CameraSelector.DEFAULT_BACK_CAMERA
                        val lensFacing = if (hasFront) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, imageCapture)

                        imageCapture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                    try {
                                        val bitmap = imageProxyToBitmap(imageProxy)
                                        imageProxy.close()
                                        cameraProvider.unbindAll()
                                        lifecycleOwner.destroy()
                                        if (resumed.compareAndSet(false, true)) {
                                            continuation.resume(Pair(bitmap, lensFacing))
                                        }
                                    } catch (e: Exception) {
                                        imageProxy.close()
                                        cameraProvider.unbindAll()
                                        lifecycleOwner.destroy()
                                        if (resumed.compareAndSet(false, true)) {
                                            continuation.resume(null)
                                        }
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    Log.e(TAG, "CameraX capture error: ${exception.message}", exception)
                                    cameraProvider.unbindAll()
                                    lifecycleOwner.destroy()
                                    if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                        tryBackCameraFallback(context, cameraProvider, outputFile, continuation, resumed)
                                    } else {
                                        if (resumed.compareAndSet(false, true)) {
                                            continuation.resume(null)
                                        }
                                    }
                                }
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "CameraX initialization error: ${e.message}", e)
                        if (resumed.compareAndSet(false, true)) {
                            continuation.resume(null)
                        }
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (e: Exception) {
                Log.e(TAG, "CameraX outer error: ${e.message}", e)
                if (resumed.compareAndSet(false, true)) {
                    continuation.resume(null)
                }
            }
        }
    }

    private fun tryBackCameraFallback(
        context: Context,
        cameraProvider: ProcessCameraProvider,
        outputFile: File,
        continuation: kotlin.coroutines.Continuation<Pair<Bitmap, Int>?>,
        resumed: AtomicBoolean
    ) {
        try {
            val lifecycleOwner = ServiceLifecycleOwner()
            val backSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val imageCapture = ImageCapture.Builder().build()

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, backSelector, imageCapture)

            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                        try {
                            val bitmap = imageProxyToBitmap(imageProxy)
                            imageProxy.close()
                            cameraProvider.unbindAll()
                            lifecycleOwner.destroy()
                            if (resumed.compareAndSet(false, true)) {
                                continuation.resume(Pair(bitmap, CameraSelector.LENS_FACING_BACK))
                            }
                        } catch (e: Exception) {
                            imageProxy.close()
                            cameraProvider.unbindAll()
                            lifecycleOwner.destroy()
                            if (resumed.compareAndSet(false, true)) {
                                continuation.resume(null)
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        cameraProvider.unbindAll()
                        lifecycleOwner.destroy()
                        if (resumed.compareAndSet(false, true)) {
                            continuation.resume(null)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            if (resumed.compareAndSet(false, true)) {
                continuation.resume(null)
            }
        }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val rotation = imageProxy.imageInfo.rotationDegrees
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        return bitmap
    }

    // ─────────────────────────────────────────────────────────
    // ML Kit Face Detection & Cropping
    // ─────────────────────────────────────────────────────────

    private suspend fun detectFaceAndCropAndSave(
        context: Context,
        bitmap: Bitmap,
        lensFacing: Int,
        outputFile: File
    ): Pair<String, String?> = suspendCancellableCoroutine { continuation ->
        val resumed = AtomicBoolean(false)
        try {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build()

            val detector = FaceDetection.getClient(options)
            val image = InputImage.fromBitmap(bitmap, 0)

            detector.process(image)
                .addOnSuccessListener { faces ->
                    try {
                        var finalBitmap = bitmap
                        var captureStatus = if (lensFacing == CameraSelector.LENS_FACING_FRONT) "captured" else "back_fallback"

                        if (faces.isNotEmpty()) {
                            val face = faces[0]
                            val bounds = face.boundingBox

                            val left = maxOf(0, bounds.left - (bounds.width() * 0.3f).toInt())
                            val top = maxOf(0, bounds.top - (bounds.height() * 0.3f).toInt())
                            val right = minOf(bitmap.width, bounds.right + (bounds.width() * 0.3f).toInt())
                            val bottom = minOf(bitmap.height, bounds.bottom + (bounds.height() * 0.3f).toInt())

                            if (right > left && bottom > top) {
                                finalBitmap = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                            }
                        } else {
                            captureStatus = "no_face"
                        }

                        val out = FileOutputStream(outputFile)
                        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        out.flush()
                        out.close()

                        val sha256 = computeSha256(outputFile)
                        if (resumed.compareAndSet(false, true)) {
                            continuation.resume(Pair(captureStatus, sha256))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing face crop: ${e.message}", e)
                        saveBitmapFallback(bitmap, outputFile, "captured", continuation, resumed)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "ML Kit face detection failed: ${e.message}")
                    saveBitmapFallback(bitmap, outputFile, "no_face", continuation, resumed)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Face detection init error: ${e.message}", e)
            saveBitmapFallback(bitmap, outputFile, "captured", continuation, resumed)
        }
    }

    private fun saveBitmapFallback(
        bitmap: Bitmap,
        outputFile: File,
        status: String,
        continuation: kotlin.coroutines.Continuation<Pair<String, String?>>,
        resumed: AtomicBoolean
    ) {
        try {
            val out = FileOutputStream(outputFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.flush()
            out.close()
            val sha256 = computeSha256(outputFile)
            if (resumed.compareAndSet(false, true)) {
                continuation.resume(Pair(status, sha256))
            }
        } catch (e: Exception) {
            if (resumed.compareAndSet(false, true)) {
                continuation.resume(Pair(status, null))
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
        Log.i(TAG, "Video evidence capture trigger active (opt-in enabled)")
    }
}
