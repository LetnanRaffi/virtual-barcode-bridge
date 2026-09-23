# ScanBridge

Turn your phone into a barcode scanner. Scan a barcode on your phone, it gets typed straight into whatever window is focused on your computer.

Connect over a USB cable with ADB, or use Wi-Fi/LAN or Android USB tethering. The Windows bridge runs as one portable EXE without an application installer.

![platform](https://img.shields.io/badge/platform-Windows%20%7C%20Linux%20%7C%20Android-4f9dff)

## What it does

1. On Windows, choose USB or network in the native window. Linux uses a local web page with USB control and a network QR code.
2. For USB, plug in the phone and open the Android app; for network, scan the QR code.
3. Any barcode you scan on the phone is injected as keyboard input into the focused window on the computer (classic barcode-scanner behaviour: the cursor just lands where you need it).

The Android app uses CameraX and ML Kit for continuous scanning. It has a configurable duplicate cooldown, connection recovery, scan acknowledgements, and a compact recent-scan card.
For more reliable scans, retail EAN/UPC values pass their check digit and every result must agree across multiple camera frames. **Scan miring** widens the aiming area for diagonal labels; Indonesian is the default Android UI language.

## Downloads

Current local development builds are in `dist/`. Published files on the [releases page](https://github.com/LetnanRaffi/virtual-barcode-bridge/releases/latest) may be older until a new release is uploaded.

| File | Platform | Size |
|---|---|---|
| `vbb.exe` | Windows 10/11 x64 | includes ADB |
| `vbb` | Linux x64 | ~9 MB |
| `ScanBridge-debug.apk` | Android 8+ | ~34 MB (debug) |

### Quick start — Windows
1. Copy `vbb.exe` anywhere. Double-click it.
2. Choose **USB Kabel** or **Jaringan** in the desktop window.
3. USB: enable USB debugging on the phone and approve this computer when prompted. Open the Android app and choose **USB**. Network: choose the reachable adapter, choose **Wi-Fi** in the Android app, scan its QR, then scan barcodes.

The EXE requires no application installation. USB ADB requires Windows to recognize your phone's ADB interface; some phones need an OEM driver installed by an administrator. If ADB is unavailable, try USB tethering under **Jaringan**.

### Quick start — Linux
1. On an **X11** session, run `./vbb` (keyboard injection needs `xdotool`; USB needs `adb`). On Debian/Ubuntu: `sudo apt install xdotool adb`.
2. The browser opens `http://localhost:8080`. For USB, click **Use USB**, connect the phone, enable USB debugging, and approve the computer. Choose **USB** in the Android app. For Wi-Fi, scan the selected adapter's QR.
3. Focus the destination text field, then scan a barcode. The Activity log reports successful scans or injection errors.

Wayland keyboard injection is not supported yet. If ADB sees no phone, run `adb devices -l`; Linux USB access may need distro-specific udev permissions. The Linux binary uses the installed `adb` and does not install packages or change system permissions.

### Android app
1. Install `ScanBridge-debug.apk` on the phone.
2. Choose **USB** for an automatic cable connection, or **Wi-Fi** to scan the desktop QR.
3. Once connected, tap **Start scanning**. The desktop confirms whether each barcode was typed successfully.

### USB Cable / USB Tethering

This alternative USB cable mode uses Android USB tethering, which creates a local IP network rather than ADB.

1. Connect the Android phone to the computer with a USB cable.
2. Enable **USB tethering** in Android Settings (usually under Network/Connections → Hotspot & tethering).
3. Start ScanBridge on the desktop. It continues to listen on port 8080 across its active local adapters.
4. Choose the tethering adapter in the desktop selector. It may be labeled **Likely USB/Tethering** when the operating system exposes a recognizable adapter name; otherwise match the adapter name and IPv4 address shown by your OS.
5. Scan its QR code in the Android app, or enter the displayed `ws://...:8080/ws` address manually.
6. Scan barcodes normally.

If tethering is enabled after the bridge starts, the native Windows window and embedded web UI at `http://localhost:8080` refresh their adapter lists within a few seconds.

### USB Cable / ADB

Choose **USB Kabel** in the Windows window or **Use USB** in the Linux web UI, connect the Android phone, enable USB debugging in Developer Options, and approve the phone's authorization prompt. Open ScanBridge on the phone and choose USB; it connects to `ws://127.0.0.1:8080/ws` through `adb reverse`. No Wi-Fi or Internet is needed. The Windows EXE extracts its bundled ADB files into the user's cache directory; Linux uses `adb` from PATH. If multiple authorized phones are connected, select one in the desktop UI.

## Build from source

### Desktop (Go 1.24+)

```bash
cd dekstop

# your OS
go build -buildvcs=false -o vbb .

# cross-compile for Windows 64-bit
GOOS=windows GOARCH=amd64 go build -buildvcs=false -ldflags="-H windowsgui" -o vbb.exe .
```

### Android (Gradle 8.7, JDK 17)

```bash
cd android
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which javac))))
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew --no-daemon assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

## How it works

```
┌────────────┐   ws://(QR)   ┌────────────────────┐   SendInput    ┌──────────────┐
│ phone app  │ ───────────▶  │ desktop bridge     │ ─────────────▶ │ focused app  │
│ ML Kit scan│               │ Go net/http + WS   │  (xdotool on  │ POS / any    │
└────────────┘               └────────────────────┘   Linux)      └──────────────┘
```

- WebSocket endpoint: `ws://<ip>:8080/ws` (LAN) or forwarded `ws://127.0.0.1:8080/ws` (USB)
- New Android scans use `barcode` messages with a unique ID; desktop replies `barcode_ack`. Legacy `scan` messages remain accepted.
- Keyboard injection: `SendInput` on Windows, `xdotool` on Linux
- Web UI (embedded, no assets on disk): QR, live scan log, manual test inject

If the computer has Wi-Fi, Ethernet, and/or USB tethering, use the network selector above the QR. Each choice shows a probable adapter type, its OS adapter name, and IPv4 address. The default follows the computer's outgoing route. The web UI and Windows native window refresh newly added or removed adapters while the bridge runs.

## Options

```
vbb -port 8080        custom port
vbb -ip 192.168.1.5   advertise a specific LAN IP
vbb -no-browser       don't auto-open the web UI
vbb -no-native        Windows: fall back from native window to web UI
vbb -no-qr            skip the terminal QR
```

## License

[MIT](LICENSE)
