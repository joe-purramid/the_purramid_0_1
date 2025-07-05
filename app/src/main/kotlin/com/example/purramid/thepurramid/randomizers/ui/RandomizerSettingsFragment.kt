// RandomizerSettingsFragment.kt
package com.example.purramid.thepurramid.randomizers.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.purramid.thepurramid.MainActivity
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.data.db.RandomizerDao
import com.example.purramid.thepurramid.data.db.RandomizerInstanceEntity
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity
import com.example.purramid.thepurramid.databinding.FragmentRandomizerSettingsBinding
import com.example.purramid.thepurramid.randomizers.RandomizerMode
import com.example.purramid.thepurramid.randomizers.RandomizersHostActivity
import com.example.purramid.thepurramid.randomizers.viewmodel.RandomizerSettingsViewModel
import com.example.purramid.thepurramid.ui.PurramidPalette
import com.google.android.material.chip.Chip
import com.google.android.material.colorpicker.MaterialColorPickerDialog
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class RandomizerSettingsFragment : Fragment() {

    private var _binding: FragmentRandomizerSettingsBinding? = null
    private val binding get() = _binding!!

    private val settingsViewModel: RandomizerSettingsViewModel by activityViewModels()
    private val args: RandomizerSettingsFragmentArgs by navArgs()

    private lateinit var currentSettingsEntity: SpinSettingsEntity
    private var initialBackgroundColor: Int = Color.BLACK

    @Inject
    lateinit var randomizerDao: RandomizerDao

    companion object {
        private const val TAG = "RandomizerSettingsFrag"
        private const val MAX_RANDOMIZER_INSTANCES = 4
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
        Log.d(TAG, "onViewCreated for instanceId: ${args.instanceId}")

        observeSettings()
        setupListeners()
        updateAddAnotherButtonState()
    }

    private fun observeSettings() {
        settingsViewModel.settings.observe(viewLifecycleOwner) { settings ->
            if (settings == null) {
                Log.e(TAG, "Settings are null for instanceId: ${args.instanceId}. Attempting to re-load or use defaults.")
                return@observe
            }
            Log.d(TAG, "Settings observed: ${settings.instanceId}, Mode: ${settings.mode}")
            currentSettingsEntity = settings
            initialBackgroundColor = settings.backgroundColor
            updateUiWithSettings()
        }

        settingsViewModel.errorEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { errorMessage ->
                Snackbar.make(binding.root, errorMessage, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUiWithSettings() {
        if (!::currentSettingsEntity.isInitialized) {
            Log.w(TAG, "updateUiWithSettings called before currentSettingsEntity is initialized.")
            return
        }

        binding.mainSettingsContainer.isVisible = true // Show UI once settings are loaded

        // Mode Selector
        binding.modeChipGroup.setOnCheckedChangeListener(null) // Temporarily remove listener
        val modeChipId = getChipIdForMode(currentSettingsEntity.mode)
        if (modeChipId != -1) {
            binding.modeChipGroup.check(modeChipId)
        }
        binding.modeChipGroup.setOnCheckedChangeListener(modeChipChangeListener)

        // Show/Hide mode-specific setting groups
        binding.spinSettingsGroup.isVisible = currentSettingsEntity.mode == RandomizerMode.SPIN
        binding.slotsSettingsGroup.isVisible = currentSettingsEntity.mode == RandomizerMode.SLOTS

        // Background Color Picker Button
        updateBackgroundColorButton(currentSettingsEntity.backgroundColor)

        // Populate Spin Settings
        if (currentSettingsEntity.mode == RandomizerMode.SPIN) {
            binding.switchSpinSoundEnabled.isChecked = currentSettingsEntity.isSoundEnabled
            binding.switchSpinResultAnnouncement.isChecked = currentSettingsEntity.isAnnounceEnabled
            binding.switchSpinSequenceEnabled.isChecked = currentSettingsEntity.isSequenceEnabled
            binding.switchSpinConfetti.isChecked = currentSettingsEntity.isConfettiEnabled
            binding.textFieldSpinDuration.setText(currentSettingsEntity.spinDurationMillis.toString())
            binding.textFieldSpinMaxItems.setText(currentSettingsEntity.spinMaxItems.toString())
        }

        // When sequence is enabled, confetti and announcement might be disabled
        if (currentSettingsEntity.isSequenceEnabled) {
            binding.switchSpinResultAnnouncement.isEnabled = false
            binding.switchSpinConfetti.isEnabled = false
        } else {
            binding.switchSpinResultAnnouncement.isEnabled = true
            // Confetti enablement depends on announcement, see section C
            binding.switchSpinConfetti.isEnabled = currentSettingsEntity.isAnnounceEnabled
        }

        // Populate Slots Settings
        if (currentSettingsEntity.mode == RandomizerMode.SLOTS) {
            binding.switchSlotsSound.isChecked = currentSettingsEntity.isSlotsSoundEnabled
            binding.switchSlotsResultAnnouncement.isChecked = currentSettingsEntity.isSlotsAnnounceResultEnabled
            binding.textFieldSlotsSpinDuration.setText(currentSettingsEntity.slotsSpinDuration.toString())
            binding.textFieldSlotsReelVariation.setText(currentSettingsEntity.slotsReelStopVariation.toString())
        }
        when (currentSettingsEntity.numSlotsColumns) {
            3 -> binding.toggleSlotsColumns.check(R.id.buttonSlotsColumns3)
            5 -> binding.toggleSlotsColumns.check(R.id.buttonSlotsColumns5)
            else -> binding.toggleSlotsColumns.check(R.id.buttonSlotsColumns3) // Default
        }
    }

    private val modeChipChangeListener = CompoundButton.OnCheckedChangeListener { chip, isChecked ->
        if (isChecked && ::currentSettingsEntity.isInitialized) {
            val newMode = when (chip.id) {
                R.id.chipSpin -> RandomizerMode.SPIN
                R.id.chipSlots -> RandomizerMode.SLOTS
                else -> currentSettingsEntity.mode // Should not happen
            }
            if (currentSettingsEntity.mode != newMode) {
                Log.d(TAG, "Mode changed to: $newMode")
                currentSettingsEntity = currentSettingsEntity.copy(mode = newMode)
                settingsViewModel.updateMode(newMode) // Update ViewModel which should persist and reload settings for new mode structure
                // updateUiWithSettings() will be called by the observer on settingsViewModel.settings
            }
        }
    }

    private fun setupListeners() {
        binding.closeSettingsButton.setOnClickListener { onCloseSettingsClicked() }
        binding.buttonAddAnotherRandomizer.setOnClickListener { addAnotherRandomizer() }

        binding.modeChipGroup.setOnCheckedChangeListener(modeChipChangeListener)

        // Background Color Picker
        binding.buttonChangeBackgroundColor.setOnClickListener { openColorPicker() }

        // Spin Settings Listeners
        binding.switchSpinSoundEnabled.setOnCheckedChangeListener { _, isChecked -> if (::currentSettingsEntity.isInitialized) currentSettingsEntity = currentSettingsEntity.copy(isSoundEnabled = isChecked) }
        binding.switchSpinResultAnnouncement.setOnCheckedChangeListener { _, isChecked ->
            if (!::currentSettingsEntity.isInitialized) return@setOnCheckedChangeListener
            currentSettingsEntity = currentSettingsEntity.copy(isAnnounceEnabled = isChecked)
            if (binding.switchSpinSequenceEnabled.isChecked) { // Sequence mode takes precedence
                binding.switchSpinConfetti.isEnabled = false
                return@setOnCheckedChangeListener
            }
            binding.switchSpinConfetti.isEnabled = isChecked
            if (!isChecked) { // If announcement is turned off
                binding.switchSpinConfetti.isChecked = false // Turn off confetti as well
                currentSettingsEntity = currentSettingsEntity.copy(isConfettiEnabled = false)
            }
        }
        binding.switchSpinSequenceEnabled.setOnCheckedChangeListener { _, isChecked ->
            if (!::currentSettingsEntity.isInitialized) return@setOnCheckedChangeListener
            currentSettingsEntity = currentSettingsEntity.copy(isSequenceEnabled = isChecked)
            // Update dependent controls
            if (isChecked) {
                binding.switchSpinResultAnnouncement.isEnabled = false
                binding.switchSpinResultAnnouncement.isChecked = false // Optionally turn off
                binding.switchSpinConfetti.isEnabled = false
                binding.switchSpinConfetti.isChecked = false // Optionally turn off
            } else {
                binding.switchSpinResultAnnouncement.isEnabled = true
                // Re-evaluate confetti enablement based on announcement
                binding.switchSpinConfetti.isEnabled = binding.switchSpinResultAnnouncement.isChecked
            }
        }
        binding.switchSpinConfetti.setOnCheckedChangeListener { _, isChecked ->
            if (!::currentSettingsEntity.isInitialized) return@setOnCheckedChangeListener
            // Only allow confetti to be checked if announcement is enabled and sequence is not
            if (binding.switchSpinResultAnnouncement.isChecked && !binding.switchSpinSequenceEnabled.isChecked) {
                currentSettingsEntity = currentSettingsEntity.copy(isConfettiEnabled = isChecked)
            } else {
                // If conditions not met, force confetti to off
                (it as CompoundButton).isChecked = false
                currentSettingsEntity = currentSettingsEntity.copy(isConfettiEnabled = false)
            }
        }
        binding.textFieldSpinDuration.doAfterTextChanged { text -> if (::currentSettingsEntity.isInitialized) currentSettingsEntity = currentSettingsEntity.copy(spinDurationMillis = text.toString().toLongOrNull() ?: 2000L) }
        binding.textFieldSpinMaxItems.doAfterTextChanged { text -> if (::currentSettingsEntity.isInitialized) currentSettingsEntity = currentSettingsEntity.copy(spinMaxItems = text.toString().toIntOrNull() ?: 20) }
        binding.buttonEditSpinList.setOnClickListener {
            if (!::currentSettingsEntity.isInitialized) return@setOnClickListener
            val instanceIdInt = currentSettingsEntity.instanceId
            val listIdToEdit = currentSettingsEntity.currentSpinListId
            val action = RandomizerSettingsFragmentDirections.actionRandomizerSettingsFragmentToListEditorActivity(
                instanceId = instanceIdInt,
                listId = listIdToEdit ?: -1L
            )
            findNavController().navigate(action)
        }

        // Slots Settings Listeners
        binding.switchSlotsSound.setOnCheckedChangeListener { _, isChecked -> if (::currentSettingsEntity.isInitialized) currentSettingsEntity = currentSettingsEntity.copy(isSlotsSoundEnabled = isChecked) }
        binding.switchSlotsResultAnnouncement.setOnCheckedChangeListener { _, isChecked -> if (::currentSettingsEntity.isInitialized) currentSettingsEntity = currentSettingsEntity.copy(isSlotsAnnounceResultEnabled = isChecked) }
        binding.textFieldSlotsSpinDuration.doAfterTextChanged { text -> if (::currentSettingsEntity.isInitialized) currentSettingsEntity = currentSettingsEntity.copy(slotsSpinDuration = text.toString().toLongOrNull() ?: 1000L) }
        binding.textFieldSlotsReelVariation.doAfterTextChanged { text -> if (::currentSettingsEntity.isInitialized) currentSettingsEntity = currentSettingsEntity.copy(slotsReelStopVariation = text.toString().toLongOrNull() ?: 200L) }
        binding.buttonEditSlotsLists.setOnClickListener { /* TODO: Navigate to Slots List Editor */ }
        binding.toggleSlotsColumns.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked && ::currentSettingsEntity.isInitialized) {
                val newColumnCount = when (checkedId) {
                    R.id.buttonSlotsColumns3 -> 3
                    R.id.buttonSlotsColumns5 -> 5
                    else -> currentSettingsEntity.numSlotsColumns
                }
                if (currentSettingsEntity.numSlotsColumns != newColumnCount) {
                    currentSettingsEntity = currentSettingsEntity.copy(numSlotsColumns = newColumnCount)
                }
            }
        }
    }

    private fun updateBackgroundColorButton(color: Int) {
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.color_square_background) as? GradientDrawable
        drawable?.setColor(color)
        binding.buttonChangeBackgroundColor.icon = drawable
        binding.textFieldRandomizerBackgroundColor.setText(String.format("#%06X", 0xFFFFFF and color))
    }

    private fun openColorPicker() {
        val initialColor = currentSettingsEntity.backgroundColor
        val colorPicker = MaterialColorPickerDialog.Builder(requireContext())
            .setTitle("Choose Background Color")
            .setColor(initialColor)
            .setListener { color, _ ->
                currentSettingsEntity = currentSettingsEntity.copy(backgroundColor = color)
                updateBackgroundColorButton(color)
            }
            .show()
    }

    private fun getChipIdForMode(mode: RandomizerMode): Int {
        return when (mode) {
            RandomizerMode.SPIN -> R.id.chipSpin
            RandomizerMode.SLOTS -> R.id.chipSlots
        }
    }

    private fun saveCurrentSettings() {
        if (::currentSettingsEntity.isInitialized) {
            Log.d(TAG, "Saving settings for instance ${currentSettingsEntity.instanceId}")
            settingsViewModel.saveSettings(currentSettingsEntity)
        } else {
            Log.w(TAG, "saveCurrentSettings called but currentSettingsEntity not initialized.")
        }
    }

    private fun onCloseSettingsClicked() {
        saveCurrentSettings()
        activity?.finish() // Finishes the RandomizersHostActivity
    }

    override fun onPause() {
        super.onPause()
        saveCurrentSettings()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateAddAnotherButtonState() {
        viewLifecycleOwner.lifecycleScope.launch {
            val activeInstances = withContext(Dispatchers.IO) {
                randomizerDao.getActiveInstancesCount()
            }
            binding.buttonAddAnotherRandomizer.isEnabled = activeInstances < MAX_RANDOMIZER_INSTANCES
            Log.d(TAG, "Active instances: $activeInstances, Add Another button enabled: ${binding.buttonAddAnotherRandomizer.isEnabled}")
        }
    }

    private fun addAnotherRandomizer() {
        if (!::currentSettingsEntity.isInitialized) {
            Snackbar.make(binding.root, "Current settings not loaded, cannot clone.", Snackbar.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val activeInstances = withContext(Dispatchers.IO) { randomizerDao.getActiveInstancesCount() }
            if (activeInstances >= MAX_RANDOMIZER_INSTANCES) {
                Snackbar.make(binding.root, "Maximum number of randomizer windows reached.", Snackbar.LENGTH_LONG).show()
                return@launch
            }

            settingsViewModel.saveSettings(currentSettingsEntity)

            // Get next available instanceId from the instance manager
            val newInstanceId = withContext(Dispatchers.IO) {
                // This should use the InstanceManager to get the next available instanceId
                // For now, we'll use a simple approach - find the next available ID
                val existingIds = randomizerDao.getAllStates().map { it.instanceId }.toSet()
                (1..MAX_RANDOMIZER_INSTANCES).find { it !in existingIds } ?: 1
            }

            val newSettings = currentSettingsEntity.copy(
                instanceId = newInstanceId,
                slotsColumnStates = emptyList(),
                currentSpinListId = null
            )
            val newInstanceEntity = RandomizerInstanceEntity(instanceId = newInstanceId)

            try {
                withContext(Dispatchers.IO) {
                    randomizerDao.saveSettings(newSettings)
                    randomizerDao.saveInstance(newInstanceEntity)
                }
                Log.d(TAG, "Cloned settings from ${currentSettingsEntity.instanceId} and created new instance: $newInstanceId")

                (activity as? MainActivity)?.launchNewRandomizerInstanceWithBounds(newInstanceId)

                updateAddAnotherButtonState()

            } catch (e: Exception) {
                Log.e(TAG, "Error saving new cloned instance or launching activity", e)
                Snackbar.make(binding.root, "Error creating new randomizer window.", Snackbar.LENGTH_LONG).show()
            }
        }
    }
}