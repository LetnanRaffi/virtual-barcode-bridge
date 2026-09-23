package com.raffitech.scanbridge.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raffitech.scanbridge.model.ConnectionState
import com.raffitech.scanbridge.scanner.BarcodeCamera
import com.raffitech.scanbridge.scanner.CameraMode
import com.raffitech.scanbridge.scanner.CameraPermissionGate
import com.raffitech.scanbridge.ui.components.Brand
import com.raffitech.scanbridge.ui.components.ConnectionCard
import com.raffitech.scanbridge.ui.components.PrimaryAction
import com.raffitech.scanbridge.ui.components.ScanIcons
import com.raffitech.scanbridge.ui.components.StatusPill
import com.raffitech.scanbridge.ui.components.SurfacePanel
import com.raffitech.scanbridge.ui.theme.Accent
import com.raffitech.scanbridge.ui.theme.Background
import com.raffitech.scanbridge.ui.theme.Error
import com.raffitech.scanbridge.ui.theme.TextPrimary
import com.raffitech.scanbridge.ui.theme.TextSecondary
import com.raffitech.scanbridge.viewmodel.MainViewModel

@Composable
fun MethodScreen(onUsb: () -> Unit, onWifi: () -> Unit, onHelp: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Background).safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Brand()
        Spacer(Modifier.height(46.dp))
        Text("Hubungkan ke komputer", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text("Pilih cara menghubungkan HP.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        Spacer(Modifier.height(38.dp))
        ConnectionCard("USB", "Cepat dan stabil.", ScanIcons.Usb, true, onUsb)
        Spacer(Modifier.height(16.dp))
        ConnectionCard("Wi-Fi", "Pindai kode QR di komputer.", ScanIcons.Wifi, false, onWifi)
        Spacer(Modifier.height(46.dp))
        TextButton(onClick = onHelp) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Accent)
            Spacer(Modifier.size(10.dp))
            Column {
                Text("Sulit terhubung?", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Text("Bantuan koneksi", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(28.dp))
        Text("HP memindai. Komputer mengetik.", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun UsbScreen(vm: MainViewModel, onBack: () -> Unit) {
    val connection by vm.connection.collectAsStateWithLifecycle()
    DisposableEffect(Unit) {
        vm.startUsbDiscovery()
        onDispose { vm.stopUsbDiscovery() }
    }
    Column(
        Modifier.fillMaxSize().background(Background).safeDrawingPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = TextPrimary) }
        Spacer(Modifier.height(20.dp))
        Text("Hubungkan lewat USB", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        Text("Biarkan layar ini terbuka saat komputer menyiapkan koneksi.", color = TextSecondary)
        Spacer(Modifier.height(34.dp))
        listOf(
            "Sambungkan HP dengan kabel USB",
            "Aktifkan USB debugging",
            "Izinkan komputer ini di HP",
        ).forEachIndexed { index, step ->
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(Accent.copy(alpha = .16f), androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", color = Accent)
                }
                Spacer(Modifier.size(16.dp))
                Text(step, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(32.dp))
        SurfacePanel(Modifier.fillMaxWidth()) {
            Column {
                val title = when (connection) {
                    ConnectionState.Disconnected -> "Menunggu koneksi USB"
                    ConnectionState.Connecting -> "Mencari komputer…"
                    ConnectionState.ComputerFound -> "Komputer ditemukan"
                    is ConnectionState.Connected -> "Terhubung"
                    is ConnectionState.Error -> "Menunggu koneksi USB"
                }
                StatusPill(title, connection is ConnectionState.Connected)
                Spacer(Modifier.height(14.dp))
                Text(
                    if (connection is ConnectionState.Error) "Periksa kabel, USB debugging, dan mode USB di komputer. Mencoba lagi otomatis."
                    else "ScanBridge memeriksa koneksi USB secara otomatis.",
                    color = TextSecondary, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("HP tidak terdeteksi? Coba Wi-Fi atau USB tethering lewat pilihan Jaringan di komputer.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiScreen(vm: MainViewModel, onBack: () -> Unit) {
    val connection by vm.connection.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var torch by remember { mutableStateOf(false) }
    var qrBusy by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(connection) { if (connection is ConnectionState.Error) qrBusy = false }
    Box(Modifier.fillMaxSize().background(Background)) {
        CameraPermissionGate {
            Box(Modifier.fillMaxSize()) {
                BarcodeCamera(
                    mode = CameraMode.QR,
                    torch = torch,
                    onCode = { code ->
                        if (!qrBusy) {
                            val valid = vm.connectWifi(code)
                            if (valid) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            qrBusy = valid
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                ScannerFrame(success = false, square = true)
            }
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = TextPrimary) }
                Spacer(Modifier.weight(1f))
                Brand()
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.height(30.dp))
            Text("Pindai QR untuk terhubung", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Arahkan kamera ke kode QR di komputer.", color = TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            if (connection is ConnectionState.Connecting || connection is ConnectionState.ComputerFound) StatusPill("Menghubungkan…", false)
            if (connection is ConnectionState.Error) Text("Gagal terhubung. Coba lagi atau masukkan alamat.", color = Error, textAlign = TextAlign.Center)
            if (message != null) Text(message ?: "", color = Error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            IconButton(onClick = { torch = !torch }, modifier = Modifier.size(56.dp)) {
                Icon(ScanIcons.Flash, if (torch) "Matikan lampu" else "Nyalakan lampu", tint = if (torch) Accent else TextPrimary)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { sheet = true }) {
                Icon(ScanIcons.Keyboard, contentDescription = null, tint = Accent)
                Spacer(Modifier.size(8.dp))
                Text("Masukkan alamat manual", color = TextPrimary)
            }
            Spacer(Modifier.height(26.dp))
        }
    }
    if (sheet) ModalBottomSheet(onDismissRequest = { sheet = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 36.dp)) {
            Text("Alamat komputer", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Salin alamat lengkap di bawah QR komputer, termasuk kode pasangan setelah /ws.", color = TextSecondary)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = address, onValueChange = { address = it; vm.clearMessage() },
                label = { Text("Alamat komputer") },
                placeholder = { Text("ws://192.168.1.2:8080/ws?pair=...") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(), isError = message != null,
            )
            if (message != null) Text(message ?: "", color = Error, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(20.dp))
            PrimaryAction("Hubungkan", onClick = {
                if (vm.connectWifi(address)) {
                    sheet = false
                    qrBusy = true
                }
            })
        }
    }
}

@Composable
fun HelpScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali", tint = TextPrimary) }
        Spacer(Modifier.height(20.dp))
        Text("Bantuan koneksi", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))
        Text("USB", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Pilih USB di aplikasi komputer. Aktifkan USB debugging di HP dan izinkan komputer. Jika HP tidak terdeteksi, periksa izin ADB atau driver USB. USB tethering lewat mode Jaringan juga bisa dipakai.", color = TextSecondary)
        Spacer(Modifier.height(24.dp))
        Text("Wi-Fi", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Pastikan HP dan komputer berada di jaringan lokal yang sama. Pilih adapter jaringan yang bisa dijangkau HP, lalu pindai kode QR-nya. Jika firewall bertanya, izinkan jaringan lokal.", color = TextSecondary)
    }
}
