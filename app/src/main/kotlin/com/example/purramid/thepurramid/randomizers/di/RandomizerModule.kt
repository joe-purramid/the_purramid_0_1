// RandomizerModule.kt
package com.example.purramid.thepurramid.randomizers.di

import com.example.purramid.thepurramid.randomizers.data.RandomizerRepository
import com.example.purramid.thepurramid.randomizers.data.RandomizerRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RandomizerModule {
    
    @Binds
    @Singleton
    abstract fun bindRandomizerRepository(
        randomizerRepositoryImpl: RandomizerRepositoryImpl
    ): RandomizerRepository
}