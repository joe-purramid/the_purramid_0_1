// ProbabilitiesPositionManager.kt
package com.example.purramid.thepurramid.probabilities

import android.content.Context
import com.example.purramid.thepurramid.probabilities.data.CoinPosition
import com.example.purramid.thepurramid.probabilities.data.ProbabilitiesPositionState
import com.example.purramid.thepurramid.probabilities.viewmodel.CoinType
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProbabilitiesPositionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("probabilities_positions", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun savePositions(instanceId: Int, state: ProbabilitiesPositionState) {
        val json = gson.toJson(state)
        prefs.edit().putString("positions_$instanceId", json).apply()
    }

    fun loadPositions(instanceId: Int): ProbabilitiesPositionState? {
        val json = prefs.getString("positions_$instanceId", null) ?: return null
        return try {
            gson.fromJson(json, ProbabilitiesPositionState::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun clearPositions(instanceId: Int) {
        prefs.edit().remove("positions_$instanceId").apply()
    }

    // For free form mode - save immediately on drag end
    fun updateCoinPosition(instanceId: Int, coinType: CoinType, index: Int, x: Float, y: Float, isHeads: Boolean) {
        val current = loadPositions(instanceId) ?: return
        val updated = current.coinPositions.toMutableList()

        val existingIndex = updated.indexOfFirst { it.coinType == coinType && it.index == index }
        if (existingIndex >= 0) {
            updated[existingIndex] = updated[existingIndex].copy(x = x, y = y, isHeads = isHeads, isFreeForm = true)
        } else {
            updated.add(CoinPosition(coinType, index, x, y, isHeads = isHeads, isFreeForm = true))
        }

        savePositions(instanceId, current.copy(coinPositions = updated))
    }
}