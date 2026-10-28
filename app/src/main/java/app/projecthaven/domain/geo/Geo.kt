// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain.geo

import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Coordinate(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
    }
}

sealed interface CoordinateParse {
    data class Ok(val coordinate: Coordinate) : CoordinateParse
    data class Invalid(val reason: String) : CoordinateParse
}

object Geo {
    /** IUGG mean Earth radius in metres. Haversine on a sphere is accurate to roughly 0.5 %. */
    const val EARTH_RADIUS_M = 6_371_008.8

    fun distanceMeters(a: Coordinate, b: Coordinate): Double {
        val phi1 = Math.toRadians(a.latitude)
        val phi2 = Math.toRadians(b.latitude)
        val dPhi = Math.toRadians(b.latitude - a.latitude)
        val dLambda = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dPhi / 2).let { it * it } +
            cos(phi1) * cos(phi2) * sin(dLambda / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    /** Initial great-circle bearing from [a] to [b], 0–360° clockwise from true north. */
    fun initialBearingDegrees(a: Coordinate, b: Coordinate): Double {
        val phi1 = Math.toRadians(a.latitude)
        val phi2 = Math.toRadians(b.latitude)
        val dLambda = Math.toRadians(b.longitude - a.longitude)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return normalizeDegrees(Math.toDegrees(atan2(y, x)))
    }

    fun normalizeDegrees(deg: Double): Double {
        val r = deg % 360.0
        return if (r < 0) r + 360.0 else r
    }

    private val CARDINALS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    fun cardinal(degrees: Double): String {
        val index = ((normalizeDegrees(degrees) + 22.5) / 45.0).toInt() % 8
        return CARDINALS[index]
    }

    /** Parses a single number, a pair "lat, lon" or values with N/S/E/W hemisphere letters. */
    fun parseNumber(text: String): Double? {
        val t = text.trim().replace("°", "").replace(',', '.')
        if (t.isEmpty()) return null
        val hemisphere = t.last().uppercaseChar()
        val sign = when (hemisphere) { 'S', 'W' -> -1.0; else -> 1.0 }
        val numeric = if (hemisphere in "NSEW") t.dropLast(1).trim() else t
        return numeric.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { it * sign }
    }

    fun parseLatitude(text: String): Double? = parseNumber(text)?.takeIf { it in -90.0..90.0 }

    fun parseLongitude(text: String): Double? = parseNumber(text)?.takeIf { it in -180.0..180.0 }

    fun parsePair(text: String): CoordinateParse {
        val parts = text.trim().split(Regex("[,;\\s]+")).filter { it.isNotBlank() }
        if (parts.size != 2) return CoordinateParse.Invalid("Enter latitude and longitude")
        val lat = parseNumber(parts[0]) ?: return CoordinateParse.Invalid("Latitude is not a number")
        val lon = parseNumber(parts[1]) ?: return CoordinateParse.Invalid("Longitude is not a number")
        if (lat !in -90.0..90.0) return CoordinateParse.Invalid("Latitude must be between -90 and 90")
        if (lon !in -180.0..180.0) return CoordinateParse.Invalid("Longitude must be between -180 and 180")
        return CoordinateParse.Ok(Coordinate(lat, lon))
    }

    fun formatDecimal(c: Coordinate, decimals: Int = 5): String =
        String.format(Locale.ROOT, "%.${decimals}f, %.${decimals}f", c.latitude, c.longitude)

    fun formatDms(c: Coordinate): String =
        "${dms(c.latitude)} ${if (c.latitude >= 0) "N" else "S"}, " +
            "${dms(c.longitude)} ${if (c.longitude >= 0) "E" else "W"}"

    private fun dms(value: Double): String {
        val a = abs(value)
        var d = floor(a).toInt()
        val minutesFull = (a - d) * 60
        var m = floor(minutesFull).toInt()
        var s = ((minutesFull - m) * 60 * 10).roundToInt() / 10.0
        if (s >= 60.0) { s -= 60.0; m += 1 }
        if (m >= 60) { m -= 60; d += 1 }
        return String.format(Locale.ROOT, "%d°%02d′%04.1f″", d, m, s)
    }

    fun formatDistance(meters: Double): String = when {
        meters < 1000 -> "${meters.roundToInt()} m"
        meters < 100_000 -> String.format(Locale.ROOT, "%.2f km", meters / 1000)
        else -> String.format(Locale.ROOT, "%.0f km", meters / 1000)
    }

    fun openStreetMapLink(c: Coordinate, zoom: Int = 17): String {
        val lat = String.format(Locale.ROOT, "%.5f", c.latitude)
        val lon = String.format(Locale.ROOT, "%.5f", c.longitude)
        return "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=$zoom/$lat/$lon"
    }
}
