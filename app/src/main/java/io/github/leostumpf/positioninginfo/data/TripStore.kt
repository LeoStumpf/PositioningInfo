// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data

import android.content.Context
import android.net.Uri
import io.github.leostumpf.positioninginfo.domain.Gpx
import io.github.leostumpf.positioninginfo.domain.TripCsv
import io.github.leostumpf.positioninginfo.domain.TripPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Keeps the recorded track in the app's private storage, one line per point, appended as
 * it is recorded — so a trip survives the app being closed or killed.
 *
 * The only way out is [exportGpx], to a document the user picked themselves.
 *
 * Every file operation runs under one lock, in the order it was asked for: appends land in
 * recording order, and a [clear] cannot be overtaken by an append queued before it and
 * leave a deleted trip behind. A full or failing disk costs points, never the app.
 */
class TripStore(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "trip.csv")
    private val lock = Mutex()

    /**
     * The recorded points in order; empty if there is no trip or the file cannot be read. A file
     * in another format is deleted, so new points are not appended to lines nobody can read.
     */
    suspend fun load(): List<TripPoint> = lock.withLock {
        withContext(Dispatchers.IO) {
            if (file.exists() && !hasCurrentHeader()) file.delete()
            read()
        }
    }

    /** False if the point could not be written. */
    suspend fun append(point: TripPoint): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            try {
                if (!file.exists() || file.length() == 0L) file.writeText(TripCsv.HEADER + "\n")
                file.appendText(TripCsv.encode(point) + "\n")
                true
            } catch (_: IOException) {
                false
            }
        }
    }

    /** Deletes the recorded trip. */
    suspend fun clear() {
        lock.withLock { withContext(Dispatchers.IO) { file.delete() } }
    }

    /** Writes the whole trip as a GPX track called [name] to [uri]; false if that failed. */
    suspend fun exportGpx(uri: Uri, name: String): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val points = read()
                appContext.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    Gpx.write(points, name, writer)
                } ?: error("no output stream")
            }.isSuccess
        }
    }

    private fun read(): List<TripPoint> = try {
        if (!file.exists()) emptyList() else TripCsv.decodeFile(file.readLines())
    } catch (_: IOException) {
        emptyList()
    }

    private fun hasCurrentHeader(): Boolean = try {
        file.bufferedReader().use { it.readLine()?.trim() == TripCsv.HEADER }
    } catch (_: IOException) {
        false
    }
}
