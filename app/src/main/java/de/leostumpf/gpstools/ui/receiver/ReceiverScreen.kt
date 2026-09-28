// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.ui.receiver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.leostumpf.gpstools.data.model.RawStreamStatus
import de.leostumpf.gpstools.domain.Band
import de.leostumpf.gpstools.domain.BandStatus
import de.leostumpf.gpstools.domain.InterferenceAssessment
import de.leostumpf.gpstools.ui.common.DASH
import de.leostumpf.gpstools.ui.common.Glossary
import de.leostumpf.gpstools.ui.common.Note
import de.leostumpf.gpstools.ui.common.Primer
import de.leostumpf.gpstools.ui.common.SectionLabel
import de.leostumpf.gpstools.ui.common.ValueRow
import de.leostumpf.gpstools.ui.common.fmt
import de.leostumpf.gpstools.ui.theme.ErrorRed
import de.leostumpf.gpstools.ui.theme.OkGreen
import de.leostumpf.gpstools.ui.theme.WarnAmber
import kotlin.math.abs
import kotlin.math.roundToInt

/** What the chip reports below the position: interference, its clock, NMEA and the satellites' own messages. */
@Composable
fun ReceiverScreen(state: ReceiverUiState, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 48.dp),
    ) {
        item { Primer(Glossary.receiver) }
        item { Spacer(Modifier.height(20.dp)) }

        // Interference and clock both come from the raw measurements.
        item { SectionLabel("INTERFERENCE") }
        item { Spacer(Modifier.height(6.dp)) }
        val a = state.assessment
        if (a == null || state.rawStatus != RawStreamStatus.READY && state.rawEpochs == 0) {
            item { Note(rawStatusText(state.rawStatus, "raw measurements")) }
        } else {
            item { InterferenceHeadline(a) }
            item { Spacer(Modifier.height(8.dp)) }
            items(a.bands.filter { it.band != Band.OTHER || it.signals > 0 }) { BandRow(it) }
            item {
                ValueRow("Multipath flagged", "${a.multipathSignals} of ${signals(a.totalSignals)}")
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
        item { SectionLabel("RECEIVER CLOCK") }
        item { Spacer(Modifier.height(6.dp)) }
        if (a == null) {
            item { Note(rawStatusText(state.rawStatus, "raw measurements")) }
        } else {
            item {
                ValueRow(
                    "Oscillator frequency error",
                    a.clockDriftPpm?.let { "${it.fmt(3)} ppm" } ?: DASH,
                    detail = a.clockDriftStdDevPpm?.let { "varies by ±${it.fmt(4)} ppm over the last minute" },
                )
            }
            item { ValueRow("Clock discontinuities", a.clockDiscontinuities.toString()) }
            item { ValueRow("Leap seconds (GPS − UTC)", a.leapSecond?.let { "$it s" } ?: DASH) }
            item {
                ValueRow(
                    "GNSS time known",
                    when (state.hasFullBias) { true -> "absolute"; false -> "relative only"; null -> DASH },
                )
            }
            item { ValueRow("Carrier phase valid", signals(state.carrierPhaseValid)) }
        }

        item { Spacer(Modifier.height(28.dp)) }
        item { SectionLabel("NMEA FROM THE CHIP") }
        item { Spacer(Modifier.height(6.dp)) }
        val n = state.nmea
        if (n.total == 0) {
            item { Note("No NMEA sentences received yet.") }
        } else {
            item {
                ValueRow(
                    "Fix type",
                    when (n.gsa?.fixType) { 3 -> "3D"; 2 -> "2D"; 1 -> "none"; else -> DASH },
                    detail = n.gga?.fixQuality?.let { "GGA quality $it: ${ggaQuality(it)}" },
                )
            }
            item { ValueRow("Satellites (GGA)", n.gga?.satellites?.toString() ?: DASH) }
            item {
                ValueRow(
                    "DOP reported by the chip",
                    n.gsa?.let { g ->
                        listOf("P" to g.pdop, "H" to g.hdop, "V" to g.vdop)
                            .joinToString("  ") { (k, v) -> "$k ${v?.fmt(1) ?: DASH}" }
                    } ?: DASH,
                )
            }
            n.gst?.let { gst ->
                item {
                    ValueRow(
                        "Error ellipse (GST, 1σ)",
                        if (gst.semiMajorM != null && gst.semiMinorM != null) {
                            "${gst.semiMajorM.fmt(1)} × ${gst.semiMinorM.fmt(1)} m"
                        } else {
                            DASH
                        },
                        detail = listOfNotNull(
                            gst.orientationDeg?.let { "major axis at ${it.roundToInt()}°" },
                            gst.latSigmaM?.let { "σ lat ${it.fmt(1)} m" },
                            gst.lonSigmaM?.let { "σ lon ${it.fmt(1)} m" },
                            gst.altSigmaM?.let { "σ alt ${it.fmt(1)} m" },
                        ).joinToString(" · ").ifEmpty { null },
                    )
                }
            }
            item {
                ValueRow(
                    "Sentences",
                    "${n.total}${if (n.rejected > 0) " (${n.rejected} bad checksum)" else ""}",
                    detail = n.counts.entries.sortedByDescending { it.value }
                        .joinToString(" · ") { "${it.key} ${it.value}" },
                )
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
        item { SectionLabel("NAVIGATION MESSAGES") }
        item { Spacer(Modifier.height(6.dp)) }
        if (state.navFrames.isEmpty()) {
            item {
                Note(
                    if (state.navStatus == RawStreamStatus.UNKNOWN && state.navSilentMs > NAV_PATIENCE_MS) {
                        "Nothing received after ${state.navSilentMs / 1_000} s. Many phone chips do not " +
                            "pass the navigation message on to Android, and this one apparently " +
                            "does not either."
                    } else {
                        rawStatusText(state.navStatus, "navigation messages")
                    },
                )
            }
        } else {
            item {
                ValueRow(
                    "Frames received",
                    state.navFrames.values.sum().toString(),
                    detail = state.navFrames.entries.sortedByDescending { it.value }
                        .joinToString(" · ") { "${it.key} ${it.value}" },
                )
            }
            val gps = state.gps
            item {
                ValueRow(
                    "GPS subframes decoded",
                    gps.subframesDecoded.toString(),
                    detail = if (gps.subframesRejected > 0) "${gps.subframesRejected} failed parity" else null,
                )
            }
            item {
                ValueRow(
                    "GPS week",
                    gps.weekNumber?.let { wn -> fullWeek(wn, state.currentGpsWeek)?.let { "$it (broadcast $wn)" } ?: "$wn (mod 1024)" } ?: DASH,
                )
            }
            item {
                val unhealthy = gps.health.filterValues { it != 0 }.keys.sorted()
                ValueRow(
                    "Satellite health",
                    if (gps.health.isEmpty()) DASH else if (unhealthy.isEmpty()) "all ${gps.health.size} healthy" else "unhealthy: ${unhealthy.joinToString { "G%02d".format(it) }}",
                    valueColor = if (unhealthy.isNotEmpty()) WarnAmber else null,
                )
            }
            item { ValueRow("Almanac pages decoded", "${gps.almanacSvids.size} of 32") }
            gps.utc?.let { utc ->
                item {
                    ValueRow(
                        "Leap seconds broadcast",
                        "${utc.deltaTls} s",
                        detail = if (utc.leapSecondPending) {
                            "change to ${utc.deltaTlsf} s announced (week ${utc.wnLsf} mod 256, day ${utc.dn})"
                        } else {
                            "no leap second announced"
                        },
                        valueColor = if (utc.leapSecondPending) WarnAmber else null,
                    )
                }
                item {
                    ValueRow(
                        "GPS − UTC offset polynomial",
                        "A0 ${(utc.a0 * 1e9).fmt(2)} ns",
                        detail = "A1 ${(utc.a1 * 1e15).fmt(3)} fs/s",
                    )
                }
            }
            gps.ionosphere?.let { k ->
                item {
                    ValueRow(
                        "Ionosphere model (Klobuchar)",
                        "received",
                        detail = "α " + k.alpha.joinToString(" ") { "%.2e".format(it) } +
                            " · β " + k.beta.joinToString(" ") { "%.2e".format(it) },
                    )
                }
            }
            if (gps.subframesDecoded == 0) {
                item {
                    Note("GPS messages take 30 s per satellite to arrive, and the almanac 12.5 minutes in full. Keep the app open under open sky.")
                }
            }
        }
    }
}

@Composable
private fun InterferenceHeadline(a: InterferenceAssessment) {
    val (text, color) = when {
        a.jammingSuspected -> "Possible jamming" to ErrorRed
        a.spoofingIndicators.isNotEmpty() -> "Spoofing indicators present" to WarnAmber
        a.epochs < 60 -> "Learning the baseline…" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "No interference detected" to OkGreen
    }
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
    a.spoofingIndicators.forEach { Note("• $it", WarnAmber) }
}

@Composable
private fun BandRow(b: BandStatus) {
    val name = when (b.band) {
        Band.L1_E1_B1 -> "L1 / E1 / B1"
        Band.L5_E5A_B2A -> "L5 / E5a / B2a"
        Band.OTHER -> "Other"
    }
    fun delta(d: Double?) = d?.let { " (${if (it >= 0) "−" else "+"}${abs(it).fmt(1)})" } ?: ""
    ValueRow(
        name,
        signals(b.signals),
        detail = "AGC ${b.agcDb?.fmt(1) ?: DASH} dB${delta(b.agcDropDb)} · " +
            "C/N₀ ${b.meanCn0DbHz?.fmt(1) ?: DASH} dB-Hz" +
            delta(if (b.meanCn0DbHz != null && b.cn0BaselineDbHz != null) b.cn0BaselineDbHz - b.meanCn0DbHz else null),
        valueColor = if ((b.agcDropDb ?: 0.0) >= 6.0) ErrorRed else null,
    )
}

private fun signals(n: Int) = if (n == 1) "1 signal" else "$n signals"

private const val NAV_PATIENCE_MS = 90_000L

private fun rawStatusText(status: RawStreamStatus, what: String) = when (status) {
    RawStreamStatus.NOT_SUPPORTED -> "This phone does not provide $what."
    RawStreamStatus.LOCATION_DISABLED -> "Location is switched off."
    else -> "Waiting for $what from the chip…"
}

private fun ggaQuality(q: Int) = when (q) {
    0 -> "no fix"; 1 -> "GNSS"; 2 -> "differential (SBAS)"; 4 -> "RTK fixed"; 5 -> "RTK float"; 6 -> "estimated"
    else -> "other"
}

/** Resolves the 10-bit broadcast week against the phone's date: the candidate nearest to it. */
internal fun fullWeek(broadcast: Int, current: Int?): Int? {
    if (current == null) return null
    val base = current - current % 1024 + broadcast
    return listOf(base - 1024, base, base + 1024).minBy { abs(it - current) }
}
