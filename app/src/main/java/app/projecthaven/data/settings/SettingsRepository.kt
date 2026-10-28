// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.projecthaven.domain.morse.MorseTiming
import app.projecthaven.domain.sos.CheckInResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class ThemeMode(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("System") }

data class MorsePrefs(val wpm: Int, val letterGap: Int, val wordGap: Int) {
    fun timing() = MorseTiming(wpm, letterGap, wordGap)
}

data class SosPrefs(val includeLocation: Boolean, val extraNote: String)

data class CheckInPrefs(
    val deadlineEpochMs: Long?,
    val startedAtEpochMs: Long?,
    val response: CheckInResponse,
)

/**
 * App preferences in a Preferences DataStore. All reads fall back to safe defaults if the
 * file is unreadable, so a corrupted preference file never blocks emergency features.
 */
class SettingsRepository(private val store: DataStore<Preferences>) {

    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val rulerPxPerMm = floatPreferencesKey("ruler_px_per_mm")
        val morseWpm = intPreferencesKey("morse_wpm")
        val morseLetterGap = intPreferencesKey("morse_letter_gap")
        val morseWordGap = intPreferencesKey("morse_word_gap")
        val levelOffsetPitch = floatPreferencesKey("level_offset_pitch")
        val levelOffsetRoll = floatPreferencesKey("level_offset_roll")
        val bookmarks = stringSetPreferencesKey("bookmarks")
        val checklist = stringSetPreferencesKey("checklist_done")
        val sosIncludeLocation = booleanPreferencesKey("sos_include_location")
        val sosExtraNote = stringPreferencesKey("sos_extra_note")
        val checkInDeadline = longPreferencesKey("checkin_deadline")
        val checkInStarted = longPreferencesKey("checkin_started")
        val checkInResponse = stringPreferencesKey("checkin_response")
        val catalogUrl = stringPreferencesKey("map_catalog_url")
        val unmeteredOnly = booleanPreferencesKey("map_unmetered_only")
        val notesSort = stringPreferencesKey("notes_sort")
    }

    private val prefs: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val themeMode: Flow<ThemeMode> = prefs.map { p ->
        p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK
    }.distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) { store.edit { it[Keys.theme] = mode.name } }

    suspend fun currentThemeMode(): ThemeMode = themeMode.first()

    val rulerPxPerMm: Flow<Float?> = prefs.map { it[Keys.rulerPxPerMm] }.distinctUntilChanged()

    suspend fun setRulerPxPerMm(value: Float?) {
        store.edit { if (value == null) it.remove(Keys.rulerPxPerMm) else it[Keys.rulerPxPerMm] = value }
    }

    val morse: Flow<MorsePrefs> = prefs.map { p ->
        val wpm = (p[Keys.morseWpm] ?: 10).coerceIn(MorseTiming.MIN_WPM, MorseTiming.MAX_WPM)
        val letter = (p[Keys.morseLetterGap] ?: MorseTiming.STANDARD_LETTER_GAP).coerceIn(3, 15)
        val word = (p[Keys.morseWordGap] ?: MorseTiming.STANDARD_WORD_GAP).coerceIn(7, 30)
        if (word > letter) MorsePrefs(wpm, letter, word) else MorsePrefs(wpm, 3, 7)
    }.distinctUntilChanged()

    suspend fun setMorse(prefs: MorsePrefs) {
        prefs.timing() // validates
        store.edit {
            it[Keys.morseWpm] = prefs.wpm
            it[Keys.morseLetterGap] = prefs.letterGap
            it[Keys.morseWordGap] = prefs.wordGap
        }
    }

    val levelOffsets: Flow<Pair<Float, Float>> =
        prefs.map { Pair(it[Keys.levelOffsetPitch] ?: 0f, it[Keys.levelOffsetRoll] ?: 0f) }.distinctUntilChanged()

    suspend fun setLevelOffsets(pitch: Float, roll: Float) {
        store.edit { it[Keys.levelOffsetPitch] = pitch; it[Keys.levelOffsetRoll] = roll }
    }

    val bookmarks: Flow<Set<String>> = prefs.map { it[Keys.bookmarks] ?: emptySet() }.distinctUntilChanged()

    suspend fun setBookmarked(key: String, bookmarked: Boolean) {
        store.edit {
            val current = it[Keys.bookmarks] ?: emptySet()
            it[Keys.bookmarks] = if (bookmarked) current + key else current - key
        }
    }

    val checklistDone: Flow<Set<String>> = prefs.map { it[Keys.checklist] ?: emptySet() }.distinctUntilChanged()

    suspend fun setChecklistItem(key: String, done: Boolean) {
        store.edit {
            val current = it[Keys.checklist] ?: emptySet()
            it[Keys.checklist] = if (done) current + key else current - key
        }
    }

    suspend fun resetChecklist(prefix: String) {
        store.edit {
            val current = it[Keys.checklist] ?: emptySet()
            it[Keys.checklist] = current.filterNot { k -> k.startsWith(prefix) }.toSet()
        }
    }

    val sos: Flow<SosPrefs> = prefs.map {
        SosPrefs(it[Keys.sosIncludeLocation] ?: true, it[Keys.sosExtraNote] ?: "")
    }.distinctUntilChanged()

    suspend fun setSos(prefs: SosPrefs) {
        store.edit {
            it[Keys.sosIncludeLocation] = prefs.includeLocation
            it[Keys.sosExtraNote] = prefs.extraNote.take(300)
        }
    }

    val checkIn: Flow<CheckInPrefs> = prefs.map {
        CheckInPrefs(
            deadlineEpochMs = it[Keys.checkInDeadline],
            startedAtEpochMs = it[Keys.checkInStarted],
            response = it[Keys.checkInResponse]?.let { r -> runCatching { CheckInResponse.valueOf(r) }.getOrNull() }
                ?: CheckInResponse.NOTIFY_AND_PREPARE,
        )
    }.distinctUntilChanged()

    suspend fun setCheckIn(deadline: Long?, startedAt: Long?) {
        store.edit {
            if (deadline == null) it.remove(Keys.checkInDeadline) else it[Keys.checkInDeadline] = deadline
            if (startedAt == null) it.remove(Keys.checkInStarted) else it[Keys.checkInStarted] = startedAt
        }
    }

    suspend fun setCheckInResponse(response: CheckInResponse) {
        store.edit { it[Keys.checkInResponse] = response.name }
    }

    /** User override of the build-time map catalog URL. Empty means "use the build default". */
    val catalogUrlOverride: Flow<String> = prefs.map { it[Keys.catalogUrl] ?: "" }.distinctUntilChanged()

    suspend fun setCatalogUrlOverride(url: String) {
        store.edit { if (url.isBlank()) it.remove(Keys.catalogUrl) else it[Keys.catalogUrl] = url.trim() }
    }

    val unmeteredOnly: Flow<Boolean> = prefs.map { it[Keys.unmeteredOnly] ?: false }.distinctUntilChanged()

    suspend fun setUnmeteredOnly(value: Boolean) { store.edit { it[Keys.unmeteredOnly] = value } }

    val notesSort: Flow<String> = prefs.map { it[Keys.notesSort] ?: "MODIFIED" }.distinctUntilChanged()

    suspend fun setNotesSort(value: String) { store.edit { it[Keys.notesSort] = value } }
}
