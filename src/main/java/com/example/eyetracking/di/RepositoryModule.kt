package com.example.eyetracking.di

import com.example.eyetracking.repository.EyeTrackingRepository
import com.example.eyetracking.repository.EyeTrackingRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindEyeTrackingRepository(
        eyeTrackingRepositoryImpl: EyeTrackingRepositoryImpl
    ): EyeTrackingRepository
}
