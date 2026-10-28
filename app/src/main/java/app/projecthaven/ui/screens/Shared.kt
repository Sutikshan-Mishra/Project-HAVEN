// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.screens

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.projecthaven.data.device.LocationAvailability
import app.projecthaven.data.device.LocationFix
import app.projecthaven.ui.LocalAppContainer
import app.projecthaven.ui.rememberResumeTick
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

data class LiveLocation(
    val availability: LocationAvailability,
    val fix: LocationFix?,
    /** Previous fix, for computing speed when the platform does not report it. */
    val previous: LocationFix?,
    /** True once updates have been running long enough that "no fix" means "searching". */
    val searching: Boolean,
)

/**
 * Live location while the screen is visible (STARTED). Stops automatically in the
 * background, so HAVEN never tracks location when not on screen.
 */
@Composable
fun rememberLiveLocation(active: Boolean = true): LiveLocation {
    val container = LocalAppContainer.current
    val tracker = container.location
    val tick = rememberResumeTick()
    val availability = remember(tick) { tracker.availability() }
    var fix by remember { mutableStateOf<LocationFix?>(null) }
    var previous by remember { mutableStateOf<LocationFix?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(availability, active) {
        if (!active || availability != LocationAvailability.READY) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            tracker.updates().collect { new ->
                val current = fix
                if (current == null || new.elapsedRealtimeMs > current.elapsedRealtimeMs) {
                    previous = current
                    fix = new
                }
            }
        }
    }
    return LiveLocation(availability, fix, previous, searching = availability == LocationAvailability.READY && fix == null)
}

/** Recomposes every [periodMs] so ages ("12 s ago") stay current. */
@Composable
fun rememberNow(periodMs: Long = 1000L): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            now = SystemClock.elapsedRealtime()
        }
    }
    return now
}

fun formatAge(ms: Long): String = when {
    ms < 5_000 -> "now"
    ms < 60_000 -> "${ms / 1000} s ago"
    ms < 3_600_000 -> "${ms / 60_000} min ago"
    else -> "${ms / 3_600_000} h ago"
}

fun formatAccuracy(m: Float?): String = m?.let { "±${it.roundToInt()} m" } ?: "accuracy unknown"

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
}

@Composable
fun <T> rememberMutable(initial: T): MutableState<T> = remember { mutableStateOf(initial) }
