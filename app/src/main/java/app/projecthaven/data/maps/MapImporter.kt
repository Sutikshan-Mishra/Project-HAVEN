// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data.maps

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.projecthaven.domain.maps.ComponentType
import app.projecthaven.domain.maps.HeaderResult
import app.projecthaven.domain.maps.ImportKind
import app.projecthaven.domain.maps.InstalledPack
import app.projecthaven.domain.maps.MapImport
import app.projecthaven.domain.maps.PackComponent
import app.projecthaven.domain.maps.PmtilesMeta
import app.projecthaven.domain.maps.RegionManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream

sealed interface ImportOutcome {
    data class Done(val pack: InstalledPack) : ImportOutcome
    data class Failed(val message: String) : ImportOutcome
}

/**
 * Lets people use their own map: copies a PMTiles file they pick into app storage, checks it,
 * and installs it through the same [MapPackStore] path as downloaded regions. Nothing leaves the phone.
 */
class MapImporter(private val context: Context, private val store: MapPackStore) {

    suspend fun import(uri: Uri): ImportOutcome = withContext(Dispatchers.IO) {
        val (fileName, size) = describe(uri)
        when (MapImport.classify(fileName)) {
            ImportKind.OSM_SOURCE -> return@withContext ImportOutcome.Failed(MapImport.OSM_SOURCE_MESSAGE)
            ImportKind.UNSUPPORTED -> return@withContext ImportOutcome.Failed(MapImport.UNSUPPORTED_MESSAGE)
            ImportKind.PMTILES -> Unit
        }
        if (size != null && size > 0 && size + SPACE_MARGIN > store.freeSpace()) {
            return@withContext ImportOutcome.Failed("Not enough free space on this phone for that file.")
        }

        val id = MapImport.regionId(fileName, store.installed.value.map { it.regionId }.toSet())
        val staged = File(store.stagingVersionDir(id, VERSION).apply { mkdirs() }, TILES_FILE)
        var installed = false
        try {
            val sha = copyTo(uri, staged)
                ?: return@withContext ImportOutcome.Failed("Could not read that file.")

            val header = ByteArray(MapImport.HEADER_SIZE)
            val read = staged.inputStream().use { it.read(header) }
            val info = when (val h = MapImport.parseHeader(header.copyOf(maxOf(read, 0)))) {
                is HeaderResult.Bad -> return@withContext ImportOutcome.Failed(h.reason)
                is HeaderResult.Ok -> h.info
            }
            if (info.tileType != MapImport.TILE_TYPE_MVT) {
                return@withContext ImportOutcome.Failed("This archive holds image tiles. HAVEN needs vector map tiles (MVT) in the OpenMapTiles layout.")
            }
            val meta = readMetadata(staged, info)
            if (!MapImport.looksLikeOpenMapTiles(meta.layers)) {
                return@withContext ImportOutcome.Failed(
                    "This map uses a different tile layout (${meta.layers.sorted().take(6).joinToString()}). HAVEN draws OpenMapTiles-layout maps, such as those made by tools/mappack.",
                )
            }

            val manifest = RegionManifest(
                id = id,
                name = meta.name ?: MapImport.displayName(fileName),
                version = VERSION,
                bbox = info.bbox ?: MapImport.WORLD_BBOX,
                dataDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                attribution = meta.attribution ?: "Imported by you. Check the map source's licence.",
                license = "User-provided",
                components = listOf(PackComponent(ComponentType.TILES, TILES_FILE, "local", staged.length(), sha)),
            )
            when (val r = store.install(manifest, imported = true)) {
                is InstallResult.Installed -> {
                    installed = true
                    ImportOutcome.Done(r.pack)
                }
                is InstallResult.Rejected -> ImportOutcome.Failed("Could not add this map: ${r.reason}")
            }
        } catch (e: java.io.IOException) {
            ImportOutcome.Failed("Could not copy the file: ${e.message ?: "storage error"}")
        } finally {
            if (!installed) store.discardPartial(id)
        }
    }

    private fun describe(uri: Uri): Pair<String?, Long?> = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val name = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { c.getString(it) }
            val size = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { c.getLong(it) }
            name to size
        }
    }.getOrNull() ?: (uri.lastPathSegment to null)

    /** Copies the picked file and returns its SHA-256, or null if it cannot be opened. */
    private suspend fun copyTo(uri: Uri, target: File): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = context.contentResolver.openInputStream(uri) ?: return null
        input.use { src ->
            target.outputStream().buffered().use { out ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = src.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                    out.write(buffer, 0, n)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readMetadata(file: File, info: app.projecthaven.domain.maps.PmtilesInfo): PmtilesMeta {
        if (!MapImport.metadataReadable(info)) return PmtilesMeta(null, null, emptySet())
        return runCatching {
            val raw = ByteArray(info.metadataLength.toInt())
            RandomAccessFile(file, "r").use { it.seek(info.metadataOffset); it.readFully(raw) }
            val text = if (MapImport.isGzip(info)) GZIPInputStream(raw.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
            else raw.toString(Charsets.UTF_8)
            MapImport.parseMetadata(text)
        }.getOrDefault(PmtilesMeta(null, null, emptySet()))
    }

    private companion object {
        const val VERSION = "1"
        const val TILES_FILE = "tiles.pmtiles"
        const val SPACE_MARGIN = 50L * 1024 * 1024
    }
}
