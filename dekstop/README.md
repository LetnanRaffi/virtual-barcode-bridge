# Virtual Barcode Bridge

Portable desktop bridge: receive barcode via WebSocket from phone, inject as keyboard input into any focused textbox.

## Requirements

- **Linux:** `xdotool` (X11; Wayland not supported)
- **Windows:** Nothing — native Win32 API

## Build

```bash
# Linux
go build -buildvcs=false -o vbb .

# Windows
GOOS=windows GOARCH=amd64 go build -buildvcs=false -o vbb.exe .
```

## Usage

```bash
# Linux
./vbb

# Windows
vbb.exe
```

On start: auto-detects LAN IP, starts server, opens browser to web UI, prints QR code.

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

Connect any WebSocket client to `ws://<YOUR_LAN_IP>:8080/ws`.

Send JSON:
```json
{
  "type": "scan",
  "data": "BARCODE_VALUE",
  "auto_enter": true
}
```

The bridge types `BARCODE_VALUE` into the focused window and presses Enter.

## Endpoints

| Path | Method | Description |
|------|--------|-------------|
| `/` | GET | Web UI |
| `/qr.png` | GET | QR code image |
| `/ws` | WebSocket | Phone scanner connects here |
| `/monitor` | WebSocket | Web UI log stream |
| `/inject` | POST | Test injection: `{"type":"scan","data":"...","auto_enter":true}` |