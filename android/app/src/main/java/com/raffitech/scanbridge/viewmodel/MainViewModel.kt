package com.raffitech.scanbridge.viewmodel

import android.app.Application
import android.content.Context
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.raffitech.scanbridge.data.SettingsRepository
import com.raffitech.scanbridge.model.ConnectionState
import com.raffitech.scanbridge.model.RecentScan
import com.raffitech.scanbridge.model.ScanSettings
import com.raffitech.scanbridge.model.ScannerState
import com.raffitech.scanbridge.model.TransportType
import com.raffitech.scanbridge.model.validManualBarcode
import com.raffitech.scanbridge.network.BridgeSocket
import java.net.URI
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val bridge = BridgeSocket()
    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connection = _connection.asStateFlow()
    private val _scanner = MutableStateFlow<ScannerState>(ScannerState.Idle)
    val scanner = _scanner.asStateFlow()
    private val _recent = MutableStateFlow<RecentScan?>(null)
    val recent = _recent.asStateFlow()
    private val _settings = MutableStateFlow(ScanSettings())
    val settings = _settings.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private var targetUrl: String? = null
    private var targetTransport = TransportType.USB
    private var usbDiscovery: Job? = null
    private var lastCode = ""
    private var lastCodeAt = 0L
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)

    init {
        viewModelScope.launch { settingsRepository.settings.collectLatest { _settings.value = it } }
        viewModelScope.launch {
            bridge.state.collectLatest { state ->
                _connection.value = state
                if (state is ConnectionState.Connected) {
                    targetUrl?.let { settingsRepository.saveConnection(it, targetTransport) }
                    _message.value = null
                }
            }
        }
        viewModelScope.launch {
            bridge.delivery.collectLatest { delivery ->
                val now = System.currentTimeMillis()
                if (delivery.success) {
                    _scanner.value = ScannerState.Sent(delivery.value)
                    _recent.value = RecentScan(delivery.value, now, true)
                    if (_settings.value.sound) tone.startTone(ToneGenerator.TONE_PROP_ACK, 100)
                } else {
                    _scanner.value = ScannerState.Failed(delivery.value, delivery.error.ifBlank { "Komputer gagal mengetik hasil pindai" })
                    _recent.value = RecentScan(delivery.value, now, false, delivery.error)
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.lastConnection.first()?.let { (url, transport) ->
                if (targetUrl == null) {
                    targetUrl = url
                    targetTransport = transport
                    bridge.connect(url, transport)
                    if (transport == TransportType.USB) startUsbDiscovery()
                }
            }
        }
    }

    fun connectWifi(raw: String): Boolean {
        val url = raw.trim()
        if (!validWebSocketUrl(url)) {
            _message.value = "Masukkan alamat ws:// komputer yang valid dan berakhiran /ws"
            return false
        }
        stopUsbDiscovery()
        targetUrl = url
        targetTransport = TransportType.WIFI
        _message.value = null
        bridge.connect(url, TransportType.WIFI)
        return true
    }

    fun startUsbDiscovery() {
        targetUrl = BridgeSocket.USB_URL
        targetTransport = TransportType.USB
        usbDiscovery?.cancel()
        usbDiscovery = viewModelScope.launch {
            while (true) {
                when (_connection.value) {
                    is ConnectionState.Disconnected, is ConnectionState.Error -> bridge.connect(BridgeSocket.USB_URL, TransportType.USB)
                    else -> Unit
                }
                delay(3500)
            }
        }
    }

    fun stopUsbDiscovery() {
        usbDiscovery?.cancel()
        usbDiscovery = null
    }

    fun disconnect() {
        stopUsbDiscovery()
        bridge.disconnect()
        targetUrl = null
        _scanner.value = ScannerState.Idle
        viewModelScope.launch { settingsRepository.clearConnection() }
    }

    fun beginScanning() { _scanner.value = ScannerState.Scanning }

    fun onBarcode(value: String) { submitBarcode(value, manual = false) }

    fun sendManualBarcode(raw: String): Boolean {
        val value = raw.trim()
        return validManualBarcode(value) && submitBarcode(value, manual = true)
    }

    private fun submitBarcode(value: String, manual: Boolean): Boolean {
        if (value.isBlank() || _scanner.value is ScannerState.Sending || (!manual && _scanner.value is ScannerState.Failed)) return false
        val now = System.currentTimeMillis()
        if (value == lastCode && now - lastCodeAt < _settings.value.cooldownSeconds * 1000L) return false
        if (!manual && !_settings.value.continuous && _scanner.value is ScannerState.Sent) return false
        _scanner.value = ScannerState.Sending(value)
        val id = bridge.send(value)
        if (id == null) {
            _recent.value = RecentScan(value, now, false, "Antrean penuh atau komputer belum dipilih")
            _scanner.value = ScannerState.Failed(value, "Antrean penuh atau komputer belum dipilih")
            return false
        }
        lastCode = value
        lastCodeAt = now
        if (_settings.value.vibration) vibrate()
        return true
    }

    fun scanAgain() { _scanner.value = ScannerState.Scanning }

    fun retryFailed() {
        val failed = _scanner.value as? ScannerState.Failed ?: return
        _scanner.value = ScannerState.Sending(failed.value)
        if (bridge.send(failed.value) == null) {
            _scanner.value = ScannerState.Failed(failed.value, "Masih menunggu komputer atau ruang antrean")
        }
    }

    fun updateSettings(value: ScanSettings) {
        _settings.value = value
        viewModelScope.launch { settingsRepository.update(value) }
    }

    fun clearMessage() { _message.value = null }

    private fun vibrate() {
        val context = getApplication<Application>()
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun onCleared() {
        tone.release()
        bridge.disconnect()
        super.onCleared()
    }
}

fun validWebSocketUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme == "ws" && !uri.host.isNullOrBlank() && uri.port in 1..65535 &&
        uri.path == "/ws" && uri.userInfo == null && uri.fragment == null
}.getOrDefault(false)
