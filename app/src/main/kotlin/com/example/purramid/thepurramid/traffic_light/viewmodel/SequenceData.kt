// SequenceData.kt
package com.example.purramid.thepurramid.traffic_light.viewmodel

import java.util.UUID

data class SequenceStep(
    val id: String = UUID.randomUUID().toString(),
    val order: Int,
    val color: LightColor? = null,
    val durationSeconds: Int = 0, // Total seconds
    val message: MessageData = MessageData()
) {
    fun isValid(): Boolean = color != null && durationSeconds > 0
    
    fun getDurationFormatted(): String {
        val hours = durationSeconds / 3600
        val minutes = (durationSeconds % 3600) / 60
        val seconds = durationSeconds % 60
        
        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
            minutes > 0 -> String.format("%d:%02d", minutes, seconds)
            else -> String.format("0:%02d", seconds)
        }
    }
}

data class TimedSequence(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val steps: List<SequenceStep> = emptyList()
) {
    fun getTotalDurationSeconds(): Int = steps.sumOf { it.durationSeconds }
    
    fun getTotalDurationFormatted(): String {
        val totalSeconds = getTotalDurationSeconds()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return String.format("%d:%02d:%02d", hours, minutes, seconds)
    }
    
    fun isValid(): Boolean = title.isNotEmpty() && steps.isNotEmpty() && steps.all { it.isValid() }
    
    companion object {
        const val MAX_STEPS = 10
        const val MAX_SEQUENCES = 10
        const val MAX_TITLE_LENGTH = 27
        const val MAX_DURATION_SECONDS = 35999 // 9:59:59
    }
}