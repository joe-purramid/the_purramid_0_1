// SequenceEditorFragment.kt
package com.example.purramid.thepurramid.traffic_light

import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.FragmentSequenceEditorBinding
import com.example.purramid.thepurramid.traffic_light.viewmodel.LightColor
import com.example.purramid.thepurramid.traffic_light.viewmodel.SequenceStep
import com.example.purramid.thepurramid.traffic_light.viewmodel.TimedSequence
import com.example.purramid.thepurramid.traffic_light.viewmodel.TrafficLightViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.UUID

class SequenceEditorFragment : DialogFragment() {

    private var _binding: FragmentSequenceEditorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TrafficLightViewModel by activityViewModels()
    private lateinit var stepAdapter: SequenceStepAdapter

    private var sequenceId: String? = null
    private var currentSequence: TimedSequence? = null
    private var editedSteps = mutableListOf<SequenceStep>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sequenceId = arguments?.getString(ARG_SEQUENCE_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSequenceEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        setupRecyclerView()
        loadSequence()
    }

    private fun setupViews() {
        // Title input
        binding.editTextTitle.filters = arrayOf(
            InputFilter.LengthFilter(TimedSequence.MAX_TITLE_LENGTH)
        )

        // Buttons
        binding.buttonSaveSequence.setOnClickListener {
            saveSequence()
        }

        binding.buttonCancelSequence.setOnClickListener {
            dismiss()
        }

        binding.buttonAddStep.setOnClickListener {
            addNewStep()
        }
    }

    private fun setupRecyclerView() {
        stepAdapter = SequenceStepAdapter(
            onColorClick = { position, step ->
                showColorPicker(position, step)
            },
            onDurationClick = { position, step ->
                showDurationPicker(position, step)
            },
            onMessageClick = { position, step ->
                showMessageEditor(position, step)
            },
            onDeleteClick = { position ->
                deleteStep(position)
            }
        )

        binding.recyclerViewSteps.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = stepAdapter
        }

        // Setup drag to reorder
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                Collections.swap(editedSteps, fromPosition, toPosition)
                stepAdapter.notifyItemMoved(fromPosition, toPosition)

                // Update order numbers
                updateStepOrder()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not used
            }
        })

        itemTouchHelper.attachToRecyclerView(binding.recyclerViewSteps)

        // Set up drag handle touch listener
        stepAdapter.dragHandleTouchListener = { viewHolder ->
            itemTouchHelper.startDrag(viewHolder)
        }
    }

    private fun loadSequence() {
        sequenceId?.let { id ->
            currentSequence = viewModel.uiState.value.timedSequences.find { it.id == id }
            currentSequence?.let { sequence ->
                binding.editTextTitle.setText(sequence.title)
                editedSteps.addAll(sequence.steps)
                updateStepList()
            }
        }

        // If no sequence (new), add one default step
        if (editedSteps.isEmpty()) {
            addNewStep()
        }
    }

    private fun addNewStep() {
        if (editedSteps.size >= TimedSequence.MAX_STEPS) {
            Snackbar.make(
                binding.root,
                getString(R.string.max_steps_reached_snackbar, TimedSequence.MAX_STEPS),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val newStep = SequenceStep(
            order = editedSteps.size + 1,
            color = null,
            durationSeconds = 0
        )

        editedSteps.add(newStep)
        updateStepList()
    }

    private fun deleteStep(position: Int) {
        if (position < editedSteps.size) {
            editedSteps.removeAt(position)
            updateStepOrder()
            updateStepList()
        }
    }

    private fun updateStepOrder() {
        editedSteps.forEachIndexed { index, step ->
            editedSteps[index] = step.copy(order = index + 1)
        }
    }

    private fun updateStepList() {
        stepAdapter.submitList(editedSteps.toList())

        // Update total duration
        val totalSeconds = editedSteps.sumOf { it.durationSeconds }
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        binding.textTotalDuration.text = String.format("Total: %d:%02d:%02d", hours, minutes, seconds)

        // Update button states
        binding.buttonAddStep.isEnabled = editedSteps.size < TimedSequence.MAX_STEPS
        binding.textStepCount.text = "${editedSteps.size}/${TimedSequence.MAX_STEPS} steps"
    }

    private fun showColorPicker(position: Int, step: SequenceStep) {
        val colors = arrayOf(getString(R.string.red_color_label), getString(R.string.yellow_color_label), getString(R.string.green_color_label))
        val colorValues = arrayOf(LightColor.RED, LightColor.YELLOW, LightColor.GREEN)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.select_color))
            .setItems(colors) { _, which ->
                editedSteps[position] = step.copy(color = colorValues[which])
                updateStepList()
            }
            .show()
    }

    private fun showDurationPicker(position: Int, step: SequenceStep) {
        DurationPickerFragment.newInstance(step.durationSeconds) { newDuration ->
            editedSteps[position] = step.copy(durationSeconds = newDuration)
            updateStepList()
        }.show(childFragmentManager, DurationPickerFragment.TAG)
    }

    private fun showMessageEditor(position: Int, step: SequenceStep) {
        StepMessageFragment.newInstance(step.message) { newMessage ->
            editedSteps[position] = step.copy(message = newMessage)
            updateStepList()
        }.show(childFragmentManager, StepMessageFragment.TAG)
    }

    private fun saveSequence() {
        val title = binding.editTextTitle.text.toString().trim()

        if (title.isEmpty()) {
            Snackbar.make(binding.root, getString(R.string.sequence_title_required), Snackbar.LENGTH_SHORT).show()
            return
        }

        // Validate all steps
        val invalidSteps = editedSteps.withIndex().filter { !it.value.isValid() }
        if (invalidSteps.isNotEmpty()) {
            val firstInvalidIndex = invalidSteps.first().index
            Snackbar.make(
                binding.root,
                getString(R.string.sequence_step_invalid, firstInvalidIndex + 1),
                Snackbar.LENGTH_LONG
            ).show()

            // Scroll to first invalid step
            binding.recyclerViewSteps.smoothScrollToPosition(firstInvalidIndex)
            return
        }

        if (editedSteps.isEmpty()) {
            Snackbar.make(
                binding.root,
                getString(R.string.sequence_empty_error),
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val sequence = TimedSequence(
            id = sequenceId ?: UUID.randomUUID().toString(),
            title = title,
            steps = editedSteps
        )

        if (sequenceId != null) {
            viewModel.updateSequence(sequence)
        } else {
            viewModel.addSequence(sequence)
        }

        // Save immediately
        viewModel.saveState()

        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SequenceEditorDialog"
        private const val ARG_SEQUENCE_ID = "sequence_id"

        fun newInstance(sequenceId: String?): SequenceEditorFragment {
            return SequenceEditorFragment().apply {
                arguments = bundleOf(ARG_SEQUENCE_ID to sequenceId)
            }
        }
    }
}