package com.latch.focus.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The Latch mark: a disc inside a ring, the tag you tap. It stands in for the stock NFC icon everywhere, so the app
 * reads as a product rather than a tech utility. It matches the launcher icon (res/drawable/ic_launcher_foreground).
 */
val LatchGlyph: ImageVector by lazy {
    ImageVector.Builder("Latch", 24.dp, 24.dp, 24f, 24f).apply {
        // Ring: r = 9, stroke 1.8
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f) {
            moveTo(3f, 12f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 21f, y1 = 12f)
            arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 3f, y1 = 12f)
            close()
        }
        // Disc: r = 5
        path(fill = SolidColor(Color.Black)) {
            moveTo(7f, 12f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 17f, y1 = 12f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 7f, y1 = 12f)
            close()
        }
    }.build()
}
