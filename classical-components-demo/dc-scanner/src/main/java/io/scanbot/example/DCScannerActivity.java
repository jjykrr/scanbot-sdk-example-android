package io.scanbot.example;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import io.scanbot.sdk.camera.ScanbotCameraView;
import io.scanbot.sdk.dcscanner.DCScanner;
import io.scanbot.sdk.dcscanner.DCScannerFrameHandler;
import io.scanbot.sdk.util.log.Logger;
import io.scanbot.sdk.util.log.LoggerProvider;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import io.scanbot.dcscanner.model.DisabilityCertificateRecognizerResultInfo;
import io.scanbot.sdk.ScanbotSDK;
import io.scanbot.sdk.camera.FrameHandlerResult;

public class DCScannerActivity extends AppCompatActivity {

    private final Logger logger = LoggerProvider.getLogger();

    private ScanbotCameraView cameraView;

    boolean flashEnabled = false;

    public static Intent newIntent(Context context) {
        return new Intent(context, DCScannerActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        supportRequestWindowFeature(WindowCompat.FEATURE_ACTION_BAR_OVERLAY);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dc_scanner);

        getSupportActionBar().hide();

        cameraView = (ScanbotCameraView) findViewById(R.id.camera);

        cameraView.setCameraOpenCallback(() -> cameraView.postDelayed(() -> {
            cameraView.useFlash(flashEnabled);
            cameraView.continuousFocus();
        }, 700));

        ScanbotSDK scanbotSDK = new ScanbotSDK(this);
        final DCScanner dcScanner = scanbotSDK.dcScanner();
        DCScannerFrameHandler dcScannerFrameHandler = DCScannerFrameHandler.attach(cameraView, dcScanner);

        dcScannerFrameHandler.addResultHandler(frameHandlerResult -> {
            if (frameHandlerResult instanceof FrameHandlerResult.Success) {
                final DisabilityCertificateRecognizerResultInfo resultInfo = ((FrameHandlerResult.Success<DisabilityCertificateRecognizerResultInfo>) frameHandlerResult).getValue();
                if (resultInfo != null && resultInfo.recognitionSuccessful) {
                    long a = System.currentTimeMillis();

                    try {
                        startActivity(DCResultActivity.newIntent(DCScannerActivity.this, resultInfo));
                    } finally {
                        long b = System.currentTimeMillis();
                        logger.d("DCScanner", "Total scanning (sec): " + (b - a) / 1000f);
                    }
                }
            }
            return false;
        });

        findViewById(R.id.flash).setOnClickListener(v -> {
            flashEnabled = !flashEnabled;
            cameraView.useFlash(flashEnabled);
        });

        Toast.makeText(
                this,
                scanbotSDK.isLicenseActive()
                        ? "License is active"
                        : "License is expired",
                Toast.LENGTH_LONG
        ).show();
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
}
