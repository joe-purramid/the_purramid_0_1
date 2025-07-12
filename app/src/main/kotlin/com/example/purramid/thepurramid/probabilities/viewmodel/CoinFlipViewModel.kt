package com.example.purramid.thepurramid.probabilities.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purramid.thepurramid.probabilities.CoinProbabilityMode
import com.example.purramid.thepurramid.probabilities.GraphDistributionType
import com.example.purramid.thepurramid.probabilities.GraphPlotType
import com.example.purramid.thepurramid.probabilities.ProbabilitiesPositionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

// Data classes
enum class CoinType { B1, B5, B10, B25, MB1, MB2 }

data class CoinConfig(
    val type: CoinType,
    var quantity: Int = 0,
    var color: Int = 0xFFDAA520.toInt() // Goldenrod default
)

data class CoinFlipSettings(
    val coinConfigs: List<CoinConfig> = CoinType.values().map { CoinConfig(it) },
    val flipAnimation: Boolean = true,
    val freeForm: Boolean = false,
    val announce: Boolean = true,
    val probabilityMode: CoinProbabilityMode = CoinProbabilityMode.NONE,
    val graphEnabled: Boolean = false,
    val graphType: GraphPlotType = GraphPlotType.HISTOGRAM,
    val graphDistribution: GraphDistributionType = GraphDistributionType.OFF,
    val probabilityEnabled: Boolean = false
)

data class CoinFlipResult(
    val results: Map<CoinType, List<Boolean>> // true = heads, false = tails
)

data class GridCellResult(
    val headsCount: Int,
    val tailsCount: Int
)

@HiltViewModel
class CoinFlipViewModel @Inject constructor(
    private val preferencesManager: ProbabilitiesPreferencesManager,
    private val positionManager: ProbabilitiesPositionManager
) : ViewModel() {

    private val _settings = MutableLiveData(CoinFlipSettings())
    val settings: LiveData<CoinFlipSettings> = _settings

    private val _result = MutableLiveData<CoinFlipResult?>()
    val result: LiveData<CoinFlipResult?> = _result

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var instanceId: Int = 0

    fun initialize(instanceId: Int) {
        this.instanceId = instanceId
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val loaded = preferencesManager.loadCoinSettings(instanceId)
                if (loaded != null) {
                    _settings.value = loaded
                } else {
                    // Set default coin pool of 1b25
                    val defaultSettings = CoinFlipSettings(
                        coinConfigs = CoinType.values().map { type ->
                            CoinConfig(
                                type = type,
                                quantity = if (type == CoinType.B25) 1 else 0,
                                color = 0xFFDAA520.toInt()
                            )
                        }
                    )
                    _settings.value = defaultSettings
                    saveSettings()
                }
            } catch (e: Exception) {
                _error.value = "Failed to load coin flip settings: ${e.message}"
            }
        }
    }

    private fun saveSettings() {
        viewModelScope.launch {
            try {
                _settings.value?.let { settings ->
                    preferencesManager.saveCoinSettings(instanceId, settings)
                }
            } catch (e: Exception) {
                _error.value = "Failed to save coin flip settings: ${e.message}"
            }
        }
    }

    fun updateCoinConfig(
        type: CoinType,
        quantity: Int? = null,
        color: Int? = null
    ) {
        viewModelScope.launch {
            try {
                val current = _settings.value ?: CoinFlipSettings()

                // Validate quantity
                val newQuantity = quantity ?: current.coinConfigs.find { it.type == type }?.quantity ?: 0
                if (newQuantity < 0 || newQuantity > 10) {
                    _error.value = "Coin quantity must be between 0 and 10"
                    return@launch
                }

                val updated = current.coinConfigs.map { config ->
                    if (config.type == type) {
                        config.copy(
                            quantity = newQuantity,
                            color = color ?: config.color
                        )
                    } else {
                        config
                    }
                }
                _settings.value = current.copy(coinConfigs = updated)
                saveSettings()
            } catch (e: Exception) {
                _error.value = "Failed to update coin configuration: ${e.message}"
            }
        }
    }

    fun updateSettings(
        flipAnimation: Boolean? = null,
        freeForm: Boolean? = null,
        announce: Boolean? = null,
        probabilityMode: CoinProbabilityMode? = null,
        graphEnabled: Boolean? = null,
        graphType: GraphPlotType? = null,
        graphDistribution: GraphDistributionType? = null
    ) {
        viewModelScope.launch {
            try {
                val current = _settings.value ?: CoinFlipSettings()

                // Handle mutual exclusivity as per specifications
                var newFreeForm = freeForm ?: current.freeForm
                var newProbabilityMode = probabilityMode ?: current.probabilityMode
                var newAnnounce = announce ?: current.announce

                // Free form and probability mode are mutually exclusive
                if (freeForm == true && current.probabilityMode != CoinProbabilityMode.NONE) {
                    newProbabilityMode = CoinProbabilityMode.NONE
                }

                // Probability mode and free form are mutually exclusive
                if (probabilityMode != null && probabilityMode != CoinProbabilityMode.NONE && current.freeForm) {
                    newFreeForm = false
                }

                // Probability mode cannot be active while Announce is toggled on
                if (probabilityMode != null && probabilityMode != CoinProbabilityMode.NONE && current.announce) {
                    newAnnounce = false
                }

                // Announce cannot be active while Probability mode is on
                if (announce == true && current.probabilityMode != CoinProbabilityMode.NONE) {
                    newProbabilityMode = CoinProbabilityMode.NONE
                }

                _settings.value = current.copy(
                    flipAnimation = flipAnimation ?: current.flipAnimation,
                    freeForm = newFreeForm,
                    announce = newAnnounce,
                    probabilityMode = newProbabilityMode,
                    graphEnabled = graphEnabled ?: current.graphEnabled,
                    graphType = graphType ?: current.graphType,
                    graphDistribution = graphDistribution ?: current.graphDistribution,
                    probabilityEnabled = newProbabilityMode != CoinProbabilityMode.NONE
                )
                saveSettings()
            } catch (e: Exception) {
                _error.value = "Failed to update coin flip settings: ${e.message}"
            }
        }
    }

    fun flipCoins() {
        viewModelScope.launch {
            try {
                val current = _settings.value ?: CoinFlipSettings()
                val results = mutableMapOf<CoinType, List<Boolean>>()

                var hasCoins = false
                for (config in current.coinConfigs) {
                    if (config.quantity > 0) {
                        hasCoins = true
                        val flips = mutableListOf<Boolean>()
                        for (i in 0 until config.quantity) {
                            flips.add(Random.nextBoolean()) // true = heads, false = tails
                        }
                        results[config.type] = flips
                    }
                }

                if (!hasCoins) {
                    _error.value = "Add coins to your pool to flip!"
                    return@launch
                }

                _result.value = CoinFlipResult(results)
            } catch (e: Exception) {
                _error.value = "Failed to flip coins: ${e.message}"
            }
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
                val current = _settings.value ?: CoinFlipSettings()
                val updated = current.coinConfigs.map { config ->
                    config.copy(color = color)
                }
                _settings.value = current.copy(coinConfigs = updated)
                saveSettings()
            } catch (e: Exception) {
                _error.value = "Failed to apply color to all coins: ${e.message}"
            }
        }
    }

    fun getTotalCoins(): Int {
        return _settings.value?.coinConfigs?.sumOf { it.quantity } ?: 0
    }

    fun getActiveCoins(): List<CoinConfig> {
        return _settings.value?.coinConfigs?.filter { it.quantity > 0 } ?: emptyList()
    }

    fun getHeadsCount(): Int {
        val result = _result.value ?: return 0
        return result.results.values.sumOf { flips ->
            flips.count { it }
        }
    }

    fun getTailsCount(): Int {
        val result = _result.value ?: return 0
        return result.results.values.sumOf { flips ->
            flips.count { !it }
        }
    }
}