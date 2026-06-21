package com.matugen.intellij.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import com.matugen.intellij.services.FileWatcherService
import javax.swing.JComponent

class MatugenSettingsConfigurable : Configurable {

    private lateinit var configPathField: TextFieldWithBrowseButton
    private lateinit var enabledCheckBox: JBCheckBox
    private lateinit var editorOnlyCheckBox: JBCheckBox

    override fun getDisplayName() = "Matugen Dynamic Colors"

    override fun createComponent(): JComponent {
        configPathField = TextFieldWithBrowseButton()
        configPathField.addBrowseFolderListener(
            "Select Matugen Config File",
            "Choose the JSON file that matugen writes its color output to",
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor("json")
        )

        enabledCheckBox = JBCheckBox("Enable dynamic color scheme")
        editorOnlyCheckBox = JBCheckBox("Apply to editor colors only (skip UI theme)")

        return panel {
            group("Config File") {
                row("Matugen output file:") {
                    cell(configPathField).resizableColumn()
                }
                row {
                    comment(
                        "Default: <code>~/.config/JetBrains/colors.json</code>. " +
                        "Matugen must be configured to write a JSON template to this path."
                    )
                }
            }
            group("Behavior") {
                row { cell(enabledCheckBox) }
                row { cell(editorOnlyCheckBox) }
            }
        }
    }

    override fun isModified(): Boolean {
        val settings = MatugenSettings.getInstance()
        return configPathField.text != settings.configFilePath ||
            enabledCheckBox.isSelected != settings.enabled ||
            editorOnlyCheckBox.isSelected != settings.applyToEditorOnly
    }

    override fun apply() {
        val settings = MatugenSettings.getInstance()
        settings.configFilePath = configPathField.text
        settings.enabled = enabledCheckBox.isSelected
        settings.applyToEditorOnly = editorOnlyCheckBox.isSelected
        FileWatcherService.getInstance().restart()
    }

    override fun reset() {
        val settings = MatugenSettings.getInstance()
        configPathField.text = settings.configFilePath
        enabledCheckBox.isSelected = settings.enabled
        editorOnlyCheckBox.isSelected = settings.applyToEditorOnly
    }
}
