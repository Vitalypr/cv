package com.vitalypr.daylog.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.LocalDateTime
import javax.inject.Qualifier

/** Injectable wall clock — tests fix it, production reads the system. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Now

@Module
@InstallIn(SingletonComponent::class)
object SystemModule {

    @Provides
    @Now
    fun now(): () -> LocalDateTime = LocalDateTime::now
}
