// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.console

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.dolphinemu.dolphinemu.R

internal val PortalFont = FontFamily(
    Font(R.font.portal_rounded_regular, FontWeight.Normal),
    Font(R.font.portal_rounded_bold, FontWeight.Bold),
    Font(R.font.portal_rounded_black, FontWeight.Black)
)

private fun TextStyle.rounded() = copy(fontFamily = PortalFont)

internal val ConsoleTypography = Typography().let {
    Typography(
        displayLarge = it.displayLarge.rounded(), displayMedium = it.displayMedium.rounded(),
        displaySmall = it.displaySmall.rounded(), headlineLarge = it.headlineLarge.rounded(),
        headlineMedium = it.headlineMedium.rounded(), headlineSmall = it.headlineSmall.rounded(),
        titleLarge = it.titleLarge.rounded(), titleMedium = it.titleMedium.rounded(),
        titleSmall = it.titleSmall.rounded(), bodyLarge = it.bodyLarge.rounded(),
        bodyMedium = it.bodyMedium.rounded(), bodySmall = it.bodySmall.rounded(),
        labelLarge = it.labelLarge.rounded(), labelMedium = it.labelMedium.rounded(),
        labelSmall = it.labelSmall.rounded()
    )
}
