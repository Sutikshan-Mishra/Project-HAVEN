// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Critical navigation paths. These run offline: nothing here needs a network connection.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForHome() {
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("Essentials", substring = true, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun homeShowsPrimaryActions() {
        waitForHome()
        rule.onNodeWithContentDescription("SOS", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("First Aid", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Offline Maps", substring = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Signal & Light", substring = true).assertIsDisplayed()
    }

    @Test fun bottomTabsOpenRealDestinations() {
        waitForHome()
        rule.onNodeWithContentDescription("Tools").performClick()
        rule.onNodeWithText("Spirit level").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Guides").performClick()
        rule.onNodeWithText("Survival Handbook").assertIsDisplayed()
        rule.onNodeWithContentDescription("Maps").performClick()
        // A fresh install has no map packs, so Maps shows its download prompt.
        rule.onNodeWithText("No offline maps yet").assertIsDisplayed()
        rule.onNodeWithContentDescription("Home").performClick()
        waitForHome()
    }

    @Test fun firstAidArticleOpensOffline() {
        waitForHome()
        rule.onNodeWithContentDescription("First Aid", substring = true).performClick()
        rule.onNodeWithText("Bleeding and wounds", substring = true).performClick()
        rule.onNodeWithText("Severe bleeding").performClick()
        rule.onNodeWithText("Do this now".uppercase()).performScrollTo().assertIsDisplayed()
    }

    @Test fun settingsShowsDeveloperCredit() {
        waitForHome()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Developed by Sutikshan Mishra").performScrollTo().assertIsDisplayed()
    }
}
