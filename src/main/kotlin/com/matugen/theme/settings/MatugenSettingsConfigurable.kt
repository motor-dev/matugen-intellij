package com.matugen.theme.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import com.matugen.theme.services.FileWatcherService
import java.io.File
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent

class MatugenSettingsConfigurable : Configurable {

    private lateinit var configPathField: TextFieldWithBrowseButton
    private lateinit var enabledCheckBox: JBCheckBox
    private lateinit var editorOnlyCheckBox: JBCheckBox
    private lateinit var presetComboBox: JComboBox<PresetEntry>

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

        val presets = loadPresets()
        presetComboBox = JComboBox(DefaultComboBoxModel(presets.toTypedArray()))

        return panel {
            group("Config File") {
                row("Matugen output file:") {
                    cell(configPathField).resizableColumn()
                }
                row {
                    comment(
                        "This file is read and watched for changes. " +
                        "Configure matugen to write its JSON output here, or use a preset below."
                    )
                }
            }
            group("Presets") {
                row("Bundled preset:") {
                    cell(presetComboBox).resizableColumn()
                    button("Copy to Config File") { applySelectedPreset() }
                }
                row {
                    comment("Copies the selected preset to the config path above and reloads colors.")
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

    // -------------------------------------------------------------------------

    private data class PresetEntry(val filename: String, val displayName: String) {
        override fun toString() = displayName
    }

    private fun loadPresets(): List<PresetEntry> {
        val stream = javaClass.getResourceAsStream("/presets/index.txt") ?: return emptyList()
        return stream.bufferedReader().readLines()
            .mapNotNull { line ->
                val eq = line.indexOf('=')
                if (eq < 0) null
                else PresetEntry(line.substring(0, eq).trim(), line.substring(eq + 1).trim())
            }
    }

    private fun applySelectedPreset() {
        val preset = presetComboBox.selectedItem as? PresetEntry ?: return
        val targetPath = configPathField.text.trim().ifEmpty { return }
        val targetFile = File(targetPath)

        if (targetFile.exists()) {
            val answer = Messages.showOkCancelDialog(
                "Overwrite '$targetPath' with the '${preset.displayName}' preset?",
                "Load Preset",
                "Overwrite",
                "Cancel",
                Messages.getQuestionIcon()
            )
            if (answer != Messages.OK) return
        }

        val content = javaClass.getResourceAsStream("/presets/${preset.filename}")
            ?.bufferedReader()?.readText() ?: return

        targetFile.parentFile?.mkdirs()
        targetFile.writeText(content)

        val settings = MatugenSettings.getInstance()
        settings.configFilePath = targetPath
        configPathField.text = targetPath
        FileWatcherService.getInstance().restart()
    }
}
