package com.matugen.intellij.startup

import com.intellij.ide.AppLifecycleListener
import com.matugen.intellij.services.FileWatcherService

class MatugenStartupListener : AppLifecycleListener {
    // Runs after all startup activities and the persisted color scheme has been
    // restored from disk — safe to set a new global scheme here.
    override fun appStarted() {
        FileWatcherService.getInstance().start()
    }
}
