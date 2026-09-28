package org.neteinstein.snap2sheet.ui.screens.targets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.neteinstein.snap2sheet.data.repository.DriveItemRepository
import org.neteinstein.snap2sheet.data.repository.FolderRepository
import org.neteinstein.snap2sheet.data.repository.InvoiceRepository
import org.neteinstein.snap2sheet.data.repository.SettingsRepository
import org.neteinstein.snap2sheet.data.repository.SpreadsheetRepository
import org.neteinstein.snap2sheet.data.sync.InvoiceSyncer
import org.neteinstein.snap2sheet.domain.format.Formatting
import org.neteinstein.snap2sheet.domain.model.AppendRules
import org.neteinstein.snap2sheet.domain.model.DriveItem
import org.neteinstein.snap2sheet.ui.MessageCenter
import kotlin.time.Clock

/**
 * - [SETUP]: first launch — pick the default spreadsheet and folder before scanning anything.
 * - [SAVE]: saving the invoice under review — defaults preselected, overridable for this invoice.
 * - [DEFAULTS]: changing the defaults from Settings.
 */
enum class SaveTargetsMode { SETUP, SAVE, DEFAULTS }

enum class TargetKind(val label: String) { SPREADSHEET("Spreadsheet"), FOLDER("Drive folder") }

data class TargetRow(val item: DriveItem, val subtitle: String)

data class SaveTargetsUiState(
    val tab: TargetKind = TargetKind.SPREADSHEET,
    val rows: List<TargetRow> = emptyList(),
    val query: String = "",
    val spreadsheet: DriveItem? = null,
    val folder: DriveItem? = null,
    /** SAVE mode: also store this invoice's choices as the new defaults. */
    val makeDefault: Boolean = false,
    val alwaysSaveToDefaults: Boolean = true,
    val appendRules: AppendRules = AppendRules(),
    val isRefreshing: Boolean = false,
    val isCreating: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
) {
    val selectedId: String? get() = if (tab == TargetKind.SPREADSHEET) spreadsheet?.id else folder?.id
    val canContinue: Boolean get() = spreadsheet != null && folder != null
    val filtered: List<TargetRow>
        get() = if (query.isBlank()) rows else rows.filter { it.item.name.contains(query, ignoreCase = true) }
}

class SaveTargetsViewModel(
    private val mode: SaveTargetsMode,
    private val spreadsheets: SpreadsheetRepository,
    private val folders: FolderRepository,
    private val invoiceRepository: InvoiceRepository,
    private val settings: SettingsRepository,
    private val syncer: InvoiceSyncer,
    private val messages: MessageCenter,
    private val clock: Clock,
) : ViewModel() {

    private data class Local(
        val tab: TargetKind = TargetKind.SPREADSHEET,
        val query: String = "",
        // SAVE mode keeps its own choice so the defaults aren't touched unless asked.
        val spreadsheet: DriveItem? = null,
        val folder: DriveItem? = null,
        val makeDefault: Boolean = false,
        val isCreating: Boolean = false,
        val error: String? = null,
        val done: Boolean = false,
    )

    private val local = MutableStateFlow(Local(spreadsheet = spreadsheets.selected.value, folder = folders.selected.value))

    private fun repository(kind: TargetKind): DriveItemRepository = if (kind == TargetKind.SPREADSHEET) spreadsheets else folders

    private val lists = combine(
        spreadsheets.items, spreadsheets.selected, folders.items, folders.selected,
        combine(spreadsheets.isRefreshing, folders.isRefreshing) { a, b -> a || b },
    ) { sheetItems, sheetDefault, folderItems, folderDefault, refreshing ->
        Lists(sheetItems, sheetDefault, folderItems, folderDefault, refreshing)
    }

    private data class Lists(
        val sheets: List<DriveItem>,
        val sheetDefault: DriveItem?,
        val folders: List<DriveItem>,
        val folderDefault: DriveItem?,
        val refreshing: Boolean,
    )

    val state: StateFlow<SaveTargetsUiState> = combine(
        lists,
        local,
        settings.alwaysSaveToDefaultSpreadsheet,
        settings.appendRules,
    ) { lists, local, always, rules ->
        val spreadsheet = if (mode == SaveTargetsMode.SAVE) local.spreadsheet else lists.sheetDefault
        val folder = if (mode == SaveTargetsMode.SAVE) local.folder else lists.folderDefault
        val (items, chosen) = if (local.tab == TargetKind.SPREADSHEET) lists.sheets to spreadsheet else lists.folders to folder
        val now = clock.now().toEpochMilliseconds()
        // Drive only lists the 100 most recent; keep the chosen one visible even if it's older.
        val all = if (chosen != null && items.none { it.id == chosen.id }) listOf(chosen) + items else items
        SaveTargetsUiState(
            tab = local.tab,
            rows = all.map { TargetRow(it, Formatting.lastEdited(it.modifiedAtEpochMillis, now)) },
            query = local.query,
            spreadsheet = spreadsheet,
            folder = folder,
            makeDefault = local.makeDefault,
            alwaysSaveToDefaults = always,
            appendRules = rules,
            isRefreshing = lists.refreshing,
            isCreating = local.isCreating,
            error = local.error,
            done = local.done,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SaveTargetsUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val failure = listOf(spreadsheets, folders).map { it.refresh() }.firstNotNullOfOrNull { it.exceptionOrNull() }
            if (failure != null) local.update { it.copy(error = failure.message ?: "Couldn't load your Google Drive.") }
        }
    }

    fun selectTab(tab: TargetKind) = local.update { it.copy(tab = tab, query = "") }

    fun onQueryChange(value: String) = local.update { it.copy(query = value) }

    fun onAlwaysSaveChange(value: Boolean) = settings.setAlwaysSaveToDefaultSpreadsheet(value)

    fun onMakeDefaultChange(value: Boolean) = local.update { it.copy(makeDefault = value) }

    fun dismissError() = local.update { it.copy(error = null) }

    fun select(item: DriveItem) {
        val tab = local.value.tab
        if (mode == SaveTargetsMode.SAVE) {
            local.update { if (tab == TargetKind.SPREADSHEET) it.copy(spreadsheet = item) else it.copy(folder = item) }
        } else {
            repository(tab).select(item)
        }
        // First-time setup walks straight on to the folder once a spreadsheet is picked.
        if (mode == SaveTargetsMode.SETUP && tab == TargetKind.SPREADSHEET && folders.selected.value == null) {
            selectTab(TargetKind.FOLDER)
        }
    }

    /** "Faturas 2026" for a new spreadsheet, "Faturas" for a new folder. */
    fun suggestedName(): String = when (local.value.tab) {
        TargetKind.SPREADSHEET -> "Faturas ${clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).year}"
        TargetKind.FOLDER -> "Faturas"
    }

    fun create(name: String) {
        if (name.isBlank() || local.value.isCreating) return
        val tab = local.value.tab
        local.update { it.copy(isCreating = true, error = null) }
        viewModelScope.launch {
            repository(tab).create(name, makeDefault = mode != SaveTargetsMode.SAVE)
                .onSuccess { created ->
                    if (mode == SaveTargetsMode.SAVE) select(created)
                    else if (mode == SaveTargetsMode.SETUP && tab == TargetKind.SPREADSHEET && folders.selected.value == null) selectTab(TargetKind.FOLDER)
                    messages.show("Created \"${created.name}\" in your Google Drive.")
                }
                .onFailure { e -> local.update { it.copy(error = e.message ?: "Couldn't create it.") } }
            local.update { it.copy(isCreating = false) }
        }
    }

    /** SETUP/DEFAULTS: done. SAVE: queues the draft for a background save to the chosen targets. */
    fun confirm() {
        val current = state.value
        val spreadsheet = current.spreadsheet
        val folder = current.folder
        if (spreadsheet == null || folder == null) {
            local.update { it.copy(error = "Pick a spreadsheet and a folder first.") }
            return
        }
        if (mode == SaveTargetsMode.SAVE) {
            if (current.makeDefault) {
                spreadsheets.select(spreadsheet)
                folders.select(folder)
            }
            if (invoiceRepository.submitDraft(spreadsheet, folder) != null) {
                syncer.kick()
                messages.show("Saving in the background — you'll be notified when it's done.")
            }
        }
        local.update { it.copy(done = true) }
    }
}
