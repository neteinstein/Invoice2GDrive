package org.neteinstein.snap2sheet.data.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.neteinstein.snap2sheet.domain.model.AppendRules
import org.neteinstein.snap2sheet.domain.model.DriveItem
import org.neteinstein.snap2sheet.domain.model.Invoice
import org.neteinstein.snap2sheet.domain.sheets.CellValue
import org.neteinstein.snap2sheet.domain.sheets.InvoiceSheetLayout
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

/**
 * An in-memory stand-in for Google Sheets and Drive, used in demo mode (and in builds without an
 * OAuth client configured) so the whole flow can be tried without a Google account. Applies the
 * same [InvoiceSheetLayout] rules as [GoogleSheetsGateway], so duplicates and monthly tabs behave
 * identically — the rows and files just never leave the device and are gone on restart.
 * [latency] makes the background save visible, as it would be over a real network.
 */
class DemoSheetsGateway(
    private val clock: Clock = Clock.System,
    private val latency: Long = 700,
) : SheetsGateway {

    private class Sheet(val destination: DriveItem, val tabs: LinkedHashMap<String, MutableList<List<String>>>)

    private val mutex = Mutex()
    private val sheets = mutableListOf<Sheet>()
    private val folders = mutableListOf<DriveItem>()
    private val files = mutableMapOf<String, Int>()
    private var nextId = 1

    init {
        val now = clock.now()
        listOf("Despesas 2026" to 0, "Contabilidade PT" to 3, "Fatura Startup Co" to 8).forEach { (name, daysAgo) ->
            sheets += newSheet(name, (now - daysAgo.days).toEpochMilliseconds())
        }
        listOf("Faturas 2026" to 1, "Contabilidade" to 5).forEach { (name, daysAgo) ->
            folders += DriveItem("demo-folder-${nextId++}", name, (now - daysAgo.days).toEpochMilliseconds())
        }
    }

    override suspend fun listSpreadsheets(): List<DriveItem> = mutex.withLock {
        sheets.map { it.destination }.sortedByDescending { it.modifiedAtEpochMillis }
    }

    override suspend fun createSpreadsheet(name: String): DriveItem = mutex.withLock {
        newSheet(name, clock.now().toEpochMilliseconds()).also { sheets += it }.destination
    }

    override suspend fun listFolders(): List<DriveItem> = mutex.withLock {
        folders.sortedByDescending { it.modifiedAtEpochMillis }
    }

    override suspend fun createFolder(name: String): DriveItem = mutex.withLock {
        DriveItem("demo-folder-${nextId++}", name, clock.now().toEpochMilliseconds()).also { folders += it }
    }

    override suspend fun uploadFile(folderId: String, name: String, mimeType: String, bytes: ByteArray): UploadedFile {
        delay(latency.milliseconds)
        return mutex.withLock {
            if (folders.none { it.id == folderId }) throw SheetsException("That folder no longer exists.", SheetsException.Kind.NOT_FOUND)
            val id = "demo-file-${nextId++}"
            files[id] = bytes.size
            UploadedFile(id, "https://drive.google.com/file/d/$id/view")
        }
    }

    override suspend fun findDuplicate(spreadsheetId: String, invoice: Invoice, rules: AppendRules): String? = mutex.withLock {
        if (!rules.skipDuplicateInvoices) return@withLock null
        val (tab, rows) = target(spreadsheetId, invoice, rules)
        tab.takeIf { isDuplicate(rows, invoice, rules) }
    }

    override suspend fun appendInvoice(spreadsheetId: String, invoice: Invoice, rules: AppendRules): AppendOutcome {
        delay(latency.milliseconds)
        return mutex.withLock {
            val (tab, rows) = target(spreadsheetId, invoice, rules)
            if (rules.skipDuplicateInvoices && isDuplicate(rows, invoice, rules)) return@withLock AppendOutcome.Duplicate(tab)
            rows += InvoiceSheetLayout.rowFor(invoice, rows.first(), rules.matchColumnsByHeader).map {
                when (it) {
                    is CellValue.Number -> it.value.toString()
                    is CellValue.Text -> it.value
                }
            }
            val index = sheets.indexOfFirst { it.destination.id == spreadsheetId }
            sheets[index] = Sheet(sheets[index].destination.copy(modifiedAtEpochMillis = clock.now().toEpochMilliseconds()), sheets[index].tabs)
            AppendOutcome.Appended(tab)
        }
    }

    private fun target(spreadsheetId: String, invoice: Invoice, rules: AppendRules): Pair<String, MutableList<List<String>>> {
        val sheet = sheets.firstOrNull { it.destination.id == spreadsheetId }
            ?: throw SheetsException("That spreadsheet no longer exists.", SheetsException.Kind.NOT_FOUND)
        val today = clock.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val tab = InvoiceSheetLayout.monthlyTabFor(invoice, rules.newSheetTabEachMonth, today) ?: sheet.tabs.keys.first()
        return tab to sheet.tabs.getOrPut(tab) { mutableListOf(InvoiceSheetLayout.defaultHeaders) }
    }

    private fun isDuplicate(rows: List<List<String>>, invoice: Invoice, rules: AppendRules): Boolean {
        val atcudIndex = InvoiceSheetLayout.atcudColumnIndex(rows.first(), rules.matchColumnsByHeader) ?: return false
        return InvoiceSheetLayout.isDuplicate(invoice, rows.drop(1).mapNotNull { it.getOrNull(atcudIndex) })
    }

    private fun newSheet(name: String, modifiedAt: Long) = Sheet(
        destination = DriveItem(id = "demo-${nextId++}", name = name, modifiedAtEpochMillis = modifiedAt),
        tabs = linkedMapOf(InvoiceSheetLayout.DEFAULT_TAB to mutableListOf(InvoiceSheetLayout.defaultHeaders)),
    )
}
