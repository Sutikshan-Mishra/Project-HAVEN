// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.projecthaven.data.db.HavenDatabase
import app.projecthaven.data.notes.ImportResult
import app.projecthaven.data.notes.NotesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Room schema, including the unique uuid index used for safe imports. */
@RunWith(AndroidJUnit4::class)
class NoteDaoTest {
    private lateinit var db: HavenDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HavenDatabase::class.java).build()
    }

    @After fun tearDown() = db.close()

    @Test fun persistAndReimport() = runBlocking {
        val repo = NotesRepository(db.notes())
        val id = repo.create(repo.newDraft().copy(title = "Shelter", body = "School hall"))
        repo.update(repo.get(id)!!.copy(pinned = true))
        assertEquals(true, repo.notes.first().single().pinned)
        val json = repo.exportJson()
        val result = repo.importJson(json) as ImportResult.Imported
        assertEquals(0, result.added)
        assertEquals(1, repo.count())
    }
}
