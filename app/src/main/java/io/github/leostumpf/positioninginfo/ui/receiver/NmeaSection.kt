// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.receiver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.ui.common.DASH
import io.github.leostumpf.positioninginfo.ui.common.Note
import io.github.leostumpf.positioninginfo.ui.common.StatTile
import io.github.leostumpf.positioninginfo.ui.common.TileRow
import io.github.leostumpf.positioninginfo.ui.common.ValueRow
import io.github.leostumpf.positioninginfo.ui.common.fmt
import io.github.leostumpf.positioninginfo.ui.common.grouped
import io.github.leostumpf.positioninginfo.ui.common.section
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.DataStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.StatusLineStyle
import kotlin.math.roundToInt

/* The chip's NMEA output: fix type, DOP, error ellipse and the sentence mix. */

/** What the chip reports in its own NMEA sentences. */
internal fun LazyListScope.nmeaSection(state: ReceiverUiState) {
    section("NMEA from the chip")
    val n = state.nmea
    if (n.total == 0) {
        item { Note("No NMEA sentences received yet.") }
    } else {
        item {
            TileRow(
                listOf(
                    { m ->
                        StatTile(
                            "Fix",
                            n.gsa?.fixType?.let(GSA_FIX_TYPES::get) ?: DASH,
                            m,
                            footnote = n.gga?.fixQuality?.let { "quality $it · ${ggaQuality(it)}" },
                        )
                    },
                    { m ->
                        StatTile("Satellites", n.gga?.satellites?.toString() ?: DASH, m, footnote = "in solution")
                    },
                    { m ->
                        StatTile(
                            "Chip DOP",
                            n.gsa?.pdop?.fmt(1) ?: DASH,
                            m,
                            footnote = n.gsa?.let { "H ${it.hdop?.fmt(1) ?: DASH} · V ${it.vdop?.fmt(1) ?: DASH}" },
                        )
                    },
                ),
            )
        }
        n.gst?.let { gst ->
            item {
                ValueRow(
                    "Error ellipse, 1σ",
                    if (gst.semiMajorM != null && gst.semiMinorM != null) {
                        "${gst.semiMajorM.fmt(1)} × ${gst.semiMinorM.fmt(1)} m"
                    } else {
                        DASH
                    },
                    detail = listOfNotNull(
                        gst.orientationDeg?.let { "major axis ${it.roundToInt()}°" },
                        gst.altSigmaM?.let { "height σ ${it.fmt(1)} m" },
                    ).joinToString(" · ").ifEmpty { null },
                )
            }
        }
        item { SentenceMix(n.counts, n.total, n.rejected) }
    }
}

/** The mix of sentence types as one bar in shades of grey, with the counts below. */
@Composable
private fun SentenceMix(counts: Map<String, Int>, total: Int, rejected: Int) {
    val sorted = counts.entries.sortedByDescending { it.value }
    val shades = listOf(
        Palette.TextPrimary,
        Palette.TextSecondary,
        Palette.TextTertiary,
        Palette.Inactive,
        Palette.Faint,
        Palette.Outline,
    )
    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text("Sentences", style = BodyStyle, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
            Text(
                total.grouped() + if (rejected > 0) " ($rejected bad)" else "",
                style = DataStyle,
                color = Palette.TextPrimary,
            )
        }
        Row(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val sum = sorted.sumOf { it.value }.coerceAtLeast(1)
            sorted.forEachIndexed { i, e ->
                Box(
                    Modifier.weight(
                        e.value.toFloat() / sum,
                    ).fillMaxHeight().background(shades[i.coerceAtMost(shades.lastIndex)]),
                )
            }
        }
        Text(
            sorted.joinToString(" · ") { "${it.key} ${it.value}" },
            style = StatusLineStyle.copy(fontSize = 11.sp),
            color = Palette.TextTertiary,
        )
    }
}

/** What the GGA fix-quality digit means (NMEA 0183). */
private fun ggaQuality(q: Int) = GGA_QUALITIES[q] ?: "other"

private val GGA_QUALITIES = mapOf(
    0 to "no fix",
    1 to "GNSS",
    2 to "SBAS",
    4 to "RTK fixed",
    5 to "RTK float",
    6 to "estimated",
)

/** GSA's fix mode (NMEA 0183): 1 no fix, 2 two-dimensional, 3 three-dimensional. */
private val GSA_FIX_TYPES = mapOf(1 to "none", 2 to "2D", 3 to "3D")
