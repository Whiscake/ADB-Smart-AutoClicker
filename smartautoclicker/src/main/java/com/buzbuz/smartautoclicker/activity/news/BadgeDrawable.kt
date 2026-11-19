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
package com.buzbuz.smartautoclicker.activity.news

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable

/** An icon with a red "!" in its top right corner. */
class BadgeDrawable(val icon: Drawable) : Drawable() {

    private val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BADGE_COLOR }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    override fun onBoundsChange(bounds: Rect) {
        icon.bounds = bounds
    }

    override fun draw(canvas: Canvas) {
        icon.draw(canvas)
        val radius = bounds.width() * 0.25f
        val cx = bounds.right - radius
        val cy = bounds.top + radius
        canvas.drawCircle(cx, cy, radius, circle)
        text.textSize = radius * 1.6f
        canvas.drawText("!", cx, cy - (text.ascent() + text.descent()) / 2, text)
    }

    override fun getIntrinsicWidth(): Int = icon.intrinsicWidth
    override fun getIntrinsicHeight(): Int = icon.intrinsicHeight

    override fun setAlpha(alpha: Int) {
        icon.alpha = alpha
        circle.alpha = alpha
        text.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        icon.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private val BADGE_COLOR = Color.parseColor("#E53935")
