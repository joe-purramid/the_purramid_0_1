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
    fun updateBooleanSetting(settingUpdater: (SpinSettingsEntity) -> SpinSettingsEntity) {
        val currentSettings = _settings.value ?: return // Need current state
        val updatedSettings = settingUpdater(currentSettings)
        saveSettings(updatedSettings)
    }

    /** Updates the Randomizer mode */
    fun updateMode(newMode: RandomizerMode) {
        val currentSettings = _settings.value ?: return
        if (currentSettings.mode != newMode) {
            val updatedSettings = currentSettings.copy(mode = newMode)
            // Add any logic here if changing mode affects other settings
            saveSettings(updatedSettings)
        }
    }

    /** Saves the provided settings state, applying business rules. */
    private fun saveSettings(newSettings: SpinSettingsEntity) {
        if (instanceId == null) {
             _errorEvent.value = "Cannot save settings: Instance ID is missing."
             return
        }

        // Ensure the settings being saved are associated with the correct instanceId
        var settingsToSave = newSettings.copy(instanceId = instanceId)

        // Apply business logic: If Sequence is enabled, disable Announce and Celebrate
        if (settingsToSave.isSequenceEnabled) {
            settingsToSave = settingsToSave.copy(
                isAnnounceEnabled = false,
                isCelebrateEnabled = false
            )
        }
        // Apply business logic: If Announce is disabled, disable Celebrate
        else if (!settingsToSave.isAnnounceEnabled) {
             settingsToSave = settingsToSave.copy(
                 isCelebrateEnabled = false
             )
        }

        // Update LiveData immediately for UI responsiveness
        _settings.value = settingsToSave

        // Persist to database in the background
        saveSettingsInternal(settingsToSave)
    }

    /** Performs the actual database save operation */
    private fun saveSettingsInternal(settingsToSave: SpinSettingsEntity) {
         viewModelScope.launch(Dispatchers.IO) {
             try {
                 randomizerDao.saveSettings(settingsToSave)
             } catch (e: Exception) {
                 Log.e("RandomizerSettingsViewModel", "Failed to save settings", e)
                 // *** Post the Resource ID for a generic save error ***
                 _errorEvent.postValue(R.string.error_settings_save_failed_kitty)
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