// RandomizerSettingsViewModel.kt
package com.example.purramid.thepurramid.randomizers.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purramid.thepurramid.data.db.RandomizerDao
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity
import com.example.purramid.thepurramid.randomizers.RandomizerMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RandomizerSettingsViewModel @Inject constructor(
    private val randomizerDao: RandomizerDao,
    savedStateHandle: SavedStateHandle // Hilt injects this automatically
) : ViewModel() {

    companion object {
        // Key used to pass instanceId via Intent extras or Navigation arguments
        const val KEY_INSTANCE_ID = "com.example.purramid.SETTINGS_INSTANCE_ID"
        // Or use the key defined in RandomizerSettingsActivity/NavGraph if different
    }

    // Get the instanceId from SavedStateHandle (passed via Intent/NavArgs)
    // Fallback to generating a new one shouldn't ideally happen in settings,
    // but handle potential null for safety. Assume ID is always passed.
    private val instanceId: UUID? = savedStateHandle.get<String>(KEY_INSTANCE_ID)?.let {
        try {
            UUID.fromString(it)
        } catch (e: IllegalArgumentException) {
            null // Invalid UUID passed
        }
    }

    private val _settings = MutableLiveData<SpinSettingsEntity?>()
    val settings: LiveData<SpinSettingsEntity?> = _settings

    // LiveData to signal errors (e.g., invalid instance ID)
    private val _errorEvent = MutableLiveData<Int?>()
    val errorEvent: LiveData<Int?> = _errorEvent

    init {
        if (instanceId != null) {
            loadSettings(instanceId)
        } else {
            // Instance ID wasn't passed or was invalid - signal error
            _errorEvent.value = "Invalid or missing Randomizer Instance ID."
            _settings.value = null // Ensure settings are null
        }
    }

    private fun loadSettings(id: UUID) {
        viewModelScope.launch(Dispatchers.IO) {
            val loadedSettings = randomizerDao.getSettingsForInstance(id)
            var finalSettings: SpinSettingsEntity? = loadedSettings
            var errorMessage: Int? = null // Variable to hold potential error message
                if (loadedSettings != null) {
                    _settings.postValue(loadedSettings) // Post loaded settings
                } else {
                    // Settings not found for this valid ID
                    finalSettings = SpinSettingsEntity(instanceId = id) // Create defaults
                    _settings.postValue(finalSettings) // Post default settings
                    errorResId = R.string.info_settings_defaulted_kitty // Informative message
                }
            // Post error/info resource ID *after* posting settings
            errorResId?.let { _errorEvent.postValue(it) }
        }
    }

    /** Updates a specific boolean setting */
    private fun updateBooleanSetting(updateAction: (SpinSettingsEntity) -> SpinSettingsEntity) {
        val currentSettings = _settings.value ?: return
        saveSettings(updateAction(currentSettings)) // Pass updated settings to common save function
    }

    /** Updates the Randomizer mode */
    fun updateMode(newMode: RandomizerMode) {
        updateBooleanSetting { it.copy(mode = newMode) }
    }

    /** Updates the number of columns for Slots mode */
    fun updateNumSlotsColumns(numColumns: Int) {
        if (numColumns != 3 && numColumns != 5) return {
            updateBooleanSetting { it.copy(numSlotsColumns = numColumns) }
        }
    }

    /** Saves the provided settings state, applying business rules. */
    private fun saveSettings(newSettings: SpinSettingsEntity) {
        if (instanceId == null) {
             _errorEvent.value = "Cannot save settings: Instance ID is missing."
             return
        }

    // --- NEW Dice Toggle Updaters ---
    fun updateUseDicePips(enabled: Boolean) {
        updateBooleanSetting { it.copy(useDicePips = enabled) }
    }

    fun updateIsPercentileDiceEnabled(enabled: Boolean) {
        updateBooleanSetting { it.copy(isPercentileDiceEnabled = enabled) }
    }

    fun updateIsDiceAnimationEnabled(enabled: Boolean) {
        updateBooleanSetting { it.copy(isDiceAnimationEnabled = enabled) }
    }

    fun updateIsDiceCritCelebrationEnabled(enabled: Boolean) {
        // Apply dependency: Crit celebration requires announcement
        updateBooleanSetting {
            val announceIsOn = it.isAnnounceEnabled
            it.copy(isDiceCritCelebrationEnabled = enabled && announceIsOn)
        }
    }

    // --- Common/Spin Toggle Updaters ---
    fun updateIsAnnounceEnabled(enabled: Boolean) {
        updateBooleanSetting {
            // If turning Announce OFF, also turn off dependent settings
            if (!enabled) {
                it.copy(
                    isAnnounceEnabled = false,
                    isCelebrateEnabled = false, // Turn off general celebration
                    isDiceCritCelebrationEnabled = false // Turn off dice crit celebration
                    // Also disable Modifiers, Sum Results later
                )
            } else {
                it.copy(
                    isAnnounceEnabled = true,
                    graphDistributionType = GraphDistributionType.OFF // Force graph off
                )
            }
        }
    }

    fun updateIsCelebrateEnabled(enabled: Boolean) { // General celebration (Spin?)
        // Requires Announce to be enabled
        updateBooleanSetting {
            val announceIsOn = it.isAnnounceEnabled
            it.copy(isCelebrateEnabled = enabled && announceIsOn)
        }
    }

    fun updateIsSpinEnabled(enabled: Boolean) {
        updateBooleanSetting { it.copy(isSpinEnabled = enabled) }
    }

    fun updateIsSequenceEnabled(enabled: Boolean) {
        // Apply dependency: Sequence disables Announce/Celebrate
        updateBooleanSetting {
            if (enabled) {
                it.copy(isSequenceEnabled = true, isAnnounceEnabled = false, isCelebrateEnabled = false)
            } else {
                it.copy(isSequenceEnabled = false)
                // Don't automatically re-enable announce/celebrate here, let user do it
            }
        }
    }

    // --- Update Graph Type ---
    fun updateGraphDistributionType(newType: GraphDistributionType) {
        updateBooleanSetting { currentState ->
            if (newType != GraphDistributionType.OFF) {
                // --- Turning Graph ON (any mode other than OFF) ---
                // Force Announcement and its dependent settings OFF
                currentState.copy(
                    graphDistributionType = newType,
                    isAnnounceEnabled = false,
                    isCelebrateEnabled = false, // Force off general celebration
                    isDiceCritCelebrationEnabled = false // Force off dice crit celebration
                    // diceSumResultType = DiceSumResultType.INDIVIDUAL, // TODO Reset sum type? Or leave?
                    // diceModifierConfigJson = DEFAULT_EMPTY_JSON_MAP // TODO Reset modifiers? Or leave?
                )
            } else {
                // --- Turning Graph OFF ---
                // Just update the graph type, don't automatically re-enable Announcement
                currentState.copy(graphDistributionType = GraphDistributionType.OFF)
            }
        }
    }

    // --- Save Logic ---
    /** Saves the provided settings state, applying business rules. */
    private fun saveSettings(newSettings: SpinSettingsEntity) {
        if (instanceId == null) {
            _errorEvent.postValue(Event(R.string.error_settings_instance_id_failed))
            return
        }

        // Handled by passing the modified object to this function
        val settingsToSave = newSettings.copy(instanceId = instanceId) // Ensure ID is correct

        // Update LiveData immediately for UI responsiveness
        _settings.value = settingsToSave

        // Persist to database in the background
        saveSettingsInternal(settingsToSave)
    }

    private fun saveSettingsInternal(settingsToSave: SpinSettingsEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                randomizerDao.saveSettings(settingsToSave)
            } catch (e: Exception) {
                Log.e("RandomizerSettingsViewModel", "Failed to save settings", e)
                _errorEvent.postValue(Event(R.string.error_settings_save_failed_kitty))
            }
        }
    }

    /** Clears any pending error event */
    fun clearErrorEvent() {
        _errorEvent.value = null
    }

    // Update critical ID error check in init if needed
    init {
        if (instanceId != null) {
            loadSettings(instanceId)
        } else {
            // Post the Resource ID for the critical error
            _errorEvent.postValue(R.string.error_settings_instance_id_failed)
            _settings.postValue(null) // Ensure settings are null
        }
    }
}