// EditSequenceFragment.kt
package com.example.purramid.thepurramid.traffic_light

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.purramid.thepurramid.databinding.FragmentEditSequenceBinding
import com.example.purramid.thepurramid.traffic_light.viewmodel.TimedSequence
import com.example.purramid.thepurramid.traffic_light.viewmodel.TrafficLightViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class EditSequenceFragment : DialogFragment() {

    private var _binding: FragmentEditSequenceBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TrafficLightViewModel by activityViewModels()
    private lateinit var sequenceAdapter: SequenceListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditSequenceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupButtons()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        sequenceAdapter = SequenceListAdapter(
            onSequenceClick = { sequence ->
                showSequenceOptionsDialog(sequence)
            },
            onDeleteClick = { sequence ->
                confirmDeleteSequence(sequence)
            }
        )

        binding.recyclerViewSequences.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = sequenceAdapter
        }
    }

    private fun setupButtons() {
        binding.buttonAddSequence.setOnClickListener {
            val sequences = viewModel.uiState.value.timedSequences
            if (sequences.size >= TimedSequence.MAX_SEQUENCES) {
                Snackbar.make(
                    binding.root,
                    "Maximum of ${TimedSequence.MAX_SEQUENCES} sequences reached",
                    Snackbar.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            openSequenceEditor(null) // New sequence
        }

        binding.buttonClose.setOnClickListener {
            dismiss()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateSequenceList(state.timedSequences, state.activeSequenceId)
                }
            }
        }
    }

    private fun showSequenceOptionsDialog(sequence: TimedSequence) {
        val options = if (viewModel.uiState.value.activeSequenceId == sequence.id) {
            arrayOf("Edit", "Deactivate", "Delete")
        } else {
            arrayOf("Edit", "Set as Active", "Delete")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(sequence.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openSequenceEditor(sequence)
                    1 -> {
                        if (viewModel.uiState.value.activeSequenceId == sequence.id) {
                            viewModel.setActiveSequence(null)
                            Snackbar.make(binding.root, "Sequence deactivated", Snackbar.LENGTH_SHORT).show()
                        } else {
                            viewModel.setActiveSequence(sequence.id)
                            Snackbar.make(binding.root, "${sequence.title} set as active", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                    2 -> confirmDeleteSequence(sequence)
                }
            }
            .show()
    }

    private fun updateSequenceList(sequences: List<TimedSequence>, activeSequenceId: String?) {
        sequenceAdapter.submitList(sequences.sortedBy { it.title }, activeSequenceId)

        // Show/hide empty state
        binding.textEmptyState.isVisible = sequences.isEmpty()
        binding.recyclerViewSequences.isVisible = sequences.isNotEmpty()

        // Update button state
        binding.buttonAddSequence.isEnabled = sequences.size < TimedSequence.MAX_SEQUENCES
    }

    private fun openSequenceEditor(sequence: TimedSequence?) {
        SequenceEditorFragment.newInstance(sequence?.id).show(
            childFragmentManager,
            SequenceEditorFragment.TAG
        )
    }

    private fun confirmDeleteSequence(sequence: TimedSequence) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Sequence?")
            .setMessage("Are you sure you want to delete '${sequence.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteSequence(sequence.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "EditSequenceDialog"
        fun newInstance(): EditSequenceFragment {
            return EditSequenceFragment()
        }
    }
}