// RandomizerViewModel.kt
package com.example.purramid.thepurramid.randomizers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.map // For transformations
import androidx.lifecycle.viewModelScope
import com.example.purramid.thepurramid.data.db.DEFAULT_SETTINGS_ID // Import the default ID
import com.example.purramid.thepurramid.data.db.PurramidDatabase
import com.example.purramid.thepurramid.data.db.RandomizerDao
import com.example.purramid.thepurramid.data.db.RandomizerInstanceEntity
import com.example.purramid.thepurramid.data.db.SpinItemEntity
import com.example.purramid.thepurramid.data.db.SpinListEntity
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity
import com.example.purramid.thepurramid.managers.RandomizerInstanceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random // For randomization logic
import kotlinx.coroutines.launch

// Data structure to hold data needed by SpinDialView
data class SpinDialViewData(
    val items: List<SpinItemEntity> = emptyList(), // Items for the current list
    val settings: SpinSettingsEntity? = null       // Settings for this instance
)

@HiltViewModel
class RandomizerViewModel @Inject constructor(
    // application: Application, // Need context for Database
    private val randomizerDao: RandomizerDao,
    private val savedStateHandle: SavedStateHandle // For saving/restoring state
) : ViewModel() {

    // --- COMPANION OBJECT ---
    companion object {
        // Key for storing/retrieving instanceId in SavedStateHandle
        const val KEY_INSTANCE_ID = "instanceId"
    }

    // --- State ---
    // Unique ID for this specific Randomizer window instance.
    internal val instanceId: UUID = savedStateHandle.get<String>(KEY_INSTANCE_ID)?.let {
        UUID.fromString(it)
    } ?: UUID.randomUUID().also { newId ->
        // If newly created (no ID passed), save this instance *and* default settings
        savedStateHandle[KEY_INSTANCE_ID] = newId.toString()
        viewModelScope.launch {
            randomizerDao.saveInstance(RandomizerInstanceEntity(instanceId = newId))
            // Load defaults if available, otherwise create standard defaults
            val defaultSettings = randomizerDao.getDefaultSettings()
            val initialSettings = defaultSettings?.copy(instanceId = newId) // Apply default to this new ID
                ?: SpinSettingsEntity(instanceId = newId) // Standard defaults if no saved default exists
            randomizerDao.saveSettings(initialSettings)
            if (_spinDialData.value == null) { _spinDialData.value = SpinDialViewData() }
            _spinDialData.value = SpinDialViewData(settings = initialSettings) // Update LiveData
        }
    }

    // LiveData for the currently selected list ID (restored from SavedStateHandle)
    private val _currentListId = savedStateHandle.getLiveData<String?>("currentListId")
        .map { it?.let { uuidString -> UUID.fromString(uuidString) } }
        val allSpinLists: LiveData<List<SpinListEntity>> = randomizerDao.getAllSpinLists()

    // LiveData for all available lists (observed from DAO)
    val allSpinLists: LiveData<List<SpinListEntity>> = randomizerDao.getAllSpinLists()

    // LiveData holding the data needed for the SpinDialView
    private val _spinDialData = MutableLiveData<SpinDialViewData>()
    val spinDialData: LiveData<SpinDialViewData> = _spinDialData

    // LiveData for dropdown visibility state
    private val _isDropdownVisible = MutableLiveData<Boolean>(false)
    val isDropdownVisible: LiveData<Boolean> = _isDropdownVisible

    // LiveData for the result of a spin
    private val _spinResult = MutableLiveData<SpinItemEntity?>()
    val spinResult: LiveData<SpinItemEntity?> = _spinResult

    // LiveData for the title of the currently selected list
    val currentListTitle: LiveData<String?> = _currentListId.map { listId ->
        listId?.let { id ->
            allSpinLists.value?.firstOrNull { it.id == id }?.title

    // NEW: LiveData for the correctly sorted list to display in the dropdown
    private val _displayedListOrder = MediatorLiveData<List<SpinListEntity>>()
    val displayedListOrder: LiveData<List<SpinListEntity>> = _displayedListOrder

    val currentListTitle: LiveData<String?> = _currentListId.map { listId ->
        listId?.let { id ->
            allSpinLists.value?.firstOrNull { it.id == id }?.title
        }
    }


    init {
        // Ensure _spinDialData is initialized if not done in instanceId block
        if (_spinDialData.value == null) {
            _spinDialData.value = SpinDialViewData()
        }

        // Load data if ID was present in SavedStateHandle
        if (savedStateHandle.contains(KEY_INSTANCE_ID)) {
            loadDataForInstance(instanceId) // Load data for existing/restored instance
        }

        // Observe the current list ID to load its items when it changes
        _currentListId.observeForever { listId ->
            listId?.let { loadItemsForList(it) }
                ?: run { _spinDialData.value = _spinDialData.value?.copy(items = emptyList()) } // Clear items if no list selected

                // Setup MediatorLiveData to observe dependencies for displayedListOrder
        _displayedListOrder.addSource(allSpinLists) { lists ->
            updateDisplayedListOrder(lists, _currentListId.value)
        }
        _displayedListOrder.addSource(_currentListId) { currentId ->
            updateDisplayedListOrder(allSpinLists.value, currentId)
        }

        // Observer for loading items when current list changes
        _currentListId.observeForever { listId ->
            listId?.let { loadItemsForList(it) }
                ?: run { _spinDialData.value = _spinDialData.value?.copy(items = emptyList()) }
        }
    }

    // Helper function to calculate the displayed list order
    private fun updateDisplayedListOrder(fullList: List<SpinListEntity>?, currentId: UUID?) {
        if (fullList == null) {
            _displayedListOrder.value = emptyList()
            return
        }

        val sortedList = mutableListOf<SpinListEntity>()
        val currentItem = fullList.find { it.id == currentId }

        // Add current item first if it exists
        currentItem?.let { sortedList.add(it) }

        // Add remaining items, sorted alphabetically, excluding the current one if it was found
        sortedList.addAll(
            fullList.filter { it.id != currentId }
                    .sortedBy { it.title }
        )
        _displayedListOrder.value = sortedList
    }

    private fun loadDataForInstance(idToLoad: UUID) {
        viewModelScope.launch {
            // Load settings for this instance
            val settings = randomizerDao.getSettingsForInstance(idToLoad)
            _spinDialData.value = SpinDialViewData(settings = settings) // Assume settings exist if ID was passed

            // Restore currentListId from settings if not already in savedStateHandle
            if (_currentListId.value == null && settings.currentListId != null) {
                savedStateHandle["currentListId"] = settings.currentListId.toString()
            } else {
                // If we have a listId already, load its items
                _currentListId.value?.let { loadItemsForList(it) }
            }
        }
    }

    private fun loadItemsForList(listId: UUID) {
        viewModelScope.launch {
            val items = randomizerDao.getItemsForList(listId)
            // Randomize order for display on dial
            val randomizedItems = items.shuffled()
            _spinDialData.value = _spinDialData.value?.copy(items = randomizedItems)
        }
    }

    // --- UI Event Handlers ---

    /**
     * Handles the user's request to spin the dial.
     * If spin is enabled, signals the Activity to start the animation (by setting result to null).
     * If spin is disabled, calculates the result immediately and updates the state.  */
    fun handleSpinRequest() {
        // Get current settings and items from the live data
        val currentSettings = _spinDialData.value?.settings ?: return // Need settings
        val currentItems = _spinDialData.value?.items ?: return // Need items

        // Ensure there are items to select from
        if (currentItems.isEmpty()) {
            _spinResult.value = null // Can't spin or select if list is empty
            // Optionally: Provide user feedback (e.g., via a separate LiveData event)
            // _toastMessage.value = "Cannot spin an empty list!"
            return
        }

        if (currentSettings.isSpinEnabled) {
            // --- Spin is ENABLED ---
            // Set the result to null. The Activity observes this null value
            // as the signal to trigger the SpinDialView animation.
            _spinResult.value = null
        } else {
            // --- Spin is DISABLED ---
            // Calculate the result immediately.
            val randomIndex = Random.nextInt(currentItems.size)
            val selectedItem = currentItems[randomIndex]

            // Set the result directly. The Activity observes this non-null value.
            _spinResult.value = selectedItem

            // Also update the dial data to immediately reflect the selection
            // (e.g., reorder items so the selected one is at the pointer)
            // Note: This assumes SpinDialView respects the item order for non-animated display.
            _spinDialData.postValue( // Use postValue if called from a background thread, though not strictly needed here
                _spinDialData.value?.copy(
                    items = currentItems.sortedByDescending { it.id == selectedItem.id }
                )
            )
        }
    }

    fun toggleListDropdown() {
        _isDropdownVisible.value = !(_isDropdownVisible.value ?: false)
    }

    fun selectList(listId: UUID) {
        // Update SavedStateHandle which triggers _currentListId LiveData
        savedStateHandle["currentListId"] = listId.toString()
        _isDropdownVisible.value = false // Hide dropdown

        // Persist the selection in settings
        viewModelScope.launch {
            val settings = randomizerDao.getSettingsForInstance(instanceId)
            settings?.let {
                val updatedSettings = it.copy(currentListId = listId)
                // SAVE HERE
                randomizerDao.saveSettings(updatedSettings)
                // Update livedata for settings if needed
                 _spinDialData.value = _spinDialData.value?.copy(settings = updatedSettings)
            }
        }
        // Items will be loaded by the _currentListId observer
    }

    // --- Cleanup ---
    fun handleManualClose() {
        // Check if this is the last instance BEFORE unregistering conceptually
        val isLast = RandomizerInstanceManager.isLastInstance(instanceId)

        if (isLast) {
            viewModelScope.launch {
                val lastSettings = randomizerDao.getSettingsForInstance(instanceId)
                // Save settings as default (use predefined ID)
                lastSettings?.let {
                    val defaultToSave = it.copy(instanceId = DEFAULT_SETTINGS_ID)
                    randomizerDao.saveDefaultSettings(defaultToSave)
                }
                // Now delete the instance-specific records
                randomizerDao.deleteSettingsForInstance(instanceId)
                randomizerDao.deleteInstance(RandomizerInstanceEntity(instanceId = instanceId))
            }
        } else {
            // Not the last instance, just delete its records
            viewModelScope.launch {
                randomizerDao.deleteSettingsForInstance(instanceId)
                randomizerDao.deleteInstance(RandomizerInstanceEntity(instanceId = instanceId))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Unregistration happens in Activity's onDestroy
        // No database deletion here - state persists unless manually closed.
    }

    // --- Functions to be called from Settings ---

    fun updateSettings(newSettings: SpinSettingsEntity) {
        viewModelScope.launch {
             // Ensure the instanceId matches
             val settingsToSave = newSettings.copy(instanceId = instanceId)
            randomizerDao.saveSettings(settingsToSave)
            // Update LiveData to reflect changes immediately
             _spinDialData.value = _spinDialData.value?.copy(settings = settingsToSave)
        }
    }

    // --- List and Item Modification Functions ---
    /** Creates a new list with the given title and saves it to the database. */
    fun addList(title: String) {
        viewModelScope.launch {
            val newList = SpinListEntity(id = UUID.randomUUID(), title = title)
            randomizerDao.insertSpinList(newList)
            // Optionally: Select the newly added list automatically?
            // selectList(newList.id)
        }
    }

    /** Deletes the specified list and all its associated items from the database. */
    fun deleteList(list: SpinListEntity) {
        viewModelScope.launch {
            // Check if this is the currently selected list
            val wasCurrentList = (_currentListId.value == list.id)

            // Delete items first to maintain integrity (though cascade delete could also be set up)
            randomizerDao.deleteItemsForList(list.id)
            randomizerDao.deleteSpinList(list) // Then delete the list itself

            // If the deleted list was the current one, clear the selection
            if (wasCurrentList) {
                savedStateHandle["currentListId"] = null // Clear from SavedStateHandle
                _spinDialData.value = _spinDialData.value?.copy(items = emptyList()) // Clear items in UI data
                // Update settings in DB to remove reference to the deleted list
                randomizerDao.getSettingsForInstance(instanceId)?.let {
                    val updatedSettings = it.copy(currentListId = null)
                    randomizerDao.saveSettings(updatedSettings)
                    _spinDialData.value = _spinDialData.value?.copy(settings = updatedSettings)
                }
            }
            // Note: allSpinLists LiveData will update automatically from the DAO observation
        }
    }

    /** Updates the title of an existing list. */
    fun updateListTitle(listId: UUID, newTitle: String) {
        viewModelScope.launch {
            randomizerDao.getSpinListById(listId)?.let { list ->
                randomizerDao.updateSpinList(list.copy(title = newTitle))
                // allSpinLists LiveData will update automatically
            }
        }
    }

    /** Adds a new item to the specified list. */
    fun addItemToList(listId: UUID, item: SpinItemEntity) {
        // Ensure the item has the correct listId and a unique ID
        val itemToAdd = item.copy(listId = listId, id = item.id ?: UUID.randomUUID())
        viewModelScope.launch {
            randomizerDao.insertSpinItem(itemToAdd)
            // Reload items if this is the current list to show the new item immediately
            if (_currentListId.value == listId) {
                loadItemsForList(listId)
            }
        }
    }

    /** Updates an existing item. */
    fun updateItem(item: SpinItemEntity) {
        viewModelScope.launch {
            randomizerDao.updateSpinItem(item)
            // Reload items if this item belongs to the current list
            if (_currentListId.value == item.listId) {
                loadItemsForList(item.listId)
            }
        }
    }

    /** Deletes an existing item. */
    fun deleteItem(item: SpinItemEntity) {
        viewModelScope.launch {
            randomizerDao.deleteItem(item)
            // Reload items if this item belongs to the current list
            if (_currentListId.value == item.listId) {
                loadItemsForList(item.listId)
            }
        }
    }
}