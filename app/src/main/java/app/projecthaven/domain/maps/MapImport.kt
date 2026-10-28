// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain.maps

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** What the user picked when importing their own map file. */
enum class ImportKind {
    /** A PMTiles archive: the only format HAVEN can draw directly. */
    PMTILES,

    /** Raw OpenStreetMap data (.osm, .osm.pbf, .pbf). Contains no drawn tiles. */
    OSM_SOURCE,
    UNSUPPORTED,
}

/** Facts read from a PMTiles v3 header. */
data class PmtilesInfo(
    val tileType: Int,
    val minZoom: Int,
    val maxZoom: Int,
    /** [west, south, east, north], or null when the file does not declare a usable box. */
    val bbox: List<Double>?,
    val internalCompression: Int,
    val metadataOffset: Long,
    val metadataLength: Long,
)

/** Optional details from the PMTiles JSON metadata. */
data class PmtilesMeta(val name: String?, val attribution: String?, val layers: Set<String>)

sealed interface HeaderResult {
    data class Ok(val info: PmtilesInfo) : HeaderResult
    data class Bad(val reason: String) : HeaderResult
}

object MapImport {

    const val OSM_SOURCE_MESSAGE =
        "This is raw OpenStreetMap data (.osm / .pbf). It holds the map's facts, not drawn map tiles, " +
            "so it has to be converted on a computer first. Use tools/mappack/build_pack.sh (Planetiler) " +
            "to make a .pmtiles file, then import that here."

    const val UNSUPPORTED_MESSAGE = "Choose a .pmtiles map file."

    const val HEADER_SIZE = 127
    const val TILE_TYPE_MVT = 1
    private const val COMPRESSION_NONE = 1
    private const val COMPRESSION_GZIP = 2

    /** Layers an OpenMapTiles-schema archive normally carries; HAVEN's style draws these. */
    private val OPENMAPTILES_LAYERS = setOf("water", "transportation", "landcover", "place", "building", "landuse")

    fun classify(fileName: String?): ImportKind {
        val n = fileName?.trim()?.lowercase().orEmpty()
        return when {
            n.endsWith(".pmtiles") -> ImportKind.PMTILES
            n.endsWith(".osm.pbf") || n.endsWith(".pbf") || n.endsWith(".osm") ||
                n.endsWith(".osm.bz2") || n.endsWith(".osm.xml") -> ImportKind.OSM_SOURCE
            else -> ImportKind.UNSUPPORTED
        }
    }

    fun parseHeader(h: ByteArray): HeaderResult {
        if (h.size < HEADER_SIZE) return HeaderResult.Bad("This is not a PMTiles file (too small).")
        if (!Integrity.looksLikePmtilesV3(h)) return HeaderResult.Bad("This is not a PMTiles version 3 archive.")
        val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        val tileType = h[99].toInt() and 0xFF
        val west = b.getInt(102) / 1e7
        val south = b.getInt(106) / 1e7
        val east = b.getInt(110) / 1e7
        val north = b.getInt(114) / 1e7
        val box = listOf(west, south, east, north).takeIf { ManifestValidator.bboxProblems(it).isEmpty() }
        return HeaderResult.Ok(
            PmtilesInfo(
                tileType = tileType,
                minZoom = h[100].toInt() and 0xFF,
                maxZoom = h[101].toInt() and 0xFF,
                bbox = box,
                internalCompression = h[97].toInt() and 0xFF,
                metadataOffset = b.getLong(24),
                metadataLength = b.getLong(32),
            ),
        )
    }

    /** Whether the metadata block can be read directly or after gzip. */
    fun metadataReadable(info: PmtilesInfo): Boolean =
        info.metadataLength in 1..MAX_METADATA_BYTES &&
            (info.internalCompression == COMPRESSION_NONE || info.internalCompression == COMPRESSION_GZIP)

    fun isGzip(info: PmtilesInfo) = info.internalCompression == COMPRESSION_GZIP

    const val MAX_METADATA_BYTES = 1L * 1024 * 1024

    private val metaJson = Json { ignoreUnknownKeys = true }

    fun parseMetadata(text: String): PmtilesMeta {
        val obj = runCatching { metaJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return PmtilesMeta(null, null, emptySet())
        fun str(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val layers = (obj["vector_layers"] as? JsonArray).orEmpty().mapNotNull { el ->
            ((el as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull
        }.toSet()
        return PmtilesMeta(str("name"), str("attribution")?.let(::stripTags), layers)
    }

    /** True when the archive is, or may be, OpenMapTiles-schema data. Unknown layers get the benefit of the doubt. */
    fun looksLikeOpenMapTiles(layers: Set<String>): Boolean = layers.isEmpty() || layers.any { it in OPENMAPTILES_LAYERS }

    fun stripTags(html: String): String = html.replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim()

    /** A valid region id for an imported file, unique among [taken]. */
    fun regionId(fileName: String?, taken: Set<String>): String {
        val base = (fileName ?: "")
            .substringBeforeLast('.', fileName ?: "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
            .trim('-')
            .ifEmpty { "map" }
        val first = "my-$base"
        if (first !in taken) return first
        var n = 2
        while ("$first-$n" in taken) n++
        return "$first-$n"
    }

    /** Display name from a file name: "lucknow_city.pmtiles" becomes "lucknow city". */
    fun displayName(fileName: String?): String =
        (fileName ?: "").substringBeforeLast('.', fileName ?: "").replace(Regex("[_\\-]+"), " ").trim().ifEmpty { "Imported map" }

    val WORLD_BBOX: List<Double> = listOf(-180.0, -85.0, 180.0, 85.0)
}
