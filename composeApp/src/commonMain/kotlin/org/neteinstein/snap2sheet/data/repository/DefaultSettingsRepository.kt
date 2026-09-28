package org.neteinstein.snap2sheet.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.neteinstein.snap2sheet.data.local.KeyValueStore
import org.neteinstein.snap2sheet.domain.model.AppTheme
import org.neteinstein.snap2sheet.domain.model.AppendRules

private const val KEY_THEME = "theme"
private const val KEY_MATCH_BY_HEADER = "append_match_by_header"
private const val KEY_SKIP_DUPLICATES = "append_skip_duplicates"
private const val KEY_NEW_TAB_MONTHLY = "append_new_tab_monthly"
private const val KEY_ALWAYS_SAVE_DEFAULT = "always_save_default"
private const val KEY_NOTIFY_WHEN_SAVED = "notify_when_saved"
private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

class DefaultSettingsRepository(private val store: KeyValueStore) : SettingsRepository {

    private val _theme = MutableStateFlow(
        store.getString(KEY_THEME)?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.SYSTEM
    )
    override val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    override fun setTheme(theme: AppTheme) {
        _theme.value = theme
        store.putString(KEY_THEME, theme.name)
    }

    private val _appendRules = MutableStateFlow(
        AppendRules(
            matchColumnsByHeader = store.getString(KEY_MATCH_BY_HEADER)?.toBooleanStrictOrNull() ?: true,
            skipDuplicateInvoices = store.getString(KEY_SKIP_DUPLICATES)?.toBooleanStrictOrNull() ?: true,
            newSheetTabEachMonth = store.getString(KEY_NEW_TAB_MONTHLY)?.toBooleanStrictOrNull() ?: false,
        )
    )
    override val appendRules: StateFlow<AppendRules> = _appendRules.asStateFlow()

    override fun setAppendRules(rules: AppendRules) {
        _appendRules.value = rules
        store.putString(KEY_MATCH_BY_HEADER, rules.matchColumnsByHeader.toString())
        store.putString(KEY_SKIP_DUPLICATES, rules.skipDuplicateInvoices.toString())
        store.putString(KEY_NEW_TAB_MONTHLY, rules.newSheetTabEachMonth.toString())
    }

    private val _alwaysSaveToDefaultSpreadsheet = MutableStateFlow(
        store.getString(KEY_ALWAYS_SAVE_DEFAULT)?.toBooleanStrictOrNull() ?: true
    )
    override val alwaysSaveToDefaultSpreadsheet: StateFlow<Boolean> = _alwaysSaveToDefaultSpreadsheet.asStateFlow()

    override fun setAlwaysSaveToDefaultSpreadsheet(enabled: Boolean) {
        _alwaysSaveToDefaultSpreadsheet.value = enabled
        store.putString(KEY_ALWAYS_SAVE_DEFAULT, enabled.toString())
    }

    private val _notifyWhenSaveFinishes = MutableStateFlow(
        store.getString(KEY_NOTIFY_WHEN_SAVED)?.toBooleanStrictOrNull() ?: true
    )
    override val notifyWhenSaveFinishes: StateFlow<Boolean> = _notifyWhenSaveFinishes.asStateFlow()

    override fun setNotifyWhenSaveFinishes(enabled: Boolean) {
        _notifyWhenSaveFinishes.value = enabled
        store.putString(KEY_NOTIFY_WHEN_SAVED, enabled.toString())
    }

    private val _onboardingCompleted = MutableStateFlow(
        store.getString(KEY_ONBOARDING_COMPLETED)?.toBooleanStrictOrNull() ?: false
    )
    override val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    override fun setOnboardingCompleted(completed: Boolean) {
        _onboardingCompleted.value = completed
        store.putString(KEY_ONBOARDING_COMPLETED, completed.toString())
    }
}
