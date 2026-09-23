package com.raffitech.scanbridge.model

fun validManualBarcode(value: String): Boolean =
    value.isNotBlank() && value.length <= 256 && value.none { it.isISOControl() }
