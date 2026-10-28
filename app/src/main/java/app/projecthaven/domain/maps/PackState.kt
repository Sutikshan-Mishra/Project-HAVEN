// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.domain.maps

/** Transfer state of one region pack, independent of any version already installed. */
sealed interface PackState {
    data object Idle : PackState
    data object Queued : PackState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : PackState {
        val fraction: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }
    /** Stopped by the user or the system; partial data is kept so it can resume. */
    data class Paused(val downloadedBytes: Long, val totalBytes: Long) : PackState
    data object Verifying : PackState
    data class Failed(val reason: String, val downloadedBytes: Long) : PackState
}

sealed interface PackEvent {
    data object Enqueue : PackEvent
    data class Progress(val downloadedBytes: Long, val totalBytes: Long) : PackEvent
    data object Pause : PackEvent
    data object DownloadFinished : PackEvent
    data object Installed : PackEvent
    data class Error(val reason: String) : PackEvent
    /** User discarded partial data. */
    data object Discard : PackEvent
}

object PackStateMachine {

    /** Returns the next state, or the same state when the event does not apply. */
    fun reduce(state: PackState, event: PackEvent): PackState = when (event) {
        PackEvent.Enqueue -> when (state) {
            PackState.Idle, is PackState.Paused, is PackState.Failed -> PackState.Queued
            else -> state
        }
        is PackEvent.Progress -> when (state) {
            PackState.Queued, is PackState.Downloading, is PackState.Paused ->
                PackState.Downloading(event.downloadedBytes, event.totalBytes)
            else -> state
        }
        PackEvent.Pause -> when (state) {
            is PackState.Downloading -> PackState.Paused(state.downloadedBytes, state.totalBytes)
            PackState.Queued -> PackState.Paused(0, 0)
            else -> state
        }
        PackEvent.DownloadFinished -> when (state) {
            PackState.Queued, is PackState.Downloading -> PackState.Verifying
            else -> state
        }
        PackEvent.Installed -> when (state) {
            PackState.Verifying -> PackState.Idle
            else -> state
        }
        is PackEvent.Error -> when (state) {
            PackState.Idle -> state
            is PackState.Downloading -> PackState.Failed(event.reason, state.downloadedBytes)
            is PackState.Paused -> PackState.Failed(event.reason, state.downloadedBytes)
            else -> PackState.Failed(event.reason, 0)
        }
        PackEvent.Discard -> when (state) {
            is PackState.Paused, is PackState.Failed -> PackState.Idle
            else -> state
        }
    }

    fun isActive(state: PackState): Boolean =
        state is PackState.Queued || state is PackState.Downloading || state is PackState.Verifying
}

/** What an installed pack can actually do on this device with this app version. */
data class PackCapabilities(
    val rendering: Boolean,
    val placeSearch: Boolean,
    val emergencyPlaces: Boolean,
    val routing: RoutingSupport,
)

enum class RoutingSupport {
    /** The pack has no routing data. */
    NO_DATA,
    /** The pack has routing data, but this app has no compatible engine. */
    NO_COMPATIBLE_ENGINE,
    READY,
}

object Capabilities {
    fun of(pack: InstalledPack, supportedEngines: Map<String, Set<String>>): PackCapabilities {
        val routing = pack.component(ComponentType.ROUTING)
        val routingSupport = when {
            routing == null -> RoutingSupport.NO_DATA
            supportedEngines[routing.engine]?.contains(routing.engineVersion) == true -> RoutingSupport.READY
            else -> RoutingSupport.NO_COMPATIBLE_ENGINE
        }
        return PackCapabilities(
            rendering = pack.component(ComponentType.TILES) != null,
            placeSearch = pack.component(ComponentType.PLACES) != null,
            emergencyPlaces = pack.component(ComponentType.POI) != null,
            routing = routingSupport,
        )
    }
}
