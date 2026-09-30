// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The few line icons the app uses, drawn as 1.6-unit strokes on a 24-unit grid so they
 * match the type's weight. Kept here rather than pulling in an icon library.
 */
object AppIcons {
    val Help = stroke("Help", CIRCLE, "M9.6 9.4a2.4 2.4 0 1 1 3.4 2.2c-.6.3-1 .8-1 1.5v.6", "M12 16.8h.01")
    val Reset = stroke("Reset", "M4 12a8 8 0 1 0 2.4-5.7", "M4 4v4h4")
    val Copy = stroke(
        "Copy",
        "M10 8h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-8a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2z",
        "M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2",
    )
    val Compass = stroke("Compass", CIRCLE, "M12 5l2.5 7h-5z", "M12 19l-2.5-7h5z")
    val Settings = stroke(
        "Settings",
        "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0",
        "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 " +
            "0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 " +
            "19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 " +
            ".33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 " +
            "1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 " +
            "1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 " +
            "1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 " +
            "1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z",
    )
    val Close = stroke("Close", "M6 6l12 12", "M18 6L6 18")
    val ShieldCheck = stroke("ShieldCheck", "M12 3l7 3v6c0 4.2-3 7.6-7 9-4-1.4-7-4.8-7-9V6z", "M8.5 12l2.5 2.5 4.5-5")
    val ShieldAlert = stroke("ShieldAlert", "M12 3l7 3v6c0 4.2-3 7.6-7 9-4-1.4-7-4.8-7-9V6z", "M12 8v5", "M12 16h.01")
    val Bell = stroke("Bell", "M6 8a6 6 0 1 1 12 0c0 7 3 8 3 8H3s3-1 3-8", "M10 20a2 2 0 0 0 4 0")
    val Battery = stroke(
        "Battery",
        "M5 7h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z",
        "M21 11v2",
        "M6 10v4",
    )
    val Lock = stroke(
        "Lock",
        "M7 11h10a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2z",
        "M8 11V8a4 4 0 0 1 8 0v3",
    )
    val Pin = stroke(
        "Pin",
        "M12 21s7-6.2 7-11.5A7 7 0 0 0 5 9.5C5 14.8 12 21 12 21z",
        "M14.5 9.5a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0",
    )
    val External = stroke(
        "External",
        "M14 4h6v6",
        "M20 4l-9 9",
        "M18 14v4a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4",
    )

    private const val CIRCLE = "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0"

    /** The icons are drawn on a 24 × 24 grid, the size they are shown at. */
    private const val GRID = 24f

    private fun stroke(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, GRID.dp, GRID.dp, GRID, GRID).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = 1.6f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
