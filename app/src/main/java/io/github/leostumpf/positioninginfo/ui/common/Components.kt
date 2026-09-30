// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.toggleable
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.DataStyle
import io.github.leostumpf.positioninginfo.ui.theme.HeroStyle
import io.github.leostumpf.positioninginfo.ui.theme.OverlineStyle
import io.github.leostumpf.positioninginfo.ui.theme.PageTitleStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.TileValueStyle
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

@Composable
fun PageHeader(page: Page, onHelp: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    val title: @Composable () -> Unit = {
        Text(page.number, style = OverlineStyle, color = Palette.TextTertiary)
        Text(page.title, style = PageTitleStyle, color = Palette.TextPrimary, modifier = Modifier.semantics { heading() })
    }
    val buttons: @Composable RowScope.() -> Unit = {
        actions()
        CircleIconButton(AppIcons.Help, contentDescription = "What am I looking at?", onClick = onHelp)
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        // With large text or on a narrow screen the buttons would squeeze the title into a
        // column a few letters wide, so they move above it instead.
        val stacked = LocalDensity.current.fontScale > 1.3f || maxWidth < 320.dp
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), content = buttons)
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
                    Text("What am I looking at?", style = TitleStyle.copy(fontSize = 20.sp), color = Palette.TextPrimary)
                }
            }
            items(page.glossary) { entry ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(entry.term, style = BodyStyle.copy(fontWeight = FontWeight.Medium), color = Palette.TextPrimary)
                    Text(entry.meaning, style = BodyStyle.copy(fontSize = 14.sp, lineHeight = 21.sp), color = Palette.TextSecondary)
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

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    trailingColor: Color = Palette.TextTertiary,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title.uppercase(Locale.ROOT), style = OverlineStyle, color = Palette.TextTertiary, modifier = Modifier.semantics { heading() })
        Box(Modifier.weight(1f).height(1.dp).background(Palette.Hairline))
        trailing?.let { Text(it, style = OverlineStyle.copy(fontWeight = FontWeight.Normal), color = trailingColor) }
    }
}


// --- Values ----------------------------------------------------------------------------

/** A page's main reading: big value, lighter unit, caption. */
@Composable
fun HeroValue(value: String, unit: String? = null, caption: String? = null, valueColor: Color = Palette.TextPrimary) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = valueColor)) { append(value) }
                unit?.let { withStyle(SpanStyle(fontSize = 24.sp, color = Palette.TextSecondary)) { append(" $it") } }
            },
            style = HeroStyle,
        )
        caption?.let { Text(it, style = CaptionStyle, color = Palette.TextTertiary) }
    }
}

/** A label on the left, a monospaced value on the right, an optional detail line. */
@Composable
fun ValueRow(
    label: String,
    value: String,
    detail: String? = null,
    valueColor: Color? = null,
    divider: Boolean = true,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = BodyStyle, color = Palette.TextSecondary)
                detail?.let { Text(it, style = CaptionStyle, color = Palette.TextTertiary) }
            }
            // Capped so a long value (a chipset name) wraps instead of squeezing the label.
            Text(
                value,
                style = DataStyle,
                textAlign = TextAlign.End,
                color = valueColor ?: Palette.TextPrimary,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
        if (divider) HorizontalDivider(color = Palette.Divider)
    }
}

/** A small tile in a grid of figures. */
/**
 * Stat tiles side by side, as many as fit: all in one row normally, two per row when the
 * system font is large, so a label such as "ephemeris" is never broken mid-word.
 */
@Composable
fun TileRow(tiles: List<@Composable (Modifier) -> Unit>, modifier: Modifier = Modifier, spacing: Dp = 8.dp) {
    val perRow = if (LocalDensity.current.fontScale > 1.3f && tiles.size > 2) 2 else tiles.size.coerceAtLeast(1)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        tiles.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { tile -> tile(Modifier.weight(1f)) }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    footnote: String? = null,
    tone: Tone? = null,
) {
    val accent = tone?.color
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(accent?.copy(alpha = 0.10f) ?: Palette.Surface)
            .border(1.dp, accent?.copy(alpha = 0.35f) ?: Palette.CardBorder, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = CaptionStyle.copy(fontSize = 12.sp), color = Palette.TextTertiary)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = accent ?: Palette.TextPrimary)) { append(value) }
                unit?.let { withStyle(SpanStyle(fontSize = 15.sp, color = Palette.TextSecondary)) { append(" $it") } }
            },
            style = TileValueStyle,
            maxLines = 1,
        )
        footnote?.let { Text(it, style = OverlineStyle.copy(fontSize = 10.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp), color = Palette.Inactive) }
    }
}

// --- Containers ------------------------------------------------------------------------

@Composable
fun InfoCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Surface)
            .border(1.dp, Palette.CardBorder, RoundedCornerShape(16.dp))
            .padding(padding),
        content = content,
    )
}

enum class Tone(val color: Color) {
    GOOD(Palette.Good),
    DEGRADED(Palette.Degraded),
    BAD(Palette.Bad),
    NEUTRAL(Palette.TextTertiary),
}

/** A short explanatory box; tinted when it reports a state, dashed when it only explains. */
@Composable
fun Notice(text: String, tone: Tone = Tone.NEUTRAL) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .tinted(tone, shape, neutralBorder = Palette.Outline)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.padding(top = 7.dp).size(6.dp).background(tone.color, CircleShape))
        Text(text, style = CaptionStyle, color = if (tone == Tone.NEUTRAL) Palette.TextSecondary else Palette.TextPrimary)
    }
}

/**
 * A card or badge tinted by its tone: plain surface and border when neutral, a wash of the
 * tone's colour otherwise. Clips to [shape].
 */
fun Modifier.tinted(
    tone: Tone,
    shape: Shape,
    neutralFill: Color = Palette.Surface,
    neutralBorder: Color = Palette.CardBorder,
    fillAlpha: Float = 0.08f,
    borderAlpha: Float = 0.3f,
): Modifier = clip(shape)
    .background(if (tone == Tone.NEUTRAL) neutralFill else tone.color.copy(alpha = fillAlpha))
    .border(1.dp, if (tone == Tone.NEUTRAL) neutralBorder else tone.color.copy(alpha = borderAlpha), shape)

/** Title (and whatever goes under it) on the left, a close button on the right: every sheet's top. */
@Composable
fun SheetHeader(onClose: () -> Unit, modifier: Modifier = Modifier, title: @Composable ColumnScope.() -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp), content = title)
        CircleIconButton(AppIcons.Close, contentDescription = "Close", onClick = onClose)
    }
}

/** Asks before something that cannot be undone; the confirming action is shown in red. */
@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Sheet,
        title = { Text(title) },
        text = { Text(text, color = Palette.TextSecondary) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirmLabel, color = Palette.Bad) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.TextPrimary) } },
    )
}

/** Plain explanatory text under a section. */
@Composable
fun Note(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextTertiary) {
    Text(text, style = CaptionStyle, color = color, modifier = modifier)
}

@Composable
fun StatusBadge(text: String, tone: Tone) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        Modifier
            .heightIn(min = 26.dp)
            .tinted(tone, shape, Palette.SurfaceRaised, Palette.Outline, fillAlpha = 0.14f, borderAlpha = 0.4f)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(tone.color, CircleShape))
        Text(
            text.uppercase(Locale.ROOT),
            style = OverlineStyle.copy(letterSpacing = 0.06.em()),
            color = if (tone == Tone.NEUTRAL) Palette.TextSecondary else tone.color,
        )
    }
}

private fun Double.em() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Em)

// --- Controls --------------------------------------------------------------------------

private val ButtonShape = RoundedCornerShape(12.dp)
private val ButtonHeight = 48.dp

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(ButtonHeight),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Palette.TextPrimary,
            contentColor = Palette.Background,
            disabledContainerColor = Palette.SurfaceRaised,
            disabledContentColor = Palette.Inactive,
        ),
    ) { Text(text, style = BodyStyle.copy(fontWeight = FontWeight.Medium)) }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(ButtonHeight),
        shape = ButtonShape,
        border = BorderStroke(1.dp, if (enabled) Palette.Outline else Palette.Hairline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.TextPrimary, disabledContentColor = Palette.Inactive),
    ) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = BodyStyle.copy(fontWeight = FontWeight.Medium))
    }
}

/** A text-only action; red when it destroys something. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, destructive: Boolean = false) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(ButtonHeight),
        shape = ButtonShape,
        contentPadding = PaddingValues(horizontal = 0.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = if (destructive) Palette.Bad else Palette.TextSecondary),
    ) { Text(text, style = BodyStyle.copy(fontWeight = FontWeight.Medium)) }
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 48.dp,
    tint: Color = Palette.TextSecondary,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size).border(1.dp, Palette.Outline, CircleShape),
    ) { Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp)) }
}

/** Mutually exclusive views of the same thing. */
@Composable
fun SegmentedToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(Palette.SurfaceRaised).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            TextButton(
                onClick = { onSelect(index) },
                modifier = Modifier.heightIn(min = 48.dp).semantics { this.selected = on },
                shape = RoundedCornerShape(9.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (on) Palette.TextPrimary else Color.Transparent,
                    contentColor = if (on) Palette.Background else Palette.TextSecondary,
                ),
            ) { Text(label, style = BodyStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium)) }
        }
    }
}

/** A setting with consequences, explained in its subtitle. */
@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.Surface)
            .border(1.dp, Palette.CardBorder, shape)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = BodyStyle, color = Palette.TextPrimary)
            Text(subtitle, style = CaptionStyle, color = Palette.TextTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.Background,
                checkedTrackColor = Palette.Good,
                uncheckedThumbColor = Palette.TextTertiary,
                uncheckedTrackColor = Palette.SurfaceRaised,
                uncheckedBorderColor = Palette.Outline,
            ),
        )
    }
}

/** A horizontal level bar, e.g. signal strength. */
@Composable
fun LevelBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    // Decorative: the value it shows is always written next to it.
    Box(modifier.clearAndSetSemantics { }.height(6.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Divider)) {
        if (fraction > 0f) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color, RoundedCornerShape(3.dp)))
        }
    }
}

// --- Page indicator --------------------------------------------------------------------

/** Tick marks with the neighbouring pages named, so the other tools are discoverable. */
@Composable
fun PageIndicator(current: Int, modifier: Modifier = Modifier) {
    val pages = Page.entries
    Row(
        modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            pages.getOrNull(current - 1)?.shortName.orEmpty(),
            style = CaptionStyle.copy(fontSize = 12.sp),
            color = Palette.TextTertiary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(72.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            pages.indices.forEach { i ->
                Box(
                    Modifier.size(width = if (i == current) 22.dp else 8.dp, height = 3.dp)
                        .background(if (i == current) Palette.TextPrimary else Palette.Outline, RoundedCornerShape(2.dp)),
                )
            }
        }
        Text(
            pages.getOrNull(current + 1)?.shortName.orEmpty(),
            style = CaptionStyle.copy(fontSize = 12.sp),
            color = Palette.TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(72.dp),
        )
    }
}

// --- Formatting ------------------------------------------------------------------------

const val DASH = "—"

fun Double.fmt(decimals: Int): String = String.format(Locale.US, "%.${decimals}f", this)
fun Float.fmt(decimals: Int): String = toDouble().fmt(decimals)

/** A count with a narrow space between thousands, "12 345", the same on every phone. */
fun Long.grouped(): String = String.format(Locale.US, "%,d", this).replace(',', '\u202F')
fun Int.grouped(): String = toLong().grouped()

/**
 * Date and time for a log line: English like the rest of the app, but on the phone's 12- or
 * 24-hour clock.
 */
@Composable
fun rememberLogTimeFormat(): SimpleDateFormat {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(is24Hour) { SimpleDateFormat(if (is24Hour) "EEE d MMM, HH:mm" else "EEE d MMM, h:mm a", Locale.US) }
}

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
