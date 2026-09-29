// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.gpstools.data

import android.content.Context
import android.net.Uri
import io.github.leostumpf.gpstools.domain.Gpx
import io.github.leostumpf.gpstools.domain.TripCsv
import io.github.leostumpf.gpstools.domain.TripPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Keeps the recorded track in the app's private storage, one line per point, appended as
 * it is recorded — so a trip survives the app being closed or killed.
 *
 * The only way out is [exportGpx], to a document the user picked themselves.
 */
class TripStore(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "trip.csv")

    suspend fun load(): List<TripPoint> = withContext(Dispatchers.IO) {
        if (!file.exists()) emptyList() else file.readLines().mapNotNull(TripCsv::decode)
    }

    suspend fun append(point: TripPoint) = withContext(Dispatchers.IO) {
        file.appendText(TripCsv.encode(point) + "\n")
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
    }

    suspend fun exportGpx(uri: Uri, name: String): Boolean = withContext(Dispatchers.IO) {
        val points = load()
        runCatching {
            appContext.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                Gpx.write(points, name, writer)
            } ?: error("no output stream")
        }.isSuccess
    }
}
