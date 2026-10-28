// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain.maps

import app.projecthaven.domain.geo.Coordinate
import app.projecthaven.domain.geo.Geo
import app.projecthaven.domain.guides.GuideSearch
import kotlinx.serialization.Serializable

/** Place or point-of-interest file produced by the map-pack pipeline from OpenStreetMap. */
@Serializable
data class PlaceFile(
    val format: String,
    val formatVersion: Int,
    /** e.g. "OpenStreetMap contributors (ODbL)" */
    val source: String,
    val dataDate: String,
    val places: List<PlaceRecord>,
) {
    companion object {
        const val FORMAT = "haven-places"
    }
}

@Serializable
data class PlaceRecord(
    val name: String,
    /** OSM-derived kind, e.g. city, town, village, hospital, pharmacy, police, fire_station. */
    val kind: String,
    val lat: Double,
    val lon: Double,
    val address: String? = null,
    val phone: String? = null,
)

enum class EmergencyKind(val osmKind: String, val label: String) {
    HOSPITAL("hospital", "Hospital"),
    CLINIC("clinic", "Clinic"),
    PHARMACY("pharmacy", "Pharmacy"),
    POLICE("police", "Police"),
    FIRE("fire_station", "Fire station");

    companion object {
        fun of(kind: String): EmergencyKind? = entries.firstOrNull { it.osmKind == kind }
    }
}

data class PlaceHit(val place: PlaceRecord, val distanceMeters: Double?)

object PlaceSearch {

    fun validRecords(file: PlaceFile): List<PlaceRecord> = file.places.filter {
        it.name.isNotBlank() && it.lat in -90.0..90.0 && it.lon in -180.0..180.0
    }

    fun search(places: List<PlaceRecord>, query: String, near: Coordinate?, limit: Int = 30): List<PlaceHit> {
        val tokens = GuideSearch.tokenize(query)
        if (tokens.isEmpty()) return emptyList()
        return places.asSequence()
            .filter { p ->
                val name = p.name.lowercase()
                tokens.all { name.contains(it) }
            }
            .map { PlaceHit(it, near?.let { n -> Geo.distanceMeters(n, Coordinate(it.lat, it.lon)) }) }
            .sortedWith(
                compareBy<PlaceHit> { !it.place.name.lowercase().startsWith(tokens.first()) }
                    .thenBy { it.distanceMeters ?: Double.MAX_VALUE }
                    .thenBy { it.place.name },
            )
            .take(limit)
            .toList()
    }

    fun nearest(places: List<PlaceRecord>, kinds: Set<EmergencyKind>, near: Coordinate, limit: Int = 50): List<PlaceHit> =
        places.asSequence()
            .filter { EmergencyKind.of(it.kind) in kinds }
            .map { PlaceHit(it, Geo.distanceMeters(near, Coordinate(it.lat, it.lon))) }
            .sortedBy { it.distanceMeters }
            .take(limit)
            .toList()
}
