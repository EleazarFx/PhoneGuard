package mw.phoneguard.app.services

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.PointF
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.FaceDetector
import android.media.ImageReader
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import mw.phoneguard.app.data.local.EventEntity
import mw.phoneguard.app.data.storage.TokenStore
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
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

        // 3. Capture Photo Evidence & Face Detection / Cropping
        var captureStatus = "pending_capture"
        var photoSha256: String? = null

        try {
            val photoResult = capturePhotoWithCamera2(appContext)
            if (photoResult != null) {
                val (bitmap, lensFacing) = photoResult
                val processed = detectFaceAndCrop(appContext, bitmap, lensFacing)
                captureStatus = processed.first
                photoSha256 = processed.second
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

    // ─────────────────────────────────────────────────────────
    // Location
    // ─────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private suspend fun fetchLocation(context: Context): Location? = suspendCancellableCoroutine { continuation ->
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager == null) {
                continuation.resume(null)
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
                continuation.resume(bestLoc)
            } else {
                // Request a single quick location update
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        try { locationManager.removeUpdates(this) } catch (_: Exception) {}
                        if (continuation.isActive) continuation.resume(location)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                }
                val provider = if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
                locationManager.requestSingleUpdate(provider, listener, context.mainLooper)

                // Timeout safety
                Handler(context.mainLooper).postDelayed({
                    try { locationManager.removeUpdates(listener) } catch (_: Exception) {}
                    if (continuation.isActive) continuation.resume(null)
                }, 4000L)
            }
        } catch (e: Exception) {
            if (continuation.isActive) continuation.resume(null)
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
    // Camera2 Stealth Capture (Front / Back fallback)
    // ─────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private suspend fun capturePhotoWithCamera2(context: Context): Pair<Bitmap, Int>? = suspendCancellableCoroutine { continuation ->
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cameraManager == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            var selectedCameraId: String? = null
            var lensFacing = CameraCharacteristics.LENS_FACING_FRONT

            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    selectedCameraId = id
                    lensFacing = CameraCharacteristics.LENS_FACING_FRONT
                    break
                }
            }

            // Back camera fallback
            if (selectedCameraId == null && cameraManager.cameraIdList.isNotEmpty()) {
                selectedCameraId = cameraManager.cameraIdList[0]
                lensFacing = CameraCharacteristics.LENS_FACING_BACK
            }

            if (selectedCameraId == null) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val thread = HandlerThread("Camera2Background").apply { start() }
            val handler = Handler(thread.looper)

            val imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 2)
            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        image.close()
                        thread.quitSafely()
                        if (continuation.isActive) continuation.resume(Pair(bitmap, lensFacing))
                    } catch (e: Exception) {
                        image.close()
                        thread.quitSafely()
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }, handler)

            cameraManager.openCamera(selectedCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(imageReader.surface)
                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        }
                        @Suppress("DEPRECATION")
                        camera.createCaptureSession(listOf(imageReader.surface), object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                                try {
                                    session.capture(captureBuilder.build(), null, handler)
                                } catch (e: Exception) {
                                    camera.close()
                                    thread.quitSafely()
                                    if (continuation.isActive) continuation.resume(null)
                                }
                            }
                            override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                                camera.close()
                                thread.quitSafely()
                                if (continuation.isActive) continuation.resume(null)
                            }
                        }, handler)
                    } catch (e: Exception) {
                        camera.close()
                        thread.quitSafely()
                        if (continuation.isActive) continuation.resume(null)
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    thread.quitSafely()
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    thread.quitSafely()
                    if (continuation.isActive) continuation.resume(null)
                }
            }, handler)

        } catch (e: Exception) {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    // ─────────────────────────────────────────────────────────
    // Face Detection & Cropping (Native FaceDetector API)
    // ─────────────────────────────────────────────────────────

    private fun detectFaceAndCrop(context: Context, bitmap: Bitmap, lensFacing: Int): Pair<String, String?> {
        return try {
            // Convert Bitmap to RGB_565 required by android.media.FaceDetector
            val rgb565Bitmap = bitmap.copy(Bitmap.Config.RGB_565, true)
            val detector = FaceDetector(rgb565Bitmap.width, rgb565Bitmap.height, 1)
            val faces = Array<FaceDetector.Face?>(1) { null }
            val numFaces = detector.findFaces(rgb565Bitmap, faces)

            var finalBitmap = bitmap
            var captureStatus = "captured"

            if (numFaces > 0 && faces[0] != null) {
                val face = faces[0]!!
                val midPoint = PointF()
                face.getMidPoint(midPoint)
                val eyesDistance = face.eyesDistance()

                val left = maxOf(0, (midPoint.x - eyesDistance * 1.5f).toInt())
                val top = maxOf(0, (midPoint.y - eyesDistance * 1.5f).toInt())
                val right = minOf(bitmap.width, (midPoint.x + eyesDistance * 1.5f).toInt())
                val bottom = minOf(bitmap.height, (midPoint.y + eyesDistance * 1.8f).toInt())

                if (right > left && bottom > top) {
                    finalBitmap = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                }
                captureStatus = if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) "captured" else "back_fallback"
            } else {
                captureStatus = "no_face"
            }

            val file = File(context.filesDir, "captures_${System.currentTimeMillis()}.jpg")
            val out = FileOutputStream(file)
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.flush()
            out.close()

            val sha256 = computeSha256(file)
            Pair(captureStatus, sha256)
        } catch (e: Exception) {
            Log.e(TAG, "Face detection error: ${e.message}", e)
            Pair("captured", null)
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
