package com.nuvio.tv.data.local

import androidx.annotation.StringRes
import com.nuvio.tv.R

/**
 * One-tap subtitle looks. Each sets size, position, weight, colours and outline together;
 * language choices and the HDR override are left as they are.
 */
enum class SubtitleStylePreset(
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val size: Int,
    val verticalOffset: Int,
    val bold: Boolean,
    val textColor: Int,
    val backgroundColor: Int,
    val outlineEnabled: Boolean,
    val outlineColor: Int,
    val outlineWidth: Int,
) {
    CLASSIC(R.string.sub_preset_classic, R.string.sub_preset_classic_desc, 120, 5, false, WHITE, TRANSPARENT, true, BLACK, 2),
    CINEMA(R.string.sub_preset_cinema, R.string.sub_preset_cinema_desc, 110, 4, false, WHITE, TRANSPARENT, true, BLACK, 1),
    BOLD(R.string.sub_preset_bold, R.string.sub_preset_bold_desc, 130, 5, true, WHITE, TRANSPARENT, true, BLACK, 3),
    YELLOW(R.string.sub_preset_yellow, R.string.sub_preset_yellow_desc, 120, 5, false, YELLOW_TEXT, TRANSPARENT, true, BLACK, 2),
    BOXED(R.string.sub_preset_boxed, R.string.sub_preset_boxed_desc, 110, 6, false, WHITE, HALF_BLACK, false, BLACK, 1),
    LARGE(R.string.sub_preset_large, R.string.sub_preset_large_desc, 160, 6, true, WHITE, TRANSPARENT, true, BLACK, 3),
    SMALL(R.string.sub_preset_small, R.string.sub_preset_small_desc, 90, 3, false, WHITE, TRANSPARENT, true, BLACK, 1);

    /** True when [style] already looks like this preset. */
    fun matches(style: SubtitleStyleSettings): Boolean =
        style.size == size && style.verticalOffset == verticalOffset && style.bold == bold &&
            style.textColor == textColor && style.backgroundColor == backgroundColor &&
            style.outlineEnabled == outlineEnabled && (!outlineEnabled || (style.outlineColor == outlineColor && style.outlineWidth == outlineWidth))

    companion object {
        fun matching(style: SubtitleStyleSettings): SubtitleStylePreset? = entries.firstOrNull { it.matches(style) }
    }
}

private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = 0xFF000000.toInt()
private const val TRANSPARENT = 0x00000000
private const val HALF_BLACK = 0xB3000000.toInt()
private const val YELLOW_TEXT = 0xFFFFE14D.toInt()
