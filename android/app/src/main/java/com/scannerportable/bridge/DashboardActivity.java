package com.scannerportable.bridge;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class DashboardActivity extends AppCompatActivity implements Bridge.Listener {

    private TextView statusView, urlView, logView, gapBtnText;
    private Button gapBtn;
    private ScrollView scroller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        statusView = findViewById(R.id.status);
        urlView    = findViewById(R.id.url);
        gapBtn     = findViewById(R.id.gap);
        logView    = findViewById(R.id.log);
        scroller   = findViewById(R.id.scroller);
        CheckBox enter = findViewById(R.id.autoEnter);

        SharedPreferences prefs = getSharedPreferences("b", 0);
        Bridge.autoEnter  = prefs.getBoolean("enter", true);
        Bridge.gapSeconds = prefs.getInt("gap", 2);
        enter.setChecked(Bridge.autoEnter);

        urlView.setText(Bridge.url);
        enter.setOnCheckedChangeListener((b, v) -> {
            Bridge.autoEnter = v;
            prefs.edit().putBoolean("enter", v).apply();
        });

        gapBtn.setOnClickListener(v -> {
            Bridge.gapSeconds = Bridge.gapSeconds >= 5 ? 1 : Bridge.gapSeconds + 1;
            prefs.edit().putInt("gap", Bridge.gapSeconds).apply();
            gapBtn.setText(Bridge.gapSeconds + "s");
        });

        findViewById(R.id.disconnect).setOnClickListener(v -> {
            Bridge.disconnect();
            finish();
        });

        findViewById(R.id.scanPortrait).setOnClickListener(v ->
            openScanner(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        findViewById(R.id.scanLandscape).setOnClickListener(v ->
            openScanner(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
    }

    private void openScanner(int orientation) {
        android.content.Intent i = new android.content.Intent(this, ScanActivity.class);
        i.putExtra("orientation", orientation);
        startActivity(i);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Bridge.addListener(this);
        refresh();
    }

    @Override
    protected void onStop() {
        Bridge.removeListener(this);
        super.onStop();
    }

    @Override
    public void onConnected() {
        refresh();
    }

    @Override
    public void onDisconnected(String reason) {
        runOnUiThread(() -> {
            statusView.setText("Offline · " + reason);
            statusView.setTextColor(Color.parseColor("#e06c5a"));
        });
    }

    private void refresh() {
        statusView.setText(Bridge.connected ? "Online" : "Offline");
        statusView.setTextColor(Color.parseColor(Bridge.connected ? "#3fca94" : "#e06c5a"));
        urlView.setText(Bridge.url);
        gapBtn.setText(Bridge.gapSeconds + "s");

        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, Bridge.log.size() - 60);
        for (int i = from; i < Bridge.log.size(); i++) {
            String code = Bridge.log.get(i);
            sb.append("✓ ").append(code).append('\n');
        }
        logView.setText(sb.toString());
        scroller.post(() -> scroller.fullScroll(ScrollView.FOCUS_DOWN));
    }
}