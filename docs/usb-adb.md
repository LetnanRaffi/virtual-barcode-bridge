# USB / ADB connection

USB mode keeps the existing WebSocket messages and keyboard injection unchanged. Android connects to `ws://127.0.0.1:8765/ws`; ADB reverse forwards that device-loopback port to the desktop's loopback-only listener.

## Use

1. Install the Android APK and enable USB debugging.
2. Place Android Platform Tools beside the Windows executable, or add `adb` to `PATH`.
3. Connect the phone by USB and launch Barcode Bridge.
4. Authorize the computer on Android when prompted.
5. Select **Connect via USB / ADB** in the Android app.

The desktop status reports ADB missing, no device, unauthorized, disconnected, or tunnel ready. The manager polls for devices and reapplies the reverse tunnel after a reconnect.

USB tethering is not used. No network adapter, routing, administrator privilege, or Windows Firewall change is required. A vendor USB driver may still be necessary on Windows when the device is not visible to ADB.
