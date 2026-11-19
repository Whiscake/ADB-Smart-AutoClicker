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

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.Toast

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment

import com.buzbuz.smartautoclicker.BuildConfig
import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.databinding.DialogNewsBinding
import com.buzbuz.smartautoclicker.databinding.ViewLibraryTitleBinding

/** What changed in this version of the clicker, and the newer published version when there is one. */
class NewsDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogNewsBinding.inflate(layoutInflater)
        val title = ViewLibraryTitleBinding.inflate(layoutInflater).apply {
            this.title.text = getString(R.string.dialog_news_title, BuildConfig.VERSION_NAME)
            btnFilter.visibility = View.GONE
        }

        binding.changes.text = changes(binding.changes.paint.measureText(BULLET).toInt())
        RemoteStatus.release.value?.let { release ->
            binding.update.visibility = View.VISIBLE
            binding.updateTitle.text = getString(R.string.dialog_news_update_title, release.name)
            binding.updateNotes.text = release.notes
            binding.updateNotes.visibility = if (release.notes.isBlank()) View.GONE else View.VISIBLE
            binding.btnUpdate.visibility = if (release.url.isBlank()) View.GONE else View.VISIBLE
            binding.btnUpdate.setOnClickListener { open(release.url) }
        }

        return AlertDialog.Builder(requireContext())
            .setCustomTitle(title.root)
            .setView(binding.root)
            .setPositiveButton(R.string.dialog_library_close, null)
            .setNeutralButton(R.string.dialog_news_tutorial) { _, _ ->
                (activity as? AppCompatActivity)?.let(Tutorial::start)
            }
            .create()
    }

    /** The four sections of this version's changes, each item a bullet with its next lines aligned after the bullet. */
    private fun changes(indent: Int): CharSequence = SpannableStringBuilder().apply {
        val items = mutableListOf<IntRange>()
        section(R.string.dialog_news_features, R.array.news_features, R.color.primary, items)
        section(R.string.dialog_news_fixes, R.array.news_fixes, R.color.badge, items)
        section(R.string.dialog_news_balance, R.array.news_balance, R.color.library, items)
        section(R.string.dialog_news_debug, R.array.news_debug, R.color.accent, items)
        items.forEach { setSpan(LeadingMarginSpan.Standard(0, indent), it.first, it.last, Spanned.SPAN_PARAGRAPH) }
    }

    private fun SpannableStringBuilder.section(
        @StringRes title: Int,
        @ArrayRes items: Int,
        color: Int,
        ranges: MutableList<IntRange>,
    ) {
        val entries = resources.getStringArray(items)
        if (entries.isEmpty()) return
        if (isNotEmpty()) append("\n")
        val start = length
        append(getString(title)).append("\n")
        setSpan(ForegroundColorSpan(ContextCompat.getColor(requireContext(), color)), start, length, SPAN)
        setSpan(StyleSpan(Typeface.BOLD), start, length, SPAN)
        setSpan(RelativeSizeSpan(1.1f), start, length, SPAN)
        entries.forEach { entry ->
            val itemStart = length
            append(BULLET).append(entry).append("\n")
            ranges.add(itemStart..length)
        }
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (ex: ActivityNotFoundException) {
            Toast.makeText(requireContext(), url, Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val FRAGMENT_TAG_NEWS_DIALOG = "NewsDialog"
    }
}

private const val SPAN = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
private const val BULLET = "\u2022  "
