# ScanBridge Android

Open this directory in Android Studio (JDK 17, Android SDK 34). The project includes a Gradle 8.7 wrapper. Build a debug APK with `./gradlew assembleDebug`; the output is `app/build/outputs/apk/debug/app-debug.apk`.

Package: `com.raffitech.scanbridge` · minimum Android 8 (API 26).

The app uses Kotlin, Compose, CameraX, bundled ML Kit barcode scanning, OkHttp WebSocket, ViewModel/Flow, Navigation Compose, and DataStore. Source packages are split into `data`, `network`, `scanner`, `ui`, `viewmodel`, and `model`.

## Connect

- USB: run the updated ScanBridge `vbb.exe` on Windows, choose USB, connect the phone, enable USB debugging, and approve the computer. On the phone choose USB. The desktop configures `adb reverse tcp:8080 tcp:8080`; the app uses `ws://127.0.0.1:8080/ws`.
- Wi-Fi/LAN: choose a reachable adapter on the desktop, then scan its QR in the app. The QR contains a `ws://<computer-ip>:8080/ws` URL. Manual address entry is inside the Wi-Fi screen.

The desktop sends a `hello` message with its computer name. Each barcode gets a unique ID and a `barcode_ack` after desktop keyboard injection. Unacknowledged IDs are retried on the current connection and after reconnect; the desktop caches acknowledgements to prevent the same ID being typed twice. A failed acknowledgement is shown in the scanner, where the user can choose to retry with a new ID.

Camera permission is requested when a camera screen is opened. On small phones the method, USB, connected, and settings screens scroll; camera controls stay above system bars using safe insets.

## Pemindaian akurat

UI Android memakai bahasa Indonesia. Scanner menganalisis kamera pada resolusi target 1280×720, memakai fokus ketuk dan saran zoom ML Kit saat barcode terlalu kecil. Barcode ritel EAN/UPC divalidasi dengan digit pemeriksa dan harus terbaca konsisten pada dua frame; format lain perlu tiga frame. Hasil yang berubah-ubah akibat blur tidak langsung dikirim. Opsi **Scan miring** memperluas area bidik untuk barcode diagonal, tetap dengan konfirmasi berulang. Jika cahaya kurang, nyalakan lampu; plastik yang sangat mengilap atau barcode yang tertutup rapat tetap mungkin tidak dapat dibaca oleh kamera mana pun.
