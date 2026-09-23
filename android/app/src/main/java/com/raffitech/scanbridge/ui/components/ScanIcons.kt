package com.raffitech.scanbridge.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object ScanIcons {
    private fun icon(name: String, draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = draw,
            )
        }.build()

    val Usb = icon("USB") {
        moveTo(12f, 20f); lineTo(12f, 5f); moveTo(12f, 12f); lineTo(6f, 8f); lineTo(6f, 5f)
        moveTo(12f, 16f); lineTo(18f, 12f); lineTo(18f, 9f)
        moveTo(10f, 20f); lineTo(14f, 20f)
        moveTo(12f, 2f); lineTo(10f, 5f); lineTo(14f, 5f); close()
        moveTo(16.5f, 7.5f); lineTo(19.5f, 7.5f); lineTo(19.5f, 10.5f); lineTo(16.5f, 10.5f); close()
    }

    val Wifi = icon("Wi-Fi") {
        moveTo(2f, 8f); curveTo(7f, 3f, 17f, 3f, 22f, 8f)
        moveTo(5f, 12f); curveTo(9f, 8f, 15f, 8f, 19f, 12f)
        moveTo(9f, 16f); curveTo(11f, 14f, 13f, 14f, 15f, 16f)
        moveTo(12f, 20f); lineTo(12.01f, 20f)
    }

    val Flash = icon("Flashlight") {
        moveTo(8f, 3f); lineTo(16f, 3f); lineTo(15f, 9f); lineTo(9f, 9f); close()
        moveTo(9f, 9f); lineTo(10f, 21f); lineTo(14f, 21f); lineTo(15f, 9f)
        moveTo(12f, 14f); lineTo(12f, 17f)
    }

    val Computer = icon("Computer") {
        moveTo(3f, 4f); lineTo(21f, 4f); lineTo(21f, 17f); lineTo(3f, 17f); close()
        moveTo(12f, 17f); lineTo(12f, 21f); moveTo(8f, 21f); lineTo(16f, 21f)
    }

    val Phone = icon("Phone") {
        moveTo(7f, 2f); lineTo(17f, 2f); lineTo(17f, 22f); lineTo(7f, 22f); close()
        moveTo(10f, 19f); lineTo(14f, 19f)
    }

    val Keyboard = icon("Keyboard") {
        moveTo(2f, 6f); lineTo(22f, 6f); lineTo(22f, 18f); lineTo(2f, 18f); close()
        moveTo(5f, 10f); lineTo(6f, 10f); moveTo(9f, 10f); lineTo(10f, 10f); moveTo(13f, 10f); lineTo(14f, 10f)
        moveTo(17f, 10f); lineTo(18f, 10f); moveTo(7f, 14f); lineTo(17f, 14f)
    }
}
