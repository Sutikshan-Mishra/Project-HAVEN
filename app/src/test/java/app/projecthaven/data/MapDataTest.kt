// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data

import app.projecthaven.data.maps.CatalogRepository
import app.projecthaven.data.maps.CatalogState
import app.projecthaven.data.maps.DownloadJob
import app.projecthaven.data.maps.InstallResult
import app.projecthaven.data.maps.MapPackStore
import app.projecthaven.data.maps.MapStyle
import app.projecthaven.data.maps.TileSource
import app.projecthaven.domain.maps.ComponentType
import app.projecthaven.domain.maps.Integrity
import app.projecthaven.domain.maps.PackComponent
import app.projecthaven.domain.maps.RegionManifest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MapPackStoreTest {

    private val root: File = Files.createTempDirectory("haven-maps").toFile()

    private fun pmtiles(seed: Int) = "PMTiles".toByteArray() + byteArrayOf(3) + ByteArray(256) { (it + seed).toByte() }

    /** Stages files as a finished download would, and returns the matching manifest. */
    private fun stage(store: MapPackStore, version: String, bytes: ByteArray, corrupt: Boolean = false): RegionManifest {
        val dir = store.stagingVersionDir("in-up-lko-knp", version).apply { mkdirs() }
        val f = File(dir, "tiles.pmtiles").apply { writeBytes(bytes) }
        val sha = Integrity.sha256Hex(f)
        if (corrupt) f.writeBytes(bytes.copyOf().also { it[20] = (it[20] + 1).toByte() })
        return RegionManifest("in-up-lko-knp", "Lucknow · Unnao · Kanpur", version, listOf(80.0, 26.2, 81.2, 27.1),
            "2026-10-20", "© OpenStreetMap contributors", "ODbL-1.0",
            listOf(PackComponent(ComponentType.TILES, "tiles.pmtiles", "tiles.pmtiles", bytes.size.toLong(), sha)))
    }

    @Test fun installVerifiesAndPersistsAcrossInstances() {
        val store = MapPackStore(root, clock = { 42L })
        val r = stage(store, "2026.10.1", pmtiles(1))
        assertTrue(store.install(r) is InstallResult.Installed)
        assertEquals(listOf("in-up-lko-knp"), store.installed.value.map { it.regionId })
        assertFalse(store.hasPartial("in-up-lko-knp"))
        val reopened = MapPackStore(root)
        assertEquals("2026.10.1", reopened.installed.value.single().version)
    }

    @Test fun corruptUpdateNeverReplacesAWorkingPack() {
        val store = MapPackStore(root)
        store.install(stage(store, "2026.10.1", pmtiles(1)))
        val result = store.install(stage(store, "2026.11.1", pmtiles(2), corrupt = true))
        assertTrue(result is InstallResult.Rejected)
        assertEquals("2026.10.1", store.installed.value.single().version)
        assertTrue(store.fileOf(store.installed.value.single(), "tiles.pmtiles").isFile)
    }

    @Test fun goodUpdateReplacesAndRemovesOldVersion() {
        val store = MapPackStore(root)
        store.install(stage(store, "2026.10.1", pmtiles(1)))
        store.install(stage(store, "2026.11.1", pmtiles(2)))
        assertEquals("2026.11.1", store.installed.value.single().version)
        assertFalse(store.packDir("in-up-lko-knp", "2026.10.1").exists())
    }

    @Test fun deleteAndMissingFilesAreNotShownAsInstalled() {
        val store = MapPackStore(root)
        store.install(stage(store, "2026.10.1", pmtiles(1)))
        store.fileOf(store.installed.value.single(), "tiles.pmtiles").delete()
        assertTrue("pack with missing file must not be listed", MapPackStore(root).installed.value.isEmpty())
        store.delete("in-up-lko-knp")
        assertTrue(store.installed.value.isEmpty())
    }

    @Test fun jobsRoundTripAndPartialDataIsCounted() {
        val store = MapPackStore(root)
        val r = stage(store, "2026.10.1", pmtiles(1))
        store.writeJob(DownloadJob("https://example.invalid/catalog.json", r))
        assertEquals(r, store.readJob(r.id)?.region)
        assertTrue(store.partialBytes(r.id) > 0)
        store.discardPartial(r.id)
        assertEquals(0L, store.partialBytes(r.id))
    }
}

class CatalogParseTest {
    private val repo = CatalogRepository(File(Files.createTempDirectory("haven-cat").toFile(), "c.json"))
    private val sha = "b".repeat(64)

    private fun catalog(regions: String) = """{"format":"haven-map-catalog","formatVersion":1,"generated":"2026-10-20","regions":[$regions]}"""
    private fun region(id: String, url: String = "packs/$id/tiles.pmtiles") =
        """{"id":"$id","name":"R","version":"1","bbox":[80,26,81,27],"dataDate":"2026-10-20","attribution":"© OSM","license":"ODbL-1.0",
           "components":[{"type":"tiles","file":"tiles.pmtiles","url":"$url","bytes":10,"sha256":"$sha"}]}"""

    @Test fun validRegionsAreKeptAndInvalidOnesReported() {
        val s = repo.parse("https://h.example/catalog.json", catalog(region("good") + "," + region("bad", "http://insecure/x")), 1, false)
        s as CatalogState.Loaded
        assertEquals(listOf("good"), s.regions.map { it.id })
        assertTrue("bad" in s.rejected)
    }

    @Test fun malformedCatalogIsAnError() {
        assertTrue(repo.parse("https://h.example/c.json", "{nope", 1, false) is CatalogState.Error)
        assertTrue(repo.parse("https://h.example/c.json", """{"format":"other","formatVersion":1,"generated":"x","regions":[]}""", 1, false) is CatalogState.Error)
    }
}

class MapStyleTest {
    @Test fun styleReferencesOnlyLocalSourcesAndOmitsLabelsWithoutGlyphs() {
        val json = MapStyle.build(listOf(TileSource("pack-a", "pmtiles://file:///data/a.pmtiles", "© OSM")), darkTheme = true, glyphsUrl = null)
        val style = Json.parseToJsonElement(json).jsonObject
        assertEquals("pmtiles://file:///data/a.pmtiles", style["sources"]!!.jsonObject["pack-a"]!!.jsonObject["url"]!!.jsonPrimitive.content)
        val types = style["layers"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content }
        assertFalse(types.contains("symbol"))
        assertFalse(json.contains("http"))
        val withLabels = MapStyle.build(listOf(TileSource("pack-a", "pmtiles://file:///a", "© OSM")), false, "asset://glyphs/{fontstack}/{range}.pbf")
        assertTrue(withLabels.contains("\"symbol\""))
    }
}
