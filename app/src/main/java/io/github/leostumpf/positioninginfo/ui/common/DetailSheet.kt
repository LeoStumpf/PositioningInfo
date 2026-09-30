// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.TitleStyle

/**
 * Everything known about one item — a cell tower, an access point — in a sheet whose text
 * can be selected and copied, with one button to copy it all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(
    title: String,
    subtitle: String,
    rows: List<DetailRow>,
    onDismiss: () -> Unit,
    titleColor: Color = Palette.TextPrimary,
    intro: String? = null,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        LazyColumn(contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 32.dp)) {
            item {
                SheetHeader(onDismiss, Modifier.padding(bottom = 8.dp)) {
                    SelectionContainer { Text(title, style = TitleStyle.copy(fontSize = 20.sp), color = titleColor) }
                    Text(subtitle, style = CaptionStyle, color = Palette.TextSecondary)
                }
            }
            intro?.let { item { Note(it, modifier = Modifier.padding(bottom = 8.dp)) } }
            items(rows) { row ->
                SelectionContainer { ValueRow(row.label, row.value, detail = row.explanation) }
            }
            item {
                SecondaryButton(
                    "Copy all",
                    icon = AppIcons.Copy,
                    onClick = {
                        copyText(
                            context,
                            (listOf("$title — $subtitle") + rows.map { "${it.label}: ${it.value}" }).joinToString("\n"),
                        )
                    },
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            item { Note("Long-press any value to select part of it.", modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

/** Copies [text]; Android 13+ confirms on its own, older versions get a short toast. */
fun copyText(context: android.content.Context, text: String, what: String = "Copied") {
    context.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText(what, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, what, Toast.LENGTH_SHORT).show()
}
