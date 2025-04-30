package com.example.purramid.thepurramid.randomizers.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purramid.thepurramid.data.db.RandomizerDao
import com.example.purramid.thepurramid.data.db.SpinItemEntity
import com.example.purramid.thepurramid.data.db.SpinListEntity
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity
import com.example.purramid.thepurramid.randomizers.SlotsColumnState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

// Data class to hold result for announcement
data class SlotsResult(val results: List<Pair<SlotsColumnState, SpinItemEntity?>>)

@HiltViewModel
class SlotsViewModel @Inject constructor(
    private val randomizerDao: RandomizerDao,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        // Key to get instanceId from SavedStateHandle (passed via NavArgs/Intent)
        // Ensure this matches the key used in navigation/intent passing
        const val KEY_INSTANCE_ID = "instanceId" // Or use RandomizerSettingsViewModel.KEY_INSTANCE_ID
        const val DEFAULT_NUM_COLUMNS = 3
    }

    private val instanceId: UUID? = savedStateHandle.get<String>(KEY_INSTANCE_ID)?.let {
        try { UUID.fromString(it) } catch (e: IllegalArgumentException) { null }
    }

    // Overall settings for this instance
    private val _settings = MutableLiveData<SpinSettingsEntity?>()
    val settings: LiveData<SpinSettingsEntity?> = _settings

    // State for each individual column
    private val _columnStates = MutableLiveData<List<SlotsColumnState>>(emptyList())
    val columnStates: LiveData<List<SlotsColumnState>> = _columnStates

    // LiveData for available lists (used for selection dropdowns)
    val allSpinLists: LiveData<List<SpinListEntity>> = randomizerDao.getAllSpinLists()

    // Signals when spinning animation is active for a column (Map<columnIndex, Boolean>)
    private val _isSpinning = MutableLiveData<Map<Int, Boolean>>(emptyMap())
    val isSpinning: LiveData<Map<Int, Boolean>> = _isSpinning

    // Holds the final result after spinning to potentially trigger announcement
    private val _spinResult = MutableLiveData<SlotsResult?>()
    val spinResult: LiveData<SlotsResult?> = _spinResult

    // Error messages
    private val _errorEvent = MutableLiveData<String?>()
    val errorEvent: LiveData<String?> = _errorEvent

    init {
        if (instanceId != null) {
            loadInitialState(instanceId)
        } else {
            _errorEvent.postValue("SlotsViewModel: Missing or invalid Instance ID.")
        }
    }

    private fun loadInitialState(id: UUID) {
        viewModelScope.launch(Dispatchers.IO) {
            val loadedSettings = randomizerDao.getSettingsForInstance(id)
            withContext(Dispatchers.Main) {
                if (loadedSettings != null) {
                    _settings.value = loadedSettings
                    // TODO: Extract slotsColumnStates from loadedSettings once added to SpinSettingsEntity
                    // val savedColumnStates = loadedSettings.slotsColumnStates
                    val savedColumnStates: List<SlotsColumnState> = emptyList() // Placeholder

                    // TODO: Get numSlotsColumns from loadedSettings once added
                    // val numColumns = loadedSettings.numSlotsColumns
                    val numColumns = DEFAULT_NUM_COLUMNS // Placeholder

                    // Initialize column states if needed (e.g., mismatch count or empty)
                    val initialStates = initializeColumnStates(savedColumnStates, numColumns)
                    _columnStates.value = initialStates

                } else {
                    _errorEvent.value = "Settings not found for instance $id."
                    // Initialize defaults?
                     _settings.value = SpinSettingsEntity(instanceId = id) // Basic default
                     _columnStates.value = initializeColumnStates(emptyList(), DEFAULT_NUM_COLUMNS)
                }
            }
        }
    }

    // Helper to ensure correct number of column states exist
    private fun initializeColumnStates(currentStates: List<SlotsColumnState>?, targetCount: Int): List<SlotsColumnState> {
        val initialStates = mutableListOf<SlotsColumnState>()
        for (i in 0 until targetCount) {
            val existing = currentStates?.firstOrNull { it.columnIndex == i }
            initialStates.add(existing ?: SlotsColumnState(columnIndex = i))
        }
        return initialStates.take(targetCount) // Ensure exact count
    }

    // --- Actions ---

    fun selectListForColumn(columnIndex: Int, listId: UUID?) {
        val currentStates = _columnStates.value?.toMutableList() ?: return
        val currentState = currentStates.firstOrNull { it.columnIndex == columnIndex } ?: return

        if (currentState.selectedListId != listId) {
            val newState = currentState.copy(selectedListId = listId, currentItemId = null) // Reset item on list change
            val index = currentStates.indexOfFirst { it.columnIndex == columnIndex }
            if (index != -1) {
                currentStates[index] = newState
                _columnStates.value = currentStates // Update LiveData
                saveCurrentState() // Persist change
            }
        }
    }

    fun toggleLockForColumn(columnIndex: Int) {
        val currentStates = _columnStates.value?.toMutableList() ?: return
        val currentState = currentStates.firstOrNull { it.columnIndex == columnIndex } ?: return

        val newState = currentState.copy(isLocked = !currentState.isLocked)
        val index = currentStates.indexOfFirst { it.columnIndex == columnIndex }
        if (index != -1) {
            currentStates[index] = newState
            _columnStates.value = currentStates // Update LiveData
            saveCurrentState() // Persist change
        }
    }

    fun spinAllUnlocked() {
        val columnsToSpin = _columnStates.value?.filter { !it.isLocked && it.selectedListId != null } ?: emptyList()
        if (columnsToSpin.isEmpty()) return // Nothing to spin

        // Reset previous result
        _spinResult.value = null

        // Set spinning state for relevant columns
        val spinningMap = _isSpinning.value?.toMutableMap() ?: mutableMapOf()
        columnsToSpin.forEach { spinningMap[it.columnIndex] = true }
        _isSpinning.value = spinningMap

        viewModelScope.launch {
            // Simulate spin duration (adjust as needed, same as Spin mode?)
            val spinDuration = 2000L
            delay(spinDuration) // Simulate the time it takes to spin

            // Determine results
            determineResults(columnsToSpin)
        }
    }

    private suspend fun determineResults(columnsSpun: List<SlotsColumnState>) {
        val finalResultsMap = mutableMapOf<Int, UUID?>()

        withContext(Dispatchers.IO) { // Perform DB fetches off main thread
            columnsSpun.forEach { column ->
                val items = column.selectedListId?.let { randomizerDao.getItemsForList(it) } ?: emptyList()
                if (items.isNotEmpty()) {
                    val randomIndex = Random.nextInt(items.size)
                    finalResultsMap[column.columnIndex] = items[randomIndex].id
                } else {
                    finalResultsMap[column.columnIndex] = null // No result if list is empty
                }
            }
        }

        // Update state on main thread
        withContext(Dispatchers.Main) {
            val currentStates = _columnStates.value?.toMutableList() ?: mutableListOf()
            val finalColumnStates = mutableListOf<SlotsColumnState>()
            val resultForAnnouncement = mutableListOf<Pair<SlotsColumnState, SpinItemEntity?>>()

            currentStates.forEach { existingState ->
                val newItemId = finalResultsMap[existingState.columnIndex] // Get new ID if it spun
                val finalState = if (newItemId != null && !existingState.isLocked) { // Update only if spun & has result
                    existingState.copy(currentItemId = newItemId)
                } else {
                    existingState // Keep existing state (locked or didn't spin/empty list)
                }
                finalColumnStates.add(finalState)

                // Prepare result for announcement (include locked columns too)
                val itemEntity = finalState.currentItemId?.let { findItemInList(it, finalState.selectedListId) }
                resultForAnnouncement.add(Pair(finalState, itemEntity))
            }

             _columnStates.value = finalColumnStates // Update state with final item IDs

            // Clear spinning state
            val spinningMap = _isSpinning.value?.toMutableMap() ?: mutableMapOf()
            columnsSpun.forEach { spinningMap[it.columnIndex] = false }
            _isSpinning.value = spinningMap

             // Set final result for announcement logic
             _spinResult.value = SlotsResult(resultForAnnouncement)

            // Persist the final state
            saveCurrentState()
        }
    }

     // Helper function to find item details (needs efficient implementation)
     // Ideally, we'd fetch all needed items together in determineResults
     private suspend fun findItemInList(itemId: UUID, listId: UUID?): SpinItemEntity? {
         return listId?.let {
             // TODO: This is inefficient if called multiple times. Consider batch fetching.
              randomizerDao.getItemsForList(it).firstOrNull { item -> item.id == itemId }
         }
     }


    fun clearSpinResult() {
        _spinResult.value = null
    }

    fun clearErrorEvent() {
        _errorEvent.value = null
    }

    // --- Persistence ---

    private fun saveCurrentState() {
        if (instanceId == null) return
        val currentSettings = _settings.value ?: return
        val currentColumns = _columnStates.value ?: return

        // TODO: Update the settings object with the current column states
        // val settingsToSave = currentSettings.copy(slotsColumnStates = currentColumns)
        val settingsToSave = currentSettings // Placeholder - needs modification

        // Persist to database
        viewModelScope.launch(Dispatchers.IO) {
            // TODO: Ensure the type converter for List<SlotsColumnState> is registered
            randomizerDao.saveSettings(settingsToSave)
        }
    }
}