// RandomizerSettingsFragment.kt
package com.example.purramid.thepurramid.randomizers.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CompoundButton
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels // Changed for potentially shared VM
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.data.db.RandomizerDao
import com.example.purramid.thepurramid.data.db.RandomizerInstanceEntity
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity
import com.example.purramid.thepurramid.randomizers.DiceSumResultType
import com.example.purramid.thepurramid.databinding.FragmentRandomizerSettingsBinding
import com.example.purramid.thepurramid.randomizers.RandomizerInstanceManager
import com.example.purramid.thepurramid.randomizers.RandomizerMode
import com.example.purramid.thepurramid.randomizers.RandomizersHostActivity
import com.example.purramid.thepurramid.randomizers.viewmodel.RandomizerSettingsViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class RandomizerSettingsFragment : Fragment() {

    private var _binding: FragmentRandomizerSettingsBinding? = null
    private val binding get() = _binding!!

    private val args: RandomizerSettingsFragmentArgs by navArgs()

    // Use activityViewModels if you intend this VM to be shared with DiceColorPickerDialogFragment
    // or other dialogs launched from settings. If not, viewModels() is fine.
    private val viewModel: RandomizerSettingsViewModel by activityViewModels()


    // For "Add Another Randomizer"
    @Inject
    lateinit var randomizerDao: RandomizerDao // Inject DAO for cloning settings
    private var currentInstanceIdForCloning: UUID? = null // Store the ID for cloning

    private var isUpdatingSwitches = false
    private var isUpdatingMode = false
    private var isUpdatingSlotsColumns = false
    private var isUpdatingDiceSettings = false

    companion object {
        private const val MAX_INSTANCES_GENERAL = 7 // Example limit
        private const val MAX_INSTANCES_DICE = 7    // Example limit for Dice (can be same or different)
        private const val TAG = "SettingsFragment"
    }

    private lateinit var sumResultsAdapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRandomizerSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentInstanceIdForCloning = try { UUID.fromString(args.instanceId) } catch (e: Exception) { null }

        setupSumResultsDropdown()
        setupListeners()
        observeViewModel()
        updateAddAnotherButtonState() // Initial state
    }

    private fun setupSumResultsDropdown() {
        // Get user-friendly names for the enum values
        val sumResultTypeNames = DiceSumResultType.values().map {
            when (it) {
                DiceSumResultType.INDIVIDUAL -> getString(R.string.dice_sum_type_individual) // TODO: Add string
                DiceSumResultType.SUM_TYPE -> getString(R.string.dice_sum_type_sum_type)   // TODO: Add string
                DiceSumResultType.SUM_TOTAL -> getString(R.string.dice_sum_type_sum_total)  // TODO: Add string
            }
        }.toTypedArray()

        sumResultsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, sumResultTypeNames)
        binding.autoCompleteTextViewSumResults.setAdapter(sumResultsAdapter)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupListeners() {
        binding.closeSettingsButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.buttonListEditor.setOnClickListener {
            try {
                // Pass null for listId to create a new list
                val action = RandomizerSettingsFragmentDirections.actionSettingsToListCreator(null)
                findNavController().navigate(action)
            } catch (e: Exception) {
                Log.e(TAG, "Navigation to List Creator failed.", e)
                Snackbar.make(requireView(), "Cannot open List Editor", Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.modeToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isUpdatingMode || !isChecked) return@addOnButtonCheckedListener
            val selectedMode = when (checkedId) {
                R.id.buttonModeSpin -> RandomizerMode.SPIN
                R.id.buttonModeSlots -> RandomizerMode.SLOTS
                R.id.buttonModeDice -> RandomizerMode.DICE
                R.id.buttonModeCoinFlip -> RandomizerMode.COIN_FLIP
                else -> viewModel.settings.value?.mode // Keep current if somehow no button is valid
            }
            selectedMode?.let { viewModel.updateMode(it) }
            updateAddAnotherButtonState() // Update button state when mode changes
        }

        binding.slotsNumColumnsToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isUpdatingSlotsColumns || !isChecked) return@addOnButtonCheckedListener
            val numColumns = when (checkedId) {
                R.id.buttonSlotsColumns3 -> 3
                R.id.buttonSlotsColumns5 -> 5
                else -> viewModel.settings.value?.numSlotsColumns ?: 3
            }
            viewModel.updateNumSlotsColumns(numColumns)
        }

        // Common Switches
        val commonSwitchListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isUpdatingSwitches) return@OnCheckedChangeListener
            when (buttonView.id) {
                R.id.switchIsAnnounceEnabled -> viewModel.updateIsAnnounceEnabled(isChecked)
                R.id.switchIsCelebrateEnabled -> viewModel.updateIsCelebrateEnabled(isChecked)
            }
        }
        binding.switchIsAnnounceEnabled.setOnCheckedChangeListener(commonSwitchListener)
        binding.switchIsCelebrateEnabled.setOnCheckedChangeListener(commonSwitchListener)

        // Spin Specific Switches
        val spinSwitchListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isUpdatingSwitches) return@OnCheckedChangeListener
            when (buttonView.id) {
                R.id.switchSpin -> viewModel.updateIsSpinEnabled(isChecked)
                R.id.switchIsSequenceEnabled -> viewModel.updateIsSequenceEnabled(isChecked)
            }
        }
        binding.switchSpin.setOnCheckedChangeListener(spinSwitchListener)
        binding.switchIsSequenceEnabled.setOnCheckedChangeListener(spinSwitchListener)

        // Dice Specific Switches
        val diceSwitchListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isUpdatingDiceSettings) return@OnCheckedChangeListener
            when (buttonView.id) {
                R.id.switchUseDicePips -> viewModel.updateUseDicePips(isChecked)
                R.id.switchIsPercentileDiceEnabled -> viewModel.updateIsPercentileDiceEnabled(isChecked)
                R.id.switchIsDiceAnimationEnabled -> viewModel.updateIsDiceAnimationEnabled(isChecked)
                R.id.switchIsDiceCritCelebrationEnabled -> viewModel.updateIsDiceCritCelebrationEnabled(isChecked)
            }
        }
        binding.switchUseDicePips.setOnCheckedChangeListener(diceSwitchListener)
        binding.switchIsPercentileDiceEnabled.setOnCheckedChangeListener(diceSwitchListener)
        binding.switchIsDiceAnimationEnabled.setOnCheckedChangeListener(diceSwitchListener)
        binding.switchIsDiceCritCelebrationEnabled.setOnCheckedChangeListener(diceSwitchListener)

        // Button to open Dice Pool Configuration
        binding.buttonDicePoolConfig.setOnClickListener {
            currentInstanceIdForCloning?.let { instanceId ->
                DicePoolDialogFragment.newInstance(instanceId).show(parentFragmentManager, DicePoolDialogFragment.TAG)
            } ?: Log.e(TAG, "Cannot open Dice Pool Config: Instance ID is null")
        }

        // *** Listener for Configure Dice Colors Button ***
        binding.buttonConfigureDiceColors.setOnClickListener {
            currentInstanceIdForCloning?.let { instanceId ->
                DiceColorPickerDialogFragment.newInstance(instanceId)
                    .show(parentFragmentManager, DiceColorPickerDialogFragment.TAG)
            } ?: Log.e(TAG, "Cannot open Dice Color Picker: Instance ID is null")
        }

        binding.buttonConfigureDiceModifiers.setOnClickListener {
            currentInstanceIdForCloning?.let { instanceId -> // Use the stored instanceId
                DiceModifiersDialogFragment.newInstance(instanceId)
                    .show(parentFragmentManager, DiceModifiersDialogFragment.TAG)
            } ?: Log.e(TAG, "Cannot open Dice Modifiers: Instance ID is null")
        }

        // Listener for the Sum Results Dropdown
        binding.autoCompleteTextViewSumResults.setOnItemClickListener { parent, _, position, _ ->
            if (isUpdatingSwitches) return@setOnItemClickListener // Use a general flag or a specific one

            val selectedEnum = DiceSumResultType.values()[position]
            viewModel.updateDiceSumResultType(selectedEnum)
        }

        // Add Another Randomizer Button
        binding.buttonAddAnotherRandomizer.setOnClickListener {
            handleAddAnotherInstance()
        }
    }

    private fun observeViewModel() {
        val lifecycleOwner = viewLifecycleOwner
        viewModel.settings.observe(lifecycleOwner) { settingsEntity ->
            isUpdatingSwitches = true
            isUpdatingMode = true
            isUpdatingSlotsColumns = true
            isUpdatingDiceSettings = true

            if (settingsEntity != null) {
                updateModeSelectionUI(settingsEntity.mode)

                // Common
                binding.switchIsAnnounceEnabled.isChecked = settingsEntity.isAnnounceEnabled
                binding.switchIsCelebrateEnabled.isChecked = settingsEntity.isCelebrateEnabled

                // Spin
                binding.switchSpin.isChecked = settingsEntity.isSpinEnabled
                binding.switchIsSequenceEnabled.isChecked = settingsEntity.isSequenceEnabled

                // Slots
                val slotsButtonToCheck = when (settingsEntity.numSlotsColumns) {
                    5 -> R.id.buttonSlotsColumns5
                    else -> R.id.buttonSlotsColumns3
                }
                if (binding.slotsNumColumnsToggleGroup.checkedButtonId != slotsButtonToCheck) {
                    binding.slotsNumColumnsToggleGroup.check(slotsButtonToCheck)
                }

                // Dice
                binding.switchUseDicePips.isChecked = settingsEntity.useDicePips
                binding.switchIsPercentileDiceEnabled.isChecked = settingsEntity.isPercentileDiceEnabled
                binding.switchIsDiceAnimationEnabled.isChecked = settingsEntity.isDiceAnimationEnabled
                binding.switchIsDiceCritCelebrationEnabled.isChecked = settingsEntity.isDiceCritCelebrationEnabled

                // *** Set Sum Results Dropdown Value ***
                val sumResultTypeNames = DiceSumResultType.values().map {
                    when (it) {
                        DiceSumResultType.INDIVIDUAL -> getString(R.string.dice_sum_type_individual)
                        DiceSumResultType.SUM_TYPE -> getString(R.string.dice_sum_type_sum_type)
                        DiceSumResultType.SUM_TOTAL -> getString(R.string.dice_sum_type_sum_total)
                    }
                }
                val currentSumTypeName = when (settingsEntity.diceSumResultType) {
                    DiceSumResultType.INDIVIDUAL -> getString(R.string.dice_sum_type_individual)
                    DiceSumResultType.SUM_TYPE -> getString(R.string.dice_sum_type_sum_type)
                    DiceSumResultType.SUM_TOTAL -> getString(R.string.dice_sum_type_sum_total)
                }

                // SetText on AutoCompleteTextView requires providing the filterable text, not the position
                binding.autoCompleteTextViewSumResults.setText(currentSumTypeName, false) // false to not filter

                updateControlEnablement(settingsEntity)
                binding.textViewSettingsPlaceholder.visibility = View.GONE
                enableAllControls(true)

            } else {
                binding.textViewSettingsPlaceholder.text = getString(R.string.error_settings_load_failed)
                binding.textViewSettingsPlaceholder.visibility = View.VISIBLE
                enableAllControls(false)
                binding.modeToggleGroup.clearChecked()
                binding.slotsNumColumnsToggleGroup.clearChecked()
                binding.autoCompleteTextViewSumResults.setText("", false) // Clear dropdown
            }
            isUpdatingSwitches = false
            isUpdatingMode = false
            isUpdatingSlotsColumns = false
            isUpdatingDiceSettings = false
        }

        viewModel.errorEvent.observe(lifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { resId ->
                val message = getString(resId)
                if (resId == R.string.error_settings_instance_id_failed) {
                    Log.e(TAG, "Critical Error: $message - Navigating back.")
                    findNavController().popBackStack()
                } else {
                    Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
                        .setAction(getString(R.string.snackbar_action_ok)) {}
                        .show()
                }
            }
        }
    }

    private fun enableAllControls(enabled: Boolean) {
        binding.modeToggleGroup.isEnabled = enabled
        binding.buttonListEditor.isEnabled = enabled
        binding.slotsNumColumnsToggleGroup.isEnabled = enabled

        binding.buttonDicePoolConfig.isEnabled = enabled
        binding.buttonConfigureDiceColors.isEnabled = enabled
        binding.switchUseDicePips.isEnabled = enabled
        binding.switchIsPercentileDiceEnabled.isEnabled = enabled
        binding.switchIsDiceAnimationEnabled.isEnabled = enabled
        binding.switchIsDiceCritCelebrationEnabled.isEnabled = enabled
        // Graph controls later

        binding.switchIsAnnounceEnabled.isEnabled = enabled
        binding.switchIsCelebrateEnabled.isEnabled = enabled

        binding.switchSpin.isEnabled = enabled
        binding.switchIsSequenceEnabled.isEnabled = enabled

        binding.buttonAddAnotherRandomizer.isEnabled = enabled // General enablement
        updateAddAnotherButtonState() // Then apply logic-based enablement
    }


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

        // Visibility Control
        val listModes = listOf(RandomizerMode.SPIN, RandomizerMode.SLOTS)
        val announceModes = listOf(RandomizerMode.SPIN, RandomizerMode.SLOTS, RandomizerMode.DICE)

        // Mode Specific Sections
        binding.buttonListEditor.isVisible = currentMode in listModes
        binding.slotsSettingsLayout.isVisible = currentMode == RandomizerMode.SLOTS
        binding.diceSettingsLayout.isVisible = currentMode == RandomizerMode.DICE
        binding.coinFlipSettingsLayout.isVisible = currentMode == RandomizerMode.COIN_FLIP
        binding.spinSpecificSettingsLayout.isVisible = currentMode == RandomizerMode.SPIN

        // Common Settings Visibility (adjust based on mode)
        binding.switchIsAnnounceEnabled.isVisible = currentMode in announceModes
        binding.switchIsCelebrateEnabled.isVisible = currentMode == RandomizerMode.SPIN // General celebrate for Spin
        // Dice has its own isDiceCritCelebrationEnabled

        updateAddAnotherButtonState() // Ensure button state reflects current mode
        viewModel.settings.value?.let { updateControlEnablement(it) }
    }

    private fun updateControlEnablement(settings: SpinSettingsEntity) {
        // This function adjusts enabled state based on OTHER settings values
        val isGraphOn = settings.graphDistributionType != GraphDistributionType.OFF
        // Announce enabled/disabled by sequence (for Spin) or graph (for Dice)
        val isAnnounceOnForDice = settings.isAnnounceEnabled && settings.mode == RandomizerMode.DICE

        // Dice specific announcement-dependent controls
        binding.menuSumResultsLayout.visibility = if (isAnnounceOnForDice) View.VISIBLE else View.GONE
        binding.buttonConfigureDiceModifiers.isEnabled = isAnnounceOnForDice // Assuming modifiers depend on announce
        binding.switchIsDiceCritCelebrationEnabled.visibility = if (isAnnounceOnForDice) View.VISIBLE else View.GONE
        binding.switchIsDiceCritCelebrationEnabled.isEnabled = isAnnounceOnForDice // Already checks announce internally in VM

        when (settings.mode) {
            RandomizerMode.SPIN -> {
                binding.switchIsAnnounceEnabled.isEnabled = !settings.isSequenceEnabled && binding.switchIsAnnounceEnabled.isVisible
                binding.switchIsCelebrateEnabled.isEnabled = !settings.isSequenceEnabled && settings.isAnnounceEnabled && binding.switchIsCelebrateEnabled.isVisible
            }
            RandomizerMode.DICE -> {
                binding.switchIsAnnounceEnabled.isEnabled = !isGraphOn && binding.switchIsAnnounceEnabled.isVisible
                binding.buttonDicePoolConfig.isEnabled = true // Always enabled for Dice mode
                binding.buttonConfigureDiceColors.isEnabled = true // Always enabled for Dice mode
                binding.switchUseDicePips.isEnabled = true
                binding.switchIsPercentileDiceEnabled.isEnabled = true
                binding.switchIsDiceAnimationEnabled.isEnabled = true
                binding.switchIsDiceCritCelebrationEnabled.isEnabled = settings.isAnnounceEnabled && binding.switchIsDiceCritCelebrationEnabled.isVisible
            }
            RandomizerMode.SLOTS -> {
                // Slots might have its own interdependencies for announce/celebrate if they get added
                binding.switchIsAnnounceEnabled.isEnabled = binding.switchIsAnnounceEnabled.isVisible // Default to visible state
            }
            else -> { // COIN_FLIP or others
                binding.switchIsAnnounceEnabled.isEnabled = binding.switchIsAnnounceEnabled.isVisible
            }
        }
    }

    private fun updateAddAnotherButtonState() {
        val currentMode = viewModel.settings.value?.mode
        val currentInstanceCount = RandomizerInstanceManager.getActiveInstanceCount()
        val limit = if (currentMode == RandomizerMode.DICE) MAX_INSTANCES_DICE else MAX_INSTANCES_GENERAL
        binding.buttonAddAnotherRandomizer.isEnabled = currentInstanceCount < limit
    }

    private fun handleAddAnotherInstance() {
        val currentMode = viewModel.settings.value?.mode
        val currentInstanceCount = RandomizerInstanceManager.getActiveInstanceCount()
        val limit = if (currentMode == RandomizerMode.DICE) MAX_INSTANCES_DICE else MAX_INSTANCES_GENERAL

        if (currentInstanceCount >= limit) {
            val messageResId = if (currentMode == RandomizerMode.DICE) {
                R.string.max_randomizers_dice_reached_snackbar
            } else {
                R.string.max_randomizers_general_reached_snackbar
            }
            Snackbar.make(binding.root, messageResId, Snackbar.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch { // Coroutine for DB operations
            val currentSettings = currentInstanceIdForCloning?.let { randomizerDao.getSettingsForInstance(it) }
            if (currentSettings == null) {
                Log.e(TAG, "Cannot clone settings, current settings not found for ID: $currentInstanceIdForCloning")
                Snackbar.make(binding.root, "Error: Could not clone current settings.", Snackbar.LENGTH_SHORT).show()
                return@launch
            }

            val newInstanceId = UUID.randomUUID()
            val newSettings = currentSettings.copy(
                instanceId = newInstanceId,
                // Optionally reset some specific fields for a new instance if desired
                // e.g., currentListId = null if cloning from Spin to a new mode
            )
            val newInstanceEntity = RandomizerInstanceEntity(instanceId = newInstanceId)

            try {
                randomizerDao.saveSettings(newSettings)
                randomizerDao.saveInstance(newInstanceEntity)
                Log.d(TAG, "Cloned settings and created new instance: $newInstanceId")

                // Launch new Activity instance
                val intent = Intent(requireActivity(), RandomizersHostActivity::class.java).apply {
                    putExtra(RandomizersHostActivity.EXTRA_INSTANCE_ID, newInstanceId.toString())
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Important for launching new activity from non-activity context if needed
                }
                requireActivity().startActivity(intent)

            } catch (e: Exception) {
                Log.e(TAG, "Error saving new cloned instance or launching activity", e)
                Snackbar.make(binding.root, "Error creating new randomizer window.", Snackbar.LENGTH_LONG).show()
            }
        }
    }
}
