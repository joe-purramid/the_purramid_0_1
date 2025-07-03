// TrafficLightSettingsFragment.kt
package com.example.purramid.thepurramid.traffic_light

import android.content.Context // Added for SharedPreferences
import android.content.DialogInterface
import android.content.Intent // Added for Service Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat // Added for starting service
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.FragmentTrafficLightSettingsBinding
import com.example.purramid.thepurramid.traffic_light.viewmodel.Orientation
import com.example.purramid.thepurramid.traffic_light.viewmodel.TrafficLightMode
import com.example.purramid.thepurramid.traffic_light.viewmodel.TrafficLightState
import com.example.purramid.thepurramid.traffic_light.viewmodel.TrafficLightViewModel
import com.google.android.material.snackbar.Snackbar // Added for Snackbar
import kotlinx.coroutines.launch

class TrafficLightSettingsFragment : DialogFragment() { // Or AppCompatDialogFragment for Material theming

    private var _binding: FragmentTrafficLightSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TrafficLightViewModel by activityViewModels()
    private var blockListeners: Boolean = false

    // Companion object to provide a newInstance method, potentially with instanceId argument
    companion object {
        const val TAG = "TrafficLightSettingsDialog"
        fun newInstance(instanceId: Int = 0): TrafficLightSettingsFragment {
            val fragment = TrafficLightSettingsFragment()
            // Pass instanceId if settings are for a specific traffic light
            // arguments = Bundle().apply { putInt(TrafficLightViewModel.KEY_INSTANCE_ID, instanceId) }
            return fragment
        }
    }

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
        observeViewModelState()
        observeSnackbarEvents()
    }

    private fun setupViews() {
        // Mode selection
        binding.radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            if (blockListeners) return@setOnCheckedChangeListener
            val newMode = when (checkedId) {
                R.id.radio_manual -> TrafficLightMode.MANUAL_CHANGE
                R.id.radio_responsive -> TrafficLightMode.RESPONSIVE_CHANGE
                R.id.radio_timed -> TrafficLightMode.TIMED_CHANGE
                else -> viewModel.uiState.value.currentMode
            }
            viewModel.setMode(newMode)
        }

        // Orientation switch
        binding.switchOrientation.setOnCheckedChangeListener { _, isChecked ->
            if (blockListeners) return@setOnCheckedChangeListener
            viewModel.setOrientation(if (isChecked) Orientation.HORIZONTAL else Orientation.VERTICAL)
        }

        // Blinking switch
        binding.switchBlinking.setOnCheckedChangeListener { _, isChecked ->
            if (blockListeners) return@setOnCheckedChangeListener
            viewModel.toggleBlinking(isChecked)
        }

        // Adjust Values button (for Responsive mode)
        binding.buttonAdjustValues.setOnClickListener {
            AdjustValuesFragment.newInstance().show(
                parentFragmentManager, AdjustValuesFragment.TAG
            )
        }

        // Add Messages button (for Manual/Responsive modes)
        binding.buttonAddMessages.setOnClickListener {
            AddMessagesFragment.newInstance().show(
                parentFragmentManager, AddMessagesFragment.TAG
            )
        }

        // Edit Sequence button (for Timed mode)
        binding.buttonEditSequence.setOnClickListener {
            EditSequenceFragment.newInstance().show(
                parentFragmentManager, EditSequenceFragment.TAG
            )
        }

        // Show Time Remaining switch (for Timed mode)
        binding.switchShowTimeRemaining.setOnCheckedChangeListener { _, isChecked ->
            if (blockListeners) return@setOnCheckedChangeListener
            viewModel.setShowTimeRemaining(isChecked)
        }

        // Show Timeline switch (for Timed mode)
        binding.switchShowTimeline.setOnCheckedChangeListener { _, isChecked ->
            if (blockListeners) return@setOnCheckedChangeListener
            viewModel.setShowTimeline(isChecked)
        }

        // Add Another button
        binding.buttonAddAnother.setOnClickListener {
            val prefs = requireActivity().getSharedPreferences(
                TrafficLightActivity.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            val activeCount = prefs.getInt(TrafficLightActivity.KEY_ACTIVE_COUNT, 0)

            if (activeCount < TrafficLightService.MAX_TRAFFIC_LIGHTS) {
                Log.d(TAG, "Add new traffic light requested from settings.")
                val serviceIntent = Intent(requireContext(), TrafficLightService::class.java).apply {
                    action = ACTION_ADD_NEW_TRAFFIC_LIGHT_INSTANCE
                }
                ContextCompat.startForegroundService(requireContext(), serviceIntent)

                // Show confirmation
                Snackbar.make(
                    binding.root,
                    "New Traffic Light added",
                    Snackbar.LENGTH_SHORT
                ).show()
            } else {
                Snackbar.make(
                    binding.root,
                    getString(R.string.max_traffic_lights_reached_snackbar),
                    Snackbar.LENGTH_LONG
                ).show()
            }
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

    private fun observeSnackbarEvents() {
        viewModel.snackbarEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUiControls(state: TrafficLightState) {
        blockListeners = true

        // Update mode selection
        binding.radioGroupMode.check(
            when (state.currentMode) {
                TrafficLightMode.MANUAL_CHANGE -> R.id.radio_manual
                TrafficLightMode.RESPONSIVE_CHANGE -> R.id.radio_responsive
                TrafficLightMode.TIMED_CHANGE -> R.id.radio_timed
                TrafficLightMode.DANGER_ALERT -> {
                    // Don't change selection during danger alert
                    when (state.previousMode) {
                        TrafficLightMode.MANUAL_CHANGE -> R.id.radio_manual
                        TrafficLightMode.RESPONSIVE_CHANGE -> R.id.radio_responsive
                        TrafficLightMode.TIMED_CHANGE -> R.id.radio_timed
                        else -> R.id.radio_manual
                    }
                }
            }
        )

        // Update switches
        binding.switchOrientation.isChecked = state.orientation == Orientation.HORIZONTAL
        binding.switchBlinking.isChecked = state.isBlinkingEnabled
        binding.switchShowTimeRemaining.isChecked = state.showTimeRemaining
        binding.switchShowTimeline.isChecked = state.showTimeline

        // Update visibility based on mode
        val isResponsive = state.currentMode == TrafficLightMode.RESPONSIVE_CHANGE
        val isTimed = state.currentMode == TrafficLightMode.TIMED_CHANGE
        val isManualOrResponsive = state.currentMode == TrafficLightMode.MANUAL_CHANGE || isResponsive

        binding.buttonAdjustValues.isVisible = isResponsive
        binding.buttonAddMessages.isVisible = isManualOrResponsive
        binding.buttonEditSequence.isVisible = isTimed
        binding.switchShowTimeRemaining.isVisible = isTimed
        binding.switchShowTimeline.isVisible = isTimed

        // Enable/disable responsive mode based on microphone availability
        binding.radioResponsive.isEnabled = state.isMicrophoneAvailable
        if (!state.isMicrophoneAvailable) {
            binding.radioResponsive.text = getString(R.string.setting_mode_responsive) + " " + getString(R.string.responsive_mode_no_mic)
        } else {
            binding.radioResponsive.text = getString(R.string.setting_mode_responsive)
        }

        // Update Add Another button state
        val prefs = context?.getSharedPreferences(
            TrafficLightActivity.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val activeCount = prefs?.getInt(TrafficLightActivity.KEY_ACTIVE_COUNT, 0) ?: 0
        binding.buttonAddAnother.isEnabled = activeCount < TrafficLightService.MAX_TRAFFIC_LIGHTS

        if (!binding.buttonAddAnother.isEnabled) {
            binding.buttonAddAnother.alpha = 0.5f
        } else {
            binding.buttonAddAnother.alpha = 1.0f
        }

        blockListeners = false
    }

    override fun onResume() {
        super.onResume()
        // Refresh state when returning from sub-dialogs
        updateUiControls(viewModel.uiState.value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}