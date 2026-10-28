// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import app.projecthaven.data.settings.ThemeMode
import app.projecthaven.ui.HavenRoot
import app.projecthaven.ui.navigation.Routes
import app.projecthaven.ui.theme.HavenTheme
import app.projecthaven.ui.theme.isDark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val pendingRoute = MutableStateFlow<String?>(null)
    private val themeMode = MutableStateFlow<ThemeMode?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val container = (application as HavenApp).container

        // The system splash stays only until the saved theme is read (a few milliseconds),
        // so the first frame is drawn in the right theme. There is no artificial delay.
        splash.setKeepOnScreenCondition { themeMode.value == null }
        lifecycleScope.launch { container.settings.themeMode.collect { themeMode.value = it } }

        handleIntent(intent)
        enableEdgeToEdge()

        setContent {
            val mode by themeMode.collectAsState()
            val route by pendingRoute.collectAsState()
            val resolved = mode ?: ThemeMode.DARK
            val dark = resolved.isDark(isSystemInDarkTheme())
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            HavenTheme(resolved) {
                HavenRoot(
                    container = container,
                    pendingRoute = route,
                    onRouteHandled = { pendingRoute.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_CHECK_IN) pendingRoute.value = Routes.CHECK_IN
    }

    companion object {
        const val ACTION_OPEN_CHECK_IN = "app.projecthaven.action.OPEN_CHECK_IN"
    }
}
