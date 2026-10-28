// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data.maps

import app.projecthaven.domain.maps.ManifestValidator
import app.projecthaven.domain.maps.MapCatalog
import app.projecthaven.domain.maps.RegionManifest
import app.projecthaven.domain.maps.Validation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface CatalogState {
    /** No catalog URL has been configured at build time or by the user. */
    data object NotConfigured : CatalogState
    /** A source is configured but its catalog has never been fetched on this device. */
    data class NotLoaded(val url: String) : CatalogState
    data object Loading : CatalogState
    data class Loaded(
        val url: String,
        val regions: List<RegionManifest>,
        /** Regions present in the catalog but rejected by validation, with reasons. */
        val rejected: Map<String, List<String>>,
        val fetchedAtEpochMs: Long,
        val fromCache: Boolean,
    ) : CatalogState
    data class Error(val message: String, val cached: Loaded?) : CatalogState
}

@Serializable
private data class CachedCatalog(val url: String, val fetchedAtEpochMs: Long, val body: String)

/** Fetches and validates the configured map catalog, keeping the last good copy for offline use. */
class CatalogRepository(
    private val cacheFile: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<CatalogState>(CatalogState.NotConfigured)
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    /** Shows the last good catalog for [url] without using the network. */
    fun loadCached(url: String) {
        if (url.isBlank()) { _state.value = CatalogState.NotConfigured; return }
        val current = _state.value
        if (current is CatalogState.Loaded && current.url == url) return
        val cached = readCache()?.takeIf { it.url == url }
        val parsed = cached?.let { parse(it.url, it.body, it.fetchedAtEpochMs, fromCache = true) }
        _state.value = (parsed as? CatalogState.Loaded) ?: CatalogState.NotLoaded(url)
    }

    suspend fun refresh(url: String) {
        if (url.isBlank()) { _state.value = CatalogState.NotConfigured; return }
        val previous = (_state.value as? CatalogState.Loaded) ?: (_state.value as? CatalogState.Error)?.cached
        _state.value = CatalogState.Loading
        val result = withContext(Dispatchers.IO) {
            try {
                if (!url.startsWith("https://")) return@withContext CatalogState.Error("The map source must use https", previous)
                val body = fetch(url)
                val parsed = parse(url, body, clock(), fromCache = false)
                if (parsed is CatalogState.Loaded) writeCache(CachedCatalog(url, parsed.fetchedAtEpochMs, body))
                parsed
            } catch (e: IOException) {
                CatalogState.Error("Couldn't reach the map source. Check your connection.", previous?.takeIf { it.url == url })
            }
        }
        _state.value = result
    }

    fun region(id: String): RegionManifest? = when (val s = _state.value) {
        is CatalogState.Loaded -> s.regions.firstOrNull { it.id == id }
        is CatalogState.Error -> s.cached?.regions?.firstOrNull { it.id == id }
        else -> null
    }

    internal fun parse(url: String, body: String, fetchedAt: Long, fromCache: Boolean): CatalogState {
        val catalog = try {
            json.decodeFromString(MapCatalog.serializer(), body)
        } catch (e: SerializationException) {
            return CatalogState.Error("The map source returned an invalid catalog.", null)
        } catch (e: IllegalArgumentException) {
            return CatalogState.Error("The map source returned an invalid catalog.", null)
        }
        when (val v = ManifestValidator.validateCatalog(catalog)) {
            is Validation.Invalid -> return CatalogState.Error(v.problems.first(), null)
            Validation.Valid -> Unit
        }
        val rejected = mutableMapOf<String, List<String>>()
        val ok = catalog.regions.filter { r ->
            when (val v = ManifestValidator.validateRegion(r)) {
                Validation.Valid -> r.components.all { ManifestValidator.resolveUrl(url, it.url) != null }
                    .also { if (!it) rejected[r.id] = listOf("A file URL is not https") }
                is Validation.Invalid -> { rejected[r.id] = v.problems; false }
            }
        }
        return CatalogState.Loaded(url, ok, rejected, fetchedAt, fromCache)
    }

    private fun fetch(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            val length = conn.contentLengthLong
            if (length > MAX_CATALOG_BYTES) throw IOException("Catalog too large")
            val bytes = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_CATALOG_BYTES) throw IOException("Catalog too large")
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
            return String(bytes, Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    private fun readCache(): CachedCatalog? = runCatching {
        json.decodeFromString(CachedCatalog.serializer(), cacheFile.readText())
    }.getOrNull()

    private fun writeCache(c: CachedCatalog) {
        runCatching {
            cacheFile.parentFile?.mkdirs()
            val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            tmp.writeText(json.encodeToString(CachedCatalog.serializer(), c))
            if (!tmp.renameTo(cacheFile)) { cacheFile.delete(); tmp.renameTo(cacheFile) }
        }
    }

    companion object {
        const val MAX_CATALOG_BYTES = 5L * 1024 * 1024
    }
}
