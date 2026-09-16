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

    public interface Listener {
        void onConnected();
        void onDisconnected(String reason);
    }

    private static final OkHttpClient client = new OkHttpClient();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final List<Listener> listeners = new ArrayList<>();
    private static WebSocket ws;
    public static boolean connected;

    public static String url = "ws://192.168.1.2:8080/ws";
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
        url = u;
        ws = client.newWebSocket(new Request.Builder().url(u).build(), new WebSocketListener() {
            @Override
            public void onOpen(WebSocket s, Response r) {
                connected = true;
                log.clear();
                main.post(() -> fireConnected());
            }

            @Override
            public void onClosed(WebSocket s, int code, String reason) {
                ws = null;
                connected = false;
                main.post(() -> fireDisconnected(reason == null || reason.isEmpty() ? "Closed" : reason));
            }

            @Override
            public void onFailure(WebSocket s, Throwable t, Response r) {
                ws = null;
                connected = false;
                String msg = t == null || t.getMessage() == null ? "Connection failed" : t.getMessage();
                main.post(() -> fireDisconnected(msg));
            }
        });
    }

    public static void disconnect() {
        if (ws != null) ws.close(1000, "bye");
        ws = null;
        connected = false;
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