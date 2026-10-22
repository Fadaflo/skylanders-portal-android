// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.skylanders.console

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme

/** Local visual QA without creating accounts or sending real emails. Not in release APKs. */
class ConsoleConfirmationPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("test_profile_handoff", false)) {
            startActivity(Intent(this, ConsoleProfileSwitchActivity::class.java))
            return
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(), typography = ConsoleTypography) {
                RegistrationConfirmation("skylander@example.com") { finish() }
            }
        }
    }
}
