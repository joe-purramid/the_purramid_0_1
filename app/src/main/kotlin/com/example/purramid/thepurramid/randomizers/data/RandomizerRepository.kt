// RandomizerRepository.kt
package com.example.purramid.thepurramid.randomizers.data

import androidx.lifecycle.LiveData
import com.example.purramid.thepurramid.data.db.RandomizerInstanceEntity
import com.example.purramid.thepurramid.data.db.SpinItemEntity
import com.example.purramid.thepurramid.data.db.SpinListEntity
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity
import java.util.UUID

/**
 * Repository interface for Randomizer data operations.
 * Provides a clean API for data access, abstracting the underlying data sources.
 */
interface RandomizerRepository {
    
    // --- List Operations ---
    fun getAllSpinLists(): LiveData<List<SpinListEntity>>
    suspend fun getSpinListById(listId: UUID): SpinListEntity?
    suspend fun insertSpinList(list: SpinListEntity)
    suspend fun updateSpinList(list: SpinListEntity)
    suspend fun deleteSpinList(list: SpinListEntity)
    suspend fun getListCount(): Int
    
    // --- Item Operations ---
    suspend fun getItemsForList(listId: UUID): List<SpinItemEntity>
    suspend fun insertSpinItem(item: SpinItemEntity)
    suspend fun insertSpinItems(items: List<SpinItemEntity>)
    suspend fun updateSpinItem(item: SpinItemEntity)
    suspend fun deleteSpinItem(item: SpinItemEntity)
    suspend fun deleteItemsForList(listId: UUID)
    
    // --- Settings Operations ---
    suspend fun getSettingsForInstance(instanceId: Int): SpinSettingsEntity?
    suspend fun saveSettings(settings: SpinSettingsEntity)
    suspend fun deleteSettingsForInstance(instanceId: Int)
    suspend fun getDefaultSettings(): SpinSettingsEntity?
    suspend fun saveDefaultSettings(settings: SpinSettingsEntity)
    
    // --- Instance Operations ---
    suspend fun getInstanceById(instanceId: Int): RandomizerInstanceEntity?
    suspend fun saveInstance(instance: RandomizerInstanceEntity)
    suspend fun deleteInstance(instanceId: Int)
    suspend fun getAllActiveInstances(): List<RandomizerInstanceEntity>
    suspend fun getActiveInstanceCount(): Int
    
    // --- Initialization ---
    suspend fun initializePreloadedLists()
}