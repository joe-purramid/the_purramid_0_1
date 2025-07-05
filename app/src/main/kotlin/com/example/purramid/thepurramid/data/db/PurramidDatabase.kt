// PurramidDatabase.kt
package com.example.purramid.thepurramid.data.db 

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import java.util.UUID


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
        TimerStateEntity::class,
        RandomizerInstanceEntity::class,
        ScreenMaskStateEntity::class,
        SpinItemEntity::class,
        SpinListEntity::class,
        SpinSettingsEntity::class,
        SpotlightInstanceEntity::class,
        SpotlightOpeningEntity::class,
        TimerStateEntity::class,
        TrafficLightStateEntity::class
    ],
    version = 16, // Updated windowState for RandomizersInstanceEntity
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
    abstract fun screenMaskDao(): ScreenMaskDao
    abstract fun spotlightDao(): SpotlightDao
    abstract fun timerDao(): TimerDao
    abstract fun trafficLightDao(): TrafficLightDao

    companion object {
        @Volatile
        private var INSTANCE: PurramidDatabase? = null

        // Migration from 13 to 14: Add UUID to screen_mask_state
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE screen_mask_state ADD COLUMN uuid TEXT NOT NULL DEFAULT '${UUID.randomUUID()}'")
                database.execSQL("ALTER TABLE timer_state ADD COLUMN isNested INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE timer_state ADD COLUMN nestedX INTEGER NOT NULL DEFAULT -1")
                database.execSQL("ALTER TABLE timer_state ADD COLUMN nestedY INTEGER NOT NULL DEFAULT -1")
                database.execSQL("ALTER TABLE timer_state ADD COLUMN soundsEnabled INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE timer_state ADD COLUMN selectedSoundUri TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE timer_state ADD COLUMN musicUrl TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE timer_state ADD COLUMN recentMusicUrlsJson TEXT NOT NULL DEFAULT '[]'")
                database.execSQL("ALTER TABLE timer_state ADD COLUMN showLapTimes INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Rename clockId to instanceId
                database.execSQL("ALTER TABLE clock_state RENAME COLUMN clockId TO instanceId")
                database.execSQL("ALTER TABLE clock_alarms RENAME COLUMN clockId TO instanceId")
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add windowState to randomizer_instances
                database.execSQL(
                    "ALTER TABLE randomizer_instances ADD COLUMN windowState TEXT NOT NULL DEFAULT 'normal'"
                )
            }
        }

        // Future migrations would be added here

        fun getDatabase(context: Context): PurramidDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PurramidDatabase::class.java,
                    "purramid_database"
                )
                    .addMigrations(
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16
                        // Add future migrations here
                    )
                    .fallbackToDestructiveMigrationOnDowngrade() // Only destroy on downgrade
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}