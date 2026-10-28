// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.screens.notebook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.projecthaven.data.notes.NotesRepository
import app.projecthaven.domain.notes.Note
import app.projecthaven.domain.notes.NoteEditing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class EditorState(
    val loading: Boolean = true,
    val note: Note? = null,
    val missing: Boolean = false,
    val saving: Boolean = false,
    val saveError: String? = null,
)

/**
 * Autosaves edits shortly after typing stops, and once more when the editor closes.
 * An empty new note is never written. Saves are serialised so they cannot race.
 */
class NoteEditorViewModel(
    private val repo: NotesRepository,
    noteId: Long,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var original: Note? = null
    private var isNew = noteId <= 0
    private var saveJob: Job? = null
    private val saveLock = Mutex()

    init {
        viewModelScope.launch {
            if (isNew) {
                _state.value = EditorState(loading = false, note = repo.newDraft())
            } else {
                val n = repo.get(noteId)
                original = n
                _state.value = if (n == null) EditorState(loading = false, missing = true) else EditorState(loading = false, note = n)
            }
        }
    }

    fun edit(transform: (Note) -> Note) {
        val current = _state.value.note ?: return
        _state.update { it.copy(note = transform(current), saveError = null) }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            save()
        }
    }

    suspend fun save() = saveLock.withLock {
        val edited = _state.value.note ?: return@withLock
        try {
            when (NoteEditing.decide(isNew, original, edited)) {
                NoteEditing.SaveAction.Nothing -> Unit
                NoteEditing.SaveAction.Insert -> {
                    val id = repo.create(edited)
                    val saved = edited.copy(id = id)
                    isNew = false
                    original = saved
                    _state.update { s -> s.copy(note = s.note?.copy(id = id)) }
                }
                NoteEditing.SaveAction.Update, NoteEditing.SaveAction.UpdateEmpty -> {
                    repo.update(edited)
                    original = edited
                }
            }
        } catch (e: Exception) {
            _state.update { it.copy(saveError = "Couldn't save. Your text is still here.") }
        }
    }

    suspend fun delete() {
        saveJob?.cancel()
        val id = _state.value.note?.id ?: 0L
        if (id > 0) repo.delete(id)
        _state.update { it.copy(note = null, missing = true) }
        isNew = false
        original = null
    }

    override fun onCleared() {
        // Final save outlives the screen so closing never loses the last keystrokes.
        saveJob?.cancel()
        if (_state.value.note != null) appScope.launch { save() }
    }

    companion object {
        const val AUTOSAVE_DELAY_MS = 600L
    }
}
