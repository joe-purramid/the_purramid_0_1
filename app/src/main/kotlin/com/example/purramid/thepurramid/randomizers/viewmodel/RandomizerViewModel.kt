// RandomizerViewModel.kt
package com.example.purramid.thepurramid.randomizers.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.purramid.thepurramid.data.db.RandomizerInstanceEntity
import com.example.purramid.thepurramid.data.db.SpinItemEntity
import com.example.purramid.thepurramid.data.db.SpinListEntity
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity
import com.example.purramid.thepurramid.randomizers.data.RandomizerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.launch

// Data structure to hold data needed by SpinDialView (can stay here or move to own file)
data class SpinDialViewData(
    val items: List<SpinItemEntity> = emptyList(),
    val settings: SpinSettingsEntity? = null
)

@HiltViewModel
class RandomizerViewModel @Inject constructor(
    private val randomizerRepository: RandomizerRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        const val KEY_INSTANCE_ID = "instanceId"
        private const val TAG = "RandomizerViewModel"
    }

    // --- State ---
    internal val instanceId: Int = savedStateHandle.get<Int>(KEY_INSTANCE_ID) ?: 0

    private val _currentListId = savedStateHandle.getLiveData<String?>("currentListId")
        .map { it?.let { uuidString -> UUID.fromString(uuidString) } }

    val allSpinLists: LiveData<List<SpinListEntity>> = randomizerRepository.getAllSpinLists()

    private val _spinDialData = MutableLiveData<SpinDialViewData>()
    val spinDialData: LiveData<SpinDialViewData> = _spinDialData

    private val _isDropdownVisible = MutableLiveData<Boolean>(false)
    val isDropdownVisible: LiveData<Boolean> = _isDropdownVisible

    private val _spinResult = MutableLiveData<SpinItemEntity?>()
    val spinResult: LiveData<SpinItemEntity?> = _spinResult

    private val _displayedListOrder = MediatorLiveData<List<SpinListEntity>>()
    val displayedListOrder: LiveData<List<SpinListEntity>> = _displayedListOrder

    // Moved Sequence LiveData definitions here (top level)
    private val _sequenceList = MutableLiveData<List<SpinItemEntity>?>()
    val sequenceList: LiveData<List<SpinItemEntity>?> = _sequenceList
    private val _sequenceIndex = MutableLiveData<Int>(0)
    val sequenceIndex: LiveData<Int> = _sequenceIndex

    // Correct placement for currentListTitle
    val currentListTitle: LiveData<String?> = _currentListId.map { listId ->
        listId?.let { id ->
            allSpinLists.value?.firstOrNull { it.id == id }?.title
        }
    }

    init {
        if (_spinDialData.value == null) {
            _spinDialData.value = SpinDialViewData()
        }

        if (instanceId > 0) {
            loadDataForInstance(instanceId)
        } else {
            Log.e(TAG, "Invalid instanceId: $instanceId")
        }

        // Keep only one set of these observers
        _displayedListOrder.addSource(allSpinLists) { lists ->
            updateDisplayedListOrder(lists, _currentListId.value)
        }
        _displayedListOrder.addSource(_currentListId) { currentId ->
            updateDisplayedListOrder(allSpinLists.value, currentId)
        }

        _currentListId.observeForever { listId ->
            if (listId != null) {
                loadItemsForList(listId)
                clearSequence()
            } else {
                _spinDialData.value = _spinDialData.value?.copy(items = emptyList())
                clearSequence()
            }
        }
    }

    private fun updateDisplayedListOrder(fullList: List<SpinListEntity>?, currentId: UUID?) {
        if (fullList == null) {
            _displayedListOrder.value = emptyList()
            return
        }
        val sortedList = mutableListOf<SpinListEntity>()
        val currentItem = fullList.find { it.id == currentId }
        currentItem?.let { sortedList.add(it) }
        sortedList.addAll(
            fullList.filter { it.id != currentId }
                .sortedBy { it.title }
        )
        _displayedListOrder.value = sortedList
    }

    private fun loadDataForInstance(idToLoad: Int) {
        viewModelScope.launch {
            val settings = randomizerRepository.getSettingsForInstance(idToLoad)
            _spinDialData.value = _spinDialData.value?.copy(settings = settings)
            if (_currentListId.value == null && settings?.currentListId != null) {
                savedStateHandle["currentListId"] = settings.currentListId.toString()
            } else {
                _currentListId.value?.let { loadItemsForList(it) }
            }
        }
    }

    private fun loadItemsForList(listId: UUID) {
        viewModelScope.launch {
            val items = randomizerRepository.getItemsForList(listId)
            val randomizedItems = items.shuffled()
            _spinDialData.value = _spinDialData.value?.copy(items = randomizedItems)
        }
    }

    // --- UI Event Handlers ---
    fun handleSpinRequest() {
        val currentSettings = _spinDialData.value?.settings ?: return
        val currentItems = _spinDialData.value?.items ?: return
        if (currentItems.isEmpty()) {
            _spinResult.value = null
            return
        }
        if (currentSettings.isSequenceEnabled) {
            clearSequence()
        }
        if (currentSettings.isSpinEnabled) {
            _spinResult.value = null
        } else {
            val randomIndex = Random.nextInt(currentItems.size)
            val selectedItem = currentItems[randomIndex]
            _spinResult.value = selectedItem
            if (currentSettings.isSequenceEnabled) {
                generateSequence(selectedItem)
            } else {
                _spinDialData.postValue(
                    _spinDialData.value?.copy(
                        items = currentItems.sortedByDescending { it.id == selectedItem.id }
                    )
                )
            }
        }
    }

    fun setSpinResult(result: SpinItemEntity?) {
        _spinResult.value = result
        val currentSettings = _spinDialData.value?.settings
        if (result != null && currentSettings?.isSequenceEnabled == true) {
            generateSequence(result)
        }
    }

    fun clearSpinResult() {
        _spinResult.value = null
    }

    fun toggleListDropdown() {
        _isDropdownVisible.value = !(_isDropdownVisible.value ?: false)
    }

    fun selectList(listId: UUID) {
        clearSequence()
        savedStateHandle["currentListId"] = listId.toString()
        _isDropdownVisible.value = false
        viewModelScope.launch {
            val settings = randomizerRepository.getSettingsForInstance(instanceId)
            settings?.let {
                val updatedSettings = it.copy(currentListId = listId)
                randomizerRepository.saveSettings(updatedSettings)
                _spinDialData.value = _spinDialData.value?.copy(settings = updatedSettings)
            }
        }
    }

    // --- Sequence Logic Functions ---
    private fun generateSequence(firstItem: SpinItemEntity) {
        viewModelScope.launch {
            val listId = firstItem.listId
            val allItemsForList = randomizerRepository.getItemsForList(listId)
            if (allItemsForList.size <= 1) {
                _sequenceList.value = allItemsForList
                _sequenceIndex.value = 0
                return@launch
            }
            val remainingItems = allItemsForList.toMutableList()
            remainingItems.remove(firstItem)
            remainingItems.shuffle()
            val finalSequence = listOf(firstItem) + remainingItems
            _sequenceList.value = finalSequence
            _sequenceIndex.value = 0
        }
    }

    fun showNextSequenceItem() {
        val currentList = _sequenceList.value ?: return
        val currentIndex = _sequenceIndex.value ?: 0
        if (currentIndex < currentList.size - 1) {
            _sequenceIndex.value = currentIndex + 1
        }
    }

    fun showPreviousSequenceItem() {
        val currentIndex = _sequenceIndex.value ?: 0
        if (currentIndex > 0) {
            _sequenceIndex.value = currentIndex - 1
        }
    }

    fun clearSequence() {
        _sequenceList.value = null
        _sequenceIndex.value = 0
    }

    /**
     * Called when the user manually closes this randomizer instance.
     * Deletes the instance-specific settings and its registration from the database.
     */
    fun handleManualClose() {
        if (instanceId > 0) {
            Log.d(TAG, "handleManualClose called for instanceId: $instanceId")
            viewModelScope.launch {
                try {
                    randomizerRepository.deleteSettingsForInstance(instanceId)
                    randomizerRepository.deleteInstance(instanceId)
                    Log.d(TAG, "Successfully deleted settings and instance record for $instanceId from DB.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting data for instance $instanceId from DB", e)
                }
            }
        } else {
            Log.w(TAG, "handleManualClose called but instanceId is invalid.")
        }
    }

    override fun onCleared() {
        super.onCleared()
        // No database deletion here
    }

    // --- Settings Update ---
    fun updateSettings(newSettings: SpinSettingsEntity) {
        viewModelScope.launch {
            // Corrected logic for applying sequence check
            var settingsToModify = newSettings.copy(instanceId = instanceId)
            if (settingsToModify.isSequenceEnabled) {
                settingsToModify = settingsToModify.copy(
                    isAnnounceEnabled = false,
                    isCelebrateEnabled = false
                )
            }
            else if (!settingsToModify.isAnnounceEnabled) {
                settingsToModify = settingsToModify.copy(
                    isCelebrateEnabled = false
                )
            }

            // Save the potentially modified settings
            randomizerRepository.saveSettings(settingsToModify)
            // Update LiveData
            if (_spinDialData.value == null) { _spinDialData.value = SpinDialViewData() }
            _spinDialData.value = _spinDialData.value?.copy(settings = settingsToModify)
        }
    }

    // --- List and Item Modification Functions ---
    fun addList(title: String) {
        viewModelScope.launch {
            val newList = SpinListEntity(id = UUID.randomUUID(), title = title)
            randomizerRepository.insertSpinList(newList)
        }
    }

    fun deleteList(list: SpinListEntity) {
        viewModelScope.launch {
            val wasCurrentList = (_currentListId.value == list.id) // Check before deleting
            randomizerRepository.deleteSpinList(list) // Repository handles deleting items first
            if (wasCurrentList) {
                // Update SavedStateHandle
                savedStateHandle["currentListId"] = null
                // Also clear settings in DB
                randomizerRepository.getSettingsForInstance(instanceId)?.let {
                    randomizerRepository.saveSettings(it.copy(currentListId = null))
                    // No need to update _spinDialData here, _currentListId observer handles it
                }
            }
        }
    }

    fun updateListTitle(listId: UUID, newTitle: String) {
        viewModelScope.launch {
            randomizerRepository.getSpinListById(listId)?.let { list ->
                randomizerRepository.updateSpinList(list.copy(title = newTitle))
            }
        }
    }

    fun addItemToList(listId: UUID, item: SpinItemEntity) {
        // Assign new ID on add
        val itemToAdd = item.copy(listId = listId, id = UUID.randomUUID())
        viewModelScope.launch {
            randomizerRepository.insertSpinItem(itemToAdd)
            if (_currentListId.value == listId) {
                // Reload items which triggers observer update
                loadItemsForList(listId)
            }
        }
    }

    fun updateItem(item: SpinItemEntity) {
        viewModelScope.launch {
            randomizerRepository.updateSpinItem(item)
            if (_currentListId.value == item.listId) {
                loadItemsForList(item.listId)
            }
        }
    }

    fun deleteItem(item: SpinItemEntity) {
        viewModelScope.launch {
            randomizerRepository.deleteSpinItem(item)
            if (_currentListId.value == item.listId) {
                loadItemsForList(item.listId)
            }
        }
    }
}