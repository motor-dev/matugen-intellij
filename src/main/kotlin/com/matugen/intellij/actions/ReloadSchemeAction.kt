package com.matugen.intellij.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.matugen.intellij.services.FileWatcherService

class ReloadSchemeAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        FileWatcherService.getInstance().restart()
    }
}
