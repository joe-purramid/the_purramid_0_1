// TrafficLightViewModel.kt
package com.example.purramid.thepurramid.traffic_light

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.math.abs
import android.os.SystemClock // For double-tap timing

// --- Enums defining possible states ---

enum class LightColor {
    RED, YELLOW, GREEN
}

enum class TrafficLightMode {
    MANUAL_CHANGE, RESPONSIVE_CHANGE, TIMED_CHANGE
}

enum class Orientation {
    VERTICAL, HORIZONTAL
}

// --- Data class representing the UI state ---

data class TrafficLightState(
    val instanceId: Int = 0, // To differentiate multiple instances later
    val currentMode: TrafficLightMode = TrafficLightMode.MANUAL_CHANGE,
    val orientation: Orientation = Orientation.VERTICAL,
    val isBlinkingEnabled: Boolean = true,
    val activeLight: LightColor? = null,
    val isSettingsOpen: Boolean = false, // To track if settings is open for *this* instance 
    val isMicrophoneAvailable: Boolean = true, // Assume available for now, update later 
    val numberOfOpenInstances: Int = 1 // Assume 1 for now, update later 
    // Add fields for position, size, settings persistence later
    // val windowX: Int = 0,
    // val windowY: Int = 0,
    // val windowWidth: Int = -1, // Use -1 or similar for default/unset
    // val windowHeight: Int = -1,
)

@HiltViewModel
class TrafficLightViewModel @Inject constructor(
    // Inject Repository for persistence later
    // private val repository: TrafficLightRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrafficLightState())
    val uiState: StateFlow<TrafficLightState> = _uiState.asStateFlow()

    // Variables for double-tap detection
    private var lastTapTimeMs: Long = 0
    private var lastTappedColor: LightColor? = null
    private val doubleTapTimeoutMs: Long = 500 // Standard double-tap timeout

    init {
        // TODO: Load initial state from repository
        // viewModelScope.launch {
        //     _uiState.value = repository.loadInitialState()
        // }
    }

    fun handleLightTap(tappedColor: LightColor) {
        val currentTimeMs = SystemClock.elapsedRealtime()
        val currentState = _uiState.value

        if (currentState.currentMode == TrafficLightMode.MANUAL_CHANGE) {
            if (currentState.activeLight == tappedColor &&
                (currentTimeMs - lastTapTimeMs) < doubleTapTimeoutMs &&
                lastTappedColor == tappedColor
            ) {
                // Double tap on the active light: turn it off
                _uiState.update { it.copy(activeLight = null) }
                // Reset tap tracking
                lastTapTimeMs = 0
                lastTappedColor = null
            } else {
                // Single tap or tap on a different light: turn the tapped light on
                _uiState.update { it.copy(activeLight = tappedColor) }
                // Update tap tracking for potential double tap
                lastTapTimeMs = currentTimeMs
                lastTappedColor = tappedColor
            }
        }
        // TODO: Handle taps in other modes if necessary (might be disabled)
    }

    fun setOrientation(newOrientation: Orientation) {
        _uiState.update { it.copy(orientation = newOrientation) }
        // TODO: Persist change
    }

    fun setMode(newMode: TrafficLightMode) {
        var newActiveLight = _uiState.value.activeLight 
        if (newMode == TrafficLightMode.TIMED_CHANGE || (newMode == TrafficLightMode.RESPONSIVE_CHANGE && _uiState.value.currentMode != TrafficLightMode.RESPONSIVE_CHANGE)) { 
             newActiveLight = null // Clear light when switching to Timed or initially to Responsive 
        }
         _uiState.update { it.copy(currentMode = newMode) }
         // if(newMode != TrafficLightMode.MANUAL_CHANGE) {
         //      _uiState.update { it.copy(activeLight = null) }
         // }
        // TODO: Persist change
    }

     fun toggleBlinking(isEnabled: Boolean) {
        _uiState.update { it.copy(isBlinkingEnabled = isEnabled) }
        // TODO: Persist change
    }

    fun setSettingsOpen(isOpen: Boolean) { 
         _uiState.update { it.copy(isSettingsOpen = isOpen) } 
    }

    // --- Placeholder functions for settings items to be implemented later --- 
    fun setShowTimeRemaining(show: Boolean) { 
         // TODO: Update state and persist 
         Log.d("TrafficLightVM", "Set Show Time Remaining: $show") 
    } 

    fun setShowTimeline(show: Boolean) { 
         // TODO: Update state and persist 
         Log.d("TrafficLightVM", "Set Show Timeline: $show") 
         } 

    // To be called from Activity when it's created, potentially with an ID from Intent 
    fun initializeInstance(id: Int) { 
         _uiState.update { it.copy(instanceId = id) } 
         // TODO: Load specific persisted state for this instanceId 
    }

    // --- Functions for future features ---

    // fun updateWindowPosition(x: Int, y: Int) {
    //     _uiState.update { it.copy(windowX = x, windowY = y) }
    // }

    // fun updateWindowSize(width: Int, height: Int) {
    //     _uiState.update { it.copy(windowWidth = width, windowHeight = height) }
    // }

    // fun saveState() {
    //     viewModelScope.launch {
    //         repository.saveState(_uiState.value)
    //     }
    // }
}