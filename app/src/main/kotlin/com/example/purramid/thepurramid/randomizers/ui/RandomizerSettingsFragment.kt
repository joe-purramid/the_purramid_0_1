package com.example.purramid.thepurramid.randomizers.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.core.view.isVisible // Import isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs // Required for Safe Args
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.FragmentRandomizerSettingsBinding // Use Fragment binding
import com.example.purramid.thepurramid.randomizers.RandomizerMode
import com.example.purramid.thepurramid.randomizers.viewmodel.RandomizerSettingsViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID

@AndroidEntryPoint
class RandomizerSettingsFragment : Fragment() {

    private var _binding: FragmentRandomizerSettingsBinding? = null
    private val binding get() = _binding!! // Use Fragment binding

    // Use Safe Args delegate to get arguments defined in NavGraph
    // Ensure 'instanceId' argument exists in randomizerSettingsFragment definition in nav graph
    private val args: RandomizerSettingsFragmentArgs by navArgs()

    // Get ViewModel scoped to this Fragment
    private val viewModel: RandomizerSettingsViewModel by viewModels()

    private var isUpdatingSwitches = false
    private var isUpdatingMode = false
    private var isUpdatingSlotsColumns = false // Add flag for column toggle
    private var isUpdatingDiceSettings = false // Flag for Dice switches

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRandomizerSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Log.d("SettingsFragment", "Received instanceId Arg: ${args.instanceId}")
        setupListeners()
        observeViewModel()

        // Initial setup: Load settings based on the passed instanceId
        args.instanceId?.let { uuidString ->
            try {
                val uuid = UUID.fromString(uuidString)
                // ViewModel now handles loading via SavedStateHandle,
                // so just observe it. If loading failed, VM handles error state.
            } catch (e: IllegalArgumentException) {
                Log.e("SettingsFragment", "Invalid UUID format in arguments: $uuidString", e)
                // Show critical error and potentially navigate back if VM hasn't already
                Snackbar.make(requireView(), R.string.error_settings_instance_id_failed, Snackbar.LENGTH_LONG).show()
                findNavController().popBackStack()
            }
        } ?: run {
            Log.e("SettingsFragment", "Instance ID argument is null")
            Snackbar.make(requireView(), R.string.error_settings_instance_id_failed, Snackbar.LENGTH_LONG).show()
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Avoid memory leaks
    }

    private fun setupListeners() {
        binding.closeSettingsButton.setOnClickListener {
            findNavController().popBackStack() // Navigate back
        }

        binding.buttonListEditor.setOnClickListener {
            try {
                // Navigate using action defined in nav graph
                findNavController().navigate(R.id.action_settings_to_list_editor)
            } catch (e: Exception) {
                Log.e("SettingsFragment", "Navigation to List Editor failed.", e)
                Snackbar.make(requireView(), "Cannot open List Editor", Snackbar.LENGTH_SHORT).show()
            }
        }

        // Switch Listeners
        val switchListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isUpdatingSwitches) return@OnCheckedChangeListener
            when (buttonView.id) {
                R.id.switchSpin -> viewModel.updateBooleanSetting { it.copy(isSpinEnabled = isChecked) }
                R.id.switchIsAnnounceEnabled -> viewModel.updateBooleanSetting { it.copy(isAnnounceEnabled = isChecked) }
                R.id.switchIsCelebrateEnabled -> viewModel.updateBooleanSetting { it.copy(isCelebrateEnabled = isChecked) }
                R.id.switchIsSequenceEnabled -> viewModel.updateBooleanSetting { it.copy(isSequenceEnabled = isChecked) }
            }
        }
        binding.switchSpin.setOnCheckedChangeListener(switchListener)
        binding.switchAnnounce.setOnCheckedChangeListener(switchListener)
        binding.switchCelebrate.setOnCheckedChangeListener(switchListener)
        binding.switchSequence.setOnCheckedChangeListener(switchListener)

        // Mode Toggle Group Listener
        binding.modeToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isUpdatingMode || !isChecked) return@addOnButtonCheckedListener
            val selectedMode = when (checkedId) {
                R.id.buttonModeSpin -> RandomizerMode.SPIN
                R.id.buttonModeSlots -> RandomizerMode.SLOTS
                R.id.buttonModeDice -> RandomizerMode.DICE
                R.id.buttonModeCoinFlip -> RandomizerMode.COIN_FLIP
                else -> null
            }
            selectedMode?.let { viewModel.updateMode(it) }
        }

        // Slots Columns Listener
        binding.slotsNumColumnsToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isUpdatingSlotsColumns || !isChecked) return@addOnButtonCheckedListener // Prevent loops

            val numColumns = when (checkedId) {
                R.id.buttonSlotsColumns3 -> 3
                R.id.buttonSlotsColumns5 -> 5
                else -> viewModel.settings.value?.numSlotsColumns ?: 3 // Default to current or 3
            }
            viewModel.updateNumSlotsColumns(numColumns)
        }

        // --- Listener for Common Switches ---
        // Apply to switches that might be shared or need general handling
        val commonSwitchListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isUpdatingSwitches) return@OnCheckedChangeListener
            when (buttonView.id) {
                // Add common switches here if any in the future
                R.id.switchIsAnnounceEnabled -> viewModel.updateIsAnnounceEnabled(isChecked)
                R.id.switchIsCelebrateEnabled -> viewModel.updateIsCelebrateEnabled(isChecked) // General celebration (Spin?)
                // Spin specific handled separately for clarity now
            }
        }
        binding.switchIsAnnounceEnabled.setOnCheckedChangeListener(commonSwitchListener)
        binding.switchIsCelebrateEnabled.setOnCheckedChangeListener(commonSwitchListener)


        // --- Listener for Spin-Specific Switches ---
        val spinSwitchListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isUpdatingSwitches) return@OnCheckedChangeListener // Reuse flag or use specific if needed
            when (buttonView.id) {
                R.id.switchSpin -> viewModel.updateIsSpinEnabled(isChecked)
                R.id.switchIsSequenceEnabled -> viewModel.updateIsSequenceEnabled(isChecked)
            }
        }
        binding.switchSpin.setOnCheckedChangeListener(spinSwitchListener)
        binding.switchIsSequenceEnabled.setOnCheckedChangeListener(spinSwitchListener)


        // --- Listener for Dice-Specific Switches ---
        val diceSwitchListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isUpdatingDiceSettings) return@OnCheckedChangeListener
            when (buttonView.id) {
                R.id.switchUseDicePips -> viewModel.updateUseDicePips(isChecked)
                R.id.switchIsPercentileDiceEnabled -> viewModel.updateIsPercentileDiceEnabled(isChecked)
                R.id.switchIsDiceAnimationEnabled -> viewModel.updateIsDiceAnimationEnabled(isChecked)
                R.id.switchIsDiceCritCelebrationEnabled -> viewModel.updateIsDiceCritCelebrationEnabled(isChecked)
                // Add listeners for Graph settings later
            }
        }
        binding.switchUseDicePips.setOnCheckedChangeListener(diceSwitchListener)
        binding.switchIsPercentileDiceEnabled.setOnCheckedChangeListener(diceSwitchListener)
        binding.switchIsDiceAnimationEnabled.setOnCheckedChangeListener(diceSwitchListener)
        binding.switchIsDiceCritCelebrationEnabled.setOnCheckedChangeListener(diceSwitchListener)
    }

    private fun observeViewModel() {
        val lifecycleOwner = viewLifecycleOwner

        // Observe Settings LiveData
        viewModel.settings.observe(lifecycleOwner) { settingsEntity ->
            isUpdatingSwitches = true
            isUpdatingMode = true
            isUpdatingSlotsColumns = true // Set flag
            isUpdatingDiceSettings = true

            if (settingsEntity != null) {
                // Update UI based on loaded settings
                updateModeSelectionUI(settingsEntity.mode) // Handles mode button and visibility of sections

                // Update Slots specific UI
                val slotsButtonToCheck = when (settingsEntity.numSlotsColumns) {
                    5 -> R.id.buttonSlotsColumns5
                    else -> R.id.buttonSlotsColumns3
                }
                if (binding.slotsNumColumnsToggleGroup.checkedButtonId != slotsButtonToCheck) {
                    binding.slotsNumColumnsToggleGroup.check(slotsButtonToCheck)
                }

                // Update Dice specific UI
                binding.switchUseDicePips.isChecked = settingsEntity.useDicePips
                binding.switchIsPercentileDiceEnabled.isChecked = settingsEntity.isPercentileDiceEnabled
                binding.switchIsDiceAnimationEnabled.isChecked = settingsEntity.isDiceAnimationEnabled
                binding.switchIsDiceCritCelebrationEnabled.isChecked = settingsEntity.isDiceCritCelebrationEnabled
                // Update Graph settings UI later

                // Update Common UI
                binding.switchIsAnnounceEnabled.isChecked = settingsEntity.isAnnounceEnabled
                binding.switchIsCelebrateEnabled.isChecked = settingsEntity.isCelebrateEnabled // General celebration

                // Update Spin Specific UI
                binding.switchSpin.isChecked = settingsEntity.isSpinEnabled
                binding.switchIsSequenceEnabled.isChecked = settingsEntity.isSequenceEnabled

                // Handle interdependencies affecting enabled state (example)
                binding.switchIsCelebrateEnabled.isEnabled = settingsEntity.isAnnounceEnabled && binding.switchIsCelebrateEnabled.isVisible // General celebration requires announce AND visibility
                binding.switchIsDiceCritCelebrationEnabled.isEnabled = settingsEntity.isAnnounceEnabled && binding.switchIsDiceCritCelebrationEnabled.isVisible // Dice crit celebration requires announce AND visibility
                // Add logic for Graph/Announcement interdependency later

                binding.textViewSettingsPlaceholder.visibility = View.GONE
                // Ensure controls are generally enabled
                enableAllControls(true)

            } else {
                // Handle null settings (error case)
                binding.textViewSettingsPlaceholder.text = getString(R.string.error_settings_load_failed)
                binding.textViewSettingsPlaceholder.visibility = View.VISIBLE
                enableAllControls(false) // Disable all controls
                binding.modeToggleGroup.clearChecked() // Clear mode selection on error
                binding.slotsNumColumnsToggleGroup.clearChecked() // Clear slots selection
            }
            // Reset all update flags
            isUpdatingSwitches = false
            isUpdatingMode = false
            isUpdatingSlotsColumns = false
            isUpdatingDiceSettings = false
        }

        // Observe Error Event
        viewModel.errorEvent.observe(lifecycleOwner) { errorResId ->
            errorResId?.let { resId ->
                val message = getString(resId)
                if (resId == R.string.error_settings_instance_id_failed) {
                    Log.e("SettingsFragment", "Critical Error: $message - Navigating back.")
                    // Don't show Toast/Snackbar if navigating back immediately
                    findNavController().popBackStack()
                } else {
                    Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
                        .setAction(getString(R.string.snackbar_action_ok)) {}
                        .show()
                }
                viewModel.clearErrorEvent()
            }
        }
    }

    // Helper to enable/disable all controls during load/error
    private fun enableAllControls(enabled: Boolean) {
        binding.modeToggleGroup.isEnabled = enabled
        binding.buttonListEditor.isEnabled = enabled
        binding.slotsNumColumnsToggleGroup.isEnabled = enabled
        binding.switchUseDicePips.isEnabled = enabled
        binding.switchIsPercentileDiceEnabled.isEnabled = enabled
        binding.switchIsDiceAnimationEnabled.isEnabled = enabled
        binding.switchIsDiceCritCelebrationEnabled.isEnabled = enabled
        binding.switchIsAnnounceEnabled.isEnabled = enabled
        binding.switchIsCelebrateEnabled.isEnabled = enabled
        binding.switchSpin.isEnabled = enabled
        binding.switchIsSequenceEnabled.isEnabled = enabled
        // Add graph controls later
    }

    // Update Mode Selection UI and visibility/enablement of related controls
    private fun updateModeSelectionUI(currentMode: RandomizerMode) {
        val buttonIdToCheck = when (currentMode) {
            RandomizerMode.SPIN -> R.id.buttonModeSpin
            RandomizerMode.SLOTS -> R.id.buttonModeSlots
            RandomizerMode.DICE -> R.id.buttonModeDice
            RandomizerMode.COIN_FLIP -> R.id.buttonModeCoinFlip
        }
        if (binding.modeToggleGroup.checkedButtonId != buttonIdToCheck) {
            binding.modeToggleGroup.check(buttonIdToCheck)
        }

        // --- Visibility Control ---
        val listModes = listOf(RandomizerMode.SPIN, RandomizerMode.SLOTS, RandomizerMode.DICE, RandomizerMode.COIN_FLIP)
        val announceModes = listOf(RandomizerMode.SPIN, RandomizerMode.SLOTS, RandomizerMode.DICE)

        // Mode Specific Sections
        binding.buttonListEditor.isVisible = currentMode in listModes
        binding.slotsSettingsLayout.isVisible = currentMode == RandomizerMode.SLOTS
        binding.diceSettingsLayout.isVisible = currentMode == RandomizerMode.DICE
        binding.diceSettingsLayout.isVisible = currentMode == RandomizerMode.COIN_FLIP

        // Common Settings Visibility
        binding.switchIsAnnounceEnabled.isVisible = currentMode in announceModes
        binding.switchIsCelebrateEnabled.isVisible = currentMode == RandomizerMode.SPIN // Only show general celebrate for Spin?

        // Spin Specific Settings Visibility
        binding.switchSpin.isVisible = currentMode == RandomizerMode.SPIN
        binding.switchIsSequenceEnabled.isVisible = currentMode == RandomizerMode.SPIN

        // --- Adjust Enablement Based on Visibility and Dependencies ---
        // (This part needs careful implementation based on final logic)
        val settings = viewModel.settings.value // Get current settings for dependency checks
        val announceEnabled = settings?.isAnnounceEnabled ?: false
        val sequenceEnabled = settings?.isSequenceEnabled ?: false
        // val graphEnabled = settings?.graphDistributionType != GraphDistributionType.OFF // Check graph state later

        // Example dependencies:
        binding.switchIsCelebrateEnabled.isEnabled = announceEnabled && binding.switchIsCelebrateEnabled.isVisible
        binding.switchIsDiceCritCelebrationEnabled.isEnabled = announceEnabled && binding.switchIsDiceCritCelebrationEnabled.isVisible

        // Spin-specific dependencies
        if (currentMode == RandomizerMode.SPIN) {
            binding.switchIsAnnounceEnabled.isEnabled = !sequenceEnabled && binding.switchIsAnnounceEnabled.isVisible
            binding.switchIsCelebrateEnabled.isEnabled = !sequenceEnabled && announceEnabled && binding.switchIsCelebrateEnabled.isVisible
        }

        // Add logic for Graph disabling Announce later
    }
}