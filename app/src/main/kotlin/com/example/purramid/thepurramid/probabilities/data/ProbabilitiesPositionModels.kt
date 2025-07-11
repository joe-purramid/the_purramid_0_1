// ProbabilitiesPositionModels.kt
package com.example.purramid.thepurramid.probabilities.data

import com.example.purramid.thepurramid.probabilities.ProbabilitiesMode
import com.example.purramid.thepurramid.probabilities.viewmodel.DieType
import com.example.purramid.thepurramid.probabilities.viewmodel.CoinType

data class DicePosition(
    val dieType: DieType,
    val index: Int, // For multiple dice of same type
    val x: Float,
    val y: Float,
    val rotation: Float = 0f,
    val lastResult: Int? = null
)

data class CoinPosition(
    val coinType: CoinType,
    val index: Int,
    val x: Float,
    val y: Float,
    val rotation: Float = 0f,
    val isHeads: Boolean = true,
    val isFreeForm: Boolean = false
)

data class ProbabilitiesPositionState(
    val instanceId: Int,
    val mode: ProbabilitiesMode,
    val dicePositions: List<DicePosition> = emptyList(),
    val coinPositions: List<CoinPosition> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)