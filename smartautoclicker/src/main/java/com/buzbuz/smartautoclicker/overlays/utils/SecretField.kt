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
package com.buzbuz.smartautoclicker.overlays.utils

import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import android.widget.ImageView

import com.buzbuz.smartautoclicker.R

/** Hides the text behind dots; the eye shows or hides it again. */
fun EditText.hideSecret(eye: ImageView) {
    var shown = false
    val update = {
        val cursor = selectionEnd
        transformationMethod = if (shown) null else PasswordTransformationMethod.getInstance()
        eye.setImageResource(if (shown) R.drawable.ic_visibility_off else R.drawable.ic_visibility)
        setSelection(cursor.coerceIn(0, length()))
    }
    eye.setOnClickListener {
        shown = !shown
        update()
    }
    update()
}
