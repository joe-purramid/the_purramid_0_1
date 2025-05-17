// DiceViewModel.kt
package com.example.purramid.thepurramid.randomizers.viewmodel

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.data.db.DEFAULT_DICE_POOL_JSON
import com.example.purramid.thepurramid.data.db.DEFAULT_EMPTY_JSON_MAP
import com.example.purramid.thepurramid.data.db.DEFAULT_SETTINGS_ID
import com.example.purramid.thepurramid.data.db.RandomizerDao
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity
import com.example.purramid.thepurramid.randomizers.DiceSumResultType
import com.example.purramid.thepurramid.randomizers.GraphDistributionType
import com.example.purramid.thepurramid.randomizers.RandomizerInstanceManager
import com.example.purramid.thepurramid.randomizers.RandomizerMode
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

// Percentile setting On
data class PercentileRollDetail(
    val tensDieRoll: Int,
    val unitsDieRoll: Int,
    val rawSum: Int,
    val modifierApplied: Int,
    val finalValue: Int
)

// Data class to hold all processed results for UI and announcement
data class FullProcessedDiceResult(
    val standardDiceRolls: Map<Int, List<Int>>,
    val rawRolls: RawDiceRolls?, // For standard pool, or component rolls for percentile (00-90, 0-9)
    val isPercentile: Boolean,
    val percentileResults: List<PercentileRollDetail>,
    val individualModifiedRolls: Map<Int, List<Int>>,
    val sumPerTypeWithModifier: Map<Int, Int>,
    val totalSumWithModifiers: Int,
    val sumResultType: String, // From DiceSumResultType.kt
    val percentileValue: Int?, // Final summed (and modified) percentile value (1-100+)
    val singleModifierAppliedToPercentile: Int? = null // Store the modifier value if applied to percentile
    val announcementString: String,
    val d20CritsRolled: Int
)

// Define data structures for graph display
data class GraphDataPoint(val value: Int, val frequency: Int, val label: String = value.toString())

sealed class DiceGraphDisplayData {
    data class BarData(
        val dataSetLabel: String,
        val points: List<GraphDataPoint>
    ) : DiceGraphDisplayData()

    // You can add other types like LineData, PieData etc. as needed
    // For INDIVIDUAL_DICE_TYPES, you might have a list of BarData or a more complex structure
    data class GroupedBarData(
        val groupedDataSets: Map<String, List<GraphDataPoint>> // e.g., "D6" -> List of points for D6 faces
    ) : DiceGraphDisplayData()

    object Empty : DiceGraphDisplayData()
    object NotApplicable : DiceGraphDisplayData() // For when graph type is not set or invalid
}

@HiltViewModel
class DiceViewModel @Inject constructor(
    application: Application,
    private val randomizerDao: RandomizerDao,
    private val sharedPreferences: SharedPreferences,
    private val savedStateHandle: SavedStateHandle
) : RandomizerViewModel(application, randomizerDao, sharedPreferences, savedStateHandle) {

    companion object {
        const val KEY_INSTANCE_ID = "instanceId"
        private const val TAG = "DiceViewModel"
        const val PERCENTILE_DIE_TYPE_KEY = -100 // Arbitrary distinct key for d%
        const val D10_MODIFIER_KEY = 10 // Modifier for a d10 is used for percentile
        // Key for saving/restoring graph accumulated data if needed across process death,
        private const val SAVED_STATE_GRAPH_SUM_FREQUENCIES = "graphSumFrequencies"
        private const val SAVED_STATE_GRAPH_INDIVIDUAL_FREQUENCIES = "graphIndividualFrequencies"
        private const val SAVED_STATE_GRAPH_FLIP_COUNT_HISTORY = "graphFlipCountHistory"
    }

    private val instanceId: UUID? = savedStateHandle.get<String>(KEY_INSTANCE_ID)?.let {
        try { UUID.fromString(it) } catch (e: IllegalArgumentException) { null }
    }

    private val gson = Gson()

    // LiveData for raw dice pool results
    private val _rawDicePoolResults = MutableLiveData<RawDiceRolls?>(null)
    val rawDicePoolResults: LiveData<RawDiceRolls?> = _rawDicePoolResults

    // LiveData for raw percentile components
    private val _rawPercentileResultComponents = MutableLiveData<Pair<Int, Int>?>(null) // Pair(tens roll, units roll)
    val rawPercentileResultComponents: LiveData<Pair<Int, Int>?> = _rawPercentileResultComponents

    // Processed result including announcement string and crit tracking
    private val _processedDiceResult = MediatorLiveData<ProcessedDiceResult?>()
    val processedDiceResult: LiveData<ProcessedDiceResult?> = _processedDiceResult

    // --- Graph Data ---
    private val _diceGraphData = MutableLiveData<DiceGraphDisplayData>(DiceGraphDisplayData.Empty)
    val diceGraphData: LiveData<DiceGraphDisplayData> = _diceGraphData

    // Internal accumulation structures for graph data
    // These will be updated with each roll if graph is enabled.
    private var sumFrequencies: MutableMap<Int, Int> = mutableMapOf()
    // Key: Dice Sides (e.g., 6 for D6), Value: Map<Face Value, Frequency>
    private var individualDiceFaceFrequencies: MutableMap<Int, MutableMap<Int, Int>> = mutableMapOf()
    private var currentGraphRollEventsCount: Int = 0 // Number of "roll" actions recorded for the graph

    init {
        // Initialize settings observation from RandomizerViewModel
        // The settings LiveData from the parent class will trigger updates.
        // Restore graph data if available in savedStateHandle (e.g., after process death)
        restoreGraphDataFromSavedState()


        // Logic for _processedDiceResult
        _processedDiceResult.addSource(_rawDicePoolResults) { results ->
            results?.let {
                val currentSettings = settings.value ?: getDefaultSettingsBlocking(instanceId.value.toString())
                if (currentSettings.isPercentileDiceEnabled) return@addSource // Handled by percentile observer
                _processedDiceResult.value = processDicePoolRoll(it, currentSettings)
                // NEW: Update graph data after processing pool results
                if (currentSettings.isDiceGraphEnabled) {
                }
            } ?: run { _processedDiceResult.value = null }
        }
        _processedDiceResult.addSource(_rawPercentileResultComponents) { components ->
            components?.let {
                val currentSettings = settings.value ?: getDefaultSettingsBlocking(instanceId.value.toString())
                if (!currentSettings.isPercentileDiceEnabled) return@addSource
                _processedDiceResult.value = processPercentileRoll(it, currentSettings)
                // Percentile dice currently don't have a specific graph distribution defined.
                // If they were to be graphed, logic would go here. For now, graph updates on pool results.
            } ?: run { _processedDiceResult.value = null }
        }
        _processedDiceResult.addSource(settings) { currentSettings ->
            // If settings change (e.g., graph enabled/disabled, type changed),
            // we might need to re-evaluate or clear graph data.
            if (currentSettings != null) {
                if (!currentSettings.isDiceGraphEnabled) {
                    // If graph gets disabled, clear displayed data (but maybe keep accumulated until manual reset?)
                    _diceGraphData.value = DiceGraphDisplayData.Empty
                } else {
                    // If graph is enabled, or type changes, regenerate display data from current accumulation
                    regenerateGraphDisplayData(currentSettings)
                }
                // Update processed result if settings affecting it change
                refreshProcessedResultsOnSettingsChange(currentSettings)
            }
        }
    }

    private fun refreshProcessedResultsOnSettingsChange(currentSettings: SpinSettingsEntity) {
        if (currentSettings.isPercentileDiceEnabled) {
            _rawPercentileResultComponents.value?.let {
                _processedDiceResult.value = processPercentileRoll(it, currentSettings)
            }
        } else {
            _rawDicePoolResults.value?.let {
                _processedDiceResult.value = processDicePoolRoll(it, currentSettings)
                if (currentSettings.isDiceGraphEnabled) {
                    updateGraphData(it, currentSettings)
                }
            }
        }
    }

    private fun restoreGraphDataFromSavedState() {
        savedStateHandle.get<String>(SAVED_STATE_GRAPH_SUM_FREQUENCIES)?.let { json ->
            try {
                sumFrequencies = gson.fromJson(json, object : TypeToken<MutableMap<Int, Int>>() {}.type) ?: mutableMapOf()
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring sumFrequencies from SavedStateHandle", e)
            }
        }
        savedStateHandle.get<String>(SAVED_STATE_GRAPH_INDIVIDUAL_FREQUENCIES)?.let { json ->
            try {
                individualDiceFaceFrequencies = gson.fromJson(json, object : TypeToken<MutableMap<Int, MutableMap<Int, Int>>>() {}.type) ?: mutableMapOf()
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring individualDiceFaceFrequencies from SavedStateHandle", e)
            }
        }
        currentGraphRollEventsCount = savedStateHandle.get<Int>(SAVED_STATE_GRAPH_FLIP_COUNT_HISTORY) ?: 0

        // After restoring, if graph is enabled, regenerate display data
        settings.value?.let {
            if (it.isDiceGraphEnabled) {
                regenerateGraphDisplayData(it)
            }
        }
    }

    private fun saveGraphDataToSavedState() {
        try {
            savedStateHandle[SAVED_STATE_GRAPH_SUM_FREQUENCIES] = gson.toJson(sumFrequencies)
            savedStateHandle[SAVED_STATE_GRAPH_INDIVIDUAL_FREQUENCIES] = gson.toJson(individualDiceFaceFrequencies)
            savedStateHandle[SAVED_STATE_GRAPH_FLIP_COUNT_HISTORY] = currentGraphRollEventsCount
        } catch (e: Exception) {
            Log.e(TAG, "Error saving graph data to SavedStateHandle", e)
        }
    }

    private val _errorEvent = MutableLiveData<Event<String>>()
    val errorEvent: LiveData<Event<String>> = _errorEvent

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

            val currentRawStandardRolls = mutableMapOf<Int, MutableList<Int>>()
            val currentIndividualModifiedRolls = mutableMapOf<Int, MutableList<Int>>()
            val currentSumPerTypeWithModifier = mutableMapOf<Int, Int>()
            var currentTotalSumWithModifiers = 0
            var d20CritCount = 0
            val rolledPercentiles = mutableListOf<PercentileRollDetail>()
            val PERCENTILE_DIE_TYPE_KEY = -100 // Placeholder - ensure this key is correctly defined
            val D10_MODIFIER_KEY = -10 // Placeholder - ensure this key is correctly defined

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
                    // val sumForThisTypeWithMod = sumForThisTypeRaw + modifier
                    currentSumPerTypeWithModifier[sidesOrKey] = sumForThisTypeRaw + modifier // If modifier is per type once
                    currentTotalSumWithModifiers += currentSumPerTypeWithModifier[sidesOrKey]!!
                }
            }

            // *** INTEGRATION POINT FOR GRAPH DATA UPDATE ***
            if (!currentSettings.isPercentileDiceEnabled && currentSettings.isDiceGraphEnabled) {
                // Convert currentRawStandardRolls to the DiceRollResults format expected by updateGraphData
                // The DiceRollResults class I defined was: data class DiceRollResults(val entries: Map<Int, List<Int>>)
                // Your currentRawStandardRolls is Map<Int, MutableList<Int>>, which is compatible.
                val graphRollResults = DiceRollResults(currentRawStandardRolls.toMap()) // Ensure it's an immutable copy if necessary
                updateGraphData(graphRollResults, currentSettings) // Call the graph update method
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
                // _rawStandardDiceRollsForDisplay.value = currentRawStandardRolls
                // _rawPercentileRollsForDisplay.value = rolledPercentiles.map { Pair(it.tensDieRoll, it.unitsDieRoll) }
                _processedDiceResult.value = adaptToSimplerProcessedDiceResult(finalProcessedResult)
            }
        }
    }

    private fun adaptToSimplerProcessedDiceResult(fullResult: FullProcessedDiceResult): ProcessedDiceResult {
        return ProcessedDiceResult(
            announcementString = fullResult.announcementString,
            finalSum = fullResult.totalSumWithModifiers, // Or however finalSum is defined
            d20CritsRolled = fullResult.d20CritsRolled,
            isPercentile = fullResult.percentileResults.isNotEmpty(),
            // Populate rawRolls for ProcessedDiceResult if needed by other logic,
            // though graph uses its own DiceRollResults instance.
            rawRolls = fullResult.standardDiceRolls, // Or combine standard and percentile appropriately
            percentileValue = if (fullResult.percentileResults.isNotEmpty()) fullResult.percentileResults.first().finalValue else null
        )
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
