package com.raffitech.scanbridge.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.ZoomSuggestionOptions
import com.google.mlkit.vision.common.InputImage
import com.raffitech.scanbridge.ui.components.PrimaryAction
import com.raffitech.scanbridge.ui.theme.Background
import com.raffitech.scanbridge.ui.theme.TextSecondary
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class CameraMode { QR, BARCODE }

@Composable
fun CameraPermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var denied by remember { mutableStateOf(false) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        denied = !it
    }
    if (granted) content() else Column(
        Modifier.fillMaxSize().background(Background).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Izin kamera diperlukan", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            if (denied) "Izin kamera belum aktif. Izinkan di pengaturan Android atau coba lagi."
            else "Kamera dipakai untuk membaca QR komputer dan barcode produk. Gambar tidak diunggah.",
            color = TextSecondary,
        )
        Spacer(Modifier.height(28.dp))
        PrimaryAction("Izinkan kamera", onClick = { request.launch(Manifest.permission.CAMERA) })
    }
}

@Composable
fun BarcodeCamera(mode: CameraMode, torch: Boolean, onCode: (String) -> Unit, modifier: Modifier = Modifier, angledScan: Boolean = false) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val currentAngledScan by rememberUpdatedState(angledScan)
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(owner, mode) {
        val active = AtomicBoolean(true)
        val busy = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val main = Handler(Looper.getMainLooper())
        val confidence = ScanConfidence()
        val options = BarcodeScannerOptions.Builder().apply {
            if (mode == CameraMode.QR) setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            else {
                setBarcodeFormats(
                    Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX,
                )
                setZoomSuggestionOptions(ZoomSuggestionOptions.Builder { ratio ->
                    val current = camera
                    val max = current?.cameraInfo?.zoomState?.value?.maxZoomRatio
                    if (!active.get() || current == null || max == null || ratio > max) false
                    else {
                        current.cameraControl.setZoomRatio(ratio)
                        true
                    }
                }.setMaxSupportedZoomRatio(3f).build())
            }
        }.build()
        val scanner = BarcodeScanning.getClient(options)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var analysis: ImageAnalysis? = null
        var lastFrameAt = 0L

        providerFuture.addListener({
            if (!active.get()) return@addListener
            runCatching {
                provider = providerFuture.get()
                preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(Size(1280, 720))
                    .build().also { useCase ->
                        useCase.setAnalyzer(executor) { proxy: ImageProxy ->
                            val now = SystemClock.elapsedRealtime()
                            if (!active.get() || busy.get() || now - lastFrameAt < 90) {
                                proxy.close()
                                return@setAnalyzer
                            }
                            val mediaImage = proxy.image
                            if (mediaImage == null) {
                                proxy.close()
                                return@setAnalyzer
                            }
                            lastFrameAt = now
                            busy.set(true)
                            val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                            scanner.process(image)
                                .addOnSuccessListener { found ->
                                    if (!active.get()) return@addOnSuccessListener
                                    val width = if (proxy.imageInfo.rotationDegrees % 180 == 0) proxy.width else proxy.height
                                    val height = if (proxy.imageInfo.rotationDegrees % 180 == 0) proxy.height else proxy.width
                                    val matches = found.mapNotNull { barcode ->
                                        val box = barcode.boundingBox ?: return@mapNotNull null
                                        val cx = box.centerX().toFloat() / width
                                        val cy = box.centerY().toFloat() / height
                                        val yRange = if (mode == CameraMode.QR) 0.16f..0.84f
                                            else if (currentAngledScan) 0.23f..0.77f else 0.34f..0.66f
                                        if (cx !in 0.12f..0.88f || cy !in yRange || barcode.rawValue.isNullOrBlank()) null
                                        else barcode to ((cx - .5f) * (cx - .5f) + (cy - .5f) * (cy - .5f))
                                    }
                                    val barcode = matches.minByOrNull { it.second }?.first
                                    val raw = barcode?.rawValue
                                    val code = if (raw == null) null else if (mode == CameraMode.QR) raw
                                        else confidence.observe(raw, barcode.format, SystemClock.elapsedRealtime())
                                    if (raw == null && mode == CameraMode.BARCODE) confidence.miss(SystemClock.elapsedRealtime())
                                    if (code != null) main.post { if (active.get()) currentOnCode(code) }
                                }
                                .addOnCompleteListener {
                                    busy.set(false)
                                    proxy.close()
                                }
                        }
                    }
                camera = provider!!.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview!!, analysis!!)
                previewView.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) {
                        val point = previewView.meteringPointFactory.createPoint(event.x, event.y)
                        camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).setAutoCancelDuration(3, TimeUnit.SECONDS).build())
                        true
                    } else true
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            active.set(false)
            previewView.setOnTouchListener(null)
            provider?.unbind(*listOfNotNull(preview, analysis).toTypedArray())
            camera = null
            scanner.close()
            executor.shutdown()
        }
    }
    LaunchedEffect(torch, camera) { camera?.cameraControl?.enableTorch(torch) }
    AndroidView(factory = { previewView }, modifier = modifier)
}
