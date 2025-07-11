package com.example.purramid.thepurramid.probabilities.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.purramid.thepurramid.probabilities.DiceSumResultType
import com.example.purramid.thepurramid.probabilities.GraphDistributionType
import com.example.purramid.thepurramid.probabilities.GraphPlotType
import com.example.purramid.thepurramid.probabilities.ProbabilitiesMode
import com.example.purramid.thepurramid.probabilities.ProbabilitiesPositionManager
import com.example.purramid.thepurramid.util.Event
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import java.lang.Exception
import javax.inject.Inject
import kotlin.random.Random

// Data class for a single die type
enum class DieType { D4, D6, D8, D10, D12, D20, PERCENTILE }
data class DieConfig(
    val type: DieType,
    var quantity: Int = 1,
    var color: Int = 0xFFFFFF,
    var modifier: Int = 0,
    var usePips: Boolean = false
)

data class DiceSettings(
    val dieConfigs: List<DieConfig> = DieType.values().map { DieConfig(it) },
    val usePercentile: Boolean = false,
    val rollAnimation: Boolean = true,
    val critEnabled: Boolean = false,
    val announce: Boolean = false,
    val criticalCelebration: Boolean = false,
    val sumResultType: DiceSumResultType = DiceSumResultType.INDIVIDUAL,
    val graphEnabled: Boolean = false,
    val graphType: GraphPlotType = GraphPlotType.HISTOGRAM,
    val graphDistribution: GraphDistributionType = GraphDistributionType.OFF
)

data class DiceResult(
    val results: Map<DieType, List<Int>>,
    val sum: Int,
    val crits: List<Boolean>,
    val modifiers: Map<DieType, Int> = emptyMap()
)

@HiltViewModel
class DiceViewModel @Inject constructor(
    private val positionManager: ProbabilitiesPositionManager
) : ViewModel() {
    private val _settings = MutableLiveData(DiceSettings())
    val settings: LiveData<DiceSettings> = _settings

    private val _result = MutableLiveData<DiceResult?>()
    val result: LiveData<DiceResult?> = _result

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _criticalHit = MutableLiveData<Event<Boolean>>()
    val criticalHit: LiveData<Event<Boolean>> = _criticalHit

    private val _rollAnimationTrigger = MutableLiveData<Event<Boolean>>()
    val rollAnimationTrigger: LiveData<Event<Boolean>> = _rollAnimationTrigger

    private var instanceId: Int = 0

    fun loadSettings(context: Context, instanceId: Int) {
        try {
            this.instanceId = instanceId
            val prefs = context.getSharedPreferences("probabilities_prefs", Context.MODE_PRIVATE)
            val json = prefs.getString("probabilities_dice_settings_$instanceId", null)
            if (json != null) {
                val loaded = Gson().fromJson(json, DiceSettings::class.java)
                _settings.value = loaded
            }
        } catch (e: Exception) {
            _error.value = "Failed to load dice settings: ${e.message}"
        }
    }

    private fun saveSettings(context: Context) {
        try {
            val prefs = context.getSharedPreferences("probabilities_prefs", Context.MODE_PRIVATE)
            val json = Gson().toJson(_settings.value)
            prefs.edit().putString("probabilities_dice_settings_$instanceId", json).apply()
        } catch (e: Exception) {
            _error.value = "Failed to save dice settings: ${e.message}"
        }
    }

    fun updateDieConfig(context: Context, type: DieType, quantity: Int? = null, color: Int? = null, modifier: Int? = null, usePips: Boolean? = null) {
        try {
            val current = _settings.value ?: DiceSettings()
            val updated = current.dieConfigs.map {
                if (it.type == type) it.copy(
                    quantity = quantity ?: it.quantity,
                    color = color ?: it.color,
                    modifier = modifier ?: it.modifier,
                    usePips = usePips ?: it.usePips
                ) else it
            }
            _settings.value = current.copy(dieConfigs = updated)
            saveSettings(context)
        } catch (e: Exception) {
            _error.value = "Failed to update die configuration: ${e.message}"
        }
    }

    fun updateSettings(context: Context,
        usePercentile: Boolean? = null,
        rollAnimation: Boolean? = null,
        critEnabled: Boolean? = null,
        announce: Boolean? = null,
        criticalCelebration: Boolean? = null,
        sumResultType: DiceSumResultType? = null,
        graphEnabled: Boolean? = null,
        graphType: GraphPlotType? = null,
        graphDistribution: GraphDistributionType? = null
    ) {
        try {
            val current = _settings.value ?: DiceSettings()
            
            // Handle mutual exclusivity as per specifications
            var newAnnounce = announce ?: current.announce
            var newCriticalCelebration = criticalCelebration ?: current.criticalCelebration
            var newGraphEnabled = graphEnabled ?: current.graphEnabled
            
            // If Critical Celebration is being turned on, ensure Announce is also on
            if (criticalCelebration == true && !newAnnounce) {
                newAnnounce = true
            }
            
            // If Graph Distribution is being turned on, turn off Announce, Sum Results, and Add Modifiers
            if (graphEnabled == true && (newAnnounce || current.sumResultType != DiceSumResultType.INDIVIDUAL)) {
                newAnnounce = false
                // Note: Sum Results and Add Modifiers would need to be reset here if they were separate settings
            }
            
            _settings.value = current.copy(
                usePercentile = usePercentile ?: current.usePercentile,
                rollAnimation = rollAnimation ?: current.rollAnimation,
                critEnabled = critEnabled ?: current.critEnabled,
                announce = newAnnounce,
                criticalCelebration = newCriticalCelebration,
                sumResultType = sumResultType ?: current.sumResultType,
                graphEnabled = newGraphEnabled,
                graphType = graphType ?: current.graphType,
                graphDistribution = graphDistribution ?: current.graphDistribution
            )
            saveSettings(context)
        } catch (e: Exception) {
            _error.value = "Failed to update dice settings: ${e.message}"
        }
    }

    fun rollDice() {
        try {
            val current = _settings.value ?: DiceSettings()
            val results = mutableMapOf<DieType, List<Int>>()
            var totalSum = 0
            val crits = mutableListOf<Boolean>()
            
            for (config in current.dieConfigs) {
                val rolls = mutableListOf<Int>()
                val sides = when (config.type) {
                    DieType.D4 -> 4
                    DieType.D6 -> 6
                    DieType.D8 -> 8
                    DieType.D10 -> 10
                    DieType.D12 -> 12
                    DieType.D20 -> 20
                    DieType.PERCENTILE -> 100
                }

                or (i in 0 until config.quantity) {
                    val roll = if (config.type == DieType.PERCENTILE) {
                        rollPercentile()
                    } else {
                        Random.nextInt(1, sides + 1)
                    }

                    rolls.add(roll)
                    val modifiedResult = roll + config.modifier
                    totalSum += modifiedResult

                    if (config.type == DieType.D20 && roll == 20 && current.critEnabled) {
                        crits.add(true)
                        if (current.criticalCelebration) {
                            _criticalHit.value = Event(true)
                        }
                    }
                }

                results[config.type] = rolls
            }
            _result.value = DiceResult(
                results = results,
                sum = totalSum,
                crits = crits,
                modifiers = current.dieConfigs.associate { it.type to it.modifier }
            )

            if (current.rollAnimation) {
                _rollAnimationTrigger.value = Event(true)
            }
        } catch (e: Exception) {
            _error.value = "Failed to roll dice: ${e.message}"
        }
    }

    private fun rollPercentile(): Int {
        val tens = Random.nextInt(0, 10) * 10
        val ones = Random.nextInt(0, 10)
        return if (tens == 0 && ones == 0) 100 else tens + ones
    }

    fun getSumByType(): Map<DieType, Int> {
        val current = _settings.value ?: return emptyMap()
        val result = _result.value ?: return emptyMap()

        return result.results.mapValues { (type, rolls) ->
            val modifier = current.dieConfigs.find { it.type == type }?.modifier ?: 0
            rolls.sum() + (rolls.size * modifier)
        }
    }

    fun getTotalSum(): Int {
        val current = _settings.value ?: return 0
        val result = _result.value ?: return 0

        return result.results.entries.sumOf { (type, rolls) ->
            val modifier = current.dieConfigs.find { it.type == type }?.modifier ?: 0
            rolls.sum() + (rolls.size * modifier)
        }
    }

    fun reset() {
        _result.value = null
        _error.value = null
    }

    fun clearError() {
        _error.value = null
    }

    // TODO: Persist/restore settings per instance
}