// ClockViewModel.kt
package com.example.purramid.thepurramid.clock.viewmodel

import android.graphics.Color
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purramid.thepurramid.data.db.ClockDao
import com.example.purramid.thepurramid.data.db.ClockStateEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Represents the UI state for a single clock instance.
 */
data class ClockState(
    val clockId: Int,
    val timeZoneId: ZoneId = ZoneId.systemDefault(),
    val isPaused: Boolean = false,
    val displaySeconds: Boolean = true,
    val is24Hour: Boolean = false,
    val clockColor: Int = Color.WHITE,
    val mode: String = "digital", // "digital" or "analog"
    val isNested: Boolean = false,
    val windowX: Int = 0,
    val windowY: Int = 0,
    val windowWidth: Int = -1,
    val windowHeight: Int = -1,
    val currentTime: LocalTime = LocalTime.now(), // Current time to display
    val manuallySetTime: LocalTime? = null // Stores time if manually set by user drag
)

@HiltViewModel
class ClockViewModel @Inject constructor(
    private val clockDao: ClockDao,
    private val savedStateHandle: SavedStateHandle // Hilt injects this
) : ViewModel() {

    companion object {
        // Key to retrieve clockId from SavedStateHandle (must match what Service/Activity passes)
        const val KEY_CLOCK_ID = "clockId"
        private const val TAG = "ClockViewModel"
        private const val TICK_INTERVAL_MS = 100L // Update more frequently for smoother seconds
    }

    // Get the clockId passed via SavedStateHandle
    private val clockId: Int? = savedStateHandle[KEY_CLOCK_ID]

    // Default state (used if loading fails or it's a new clock)
    private val defaultState = ClockState(clockId ?: -1) // Use -1 if ID is somehow null

    private val _uiState = MutableStateFlow(defaultState)
    val uiState: StateFlow<ClockState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null

    init {
        Log.d(TAG, "Initializing ViewModel for clockId: $clockId")
        if (clockId != null && clockId != -1) {
            loadInitialState(clockId)
        } else {
            Log.e(TAG, "Invalid clockId ($clockId), ViewModel will use default state but not persist.")
            // Start ticker even with default state if ID is invalid? Or wait for valid ID?
            // Let's start it, it will use system time.
             startTicker()
        }
    }

    private fun loadInitialState(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = clockDao.getById(id)
            withContext(Dispatchers.Main) {
                if (entity != null) {
                    Log.d(TAG, "Loaded state from DB for clock $id")
                    val loadedState = mapEntityToState(entity)
                    _uiState.value = loadedState
                    // Start ticker after state is loaded
                    startTicker()
                } else {
                    Log.w(TAG, "No saved state found for clock $id, initializing with defaults.")
                    // Initialize with default state for this new instance ID
                    val initialState = ClockState(clockId = id, currentTime = LocalTime.now(ZoneId.systemDefault()))
                    _uiState.value = initialState
                    // Save the initial default state to the DB
                    saveState(initialState) // Save immediately
                    startTicker() // Start ticker for the new clock
                }
            }
        }
    }

    // --- Ticker Logic ---
    private fun startTicker() {
        tickerJob?.cancel() // Cancel any existing job
        tickerJob = viewModelScope.launch(Dispatchers.Main) { // Use Main dispatcher for UI state updates
            while (isActive) {
                _uiState.update { currentState ->
                    if (!currentState.isPaused) {
                        // If manually set time exists, increment that, otherwise use system time
                        val timeToUpdate = currentState.manuallySetTime ?: LocalTime.now(currentState.timeZoneId)
                        // Increment logic needs care for LocalTime - handle potential day rollover if needed
                        // For simplicity, let's just fetch current time if not manually set.
                        val newTime = currentState.manuallySetTime?.plusNanos(TICK_INTERVAL_MS * 1_000_000)
                                      ?: LocalTime.now(currentState.timeZoneId)

                        currentState.copy(currentTime = newTime.truncatedTo(ChronoUnit.NANOS)) // Update with new time
                    } else {
                        currentState // No change if paused
                    }
                }
                delay(TICK_INTERVAL_MS)
            }
        }
         Log.d(TAG, "Ticker started for clock $clockId")
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
         Log.d(TAG, "Ticker stopped for clock $clockId")
    }

    // --- State Update Actions ---

    fun setPaused(isPaused: Boolean) {
        if (_uiState.value.isPaused == isPaused) return
        _uiState.update {
            // If resuming, clear manually set time and sync to current time zone time
            val newTime = if (!isPaused) LocalTime.now(it.timeZoneId) else it.currentTime
            val manualTime = if (!isPaused) null else it.manuallySetTime // Clear manual time on resume
            it.copy(isPaused = isPaused, currentTime = newTime, manuallySetTime = manualTime)
        }
        if (isPaused) {
            stopTicker() // Stop ticker when paused
        } else {
            startTicker() // Restart ticker when resumed
        }
        saveState(_uiState.value)
    }

    fun resetTime() {
        _uiState.update {
            it.copy(
                currentTime = LocalTime.now(it.timeZoneId),
                isPaused = false, // Ensure not paused after reset
                manuallySetTime = null // Clear manually set time
            )
        }
        startTicker() // Ensure ticker is running
        saveState(_uiState.value)
    }

    // Called by the View/Service when user finishes dragging a hand
    fun setManuallySetTime(manualTime: LocalTime) {
         if (!_uiState.value.isPaused) {
             Log.w(TAG, "Attempted to set manual time while clock is running. Pausing first.")
             setPaused(true) // Force pause if not already paused
         }
         _uiState.update {
             it.copy(
                 manuallySetTime = manualTime,
                 currentTime = manualTime // Update display time immediately
             )
         }
        saveState(_uiState.value) // Save the manually set time
    }


    // --- Settings Updates (Called from Settings UI/Service) ---

    fun updateMode(newMode: String) { // "digital" or "analog"
        if (_uiState.value.mode == newMode) return
        // Reset time when changing mode to avoid visual glitches
        val resetTime = LocalTime.now(_uiState.value.timeZoneId)
        _uiState.update { it.copy(mode = newMode, currentTime = resetTime, manuallySetTime = null, isPaused = false) }
        startTicker() // Ensure ticker runs after mode change
        saveState(_uiState.value)
    }

    fun updateColor(newColor: Int) {
        if (_uiState.value.clockColor == newColor) return
        _uiState.update { it.copy(clockColor = newColor) }
        saveState(_uiState.value)
    }

    fun updateIs24Hour(is24: Boolean) {
        if (_uiState.value.is24Hour == is24) return
        _uiState.update { it.copy(is24Hour = is24) }
        saveState(_uiState.value)
    }

    fun updateTimeZone(zoneId: ZoneId) {
        if (_uiState.value.timeZoneId == zoneId) return
        // Update time immediately to reflect new zone, clear manual time
        val newTime = LocalTime.now(zoneId)
        _uiState.update { it.copy(timeZoneId = zoneId, currentTime = newTime, manuallySetTime = null) }
        // Ticker will continue with the new zone
        saveState(_uiState.value)
    }

    fun updateDisplaySeconds(display: Boolean) {
        if (_uiState.value.displaySeconds == display) return
        _uiState.update { it.copy(displaySeconds = display) }
        saveState(_uiState.value)
    }

    fun updateIsNested(isNested: Boolean) {
        if (_uiState.value.isNested == isNested) return
        _uiState.update { it.copy(isNested = isNested) }
        saveState(_uiState.value)
    }

    fun updateWindowPosition(x: Int, y: Int) {
        if (_uiState.value.windowX == x && _uiState.value.windowY == y) return
        _uiState.update { it.copy(windowX = x, windowY = y) }
        saveState(_uiState.value)
    }

    fun updateWindowSize(width: Int, height: Int) {
        if (_uiState.value.windowWidth == width && _uiState.value.windowHeight == height) return
        _uiState.update { it.copy(windowWidth = width, windowHeight = height) }
        saveState(_uiState.value)
    }

    // --- Persistence ---
    private fun saveState(state: ClockState) {
        val currentId = state.clockId
        if (currentId == -1) {
            Log.w(TAG, "Cannot save state, invalid clockId: $currentId")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = mapStateToEntity(state)
                clockDao.insertOrUpdate(entity)
                Log.d(TAG, "Saved state for clock $currentId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save state for clock $currentId", e)
                // TODO: Consider notifying UI about save failure
            }
        }
    }

     fun deleteState() {
        val currentId = clockId
        if (currentId == null || currentId == -1) {
            Log.w(TAG, "Cannot delete state, invalid clockId: $currentId")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                clockDao.deleteById(currentId)
                Log.d(TAG, "Deleted state for clock $currentId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete state for clock $currentId", e)
            }
        }
    }


    // --- Mappers ---
    private fun mapEntityToState(entity: ClockStateEntity): ClockState {
        val timeZone = try { ZoneId.of(entity.timeZoneId) } catch (e: Exception) { ZoneId.systemDefault() }
        val manualTime = entity.manuallySetTimeSeconds?.let { LocalTime.ofSecondOfDay(it % (24 * 3600)) } // Convert seconds back to LocalTime
        val currentTime = manualTime ?: LocalTime.now(timeZone) // Use manual time if set, else current time

        return ClockState(
            clockId = entity.clockId,
            timeZoneId = timeZone,
            isPaused = entity.isPaused,
            displaySeconds = entity.displaySeconds,
            is24Hour = entity.is24Hour,
            clockColor = entity.clockColor,
            mode = entity.mode,
            isNested = entity.isNested,
            windowX = entity.windowX,
            windowY = entity.windowY,
            windowWidth = entity.windowWidth,
            windowHeight = entity.windowHeight,
            currentTime = currentTime, // Set initial current time
            manuallySetTime = manualTime // Set loaded manual time
        )
    }

    private fun mapStateToEntity(state: ClockState): ClockStateEntity {
        // Convert manually set LocalTime to total seconds of the day for storage
        val manualTimeSeconds = state.manuallySetTime?.toSecondOfDay()?.toLong()

        return ClockStateEntity(
            clockId = state.clockId,
            timeZoneId = state.timeZoneId.id,
            isPaused = state.isPaused,
            displaySeconds = state.displaySeconds,
            is24Hour = state.is24Hour,
            clockColor = state.clockColor,
            mode = state.mode,
            isNested = state.isNested,
            windowX = state.windowX,
            windowY = state.windowY,
            windowWidth = state.windowWidth,
            windowHeight = state.windowHeight,
            manuallySetTimeSeconds = manualTimeSeconds // Store as Long?
        )
    }

    // --- Cleanup ---
    override fun onCleared() {
        Log.d(TAG, "ViewModel cleared for clockId: $clockId")
        stopTicker()
        // State should ideally be saved on every change, but a final save could be added here if needed.
        // saveState(_uiState.value)
        super.onCleared()
    }
}