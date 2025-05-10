// src/main/kotlin/com/example/purramid/thepurramid/randomizers/ui/RandomizerSettingsFragment.kt
package com.example.purramid.thepurramid.randomizers.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope // Added for coroutines
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.data.db.RandomizerDao // To fetch current settings for cloning
import com.example.purramid.thepurramid.data.db.RandomizerInstanceEntity
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity
import com.example.purramid.thepurramid.databinding.FragmentRandomizerSettingsBinding
import com.example.purramid.thepurramid.randomizers.RandomizerMode
import com.example.purramid.thepurramid.randomizers.RandomizersHostActivity // To launch new instance
import com.example.purramid.thepurramid.randomizers.RandomizerInstanceManager // To check active count
import com.example.purramid.thepurramid.randomizers.viewmodel.RandomizerSettingsViewModel
import com.example.purramid.thepurramid.randomizers.viewmodel.RandomizerViewModel // For KEY_INSTANCE_ID
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject // Added for DAO injection

@AndroidEntryPoint
class RandomizerSettingsFragment : Fragment() {

    private var _binding: FragmentRandomizerSettingsBinding? = null
    private val binding get() = _binding!!

    private val args: RandomizerSettingsFragmentArgs by navArgs()
    private val viewModel: RandomizerSettingsViewModel by viewModels()

    @Inject // Inject DAO for cloning settings
    lateinit var randomizerDao: RandomizerDao

    private var isUpdatingSwitches = false
    private var isUpdatingMode = false
    private var isUpdatingSlotsColumns = false
    private var isUpdatingDiceSettings = false

    private var currentInstanceId: UUID? = null // To store the ID of the current settings window

    companion object {
        private const val TAG = "RandomizerSettingsFrag"
        private const val MAX_INSTANCES_GENERAL = 4
        private const val MAX_INSTANCES_DICE = 7
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRandomizerSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        args.instanceId?.let { uuidString ->
            try {
                currentInstanceId = UUID.fromString(uuidString)
                // ViewModel handles loading based on this ID via SavedStateHandle
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid UUID format in arguments: $uuidString", e)
                Snackbar.make(requireView(), R.string.error_settings_instance_id_failed, Snackbar.LENGTH_LONG).show()
                findNavController().popBackStack() // Go back if ID is invalid
                return
            }
        } ?: run {
            Log.e(TAG, "Instance ID argument is null in RandomizerSettingsFragment")
            Snackbar.make(requireView(), R.string.error_settings_instance_id_failed, Snackbar.LENGTH_LONG).show()
            findNavController().popBackStack()
            return
        }

        setupListeners()
        observeViewModel()
        updateAddAnotherButtonState() // Initial state
    }

    private fun updateAddAnotherButtonState() {
        val activeCount = RandomizerInstanceManager.getActiveInstanceCount()
        // Determine current mode to apply correct limit
        val currentMode = viewModel.settings.value?.mode ?: RandomizerMode.SPIN // Default to SPIN if not loaded
        val limit = if (currentMode == RandomizerMode.DICE) MAX_INSTANCES_DICE else MAX_INSTANCES_GENERAL

        binding.buttonAddAnotherRandomizer.isEnabled = activeCount < limit
        binding.buttonAddAnotherRandomizer.alpha = if (activeCount < limit) 1.0f else 0.5f
    }

    private fun setupListeners() {
        binding.closeSettingsButton.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.buttonListEditor.setOnClickListener {
            try {
                // Assuming listId is not strictly needed by ListCreatorFragment if it creates new or loads last edited.
                // If ListCreatorFragment *always* needs a listId (even for a new list),
                // then we might need to pass the current listId from settings.
                // Or ListCreatorFragment creates a new list if no ID is passed.
                val currentListIdToEdit = viewModel.settings.value?.currentListId?.toString()
                val action = RandomizerSettingsFragmentDirections.actionSettingsToListEditor(currentListIdToEdit)
                findNavController().navigate(action)
            } catch (e: Exception) {
                Log.e(TAG, "Navigation to List Editor failed.", e)
                Snackbar.make(requireView(), "Cannot open List Editor", Snackbar.LENGTH_SHORT).show()
            }
        }

        binding.buttonAddAnotherRandomizer.setOnClickListener { // Changed ID
            handleAddAnotherInstance()
        }

        // Switch Listeners (common and mode-specific)
        val commonSwitchListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isUpdatingSwitches) return@OnCheckedChangeListener
            when (buttonView.id) {
                R.id.switchIsAnnounceEnabled -> viewModel.updateIsAnnounceEnabled(isChecked)
                R.id.switchIsCelebrateEnabled -> viewModel.updateIsCelebrateEnabled(isChecked)
            }
        }
        binding.switchIsAnnounceEnabled.setOnCheckedChangeListener(commonSwitchListener)
        binding.switchIsCelebrateEnabled.setOnCheckedChangeListener(commonSwitchListener)

        val spinSwitchListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isUpdatingSwitches) return@OnCheckedChangeListener
            when (buttonView.id) {
                R.id.switchSpin -> viewModel.updateIsSpinEnabled(isChecked)
                R.id.switchIsSequenceEnabled -> viewModel.updateIsSequenceEnabled(isChecked)
            }
        }
        binding.switchSpin.setOnCheckedChangeListener(spinSwitchListener)
        binding.switchIsSequenceEnabled.setOnCheckedChangeListener(spinSwitchListener)


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


        // Mode Toggle Group Listener
        binding.modeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isUpdatingMode || !isChecked) return@addOnButtonCheckedListener
            val selectedMode = when (checkedId) {
                R.id.buttonModeSpin -> RandomizerMode.SPIN
                R.id.buttonModeSlots -> RandomizerMode.SLOTS
                R.id.buttonModeDice -> RandomizerMode.DICE
                R.id.buttonModeCoinFlip -> RandomizerMode.COIN_FLIP
                else -> viewModel.settings.value?.mode // Should not happen with selectionRequired
            }
            selectedMode?.let {
                viewModel.updateMode(it)
                updateAddAnotherButtonState() // Update button based on new mode's limit
            }
        }

        // Slots Columns Listener
        binding.slotsNumColumnsToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isUpdatingSlotsColumns || !isChecked) return@addOnButtonCheckedListener
            val numColumns = when (checkedId) {
                R.id.buttonSlotsColumns3 -> 3
                R.id.buttonSlotsColumns5 -> 5
                else -> viewModel.settings.value?.numSlotsColumns ?: 3
            }
            viewModel.updateNumSlotsColumns(numColumns)
        }

        // Dice Pool Button Listener
        binding.buttonDicePoolConfig.setOnClickListener {
            currentInstanceId?.let { id ->
                DicePoolDialogFragment.newInstance(id)
                    .show(parentFragmentManager, DicePoolDialogFragment.TAG)
            } ?: Snackbar.make(binding.root, "Cannot open dice pool: Invalid instance.", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun handleAddAnotherInstance() {
        val activeCount = RandomizerInstanceManager.getActiveInstanceCount()
        val currentMode = viewModel.settings.value?.mode ?: RandomizerMode.SPIN
        val limit = if (currentMode == RandomizerMode.DICE) MAX_INSTANCES_DICE else MAX_INSTANCES_GENERAL

        if (activeCount >= limit) {
            val message = if (currentMode == RandomizerMode.DICE) {
                getString(R.string.max_randomizers_dice_reached_snackbar, MAX_INSTANCES_DICE)
            } else {
                getString(R.string.max_randomizers_general_reached_snackbar, MAX_INSTANCES_GENERAL)
            }
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
            return
        }

        currentInstanceId?.let { instanceIdToCloneFrom ->
            binding.buttonAddAnotherRandomizer.isEnabled = false // Disable button temporarily
            lifecycleScope.launch(Dispatchers.IO) {
                val settingsToClone = randomizerDao.getSettingsForInstance(instanceIdToCloneFrom)
                if (settingsToClone != null) {
                    val newInstanceId = UUID.randomUUID()
                    val clonedSettings = settingsToClone.copy(instanceId = newInstanceId)

                    randomizerDao.saveSettings(clonedSettings)
                    randomizerDao.saveInstance(RandomizerInstanceEntity(instanceId = newInstanceId))

                    withContext(Dispatchers.Main) {
                        val intent = Intent(requireContext(), RandomizersHostActivity::class.java).apply {
                            putExtra(RandomizersHostActivity.EXTRA_INSTANCE_ID, newInstanceId.toString())
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Ensures it can be started from non-Activity context
                        }
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start new RandomizersHostActivity", e)
                            // Rollback DB changes if activity launch fails? Or leave them for next app launch?
                            // For now, log and show error.
                            Snackbar.make(binding.root, "Failed to open new Randomizer window.", Snackbar.LENGTH_LONG).show()
                            // Re-enable button if launch failed
                            updateAddAnotherButtonState()
                        }
                        // No need to update button state here if activity will be replaced or closed
                        // It will update on next onViewCreated if user navigates back.
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Snackbar.make(binding.root, "Could not clone current settings to create new instance.", Snackbar.LENGTH_LONG).show()
                        updateAddAnotherButtonState() // Re-enable button
                    }
                }
            }
        } ?: run {
            Snackbar.make(binding.root, "Cannot add another: Current instance ID is missing.", Snackbar.LENGTH_LONG).show()
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

                val slotsButtonToCheck = when (settingsEntity.numSlotsColumns) {
                    5 -> R.id.buttonSlotsColumns5
                    else -> R.id.buttonSlotsColumns3
                }
                if (binding.slotsNumColumnsToggleGroup.checkedButtonId != slotsButtonToCheck) {
                    binding.slotsNumColumnsToggleGroup.check(slotsButtonToCheck)
                }

                binding.switchUseDicePips.isChecked = settingsEntity.useDicePips
                binding.switchIsPercentileDiceEnabled.isChecked = settingsEntity.isPercentileDiceEnabled
                binding.switchIsDiceAnimationEnabled.isChecked = settingsEntity.isDiceAnimationEnabled
                binding.switchIsDiceCritCelebrationEnabled.isChecked = settingsEntity.isDiceCritCelebrationEnabled

                binding.switchIsAnnounceEnabled.isChecked = settingsEntity.isAnnounceEnabled
                binding.switchIsCelebrateEnabled.isChecked = settingsEntity.isCelebrateEnabled

                binding.switchSpin.isChecked = settingsEntity.isSpinEnabled
                binding.switchIsSequenceEnabled.isChecked = settingsEntity.isSequenceEnabled

                // Visibility & Enablement logic based on mode and dependencies
                updateControlEnablement(settingsEntity)

                binding.textViewSettingsPlaceholder.visibility = View.GONE
                enableAllPrimaryControls(true)
            } else {
                binding.textViewSettingsPlaceholder.text = getString(R.string.error_settings_load_failed)
                binding.textViewSettingsPlaceholder.visibility = View.VISIBLE
                enableAllPrimaryControls(false)
                binding.modeToggleGroup.clearChecked()
                binding.slotsNumColumnsToggleGroup.clearChecked()
            }
            updateAddAnotherButtonState() // Update based on potentially new mode and active count

            isUpdatingSwitches = false
            isUpdatingMode = false
            isUpdatingSlotsColumns = false
            isUpdatingDiceSettings = false
        }

        viewModel.errorEvent.observe(lifecycleOwner) { event ->
            event?.getContentIfNotHandled()?.let { errorMsg -> // Assuming Event<String>
                Snackbar.make(requireView(), errorMsg, Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.snackbar_action_ok)) {}
                    .show()
            }
        }
    }

    private fun enableAllPrimaryControls(enabled: Boolean) {
        binding.modeToggleGroup.isEnabled = enabled
        binding.buttonListEditor.isEnabled = enabled
        // Further enablement is handled by updateControlEnablement
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

        // Section Visibility
        val listModes = listOf(RandomizerMode.SPIN, RandomizerMode.SLOTS) // Dice/Coin don't use SpinListEntity for main items
        val announceModes = listOf(RandomizerMode.SPIN, RandomizerMode.SLOTS, RandomizerMode.DICE)

        binding.buttonListEditor.isVisible = currentMode in listModes
        binding.slotsSettingsLayout.isVisible = currentMode == RandomizerMode.SLOTS
        binding.diceSettingsLayout.isVisible = currentMode == RandomizerMode.DICE
        binding.coinFlipSettingsLayout.isVisible = currentMode == RandomizerMode.COIN_FLIP // Assuming you add this layout

        // Common Settings Visibility
        binding.switchIsAnnounceEnabled.isVisible = currentMode in announceModes
        binding.switchIsCelebrateEnabled.isVisible = currentMode == RandomizerMode.SPIN // General celebrate for Spin

        // Spin Specific Settings Visibility
        binding.spinSpecificSettingsLayout.isVisible = currentMode == RandomizerMode.SPIN
    }


    private fun updateControlEnablement(settings: SpinSettingsEntity) {
        val announceEnabled = settings.isAnnounceEnabled
        val sequenceEnabled = settings.isSequenceEnabled
        val graphEnabled = settings.graphDistributionType != com.example.purramid.thepurramid.randomizers.GraphDistributionType.OFF
        val currentMode = settings.mode

        // Common
        binding.switchIsAnnounceEnabled.isEnabled = binding.switchIsAnnounceEnabled.isVisible && !sequenceEnabled && !graphEnabled
        binding.switchIsCelebrateEnabled.isEnabled = binding.switchIsCelebrateEnabled.isVisible && announceEnabled && !sequenceEnabled

        // Spin specific
        binding.switchSpin.isEnabled = binding.spinSpecificSettingsLayout.isVisible
        binding.switchIsSequenceEnabled.isEnabled = binding.spinSpecificSettingsLayout.isVisible

        // Dice specific
        binding.switchUseDicePips.isEnabled = binding.diceSettingsLayout.isVisible
        binding.switchIsPercentileDiceEnabled.isEnabled = binding.diceSettingsLayout.isVisible
        binding.switchIsDiceAnimationEnabled.isEnabled = binding.diceSettingsLayout.isVisible
        binding.switchIsDiceCritCelebrationEnabled.isEnabled = binding.diceSettingsLayout.isVisible && announceEnabled && !graphEnabled

        // Dice Pool Button
        binding.buttonDicePoolConfig.isEnabled = binding.diceSettingsLayout.isVisible && !settings.isPercentileDiceEnabled

        // Update Add Another button state based on limits too
        updateAddAnotherButtonState()
    }


    override fun onResume() {
        super.onResume()
        updateAddAnotherButtonState() // Refresh button state in case active instance count changed
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}