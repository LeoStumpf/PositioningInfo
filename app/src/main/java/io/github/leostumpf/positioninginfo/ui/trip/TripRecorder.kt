// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.ui.trip

import android.app.Application
import android.net.Uri
import io.github.leostumpf.positioninginfo.data.TripStore
import io.github.leostumpf.positioninginfo.domain.SpeedUnit
import io.github.leostumpf.positioninginfo.domain.TripAccumulator
import io.github.leostumpf.positioninginfo.domain.TripPoint
import io.github.leostumpf.positioninginfo.domain.counted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The recorded trip: its running statistics, the altitude profile, and the copy on disk.
 *
 * Points are kept on the phone as they are recorded ([TripStore]), so a trip survives the app
 * being closed; the saved trip is loaded once at start. Owned by the analysis session, which
 * feeds it points while recording.
 */
class TripRecorder(
    application: Application,
    private val scope: CoroutineScope,
    private val hasBarometer: Boolean,
    private val speedUnit: () -> SpeedUnit,
) {
    private val store = TripStore(application)

    private val _state = MutableStateFlow(TripUiState(climbFromBarometer = hasBarometer))
    val state: StateFlow<TripUiState> = _state.asStateFlow()

    private var trip = TripAccumulator()

    /** Appended in place: copying the list on every fix cost O(n²) over a long trip. */
    private val altitudes = ArrayList<Double>()
    var recording = false
        private set

    /** Recording waits until the saved trip is loaded, so the load cannot overwrite new points. */
    private var loaded = false

    /** Bumped by every delete, so a load that was already under way cannot bring a trip back. */
    private var generation = 0
    private var message: String? = null

    /** Whether the app is on screen; out of sight the state is not rebuilt. */
    var visible = true
        set(value) {
            field = value
            if (value) publish()
        }

    init {
        scope.launch {
            val started = generation
            val saved = store.load()
            if (started == generation) {
                // Each point carries the climb height and source it was counted from live.
                trip = saved.fold(TripAccumulator(), TripAccumulator::add)
                altitudes.clear()
                saved.mapNotNullTo(altitudes) { it.altitudeM }
            }
            loaded = true
            publish()
        }
    }

    /** A new point while [recording]; the trip stops by itself once it is full. */
    fun add(point: TripPoint) {
        if (!recording) return
        if (trip.stats.points >= MAX_POINTS) {
            recording = false
            message = "The trip is full (${MAX_POINTS.counted("point")}). Export it and delete it to record a new one."
        } else {
            trip = trip.add(point)
            point.altitudeM?.let { altitudes += it }
            scope.launch { store.append(point) }
        }
        publish()
    }

    /** Starts or pauses recording; ignored until the saved trip has loaded. */
    fun toggle() {
        if (!loaded) return
        recording = !recording
        message = null
        publish()
    }

    /** Deletes the trip from memory and the phone; [note] is shown in its place. */
    fun clear(note: String? = "Trip deleted.") {
        recording = false
        trip = TripAccumulator()
        altitudes.clear()
        generation++
        message = note
        scope.launch { store.clear() }
        publish()
    }

    /** Writes the trip as GPX to [uri] in the background; the outcome is shown as the message. */
    fun export(uri: Uri) {
        scope.launch {
            val ok = store.exportGpx(uri, "Positioning Info trip ${suggestedDate()}")
            message = if (ok) "Exported ${trip.stats.points.counted("point")} as GPX." else "Export failed."
            publish()
        }
    }

    /** A file name for the export, e.g. "trip-2026-09-30_1420.gpx". */
    fun suggestedFileName(): String = "trip-${suggestedDate().replace(' ', '_').replace(":", "")}.gpx"

    /** Rebuilds [state] from the trip so far, unless the app is out of sight. */
    fun publish() {
        if (!visible) return
        _state.value = TripUiState(
            recording = recording,
            stats = trip.stats,
            unit = speedUnit(),
            climbFromBarometer = hasBarometer,
            message = message,
            elevationProfile = thin(altitudes, PROFILE_POINTS),
        )
    }

    /** Every n-th value, so a long trip still draws cheaply. */
    private fun thin(values: List<Double>, max: Int): List<Double> {
        if (values.size <= max) return values.toList()
        val step = values.size / max + 1
        return List((values.size + step - 1) / step) { values[it * step] }
    }

    private fun suggestedDate(): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

    private companion object {
        const val PROFILE_POINTS = 300

        /** About 55 hours at one fix a second, some 12 MB on disk; the whole file is read at start. */
        const val MAX_POINTS = 200_000
    }
}
