// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.projecthaven.data.settings.MorsePrefs
import app.projecthaven.data.settings.SettingsRepository
import app.projecthaven.data.settings.ThemeMode
import app.projecthaven.domain.sos.CheckInResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SettingsRepositoryTest {

    private val dir: File = Files.createTempDirectory("haven-settings").toFile()
    private val file = File(dir, "settings.preferences_pb")

    /** Simulates an app process: one DataStore instance per file at a time. */
    private fun <T> withRepo(block: suspend (SettingsRepository) -> T): T = runBlocking {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        try {
            block(SettingsRepository(store))
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test fun darkIsTheDefaultTheme() {
        assertEquals(ThemeMode.DARK, withRepo { it.themeMode.first() })
    }

    @Test fun themeSurvivesRestart() {
        withRepo { it.setThemeMode(ThemeMode.LIGHT) }
        assertEquals(ThemeMode.LIGHT, withRepo { it.themeMode.first() })
        withRepo { it.setThemeMode(ThemeMode.SYSTEM) }
        assertEquals(ThemeMode.SYSTEM, withRepo { it.themeMode.first() })
    }

    @Test fun checklistProgressPersistsAndResetsByArticle() {
        withRepo {
            it.setChecklistItem("survival/emergency-kit/kit/0", true)
            it.setChecklistItem("survival/emergency-kit/kit/3", true)
            it.setChecklistItem("survival/power-outage/list/1", true)
            it.setChecklistItem("survival/emergency-kit/kit/3", false)
        }
        assertEquals(setOf("survival/emergency-kit/kit/0", "survival/power-outage/list/1"), withRepo { it.checklistDone.first() })
        withRepo { it.resetChecklist("survival/emergency-kit/") }
        assertEquals(setOf("survival/power-outage/list/1"), withRepo { it.checklistDone.first() })
    }

    @Test fun bookmarksPersist() {
        withRepo { it.setBookmarked("first_aid/burns", true); it.setBookmarked("recipe/khichdi", true) }
        withRepo { it.setBookmarked("first_aid/burns", false) }
        assertEquals(setOf("recipe/khichdi"), withRepo { it.bookmarks.first() })
    }

    @Test fun rulerCalibrationAndCheckInPersist() {
        withRepo {
            it.setRulerPxPerMm(17.5f)
            it.setCheckIn(deadline = 5_000L, startedAt = 1_000L)
            it.setCheckInResponse(CheckInResponse.NOTIFY)
        }
        withRepo {
            assertEquals(17.5f, it.rulerPxPerMm.first()!!, 1e-6f)
            val c = it.checkIn.first()
            assertEquals(5_000L, c.deadlineEpochMs)
            assertEquals(CheckInResponse.NOTIFY, c.response)
            it.setRulerPxPerMm(null)
            it.setCheckIn(null, null)
        }
        withRepo {
            assertNull(it.rulerPxPerMm.first())
            assertNull(it.checkIn.first().deadlineEpochMs)
        }
    }

    @Test fun morsePrefsAreValidated() {
        withRepo { it.setMorse(MorsePrefs(15, 5, 12)) }
        assertEquals(MorsePrefs(15, 5, 12), withRepo { it.morse.first() })
        val failed = runCatching { withRepo { it.setMorse(MorsePrefs(15, 8, 7)) } }.isFailure
        assertTrue("word gap shorter than letter gap must be rejected", failed)
    }
}
