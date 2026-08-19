package com.example.contactapp.di

import android.content.Context
import com.example.contactapp.data.repository.CallLogRepositoryImpl
import com.example.contactapp.data.repository.ContactRepositoryImpl
import com.example.contactapp.domain.repository.CallLogRepository
import com.example.contactapp.domain.repository.ContactRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCallLogRepository(
        impl: CallLogRepositoryImpl
    ): CallLogRepository

    @Binds
    @Singleton
    abstract fun bindContactRepository(
        impl: ContactRepositoryImpl
    ): ContactRepository

    companion object {
        @Provides
        @Singleton
        fun provideContentResolver(@ApplicationContext context: Context) = context.contentResolver
    }
}
