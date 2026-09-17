package com.scannerportable.bridge;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
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
import com.journeyapps.barcodescanner.Size;
import com.journeyapps.barcodescanner.camera.CameraSettings;

import java.util.Arrays;
import java.util.Collections;

public class ScanActivity extends AppCompatActivity implements Bridge.Listener {

    private CompoundBarcodeView barcodeView;
    private ImageButton flashBtn;
    private View cooldownOverlay;
    private TextView resultView, countdownView, lowLightHint;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ToneGenerator beep;
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private boolean flashOn;
    private boolean cooling;
    private boolean connectMode;
    private long scanWindowStartedNs;
    private long lowLightStartedMs;

    private final SensorEventListener lightListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent event) {
            float lux = event.values[0];
            long now = SystemClock.elapsedRealtime();
            if (lux < 12f && !flashOn) {
                if (lowLightStartedMs == 0) lowLightStartedMs = now;
                if (now - lowLightStartedMs > 800) lowLightHint.setVisibility(View.VISIBLE);
            } else if (lux > 20f || flashOn) {
                lowLightStartedMs = 0;
                lowLightHint.setVisibility(View.GONE);
            }
        }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private final BarcodeCallback callback = result -> {
        String code = result.getText();
        if (code == null || code.isEmpty()) return;

        long acquireMs = scanWindowStartedNs == 0 ? -1
            : (SystemClock.elapsedRealtimeNanos() - scanWindowStartedNs) / 1_000_000L;
        Log.i("VBBScan", "acquire_ms=" + acquireMs + " format=" + result.getBarcodeFormat());

        if (connectMode) {
            handleConnectCode(code);
            return;
        }
        if (!Bridge.connected) {
            showConnectionLost("Waiting for USB / LAN connection…");
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
        lowLightHint    = findViewById(R.id.low_light_hint);
        beep = new ToneGenerator(AudioManager.STREAM_MUSIC, 90);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        lightSensor = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        findViewById(R.id.back).setOnClickListener(v -> finish());
        flashBtn.setOnClickListener(v -> toggleFlash());

        barcodeView.setDecoderFactory(new FastFallbackDecoderFactory(
            connectMode
                ? Arrays.asList(BarcodeFormat.QR_CODE)
                : Arrays.asList(
                    BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX,
                    BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93,
                    BarcodeFormat.EAN_13, BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.ITF
                )));

        CameraSettings cs = barcodeView.getCameraSettings();
        cs.setAutoFocusEnabled(true);
        cs.setContinuousFocusEnabled(true);
        cs.setFocusMode(CameraSettings.FocusMode.CONTINUOUS);
        cs.setBarcodeSceneModeEnabled(true);
        cs.setExposureEnabled(true);
        cs.setMeteringEnabled(true);
        cs.setAutoTorchEnabled(false);

        barcodeView.getBarcodeView().setMarginFraction(0.08);
        barcodeView.post(() -> {
            int width = barcodeView.getWidth(), height = barcodeView.getHeight();
            if (width > 0 && height > 0) {
                boolean portrait = height >= width;
                barcodeView.getBarcodeView().setFramingRectSize(new Size(
                    (int) (width * (portrait ? 0.86 : 0.76)),
                    (int) (height * (portrait ? 0.52 : 0.68))));
            }
        });

        barcodeView.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                applyTapMetering(event.getX(), event.getY());
                view.performClick();
            }
            return true;
        });

        barcodeView.setTorchListener(new CompoundBarcodeView.TorchListener() {
            @Override public void onTorchOn() {
                flashOn = true;
                lowLightHint.setVisibility(View.GONE);
                updateFlashUi();
            }
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
        scanWindowStartedNs = SystemClock.elapsedRealtimeNanos();
        barcodeView.decodeContinuous(callback);
    }

    private void applyTapMetering(float x, float y) {
        int width = barcodeView.getWidth(), height = barcodeView.getHeight();
        if (width <= 0 || height <= 0) return;
        int centerX = Math.round(x / width * 2000f - 1000f);
        int centerY = Math.round(y / height * 2000f - 1000f);
        Rect areaRect = new Rect(clamp(centerX - 180), clamp(centerY - 180),
            clamp(centerX + 180), clamp(centerY + 180));
        barcodeView.getBarcodeView().changeCameraParameters(parameters -> {
            try {
                Camera.Area area = new Camera.Area(areaRect, 1000);
                if (parameters.getMaxNumFocusAreas() > 0) parameters.setFocusAreas(Collections.singletonList(area));
                if (parameters.getMaxNumMeteringAreas() > 0) parameters.setMeteringAreas(Collections.singletonList(area));
            } catch (RuntimeException ignored) {}
            return parameters;
        });
    }

    private static int clamp(int value) { return Math.max(-1000, Math.min(1000, value)); }

    private void tuneExposure() {
        barcodeView.getBarcodeView().changeCameraParameters(parameters -> {
            try {
                float step = parameters.getExposureCompensationStep();
                if (step > 0f) {
                    int halfStop = Math.round(0.5f / step);
                    parameters.setExposureCompensation(Math.max(parameters.getMinExposureCompensation(),
                        Math.min(parameters.getMaxExposureCompensation(), halfStop)));
                }
            } catch (RuntimeException ignored) {}
            return parameters;
        });
    }

    private void showConnectionLost(String reason) {
        cooling = false;
        cooldownOverlay.setVisibility(View.VISIBLE);
        resultView.setTextColor(Color.parseColor("#e06c5a"));
        resultView.setText("Connection lost");
        countdownView.setText(reason);
        stopDecode();
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
        Bridge.addListener(this);
    }

    @Override
    protected void onStop() {
        Bridge.removeListener(this);
        super.onStop();
    }

    @Override
    public void onConnected() {
        if (isFinishing()) return;
        if (connectMode) {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
        } else runOnUiThread(() -> {
            cooldownOverlay.setVisibility(View.GONE);
            cooling = false;
            startDecode();
        });
    }

    @Override
    public void onDisconnected(String reason) {
        if (isFinishing()) return;
        runOnUiThread(() -> {
            if (connectMode) {
                cooldownOverlay.setVisibility(View.GONE);
                showMessage(Color.parseColor("#e06c5a"), "Couldn't connect", reason);
            } else showConnectionLost(reason);
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
        scanWindowStartedNs = SystemClock.elapsedRealtimeNanos();
        barcodeView.resume();
        handler.postDelayed(this::tuneExposure, 500);
        if (sensorManager != null && lightSensor != null)
            sensorManager.registerListener(lightListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        if (sensorManager != null) sensorManager.unregisterListener(lightListener);
        lowLightHint.setVisibility(View.GONE);
        lowLightStartedMs = 0;
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
