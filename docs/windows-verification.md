# Windows executable verification

## Current development build (2026-09-23)

The USB ADB / connection-picker build with ScanBridge barcode acknowledgements was cross-compiled into `dist/vbb.exe` (Windows x64 GUI). Its SHA-256 is `88ce8a29ad3ba5660ee3fc00b7cefb3454be285cccaf2eb496eb71be0f66af5a`. Linux Go tests and the Android ScanBridge 1.0.0 debug APK build passed. The new native GUI and physical USB ADB flow have not yet been exercised on a Windows 10/11 computer with a phone; the checks below apply to the older 2026-09-17 build.

Date: 2026-09-17

Artifact: `dist/vbb.exe` (Windows x64)
SHA-256: `5dd6f18b6e3ef793fabc32ec442287c9160675ca902f6b2c1f7e799a3f734a39`

## Executed checks

Windows test executable ran under Wine 9.0 with an isolated Xvfb display.
`TestNativeWindowLifecycle` passed:

- Resolve Win32 DLL functions and create the native window.
- Paint the initial QR.
- Switch from an Ethernet endpoint (172.16.1.2) to a Wi-Fi endpoint
  (192.168.1.2), verify the selected URL, and repaint.
- Connect a real WebSocket client to the bridge using the Windows keyboard
  injector; send VBB-1234567890 and verify the exact text in a focused native
  Windows EDIT control.
- Process WM_CLOSE and exit the message loop.

The actual dist/vbb.exe also ran under Wine:

- Detected the host Wi-Fi interface and advertised 192.168.1.2.
- Displayed the adapter selector, QR, endpoint text and instructions.
- Served /networks on port 8080.
- Exited with code 0 after Alt+F4.

Earlier checks passed: Linux Go test suite, per-network QR response tests,
Windows x64 build and Windows test compilation.

## Limits

Wine execution is not a Windows 10/11 hardware test. Windows Firewall,
simultaneous physical LAN/Wi-Fi adapters, and Android camera-to-PC pairing
still require checking on the target computer. The network-switch test uses
two supplied endpoints; it does not prove reachability of both networks.

## Repeat on Windows with Go installed

From the dekstop directory:

```powershell
go test -run TestNativeWindowLifecycle -v -timeout 30s .
```

The test briefly opens a window and focuses its own input field.
