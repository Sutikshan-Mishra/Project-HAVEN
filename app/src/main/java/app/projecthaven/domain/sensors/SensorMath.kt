// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain.sensors

import app.projecthaven.domain.geo.Coordinate
import app.projecthaven.domain.geo.Geo
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Smooths compass headings on the circle so 359° → 1° does not swing through 180°. */
class HeadingFilter(private val alpha: Double = 0.15) {
    private var sin = 0.0
    private var cos = 0.0
    private var initialised = false

    fun update(degrees: Double): Double {
        val r = Math.toRadians(degrees)
        if (!initialised) {
            sin = kotlin.math.sin(r); cos = kotlin.math.cos(r); initialised = true
        } else {
            sin += alpha * (kotlin.math.sin(r) - sin)
            cos += alpha * (kotlin.math.cos(r) - cos)
        }
        return Geo.normalizeDegrees(Math.toDegrees(atan2(sin, cos)))
    }

    fun reset() { initialised = false }
}

/** Tilt angles from a gravity vector in device coordinates (m/s²). */
data class Tilt(
    /** Rotation about the device's X axis (top edge up/down), degrees. */
    val pitch: Double,
    /** Rotation about the device's Y axis (left/right), degrees. */
    val roll: Double,
    /** Angle of the device's long edge from vertical when held upright, degrees. */
    val edgeAngle: Double,
)

object LevelMath {
    fun tilt(x: Double, y: Double, z: Double): Tilt? {
        val g = sqrt(x * x + y * y + z * z)
        if (g < 1.0) return null // free fall or no data
        val pitch = Math.toDegrees(atan2(y, hypot(x, z)))
        val roll = Math.toDegrees(atan2(-x, hypot(y, z)))
        val edge = Math.toDegrees(atan2(x, y))
        return Tilt(pitch, roll, edge)
    }

    fun isLevel(angle: Double, tolerance: Double = 0.5): Boolean = abs(angle) <= tolerance
}

/** Screen measurement calibration in pixels per millimetre. */
object Measure {
    /** ISO/IEC 7810 ID-1 card (bank card) long edge. */
    const val ID1_CARD_WIDTH_MM = 85.60
    const val ID1_CARD_HEIGHT_MM = 53.98
    const val MM_PER_INCH = 25.4

    fun pxPerMmFromDpi(dpi: Float): Float = dpi / MM_PER_INCH.toFloat()

    fun calibrate(pixels: Float, knownMillimetres: Double): Float {
        require(pixels > 0f && knownMillimetres > 0.0)
        return (pixels / knownMillimetres).toFloat()
    }

    fun toMillimetres(pixels: Float, pxPerMm: Float): Double = pixels / pxPerMm.toDouble()

    /** Plausible range of real displays, to reject a mis-dragged calibration. */
    fun isPlausible(pxPerMm: Float): Boolean = pxPerMm in 3f..40f
}

object AngleMath {
    /** Angle of the vector (dx, dy) in screen space, 0° = right, counter-clockwise positive. */
    fun screenAngle(dx: Float, dy: Float): Double = Geo.normalizeDegrees(Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble())))

    /** Smallest angle between two directions, 0–180°. */
    fun between(a: Double, b: Double): Double {
        val d = abs(Geo.normalizeDegrees(a) - Geo.normalizeDegrees(b)) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    fun pointAt(center: Pair<Float, Float>, degrees: Double, radius: Float): Pair<Float, Float> {
        val r = Math.toRadians(degrees)
        return Pair(center.first + (radius * cos(r)).toFloat(), center.second - (radius * sin(r)).toFloat())
    }
}

// ---- Speed from location fixes -------------------------------------------------------

data class Fix(
    /** SystemClock.elapsedRealtime of the fix, ms. */
    val elapsedMs: Long,
    val coordinate: Coordinate,
    val accuracyMeters: Float?,
    val speedMps: Float?,
)

sealed interface SpeedReading {
    data object NoFix : SpeedReading
    data class Stale(val ageMs: Long) : SpeedReading
    data class LowAccuracy(val accuracyMeters: Float?) : SpeedReading
    data class Current(val metersPerSecond: Double, val measured: Boolean) : SpeedReading
}

object SpeedLogic {
    const val STALE_AFTER_MS = 5_000L
    const val MAX_ACCURACY_M = 30f

    fun evaluate(latest: Fix?, previous: Fix?, nowElapsedMs: Long): SpeedReading {
        if (latest == null) return SpeedReading.NoFix
        val age = nowElapsedMs - latest.elapsedMs
        if (age > STALE_AFTER_MS) return SpeedReading.Stale(age)
        latest.speedMps?.let { return SpeedReading.Current(it.toDouble().coerceAtLeast(0.0), measured = true) }
        val acc = latest.accuracyMeters
        if (acc == null || acc > MAX_ACCURACY_M) return SpeedReading.LowAccuracy(acc)
        if (previous == null) return SpeedReading.LowAccuracy(acc)
        val dt = (latest.elapsedMs - previous.elapsedMs) / 1000.0
        if (dt <= 0.5) return SpeedReading.LowAccuracy(acc)
        val prevAcc = previous.accuracyMeters ?: return SpeedReading.LowAccuracy(acc)
        if (prevAcc > MAX_ACCURACY_M) return SpeedReading.LowAccuracy(prevAcc)
        val d = Geo.distanceMeters(previous.coordinate, latest.coordinate)
        // Movement smaller than the combined uncertainty is indistinguishable from standing still.
        val speed = if (d < acc + prevAcc) 0.0 else d / dt
        return SpeedReading.Current(speed, measured = false)
    }
}

/** Location quality as shown on the Home status card. */
enum class FixQuality { GOOD, FAIR, POOR }

object FixRules {
    const val GOOD_ACCURACY_M = 25f
    const val FAIR_ACCURACY_M = 100f
    const val FRESH_MS = 2 * 60_000L

    fun quality(accuracyMeters: Float?): FixQuality = when {
        accuracyMeters == null -> FixQuality.POOR
        accuracyMeters <= GOOD_ACCURACY_M -> FixQuality.GOOD
        accuracyMeters <= FAIR_ACCURACY_M -> FixQuality.FAIR
        else -> FixQuality.POOR
    }
}
