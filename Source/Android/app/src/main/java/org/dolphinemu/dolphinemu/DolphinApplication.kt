// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu

import android.app.Activity
import android.app.Application
import android.content.Context
import android.hardware.usb.UsbManager
import org.dolphinemu.dolphinemu.utils.ActivityTracker
import org.dolphinemu.dolphinemu.utils.DirectoryInitialization
import org.dolphinemu.dolphinemu.utils.GCAdapter
import org.dolphinemu.dolphinemu.utils.WiimoteAdapter

class DolphinApplication : Application() {
    private val activityTracker = ActivityTracker()

    override fun onCreate() {
        super.onCreate()
        instance = this
        // The short profile handoff owns no emulator state. It must not open
        // another account's files while the previous process is shutting down.
        val processName = if (android.os.Build.VERSION.SDK_INT >= 28) getProcessName()
            else getSystemService(android.app.ActivityManager::class.java)
                .runningAppProcesses?.firstOrNull { it.pid == android.os.Process.myPid() }?.processName
        if (processName == "$packageName:profile_switch") return
        registerActivityLifecycleCallbacks(activityTracker)
        System.loadLibrary("main")

        val usbManager = getSystemService(USB_SERVICE) as UsbManager
        GCAdapter.manager = usbManager
        WiimoteAdapter.manager = usbManager

        if (DirectoryInitialization.shouldStart(applicationContext)) {
            DirectoryInitialization.start(applicationContext)
        }
    }

    companion object {
        @JvmStatic
        lateinit var instance: DolphinApplication
            private set

        @JvmStatic
        fun getAppContext(): Context = instance.applicationContext

        @JvmStatic
        fun getAppActivity(): Activity? = instance.activityTracker.currentActivity
    }
}
