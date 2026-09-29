# Snap2Sheet Architecture

Same pattern as [neteinstein/loopgain](https://github.com/neteinstein/loopgain): Kotlin
Multiplatform + Compose Multiplatform, sharing the domain, data and UI layers between Android
and iOS from a single `composeApp` module.

## Layers

```
ui/          Compose screens, view models, navigation, theme, MessageCenter (app-wide snackbar)
domain/      Pure Kotlin: models, the AT fiscal QR parser (qr/), validation (NIF, totals),
             the invoice → spreadsheet-row layout (sheets/), date/amount formatting
data/        Repositories, the SheetsGateway (Google REST), Google auth,
             KeyValueStore + PhotoStore, and sync/InvoiceSyncer (the background save queue)
platform/    expect/actual: camera permission + QR scanner, photo picker, notifications,
             background scheduling
di/          Koin module wiring it together
```

## Module structure

- `composeApp` — the shared KMP library (`commonMain`, `androidMain`, `iosMain`, `wasmJsMain`, `commonTest`)
- `androidApp` — thin Android application shell (`Application`, `MainActivity`, manifest, launcher icon)
- `iosApp` — SwiftUI wrapper (`iOSApp.swift`, `ContentView.swift`) plus the Xcode project

## State management

MVVM with `StateFlow`: each screen with meaningful state has a `ViewModel` (Koin `viewModel {}`)
exposing a single `StateFlow<UiState>` built with `combine(...).stateIn(...)`. Welcome is pure
display and takes callbacks directly. Results that outlive a screen, like "Saved to Despesas 2026"
announced after navigating home, go through `MessageCenter`.

## Data layer

- **KeyValueStore**: an `expect`/`actual` string store (`SharedPreferences` / `NSUserDefaults` /
  `localStorage`). It persists settings, the account, the cached spreadsheet list and the invoice
  history, all serialized as JSON. The history is small enough that a database would be overkill.
- **SheetsGateway**: everything that needs Google. `GoogleSheetsGateway` calls the Sheets v4 and
  Drive v3 REST APIs through Ktor. It lists spreadsheets, creates one with a header row, and appends
  a row after resolving the tab, header row and duplicate check. It retries once with a refreshed
  token on 401 and maps failures to `SheetsException`s with user-facing messages.
  `AccountAwareSheetsGateway` fails with `NOT_SIGNED_IN` until an account is signed in.
- **GoogleAuthProvider** (`expect fun platformGoogleAuthProvider`) reduces each platform's OAuth
  flow to "give me an access token":
  - Android: Google Identity Services `AuthorizationClient`. Play services caches and refreshes the tokens.
  - iOS: authorization code + PKCE in `ASWebAuthenticationSession`, with the refresh token in the Keychain.
  - Web: the Google Identity Services token client, with the access token in `sessionStorage`.
- **Repositories**: `AccountRepository` (also the gateway's token source), `SpreadsheetRepository`
  and `FolderRepository` (both a `CachedDriveItemRepository`, holding the cached list and the
  default), `InvoiceRepository` and `SettingsRepository`. `InvoiceRepository` stores the history
  and the draft under review. `submitDraft` queues the draft, and the repository does no networking
  itself.
- **PhotoStore** (`expect fun platformPhotoStore`) keeps photos until they're uploaded: app files
  on Android and iOS, IndexedDB on the web.
- **InvoiceSyncer** runs the queue on an app-wide coroutine scope. For each `QUEUED` invoice it
  checks for a duplicate, uploads the photo, then appends the row. Each step's result
  (`driveFileId`, `rowAppended`) is persisted as it completes, so retries resume where the last
  attempt stopped. Transient `SheetsException`s back off automatically, and other failures mark the
  invoice `FAILED`. A finished invoice posts a `Notifier` notification and an in-app message.
  `BackgroundScheduler` hands the queue to WorkManager on Android (`InvoiceSyncWorker`) and wraps
  processing in `beginBackgroundTask` on iOS. `initKoin()` resumes the queue at startup.

The rules for turning an `Invoice` into a row live in `domain/sheets/InvoiceSheetLayout`. That
covers column matching by header aliases, duplicate detection by ATCUD, monthly tab names, and
escaping text so a value from a QR payload can't become a formula. Both gateways share it, and it's
unit-tested without HTTP.

## Navigation

One `NavHost` (`ui/navigation/AppNavigation.kt`) with a `Screen` sealed class, mirroring the
journey: Welcome → SignIn → Setup → Home → {Scan → Photo → Review → SaveTargets, History,
Settings}. The start screen depends on stored state: Welcome on first launch, SignIn once
onboarded, Setup until both defaults are chosen, then Home. Signing out anywhere returns to
SignIn. `SaveTargetsScreen` has three modes, each choosing a spreadsheet and a folder: *setup*
(first launch), *save* (from Review, for this invoice only unless "make these my defaults" is on,
then queues the save) and *defaults* (from Settings).

## Theme

`ui/theme/Color.kt` and `Theme.kt` hold the design's brand tokens (accent blue `#2E5AAC`, the
neutral ground, status colors) as a Material 3 `ColorScheme`. The design's three Google Fonts
(Space Grotesk, Manrope, Space Mono) aren't bundled as font files yet — see the doc comment on
`FaturaFonts` for how to add them.
