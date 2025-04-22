// DatabaseModule.kt
package com.example.purramid.thepurramid.data.db

import android.content.Context
import com.example.purramid.thepurramid.data.db.PurramidDatabase
import com.example.purramid.thepurramid.data.db.RandomizerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // Provides dependencies for the entire app lifecycle
object DatabaseModule {

    @Provides
    @Singleton // Ensures only one instance of the DAO is created
    fun provideRandomizerDao(database: PurramidDatabase): RandomizerDao {
        return database.randomizerDao()
    }

    @Provides
    @Singleton // Ensures only one instance of the Database is created
    fun providePurramidDatabase(@ApplicationContext appContext: Context): PurramidDatabase {
        return PurramidDatabase.getDatabase(appContext)
    }
}