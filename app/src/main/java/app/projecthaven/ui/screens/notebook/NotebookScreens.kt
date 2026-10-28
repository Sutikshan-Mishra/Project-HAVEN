// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.screens.notebook

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.projecthaven.data.notes.ImportResult
import app.projecthaven.domain.geo.Coordinate
import app.projecthaven.domain.geo.Geo
import app.projecthaven.domain.notes.Note
import app.projecthaven.domain.notes.NoteFilter
import app.projecthaven.domain.notes.NoteQuery
import app.projecthaven.domain.notes.NoteSort
import app.projecthaven.domain.notes.NoteTag
import app.projecthaven.ui.LocalAppContainer
import app.projecthaven.ui.components.ConfirmDialog
import app.projecthaven.ui.components.EmptyState
import app.projecthaven.ui.components.HavenCard
import app.projecthaven.ui.components.HavenChip
import app.projecthaven.ui.components.HavenHeader
import app.projecthaven.ui.components.Hint
import app.projecthaven.ui.components.LocalShowMessage
import app.projecthaven.ui.components.SearchField
import app.projecthaven.ui.components.SecondaryButton
import app.projecthaven.ui.components.SystemActions
import app.projecthaven.ui.navigation.Routes
import app.projecthaven.ui.screens.formatAccuracy
import app.projecthaven.ui.screens.rememberLiveLocation
import app.projecthaven.ui.theme.Dimens
import app.projecthaven.ui.theme.Haven
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private fun formatTime(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

@Composable
fun NotebookScreen(back: () -> Unit, go: (String) -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val show = LocalShowMessage.current
    val scope = rememberCoroutineScope()
    val notes by container.notes.notes.collectAsStateWithLifecycle(initialValue = null)
    val sortName by container.settings.notesSort.collectAsStateWithLifecycle(initialValue = NoteSort.MODIFIED.name)
    val sort = runCatching { NoteSort.valueOf(sortName) }.getOrDefault(NoteSort.MODIFIED)
    var query by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf<NoteTag?>(null) }
    var favourites by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                val json = container.notes.exportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) } ?: error("No stream")
                }
            }.isSuccess
            show(if (ok) "Notes exported" else "Export failed. Try another location.")
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readNBytesCompat(MAX_IMPORT_BYTES + 1)
                        if (bytes.size > MAX_IMPORT_BYTES) null else String(bytes, Charsets.UTF_8)
                    }
                }
            }.getOrNull()
            if (text == null) { show("Couldn't read that file"); return@launch }
            when (val r = container.notes.importJson(text)) {
                is ImportResult.Failed -> show(r.reason)
                is ImportResult.Imported -> show(buildString {
                    append(if (r.added == 1) "1 note imported" else "${r.added} notes imported")
                    if (r.duplicates > 0) append(" · ${r.duplicates} already here")
                    if (r.invalid > 0) append(" · ${r.invalid} skipped")
                })
            }
        }
    }

    val filtered = remember(notes, query, tag, favourites, sort) {
        NoteQuery.apply(notes.orEmpty(), NoteFilter(query, tag, favourites, sort))
    }

    Box(Modifier.fillMaxSize().background(Haven.colors.background)) {
        Column(Modifier.fillMaxSize()) {
            HavenHeader("Field Notebook", onBack = back, largeTitle = false, actions = {
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort", tint = Haven.colors.accent) }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }, modifier = Modifier.background(Haven.colors.surface)) {
                        NoteSort.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label, color = if (s == sort) Haven.colors.accent else Haven.colors.text) },
                                onClick = { scope.launch { container.settings.setNotesSort(s.name) }; sortMenu = false },
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = Haven.colors.accent) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(Haven.colors.surface)) {
                        DropdownMenuItem(
                            text = { Text("Export notes", color = Haven.colors.text) },
                            leadingIcon = { Icon(Icons.Rounded.FileUpload, null, tint = Haven.colors.accent) },
                            onClick = { menu = false; exporter.launch("haven-notes.json") },
                        )
                        DropdownMenuItem(
                            text = { Text("Import notes", color = Haven.colors.text) },
                            leadingIcon = { Icon(Icons.Rounded.FileDownload, null, tint = Haven.colors.accent) },
                            onClick = { menu = false; importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        )
                    }
                }
            })
            LazyColumn(
                contentPadding = PaddingValues(start = Dimens.gutter, end = Dimens.gutter, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.gapSmall),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    SearchField(query, { query = it }, placeholder = "Search notes")
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
                        HavenChip("All", selected = tag == null && !favourites, onClick = { tag = null; favourites = false })
                        HavenChip("Favourites", selected = favourites, onClick = { favourites = !favourites })
                        NoteTag.entries.forEach { t -> HavenChip(t.label, selected = tag == t, onClick = { tag = if (tag == t) null else t }) }
                    }
                }
                val all = notes
                when {
                    all == null -> Unit
                    all.isEmpty() -> item {
                        EmptyState(Icons.Rounded.Edit, "No notes yet", "Notes are saved on this device.", "New note", onAction = { go(Routes.note(Routes.NEW_NOTE_ID)) })
                    }
                    filtered.isEmpty() -> item { EmptyState(Icons.Rounded.Edit, "No matching notes") }
                    else -> items(filtered, key = { it.id }) { n -> NoteRow(n) { go(Routes.note(n.id)) } }
                }
            }
        }
        FloatingActionButton(
            onClick = { go(Routes.note(Routes.NEW_NOTE_ID)) },
            containerColor = Haven.colors.accent,
            contentColor = Haven.colors.onAccent,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Dimens.gutter),
        ) { Icon(Icons.Rounded.Add, contentDescription = "New note") }
    }
}

private const val MAX_IMPORT_BYTES = 20 * 1024 * 1024

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    var total = 0
    while (total < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - total))
        if (n < 0) break
        out.write(buf, 0, n)
        total += n
    }
    return out.toByteArray()
}

@Composable
private fun NoteRow(n: Note, onClick: () -> Unit) {
    HavenCard(Modifier.fillMaxWidth(), onClick = onClick, onClickLabel = "Open note") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(n.displayTitle, style = Haven.type.headline, color = Haven.colors.text, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (n.pinned) Icon(Icons.Rounded.PushPin, contentDescription = "Pinned", tint = Haven.colors.accent, modifier = Modifier.size(18.dp))
            if (n.favourite) Icon(Icons.Rounded.Star, contentDescription = "Favourite", tint = Haven.colors.accent, modifier = Modifier.size(18.dp))
        }
        val preview = (if (n.title.isBlank()) n.body.lineSequence().drop(1).joinToString(" ") else n.body).trim()
        if (preview.isNotEmpty()) {
            Text(preview, style = Haven.type.footnote, color = Haven.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(n.updatedAt), style = Haven.type.caption, color = Haven.colors.textSecondary)
            n.tag?.let {
                Spacer(Modifier.width(8.dp))
                Text("· ${it.label}", style = Haven.type.caption, color = Haven.colors.accent)
            }
            if (n.hasLocation) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.LocationOn, contentDescription = "Has location", tint = Haven.colors.textSecondary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ---- Editor --------------------------------------------------------------------------------

@Composable
fun NoteEditorScreen(noteId: Long, back: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val show = LocalShowMessage.current
    val scope = rememberCoroutineScope()
    val vm: NoteEditorViewModel = viewModel(
        key = "note-$noteId",
        factory = viewModelFactory { initializer { NoteEditorViewModel(container.notes, noteId, container.appScope) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var attaching by remember { mutableStateOf(false) }
    val close: () -> Unit = { scope.launch { vm.save(); back() } }
    BackHandler(onBack = close)

    Column(Modifier.fillMaxSize().background(Haven.colors.background)) {
        val note = state.note
        HavenHeader("", onBack = close, largeTitle = false, actions = {
            if (note != null) {
                IconButton(onClick = { vm.edit { it.copy(pinned = !it.pinned) } }) {
                    Icon(Icons.Rounded.PushPin, contentDescription = if (note.pinned) "Unpin" else "Pin",
                        tint = if (note.pinned) Haven.colors.accent else Haven.colors.textSecondary)
                }
                IconButton(onClick = { vm.edit { it.copy(favourite = !it.favourite) } }) {
                    Icon(if (note.favourite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (note.favourite) "Remove from favourites" else "Add to favourites", tint = Haven.colors.accent)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = Haven.colors.accent) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(Haven.colors.surface)) {
                        val text = noteShareText(note)
                        DropdownMenuItem(text = { Text("Share", color = Haven.colors.text) }, leadingIcon = { Icon(Icons.Rounded.Share, null, tint = Haven.colors.accent) },
                            onClick = { menu = false; SystemActions.share(context, text, "Share note") }, enabled = !note.isBlank)
                        DropdownMenuItem(text = { Text("Copy", color = Haven.colors.text) }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, tint = Haven.colors.accent) },
                            onClick = { menu = false; SystemActions.copy(context, "Note", text); show("Note copied") }, enabled = !note.isBlank)
                        DropdownMenuItem(text = { Text("Delete", color = Haven.colors.emergency) }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Haven.colors.emergency) },
                            onClick = { menu = false; confirmDelete = true })
                    }
                }
            }
        })
        when {
            state.loading -> Unit
            state.missing || note == null -> EmptyState(Icons.Rounded.Edit, "Note not found")
            else -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.gutter),
                verticalArrangement = Arrangement.spacedBy(Dimens.gap),
            ) {
                PlainField(note.title, { v -> vm.edit { it.copy(title = v.take(500)) } }, "Title", Haven.type.title, singleLine = true)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
                    NoteTag.entries.forEach { t ->
                        HavenChip(t.label, selected = note.tag == t, onClick = { vm.edit { it.copy(tag = if (it.tag == t) null else t) } })
                    }
                }
                PlainField(note.body, { v -> vm.edit { it.copy(body = v.take(200_000)) } }, "Write here…", Haven.type.body, singleLine = false)
                if (note.hasLocation) {
                    HavenCard(Modifier.fillMaxWidth()) {
                        val c = Coordinate(note.latitude!!, note.longitude!!)
                        Text(Geo.formatDecimal(c), style = Haven.type.callout, color = Haven.colors.text)
                        note.accuracyMeters?.let { Text(formatAccuracy(it), style = Haven.type.footnote, color = Haven.colors.textSecondary) }
                        SecondaryButton("Remove location", onClick = { vm.edit { it.copy(latitude = null, longitude = null, accuracyMeters = null) } })
                    }
                } else if (attaching) {
                    AttachLocation(onAttach = { lat, lon, acc ->
                        vm.edit { it.copy(latitude = lat, longitude = lon, accuracyMeters = acc) }
                        attaching = false
                    }, onCancel = { attaching = false })
                } else {
                    SecondaryButton("Attach my location", icon = Icons.Rounded.LocationOn, onClick = { attaching = true })
                }
                state.saveError?.let { Hint(it, color = Haven.colors.emergency) }
                Hint("Created ${formatTime(note.createdAt)} · Edited ${formatTime(note.updatedAt)}")
                Spacer(Modifier.size(Dimens.gapLarge))
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog("Delete this note?", "This can't be undone.", "Delete", destructive = true,
            onConfirm = { scope.launch { vm.delete(); show("Note deleted"); back() } }, onDismiss = { confirmDelete = false })
    }
}

@Composable
private fun AttachLocation(onAttach: (Double, Double, Float?) -> Unit, onCancel: () -> Unit) {
    val live = rememberLiveLocation()
    HavenCard(Modifier.fillMaxWidth()) {
        val fix = live.fix
        if (fix == null) {
            Text(
                when (live.availability) {
                    app.projecthaven.data.device.LocationAvailability.PERMISSION_REQUIRED -> "Location permission is needed"
                    app.projecthaven.data.device.LocationAvailability.PROVIDERS_OFF -> "Location is turned off"
                    else -> "Searching for GPS…"
                },
                style = Haven.type.callout, color = Haven.colors.text,
            )
            if (live.availability == app.projecthaven.data.device.LocationAvailability.PERMISSION_REQUIRED) {
                val request = app.projecthaven.ui.rememberLocationPermissionRequest()
                SecondaryButton("Allow location", onClick = request)
            }
        } else {
            Text("${Geo.formatDecimal(fix.coordinate)} · ${formatAccuracy(fix.accuracyMeters)}", style = Haven.type.callout, color = Haven.colors.text)
            SecondaryButton("Attach", onClick = { onAttach(fix.coordinate.latitude, fix.coordinate.longitude, fix.accuracyMeters) })
        }
        Text("Cancel", style = Haven.type.callout, color = Haven.colors.accent,
            modifier = Modifier.padding(top = 8.dp).clickable(role = Role.Button, onClick = onCancel))
    }
}

@Composable
private fun PlainField(value: String, onChange: (String) -> Unit, placeholder: String, style: androidx.compose.ui.text.TextStyle, singleLine: Boolean) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = style.copy(color = Haven.colors.text),
        singleLine = singleLine,
        cursorBrush = SolidColor(Haven.colors.accent),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style, color = Haven.colors.textSecondary)
                inner()
            }
        },
    )
}

private fun noteShareText(n: Note): String = buildString {
    if (n.title.isNotBlank()) appendLine(n.title.trim())
    if (n.body.isNotBlank()) appendLine(n.body.trim())
    if (n.latitude != null && n.longitude != null) {
        val c = Coordinate(n.latitude, n.longitude)
        appendLine(Geo.formatDecimal(c))
        append(Geo.openStreetMapLink(c))
    }
}.trim()
