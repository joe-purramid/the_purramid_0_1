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
import com.example.purramid.thepurramid.randomizers.DiceSumResultType
import com.example.purramid.thepurramid.randomizers.GraphDistributionType
import com.example.purramid.thepurramid.randomizers.GraphLineStyle
import com.example.purramid.thepurramid.randomizers.RandomizerMode
import com.example.purramid.thepurramid.util.Event
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
        const val KEY_INSTANCE_ID = "instanceId" // Ensure this matches NavArgs key
        private const val TAG = "SettingsViewModel"
    }

    private val instanceId: UUID? = savedStateHandle.get<String>(KEY_INSTANCE_ID)?.let {
        try { UUID.fromString(it) } catch (e: IllegalArgumentException) { null }
    }

    private val _settings = MutableLiveData<SpinSettingsEntity?>()
    val settings: LiveData<SpinSettingsEntity?> = _settings

    private val _errorEvent = MutableLiveData<Event<Int>>() // Using Int for String Resource ID
    val errorEvent: LiveData<Event<Int>> = _errorEvent

    init {
        if (instanceId != null) {
            loadSettings(instanceId)
        } else {
            Log.e(TAG, "Critical Error: Instance ID is null in SavedStateHandle.")
            _errorEvent.postValue(Event(R.string.error_settings_instance_id_failed))
            _settings.postValue(null)
        }
    }

    private fun loadSettings(id: UUID) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val loadedSettings = randomizerDao.getSettingsForInstance(id)
                if (loadedSettings != null) {
                    withContext(Dispatchers.Main) { _settings.value = loadedSettings }
                } else {
                    Log.w(TAG, "No settings found for instance $id. Creating new default settings.")
                    val defaultSettings = SpinSettingsEntity(instanceId = id) // Create default
                    randomizerDao.saveSettings(defaultSettings) // Save it
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
            _errorEvent.postValue(Event(R.string.error_settings_not_loaded_cant_save)) // TODO: Add string
            return
        }
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

    fun updateUseDicePips(enabled: Boolean) {
        updateSettingsField { it.copy(useDicePips = enabled) }
    }

    fun updateIsPercentileDiceEnabled(enabled: Boolean) {
        updateSettingsField { it.copy(isPercentileDiceEnabled = enabled) }
    }

    fun updateIsDiceAnimationEnabled(enabled: Boolean) {
        updateSettingsField { it.copy(isDiceAnimationEnabled = enabled) }
    }

    fun updateDiceSumResultType(newType: DiceSumResultType) {
        updateSettingsField { it.copy(diceSumResultType = newType) }
    }

    fun updateIsDiceCritCelebrationEnabled(enabled: Boolean) {
        updateSettingsField {
            val announceIsOn = it.isAnnounceEnabled
            it.copy(isDiceCritCelebrationEnabled = enabled && announceIsOn)
        }
    }

    fun updateIsAnnounceEnabled(enabled: Boolean) {
        updateSettingsField { settings ->
            if (!enabled) {
                settings.copy(
                    isAnnounceEnabled = false,
                    isCelebrateEnabled = false, // General celebration
                    isDiceCritCelebrationEnabled = false, // Dice crit celebration
                    // If Sum Results and Modifiers should be RESET when Announce is OFF:
                    // diceSumResultType = DiceSumResultType.INDIVIDUAL, // Reset to default
                    // diceModifierConfigJson = DEFAULT_EMPTY_JSON_MAP // Reset modifiers
                    // For now, we'll just let them retain their values but be disabled by UI
                )
            } else { // Turning Announce ON
                settings.copy(
                    isAnnounceEnabled = true,
                    // Force Graph OFF if Announce is turned ON
                    graphDistributionType = GraphDistributionType.OFF
                )
            }
        }
    }

    fun updateIsCelebrateEnabled(enabled: Boolean) {
        updateSettingsField {
            val announceIsOn = it.isAnnounceEnabled
            it.copy(isCelebrateEnabled = enabled && announceIsOn)
        }
    }

    fun updateIsSpinEnabled(enabled: Boolean) {
        updateSettingsField { it.copy(isSpinEnabled = enabled) }
    }

    fun updateIsSequenceEnabled(enabled: Boolean) {
        updateSettingsField {
            if (enabled) {
                it.copy(isSequenceEnabled = true, isAnnounceEnabled = false, isCelebrateEnabled = false)
            } else {
                it.copy(isSequenceEnabled = false)
            }
        }
    }

    fun updateDicePoolConfig(newConfigJson: String) {
        updateSettingsField { it.copy(dicePoolConfigJson = newConfigJson) }
    }

    // *** Function to update dice color config ***
    fun updateDiceColorConfig(newConfigJson: String) {
        updateSettingsField { it.copy(diceColorConfigJson = newConfigJson) }
    }

    fun updateDiceModifierConfig(newConfigJson: String) {
        updateSettingsField { it.copy(diceModifierConfigJson = newConfigJson) }
    }

    fun updateGraphDistributionType(newType: GraphDistributionType) {
        updateSettingsField { currentState ->
            if (newType != GraphDistributionType.OFF) {
                currentState.copy(
                    graphDistributionType = newType,
                    isAnnounceEnabled = false,
                    isCelebrateEnabled = false,
                    isDiceCritCelebrationEnabled = false
                    // TODO: Decide if diceSumResultType and diceModifierConfigJson should be reset
                    // diceSumResultType = DiceSumResultType.INDIVIDUAL,
                    // diceModifierConfigJson = DEFAULT_EMPTY_JSON_MAP
                )
            } else {
                currentState.copy(graphDistributionType = GraphDistributionType.OFF)
            }
        }
    }

    private fun saveSettings(settingsToSave: SpinSettingsEntity) {
        if (instanceId == null) { // Should have been caught by updateSettingsField
            Log.e(TAG, "Instance ID is null during saveSettings. This should not happen.")
            _errorEvent.postValue(Event(R.string.error_settings_instance_id_failed))
            return
        }

        // Ensure the instanceId is correctly set on the object being saved.
        val finalSettingsToSave = settingsToSave.copy(instanceId = this.instanceId)

        _settings.value = finalSettingsToSave // Update LiveData immediately

        viewModelScope.launch(Dispatchers.IO) {
            try {
                randomizerDao.saveSettings(finalSettingsToSave)
                Log.d(TAG, "Settings saved for instance ${finalSettingsToSave.instanceId}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save settings for instance ${finalSettingsToSave.instanceId}", e)
                withContext(Dispatchers.Main) {
                    _errorEvent.value = Event(R.string.error_settings_save_failed_kitty)
                }
            }
        }
    }

    fun clearErrorEvent() {
        _errorEvent.value = Event(0) // Or null if your Event wrapper handles null content
    }
}
