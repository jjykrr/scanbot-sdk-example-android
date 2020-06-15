package io.scanbot.example;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.scanbot.sdk.ScanbotSDK;
import io.scanbot.sdk.SdkLicenseError;
import io.scanbot.sdk.camera.AutoSnappingController;
import io.scanbot.sdk.camera.CameraPreviewMode;
import io.scanbot.sdk.camera.FrameHandlerResult;
import io.scanbot.sdk.camera.PictureCallback;
import io.scanbot.sdk.camera.ScanbotCameraView;
import io.scanbot.sdk.contourdetector.ContourDetectorFrameHandler;
import io.scanbot.sdk.core.contourdetector.ContourDetector;
import io.scanbot.sdk.core.contourdetector.DetectionResult;
import io.scanbot.sdk.core.contourdetector.PageAspectRatio;
import io.scanbot.sdk.process.CropOperation;
import io.scanbot.sdk.process.Operation;
import io.scanbot.sdk.ui.camera.AdaptiveFinderOverlayView;
import io.scanbot.sdk.ui.camera.FinderAspectRatio;
import io.scanbot.sdk.ui.camera.ShutterButton;


public class MainActivity extends AppCompatActivity implements PictureCallback,
        ContourDetectorFrameHandler.ResultHandler {

    private ScanbotCameraView cameraView;
    private ImageView resultView;
    private TextView userGuidanceHint;
    private long lastUserGuidanceHintTs = 0L;
    private ShutterButton shutterButton;
    private ScanbotSDK scanbotSDK;

    private boolean flashEnabled = false;

    private final FinderAspectRatio[] requiredPageAspectRatios = new FinderAspectRatio[]{
            new FinderAspectRatio(21.0, 29.7), // A4 page size
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        supportRequestWindowFeature(WindowCompat.FEATURE_ACTION_BAR_OVERLAY);
        super.onCreate(savedInstanceState);
        scanbotSDK = new ScanbotSDK(this);
        askPermission();

        setContentView(R.layout.activity_main);

        getSupportActionBar().hide();

        cameraView = (ScanbotCameraView) findViewById(R.id.camera);
        cameraView.setPreviewMode(CameraPreviewMode.FILL_IN);

        // Lock the orientation of the UI (Activity) as well as the orientation of the taken picture to portrait.
        cameraView.lockToPortrait(true);

        cameraView.setCameraOpenCallback(() -> cameraView.postDelayed(() -> {
            cameraView.setAutoFocusSound(false);
            // Shutter sound is ON by default. You can disable it:
                /*
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    cameraView.setShutterSound(false);
                }
                */
            cameraView.continuousFocus();
            cameraView.useFlash(flashEnabled);
        }, 700));

        resultView = (ImageView) findViewById(R.id.result);

        ContourDetectorFrameHandler contourDetectorFrameHandler = ContourDetectorFrameHandler.attach(cameraView, scanbotSDK.contourDetector());
        //contourDetectorFrameHandler.setAcceptedSizeScore(70);

        AdaptiveFinderOverlayView finderOverlayView = (AdaptiveFinderOverlayView) findViewById(R.id.finder_overlay);
        finderOverlayView.setRequiredAspectRatios(Arrays.asList(requiredPageAspectRatios));

        ArrayList<PageAspectRatio> list = new ArrayList<>();
        for (FinderAspectRatio ratio : requiredPageAspectRatios) {
            list.add(new PageAspectRatio(ratio.getWidth(),ratio.getHeight()));
        }
        contourDetectorFrameHandler.setRequiredAspectRatios(list);
        contourDetectorFrameHandler.addResultHandler(finderOverlayView.getContourDetectorFrameHandler());
        contourDetectorFrameHandler.addResultHandler(this);

        AutoSnappingController autoSnappingController = AutoSnappingController.attach(cameraView, contourDetectorFrameHandler);
        //autoSnappingController.setSensitivity(0.4f);
        autoSnappingController.setIgnoreBadAspectRatio(true);

        cameraView.addPictureCallback(this);

        userGuidanceHint = findViewById(R.id.userGuidanceHint);

        shutterButton = findViewById(R.id.shutterButton);
        shutterButton.setOnClickListener(v -> cameraView.takePicture(false));
        shutterButton.setVisibility(View.VISIBLE);
        shutterButton.post(() -> shutterButton.showAutoButton());

        findViewById(R.id.flashToggle).setOnClickListener(v -> {
            flashEnabled = !flashEnabled;
            cameraView.useFlash(flashEnabled);
        });
    }

    private void askPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        999);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraView.onPause();
    }

    @Override
    public boolean handle(@NotNull final FrameHandlerResult<? extends ContourDetectorFrameHandler.DetectedFrame, ? extends SdkLicenseError> frameHandlerResult) {
        // Here you are continuously notified about contour detection results.
        // For example, you can show a user guidance text depending on the current detection status.
        userGuidanceHint.post(() -> {
            if (frameHandlerResult instanceof FrameHandlerResult.Success) {
                showUserGuidance(((FrameHandlerResult.Success<ContourDetectorFrameHandler.DetectedFrame>) frameHandlerResult).getValue().detectionResult);
            }
        });

        return false; // typically you need to return false
    }

    private void showUserGuidance(final DetectionResult result) {
        boolean autoSnappingEnabled = true;
        if (!autoSnappingEnabled) {
            return;
        }

        if (System.currentTimeMillis() - lastUserGuidanceHintTs < 400) {
            return;
        }

        switch (result) {
            case OK:
                userGuidanceHint.setText("Don't move");
                userGuidanceHint.setVisibility(View.VISIBLE);
                break;
            case OK_BUT_TOO_SMALL:
                userGuidanceHint.setText("Move closer");
                userGuidanceHint.setVisibility(View.VISIBLE);
                break;
            case OK_BUT_BAD_ANGLES:
                userGuidanceHint.setText("Perspective");
                userGuidanceHint.setVisibility(View.VISIBLE);
                break;
            case OK_OFF_CENTER:
                userGuidanceHint.setText("Move to the center");
                userGuidanceHint.setVisibility(View.VISIBLE);
                break;
            case ERROR_NOTHING_DETECTED:
                userGuidanceHint.setText("No Document");
                userGuidanceHint.setVisibility(View.VISIBLE);
                break;
            case ERROR_TOO_NOISY:
                userGuidanceHint.setText("Background too noisy");
                userGuidanceHint.setVisibility(View.VISIBLE);
                break;
            case ERROR_TOO_DARK:
                userGuidanceHint.setText("Poor light");
                userGuidanceHint.setVisibility(View.VISIBLE);
                break;
            default:
                userGuidanceHint.setVisibility(View.GONE);
                break;
        }

        lastUserGuidanceHintTs = System.currentTimeMillis();
    }

    @Override
    public void onPictureTaken(byte[] image, int imageOrientation) {
        // Here we get the full (original) image from the camera.

        // Decode Bitmap from bytes of original image:
        final BitmapFactory.Options options = new BitmapFactory.Options();
        // Please note: In this simple demo we downscale the original image to 1/8 for the preview!
        options.inSampleSize = 8;
        // Typically you will need the full resolution of the original image! So please change the "inSampleSize" value to 1!
        //options.inSampleSize = 1;
        Bitmap originalBitmap = BitmapFactory.decodeByteArray(image, 0, image.length, options);

        // Rotate the original image based on the imageOrientation value.
        // Required for some Android devices like Samsung!
        if (imageOrientation > 0) {
            final Matrix matrix = new Matrix();
            matrix.setRotate(imageOrientation, originalBitmap.getWidth() / 2f, originalBitmap.getHeight() / 2f);
            originalBitmap = Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.getWidth(), originalBitmap.getHeight(), matrix, false);
        }

        // Run document detection on original image:
        final ContourDetector detector = scanbotSDK.contourDetector();
        ArrayList<PageAspectRatio> list = new ArrayList<>();
        for (FinderAspectRatio ratio : requiredPageAspectRatios) {
            list.add(new PageAspectRatio(ratio.getWidth(),ratio.getHeight()));
        }
        detector.setRequiredAspectRatios(list);
        detector.detect(originalBitmap);
        List<Operation> operations = new ArrayList<>();
        operations.add(new CropOperation(detector.getPolygonF()));
        final Bitmap documentImage = scanbotSDK.imageProcessor().process(originalBitmap, operations, false);

        resultView.post(() -> {
            resultView.setImageBitmap(documentImage);
            cameraView.continuousFocus();
            cameraView.startPreview();
        });
    }

}
