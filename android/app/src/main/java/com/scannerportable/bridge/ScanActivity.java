package com.scannerportable.bridge;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.CompoundBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import com.journeyapps.barcodescanner.camera.CameraSettings;

import java.util.Arrays;

public class ScanActivity extends AppCompatActivity implements Bridge.Listener {

    private CompoundBarcodeView barcodeView;
    private ImageButton flashBtn;
    private View cooldownOverlay;
    private TextView resultView, countdownView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ToneGenerator beep;
    private boolean flashOn;
    private boolean cooling;
    private boolean connectMode;

    private final BarcodeCallback callback = result -> {
        String code = result.getText();
        if (code == null || code.isEmpty()) return;

        if (connectMode) {
            handleConnectCode(code);
            return;
        }
        if (cooling) return;
        cooling = true;
        Bridge.send(code);
        playBeep(true);
        startCooldown(code, Bridge.gapSeconds);
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        connectMode = "connect".equals(getIntent().getStringExtra("mode"));
        int orientation = connectMode
            ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            : getIntent().getIntExtra("orientation", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setRequestedOrientation(orientation);

        setContentView(R.layout.activity_scan);

        barcodeView     = findViewById(R.id.barcode_view);
        flashBtn        = findViewById(R.id.flash);
        cooldownOverlay = findViewById(R.id.cooldown);
        resultView      = findViewById(R.id.result);
        countdownView   = findViewById(R.id.countdown);
        beep = new ToneGenerator(AudioManager.STREAM_MUSIC, 90);

        findViewById(R.id.back).setOnClickListener(v -> finish());
        flashBtn.setOnClickListener(v -> toggleFlash());

        barcodeView.setDecoderFactory(new DefaultDecoderFactory(
            connectMode
                ? Arrays.asList(BarcodeFormat.QR_CODE)
                : Arrays.asList(
                    BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX,
                    BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93,
                    BarcodeFormat.EAN_13, BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.ITF,
                    BarcodeFormat.PDF_417, BarcodeFormat.AZTEC
                )));

        CameraSettings cs = barcodeView.getCameraSettings();
        cs.setAutoFocusEnabled(true);
        cs.setContinuousFocusEnabled(true);
        cs.setBarcodeSceneModeEnabled(true);
        cs.setExposureEnabled(true);

        barcodeView.setTorchListener(new CompoundBarcodeView.TorchListener() {
            @Override public void onTorchOn() { flashOn = true; updateFlashUi(); }
            @Override public void onTorchOff() { flashOn = false; updateFlashUi(); }
        });

        barcodeView.decodeContinuous(callback);
    }

    private void handleConnectCode(String code) {
        String url = code.trim();
        if (!url.startsWith("ws://")) {
            showMessage(Color.parseColor("#e06c5a"), "Not a bridge link", "Try the QR from the desktop app");
            return;
        }
        Bridge.connect(url);
        countdownView.setText("Connecting to desktop…");
        cooldownOverlay.setVisibility(View.VISIBLE);
        resultView.setTextColor(Color.parseColor("#e0b35a"));
        resultView.setText(url);
        stopDecode();
    }

    private void startCooldown(String code, int seconds) {
        cooldownOverlay.setVisibility(View.VISIBLE);
        resultView.setTextColor(Color.parseColor("#3fca94"));
        resultView.setText("✓ " + code);
        stopDecode();
        for (int i = seconds; i >= 0; i--) {
            final int remaining = i;
            handler.postDelayed(() -> {
                if (remaining == 0) {
                    cooldownOverlay.setVisibility(View.GONE);
                    cooling = false;
                    startDecode();
                } else {
                    countdownView.setText("Next scan in " + remaining + "s");
                }
            }, (long) (seconds - i) * 1000);
        }
    }

    private void showMessage(int color, String title, String sub) {
        cooldownOverlay.setVisibility(View.VISIBLE);
        resultView.setTextColor(color);
        resultView.setText(title);
        countdownView.setText(sub);
        stopDecode();
        handler.postDelayed(() -> {
            cooldownOverlay.setVisibility(View.GONE);
            startDecode();
        }, 1600);
    }

    private void stopDecode() {
        barcodeView.getBarcodeView().stopDecoding();
    }

    private void startDecode() {
        barcodeView.decodeContinuous(callback);
    }

    private void toggleFlash() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) return;
        try {
            if (flashOn) barcodeView.setTorchOff();
            else barcodeView.setTorchOn();
        } catch (Exception e) {
            flashOn = false;
            updateFlashUi();
        }
    }

    private void updateFlashUi() {
        flashBtn.setImageResource(flashOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
        flashBtn.setBackgroundResource(flashOn ? R.drawable.flash_on_bg : R.drawable.flash_off_bg);
    }

    private void playBeep(boolean ok) {
        try {
            beep.startTone(ok ? ToneGenerator.TONE_PROP_ACK : ToneGenerator.TONE_PROP_BEEP, 120);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (connectMode) Bridge.addListener(this);
    }

    @Override
    protected void onStop() {
        if (connectMode) Bridge.removeListener(this);
        super.onStop();
    }

    @Override
    public void onConnected() {
        if (isFinishing()) return;
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }

    @Override
    public void onDisconnected(String reason) {
        if (isFinishing()) return;
        runOnUiThread(() -> {
            cooldownOverlay.setVisibility(View.GONE);
            showMessage(Color.parseColor("#e06c5a"), "Couldn't connect", reason);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            cameraPerm.launch(Manifest.permission.CAMERA);
            return;
        }
        barcodeView.resume();
    }

    @Override
    protected void onPause() {
        barcodeView.pause();
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (beep != null) beep.release();
    }

    private final ActivityResultLauncher<String> cameraPerm =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                barcodeView.resume();
            } else {
                finish();
            }
        });
}