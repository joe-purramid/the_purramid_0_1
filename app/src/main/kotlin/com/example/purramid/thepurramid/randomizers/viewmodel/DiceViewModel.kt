// DiceViewModel.kt
package com.example.purramid.thepurramid.randomizers.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purramid.thepurramid.R // Import R for error strings if needed
import com.example.purramid.thepurramid.data.db.DEFAULT_DICE_POOL_JSON
import com.example.purramid.thepurramid.data.db.DEFAULT_EMPTY_JSON_MAP
import com.example.purramid.thepurramid.data.db.RandomizerDao
import com.example.purramid.thepurramid.data.db.RandomizerInstanceEntity
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity
import com.example.purramid.thepurramid.util.Event // Assuming you have an Event wrapper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

// Data class to hold structured dice results (optional, but good practice)
// Key: Number of sides (e.g., 4, 6, 20), Value: List of results for that die type
typealias DiceRollResults = Map<Int, List<Int>>

@HiltViewModel
class DiceViewModel @Inject constructor(
    private val randomizerDao: RandomizerDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        // Key to get instanceId from SavedStateHandle (passed via NavArgs)
        // Should match the key used elsewhere (e.g., RandomizerSettingsViewModel.KEY_INSTANCE_ID)
        const val KEY_INSTANCE_ID = "instanceId"
        private const val TAG = "DiceViewModel" // For logging
    }

    // --- Instance ID ---
    private val instanceId: UUID? = savedStateHandle.get<String>(KEY_INSTANCE_ID)?.let {
        try { UUID.fromString(it) } catch (e: IllegalArgumentException) { null }
    }

    // --- Settings ---
    // Holds the full settings for this specific dice instance
    private val _settings = MutableLiveData<SpinSettingsEntity?>()
    val settings: LiveData<SpinSettingsEntity?> = _settings

    // --- Results ---
    // Holds the results for standard dice pool rolls
    private val _dicePoolResults = MutableLiveData<DiceRollResults?>(null)
    val dicePoolResults: LiveData<DiceRollResults?> = _dicePoolResults

    // Holds the result for a percentile (d%) roll
    private val _percentileResult = MutableLiveData<Int?>(null)
    val percentileResult: LiveData<Int?> = _percentileResult

    // --- Error Handling ---
    private val _errorEvent = MutableLiveData<Event<String>>() // Use String for flexibility
    val errorEvent: LiveData<Event<String>> = _errorEvent

    // --- Gson for JSON Parsing ---
    private val gson = Gson()

    init {
        if (instanceId != null) {
            loadSettings(instanceId)
        } else {
            Log.e(TAG, "Critical Error: Instance ID is null or invalid.")
            _errorEvent.postValue(Event("Invalid Instance ID")) // Post error
            _settings.postValue(null) // Ensure settings are null
        }
    }

    /**
     * Loads the settings for the current instanceId from the database.
     */
    private fun loadSettings(id: UUID) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val loadedSettings = randomizerDao.getSettingsForInstance(id)
                withContext(Dispatchers.Main) {
                    _settings.value = loadedSettings
                    if (loadedSettings == null) {
                        Log.w(TAG, "No settings found for instance $id, defaults might apply.")
                        // Optionally post an info event or handle default creation if needed
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading settings for instance $id", e)
                withContext(Dispatchers.Main) {
                    _errorEvent.value = Event("Failed to load settings")
                    _settings.value = null
                }
            }
        }
    }

    /**
     * Performs a dice roll based on the current settings (percentile or standard pool).
     * Updates the corresponding LiveData with the results.
     */
    fun rollDice() {
        val currentSettings = _settings.value
        if (currentSettings == null) {
            Log.e(TAG, "Cannot roll dice, settings not loaded.")
            _errorEvent.value = Event("Settings not available")
            return
        }

        viewModelScope.launch(Dispatchers.Default) { // Use Default dispatcher for computation
            if (currentSettings.isPercentileDiceEnabled) {
                // --- Percentile Roll (d%) ---
                val tensDie = Random.nextInt(10) // 0-9
                val unitsDie = Random.nextInt(10) // 0-9

                val tensValue = tensDie * 10
                val unitsValue = unitsDie

                // Special case: 00 + 0 = 100
                val finalResult = if (tensValue == 0 && unitsValue == 0) 100 else tensValue + unitsValue

                Log.d(TAG, "Rolled Percentile: Tens=$tensValue, Units=$unitsValue -> Result=$finalResult")

                withContext(Dispatchers.Main) {
                    _percentileResult.value = finalResult
                    _dicePoolResults.value = null // Clear other result type
                }

            } else {
                // --- Standard Dice Pool Roll ---
                val poolConfig = parseDicePoolConfig(currentSettings.dicePoolConfigJson)
                if (poolConfig.isNullOrEmpty()) {
                     Log.w(TAG, "Dice pool is empty or failed to parse. Cannot roll.")
                     // Optionally post an error/info event
                     // Clear results on main thread
                     withContext(Dispatchers.Main) {
                         _dicePoolResults.value = emptyMap()
                         _percentileResult.value = null
                     }
                     return@launch
                }

                val resultsMap = mutableMapOf<Int, MutableList<Int>>()

                poolConfig.forEach { (sides, count) ->
                    if (count > 0 && sides > 0) { // Ensure valid sides and count
                        val rolls = mutableListOf<Int>()
                        repeat(count) {
                            rolls.add(Random.nextInt(1, sides + 1)) // Roll 1 to sides
                        }
                        resultsMap[sides] = rolls
                    }
                }

                Log.d(TAG, "Rolled Dice Pool: $resultsMap")

                withContext(Dispatchers.Main) {
                    _dicePoolResults.value = resultsMap
                    _percentileResult.value = null // Clear other result type
                }
            }
        }
    }

    /**
     * Safely parses the dice pool configuration JSON string.
     * Returns a Map<Int, Int> or null if parsing fails or string is invalid.
     */
    private fun parseDicePoolConfig(json: String?): Map<Int, Int>? {
        if (json.isNullOrEmpty()) {
            // Use default if json is null or empty? Or return null?
            // Let's return null to indicate empty/invalid config explicitly
            Log.w(TAG, "Dice pool config JSON is null or empty.")
            return null
        }
        return try {
            val mapType = object : TypeToken<Map<Int, Int>>() {}.type
            gson.fromJson(json, mapType)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse dice pool config JSON: $json", e)
            null // Return null on parsing error
        }
    }

    fun handleManualClose() {
        instanceId?.let { idToClose ->
            Log.d(TAG, "handleManualClose called for instanceId: $idToClose")
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    randomizerDao.deleteSettingsForInstance(idToClose)
                    randomizerDao.deleteInstance(RandomizerInstanceEntity(instanceId = idToClose))
                    Log.d(TAG, "Successfully deleted settings and instance record for $idToClose from DB.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting data for instance $idToClose from DB", e)
                }
            }
        } ?: Log.w(TAG, "handleManualClose called but instanceId is null.")
    }

    override fun onCleared() {
        Log.d(TAG, "DiceViewModel onCleared for instanceId: $instanceId")
        super.onCleared()
        // If you had manual jobs not in viewModelScope, you'd cancel them here:
        // myCustomJob?.cancel()
    }

    /**
     * Clears any displayed error event.
     */
    fun clearErrorEvent() {
        _errorEvent.value = Event(null) // Clear the event content
    }
}