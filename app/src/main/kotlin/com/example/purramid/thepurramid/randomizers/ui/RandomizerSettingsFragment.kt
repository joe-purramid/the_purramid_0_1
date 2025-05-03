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
import com.google.android.material.button.MaterialButtonToggleGroup
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

    private var isUpdatingSwitches = false
    private var isUpdatingMode = false
    private var isUpdatingNumColumns = false // Add flag for column toggle

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
                R.id.switchAnnounce -> viewModel.updateBooleanSetting { it.copy(isAnnounceEnabled = isChecked) }
                R.id.switchCelebrate -> viewModel.updateBooleanSetting { it.copy(isCelebrateEnabled = isChecked) }
                R.id.switchSequence -> viewModel.updateBooleanSetting { it.copy(isSequenceEnabled = isChecked) }
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

        binding.slotsNumColumnsToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isUpdatingNumColumns || !isChecked) return@addOnButtonCheckedListener // Prevent loops

            val numColumns = when (checkedId) {
                R.id.buttonSlotsColumns3 -> 3
                R.id.buttonSlotsColumns5 -> 5
                else -> viewModel.settings.value?.numSlotsColumns ?: 3 // Default to current or 3
            }
            viewModel.updateNumSlotsColumns(numColumns)
        }
    }

    private fun observeViewModel() {
        val lifecycleOwner = viewLifecycleOwner

        // Observe Settings LiveData
        viewModel.settings.observe(lifecycleOwner) { settingsEntity ->
            isUpdatingSwitches = true
            isUpdatingMode = true
            isUpdatingNumColumns = true // Set flag

            if (settingsEntity != null) {
                binding.switchSpin.isChecked = settingsEntity.isSpinEnabled
                binding.switchAnnounce.isChecked = settingsEntity.isAnnounceEnabled
                binding.switchCelebrate.isChecked = settingsEntity.isCelebrateEnabled
                binding.switchSequence.isChecked = settingsEntity.isSequenceEnabled

                updateModeSelectionUI(settingsEntity.mode) // Update mode selector visual state

                // *** UPDATE Number of Columns Toggle ***
                val buttonToCheck = when (settingsEntity.numSlotsColumns) {
                    5 -> R.id.buttonSlotsColumns5
                    else -> R.id.buttonSlotsColumns3 // Default to 3
                }
                if (binding.slotsNumColumnsToggleGroup.checkedButtonId != buttonToCheck) {
                    binding.slotsNumColumnsToggleGroup.check(buttonToCheck)
                }

                // Hide placeholder text
                binding.textViewSettingsPlaceholder.visibility = View.GONE

            } else {
                // Show placeholder/error text and disable controls
                binding.textViewSettingsPlaceholder.text = getString(R.string.error_settings_load_failed)
                binding.textViewSettingsPlaceholder.visibility = View.VISIBLE
                binding.modeToggleGroup.isEnabled = false
                binding.switchSpin.isEnabled = false
                binding.switchAnnounce.isEnabled = false
                binding.switchCelebrate.isEnabled = false
                binding.switchSequence.isEnabled = false
                binding.buttonListEditor.isEnabled = false
                binding.slotsNumColumnsToggleGroup.isEnabled = false
                // Also clear toggle group selection visually on error
                binding.modeToggleGroup.clearChecked()
            }
            isUpdatingSwitches = false
            isUpdatingMode = false
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

        // Visibility/Enablement based on mode
        val listModes = listOf(RandomizerMode.SPIN, RandomizerMode.SLOTS, RandomizerMode.DICE, RandomizerMode.COIN_FLIP)
        val announceModes = listOf(RandomizerMode.SPIN, RandomizerMode.SLOTS)
        val isSpinMode = currentMode == RandomizerMode.SPIN
        val isSlotsMode = currentMode == RandomizerMode.SLOTS
        val isDiceMode = currentMode == RandomizerMode.DICE
        val isCoinFlipMode = currentMode == RandomizerMode.COIN_FLIP

        binding.buttonListEditor.isVisible = currentMode in listModes // Use isVisible
        binding.switchSpin.isVisible = isSpinMode
        binding.switchAnnounce.isVisible = currentMode in announceModes
        binding.switchCelebrate.isVisible = isSpinMode
        binding.switchSequence.isVisible = isSpinMode

        // *** SHOW/HIDE SLOTS SETTINGS ***
        binding.slotsSettingsLayout.isVisible = isSlotsMode // Show layout only for Slots

        // Adjust enablement based on other switches (only relevant if visible)
        val sequenceEnabled = viewModel.settings.value?.isSequenceEnabled ?: false
        val announceChecked = viewModel.settings.value?.isAnnounceEnabled ?: false

        // Announce is disabled if sequence is ON (applies only if Announce is visible)
        binding.switchAnnounce.isEnabled = !sequenceEnabled && binding.switchAnnounce.isVisible

        // Celebrate is disabled if sequence is ON OR announce is OFF (applies only if Celebrate is visible)
        binding.switchCelebrate.isEnabled = !sequenceEnabled && announceChecked && binding.switchCelebrate.isVisible
    }
}