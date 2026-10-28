// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.screens.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.projecthaven.BuildConfig
import app.projecthaven.data.maps.BundledMaps
import app.projecthaven.data.maps.CatalogState
import app.projecthaven.data.maps.MapStyle
import app.projecthaven.data.maps.OfflineRouting
import app.projecthaven.data.maps.RegionUi
import app.projecthaven.data.maps.TileSource
import app.projecthaven.domain.geo.Coordinate
import app.projecthaven.domain.geo.Geo
import app.projecthaven.domain.maps.Capabilities
import app.projecthaven.domain.maps.ComponentType
import app.projecthaven.domain.maps.EmergencyKind
import app.projecthaven.domain.maps.InstalledPack
import app.projecthaven.domain.maps.PackState
import app.projecthaven.domain.maps.PackStateMachine
import app.projecthaven.domain.maps.PlaceHit
import app.projecthaven.domain.maps.PlaceRecord
import app.projecthaven.domain.maps.PlaceSearch
import app.projecthaven.domain.maps.RoutingSupport
import app.projecthaven.ui.LocalAppContainer
import app.projecthaven.ui.components.ConfirmDialog
import app.projecthaven.ui.components.EmergencyButton
import app.projecthaven.ui.components.EmptyState
import app.projecthaven.ui.components.GroupDivider
import app.projecthaven.ui.components.HavenCard
import app.projecthaven.ui.components.HavenChip
import app.projecthaven.ui.components.HavenScreen
import app.projecthaven.ui.components.HavenSwitch
import app.projecthaven.ui.components.HavenTextField
import app.projecthaven.ui.components.Hint
import app.projecthaven.ui.components.ListGroup
import app.projecthaven.ui.components.ListRow
import app.projecthaven.ui.components.LocalShowMessage
import app.projecthaven.ui.components.PrimaryButton
import app.projecthaven.ui.components.SearchField
import app.projecthaven.ui.components.SecondaryButton
import app.projecthaven.ui.components.SectionLabel
import app.projecthaven.ui.components.SystemActions
import app.projecthaven.ui.navigation.Routes
import app.projecthaven.ui.screens.formatBytes
import app.projecthaven.ui.screens.rememberLiveLocation
import app.projecthaven.ui.theme.ControlShape
import app.projecthaven.ui.theme.Dimens
import app.projecthaven.ui.theme.Haven
import kotlinx.coroutines.launch

/** Downloaded packs plus any bundled in the APK. */
@Composable
private fun rememberInstalledPacks(): List<InstalledPack> {
    val container = LocalAppContainer.current
    val downloaded by container.mapStore.installed.collectAsStateWithLifecycle()
    val bundled = remember(container) { container.bundledMaps.packs }
    return bundled + downloaded
}

@Composable
private fun rememberPlaces(packs: List<InstalledPack>, type: ComponentType): List<PlaceRecord> {
    val container = LocalAppContainer.current
    val state by produceState(emptyList<PlaceRecord>(), packs, type) {
        value = packs.mapNotNull { container.places.load(it, type) }.flatMap { it.places }
    }
    return state
}

@Composable
fun MapsScreen(go: (String) -> Unit) {
    val container = LocalAppContainer.current
    val packs = rememberInstalledPacks()
    if (packs.none { it.component(ComponentType.TILES) != null }) {
        HavenScreen("Maps") {
            EmptyState(Icons.Rounded.Map, "No offline maps yet", "Download a region to use maps without internet.", "Get maps", onAction = { go(Routes.DOWNLOADS) })
            ListGroup { ListRow("Manage downloads", icon = Icons.Rounded.CloudDownload, chevron = true, onClick = { go(Routes.DOWNLOADS) }) }
        }
        return
    }
    val dark = Haven.colors.isDark
    val style = remember(packs, dark) {
        MapStyle.build(
            packs.mapNotNull { p ->
                val tiles = p.component(ComponentType.TILES) ?: return@mapNotNull null
                val url = if (p.bundled) BundledMaps.assetUrl(tiles.file) else "pmtiles://file://" + container.mapStore.fileOf(p, tiles.file).absolutePath
                TileSource("pack-${p.regionId}", url, p.attribution)
            },
            darkTheme = dark,
            glyphsUrl = container.glyphsUrl,
        )
    }
    val live = rememberLiveLocation()
    val places = rememberPlaces(packs, ComponentType.PLACES)
    val pois = rememberPlaces(packs, ComponentType.POI)
    var query by remember { mutableStateOf("") }
    var focus by remember { mutableStateOf<Coordinate?>(null) }
    val hits = remember(query, places) { PlaceSearch.search(places, query, live.fix?.coordinate, limit = 8) }
    val routingReady = packs.any { Capabilities.of(it, OfflineRouting.supportedEngines).routing == RoutingSupport.READY }

    Box(Modifier.fillMaxSize()) {
        OfflineMap(
            styleJson = style,
            initialBounds = packs.first().bbox,
            user = live.fix?.coordinate,
            pois = pois,
            focus = focus,
            darkTheme = dark,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(Dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(Dimens.gapSmall),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
                if (places.isNotEmpty()) {
                    SearchField(query, { query = it }, placeholder = "Search places", modifier = Modifier.weight(1f))
                } else {
                    Box(Modifier.weight(1f))
                }
                MapButton(Icons.Rounded.CloudDownload, "Map downloads") { go(Routes.DOWNLOADS) }
            }
            if (query.isNotBlank()) {
                HavenCard(Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                    if (hits.isEmpty()) ListRow("No matching places")
                    hits.forEachIndexed { i, h ->
                        if (i > 0) GroupDivider(inset = 16.dp)
                        ListRow(h.place.name, subtitle = placeSubtitle(h), onClick = {
                            focus = Coordinate(h.place.lat, h.place.lon); query = ""
                        })
                    }
                }
            }
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(Dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(Dimens.gapSmall),
            horizontalAlignment = Alignment.End,
        ) {
            if (pois.isNotEmpty()) MapButton(Icons.Rounded.LocalHospital, "Emergency places") { go(Routes.EMERGENCY_PLACES) }
            MapButton(Icons.Rounded.MyLocation, "Show my location") { live.fix?.let { focus = it.coordinate } }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = Dimens.gutter, bottom = Dimens.gutter)) {
            if (!routingReady) MapLabel("Offline routing not available for this region")
            MapLabel(packs.map { it.attribution }.distinct().joinToString(" · "))
        }
    }
}

private fun placeSubtitle(h: PlaceHit): String =
    listOfNotNull(h.place.kind.replace('_', ' ').replaceFirstChar { it.uppercase() }, h.distanceMeters?.let { Geo.formatDistance(it) }).joinToString(" · ")

@Composable
private fun MapButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = ControlShape, color = Haven.colors.surface, contentColor = Haven.colors.accent, shadowElevation = 2.dp) {
        Box(Modifier.heightIn(min = 48.dp).padding(12.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label)
        }
    }
}

@Composable
private fun MapLabel(text: String) {
    Text(
        text, style = Haven.type.caption, color = Haven.colors.text,
        modifier = Modifier.padding(top = 4.dp).background(Haven.colors.surface.copy(alpha = 0.85f), ControlShape).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

// ---- Downloads ---------------------------------------------------------------------------

@Composable
fun DownloadsScreen(back: () -> Unit, go: (String) -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val override by container.settings.catalogUrlOverride.collectAsStateWithLifecycle(initialValue = "")
    val unmetered by container.settings.unmeteredOnly.collectAsStateWithLifecycle(initialValue = false)
    val url = override.ifBlank { BuildConfig.MAP_CATALOG_URL }
    val catalog by container.catalog.state.collectAsStateWithLifecycle()
    val regions by container.maps.regions.collectAsStateWithLifecycle(initialValue = emptyList())
    val bundled = remember(container) { container.bundledMaps.packs }
    var editSource by remember { mutableStateOf(false) }

    LaunchedEffect(url) { container.catalog.loadCached(url) }

    HavenScreen("Map downloads", onBack = back, largeTitle = false, actions = {
        if (url.isNotBlank()) IconButton(onClick = { scope.launch { container.catalog.refresh(url) } }) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh regions", tint = Haven.colors.accent)
        }
    }) {
        if (bundled.isNotEmpty()) {
            SectionLabel("Included with the app")
            ListGroup {
                bundled.forEachIndexed { i, p ->
                    if (i > 0) GroupDivider(inset = 16.dp)
                    ListRow(p.name, subtitle = "Data ${p.dataDate}", value = "Ready", valueColor = Haven.colors.ready)
                }
            }
        }

        val installed = regions.filter { it.installed != null }
        if (installed.isNotEmpty()) {
            SectionLabel("On this device")
            RegionList(installed, go)
        }

        SectionLabel("Available")
        when (val c = catalog) {
            CatalogState.NotConfigured -> HavenCard(Modifier.fillMaxWidth()) {
                Text("No map source set", style = Haven.type.headline, color = Haven.colors.text)
                Hint("Add the address of a HAVEN map catalog to see regions.")
                PrimaryButton("Set map source", onClick = { editSource = true })
            }
            is CatalogState.NotLoaded -> PrimaryButton("Load regions", modifier = Modifier.fillMaxWidth(), onClick = { scope.launch { container.catalog.refresh(url) } })
            CatalogState.Loading -> Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = Haven.colors.accent)
            }
            is CatalogState.Error -> {
                HavenCard(Modifier.fillMaxWidth()) {
                    Text(c.message, style = Haven.type.callout, color = Haven.colors.text)
                    SecondaryButton("Try again", onClick = { scope.launch { container.catalog.refresh(url) } })
                }
            }
            is CatalogState.Loaded -> {
                val available = regions.filter { it.installed == null && it.available != null }
                if (available.isEmpty()) Hint(if (c.regions.isEmpty()) "This source lists no regions." else "All listed regions are on this device.")
                else RegionList(available, go)
                if (c.rejected.isNotEmpty()) Hint("${c.rejected.size} listed region(s) failed validation and are hidden.", color = Haven.colors.attention)
                if (c.fromCache) Hint("Showing the saved list. Refresh when online.")
            }
        }

        SectionLabel("Options")
        ListGroup {
            ListRow("Wi-Fi only", subtitle = "Avoid mobile data for map downloads", trailing = {
                HavenSwitch(unmetered) { v -> scope.launch { container.settings.setUnmeteredOnly(v) } }
            })
            GroupDivider(inset = 16.dp)
            ListRow("Map source", subtitle = url.ifBlank { "Not set" }, chevron = true, onClick = { editSource = true })
        }
    }

    if (editSource) SourceDialog(override, onDismiss = { editSource = false }) { value ->
        scope.launch { container.settings.setCatalogUrlOverride(value) }
    }
}

@Composable
private fun RegionList(list: List<RegionUi>, go: (String) -> Unit) {
    ListGroup {
        list.forEachIndexed { i, r ->
            if (i > 0) GroupDivider(inset = 16.dp)
            Column {
                ListRow(r.name, subtitle = regionSubtitle(r), value = regionValue(r), valueColor = regionColor(r), chevron = true, onClick = { go(Routes.region(r.id)) })
                (r.transfer as? PackState.Downloading)?.let { d ->
                    LinearProgressIndicator(progress = { d.fraction }, color = Haven.colors.accent, trackColor = Haven.colors.surfaceRaised,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
        }
    }
}

private fun regionSubtitle(r: RegionUi): String {
    val size = (r.available?.totalBytes ?: r.installed?.components?.sumOf { it.bytes })?.let { formatBytes(it) }
    val version = r.installed?.version ?: r.available?.version
    return listOfNotNull(size, version?.let { "v$it" }).joinToString(" · ")
}

private fun regionValue(r: RegionUi): String = when (val t = r.transfer) {
    PackState.Queued -> "Waiting"
    is PackState.Downloading -> "${(t.fraction * 100).toInt()}%"
    is PackState.Paused -> "Paused"
    PackState.Verifying -> "Verifying"
    is PackState.Failed -> "Failed"
    PackState.Idle -> when {
        r.updateAvailable -> "Update"
        r.installed != null -> "Installed"
        else -> ""
    }
}

@Composable
private fun regionColor(r: RegionUi) = when {
    r.transfer is PackState.Failed -> Haven.colors.emergency
    r.updateAvailable -> Haven.colors.accent
    r.installed != null && r.transfer == PackState.Idle -> Haven.colors.ready
    else -> Haven.colors.textSecondary
}

@Composable
private fun SourceDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(current.ifBlank { BuildConfig.MAP_CATALOG_URL }) }
    val valid = value.isBlank() || value.trim().startsWith("https://")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Haven.colors.surface,
        title = { Text("Map source", style = Haven.type.headline, color = Haven.colors.text) },
        text = {
            HavenTextField(value, { value = it.trim() }, placeholder = "https://…/catalog.json",
                isError = !valid, supportingText = if (!valid) "Must start with https://" else "Leave empty to use the app default")
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(if (value == BuildConfig.MAP_CATALOG_URL) "" else value); onDismiss() }) { Text("Save", color = Haven.colors.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Haven.colors.accent) } },
    )
}

// ---- Region detail -----------------------------------------------------------------------

@Composable
fun RegionScreen(id: String, back: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val show = LocalShowMessage.current
    val scope = rememberCoroutineScope()
    val regions by container.maps.regions.collectAsStateWithLifecycle(initialValue = null)
    val unmetered by container.settings.unmeteredOnly.collectAsStateWithLifecycle(initialValue = false)
    val override by container.settings.catalogUrlOverride.collectAsStateWithLifecycle(initialValue = "")
    val url = override.ifBlank { BuildConfig.MAP_CATALOG_URL }
    val r = regions?.firstOrNull { it.id == id }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    HavenScreen(r?.name ?: "Region", onBack = back, largeTitle = true) {
        if (regions == null) return@HavenScreen
        if (r == null) { EmptyState(Icons.Rounded.Map, "Region not found"); return@HavenScreen }
        val manifest = r.available
        val pack = r.installed
        ListGroup {
            ListRow("Size", value = formatBytes(manifest?.totalBytes ?: pack?.components?.sumOf { it.bytes } ?: 0))
            GroupDivider(inset = 16.dp)
            ListRow("Version", value = pack?.version ?: manifest?.version ?: "—")
            GroupDivider(inset = 16.dp)
            ListRow("Map data date", value = pack?.dataDate ?: manifest?.dataDate ?: "—")
            if (pack == null && manifest != null) {
                GroupDivider(inset = 16.dp)
                ListRow("Free space", value = formatBytes(context.filesDir.usableSpace))
            }
        }
        manifest?.description?.let { Hint(it) }

        when (val t = r.transfer) {
            PackState.Queued -> {
                Hint(if (unmetered) "Waiting for Wi-Fi…" else "Waiting for a connection…")
                SecondaryButton("Cancel", modifier = Modifier.fillMaxWidth(), onClick = { container.maps.cancel(id) })
            }
            is PackState.Downloading -> {
                LinearProgressIndicator(progress = { t.fraction }, color = Haven.colors.accent, trackColor = Haven.colors.surfaceRaised, modifier = Modifier.fillMaxWidth())
                Hint("${formatBytes(t.downloadedBytes)} of ${formatBytes(t.totalBytes)}")
                SecondaryButton("Pause", modifier = Modifier.fillMaxWidth(), onClick = { container.maps.cancel(id) })
            }
            PackState.Verifying -> Hint("Checking files…")
            is PackState.Paused -> {
                Hint("Paused at ${formatBytes(t.downloadedBytes)}")
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
                    PrimaryButton("Resume", modifier = Modifier.weight(1f), enabled = manifest != null,
                        onClick = { manifest?.let { container.maps.download(it, url, unmetered) } })
                    SecondaryButton("Discard", modifier = Modifier.weight(1f), onClick = { confirmDiscard = true })
                }
            }
            is PackState.Failed -> {
                HavenCard(Modifier.fillMaxWidth()) { Text(t.reason, style = Haven.type.callout, color = Haven.colors.emergency) }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
                    PrimaryButton("Retry", modifier = Modifier.weight(1f), enabled = manifest != null,
                        onClick = { manifest?.let { container.maps.download(it, url, unmetered) } })
                    if (r.partialBytes > 0) SecondaryButton("Discard", modifier = Modifier.weight(1f), onClick = { confirmDiscard = true })
                }
            }
            PackState.Idle -> when {
                pack == null && manifest != null -> PrimaryButton("Download", icon = Icons.Rounded.CloudDownload, modifier = Modifier.fillMaxWidth(),
                    onClick = { container.maps.download(manifest, url, unmetered); show("Download started") })
                r.updateAvailable && manifest != null -> PrimaryButton("Update to v${manifest.version}", modifier = Modifier.fillMaxWidth(),
                    onClick = { container.maps.download(manifest, url, unmetered) })
                else -> Unit
            }
        }

        SectionLabel("What works offline")
        val caps = pack?.let { Capabilities.of(it, OfflineRouting.supportedEngines) }
        val has = { type: ComponentType -> pack?.component(type) != null || (pack == null && manifest?.component(type) != null) }
        ListGroup {
            FeatureRow("Map display", has(ComponentType.TILES))
            GroupDivider(inset = 16.dp)
            FeatureRow("Place search", has(ComponentType.PLACES))
            GroupDivider(inset = 16.dp)
            FeatureRow("Emergency places", has(ComponentType.POI))
            GroupDivider(inset = 16.dp)
            val routing = caps?.routing ?: if (manifest?.component(ComponentType.ROUTING) != null) RoutingSupport.NO_COMPATIBLE_ENGINE else RoutingSupport.NO_DATA
            ListRow("Turn-by-turn routing", subtitle = if (routing == RoutingSupport.READY) null else "Offline routing not available for this region",
                value = if (routing == RoutingSupport.READY) "Yes" else "No",
                valueColor = if (routing == RoutingSupport.READY) Haven.colors.ready else Haven.colors.textSecondary)
        }
        Hint("${pack?.attribution ?: manifest?.attribution ?: "© OpenMapTiles © OpenStreetMap contributors"} · ${pack?.license ?: manifest?.license ?: "ODbL-1.0"}")

        if (pack != null && !pack.bundled && !PackStateMachine.isActive(r.transfer)) {
            SecondaryButton("Delete from device", modifier = Modifier.fillMaxWidth(), tint = Haven.colors.emergency, onClick = { confirmDelete = true })
        }
    }

    if (confirmDelete) ConfirmDialog("Delete ${r?.name ?: "region"}?", "Maps for this region won't work offline until you download it again.", "Delete", destructive = true,
        onConfirm = { container.maps.delete(id); container.places.clear(); show("Region deleted") }, onDismiss = { confirmDelete = false })
    if (confirmDiscard) ConfirmDialog("Discard partial download?", null, "Discard", destructive = true,
        onConfirm = { container.maps.discard(id) }, onDismiss = { confirmDiscard = false })
}

@Composable
private fun FeatureRow(label: String, available: Boolean) {
    ListRow(label, value = if (available) "Yes" else "No", valueColor = if (available) Haven.colors.ready else Haven.colors.textSecondary)
}

// ---- Emergency places ------------------------------------------------------------------------

@Composable
fun EmergencyPlacesScreen(back: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val show = LocalShowMessage.current
    val packs = rememberInstalledPacks()
    val live = rememberLiveLocation()
    var kinds by remember { mutableStateOf(EmergencyKind.entries.toSet()) }
    val sets by produceState(emptyList<app.projecthaven.data.maps.PlaceSet>(), packs) {
        value = packs.mapNotNull { container.places.load(it, ComponentType.POI) }
    }
    val all = sets.flatMap { it.places }
    val here = live.fix?.coordinate

    HavenScreen("Emergency places", onBack = back, largeTitle = false) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall), verticalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
            EmergencyKind.entries.forEach { k ->
                HavenChip(k.label, selected = k in kinds, onClick = { kinds = if (k in kinds) kinds - k else kinds + k })
            }
        }
        when {
            all.isEmpty() -> EmptyState(Icons.Rounded.LocalHospital, "No emergency places data", "Download a region that includes emergency places.")
            here == null -> {
                Hint("Showing places without distance until a GPS fix is available.")
                PlaceList(all.filter { EmergencyKind.of(it.kind) in kinds }.take(50).map { PlaceHit(it, null) }, context, show)
            }
            else -> PlaceList(PlaceSearch.nearest(all, kinds, here), context, show)
        }
        sets.forEach { s -> Hint("${s.regionName}: ${s.source}, data ${s.dataDate}. May be incomplete or out of date.") }
        EmergencyButton("Call 112", icon = Icons.Rounded.Phone, modifier = Modifier.fillMaxWidth(), onClick = { SystemActions.dial(context, "112") })
    }
}

@Composable
private fun PlaceList(hits: List<PlaceHit>, context: android.content.Context, show: (String) -> Unit) {
    if (hits.isEmpty()) { Hint("No places of the selected types."); return }
    ListGroup {
        hits.forEachIndexed { i, h ->
            if (i > 0) GroupDivider(inset = 16.dp)
            val p = h.place
            ListRow(
                p.name,
                subtitle = listOfNotNull(EmergencyKind.of(p.kind)?.label, h.distanceMeters?.let { Geo.formatDistance(it) }, p.address).joinToString(" · "),
                trailing = {
                    Row {
                        IconButton(onClick = { SystemActions.copy(context, "Location", "${p.name}\n${Geo.formatDecimal(Coordinate(p.lat, p.lon))}"); show("Copied") }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy location of ${p.name}", tint = Haven.colors.accent)
                        }
                        if (!p.phone.isNullOrBlank()) IconButton(onClick = { SystemActions.dial(context, p.phone) }) {
                            Icon(Icons.Rounded.Phone, contentDescription = "Call ${p.name}", tint = Haven.colors.accent)
                        }
                    }
                },
            )
        }
    }
}
