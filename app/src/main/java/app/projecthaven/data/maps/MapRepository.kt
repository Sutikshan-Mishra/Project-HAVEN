// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data.maps

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.projecthaven.domain.maps.Capabilities
import app.projecthaven.domain.maps.InstalledPack
import app.projecthaven.domain.maps.PackCapabilities
import app.projecthaven.domain.maps.PackEvent
import app.projecthaven.domain.maps.PackState
import app.projecthaven.domain.maps.PackStateMachine
import app.projecthaven.domain.maps.RegionManifest
import app.projecthaven.domain.maps.Versions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.TimeUnit

data class RegionUi(
    val id: String,
    val name: String,
    val available: RegionManifest?,
    val installed: InstalledPack?,
    val transfer: PackState,
    val updateAvailable: Boolean,
    val capabilities: PackCapabilities?,
    val partialBytes: Long,
)

/** Joins the catalog, installed packs and WorkManager state into one list for the UI. */
class MapRepository(
    private val context: Context,
    val store: MapPackStore,
    val catalog: CatalogRepository,
) {
    private val work get() = WorkManager.getInstance(context)

    val regions: Flow<List<RegionUi>> = combine(
        catalog.state,
        store.installed,
        work.getWorkInfosByTagFlow(MapDownloadWorker.TAG),
    ) { catalogState, installed, infos ->
        val available = when (catalogState) {
            is CatalogState.Loaded -> catalogState.regions
            is CatalogState.Error -> catalogState.cached?.regions.orEmpty()
            else -> emptyList()
        }
        val ids = (installed.map { it.regionId } + available.map { it.id }).distinct()
        ids.map { id ->
            val manifest = available.firstOrNull { it.id == id }
            val pack = installed.firstOrNull { it.regionId == id }
            val info = infos.filter { MapDownloadWorker.regionTag(id) in it.tags }.lastOrNull { !it.state.isFinished }
                ?: infos.lastOrNull { MapDownloadWorker.regionTag(id) in it.tags }
            val partial = store.partialBytes(id)
            RegionUi(
                id = id,
                name = manifest?.name ?: pack?.name ?: id,
                available = manifest,
                installed = pack,
                transfer = stateFrom(info, partial, manifest?.totalBytes ?: 0L, pack),
                updateAvailable = pack != null && manifest != null && Versions.compare(manifest.version, pack.version) > 0,
                capabilities = pack?.let { Capabilities.of(it, OfflineRouting.supportedEngines) },
                partialBytes = partial,
            )
        }.sortedWith(compareByDescending<RegionUi> { it.installed != null }.thenBy { it.name })
    }.flowOn(Dispatchers.IO) // partial-download sizes are read from disk

    private fun stateFrom(info: WorkInfo?, partial: Long, total: Long, installed: InstalledPack?): PackState {
        var s: PackState = PackState.Idle
        fun e(ev: PackEvent) { s = PackStateMachine.reduce(s, ev) }
        when (info?.state) {
            null -> if (partial > 0) { e(PackEvent.Enqueue); e(PackEvent.Progress(partial, total)); e(PackEvent.Pause) }
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                e(PackEvent.Enqueue)
            }
            WorkInfo.State.RUNNING -> {
                e(PackEvent.Enqueue)
                val p = info.progress
                if (p.getString(MapDownloadWorker.KEY_PHASE) == MapDownloadWorker.PHASE_VERIFY) e(PackEvent.DownloadFinished)
                else e(PackEvent.Progress(p.getLong(MapDownloadWorker.KEY_DONE, partial), p.getLong(MapDownloadWorker.KEY_TOTAL, total)))
            }
            WorkInfo.State.SUCCEEDED -> Unit
            WorkInfo.State.FAILED -> {
                e(PackEvent.Enqueue)
                if (partial > 0) e(PackEvent.Progress(partial, total))
                e(PackEvent.Error(info.outputData.getString(MapDownloadWorker.KEY_REASON) ?: "Download failed"))
            }
            WorkInfo.State.CANCELLED -> if (partial > 0) {
                e(PackEvent.Enqueue); e(PackEvent.Progress(partial, total)); e(PackEvent.Pause)
            }
        }
        // A finished job for a version that is now installed is simply "installed".
        if (s is PackState.Failed && installed != null && partial == 0L) s = PackState.Idle
        return s
    }

    fun download(region: RegionManifest, catalogUrl: String, unmeteredOnly: Boolean) {
        store.writeJob(DownloadJob(catalogUrl, region))
        val request = OneTimeWorkRequestBuilder<MapDownloadWorker>()
            .setInputData(workDataOf(MapDownloadWorker.KEY_REGION to region.id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(MapDownloadWorker.TAG)
            .addTag(MapDownloadWorker.regionTag(region.id))
            .build()
        work.enqueueUniqueWork(MapDownloadWorker.uniqueName(region.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(regionId: String) {
        work.cancelUniqueWork(MapDownloadWorker.uniqueName(regionId))
    }

    fun discard(regionId: String) {
        work.cancelUniqueWork(MapDownloadWorker.uniqueName(regionId))
        store.discardPartial(regionId)
        work.pruneWork()
    }

    fun delete(regionId: String) {
        work.cancelUniqueWork(MapDownloadWorker.uniqueName(regionId))
        store.delete(regionId)
    }
}

/**
 * Offline routing engines this build can run, keyed by engine id with the data-format
 * versions each supports. V1 ships none, so every region reports routing as unavailable
 * instead of drawing invented routes. See docs/ROUTING.md for the integration plan.
 */
object OfflineRouting {
    val supportedEngines: Map<String, Set<String>> = emptyMap()
}
