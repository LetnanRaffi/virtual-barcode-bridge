package com.scannerportable.bridge;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ConnectActivity extends AppCompatActivity implements Bridge.Listener {

    private Button scanQrBtn, connectBtn;
    private EditText urlField;
    private TextView hint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        scanQrBtn  = findViewById(R.id.scanQr);
        connectBtn = findViewById(R.id.connect);
        urlField   = findViewById(R.id.url);
        hint       = findViewById(R.id.hint);

        SharedPreferences prefs = getSharedPreferences("b", 0);
        urlField.setText(prefs.getString("url", Bridge.url));

        if (Bridge.connected) openDashboard();

        scanQrBtn.setOnClickListener(v -> {
            Intent i = new Intent(this, ScanActivity.class);
            i.putExtra("mode", "connect");
            startActivity(i);
        });

        connectBtn.setOnClickListener(v -> {
            String url = urlField.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(this, "Enter the WebSocket address", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString("url", url).apply();
            connect(url);
        });
    }

    private void connect(String url) {
        hint.setVisibility(TextView.VISIBLE);
        hint.setTextColor(Color.parseColor("#e0b35a"));
        hint.setText("Connecting to " + url + " …");
        scanQrBtn.setEnabled(false);
        connectBtn.setEnabled(false);
        Bridge.connect(url);
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
        openDashboard();
    }

    @Override
    public void onDisconnected(String reason) {
        if (isFinishing()) return;
        scanQrBtn.setEnabled(true);
        connectBtn.setEnabled(true);
        hint.setTextColor(Color.parseColor("#e06c5a"));
        hint.setText("Offline: " + reason);
    }

    private void openDashboard() {
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }
}