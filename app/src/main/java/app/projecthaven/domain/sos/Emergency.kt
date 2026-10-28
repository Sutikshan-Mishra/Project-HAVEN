// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain.sos

import app.projecthaven.domain.geo.Coordinate
import app.projecthaven.domain.geo.Geo
import kotlin.math.roundToInt

object EmergencyNumbers {
    /** India's Emergency Response Support System number. */
    const val INDIA_EMERGENCY = "112"
}

data class LocationForMessage(
    val coordinate: Coordinate,
    val accuracyMeters: Float?,
    val ageMillis: Long,
)

/**
 * Builds the SMS text a user reviews before sending. It never claims help is on the way.
 */
object EmergencyMessage {

    fun build(
        location: LocationForMessage?,
        includeLocation: Boolean,
        extraNote: String,
        senderName: String?,
    ): String = buildString {
        append("EMERGENCY: I need help.")
        senderName?.trim()?.takeIf { it.isNotEmpty() }?.let { append(" This is ").append(it).append('.') }
        if (includeLocation) {
            if (location != null) {
                append("\nMy location: ").append(Geo.formatDecimal(location.coordinate))
                val details = buildList {
                    location.accuracyMeters?.let { add("±${it.roundToInt()} m") }
                    add(age(location.ageMillis))
                }
                append(" (").append(details.joinToString(", ")).append(')')
                append("\n").append(Geo.openStreetMapLink(location.coordinate))
            } else {
                append("\nMy location is not available.")
            }
        }
        extraNote.trim().takeIf { it.isNotEmpty() }?.let { append("\n").append(it.take(300)) }
    }

    fun age(ms: Long): String = when {
        ms < 60_000 -> "just now"
        ms < 3_600_000 -> "${ms / 60_000} min ago"
        else -> "${ms / 3_600_000} h ago"
    }
}

/** Normalises user-entered phone numbers for dialler and SMS intents. */
object PhoneNumbers {
    private val allowed = Regex("[^0-9+*#]")

    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        val plus = trimmed.startsWith("+")
        val digits = allowed.replace(trimmed, "").replace("+", "")
        return if (plus) "+$digits" else digits
    }

    fun isPlausible(raw: String): Boolean = normalize(raw).count { it.isDigit() } in 3..15
}

// ---- Safety check-in ---------------------------------------------------------------------

enum class CheckInResponse(val label: String) {
    /** Notify the user only. */
    NOTIFY("Remind me"),
    /** Notify the user and offer to prepare alert messages to contacts. */
    NOTIFY_AND_PREPARE("Remind me and prepare alert"),
}

sealed interface CheckInStatus {
    data object Idle : CheckInStatus
    data class Active(val deadlineEpochMs: Long, val remainingMs: Long) : CheckInStatus
    data class Overdue(val deadlineEpochMs: Long, val overdueMs: Long) : CheckInStatus
}

object CheckIn {
    const val MIN_DURATION_MS = 5 * 60_000L
    const val MAX_DURATION_MS = 24 * 3_600_000L

    fun status(deadlineEpochMs: Long?, nowEpochMs: Long): CheckInStatus = when {
        deadlineEpochMs == null -> CheckInStatus.Idle
        nowEpochMs < deadlineEpochMs -> CheckInStatus.Active(deadlineEpochMs, deadlineEpochMs - nowEpochMs)
        else -> CheckInStatus.Overdue(deadlineEpochMs, nowEpochMs - deadlineEpochMs)
    }

    fun deadline(nowEpochMs: Long, durationMs: Long): Long {
        require(durationMs in MIN_DURATION_MS..MAX_DURATION_MS) { "Check-in must be between 5 minutes and 24 hours" }
        return nowEpochMs + durationMs
    }

    fun extend(deadlineEpochMs: Long, nowEpochMs: Long, byMs: Long): Long =
        maxOf(deadlineEpochMs, nowEpochMs) + byMs

    fun formatRemaining(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
