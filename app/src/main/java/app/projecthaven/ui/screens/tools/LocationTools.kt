// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.screens.tools

import android.hardware.GeomagneticField
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.projecthaven.data.device.LocationAvailability
import app.projecthaven.data.device.OrientationReading
import app.projecthaven.domain.geo.CoordinateParse
import app.projecthaven.domain.geo.Geo
import app.projecthaven.domain.sensors.Fix
import app.projecthaven.domain.sensors.HeadingFilter
import app.projecthaven.domain.sensors.SpeedLogic
import app.projecthaven.domain.sensors.SpeedReading
import app.projecthaven.ui.LocalAppContainer
import app.projecthaven.ui.components.BigValue
import app.projecthaven.ui.components.EmptyState
import app.projecthaven.ui.components.GroupDivider
import app.projecthaven.ui.components.HavenCard
import app.projecthaven.ui.components.HavenScreen
import app.projecthaven.ui.components.HavenTextField
import app.projecthaven.ui.components.Hint
import app.projecthaven.ui.components.ListGroup
import app.projecthaven.ui.components.ListRow
import app.projecthaven.ui.components.LocalShowMessage
import app.projecthaven.ui.components.SecondaryButton
import app.projecthaven.ui.components.SectionLabel
import app.projecthaven.ui.components.Segmented
import app.projecthaven.ui.components.SystemActions
import app.projecthaven.ui.screens.formatAccuracy
import app.projecthaven.ui.screens.formatAge
import app.projecthaven.ui.screens.rememberLiveLocation
import app.projecthaven.ui.screens.rememberNow
import app.projecthaven.ui.screens.sos.LocationCard
import app.projecthaven.ui.theme.Haven
import app.projecthaven.ui.theme.Inter
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.flow.map
import java.util.Locale
import kotlin.math.roundToInt

// ---- Compass -----------------------------------------------------------------------------

@Composable
fun CompassScreen(back: () -> Unit) {
    val container = LocalAppContainer.current
    val sensors = container.sensors
    if (!sensors.hasCompass) {
        HavenScreen("Compass", onBack = back, largeTitle = false) {
            EmptyState(Icons.Rounded.MyLocation, "Compass unavailable", "This device has no magnetic sensor.")
        }
        return
    }
    val filter = remember { HeadingFilter() }
    val reading by remember(sensors) {
        sensors.orientation().map { OrientationReading(filter.update(it.azimuthDegrees), it.accuracy) }
    }.collectAsStateWithLifecycle(initialValue = null)
    val live = rememberLiveLocation()
    var trueNorth by remember { mutableStateOf(false) }

    val declination = live.fix?.let { f ->
        GeomagneticField(f.coordinate.latitude.toFloat(), f.coordinate.longitude.toFloat(),
            (f.altitudeMeters ?: 0.0).toFloat(), System.currentTimeMillis()).declination.toDouble()
    }
    val heading = reading?.azimuthDegrees?.let { if (trueNorth && declination != null) Geo.normalizeDegrees(it + declination) else it }

    HavenScreen("Compass", onBack = back, largeTitle = false) {
        val measurer = rememberTextMeasurer()
        val text = Haven.colors.text
        val secondary = Haven.colors.textSecondary
        val accent = Haven.colors.accent
        val red = Haven.colors.emergency
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(16.dp)
                .semantics { contentDescription = heading?.let { "Heading ${it.roundToInt()} degrees ${Geo.cardinal(it)}" } ?: "Reading compass" },
        ) {
            val r = size.minDimension / 2
            val c = center
            drawCircle(secondary, radius = r, center = c, style = Stroke(width = 2.dp.toPx()))
            // Fixed pointer at the top showing where the phone points.
            drawLine(accent, Offset(c.x, c.y - r - 4.dp.toPx()), Offset(c.x, c.y - r + 18.dp.toPx()), strokeWidth = 4.dp.toPx())
            rotate(-(heading ?: 0.0).toFloat(), c) {
                for (deg in 0 until 360 step 10) {
                    val major = deg % 30 == 0
                    rotate(deg.toFloat(), c) {
                        drawLine(
                            if (deg == 0) red else secondary,
                            Offset(c.x, c.y - r + 4.dp.toPx()),
                            Offset(c.x, c.y - r + (if (major) 16 else 9).dp.toPx()),
                            strokeWidth = (if (major) 2 else 1).dp.toPx(),
                        )
                    }
                }
                listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (label, deg) ->
                    val rad = Math.toRadians(deg.toDouble() - 90)
                    val pos = Offset(c.x + (r - 36.dp.toPx()) * kotlin.math.cos(rad).toFloat(), c.y + (r - 36.dp.toPx()) * kotlin.math.sin(rad).toFloat())
                    val layout = measurer.measure(label, TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = if (label == "N") red else text))
                    drawText(layout, topLeft = Offset(pos.x - layout.size.width / 2f, pos.y - layout.size.height / 2f))
                }
            }
        }
        BigValue(
            value = heading?.let { "${it.roundToInt() % 360}°" } ?: "—",
            unit = heading?.let { Geo.cardinal(it) },
            caption = if (trueNorth && declination != null) "True north" else "Magnetic north",
            modifier = Modifier.fillMaxWidth(),
        )
        Segmented(listOf(false, true), trueNorth, { if (it) "True" else "Magnetic" }, { trueNorth = it })
        if (trueNorth && declination == null) Hint("True north needs a GPS fix to work out local declination.")
        val acc = reading?.accuracy
        if (acc != null && acc <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
            Hint("Low accuracy. Move the phone in a figure 8 to calibrate.", color = Haven.colors.attention)
        } else {
            Hint("Keep away from metal, magnets and vehicles.")
        }
    }
}

// ---- Coordinates -------------------------------------------------------------------------

@Composable
fun CoordinatesScreen(back: () -> Unit) {
    val live = rememberLiveLocation()
    val now = rememberNow()
    val context = LocalContext.current
    val show = LocalShowMessage.current
    HavenScreen("GPS coordinates", onBack = back, largeTitle = false) {
        LocationCard(live, now)
        val fix = live.fix
        if (fix != null) {
            ListGroup {
                ListRow("Degrees, minutes, seconds", subtitle = Geo.formatDms(fix.coordinate), trailing = {
                    SecondaryButton("Copy", icon = Icons.Rounded.ContentCopy, onClick = {
                        SystemActions.copy(context, "Coordinates", Geo.formatDms(fix.coordinate)); show("Copied")
                    })
                })
                GroupDivider(inset = 16.dp)
                ListRow("Accuracy", value = formatAccuracy(fix.accuracyMeters))
                GroupDivider(inset = 16.dp)
                ListRow("Updated", value = formatAge(fix.ageMs(now)))
                GroupDivider(inset = 16.dp)
                ListRow("Source", value = when (fix.provider) { "gps" -> "Satellites"; "network" -> "Network"; else -> fix.provider ?: "Unknown" })
            }
            Hint("Satellite fixes work without mobile data.")
        }
    }
}

// ---- Distance ----------------------------------------------------------------------------

@Composable
fun DistanceScreen(back: () -> Unit) {
    val live = rememberLiveLocation()
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val pa = remember(a) { if (a.isBlank()) null else Geo.parsePair(a) }
    val pb = remember(b) { if (b.isBlank()) null else Geo.parsePair(b) }

    HavenScreen("Distance", onBack = back, largeTitle = false) {
        HavenTextField(
            a, { a = it }, placeholder = "26.84670, 80.94620", label = "From (latitude, longitude)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            isError = pa is CoordinateParse.Invalid, supportingText = (pa as? CoordinateParse.Invalid)?.reason,
        )
        if (live.fix != null) {
            SecondaryButton("Use my location", icon = Icons.Rounded.MyLocation, onClick = {
                a = Geo.formatDecimal(live.fix.coordinate)
            })
        }
        HavenTextField(
            b, { b = it }, placeholder = "26.44990, 80.33190", label = "To (latitude, longitude)",
            isError = pb is CoordinateParse.Invalid, supportingText = (pb as? CoordinateParse.Invalid)?.reason,
        )
        val ca = (pa as? CoordinateParse.Ok)?.coordinate
        val cb = (pb as? CoordinateParse.Ok)?.coordinate
        if (ca != null && cb != null) {
            val meters = Geo.distanceMeters(ca, cb)
            val bearing = Geo.initialBearingDegrees(ca, cb)
            HavenCard(Modifier.fillMaxWidth()) {
                BigValue(String.format(Locale.ROOT, "%.2f", meters / 1000), "km", String.format(Locale.ROOT, "%.2f miles", meters / 1609.344),
                    Modifier.align(Alignment.CenterHorizontally))
            }
            ListGroup {
                ListRow("Initial bearing", value = "${bearing.roundToInt()}° ${Geo.cardinal(bearing)}")
            }
            Hint("Straight-line distance over the Earth's surface, not road distance.")
        }
    }
}

// ---- Speed and altitude ----------------------------------------------------------------------

private enum class SpeedUnit(val label: String, val factor: Double) { KMH("km/h", 3.6), MPH("mph", 2.2369362921), MPS("m/s", 1.0) }

@Composable
fun SpeedScreen(back: () -> Unit) {
    val container = LocalAppContainer.current
    val live = rememberLiveLocation()
    rememberNow(1000)
    var unit by remember { mutableStateOf(SpeedUnit.KMH) }
    val pressure by remember(container) { container.sensors.pressure() }.collectAsStateWithLifecycle(initialValue = null as Float?)

    val toFix: (app.projecthaven.data.device.LocationFix?) -> Fix? = { f -> f?.let { Fix(it.elapsedRealtimeMs, it.coordinate, it.accuracyMeters, it.speedMps) } }
    val reading = SpeedLogic.evaluate(toFix(live.fix), toFix(live.previous), SystemClock.elapsedRealtime())

    HavenScreen("Speed and altitude", onBack = back, largeTitle = false) {
        if (live.availability != LocationAvailability.READY) {
            LocationCard(live, SystemClock.elapsedRealtime())
        } else {
            HavenCard(Modifier.fillMaxWidth()) {
                val (value, caption) = when (reading) {
                    SpeedReading.NoFix -> "—" to "Searching for GPS…"
                    is SpeedReading.Stale -> "—" to "No recent fix"
                    is SpeedReading.LowAccuracy -> "—" to "Accuracy too low (${formatAccuracy(reading.accuracyMeters)})"
                    is SpeedReading.Current -> String.format(Locale.ROOT, "%.1f", reading.metersPerSecond * unit.factor) to
                        (if (reading.measured) "GPS speed" else "Estimated from position")
                }
                BigValue(value, unit.label, caption, Modifier.align(Alignment.CenterHorizontally))
            }
            Segmented(SpeedUnit.entries.toList(), unit, { it.label }, { unit = it })
        }

        SectionLabel("Altitude")
        val fix = live.fix
        val rows = buildList {
            fix?.mslAltitudeMeters?.let { add("Above sea level (GPS)" to "${it.roundToInt()} m") }
            if (fix?.mslAltitudeMeters == null) fix?.altitudeMeters?.let { add("GPS height (ellipsoid)" to "${it.roundToInt()} m") }
            pressure?.let {
                add("Barometric (uncalibrated)" to "${SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, it).roundToInt()} m")
            }
        }
        if (rows.isEmpty()) {
            Hint("Altitude is not available on this device right now.")
        } else {
            ListGroup {
                rows.forEachIndexed { i, (label, v) ->
                    if (i > 0) GroupDivider(inset = 16.dp)
                    ListRow(label, value = v)
                }
            }
            Hint("GPS height can differ from sea level by tens of metres. Barometric height changes with the weather.")
        }
    }
}
