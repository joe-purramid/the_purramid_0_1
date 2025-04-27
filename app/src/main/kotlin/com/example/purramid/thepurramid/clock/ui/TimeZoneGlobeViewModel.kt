package com.example.purramid.thepurramid.clock.ui

import android.graphics.Color
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purramid.thepurramid.clock.data.TimeZoneRepository
import io.github.sceneview.math.Rotation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.locationtech.jts.geom.Polygon
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone // Keep for getRawOffset if needed, but prefer ZoneId
import javax.inject.Inject
import kotlin.math.abs

// Data class to hold processed info for overlays
data class TimeZoneOverlayInfo(
    val tzid: String,
    val polygons: List<Polygon>,
    val color: Int // Use Android Color Int
)

// Represents the state of the Globe UI
data class TimeZoneGlobeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val timeZoneOverlays: List<TimeZoneOverlayInfo> = emptyList(),
    val activeTimeZoneId: String? = TimeZone.getDefault().id, // Initial default
    val activeTimeZoneInfo: ActiveZoneDisplayInfo? = null,
    val currentRotation: Rotation = Rotation(0f, 0f, 0f)
)

data class ActiveZoneDisplayInfo(
    val northernCity: String,
    val southernCity: String,
    val utcOffsetString: String
)


@HiltViewModel
class TimeZoneGlobeViewModel @Inject constructor(
    private val repository: TimeZoneRepository
) : ViewModel() {

    private val TAG = "TimeZoneGlobeViewModel"

    private val _uiState = MutableLiveData(TimeZoneGlobeUiState())
    val uiState: LiveData<TimeZoneGlobeUiState> = _uiState

    // Keep track of raw polygon data separate from UI overlay info
    private var rawTimeZonePolygons: Map<String, List<Polygon>> = emptyMap()
    private var timeZoneOffsets: MutableMap<String, Int> = mutableMapOf() // Offset in seconds

    // Colors for hourly offsets (Red, Yellow, Green, Blue cycle) - Now Int
    private val timeZoneColors = listOf(
        Color.argb(128, 255, 0, 0),   // Red (alpha 0.5)
        Color.argb(128, 255, 255, 0), // Yellow (alpha 0.5)
        Color.argb(128, 0, 255, 0),   // Green (alpha 0.5)
        Color.argb(128, 0, 0, 255)    // Blue (alpha 0.5)
    )
    private val nonHourlyColor = Color.argb(128, 128, 0, 128) // Violet (alpha 0.5) - Placeholder
    private val activeTimeZoneColor = Color.argb(180, 255, 165, 0) // Orange (alpha ~0.7)


    init {
        loadTimeZoneData()
    }

    private fun loadTimeZoneData() {
        _uiState.value = _uiState.value?.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = repository.getTimeZonePolygons()
            result.onSuccess { polygons ->
                rawTimeZonePolygons = polygons
                calculateAllTimeZoneOffsets()
                processDataForUi()
                updateActiveZoneInfo(_uiState.value?.activeTimeZoneId) // Update info for default zone
            }.onFailure { exception ->
                Log.e(TAG, "Failed to load time zone data", exception)
                _uiState.value = _uiState.value?.copy(isLoading = false, error = "Failed to load time zones")
            }
        }
    }

    // Calculate and store current offsets (considering DST) for all loaded time zones
    private fun calculateAllTimeZoneOffsets() {
        val now = Instant.now()
        timeZoneOffsets.clear()
        rawTimeZonePolygons.keys.forEach { tzId ->
            try {
                val zoneId = ZoneId.of(tzId)
                val zonedDateTime = ZonedDateTime.ofInstant(now, zoneId)
                timeZoneOffsets[tzId] = zonedDateTime.offset.totalSeconds
            } catch (e: Exception) {
                Log.e(TAG, "Could not get ZoneId or offset for: $tzId", e)
            }
        }
        Log.d(TAG, "Calculated offsets for ${timeZoneOffsets.size} time zones.")
    }

    // Processes raw polygons and offsets into list of TimeZoneOverlayInfo for the UI
    private fun processDataForUi() {
        val overlays = mutableListOf<TimeZoneOverlayInfo>()
        val activeId = _uiState.value?.activeTimeZoneId

        // Group by offset for coloring (similar to previous logic)
        val offsetsGrouped = timeZoneOffsets.entries.groupBy { it.value / 3600.0 }
        val sortedOffsets = offsetsGrouped.keys.sorted()
        val hourlyOffsetMap = sortedOffsets.filter { it == it.toInt().toDouble() }.associateWith { offset ->
            val colorIndex = (offset.toInt() % timeZoneColors.size + timeZoneColors.size) % timeZoneColors.size
            timeZoneColors[colorIndex]
        }

        rawTimeZonePolygons.forEach { (tzId, polygons) ->
            val currentOffsetSeconds = timeZoneOffsets[tzId] ?: return@forEach // Skip if no offset
            val isSystemActiveZone = tzId == activeId
            val currentOffsetHours = currentOffsetSeconds / 3600.0

            val zoneColor: Int = if (isSystemActiveZone) {
                activeTimeZoneColor
            } else {
                if (currentOffsetHours == currentOffsetHours.toInt().toDouble()) {
                    hourlyOffsetMap[currentOffsetHours] ?: nonHourlyColor
                } else {
                    // Placeholder for non-hourly/striped
                    // TODO: Implement logic to find bounding colors if needed for striping later
                    nonHourlyColor
                }
            }
            overlays.add(TimeZoneOverlayInfo(tzId, polygons, zoneColor))
        }

        _uiState.value = _uiState.value?.copy(
            isLoading = false,
            timeZoneOverlays = overlays,
            error = null
        )
        Log.d(TAG, "Processed ${overlays.size} overlays for UI.")
    }


    // Call this when the user interacts or system default changes
    fun setActiveTimeZone(tzId: String) {
        if (_uiState.value?.activeTimeZoneId != tzId) {
            _uiState.value = _uiState.value?.copy(activeTimeZoneId = tzId)
            processDataForUi() // Re-process to update active color
            updateActiveZoneInfo(tzId) // Update text info
        }
    }

    // Updates the display text info for the active zone
    private fun updateActiveZoneInfo(timeZoneId: String?) {
        _uiState.value = _uiState.value?.copy(activeTimeZoneInfo = null)
        if (timeZoneId == null) {
            val offsetString = getFormattedOffset(timeZoneId) ?: "Invalid Zone"
            // return
        }

        try {
            val zoneId = ZoneId.of(timeZoneId)
            val now = ZonedDateTime.now(zoneId)
            val offset = now.offset
            val offsetHours = offset.totalSeconds / 3600
            val offsetMinutes = abs((offset.totalSeconds % 3600) / 60)
            val offsetString = when {
                offset.totalSeconds == 0 -> "UTC"
                else -> "UTC${if (offsetHours >= 0) "+" else ""}${offsetHours}${if (offsetMinutes > 0) ":${"%02d".format(offsetMinutes)}" else ""}"
            }

            // --- TODO: Find Representative Cities ---
            val northernCity = timeZoneId.substringAfterLast('/', timeZoneId).replace('_', ' ') + " (N)"
            val southernCity = "(City lookup TBD)"
            // --- End TODO ---

            _uiState.value = _uiState.value?.copy(
                activeTimeZoneInfo = ActiveZoneDisplayInfo(northernCity, southernCity, offsetString)
            )
            Log.d(TAG, "Updated active zone info for: $timeZoneId ($offsetString)")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating info display for $timeZoneId", e)
            _uiState.value = _uiState.value?.copy(activeTimeZoneInfo = null) // Clear info on error
        }
    }

    fun updateRotation(newRotation: Quaternion) {
        _uiState.value = _uiState.value?.copy(currentRotation = newRotation)
    }
}