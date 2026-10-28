// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.screens.sos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.projecthaven.data.profile.EmergencyProfile
import app.projecthaven.data.settings.SosPrefs
import app.projecthaven.domain.sos.CheckIn
import app.projecthaven.domain.sos.CheckInResponse
import app.projecthaven.domain.sos.CheckInStatus
import app.projecthaven.domain.sos.EmergencyMessage
import app.projecthaven.domain.sos.LocationForMessage
import app.projecthaven.ui.LocalAppContainer
import app.projecthaven.ui.components.BigValue
import app.projecthaven.ui.components.EmergencyButton
import app.projecthaven.ui.components.GroupDivider
import app.projecthaven.ui.components.HavenCard
import app.projecthaven.ui.components.HavenChip
import app.projecthaven.ui.components.HavenScreen
import app.projecthaven.ui.components.Hint
import app.projecthaven.ui.components.ListGroup
import app.projecthaven.ui.components.ListRow
import app.projecthaven.ui.components.LocalShowMessage
import app.projecthaven.ui.components.PrimaryButton
import app.projecthaven.ui.components.SecondaryButton
import app.projecthaven.ui.components.SectionLabel
import app.projecthaven.ui.components.Segmented
import app.projecthaven.ui.components.SystemActions
import app.projecthaven.ui.navigation.Routes
import app.projecthaven.ui.rememberNotificationPermissionRequest
import app.projecthaven.ui.rememberResumeTick
import app.projecthaven.ui.screens.rememberLiveLocation
import app.projecthaven.ui.screens.rememberNow
import app.projecthaven.ui.theme.Dimens
import app.projecthaven.ui.theme.Haven
import kotlinx.coroutines.launch

private val durations = listOf(
    "30 min" to 30 * 60_000L,
    "1 h" to 60 * 60_000L,
    "2 h" to 2 * 60 * 60_000L,
    "4 h" to 4 * 60 * 60_000L,
    "8 h" to 8 * 60 * 60_000L,
)

@Composable
fun CheckInScreen(back: () -> Unit, go: (String) -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val show = LocalShowMessage.current
    val scope = rememberCoroutineScope()
    val prefs by container.settings.checkIn.collectAsStateWithLifecycle(initialValue = null)
    rememberNow(1000) // ticks the countdown
    val wallNow = System.currentTimeMillis()
    val tick = rememberResumeTick()
    val exactAllowed = remember(tick) { container.checkIn.canScheduleExact() }
    val notificationsAllowed = remember(tick) { container.checkIn.notificationsAllowed() }
    var chosen by remember { mutableLongStateOf(durations[1].second) }

    val start: () -> Unit = {
        scope.launch {
            container.checkIn.start(chosen)
            show("Check-in started")
        }
    }
    val requestNotifications = rememberNotificationPermissionRequest { granted ->
        if (!granted) show("Without notifications you will only see the alert inside HAVEN")
        start()
    }

    val status = CheckIn.status(prefs?.deadlineEpochMs, wallNow)

    HavenScreen("Safety check-in", onBack = back, largeTitle = false) {
        when (status) {
            CheckInStatus.Idle -> {
                Hint("Set a time to check in. If you don't, HAVEN alerts you and can prepare a message to your contacts.")
                SectionLabel("Check in within")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall), verticalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
                    durations.forEach { (label, ms) -> HavenChip(label, selected = chosen == ms, onClick = { chosen = ms }) }
                }
                SectionLabel("When the time passes")
                val response = prefs?.response ?: CheckInResponse.NOTIFY_AND_PREPARE
                Segmented(
                    options = CheckInResponse.entries.toList(),
                    selected = response,
                    label = { if (it == CheckInResponse.NOTIFY) "Alert me" else "Alert me + prepare SMS" },
                    onSelect = { r -> scope.launch { container.settings.setCheckInResponse(r) } },
                )
                PrimaryButton("Start check-in", modifier = Modifier.fillMaxWidth(), onClick = {
                    if (notificationsAllowed) start() else requestNotifications()
                })
            }
            is CheckInStatus.Active -> {
                HavenCard(Modifier.fillMaxWidth()) {
                    BigValue(CheckIn.formatRemaining(status.remainingMs), null, "until check-in", Modifier.align(Alignment.CenterHorizontally))
                }
                PrimaryButton("I'm safe", modifier = Modifier.fillMaxWidth(), onClick = { scope.launch { container.checkIn.cancel(); show("Check-in ended") } })
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall), modifier = Modifier.fillMaxWidth()) {
                    SecondaryButton("+30 min", modifier = Modifier.weight(1f), onClick = { scope.launch { container.checkIn.extend(30 * 60_000L) } })
                    SecondaryButton("+1 h", modifier = Modifier.weight(1f), onClick = { scope.launch { container.checkIn.extend(60 * 60_000L) } })
                }
            }
            is CheckInStatus.Overdue -> {
                HavenCard(Modifier.fillMaxWidth(), color = Haven.colors.emergency) {
                    Text("Check-in overdue", style = Haven.type.title, color = Haven.colors.onEmergency)
                    Text("Missed by ${CheckIn.formatRemaining(status.overdueMs)}", style = Haven.type.callout, color = Haven.colors.onEmergency)
                }
                PrimaryButton("I'm safe", modifier = Modifier.fillMaxWidth(), onClick = { scope.launch { container.checkIn.cancel(); show("Check-in ended") } })
                if ((prefs?.response ?: CheckInResponse.NOTIFY_AND_PREPARE) == CheckInResponse.NOTIFY_AND_PREPARE) {
                    OverdueAlert(go)
                }
                SecondaryButton("Extend 30 min", modifier = Modifier.fillMaxWidth(), onClick = { scope.launch { container.checkIn.extend(30 * 60_000L) } })
            }
        }

        if (!notificationsAllowed || !exactAllowed) {
            SectionLabel("Reliability")
            ListGroup {
                if (!notificationsAllowed) {
                    ListRow("Notifications are off", subtitle = "You won't be alerted outside HAVEN", chevron = true,
                        onClick = { SystemActions.openAppSettings(context) })
                }
                if (!notificationsAllowed && !exactAllowed) GroupDivider(inset = 16.dp)
                if (!exactAllowed) {
                    ListRow("Reminder may be late", subtitle = "Allow alarms and reminders for on-time alerts", chevron = true,
                        onClick = { SystemActions.openExactAlarmSettings(context) })
                }
            }
        }
        Hint("Battery saving can delay alerts. HAVEN never sends anything automatically.", Modifier.padding(top = 4.dp))
    }
}

/** Lets the user send a prepared alert to each contact, one review at a time. */
@Composable
private fun OverdueAlert(go: (String) -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val show = LocalShowMessage.current
    val live = rememberLiveLocation()
    val now = rememberNow()
    val contacts by container.contacts.contacts.collectAsStateWithLifecycle(initialValue = emptyList())
    val sos by container.settings.sos.collectAsStateWithLifecycle(initialValue = SosPrefs(true, ""))
    val profile by container.profile.profile.collectAsStateWithLifecycle(initialValue = EmergencyProfile())
    val message = EmergencyMessage.build(
        live.fix?.let { LocationForMessage(it.coordinate, it.accuracyMeters, it.ageMs(now)) },
        sos.includeLocation,
        listOf("I missed my safety check-in.", sos.extraNote).filter { it.isNotBlank() }.joinToString(" "),
        profile.name,
    )
    SectionLabel("Alert your contacts")
    if (contacts.isEmpty()) {
        EmergencyButton("Add emergency contacts", onClick = { go(Routes.CONTACTS) }, modifier = Modifier.fillMaxWidth())
        return
    }
    ListGroup {
        contacts.forEachIndexed { i, c ->
            if (i > 0) GroupDivider(inset = 16.dp)
            ListRow(c.name, subtitle = c.phone, trailing = {
                Row {
                    IconButton(onClick = {
                        if (SystemActions.composeSms(context, c.phone, message)) show("Opened in your messaging app. Not sent until you send it there.")
                        else show("No messaging app is available")
                    }) { Icon(Icons.Rounded.Sms, contentDescription = "Message ${c.name}", tint = Haven.colors.accent) }
                    IconButton(onClick = { SystemActions.dial(context, c.phone) }) {
                        Icon(Icons.Rounded.Phone, contentDescription = "Call ${c.name}", tint = Haven.colors.accent)
                    }
                }
            })
        }
    }
}
