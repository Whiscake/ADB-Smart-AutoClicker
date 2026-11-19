/*
 * Copyright (C) 2026 Whiscake
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.activity.library

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan

/**
 * The tags of the library configs, each with its own color. A tag the index uses but this list doesn't know still
 * shows, with a color picked from its name, so new tags need no new APK.
 */
object LibraryTags {

    private val known = linkedMapOf(
        "Pseudokai" to "#8E24AA",
        "Gates" to "#E53935",
        "Energy" to "#EF8F00",
        "Score Attack" to "#1E88E5",
        "Maps" to "#43A047",
        "General" to "#757575",
    )

    private val spare = listOf("#00897B", "#D81B60", "#6D4C41", "#3949AB", "#F4511E", "#5E35B1")

    val names: List<String> = known.keys.toList()

    fun canonical(tag: String): String = names.firstOrNull { it.equals(tag, ignoreCase = true) } ?: tag

    fun color(tag: String): Int =
        Color.parseColor(known[tag] ?: spare[Math.floorMod(tag.lowercase().hashCode(), spare.size)])

    /** The tags as colored dots followed by their colored names. */
    fun format(tags: List<String>): CharSequence = SpannableStringBuilder().apply {
        tags.forEachIndexed { index, tag ->
            if (index > 0) append("   ")
            val start = length
            append("● ").append(tag)
            setSpan(ForegroundColorSpan(color(tag)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.BOLD), start + 2, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
