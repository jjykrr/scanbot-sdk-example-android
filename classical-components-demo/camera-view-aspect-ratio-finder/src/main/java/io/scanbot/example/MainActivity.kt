package io.scanbot.example

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.view.View
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
import io.scanbot.sdk.contourdetector.DocumentAutoSnappingController
import io.scanbot.sdk.core.contourdetector.DetectionResult
import io.scanbot.sdk.core.contourdetector.PageAspectRatio
import io.scanbot.sdk.process.CropOperation
import io.scanbot.sdk.process.Operation
import io.scanbot.sdk.ui.camera.AdaptiveFinderOverlayView
import io.scanbot.sdk.ui.camera.FinderAspectRatio
import io.scanbot.sdk.ui.camera.ShutterButton
import java.util.*

class MainActivity : AppCompatActivity(), PictureCallback, ContourDetectorFrameHandler.ResultHandler {
    private lateinit var cameraView: ScanbotCameraView
    private lateinit var resultView: ImageView
    private lateinit var userGuidanceHint: TextView
    private lateinit var shutterButton: ShutterButton

    private lateinit var scanbotSDK: ScanbotSDK

    private var flashEnabled = false
    private var lastUserGuidanceHintTs = 0L
    private val requiredPageAspectRatios = listOf(FinderAspectRatio(21.0, 29.7))

    override fun onCreate(savedInstanceState: Bundle?) {
        supportRequestWindowFeature(WindowCompat.FEATURE_ACTION_BAR_OVERLAY)
        super.onCreate(savedInstanceState)
        scanbotSDK = ScanbotSDK(this)
        askPermission()
        setContentView(R.layout.activity_main)
        supportActionBar!!.hide()
        cameraView = findViewById<View>(R.id.camera) as ScanbotCameraView
        cameraView.setPreviewMode(CameraPreviewMode.FILL_IN)

        // Lock the orientation of the UI (Activity) as well as the orientation of the taken picture to portrait.
        cameraView.lockToPortrait(true)
        cameraView.setCameraOpenCallback(object : CameraOpenCallback {
            override fun onCameraOpened() {
                cameraView.postDelayed({
                    cameraView.setAutoFocusSound(false)

                    // Shutter sound is ON by default. You can disable it:
                    // cameraView.setShutterSound(false);

                    cameraView.continuousFocus()
                    cameraView.useFlash(flashEnabled)
                }, 700)
            }
        })
        resultView = findViewById<View>(R.id.result) as ImageView

        val contourDetectorFrameHandler = ContourDetectorFrameHandler.attach(cameraView, scanbotSDK.contourDetector())
        // contourDetectorFrameHandler.setAcceptedSizeScore(70);

        val finderOverlayView = findViewById<View>(R.id.finder_overlay) as AdaptiveFinderOverlayView
        finderOverlayView.setRequiredAspectRatios(requiredPageAspectRatios)
        val list = ArrayList<PageAspectRatio>()
        for ((width, height) in requiredPageAspectRatios) {
            list.add(PageAspectRatio(width, height))
        }

        contourDetectorFrameHandler.setRequiredAspectRatios(list)
        contourDetectorFrameHandler.addResultHandler(finderOverlayView.contourDetectorFrameHandler)
        contourDetectorFrameHandler.addResultHandler(this)

        val autoSnappingController = DocumentAutoSnappingController.attach(cameraView, contourDetectorFrameHandler)
        // autoSnappingController.setSensitivity(0.4f);
        autoSnappingController.setIgnoreBadAspectRatio(true)
        cameraView.addPictureCallback(this)
        userGuidanceHint = findViewById(R.id.userGuidanceHint)

        shutterButton = findViewById(R.id.shutterButton)
        shutterButton.setOnClickListener { cameraView.takePicture(false) }
        shutterButton.visibility = View.VISIBLE
        shutterButton.post { shutterButton.showAutoButton() }

        findViewById<View>(R.id.flashToggle).setOnClickListener {
            flashEnabled = !flashEnabled
            cameraView.useFlash(flashEnabled)
        }
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
        val autoSnappingEnabled = true
        if (!autoSnappingEnabled) {
            return
        }
        if (System.currentTimeMillis() - lastUserGuidanceHintTs < 400) {
            return
        }
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
            DetectionResult.OK_OFF_CENTER -> {
                userGuidanceHint.text = "Move to the center"
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
            DetectionResult.ERROR_TOO_DARK -> {
                userGuidanceHint.text = "Poor light"
                userGuidanceHint.visibility = View.VISIBLE
            }
            else -> userGuidanceHint.visibility = View.GONE
        }
        lastUserGuidanceHintTs = System.currentTimeMillis()
    }

    override fun onPictureTaken(image: ByteArray, imageOrientation: Int) {
        // Here we get the full (original) image from the camera.

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

        // Run document detection on original image:
        val detector = scanbotSDK.contourDetector()
        val list = ArrayList<PageAspectRatio>()
        for ((width, height) in requiredPageAspectRatios) {
            list.add(PageAspectRatio(width, height))
        }
        detector.setRequiredAspectRatios(list)
        detector.detect(originalBitmap)
        val operations: MutableList<Operation> = ArrayList()
        operations.add(CropOperation(detector.polygonF!!))
        val documentImage = scanbotSDK.imageProcessor().process(originalBitmap, operations, false)
        resultView.post {
            resultView.setImageBitmap(documentImage)
            cameraView.continuousFocus()
            cameraView.startPreview()
        }
    }
}