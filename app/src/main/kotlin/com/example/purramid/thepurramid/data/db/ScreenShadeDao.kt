// ScreenShadeDao.kt
package com.example.purramid.thepurramid.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ScreenShadeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(state: ScreenShadeStateEntity)

    @Query("SELECT * FROM screen_shade_state WHERE instanceId = :id")
    suspend fun getById(id: Int): ScreenShadeStateEntity?

    @Query("SELECT * FROM screen_shade_state")
    suspend fun getAllStates(): List<ScreenShadeStateEntity>

    @Query("DELETE FROM screen_shade_state WHERE instanceId = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT COUNT(*) FROM screen_shade_state")
    suspend fun getCount(): Int

    @Query("SELECT MAX(instanceId) FROM screen_shade_state") // To help with new ID generation
    suspend fun getMaxInstanceId(): Int?

    @Query("DELETE FROM screen_shade_state")
    suspend fun clearAll()
}