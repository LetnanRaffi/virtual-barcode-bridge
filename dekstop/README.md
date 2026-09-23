# ScanBridge desktop

Portable desktop bridge: receive barcode via WebSocket from phone, inject as keyboard input into any focused textbox.

## Requirements

- **Linux:** `xdotool` for keyboard injection (X11; Wayland not supported). `adb` for USB cable mode.
- **Windows:** Native Win32 API. USB ADB needs an authorized Android phone and a working ADB USB driver.

## Build

```bash
# Linux
go build -buildvcs=false -o vbb .

# Windows
GOOS=windows GOARCH=amd64 go build -buildvcs=false -ldflags="-H windowsgui" -o vbb.exe .
```

## Usage

```bash
# Linux
./vbb

# Windows
vbb.exe
```

On start: discovers active Wi-Fi, Ethernet, and other IPv4 adapters, starts one server on port 8080 for all of them, and prints QR code. The Windows native window lets users choose USB ADB or a network adapter. The Linux web UI at `http://localhost:8080` offers **Use USB** and the adapter QR selector. USB tethering is supported because Android exposes it as a normal IP interface.

## USB ADB (Linux)

Install `adb`, start `./vbb`, click **Use USB** in the local web UI, connect the phone, enable USB debugging, and approve the computer on the phone. Choose USB in the Android app. The desktop creates `adb reverse tcp:8080 tcp:8080` (or forwards to the chosen `-port`) and removes its mapping on exit. It does not kill a shared ADB server. If no phone appears, check `adb devices -l` and your distro's udev permissions. No Wi-Fi or firewall change is required.

## USB ADB (Windows)

Choose USB in the native window, connect an Android phone with USB debugging enabled, and approve the authorization prompt. The bridge configures `adb reverse tcp:8080 tcp:8080` and the Android app connects to its own localhost. The EXE contains ADB 37.0.1 and extracts it to the user's cache. The bundled files came from Android SDK Platform-Tools for Windows; see `usbtools/NOTICE.txt`. If the PC needs an OEM ADB driver, an administrator may have to install it.

## USB Cable / USB Tethering

This is Android **USB tethering**, not ADB. Connect the phone by USB, enable USB tethering in Android settings, then choose its adapter in the QR selector and scan the generated QR. Recognizable adapter names are labeled `Likely USB/Tethering`; this is an indication only because standard Go networking APIs do not expose a reliable USB flag on every OS. The browser and Windows native UI refresh interface choices while running.

## Web UI (Browser)

Opens automatically at `http://localhost:8080`:

- **QR code** + endpoint URL for your phone to scan
- **Activity log** — live feed of connect/disconnect/scan events
- **Test injection** — type text + press "Send keystrokes" to verify it lands in the focused window

## Options

| Flag | Default | Description |
|------|---------|-------------|
| `-port` | 8080 | Listen port |
| `-ip` | auto-detect | Override LAN IP shown in QR |
| `-no-qr` | false | Skip terminal QR rendering |
| `-no-browser` | false | Don't auto-open the browser |

## WebSocket Protocol

Connect a LAN WebSocket client to the complete QR URL, `ws://<YOUR_LAN_IP>:8080/ws?pair=<session-code>`. The code is random for each desktop run; scan the new QR after a restart. The ADB-reversed localhost URL does not need the code. Keep Wi-Fi mode on a trusted LAN because `ws://` does not encrypt traffic.

Send JSON:
```json
{
  "type": "barcode",
  "id": "unique-scan-id",
  "value": "BARCODE_VALUE",
  "auto_enter": true
}
```

The bridge types `BARCODE_VALUE` into the focused window and presses Enter, then replies with `{"type":"barcode_ack","id":"unique-scan-id","value":"BARCODE_VALUE","success":true}`. Repeated IDs return the same acknowledgement without typing again. Existing `{"type":"scan","data":"BARCODE_VALUE"}` clients are still supported.

## Endpoints

| Path | Method | Description |
|------|--------|-------------|
| `/` | GET | Web UI |
| `/qr.png` | GET | QR code image |
| `/ws` | WebSocket | Phone scanner connects here |
| `/monitor` | WebSocket | Web UI log stream |
| `/inject` | POST | Test injection: `{"type":"scan","data":"...","auto_enter":true}` |

The web UI, QR, activity stream, and test-injection endpoint are local-desktop only. LAN clients may access `/ws` only with the current pairing code.
