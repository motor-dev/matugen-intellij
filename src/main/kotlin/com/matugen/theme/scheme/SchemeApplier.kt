package com.matugen.theme.scheme

import com.intellij.ide.ui.LafManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.IconLoader
import com.intellij.util.SVGLoader
import java.awt.Color
import java.awt.Font
import java.awt.Window
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.plaf.ColorUIResource

private val LOG = logger<SchemeApplier>()

object SchemeApplier {

    fun apply(config: ColorSchemeConfig) {
        ApplicationManager.getApplication().invokeLater {
            applyEditorScheme(config)
            applyUiColors(config.uiColors)
            applyIconColors(config.iconColors, config.iconColorOverrides)
            LOG.info("Matugen color scheme applied")
        }
    }

    /**
     * Recolor monochrome platform icons. Icon colors come from a theme load-time
     * palette snapshot that UIManager can't reach, so the only runtime hook is the
     * global SVG color patcher (see [MatugenIconColorPatcher]). Installing a patcher
     * with a fresh digest + clearing the icon cache forces re-rasterization.
     */
    @Suppress("DEPRECATION", "UnstableApiUsage")
    private fun applyIconColors(
        iconColors: Map<String, String>,
        iconColorOverrides: Map<String, Map<String, String>>,
    ) {
        if (iconColors.isEmpty() && iconColorOverrides.isEmpty()) return
        // SVGLoader.colorPatcherProvider is the global icon color patcher hook. It is
        // marked deprecated/internal, but it remains the only runtime entry point for
        // recoloring monochrome icons (the icon palette is otherwise a load-time snapshot).
        SVGLoader.colorPatcherProvider = MatugenIconColorPatcher(iconColors, iconColorOverrides)
        IconLoader.clearCache()
        for (window in Window.getWindows()) {
            window.repaint()
        }
    }

    private fun applyEditorScheme(config: ColorSchemeConfig) {
        val manager = EditorColorsManager.getInstance()
        val scheme = manager.globalScheme.clone() as EditorColorsScheme
        for ((name, color) in config.editorColors) {
            scheme.setColor(ColorKey.createColorKey(name), color)
        }
        for ((name, attr) in config.textAttributes) {
            scheme.setAttributes(TextAttributesKey.createTextAttributesKey(name), attr.toTextAttributes())
        }
        manager.setGlobalScheme(scheme)
    }

    private fun applyUiColors(uiColors: Map<String, Color>) {
        if (uiColors.isEmpty()) return

        // Install our overrides into BOTH the UIManager override table and the active
        // LaF's own defaults map. IntelliJ components painted by custom UI delegates
        // (DarculaButtonUI, DarculaCheckBoxUI, ...) resolve their colors through
        // JBColor.namedColor(key) -> UIManager.getColor(key), so the value that wins
        // is whatever is in those maps at paint time.
        //
        // CRUCIAL ORDERING: LafManager.updateUI() rebuilds the LaF defaults map from
        // the active theme, which clobbers any button/checkbox keys the theme defines.
        // So we let it rebuild FIRST, then write our overrides into the *freshly built*
        // map (re-fetched after updateUI), and only then force a repaint. Writing our
        // overrides last is what makes them win for theme-defined keys like Button.*.
        fun install() {
            val lafDefaults = UIManager.getLookAndFeel()?.defaults
            for ((key, color) in uiColors) {
                val r = color.toUIResource()
                UIManager.put(key, r)
                lafDefaults?.put(key, r)
            }
        }

        // 1) Seed overrides so updateUI() flushes JBColor caches against our values.
        install()
        // 2) Let IntelliJ rebuild defaults + flush caches (also repaints visible tree).
        LafManager.getInstance().updateUI()
        // 3) Re-install into the freshly rebuilt defaults map so theme-defined keys
        //    (Button.*, CheckBox.*, ...) end up holding OUR colors, not the theme's.
        install()
        // 4) Repaint every window (incl. non-visible cached popups/dialogs) so all
        //    components re-read the now-final values.
        for (window in Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window)
        }
    }
}

private fun Color.toUIResource(): ColorUIResource = ColorUIResource(this)

private fun TextAttrConfig.toTextAttributes(): TextAttributes {
    val fontType = when {
        bold && italic -> Font.BOLD or Font.ITALIC
        bold           -> Font.BOLD
        italic         -> Font.ITALIC
        else           -> Font.PLAIN
    }

    // `wave` is the legacy shortcut for an error/typo wavy underline; the generic
    // effectType/effectColor pair supersedes it but `wave` still works on its own.
    val effColor: Color?
    val effType: EffectType?
    if (effectColor != null || effectType != null) {
        effColor = effectColor ?: fg
        effType = effectType.toEffectType()
    } else if (wave != null) {
        effColor = wave
        effType = EffectType.WAVE_UNDERSCORE
    } else {
        effColor = null
        effType = null
    }

    return TextAttributes(fg, bg, effColor, effType, fontType).also {
        if (errorStripe != null) it.errorStripeColor = errorStripe
    }
}

private fun String?.toEffectType(): EffectType = when (this?.lowercase()) {
    "underline", "line_underscore"            -> EffectType.LINE_UNDERSCORE
    "bold_underline", "bold_line_underscore"  -> EffectType.BOLD_LINE_UNDERSCORE
    "dotted", "bold_dotted_line"              -> EffectType.BOLD_DOTTED_LINE
    "strikeout"                               -> EffectType.STRIKEOUT
    "boxed", "box"                            -> EffectType.BOXED
    "rounded_box", "roundedbox"               -> EffectType.ROUNDED_BOX
    "search", "search_match"                  -> EffectType.SEARCH_MATCH
    else                                      -> EffectType.WAVE_UNDERSCORE
}
