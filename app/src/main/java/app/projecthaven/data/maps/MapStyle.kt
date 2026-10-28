// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data.maps

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** One installed vector-tile archive to render. */
data class TileSource(val id: String, val url: String, val attribution: String)

/**
 * Builds a minimal MapLibre style for OpenMapTiles-schema vector tiles (the schema produced
 * by the Planetiler pipeline in tools/mappack). International boundaries are not drawn;
 * see docs/MAP_PACKS.md for why.
 */
object MapStyle {

    private data class Palette(
        val background: String, val water: String, val green: String, val urban: String,
        val building: String, val minor: String, val major: String, val motorway: String,
        val casing: String, val rail: String, val boundary: String, val label: String, val halo: String,
    )

    private val dark = Palette(
        background = "#0B0B0C", water = "#0E2338", green = "#122016", urban = "#141416",
        building = "#1E1E21", minor = "#2C2C2E", major = "#48484A", motorway = "#6E6E73",
        casing = "#000000", rail = "#3A3A3C", boundary = "#8E8E93", label = "#E5E5EA", halo = "#000000",
    )

    private val light = Palette(
        background = "#F2F2F7", water = "#B9D7F0", green = "#DDEBD6", urban = "#ECECEF",
        building = "#DADADF", minor = "#FFFFFF", major = "#FFE8A8", motorway = "#F7C46C",
        casing = "#C7C7CC", rail = "#AEAEB2", boundary = "#8E8E93", label = "#1C1C1E", halo = "#FFFFFF",
    )

    private const val NAME = """["coalesce", ["get", "name:latin"], ["get", "name_en"], ["get", "name"]]"""

    fun build(sources: List<TileSource>, darkTheme: Boolean, glyphsUrl: String?): String {
        val p = if (darkTheme) dark else light
        val style = buildJsonObject {
            put("version", 8)
            put("name", "HAVEN offline")
            if (glyphsUrl != null) put("glyphs", glyphsUrl)
            putJsonObject("sources") {
                sources.forEach { s ->
                    putJsonObject(s.id) {
                        put("type", "vector")
                        put("url", s.url)
                        put("attribution", s.attribution)
                    }
                }
            }
            putJsonArray("layers") {
                add(layer("background", "background", null, null, paint = obj("background-color" to p.background)))
                sources.forEach { s ->
                    val id = s.id
                    add(layer("$id-landcover", "fill", id, "landcover",
                        filter = json("""["in", ["get", "class"], ["literal", ["wood", "grass", "farmland", "wetland"]]]"""),
                        paint = obj("fill-color" to p.green, "fill-opacity" to 0.7)))
                    add(layer("$id-park", "fill", id, "park", paint = obj("fill-color" to p.green, "fill-opacity" to 0.8)))
                    add(layer("$id-landuse", "fill", id, "landuse",
                        filter = json("""["in", ["get", "class"], ["literal", ["residential", "commercial", "industrial", "retail"]]]"""),
                        paint = obj("fill-color" to p.urban)))
                    add(layer("$id-water", "fill", id, "water", paint = obj("fill-color" to p.water)))
                    add(layer("$id-waterway", "line", id, "waterway",
                        paint = obj("line-color" to p.water, "line-width" to json("""["interpolate", ["linear"], ["zoom"], 8, 0.5, 14, 2]"""))))
                    add(layer("$id-building", "fill", id, "building", minZoom = 14, paint = obj("fill-color" to p.building)))
                    add(layer("$id-boundary-state", "line", id, "boundary",
                        filter = json("""["==", ["get", "admin_level"], 4]"""),
                        paint = obj("line-color" to p.boundary, "line-width" to 1, "line-dasharray" to json("[3, 2]"), "line-opacity" to 0.6)))
                    add(layer("$id-rail", "line", id, "transportation", minZoom = 10,
                        filter = json("""["==", ["get", "class"], "rail"]"""),
                        paint = obj("line-color" to p.rail, "line-width" to 1)))
                    add(roadLayer("$id-road-minor", id, """["in", ["get", "class"], ["literal", ["minor", "service", "track", "path"]]]""", p.minor, 12, 0.5, 16, 6, minZoom = 12))
                    add(roadLayer("$id-road-secondary", id, """["in", ["get", "class"], ["literal", ["secondary", "tertiary"]]]""", p.major, 8, 0.5, 16, 8))
                    add(roadLayer("$id-road-primary", id, """["in", ["get", "class"], ["literal", ["primary", "trunk"]]]""", p.major, 6, 0.6, 16, 10))
                    add(roadLayer("$id-road-motorway", id, """["==", ["get", "class"], "motorway"]""", p.motorway, 5, 0.8, 16, 12))
                    if (glyphsUrl != null) {
                        add(layer("$id-road-label", "symbol", id, "transportation_name", minZoom = 13,
                            layout = obj("symbol-placement" to "line", "text-field" to json(NAME),
                                "text-font" to json("""["Inter Regular"]"""), "text-size" to 12),
                            paint = obj("text-color" to p.label, "text-halo-color" to p.halo, "text-halo-width" to 1.2)))
                        add(layer("$id-place-label", "symbol", id, "place",
                            filter = json("""["in", ["get", "class"], ["literal", ["city", "town", "village", "suburb", "neighbourhood"]]]"""),
                            layout = obj("text-field" to json(NAME), "text-font" to json("""["Inter SemiBold"]"""),
                                "text-size" to json("""["match", ["get", "class"], "city", 16, "town", 14, 12]""")),
                            paint = obj("text-color" to p.label, "text-halo-color" to p.halo, "text-halo-width" to 1.5)))
                    }
                }
            }
        }
        return style.toString()
    }

    private fun roadLayer(id: String, source: String, filter: String, color: String, z1: Int, w1: Double, z2: Int, w2: Int, minZoom: Int? = null) =
        layer(id, "line", source, "transportation", minZoom = minZoom, filter = json(filter),
            layout = obj("line-cap" to "round", "line-join" to "round"),
            paint = obj("line-color" to color, "line-width" to json("""["interpolate", ["exponential", 1.4], ["zoom"], $z1, $w1, $z2, $w2]""")))

    private fun layer(
        id: String, type: String, source: String?, sourceLayer: String?,
        filter: JsonElement? = null, minZoom: Int? = null,
        layout: JsonObject? = null, paint: JsonObject? = null,
    ): JsonObject = buildJsonObject {
        put("id", id)
        put("type", type)
        if (source != null) put("source", source)
        if (sourceLayer != null) put("source-layer", sourceLayer)
        if (minZoom != null) put("minzoom", minZoom)
        if (filter != null) put("filter", filter)
        if (layout != null) put("layout", layout)
        if (paint != null) put("paint", paint)
    }

    private fun obj(vararg pairs: Pair<String, Any>): JsonObject = JsonObject(pairs.associate { (k, v) ->
        k to when (v) {
            is JsonElement -> v
            is String -> JsonPrimitive(v)
            is Number -> JsonPrimitive(v)
            is Boolean -> JsonPrimitive(v)
            else -> error("Unsupported style value $v")
        }
    })

    private fun json(text: String): JsonElement = kotlinx.serialization.json.Json.parseToJsonElement(text)
}
