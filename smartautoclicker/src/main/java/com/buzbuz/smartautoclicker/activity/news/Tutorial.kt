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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView

import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView

import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.databinding.ViewTutorialBinding

import kotlin.math.hypot
import kotlin.math.max

/** A tour of the config list: each step circles a button in red, points at it and says what it does. */
class Tutorial private constructor(private val activity: AppCompatActivity) {

    private class Step(@StringRes val title: Int, @StringRes val text: Int, val target: (() -> View?)? = null)

    private val steps = listOf(
        Step(R.string.tutorial_welcome_title, R.string.tutorial_welcome_text),
        Step(R.string.tutorial_add_title, R.string.tutorial_add_text) { view(R.id.add) },
        Step(R.string.tutorial_config_title, R.string.tutorial_config_text) {
            view<RecyclerView>(R.id.list)?.getChildAt(0)
        },
        Step(R.string.tutorial_edit_title, R.string.tutorial_edit_text) {
            view<RecyclerView>(R.id.list)?.getChildAt(0)?.findViewById(R.id.btn_rename)
        },
        Step(R.string.tutorial_news_title, R.string.tutorial_news_text) { view(R.id.action_news) },
        Step(R.string.tutorial_library_title, R.string.tutorial_library_text) { view(R.id.action_library) },
        Step(R.string.tutorial_settings_title, R.string.tutorial_settings_text) { view(R.id.action_settings) },
        Step(R.string.tutorial_search_title, R.string.tutorial_search_text) { view(R.id.action_search) },
        Step(R.string.tutorial_more_title, R.string.tutorial_more_text) { overflowButton() },
        Step(R.string.tutorial_end_title, R.string.tutorial_end_text),
    )

    private val root = activity.window.decorView as ViewGroup
    private val binding = ViewTutorialBinding.inflate(activity.layoutInflater, root, false)
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { place() }
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = finish()
    }
    private var index = 0

    private fun show() {
        root.addView(binding.root)
        root.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)
        binding.btnSkip.setOnClickListener { finish() }
        binding.btnBack.setOnClickListener { move(-1) }
        binding.btnNext.setOnClickListener { move(1) }
        render()
    }

    private fun move(direction: Int) {
        var next = index + direction
        while (next in steps.indices && !steps[next].isAvailable()) next += direction
        when {
            next > steps.lastIndex -> finish()
            next >= 0 -> {
                index = next
                render()
            }
        }
    }

    private fun render() {
        val step = steps[index]
        val shown = steps.filter { it.isAvailable() }
        binding.progress.text = activity.getString(R.string.tutorial_progress, shown.indexOf(step) + 1, shown.size)
        binding.title.setText(step.title)
        binding.text.setText(step.text)
        binding.btnBack.visibility = if (index == 0) View.INVISIBLE else View.VISIBLE
        binding.btnNext.setText(if (index == steps.lastIndex) R.string.tutorial_done else R.string.tutorial_next)
        binding.btnSkip.visibility = if (index == steps.lastIndex) View.INVISIBLE else View.VISIBLE
        place()
    }

    /** Puts the card a little below or above the circled button, toward the middle, then redraws the spotlight. */
    private fun place() {
        val target = steps[index].target?.invoke()?.takeIf { it.isShown && it.width > 0 }
        val spotlight = binding.spotlight
        val rect = target?.let { view ->
            val origin = IntArray(2).also(spotlight::getLocationInWindow)
            val location = IntArray(2).also(view::getLocationInWindow)
            val left = (location[0] - origin[0]).toFloat()
            val top = (location[1] - origin[1]).toFloat()
            RectF(left, top, left + view.width, top + view.height)
        }

        val gap = activity.resources.getDimensionPixelSize(R.dimen.tutorial_card_margin)
        val below = rect != null && rect.centerY() < spotlight.height / 2
        val gravity = when {
            rect == null -> Gravity.CENTER_VERTICAL
            below -> Gravity.TOP
            else -> Gravity.BOTTOM
        }
        val top = if (rect != null && below) rect.bottom.toInt() + gap else 0
        val bottom = if (rect != null && !below) spotlight.height - rect.top.toInt() + gap else 0
        (binding.card.layoutParams as FrameLayout.LayoutParams).let { params ->
            if (params.gravity != gravity || params.topMargin != top || params.bottomMargin != bottom) {
                params.gravity = gravity
                params.topMargin = top
                params.bottomMargin = bottom
                binding.card.layoutParams = params
            }
        }
        spotlight.show(rect, binding.card)
    }

    private fun finish() {
        root.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        backCallback.remove()
        root.removeView(binding.root)
    }

    private fun Step.isAvailable(): Boolean = target == null || target.invoke()?.isShown == true

    private fun <T : View> view(@IdRes id: Int): T? = activity.findViewById(id)

    private fun overflowButton(): View? = activity.findViewById<Toolbar>(R.id.toolbar)?.children
        ?.filterIsInstance<ActionMenuView>()?.firstOrNull()?.children?.lastOrNull { it is ImageView }

    companion object {
        fun start(activity: AppCompatActivity) = Tutorial(activity).show()
    }
}

/**
 * Dims the screen except for a red circle around the step's button, or a red frame around a wide one like a list row,
 * with a red arrow from the card to it.
 */
class TutorialSpotlightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val red = ContextCompat.getColor(context, R.color.badge)
    private val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = red
        style = Paint.Style.STROKE
        strokeWidth = 4 * density
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = red }
    private val arrowHead = Path()
    private val highlight = RectF()

    private var target: RectF? = null
    private var card: View? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun show(target: RectF?, card: View) {
        this.target = target
        this.card = card
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(DIM)
        val rect = target ?: return
        val margin = 6 * density
        val wide = rect.width() > rect.height() * WIDE_RATIO
        val radius = max(rect.width(), rect.height()) / 2 + margin
        if (wide) {
            highlight.set(rect.left + margin, rect.top + margin / 2, rect.right - margin, rect.bottom - margin / 2)
            canvas.drawRoundRect(highlight, CORNER * density, CORNER * density, clear)
            canvas.drawRoundRect(highlight, CORNER * density, CORNER * density, stroke)
        } else {
            canvas.drawCircle(rect.centerX(), rect.centerY(), radius, clear)
            canvas.drawCircle(rect.centerX(), rect.centerY(), radius, stroke)
        }

        val card = card?.takeIf { it.height > 0 } ?: return
        val inset = 32 * density
        val startX = rect.centerX().coerceIn(card.left + inset, card.right - inset)
        val startY = if (card.top > rect.bottom) card.top.toFloat() else card.bottom.toFloat()
        val (endX, endY) = if (wide) {
            startX to (if (startY > highlight.bottom) highlight.bottom + margin else highlight.top - margin)
        } else {
            val dx = rect.centerX() - startX
            val dy = rect.centerY() - startY
            val length = hypot(dx, dy)
            (rect.centerX() - dx / length * (radius + margin)) to (rect.centerY() - dy / length * (radius + margin))
        }
        val dx = endX - startX
        val dy = endY - startY
        val length = hypot(dx, dy)
        if (length <= inset / 2) return

        val ux = dx / length
        val uy = dy / length
        canvas.drawLine(startX + ux * 4 * density, startY + uy * 4 * density, endX, endY, stroke)

        val head = 14 * density
        arrowHead.reset()
        arrowHead.moveTo(endX, endY)
        arrowHead.lineTo(endX - ux * head - uy * head * 0.6f, endY - uy * head + ux * head * 0.6f)
        arrowHead.lineTo(endX - ux * head + uy * head * 0.6f, endY - uy * head - ux * head * 0.6f)
        arrowHead.close()
        canvas.drawPath(arrowHead, fill)
    }
}

private val DIM = Color.argb(185, 0, 0, 0)
private const val WIDE_RATIO = 1.6f
private const val CORNER = 12f
