# USB / ADB connection

USB mode keeps the WebSocket messages and keyboard injection unchanged. Android connects to `ws://127.0.0.1:8080/ws`; ADB reverse forwards that device-loopback port to the desktop listener on port 8080 (or the chosen `-port`).

## Use

1. Install the Android APK and enable USB debugging.
2. On Windows, ADB is bundled inside the executable. On Linux, install `adb` from your distribution.
3. Connect the phone by USB and launch ScanBridge.
4. Authorize the computer on Android when prompted.
5. Select **USB** in the Android app. On Windows choose **USB Kabel** in the native window; on Linux click **Use USB** in the web UI.

The desktop status reports ADB missing, no device, unauthorized, disconnected, or tunnel ready. The manager polls for devices and reapplies the reverse tunnel after a reconnect.

USB/ADB mode does not use USB tethering or need a network adapter or Windows Firewall change. A vendor USB driver may still be necessary on Windows when the device is not visible to ADB. USB tethering remains a separate network mode.
