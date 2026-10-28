// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain.maps

import java.io.File
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest

sealed interface Validation {
    data object Valid : Validation
    data class Invalid(val problems: List<String>) : Validation
}

/**
 * Validates catalogs and region manifests before anything is downloaded, so a malformed or
 * hostile catalog cannot write outside the pack directory or masquerade as a working pack.
 */
object ManifestValidator {

    private val ID = Regex("^[a-z0-9][a-z0-9-]{1,62}$")
    private val VERSION = Regex("^[0-9]+(\\.[0-9]+){0,3}$")
    private val FILE = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$")
    private val SHA256 = Regex("^[0-9a-f]{64}$")
    private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    /** Hard cap per file to protect device storage from a broken catalog (8 GiB). */
    const val MAX_COMPONENT_BYTES: Long = 8L * 1024 * 1024 * 1024

    fun validateCatalog(catalog: MapCatalog): Validation {
        val problems = mutableListOf<String>()
        if (catalog.format != MapCatalog.FORMAT) problems += "Unknown catalog format '${catalog.format}'"
        if (catalog.formatVersion > MapCatalog.SUPPORTED_FORMAT_VERSION)
            problems += "Catalog format version ${catalog.formatVersion} needs a newer app"
        val ids = catalog.regions.map { it.id }
        ids.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { problems += "Duplicate region id '$it'" }
        return if (problems.isEmpty()) Validation.Valid else Validation.Invalid(problems)
    }

    fun validateRegion(region: RegionManifest): Validation {
        val p = mutableListOf<String>()
        if (!ID.matches(region.id)) p += "Invalid region id '${region.id}'"
        if (region.name.isBlank()) p += "Region name is empty"
        if (!VERSION.matches(region.version)) p += "Invalid version '${region.version}'"
        if (!DATE.matches(region.dataDate)) p += "Invalid data date '${region.dataDate}'"
        if (region.attribution.isBlank()) p += "Attribution is required"
        if (region.license.isBlank()) p += "Licence is required"
        p += bboxProblems(region.bbox)

        if (region.components.isEmpty()) p += "No components"
        if (region.components.none { it.type == ComponentType.TILES }) p += "A tiles component is required"
        region.components.groupBy { it.type }.filter { it.value.size > 1 }.keys
            .forEach { p += "Duplicate component type $it" }
        region.components.groupBy { it.file }.filter { it.value.size > 1 }.keys
            .forEach { p += "Duplicate file name '$it'" }

        region.components.forEach { c ->
            if (!FILE.matches(c.file) || c.file.contains("..")) p += "Unsafe file name '${c.file}'"
            if (c.type == ComponentType.TILES && !c.file.endsWith(".pmtiles")) p += "Tiles must be a .pmtiles file"
            if ((c.type == ComponentType.PLACES || c.type == ComponentType.POI) && !c.file.endsWith(".json"))
                p += "${c.type} must be a .json file"
            if (c.type == ComponentType.ROUTING && (c.engine.isNullOrBlank() || c.engineVersion.isNullOrBlank()))
                p += "Routing data must name its engine and engine version"
            if (c.bytes <= 0) p += "Component '${c.file}' has no size"
            if (c.bytes > MAX_COMPONENT_BYTES) p += "Component '${c.file}' is too large"
            if (!SHA256.matches(c.sha256)) p += "Component '${c.file}' has an invalid SHA-256"
            if (c.url.isBlank()) p += "Component '${c.file}' has no URL"
            if (c.url.contains("://") && !c.url.startsWith("https://")) p += "Component '${c.file}' must use https"
        }
        return if (p.isEmpty()) Validation.Valid else Validation.Invalid(p)
    }

    fun bboxProblems(bbox: List<Double>): List<String> {
        if (bbox.size != 4) return listOf("Bounding box must have 4 numbers")
        val (w, s, e, n) = bbox
        val out = mutableListOf<String>()
        if (w !in -180.0..180.0 || e !in -180.0..180.0) out += "Bounding box longitude out of range"
        if (s !in -90.0..90.0 || n !in -90.0..90.0) out += "Bounding box latitude out of range"
        if (w >= e || s >= n) out += "Bounding box is empty or inverted"
        return out
    }

    /** Resolves a component URL against the catalog URL. Returns null when the result is not https. */
    fun resolveUrl(catalogUrl: String, componentUrl: String): String? = try {
        val resolved = URI(catalogUrl).resolve(componentUrl).toString()
        resolved.takeIf { it.startsWith("https://") }
    } catch (e: Exception) {
        null
    }
}

object Integrity {

    fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(file: File): String = file.inputStream().buffered().use { sha256Hex(it) }

    /** PMTiles v3 archives start with the ASCII magic "PMTiles" followed by version byte 3. */
    fun looksLikePmtilesV3(header: ByteArray): Boolean =
        header.size >= 8 &&
            String(header, 0, 7, Charsets.US_ASCII) == "PMTiles" &&
            header[7].toInt() == 3

    sealed interface FileCheck {
        data object Ok : FileCheck
        data class Failed(val reason: String) : FileCheck
    }

    fun verify(file: File, expectedBytes: Long, expectedSha256: String, type: ComponentType): FileCheck {
        if (!file.isFile) return FileCheck.Failed("File missing")
        if (file.length() != expectedBytes) return FileCheck.Failed("Size mismatch")
        if (type == ComponentType.TILES) {
            val header = ByteArray(8)
            val n = file.inputStream().use { it.read(header) }
            if (n < 8 || !looksLikePmtilesV3(header)) return FileCheck.Failed("Not a PMTiles v3 archive")
        }
        val actual = sha256Hex(file)
        if (!actual.equals(expectedSha256, ignoreCase = true)) return FileCheck.Failed("Checksum mismatch")
        return FileCheck.Ok
    }
}
