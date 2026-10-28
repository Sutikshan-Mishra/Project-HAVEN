// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.navigation

import android.net.Uri

/** Navigation routes. Every route maps to a real screen in [HavenNavHost]. */
object Routes {
    const val HOME = "home"
    const val TOOLS = "tools"
    const val GUIDES = "guides"
    const val MAPS = "maps"

    const val SETTINGS = "settings"
    const val SETTINGS_PERMISSIONS = "settings/permissions"
    const val SETTINGS_STORAGE = "settings/storage"
    const val SETTINGS_PRIVACY = "settings/privacy"
    const val SETTINGS_ABOUT = "settings/about"
    const val SETTINGS_LICENSES = "settings/licenses"

    const val SOS = "sos"
    const val CONTACTS = "sos/contacts"
    const val PROFILE = "sos/profile"
    const val CHECK_IN = "sos/checkin"
    const val SIGNAL = "signal"

    const val COMPASS = "tools/compass"
    const val COORDINATES = "tools/coordinates"
    const val RULER = "tools/ruler"
    const val CALIPERS = "tools/calipers"
    const val PROTRACTOR = "tools/protractor"
    const val LEVEL = "tools/level"
    const val DISTANCE = "tools/distance"
    const val CONVERTER = "tools/converter"
    const val CALCULATOR = "tools/calculator"
    const val SPEED = "tools/speed"
    const val BATTERY = "tools/battery"

    const val NOTEBOOK = "notebook"
    const val NOTE = "notebook/note/{id}"
    fun note(id: Long) = "notebook/note/$id"
    const val NEW_NOTE_ID = -1L

    const val COLLECTION = "guides/collection/{collection}"
    fun collection(key: String) = "guides/collection/$key"
    const val ARTICLE = "guides/article/{collection}/{id}"
    fun article(collection: String, id: String) = "guides/article/$collection/${Uri.encode(id)}"
    const val COOKBOOK = "guides/cookbook"
    const val RECIPE = "guides/recipe/{id}"
    fun recipe(id: String) = "guides/recipe/${Uri.encode(id)}"
    const val PLANNER = "guides/planner"
    const val SAVED = "guides/saved"

    const val DOWNLOADS = "maps/downloads"
    const val REGION = "maps/region/{id}"
    fun region(id: String) = "maps/region/${Uri.encode(id)}"
    const val EMERGENCY_PLACES = "maps/places"

    val topLevel = listOf(HOME, TOOLS, GUIDES, MAPS)

    /** Which bottom tab a route belongs to, so the tab stays highlighted on detail screens. */
    fun tabFor(route: String?): String = when {
        route == null -> HOME
        route.startsWith("tools") || route == SIGNAL || route.startsWith("notebook") -> TOOLS
        route.startsWith("guides") -> GUIDES
        route.startsWith("maps") -> MAPS
        else -> HOME
    }
}
