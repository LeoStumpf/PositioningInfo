// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.receiver

import io.github.leostumpf.positioninginfo.domain.SpoofingIndicator
import io.github.leostumpf.positioninginfo.ui.common.tinted
import io.github.leostumpf.positioninginfo.domain.InterferenceMonitor
import io.github.leostumpf.positioninginfo.domain.counted
import java.util.Locale
import io.github.leostumpf.positioninginfo.ui.common.grouped
import io.github.leostumpf.positioninginfo.ui.common.TileRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.leostumpf.positioninginfo.data.model.RawStreamStatus
import io.github.leostumpf.positioninginfo.domain.Band
import io.github.leostumpf.positioninginfo.domain.BandStatus
import io.github.leostumpf.positioninginfo.domain.InterferenceAssessment
import io.github.leostumpf.positioninginfo.ui.common.AppIcons
import io.github.leostumpf.positioninginfo.ui.common.DASH
import io.github.leostumpf.positioninginfo.ui.common.Note
import io.github.leostumpf.positioninginfo.ui.common.Notice
import io.github.leostumpf.positioninginfo.ui.common.Page
import io.github.leostumpf.positioninginfo.ui.common.PageScaffold
import io.github.leostumpf.positioninginfo.ui.common.StatTile
import io.github.leostumpf.positioninginfo.ui.common.Tone
import io.github.leostumpf.positioninginfo.ui.common.ValueRow
import io.github.leostumpf.positioninginfo.ui.common.fmt
import io.github.leostumpf.positioninginfo.ui.common.section
import io.github.leostumpf.positioninginfo.ui.theme.BodyStyle
import io.github.leostumpf.positioninginfo.ui.theme.CaptionStyle
import io.github.leostumpf.positioninginfo.ui.theme.DataStyle
import io.github.leostumpf.positioninginfo.ui.theme.OverlineStyle
import io.github.leostumpf.positioninginfo.ui.theme.Palette
import io.github.leostumpf.positioninginfo.ui.theme.StatusLineStyle
import io.github.leostumpf.positioninginfo.ui.theme.TitleStyle
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the chip reports below the position: interference, its clock, its NMEA output, the
 * satellites' own messages, and what the hardware supports.
 */
@Composable
fun ReceiverScreen(state: ReceiverUiState, modifier: Modifier = Modifier) {
    val a = state.assessment
    PageScaffold(Page.RECEIVER, modifier) {
        section("Interference")
        if (a == null || (state.rawStatus != RawStreamStatus.READY && state.rawEpochs == 0)) {
            item { Notice(rawStatusText(state.rawStatus, "raw measurements")) }
        } else {
            item { Verdict(a) }
            if (a.bands.any { it.signals > 0 }) {
                item { BandHeader() }
                a.bands.filter { it.signals > 0 || it.band != Band.OTHER }.forEach { b -> item { BandRow(b) } }
            }
            item { ValueRow("Multipath flagged", "${a.multipathSignals} of ${a.totalSignals}", divider = false) }
        }

        section("Receiver clock")
        if (a == null) {
            item { Note(rawStatusText(state.rawStatus, "raw measurements")) }
        } else {
            item {
                ValueRow(
                    "Oscillator error",
                    a.clockDriftPpm?.let { "${it.fmt(3).replace("-", "−")} ppm" } ?: DASH,
                    detail = a.clockDriftStdDevPpm?.let { "±${it.fmt(4)} ppm over the last minute" },
                )
            }
            item { ValueRow("Leap seconds GPS − UTC", a.leapSecond?.let { "$it s" } ?: DASH) }
            item {
                ValueRow("GNSS time", when (state.hasFullBias) { true -> "absolute"; false -> "relative only"; null -> DASH })
            }
            item { ValueRow("Clock discontinuities", a.clockDiscontinuities.toString()) }
            item { ValueRow("Carrier phase valid", signals(state.carrierPhaseValid), divider = false) }
        }

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
                                "Fix", when (n.gsa?.fixType) { 3 -> "3D"; 2 -> "2D"; 1 -> "none"; else -> DASH }, m,
                                footnote = n.gga?.fixQuality?.let { "quality $it · ${ggaQuality(it)}" },
                            )
                        },
                        { m -> StatTile("Satellites", n.gga?.satellites?.toString() ?: DASH, m, footnote = "in solution") },
                        { m ->
                            StatTile(
                                "Chip DOP", n.gsa?.pdop?.fmt(1) ?: DASH, m,
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
                        if (gst.semiMajorM != null && gst.semiMinorM != null) "${gst.semiMajorM.fmt(1)} × ${gst.semiMinorM.fmt(1)} m" else DASH,
                        detail = listOfNotNull(
                            gst.orientationDeg?.let { "major axis ${it.roundToInt()}°" },
                            gst.altSigmaM?.let { "height σ ${it.fmt(1)} m" },
                        ).joinToString(" · ").ifEmpty { null },
                    )
                }
            }
            item { SentenceMix(n.counts, n.total, n.rejected) }
        }

        section("Navigation messages")
        if (state.navFrames.isEmpty()) {
            item {
                Notice(
                    if (state.navStatus == RawStreamStatus.UNKNOWN && state.navSilentMs > NAV_PATIENCE_MS) {
                        "Nothing received after ${state.navSilentMs / 1_000} s. This chip doesn't pass the " +
                            "satellites' broadcast messages on to Android — many phone chips don't."
                    } else {
                        rawStatusText(state.navStatus, "navigation messages")
                    },
                )
            }
        } else {
            val gps = state.gps
            item {
                ValueRow(
                    "Frames received", state.navFrames.values.sum().toString(),
                    detail = state.navFrames.entries.sortedByDescending { it.value }.joinToString(" · ") { "${it.key} ${it.value}" },
                )
            }
            item {
                ValueRow(
                    "GPS subframes decoded", gps.subframesDecoded.toString(),
                    detail = if (gps.subframesRejected > 0) "${gps.subframesRejected} failed parity" else null,
                )
            }
            item {
                ValueRow(
                    "GPS week",
                    gps.weekNumber?.let { wn -> fullWeek(wn, state.currentGpsWeek)?.let { "$it" } ?: "$wn mod 1024" } ?: DASH,
                    detail = gps.weekNumber?.let { "broadcast as $it (10 bits)" },
                )
            }
            item {
                val unhealthy = gps.health.filterValues { it != 0 }.keys.sorted()
                ValueRow(
                    "Satellite health",
                    when {
                        gps.health.isEmpty() -> DASH
                        unhealthy.isEmpty() -> "all ${gps.health.size} healthy"
                        else -> unhealthy.joinToString { "G%02d".format(Locale.US, it) }
                    },
                    valueColor = if (unhealthy.isNotEmpty()) Palette.Degraded else null,
                )
            }
            item { ValueRow("Almanac pages", "${gps.almanacSvids.size} of 32") }
            gps.utc?.let { utc ->
                item {
                    ValueRow(
                        "Leap seconds broadcast", "${utc.deltaTls} s",
                        detail = if (utc.leapSecondPending) "change to ${utc.deltaTlsf} s announced (week ${utc.wnLsf} mod 256, day ${utc.dn})" else "none announced",
                        valueColor = if (utc.leapSecondPending) Palette.Degraded else null,
                    )
                }
                item { ValueRow("GPS − UTC offset", "A0 ${(utc.a0 * 1e9).fmt(2)} ns", detail = "A1 ${(utc.a1 * 1e15).fmt(3)} fs/s") }
            }
            gps.ionosphere?.let { k ->
                item {
                    ValueRow(
                        "Ionosphere model", "received",
                        detail = "α " + k.alpha.joinToString(" ") { "%.2e".format(Locale.US, it) } + " · β " + k.beta.joinToString(" ") { "%.2e".format(Locale.US, it) },
                        divider = false,
                    )
                }
            }
        }

        section("Hardware")
        val caps = state.capabilities
        caps.hardwareModel?.let { item { ValueRow("Chipset", it.replace(';', ' ').trim()) } }
        caps.hardwareYear?.let { item { ValueRow("Hardware generation", it.toString()) } }
        if (!caps.reported) {
            item { Note("Android 12 and later report which services the receiver supports; this device is older.", modifier = Modifier.padding(top = 6.dp)) }
        } else {
            item { Capability("Raw measurements", caps.rawMeasurements) }
            item { Capability("Navigation messages", caps.navigationMessages) }
            item { Capability("Antenna corrections", caps.antennaInfo, last = !caps.assistanceReported) }
            if (caps.assistanceReported) {
                item { Capability("A-GNSS, network computes", caps.assistedMsa) }
                item { Capability("A-GNSS, phone computes", caps.assistedMsb) }
                item { Capability("Time injection", caps.onDemandTime) }
                item { Capability("Measurement corrections", caps.measurementCorrections) }
                item { Capability("Carrier phase tracking", caps.carrierPhase) }
                item { Capability("Satellite positions from the chip", caps.satellitePvt) }
                item { Capability("Satellite blocklist", caps.satelliteBlocklist) }
                item { Capability("Low-power mode", caps.lowPowerMode) }
                item { Capability("Geofencing on the chip", caps.geofencing) }
                item { Capability("Own fix scheduling", caps.scheduling) }
                item { Capability("Single-shot fix", caps.singleShotFix) }
                item { Capability("Correlation vectors", caps.correlationVectors) }
                item { Capability("Power statistics", caps.powerStats) }
                item {
                    ValueRow(
                        "Path corrections accepted", caps.correctionKinds.joinToString().ifEmpty { "none" },
                        valueColor = if (caps.correctionKinds.isEmpty()) Palette.TextTertiary else Palette.Good,
                        divider = false,
                    )
                }
            } else {
                item {
                    Note(
                        "Android reports the assistance services (A-GNSS, time injection, corrections) and the chip's " +
                            "other features only from version 14; this phone almost certainly uses assisted GNSS but cannot say so.",
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Verdict(a: InterferenceAssessment) {
    val (title, subtitle, tone) = when {
        a.jammingSuspected -> Triple("Possible jamming", "Gain and signal strength dropped together", Tone.BAD)
        a.spoofingIndicators.isNotEmpty() -> Triple("Spoofing indicators", a.spoofingIndicators.first().describe(), Tone.DEGRADED)
        a.epochs < BASELINE_EPOCHS -> Triple("Learning the baseline…", "Judged after the first minute", Tone.NEUTRAL)
        else -> Triple("No interference detected", "Gain and signal strength match the baseline", Tone.GOOD)
    }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().tinted(tone, shape).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (tone == Tone.GOOD || tone == Tone.NEUTRAL) AppIcons.ShieldCheck else AppIcons.ShieldAlert,
            contentDescription = null, tint = tone.color, modifier = Modifier.size(28.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = TitleStyle, color = Palette.TextPrimary)
            Text(subtitle, style = CaptionStyle, color = Palette.TextSecondary)
        }
    }
    a.spoofingIndicators.drop(1).forEach { Note("• ${it.describe()}", Modifier.padding(top = 6.dp), color = Palette.Degraded) }
}

private val BandWeights = listOf(1.4f, 1f, 1f, 0.6f)

@Composable
private fun BandHeader() {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)) {
        listOf("BAND", "AGC dB", "C/N₀", "SIG").forEachIndexed { i, h ->
            Text(h, style = OverlineStyle.copy(fontWeight = FontWeight.Normal), color = Palette.TextTertiary,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End, modifier = Modifier.weight(BandWeights[i]))
        }
    }
}

@Composable
private fun BandRow(b: BandStatus) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                b.band.label(),
                style = BodyStyle.copy(fontSize = 13.sp), color = Palette.TextPrimary, modifier = Modifier.weight(BandWeights[0]),
            )
            ValueWithDelta(b.agcDb, b.agcDropDb?.let { -it }, alarm = (b.agcDropDb ?: 0.0) >= 6.0, modifier = Modifier.weight(BandWeights[1]))
            ValueWithDelta(
                b.meanCn0DbHz,
                if (b.meanCn0DbHz != null && b.cn0BaselineDbHz != null) b.meanCn0DbHz - b.cn0BaselineDbHz else null,
                alarm = false, modifier = Modifier.weight(BandWeights[2]),
            )
            Text(b.signals.toString(), style = DataStyle, color = Palette.TextPrimary, textAlign = TextAlign.End, modifier = Modifier.weight(BandWeights[3]))
        }
        HorizontalDivider(color = Palette.Divider)
    }
}

/** A value with its change against the baseline in small type: "−58.6 +0.1". */
@Composable
private fun ValueWithDelta(value: Double?, delta: Double?, alarm: Boolean, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
        Text(value?.fmt(1)?.replace("-", "−") ?: DASH, style = DataStyle, color = if (alarm) Palette.Bad else Palette.TextPrimary)
        delta?.let {
            Text(" ${if (it >= 0) "+" else "−"}${abs(it).fmt(1)}", style = DataStyle.copy(fontSize = 10.sp), color = Palette.TextTertiary)
        }
    }
}

/** The mix of sentence types as one bar in shades of grey, with the counts below. */
@Composable
private fun SentenceMix(counts: Map<String, Int>, total: Int, rejected: Int) {
    val sorted = counts.entries.sortedByDescending { it.value }
    val shades = listOf(Palette.TextPrimary, Palette.TextSecondary, Palette.TextTertiary, Palette.Inactive, Palette.Faint, Palette.Outline)
    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text("Sentences", style = BodyStyle, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
            Text(total.grouped() + if (rejected > 0) " ($rejected bad)" else "", style = DataStyle, color = Palette.TextPrimary)
        }
        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val sum = sorted.sumOf { it.value }.coerceAtLeast(1)
            sorted.forEachIndexed { i, e ->
                Box(Modifier.weight(e.value.toFloat() / sum).fillMaxHeight().background(shades[i.coerceAtMost(shades.lastIndex)]))
            }
        }
        Text(sorted.joinToString(" · ") { "${it.key} ${it.value}" }, style = StatusLineStyle.copy(fontSize = 11.sp), color = Palette.TextTertiary)
    }
}

@Composable
private fun Capability(label: String, available: Boolean, last: Boolean = false) {
    ValueRow(label, if (available) "yes" else "no", valueColor = if (available) Palette.Good else Palette.TextTertiary, divider = !last)
}

private fun signals(n: Int) = n.counted("signal")

private const val NAV_PATIENCE_MS = 90_000L

private fun rawStatusText(status: RawStreamStatus, what: String) = when (status) {
    RawStreamStatus.NOT_SUPPORTED -> "This phone does not provide $what."
    RawStreamStatus.LOCATION_DISABLED -> "Location is switched off."
    else -> "Waiting for $what from the chip…"
}

private fun ggaQuality(q: Int) = when (q) {
    0 -> "no fix"; 1 -> "GNSS"; 2 -> "SBAS"; 4 -> "RTK fixed"; 5 -> "RTK float"; 6 -> "estimated"
    else -> "other"
}

/** Resolves the 10-bit broadcast week against the phone's date: the candidate nearest to it. */
internal fun fullWeek(broadcast: Int, current: Int?): Int? {
    if (current == null) return null
    val base = current - current % 1024 + broadcast
    return listOf(base - 1024, base, base + 1024).minBy { abs(it - current) }
}

/** One epoch a second for [InterferenceMonitor.BASELINE_MS]: the baseline minute. */
private const val BASELINE_EPOCHS = (InterferenceMonitor.BASELINE_MS / 1_000).toInt()

internal fun Band.label(): String = when (this) {
    Band.L1_E1_B1 -> "L1/E1/B1"
    Band.L5_E5A_B2A -> "L5/E5a/B2a"
    Band.OTHER -> "Other"
}

/** An indicator in words: cautious, because each is a reason to look closer, not a verdict. */
internal fun SpoofingIndicator.describe(): String = when (this) {
    is SpoofingIndicator.UniformStrength ->
        "${band.label()}: $signals signals are unusually alike in strength " +
            "(%.0f dB-Hz ± %.1f). Real satellites at different elevations usually differ more; ".format(Locale.US, meanDbHz, spreadDb) +
            "one transmitter could make them alike."
    is SpoofingIndicator.PowerWithStrongerSignals ->
        "${band.label()}: more power in the band (AGC %.0f dB below baseline) ".format(Locale.US, agcDropDb) +
            "while signals got stronger. This could be a source stronger than the sky."
    is SpoofingIndicator.DriftJump ->
        "The clock drift jumped by more than %.1f ppm between epochs, which an oscillator ".format(Locale.US, thresholdPpm) +
            "rarely does on its own."
}
