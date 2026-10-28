// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data.notes

import app.projecthaven.data.db.NoteDao
import app.projecthaven.data.db.NoteEntity
import app.projecthaven.domain.notes.ImportCheck
import app.projecthaven.domain.notes.ImportPlan
import app.projecthaven.domain.notes.Note
import app.projecthaven.domain.notes.NotesBackup
import app.projecthaven.domain.notes.NotesBackupRules
import app.projecthaven.domain.notes.toDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface ImportResult {
    data class Imported(val added: Int, val duplicates: Int, val invalid: Int) : ImportResult
    data class Failed(val reason: String) : ImportResult
}

class NotesRepository(
    private val dao: NoteDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newUuid: () -> String = { UUID.randomUUID().toString() },
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    val notes: Flow<List<Note>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): Note? = dao.get(id)?.toDomain()

    fun newDraft(): Note {
        val now = clock()
        return Note(uuid = newUuid(), title = "", body = "", createdAt = now, updatedAt = now)
    }

    /** Inserts a new note and returns its id. */
    suspend fun create(note: Note): Long {
        val now = clock()
        return dao.insert(NoteEntity.from(note.copy(id = 0, createdAt = note.createdAt.takeIf { it > 0 } ?: now, updatedAt = now)))
    }

    suspend fun update(note: Note) {
        require(note.id > 0) { "Cannot update a note that has not been saved" }
        dao.update(NoteEntity.from(note.copy(updatedAt = clock())))
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun count(): Int = dao.count()

    suspend fun exportJson(): String {
        val notes = dao.all().map { it.toDomain().toDto() }
        return withContext(Dispatchers.Default) {
            json.encodeToString(NotesBackup.serializer(), NotesBackup(exportedAt = clock(), notes = notes))
        }
    }

    /**
     * Validates the whole file first, then inserts only new notes. Existing notes are never
     * modified, so a bad or repeated import cannot destroy data.
     */
    suspend fun importJson(text: String): ImportResult {
        if (text.length > MAX_IMPORT_CHARS) return ImportResult.Failed("File is too large")
        val backup = try {
            withContext(Dispatchers.Default) { json.decodeFromString(NotesBackup.serializer(), text) }
        } catch (e: SerializationException) {
            return ImportResult.Failed("This file is not a valid HAVEN notes backup")
        } catch (e: IllegalArgumentException) {
            return ImportResult.Failed("This file is not a valid HAVEN notes backup")
        }
        val plan: ImportPlan = when (val check = NotesBackupRules.plan(backup, dao.allUuids().toSet())) {
            is ImportCheck.Rejected -> return ImportResult.Failed(check.reason)
            is ImportCheck.Ok -> check.plan
        }
        val ids = dao.insertAllIgnoringConflicts(plan.toInsert.map { NoteEntity.from(it.copy(id = 0)) })
        val added = ids.count { it > 0 }
        return ImportResult.Imported(added, plan.duplicates + (plan.toInsert.size - added), plan.invalid)
    }

    companion object {
        const val MAX_IMPORT_CHARS = 50_000_000
    }
}
