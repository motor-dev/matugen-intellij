package com.matugen.theme.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import com.matugen.theme.scheme.SchemeApplier
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
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor("json")
                .withTitle("Select Matugen Config File")
                .withDescription("Choose the JSON file that matugen writes its color output to")
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
            group("Matugen Templates") {
                row {
                    button("Export Matugen Templates…") { exportTemplates() }
                }
                row {
                    comment(
                        "Writes the bundled matugen templates to disk and shows the " +
                        "config snippet to add to your matugen config.toml. " +
                        "Use this for fully dynamic colors driven by matugen."
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
        // restart() re-applies the scheme (which reverts UI overrides itself in editor-only
        // mode), but when the plugin is disabled nothing re-applies, so revert explicitly.
        if (!settings.enabled) {
            SchemeApplier.revertUiColors()
        }
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

    private val templateFiles = listOf(
        "colors.json.tera",
        "colors.dark.json.tera",
        "colors.light.json.tera",
    )

    private fun exportTemplates() {
        // Default to the matugen dir under the IDE config path (sibling of colors.json).
        val defaultDir = File(MatugenSettings.defaultConfigPath()).parentFile.resolve("templates")

        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Select Export Directory")
            .withDescription("The matugen templates will be written here")
        val chosen = com.intellij.openapi.fileChooser.FileChooser.chooseFile(
            descriptor, null, com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .refreshAndFindFileByIoFile(defaultDir.apply { mkdirs() })
        ) ?: return
        val dir = File(chosen.path)

        val written = templateFiles.mapNotNull { name ->
            val content = javaClass.getResourceAsStream("/templates/$name")
                ?.bufferedReader()?.readText() ?: return@mapNotNull null
            dir.resolve(name).writeText(content)
            name
        }
        if (written.isEmpty()) {
            Messages.showErrorDialog("No bundled templates were found in the plugin.", "Export Failed")
            return
        }

        val output = configPathField.text.trim().ifEmpty { MatugenSettings.defaultConfigPath() }
        val input = dir.resolve("colors.json.tera").absolutePath
        val snippet = """
            [templates.intellij]
            input_path  = "$input"
            output_path = "$output"
        """.trimIndent()

        com.intellij.openapi.ide.CopyPasteManager.getInstance()
            .setContents(java.awt.datatransfer.StringSelection(snippet))

        Messages.showInfoMessage(
            "Exported ${written.size} template(s) to:\n$dir\n\n" +
            "Add this to your matugen config.toml (copied to clipboard):\n\n$snippet\n\n" +
            "Then run matugen to generate the colors file the plugin watches.",
            "Templates Exported"
        )
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
