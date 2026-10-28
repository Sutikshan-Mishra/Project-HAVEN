// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.projecthaven.BuildConfig
import app.projecthaven.R
import app.projecthaven.data.settings.ThemeMode
import app.projecthaven.ui.LocalAppContainer
import app.projecthaven.ui.components.ConfirmDialog
import app.projecthaven.ui.components.GroupDivider
import app.projecthaven.ui.components.HavenScreen
import app.projecthaven.ui.components.Hint
import app.projecthaven.ui.components.ListGroup
import app.projecthaven.ui.components.ListRow
import app.projecthaven.ui.components.LocalShowMessage
import app.projecthaven.ui.components.SecondaryButton
import app.projecthaven.ui.components.SectionLabel
import app.projecthaven.ui.components.Segmented
import app.projecthaven.ui.components.SystemActions
import app.projecthaven.ui.navigation.Routes
import app.projecthaven.ui.rememberResumeTick
import app.projecthaven.ui.screens.Brand
import app.projecthaven.ui.screens.formatBytes
import app.projecthaven.ui.theme.Haven
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen(back: () -> Unit, go: (String) -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val theme by container.settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.DARK)

    HavenScreen("Settings", onBack = back, largeTitle = true) {
        SectionLabel("Appearance")
        Segmented(
            options = ThemeMode.entries.toList(),
            selected = theme,
            label = { it.label },
            onSelect = { mode -> scope.launch { container.settings.setThemeMode(mode) } },
        )

        SectionLabel("Preferences")
        ListGroup {
            ListRow("Maps and downloads", icon = Icons.Rounded.Map, chevron = true, onClick = { go(Routes.DOWNLOADS) })
            GroupDivider()
            ListRow("Safety and permissions", icon = Icons.Rounded.Security, chevron = true, onClick = { go(Routes.SETTINGS_PERMISSIONS) })
            GroupDivider()
            ListRow("Storage and data", icon = Icons.Rounded.Storage, chevron = true, onClick = { go(Routes.SETTINGS_STORAGE) })
            GroupDivider()
            ListRow("Privacy", icon = Icons.Rounded.Lock, chevron = true, onClick = { go(Routes.SETTINGS_PRIVACY) })
            GroupDivider()
            ListRow("About", icon = Icons.Rounded.Info, chevron = true, onClick = { go(Routes.SETTINGS_ABOUT) })
        }

        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${Brand.APP_NAME} ${BuildConfig.VERSION_NAME}", style = Haven.type.footnote, color = Haven.colors.textSecondary)
            Text(Brand.DEVELOPER_CREDIT, style = Haven.type.footnote, color = Haven.colors.textSecondary)
        }
    }
}

@Composable
fun PermissionsScreen(back: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val tick = rememberResumeTick()
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    val fine = remember(tick) { granted(Manifest.permission.ACCESS_FINE_LOCATION) }
    val coarse = remember(tick) { granted(Manifest.permission.ACCESS_COARSE_LOCATION) }
    val notifications = remember(tick) { container.checkIn.notificationsAllowed() }
    val exact = remember(tick) { container.checkIn.canScheduleExact() }

    HavenScreen("Safety and permissions", onBack = back, largeTitle = false) {
        Hint("HAVEN asks for a permission only when you use the feature that needs it.")
        ListGroup {
            ListRow(
                "Location", subtitle = "Coordinates, SOS messages, maps",
                value = when { fine -> "Precise"; coarse -> "Approximate"; else -> "Not allowed" },
            )
            GroupDivider(inset = 16.dp)
            ListRow(
                "Notifications", subtitle = "Check-in reminders",
                value = if (notifications) "Allowed" else "Not allowed",
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                GroupDivider(inset = 16.dp)
                ListRow(
                    "Alarms and reminders", subtitle = "On-time check-in reminders",
                    value = if (exact) "Allowed" else "Not allowed",
                    onClick = if (!exact) ({ SystemActions.openExactAlarmSettings(context); Unit }) else null,
                )
            }
            GroupDivider(inset = 16.dp)
            ListRow("Flashlight", subtitle = "Uses the camera flash", value = "No permission needed")
            GroupDivider(inset = 16.dp)
            ListRow("Contacts", subtitle = "You pick one contact at a time", value = "No permission needed")
            GroupDivider(inset = 16.dp)
            ListRow("Calls and SMS", subtitle = "Opens your phone and messaging apps", value = "No permission needed")
        }
        SecondaryButton("Open Android settings", onClick = { SystemActions.openAppSettings(context) }, modifier = Modifier.fillMaxWidth())
        Hint("Calls and SMS depend on your SIM, network and device.")
    }
}

@Composable
fun StorageScreen(back: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val show = LocalShowMessage.current
    var refresh by remember { mutableIntStateOf(0) }
    var notes by remember { mutableIntStateOf(0) }
    var dbBytes by remember { mutableLongStateOf(0L) }
    var mapBytes by remember { mutableLongStateOf(0L) }
    var partialBytes by remember { mutableLongStateOf(0L) }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    val profile by container.profile.profile.collectAsStateWithLifecycle(initialValue = null)
    var confirmPartial by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
        withContext(Dispatchers.IO) {
            notes = container.notes.count()
            dbBytes = listOf("", "-wal", "-shm").sumOf { File(container.databaseFile.path + it).takeIf(File::exists)?.length() ?: 0L }
            mapBytes = container.mapStore.installedBytes()
            partialBytes = container.mapStore.stagingBytes()
            cacheBytes = container.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }

    HavenScreen("Storage and data", onBack = back, largeTitle = false) {
        SectionLabel("Your data")
        ListGroup {
            ListRow("Field notebook", subtitle = if (notes == 1) "1 note" else "$notes notes", value = formatBytes(dbBytes))
            GroupDivider(inset = 16.dp)
            val p = profile
            ListRow("Emergency profile", value = when { p == null -> "…"; p.isEmpty -> "Empty"; else -> "Saved" })
            GroupDivider(inset = 16.dp)
            ListRow("Offline map packs", value = formatBytes(mapBytes))
        }
        Hint("Clearing the cache never removes notes, your profile or installed maps.")
        SectionLabel("Disposable")
        ListGroup {
            ListRow("Cache", value = formatBytes(cacheBytes), trailing = {
                SecondaryButton("Clear", onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { container.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                        refresh++
                        show("Cache cleared")
                    }
                }, enabled = cacheBytes > 0)
            })
            GroupDivider(inset = 16.dp)
            ListRow("Incomplete downloads", value = formatBytes(partialBytes), trailing = {
                SecondaryButton("Remove", onClick = { confirmPartial = true }, enabled = partialBytes > 0)
            })
        }
    }
    if (confirmPartial) {
        ConfirmDialog(
            title = "Remove incomplete downloads?",
            text = "Paused map downloads will start again from the beginning.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                scope.launch {
                    // Stop any paused or queued jobs first; installed packs are untouched.
                    File(container.mapsRoot, "staging").listFiles()?.forEach { container.maps.cancel(it.name) }
                    withContext(Dispatchers.IO) { container.mapStore.discardAllPartial() }
                    refresh++
                }
            },
            onDismiss = { confirmPartial = false },
        )
    }
}

@Composable
fun PrivacyScreen(back: () -> Unit) {
    HavenScreen("Privacy", onBack = back, largeTitle = false) {
        ListGroup {
            PrivacyRow("No account", "HAVEN works without signing in.")
            GroupDivider(inset = 16.dp)
            PrivacyRow("Stored on this device", "Notes, contacts and your profile stay in app storage and are excluded from cloud backup.")
            GroupDivider(inset = 16.dp)
            PrivacyRow("Location", "Read only while a location screen is open. Never stored unless you attach it to a note.")
            GroupDivider(inset = 16.dp)
            PrivacyRow("Nothing sent silently", "Calls, messages and shares always open another app for you to confirm.")
            GroupDivider(inset = 16.dp)
            PrivacyRow("Network use", "Only to fetch the map catalog and map packs you choose to download.")
            GroupDivider(inset = 16.dp)
            PrivacyRow("Not encrypted by HAVEN", "Data relies on Android's device encryption and your screen lock.")
        }
    }
}

@Composable
private fun PrivacyRow(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = Haven.type.callout, color = Haven.colors.text)
        Text(text, style = Haven.type.footnote, color = Haven.colors.textSecondary)
    }
}

@Composable
fun AboutScreen(back: () -> Unit, go: (String) -> Unit) {
    val context = LocalContext.current
    HavenScreen("About", onBack = back, largeTitle = false) {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.ic_haven_mark), contentDescription = null, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(12.dp))
            Text(Brand.APP_NAME, style = Haven.type.title, color = Haven.colors.text)
            Text(Brand.TAGLINE, style = Haven.type.footnote, color = Haven.colors.textSecondary, textAlign = TextAlign.Center)
        }
        ListGroup {
            ListRow("Version", value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            GroupDivider(inset = 16.dp)
            ListRow(Brand.DEVELOPER_CREDIT)
            GroupDivider(inset = 16.dp)
            ListRow("Licence", value = "GPL-3.0-or-later")
            GroupDivider(inset = 16.dp)
            ListRow("Open-source notices", icon = Icons.AutoMirrored.Rounded.MenuBook, chevron = true, onClick = { go(Routes.SETTINGS_LICENSES) })
            if (BuildConfig.REPOSITORY_URL.isNotBlank()) {
                GroupDivider(inset = 16.dp)
                ListRow("Source code", icon = Icons.Rounded.Code, chevron = true, onClick = { SystemActions.openUrl(context, BuildConfig.REPOSITORY_URL) })
            }
            GroupDivider(inset = 16.dp)
            ListRow("Map data", subtitle = "© OpenStreetMap contributors (ODbL) · Map tiles use the OpenMapTiles schema (© OpenMapTiles)")
        }
        Hint("Not affiliated with or endorsed by the Government of India. Not a replacement for emergency services or medical care.")
    }
}

@Composable
fun LicensesScreen(back: () -> Unit) {
    val context = LocalContext.current
    val text = remember {
        runCatching { context.assets.open("licenses/THIRD_PARTY_NOTICES.txt").bufferedReader().use { it.readText() } }
            .getOrDefault("Notices could not be loaded.")
    }
    HavenScreen("Open-source notices", onBack = back, largeTitle = false) {
        Text(text, style = Haven.type.footnote, color = Haven.colors.text)
    }
}
