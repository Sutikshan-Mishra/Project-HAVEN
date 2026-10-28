// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data.maps

import app.projecthaven.domain.maps.ComponentType
import app.projecthaven.domain.maps.InstalledIndex
import app.projecthaven.domain.maps.InstalledPack
import app.projecthaven.domain.maps.PlaceFile
import app.projecthaven.domain.maps.PlaceRecord
import app.projecthaven.domain.maps.PlaceSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** Places and emergency points of interest from installed packs, with their provenance. */
data class PlaceSet(
    val regionName: String,
    val source: String,
    val dataDate: String,
    val places: List<PlaceRecord>,
)

/** Opens a file belonging to a pack, whether downloaded or bundled in the APK. */
fun interface PackFileOpener {
    fun open(pack: InstalledPack, file: String): InputStream
}

class PlacesRepository(private val opener: PackFileOpener) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, PlaceSet>()

    suspend fun load(pack: InstalledPack, type: ComponentType): PlaceSet? = withContext(Dispatchers.IO) {
        require(type == ComponentType.PLACES || type == ComponentType.POI)
        val component = pack.component(type) ?: return@withContext null
        val key = "${pack.regionId}/${pack.version}/${type.name}"
        cache[key] ?: runCatching {
            val text = opener.open(pack, component.file).bufferedReader().use { it.readText() }
            val file = json.decodeFromString(PlaceFile.serializer(), text)
            if (file.format != PlaceFile.FORMAT) null
            else PlaceSet(pack.name, file.source, file.dataDate, PlaceSearch.validRecords(file))
        }.getOrNull()?.also { cache[key] = it }
    }

    fun clear() = cache.clear()
}

/**
 * Packs shipped inside the APK under assets/maps/, described by assets/maps/bundled.json
 * (an [InstalledIndex] with `bundled = true`). The repository ships no map data by default;
 * tools/mappack/build_starter.sh generates the overview and Lucknow–Unnao–Kanpur packs.
 */
class BundledMaps(private val listAssets: () -> List<String>, private val readAsset: (String) -> String?) {
    private val json = Json { ignoreUnknownKeys = true }

    val packs: List<InstalledPack> by lazy {
        if (INDEX !in listAssets()) return@lazy emptyList()
        val text = readAsset("maps/$INDEX") ?: return@lazy emptyList()
        val available = listAssets().toSet()
        runCatching { json.decodeFromString(InstalledIndex.serializer(), text).packs }
            .getOrDefault(emptyList())
            .map { it.copy(bundled = true) }
            // Only list packs whose files are really present in the APK.
            .filter { p -> p.components.all { it.file in available } }
    }

    companion object {
        const val INDEX = "bundled.json"
        fun assetUrl(file: String) = "pmtiles://asset://maps/$file"
    }
}
