package com.raffitech.scanbridge.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raffitech.scanbridge.model.ConnectionState
import com.raffitech.scanbridge.model.ScannerState
import com.raffitech.scanbridge.model.TransportType
import com.raffitech.scanbridge.scanner.BarcodeCamera
import com.raffitech.scanbridge.scanner.CameraMode
import com.raffitech.scanbridge.scanner.CameraPermissionGate
import com.raffitech.scanbridge.ui.components.Brand
import com.raffitech.scanbridge.ui.components.PrimaryAction
import com.raffitech.scanbridge.ui.components.ScanIcons
import com.raffitech.scanbridge.ui.components.StatusPill
import com.raffitech.scanbridge.ui.components.SurfacePanel
import com.raffitech.scanbridge.ui.theme.Accent
import com.raffitech.scanbridge.ui.theme.Background
import com.raffitech.scanbridge.ui.theme.Error
import com.raffitech.scanbridge.ui.theme.Outline
import com.raffitech.scanbridge.ui.theme.Success
import com.raffitech.scanbridge.ui.theme.Surface
import com.raffitech.scanbridge.ui.theme.TextPrimary
import com.raffitech.scanbridge.ui.theme.TextSecondary
import com.raffitech.scanbridge.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadyScreen(vm: MainViewModel, onScan: () -> Unit, onDisconnect: () -> Unit) {
    val connection by vm.connection.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    Column(
        Modifier.fillMaxSize().background(Background).safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Brand(Modifier.weight(1f))
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Pengaturan pemindaian", tint = TextPrimary) }
        }
        Spacer(Modifier.height(24.dp))
        StatusPill(if (connection is ConnectionState.Connected) "Terhubung" else "Koneksi terputus · menghubungkan ulang", connection is ConnectionState.Connected)
        Spacer(Modifier.height(28.dp))
        Text("Siap memindai", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(7.dp))
        Text("HP memindai. Komputer mengetik.", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(30.dp))
        ConnectionIllustration()
        Spacer(Modifier.height(20.dp))
        SurfacePanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(ScanIcons.Computer, contentDescription = null, tint = Accent, modifier = Modifier.size(25.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text((connection as? ConnectionState.Connected)?.computerName ?: "Komputer", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(if ((connection as? ConnectionState.Connected)?.transport == TransportType.USB) "Terhubung lewat USB" else "Terhubung lewat Wi-Fi", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                if (connection is ConnectionState.Connected) Icon(Icons.Default.Check, contentDescription = "Terhubung", tint = Success)
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryAction("Mulai memindai", onClick = onScan, enabled = connection is ConnectionState.Connected)
        Spacer(Modifier.height(16.dp))
        SurfacePanel(Modifier.fillMaxWidth()) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Pemindaian terakhir", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                    if (recent != null) Text(
                        when (val seconds = ((now - recent!!.sentAt) / 1000).coerceAtLeast(0)) {
                            in 0..59 -> "${seconds} detik lalu"
                            else -> "${seconds / 60} menit lalu"
                        }, color = TextSecondary, style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(recent?.value ?: "Belum ada pemindaian", fontSize = 18.sp, color = TextPrimary)
                if (recent != null) Text(
                    if (recent!!.success) "Berhasil dikirim" else recent!!.message.ifBlank { "Gagal dikirim" },
                    color = if (recent!!.success) Success else Error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        TextButton(onClick = onDisconnect, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Putuskan koneksi", color = TextSecondary)
        }
    }

    if (showSettings) ModalBottomSheet(onDismissRequest = { showSettings = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 36.dp)) {
            Text("Pengaturan pemindaian", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(18.dp))
            SettingToggle("Pindai terus-menerus", settings.continuous) { vm.updateSettings(settings.copy(continuous = it)) }
            SettingToggle("Scan miring", settings.angledScan) { vm.updateSettings(settings.copy(angledScan = it)) }
            Text("Perluas area bidik untuk barcode diagonal.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            SettingToggle("Suara konfirmasi", settings.sound) { vm.updateSettings(settings.copy(sound = it)) }
            SettingToggle("Getaran", settings.vibration) { vm.updateSettings(settings.copy(vibration = it)) }
            Spacer(Modifier.height(12.dp))
            Text("Jeda barcode yang sama · ${settings.cooldownSeconds} detik", color = TextSecondary)
            Slider(
                value = settings.cooldownSeconds.toFloat(),
                onValueChange = { vm.updateSettings(settings.copy(cooldownSeconds = it.toInt().coerceIn(1, 5))) },
                valueRange = 1f..5f, steps = 3,
            )
        }
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ConnectionIllustration() {
    Card(
        Modifier.fillMaxWidth().height(156.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Outline.copy(alpha = .6f)),
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(ScanIcons.Computer, contentDescription = null, tint = Accent, modifier = Modifier.size(66.dp))
            Spacer(Modifier.width(28.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Success, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(28.dp))
            Icon(ScanIcons.Phone, contentDescription = null, tint = Accent, modifier = Modifier.size(61.dp))
        }
    }
}

@Composable
fun BarcodeScreen(vm: MainViewModel, onBack: () -> Unit) {
    val connection by vm.connection.collectAsStateWithLifecycle()
    val scanner by vm.scanner.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var torch by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Background)) {
        CameraPermissionGate {
            Box(Modifier.fillMaxSize()) {
                BarcodeCamera(CameraMode.BARCODE, torch, vm::onBarcode, Modifier.fillMaxSize(), angledScan = settings.angledScan)
                ScannerFrame(success = scanner is ScannerState.Sent, angled = settings.angledScan)
            }
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = TextPrimary) }
                Spacer(Modifier.weight(1f))
                Text("Pindai barcode", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { torch = !torch }) {
                    Icon(ScanIcons.Flash, if (torch) "Matikan lampu" else "Nyalakan lampu", tint = if (torch) Accent else TextPrimary)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Arahkan barcode ke bingkai. Ketuk untuk fokus.", color = TextPrimary, modifier = Modifier.align(Alignment.CenterHorizontally))
            TextButton(onClick = { vm.updateSettings(settings.copy(angledScan = !settings.angledScan)) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (settings.angledScan) "Scan miring: aktif" else "Scan miring: nonaktif", color = TextPrimary)
            }
            Spacer(Modifier.weight(1f))
            if (connection !is ConnectionState.Connected) {
                StatusPill("Koneksi terputus · menghubungkan ulang", false)
                Spacer(Modifier.height(12.dp))
            }
            SurfacePanel(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        when (scanner) {
                            ScannerState.Idle, ScannerState.Scanning -> "Memindai…"
                            is ScannerState.BarcodeDetected -> "Barcode terbaca"
                            is ScannerState.Sending -> "Mengirim ke komputer…"
                            is ScannerState.Sent -> "Berhasil dikirim"
                            is ScannerState.Failed -> "Gagal dikirim"
                        },
                        color = if (scanner is ScannerState.Sent) Success else if (scanner is ScannerState.Failed) Error else Accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val value = when (scanner) {
                        is ScannerState.BarcodeDetected -> (scanner as ScannerState.BarcodeDetected).value
                        is ScannerState.Sending -> (scanner as ScannerState.Sending).value
                        is ScannerState.Sent -> (scanner as ScannerState.Sent).value
                        is ScannerState.Failed -> (scanner as ScannerState.Failed).value
                        else -> "Arahkan ke barcode produk"
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(value, color = TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (scanner is ScannerState.Failed) Text((scanner as ScannerState.Failed).message, color = Error, style = MaterialTheme.typography.bodyMedium)
                    if (scanner is ScannerState.Failed) TextButton(onClick = vm::retryFailed) { Text("Coba kirim lagi", color = TextPrimary) }
                    if (scanner is ScannerState.Failed) TextButton(onClick = vm::scanAgain) { Text("Pindai barcode lain", color = TextPrimary) }
                    if (!settings.continuous && scanner is ScannerState.Sent) {
                        TextButton(onClick = vm::scanAgain) { Text("Pindai lagi", color = TextPrimary) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ScannerFrame(success: Boolean, square: Boolean = false, angled: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "scanner")
    val progress by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400), RepeatMode.Reverse), label = "scan line")
    Canvas(Modifier.fillMaxSize()) {
        val width = size.width * .76f
        val height = if (square) width else (size.height * if (angled) .48f else .30f).coerceAtMost(width)
        val left = (size.width - width) / 2
        val top = (size.height - height) / 2
        val right = left + width
        val bottom = top + height
        val length = 34.dp.toPx()
        val c = if (success) Success else Accent
        val stroke = 3.dp.toPx()
        listOf(
            Offset(left, top + length) to Offset(left, top), Offset(left, top) to Offset(left + length, top),
            Offset(right - length, top) to Offset(right, top), Offset(right, top) to Offset(right, top + length),
            Offset(left, bottom - length) to Offset(left, bottom), Offset(left, bottom) to Offset(left + length, bottom),
            Offset(right - length, bottom) to Offset(right, bottom), Offset(right, bottom) to Offset(right, bottom - length),
        ).forEach { (start, end) -> drawLine(c, start, end, strokeWidth = stroke, cap = StrokeCap.Round) }
        drawLine(c.copy(alpha = .25f), Offset(left + 16.dp.toPx(), top + height * progress), Offset(right - 16.dp.toPx(), top + height * progress), strokeWidth = 1.dp.toPx())
    }
}
