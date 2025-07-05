// RandomizerSettingsViewModel.kt
package com.example.purramid.thepurramid.randomizers.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.data.db.RandomizerDao
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity
import com.example.purramid.thepurramid.randomizers.RandomizerMode
import com.example.purramid.thepurramid.util.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class RandomizerSettingsViewModel @Inject constructor(
    private val randomizerDao: RandomizerDao,
    savedStateHandle: SavedStateHandle // Hilt injects this automatically
) : ViewModel() {

    companion object {
        const val KEY_INSTANCE_ID = "instanceId" // Ensure this matches NavArgs key
        private const val TAG = "SettingsViewModel"
    }

    private val instanceId: Int = savedStateHandle.get<Int>(KEY_INSTANCE_ID) ?: 0

    private val _settings = MutableLiveData<SpinSettingsEntity?>()
    val settings: LiveData<SpinSettingsEntity?> = _settings

    private val _errorEvent = MutableLiveData<Event<Int>>() // Using Int for String Resource ID
    val errorEvent: LiveData<Event<Int>> = _errorEvent

    init {
        if (instanceId > 0) {
            loadSettings(instanceId)
        } else {
            Log.e(TAG, "Critical Error: Instance ID is invalid: $instanceId")
            _errorEvent.postValue(Event(R.string.error_settings_instance_id_failed))
            _settings.postValue(null)
        }
    }

    private fun loadSettings(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val loadedSettings = randomizerDao.getSettingsForInstance(id)
                if (loadedSettings != null) {
                    withContext(Dispatchers.Main) { _settings.value = loadedSettings }
                } else {
                    Log.w(TAG, "No settings found for instance $id. Creating new default settings.")
                    // Ensure all defaults are set
                    val defaultSettings = SpinSettingsEntity(
                        instanceId = id,
                        mode = RandomizerMode.SPIN // Default mode for a new instance
                    )
                    randomizerDao.saveSettings(defaultSettings)
                    withContext(Dispatchers.Main) {
                        _settings.value = defaultSettings
                        _errorEvent.value = Event(R.string.info_settings_defaulted_kitty)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading settings for instance $id", e)
                withContext(Dispatchers.Main) {
                    _errorEvent.value = Event(R.string.error_settings_load_failed)
                    _settings.value = null
                }
            }
        }
    }

    private fun updateSettingsField(updateAction: (SpinSettingsEntity) -> SpinSettingsEntity) {
        val currentSettings = _settings.value
        if (currentSettings == null) {
            Log.e(TAG, "Attempted to update settings but current settings are null.")
            _errorEvent.postValue(Event(R.string.error_settings_not_loaded_cant_save))
            return
        }
        // Apply the update action and then save
        saveSettings(updateAction(currentSettings))
    }

    fun updateMode(newMode: RandomizerMode) {
        updateSettingsField { it.copy(mode = newMode) }
    }

    fun updateNumSlotsColumns(numColumns: Int) {
        if (numColumns == 3 || numColumns == 5) {
            updateSettingsField { it.copy(numSlotsColumns = numColumns) }
        }
    }

    fun updateIsAnnounceEnabled(enabled: Boolean) {
        updateSettingsField { settings ->
            var updatedSettings = settings.copy(isAnnounceEnabled = enabled)
            if (enabled) { // Turning Announce ON
                // Mutually exclusive with Sequence for Spin
                if (settings.mode == RandomizerMode.SPIN) {
                    updatedSettings = updatedSettings.copy(isSequenceEnabled = false)
                }
            } else { // Turning Announce OFF
                // If announce is off, celebration should also be off
                updatedSettings = updatedSettings.copy(
                    isCelebrateEnabled = false // General celebration
                )
            }
            updatedSettings
        }
    }

    fun updateIsCelebrateEnabled(enabled: Boolean) { // General celebration (for Spin)
        updateSettingsField { settings ->
            val announceIsOn = settings.isAnnounceEnabled && settings.mode == RandomizerMode.SPIN
            settings.copy(isCelebrateEnabled = enabled && announceIsOn)
        }
    }

    fun updateIsSpinEnabled(enabled: Boolean) {
        updateSettingsField { it.copy(isSpinEnabled = enabled) }
    }

    // In RandomizerSettingsViewModel.kt
    fun updateSpinSequenceEnabled(isEnabled: Boolean) {
        val current = _settings.value ?: return // _settings is MutableLiveData
        var newAnnounceEnabled = current.isAnnounceEnabled
        var newConfettiEnabled = current.isConfettiEnabled

        if (isEnabled) { // If sequence is being turned ON
            newAnnounceEnabled = false // Turn off announcement
            newConfettiEnabled = false // Turn off confetti
        }
        // else: if sequence is being turned OFF, announce/confetti retain previous valid states
        // or re-evaluate based on other rules.

        _settings.value = current.copy(
            isSequenceEnabled = isEnabled,
            isAnnounceEnabled = newAnnounceEnabled,
            isConfettiEnabled = newConfettiEnabled
            // ... other logic ...
        )
    }

    private fun saveSettings(settingsToSave: SpinSettingsEntity) {
        if (instanceId == 0) {
            Log.e(TAG, "Instance ID is invalid during saveSettings. This should not happen.")
            _errorEvent.postValue(Event(R.string.error_settings_instance_id_failed))
            return
        }

        val finalSettingsToSave = settingsToSave.copy(instanceId = this.instanceId)
        _settings.value = finalSettingsToSave // Update LiveData immediately

        viewModelScope.launch(Dispatchers.IO) {
            try {
                randomizerDao.saveSettings(finalSettingsToSave)
                Log.d(TAG, "Settings saved for instance ${finalSettingsToSave.instanceId}: Mode=${finalSettingsToSave.mode}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save settings for instance ${finalSettingsToSave.instanceId}", e)
                withContext(Dispatchers.Main) {
                    _errorEvent.value = Event(R.string.error_settings_save_failed_kitty)
                }
            }
        }
    }

    fun clearErrorEvent() {
        _errorEvent.value = Event(0) // Using 0 or a specific "no_error" resource ID
    }
}