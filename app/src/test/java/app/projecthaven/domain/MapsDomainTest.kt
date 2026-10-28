// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain

import app.projecthaven.domain.geo.Coordinate
import app.projecthaven.domain.maps.Capabilities
import app.projecthaven.domain.maps.ComponentType
import app.projecthaven.domain.maps.EmergencyKind
import app.projecthaven.domain.maps.InstalledComponent
import app.projecthaven.domain.maps.InstalledPack
import app.projecthaven.domain.maps.Integrity
import app.projecthaven.domain.maps.ManifestValidator
import app.projecthaven.domain.maps.MapCatalog
import app.projecthaven.domain.maps.PackComponent
import app.projecthaven.domain.maps.PackEvent
import app.projecthaven.domain.maps.PackState
import app.projecthaven.domain.maps.PackStateMachine
import app.projecthaven.domain.maps.PlaceRecord
import app.projecthaven.domain.maps.PlaceSearch
import app.projecthaven.domain.maps.RegionManifest
import app.projecthaven.domain.maps.RoutingSupport
import app.projecthaven.domain.maps.Validation
import app.projecthaven.domain.maps.Versions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManifestValidatorTest {

    private val sha = "a".repeat(64)

    private fun region(
        id: String = "in-up-lko-knp",
        components: List<PackComponent> = listOf(PackComponent(ComponentType.TILES, "tiles.pmtiles", "tiles.pmtiles", 1000, sha)),
        bbox: List<Double> = listOf(80.0, 26.2, 81.2, 27.1),
    ) = RegionManifest(id, "Lucknow · Unnao · Kanpur", "2026.10.1", bbox, "2026-10-20",
        "© OpenStreetMap contributors", "ODbL-1.0", components)

    private fun problems(r: RegionManifest) = (ManifestValidator.validateRegion(r) as? Validation.Invalid)?.problems.orEmpty()

    @Test fun validRegionPasses() {
        assertEquals(Validation.Valid, ManifestValidator.validateRegion(region()))
    }

    @Test fun pathTraversalAndBadNamesAreRejected() {
        for (bad in listOf("../evil.pmtiles", "a/b.pmtiles", ".hidden.pmtiles", "x..pmtiles")) {
            val r = region(components = listOf(PackComponent(ComponentType.TILES, bad, "u", 10, sha)))
            assertTrue(bad, problems(r).any { it.startsWith("Unsafe file name") })
        }
    }

    @Test fun missingTilesWrongExtensionAndBadHash() {
        assertTrue(problems(region(components = listOf(PackComponent(ComponentType.PLACES, "p.json", "p.json", 10, sha))))
            .contains("A tiles component is required"))
        assertTrue(problems(region(components = listOf(PackComponent(ComponentType.TILES, "t.mbtiles", "t", 10, sha))))
            .contains("Tiles must be a .pmtiles file"))
        assertTrue(problems(region(components = listOf(PackComponent(ComponentType.TILES, "t.pmtiles", "t", 10, "XYZ"))))
            .any { it.contains("SHA-256") })
        assertTrue(problems(region(components = listOf(PackComponent(ComponentType.TILES, "t.pmtiles", "http://x/t", 10, sha))))
            .any { it.contains("https") })
    }

    @Test fun routingNeedsEngineVersion() {
        val r = region(components = listOf(
            PackComponent(ComponentType.TILES, "t.pmtiles", "t", 10, sha),
            PackComponent(ComponentType.ROUTING, "graph.zip", "g", 10, sha),
        ))
        assertTrue(problems(r).any { it.startsWith("Routing data must name") })
    }

    @Test fun bboxAndIds() {
        assertTrue(problems(region(bbox = listOf(81.0, 26.0, 80.0, 27.0))).any { it.contains("inverted") })
        assertTrue(problems(region(bbox = listOf(1.0, 2.0))).isNotEmpty())
        assertTrue(problems(region(id = "Bad ID")).any { it.startsWith("Invalid region id") })
    }

    @Test fun catalogChecks() {
        val ok = MapCatalog(MapCatalog.FORMAT, 1, "2026-10-20", listOf(region()))
        assertEquals(Validation.Valid, ManifestValidator.validateCatalog(ok))
        val dup = ok.copy(regions = listOf(region(), region()))
        assertTrue(ManifestValidator.validateCatalog(dup) is Validation.Invalid)
        val future = ok.copy(formatVersion = 99)
        assertTrue(ManifestValidator.validateCatalog(future) is Validation.Invalid)
    }

    @Test fun urlResolution() {
        assertEquals("https://h.example/packs/a/tiles.pmtiles",
            ManifestValidator.resolveUrl("https://h.example/packs/catalog.json", "a/tiles.pmtiles"))
        assertNull(ManifestValidator.resolveUrl("https://h.example/c.json", "http://other/x"))
    }

    @Test fun versionsCompareNumerically() {
        assertTrue(Versions.compare("2026.10.2", "2026.9.30") > 0)
        assertEquals(0, Versions.compare("1.0", "1.0.0"))
        assertTrue(Versions.compare("1.2", "1.10") < 0)
    }
}

class IntegrityTest {

    private fun temp(bytes: ByteArray): File = File.createTempFile("haven", ".bin").apply {
        deleteOnExit(); writeBytes(bytes)
    }

    @Test fun sha256OfKnownInput() {
        // SHA-256("abc") from FIPS 180-2
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Integrity.sha256Hex("abc".byteInputStream()))
    }

    @Test fun verifyChecksSizeMagicAndHash() {
        val content = "PMTiles".toByteArray() + byteArrayOf(3) + ByteArray(120) { it.toByte() }
        val f = temp(content)
        val hash = Integrity.sha256Hex(f)
        assertEquals(Integrity.FileCheck.Ok, Integrity.verify(f, content.size.toLong(), hash, ComponentType.TILES))
        assertEquals(Integrity.FileCheck.Failed("Size mismatch"), Integrity.verify(f, 5, hash, ComponentType.TILES))
        assertEquals(Integrity.FileCheck.Failed("Checksum mismatch"),
            Integrity.verify(f, content.size.toLong(), "0".repeat(64), ComponentType.TILES))

        val notPm = temp(ByteArray(128))
        assertEquals(Integrity.FileCheck.Failed("Not a PMTiles v3 archive"),
            Integrity.verify(notPm, 128, Integrity.sha256Hex(notPm), ComponentType.TILES))
        assertEquals(Integrity.FileCheck.Ok, Integrity.verify(notPm, 128, Integrity.sha256Hex(notPm), ComponentType.PLACES))
        assertEquals(Integrity.FileCheck.Failed("File missing"),
            Integrity.verify(File("/nonexistent/x"), 1, hash, ComponentType.TILES))
    }

    @Test fun pmtilesMagic() {
        assertTrue(Integrity.looksLikePmtilesV3("PMTiles".toByteArray() + byteArrayOf(3)))
        assertFalse(Integrity.looksLikePmtilesV3("PMTiles".toByteArray() + byteArrayOf(2)))
        assertFalse(Integrity.looksLikePmtilesV3(ByteArray(3)))
    }
}

class PackStateMachineTest {

    private fun run(vararg events: PackEvent): PackState =
        events.fold(PackState.Idle as PackState) { s, e -> PackStateMachine.reduce(s, e) }

    @Test fun happyPath() {
        assertEquals(PackState.Queued, run(PackEvent.Enqueue))
        assertEquals(PackState.Downloading(50, 100), run(PackEvent.Enqueue, PackEvent.Progress(50, 100)))
        assertEquals(PackState.Verifying, run(PackEvent.Enqueue, PackEvent.Progress(100, 100), PackEvent.DownloadFinished))
        assertEquals(PackState.Idle, run(PackEvent.Enqueue, PackEvent.Progress(100, 100), PackEvent.DownloadFinished, PackEvent.Installed))
    }

    @Test fun pauseResumeKeepsProgress() {
        val paused = run(PackEvent.Enqueue, PackEvent.Progress(40, 100), PackEvent.Pause)
        assertEquals(PackState.Paused(40, 100), paused)
        assertEquals(PackState.Queued, PackStateMachine.reduce(paused, PackEvent.Enqueue))
        assertEquals(PackState.Downloading(60, 100), PackStateMachine.reduce(PackState.Queued, PackEvent.Progress(60, 100)))
    }

    @Test fun failureAndRetry() {
        val failed = run(PackEvent.Enqueue, PackEvent.Progress(10, 100), PackEvent.Error("Network lost"))
        assertEquals(PackState.Failed("Network lost", 10), failed)
        assertEquals(PackState.Queued, PackStateMachine.reduce(failed, PackEvent.Enqueue))
        assertEquals(PackState.Idle, PackStateMachine.reduce(failed, PackEvent.Discard))
        assertEquals(PackState.Failed("Checksum mismatch", 0),
            PackStateMachine.reduce(PackState.Verifying, PackEvent.Error("Checksum mismatch")))
    }

    @Test fun invalidTransitionsAreIgnored() {
        assertEquals(PackState.Idle, run(PackEvent.Installed))
        assertEquals(PackState.Idle, run(PackEvent.DownloadFinished))
        assertEquals(PackState.Idle, run(PackEvent.Error("x")))
        assertEquals(PackState.Verifying, PackStateMachine.reduce(PackState.Verifying, PackEvent.Progress(1, 2)))
        assertTrue(PackStateMachine.isActive(PackState.Verifying))
        assertFalse(PackStateMachine.isActive(PackState.Paused(1, 2)))
    }

    @Test fun downloadFraction() {
        assertEquals(0.25f, PackState.Downloading(25, 100).fraction, 1e-6f)
        assertEquals(0f, PackState.Downloading(25, 0).fraction, 1e-6f)
    }
}

class CapabilitiesAndPlacesTest {

    private fun pack(vararg c: InstalledComponent) =
        InstalledPack("r", "R", "1", listOf(0.0, 0.0, 1.0, 1.0), "2026-10-01", "© OSM", "ODbL-1.0", c.toList(), 0)

    @Test fun capabilitiesReflectInstalledComponents() {
        val tilesOnly = Capabilities.of(pack(InstalledComponent(ComponentType.TILES, "t.pmtiles", 1, "a")), emptyMap())
        assertTrue(tilesOnly.rendering)
        assertFalse(tilesOnly.placeSearch)
        assertEquals(RoutingSupport.NO_DATA, tilesOnly.routing)

        val withRouting = pack(
            InstalledComponent(ComponentType.TILES, "t.pmtiles", 1, "a"),
            InstalledComponent(ComponentType.ROUTING, "g.zip", 1, "b", "graphhopper", "9"),
        )
        assertEquals(RoutingSupport.NO_COMPATIBLE_ENGINE, Capabilities.of(withRouting, emptyMap()).routing)
        assertEquals(RoutingSupport.READY, Capabilities.of(withRouting, mapOf("graphhopper" to setOf("9"))).routing)
    }

    private val places = listOf(
        PlaceRecord("Lucknow", "city", 26.8467, 80.9462),
        PlaceRecord("Kanpur", "city", 26.4499, 80.3319),
        PlaceRecord("Unnao", "town", 26.5393, 80.4878),
        PlaceRecord("District Hospital Unnao", "hospital", 26.54, 80.49),
        PlaceRecord("City Pharmacy", "pharmacy", 26.85, 80.95),
    )

    @Test fun searchMatchesAllTokensAndSortsByDistance() {
        val hits = PlaceSearch.search(places, "unnao", near = Coordinate(26.8467, 80.9462))
        assertEquals(listOf("Unnao", "District Hospital Unnao"), hits.map { it.place.name })
        assertTrue(PlaceSearch.search(places, "   ", null).isEmpty())
        assertEquals(listOf("Kanpur"), PlaceSearch.search(places, "kan", null).map { it.place.name })
    }

    @Test fun nearestEmergencyPlaces() {
        val hits = PlaceSearch.nearest(places, setOf(EmergencyKind.HOSPITAL, EmergencyKind.PHARMACY), Coordinate(26.8467, 80.9462))
        assertEquals(listOf("City Pharmacy", "District Hospital Unnao"), hits.map { it.place.name })
    }
}
