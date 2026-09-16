# Virtual Barcode Bridge

Turn your phone into a wireless barcode scanner. Scan a barcode on your phone, it gets typed straight into whatever window is focused on your computer.

Bridge the gap between your POS/keyboard and a zero-infrastructure mobile scanner. No drivers, no installs, no servers, no internet required — just one file on the computer and one app on the phone, both on the same Wi-Fi.

![platform](https://img.shields.io/badge/platform-Windows%20%7C%20Linux%20%7C%20Android-4f9dff)

## What it does

1. The desktop app shows a QR code on a native window (Windows) or a local web page (Linux).
2. Scan that QR with the Android app — it grabs the `ws://` address and connects automatically.
3. Any barcode you scan on the phone is injected as keyboard input into the focused window on the computer (classic barcode-scanner behaviour: the cursor just lands where you need it).

The Android app adds a per-scan cooldown (1–5 s, tunable) so you can swap items without re-scanning the same thing, plus portrait/landscape scanning modes and a scan log.

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

### Quick start — Linux
1. Run `./vbb` (needs `xdotool` installed: `sudo apt install xdotool`).
2. Your browser opens the local web UI with the QR code.
3. Phone scan → connected → scan barcodes.

### Android app
1. Install `BarcodeBridge.apk` on the phone.
2. Open it → **Scan QR to connect**.
3. Scan the QR shown on the computer. Dashboard opens: choose **Scan** (portrait) or **Scan** (landscape), pick the scan gap, and go.

## Build from source

### Desktop (Go 1.24+)

```bash
cd dekstop

# your OS
go build -buildvcs=false -o vbb .

# cross-compile for Windows 64-bit
GOOS=windows GOARCH=amd64 go build -buildvcs=false -o vbb.exe .
```

### Android (Gradle)

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