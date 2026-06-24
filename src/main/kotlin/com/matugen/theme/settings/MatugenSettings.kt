package com.matugen.theme.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import java.io.File

@State(
    name = "MatugenSettings",
    storages = [Storage("matugen.xml")]
)
class MatugenSettings : PersistentStateComponent<MatugenSettings.State> {

    data class State(
        var configFilePath: String = defaultConfigPath(),
        var enabled: Boolean = true,
        var applyToEditorOnly: Boolean = false,
    )

    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) { this.state = state }

    var configFilePath: String
        get() = state.configFilePath
        set(value) { state.configFilePath = value }

    var enabled: Boolean
        get() = state.enabled
        set(value) { state.enabled = value }

    var applyToEditorOnly: Boolean
        get() = state.applyToEditorOnly
        set(value) { state.applyToEditorOnly = value }

    companion object {
        fun getInstance(): MatugenSettings =
            ApplicationManager.getApplication().getService(MatugenSettings::class.java)

        fun defaultConfigPath(): String =
            File(PathManager.getConfigPath()).parentFile.resolve("matugen/colors.json").absolutePath
    }
}
