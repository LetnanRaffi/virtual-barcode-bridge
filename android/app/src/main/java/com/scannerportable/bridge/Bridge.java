package com.scannerportable.bridge;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

// Bridge is the app-wide WebSocket holder: one shared connection across screens.
public class Bridge {

    public enum Mode { LAN, USB }

    public interface Listener {
        void onConnected();
        void onDisconnected(String reason);
    }

    private static final OkHttpClient client = new OkHttpClient();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final List<Listener> listeners = new ArrayList<>();
    private static WebSocket ws;
    private static int generation;
    public static boolean connected;
    public static Mode mode = Mode.LAN;

    public static String url = "ws://192.168.1.2:8080/ws";
    public static final String USB_URL = "ws://127.0.0.1:8765/ws";
    public static boolean autoEnter = true;
    public static int gapSeconds = 2;
    public static final List<String> log = new ArrayList<>();

    public static synchronized void addListener(Listener l) { listeners.add(l); }
    public static synchronized void removeListener(Listener l) { listeners.remove(l); }

    private static void fireConnected() {
        for (Listener l : new ArrayList<>(listeners)) l.onConnected();
    }

    private static void fireDisconnected(String reason) {
        for (Listener l : new ArrayList<>(listeners)) l.onDisconnected(reason);
    }

    public static void connect(String u) {
        disconnect();
        mode = USB_URL.equals(u) ? Mode.USB : Mode.LAN;
        url = u;
        open(u, ++generation);
    }

    public static void connectUsb() { connect(USB_URL); }

    private static void open(String u, int attempt) {
        ws = client.newWebSocket(new Request.Builder().url(u).build(), new WebSocketListener() {
            @Override
            public void onOpen(WebSocket s, Response r) {
                if (attempt != generation) { s.close(1000, "stale"); return; }
                connected = true;
                log.clear();
                main.post(() -> fireConnected());
            }

            @Override
            public void onClosed(WebSocket s, int code, String reason) {
                if (attempt != generation) return;
                ws = null;
                connected = false;
                main.post(() -> fireDisconnected(reason == null || reason.isEmpty() ? "Closed" : reason));
                retryUsb(attempt);
            }

            @Override
            public void onFailure(WebSocket s, Throwable t, Response r) {
                if (attempt != generation) return;
                ws = null;
                connected = false;
                String msg = t == null || t.getMessage() == null ? "Connection failed" : t.getMessage();
                main.post(() -> fireDisconnected(msg));
                retryUsb(attempt);
            }
        });
    }

    private static void retryUsb(int attempt) {
        if (mode != Mode.USB || attempt != generation) return;
        main.postDelayed(() -> {
            if (mode == Mode.USB && !connected && attempt == generation) open(USB_URL, attempt);
        }, 1000);
    }

    public static void disconnect() {
        generation++;
        if (ws != null) ws.close(1000, "bye");
        ws = null;
        connected = false;
    }

    public static String connectionLabel() {
        return mode == Mode.USB ? "USB / ADB" : "LAN / Wi-Fi";
    }

    public static void send(String code) {
        if (ws == null) return;
        String json = "{\"type\":\"scan\",\"data\":\"" + esc(code) + "\",\"auto_enter\":" + autoEnter + "}";
        ws.send(json);
        log.add(code);
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
