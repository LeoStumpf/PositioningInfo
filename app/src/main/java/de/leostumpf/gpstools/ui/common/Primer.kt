// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.leostumpf.gpstools.ui.theme.StatusLineStyle

/** One term and a reminder of what it means. */
data class PrimerEntry(val term: String, val meaning: String)

/**
 * A short glossary for the page, collapsed by default.
 *
 * Written as a reminder for a technical reader rather than a tutorial: enough to read the
 * numbers on the page correctly, out of the way once that is familiar.
 */
@Composable
fun Primer(entries: List<PrimerEntry>, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF111111), RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row {
            Text(
                text = "What am I looking at?",
                style = StatusLineStyle,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) "▾" else "▸",
                style = StatusLineStyle,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                entries.forEach { entry ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = entry.term,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = entry.meaning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
