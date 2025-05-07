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
    val isMicrophoneAvailable: Boolean = true, // Update later with actual check
    val numberOfOpenInstances: Int = 1,       // Update later when "Add Another" is implemented
    val responsiveModeSettings: ResponsiveModeSettings = ResponsiveModeSettings()
    // Add other mode-specific settings bundles here as needed, e.g.,
    // val timedModeSettings: TimedModeSettings = TimedModeSettings(),
    // val messages: Map<LightColor, MessageData> = emptyMap()
)