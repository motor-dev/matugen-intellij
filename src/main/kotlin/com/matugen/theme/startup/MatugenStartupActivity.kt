package com.matugen.theme.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.matugen.theme.services.FileWatcherService

/**
 * Bootstraps the application-level [FileWatcherService] once the IDE is fully started.
 *
 * Replaces the former `AppLifecycleListener.appStarted()` hook, which is `@ApiStatus.Internal`
 * and is rejected by the JetBrains Marketplace verifier. `ProjectActivity` (registered as a
 * `postStartupActivity`) is the public, stable equivalent: it runs after application init and
 * after the persisted color scheme has been restored from disk, so it is still safe to apply a
 * new global scheme from here. It fires once per opened project, but [FileWatcherService.start]
 * is idempotent, so only the first invocation actually starts the watcher.
 */
internal class MatugenStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        FileWatcherService.getInstance().start()
    }
}
