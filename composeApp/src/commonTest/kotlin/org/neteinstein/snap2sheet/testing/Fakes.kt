package org.neteinstein.snap2sheet.testing

import org.neteinstein.snap2sheet.data.local.KeyValueStore
import org.neteinstein.snap2sheet.data.local.PhotoStore
import org.neteinstein.snap2sheet.data.remote.AppendOutcome
import org.neteinstein.snap2sheet.data.remote.SheetsGateway
import org.neteinstein.snap2sheet.data.remote.UploadedFile
import org.neteinstein.snap2sheet.domain.model.AppendRules
import org.neteinstein.snap2sheet.domain.model.DriveItem
import org.neteinstein.snap2sheet.domain.model.Invoice
import org.neteinstein.snap2sheet.platform.BackgroundScheduler
import org.neteinstein.snap2sheet.platform.Notifier
import kotlin.time.Clock
import kotlin.time.Instant

class InMemoryKeyValueStore : KeyValueStore {
    val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) {
        values[key] = value
    }
    override fun remove(key: String) {
        values.remove(key)
    }
}

class InMemoryPhotoStore : PhotoStore {
    val photos = mutableMapOf<String, ByteArray>()
    private var next = 1
    override suspend fun save(bytes: ByteArray): String = "photo-${next++}".also { photos[it] = bytes }
    override suspend fun load(key: String): ByteArray? = photos[key]
    override suspend fun delete(key: String) {
        photos.remove(key)
    }
}

class FixedClock(var now: Instant = Instant.parse("2026-09-18T14:32:00Z")) : Clock {
    override fun now(): Instant = now
}

class RecordingNotifier : Notifier {
    val notifications = mutableListOf<Pair<String, String>>()
    override fun notify(title: String, message: String) {
        notifications += title to message
    }
}

class RecordingScheduler : BackgroundScheduler {
    var scheduled = 0
    override fun scheduleSync() {
        scheduled++
    }
    override suspend fun <T> keepAlive(block: suspend () -> T): T = block()
}

class FakeSheetsGateway : SheetsGateway {
    var spreadsheets = mutableListOf(DriveItem("sheet-1", "Despesas 2026", 1_000L))
    var folders = mutableListOf(DriveItem("folder-1", "Faturas", 1_000L))
    val appended = mutableListOf<Pair<String, Invoice>>()
    val uploads = mutableListOf<Triple<String, String, Int>>()

    /** Thrown by every call while set. */
    var failWith: Exception? = null

    /** Thrown by the next append only. */
    var failNextAppendWith: Exception? = null
    var duplicate = false

    override suspend fun listSpreadsheets() = failWith?.let { throw it } ?: spreadsheets.toList()

    override suspend fun createSpreadsheet(name: String): DriveItem {
        failWith?.let { throw it }
        return DriveItem("sheet-${spreadsheets.size + 1}", name, 2_000L).also { spreadsheets.add(0, it) }
    }

    override suspend fun listFolders() = failWith?.let { throw it } ?: folders.toList()

    override suspend fun createFolder(name: String): DriveItem {
        failWith?.let { throw it }
        return DriveItem("folder-${folders.size + 1}", name, 2_000L).also { folders.add(0, it) }
    }

    override suspend fun findDuplicate(spreadsheetId: String, invoice: Invoice, rules: AppendRules): String? {
        failWith?.let { throw it }
        return if (duplicate) "Faturas" else null
    }

    override suspend fun appendInvoice(spreadsheetId: String, invoice: Invoice, rules: AppendRules): AppendOutcome {
        failWith?.let { throw it }
        failNextAppendWith?.let { failNextAppendWith = null; throw it }
        if (duplicate) return AppendOutcome.Duplicate("Faturas")
        appended += spreadsheetId to invoice
        return AppendOutcome.Appended("Faturas")
    }

    override suspend fun uploadFile(folderId: String, name: String, mimeType: String, bytes: ByteArray): UploadedFile {
        failWith?.let { throw it }
        uploads += Triple(folderId, name, bytes.size)
        return UploadedFile("file-${uploads.size}", "https://drive.google.com/file/d/file-${uploads.size}/view")
    }
}

/** A real AT QR payload shape; the NIFs pass the check digit. */
const val SAMPLE_QR = "A:500100209*B:999999990*C:PT*D:FS*E:N*F:20260918*G:FS 2026/004821*H:AAJFJMM9-4821*" +
    "I1:PT*I7:14.98*I8:3.44*N:3.44*O:18.42*Q:abcd*R:1234"
