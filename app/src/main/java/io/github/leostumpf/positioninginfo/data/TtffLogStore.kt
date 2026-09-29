// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.data

import android.content.Context
import io.github.leostumpf.positioninginfo.domain.TtffEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The last few times to first fix, in the app's private storage; never leaves the phone. */
class TtffLogStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "ttff.csv")

    suspend fun load(): List<TtffEntry> = withContext(Dispatchers.IO) {
        if (!file.exists()) emptyList() else file.readLines().mapNotNull(TtffEntry::decode)
    }

    /** Appends and keeps only the newest [TtffEntry.MAX_ENTRIES]; returns the new list. */
    suspend fun add(entry: TtffEntry): List<TtffEntry> = withContext(Dispatchers.IO) {
        val entries = (load() + entry).takeLast(TtffEntry.MAX_ENTRIES)
        file.writeText(entries.joinToString("\n") { it.encode() } + "\n")
        entries
    }

    suspend fun clear() = withContext(Dispatchers.IO) { file.delete() }

    suspend fun count(): Int = load().size
}
