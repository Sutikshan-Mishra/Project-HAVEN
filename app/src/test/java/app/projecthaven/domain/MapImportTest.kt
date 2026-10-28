// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain

import app.projecthaven.domain.maps.HeaderResult
import app.projecthaven.domain.maps.ImportKind
import app.projecthaven.domain.maps.MapImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MapImportTest {

    private fun header(tileType: Int = 1, west: Double = 80.0, south: Double = 26.0, east: Double = 81.0, north: Double = 27.0): ByteArray {
        val h = ByteArray(MapImport.HEADER_SIZE)
        "PMTiles".toByteArray().copyInto(h)
        h[7] = 3
        h[97] = 1
        h[99] = tileType.toByte()
        h[100] = 0
        h[101] = 14
        ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(102, (west * 1e7).toInt()); putInt(106, (south * 1e7).toInt())
            putInt(110, (east * 1e7).toInt()); putInt(114, (north * 1e7).toInt())
        }
        return h
    }

    @Test fun classifiesByFileName() {
        assertEquals(ImportKind.PMTILES, MapImport.classify("Lucknow.PMTiles"))
        assertEquals(ImportKind.OSM_SOURCE, MapImport.classify("india-latest.osm.pbf"))
        assertEquals(ImportKind.OSM_SOURCE, MapImport.classify("area.osm"))
        assertEquals(ImportKind.UNSUPPORTED, MapImport.classify("photo.jpg"))
        assertEquals(ImportKind.UNSUPPORTED, MapImport.classify(null))
    }

    @Test fun readsHeader() {
        val ok = MapImport.parseHeader(header()) as HeaderResult.Ok
        assertEquals(1, ok.info.tileType)
        assertEquals(14, ok.info.maxZoom)
        assertEquals(listOf(80.0, 26.0, 81.0, 27.0), ok.info.bbox)
    }

    @Test fun emptyBoxFallsBackToNull() {
        val ok = MapImport.parseHeader(header(west = 0.0, south = 0.0, east = 0.0, north = 0.0)) as HeaderResult.Ok
        assertNull(ok.info.bbox)
    }

    @Test fun rejectsNonPmtiles() {
        assertTrue(MapImport.parseHeader(ByteArray(10)) is HeaderResult.Bad)
        assertTrue(MapImport.parseHeader(ByteArray(MapImport.HEADER_SIZE)) is HeaderResult.Bad)
    }

    @Test fun readsMetadata() {
        val m = MapImport.parseMetadata(
            """{"name":"Lucknow","attribution":"<a href=\"x\">© OSM</a> contributors","vector_layers":[{"id":"water"},{"id":"transportation"}]}""",
        )
        assertEquals("Lucknow", m.name)
        assertEquals("© OSM contributors", m.attribution)
        assertEquals(setOf("water", "transportation"), m.layers)
        assertTrue(MapImport.looksLikeOpenMapTiles(m.layers))
        assertFalse(MapImport.looksLikeOpenMapTiles(setOf("roads", "pois")))
        assertTrue(MapImport.looksLikeOpenMapTiles(emptySet()))
    }

    @Test fun badMetadataIsHarmless() {
        val m = MapImport.parseMetadata("not json")
        assertNull(m.name)
        assertTrue(m.layers.isEmpty())
    }

    @Test fun regionIdsAreValidAndUnique() {
        val id = MapImport.regionId("My City Map (v2).pmtiles", emptySet())
        assertEquals("my-my-city-map-v2", id)
        assertTrue(Regex("^[a-z0-9][a-z0-9-]{1,62}$").matches(id))
        assertEquals("my-my-city-map-v2-2", MapImport.regionId("My City Map (v2).pmtiles", setOf(id)))
        assertEquals("my-map", MapImport.regionId("???.pmtiles", emptySet()))
        assertTrue(Regex("^[a-z0-9][a-z0-9-]{1,62}$").matches(MapImport.regionId("a".repeat(200) + ".pmtiles", emptySet())))
    }

    @Test fun displayNameIsReadable() {
        assertEquals("lucknow city", MapImport.displayName("lucknow_city.pmtiles"))
        assertEquals("Imported map", MapImport.displayName(null))
    }
}
