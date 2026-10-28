// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.projecthaven.data.device.BatteryInfo
import app.projecthaven.data.device.LocationAvailability
import app.projecthaven.domain.guides.CollectionId
import app.projecthaven.domain.maps.PackState
import app.projecthaven.domain.maps.RoutingSupport
import app.projecthaven.domain.sensors.FixQuality
import app.projecthaven.domain.sensors.FixRules
import app.projecthaven.ui.LocalAppContainer
import app.projecthaven.ui.components.GroupDivider
import app.projecthaven.ui.components.HavenCard
import app.projecthaven.ui.components.HavenScreen
import app.projecthaven.ui.components.ListGroup
import app.projecthaven.ui.components.ListRow
import app.projecthaven.ui.components.SectionLabel
import app.projecthaven.ui.components.StatusLevel
import app.projecthaven.ui.components.StatusRow
import app.projecthaven.ui.components.SystemActions
import app.projecthaven.ui.navigation.Routes
import app.projecthaven.ui.rememberLocationPermissionRequest
import app.projecthaven.ui.screens.Brand
import app.projecthaven.ui.screens.formatAccuracy
import app.projecthaven.ui.screens.rememberLiveLocation
import app.projecthaven.ui.screens.rememberNow
import app.projecthaven.ui.theme.Dimens
import app.projecthaven.ui.theme.Haven

@Composable
fun HomeScreen(go: (String) -> Unit) {
    HavenScreen(
        title = Brand.APP_NAME,
        actions = {
            IconButton(onClick = { go(Routes.SETTINGS) }) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Haven.colors.accent)
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            ActionTile("SOS", "Call 112, alert contacts", Icons.Rounded.Warning, emergency = true, Modifier.weight(1f)) { go(Routes.SOS) }
            ActionTile("First Aid", "Offline guide", Icons.Rounded.LocalHospital, emergency = false, Modifier.weight(1f)) {
                go(Routes.collection(CollectionId.FIRST_AID.key))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            ActionTile("Offline Maps", "Regions and places", Icons.Rounded.Map, emergency = false, Modifier.weight(1f)) { go(Routes.MAPS) }
            ActionTile("Signal & Light", "Torch, Morse, siren", Icons.Rounded.FlashOn, emergency = false, Modifier.weight(1f)) { go(Routes.SIGNAL) }
        }

        SectionLabel("Essentials")
        ListGroup {
            ListRow("Compass", icon = Icons.Rounded.Explore, chevron = true, onClick = { go(Routes.COMPASS) })
            GroupDivider()
            ListRow("Survival Handbook", icon = Icons.AutoMirrored.Rounded.MenuBook, chevron = true,
                onClick = { go(Routes.collection(CollectionId.SURVIVAL.key)) })
            GroupDivider()
            ListRow("Emergency Cookbook", icon = Icons.Rounded.Restaurant, chevron = true, onClick = { go(Routes.COOKBOOK) })
            GroupDivider()
            ListRow("Field Notebook", icon = Icons.Rounded.Edit, chevron = true, onClick = { go(Routes.NOTEBOOK) })
            GroupDivider()
            ListRow("All field tools", icon = Icons.Rounded.Build, chevron = true, onClick = { go(Routes.TOOLS) })
        }

        SectionLabel("Status")
        StatusCard(go)
    }
}

@Composable
private fun ActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    emergency: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = if (emergency) Haven.colors.emergency else Haven.colors.surface
    val fg = if (emergency) Haven.colors.onEmergency else Haven.colors.text
    val sub = if (emergency) Haven.colors.onEmergency.copy(alpha = 0.85f) else Haven.colors.textSecondary
    val iconTint = if (emergency) Haven.colors.onEmergency else Haven.colors.accent
    HavenCard(
        modifier = modifier.height(124.dp).semantics { contentDescription = "$title. $subtitle" },
        onClick = onClick,
        color = bg,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
        Spacer(Modifier.weight(1f))
        Text(title, style = Haven.type.headline, color = fg)
        Text(subtitle, style = Haven.type.footnote, color = sub, maxLines = 1)
    }
}

@Composable
private fun StatusCard(go: (String) -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val live = rememberLiveLocation()
    val now = rememberNow(2000)
    val requestLocation = rememberLocationPermissionRequest()
    val battery by remember(container) { container.battery.updates() }.collectAsStateWithLifecycle(initialValue = null as BatteryInfo?)
    val regions by container.maps.regions.collectAsStateWithLifecycle(initialValue = emptyList())
    val bundled = remember(container) { container.bundledMaps.packs }

    ListGroup {
        when (live.availability) {
            LocationAvailability.PERMISSION_REQUIRED ->
                StatusRow("Location", "Permission required", StatusLevel.ATTENTION, onClick = requestLocation)
            LocationAvailability.PROVIDERS_OFF ->
                StatusRow("Location", "Turned off", StatusLevel.ATTENTION, onClick = { SystemActions.openLocationSettings(context) })
            LocationAvailability.READY -> StatusRow("Location", "Ready", StatusLevel.READY)
        }
        GroupDivider(inset = 36.dp)
        val fix = live.fix
        when {
            live.availability != LocationAvailability.READY -> StatusRow("GPS fix", "Unavailable", StatusLevel.NEUTRAL)
            fix == null -> StatusRow("GPS fix", "Searching…", StatusLevel.ATTENTION, onClick = { go(Routes.COORDINATES) })
            fix.ageMs(now) > FixRules.FRESH_MS -> StatusRow("GPS fix", "Last fix is old", StatusLevel.ATTENTION, onClick = { go(Routes.COORDINATES) })
            else -> {
                val level = when (FixRules.quality(fix.accuracyMeters)) {
                    FixQuality.GOOD -> StatusLevel.READY
                    FixQuality.FAIR, FixQuality.POOR -> StatusLevel.ATTENTION
                }
                val label = when (FixRules.quality(fix.accuracyMeters)) {
                    FixQuality.GOOD -> "Ready · ${formatAccuracy(fix.accuracyMeters)}"
                    FixQuality.FAIR -> "Low accuracy · ${formatAccuracy(fix.accuracyMeters)}"
                    FixQuality.POOR -> "Poor accuracy · ${formatAccuracy(fix.accuracyMeters)}"
                }
                StatusRow("GPS fix", label, level, onClick = { go(Routes.COORDINATES) })
            }
        }
        GroupDivider(inset = 36.dp)
        val installedCount = regions.count { it.installed != null } + bundled.size
        if (installedCount > 0) {
            StatusRow("Offline maps", if (installedCount == 1) "Ready · 1 region" else "Ready · $installedCount regions", StatusLevel.READY, onClick = { go(Routes.MAPS) })
        } else {
            StatusRow("Offline maps", "Download required", StatusLevel.ATTENTION, onClick = { go(Routes.DOWNLOADS) })
        }
        GroupDivider(inset = 36.dp)
        val routingReady = regions.any { it.capabilities?.routing == RoutingSupport.READY }
        StatusRow("Offline routing", if (routingReady) "Ready" else "Unavailable", if (routingReady) StatusLevel.READY else StatusLevel.NEUTRAL)

        val active = regions.firstOrNull { it.transfer is PackState.Downloading || it.transfer is PackState.Queued || it.transfer is PackState.Verifying }
        if (active != null) {
            GroupDivider(inset = 36.dp)
            val value = when (val t = active.transfer) {
                is PackState.Downloading -> "Downloading ${(t.fraction * 100).toInt()}%"
                PackState.Verifying -> "Verifying"
                else -> "Waiting"
            }
            StatusRow("Map download", value, StatusLevel.ATTENTION, onClick = { go(Routes.DOWNLOADS) })
        }
        val failed = regions.firstOrNull { it.transfer is PackState.Failed }
        if (active == null && failed != null) {
            GroupDivider(inset = 36.dp)
            StatusRow("Map download", "Error", StatusLevel.ERROR, onClick = { go(Routes.DOWNLOADS) })
        }

        GroupDivider(inset = 36.dp)
        val b = battery
        val pct = b?.percent
        when {
            b == null || pct == null -> StatusRow("Battery", "Unavailable", StatusLevel.NEUTRAL)
            else -> {
                val level = when {
                    b.charging || pct > 30 -> StatusLevel.READY
                    pct > 15 -> StatusLevel.ATTENTION
                    else -> StatusLevel.ERROR
                }
                StatusRow("Battery", "$pct%" + if (b.charging) " · Charging" else "", level, onClick = { go(Routes.BATTERY) })
            }
        }
    }
}
