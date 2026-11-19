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

import android.app.Dialog
import android.content.Context
import android.graphics.Point
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.GridLayout
import android.widget.Spinner

import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView

import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.activity.backup.BackupDialogFragment
import com.buzbuz.smartautoclicker.databinding.DialogLibraryBinding
import com.buzbuz.smartautoclicker.databinding.DialogLibraryInfoBinding
import com.buzbuz.smartautoclicker.databinding.ItemLibraryBinding
import com.buzbuz.smartautoclicker.databinding.MergeLoadableListBinding
import com.buzbuz.smartautoclicker.databinding.ViewLibraryTitleBinding

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Lists the configs of the online library; the chosen one is downloaded and imported like a backup. */
class LibraryDialogFragment : DialogFragment() {

    private val viewModel: LibraryViewModel by viewModels()
    private lateinit var binding: DialogLibraryBinding
    private lateinit var listBinding: MergeLoadableListBinding
    private val adapter = LibraryAdapter(onInfo = { showInfo(it) }, onDownload = { viewModel.download(it) })
    private var screens: List<Point?> = emptyList()
    private var authors: List<String?> = emptyList()
    private var tagNames: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) viewModel.load()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogLibraryBinding.inflate(layoutInflater)
        listBinding = MergeLoadableListBinding.bind(binding.layoutList)
        listBinding.list.apply {
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            adapter = this@LibraryDialogFragment.adapter
        }

        val title = ViewLibraryTitleBinding.inflate(layoutInflater).apply {
            this.title.setText(R.string.dialog_library_title)
            btnFilter.setOnClickListener {
                binding.filters.visibility = if (binding.filters.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        binding.apply {
            search.setText(viewModel.filter.value.query)
            search.doAfterTextChanged { viewModel.setFilter(viewModel.filter.value.copy(query = it.toString())) }
            if (viewModel.filter.value.isAdvanced) filters.visibility = View.VISIBLE
            filterResolution.onSelected { viewModel.setFilter(viewModel.filter.value.copy(screen = screens[it])) }
            filterAuthor.onSelected { viewModel.setFilter(viewModel.filter.value.copy(author = authors[it])) }
            btnClearFilters.setOnClickListener {
                viewModel.setFilter(LibraryFilter(query = viewModel.filter.value.query))
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.combine(viewModel.filter) { state, filter -> state to filter }
                    .collect { (state, filter) -> onNewState(state, filter) }
            }
        }

        return AlertDialog.Builder(requireContext())
            .setCustomTitle(title.root)
            .setView(binding.root)
            .setNegativeButton(R.string.dialog_library_close, null)
            .setNeutralButton(R.string.dialog_library_refresh, null)
            .create()
            .apply {
                setOnShowListener { getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { viewModel.load() } }
            }
    }

    private fun onNewState(state: LibraryState, filter: LibraryFilter) {
        listBinding.apply {
            loading.visibility = if (state is LibraryState.Loading) View.VISIBLE else View.GONE
            when (state) {
                is LibraryState.Loaded -> {
                    updateFilters(state.configs, filter)
                    val shown = state.configs.filter(filter::matches)
                    adapter.downloading = state.downloading
                    adapter.submit(shown)
                    list.visibility = if (shown.isEmpty()) View.GONE else View.VISIBLE
                    empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
                    empty.setText(
                        if (state.configs.isEmpty()) R.string.dialog_library_empty else R.string.dialog_library_no_match
                    )
                }
                is LibraryState.Error -> {
                    list.visibility = View.GONE
                    empty.visibility = View.VISIBLE
                    empty.text = getString(R.string.dialog_library_error, state.reason)
                }
                is LibraryState.Downloaded -> {
                    BackupDialogFragment.newImport(state.file, state.screen)
                        .show(parentFragmentManager, BackupDialogFragment.FRAGMENT_TAG_BACKUP_DIALOG)
                    dismiss()
                }
                LibraryState.Loading -> {
                    list.visibility = View.GONE
                    empty.visibility = View.GONE
                }
            }
        }
    }

    /** Fills the advanced filters with the resolutions, authors and tags of the library, keeping the selection. */
    private fun updateFilters(configs: List<LibraryConfig>, filter: LibraryFilter) {
        val all = getString(R.string.dialog_library_all)

        val newScreens = listOf<Point?>(null) + configs.mapNotNull { it.screen }.distinct()
            .sortedWith(compareBy({ it.x }, { it.y }))
        if (newScreens != screens) {
            screens = newScreens
            binding.filterResolution.setItems(screens.map { it?.let { "${it.x}x${it.y}" } ?: all })
        }
        binding.filterResolution.select(screens.indexOf(filter.screen))

        val newAuthors = listOf<String?>(null) + configs.map { it.author }.filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
        if (newAuthors != authors) {
            authors = newAuthors
            binding.filterAuthor.setItems(authors.map { it ?: all })
        }
        binding.filterAuthor.select(authors.indexOfFirst { it.equals(filter.author, ignoreCase = true) })

        val newTags = (LibraryTags.names + configs.flatMap { it.tags }).distinct()
        if (newTags != tagNames) {
            tagNames = newTags
            binding.filterTags.removeAllViews()
            tagNames.forEach { tag ->
                binding.filterTags.addView(CheckBox(requireContext()).apply {
                    text = LibraryTags.format(listOf(tag))
                    this.tag = tag
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    }
                    setOnCheckedChangeListener { _, checked ->
                        val tags = viewModel.filter.value.tags
                        viewModel.setFilter(viewModel.filter.value.copy(tags = if (checked) tags + tag else tags - tag))
                    }
                })
            }
        }
        for (i in 0 until binding.filterTags.childCount) {
            (binding.filterTags.getChildAt(i) as CheckBox).apply {
                val checked = this.tag in filter.tags
                if (isChecked != checked) isChecked = checked
            }
        }
    }

    private fun showInfo(config: LibraryConfig) {
        val context = requireContext()
        val info = DialogLibraryInfoBinding.inflate(layoutInflater).apply {
            author.text = config.author.ifEmpty { "-" }
            resolution.text = config.screen?.let { "${it.x}x${it.y}" } ?: "-"
            events.text = config.events.takeIf { it > 0 }?.toString() ?: "-"
            tags.text = LibraryTags.format(config.tags)
            rowTags.visibility = if (config.tags.isEmpty()) View.GONE else View.VISIBLE
            description.text = config.description
            val hasDescription = config.description.isNotEmpty()
            labelDescription.visibility = if (hasDescription) View.VISIBLE else View.GONE
            description.visibility = if (hasDescription) View.VISIBLE else View.GONE
        }
        val title = ViewLibraryTitleBinding.inflate(layoutInflater).apply {
            this.title.text = config.name
            btnFilter.visibility = View.GONE
        }
        AlertDialog.Builder(context)
            .setCustomTitle(title.root)
            .setView(info.root)
            .setPositiveButton(R.string.dialog_library_download) { _, _ -> viewModel.download(config) }
            .setNegativeButton(R.string.dialog_library_close, null)
            .show()
    }

    companion object {
        const val FRAGMENT_TAG_LIBRARY_DIALOG = "LibraryDialog"
    }
}

private fun Spinner.setItems(items: List<String>) {
    adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, items).apply {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }
}

private fun Spinner.select(position: Int) {
    val target = position.coerceAtLeast(0)
    if (selectedItemPosition != target) setSelection(target, false)
}

private fun Spinner.onSelected(listener: (Int) -> Unit) {
    onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = listener(position)
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }
}

private class LibraryAdapter(
    private val onInfo: (LibraryConfig) -> Unit,
    private val onDownload: (LibraryConfig) -> Unit,
) : RecyclerView.Adapter<LibraryAdapter.ViewHolder>() {

    private var configs: List<LibraryConfig> = emptyList()
    var downloading: LibraryConfig? = null

    fun submit(list: List<LibraryConfig>) {
        configs = list
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = configs.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemLibraryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val config = configs[position]
        holder.binding.apply {
            name.text = config.name
            details.text = root.context.details(config)
            tags.text = LibraryTags.format(config.tags)
            tags.visibility = if (config.tags.isEmpty()) View.GONE else View.VISIBLE
            btnInfo.setOnClickListener { onInfo(config) }
            btnDownload.isEnabled = downloading == null
            btnDownload.setText(
                if (config == downloading) R.string.dialog_library_downloading else R.string.dialog_library_download
            )
            btnDownload.setOnClickListener { onDownload(config) }
        }
    }

    private fun Context.details(config: LibraryConfig): String = listOfNotNull(
        config.screen?.let { getString(R.string.dialog_library_screen, it.x, it.y) },
        config.author.ifEmpty { null },
    ).joinToString(" · ")

    class ViewHolder(val binding: ItemLibraryBinding) : RecyclerView.ViewHolder(binding.root)
}
