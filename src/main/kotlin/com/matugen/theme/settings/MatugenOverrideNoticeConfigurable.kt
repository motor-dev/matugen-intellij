package com.matugen.theme.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * Read-only informational page shown next to a platform color page (Appearance /
 * Editor > Color Scheme) to warn the user that the colors configured there are
 * overridden at runtime by the Matugen Dynamic Colors plugin. Offers a shortcut to
 * the plugin's own settings page.
 *
 * The platform offers no supported way to inject a banner into a configurable we don't
 * own, so instead we register sibling nodes via `parentId` (see plugin.xml).
 */
abstract class MatugenOverrideNoticeConfigurable(
    private val scopeDescription: String,
) : Configurable {

    override fun getDisplayName() = "Overridden by Matugen"

    override fun createComponent(): JComponent {
        val settings = MatugenSettings.getInstance()
        return panel {
            row {
                label("⚠️  These colors are controlled by the Matugen Dynamic Colors plugin.")
            }
            row {
                comment(
                    "While the plugin is enabled, $scopeDescription are overwritten at runtime " +
                    "from the matugen config file (or the selected preset), so changes you make " +
                    "on the related page may have no visible effect."
                )
            }
            row {
                comment(
                    if (settings.enabled) "The plugin is currently <b>enabled</b>."
                    else "The plugin is currently <b>disabled</b>, so these colors are not being overridden."
                )
            }
            row {
                button("Open Matugen Settings…") {
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(null, MatugenSettingsConfigurable::class.java)
                }
            }
        }
    }

    override fun isModified() = false

    override fun apply() {}
}

/** Sibling node under Appearance & Behavior > Appearance. */
class MatugenAppearanceNoticeConfigurable : MatugenOverrideNoticeConfigurable(
    "the IDE theme and UI colors"
)

/** Sibling node under Editor > Color Scheme. */
class MatugenColorSchemeNoticeConfigurable : MatugenOverrideNoticeConfigurable(
    "the editor color scheme"
)
