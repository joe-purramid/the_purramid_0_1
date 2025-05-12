// DiceViewModel.kt
package com.example.purramid.thepurramid.randomizers.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.data.db.DEFAULT_DICE_POOL_JSON
import com.example.purramid.thepurramid.data.db.DEFAULT_EMPTY_JSON_MAP
import com.example.purramid.thepurramid.data.db.RandomizerDao
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity
import com.example.purramid.thepurramid.randomizers.DiceSumResultType
import com.example.purramid.thepurramid.randomizers.ui.DicePoolDialogFragment // For D10_KEYS
import com.example.purramid.thepurramid.util.Event
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

// Original raw rolls: Map<Sides, List<IndividualRollValue>>
typealias RawDiceRolls = Map<Int, List<Int>>

// Data class to hold all processed results for UI and announcement
data class ProcessedDiceResult(
    val isPercentile: Boolean,
    val rawRolls: RawDiceRolls?, // For standard pool, or component rolls for percentile (00-90, 0-9)
    val percentileValue: Int?, // Final summed (and modified) percentile value (1-100+)
    val individualModifiedRolls: Map<Int, List<Int>>?, // Standard pool: Raw roll + modifier for that die type
    val sumPerTypeWithModifier: Map<Int, Int>?, // Standard pool: Sum of (raw rolls for type) + modifier for that type
    val totalSumWithModifiers: Int?, // Standard pool: Grand total
    val sumResultType: DiceSumResultType,
    val announcementString: String,
    val singleModifierAppliedToPercentile: Int? = null // Store the modifier value if applied to percentile
)

@HiltViewModel
class DiceViewModel @Inject constructor(
    private val randomizerDao: RandomizerDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        const val KEY_INSTANCE_ID = "instanceId"
        private const val TAG = "DiceViewModel"
        const val PERCENTILE_MODIFIER_KEY = 10 // Key in modifier map for the single percentile modifier
    }

    private val instanceId: UUID? = savedStateHandle.get<String>(KEY_INSTANCE_ID)?.let {
        try { UUID.fromString(it) } catch (e: IllegalArgumentException) { null }
    }

    private val _settings = MutableLiveData<SpinSettingsEntity?>()
    val settings: LiveData<SpinSettingsEntity?> = _settings

    private val _processedDiceResult = MutableLiveData<ProcessedDiceResult?>()
    val processedDiceResult: LiveData<ProcessedDiceResult?> = _processedDiceResult

    // These LiveData hold the raw, unmodified values primarily for visual display of die faces
    private val _rawDicePoolResults = MutableLiveData<RawDiceRolls?>(null)
    val rawDicePoolResults: LiveData<RawDiceRolls?> = _rawDicePoolResults

    private val _rawPercentileResultComponents = MutableLiveData<Pair<Int, Int>?>(null) // Pair(tens roll, units roll)
    val rawPercentileResultComponents: LiveData<Pair<Int, Int>?> = _rawPercentileResultComponents


    private val _errorEvent = MutableLiveData<Event<String>>()
    val errorEvent: LiveData<Event<String>> = _errorEvent

    private val gson = Gson()

    init {
        if (instanceId != null) {
            loadSettings(instanceId)
        } else {
            Log.e(TAG, "Critical Error: Instance ID is null or invalid.")
            _errorEvent.postValue(Event("Invalid Instance ID"))
            _settings.postValue(null)
        }
    }

    private fun loadSettings(id: UUID) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val loadedSettings = randomizerDao.getSettingsForInstance(id)
                withContext(Dispatchers.Main) {
                    _settings.value = loadedSettings
                    if (loadedSettings == null) {
                        Log.w(TAG, "No settings found for instance $id.")
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

    fun rollDice() {
        val currentSettings = _settings.value
        if (currentSettings == null) {
            Log.e(TAG, "Cannot roll dice, settings not loaded.")
            _errorEvent.value = Event("Settings not available")
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            val modifiers = parseDiceModifierConfig(currentSettings.diceModifierConfigJson) ?: emptyMap()
            var finalProcessedResult: ProcessedDiceResult? = null

            if (currentSettings.isPercentileDiceEnabled) {
                val tensDieRoll = Random.nextInt(10) // 0-9 (for display/component)
                val unitsDieRoll = Random.nextInt(10) // 0-9 (for display/component)

                val tensValueForSum = tensDieRoll * 10 // 00, 10, ... 90
                val unitsValueForSum = unitsDieRoll    // 0, 1, ... 9

                val rawPercentileSum = if (tensValueForSum == 0 && unitsValueForSum == 0) 100 else tensValueForSum + unitsValueForSum

                // Apply the single modifier for percentile dice (using key 10 for d10 modifier)
                val percentileModifier = modifiers[PERCENTILE_MODIFIER_KEY] ?: 0
                val finalModifiedPercentileSum = rawPercentileSum + percentileModifier

                val componentRolls = mapOf(
                    DicePoolDialogFragment.D10_TENS_KEY to listOf(tensDieRoll), // Store 0-9 for display
                    DicePoolDialogFragment.D10_UNITS_KEY to listOf(unitsDieRoll)  // Store 0-9 for display
                )

                finalProcessedResult = ProcessedDiceResult(
                    isPercentile = true,
                    rawRolls = componentRolls, // Store component die rolls for visual display
                    percentileValue = finalModifiedPercentileSum, // This is the final result (sum + modifier)
                    individualModifiedRolls = null,
                    sumPerTypeWithModifier = mapOf(100 to finalModifiedPercentileSum), // Use a distinct key for the sum
                    totalSumWithModifiers = finalModifiedPercentileSum,
                    sumResultType = currentSettings.diceSumResultType, // Should effectively be INDIVIDUAL for percentile display
                    announcementString = formatAnnouncement(
                        isPercentile = true,
                        percentileValue = finalModifiedPercentileSum,
                        rawRolls = null, // Not used for percentile announcement formatting
                        sumType = currentSettings.diceSumResultType,
                        modifiers = modifiers, // Pass all modifiers for context if needed
                        percentileComponentRawRolls = componentRolls, // Pass component rolls
                        singlePercentileModifierApplied = percentileModifier
                    ),
                    singleModifierAppliedToPercentile = percentileModifier
                )
                withContext(Dispatchers.Main) {
                    _rawDicePoolResults.value = null
                    _rawPercentileResultComponents.value = Pair(tensDieRoll, unitsDieRoll) // Post raw components for display
                    _processedDiceResult.value = finalProcessedResult
                }

            } else {
                // Standard Dice Pool Roll
                val poolConfig = parseDicePoolConfig(currentSettings.dicePoolConfigJson)
                if (poolConfig.isNullOrEmpty()) {
                    Log.w(TAG, "Dice pool is empty or failed to parse.")
                    finalProcessedResult = ProcessedDiceResult(false, emptyMap(), null, emptyMap(), emptyMap(),0, currentSettings.diceSumResultType, "No dice in pool.")
                    withContext(Dispatchers.Main) {
                        _rawDicePoolResults.value = emptyMap()
                        _rawPercentileResultComponents.value = null
                        _processedDiceResult.value = finalProcessedResult
                    }
                    return@launch
                }

                val currentRawRolls = mutableMapOf<Int, MutableList<Int>>()
                val currentIndividualModifiedRolls = mutableMapOf<Int, MutableList<Int>>()
                val currentSumPerTypeWithModifier = mutableMapOf<Int, Int>()
                var currentTotalSumWithModifiers = 0

                poolConfig.forEach { (sides, count) ->
                    if (count > 0 && sides > 0) {
                        val rollsForThisType = mutableListOf<Int>()
                        val modifiedRollsForThisType = mutableListOf<Int>()
                        var sumForThisTypeRaw = 0
                        val modifier = modifiers[sides] ?: 0

                        repeat(count) {
                            val roll = Random.nextInt(1, sides + 1)
                            rollsForThisType.add(roll)
                            modifiedRollsForThisType.add(roll + modifier)
                            sumForThisTypeRaw += roll
                        }
                        currentRawRolls[sides] = rollsForThisType
                        currentIndividualModifiedRolls[sides] = modifiedRollsForThisType
                        val sumForThisTypeWithMod = sumForThisTypeRaw + modifier
                        currentSumPerTypeWithModifier[sides] = sumForThisTypeWithMod
                        currentTotalSumWithModifiers += sumForThisTypeWithMod
                    }
                }

                finalProcessedResult = ProcessedDiceResult(
                    isPercentile = false,
                    rawRolls = currentRawRolls,
                    percentileValue = null,
                    individualModifiedRolls = currentIndividualModifiedRolls,
                    sumPerTypeWithModifier = currentSumPerTypeWithModifier,
                    totalSumWithModifiers = currentTotalSumWithModifiers,
                    sumResultType = currentSettings.diceSumResultType,
                    announcementString = formatAnnouncement(
                        isPercentile = false,
                        percentileValue = null,
                        rawRolls = currentRawRolls,
                        sumType = currentSettings.diceSumResultType,
                        modifiers = modifiers,
                        percentileComponentRawRolls = null,
                        singlePercentileModifierApplied = null
                    )
                )
                withContext(Dispatchers.Main) {
                    _rawDicePoolResults.value = currentRawRolls
                    _rawPercentileResultComponents.value = null
                    _processedDiceResult.value = finalProcessedResult
                }
            }
        }
    }

    private fun formatAnnouncement(
        isPercentile: Boolean,
        percentileValue: Int?, // This is the final, modified percentile value
        rawRolls: RawDiceRolls?,
        sumType: DiceSumResultType,
        modifiers: Map<Int, Int>,
        percentileComponentRawRolls: RawDiceRolls?, // Map of D10_TENS_KEY/D10_UNITS_KEY to their single raw roll (0-9)
        singlePercentileModifierApplied: Int?
    ): String {
        if (isPercentile) {
            val tensDieDisplay = percentileComponentRawRolls?.get(DicePoolDialogFragment.D10_TENS_KEY)?.firstOrNull() ?: 0
            val unitsDieDisplay = percentileComponentRawRolls?.get(DicePoolDialogFragment.D10_UNITS_KEY)?.firstOrNull() ?: 0
            val modifierValue = singlePercentileModifierApplied ?: 0
            val rawSum = (if (tensDieDisplay == 0 && unitsDieDisplay == 0) 100 else (tensDieDisplay * 10) + unitsDieDisplay)
            // The percentileValue already includes the modifier
            val finalResult = percentileValue ?: (rawSum + modifierValue)

            return "d%: ${tensDieDisplay * 10} + $unitsDieDisplay ${if (modifierValue != 0) "(${if (modifierValue > 0) "+" else ""}$modifierValue)" else ""} = $finalResult%"
        }

        if (rawRolls.isNullOrEmpty()) return "No results."

        return when (sumType) {
            DiceSumResultType.INDIVIDUAL -> {
                rawRolls.entries.filter { it.value.isNotEmpty() }.joinToString("; ") { (sides, rolls) ->
                    val modifier = modifiers[sides] ?: 0
                    val modifiedRollsString = rolls.joinToString(", ") { roll ->
                        "${roll + modifier} (${roll}${if (modifier != 0) "${if (modifier > 0) "+" else ""}$modifier" else ""})"
                    }
                    "${rolls.size}d$sides: $modifiedRollsString"
                }
            }
            DiceSumResultType.SUM_TYPE -> {
                rawRolls.entries.filter { it.value.isNotEmpty() }.joinToString("; ") { (sides, rolls) ->
                    val modifier = modifiers[sides] ?: 0
                    val sum = rolls.sum() + modifier
                    "${rolls.size}d$sides Sum: $sum"
                }
            }
            DiceSumResultType.SUM_TOTAL -> {
                var totalSum = 0
                rawRolls.forEach { (sides, rolls) ->
                    if (rolls.isNotEmpty()) {
                        val modifier = modifiers[sides] ?: 0
                        totalSum += rolls.sum() + modifier
                    }
                }
                "Total Sum: $totalSum"
            }
        }
    }

    fun parseDicePoolConfig(json: String?): Map<Int, Int>? {
        if (json.isNullOrEmpty()) return parseDicePoolConfig(DEFAULT_DICE_POOL_JSON) // Fallback to default if empty
        return try {
            gson.fromJson(json, object : TypeToken<Map<Int, Int>>() {}.type)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing dice pool config: $json", e)
            parseDicePoolConfig(DEFAULT_DICE_POOL_JSON) // Fallback to default on error
        }
    }

    private fun parseDiceModifierConfig(json: String?): Map<Int, Int>? {
        if (json.isNullOrEmpty() || json == DEFAULT_EMPTY_JSON_MAP) return emptyMap()
        return try {
            gson.fromJson(json, object : TypeToken<Map<Int, Int>>() {}.type)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing dice modifier config: $json", e)
            emptyMap()
        }
    }

    fun handleManualClose() {
        instanceId?.let {
            viewModelScope.launch(Dispatchers.IO) {
                Log.d(TAG, "Manual close for instance: $it. Deleting settings and instance record.")
                randomizerDao.deleteSettingsForInstance(it)
                randomizerDao.deleteInstance(RandomizerInstanceEntity(instanceId = it))
            }
        }
    }

    override fun onCleared() {
        Log.d(TAG, "ViewModel instance $instanceId cleared.")
        super.onCleared()
    }

    fun clearErrorEvent() {
        _errorEvent.value = Event(null)
    }
}
