// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven

import android.app.Application
import app.projecthaven.data.checkin.CheckInScheduler
import kotlinx.coroutines.launch

class HavenApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CheckInScheduler.ensureChannel(this)
        // Re-arm an active check-in if the process was killed. No network, no blocking work.
        container.appScope.launch { runCatching { container.checkIn.restore() } }
    }
}
