// TrafficLightState.kt
package com.example.purramid.thepurramid.traffic_light.viewmodel

// Imports from the same package
// No explicit imports needed if they are in the same package,
// but good practice if you move them further.
// import com.example.purramid.thepurramid.traffic_light.viewmodel.TrafficLightMode
// import com.example.purramid.thepurramid.traffic_light.viewmodel.Orientation
// import com.example.purramid.thepurramid.traffic_light.viewmodel.LightColor
// import com.example.purramid.thepurramid.traffic_light.viewmodel.ResponsiveModeSettings

data class TrafficLightState(
    val instanceId: Int = 0,
    val currentMode: TrafficLightMode = TrafficLightMode.MANUAL_CHANGE,
    val orientation: Orientation = Orientation.VERTICAL,
    val isBlinkingEnabled: Boolean = true,
    val activeLight: LightColor? = null,
    val isSettingsOpen: Boolean = false,
    val messages: TrafficLightMessages = TrafficLightMessages(),
    val needsMicrophonePermission: Boolean = false,
    val isMicrophoneAvailable: Boolean = true, // Update later with actual check
    val isDangerousAlertActive: Boolean = false,
    val previousMode: TrafficLightMode? = null,
    val currentDecibelLevel: Int? = null,
    val dangerousSoundDetectedAt: Long? = null,
    val numberOfOpenInstances: Int = 1,       // Update later when "Add Another" is implemented
    val responsiveModeSettings: ResponsiveModeSettings = ResponsiveModeSettings()
    val isKeyboardAvailable: Boolean = true,
    val timedSequences: List<TimedSequence> = emptyList(),
    val activeSequenceId: String? = null,
    val currentStepIndex: Int = 0,
    val elapsedStepSeconds: Int = 0,
    val isSequencePlaying: Boolean = false,
    val showTimeRemaining: Boolean = false, // Default to false
    val showTimeline: Boolean = true,      // Default to true (as per layout)
    // Add fields for window persistence state
    val windowX: Int = 0,
    val windowY: Int = 0,
    val windowWidth: Int = -1, // Use -1 for WRAP_CONTENT or default
    val windowHeight: Int = -1 // Use -1 for WRAP_CONTENT or default
)