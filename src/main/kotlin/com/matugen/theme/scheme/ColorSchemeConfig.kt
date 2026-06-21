package com.matugen.theme.scheme

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.awt.Color
import java.io.File

data class TextAttrConfig(
    val fg: Color? = null,
    val bg: Color? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    /**
     * Underline/box style. One of: boxed, underline, wave, strikeout,
     * bold_underline, dotted, search, rounded_box. Paired with [effectColor].
     */
    val effectType: String? = null,
    val effectColor: Color? = null,
    /** Color of the marker shown in the right-hand error stripe / scrollbar. */
    val errorStripe: Color? = null,
    /** Backward-compat shortcut: equivalent to effectType=wave + effectColor=<this>. */
    val wave: Color? = null,
)

data class ColorSchemeConfig(
    val editorColors: Map<String, Color>,
    val textAttributes: Map<String, TextAttrConfig>,
    val uiColors: Map<String, Color>,
    /** Source icon-hex (lowercase) -> replacement hex, applied to ALL icons. */
    val iconColors: Map<String, String>,
    /**
     * Path-substring (case-insensitive) -> (source icon-hex -> replacement) maps applied
     * only to icons whose resource path contains the substring, layered on top of
     * [iconColors]. Lets folders/checkboxes be themed distinctly from generic icons that
     * share the same source hex. See [MatugenIconColorPatcher].
     */
    val iconColorOverrides: Map<String, Map<String, String>>,
) {
    companion object {
        private val gson = Gson()

        fun parse(file: File): ColorSchemeConfig {
            val root = gson.fromJson(file.readText(), JsonObject::class.java)

            val editorColors = mutableMapOf<String, Color>()
            root.getAsJsonObject("editorColors")?.entrySet()?.forEach { (k, v) ->
                editorColors[k] = parseHex(v.asString)
            }

            val textAttributes = mutableMapOf<String, TextAttrConfig>()
            root.getAsJsonObject("textAttributes")?.entrySet()?.forEach { (k, v) ->
                val obj = v.asJsonObject
                textAttributes[k] = TextAttrConfig(
                    fg          = obj.get("fg")?.asString?.let { parseHex(it) },
                    bg          = obj.get("bg")?.asString?.let { parseHex(it) },
                    bold        = obj.get("bold")?.asBoolean ?: false,
                    italic      = obj.get("italic")?.asBoolean ?: false,
                    effectType  = obj.get("effectType")?.asString,
                    effectColor = obj.get("effectColor")?.asString?.let { parseHex(it) },
                    errorStripe = obj.get("errorStripe")?.asString?.let { parseHex(it) },
                    wave        = obj.get("wave")?.asString?.let { parseHex(it) },
                )
            }

            val uiColors = mutableMapOf<String, Color>()
            root.getAsJsonObject("uiColors")?.entrySet()?.forEach { (k, v) ->
                uiColors[k] = parseHex(v.asString)
            }

            val iconColors = mutableMapOf<String, String>()
            root.getAsJsonObject("iconColors")?.entrySet()?.forEach { (k, v) ->
                iconColors[k.lowercase()] = v.asString
            }

            val iconColorOverrides = linkedMapOf<String, Map<String, String>>()
            root.getAsJsonObject("iconColorOverrides")?.entrySet()?.forEach { (pattern, mapJson) ->
                val map = mutableMapOf<String, String>()
                mapJson.asJsonObject.entrySet().forEach { (src, dst) ->
                    map[src.lowercase()] = dst.asString
                }
                iconColorOverrides[pattern.lowercase()] = map
            }

            return ColorSchemeConfig(editorColors, textAttributes, uiColors, iconColors, iconColorOverrides)
        }

        private fun parseHex(hex: String): Color {
            val cleaned = hex.trimStart('#')
            return when (cleaned.length) {
                6 -> Color(
                    cleaned.substring(0, 2).toInt(16),
                    cleaned.substring(2, 4).toInt(16),
                    cleaned.substring(4, 6).toInt(16)
                )
                8 -> Color(
                    cleaned.substring(2, 4).toInt(16),
                    cleaned.substring(4, 6).toInt(16),
                    cleaned.substring(6, 8).toInt(16),
                    cleaned.substring(0, 2).toInt(16)
                )
                else -> throw IllegalArgumentException("Unrecognised hex color: $hex")
            }
        }
    }
}
