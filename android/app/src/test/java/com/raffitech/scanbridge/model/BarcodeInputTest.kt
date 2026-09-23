package com.raffitech.scanbridge.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeInputTest {
    @Test fun acceptsRetailAndInventoryValues() {
        assertTrue(validManualBarcode("4006381333931"))
        assertTrue(validManualBarcode("INV-ABC 123"))
    }

    @Test fun rejectsEmptyUnsafeOrOversizedValues() {
        assertFalse(validManualBarcode("  "))
        assertFalse(validManualBarcode("ABC\n123"))
        assertFalse(validManualBarcode("A".repeat(257)))
    }
}
