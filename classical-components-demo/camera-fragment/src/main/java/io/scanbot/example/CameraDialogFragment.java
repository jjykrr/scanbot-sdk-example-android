package io.scanbot.example;

import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.fragment.app.DialogFragment;

import org.jetbrains.annotations.NotNull;

import io.scanbot.sdk.camera.AutoSnappingController;
import io.scanbot.sdk.camera.PictureCallback;
import io.scanbot.sdk.camera.ScanbotCameraView;
import io.scanbot.sdk.contourdetector.ContourDetectorFrameHandler;
import io.scanbot.sdk.core.contourdetector.ContourDetector;
import io.scanbot.sdk.core.contourdetector.DetectionResult;

import java.util.ArrayList;
import java.util.List;

import io.scanbot.sdk.ScanbotSDK;
import io.scanbot.sdk.process.CropOperation;
import io.scanbot.sdk.process.Operation;
import io.scanbot.sdk.ui.PolygonView;

/**
 * {@link ScanbotCameraView} integrated in {@link DialogFragment} example
 */
public class CameraDialogFragment extends DialogFragment implements PictureCallback {
    private ScanbotCameraView cameraView;
    private ImageView resultView;
    private ScanbotSDK scanbotSDK;
    boolean flashEnabled = false;

    /**
     * Create a new instance of CameraDialogFragment
     */
    static CameraDialogFragment newInstance() {
        return new CameraDialogFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        scanbotSDK = new ScanbotSDK(getContext());
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View baseView = getActivity().getLayoutInflater().inflate(R.layout.scanbot_camera_view, container, false);

        cameraView = (ScanbotCameraView) baseView.findViewById(R.id.camera);
        cameraView.setCameraOpenCallback(() -> cameraView.postDelayed(() -> {
            cameraView.continuousFocus();
            cameraView.useFlash(flashEnabled);
        }, 700));

        resultView = (ImageView) baseView.findViewById(R.id.result);

        ContourDetectorFrameHandler contourDetectorFrameHandler = ContourDetectorFrameHandler.attach(cameraView, scanbotSDK.contourDetector());

        PolygonView polygonView = baseView.findViewById(R.id.polygonView);
        contourDetectorFrameHandler.addResultHandler(polygonView.contourDetectorResultHandler);

        AutoSnappingController.attach(cameraView, contourDetectorFrameHandler);

        cameraView.addPictureCallback(this);

        baseView.findViewById(R.id.snap).setOnClickListener(v -> cameraView.takePicture(false));

        baseView.findViewById(R.id.flash).setOnClickListener(v -> {
            flashEnabled = !flashEnabled;
            cameraView.useFlash(flashEnabled);
        });

        return baseView;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = ViewGroup.LayoutParams.MATCH_PARENT;
            dialog.getWindow().setLayout(width, height);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        cameraView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        cameraView.onPause();
    }

    @Override
    public void onPictureTaken(final byte[] image, int imageOrientation) {
        // Here we get the full image from the camera.
        // Implement a suitable async(!) detection and image handling here.
        // This is just a demo showing detected image as downscaled preview image.

        // Decode Bitmap from bytes of original image:
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 8; // use 1 for original size (if you want no downscale)!
        // in this demo we downscale the image to 1/8 for the preview.
        Bitmap originalBitmap = BitmapFactory.decodeByteArray(image, 0, image.length, options);

        // rotate original image if required:
        if (imageOrientation > 0) {
            final Matrix matrix = new Matrix();
            matrix.setRotate(imageOrientation, originalBitmap.getWidth() / 2f, originalBitmap.getHeight() / 2f);
            originalBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.getWidth(), originalBitmap.getHeight(), matrix, false);
        }

        // Run document detection on original image:
        final ContourDetector detector = new ScanbotSDK(getContext()).contourDetector();
        DetectionResult detectionResult = detector.detect(originalBitmap);
        List<Operation> operations = new ArrayList<>();
        operations.add(new CropOperation(detector.getPolygonF()));
        if (detectionResult != null) {
            final Bitmap documentImage = scanbotSDK.imageProcessor().process(originalBitmap, operations, false);

            if (documentImage != null)
                resultView.post(() -> {
                    resultView.setImageBitmap(documentImage);
                    cameraView.continuousFocus();
                    cameraView.startPreview();
                });
        }

    }
}

