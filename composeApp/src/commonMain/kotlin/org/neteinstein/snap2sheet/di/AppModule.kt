package org.neteinstein.snap2sheet.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.neteinstein.snap2sheet.data.auth.platformGoogleAuthProvider
import org.neteinstein.snap2sheet.data.local.platformKeyValueStore
import org.neteinstein.snap2sheet.data.local.platformPhotoStore
import org.neteinstein.snap2sheet.data.remote.AccountAwareSheetsGateway
import org.neteinstein.snap2sheet.data.remote.DemoSheetsGateway
import org.neteinstein.snap2sheet.data.remote.GoogleSheetsGateway
import org.neteinstein.snap2sheet.data.remote.SheetsGateway
import org.neteinstein.snap2sheet.data.repository.AccountRepository
import org.neteinstein.snap2sheet.data.repository.DefaultAccountRepository
import org.neteinstein.snap2sheet.data.repository.DefaultFolderRepository
import org.neteinstein.snap2sheet.data.repository.DefaultInvoiceRepository
import org.neteinstein.snap2sheet.data.repository.DefaultSettingsRepository
import org.neteinstein.snap2sheet.data.repository.DefaultSpreadsheetRepository
import org.neteinstein.snap2sheet.data.repository.FolderRepository
import org.neteinstein.snap2sheet.data.repository.InvoiceRepository
import org.neteinstein.snap2sheet.data.repository.SettingsRepository
import org.neteinstein.snap2sheet.data.repository.SpreadsheetRepository
import org.neteinstein.snap2sheet.data.sync.InvoiceSyncer
import org.neteinstein.snap2sheet.data.sync.SaveReporter
import org.neteinstein.snap2sheet.platform.platformBackgroundScheduler
import org.neteinstein.snap2sheet.platform.platformNotifier
import org.neteinstein.snap2sheet.ui.MessageCenter
import org.neteinstein.snap2sheet.ui.screens.history.HistoryViewModel
import org.neteinstein.snap2sheet.ui.screens.home.HomeViewModel
import org.neteinstein.snap2sheet.ui.screens.photo.PhotoViewModel
import org.neteinstein.snap2sheet.ui.screens.review.ReviewViewModel
import org.neteinstein.snap2sheet.ui.screens.scan.ScanViewModel
import org.neteinstein.snap2sheet.ui.screens.settings.SettingsViewModel
import org.neteinstein.snap2sheet.ui.screens.signin.SignInViewModel
import org.neteinstein.snap2sheet.ui.screens.targets.SaveTargetsMode
import org.neteinstein.snap2sheet.ui.screens.targets.SaveTargetsViewModel
import kotlin.time.Clock

internal val appModule = module {
    single<Clock> { Clock.System }
    /** Outlives every screen: background saves and photo cleanup run here. */
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
    single {
        HttpClient {
            expectSuccess = false
            install(ContentNegotiation) { json(get<Json>()) }
            install(HttpTimeout) {
                // Generous enough for a photo upload on a slow mobile connection.
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 15_000
            }
        }
    }
    single { platformKeyValueStore() }
    single { platformPhotoStore() }
    single { platformGoogleAuthProvider(get(), get()) }
    single { platformNotifier() }
    single { platformBackgroundScheduler() }
    single { MessageCenter() }

    single { DefaultSettingsRepository(get()) } bind SettingsRepository::class
    single { DefaultAccountRepository(get(), get(), get(), get()) } bind AccountRepository::class

    single<SheetsGateway>(named("google")) { GoogleSheetsGateway(get(), get<AccountRepository>(), get()) }
    single<SheetsGateway>(named("demo")) { DemoSheetsGateway(get()) }
    single<SheetsGateway> { AccountAwareSheetsGateway(get(), get(named("google")), get(named("demo"))) }

    single { DefaultSpreadsheetRepository(get(), get(), get()) } bind SpreadsheetRepository::class
    single { DefaultFolderRepository(get(), get(), get()) } bind FolderRepository::class
    single { DefaultInvoiceRepository(get(), get(), get(), get(), get()) } bind InvoiceRepository::class
    single {
        val messages = get<MessageCenter>()
        InvoiceSyncer(
            invoices = get(),
            gateway = get(),
            settings = get(),
            notifier = get(),
            scheduler = get(),
            reporter = SaveReporter(messages::show),
            scope = get(),
            clock = get(),
        )
    }

    viewModel { SignInViewModel(get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get()) }
    viewModel { ScanViewModel(get()) }
    viewModel { PhotoViewModel(get()) }
    viewModel { ReviewViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { (mode: SaveTargetsMode) -> SaveTargetsViewModel(mode, get(), get(), get(), get(), get(), get(), get()) }
    viewModel { HistoryViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get()) }
}
