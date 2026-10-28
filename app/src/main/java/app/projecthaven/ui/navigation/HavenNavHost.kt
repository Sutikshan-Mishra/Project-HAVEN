// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.projecthaven.ui.screens.guides.ArticleScreen
import app.projecthaven.ui.screens.guides.CollectionScreen
import app.projecthaven.ui.screens.guides.CookbookScreen
import app.projecthaven.ui.screens.guides.GuidesScreen
import app.projecthaven.ui.screens.guides.PlannerScreen
import app.projecthaven.ui.screens.guides.RecipeScreen
import app.projecthaven.ui.screens.guides.SavedScreen
import app.projecthaven.ui.screens.home.HomeScreen
import app.projecthaven.ui.screens.maps.DownloadsScreen
import app.projecthaven.ui.screens.maps.EmergencyPlacesScreen
import app.projecthaven.ui.screens.maps.MapsScreen
import app.projecthaven.ui.screens.maps.RegionScreen
import app.projecthaven.ui.screens.notebook.NoteEditorScreen
import app.projecthaven.ui.screens.notebook.NotebookScreen
import app.projecthaven.ui.screens.settings.AboutScreen
import app.projecthaven.ui.screens.settings.LicensesScreen
import app.projecthaven.ui.screens.settings.PermissionsScreen
import app.projecthaven.ui.screens.settings.PrivacyScreen
import app.projecthaven.ui.screens.settings.SettingsScreen
import app.projecthaven.ui.screens.settings.StorageScreen
import app.projecthaven.ui.screens.signal.SignalScreen
import app.projecthaven.ui.screens.sos.CheckInScreen
import app.projecthaven.ui.screens.sos.ContactsScreen
import app.projecthaven.ui.screens.sos.ProfileScreen
import app.projecthaven.ui.screens.sos.SosScreen
import app.projecthaven.ui.screens.tools.BatteryScreen
import app.projecthaven.ui.screens.tools.CalculatorScreen
import app.projecthaven.ui.screens.tools.CalipersScreen
import app.projecthaven.ui.screens.tools.CompassScreen
import app.projecthaven.ui.screens.tools.ConverterScreen
import app.projecthaven.ui.screens.tools.CoordinatesScreen
import app.projecthaven.ui.screens.tools.DistanceScreen
import app.projecthaven.ui.screens.tools.LevelScreen
import app.projecthaven.ui.screens.tools.ProtractorScreen
import app.projecthaven.ui.screens.tools.RulerScreen
import app.projecthaven.ui.screens.tools.SpeedScreen
import app.projecthaven.ui.screens.tools.ToolsScreen

@Composable
fun HavenNavHost(nav: NavHostController, modifier: Modifier = Modifier) {
    val back: () -> Unit = { nav.popBackStack() }
    val go: (String) -> Unit = { nav.navigate(it) { launchSingleTop = true } }

    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        modifier = modifier,
        enterTransition = { fadeIn(tween(160)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(160)) },
        popExitTransition = { fadeOut(tween(120)) },
    ) {
        composable(Routes.HOME) { HomeScreen(go) }
        composable(Routes.TOOLS) { ToolsScreen(go) }
        composable(Routes.GUIDES) { GuidesScreen(go) }
        composable(Routes.MAPS) { MapsScreen(go) }

        composable(Routes.SETTINGS) { SettingsScreen(back, go) }
        composable(Routes.SETTINGS_PERMISSIONS) { PermissionsScreen(back) }
        composable(Routes.SETTINGS_STORAGE) { StorageScreen(back) }
        composable(Routes.SETTINGS_PRIVACY) { PrivacyScreen(back) }
        composable(Routes.SETTINGS_ABOUT) { AboutScreen(back, go) }
        composable(Routes.SETTINGS_LICENSES) { LicensesScreen(back) }

        composable(Routes.SOS) { SosScreen(back, go) }
        composable(Routes.CONTACTS) { ContactsScreen(back) }
        composable(Routes.PROFILE) { ProfileScreen(back) }
        composable(Routes.CHECK_IN) { CheckInScreen(back, go) }
        composable(Routes.SIGNAL) { SignalScreen(back) }

        composable(Routes.COMPASS) { CompassScreen(back) }
        composable(Routes.COORDINATES) { CoordinatesScreen(back) }
        composable(Routes.RULER) { RulerScreen(back) }
        composable(Routes.CALIPERS) { CalipersScreen(back) }
        composable(Routes.PROTRACTOR) { ProtractorScreen(back) }
        composable(Routes.LEVEL) { LevelScreen(back) }
        composable(Routes.DISTANCE) { DistanceScreen(back) }
        composable(Routes.CONVERTER) { ConverterScreen(back) }
        composable(Routes.CALCULATOR) { CalculatorScreen(back) }
        composable(Routes.SPEED) { SpeedScreen(back) }
        composable(Routes.BATTERY) { BatteryScreen(back) }

        composable(Routes.NOTEBOOK) { NotebookScreen(back, go) }
        composable(Routes.NOTE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            NoteEditorScreen(entry.arguments?.getLong("id") ?: Routes.NEW_NOTE_ID, back)
        }

        composable(Routes.COLLECTION, arguments = listOf(navArgument("collection") { type = NavType.StringType })) { entry ->
            CollectionScreen(entry.arguments?.getString("collection").orEmpty(), back, go)
        }
        composable(
            Routes.ARTICLE,
            arguments = listOf(
                navArgument("collection") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType },
            ),
        ) { entry ->
            ArticleScreen(entry.arguments?.getString("collection").orEmpty(), entry.arguments?.getString("id").orEmpty(), back)
        }
        composable(Routes.COOKBOOK) { CookbookScreen(back, go) }
        composable(Routes.RECIPE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            RecipeScreen(entry.arguments?.getString("id").orEmpty(), back)
        }
        composable(Routes.PLANNER) { PlannerScreen(back) }
        composable(Routes.SAVED) { SavedScreen(back, go) }

        composable(Routes.DOWNLOADS) { DownloadsScreen(back, go) }
        composable(Routes.REGION, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            RegionScreen(entry.arguments?.getString("id").orEmpty(), back)
        }
        composable(Routes.EMERGENCY_PLACES) { EmergencyPlacesScreen(back) }
    }
}
