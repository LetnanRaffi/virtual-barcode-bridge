package com.raffitech.scanbridge.scanner

import com.google.mlkit.vision.barcode.common.Barcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScanConfidenceTest {
    @Test fun validRetailBarcodeNeedsTwoFrames() {
        val confidence = ScanConfidence()
        assertNull(confidence.observe("4006381333931", Barcode.FORMAT_EAN_13, 1000))
        assertEquals("4006381333931", confidence.observe("4006381333931", Barcode.FORMAT_EAN_13, 1130))
    }

    @Test fun badCheckDigitIsNeverSent() {
        val confidence = ScanConfidence()
        repeat(5) { index ->
            assertNull(confidence.observe("4006381333932", Barcode.FORMAT_EAN_13, 1000 + index * 120L))
        }
    }

    @Test fun changingOrStaleResultRestartsConfirmation() {
        val confidence = ScanConfidence()
        assertNull(confidence.observe("ABC-1", Barcode.FORMAT_CODE_128, 1000))
        assertNull(confidence.observe("ABC-2", Barcode.FORMAT_CODE_128, 1100))
        assertNull(confidence.observe("ABC-1", Barcode.FORMAT_CODE_128, 1200))
        assertNull(confidence.observe("ABC-1", Barcode.FORMAT_CODE_128, 2800))
        assertNull(confidence.observe("ABC-1", Barcode.FORMAT_CODE_128, 2900))
        assertEquals("ABC-1", confidence.observe("ABC-1", Barcode.FORMAT_CODE_128, 3000))
    }

    @Test fun controlCharactersAreRejected() {
        val confidence = ScanConfidence()
        repeat(4) { index -> assertNull(confidence.observe("ABC\n123", Barcode.FORMAT_CODE_128, 1000 + index * 100L)) }
    }
}
