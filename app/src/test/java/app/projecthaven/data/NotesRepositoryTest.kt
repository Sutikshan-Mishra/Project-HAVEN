// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data

import app.projecthaven.data.db.NoteDao
import app.projecthaven.data.db.NoteEntity
import app.projecthaven.data.notes.ImportResult
import app.projecthaven.data.notes.NotesRepository
import app.projecthaven.domain.notes.NoteTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory DAO with the same uniqueness rule as the Room schema (unique uuid). */
class FakeNoteDao : NoteDao {
    private val rows = MutableStateFlow<List<NoteEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<NoteEntity>> = rows
    override suspend fun get(id: Long) = rows.value.firstOrNull { it.id == id }
    override suspend fun allUuids() = rows.value.map { it.uuid }
    override suspend fun all() = rows.value
    override suspend fun insert(note: NoteEntity): Long {
        require(rows.value.none { it.uuid == note.uuid }) { "UNIQUE constraint failed" }
        val id = nextId++
        rows.value = rows.value + note.copy(id = id)
        return id
    }
    override suspend fun insertAllIgnoringConflicts(notes: List<NoteEntity>): List<Long> =
        notes.map { n -> if (rows.value.any { it.uuid == n.uuid }) -1L else insert(n) }
    override suspend fun update(note: NoteEntity) {
        rows.value = rows.value.map { if (it.id == note.id) note else it }
    }
    override suspend fun delete(id: Long) { rows.value = rows.value.filterNot { it.id == id } }
    override suspend fun count() = rows.value.size
}

class NotesRepositoryTest {
    private var now = 1_000L
    private var uuidCounter = 0
    private fun repo(dao: NoteDao = FakeNoteDao()) = NotesRepository(
        dao, clock = { now },
        newUuid = { "00000000-0000-4000-8000-%012d".format(++uuidCounter) },
    )

    @Test fun createEditDelete() = runBlocking {
        val r = repo()
        val id = r.create(r.newDraft().copy(title = "Water", body = "Hand pump", tag = NoteTag.LOCATION))
        now = 2_000L
        r.update(r.get(id)!!.copy(body = "Hand pump near school"))
        val saved = r.get(id)!!
        assertEquals("Hand pump near school", saved.body)
        assertEquals(1_000L, saved.createdAt)
        assertEquals(2_000L, saved.updatedAt)
        assertEquals(1, r.notes.first().size)
        r.delete(id)
        assertEquals(0, r.count())
    }

    @Test fun exportThenImportIntoEmptyDatabaseRestoresNotes() = runBlocking {
        val source = repo()
        source.create(source.newDraft().copy(title = "Route", body = "NH27 via Unnao", pinned = true, latitude = 26.5, longitude = 80.5))
        source.create(source.newDraft().copy(body = "Rice 5 kg", tag = NoteTag.SUPPLIES))
        val json = source.exportJson()

        val target = repo()
        val result = target.importJson(json) as ImportResult.Imported
        assertEquals(2, result.added)
        val notes = target.notes.first()
        assertEquals(setOf("Route", ""), notes.map { it.title }.toSet())
        assertTrue(notes.first { it.title == "Route" }.pinned)
        assertEquals(26.5, notes.first { it.title == "Route" }.latitude!!, 1e-9)
    }

    @Test fun reimportingTheSameBackupAddsNothing() = runBlocking {
        val r = repo()
        r.create(r.newDraft().copy(title = "A"))
        val json = r.exportJson()
        val result = r.importJson(json) as ImportResult.Imported
        assertEquals(0, result.added)
        assertEquals(1, result.duplicates)
        assertEquals(1, r.count())
    }

    @Test fun invalidFilesAreRejectedWithoutChangingData() = runBlocking {
        val r = repo()
        r.create(r.newDraft().copy(title = "Keep me"))
        assertTrue(r.importJson("not json at all") is ImportResult.Failed)
        assertTrue(r.importJson("""{"format":"something-else","formatVersion":1,"exportedAt":1,"notes":[]}""") is ImportResult.Failed)
        assertTrue(r.importJson("""{"format":"haven-notes","formatVersion":9,"exportedAt":1,"notes":[]}""") is ImportResult.Failed)
        assertEquals(1, r.count())
        assertEquals("Keep me", r.notes.first().single().title)
    }
}
