// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Optional, user-entered emergency information. Every field may be empty. */
data class EmergencyProfile(
    val name: String = "",
    val bloodGroup: String = "",
    val allergies: String = "",
    val medications: String = "",
    val conditions: String = "",
    val emergencyNotes: String = "",
) {
    val isEmpty: Boolean
        get() = listOf(name, bloodGroup, allergies, medications, conditions, emergencyNotes).all { it.isBlank() }

    /** Text the user can choose to share. Only non-empty fields are included. */
    fun shareText(): String = buildString {
        append("Emergency information")
        fun line(label: String, value: String) {
            if (value.isNotBlank()) append("\n").append(label).append(": ").append(value.trim())
        }
        line("Name", name)
        line("Blood group", bloodGroup)
        line("Allergies", allergies)
        line("Medications", medications)
        line("Medical conditions", conditions)
        line("Notes", emergencyNotes)
    }

    companion object {
        const val MAX_FIELD = 1000
    }
}

/** Stored in its own app-private DataStore file; excluded from cloud backup (see backup rules). */
class ProfileRepository(private val store: DataStore<Preferences>) {

    private object Keys {
        val name = stringPreferencesKey("name")
        val blood = stringPreferencesKey("blood_group")
        val allergies = stringPreferencesKey("allergies")
        val meds = stringPreferencesKey("medications")
        val conditions = stringPreferencesKey("conditions")
        val notes = stringPreferencesKey("notes")
    }

    val profile: Flow<EmergencyProfile> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map {
            EmergencyProfile(
                it[Keys.name] ?: "", it[Keys.blood] ?: "", it[Keys.allergies] ?: "",
                it[Keys.meds] ?: "", it[Keys.conditions] ?: "", it[Keys.notes] ?: "",
            )
        }

    suspend fun save(p: EmergencyProfile) {
        store.edit {
            fun put(key: Preferences.Key<String>, value: String) {
                val v = value.take(EmergencyProfile.MAX_FIELD)
                if (v.isBlank()) it.remove(key) else it[key] = v
            }
            put(Keys.name, p.name)
            put(Keys.blood, p.bloodGroup)
            put(Keys.allergies, p.allergies)
            put(Keys.meds, p.medications)
            put(Keys.conditions, p.conditions)
            put(Keys.notes, p.emergencyNotes)
        }
    }

    suspend fun delete() { store.edit { it.clear() } }
}
