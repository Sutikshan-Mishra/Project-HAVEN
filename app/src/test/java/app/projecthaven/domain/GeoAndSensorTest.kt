// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain

import app.projecthaven.domain.geo.Coordinate
import app.projecthaven.domain.geo.CoordinateParse
import app.projecthaven.domain.geo.Geo
import app.projecthaven.domain.sensors.AngleMath
import app.projecthaven.domain.sensors.Fix
import app.projecthaven.domain.sensors.FixQuality
import app.projecthaven.domain.sensors.FixRules
import app.projecthaven.domain.sensors.HeadingFilter
import app.projecthaven.domain.sensors.LevelMath
import app.projecthaven.domain.sensors.Measure
import app.projecthaven.domain.sensors.SpeedLogic
import app.projecthaven.domain.sensors.SpeedReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    @Test fun oneDegreeOfLongitudeAtEquator() {
        val d = Geo.distanceMeters(Coordinate(0.0, 0.0), Coordinate(0.0, 1.0))
        assertEquals(2 * Math.PI * Geo.EARTH_RADIUS_M / 360.0, d, 1e-6)
    }

    @Test fun zeroDistanceToSelfAndSymmetry() {
        val a = Coordinate(26.8467, 80.9462)
        val b = Coordinate(26.4499, 80.3319)
        assertEquals(0.0, Geo.distanceMeters(a, a), 1e-9)
        assertEquals(Geo.distanceMeters(a, b), Geo.distanceMeters(b, a), 1e-9)
    }

    @Test fun londonToParisIsAbout344Km() {
        val d = Geo.distanceMeters(Coordinate(51.5074, -0.1278), Coordinate(48.8566, 2.3522))
        assertEquals(343_500.0, d, 1_500.0)
    }

    @Test fun antipodesAreHalfCircumference() {
        val d = Geo.distanceMeters(Coordinate(0.0, 0.0), Coordinate(0.0, 180.0))
        assertEquals(Math.PI * Geo.EARTH_RADIUS_M, d, 1e-3)
    }

    @Test fun bearings() {
        assertEquals(0.0, Geo.initialBearingDegrees(Coordinate(0.0, 0.0), Coordinate(1.0, 0.0)), 1e-9)
        assertEquals(90.0, Geo.initialBearingDegrees(Coordinate(0.0, 0.0), Coordinate(0.0, 1.0)), 1e-9)
        assertEquals("N", Geo.cardinal(359.0))
        assertEquals("NE", Geo.cardinal(45.0))
        assertEquals("W", Geo.cardinal(-90.0))
    }

    @Test fun parsing() {
        val ok = Geo.parsePair("26.8467, 80.9462") as CoordinateParse.Ok
        assertEquals(26.8467, ok.coordinate.latitude, 1e-12)
        val hemi = Geo.parsePair("33.9S 18.4E") as CoordinateParse.Ok
        assertEquals(-33.9, hemi.coordinate.latitude, 1e-12)
        assertTrue(Geo.parsePair("91, 10") is CoordinateParse.Invalid)
        assertTrue(Geo.parsePair("abc, 10") is CoordinateParse.Invalid)
        assertTrue(Geo.parsePair("10") is CoordinateParse.Invalid)
        assertNull(Geo.parseLatitude("-95"))
        assertEquals(-80.5, Geo.parseLongitude("80.5 W")!!, 1e-12)
    }

    @Test fun invalidCoordinateConstruction() {
        assertThrows(IllegalArgumentException::class.java) { Coordinate(100.0, 0.0) }
    }

    @Test fun formatting() {
        assertEquals("26.84670, 80.94620", Geo.formatDecimal(Coordinate(26.8467, 80.9462)))
        assertEquals("0°30′00.0″ N, 0°30′00.0″ W", Geo.formatDms(Coordinate(0.5, -0.5)))
        assertEquals("850 m", Geo.formatDistance(850.0))
        assertEquals("76.40 km", Geo.formatDistance(76_400.0))
        assertTrue(Geo.openStreetMapLink(Coordinate(1.0, 2.0)).startsWith("https://www.openstreetmap.org/?mlat=1.00000&mlon=2.00000"))
    }
}

class SensorMathTest {

    @Test fun headingFilterWrapsAroundNorth() {
        val f = HeadingFilter(alpha = 0.5)
        f.update(359.0)
        val h = f.update(1.0)
        assertTrue("heading $h should stay near north", h > 355.0 || h < 5.0)
    }

    @Test fun levelFlatPhoneReadsZero() {
        val t = LevelMath.tilt(0.0, 0.0, 9.81)!!
        assertEquals(0.0, t.pitch, 1e-9)
        assertEquals(0.0, t.roll, 1e-9)
        assertNull(LevelMath.tilt(0.0, 0.0, 0.1))
        val upright = LevelMath.tilt(0.0, 9.81, 0.0)!!
        assertEquals(0.0, upright.edgeAngle, 1e-9)
        assertTrue(LevelMath.isLevel(0.3))
    }

    @Test fun measureCalibration() {
        val pxPerMm = Measure.calibrate(856.0f, Measure.ID1_CARD_WIDTH_MM)
        assertEquals(10.0f, pxPerMm, 1e-4f)
        assertEquals(42.0, Measure.toMillimetres(420f, pxPerMm), 1e-3)
        assertTrue(Measure.isPlausible(pxPerMm))
        assertEquals(17.0f, Measure.pxPerMmFromDpi(431.8f), 1e-3f)
        assertThrows(IllegalArgumentException::class.java) { Measure.calibrate(0f, 10.0) }
    }

    @Test fun angles() {
        assertEquals(90.0, AngleMath.screenAngle(0f, -10f), 1e-9) // screen y grows downwards
        assertEquals(20.0, AngleMath.between(350.0, 10.0), 1e-9)
        assertEquals(180.0, AngleMath.between(0.0, 180.0), 1e-9)
    }

    @Test fun speedLogic() {
        val p = Coordinate(26.0, 80.0)
        assertEquals(SpeedReading.NoFix, SpeedLogic.evaluate(null, null, 0))
        assertTrue(SpeedLogic.evaluate(Fix(0, p, 5f, 3f), null, 10_000) is SpeedReading.Stale)
        assertEquals(SpeedReading.Current(3.0, true), SpeedLogic.evaluate(Fix(1000, p, 50f, 3f), null, 1500))
        assertTrue(SpeedLogic.evaluate(Fix(1000, p, 80f, null), null, 1500) is SpeedReading.LowAccuracy)

        // 100 m north in 10 s with 5 m accuracy -> ~10 m/s computed
        val q = Coordinate(26.0 + 100.0 / 111_195.0, 80.0)
        val r = SpeedLogic.evaluate(Fix(11_000, q, 5f, null), Fix(1_000, p, 5f, null), 11_500) as SpeedReading.Current
        assertEquals(10.0, r.metersPerSecond, 0.1)
        assertEquals(false, r.measured)

        // Jitter inside accuracy circles reads as stationary
        val jitter = Coordinate(26.0 + 4.0 / 111_195.0, 80.0)
        val s = SpeedLogic.evaluate(Fix(11_000, jitter, 5f, null), Fix(1_000, p, 5f, null), 11_500) as SpeedReading.Current
        assertEquals(0.0, s.metersPerSecond, 1e-9)
    }

    @Test fun fixQuality() {
        assertEquals(FixQuality.GOOD, FixRules.quality(8f))
        assertEquals(FixQuality.FAIR, FixRules.quality(60f))
        assertEquals(FixQuality.POOR, FixRules.quality(500f))
        assertEquals(FixQuality.POOR, FixRules.quality(null))
    }
}
