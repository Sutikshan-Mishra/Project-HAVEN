// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.projecthaven.AppContainer
import app.projecthaven.data.signal.MorseOutput
import app.projecthaven.data.signal.SignalMode
import app.projecthaven.ui.components.FullBrightness
import app.projecthaven.ui.components.KeepScreenOn
import app.projecthaven.ui.components.LocalShowMessage
import app.projecthaven.ui.navigation.HavenNavHost
import app.projecthaven.ui.navigation.Routes
import app.projecthaven.ui.screens.BrandSplash
import app.projecthaven.ui.theme.Haven
import kotlinx.coroutines.launch

@Composable
fun HavenRoot(container: AppContainer, pendingRoute: String?, onRouteHandled: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    // Genuine start-up work: parse the bundled guide library once. The branded screen is
    // shown only while this runs; it is never held for a fixed time.
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        runCatching { container.guides.library() }
        ready = true
    }

    LaunchedEffect(pendingRoute, ready) {
        if (ready && pendingRoute != null) {
            nav.navigate(pendingRoute) { launchSingleTop = true }
            onRouteHandled()
        }
    }

    CompositionLocalProvider(LocalAppContainer provides container, LocalShowMessage provides showMessage) {
        Box(Modifier.fillMaxSize().background(Haven.colors.background)) {
            Scaffold(
                containerColor = Haven.colors.background,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = { HavenBottomBar(nav) },
                snackbarHost = {
                    SnackbarHost(snackbar) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = Haven.colors.surfaceRaised,
                            contentColor = Haven.colors.text,
                            actionColor = Haven.colors.accent,
                        )
                    }
                },
            ) { padding ->
                HavenNavHost(nav, Modifier.padding(bottom = padding.calculateBottomPadding()))
            }
            SignalOverlay()
            AnimatedVisibility(visible = !ready, enter = fadeIn(), exit = fadeOut(tween(220))) {
                BrandSplash()
            }
        }
    }
}

@Composable
private fun HavenBottomBar(nav: NavHostController) {
    val entry by nav.currentBackStackEntryAsState()
    val currentTab = Routes.tabFor(entry?.destination?.route)
    val items = listOf(
        Triple(Routes.HOME, "Home", Icons.Rounded.Home),
        Triple(Routes.TOOLS, "Tools", Icons.Rounded.Build),
        Triple(Routes.GUIDES, "Guides", Icons.AutoMirrored.Rounded.MenuBook),
        Triple(Routes.MAPS, "Maps", Icons.Rounded.Map),
    )
    Column {
        HorizontalDivider(thickness = 0.5.dp, color = Haven.colors.divider)
        NavigationBar(containerColor = Haven.colors.background, tonalElevation = 0.dp) {
            items.forEach { (route, label, icon) ->
                val selected = currentTab == route
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        if (selected && entry?.destination?.route != route) {
                            nav.popBackStack(route, inclusive = false)
                        } else {
                            nav.navigate(route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = { Icon(icon, contentDescription = null) },
                    label = { Text(label, style = Haven.type.caption) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Haven.colors.accent,
                        selectedTextColor = Haven.colors.accent,
                        unselectedIconColor = Haven.colors.textSecondary,
                        unselectedTextColor = Haven.colors.textSecondary,
                        indicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.semantics { contentDescription = label },
                )
            }
        }
    }
}

/**
 * Full-screen white light for screen-flash Morse and the steady screen light. Drawn above
 * everything so it works from any screen; one tap anywhere stops it.
 */
@Composable
private fun SignalOverlay() {
    val signal = LocalAppContainer.current.signal
    val state by signal.state.collectAsStateWithLifecycle()
    val mode = state.mode
    val screenMode = mode is SignalMode.ScreenLight || (mode is SignalMode.Morse && !mode.preview && MorseOutput.SCREEN in mode.outputs)
    if (!screenMode) return
    KeepScreenOn()
    FullBrightness()
    Box(
        Modifier
            .fillMaxSize()
            .background(if (state.lit) Color.White else Color.Black)
            .clickable(onClickLabel = "Stop signal") { signal.stop() }
            .semantics { contentDescription = "Screen signal active. Tap to stop." },
    ) {
        Text(
            "Tap to stop",
            style = Haven.type.footnote,
            color = if (state.lit) Color.Black else Color(0xFF8E8E93),
            modifier = Modifier
                .padding(bottom = 48.dp)
                .align(androidx.compose.ui.Alignment.BottomCenter),
        )
    }
}
