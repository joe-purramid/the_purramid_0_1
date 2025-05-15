// PurramidDatabase.kt
package com.example.purramid.thepurramid.data.db 

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.purramid.thepurramid.data.db.ClockStateEntity
import com.example.purramid.thepurramid.data.db.CityEntity
import com.example.purramid.thepurramid.data.db.CityDao
import com.example.purramid.thepurramid.data.db.TimeZoneBoundaryEntity
import com.example.purramid.thepurramid.data.db.TimeZoneDao
import com.example.purramid.thepurramid.data.db.RandomizerInstanceEntity
import com.example.purramid.thepurramid.data.db.RandomizerDao
import com.example.purramid.thepurramid.data.db.SpinItemEntity
import com.example.purramid.thepurramid.data.db.SpinListEntity
import com.example.purramid.thepurramid.data.db.SpinSettingsEntity
import com.example.purramid.thepurramid.data.db.ScreenShadeStateEntity
import com.example.purramid.thepurramid.data.db.ScreenShadeDao
import com.example.purramid.thepurramid.data.db.SpotlightStateEntity
import com.example.purramid.thepurramid.data.db.SpotlightDao
import com.example.purramid.thepurramid.data.db.TimerStateEntity
import com.example.purramid.thepurramid.data.db.TimerDao
import com.example.purramid.thepurramid.data.db.TrafficLightStateEntity


/**
 * The Room database for the Purramid application.
 * This contains tables for Clock and Randomizer features.
 */
@Database(
    entities = [
        // List all your entity classes here
        ClockStateEntity::class,
        TimeZoneBoundaryEntity::class,
        CityEntity::class,
        RandomizerInstanceEntity::class,
        ScreenShadeStateEntity::class,
        SpinItemEntity::class,
        SpinListEntity::class,
        SpinSettingsEntity::class,
        SpotlightStateEntity::class,
        TimerStateEntity::class,
        TrafficLightStateEntity::class
    ],
    version = 11, // Updated with Slots randomizer mode
    exportSchema = false // Set to true if you want to export the schema to a file for version control (recommended for production apps)
)
@TypeConverters(Converters::class) // Register the TypeConverters class
abstract class PurramidDatabase : RoomDatabase() {

    /**
     * Abstract function to get the Data Access Objects
     * Room will generate the implementation.
     */
    abstract fun clockDao(): ClockDao
    abstract fun timeZoneDao(): TimeZoneDao
    abstract fun cityDao(): CityDao
    abstract fun randomizerDao(): RandomizerDao
    abstract fun screenShadeDao(): ScreenShadeDao
    abstract fun spotlightDao(): SpotlightDao
    abstract fun timerDao(): TimerDao
    abstract fun trafficLightDao(): TrafficLightDao

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
                // IMPORTANT: Migration needed due to version bump
                .fallbackToDestructiveMigration() // Replace with real migrations later
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}