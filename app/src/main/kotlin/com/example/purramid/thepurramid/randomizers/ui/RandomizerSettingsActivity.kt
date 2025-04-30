// RandomizerSettingsActivity.kt
package com.example.purramid.thepurramid.randomizers.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.CompoundButton // Import for switch listener
import android.widget.Toast // Import Toast for errors
import androidx.activity.viewModels // Import for viewModels delegate
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar
import com.example.purramid.thepurramid.R // Import R
import com.example.purramid.thepurramid.databinding.ActivityRandomizerSettingsBinding
import com.example.purramid.thepurramid.randomizers.RandomizerMode // Import enum
import com.example.purramid.thepurramid.randomizers.viewmodel.RandomizerSettingsViewModel // Import ViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID

@AndroidEntryPoint
class RandomizerSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRandomizerSettingsBinding
    private val viewModel: RandomizerSettingsViewModel by viewModels()
    private var isUpdatingSwitches = false
    private var isUpdatingMode = false //

    companion object {
        // Use the key defined in the ViewModel for consistency
        const val EXTRA_INSTANCE_ID = RandomizerSettingsViewModel.KEY_INSTANCE_ID
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRandomizerSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve instanceId passed via Intent (Activity doesn't strictly need it if VM handles it,
        // but good for validation before setup)
        val instanceIdString = intent.getStringExtra(EXTRA_INSTANCE_ID)
        var instanceIdValid = false // Track validity

        if (instanceIdString == null) {
            Log.e("SettingsActivity", "Instance ID missing in Intent extras.")
        } else {
            try {
                UUID.fromString(instanceIdString) // Validate UUID format
                instanceIdValid = true // Mark as potentially valid if format is okay
            } catch (e: IllegalArgumentException) {
                Log.e("SettingsActivity", "Invalid Instance ID format: $instanceIdString")
                // ID format is invalid, validity remains false
            }
        }

       if (!instanceIdValid) {
            Toast.makeText(this, getString(R.string.error_settings_instance_id_failed), Toast.LENGTH_LONG).show()
            finish()
            return // Stop further execution in onCreate
        }

        // ID seems valid, proceed with setup
        setupListeners()
        observeViewModel()
    }

        // Instance ID seems valid, ViewModel will load based on SavedStateHandle
        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.closeSettingsButton.setOnClickListener {
            finish() // Simply close the activity
        }

        binding.buttonListEditor.setOnClickListener {
            val intent = Intent(this, ListEditorActivity::class.java)
            // ListEditorActivity doesn't seem to require the instanceId directly based on current code, else:
            // intent.putExtra(ListEditorActivity.EXTRA_INSTANCE_ID, instanceIdString)
            startActivity(intent)
        }

        // --- Switch Listeners ---
        val switchListener = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
            if (isUpdatingSwitches) return@OnCheckedChangeListener // Prevent loop

            // Call ViewModel update function based on which switch was toggled
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

        // --- Mode Toggle Group Listener ---
        binding.modeToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            // We only care about the button that is *checked* (isChecked == true)
            // in a single-select group. Ignore un-check events if any.
            if (isUpdatingMode || !isChecked) return@addOnButtonCheckedListener

            val selectedMode = when (checkedId) {
                R.id.buttonModeSpin -> RandomizerMode.SPIN
                R.id.buttonModeSlots -> RandomizerMode.SLOTS
                R.id.buttonModeDice -> RandomizerMode.DICE
                R.id.buttonModeCoinFlip -> RandomizerMode.COIN_FLIP
                else -> null // Should not happen with selectionRequired=true
            }

            selectedMode?.let {
                viewModel.updateMode(it)
            }
        }
    }

    private fun observeViewModel() {
        // Observe Settings LiveData
        viewModel.settings.observe(this) { settingsEntity ->
            // Prevent listeners firing while we update UI
            isUpdatingSwitches = true
            // Prevent mode listener during update
            isUpdatingMode = true
            if (settingsEntity != null) {
                binding.switchSpin.isChecked = settingsEntity.isSpinEnabled
                binding.switchAnnounce.isChecked = settingsEntity.isAnnounceEnabled
                binding.switchCelebrate.isChecked = settingsEntity.isCelebrateEnabled
                binding.switchSequence.isChecked = settingsEntity.isSequenceEnabled

                // Handle interdependencies affecting switch enabled state
                binding.switchCelebrate.isEnabled = settingsEntity.isAnnounceEnabled
                binding.switchAnnounce.isEnabled = !settingsEntity.isSequenceEnabled
                // Also disable Celebrate if Sequence is on (ViewModel logic handles saving, UI reflects)
                if (settingsEntity.isSequenceEnabled) {
                    binding.switchCelebrate.isEnabled = false
                }

                updateModeSelectionUI(settingsEntity.mode)

                binding.textViewSettingsPlaceholder.text = "" // Update placeholder

            } else {
                // Handle null settings, Disable UI elements
                binding.textViewSettingsPlaceholder.text = getString(R.string.error_settings_load_failed)
                binding.modeToggleGroup.isEnabled = false // Disable mode group on error
                binding.switchSpin.isEnabled = false
                binding.switchAnnounce.isEnabled = false
                binding.switchCelebrate.isEnabled = false
                binding.switchSequence.isEnabled = false
                binding.buttonListEditor.isEnabled = false
            }
            isUpdatingSwitches = false
            isUpdatingMode = false
        }

        // Observe Error Events
        viewModel.errorEvent.observe(this) { errorResId ->
            errorResId?.let { resId ->
                // Get the actual translated string using the Activity's context
                val message = getString(resId)

                // Check if it's the critical ID failure case
                if (resId == R.string.error_settings_instance_id_failed) {
                    // Still use Toast for this one as the activity is closing immediately
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    // For other info/error messages, use Snackbar
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                        // Optional: Add a simple Dismiss action
                        .setAction(getString(R.string.snackbar_action_ok)) { /* Snackbar dismisses on its own */ }
                        .show()
                }
                viewModel.clearErrorEvent() // Consume the error event
            }
        }
    }

    // --- Mode Selection UI Update ---
    private fun updateModeSelectionUI(currentMode: RandomizerMode) {
        // Check the correct button based on the current mode
        // Note: Checking a button programmatically fires the listener, hence the isUpdatingMode flag
        val buttonIdToCheck = when (currentMode) {
            RandomizerMode.SPIN -> R.id.buttonModeSpin
            RandomizerMode.SLOTS -> R.id.buttonModeSlots
            RandomizerMode.DICE -> R.id.buttonModeDice
            RandomizerMode.COIN_FLIP -> R.id.buttonModeCoinFlip
        }

        // Check the button if it's not already checked (prevents loop if check triggers listener)
        if (binding.modeToggleGroup.checkedButtonId != buttonIdToCheck) {
            binding.modeToggleGroup.check(buttonIdToCheck)
        }

        // --- Visibility/Enablement of other settings based on mode ---
        // List Editor Button: Visible for Spin and Slots
        val listModes = listOf(RandomizerMode.SPIN, RandomizerMode.SLOTS)
        binding.buttonListEditor.isEnabled = currentMode in listModes
        // Optionally hide it completely if not applicable:
        // binding.buttonListEditor.isVisible = currentMode in listModes

        // Spin Animation Switch: Only for Spin
        binding.switchSpin.isVisible = currentMode == RandomizerMode.SPIN

        // Announce Switch: Visible for Spin and Slots
        val announceModes = listOf(RandomizerMode.SPIN, RandomizerMode.SLOTS)
        binding.switchAnnounce.isVisible = currentMode in announceModes
        // Ensure Celebrate switch enablement still depends on Announce *when visible*
        binding.switchCelebrate.isEnabled = binding.switchAnnounce.isChecked && binding.switchAnnounce.isVisible

        // Celebrate Switch: Only for Spin
        binding.switchCelebrate.isVisible = currentMode == RandomizerMode.SPIN

        // Sequence Switch: Only for Spin
        binding.switchSequence.isVisible = currentMode == RandomizerMode.SPIN

        // Adjust enablement based on Sequence for Spin mode
        if (currentMode == RandomizerMode.SPIN) {
            // Get sequence state from ViewModel if needed, assume settingsEntity is available from observer
            val sequenceEnabled = viewModel.settings.value?.isSequenceEnabled ?: false
            binding.switchAnnounce.isEnabled = !sequenceEnabled && binding.switchAnnounce.isVisible // Announce disabled if sequence on (and Announce is visible)
            binding.switchCelebrate.isEnabled = !sequenceEnabled && binding.switchAnnounce.isChecked && binding.switchCelebrate.isVisible // Celebrate disabled if sequence on or announce off (and Celebrate is visible)

        } else {
            // Ensure Announce/Celebrate are enabled if visible for non-Spin modes (like Slots) unless other logic dictates otherwise
            binding.switchAnnounce.isEnabled = binding.switchAnnounce.isVisible
        }

        // Hide placeholder text now that mode is shown
        binding.textViewSettingsPlaceholder.text = ""
    }
}