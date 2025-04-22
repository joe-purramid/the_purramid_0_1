// PurramidDatabase.kt
package com.example.purramid.thepurramid.data.db 

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The Room database for the Purramid application.
 * This contains tables for Randomizer features.
 */
@Database(
    entities = [
        // List all your entity classes here
        SpinListEntity::class,
        SpinItemEntity::class,
        SpinSettingsEntity::class,
        RandomizerInstanceEntity::class
    ],
    version = 1, // Start with version 1. Increment this number if you change the schema later and provide a Migration.
    exportSchema = false // Set to true if you want to export the schema to a file for version control (recommended for production apps)
)
@TypeConverters(Converters::class) // Register the TypeConverters class
abstract class PurramidDatabase : RoomDatabase() {

    /**
     * Abstract function to get the Data Access Object for Randomizer tables.
     * Room will generate the implementation.
     */
    abstract fun randomizerDao(): RandomizerDao

    companion object {
        // Singleton prevents multiple instances of the database opening at the
        // same time. Using @Volatile ensures that writes to this field are immediately
        // made visible to other threads.
        @Volatile
        private var INSTANCE: PurramidDatabase? = null

        /**
         * Gets the singleton instance of the PurramidDatabase.
         * Creates the database the first time it's accessed, using Room's
         * database builder.
         *
         * @param context The application context.
         * @return The singleton PurramidDatabase instance.
         */
        fun getDatabase(context: Context): PurramidDatabase {
            // If the INSTANCE is not null, return it; otherwise, create the database
            // within a synchronized block to ensure thread safety.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext, // Use application context to avoid leaks
                    PurramidDatabase::class.java,
                    "purramid_database" // Name of the database file on the device
                )
                // TODO: Add migrations here if you update the database version later.
                // Example: .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration() // TEMP: For development, deletes and recreates DB on version change. REMOVE for production and add proper migrations.
                .build()
                INSTANCE = instance
                // Return the instance
                instance
            }
        }
    }
}