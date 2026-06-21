package com.matugen.intellij.scheme

import com.intellij.ui.svg.SvgAttributePatcher
import com.intellij.util.SVGLoader
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Recolors monochrome platform SVG icons to the matugen palette.
 *
 * IntelliJ resolves icon colors from the active theme's load-time `icons.ColorPalette`
 * snapshot, which is NOT reachable through `UIManager` — the route [SchemeApplier] uses
 * for every other color. The only runtime hook is a global SVG color patcher: the
 * platform consults it as each icon is rasterized (see `SvgKt.loadSvg`) and lets it
 * rewrite the icon's color attributes.
 *
 * Two layers of mapping:
 *
 *  - [globalMap] maps a source hex (lowercase, as it appears in the stock icons — e.g.
 *    `#ced0d6` dark / `#6c707e` light stroke, `#3574f0` accent) to its matugen
 *    replacement, and applies to EVERY icon. Use it for the neutral/accent glyph colors
 *    shared across the toolbar, tool windows, gutter, etc.
 *
 *  - [pathOverrides] maps a path substring (case-insensitive, e.g. `checkBox`,
 *    `nodes/folder`) to a source→replacement map that applies ONLY to icons whose
 *    resource path contains that substring, layered ON TOP of [globalMap]. This is what
 *    lets folders and checkboxes be themed distinctly from generic icons that happen to
 *    share the same source hex (the plain folder fill `#ebecf0`/`#43454a` is also the
 *    generic icon fill, so it can only be tinted folder-specifically via a path match).
 *
 * The platform passes the icon's resource path (e.g. `expui/nodes/folder.svg`,
 * `themes/expUI/icons/dark/checkBox.svg`) to [attributeForPath]. Colors that aren't in
 * any applicable map (semantic green/red, multi-color object icons, file-type icons) are
 * left untouched, so only the intended glyphs follow the dynamic theme.
 */
class MatugenIconColorPatcher(
    globalMap: Map<String, String>,
    pathOverrides: Map<String, Map<String, String>> = emptyMap(),
) : SVGLoader.SvgElementColorPatcherProvider {

    private val globalMap: Map<String, String> =
        globalMap.entries.associate { it.key.lowercase() to it.value }

    // Path matcher (lowercased) -> (source hex lowercased -> replacement). Order is kept
    // so that, when several patterns match one path, later entries win on conflicts.
    private val pathOverrides: Map<String, Map<String, String>> =
        pathOverrides.entries.associateTo(LinkedHashMap()) { (pattern, map) ->
            pattern.lowercase() to map.entries.associate { it.key.lowercase() to it.value }
        }

    // A patcher carries a single resolved color map. The global one is reused for every
    // icon that matches no override; combined maps are built (and cached) per matching
    // override-set so we don't rebuild on every rasterization.
    private fun patcherFor(map: Map<String, String>) = object : SvgAttributePatcher {
        override fun patchColors(attributes: MutableMap<String, String>) {
            for (entry in attributes.entries) {
                val replacement = map[entry.value.lowercase()] ?: continue
                entry.setValue(replacement)
            }
        }
    }

    private val globalPatcher = patcherFor(globalMap)
    private val combinedCache = ConcurrentHashMap<String, SvgAttributePatcher>()

    // The platform caches rasterized icons keyed by this digest, so it MUST change
    // whenever ANY mapping changes or icons would stay stale across re-applies.
    private val digestValue: LongArray = run {
        fun canon(m: Map<String, String>) =
            m.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value.lowercase()}" }
        val canonical = buildString {
            append(canon(globalMap))
            for ((pattern, map) in this@MatugenIconColorPatcher.pathOverrides) {
                append('|').append(pattern).append('>').append(canon(map))
            }
        }
        val sha = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        LongArray(2) { i ->
            var v = 0L
            for (b in 0 until 8) v = (v shl 8) or (sha[i * 8 + b].toLong() and 0xff)
            v
        }
    }

    override fun digest(): LongArray = digestValue

    override fun attributeForPath(path: String): SvgAttributePatcher? {
        if (pathOverrides.isEmpty()) return globalPatcher

        val lower = path.lowercase()
        val matched = pathOverrides.filterKeys { lower.contains(it) }
        if (matched.isEmpty()) return globalPatcher

        // Cache the combined map per matching-pattern signature.
        val signature = matched.keys.joinToString("|")
        return combinedCache.getOrPut(signature) {
            val combined = HashMap(globalMap)
            for (map in matched.values) combined.putAll(map) // override wins over global
            patcherFor(combined)
        }
    }
}
