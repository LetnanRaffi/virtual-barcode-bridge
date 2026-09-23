package com.raffitech.scanbridge.scanner

import com.google.mlkit.vision.barcode.common.Barcode

/** Rejects malformed retail codes and waits for the same decode on separate camera frames. */
internal class ScanConfidence {
    private var value: String? = null
    private var format = -1
    private var count = 0
    private var firstSeenAt = 0L
    private var emittedAt = 0L
    private var latchedValue: String? = null
    private var missingSince = 0L

    fun observe(raw: String, barcodeFormat: Int, now: Long): String? {
        if (!validCode(raw, barcodeFormat)) return null
        missingSince = 0L
        if (latchedValue == raw) return null
        if (latchedValue != null) latchedValue = null
        if (value != raw || format != barcodeFormat || now - firstSeenAt > 1500L) {
            value = raw
            format = barcodeFormat
            count = 1
            firstSeenAt = now
            return null
        }
        count++
        val required = when (barcodeFormat) {
            Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E -> 2
            else -> 3
        }
        if (count < required || now - emittedAt < 900L) return null
        emittedAt = now
        latchedValue = raw
        count = 0
        firstSeenAt = now
        return raw
    }

    fun miss(now: Long) {
        if (missingSince == 0L) missingSince = now
        if (now - missingSince >= 800L) {
            latchedValue = null
            value = null
            count = 0
        }
    }

    private fun validCode(raw: String, format: Int): Boolean = when (format) {
        Barcode.FORMAT_EAN_13 -> raw.length == 13 && validMod10(raw)
        Barcode.FORMAT_EAN_8 -> raw.length == 8 && validMod10(raw)
        Barcode.FORMAT_UPC_A -> raw.length == 12 && validMod10(raw)
        Barcode.FORMAT_UPC_E -> validUpcE(raw)
        else -> raw.isNotBlank() && raw.length <= 256 && raw.none { it.isISOControl() }
    }

    private fun validUpcE(raw: String): Boolean {
        if (raw.length != 8 || !raw.all(Char::isDigit) || raw[0] !in "01") return false
        val numberSystem = raw[0]
        val data = raw.substring(1, 7)
        val expanded = when (data[5]) {
            '0', '1', '2' -> "$numberSystem${data.substring(0, 2)}${data[5]}0000${data.substring(2, 5)}"
            '3' -> "$numberSystem${data.substring(0, 3)}00000${data.substring(3, 5)}"
            '4' -> "$numberSystem${data.substring(0, 4)}00000${data[4]}"
            else -> "$numberSystem${data.substring(0, 5)}0000${data[5]}"
        }
        return validMod10(expanded + raw[7])
    }

    private fun validMod10(raw: String): Boolean {
        if (!raw.all(Char::isDigit)) return false
        val sum = raw.dropLast(1).reversed().mapIndexed { index, digit ->
            digit.digitToInt() * if (index % 2 == 0) 3 else 1
        }.sum()
        return (10 - sum % 10) % 10 == raw.last().digitToInt()
    }
}
