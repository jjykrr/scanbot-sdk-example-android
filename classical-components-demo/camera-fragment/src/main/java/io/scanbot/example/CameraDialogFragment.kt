package io.scanbot.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import io.scanbot.sdk.ScanbotSDK
import io.scanbot.sdk.camera.AutoSnappingController
import io.scanbot.sdk.camera.CameraOpenCallback
import io.scanbot.sdk.camera.PictureCallback
import io.scanbot.sdk.camera.ScanbotCameraView
import io.scanbot.sdk.contourdetector.ContourDetectorFrameHandler
import io.scanbot.sdk.process.CropOperation
import io.scanbot.sdk.process.Operation
import io.scanbot.sdk.ui.PolygonView
import java.util.*

/**
 * [ScanbotCameraView] integrated in [DialogFragment] example
 */
class CameraDialogFragment : DialogFragment(), PictureCallback {
    private lateinit var cameraView: ScanbotCameraView
    private lateinit var resultView: ImageView
    private lateinit var scanbotSDK: ScanbotSDK

    private var flashEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scanbotSDK = ScanbotSDK(context!!)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val baseView = activity!!.layoutInflater.inflate(R.layout.scanbot_camera_view, container, false)
        cameraView = baseView.findViewById<View>(R.id.camera) as ScanbotCameraView
        cameraView.setCameraOpenCallback(CameraOpenCallback {
            cameraView.postDelayed({
                cameraView.continuousFocus()
                cameraView.useFlash(flashEnabled)
            }, 700)
        })
        resultView = baseView.findViewById<View>(R.id.result) as ImageView
        val contourDetectorFrameHandler = ContourDetectorFrameHandler.attach(cameraView, scanbotSDK.contourDetector())
        val polygonView: PolygonView = baseView.findViewById(R.id.polygonView)
        contourDetectorFrameHandler.addResultHandler(polygonView.contourDetectorResultHandler)
        AutoSnappingController.attach(cameraView, contourDetectorFrameHandler)
        cameraView.addPictureCallback(this)
        baseView.findViewById<View>(R.id.snap).setOnClickListener { v: View? -> cameraView.takePicture(false) }
        baseView.findViewById<View>(R.id.flash).setOnClickListener { v: View? ->
            flashEnabled = !flashEnabled
            cameraView.useFlash(flashEnabled)
        }
        return baseView
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            val width = ViewGroup.LayoutParams.MATCH_PARENT
            val height = ViewGroup.LayoutParams.MATCH_PARENT
            dialog.window.setLayout(width, height)
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

    override fun onPictureTaken(image: ByteArray, imageOrientation: Int) {
        // Here we get the full image from the camera.
        // Implement a suitable async(!) detection and image handling here.
        // This is just a demo showing detected image as downscaled preview image.

        // Decode Bitmap from bytes of original image:
        val options = BitmapFactory.Options()
        options.inSampleSize = 8 // use 1 for original size (if you want no downscale)!
        // in this demo we downscale the image to 1/8 for the preview.
        var originalBitmap = BitmapFactory.decodeByteArray(image, 0, image.size, options)

        // rotate original image if required:
        if (imageOrientation > 0) {
            val matrix = Matrix()
            matrix.setRotate(imageOrientation.toFloat(), originalBitmap.width / 2f, originalBitmap.height / 2f)
            originalBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, false)
        }

        // Run document detection on original image:
        val detector = ScanbotSDK(context!!).contourDetector()
        val detectionResult = detector.detect(originalBitmap)
        val operations: MutableList<Operation> = ArrayList()
        if (detectionResult != null) {
            detector.polygonF?.let { polygon ->
                operations.add(CropOperation(polygon))
                val documentImage = scanbotSDK.imageProcessor().process(originalBitmap, operations, false)
                if (documentImage != null) resultView.post {
                    resultView.setImageBitmap(documentImage)
                    cameraView.continuousFocus()
                    cameraView.startPreview()
                }
            }
        }
    }

    companion object {
        /**
         * Create a new instance of CameraDialogFragment
         */
        fun newInstance(): CameraDialogFragment {
            return CameraDialogFragment()
        }
    }
}