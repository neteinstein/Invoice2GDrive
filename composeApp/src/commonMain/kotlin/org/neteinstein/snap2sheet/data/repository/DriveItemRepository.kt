package org.neteinstein.snap2sheet.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import org.neteinstein.snap2sheet.data.local.KeyValueStore
import org.neteinstein.snap2sheet.data.remote.SheetsGateway
import org.neteinstein.snap2sheet.domain.model.DriveItem

/**
 * A kind of Drive item the user saves to — spreadsheets or folders — and which one is the
 * default. The list is cached locally so the last known state shows offline; [refresh] pulls it
 * from Google Drive.
 */
interface DriveItemRepository {
    val items: StateFlow<List<DriveItem>>

    /** The default, set during setup. Kept even if it drops out of [items]. */
    val selected: StateFlow<DriveItem?>
    val isRefreshing: StateFlow<Boolean>

    suspend fun refresh(): Result<Unit>
    fun select(item: DriveItem)

    /** Creates a new item in Drive; it becomes the default when [makeDefault]. */
    suspend fun create(name: String, makeDefault: Boolean = true): Result<DriveItem>

    /** Forgets the cached list and the default — on sign-out. */
    fun clear()
}

/** Spreadsheets invoices are appended to. */
interface SpreadsheetRepository : DriveItemRepository

/** Folders invoice photos are uploaded to. */
interface FolderRepository : DriveItemRepository

open class CachedDriveItemRepository(
    private val store: KeyValueStore,
    private val json: Json,
    private val listKey: String,
    private val defaultKey: String,
    private val list: suspend () -> List<DriveItem>,
    private val createItem: suspend (String) -> DriveItem,
) : DriveItemRepository {

    private val _items = MutableStateFlow(
        store.getString(listKey)?.let { runCatching { json.decodeFromString<List<DriveItem>>(it) }.getOrNull() }.orEmpty()
    )
    override val items: StateFlow<List<DriveItem>> = _items.asStateFlow()

    private val _selected = MutableStateFlow(
        store.getString(defaultKey)?.let { runCatching { json.decodeFromString<DriveItem>(it) }.getOrNull() }
    )
    override val selected: StateFlow<DriveItem?> = _selected.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    override val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    override suspend fun refresh(): Result<Unit> {
        _isRefreshing.value = true
        return try {
            val fresh = list()
            setItems(fresh)
            // Pick up renames of the default.
            _selected.value?.let { selected -> fresh.firstOrNull { it.id == selected.id }?.let(::select) }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _isRefreshing.value = false
        }
    }

    override fun select(item: DriveItem) {
        _selected.value = item
        store.putString(defaultKey, json.encodeToString(item))
    }

    override suspend fun create(name: String, makeDefault: Boolean): Result<DriveItem> = try {
        val created = createItem(name.trim())
        setItems(listOf(created) + _items.value.filterNot { it.id == created.id })
        if (makeDefault) select(created)
        Result.success(created)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun clear() {
        _items.value = emptyList()
        _selected.value = null
        store.remove(listKey)
        store.remove(defaultKey)
    }

    private fun setItems(list: List<DriveItem>) {
        _items.value = list
        store.putString(listKey, json.encodeToString(list))
    }
}

class DefaultSpreadsheetRepository(store: KeyValueStore, gateway: SheetsGateway, json: Json) :
    CachedDriveItemRepository(store, json, "spreadsheets", "default_spreadsheet", gateway::listSpreadsheets, gateway::createSpreadsheet),
    SpreadsheetRepository

class DefaultFolderRepository(store: KeyValueStore, gateway: SheetsGateway, json: Json) :
    CachedDriveItemRepository(store, json, "folders", "default_folder", gateway::listFolders, gateway::createFolder),
    FolderRepository
