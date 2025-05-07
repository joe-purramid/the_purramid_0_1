// PurramidDatabase.kt
package com.example.purramid.thepurramid.data.db 

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.purramid.thepurramid.data.db.CityEntity
import com.example.purramid.thepurramid.data.db.CityDao
import com.example.purramid.thepurramid.data.db.SpotlightStateEntity
import com.example.purramid.thepurramid.data.db.TimeZoneBoundaryEntity
import com.example.purramid.thepurramid.data.db.TimeZoneDao


/**
 * The Room database for the Purramid application.
 * This contains tables for Clock and Randomizer features.
 */
@Database(
    entities = [
        // List all your entity classes here
        RandomizerInstanceEntity::class,
        SpinItemEntity::class,
        SpinListEntity::class,
        SpinSettingsEntity::class,
        TimeZoneBoundaryEntity::class,
        CityEntity::class
        SpotlightStateEntity::class
    ],
    version = 6, // Updated with Slots randomizer mode
    exportSchema = false // Set to true if you want to export the schema to a file for version control (recommended for production apps)
)
@TypeConverters(Converters::class) // Register the TypeConverters class
abstract class PurramidDatabase : RoomDatabase() {

    /**
     * Abstract function to get the Data Access Objects
     * Room will generate the implementation.
     */
    abstract fun randomizerDao(): RandomizerDao
    abstract fun timeZoneDao(): TimeZoneDao
    abstract fun cityDao(): CityDao
    abstract fun spotlightDao(): SpotlightDao

    companion object {
        // Singleton prevents multiple instances of the database opening at once
        // Using @Volatile ensures that writes to this field are immediately made visible to other threads.
        @Volatile
        private var INSTANCE: PurramidDatabase? = null

        fun getDatabase(context: Context): PurramidDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PurramidDatabase::class.java,
                    "purramid_database"
                )
                // IMPORTANT: need a migration strategy.
                // .fallbackToDestructiveMigration()
                // For production, implement proper migrations: .addMigrations(MIGRATION_1_2, ...)
                .fallbackToDestructiveMigration() // Replace with real migrations later
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}