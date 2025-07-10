// ListEditorViewModel.kt
package com.example.purramid.thepurramid.randomizers.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.purramid.thepurramid.data.db.PurramidDatabase
import com.example.purramid.thepurramid.data.db.RandomizerDao
import com.example.purramid.thepurramid.data.db.SpinListEntity
import com.example.purramid.thepurramid.randomizers.data.RandomizerRepository
import jakarta.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

// Data class to hold list entity and its item count
data class ListWithCount(
    val listEntity: SpinListEntity,
    val itemCount: Int
)

class ListEditorViewModel @Inject constructor(
    private val randomizerRepository: RandomizerRepository
) : ViewModel() {

    // Observe all lists from the DAO
    val allSpinLists: LiveData<List<SpinListEntity>> = randomizerRepository.getAllSpinLists()

    // We might need item counts - calculating this reactively can be complex.
    // For simplicity now, we can fetch counts when needed or pass them around.
    // Let's just provide the delete function for now.

    /** Deletes the specified list and all its associated items from the database. */
    fun deleteList(list: SpinListEntity) {
        viewModelScope.launch(Dispatchers.IO) { // Use IO dispatcher for DB operations
            // Delete items first
            randomizerRepository.deleteItemsForList(list.id)
            // Then delete the list itself
            randomizerRepository.deleteSpinList(list)
        }
    }

    // Example function to get count (can be inefficient if called often)
    suspend fun getItemCountForList(listId: UUID): Int {
       return withContext(Dispatchers.IO) {
           randomizerRepository.getItemsForList(listId).size
       }
    }
}