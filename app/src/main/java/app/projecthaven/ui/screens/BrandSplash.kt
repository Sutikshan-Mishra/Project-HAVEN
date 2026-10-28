// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.projecthaven.R
import app.projecthaven.ui.theme.Haven
import app.projecthaven.ui.theme.HavenPalette

object Brand {
    const val APP_NAME = "Project HAVEN"
    const val TAGLINE = "India's very own emergency utility app"
    const val DEVELOPER_CREDIT = "Developed by Sutikshan Mishra"
    const val SPECIAL_THANKS = "Special thanks to Pratik Yadav for UI direction and project support"
    const val DEVELOPER_LINKEDIN = "https://www.linkedin.com/in/sutikshan-mishra-73a458319"
    const val THANKS_LINKEDIN = "https://www.linkedin.com/in/pratik-yadav-b87513368"
}

/** Branded start screen. Always black, per the brand, in both themes. */
@Composable
fun BrandSplash() {
    Box(
        Modifier
            .fillMaxSize()
            .background(HavenPalette.Black),
    ) {
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_haven_mark),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(Brand.APP_NAME, style = Haven.type.largeTitle, color = HavenPalette.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(Brand.TAGLINE, style = Haven.type.callout, color = HavenPalette.Blue, textAlign = TextAlign.Center)
        }
        Text(
            Brand.DEVELOPER_CREDIT,
            style = Haven.type.footnote,
            color = HavenPalette.Gray,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
        )
    }
}
