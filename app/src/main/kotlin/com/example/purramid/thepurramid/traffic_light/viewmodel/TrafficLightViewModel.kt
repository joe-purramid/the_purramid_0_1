package com.example.purramid.thepurramid.traffic_light.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.media.AudioFormat
import android.media.MediaRecorder
import android.Manifest
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.data.db.TrafficLightDao
import com.example.purramid.thepurramid.data.db.TrafficLightStateEntity
import com.example.purramid.thepurramid.traffic_light.AdjustValuesFragment
import com.example.purramid.thepurramid.util.Event
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import kotlin.concurrent.thread
import kotlin.math.log10

@HiltViewModel
class TrafficLightViewModel @Inject constructor(
    private val trafficLightDao: TrafficLightDao,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        const val KEY_INSTANCE_ID = "extra_traffic_light_instance_id"
        private const val TAG = "TrafficLightVM"
        private val gson = Gson()

        // Microphone constants
        private const val MICROPHONE_CHECK_INTERVAL = 2000L // 2 seconds per spec
        private const val MICROPHONE_GRACE_PERIOD = 10000L // 10 seconds
        private const val MICROPHONE_RECOVERY_CHECK_INTERVAL = 30000L // 30 seconds
        private const val MICROPHONE_RECOVERY_TIMEOUT = 300000L // 5 minutes
        private const val BLINK_THRESHOLD_DB = 5 // Within 5 dB of max
        private const val DANGEROUS_SOUND_THRESHOLD = 150
    }

    private var instanceId: Int? = null

    fun initialize(id: Int) {
        if (instanceId != null) {
            Log.w(TAG, "ViewModel already initialized with ID: $instanceId")
            return
        }

        instanceId = id
        Log.d(TAG, "Initializing ViewModel for instanceId: $id")
        loadInitialState(id)
    }

    private val _uiState = MutableStateFlow(TrafficLightState())
    val uiState: StateFlow<TrafficLightState> = _uiState.asStateFlow()

    // Variables for double-tap detection
    private var lastTapTimeMs: Long = 0
    private var lastTappedColor: LightColor? = null
    private val doubleTapTimeoutMs: Long = 500
    private var lastGreenTapTime = 0L
    private val DOUBLE_TAP_THRESHOLD = 500L

    // Variables for microphone access
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var microphoneCheckHandler: Handler? = null
    private var microphoneCheckRunnable: Runnable? = null
    private var lastMicrophoneAvailable = true
    private var microphoneLostTimestamp: Long? = null

    // Timer variables for Timed mode
    private var sequenceTimer: Job? = null

    // Snackbar event for UI communication
    private val _snackbarEvent = MutableLiveData<Event<String>>()
    val snackbarEvent: LiveData<Event<String>> = _snackbarEvent

    init {
        Log.d(TAG, "ViewModel created, awaiting initialization")
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
                    val defaultState = TrafficLightState(instanceId = id)
                    _uiState.value = defaultState
                    saveState(defaultState)
                }
            }
        }
    }

    fun handleLightTap(tappedColor: LightColor) {
        val currentTimeMs = SystemClock.elapsedRealtime()
        val currentState = _uiState.value

        // Handle dangerous alert dismissal
        if (currentState.isDangerousAlertActive && tappedColor == LightColor.GREEN) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastGreenTapTime < DOUBLE_TAP_THRESHOLD) {
                deactivateDangerousAlert()
                lastGreenTapTime = 0L
                return
            }
            lastGreenTapTime = currentTime
            return
        }

        // Reset green tap timer if not in danger mode
        lastGreenTapTime = 0L

        if (currentState.currentMode != TrafficLightMode.MANUAL_CHANGE) return

        var newActiveLight: LightColor? = null
        if (currentState.activeLight == tappedColor &&
            (currentTimeMs - lastTapTimeMs) < doubleTapTimeoutMs &&
            lastTappedColor == tappedColor
        ) {
            newActiveLight = null
            lastTapTimeMs = 0
            lastTappedColor = null
        } else {
            newActiveLight = tappedColor
            lastTapTimeMs = currentTimeMs
            lastTappedColor = tappedColor
        }

        if (currentState.activeLight != newActiveLight) {
            _uiState.update { it.copy(activeLight = newActiveLight) }
            saveState()
        }
    }

    fun setOrientation(newOrientation: Orientation) {
        if (_uiState.value.orientation == newOrientation) return
        _uiState.update { it.copy(orientation = newOrientation) }
        saveState()
    }

    fun setMode(newMode: TrafficLightMode) {
        val currentState = _uiState.value
        if (currentState.currentMode == newMode) return

        var updatedActiveLight = currentState.activeLight
        if (currentState.activeLight != null) {
            updatedActiveLight = null
        }

        _uiState.update { it.copy(currentMode = newMode, activeLight = updatedActiveLight) }
        saveState()

        // Handle mode-specific initialization
        when (newMode) {
            TrafficLightMode.RESPONSIVE_CHANGE -> {
                if (!checkMicrophonePermission()) {
                    _uiState.update { it.copy(needsMicrophonePermission = true) }
                } else {
                    startMicrophoneMonitoring()
                }
            }
            TrafficLightMode.MANUAL_CHANGE -> {
                stopMicrophoneMonitoring()
                pauseTimedSequence()
            }
            TrafficLightMode.TIMED_CHANGE -> {
                stopMicrophoneMonitoring()
            }
            TrafficLightMode.DANGER_ALERT -> {
                // Handled by activateDangerousAlert
            }
        }
    }

    fun toggleBlinking(isEnabled: Boolean) {
        if (_uiState.value.isBlinkingEnabled == isEnabled) return
        _uiState.update { it.copy(isBlinkingEnabled = isEnabled) }
        saveState()
    }

    fun setSettingsOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isSettingsOpen = isOpen) }
    }

    fun setShowTimeRemaining(show: Boolean) {
        if (_uiState.value.showTimeRemaining == show) return
        _uiState.update { it.copy(showTimeRemaining = show) }
        saveState()
    }

    fun setShowTimeline(show: Boolean) {
        if (_uiState.value.showTimeline == show) return
        _uiState.update { it.copy(showTimeline = show) }
        saveState()
    }

    fun updateMessages(messages: TrafficLightMessages) {
        _uiState.update { it.copy(messages = messages) }
        saveState()
    }

    fun addSequence(sequence: TimedSequence) {
        val currentSequences = _uiState.value.timedSequences
        if (currentSequences.size >= TimedSequence.MAX_SEQUENCES) return

        _uiState.update {
            it.copy(
                timedSequences = currentSequences + sequence,
                activeSequenceId = sequence.id)
        }
        saveState()
    }

    fun updateSequence(sequence: TimedSequence) {
        val currentSequences = _uiState.value.timedSequences.toMutableList()
        val index = currentSequences.indexOfFirst { it.id == sequence.id }

        if (index >= 0) {
            currentSequences[index] = sequence
            _uiState.update {
                it.copy(
                    timedSequences = currentSequences,
                    activeSequenceId = sequence.id)
            }
            saveState()
        }
    }

    fun deleteSequence(sequenceId: String) {
        _uiState.update { state ->
            state.copy(
                timedSequences = state.timedSequences.filter { it.id != sequenceId },
                activeSequenceId = if (state.activeSequenceId == sequenceId) null else state.activeSequenceId
            )
        }
        saveState()
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
        saveState()
    }

    fun updateResponsiveSettings(newSettings: ResponsiveModeSettings) {
        if (_uiState.value.responsiveModeSettings == newSettings) return
        _uiState.update { it.copy(responsiveModeSettings = newSettings) }
        saveState()
    }

    fun setDangerousSoundAlert(isEnabled: Boolean) {
        val currentSettings = _uiState.value.responsiveModeSettings
        if (currentSettings.dangerousSoundAlertEnabled == isEnabled) return
        val newSettings = currentSettings.copy(dangerousSoundAlertEnabled = isEnabled)
        updateResponsiveSettings(newSettings)
    }

    fun updateSpecificDbValue(
        colorForRange: AdjustValuesFragment.ColorForRange,
        isMinField: Boolean,
        newValue: Int?
    ) {
        val currentSettings = _uiState.value.responsiveModeSettings
        val updatedSettings = calculateUpdatedRanges(currentSettings, colorForRange, isMinField, newValue)

        if (currentSettings != updatedSettings) {
            updateResponsiveSettings(updatedSettings)
        }
    }

    // Microphone permission and monitoring
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

        if (_uiState.value.currentMode == TrafficLightMode.RESPONSIVE_CHANGE) {
            startMicrophoneMonitoring()
        }
    }

    fun clearMicrophonePermissionRequest() {
        _uiState.update { it.copy(needsMicrophonePermission = false) }
    }

    fun clearMicrophoneRecoverySnackbar() {
        _uiState.update { it.copy(showMicrophoneRecoverySnackbar = false) }
    }

    private fun startMicrophoneMonitoring() {
        if (!checkMicrophonePermission()) {
            _uiState.update { it.copy(
                isMicrophoneAvailable = false,
                needsMicrophonePermission = true
            )}
            return
        }

        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                44100,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                handleMicrophoneUnavailable()
                return
            }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                44100,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                handleMicrophoneUnavailable()
                return
            }

            audioRecord?.startRecording()
            isRecording = true
            lastMicrophoneAvailable = true

            microphoneLostTimestamp = null
            _uiState.update { it.copy(
                isMicrophoneAvailable = true,
                showMicrophoneGracePeriodBanner = false
            )}

            recordingThread = thread(start = true) {
                val audioBuffer = ShortArray(bufferSize)

                while (isRecording) {
                    try {
                        val result = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0

                        if (result > 0) {
                            val maxAmplitude = audioBuffer.maxOrNull()?.toInt() ?: 0
                            val db = if (maxAmplitude > 0) {
                                (20 * log10(maxAmplitude.toDouble() / 32767.0) + 90).toInt()
                            } else 0

                            viewModelScope.launch {
                                updateLightForDecibel(db)
                            }
                        } else if (result < 0) {
                            viewModelScope.launch {
                                handleMicrophoneLost()
                            }
                            break
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading from microphone", e)
                        viewModelScope.launch {
                            handleMicrophoneLost()
                        }
                        break
                    }

                    Thread.sleep(MICROPHONE_CHECK_INTERVAL)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing microphone", e)
            handleMicrophoneUnavailable()
        }
    }

    private fun updateLightForDecibel(db: Int) {
        val currentState = _uiState.value
        val settings = currentState.responsiveModeSettings

        if (settings.dangerousSoundAlertEnabled && db >= DANGEROUS_SOUND_THRESHOLD) {
            if (!currentState.isDangerousAlertActive) {
                activateDangerousAlert()
            }
            return
        }

        val newLight = when {
            settings.redRange.minDb != null && settings.redRange.maxDb != null &&
                    db >= settings.redRange.minDb!! && db <= settings.redRange.maxDb!! -> LightColor.RED
            settings.yellowRange.minDb != null && settings.yellowRange.maxDb != null &&
                    db >= settings.yellowRange.minDb!! && db <= settings.yellowRange.maxDb!! -> LightColor.YELLOW
            settings.greenRange.minDb != null && settings.greenRange.maxDb != null &&
                    db >= settings.greenRange.minDb!! && db <= settings.greenRange.maxDb!! -> LightColor.GREEN
            else -> null
        }

        val shouldBlink = when (newLight) {
            LightColor.RED -> settings.redRange.maxDb?.let { db >= it - BLINK_THRESHOLD_DB } ?: false
            LightColor.YELLOW -> settings.yellowRange.maxDb?.let { db >= it - BLINK_THRESHOLD_DB } ?: false
            LightColor.GREEN -> settings.greenRange.maxDb?.let { db >= it - BLINK_THRESHOLD_DB } ?: false
            null -> false
        }

        _uiState.update {
            it.copy(
                activeLight = newLight,
                currentDecibelLevel = db,
                shouldBlinkForThreshold = shouldBlink
            )
        }
    }

    private fun handleMicrophoneLost() {
        if (!lastMicrophoneAvailable) return

        lastMicrophoneAvailable = false
        microphoneLostTimestamp = System.currentTimeMillis()

        _uiState.update { it.copy(
            showMicrophoneGracePeriodBanner = true,
            microphoneGracePeriodMessage = "Microphone temporarily unavailable. Reconnecting..."
        )}

        startGracePeriodTimer()
        startMicrophoneReconnectAttempts()
    }

    private fun startGracePeriodTimer() {
        viewModelScope.launch {
            delay(MICROPHONE_GRACE_PERIOD)

            if (!lastMicrophoneAvailable) {
                switchToManualDueToMicLoss()
            }
        }
    }

    private fun startMicrophoneReconnectAttempts() {
        viewModelScope.launch {
            repeat(5) {
                delay(2000)

                if (tryReconnectMicrophone()) {
                    handleMicrophoneReconnected()
                    return@launch
                }
            }
        }
    }

    private fun tryReconnectMicrophone(): Boolean {
        try {
            stopMicrophoneMonitoring()

            val bufferSize = AudioRecord.getMinBufferSize(
                44100,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                return false
            }

            val testRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                44100,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (testRecord.state == AudioRecord.STATE_INITIALIZED) {
                testRecord.release()
                return true
            }

            testRecord.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reconnect microphone", e)
        }

        return false
    }

    private fun handleMicrophoneReconnected() {
        lastMicrophoneAvailable = true
        microphoneLostTimestamp = null

        _uiState.update { it.copy(
            showMicrophoneGracePeriodBanner = false,
            isMicrophoneAvailable = true
        )}

        startMicrophoneMonitoring()
    }

    private fun switchToManualDueToMicLoss() {
        val wasResponsive = _uiState.value.currentMode == TrafficLightMode.RESPONSIVE_CHANGE

        _uiState.update { it.copy(
            currentMode = TrafficLightMode.MANUAL_CHANGE,
            activeLight = null,
            showMicrophoneGracePeriodBanner = false,
            isMicrophoneAvailable = false,
            wasInResponsiveModeBeforeMicLoss = wasResponsive
        )}

        stopMicrophoneMonitoring()

        _snackbarEvent.value = Event(context.getString(R.string.microphone_disconnected_snackbar))

        if (wasResponsive) {
            startAutomaticRecoveryCheck()
        }
    }

    private fun startAutomaticRecoveryCheck() {
        val startTime = System.currentTimeMillis()

        viewModelScope.launch {
            while (System.currentTimeMillis() - startTime < MICROPHONE_RECOVERY_TIMEOUT) {
                delay(MICROPHONE_RECOVERY_CHECK_INTERVAL)

                if (_uiState.value.currentMode != TrafficLightMode.MANUAL_CHANGE) {
                    break
                }

                if (tryReconnectMicrophone()) {
                    _uiState.update { it.copy(
                        isMicrophoneAvailable = true,
                        showMicrophoneRecoverySnackbar = true
                    )}
                    break
                }
            }
        }
    }

    private fun stopMicrophoneMonitoring() {
        isRecording = false
        recordingThread?.interrupt()
        recordingThread = null

        audioRecord?.let { record ->
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
                record.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping audio record", e)
            }
        }
        audioRecord = null
    }

    private fun handleMicrophoneUnavailable() {
        _uiState.update { it.copy(
            isMicrophoneAvailable = false,
            currentMode = TrafficLightMode.MANUAL_CHANGE,
            activeLight = null
        )}

        _snackbarEvent.value = Event(context.getString(R.string.no_microphone_detected))
    }

    // Timed mode functions
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

        if (currentState.elapsedStepSeconds == 0) {
            _uiState.update {
                it.copy(
                    currentStepIndex = 0,
                    elapsedStepSeconds = 0,
                    isSequencePlaying = false
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    elapsedStepSeconds = 0,
                    isSequencePlaying = false
                )
            }
        }

        blinkCurrentLight()

        if (wasPlaying) {
            viewModelScope.launch {
                delay(300)
                resumeTimedSequence()
            }
        }
    }

    private fun startStepTimer() {
        sequenceTimer?.cancel()

        val currentState = _uiState.value
        val sequence = currentState.timedSequences.find { it.id == currentState.activeSequenceId } ?: return
        val currentStep = sequence.steps.getOrNull(currentState.currentStepIndex) ?: return

        _uiState.update { it.copy(activeLight = currentStep.color) }

        val remainingSeconds = currentStep.durationSeconds - currentState.elapsedStepSeconds

        sequenceTimer = viewModelScope.launch {
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
            val currentColor = sequence.steps[currentState.currentStepIndex].color
            val nextColor = sequence.steps[nextStepIndex].color

            _uiState.update {
                it.copy(
                    currentStepIndex = nextStepIndex,
                    elapsedStepSeconds = 0
                )
            }

            if (currentColor == nextColor) {
                blinkCurrentLight(times = 2)
            }

            startStepTimer()
        } else {
            sequenceComplete()
        }
    }

    private fun sequenceComplete() {
        sequenceTimer?.cancel()

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

    // Dangerous alert functions
    fun activateDangerousAlert() {
        val currentState = _uiState.value
        if (currentState.isDangerousAlertActive) return

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

    // Range calculation
    private fun calculateUpdatedRanges(
        currentSettings: ResponsiveModeSettings,
        colorForRange: AdjustValuesFragment.ColorForRange,
        isMinField: Boolean,
        newValue: Int?
    ): ResponsiveModeSettings {
        var newGreen = currentSettings.greenRange
        var newYellow = currentSettings.yellowRange
        var newRed = currentSettings.redRange
        val safeValue = newValue?.coerceIn(0, 149)

        when(colorForRange) {
            AdjustValuesFragment.ColorForRange.GREEN -> {
                if (isMinField) {
                    newGreen = newGreen.copy(minDb = safeValue)
                } else {
                    newGreen = newGreen.copy(maxDb = safeValue)
                    // Adjust yellow min if needed
                    safeValue?.let {
                        if (newYellow.minDb != null && it >= newYellow.minDb!!) {
                            newYellow = newYellow.copy(minDb = it + 1)
                        }
                    }
                }
            }
            AdjustValuesFragment.ColorForRange.YELLOW -> {
                if (isMinField) {
                    newYellow = newYellow.copy(minDb = safeValue)
                    // Adjust green max if needed
                    safeValue?.let {
                        if (newGreen.maxDb != null && it <= newGreen.maxDb!!) {
                            newGreen = newGreen.copy(maxDb = it - 1)
                        }
                    }
                } else {
                    newYellow = newYellow.copy(maxDb = safeValue)
                    // Adjust red min if needed
                    safeValue?.let {
                        if (newRed.minDb != null && it >= newRed.minDb!!) {
                            newRed = newRed.copy(minDb = it + 1)
                        }
                    }
                }
            }
            AdjustValuesFragment.ColorForRange.RED -> {
                if (isMinField) {
                    newRed = newRed.copy(minDb = safeValue)
                    // Adjust yellow max if needed
                    safeValue?.let {
                        if (newYellow.maxDb != null && it <= newYellow.maxDb!!) {
                            newYellow = newYellow.copy(maxDb = it - 1)
                        }
                    }
                } else {
                    newRed = newRed.copy(maxDb = safeValue)
                }
            }
        }

        return currentSettings.copy(greenRange = newGreen, yellowRange = newYellow, redRange = newRed)
    }

    // Persistence
    fun saveState() {
        saveState(_uiState.value)
    }

    private fun saveState(state: TrafficLightState) {
        val currentInstanceId = state.instanceId
        if (currentInstanceId <= 0) {
            Log.w(TAG, "Attempted to save state with invalid instanceId: $currentInstanceId")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val entity = mapStateToEntity(state)
                trafficLightDao.insertOrUpdate(entity)
                Log.d(TAG, "Saved state for instance $currentInstanceId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save state for instance $currentInstanceId",
                    Log.e(TAG, "Failed to save state for instance $currentInstanceId", e)
            }
        }
    }

    fun saveWindowPosition(x: Int, y: Int) {
        val currentState = _uiState.value
        if (currentState.windowX == x && currentState.windowY == y) return
        _uiState.update { it.copy(windowX = x, windowY = y) }
        saveState()
    }

    fun saveWindowSize(width: Int, height: Int) {
        val currentState = _uiState.value
        if (currentState.windowWidth == width && currentState.windowHeight == height) return
        _uiState.update { it.copy(windowWidth = width, windowHeight = height) }
        saveState()
    }

    fun deleteState() {
        instanceId?.let { id ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    trafficLightDao.deleteById(id)
                    Log.d(TAG, "Deleted state for instance $id")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete state for instance $id", e)
                }
            }
        }
    }

    // Mappers
    private fun mapEntityToState(entity: TrafficLightStateEntity): TrafficLightState {
        val responsiveSettings = try {
            gson.fromJson(entity.responsiveModeSettingsJson, ResponsiveModeSettings::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ResponsiveModeSettings JSON, using default.", e)
            ResponsiveModeSettings()
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
            currentMode = try {
                TrafficLightMode.valueOf(entity.currentMode)
            } catch (e: Exception) {
                TrafficLightMode.MANUAL_CHANGE
            },
            orientation = try {
                Orientation.valueOf(entity.orientation)
            } catch (e: Exception) {
                Orientation.VERTICAL
            },
            isBlinkingEnabled = entity.isBlinkingEnabled,
            activeLight = entity.activeLight?.let {
                try {
                    LightColor.valueOf(it)
                } catch (e: Exception) {
                    null
                }
            },
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
            previousMode = entity.previousMode?.let {
                try {
                    TrafficLightMode.valueOf(it)
                } catch (e: Exception) {
                    null
                }
            },
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
            uuid = UUID.randomUUID().toString(),
            currentMode = state.currentMode.name,
            orientation = state.orientation.name,
            isBlinkingEnabled = state.isBlinkingEnabled,
            activeLight = state.activeLight?.name,
            isSettingsOpen = false, // Don't persist UI state
            isMicrophoneAvailable = state.isMicrophoneAvailable,
            numberOfOpenInstances = state.numberOfOpenInstances,
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

    override fun onCleared() {
        Log.d(TAG, "ViewModel cleared for instanceId: $instanceId")
        stopMicrophoneMonitoring()
        sequenceTimer?.cancel()
        super.onCleared()
    }
}