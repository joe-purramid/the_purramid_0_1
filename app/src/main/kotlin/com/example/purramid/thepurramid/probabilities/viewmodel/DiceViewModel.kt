package com.example.purramid.thepurramid.probabilities.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purramid.thepurramid.probabilities.DiceSumResultType
import com.example.purramid.thepurramid.probabilities.GraphDistributionType
import com.example.purramid.thepurramid.probabilities.GraphPlotType
import com.example.purramid.thepurramid.probabilities.ProbabilitiesPositionManager
import com.example.purramid.thepurramid.util.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

// Data classes
enum class DieType { D4, D6, D8, D10, D12, D20, PERCENTILE }

data class DieConfig(
    val type: DieType,
    var quantity: Int = 0,
    var color: Int = 0xFFFFFFFF.toInt(),
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
    private val preferencesManager: ProbabilitiesPreferencesManager,
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

    fun initialize(instanceId: Int) {
        this.instanceId = instanceId
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val loaded = preferencesManager.loadDiceSettings(instanceId)
                if (loaded != null) {
                    _settings.value = loaded
                } else {
                    // Set default dice pool of 1d6
                    val defaultSettings = DiceSettings(
                        dieConfigs = DieType.values().map { type ->
                            DieConfig(
                                type = type,
                                quantity = if (type == DieType.D6) 1 else 0,
                                color = 0xFFFFFFFF.toInt()
                            )
                        }
                    )
                    _settings.value = defaultSettings
                    saveSettings()
                }
            } catch (e: Exception) {
                _error.value = "Failed to load dice settings: ${e.message}"
            }
        }
    }

    private fun saveSettings() {
        viewModelScope.launch {
            try {
                _settings.value?.let { settings ->
                    preferencesManager.saveDiceSettings(instanceId, settings)
                }
            } catch (e: Exception) {
                _error.value = "Failed to save dice settings: ${e.message}"
            }
        }
    }

    fun updateDieConfig(
        type: DieType,
        quantity: Int? = null,
        color: Int? = null,
        modifier: Int? = null,
        usePips: Boolean? = null
    ) {
        viewModelScope.launch {
            try {
                val current = _settings.value ?: DiceSettings()
                val updated = current.dieConfigs.map { config ->
                    if (config.type == type) {
                        config.copy(
                            quantity = quantity ?: config.quantity,
                            color = color ?: config.color,
                            modifier = modifier ?: config.modifier,
                            usePips = usePips ?: config.usePips
                        )
                    } else {
                        config
                    }
                }
                _settings.value = current.copy(dieConfigs = updated)
                saveSettings()
            } catch (e: Exception) {
                _error.value = "Failed to update die configuration: ${e.message}"
            }
        }
    }

    fun updateSettings(
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
        viewModelScope.launch {
            try {
                val current = _settings.value ?: DiceSettings()

                // Handle mutual exclusivity as per specifications
                var newAnnounce = announce ?: current.announce
                var newCriticalCelebration = criticalCelebration ?: current.criticalCelebration
                var newGraphEnabled = graphEnabled ?: current.graphEnabled
                var newSumResultType = sumResultType ?: current.sumResultType

                // If Critical Celebration is being turned on, ensure Announce is also on
                if (criticalCelebration == true && !newAnnounce) {
                    newAnnounce = true
                }

                // If Graph Distribution is being turned on, turn off incompatible features
                if (graphEnabled == true) {
                    if (newAnnounce || newSumResultType != DiceSumResultType.INDIVIDUAL) {
                        newAnnounce = false
                        newSumResultType = DiceSumResultType.INDIVIDUAL
                        newCriticalCelebration = false
                    }
                }

                // If Announce is being turned off, turn off dependent features
                if (announce == false) {
                    newCriticalCelebration = false
                    if (current.sumResultType != DiceSumResultType.INDIVIDUAL) {
                        newSumResultType = DiceSumResultType.INDIVIDUAL
                    }
                }

                _settings.value = current.copy(
                    usePercentile = usePercentile ?: current.usePercentile,
                    rollAnimation = rollAnimation ?: current.rollAnimation,
                    critEnabled = critEnabled ?: current.critEnabled,
                    announce = newAnnounce,
                    criticalCelebration = newCriticalCelebration,
                    sumResultType = newSumResultType,
                    graphEnabled = newGraphEnabled,
                    graphType = graphType ?: current.graphType,
                    graphDistribution = graphDistribution ?: current.graphDistribution
                )
                saveSettings()
            } catch (e: Exception) {
                _error.value = "Failed to update dice settings: ${e.message}"
            }
        }
    }

    fun rollDice() {
        viewModelScope.launch {
            try {
                val current = _settings.value ?: DiceSettings()
                val results = mutableMapOf<DieType, List<Int>>()
                var totalSum = 0
                val crits = mutableListOf<Boolean>()
                val modifiers = mutableMapOf<DieType, Int>()

                // Roll regular dice
                for (config in current.dieConfigs) {
                    if (config.quantity > 0 && config.type != DieType.PERCENTILE) {
                        val rolls = mutableListOf<Int>()
                        val sides = getSidesForDieType(config.type)

                        for (i in 0 until config.quantity) {
                            val roll = Random.nextInt(1, sides + 1)
                            rolls.add(roll)

                            val modifiedResult = roll + config.modifier
                            totalSum += modifiedResult

                            // Check for critical hit (natural 20 on d20)
                            if (config.type == DieType.D20 && roll == 20 && current.critEnabled) {
                                crits.add(true)
                                if (current.criticalCelebration) {
                                    _criticalHit.value = Event(true)
                                }
                            }
                        }

                        if (rolls.isNotEmpty()) {
                            results[config.type] = rolls
                            modifiers[config.type] = config.modifier
                        }
                    }
                }

                // Roll percentile dice if enabled
                if (current.usePercentile) {
                    val percentileConfig = current.dieConfigs.find { it.type == DieType.PERCENTILE }
                    if (percentileConfig != null && percentileConfig.quantity > 0) {
                        val percentileRolls = mutableListOf<Int>()

                        for (i in 0 until percentileConfig.quantity) {
                            val percentileRoll = rollPercentile()
                            percentileRolls.add(percentileRoll)
                            totalSum += percentileRoll + percentileConfig.modifier
                        }

                        if (percentileRolls.isNotEmpty()) {
                            results[DieType.PERCENTILE] = percentileRolls
                            modifiers[DieType.PERCENTILE] = percentileConfig.modifier
                        }
                    }
                }

                _result.value = DiceResult(
                    results = results,
                    sum = totalSum,
                    crits = crits,
                    modifiers = modifiers
                )

                if (current.rollAnimation) {
                    _rollAnimationTrigger.value = Event(true)
                }
            } catch (e: Exception) {
                _error.value = "Failed to roll dice: ${e.message}"
            }
        }
    }

    private fun getSidesForDieType(type: DieType): Int {
        return when (type) {
            DieType.D4 -> 4
            DieType.D6 -> 6
            DieType.D8 -> 8
            DieType.D10 -> 10
            DieType.D12 -> 12
            DieType.D20 -> 20
            DieType.PERCENTILE -> 100
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

    fun applyColorToAll(color: Int) {
        viewModelScope.launch {
            try {
                val current = _settings.value ?: DiceSettings()
                val updated = current.dieConfigs.map { config ->
                    config.copy(color = color)
                }
                _settings.value = current.copy(dieConfigs = updated)
                saveSettings()
            } catch (e: Exception) {
                _error.value = "Failed to apply color to all dice: ${e.message}"
            }
        }
    }

    fun applyModifierToAll(modifier: Int) {
        viewModelScope.launch {
            try {
                val current = _settings.value ?: DiceSettings()
                val updated = current.dieConfigs.map { config ->
                    config.copy(modifier = modifier)
                }
                _settings.value = current.copy(dieConfigs = updated)
                saveSettings()
            } catch (e: Exception) {
                _error.value = "Failed to apply modifier to all dice: ${e.message}"
            }
        }
    }
}