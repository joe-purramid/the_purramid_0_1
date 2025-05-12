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
    val d20CritsRolled: Int = 0
)

@HiltViewModel
class DiceViewModel @Inject constructor(
    private val randomizerDao: RandomizerDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        const val KEY_INSTANCE_ID = "instanceId"
        private const val TAG = "DiceViewModel"
        const val PERCENTILE_DIE_TYPE_KEY = -100 // Arbitrary distinct key for d%
        const val D10_MODIFIER_KEY = 10 // Modifier for a d10 is used for percentile
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
            val poolConfig = parseDicePoolConfig(currentSettings.dicePoolConfigJson)
                ?: parseDicePoolConfig(DEFAULT_DICE_POOL_JSON) ?: emptyMap()

            val currentRawRolls = mutableMapOf<Int, MutableList<Int>>()
            val currentIndividualModifiedRolls = mutableMapOf<Int, MutableList<Int>>()
            val currentSumPerTypeWithModifier = mutableMapOf<Int, Int>()
            var currentTotalSumWithModifiers = 0
            var d20CritCount = 0

            val rolledPercentiles = mutableListOf<PercentileRollDetail>()

            poolConfig.forEach { (sidesOrKey, count) ->
                if (count <= 0) return@forEach // Skip if count is zero or less

                if (sidesOrKey == PERCENTILE_DIE_TYPE_KEY) {
                    // Handle Percentile Dice Rolls
                    val percentileModifier = modifiers[D10_MODIFIER_KEY] ?: 0 // Use d10 modifier for percentile
                    repeat(count) {
                        val tensDieRoll = Random.nextInt(10) // 00-90
                        val unitsDieRoll = Random.nextInt(10) // 0-9
                        val tensValueForSum = tensDieRoll * 10
                        val unitsValueForSum = unitsDieRoll
                        val rawSum = if (tensValueForSum == 0 && unitsValueForSum == 0) 100 else tensValueForSum + unitsValueForSum
                        val finalValue = rawSum + percentileModifier

                        rolledPercentiles.add(
                            PercentileRollDetail(
                                tensDieRoll = tensDieRoll, // Store 00-90 for display
                                unitsDieRoll = unitsDieRoll, // Store 0-9 for display
                                rawSum = rawSum,
                                modifierApplied = percentileModifier,
                                finalValue = finalValue
                            )
                        )
                        // Note: Percentile results are typically not added to the "total sum" of other dice
                        // unless explicitly specified by a game system. We'll keep them separate for now.
                    }
                } else if (sidesOrKey > 0) {
                    // Handle Standard Dice Rolls (d4, d6, d20, etc.)
                    val rollsForThisType = mutableListOf<Int>()
                    val modifiedRollsForThisType = mutableListOf<Int>()
                    var sumForThisTypeRaw = 0
                    val modifier = modifiers[sidesOrKey] ?: 0

                    repeat(count) {
                        val roll = Random.nextInt(1, sidesOrKey + 1)
                        rollsForThisType.add(roll)
                        modifiedRollsForThisType.add(roll + modifier)
                        sumForThisTypeRaw += roll
                        if (sidesOrKey == 20 && roll == 20) {
                            d20CritCount++
                        }
                    }
                    currentRawStandardRolls[sidesOrKey] = rollsForThisType
                    currentIndividualModifiedRolls[sidesOrKey] = modifiedRollsForThisType
                    val sumForThisTypeWithMod = sumForThisTypeRaw + modifier
                    currentSumPerTypeWithModifier[sidesOrKey] = sumForThisTypeWithMod
                    currentTotalSumWithModifiers += sumForThisTypeWithMod
                }
            }
            val announcement = formatCombinedAnnouncement(
                standardRolls = currentRawStandardRolls,
                percentileRolls = rolledPercentiles,
                sumType = currentSettings.diceSumResultType,
                modifiers = modifiers
            )

            val finalProcessedResult = ProcessedDiceResult(
                standardDiceRolls = currentRawStandardRolls,
                percentileResults = rolledPercentiles,
                individualModifiedRolls = currentIndividualModifiedRolls,
                sumPerTypeWithModifier = currentSumPerTypeWithModifier,
                totalSumWithModifiers = currentTotalSumWithModifiers,
                sumResultType = currentSettings.diceSumResultType,
                announcementString = announcement,
                d20CritsRolled = d20CritCount
            )

            withContext(Dispatchers.Main) {
                _rawStandardDiceRollsForDisplay.value = currentRawStandardRolls
                _rawPercentileRollsForDisplay.value = rolledPercentiles.map { Pair(it.tensDieRoll, it.unitsDieRoll) }
                _processedDiceResult.value = finalProcessedResult
            }
        }
    }

    private fun formatCombinedAnnouncement(
        standardRolls: RawDiceRolls,
        percentileRolls: List<PercentileRollDetail>,
        sumType: DiceSumResultType,
        modifiers: Map<Int, Int>
    ): String {
        val announcements = mutableListOf<String>()

        // Format standard dice
        if (standardRolls.isNotEmpty()) {
            val standardDiceAnnouncement = when (sumType) {
                DiceSumResultType.INDIVIDUAL -> {
                    standardRolls.entries.filter { it.value.isNotEmpty() }.joinToString("; ") { (sides, rolls) ->
                        val modifier = modifiers[sides] ?: 0
                        val modifiedRollsString = rolls.joinToString(", ") { roll ->
                            "${roll + modifier} (${roll}${if (modifier != 0) "${if (modifier > 0) "+" else ""}$modifier" else ""})"
                        }
                        "${rolls.size}d$sides: $modifiedRollsString"
                    }
                }
                DiceSumResultType.SUM_TYPE -> {
                    standardRolls.entries.filter { it.value.isNotEmpty() }.joinToString("; ") { (sides, rolls) ->
                        val modifier = modifiers[sides] ?: 0
                        val sum = rolls.sum() + modifier
                        "${rolls.size}d$sides Sum: $sum"
                    }
                }
                DiceSumResultType.SUM_TOTAL -> {
                    var totalSum = 0
                    standardRolls.forEach { (sides, rolls) ->
                        if (rolls.isNotEmpty()) {
                            val modifier = modifiers[sides] ?: 0
                            totalSum += rolls.sum() + modifier
                        }
                    }
                    "Standard Dice Total: $totalSum" // Label clearly
                }
            }
            if (standardDiceAnnouncement.isNotBlank()) {
                announcements.add(standardDiceAnnouncement)
            }
        }

        // Format percentile dice
        if (percentileRolls.isNotEmpty()) {
            val percentileAnnouncements = percentileRolls.map { pRoll ->
                // For percentile, "Individual", "Sum Type", and "Sum Total" all effectively mean showing the final value.
                // The component dice are more for visual representation.
                "d%: ${pRoll.tensDieRoll * 10} + ${pRoll.unitsDieRoll} ${if (pRoll.modifierApplied != 0) "(${if (pRoll.modifierApplied > 0) "+" else ""}${pRoll.modifierApplied})" else ""} = ${pRoll.finalValue}%"
            }
            announcements.addAll(percentileAnnouncements)
        }

        return if (announcements.isEmpty()) "No dice rolled or pool empty." else announcements.joinToString("\n")
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
