// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data.maps

import app.projecthaven.domain.maps.InstalledComponent
import app.projecthaven.domain.maps.InstalledIndex
import app.projecthaven.domain.maps.InstalledPack
import app.projecthaven.domain.maps.Integrity
import app.projecthaven.domain.maps.ManifestValidator
import app.projecthaven.domain.maps.RegionManifest
import app.projecthaven.domain.maps.Validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

sealed interface InstallResult {
    data class Installed(val pack: InstalledPack) : InstallResult
    data class Rejected(val reason: String) : InstallResult
}

/**
 * On-disk layout under [root]:
 *  packs/<region>/<version>/<files>   verified, installed data
 *  staging/<region>/<version>/<files> downloads in progress (*.part while incomplete)
 *  staging/<region>/job.json          what is being downloaded
 *  installed.json                     index of installed packs (written atomically)
 *
 * A new version is only switched in after every file has been verified, and the previous
 * version is deleted only after the index points at the new one.
 */
class MapPackStore(
    private val root: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }
    private val packsDir = File(root, "packs")
    private val stagingRoot = File(root, "staging")
    private val indexFile = File(root, "installed.json")

    private val _installed = MutableStateFlow(readIndex().packs)
    val installed: StateFlow<List<InstalledPack>> = _installed.asStateFlow()

    fun packDir(regionId: String, version: String) = File(File(packsDir, regionId), version)

    fun stagingDir(regionId: String) = File(stagingRoot, regionId)

    fun stagingVersionDir(regionId: String, version: String) = File(stagingDir(regionId), version)

    fun fileOf(pack: InstalledPack, file: String): File = File(packDir(pack.regionId, pack.version), file)

    fun partialBytes(regionId: String): Long =
        stagingDir(regionId).takeIf { it.exists() }?.walkTopDown()?.filter { it.isFile && it.name != JOB_FILE }?.sumOf { it.length() } ?: 0L

    fun hasPartial(regionId: String): Boolean = partialBytes(regionId) > 0

    // ---- download jobs ----

    fun writeJob(job: DownloadJob) {
        val dir = stagingDir(job.region.id).apply { mkdirs() }
        // Starting a different version discards stale partial data from an older one.
        dir.listFiles()?.filter { it.isDirectory && it.name != job.region.version }?.forEach { it.deleteRecursively() }
        atomicWrite(File(dir, JOB_FILE), json.encodeToString(DownloadJob.serializer(), job))
    }

    fun readJob(regionId: String): DownloadJob? = runCatching {
        json.decodeFromString(DownloadJob.serializer(), File(stagingDir(regionId), JOB_FILE).readText())
    }.getOrNull()

    fun discardPartial(regionId: String) {
        stagingDir(regionId).deleteRecursively()
    }

    // ---- install / delete ----

    @Synchronized
    fun install(region: RegionManifest): InstallResult {
        if (ManifestValidator.validateRegion(region) is Validation.Invalid) return InstallResult.Rejected("Invalid manifest")
        val staged = stagingVersionDir(region.id, region.version)
        for (c in region.components) {
            val f = File(staged, c.file)
            val check = Integrity.verify(f, c.bytes, c.sha256, c.type)
            if (check is Integrity.FileCheck.Failed) return InstallResult.Rejected("${c.file}: ${check.reason}")
        }
        val target = packDir(region.id, region.version)
        try {
            target.parentFile?.mkdirs()
            if (target.exists()) target.deleteRecursively()
            if (!staged.renameTo(target)) {
                staged.copyRecursively(target, overwrite = true)
                staged.deleteRecursively()
            }
        } catch (e: IOException) {
            target.deleteRecursively()
            return InstallResult.Rejected("Could not move files: ${e.message}")
        }
        val pack = InstalledPack(
            regionId = region.id,
            name = region.name,
            version = region.version,
            bbox = region.bbox,
            dataDate = region.dataDate,
            attribution = region.attribution,
            license = region.license,
            components = region.components.map { InstalledComponent(it.type, it.file, it.bytes, it.sha256, it.engine, it.engineVersion) },
            installedAtEpochMs = clock(),
        )
        val others = _installed.value.filterNot { it.regionId == region.id }
        writeIndex(InstalledIndex(packs = others + pack))
        // Only now remove older versions and leftovers.
        File(packsDir, region.id).listFiles()?.filter { it.name != region.version }?.forEach { it.deleteRecursively() }
        stagingDir(region.id).deleteRecursively()
        return InstallResult.Installed(pack)
    }

    @Synchronized
    fun delete(regionId: String) {
        writeIndex(InstalledIndex(packs = _installed.value.filterNot { it.regionId == regionId }))
        File(packsDir, regionId).deleteRecursively()
    }

    fun installedBytes(): Long = packsDir.takeIf { it.exists() }?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    fun stagingBytes(): Long = stagingRoot.takeIf { it.exists() }?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    fun discardAllPartial() = stagingRoot.deleteRecursively()

    private fun readIndex(): InstalledIndex = runCatching {
        json.decodeFromString(InstalledIndex.serializer(), indexFile.readText())
    }.getOrDefault(InstalledIndex()).let { index ->
        // Drop entries whose files have gone missing so a broken pack is never shown as installed.
        index.copy(packs = index.packs.filter { p -> p.components.all { fileOf(p, it.file).isFile } })
    }

    private fun writeIndex(index: InstalledIndex) {
        root.mkdirs()
        atomicWrite(indexFile, json.encodeToString(InstalledIndex.serializer(), index))
        _installed.value = index.packs
    }

    private fun atomicWrite(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw IOException("Could not write ${target.name}")
        }
    }

    companion object {
        const val JOB_FILE = "job.json"
    }
}

@kotlinx.serialization.Serializable
data class DownloadJob(val catalogUrl: String, val region: RegionManifest)
