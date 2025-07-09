// RandomizerRepositoryImpl.kt
package com.example.purramid.thepurramid.randomizers.data

import android.content.Context
import androidx.lifecycle.LiveData
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.data.db.RandomizerDao
import com.example.purramid.thepurramid.data.db.RandomizerInstanceEntity
import com.example.purramid.thepurramid.data.db.SpinItemEntity
import com.example.purramid.thepurramid.data.db.SpinListEntity
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity
import com.example.purramid.thepurramid.randomizers.SpinItemType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of RandomizerRepository.
 * Handles all data operations for the Randomizer feature.
 */
@Singleton
class RandomizerRepositoryImpl @Inject constructor(
    private val randomizerDao: RandomizerDao,
    @ApplicationContext private val context: Context
) : RandomizerRepository {

    // --- List Operations ---
    override fun getAllSpinLists(): LiveData<List<SpinListEntity>> {
        return randomizerDao.getAllSpinLists()
    }

    override suspend fun getSpinListById(listId: UUID): SpinListEntity? {
        return withContext(Dispatchers.IO) {
            randomizerDao.getSpinListById(listId)
        }
    }

    override suspend fun insertSpinList(list: SpinListEntity) {
        withContext(Dispatchers.IO) {
            randomizerDao.insertSpinList(list)
        }
    }

    override suspend fun updateSpinList(list: SpinListEntity) {
        withContext(Dispatchers.IO) {
            randomizerDao.updateSpinList(list)
        }
    }

    override suspend fun deleteSpinList(list: SpinListEntity) {
        withContext(Dispatchers.IO) {
            // Delete items first, then the list
            randomizerDao.deleteItemsForList(list.id)
            randomizerDao.deleteSpinList(list)
        }
    }

    override suspend fun getListCount(): Int {
        return withContext(Dispatchers.IO) {
            randomizerDao.getListCount()
        }
    }

    // --- Item Operations ---
    override suspend fun getItemsForList(listId: UUID): List<SpinItemEntity> {
        return withContext(Dispatchers.IO) {
            randomizerDao.getItemsForList(listId)
        }
    }

    override suspend fun insertSpinItem(item: SpinItemEntity) {
        withContext(Dispatchers.IO) {
            randomizerDao.insertSpinItem(item)
        }
    }

    override suspend fun insertSpinItems(items: List<SpinItemEntity>) {
        withContext(Dispatchers.IO) {
            randomizerDao.insertSpinItems(items)
        }
    }

    override suspend fun updateSpinItem(item: SpinItemEntity) {
        withContext(Dispatchers.IO) {
            randomizerDao.updateSpinItem(item)
        }
    }

    override suspend fun deleteSpinItem(item: SpinItemEntity) {
        withContext(Dispatchers.IO) {
            randomizerDao.deleteSpinItem(item)
        }
    }

    override suspend fun deleteItemsForList(listId: UUID) {
        withContext(Dispatchers.IO) {
            randomizerDao.deleteItemsForList(listId)
        }
    }

    // --- Settings Operations ---
    override suspend fun getSettingsForInstance(instanceId: Int): SpinSettingsEntity? {
        return withContext(Dispatchers.IO) {
            // Convert Int instanceId to UUID for now - this will be fixed when we update the DAO
            randomizerDao.getSettingsForInstance(instanceId)
        }
    }

    override suspend fun saveSettings(settings: SpinSettingsEntity) {
        withContext(Dispatchers.IO) {
            randomizerDao.saveSettings(settings)
        }
    }

    override suspend fun deleteSettingsForInstance(instanceId: Int) {
        withContext(Dispatchers.IO) {
            // Convert Int instanceId to UUID for now
            randomizerDao.deleteSettingsForInstance(instanceId)
        }
    }

    override suspend fun getDefaultSettings(): SpinSettingsEntity? {
        return withContext(Dispatchers.IO) {
            randomizerDao.getDefaultSettings()
        }
    }

    override suspend fun saveDefaultSettings(settings: SpinSettingsEntity) {
        withContext(Dispatchers.IO) {
            // Ensure the settings use the default ID
            val defaultSettings = settings.copy(instanceId = 0) // Will need to update this
            randomizerDao.saveSettings(defaultSettings)
        }
    }

    // --- Instance Operations ---
    override suspend fun getInstanceById(instanceId: Int): RandomizerInstanceEntity? {
        return withContext(Dispatchers.IO) {
            randomizerDao.getByInstanceId(instanceId)
        }
    }

    override suspend fun saveInstance(instance: RandomizerInstanceEntity) {
        withContext(Dispatchers.IO) {
            randomizerDao.saveInstance(instance)
        }
    }

    override suspend fun deleteInstance(instanceId: Int) {
        withContext(Dispatchers.IO) {
            randomizerDao.deleteByInstanceId(instanceId)
        }
    }

    override suspend fun getAllActiveInstances(): List<RandomizerInstanceEntity> {
        return withContext(Dispatchers.IO) {
            randomizerDao.getAllStates()
        }
    }

    override suspend fun getActiveInstanceCount(): Int {
        return withContext(Dispatchers.IO) {
            randomizerDao.getActiveInstanceCount()
        }
    }

    // --- Initialization ---
    override suspend fun initializePreloadedLists() {
        withContext(Dispatchers.IO) {
            // Check if preloaded lists already exist
            val existingLists = randomizerDao.getAllSpinListsNonLiveData() ?: emptyList()
            val existingTitles = existingLists.map { it.title }.toSet()

            // Create preloaded lists if they don't exist
            createPreloadedListIfNotExists(
                title = context.getString(R.string.default_list_title_colors),
                existingTitles = existingTitles,
                items = context.resources.getStringArray(R.array.default_list_items_colors).toList()
            )

            createPreloadedListIfNotExists(
                title = context.getString(R.string.default_list_title_consonants),
                existingTitles = existingTitles,
                items = listOf("B", "C", "D", "F", "G", "H", "J", "K", "L", "M", 
                              "N", "P", "Q", "R", "S", "T", "V", "W", "X", "Y*", "Z")
            )

            createPreloadedListIfNotExists(
                title = context.getString(R.string.default_list_title_continents),
                existingTitles = existingTitles,
                items = context.resources.getStringArray(R.array.default_list_items_continents).toList()
            )

            createPreloadedListIfNotExists(
                title = context.getString(R.string.default_list_title_numbers),
                existingTitles = existingTitles,
                items = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
            )

            createPreloadedListIfNotExists(
                title = context.getString(R.string.default_list_title_oceans),
                existingTitles = existingTitles,
                items = context.resources.getStringArray(R.array.default_list_items_oceans).toList()
            )

            createPreloadedListIfNotExists(
                title = context.getString(R.string.default_list_title_vowels),
                existingTitles = existingTitles,
                items = listOf("A", "E", "I", "O", "U", "Y*")
            )
        }
    }

    private suspend fun createPreloadedListIfNotExists(
        title: String,
        existingTitles: Set<String>,
        items: List<String>
    ) {
        if (title !in existingTitles) {
            val listId = UUID.randomUUID()
            val spinList = SpinListEntity(
                id = listId,
                title = title
            )
            
            randomizerDao.insertSpinList(spinList)
            
            val spinItems = items.mapIndexed { index, content ->
                SpinItemEntity(
                    id = UUID.randomUUID(),
                    listId = listId,
                    itemType = SpinItemType.TEXT,
                    content = content,
                    backgroundColor = null,
                    emojiList = emptyList()
                )
            }
            
            randomizerDao.insertSpinItems(spinItems)
        }
    }
}