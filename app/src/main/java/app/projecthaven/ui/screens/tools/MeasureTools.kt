// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.screens.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
import app.projecthaven.domain.sensors.AngleMath
import app.projecthaven.domain.sensors.LevelMath
import app.projecthaven.domain.sensors.Measure
import app.projecthaven.ui.LocalAppContainer
import app.projecthaven.ui.components.BigValue
import app.projecthaven.ui.components.EmptyState
import app.projecthaven.ui.components.HavenHeader
import app.projecthaven.ui.components.HavenScreen
import app.projecthaven.ui.components.HavenTextField
import app.projecthaven.ui.components.Hint
import app.projecthaven.ui.components.LocalShowMessage
import app.projecthaven.ui.components.PrimaryButton
import app.projecthaven.ui.components.SecondaryButton
import app.projecthaven.ui.components.Segmented
import app.projecthaven.ui.theme.Dimens
import app.projecthaven.ui.theme.Haven
import app.projecthaven.ui.theme.Inter
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Calibrated pixels per millimetre, or the display's reported density until calibrated. */
@Composable
private fun rememberPxPerMm(): Pair<Float, Boolean> {
    val container = LocalAppContainer.current
    val dpi = LocalContext.current.resources.displayMetrics.ydpi
    val stored by container.settings.rulerPxPerMm.collectAsStateWithLifecycle(initialValue = null)
    val s = stored
    return if (s != null && Measure.isPlausible(s)) s to true else Measure.pxPerMmFromDpi(dpi) to false
}

private enum class LengthUnit(val label: String) { MM("mm"), CM("cm"), IN("in") }

private fun formatLength(mm: Double, unit: LengthUnit): String = when (unit) {
    LengthUnit.MM -> String.format(Locale.ROOT, "%.0f", mm)
    LengthUnit.CM -> String.format(Locale.ROOT, "%.1f", mm / 10)
    LengthUnit.IN -> String.format(Locale.ROOT, "%.2f", mm / Measure.MM_PER_INCH)
}

// ---- Ruler --------------------------------------------------------------------------------

@Composable
fun RulerScreen(back: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val show = LocalShowMessage.current
    val (pxPerMm, calibrated) = rememberPxPerMm()
    var calibrating by remember { mutableStateOf(false) }
    var inches by remember { mutableStateOf(false) }
    var markerY by remember { mutableFloatStateOf(-1f) }
    var calibPx by remember { mutableFloatStateOf(0f) }
    var knownMm by remember { mutableStateOf(String.format(Locale.ROOT, "%.2f", Measure.ID1_CARD_WIDTH_MM)) }

    Column(Modifier.fillMaxSize().background(Haven.colors.background)) {
        HavenHeader("Ruler", onBack = back, largeTitle = false)
        val measurer = rememberTextMeasurer()
        val tick = Haven.colors.text
        val accent = Haven.colors.accent
        val label = TextStyle(fontFamily = Inter, fontSize = 11.sp, color = Haven.colors.textSecondary)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(calibrating) {
                        detectDragGestures(onDragStart = { if (!calibrating) markerY = it.y }) { change, drag ->
                            change.consume()
                            if (calibrating) calibPx = (calibPx + drag.y).coerceAtLeast(20f)
                            else markerY = (markerY + drag.y).coerceIn(0f, size.height.toFloat())
                        }
                    }
                    .semantics { contentDescription = "On-screen ruler" },
            ) {
                if (calibrating) {
                    drawLine(accent, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 3.dp.toPx())
                    drawLine(accent, Offset(0f, calibPx), Offset(size.width, calibPx), strokeWidth = 3.dp.toPx())
                    drawRect(accent.copy(alpha = 0.12f), topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width, calibPx))
                    return@Canvas
                }
                val step = if (inches) pxPerMm * Measure.MM_PER_INCH.toFloat() / 16f else pxPerMm
                var i = 0
                var y = 0f
                while (y <= size.height) {
                    val len = if (inches) when {
                        i % 16 == 0 -> 40.dp.toPx(); i % 8 == 0 -> 28.dp.toPx(); i % 4 == 0 -> 20.dp.toPx(); else -> 12.dp.toPx()
                    } else when {
                        i % 10 == 0 -> 40.dp.toPx(); i % 5 == 0 -> 26.dp.toPx(); else -> 14.dp.toPx()
                    }
                    drawLine(tick, Offset(0f, y), Offset(len, y), strokeWidth = 1.dp.toPx())
                    val major = if (inches) i % 16 == 0 else i % 10 == 0
                    if (major && i > 0) {
                        val t = measurer.measure((if (inches) i / 16 else i / 10).toString(), label)
                        drawText(t, topLeft = Offset(len + 6.dp.toPx(), y - t.size.height / 2f))
                    }
                    i++
                    y = i * step
                }
                if (markerY >= 0f) {
                    drawLine(accent, Offset(0f, markerY), Offset(size.width, markerY), strokeWidth = 2.dp.toPx())
                }
            }
            if (!calibrating && markerY >= 0f) {
                val mm = Measure.toMillimetres(markerY, pxPerMm)
                Text(
                    if (inches) "${formatLength(mm, LengthUnit.IN)} in" else "${formatLength(mm, LengthUnit.CM)} cm",
                    style = Haven.type.title, color = Haven.colors.accent,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = Dimens.gutter),
                )
            }
        }
        Column(Modifier.padding(Dimens.gutter), verticalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
            if (calibrating) {
                Hint("Line up a bank card's long edge (85.60 mm) between the lines, or enter another known length.")
                HavenTextField(knownMm, { knownMm = it }, placeholder = "Length in mm", label = "Known length (mm)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
                    SecondaryButton("Cancel", modifier = Modifier.weight(1f), onClick = { calibrating = false })
                    PrimaryButton("Save", modifier = Modifier.weight(1f), onClick = {
                        val mm = knownMm.replace(',', '.').toDoubleOrNull()
                        val value = if (mm != null && mm > 5) Measure.calibrate(calibPx, mm) else null
                        if (value == null || !Measure.isPlausible(value)) show("That calibration doesn't look right. Try again.")
                        else scope.launch { container.settings.setRulerPxPerMm(value); calibrating = false; show("Ruler calibrated") }
                    })
                }
            } else {
                Segmented(listOf(false, true), inches, { if (it) "Inches" else "Centimetres" }, { inches = it })
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
                    SecondaryButton(if (calibrated) "Recalibrate" else "Calibrate", modifier = Modifier.weight(1f), onClick = {
                        calibPx = Measure.ID1_CARD_WIDTH_MM.toFloat() * pxPerMm
                        calibrating = true
                    })
                    if (calibrated) SecondaryButton("Reset", modifier = Modifier.weight(1f), onClick = { scope.launch { container.settings.setRulerPxPerMm(null) } })
                }
                Hint(if (calibrated) "Approximate. Drag to measure from the top edge." else "Not calibrated. Measurements may be off by several percent.")
            }
        }
    }
}

// ---- Calipers ----------------------------------------------------------------------------

@Composable
fun CalipersScreen(back: () -> Unit) {
    val (pxPerMm, calibrated) = rememberPxPerMm()
    var top by remember { mutableFloatStateOf(120f) }
    var bottom by remember { mutableFloatStateOf(520f) }
    var unit by remember { mutableStateOf(LengthUnit.MM) }
    Column(Modifier.fillMaxSize().background(Haven.colors.background)) {
        HavenHeader("Calipers", onBack = back, largeTitle = false)
        val accent = Haven.colors.accent
        val faint = Haven.colors.surfaceRaised
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        var dragging = 0
                        detectDragGestures(
                            onDragStart = { p -> dragging = if (abs(p.y - top) < abs(p.y - bottom)) 1 else 2 },
                            onDragEnd = { dragging = 0 },
                        ) { change, drag ->
                            change.consume()
                            val h = size.height.toFloat()
                            if (dragging == 1) top = (top + drag.y).coerceIn(0f, bottom - 1f)
                            else bottom = (bottom + drag.y).coerceIn(top + 1f, h)
                        }
                    }
                    .semantics { contentDescription = "Calipers. Drag the two lines to the edges of an object." },
            ) {
                drawRect(faint, topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(size.width, bottom - top))
                listOf(top, bottom).forEach { y ->
                    drawLine(accent, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx())
                    drawCircle(accent, radius = 10.dp.toPx(), center = Offset(size.width - 28.dp.toPx(), y))
                }
            }
            val mm = Measure.toMillimetres(bottom - top, pxPerMm)
            BigValue(formatLength(mm, unit), unit.label, null, Modifier.align(Alignment.Center))
        }
        Column(Modifier.padding(Dimens.gutter), verticalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
            Segmented(LengthUnit.entries.toList(), unit, { it.label }, { unit = it })
            Hint(if (calibrated) "Approximate screen measurement." else "Calibrate in Ruler for better accuracy.")
        }
    }
}

// ---- Protractor --------------------------------------------------------------------------

@Composable
fun ProtractorScreen(back: () -> Unit) {
    var armA by remember { mutableStateOf(0.0) }
    var armB by remember { mutableStateOf(60.0) }
    Column(Modifier.fillMaxSize().background(Haven.colors.background)) {
        HavenHeader("Protractor", onBack = back, largeTitle = false)
        val measurer = rememberTextMeasurer()
        val text = Haven.colors.textSecondary
        val accent = Haven.colors.accent
        val tick = Haven.colors.text
        val labelStyle = TextStyle(fontFamily = Inter, fontSize = 10.sp, color = text)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .padding(Dimens.gutter)
                    .pointerInput(Unit) {
                        var which = 0
                        detectDragGestures(
                            onDragStart = { p ->
                                val c = Offset(size.width / 2f, size.height * 0.75f)
                                val a = AngleMath.screenAngle(p.x - c.x, p.y - c.y)
                                which = if (AngleMath.between(a, armA) <= AngleMath.between(a, armB)) 1 else 2
                            },
                        ) { change, _ ->
                            change.consume()
                            val c = Offset(size.width / 2f, size.height * 0.75f)
                            val a = AngleMath.screenAngle(change.position.x - c.x, change.position.y - c.y)
                            val clamped = if (a > 180.0) (if (a > 270.0) 0.0 else 180.0) else a
                            if (which == 1) armA = clamped else armB = clamped
                        }
                    }
                    .semantics { contentDescription = "Protractor. Drag the two arms." },
            ) {
                val c = Offset(size.width / 2f, size.height * 0.75f)
                val r = minOf(size.width / 2f, size.height * 0.7f) - 8.dp.toPx()
                drawArc(tick, 180f, 180f, useCenter = false, topLeft = Offset(c.x - r, c.y - r),
                    size = androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = Stroke(width = 2.dp.toPx()))
                drawLine(tick, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = 1.dp.toPx())
                for (deg in 0..180) {
                    val len = when { deg % 10 == 0 -> 16.dp.toPx(); deg % 5 == 0 -> 10.dp.toPx(); else -> 5.dp.toPx() }
                    val (x1, y1) = AngleMath.pointAt(c.x to c.y, deg.toDouble(), r)
                    val (x2, y2) = AngleMath.pointAt(c.x to c.y, deg.toDouble(), r - len)
                    drawLine(tick, Offset(x1, y1), Offset(x2, y2), strokeWidth = 1.dp.toPx())
                    if (deg % 30 == 0) {
                        val (lx, ly) = AngleMath.pointAt(c.x to c.y, deg.toDouble(), r - 30.dp.toPx())
                        val t = measurer.measure("$deg", labelStyle)
                        drawText(t, topLeft = Offset(lx - t.size.width / 2f, ly - t.size.height / 2f))
                    }
                }
                listOf(armA, armB).forEach { a ->
                    val (x, y) = AngleMath.pointAt(c.x to c.y, a, r + 6.dp.toPx())
                    drawLine(accent, c, Offset(x, y), strokeWidth = 3.dp.toPx())
                    drawCircle(accent, radius = 11.dp.toPx(), center = Offset(x, y))
                }
                val start = -maxOf(armA, armB).toFloat()
                drawArc(accent.copy(alpha = 0.25f), start, abs(armA - armB).toFloat(), useCenter = true,
                    topLeft = Offset(c.x - r * 0.35f, c.y - r * 0.35f), size = androidx.compose.ui.geometry.Size(r * 0.7f, r * 0.7f))
                drawCircle(accent, radius = 4.dp.toPx(), center = c)
            }
        }
        Column(Modifier.padding(Dimens.gutter), horizontalAlignment = Alignment.CenterHorizontally) {
            BigValue("${abs(armA - armB).roundToInt()}°", null, "Between the arms", Modifier.fillMaxWidth())
            Hint("Place the object's corner on the centre dot.")
        }
    }
}

// ---- Spirit level ------------------------------------------------------------------------

@Composable
fun LevelScreen(back: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val sensors = container.sensors
    if (!sensors.hasAccelerometer) {
        HavenScreen("Spirit level", onBack = back, largeTitle = false) {
            EmptyState(Icons.Rounded.Tune, "Level unavailable", "This device has no motion sensor.")
        }
        return
    }
    val gravity by remember(sensors) { sensors.gravity() }.collectAsStateWithLifecycle(initialValue = null)
    val offsets by container.settings.levelOffsets.collectAsStateWithLifecycle(initialValue = 0f to 0f)
    var edgeMode by remember { mutableStateOf(false) }
    var edgeOffset by remember { mutableStateOf(0.0) }
    val tilt = gravity?.let { LevelMath.tilt(it.x, it.y, it.z) }
    val pitch = tilt?.let { it.pitch - offsets.first }
    val roll = tilt?.let { it.roll - offsets.second }
    val edge = tilt?.let { it.edgeAngle - edgeOffset }
    val level = if (edgeMode) edge?.let { LevelMath.isLevel(it) } else (pitch != null && roll != null && LevelMath.isLevel(pitch) && LevelMath.isLevel(roll))

    HavenScreen("Spirit level", onBack = back, largeTitle = false) {
        Segmented(listOf(false, true), edgeMode, { if (it) "Edge" else "Surface" }, { edgeMode = it })
        val accent = if (level == true) Haven.colors.ready else Haven.colors.accent
        val ring = Haven.colors.textSecondary
        val surface = Haven.colors.surface
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).padding(24.dp).semantics {
            contentDescription = if (level == true) "Level" else "Not level"
        }) {
            val c = center
            val r = size.minDimension / 2
            drawCircle(surface, r, c)
            drawCircle(ring, r * 0.12f, c, style = Stroke(1.5.dp.toPx()))
            drawCircle(ring, r, c, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
            if (!edgeMode) {
                val px = ((roll ?: 0.0) / 30.0).coerceIn(-1.0, 1.0).toFloat()
                val py = ((pitch ?: 0.0) / 30.0).coerceIn(-1.0, 1.0).toFloat()
                drawCircle(accent, r * 0.11f, Offset(c.x - px * r * 0.85f, c.y + py * r * 0.85f))
            } else {
                drawLine(ring, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = 1.dp.toPx())
                val e = ((edge ?: 0.0) / 30.0).coerceIn(-1.0, 1.0).toFloat()
                drawCircle(accent, r * 0.11f, Offset(c.x + e * r * 0.85f, c.y))
            }
        }
        val reading = when {
            tilt == null -> "—"
            edgeMode -> String.format(Locale.ROOT, "%.1f°", abs(edge ?: 0.0))
            else -> String.format(Locale.ROOT, "%.1f° · %.1f°", abs(pitch ?: 0.0), abs(roll ?: 0.0))
        }
        BigValue(reading, null, if (level == true) "Level" else if (edgeMode) "Tilt of the long edge" else "Front–back · side–side", Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.gapSmall)) {
            SecondaryButton("Set zero", modifier = Modifier.weight(1f), enabled = tilt != null, onClick = {
                val t = tilt ?: return@SecondaryButton
                if (edgeMode) edgeOffset = t.edgeAngle
                else scope.launch { container.settings.setLevelOffsets(t.pitch.toFloat(), t.roll.toFloat()) }
            })
            SecondaryButton("Reset", modifier = Modifier.weight(1f), onClick = {
                if (edgeMode) edgeOffset = 0.0 else scope.launch { container.settings.setLevelOffsets(0f, 0f) }
            })
        }
        Hint(if (edgeMode) "Hold the phone upright with its long edge on the surface." else "Lay the phone flat. Calibrate on a surface you know is level.")
    }
}
