package com.raffitech.scanbridge.model

enum class TransportType { USB, WIFI }

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object ComputerFound : ConnectionState
    data class Connected(val computerName: String, val transport: TransportType) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

sealed interface ScannerState {
    data object Idle : ScannerState
    data object Scanning : ScannerState
    data class BarcodeDetected(val value: String) : ScannerState
    data class Sending(val value: String) : ScannerState
    data class Sent(val value: String) : ScannerState
    data class Failed(val value: String, val message: String) : ScannerState
}

data class ScanSettings(
    val continuous: Boolean = true,
    val sound: Boolean = true,
    val vibration: Boolean = true,
    val cooldownSeconds: Int = 2,
    val angledScan: Boolean = false,
)

data class RecentScan(
    val value: String,
    val sentAt: Long,
    val success: Boolean,
    val message: String = "",
)

data class ScanDelivery(val id: String, val value: String, val success: Boolean, val error: String = "")
