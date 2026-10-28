// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain.maps

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Map catalog published by a map-pack host (see tools/mappack and docs/MAP_PACKS.md).
 * The app never assumes a default host: the catalog URL is configured at build time or by
 * the user.
 */
@Serializable
data class MapCatalog(
    val format: String,
    val formatVersion: Int,
    val generated: String,
    val regions: List<RegionManifest>,
) {
    companion object {
        const val FORMAT = "haven-map-catalog"
        const val SUPPORTED_FORMAT_VERSION = 1
    }
}

@Serializable
data class RegionManifest(
    val id: String,
    val name: String,
    /** Data version, e.g. "2026.10.1". Compared segment by segment. */
    val version: String,
    /** [west, south, east, north] in WGS84 degrees. */
    val bbox: List<Double>,
    /** Date of the source data extract (yyyy-MM-dd). */
    val dataDate: String,
    val attribution: String,
    val license: String,
    val components: List<PackComponent>,
    val description: String? = null,
) {
    val totalBytes: Long get() = components.sumOf { it.bytes }

    fun component(type: ComponentType): PackComponent? = components.firstOrNull { it.type == type }
}

@Serializable
data class PackComponent(
    val type: ComponentType,
    /** File name inside the pack directory. Plain name only, no paths. */
    val file: String,
    /** Absolute https URL, or a path relative to the catalog URL. */
    val url: String,
    val bytes: Long,
    /** Lower-case hex SHA-256 of the file. */
    val sha256: String,
    /** For routing data: the engine and data-format version it was built for. */
    val engine: String? = null,
    val engineVersion: String? = null,
)

@Serializable
enum class ComponentType {
    /** Vector tiles in a PMTiles v3 archive, OpenMapTiles schema. */
    @SerialName("tiles") TILES,
    /** Searchable place names (haven-places JSON). */
    @SerialName("places") PLACES,
    /** Emergency points of interest (haven-places JSON with emergency kinds). */
    @SerialName("poi") POI,
    /** Routing graph for an offline routing engine. */
    @SerialName("routing") ROUTING,
}

/** Record of a pack that has been downloaded, verified and installed. */
@Serializable
data class InstalledPack(
    val regionId: String,
    val name: String,
    val version: String,
    val bbox: List<Double>,
    val dataDate: String,
    val attribution: String,
    val license: String,
    val components: List<InstalledComponent>,
    val installedAtEpochMs: Long,
    /** True when shipped inside the APK rather than downloaded. */
    val bundled: Boolean = false,
) {
    fun component(type: ComponentType): InstalledComponent? = components.firstOrNull { it.type == type }
}

@Serializable
data class InstalledComponent(
    val type: ComponentType,
    val file: String,
    val bytes: Long,
    val sha256: String,
    val engine: String? = null,
    val engineVersion: String? = null,
)

@Serializable
data class InstalledIndex(
    val formatVersion: Int = 1,
    val packs: List<InstalledPack> = emptyList(),
)

object Versions {
    /** Compares dotted numeric versions ("2026.10.1"). Non-numeric segments compare as text. */
    fun compare(a: String, b: String): Int {
        val pa = a.split('.')
        val pb = b.split('.')
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val sa = pa.getOrElse(i) { "0" }
            val sb = pb.getOrElse(i) { "0" }
            val na = sa.toLongOrNull()
            val nb = sb.toLongOrNull()
            val c = if (na != null && nb != null) na.compareTo(nb) else sa.compareTo(sb)
            if (c != 0) return c
        }
        return 0
    }
}
