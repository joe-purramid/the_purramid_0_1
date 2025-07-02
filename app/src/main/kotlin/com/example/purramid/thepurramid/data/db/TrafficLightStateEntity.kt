// TrafficLightStateEntity.kt
package com.example.purramid.thepurramid.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "traffic_light_state")
data class TrafficLightStateEntity(
    @PrimaryKey
    val instanceId: Int,
    val uuid: String = UUID.randomUUID().toString(),
    val currentMode: String,
    val orientation: String,
    val isBlinkingEnabled: Boolean,
    val activeLight: String?,
    val isSettingsOpen: Boolean,
    val isMicrophoneAvailable: Boolean,
    val numberOfOpenInstances: Int,
    val responsiveModeSettingsJson: String,

    // Traffic Light messages
    val messagesJson: String = "{}",  // Stores TrafficLightMessages as JSON
    val timedSequencesJson: String = "[]",  // Stores List<TimedSequence> as JSON
    val activeSequenceId: String? = null,
    val currentStepIndex: Int = 0,
    val elapsedStepSeconds: Int = 0,
    val isSequencePlaying: Boolean = false,
    val isDangerousAlertActive: Boolean = false,
    val previousMode: String? = null,
    val currentDecibelLevel: Int? = null,
    val dangerousSoundDetectedAt: Long? = null,

    // Add fields for Timed Mode UI settings
    val showTimeRemaining: Boolean,
    val showTimeline: Boolean,

    // Add fields for window persistence
    val windowX: Int = 0,
    val windowY: Int = 0,
    val windowWidth: Int = -1,
    val windowHeight: Int = -1
)