// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.OverlineStyle
import io.github.leostumpf.positioninginfo.ui.theme.PageTitleStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.TitleStyle
import java.util.Locale

/*
 * The shared building blocks of every page, so the app reads as one instrument: the page
 * frame, section headers, values, cards, badges, buttons and switches.
 */

/** Page gutter. */
val Gutter = 20.dp

/** Widest a page's content gets, so labels and values stay within one glance on a tablet. */
val MaxContentWidth = 640.dp

/**
 * Room kept free at the bottom for the page indicator: its padding plus one line of its
 * labels, which grows with the system font size.
 */
@Composable
fun indicatorClearance(): Dp = with(LocalDensity.current) { maxOf(72.dp, 40.dp + 19.sp.toDp()) }

// --- Page frame -----------------------------------------------------------------------

/**
 * A scrolling detail page: number, title and a glossary button, then the content.
 * The glossary opens as a bottom sheet over the page instead of pushing it around.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScaffold(page: Page, modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    var glossaryOpen by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize().background(Palette.Background)) {
        // On a tablet or in landscape the rows keep a readable width, centred; the list still
        // scrolls from anywhere on the screen.
        val side = maxOf(Gutter, (maxWidth - MaxContentWidth) / 2)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = side, end = side, top = 24.dp, bottom = indicatorClearance()),
        ) {
            item { PageHeader(page, onHelp = { glossaryOpen = true }) }
            content()
        }
    }
    if (glossaryOpen) GlossarySheet(page, onDismiss = { glossaryOpen = false })
}

/**
 * A page's number and title with the glossary button and any [actions] beside it. With large text
 * or on a narrow screen the buttons move above the title.
 */
@Composable
fun PageHeader(
    page: Page,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val title: @Composable () -> Unit = {
        Text(page.number, style = OverlineStyle, color = Palette.TextTertiary)
        Text(
            page.title,
            style = PageTitleStyle,
            color = Palette.TextPrimary,
            modifier = Modifier.semantics { heading() },
        )
    }
    val buttons: @Composable RowScope.() -> Unit = {
        actions()
        CircleIconButton(AppIcons.Help, contentDescription = "What am I looking at?", onClick = onHelp)
    }
    BoxWithConstraints(modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        // With large text or on a narrow screen the buttons would squeeze the title into a
        // column a few letters wide, so they move above it instead.
        val stacked = LocalDensity.current.fontScale > 1.3f || maxWidth < 320.dp
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    content = buttons,
                )
                title()
            }
        } else {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { title() }
                buttons()
            }
        }
    }
}

/** The page's glossary as a bottom sheet: each term and what it means. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlossarySheet(page: Page, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Palette.Sheet,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                Modifier.padding(top = 12.dp, bottom = 8.dp).size(36.dp, 4.dp)
                    .background(Palette.Outline, RoundedCornerShape(2.dp)),
            )
        },
    ) {
        LazyColumn(contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 32.dp)) {
            item {
                SheetHeader(onDismiss, Modifier.padding(bottom = 8.dp)) {
                    Text(page.title.uppercase(Locale.ROOT), style = OverlineStyle, color = Palette.TextTertiary)
                    Text(
                        "What am I looking at?",
                        style = TitleStyle.copy(fontSize = 20.sp),
                        color = Palette.TextPrimary,
                    )
                }
            }
            items(page.glossary) { entry ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        entry.term,
                        style = BodyStyle.copy(fontWeight = FontWeight.Medium),
                        color = Palette.TextPrimary,
                    )
                    Text(
                        entry.meaning,
                        style = BodyStyle.copy(fontSize = 14.sp, lineHeight = 21.sp),
                        color = Palette.TextSecondary,
                    )
                }
                HorizontalDivider(color = Palette.CardBorder)
            }
        }
    }
}

/** A section's title: overline, hairline, optional trailing note. Starts a new section. */
fun LazyListScope.section(title: String, trailing: String? = null, trailingColor: Color = Palette.TextTertiary) {
    item { SectionHeader(title, Modifier.padding(top = 32.dp, bottom = 10.dp), trailing, trailingColor) }
}

/** The overline, hairline and optional trailing note of [section], for use outside a list. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    trailingColor: Color = Palette.TextTertiary,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title.uppercase(Locale.ROOT),
            style = OverlineStyle,
            color = Palette.TextTertiary,
            modifier = Modifier.semantics { heading() },
        )
        Box(Modifier.weight(1f).height(1.dp).background(Palette.Hairline))
        trailing?.let { Text(it, style = OverlineStyle.copy(fontWeight = FontWeight.Normal), color = trailingColor) }
    }
}
