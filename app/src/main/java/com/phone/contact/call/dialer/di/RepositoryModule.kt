package com.phone.contact.call.dialer.di

import android.content.Context
import com.phone.contact.call.dialer.data.repository.CallLogRepositoryImpl
import com.phone.contact.call.dialer.data.repository.ContactRepositoryImpl
import com.phone.contact.call.dialer.domain.repository.CallLogRepository
import com.phone.contact.call.dialer.domain.repository.ContactRepository
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
