package com.example.booking_app.feature.dropin.data.di

import com.example.booking_app.feature.dropin.data.DropInApi
import com.example.booking_app.feature.dropin.data.DropInRegistrationRepositoryImpl
import com.example.booking_app.feature.dropin.data.FirestoreDropInSessionRepository
import com.example.booking_app.feature.dropin.data.FirestoreVenueStatusSource
import com.example.booking_app.feature.dropin.data.SupabaseDropInApi
import com.example.booking_app.feature.dropin.data.VenueStatusSource
import com.example.booking_app.feature.dropin.domain.DropInRegistrationReader
import com.example.booking_app.feature.dropin.domain.DropInRegistrationRepository
import com.example.booking_app.feature.dropin.domain.DropInSessionRepository
import com.example.booking_app.feature.dropin.domain.usecase.CheckDropInWarnings
import com.example.booking_app.feature.dropin.domain.usecase.CheckDropInWarningsImpl
import com.example.booking_app.feature.dropin.domain.usecase.GetDefaultDropInFilter
import com.example.booking_app.feature.dropin.domain.usecase.GetDefaultDropInFilterImpl
import com.example.booking_app.feature.dropin.domain.usecase.LoadDropInPage
import com.example.booking_app.feature.dropin.domain.usecase.LoadDropInPageImpl
import com.example.booking_app.feature.dropin.domain.usecase.ObserveDropInSession
import com.example.booking_app.feature.dropin.domain.usecase.ObserveDropInSessionImpl
import com.example.booking_app.feature.dropin.domain.usecase.ObserveMyDropInRegistration
import com.example.booking_app.feature.dropin.domain.usecase.ObserveMyDropInRegistrationImpl
import com.example.booking_app.feature.dropin.domain.usecase.RegisterDropIn
import com.example.booking_app.feature.dropin.domain.usecase.RegisterDropInImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** T015 (phần Hilt): repository, reader cho nhóm khác và các usecase của spec 080. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DropInModule {

    // ---------- Data ----------

    @Binds
    abstract fun bindSessionRepository(impl: FirestoreDropInSessionRepository): DropInSessionRepository

    @Binds
    abstract fun bindRegistrationRepository(impl: DropInRegistrationRepositoryImpl): DropInRegistrationRepository

    /** Cho B (spec 060) đọc lượt sắp tới (contracts/ui-screens.md mục 3). */
    @Binds
    abstract fun bindRegistrationReader(impl: DropInRegistrationRepositoryImpl): DropInRegistrationReader

    @Binds
    abstract fun bindDropInApi(impl: SupabaseDropInApi): DropInApi

    @Binds
    abstract fun bindVenueStatusSource(impl: FirestoreVenueStatusSource): VenueStatusSource

    // ---------- Usecase ----------

    @Binds
    abstract fun bindLoadDropInPage(impl: LoadDropInPageImpl): LoadDropInPage

    @Binds
    abstract fun bindGetDefaultDropInFilter(impl: GetDefaultDropInFilterImpl): GetDefaultDropInFilter

    @Binds
    abstract fun bindObserveDropInSession(impl: ObserveDropInSessionImpl): ObserveDropInSession

    @Binds
    abstract fun bindObserveMyDropInRegistration(impl: ObserveMyDropInRegistrationImpl): ObserveMyDropInRegistration

    @Binds
    abstract fun bindRegisterDropIn(impl: RegisterDropInImpl): RegisterDropIn

    @Binds
    abstract fun bindCheckDropInWarnings(impl: CheckDropInWarningsImpl): CheckDropInWarnings
}
