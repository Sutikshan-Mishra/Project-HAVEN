// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain.notes

import kotlinx.serialization.Serializable

enum class NoteTag(val label: String) {
    GENERAL("General"),
    LOCATION("Location"),
    SUPPLIES("Supplies"),
    ROUTE("Route"),
    EMERGENCY("Emergency");

    companion object {
        fun fromName(name: String?): NoteTag? = entries.firstOrNull { it.name == name }
    }
}

data class Note(
    val id: Long = 0,
    /** Stable identity used to detect duplicates when importing backups. */
    val uuid: String,
    val title: String,
    val body: String,
    val tag: NoteTag? = null,
    val pinned: Boolean = false,
    val favourite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
) {
    val isBlank: Boolean get() = title.isBlank() && body.isBlank()
    val hasLocation: Boolean get() = latitude != null && longitude != null

    /** Title, or the first line of the body when the title is empty. */
    val displayTitle: String
        get() = title.trim().ifEmpty { body.trim().lineSequence().firstOrNull()?.take(80).orEmpty() }
            .ifEmpty { "Untitled note" }
}

enum class NoteSort(val label: String) {
    MODIFIED("Last modified"),
    CREATED("Date created"),
}

data class NoteFilter(
    val query: String = "",
    val tag: NoteTag? = null,
    val favouritesOnly: Boolean = false,
    val sort: NoteSort = NoteSort.MODIFIED,
)

object NoteQuery {
    /** Pinned notes first, then newest first by the chosen sort key. */
    fun apply(notes: List<Note>, filter: NoteFilter): List<Note> {
        val terms = filter.query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return notes
            .asSequence()
            .filter { filter.tag == null || it.tag == filter.tag }
            .filter { !filter.favouritesOnly || it.favourite }
            .filter { note ->
                if (terms.isEmpty()) true else {
                    val haystack = (note.title + "\n" + note.body).lowercase()
                    terms.all { haystack.contains(it) }
                }
            }
            .sortedWith(
                compareByDescending<Note> { it.pinned }
                    .thenByDescending { if (filter.sort == NoteSort.MODIFIED) it.updatedAt else it.createdAt }
                    .thenByDescending { it.id },
            )
            .toList()
    }
}

/** Decides whether an edit should be written, so empty new notes are never stored. */
object NoteEditing {
    sealed interface SaveAction {
        data object Nothing : SaveAction
        data object Insert : SaveAction
        data object Update : SaveAction
        /** An existing note was emptied; keep it (user deletes explicitly) but save the change. */
        data object UpdateEmpty : SaveAction
    }

    fun decide(isNew: Boolean, original: Note?, edited: Note): SaveAction = when {
        isNew && edited.isBlank -> SaveAction.Nothing
        isNew -> SaveAction.Insert
        original != null && sameContent(original, edited) -> SaveAction.Nothing
        edited.isBlank -> SaveAction.UpdateEmpty
        else -> SaveAction.Update
    }

    fun sameContent(a: Note, b: Note): Boolean =
        a.title == b.title && a.body == b.body && a.tag == b.tag && a.pinned == b.pinned &&
            a.favourite == b.favourite && a.latitude == b.latitude && a.longitude == b.longitude
}

// ---- Backup format ---------------------------------------------------------------------

@Serializable
data class NotesBackup(
    val format: String = FORMAT,
    val formatVersion: Int = 1,
    val exportedAt: Long,
    val app: String = "Project HAVEN",
    val notes: List<NoteDto>,
) {
    companion object {
        const val FORMAT = "haven-notes"
        const val SUPPORTED_VERSION = 1
    }
}

@Serializable
data class NoteDto(
    val uuid: String,
    val title: String = "",
    val body: String = "",
    val tag: String? = null,
    val pinned: Boolean = false,
    val favourite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
)

fun Note.toDto() = NoteDto(uuid, title, body, tag?.name, pinned, favourite, createdAt, updatedAt, latitude, longitude, accuracyMeters)

data class ImportPlan(
    val toInsert: List<Note>,
    val duplicates: Int,
    val invalid: Int,
)

sealed interface ImportCheck {
    data class Ok(val plan: ImportPlan) : ImportCheck
    data class Rejected(val reason: String) : ImportCheck
}

object NotesBackupRules {
    const val MAX_NOTES = 20_000
    const val MAX_TITLE = 500
    const val MAX_BODY = 200_000
    private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun isValid(dto: NoteDto): Boolean {
        if (!UUID.matches(dto.uuid)) return false
        if (dto.title.length > MAX_TITLE || dto.body.length > MAX_BODY) return false
        if (dto.title.isBlank() && dto.body.isBlank()) return false
        if (dto.createdAt <= 0 || dto.updatedAt <= 0) return false
        if ((dto.latitude == null) != (dto.longitude == null)) return false
        if (dto.latitude != null && dto.latitude !in -90.0..90.0) return false
        if (dto.longitude != null && dto.longitude !in -180.0..180.0) return false
        if (dto.tag != null && NoteTag.fromName(dto.tag) == null) return false
        return true
    }

    /**
     * Builds an import plan without touching existing data. Notes whose uuid already exists,
     * or which repeat inside the file, are counted as duplicates and skipped.
     */
    fun plan(backup: NotesBackup, existingUuids: Set<String>): ImportCheck {
        if (backup.format != NotesBackup.FORMAT) return ImportCheck.Rejected("This is not a HAVEN notes backup")
        if (backup.formatVersion > NotesBackup.SUPPORTED_VERSION)
            return ImportCheck.Rejected("This backup was made by a newer version of HAVEN")
        if (backup.notes.size > MAX_NOTES) return ImportCheck.Rejected("Backup has too many notes")

        val seen = HashSet<String>(existingUuids.map { it.lowercase() })
        var duplicates = 0
        var invalid = 0
        val out = ArrayList<Note>()
        for (dto in backup.notes) {
            if (!isValid(dto)) { invalid++; continue }
            if (!seen.add(dto.uuid.lowercase())) { duplicates++; continue }
            out += Note(
                uuid = dto.uuid.lowercase(),
                title = dto.title,
                body = dto.body,
                tag = NoteTag.fromName(dto.tag),
                pinned = dto.pinned,
                favourite = dto.favourite,
                createdAt = dto.createdAt,
                updatedAt = maxOf(dto.updatedAt, dto.createdAt),
                latitude = dto.latitude,
                longitude = dto.longitude,
                accuracyMeters = dto.accuracyMeters,
            )
        }
        return ImportCheck.Ok(ImportPlan(out, duplicates, invalid))
    }
}
