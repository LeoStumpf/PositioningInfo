// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.leostumpf.gpstools.ui.theme.DimGrey
import de.leostumpf.gpstools.ui.theme.StatusLineStyle
import java.util.Locale

/** Building blocks shared by the detail pages, so they read as one app. */

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = StatusLineStyle,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun Note(text: String, color: Color = DimGrey) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

/** A label on the left and a value on the right, with an optional grey detail line. */
@Composable
fun ValueRow(label: String, value: String, detail: String? = null, valueColor: Color? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = StatusLineStyle,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                color = valueColor ?: MaterialTheme.colorScheme.onBackground,
            )
        }
        if (detail != null) {
            Text(text = detail, style = StatusLineStyle, color = DimGrey)
        }
    }
}

const val DASH = "—"

fun Double.fmt(decimals: Int): String = String.format(Locale.US, "%.${decimals}f", this)
fun Float.fmt(decimals: Int): String = toDouble().fmt(decimals)

/** "12.3 m", or "1.2 km" beyond a kilometre. */
fun metres(value: Double?, decimals: Int = 1): String = when {
    value == null -> DASH
    kotlin.math.abs(value) >= 1_000 -> "${(value / 1_000).fmt(2)} km"
    else -> "${value.fmt(decimals)} m"
}

/** "1:02:03" or "4:05". */
fun duration(ms: Long): String {
    val s = ms / 1_000
    return if (s >= 3_600) String.format(Locale.US, "%d:%02d:%02d", s / 3_600, s / 60 % 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}
