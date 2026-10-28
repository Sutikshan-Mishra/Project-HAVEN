// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.screens.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.ChangeHistory
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SquareFoot
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import app.projecthaven.ui.components.GroupDivider
import app.projecthaven.ui.components.HavenScreen
import app.projecthaven.ui.components.ListGroup
import app.projecthaven.ui.components.ListRow
import app.projecthaven.ui.components.SectionLabel
import app.projecthaven.ui.navigation.Routes

@Composable
fun ToolsScreen(go: (String) -> Unit) {
    HavenScreen("Tools") {
        ListGroup {
            ListRow("Signal & Light", subtitle = "Flashlight, Morse, siren", icon = Icons.Rounded.FlashOn, chevron = true, onClick = { go(Routes.SIGNAL) })
            GroupDivider()
            ListRow("Field Notebook", subtitle = "Offline notes", icon = Icons.Rounded.Edit, chevron = true, onClick = { go(Routes.NOTEBOOK) })
        }
        SectionLabel("Navigation")
        ListGroup {
            ListRow("Compass", icon = Icons.Rounded.Explore, chevron = true, onClick = { go(Routes.COMPASS) })
            GroupDivider()
            ListRow("GPS coordinates", icon = Icons.Rounded.GpsFixed, chevron = true, onClick = { go(Routes.COORDINATES) })
            GroupDivider()
            ListRow("Distance between points", icon = Icons.Rounded.Place, chevron = true, onClick = { go(Routes.DISTANCE) })
            GroupDivider()
            ListRow("Speed and altitude", icon = Icons.Rounded.Speed, chevron = true, onClick = { go(Routes.SPEED) })
        }
        SectionLabel("Measure")
        ListGroup {
            ListRow("Ruler", icon = Icons.Rounded.Straighten, chevron = true, onClick = { go(Routes.RULER) })
            GroupDivider()
            ListRow("Calipers", icon = Icons.Rounded.SquareFoot, chevron = true, onClick = { go(Routes.CALIPERS) })
            GroupDivider()
            ListRow("Protractor", icon = Icons.Rounded.ChangeHistory, chevron = true, onClick = { go(Routes.PROTRACTOR) })
            GroupDivider()
            ListRow("Spirit level", icon = Icons.Rounded.Tune, chevron = true, onClick = { go(Routes.LEVEL) })
        }
        SectionLabel("Utilities")
        ListGroup {
            ListRow("Unit converter", icon = Icons.Rounded.SwapHoriz, chevron = true, onClick = { go(Routes.CONVERTER) })
            GroupDivider()
            ListRow("Calculator", icon = Icons.Rounded.Calculate, chevron = true, onClick = { go(Routes.CALCULATOR) })
            GroupDivider()
            ListRow("Battery", icon = Icons.Rounded.BatteryFull, chevron = true, onClick = { go(Routes.BATTERY) })
        }
    }
}
