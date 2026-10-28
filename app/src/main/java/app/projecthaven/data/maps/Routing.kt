// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data.maps

import app.projecthaven.domain.geo.Coordinate

/**
 * Integration point for a real offline routing engine (for example GraphHopper or Valhalla
 * built for Android). An implementation must compute routes entirely from installed routing
 * data that preserves road classes, access and turn restrictions. Nothing in V1 implements
 * this interface; see docs/ROUTING.md.
 */
interface RoutingEngine {
    /** Matches PackComponent.engine in the region manifest. */
    val engineId: String
    val supportedDataVersions: Set<String>

    suspend fun route(request: RouteRequest): RouteResult
}

enum class TravelMode { CAR, MOTORCYCLE, BICYCLE, FOOT }

data class RouteRequest(
    val regionId: String,
    val from: Coordinate,
    val to: Coordinate,
    val mode: TravelMode,
)

data class RouteInstruction(
    val text: String,
    val distanceMeters: Double,
    val at: Coordinate,
)

sealed interface RouteResult {
    data class Route(
        val geometry: List<Coordinate>,
        val distanceMeters: Double,
        val instructions: List<RouteInstruction>,
    ) : RouteResult
    data class NoRoute(val reason: String) : RouteResult
    data class Unavailable(val reason: String) : RouteResult
}
