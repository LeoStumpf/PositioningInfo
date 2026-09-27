// SPDX-License-Identifier: AGPL-3.0-or-later
package de.leostumpf.gpstools.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.leostumpf.gpstools.domain.SpeedUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Persists the unit the user last cycled to, so the app opens the way they left it. */
class UnitPreference(context: Context) {

    private val store = context.applicationContext.dataStore

    val unit: Flow<SpeedUnit> = store.data.map { prefs ->
        SpeedUnit.fromName(prefs[KEY_UNIT])
    }

    suspend fun set(unit: SpeedUnit) {
        store.edit { it[KEY_UNIT] = unit.name }
    }

    private companion object {
        val KEY_UNIT = stringPreferencesKey("speed_unit")
    }
}
