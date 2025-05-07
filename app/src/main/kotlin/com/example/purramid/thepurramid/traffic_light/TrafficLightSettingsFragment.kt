// TrafficLightSettingsFragment.kt
package com.example.purramid.thepurramid.traffic_light

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        observeViewModel() // We'll populate this in the next step
    }

    /* // Optional: Use MaterialAlertDialogBuilder for a standard dialog frame
     override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
         _binding = FragmentTrafficLightSettingsBinding.inflate(LayoutInflater.from(context))

         setupViews()
         observeViewModel()

         return MaterialAlertDialogBuilder(requireContext())
             .setTitle(R.string.traffic_light_settings) // Use string resource
             .setView(binding.root)
             // Add Positive/Negative buttons if needed (e.g., Save/Cancel)
             .setPositiveButton(R.string.dialog_close) { dialog, _ ->
                 dialog.dismiss()
             }
             .create()
     }*/

     private fun setupViews() {
        // --- Setup Initial Listeners (functionality added in next step) ---

        binding.radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            // TODO: Call ViewModel to update mode
        }

        binding.switchOrientation.setOnCheckedChangeListener { _, isChecked ->
            // TODO: Call ViewModel to update orientation (isChecked=Horizontal?)
        }

        binding.switchBlinking.setOnCheckedChangeListener { _, isChecked ->
            // TODO: Call ViewModel to update blinking enabled
        }

        binding.buttonAdjustValues.setOnClickListener {
            // TODO: Open Adjust Values Dialog/Fragment
        }

        binding.buttonAddMessages.setOnClickListener {
            // TODO: Open Add Messages Dialog/Fragment
        }

        binding.buttonEditSequence.setOnClickListener {
            // TODO: Open Edit Sequence Activity/Fragment
        }

         binding.switchShowTimeRemaining.setOnCheckedChangeListener { _, isChecked ->
            // TODO: Call ViewModel to update show time remaining
        }

         binding.switchShowTimeline.setOnCheckedChangeListener { _, isChecked ->
            // TODO: Call ViewModel to update show timeline
        }

        binding.buttonAddAnother.setOnClickListener {
            // TODO: Tell Activity/Service to launch another instance
             dismiss() // Close settings after requesting a new light
        }

        // TODO: Set initial state of views based on ViewModel (in observeViewModel)
        // TODO: Set visibility of conditional views based on mode (in observeViewModel)
    }

    private fun observeViewModel() {
        // TODO: Observe viewModel.uiState
        // Update radio buttons, switches based on state.currentMode, state.orientation etc.
        // Update visibility of conditional buttons/switches based on state.currentMode
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