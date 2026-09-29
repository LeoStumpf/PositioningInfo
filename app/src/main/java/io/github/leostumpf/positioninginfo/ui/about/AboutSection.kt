// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.BuildConfig
import io.github.leostumpf.positioninginfo.ui.common.AppIcons
import io.github.leostumpf.positioninginfo.ui.common.InfoCard
import io.github.leostumpf.positioninginfo.ui.common.QuietButton
import io.github.leostumpf.positioninginfo.ui.common.SecondaryButton
import io.github.leostumpf.positioninginfo.ui.common.ValueRow
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.DataStyle
import io.github.leostumpf.positioninginfo.ui.theme.OverlineStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.TitleStyle

const val SOURCE_URL = "https://github.com/LeoStumpf/PositioningInfo"
const val LICENSE_URL = "https://www.gnu.org/licenses/agpl-3.0.html"

/**
 * What the app is, its licence and where the source is.
 *
 * The AGPL requires that users be told the licence and where to get the corresponding
 * source; this is that notice. Links open in the browser — the app itself never connects.
 */
@Composable
fun AboutSection() {
    val uriHandler = LocalUriHandler.current
    var showFontLicence by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        InfoCard {
            Row(Modifier.fillMaxWidth()) {
                Text("Positioning Info", style = TitleStyle.copy(fontSize = 20.sp), color = Palette.TextPrimary, modifier = Modifier.weight(1f))
                Text("v${BuildConfig.VERSION_NAME}", style = DataStyle, color = Palette.TextTertiary)
            }
            Text(
                "An open-source instrument for everything a phone's GNSS receiver knows. No ads, no " +
                    "account, no tracking — and no internet access at all: every figure on these pages " +
                    "is measured or computed on this phone.",
                style = BodyStyle.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = Palette.TextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Column {
            Text("LICENCE", style = OverlineStyle, color = Palette.TextTertiary)
            Text(
                "GNU Affero General Public License, version 3 or later. You may use, study, share and " +
                    "modify this program under its terms. It comes with absolutely no warranty.",
                style = CaptionStyle,
                color = Palette.TextSecondary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton("Source on GitHub", icon = AppIcons.External, onClick = { uriHandler.openUri(SOURCE_URL) }, modifier = Modifier.weight(1f))
            SecondaryButton("AGPL-3.0", icon = AppIcons.External, onClick = { uriHandler.openUri(LICENSE_URL) })
        }
        Column {
            ValueRow("Source code", SOURCE_URL.removePrefix("https://"))
            ValueRow("Copyright", "© 2026 Leo Stumpf")
            ValueRow("Typeface", "IBM Plex · SIL OFL 1.1")
            ValueRow("Written by", "Claude (Anthropic)", detail = "under Leo Stumpf's direction — see the README", divider = false)
        }
        QuietButton("Show the font licence", onClick = { showFontLicence = true })
    }
    if (showFontLicence) FontLicenceDialog(onDismiss = { showFontLicence = false })
}

@Composable
private fun FontLicenceDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember {
        runCatching { context.assets.open("licenses/OFL-IBM-Plex.txt").bufferedReader().use { it.readText() } }
            .getOrDefault("SIL Open Font License 1.1")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        title = { Text("IBM Plex font licence") },
        text = {
            Text(
                text,
                style = CaptionStyle.copy(fontSize = 12.sp),
                color = Palette.TextSecondary,
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Palette.TextPrimary) } },
    )
}
