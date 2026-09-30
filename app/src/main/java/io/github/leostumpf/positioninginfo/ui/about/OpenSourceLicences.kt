// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.ui.common.Gutter
import io.github.leostumpf.positioninginfo.ui.common.Note
import io.github.leostumpf.positioninginfo.ui.common.SheetHeader
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.DataStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.TitleStyle

/** A licence shipped with the app, as a text file under assets/licenses. */
private enum class Licence(val label: String, val short: String, val asset: String) {
    APACHE("Apache License 2.0", "Apache 2.0", "licenses/Apache-2.0.txt"),
    BSD3("BSD 3-Clause License", "BSD-3", "licenses/protobuf-BSD-3-Clause.txt"),
}

private data class Library(val name: String, val parts: String, val copyright: String, val licence: Licence)

/** Every third-party project inside the APK; the per-component list is THIRD_PARTY_NOTICES.md. */
private val LIBRARIES = listOf(
    Library(
        "AndroidX / Jetpack",
        "Compose UI, foundation, animation, runtime and Material 3; Activity, Lifecycle, SavedState, " +
            "DataStore, Core, Collection, Annotation and other support libraries",
        "© The Android Open Source Project",
        Licence.APACHE,
    ),
    Library(
        "Kotlin standard library",
        "the language runtime",
        "© JetBrains s.r.o. and Kotlin Programming Language contributors",
        Licence.APACHE,
    ),
    Library(
        "kotlinx.coroutines",
        "background work and live data streams",
        "© JetBrains s.r.o. and contributors",
        Licence.APACHE,
    ),
    Library("kotlinx.serialization", "used inside AndroidX", "© JetBrains s.r.o. and contributors", Licence.APACHE),
    Library("JetBrains Java Annotations", "code annotations", "© JetBrains s.r.o.", Licence.APACHE),
    Library("Okio", "file access for DataStore", "© Square, Inc.", Licence.APACHE),
    Library("Guava ListenableFuture", "a single interface used by AndroidX", "© The Guava Authors", Licence.APACHE),
    Library("JSpecify", "nullness annotations", "© The JSpecify Authors", Licence.APACHE),
    Library("Protocol Buffers", "repackaged inside AndroidX DataStore", "© Google Inc.", Licence.BSD3),
)

/** The libraries the app is built on, their licences, and the full licence texts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicencesSheet(onDismiss: () -> Unit) {
    var shown by rememberSaveable { mutableStateOf<Licence?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        LazyColumn(contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 32.dp)) {
            item {
                SheetHeader(onDismiss, Modifier.padding(bottom = 8.dp)) {
                    Text("Open-source licences", style = TitleStyle.copy(fontSize = 20.sp), color = Palette.TextPrimary)
                    Text("The libraries this app is built on", style = CaptionStyle, color = Palette.TextSecondary)
                }
            }
            item {
                Note(
                    "Positioning Info itself is licensed under the GNU AGPL, version 3 or later. It includes the " +
                        "following open-source libraries, all under permissive licences. Tap one to read its licence.",
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            items(LIBRARIES) { lib ->
                Column(
                    Modifier.fillMaxWidth().clickable(
                        onClickLabel = "Show the ${lib.licence.label}",
                    ) { shown = lib.licence }
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row {
                        Text(lib.name, style = BodyStyle, color = Palette.TextPrimary, modifier = Modifier.weight(1f))
                        Text(lib.licence.short, style = DataStyle.copy(fontSize = 12.sp), color = Palette.TextSecondary)
                    }
                    Text(lib.parts, style = CaptionStyle.copy(fontSize = 12.sp), color = Palette.TextTertiary)
                    Text(lib.copyright, style = CaptionStyle.copy(fontSize = 12.sp), color = Palette.TextTertiary)
                }
                HorizontalDivider(color = Palette.CardBorder)
            }
        }
    }
    shown?.let { LicenceTextDialog(it) { shown = null } }
}

@Composable
private fun LicenceTextDialog(licence: Licence, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember(licence) {
        runCatching {
            context.assets.open(
                licence.asset,
            ).bufferedReader().use { reflow(it.readText()) }
        }.getOrDefault(licence.label)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        title = { Text(licence.label) },
        text = {
            SelectionContainer {
                Text(
                    text,
                    style = CaptionStyle.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = Palette.TextSecondary,
                    modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Palette.TextPrimary) } },
    )
}

/**
 * Licence files are hard-wrapped at 80 columns with centred headings, which wraps badly on a
 * phone. Joins each paragraph into one line and drops the indentation; the wording is unchanged.
 */
private fun reflow(text: String): String = text.replace("\r", "").split(Regex("\n\\s*\n"))
    .map { para -> para.lines().joinToString(" ") { it.trim() }.replace(Regex(" {2,}"), " ").trim() }
    .filter { it.isNotEmpty() }
    .joinToString("\n\n")
