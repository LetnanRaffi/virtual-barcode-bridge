# Virtual Barcode Bridge

Turn your phone into a Wi-Fi or USB barcode scanner. Scan a barcode on your phone, it gets typed straight into whatever window is focused on your computer.

Bridge the gap between your POS/keyboard and a zero-infrastructure mobile scanner. LAN mode works on a shared network; USB mode uses an ADB tunnel and needs no LAN reachability, tethering, or inbound firewall rule.

![platform](https://img.shields.io/badge/platform-Windows%20%7C%20Linux%20%7C%20Android-4f9dff)

## What it does

1. The desktop app shows a QR code on a native window (Windows) or a local web page (Linux).
2. Scan that QR with the Android app — it grabs the `ws://` address and connects automatically.
3. Any barcode you scan on the phone is injected as keyboard input into the focused window on the computer (classic barcode-scanner behaviour: the cursor just lands where you need it).

The Android app adds a per-scan cooldown (1–5 s, tunable) so you can swap items without re-scanning the same thing, plus portrait/landscape scanning modes and a scan log. Its scanner uses continuous focus, tap metering, low-light exposure tuning, a wide analysis ROI, and a fast ZXing path with expensive recovery only after normal decoding misses.

## Downloads

All binaries are on the [releases page](https://github.com/LetnanRaffi/virtual-barcode-bridge/releases/latest). Self-contained, no dependencies.

| File | Platform | Size |
|---|---|---|
| `vbb.exe` | Windows 10/11 x64 | ~9 MB |
| `vbb` | Linux x64 | ~9 MB |
| `BarcodeBridge.apk` | Android 7+ | ~4 MB |

### Quick start — Windows
1. Copy `vbb.exe` anywhere. Double-click it.
2. Click **Allow** on the Windows Firewall prompt (lets the phone in on port 8080).
3. A native window opens showing the QR code. Select the Wi-Fi or Ethernet adapter that your phone can reach; the QR updates automatically. Phone scan → connected → scan barcodes.

No admin, no installation, no drivers.

### USB / ADB mode

1. Put Android Platform Tools (`adb.exe`, `AdbWinApi.dll`, and `AdbWinUsbApi.dll`) beside `vbb.exe`, or make `adb` available on `PATH`.
2. Enable **Developer options → USB debugging** on Android and connect the cable.
3. Start the desktop bridge and accept Android's computer authorization prompt.
4. In the Android app choose **Connect via USB / ADB**.

The desktop app detects disconnects, unauthorized devices, and reconnects. It configures `adb reverse tcp:8765 tcp:8765` automatically. The USB WebSocket listener binds only to `127.0.0.1`, so USB mode does not require an inbound Windows Firewall exception and does not change the PC's internet route.

### Quick start — Linux
1. Run `./vbb` (needs `xdotool` installed: `sudo apt install xdotool`).
2. Your browser opens the local web UI with the QR code.
3. Phone scan → connected → scan barcodes.

### Android app
1. Install `BarcodeBridge.apk` on the phone.
2. Open it → **Scan QR to connect**.
3. Scan the QR shown on the computer. Dashboard opens: choose **Scan · Portrait** or **Scan · Landscape**, set the time between scans, and go.

## Build from source

### Desktop (Go 1.24+)

```bash
cd dekstop

# your OS
go build -buildvcs=false -o vbb .

# cross-compile for Windows 64-bit
GOOS=windows GOARCH=amd64 go build -buildvcs=false -o vbb.exe .
```

### Android (Gradle 8.7, JDK 17)

```bash
cd android
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which javac))))
export ANDROID_HOME=$HOME/Android/Sdk
gradle --no-daemon assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

## How it works

```
┌────────────┐   ws://(QR)   ┌────────────────────┐   SendInput    ┌──────────────┐
│ phone app  │ ───────────▶  │ desktop bridge     │ ─────────────▶ │ focused app  │
│ zxing scan │               │ Go net/http + WS   │  (xdotool on  │ POS / any    │
└────────────┘               └────────────────────┘   Linux)      └──────────────┘
```

- WebSocket endpoint: `ws://<ip>:8080/ws`
- Keyboard injection: `SendInput` on Windows, `xdotool` on Linux
- Web UI (embedded, no assets on disk): QR, live scan log, manual test inject

If the computer has both LAN and Wi-Fi, use the network selector above the QR (Windows or web UI). Each choice shows the adapter name and IPv4 address. The default follows the computer's outgoing route; you can select another adapter without restarting. After connecting or disconnecting an adapter, reopen the bridge to refresh the list.

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
