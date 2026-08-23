package com.tripcompanion.app.di

import com.tripcompanion.app.data.ticket.IrctcTicketImportService
import com.tripcompanion.app.domain.service.TicketImportService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * One binding, for the same reason [SearchModule] has two.
 *
 * The editor depends on [TicketImportService] and knows nothing about content streams or glyph
 * codes. Replacing the hand-rolled extractor with a PDF library, or adding a second ticket
 * layout, changes the class named here and nothing that a screen or a ViewModel imports.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TicketModule {

    @Binds
    @Singleton
    abstract fun bindTicketImportService(
        service: IrctcTicketImportService
    ): TicketImportService
}
