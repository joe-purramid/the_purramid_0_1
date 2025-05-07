package com.example.purramid.thepurramid.traffic_light.viewmodel

import android.os.SystemClock // For double-tap timing
import android.util.Log
import androidx.lifecycle.ViewModel
// import androidx.lifecycle.viewModelScope // Only if you add coroutines here
import com.example.purramid.thepurramid.traffic_light.AdjustValuesFragment // For ColorForRange enum
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class TrafficLightViewModel @Inject constructor(
    // TODO: Inject Repository for persistence later
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
        val currentState = _uiState.value
        var updatedActiveLight = currentState.activeLight

        if (currentState.currentMode != newMode && currentState.activeLight != null) {
            // If the mode is actually changing AND a light is currently active,
            // clear the active light.
            updatedActiveLight = null
        }
        // This covers the general case. Specific modes might have further initial states
        // once entered (e.g., Timed mode waits for play, Responsive starts measuring).

        _uiState.update { it.copy(currentMode = newMode, activeLight = updatedActiveLight) }
        // TODO: Persist currentMode and activeLight changes
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

    fun updateResponsiveSettings(newSettings: ResponsiveModeSettings) {
        _uiState.update { it.copy(responsiveModeSettings = newSettings) }
        // TODO: Persist change
    }

    fun setDangerousSoundAlert(isEnabled: Boolean) {
        val currentSettings = _uiState.value.responsiveModeSettings
        _uiState.update {
            it.copy(responsiveModeSettings = currentSettings.copy(dangerousSoundAlertEnabled = isEnabled))
        }
        // TODO: Persist change
    }

    // Placeholder for complex update logic
    fun updateSpecificDbValue(
        colorForRange: AdjustValuesFragment.ColorForRange, // Using enum from Fragment for now
        isMinField: Boolean,
        newValue: Int?
    ) {
        val currentSettings = _uiState.value.responsiveModeSettings
        var newGreen = currentSettings.greenRange
        var newYellow = currentSettings.yellowRange
        var newRed = currentSettings.redRange

        // --- START OF COMPLEX LINKED LOGIC (TO BE FULLY IMPLEMENTED) ---
        // This is a simplified placeholder. Real logic needs to handle:
        // - Ensuring min <= max for the same color.
        // - Adjacency: Green.max < Yellow.min, Yellow.max < Red.min
        // - Auto-adjusting adjacent bands.
        // - N/A logic if bands are eliminated.
        // - Clamping values (0-120 for Red.max, etc.)

        val safeValue = newValue?.coerceIn(0, 120) // Basic clamping

        when (colorForRange) {
            AdjustValuesFragment.ColorForRange.GREEN -> {
                newGreen = if (isMinField) newGreen.copy(minDb = safeValue) else newGreen.copy(maxDb = safeValue)
                // Example: if green.max changes, yellow.min might need to change
                if (!isMinField && safeValue != null && newYellow.minDb != null && safeValue >= newYellow.minDb!!) {
                    newYellow = newYellow.copy(minDb = safeValue + 1)
                } else if (isMinField && safeValue !=null && newGreen.maxDb !=null && safeValue >= newGreen.maxDb!!){
                    // if min becomes > max, set max to min+1 or handle error
                    newGreen = newGreen.copy(maxDb = safeValue+1) // very basic
                }

            }
            AdjustValuesFragment.ColorForRange.YELLOW -> {
                newYellow = if (isMinField) newYellow.copy(minDb = safeValue) else newYellow.copy(maxDb = safeValue)
                // Example: if yellow.min changes, green.max might need to change
                if (isMinField && safeValue != null && newGreen.maxDb != null && safeValue <= newGreen.maxDb!!) {
                    newGreen = newGreen.copy(maxDb = safeValue - 1)
                }
                // Example: if yellow.max changes, red.min might need to change
                if (!isMinField && safeValue != null && newRed.minDb != null && safeValue >= newRed.minDb!!) {
                    newRed = newRed.copy(minDb = safeValue + 1)
                }
            }
            AdjustValuesFragment.ColorForRange.RED -> {
                newRed = if (isMinField) newRed.copy(minDb = safeValue) else newRed.copy(maxDb = safeValue)
                // Example: if red.min changes, yellow.max might need to change
                if (isMinField && safeValue != null && newYellow.maxDb != null && safeValue <= newYellow.maxDb!!) {
                    newYellow = newYellow.copy(maxDb = safeValue - 1)
                }
            }
        }

        // --- N/A Logic Placeholder ---
        // If Green max >= Yellow max -> Yellow becomes N/A
        if (newGreen.maxDb != null && newYellow.maxDb != null && !newYellow.isNa() && newGreen.maxDb!! >= newYellow.maxDb!!) {
            newYellow = DbRange.NA_RANGE
        }
        // If Yellow max >= Red max (and Yellow not N/A) -> Red might become N/A (or just clamped)
        // If Green min >= Red min (and Green not N/A) -> Yellow and Red might become N/A

        // Basic check for green range validity
        if (newGreen.minDb != null && newGreen.maxDb != null && newGreen.minDb!! > newGreen.maxDb!!) {
            newGreen = newGreen.copy(maxDb = newGreen.minDb) // or revert, or show error
        }
        // Similar checks for yellow and red


        // Ensure ranges don't overlap improperly or become invalid
        // This requires careful cascading updates.
        // Example: If Green.Max is set to 70:
        //  - Yellow.Min should become 71.
        //  - If Yellow.Max was < 71, Yellow becomes N/A.
        //      - Then Green.Max might extend further, or Red.Min needs to adjust to Green.Max + 1.

        // This logic will be the most complex part of this feature.
        // For now, this is a very simplified update.

        // --- END OF COMPLEX LINKED LOGIC ---

        val updatedSettings = currentSettings.copy(
            greenRange = newGreen,
            yellowRange = newYellow,
            redRange = newRed
        )
        _uiState.update { it.copy(responsiveModeSettings = updatedSettings) }
        // TODO: Persist
    }
}