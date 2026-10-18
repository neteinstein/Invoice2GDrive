package org.neteinstein.snap2sheet.data.repository

import kotlinx.coroutines.flow.StateFlow
import org.neteinstein.snap2sheet.domain.model.AppTheme
import org.neteinstein.snap2sheet.domain.model.AppendRules
import org.neteinstein.snap2sheet.platform.AppLanguage

/** User-configurable preferences, persisted across launches via [org.neteinstein.snap2sheet.data.local.KeyValueStore]. */
interface SettingsRepository {
    val theme: StateFlow<AppTheme>
    fun setTheme(theme: AppTheme)

    /** Portuguese unless the user picked English. Setting it also switches [org.neteinstein.snap2sheet.platform.appLanguage]. */
    val language: StateFlow<AppLanguage>
    fun setLanguage(language: AppLanguage)

    val appendRules: StateFlow<AppendRules>
    fun setAppendRules(rules: AppendRules)

    /** "Always save to my defaults": Review saves straight away instead of asking where. */
    val alwaysSaveToDefaultSpreadsheet: StateFlow<Boolean>
    fun setAlwaysSaveToDefaultSpreadsheet(enabled: Boolean)

    /** A notification when a background save finishes — saved, duplicate or failed. */
    val notifyWhenSaveFinishes: StateFlow<Boolean>
    fun setNotifyWhenSaveFinishes(enabled: Boolean)

    /** Whether the Welcome screen has been dismissed once; decides the start screen. */
    val onboardingCompleted: StateFlow<Boolean>
    fun setOnboardingCompleted(completed: Boolean)
}
