// TrafficLightSettingsFragment.kt
package com.example.purramid.thepurramid.traffic_light

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels // Use activityViewModels if sharing ViewModel with Activity
import androidx.lifecycle.Lifecycle 
import androidx.lifecycle.lifecycleScope 
import androidx.lifecycle.repeatOnLifecycle 
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.FragmentTrafficLightSettingsBinding
import com.example.purramid.thepurramid.traffic_light.viewmodel.Orientation
import com.example.purramid.thepurramid.traffic_light.viewmodel.TrafficLightMode
import com.example.purramid.thepurramid.traffic_light.viewmodel.TrafficLightState
import com.example.purramid.thepurramid.traffic_light.viewmodel.TrafficLightViewModel
import kotlinx.coroutines.launch

// Note: Using activityViewModels assumes you want this settings fragment
// to directly interact with the TrafficLightActivity's ViewModel instance.
// If multiple traffic lights need independent settings later,
// this might need adjustment (e.g., passing an instance ID).
class TrafficLightSettingsFragment : DialogFragment() {

    private var _binding: FragmentTrafficLightSettingsBinding? = null
    private val binding get() = _binding!!

    // Get a reference to the Activity's ViewModel
    private val viewModel: TrafficLightViewModel by activityViewModels()

    // To prevent listener loops during programmatic changes 
    private var blockListeners: Boolean = false 

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrafficLightSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    /* // Using onCreateView for more control over dialog presentation if not using MaterialAlertDialogBuilder
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = FragmentTrafficLightSettingsBinding.inflate(LayoutInflater.from(context))
        // viewModel.setSettingsOpen(true) // Managed by Activity now

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.traffic_light_settings)
            .setView(binding.root)
            .setPositiveButton(R.string.dialog_close) { d, _ ->
                d.dismiss() // onDismiss will be called
            }
            .create()

        // Need to call setup and observe after binding is available and before view is shown
        // This lifecycle is tricky with onCreateDialog directly.
        // Consider using onCreateView and letting DialogFragment manage the dialog shell,
        // or call setupViews and observeViewModel in onStart or onResume if using onCreateDialog.
        // For simplicity, we'll stick to onCreateView and let DialogFragment manage the dialog.
        return dialog
    }*/

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        observeViewModel() // We'll populate this in the next step
    }

     private fun setupViews() {
         binding.radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
             if (blockListeners) return@setOnCheckedChangeListener
             val newMode = when (checkedId) {
                 R.id.radio_manual -> TrafficLightMode.MANUAL_CHANGE
                 R.id.radio_responsive -> TrafficLightMode.RESPONSIVE_CHANGE
                 R.id.radio_timed -> TrafficLightMode.TIMED_CHANGE
                 else -> viewModel.uiState.value.currentMode // Should not happen
             }
             viewModel.setMode(newMode)
         }

         binding.switchOrientation.setOnCheckedChangeListener { _, isChecked ->
             if (blockListeners) return@setOnCheckedChangeListener
             viewModel.setOrientation(if (isChecked) Orientation.HORIZONTAL else Orientation.VERTICAL)
         }

         binding.switchBlinking.setOnCheckedChangeListener { _, isChecked ->
             if (blockListeners) return@setOnCheckedChangeListener
             viewModel.toggleBlinking(isChecked)
         }

         binding.buttonAdjustValues.setOnClickListener {
             AdjustValuesFragment.newInstance().show(
                 parentFragmentManager, AdjustValuesFragment.TAG
             )
         }

         binding.buttonAddMessages.setOnClickListener {
             Toast.makeText(context, "Add Messages: Coming Soon", Toast.LENGTH_SHORT).show()
         }

         binding.buttonEditSequence.setOnClickListener {
             Toast.makeText(context, "Edit Sequence: Coming Soon", Toast.LENGTH_SHORT).show()
         }

         binding.switchShowTimeRemaining.setOnCheckedChangeListener { _, isChecked ->
             if (blockListeners) return@setOnCheckedChangeListener
             viewModel.setShowTimeRemaining(isChecked) // Placeholder in VM
         }

         binding.switchShowTimeline.setOnCheckedChangeListener { _, isChecked ->
             if (blockListeners) return@setOnCheckedChangeListener
             viewModel.setShowTimeline(isChecked) // Placeholder in VM
         }

         binding.buttonAddAnother.setOnClickListener {
             Toast.makeText(context, "Add Another: Coming Soon", Toast.LENGTH_SHORT).show()
             // TODO: Tell Activity/Service to launch another instance
             // This would likely involve an interface callback to the hosting Activity
             // or a shared ViewModel/Service that manages instances.
             dismiss()
         }
     }

    private fun observeViewModelState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUiControls(state)
                }
            }
        }
    }

    private fun updateUiControls(state: TrafficLightState) {
        blockListeners = true // Prevent listeners from firing during programmatic updates

        binding.radioGroupMode.check(
            when (state.currentMode) {
                TrafficLightMode.MANUAL_CHANGE -> R.id.radio_manual
                TrafficLightMode.RESPONSIVE_CHANGE -> R.id.radio_responsive
                TrafficLightMode.TIMED_CHANGE -> R.id.radio_timed
            }
        )

        binding.switchOrientation.isChecked = state.orientation == Orientation.HORIZONTAL
        binding.switchBlinking.isChecked = state.isBlinkingEnabled

        // Conditional visibility
        binding.buttonAdjustValues.isVisible = state.currentMode == TrafficLightMode.RESPONSIVE_CHANGE
        binding.buttonAddMessages.isVisible = state.currentMode == TrafficLightMode.MANUAL_CHANGE || state.currentMode == TrafficLightMode.RESPONSIVE_CHANGE
        binding.buttonEditSequence.isVisible = state.currentMode == TrafficLightMode.TIMED_CHANGE
        binding.switchShowTimeRemaining.isVisible = state.currentMode == TrafficLightMode.TIMED_CHANGE
        binding.switchShowTimeline.isVisible = state.currentMode == TrafficLightMode.TIMED_CHANGE

        // Handle conditional enabling (placeholders for now)
        binding.radioResponsive.isEnabled = state.isMicrophoneAvailable // Based on mic check later
        if (!state.isMicrophoneAvailable && state.currentMode == TrafficLightMode.RESPONSIVE_CHANGE) {
            // If somehow responsive is selected but mic becomes unavailable, switch to manual.
            // This logic is a bit preemptive; mic check should prevent selection.
            viewModel.setMode(TrafficLightMode.MANUAL_CHANGE)
            binding.radioGroupMode.check(R.id.radio_manual)
        }

        binding.buttonAddAnother.isEnabled = state.numberOfOpenInstances < 4 // Based on instance count later

        blockListeners = false
    }


    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        viewModel.setSettingsOpen(false) // Notify ViewModel that settings are closed
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Avoid memory leaks
    }

    companion object {
        const val TAG = "TrafficLightSettingsDialog" // Tag for finding the fragment

        fun newInstance(): TrafficLightSettingsFragment {
            return TrafficLightSettingsFragment()
            // Add arguments here if needed later (e.g., traffic light instance ID)
        }
    }
}