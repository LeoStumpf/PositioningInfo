// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.leostumpf.positioninginfo.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.leostumpf.positioninginfo.domain.SpeedUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// A corrupt file holds nothing worth more than the app starting: it is replaced by the
// defaults instead of failing every launch.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** Persists the unit the user last cycled to, so the app opens the way they left it. */
class UnitPreference(context: Context) {

    private val store = context.applicationContext.dataStore

    val unit: Flow<SpeedUnit> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> SpeedUnit.fromName(prefs[KEY_UNIT]) }

    /** A failed write only costs remembering the unit; the app carries on with it in memory. */
    suspend fun set(unit: SpeedUnit) {
        try {
            store.edit { it[KEY_UNIT] = unit.name }
        } catch (_: IOException) {
        }
    }

    /** Back to the default unit, with nothing stored. */
    suspend fun clear() {
        try {
            store.edit { it.clear() }
        } catch (_: IOException) {
        }
    }

    private companion object {
        val KEY_UNIT = stringPreferencesKey("speed_unit")
    }
}
