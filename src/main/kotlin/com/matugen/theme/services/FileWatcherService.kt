package com.matugen.theme.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.matugen.theme.scheme.ColorSchemeConfig
import com.matugen.theme.scheme.SchemeApplier
import com.matugen.theme.settings.MatugenSettings
import java.io.File
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

private val LOG = logger<FileWatcherService>()

@Service(Service.Level.APP)
class FileWatcherService {

    private val watchThread = AtomicReference<Thread?>(null)

    /** Called by the settings configurable after the user saves changes. */
    fun restart() {
        stop()
        start()
    }

    fun start() {
        val settings = MatugenSettings.getInstance()
        if (!settings.enabled) return

        val configFile = File(settings.configFilePath)
        if (!configFile.isFile) {
            notify("Matugen config not found at ${configFile.absolutePath}", NotificationType.WARNING)
            return
        }

        // Apply immediately on startup
        loadAndApply(configFile)

        val thread = thread(isDaemon = true, name = "matugen-file-watcher") {
            watchLoop(configFile)
        }
        watchThread.set(thread)
    }

    fun stop() {
        watchThread.getAndSet(null)?.interrupt()
    }

    // -------------------------------------------------------------------------

    private fun watchLoop(configFile: File) {
        val dir = configFile.parentFile?.toPath() ?: return
        try {
            FileSystems.getDefault().newWatchService().use { watcher ->
                dir.register(watcher, ENTRY_CREATE, ENTRY_MODIFY)
                LOG.info("Watching ${configFile.absolutePath} for changes")

                while (!Thread.currentThread().isInterrupted) {
                    val key = watcher.take()   // blocks until an event arrives
                    for (event in key.pollEvents()) {
                        val changed = dir.resolve(event.context() as Path).toFile()
                        if (changed.canonicalPath == configFile.canonicalPath) {
                            loadAndApply(configFile)
                        }
                    }
                    if (!key.reset()) break
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            LOG.warn("File watcher stopped unexpectedly", e)
            notify("Matugen watcher error: ${e.message}", NotificationType.ERROR)
        }
    }

    private fun loadAndApply(file: File) {
        try {
            val config = ColorSchemeConfig.parse(file)
            SchemeApplier.apply(config)
        } catch (e: Exception) {
            LOG.warn("Failed to parse or apply matugen colors", e)
            notify("Matugen parse error: ${e.message}", NotificationType.ERROR)
        }
    }

    private fun notify(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Matugen")
            .createNotification(message, type)
            .notify(null)
    }

    // Startup is triggered by MatugenStartupListener via AppLifecycleListener.appStarted(),
    // which fires after the IDE has restored the persisted color scheme from disk.

    companion object {
        fun getInstance(): FileWatcherService =
            ApplicationManager.getApplication().getService(FileWatcherService::class.java)
    }
}
