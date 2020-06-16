package io.scanbot.example

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import io.scanbot.sdk.ScanbotSDK
import io.scanbot.sdk.SdkLicenseError
import io.scanbot.sdk.camera.*
import io.scanbot.sdk.contourdetector.ContourDetectorFrameHandler
import io.scanbot.sdk.contourdetector.ContourDetectorFrameHandler.DetectedFrame
import io.scanbot.sdk.core.contourdetector.DetectionResult
import io.scanbot.sdk.process.CropOperation
import io.scanbot.sdk.process.Operation
import io.scanbot.sdk.ui.PolygonView
import io.scanbot.sdk.ui.camera.ShutterButton
import java.util.*

class MainActivity : AppCompatActivity(), PictureCallback, ContourDetectorFrameHandler.ResultHandler {
    private lateinit var cameraView: ScanbotCameraView
    private lateinit var polygonView: PolygonView
    private lateinit var resultView: ImageView
    private lateinit var userGuidanceHint: TextView
    private lateinit var autoSnappingToggleButton: Button
    private lateinit var shutterButton: ShutterButton

    private lateinit var contourDetectorFrameHandler: ContourDetectorFrameHandler
    private lateinit var autoSnappingController: AutoSnappingController

    private lateinit var scanbotSDK: ScanbotSDK

    private var lastUserGuidanceHintTs = 0L
    private var flashEnabled = false
    private var autoSnappingEnabled = true
    private val ignoreBadAspectRatio = true

    override fun onCreate(savedInstanceState: Bundle?) {
        supportRequestWindowFeature(WindowCompat.FEATURE_ACTION_BAR_OVERLAY)
        super.onCreate(savedInstanceState)
        askPermission()
        setContentView(R.layout.activity_main)
        supportActionBar!!.hide()
        scanbotSDK = ScanbotSDK(this)
        cameraView = findViewById<View>(R.id.camera) as ScanbotCameraView

        // In this example we demonstrate how to lock the orientation of the UI (Activity)
        // as well as the orientation of the taken picture to portrait.
        cameraView.lockToPortrait(true)

        // See https://github.com/doo/scanbot-sdk-example-android/wiki/Using-ScanbotCameraView#preview-mode
        //cameraView.setPreviewMode(io.scanbot.sdk.camera.CameraPreviewMode.FIT_IN);
        cameraView.setCameraOpenCallback(CameraOpenCallback {
            cameraView.postDelayed({
                cameraView.setAutoFocusSound(false)

                // Shutter sound is ON by default. You can disable it:
                // cameraView.setShutterSound(false);

                cameraView.continuousFocus()
                cameraView.useFlash(flashEnabled)
            }, 700)
        })
        resultView = findViewById<View>(R.id.result) as ImageView

        polygonView = findViewById<View>(R.id.polygonView) as PolygonView
        polygonView.setFillColor(POLYGON_FILL_COLOR)
        polygonView.setFillColorOK(POLYGON_FILL_COLOR_OK)

        contourDetectorFrameHandler = ContourDetectorFrameHandler.attach(cameraView, scanbotSDK.contourDetector())

        // Please note: https://github.com/doo/Scanbot-SDK-Examples/wiki/Detecting-and-drawing-contours#contour-detection-parameters
        contourDetectorFrameHandler.setAcceptedAngleScore(60.0)
        contourDetectorFrameHandler.setAcceptedSizeScore(75.0)
        contourDetectorFrameHandler.addResultHandler(polygonView.contourDetectorResultHandler)
        contourDetectorFrameHandler.addResultHandler(this)

        autoSnappingController = AutoSnappingController.attach(cameraView, contourDetectorFrameHandler)
        autoSnappingController.setIgnoreBadAspectRatio(ignoreBadAspectRatio)

        // Please note: https://github.com/doo/Scanbot-SDK-Examples/wiki/Autosnapping#sensitivity
        autoSnappingController.setSensitivity(0.85f)
        cameraView.addPictureCallback(this)
        userGuidanceHint = findViewById(R.id.userGuidanceHint)

        shutterButton = findViewById(R.id.shutterButton)
        shutterButton.setOnClickListener { cameraView.takePicture(false) }
        shutterButton.visibility = View.VISIBLE

        findViewById<View>(R.id.flashToggle).setOnClickListener {
            flashEnabled = !flashEnabled
            cameraView.useFlash(flashEnabled)
        }

        autoSnappingToggleButton = findViewById(R.id.autoSnappingToggle)
        autoSnappingToggleButton.setOnClickListener {
            autoSnappingEnabled = !autoSnappingEnabled
            setAutoSnapEnabled(autoSnappingEnabled)
        }
        autoSnappingToggleButton.post { setAutoSnapEnabled(autoSnappingEnabled) }
    }

    private fun askPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 999)
        }
    }

    override fun onResume() {
        super.onResume()
        cameraView.onResume()
    }

    override fun onPause() {
        super.onPause()
        cameraView.onPause()
    }

    override fun handle(result: FrameHandlerResult<DetectedFrame, SdkLicenseError>): Boolean {
        // Here you are continuously notified about contour detection results.
        // For example, you can show a user guidance text depending on the current detection status.
        userGuidanceHint.post {
            if (result is FrameHandlerResult.Success<*>) {
                showUserGuidance((result as FrameHandlerResult.Success<DetectedFrame>).value.detectionResult)
            }
        }
        return false // typically you need to return false
    }

    private fun showUserGuidance(result: DetectionResult) {
        if (!autoSnappingEnabled) {
            return
        }
        if (System.currentTimeMillis() - lastUserGuidanceHintTs < 400) {
            return
        }

        // Make sure to reset the default polygon fill color (see the ignoreBadAspectRatio case).
        polygonView.setFillColor(POLYGON_FILL_COLOR)
        when (result) {
            DetectionResult.OK -> {
                userGuidanceHint.text = "Don't move"
                userGuidanceHint.visibility = View.VISIBLE
            }
            DetectionResult.OK_BUT_TOO_SMALL -> {
                userGuidanceHint.text = "Move closer"
                userGuidanceHint.visibility = View.VISIBLE
            }
            DetectionResult.OK_BUT_BAD_ANGLES -> {
                userGuidanceHint.text = "Perspective"
                userGuidanceHint.visibility = View.VISIBLE
            }
            DetectionResult.ERROR_NOTHING_DETECTED -> {
                userGuidanceHint.text = "No Document"
                userGuidanceHint.visibility = View.VISIBLE
            }
            DetectionResult.ERROR_TOO_NOISY -> {
                userGuidanceHint.text = "Background too noisy"
                userGuidanceHint.visibility = View.VISIBLE
            }
            DetectionResult.OK_BUT_BAD_ASPECT_RATIO -> {
                if (ignoreBadAspectRatio) {
                    userGuidanceHint.text = "Don't move"
                    // change polygon color to "OK"
                    polygonView.setFillColor(POLYGON_FILL_COLOR_OK)
                } else {
                    userGuidanceHint.text = "Wrong aspect ratio.\n Rotate your device."
                }
                userGuidanceHint.visibility = View.VISIBLE
            }
            DetectionResult.ERROR_TOO_DARK -> {
                userGuidanceHint.text = "Poor light"
                userGuidanceHint.visibility = View.VISIBLE
            }
            else -> userGuidanceHint.visibility = View.GONE
        }
        lastUserGuidanceHintTs = System.currentTimeMillis()
    }

    override fun onPictureTaken(image: ByteArray, imageOrientation: Int) {
        // Here we get the full image from the camera.
        // Please see https://github.com/doo/Scanbot-SDK-Examples/wiki/Handling-camera-picture
        // This is just a demo showing the detected document image as a downscaled(!) preview image.

        // Decode Bitmap from bytes of original image:
        val options = BitmapFactory.Options()
        // Please note: In this simple demo we downscale the original image to 1/8 for the preview!
        options.inSampleSize = 8
        // Typically you will need the full resolution of the original image! So please change the "inSampleSize" value to 1!
        //options.inSampleSize = 1;
        var originalBitmap = BitmapFactory.decodeByteArray(image, 0, image.size, options)

        // Rotate the original image based on the imageOrientation value.
        // Required for some Android devices like Samsung!
        if (imageOrientation > 0) {
            val matrix = Matrix()
            matrix.setRotate(imageOrientation.toFloat(), originalBitmap.width / 2f, originalBitmap.height / 2f)
            originalBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, false)
        }
        val detector = scanbotSDK.contourDetector()
        // Run document detection on original image:
        detector.detect(originalBitmap)
        val operations: MutableList<Operation> = ArrayList()
        operations.add(CropOperation(detector.polygonF))
        val documentImage = scanbotSDK.imageProcessor().process(originalBitmap, operations, false)
        resultView.post { resultView.setImageBitmap(documentImage) }

        // continue scanning
        cameraView.postDelayed({
            cameraView.continuousFocus()
            cameraView.startPreview()
        }, 1000)
    }

    private fun setAutoSnapEnabled(enabled: Boolean) {
        autoSnappingController.isEnabled = enabled
        contourDetectorFrameHandler.isEnabled = enabled
        polygonView.visibility = if (enabled) View.VISIBLE else View.GONE
        autoSnappingToggleButton.text = "Automatic ${if (enabled) "ON" else "OFF"}"
        if (enabled) {
            shutterButton.showAutoButton()
        } else {
            shutterButton.showManualButton()
            userGuidanceHint.visibility = View.GONE
        }
    }

    companion object {
        private val POLYGON_FILL_COLOR = Color.parseColor("#55ff0000")
        private val POLYGON_FILL_COLOR_OK = Color.parseColor("#4400ff00")
    }
}