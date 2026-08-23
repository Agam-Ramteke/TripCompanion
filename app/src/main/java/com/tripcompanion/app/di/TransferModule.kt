package com.tripcompanion.app.di

import android.content.Context
import com.tripcompanion.app.data.local.ImageStorageHelper
import com.tripcompanion.app.data.transfer.TripImagesDir
import com.tripcompanion.app.data.transfer.TripTransferServiceImpl
import com.tripcompanion.app.domain.service.TripTransferService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * Moving a trip between devices: the service, and the one folder it needs.
 *
 * `@Provides` rather than `@Binds` for the service because the directory has to be provided
 * anyway, and a module that is half abstract and half not is worse than either.
 */
@Module
@InstallIn(SingletonComponent::class)
object TransferModule {

    /**
     * The photographs directory, resolved once here.
     *
     * Handing [TripTransferServiceImpl] a `File` instead of a `Context` is what keeps every line
     * of the import and export runnable in a JVM test against a temporary folder. The directory
     * is the same one [ImageStorageHelper] writes picked photos into, so an imported picture and
     * a chosen one are indistinguishable afterwards — which is the point: after the import there
     * is no such thing as a transferred photo, only the trip's photos.
     */
    @Provides
    @Singleton
    @TripImagesDir
    fun provideTripImagesDir(@ApplicationContext context: Context): File =
        ImageStorageHelper.photosDir(context)

    @Provides
    @Singleton
    fun provideTripTransferService(impl: TripTransferServiceImpl): TripTransferService = impl
}
