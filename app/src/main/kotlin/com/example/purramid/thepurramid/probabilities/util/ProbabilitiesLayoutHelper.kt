// ProbabilitiesLayoutHelper.kt
package com.example.purramid.thepurramid.probabilities.util

import com.example.purramid.thepurramid.probabilities.data.CoinPosition
import com.example.purramid.thepurramid.probabilities.data.DicePosition
import com.example.purramid.thepurramid.probabilities.viewmodel.CoinType
import com.example.purramid.thepurramid.probabilities.viewmodel.DieType
import kotlin.math.cos
import kotlin.math.sin

object ProbabilitiesLayoutHelper {
    fun calculateDefaultDicePositions(
        containerWidth: Int,
        containerHeight: Int,
        diceGroups: Map<DieType, Int>
    ): List<DicePosition> {
        val positions = mutableListOf<DicePosition>()
        val groupCount = diceGroups.filter { it.value > 0 }.size
        
        // Calculate positions based on polygon layout
        val angleStep = 360f / groupCount
        val radius = minOf(containerWidth, containerHeight) * 0.35f
        val centerX = containerWidth / 2f
        val centerY = containerHeight / 2f
        
        var groupIndex = 0
        diceGroups.filter { it.value > 0 }.forEach { (dieType, count) ->
            val angle = Math.toRadians(angleStep * groupIndex.toDouble())
            val groupCenterX = centerX + (radius * cos(angle)).toFloat()
            val groupCenterY = centerY + (radius * sin(angle)).toFloat()
            
            // Position dice within group
            for (i in 0 until count) {
                val offsetX = (i - count / 2f) * 70 // 70dp spacing
                positions.add(DicePosition(
                    dieType = dieType,
                    index = i,
                    x = groupCenterX + offsetX,
                    y = groupCenterY
                ))
            }
            groupIndex++
        }
        
        return positions
    }

    fun calculateDefaultCoinPositions(
        containerWidth: Int,
        containerHeight: Int,
        coinGroups: Map<CoinType, Int>
    ): List<CoinPosition> {
        val positions = mutableListOf<CoinPosition>()
        val groupCount = coinGroups.filter { it.value > 0 }.size

        val angleStep = 360f / groupCount
        val radius = minOf(containerWidth, containerHeight) * 0.35f
        val centerX = containerWidth / 2f
        val centerY = containerHeight / 2f

        var groupIndex = 0
        coinGroups.filter { it.value > 0 }.forEach { (coinType, count) ->
            val angle = Math.toRadians(angleStep * groupIndex.toDouble())
            val groupCenterX = centerX + (radius * cos(angle)).toFloat()
            val groupCenterY = centerY + (radius * sin(angle)).toFloat()

            for (i in 0 until count) {
                val offsetX = (i - count / 2f) * 65
                positions.add(CoinPosition(
                    coinType = coinType,
                    index = i,
                    x = groupCenterX + offsetX,
                    y = groupCenterY,
                    rotation = 0f,
                    isHeads = true,
                    isFreeForm = false
                ))
            }
            groupIndex++
        }

        return positions
    }
}