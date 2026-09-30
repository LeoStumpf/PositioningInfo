// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data

import android.content.Context
import io.github.leostumpf.positioninginfo.domain.TtffEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * The last few times to first fix, in the app's private storage; never leaves the phone.
 *
 * Read-modify-write and clear run under one lock, so a clear is never undone by an add that
 * was already under way. The file is replaced whole, through a temporary one, so a process
 * killed mid-write leaves the old log rather than half a new one.
 */
class TtffLogStore(context: Context) {

    private val dir = context.applicationContext.filesDir
    private val file = File(dir, "ttff.csv")
    private val lock = Mutex()

    /** The logged entries, oldest first; empty if there are none or the file cannot be read. */
    suspend fun load(): List<TtffEntry> = lock.withLock { withContext(Dispatchers.IO) { read() } }

    /** Appends and keeps only the newest [TtffEntry.MAX_ENTRIES]; returns the new list. */
    suspend fun add(entry: TtffEntry): List<TtffEntry> = lock.withLock {
        withContext(Dispatchers.IO) {
            val before = read()
            val entries = (before + entry).takeLast(TtffEntry.MAX_ENTRIES)
            // Written beside the log and renamed over it, so a failed write never leaves half a file.
            // If saving fails the log is unchanged, and so is what the page shows.
            val tmp = File(dir, "ttff.csv.tmp")
            val saved = try {
                tmp.writeText(entries.joinToString("\n") { it.encode() } + "\n")
                tmp.renameTo(file)
            } catch (_: IOException) {
                false
            }
            if (saved) entries else before.also { tmp.delete() }
        }
    }

    /** Deletes the log. */
    suspend fun clear() {
        lock.withLock { withContext(Dispatchers.IO) { file.delete() } }
    }

    /** How many entries are logged; reads the whole file. */
    suspend fun count(): Int = load().size

    private fun read(): List<TtffEntry> = try {
        if (!file.exists()) emptyList() else file.readLines().mapNotNull(TtffEntry::decode)
    } catch (_: IOException) {
        emptyList()
    }
}
