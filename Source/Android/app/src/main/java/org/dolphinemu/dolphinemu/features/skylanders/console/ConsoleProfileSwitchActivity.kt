// SPDX-License-Identifier: GPL-2.0-or-later
package org.dolphinemu.dolphinemu.features.skylanders.console

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ProgressBar

/** Keeps the task visible while Dolphin opens the newly signed-in account in a
 * clean process. Native singletons and open files must never cross accounts. */
class ConsoleProfileSwitchActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var oldPid: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (16 * resources.displayMetrics.density).toInt()
        val size = (24 * resources.displayMetrics.density).toInt()
        val background = FrameLayout(this).apply { setBackgroundColor(Color.rgb(8, 13, 28)) }
        background.addView(ProgressBar(this).apply { contentDescription = "Profil wird geladen" },
            FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.START).apply {
                setMargins(padding, padding, padding, padding)
            })
        setContentView(background)
    }

    override fun onResume() {
        super.onResume()
        // This activity runs in a separate, lightweight process. Only our main
        // process is eligible; never trust a caller-supplied process ID.
        oldPid = getSystemService(ActivityManager::class.java).runningAppProcesses
            ?.firstOrNull { it.processName == packageName && it.pid != Process.myPid() }?.pid
        oldPid?.let(Process::killProcess)
        handler.postDelayed(::openProfileWhenStopped, 150)
    }

    private fun openProfileWhenStopped() {
        if (isFinishing || isDestroyed) return
        val previousStillRunning = oldPid?.let { previous ->
            getSystemService(ActivityManager::class.java).runningAppProcesses
                ?.any { it.pid == previous && it.processName == packageName } == true
        } == true
        if (previousStillRunning) {
            handler.postDelayed(::openProfileWhenStopped, 100)
            return
        }
        startActivity(Intent(this, ConsoleActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
