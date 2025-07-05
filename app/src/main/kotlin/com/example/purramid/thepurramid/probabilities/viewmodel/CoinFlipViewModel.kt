package com.example.purramid.thepurramid.probabilities.viewmodel

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.purramid.thepurramid.probabilities.CoinProbabilityMode
import com.example.purramid.thepurramid.probabilities.GraphDistributionType
import com.example.purramid.thepurramid.probabilities.GraphPlotType
import com.google.gson.Gson
import java.lang.Exception

// Data class for a single coin type
enum class CoinType { B1, B5, B10, B25, MB1, MB2 }
data class CoinConfig(
    val type: CoinType,
    var quantity: Int = 1,
    var color: Int = 0xFFFFFF
)

data class CoinFlipSettings(
    val coinConfigs: List<CoinConfig> = CoinType.values().map { CoinConfig(it) },
    val flipAnimation: Boolean = true,
    val freeForm: Boolean = false,
    val announce: Boolean = true,
    val probabilityMode: CoinProbabilityMode = CoinProbabilityMode.NONE,
    val graphEnabled: Boolean = false,
    val graphType: GraphPlotType = GraphPlotType.HISTOGRAM,
    val graphDistribution: GraphDistributionType = GraphDistributionType.OFF
)

data class CoinFlipResult(
    val results: Map<CoinType, List<Boolean>> // true = heads, false = tails
)

class CoinFlipViewModel : ViewModel() {
    private val _settings = MutableLiveData(CoinFlipSettings())
    val settings: LiveData<CoinFlipSettings> = _settings

    private val _result = MutableLiveData<CoinFlipResult?>()
    val result: LiveData<CoinFlipResult?> = _result

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var instanceId: Int = 0

    fun loadSettings(context: Context, instanceId: Int) {
        try {
            this.instanceId = instanceId
            val prefs = context.getSharedPreferences("probabilities_prefs", Context.MODE_PRIVATE)
            val json = prefs.getString("probabilities_coin_settings_$instanceId", null)
            if (json != null) {
                val loaded = Gson().fromJson(json, CoinFlipSettings::class.java)
                _settings.value = loaded
            }
        } catch (e: Exception) {
            _error.value = "Failed to load coin flip settings: ${e.message}"
        }
    }

    private fun saveSettings(context: Context) {
        try {
            val prefs = context.getSharedPreferences("probabilities_prefs", Context.MODE_PRIVATE)
            val json = Gson().toJson(_settings.value)
            prefs.edit().putString("probabilities_coin_settings_$instanceId", json).apply()
        } catch (e: Exception) {
            _error.value = "Failed to save coin flip settings: ${e.message}"
        }
    }

    fun updateCoinConfig(context: Context, type: CoinType, quantity: Int? = null, color: Int? = null) {
        try {
            val current = _settings.value ?: CoinFlipSettings()
            val updated = current.coinConfigs.map {
                if (it.type == type) it.copy(
                    quantity = quantity ?: it.quantity,
                    color = color ?: it.color
                ) else it
            }
            _settings.value = current.copy(coinConfigs = updated)
            saveSettings(context)
        } catch (e: Exception) {
            _error.value = "Failed to update coin configuration: ${e.message}"
        }
    }

    fun updateSettings(context: Context,
        flipAnimation: Boolean? = null,
        freeForm: Boolean? = null,
        announce: Boolean? = null,
        probabilityMode: CoinProbabilityMode? = null,
        graphEnabled: Boolean? = null,
        graphType: GraphPlotType? = null,
        graphDistribution: GraphDistributionType? = null
    ) {
        try {
            val current = _settings.value ?: CoinFlipSettings()
            _settings.value = current.copy(
                flipAnimation = flipAnimation ?: current.flipAnimation,
                freeForm = freeForm ?: current.freeForm,
                announce = announce ?: current.announce,
                probabilityMode = probabilityMode ?: current.probabilityMode,
                graphEnabled = graphEnabled ?: current.graphEnabled,
                graphType = graphType ?: current.graphType,
                graphDistribution = graphDistribution ?: current.graphDistribution
            )
            saveSettings(context)
        } catch (e: Exception) {
            _error.value = "Failed to update coin flip settings: ${e.message}"
        }
    }

    fun flipCoins() {
        try {
            val current = _settings.value ?: CoinFlipSettings()
            val results = mutableMapOf<CoinType, List<Boolean>>()
            for (config in current.coinConfigs) {
                val flips = mutableListOf<Boolean>()
                for (i in 1..config.quantity) {
                    flips.add(listOf(true, false).random())
                }
                results[config.type] = flips
            }
            _result.value = CoinFlipResult(results)
        } catch (e: Exception) {
            _error.value = "Failed to flip coins: ${e.message}"
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