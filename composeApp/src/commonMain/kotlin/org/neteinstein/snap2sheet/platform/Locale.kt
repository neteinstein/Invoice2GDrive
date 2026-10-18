package org.neteinstein.snap2sheet.platform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppLanguage { PT, EN }

/**
 * The language the app shows: Portuguese by default, switchable to English from the top-right toggle (App.kt).
 * Snapshot state, so composables that call [tr] recompose when it changes. Persisted by
 * [org.neteinstein.snap2sheet.data.repository.SettingsRepository.setLanguage] and restored by `initKoin()`.
 */
var appLanguage: AppLanguage by mutableStateOf(AppLanguage.PT)

val isPortuguese: Boolean get() = appLanguage == AppLanguage.PT

/** Picks the English or Portuguese text for the current [appLanguage]. */
fun tr(en: String, pt: String): String = if (isPortuguese) pt else en

/** The app's display name. */
val appName: String = "Invoice2Sheet"
