package com.example.purramid.thepurramid.probabilities.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purramid.thepurramid.probabilities.ProbabilitiesMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProbabilitiesSettingsViewModel @Inject constructor(
    private val preferencesManager: ProbabilitiesPreferencesManager
) : ViewModel() {

    private val _settings = MutableLiveData<ProbabilitiesSettingsEntity?>()
    val settings: LiveData<ProbabilitiesSettingsEntity?> = _settings

    fun loadSettings(instanceId: Int) {
        viewModelScope.launch {
            val mode = preferencesManager.loadMode(instanceId)
            _settings.value = ProbabilitiesSettingsEntity(mode = mode, instanceId = instanceId)
        }
    }

    fun updateMode(instanceId: Int, newMode: ProbabilitiesMode) {
        viewModelScope.launch {
            preferencesManager.saveMode(instanceId, newMode)
            _settings.value = _settings.value?.copy(mode = newMode)
        }
    }

    fun cloneSettingsFrom(fromInstanceId: Int, toInstanceId: Int) {
        viewModelScope.launch {
            val fromMode = preferencesManager.loadMode(fromInstanceId)
            preferencesManager.saveMode(toInstanceId, fromMode)

            // Clone other settings based on mode
            when (fromMode) {
                ProbabilitiesMode.DICE -> {
                    val diceSettings = preferencesManager.loadDiceSettings(fromInstanceId)
                    diceSettings?.let {
                        preferencesManager.saveDiceSettings(toInstanceId, it)
                    }
                }
                ProbabilitiesMode.COIN_FLIP -> {
                    val coinSettings = preferencesManager.loadCoinSettings(fromInstanceId)
                    coinSettings?.let {
                        preferencesManager.saveCoinSettings(toInstanceId, it)
                    }
                }
            }
        }
    }
}

data class ProbabilitiesSettingsEntity(
    val mode: ProbabilitiesMode,
    val instanceId: Int
)