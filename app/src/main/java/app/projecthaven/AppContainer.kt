// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import app.projecthaven.data.checkin.CheckInScheduler
import app.projecthaven.data.contacts.ContactsRepository
import app.projecthaven.data.db.HavenDatabase
import app.projecthaven.data.device.BatteryMonitor
import app.projecthaven.data.device.DeviceSensors
import app.projecthaven.data.device.LocationTracker
import app.projecthaven.data.device.TonePlayer
import app.projecthaven.data.device.TorchController
import app.projecthaven.data.guides.GuideRepository
import app.projecthaven.data.maps.BundledMaps
import app.projecthaven.data.maps.CatalogRepository
import app.projecthaven.data.maps.MapImporter
import app.projecthaven.data.maps.MapPackStore
import app.projecthaven.data.maps.MapRepository
import app.projecthaven.data.maps.PlacesRepository
import app.projecthaven.data.notes.NotesRepository
import app.projecthaven.data.profile.ProfileRepository
import app.projecthaven.data.settings.SettingsRepository
import app.projecthaven.data.signal.SignalController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val Context.profileStore: DataStore<Preferences> by preferencesDataStore(name = "emergency_profile")

/**
 * Manual dependency wiring. The app is small enough that a DI framework would add more
 * ceremony than value; everything is created lazily on first use.
 */
class AppContainer(private val app: Application) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings by lazy { SettingsRepository(app.settingsStore) }
    val profile by lazy { ProfileRepository(app.profileStore) }

    private val database by lazy {
        Room.databaseBuilder(app, HavenDatabase::class.java, HavenDatabase.NAME).build()
    }
    val notes by lazy { NotesRepository(database.notes()) }
    val contacts by lazy { ContactsRepository(database.contacts()) }

    val guides by lazy {
        GuideRepository { name -> app.assets.open(name).bufferedReader().use { it.readText() } }
    }

    val location by lazy { LocationTracker(app) }
    val battery by lazy { BatteryMonitor(app) }
    val sensors by lazy { DeviceSensors(app) }
    private val torch by lazy { TorchController(app) }
    private val tones by lazy { TonePlayer(app) }
    val signal by lazy { SignalController(torch, tones) }
    val checkIn by lazy { CheckInScheduler(app, settings) }

    val mapsRoot: File get() = File(app.filesDir, "maps")
    val mapStore by lazy { MapPackStore(mapsRoot) }
    val catalog by lazy { CatalogRepository(File(mapsRoot, "catalog-cache.json")) }
    val maps by lazy { MapRepository(app, mapStore, catalog) }
    val mapImporter by lazy { MapImporter(app, mapStore) }
    val bundledMaps by lazy {
        BundledMaps(
            listAssets = { runCatching { app.assets.list("maps")?.toList() }.getOrNull().orEmpty() },
            readAsset = { path -> runCatching { app.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull() },
        )
    }
    val places by lazy {
        PlacesRepository { pack, file ->
            if (pack.bundled) app.assets.open("maps/$file") else mapStore.fileOf(pack, file).inputStream()
        }
    }

    /** Label glyphs are optional; without them maps render without text labels. */
    val glyphsUrl: String? by lazy {
        val present = runCatching { app.assets.list("glyphs")?.isNotEmpty() == true }.getOrDefault(false)
        if (present) "asset://glyphs/{fontstack}/{range}.pbf" else null
    }

    val databaseFile: File get() = app.getDatabasePath(HavenDatabase.NAME)
    val cacheDir: File get() = app.cacheDir
}
