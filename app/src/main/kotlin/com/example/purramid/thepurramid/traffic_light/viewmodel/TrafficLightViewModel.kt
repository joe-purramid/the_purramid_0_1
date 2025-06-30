package com.example.purramid.thepurramid.traffic_light.viewmodel

// import androidx.lifecycle.viewModelScope
import android.app.Application
import android.content.pm.PackageManager
import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.CountDownTimer
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purramid.thepurramid.data.db.TrafficLightDao
import com.example.purramid.thepurramid.data.db.TrafficLightStateEntity
import com.example.purramid.thepurramid.traffic_light.AdjustValuesFragment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class TrafficLightViewModel @Inject constructor(
    private val trafficLightDao: TrafficLightDao, // Inject DAO
    savedStateHandle: SavedStateHandle // Inject SavedStateHandle
) : ViewModel() {

    companion object {
        const val KEY_INSTANCE_ID = TrafficLightActivity.EXTRA_INSTANCE_ID // Use key from Activity
        private const val TAG = "TrafficLightVM"
    }

    // instanceId passed via Intent/Args through SavedStateHandle
    private val instanceId: Int? = null

    fun initialize(id: Int) {
        if (instanceId != null) {
            Log.w(TAG, "ViewModel already initialized with ID: $instanceId")
            return
        }

        instanceId = id
        Log.d(TAG, "Initializing ViewModel for instanceId: $id")
        loadInitialState(id)
    }

    private fun checkMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun onMicrophonePermissionGranted() {
        _uiState.update {
            it.copy(
                isMicrophoneAvailable = true,
                needsMicrophonePermission = false
            )
        }

        // If in Responsive mode, start monitoring
        if (_uiState.value.currentMode == TrafficLightMode.RESPONSIVE_CHANGE) {
            startMicrophoneMonitoring()
        }
    }

    fun clearMicrophonePermissionRequest() {
        _uiState.update { it.copy(needsMicrophonePermission = false) }
    }

    private val _uiState = MutableStateFlow(TrafficLightState())
    val uiState: StateFlow<TrafficLightState> = _uiState.asStateFlow()

    // Variables for double-tap detection
    private var lastTapTimeMs: Long = 0
    private var lastTappedColor: LightColor? = null
    private val doubleTapTimeoutMs: Long = 500 // Standard double-tap timeout
    private var lastGreenTapTime = 0L
    private val DOUBLE_TAP_THRESHOLD = 500L // ms

    // Variables for microphone access
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    init {
        Log.d(TAG, "ViewModel created, awaiting initialization")
        if (instanceId != null) {
            loadInitialState(instanceId)
        } else {
            Log.e(TAG, "Instance ID is null, cannot load state.")
            // Consider setting an error state or using a default non-persistent instance ID
            _uiState.update { it.copy(instanceId = -1) } // Indicate error or default
        }
    }

    private fun loadInitialState(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = trafficLightDao.getById(id)
            withContext(Dispatchers.Main) {
                if (entity != null) {
                    Log.d(TAG, "Loaded state from DB for instance $id")
                    _uiState.value = mapEntityToState(entity)
                } else {
                    Log.d(TAG, "No saved state found for instance $id, using defaults.")
                    // Initialize with default state for this new instance ID
                    val defaultState = TrafficLightState(instanceId = id)
                    _uiState.value = defaultState
                    // Save the initial default state to the DB
                    saveState(defaultState)
                }
            }
        }
    }

    fun handleLightTap(tappedColor: LightColor) {
        val currentTimeMs = SystemClock.elapsedRealtime()
        val currentState = _uiState.value

        if (currentState.currentMode != TrafficLightMode.MANUAL_CHANGE) return

        var newActiveLight: LightColor? = null
        if (currentState.activeLight == tappedColor &&
            (currentTimeMs - lastTapTimeMs) < doubleTapTimeoutMs &&
            lastTappedColor == tappedColor
        ) {
            // Double tap on active: turn off
            newActiveLight = null
            lastTapTimeMs = 0
            lastTappedColor = null
        } else {
            // Single tap or different light: turn tapped on
            newActiveLight = tappedColor
            lastTapTimeMs = currentTimeMs
            lastTappedColor = tappedColor
        }

        if (currentState.activeLight != newActiveLight) {
            _uiState.update { it.copy(activeLight = newActiveLight) }
            saveState(_uiState.value) // Save updated state
        }

        if (currentState.isDangerousAlertActive && tappedColor == LightColor.GREEN) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastGreenTapTime < DOUBLE_TAP_THRESHOLD) {
                // Double tap detected on green - dismiss alert
                deactivateDangerousAlert()
                lastGreenTapTime = 0L
                return
            }
            lastGreenTapTime = currentTime
            return // Don't process single taps in danger mode
        }

        // Reset green tap timer if not in danger mode
        lastGreenTapTime = 0L
    }

    // Add method to check decibel levels for danger
    fun checkDecibelForDanger(db: Int) {
        val currentState = _uiState.value

        // Only check if dangerous sound alert is enabled
        if (!currentState.responsiveModeSettings.dangerousSoundAlertEnabled) return

        if (db >= 150 && !currentState.isDangerousAlertActive) {
            activateDangerousAlert()
        }
    }

    fun setOrientation(newOrientation: Orientation) {
        if (_uiState.value.orientation == newOrientation) return
        _uiState.update { it.copy(orientation = newOrientation) }
        saveState(_uiState.value)
    }

    fun setMode(newMode: TrafficLightMode) {
        val currentState = _uiState.value
        if (currentState.currentMode == newMode) return

        var updatedActiveLight = currentState.activeLight
        if (currentState.activeLight != null) {
            updatedActiveLight = null // Clear light when changing mode
        }

        _uiState.update { it.copy(currentMode = newMode, activeLight = updatedActiveLight) }
        saveState(_uiState.value)

        // Handle mode-specific initialization
        when (newMode) {
            TrafficLightMode.RESPONSIVE_CHANGE -> {
                if (!checkMicrophonePermission()) {
                    // Trigger permission request through the service
                    _uiState.update { it.copy(needsMicrophonePermission = true) }
                } else {
                    startMicrophoneMonitoring()
                }
            }
            TrafficLightMode.MANUAL_CHANGE -> {
                stopMicrophoneMonitoring()
            }
            TrafficLightMode.TIMED_CHANGE -> {
                stopMicrophoneMonitoring()
            }
        }
    }

    fun toggleBlinking(isEnabled: Boolean) {
        if (_uiState.value.isBlinkingEnabled == isEnabled) return
        _uiState.update { it.copy(isBlinkingEnabled = isEnabled) }
        saveState(_uiState.value)
    }

    fun setSettingsOpen(isOpen: Boolean) {
        // isSettingsOpen is likely transient UI state, maybe don't persist?
        // If persistence is desired, uncomment saveState.
        _uiState.update { it.copy(isSettingsOpen = isOpen) }
    }

    fun setShowTimeRemaining(show: Boolean) {
        if (_uiState.value.showTimeRemaining == show) return
        _uiState.update { it.copy(showTimeRemaining = show) }
        saveState(_uiState.value) // Save the change
   }

    fun updateMessages(messages: TrafficLightMessages) {
        _uiState.update { it.copy(messages = messages) }
        saveState(_uiState.value)
    }

    fun addSequence(sequence: TimedSequence) {
        val currentSequences = _uiState.value.timedSequences
        if (currentSequences.size >= TimedSequence.MAX_SEQUENCES) return

        _uiState.update {
            it.copy(timedSequences = currentSequences + sequence)
        }
        saveState()
    }

    fun updateSequence(sequence: TimedSequence) {
        val currentSequences = _uiState.value.timedSequences.toMutableList()
        val index = currentSequences.indexOfFirst { it.id == sequence.id }

        if (index >= 0) {
            currentSequences[index] = sequence
            _uiState.update {
                it.copy(timedSequences = currentSequences)
            }
            saveState()
        }
    }

    fun deleteSequence(sequenceId: String) {
        _uiState.update { state ->
            state.copy(
                timedSequences = state.timedSequences.filter { it.id != sequenceId },
                // Clear active sequence if it was deleted
                activeSequenceId = if (state.activeSequenceId == sequenceId) null else state.activeSequenceId
            )
        }
        saveState()
    }

    private var sequenceTimer: CountDownTimer? = null
    private var timerJob: Job? = null

    fun startTimedSequence() {
        val currentState = _uiState.value
        val sequenceId = currentState.activeSequenceId ?: return
        val sequence = currentState.timedSequences.find { it.id == sequenceId } ?: return

        if (sequence.steps.isEmpty()) return

        _uiState.update {
            it.copy(
                isSequencePlaying = true,
                currentStepIndex = 0,
                elapsedStepSeconds = 0
            )
        }

        startStepTimer()
    }

    fun pauseTimedSequence() {
        sequenceTimer?.cancel()
        timerJob?.cancel()
        _uiState.update { it.copy(isSequencePlaying = false) }
    }

    fun resumeTimedSequence() {
        if (_uiState.value.activeSequenceId != null) {
            _uiState.update { it.copy(isSequencePlaying = true) }
            startStepTimer()
        }
    }

    fun resetTimedSequence() {
        val currentState = _uiState.value
        val wasPlaying = currentState.isSequencePlaying

        sequenceTimer?.cancel()
        timerJob?.cancel()

        // Check if we're at the beginning of current step
        if (currentState.elapsedStepSeconds == 0) {
            // Reset to first step
            _uiState.update {
                it.copy(
                    currentStepIndex = 0,
                    elapsedStepSeconds = 0,
                    isSequencePlaying = false
                )
            }
        } else {
            // Reset current step
            _uiState.update {
                it.copy(
                    elapsedStepSeconds = 0,
                    isSequencePlaying = false
                )
            }
        }

        // Blink effect
        blinkCurrentLight()

        if (wasPlaying) {
            // Resume if was playing
            viewModelScope.launch {
                delay(300) // Wait for blink to complete
                resumeTimedSequence()
            }
        }
    }

    private fun startStepTimer() {
        sequenceTimer?.cancel()
        timerJob?.cancel()

        val currentState = _uiState.value
        val sequence = currentState.timedSequences.find { it.id == currentState.activeSequenceId } ?: return
        val currentStep = sequence.steps.getOrNull(currentState.currentStepIndex) ?: return

        // Update active light for current step
        _uiState.update { it.copy(activeLight = currentStep.color) }

        val remainingSeconds = currentStep.durationSeconds - currentState.elapsedStepSeconds

        timerJob = viewModelScope.launch {
            for (i in 1..remainingSeconds) {
                delay(1000)
                if (!_uiState.value.isSequencePlaying) break

                _uiState.update { state ->
                    state.copy(elapsedStepSeconds = state.elapsedStepSeconds + 1)
                }
            }

            if (_uiState.value.isSequencePlaying) {
                moveToNextStep()
            }
        }
    }

    private fun moveToNextStep() {
        val currentState = _uiState.value
        val sequence = currentState.timedSequences.find { it.id == currentState.activeSequenceId } ?: return

        val nextStepIndex = currentState.currentStepIndex + 1

        if (nextStepIndex < sequence.steps.size) {
            // Check if current and next step have same color
            val currentColor = sequence.steps[currentState.currentStepIndex].color
            val nextColor = sequence.steps[nextStepIndex].color

            _uiState.update {
                it.copy(
                    currentStepIndex = nextStepIndex,
                    elapsedStepSeconds = 0
                )
            }

            if (currentColor == nextColor) {
                // Blink twice if same color (spec 19.3.3)
                blinkCurrentLight(times = 2)
            }

            startStepTimer()
        } else {
            // Sequence complete
            sequenceComplete()
        }
    }

    private fun sequenceComplete() {
        sequenceTimer?.cancel()
        timerJob?.cancel()

        _uiState.update {
            it.copy(
                isSequencePlaying = false,
                activeLight = null
            )
        }
    }

    private fun blinkCurrentLight(times: Int = 1) {
        viewModelScope.launch {
            val currentLight = _uiState.value.activeLight
            repeat(times) {
                _uiState.update { it.copy(activeLight = null) }
                delay(300)
                _uiState.update { it.copy(activeLight = currentLight) }
                delay(300)
            }
        }
    }

    fun setActiveSequence(sequenceId: String?) {
        _uiState.update {
            it.copy(
                activeSequenceId = sequenceId,
                currentStepIndex = 0,
                elapsedStepSeconds = 0,
                isSequencePlaying = false
            )
        }
    }

    fun setShowTimeline(show: Boolean) {
        if (_uiState.value.showTimeline == show) return
        _uiState.update { it.copy(showTimeline = show) }
        saveState(_uiState.value) // Save the change
        }

    fun updateResponsiveSettings(newSettings: ResponsiveModeSettings) {
        if (_uiState.value.responsiveModeSettings == newSettings) return
        _uiState.update { it.copy(responsiveModeSettings = newSettings) }
        saveState(_uiState.value)
    }

    fun setDangerousSoundAlert(isEnabled: Boolean) {
        val currentSettings = _uiState.value.responsiveModeSettings
        if (currentSettings.dangerousSoundAlertEnabled == isEnabled) return
        val newSettings = currentSettings.copy(dangerousSoundAlertEnabled = isEnabled)
        updateResponsiveSettings(newSettings) // Calls saveState internally
    }

    fun updateSpecificDbValue(
        colorForRange: AdjustValuesFragment.ColorForRange,
        isMinField: Boolean,
        newValue: Int?
    ) {
        val currentSettings = _uiState.value.responsiveModeSettings
        val updatedSettings = calculateUpdatedRanges(currentSettings, colorForRange, isMinField, newValue)

        if (currentSettings != updatedSettings) {
            updateResponsiveSettings(updatedSettings) // Calls saveState internally
        }
    }

    private fun startMicrophoneMonitoring() {
        if (!checkMicrophonePermission()) {
            _uiState.update { it.copy(isMicrophoneAvailable = false) }
            return
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            44100,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            44100,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true

        recordingThread = thread(start = true) {
            val audioBuffer = ShortArray(bufferSize)
            while (isRecording) {
                val result = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0
                if (result > 0) {
                    val maxAmplitude = audioBuffer.maxOrNull()?.toInt() ?: 0
                    val db = 20 * log10(maxAmplitude.toDouble() / 32767.0) + 90

                    // Check for dangerous sound alert
                    checkDecibelForDanger(db.toInt())

                    // Update light based on decibel ranges
                    updateLightForDecibel(db.toInt())
                }
                Thread.sleep(2000) // Check every 2 seconds as per spec
            }
        }
    }

    private fun updateLightForDecibel(db: Int) {
        val settings = _uiState.value.responsiveModeSettings
        val newLight = when {
            settings.redRange.minDb != null && db >= settings.redRange.minDb!! -> LightColor.RED
            settings.yellowRange.minDb != null && db >= settings.yellowRange.minDb!! -> LightColor.YELLOW
            settings.greenRange.minDb != null && db >= settings.greenRange.minDb!! -> LightColor.GREEN
            else -> null
        }

        _uiState.update { it.copy(
            activeLight = newLight,
            currentDecibelLevel = db
        )}
    }
    private fun stopMicrophoneMonitoring() {
        isRecording = false
        recordingThread?.join()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }

    // Extracted calculation logic (remains complex, needs careful implementation)
    private fun calculateUpdatedRanges(
        currentSettings: ResponsiveModeSettings,
        colorForRange: AdjustValuesFragment.ColorForRange,
        isMinField: Boolean,
        newValue: Int?
    ) : ResponsiveModeSettings {
        // --- START OF COMPLEX LINKED LOGIC (placeholder - needs full implementation) ---
        var newGreen = currentSettings.greenRange
        var newYellow = currentSettings.yellowRange
        var newRed = currentSettings.redRange
        val safeValue = newValue?.coerceIn(0, 120)

        // TODO: Implement the full cascading logic for min/max and N/A states
        // This placeholder just updates the specific field without validation/cascading
        when(colorForRange) {
            AdjustValuesFragment.ColorForRange.GREEN -> newGreen = if (isMinField) newGreen.copy(minDb = safeValue) else newGreen.copy(maxDb = safeValue)
            AdjustValuesFragment.ColorForRange.YELLOW -> newYellow = if (isMinField) newYellow.copy(minDb = safeValue) else newYellow.copy(maxDb = safeValue)
            AdjustValuesFragment.ColorForRange.RED -> newRed = if (isMinField) newRed.copy(minDb = safeValue) else newRed.copy(maxDb = safeValue)
        }
        // --- END OF COMPLEX LINKED LOGIC ---

        // Return potentially modified ranges
        return currentSettings.copy(greenRange = newGreen, yellowRange = newYellow, redRange = newRed)
    }

    fun activateDangerousAlert() {
        val currentState = _uiState.value
        if (currentState.isDangerousAlertActive) return // Already active

        _uiState.update {
            it.copy(
                isDangerousAlertActive = true,
                previousMode = it.currentMode,
                currentMode = TrafficLightMode.DANGER_ALERT,
                dangerousSoundDetectedAt = System.currentTimeMillis()
            )
        }
    }

    fun deactivateDangerousAlert() {
        val currentState = _uiState.value
        if (!currentState.isDangerousAlertActive) return

        _uiState.update {
            it.copy(
                isDangerousAlertActive = false,
                currentMode = it.previousMode ?: TrafficLightMode.MANUAL_CHANGE,
                previousMode = null,
                dangerousSoundDetectedAt = null
            )
        }
    }

    fun setKeyboardAvailable(available: Boolean) {
        _uiState.update { it.copy(isKeyboardAvailable = available) }
    }

    // --- Persistence ---
    fun saveState() {
        saveState(_uiState.value)
    }

    // Keep the private overload for internal use:
    private fun saveState(state: TrafficLightState) {
        val currentInstanceId = state.instanceId
        if (currentInstanceId <= 0) { // Don't save if ID is invalid/default
            Log.w(TAG, "Attempted to save state with invalid instanceId: $currentInstanceId")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = mapStateToEntity(state)
                trafficLightDao.insertOrUpdate(entity)
                Log.d(TAG, "Saved state for instance $currentInstanceId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save state for instance $currentInstanceId", e)
                // Optionally notify UI of save failure
            }
        }
    }

    fun saveWindowPosition(x: Int, y: Int) {
        val currentState = _uiState.value
        if (currentState.windowX == x && currentState.windowY == y) return
        _uiState.update { it.copy(windowX = x, windowY = y) }
        saveState(_uiState.value)
    }

    fun saveWindowSize(width: Int, height: Int) {
        val currentState = _uiState.value
        if (currentState.windowWidth == width && currentState.windowHeight == height) return
        _uiState.update { it.copy(windowWidth = width, windowHeight = height) }
        saveState(_uiState.value)
    }

    // --- Mappers ---
    private fun mapEntityToState(entity: TrafficLightStateEntity): TrafficLightState {
        val responsiveSettings = try {
            gson.fromJson(entity.responsiveModeSettingsJson, ResponsiveModeSettings::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ResponsiveModeSettings JSON, using default.", e)
            ResponsiveModeSettings() // Default on error
        }

        val messages = try {
            gson.fromJson(entity.messagesJson, TrafficLightMessages::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse TrafficLightMessages JSON, using default.", e)
            TrafficLightMessages()
        }

        val sequences = try {
            val listType = object : TypeToken<List<TimedSequence>>() {}.type
            gson.fromJson<List<TimedSequence>>(entity.timedSequencesJson, listType)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse TimedSequence list JSON, using default.", e)
            emptyList<TimedSequence>()
        }
        return TrafficLightState(
            instanceId = entity.instanceId,
            currentMode = try { TrafficLightMode.valueOf(entity.currentMode) } catch (e: Exception) { TrafficLightMode.MANUAL_CHANGE },
            orientation = try { Orientation.valueOf(entity.orientation) } catch (e: Exception) { Orientation.VERTICAL },
            isBlinkingEnabled = entity.isBlinkingEnabled,
            activeLight = entity.activeLight?.let { try { LightColor.valueOf(it) } catch (e: Exception) { null } },
            isSettingsOpen = entity.isSettingsOpen,
            isMicrophoneAvailable = entity.isMicrophoneAvailable,
            numberOfOpenInstances = entity.numberOfOpenInstances,
            responsiveModeSettings = responsiveSettings,
            showTimeRemaining = entity.showTimeRemaining,
            showTimeline = entity.showTimeline,
            windowX = entity.windowX,
            windowY = entity.windowY,
            windowWidth = entity.windowWidth,
            windowHeight = entity.windowHeight,
            messages = messages,
            timedSequences = sequences,
            activeSequenceId = entity.activeSequenceId,
            currentStepIndex = entity.currentStepIndex,
            elapsedStepSeconds = entity.elapsedStepSeconds,
            isSequencePlaying = entity.isSequencePlaying,
            isDangerousAlertActive = entity.isDangerousAlertActive,
            previousMode = entity.previousMode?.let { try { TrafficLightMode.valueOf(it) } catch (e: Exception) { null } },
            currentDecibelLevel = entity.currentDecibelLevel,
            dangerousSoundDetectedAt = entity.dangerousSoundDetectedAt
        )
    }

    private fun mapStateToEntity(state: TrafficLightState): TrafficLightStateEntity {
        val responsiveJson = gson.toJson(state.responsiveModeSettings)
        val messagesJson = gson.toJson(state.messages)
        val sequencesJson = gson.toJson(state.timedSequences)

        return TrafficLightStateEntity(
            instanceId = state.instanceId,
            currentMode = state.currentMode.name,
            orientation = state.orientation.name,
            isBlinkingEnabled = state.isBlinkingEnabled,
            activeLight = state.activeLight?.name,
            isSettingsOpen = false,
            isMicrophoneAvailable = state.isMicrophoneAvailable,
            numberOfOpenInstances = state.numberOfOpenInstances, // This might be better managed globally
            responsiveModeSettingsJson = responsiveJson,
            showTimeRemaining = state.showTimeRemaining,
            showTimeline = state.showTimeline,
            windowX = state.windowX,
            windowY = state.windowY,
            windowWidth = state.windowWidth,
            windowHeight = state.windowHeight,
            messagesJson = messagesJson,
            timedSequencesJson = sequencesJson,
            activeSequenceId = state.activeSequenceId,
            currentStepIndex = state.currentStepIndex,
            elapsedStepSeconds = state.elapsedStepSeconds,
            isSequencePlaying = state.isSequencePlaying,
            isDangerousAlertActive = state.isDangerousAlertActive,
            previousMode = state.previousMode?.name,
            currentDecibelLevel = state.currentDecibelLevel,
            dangerousSoundDetectedAt = state.dangerousSoundDetectedAt
        )
    }

    fun togglePlayPause() {
        if (_uiState.value.isSequencePlaying) {
            pauseTimedSequence()
        } else {
            if (_uiState.value.currentStepIndex == 0 && _uiState.value.elapsedStepSeconds == 0) {
                startTimedSequence()
            } else {
                resumeTimedSequence()
            }
        }
    }

    override fun onCleared() {
        sequenceTimer?.cancel()
        timerJob?.cancel()
        super.onCleared()
    }
}