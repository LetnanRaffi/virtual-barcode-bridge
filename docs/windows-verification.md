# Windows executable verification

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
