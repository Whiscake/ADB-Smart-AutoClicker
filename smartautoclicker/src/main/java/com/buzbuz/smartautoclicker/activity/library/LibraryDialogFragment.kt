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
import android.graphics.Point
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.RecyclerView

import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.activity.backup.BackupDialogFragment
import com.buzbuz.smartautoclicker.baseui.dialog.setCustomTitle
import com.buzbuz.smartautoclicker.databinding.DialogLibraryBinding
import com.buzbuz.smartautoclicker.databinding.ItemLibraryBinding
import com.buzbuz.smartautoclicker.databinding.MergeLoadableListBinding

import kotlinx.coroutines.launch

/** Lists the configs of the online library; the chosen one is downloaded and imported like a backup. */
class LibraryDialogFragment : DialogFragment() {

    private val viewModel: LibraryViewModel by viewModels()
    private lateinit var listBinding: MergeLoadableListBinding
    private val adapter = LibraryAdapter { viewModel.download(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) viewModel.load()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogLibraryBinding.inflate(layoutInflater)
        listBinding = MergeLoadableListBinding.bind(binding.root)
        listBinding.list.apply {
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
            adapter = this@LibraryDialogFragment.adapter
        }
        adapter.screen = viewModel.screen

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { onNewState(it) }
            }
        }

        return AlertDialog.Builder(requireContext())
            .setCustomTitle(R.layout.view_dialog_title, R.string.dialog_library_title)
            .setView(binding.root)
            .setNegativeButton(R.string.dialog_library_close, null)
            .setNeutralButton(R.string.dialog_library_refresh, null)
            .create()
            .apply {
                setOnShowListener { getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { viewModel.load() } }
            }
    }

    private fun onNewState(state: LibraryState) {
        listBinding.apply {
            loading.visibility = if (state is LibraryState.Loading) View.VISIBLE else View.GONE
            when (state) {
                is LibraryState.Loaded -> {
                    adapter.downloading = state.downloading
                    adapter.submit(state.configs)
                    list.visibility = if (state.configs.isEmpty()) View.GONE else View.VISIBLE
                    empty.visibility = if (state.configs.isEmpty()) View.VISIBLE else View.GONE
                    empty.setText(R.string.dialog_library_empty)
                }
                is LibraryState.Error -> {
                    list.visibility = View.GONE
                    empty.visibility = View.VISIBLE
                    empty.text = getString(R.string.dialog_library_error, state.reason)
                }
                is LibraryState.Downloaded -> {
                    BackupDialogFragment.newImport(state.file)
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

    companion object {
        const val FRAGMENT_TAG_LIBRARY_DIALOG = "LibraryDialog"
    }
}

private class LibraryAdapter(private val onDownload: (LibraryConfig) -> Unit) :
    RecyclerView.Adapter<LibraryAdapter.ViewHolder>() {

    private var configs: List<LibraryConfig> = emptyList()
    var screen: Point? = null
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
            val context = root.context
            name.text = config.name
            details.text = listOfNotNull(
                config.author.ifEmpty { null },
                config.screen?.let {
                    context.getString(
                        if (it == screen) R.string.dialog_library_screen_fits else R.string.dialog_library_screen,
                        it.x, it.y,
                    )
                },
                config.events.takeIf { it > 0 }?.let {
                    context.resources.getQuantityString(R.plurals.scenario_sub_text, it, it)
                },
            ).joinToString(" · ")
            description.text = config.description
            description.visibility = if (config.description.isEmpty()) View.GONE else View.VISIBLE
            btnDownload.isEnabled = downloading == null
            btnDownload.setText(
                if (config == downloading) R.string.dialog_library_downloading else R.string.dialog_library_download
            )
            btnDownload.setOnClickListener { onDownload(config) }
        }
    }

    class ViewHolder(val binding: ItemLibraryBinding) : RecyclerView.ViewHolder(binding.root)
}
