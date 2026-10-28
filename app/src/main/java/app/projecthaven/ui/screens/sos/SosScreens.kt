// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.screens.sos

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.projecthaven.data.contacts.ContactsRepository
import app.projecthaven.data.contacts.EmergencyContact
import app.projecthaven.data.device.LocationAvailability
import app.projecthaven.data.profile.EmergencyProfile
import app.projecthaven.data.settings.SosPrefs
import app.projecthaven.domain.geo.Geo
import app.projecthaven.domain.sos.CheckIn
import app.projecthaven.domain.sos.CheckInStatus
import app.projecthaven.domain.sos.EmergencyMessage
import app.projecthaven.domain.sos.EmergencyNumbers
import app.projecthaven.domain.sos.LocationForMessage
import app.projecthaven.ui.LocalAppContainer
import app.projecthaven.ui.components.ConfirmDialog
import app.projecthaven.ui.components.EmergencyButton
import app.projecthaven.ui.components.GroupDivider
import app.projecthaven.ui.components.HavenCard
import app.projecthaven.ui.components.HavenScreen
import app.projecthaven.ui.components.HavenSwitch
import app.projecthaven.ui.components.HavenTextField
import app.projecthaven.ui.components.Hint
import app.projecthaven.ui.components.ListGroup
import app.projecthaven.ui.components.ListRow
import app.projecthaven.ui.components.LocalShowMessage
import app.projecthaven.ui.components.PrimaryButton
import app.projecthaven.ui.components.SecondaryButton
import app.projecthaven.ui.components.SectionLabel
import app.projecthaven.ui.components.SystemActions
import app.projecthaven.ui.navigation.Routes
import app.projecthaven.ui.rememberLocationPermissionRequest
import app.projecthaven.ui.screens.LiveLocation
import app.projecthaven.ui.screens.formatAccuracy
import app.projecthaven.ui.screens.formatAge
import app.projecthaven.ui.screens.rememberLiveLocation
import app.projecthaven.ui.screens.rememberNow
import app.projecthaven.ui.theme.Dimens
import app.projecthaven.ui.theme.Haven
import kotlinx.coroutines.launch

@Composable
fun SosScreen(back: () -> Unit, go: (String) -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val show = LocalShowMessage.current
    val scope = rememberCoroutineScope()
    val live = rememberLiveLocation()
    val now = rememberNow()
    val contacts by container.contacts.contacts.collectAsStateWithLifecycle(initialValue = emptyList())
    val sos by container.settings.sos.collectAsStateWithLifecycle(initialValue = SosPrefs(true, ""))
    val profile by container.profile.profile.collectAsStateWithLifecycle(initialValue = EmergencyProfile())
    val checkIn by container.settings.checkIn.collectAsStateWithLifecycle(initialValue = null)
    var editMessage by remember { mutableStateOf(false) }

    val message = EmergencyMessage.build(
        location = live.fix?.let { LocationForMessage(it.coordinate, it.accuracyMeters, it.ageMs(now)) },
        includeLocation = sos.includeLocation,
        extraNote = sos.extraNote,
        senderName = profile.name,
    )

    HavenScreen("SOS", onBack = back, largeTitle = true) {
        EmergencyButton(
            "Call ${EmergencyNumbers.INDIA_EMERGENCY}",
            onClick = { if (!SystemActions.dial(context, EmergencyNumbers.INDIA_EMERGENCY)) show("No phone app is available on this device") },
            icon = Icons.Rounded.Phone,
            modifier = Modifier.fillMaxWidth(),
        )
        Hint("India emergency number. Opens your phone app to place the call.")

        SectionLabel("Your location")
        LocationCard(live, now)

        SectionLabel("Alert contacts")
        if (contacts.isEmpty()) {
            ListGroup {
                ListRow("Add emergency contacts", icon = Icons.Rounded.Add, chevron = true, onClick = { go(Routes.CONTACTS) })
            }
        } else {
            ListGroup {
                contacts.forEachIndexed { i, c ->
                    if (i > 0) GroupDivider(inset = 16.dp)
                    ContactActionRow(
                        c,
                        onCall = { if (!SystemActions.dial(context, c.phone)) show("No phone app is available") },
                        onMessage = {
                            if (SystemActions.composeSms(context, c.phone, message)) {
                                show("Opened in your messaging app. Not sent until you send it there.")
                            } else show("No messaging app is available")
                        },
                    )
                }
            }
            HavenCard {
                Text("Message", style = Haven.type.caption, color = Haven.colors.textSecondary)
                Text(message, style = Haven.type.footnote, color = Haven.colors.text)
                TextButton(onClick = { editMessage = true }) {
                    Text("Edit message", style = Haven.type.callout, color = Haven.colors.accent)
                }
            }
        }

        SectionLabel("More")
        ListGroup {
            ListRow("Emergency contacts", icon = Icons.Rounded.Contacts, value = contacts.size.toString(), chevron = true, onClick = { go(Routes.CONTACTS) })
            GroupDivider()
            ListRow("Medical profile", icon = Icons.Rounded.Person, value = if (profile.isEmpty) "Not set" else "Saved", chevron = true, onClick = { go(Routes.PROFILE) })
            GroupDivider()
            val ci = checkIn
            val status = CheckIn.status(ci?.deadlineEpochMs, System.currentTimeMillis())
            ListRow(
                "Safety check-in", icon = Icons.Rounded.Timer,
                value = when (status) { CheckInStatus.Idle -> "Off"; is CheckInStatus.Active -> "Active"; is CheckInStatus.Overdue -> "Overdue" },
                valueColor = if (status is CheckInStatus.Overdue) Haven.colors.emergency else Haven.colors.textSecondary,
                chevron = true, onClick = { go(Routes.CHECK_IN) },
            )
            GroupDivider()
            ListRow("Signal & Light", icon = Icons.Rounded.FlashOn, chevron = true, onClick = { go(Routes.SIGNAL) })
            GroupDivider()
            ListRow("First aid", icon = Icons.Rounded.LocalHospital, chevron = true, onClick = { go(Routes.collection("first_aid")) })
        }
    }

    if (editMessage) {
        MessageSettingsDialog(sos, onDismiss = { editMessage = false }, onSave = { scope.launch { container.settings.setSos(it) } })
    }
}

@Composable
fun LocationCard(live: LiveLocation, now: Long) {
    val context = LocalContext.current
    val show = LocalShowMessage.current
    val request = rememberLocationPermissionRequest()
    HavenCard {
        when {
            live.availability == LocationAvailability.PERMISSION_REQUIRED -> {
                Text("Location permission is needed", style = Haven.type.body, color = Haven.colors.text)
                PrimaryButton("Allow location", onClick = request, icon = Icons.Rounded.MyLocation)
            }
            live.availability == LocationAvailability.PROVIDERS_OFF -> {
                Text("Location is turned off", style = Haven.type.body, color = Haven.colors.text)
                SecondaryButton("Open location settings", onClick = { SystemActions.openLocationSettings(context) })
            }
            live.fix == null -> {
                Text("Searching for GPS…", style = Haven.type.body, color = Haven.colors.text)
                Hint("Go outdoors or near a window for a faster fix.")
            }
            else -> {
                val fix = requireNotNull(live.fix)
                val text = Geo.formatDecimal(fix.coordinate)
                Text(text, style = Haven.type.title, color = Haven.colors.text)
                Text("${formatAccuracy(fix.accuracyMeters)} · ${formatAge(fix.ageMs(now))}", style = Haven.type.footnote, color = Haven.colors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton("Copy", icon = Icons.Rounded.ContentCopy, modifier = Modifier.weight(1f), onClick = {
                        SystemActions.copy(context, "Coordinates", text); show("Coordinates copied")
                    })
                    SecondaryButton("Share", icon = Icons.Rounded.Share, modifier = Modifier.weight(1f), onClick = {
                        SystemActions.share(context, "$text\n${Geo.openStreetMapLink(fix.coordinate)}", "Share location")
                    })
                }
            }
        }
    }
}

@Composable
private fun ContactActionRow(c: EmergencyContact, onCall: () -> Unit, onMessage: () -> Unit) {
    ListRow(
        title = c.name,
        subtitle = c.phone,
        trailing = {
            Row {
                IconButton(onClick = onMessage) { Icon(Icons.Rounded.Sms, contentDescription = "Message ${c.name}", tint = Haven.colors.accent) }
                IconButton(onClick = onCall) { Icon(Icons.Rounded.Phone, contentDescription = "Call ${c.name}", tint = Haven.colors.accent) }
            }
        },
    )
}

@Composable
private fun MessageSettingsDialog(current: SosPrefs, onDismiss: () -> Unit, onSave: (SosPrefs) -> Unit) {
    var include by remember { mutableStateOf(current.includeLocation) }
    var note by remember { mutableStateOf(current.extraNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Haven.colors.surface,
        title = { Text("Emergency message", style = Haven.type.headline, color = Haven.colors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                ListRow("Include my location", trailing = { HavenSwitch(include) { include = it } })
                HavenTextField(note, { note = it.take(300) }, placeholder = "Optional extra line", label = "Extra line", singleLine = false, minLines = 2)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(SosPrefs(include, note)); onDismiss() }) { Text("Save", color = Haven.colors.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Haven.colors.accent) } },
    )
}

// ---- Contacts ------------------------------------------------------------------------------

@Composable
fun ContactsScreen(back: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val show = LocalShowMessage.current
    val scope = rememberCoroutineScope()
    val contacts by container.contacts.contacts.collectAsStateWithLifecycle(initialValue = emptyList())
    var manual by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<EmergencyContact?>(null) }
    var confirmDelete by remember { mutableStateOf<EmergencyContact?>(null) }

    // Picking one phone number grants temporary access to that row only; no contacts permission.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) return@rememberLauncherForActivityResult
        val picked = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) Pair(c.getString(0).orEmpty(), c.getString(1).orEmpty()) else null }
        }.getOrNull()
        if (picked == null) { show("Couldn't read that contact"); return@rememberLauncherForActivityResult }
        scope.launch {
            when (val r = container.contacts.add(picked.first, picked.second)) {
                is ContactsRepository.AddResult.Added -> show("Added ${picked.first.ifBlank { picked.second }}")
                is ContactsRepository.AddResult.Invalid -> show(r.reason)
            }
        }
    }

    HavenScreen("Emergency contacts", onBack = back, largeTitle = false) {
        if (contacts.isEmpty()) {
            Hint("Add people you trust. You can call or message them from SOS.")
        } else {
            ListGroup {
                contacts.forEachIndexed { i, c ->
                    if (i > 0) GroupDivider(inset = 16.dp)
                    ListRow(
                        c.name, subtitle = c.phone,
                        trailing = if (c.isPrimary) {
    @Composable {
        Icon(Icons.Rounded.Star, contentDescription = "Primary contact", tint = Haven.colors.accent)
    }
} else null,
                        onClick = { selected = c },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton("Choose contact", icon = Icons.Rounded.Contacts, modifier = Modifier.weight(1f), onClick = {
                val ok = runCatching {
                    picker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                }.isSuccess
                if (!ok) show("No contacts app is available")
            })
            SecondaryButton("Enter number", modifier = Modifier.weight(1f), onClick = { manual = true })
        }
        Hint("Saved only on this device.")
    }

    selected?.let { c ->
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = Haven.colors.surface,
            title = { Text(c.name, style = Haven.type.headline, color = Haven.colors.text) },
            text = { Text(c.phone, style = Haven.type.body, color = Haven.colors.textSecondary) },
            confirmButton = {
                Row {
                    if (!c.isPrimary) TextButton(onClick = { scope.launch { container.contacts.makePrimary(c.id) }; selected = null }) {
                        Text("Make primary", color = Haven.colors.accent)
                    }
                    TextButton(onClick = { confirmDelete = c; selected = null }) { Text("Remove", color = Haven.colors.emergency) }
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Close", color = Haven.colors.accent) } },
        )
    }
    confirmDelete?.let { c ->
        ConfirmDialog("Remove ${c.name}?", null, "Remove", destructive = true,
            onConfirm = { scope.launch { container.contacts.delete(c.id) } }, onDismiss = { confirmDelete = null })
    }
    if (manual) ManualContactDialog(onDismiss = { manual = false }) { name, phone ->
        scope.launch {
            when (val r = container.contacts.add(name, phone)) {
                is ContactsRepository.AddResult.Added -> { manual = false; show("Contact added") }
                is ContactsRepository.AddResult.Invalid -> show(r.reason)
            }
        }
    }
}

@Composable
private fun ManualContactDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Haven.colors.surface,
        title = { Text("Add contact", style = Haven.type.headline, color = Haven.colors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.gap)) {
                HavenTextField(name, { name = it.take(80) }, placeholder = "Name")
                HavenTextField(phone, { phone = it.take(20) }, placeholder = "Phone number", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, phone) }, enabled = phone.isNotBlank()) { Text("Add", color = Haven.colors.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Haven.colors.accent) } },
    )
}

// ---- Medical profile ---------------------------------------------------------------------

@Composable
fun ProfileScreen(back: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val show = LocalShowMessage.current
    val scope = rememberCoroutineScope()
    val stored by container.profile.profile.collectAsStateWithLifecycle(initialValue = null)
    var draft by remember { mutableStateOf<EmergencyProfile?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(stored) { if (draft == null && stored != null) draft = stored }
    val p = draft ?: EmergencyProfile()

    HavenScreen("Medical profile", onBack = back, largeTitle = false) {
        Hint("Every field is optional. Stored only on this device.")
        HavenTextField(p.name, { draft = p.copy(name = it) }, placeholder = "Name", label = "Name")
        HavenTextField(p.bloodGroup, { draft = p.copy(bloodGroup = it.take(10)) }, placeholder = "e.g. B+", label = "Blood group")
        HavenTextField(p.allergies, { draft = p.copy(allergies = it) }, placeholder = "None entered", label = "Allergies", singleLine = false)
        HavenTextField(p.medications, { draft = p.copy(medications = it) }, placeholder = "None entered", label = "Medications", singleLine = false)
        HavenTextField(p.conditions, { draft = p.copy(conditions = it) }, placeholder = "None entered", label = "Medical conditions", singleLine = false)
        HavenTextField(p.emergencyNotes, { draft = p.copy(emergencyNotes = it) }, placeholder = "Anything responders should know", label = "Notes", singleLine = false, minLines = 2)

        PrimaryButton("Save", modifier = Modifier.fillMaxWidth(), enabled = draft != null && draft != stored, onClick = {
            scope.launch { container.profile.save(p); show("Profile saved") }
        })
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton("Share", icon = Icons.Rounded.Share, modifier = Modifier.weight(1f), enabled = !p.isEmpty, onClick = {
                SystemActions.share(context, p.shareText(), "Share medical profile")
            })
            SecondaryButton("Delete", modifier = Modifier.weight(1f), enabled = stored?.isEmpty == false, tint = Haven.colors.emergency, onClick = { confirmDelete = true })
        }
    }
    if (confirmDelete) {
        ConfirmDialog("Delete medical profile?", "This removes all fields from this device.", "Delete", destructive = true,
            onConfirm = { scope.launch { container.profile.delete(); draft = EmergencyProfile(); show("Profile deleted") } },
            onDismiss = { confirmDelete = false })
    }
}
