// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.data.maps

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.projecthaven.HavenApp
import app.projecthaven.domain.maps.Integrity
import app.projecthaven.domain.maps.ManifestValidator
import app.projecthaven.domain.maps.PackComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads one region pack with HTTP range requests, so an interrupted download (stopped by
 * the user, the system or a lost connection) resumes from where it stopped. Each file is
 * verified by size, format and SHA-256 before the pack is installed.
 */
class MapDownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val store: MapPackStore get() = (applicationContext as HavenApp).container.mapStore

    override suspend fun doWork(): Result {
        val regionId = inputData.getString(KEY_REGION) ?: return Result.failure(reason("Missing region"))
        val job = store.readJob(regionId) ?: return Result.failure(reason("Download details are missing. Start it again."))
        val region = job.region
        val total = region.totalBytes
        val dir = store.stagingVersionDir(region.id, region.version).apply { mkdirs() }

        if (dir.usableSpace < total - store.partialBytes(region.id) + SAFETY_MARGIN_BYTES) {
            return Result.failure(reason("Not enough free storage"))
        }

        var completedBytes = 0L
        try {
            for (component in region.components) {
                val dest = File(dir, component.file)
                if (dest.isFile && dest.length() == component.bytes) {
                    completedBytes += component.bytes
                    continue
                }
                val url = ManifestValidator.resolveUrl(job.catalogUrl, component.url)
                    ?: return Result.failure(reason("Invalid download address"))
                download(url, component, dest, completedBytes, total)
                when (val check = Integrity.verify(dest, component.bytes, component.sha256, component.type)) {
                    Integrity.FileCheck.Ok -> Unit
                    is Integrity.FileCheck.Failed -> {
                        dest.delete()
                        return Result.failure(reason("Verification failed: ${check.reason}"))
                    }
                }
                completedBytes += component.bytes
            }
        } catch (e: IOException) {
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure(reason("Download failed: network error"))
        }

        setProgress(workDataOf(KEY_PHASE to PHASE_VERIFY, KEY_DONE to total, KEY_TOTAL to total))
        return when (val r = withContext(Dispatchers.IO) { store.install(region) }) {
            is InstallResult.Installed -> Result.success()
            is InstallResult.Rejected -> Result.failure(reason(r.reason))
        }
    }

    private suspend fun download(url: String, component: PackComponent, dest: File, before: Long, total: Long) = withContext(Dispatchers.IO) {
        val part = File(dest.parentFile, dest.name + ".part")
        var existing = if (part.isFile) part.length() else 0L
        if (existing > component.bytes) { part.delete(); existing = 0L }

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            val code = conn.responseCode
            val append = when {
                code == HttpURLConnection.HTTP_PARTIAL && existing > 0 -> true
                code == HttpURLConnection.HTTP_OK -> { existing = 0L; false } // server ignored the range
                code == 416 && existing == component.bytes -> { part.renameTo(dest); return@withContext }
                else -> throw IOException("HTTP $code")
            }
            var written = existing
            var lastReport = 0L
            conn.inputStream.use { input ->
                FileOutputStream(part, append).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        written += n
                        if (written > component.bytes) throw IOException("More data than expected")
                        out.write(buf, 0, n)
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 500) {
                            lastReport = now
                            setProgress(workDataOf(KEY_PHASE to PHASE_DOWNLOAD, KEY_DONE to before + written, KEY_TOTAL to total))
                        }
                    }
                    out.fd.sync()
                }
            }
            if (written != component.bytes) throw IOException("Incomplete download")
            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) throw IOException("Could not finalise ${dest.name}")
        } finally {
            conn.disconnect()
        }
    }

    private fun reason(text: String) = workDataOf(KEY_REASON to text)

    companion object {
        const val KEY_REGION = "region"
        const val KEY_PHASE = "phase"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_REASON = "reason"
        const val PHASE_DOWNLOAD = "download"
        const val PHASE_VERIFY = "verify"
        const val TAG = "map-download"
        const val MAX_ATTEMPTS = 8
        const val SAFETY_MARGIN_BYTES = 100L * 1024 * 1024

        fun uniqueName(regionId: String) = "map-download-$regionId"
        fun regionTag(regionId: String) = "region:$regionId"
    }
}
