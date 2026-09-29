package org.neteinstein.snap2sheet.data.remote

import org.neteinstein.snap2sheet.platform.tr

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.neteinstein.snap2sheet.data.auth.AccessTokenProvider
import org.neteinstein.snap2sheet.data.auth.NotSignedInException
import org.neteinstein.snap2sheet.domain.model.AppendRules
import org.neteinstein.snap2sheet.domain.model.Invoice
import org.neteinstein.snap2sheet.domain.model.DriveItem
import org.neteinstein.snap2sheet.domain.sheets.CellValue
import org.neteinstein.snap2sheet.domain.sheets.InvoiceSheetLayout
import kotlin.time.Clock
import kotlin.time.Instant

private const val SHEETS_API = "https://sheets.googleapis.com/v4/spreadsheets"
private const val DRIVE_FILES_API = "https://www.googleapis.com/drive/v3/files"
private const val DRIVE_UPLOAD_API = "https://www.googleapis.com/upload/drive/v3/files"
private const val SPREADSHEET_MIME = "application/vnd.google-apps.spreadsheet"
private const val FOLDER_MIME = "application/vnd.google-apps.folder"

/** [SheetsGateway] over the Google Sheets v4 and Drive v3 REST APIs. */
class GoogleSheetsGateway(
    private val http: HttpClient,
    private val tokens: AccessTokenProvider,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : SheetsGateway {

    override suspend fun listSpreadsheets(): List<DriveItem> = listDriveItems(SPREADSHEET_MIME)

    override suspend fun listFolders(): List<DriveItem> = listDriveItems(FOLDER_MIME)

    private suspend fun listDriveItems(mimeType: String): List<DriveItem> {
        val list: DriveFileList = call { token ->
            http.get(DRIVE_FILES_API) {
                bearerAuth(token)
                parameter("q", "mimeType='$mimeType' and trashed=false")
                parameter("orderBy", "modifiedTime desc")
                parameter("fields", "files(id,name,modifiedTime)")
                parameter("pageSize", 100)
            }
        }
        return list.files.map { it.toDriveItem() }
    }

    override suspend fun createFolder(name: String): DriveItem {
        val created: DriveFile = call { token ->
            http.post(DRIVE_FILES_API) {
                bearerAuth(token)
                parameter("fields", "id,name,modifiedTime")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("name", name)
                    put("mimeType", FOLDER_MIME)
                })
            }
        }
        return created.toDriveItem()
    }

    override suspend fun uploadFile(folderId: String, name: String, mimeType: String, bytes: ByteArray): UploadedFile {
        val boundary = "fatura-" + clock.now().toEpochMilliseconds()
        val metadata = buildJsonObject {
            put("name", name)
            putJsonArray("parents") { add(folderId) }
        }.toString()
        // Drive's "multipart" upload: JSON metadata and the file bytes in one multipart/related body.
        val body = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata)
            append("\r\n--$boundary\r\nContent-Type: $mimeType\r\n\r\n")
        }.encodeToByteArray() + bytes + "\r\n--$boundary--\r\n".encodeToByteArray()

        val uploaded: DriveFile = call { token ->
            http.post(DRIVE_UPLOAD_API) {
                bearerAuth(token)
                parameter("uploadType", "multipart")
                parameter("fields", "id,name,webViewLink")
                setBody(ByteArrayContent(body, ContentType.parse("multipart/related; boundary=$boundary")))
            }
        }
        return UploadedFile(uploaded.id, uploaded.webViewLink ?: "https://drive.google.com/file/d/${uploaded.id}/view")
    }

    override suspend fun createSpreadsheet(name: String): DriveItem {
        val created: CreatedSpreadsheet = call { token ->
            http.post(SHEETS_API) {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        putJsonObject("properties") { put("title", name) }
                        putJsonArray("sheets") {
                            addJsonObject { put("properties", sheetProperties(InvoiceSheetLayout.DEFAULT_TAB)) }
                        }
                    }
                )
            }
        }
        writeRow(created.spreadsheetId, "${InvoiceSheetLayout.quotedTab(InvoiceSheetLayout.DEFAULT_TAB)}!A1", InvoiceSheetLayout.defaultHeaders)
        return DriveItem(
            id = created.spreadsheetId,
            name = created.properties?.title ?: name,
            modifiedAtEpochMillis = clock.now().toEpochMilliseconds(),
        )
    }

    override suspend fun findDuplicate(spreadsheetId: String, invoice: Invoice, rules: AppendRules): String? {
        if (!rules.skipDuplicateInvoices) return null
        val target = resolveTarget(spreadsheetId, invoice, rules)
        return target.tab.takeIf { isDuplicateIn(spreadsheetId, target, invoice, rules) }
    }

    override suspend fun appendInvoice(spreadsheetId: String, invoice: Invoice, rules: AppendRules): AppendOutcome {
        val target = resolveTarget(spreadsheetId, invoice, rules)
        if (rules.skipDuplicateInvoices && isDuplicateIn(spreadsheetId, target, invoice, rules)) {
            return AppendOutcome.Duplicate(target.tab)
        }
        val row = InvoiceSheetLayout.rowFor(invoice, target.headers, rules.matchColumnsByHeader)
        call<AppendResponse> { token ->
            http.post("$SHEETS_API/$spreadsheetId/values/${encodeRange("${target.quoted}!A1")}:append") {
                bearerAuth(token)
                parameter("valueInputOption", "USER_ENTERED")
                parameter("insertDataOption", "INSERT_ROWS")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("values", buildJsonArray { add(row.toJson()) }) })
            }
        }
        return AppendOutcome.Appended(target.tab)
    }

    private class Target(val tab: String, val headers: List<String>) {
        val quoted: String get() = InvoiceSheetLayout.quotedTab(tab)
    }

    /** The tab [invoice] belongs in and its header row — creating the tab and headers when missing. */
    private suspend fun resolveTarget(spreadsheetId: String, invoice: Invoice, rules: AppendRules): Target {
        val today = clock.now().toLocalDateTime(timeZone).date
        val existingTabs = sheetTitles(spreadsheetId)
        val tab = InvoiceSheetLayout.monthlyTabFor(invoice, rules.newSheetTabEachMonth, today)
            ?: existingTabs.firstOrNull()
            ?: InvoiceSheetLayout.DEFAULT_TAB
        if (tab !in existingTabs) addSheet(spreadsheetId, tab)
        val quoted = InvoiceSheetLayout.quotedTab(tab)
        val headers = readValues(spreadsheetId, "$quoted!1:1", byColumns = false).firstOrNull().orEmpty()
        if (headers.all { it.isBlank() }) {
            writeRow(spreadsheetId, "$quoted!A1", InvoiceSheetLayout.defaultHeaders)
            return Target(tab, InvoiceSheetLayout.defaultHeaders)
        }
        return Target(tab, headers)
    }

    private suspend fun isDuplicateIn(spreadsheetId: String, target: Target, invoice: Invoice, rules: AppendRules): Boolean {
        val atcudIndex = InvoiceSheetLayout.atcudColumnIndex(target.headers, rules.matchColumnsByHeader) ?: return false
        val letter = InvoiceSheetLayout.columnLetter(atcudIndex)
        val column = readValues(spreadsheetId, "${target.quoted}!${letter}2:$letter", byColumns = true).firstOrNull().orEmpty()
        return InvoiceSheetLayout.isDuplicate(invoice, column)
    }

    private suspend fun sheetTitles(spreadsheetId: String): List<String> {
        val info: SpreadsheetInfo = call { token ->
            http.get("$SHEETS_API/$spreadsheetId") {
                bearerAuth(token)
                parameter("fields", "sheets.properties.title")
            }
        }
        return info.sheets.mapNotNull { it.properties?.title }
    }

    private suspend fun addSheet(spreadsheetId: String, title: String) {
        call<BatchUpdateResponse> { token ->
            http.post("$SHEETS_API/$spreadsheetId:batchUpdate") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        putJsonArray("requests") {
                            addJsonObject { putJsonObject("addSheet") { put("properties", sheetProperties(title)) } }
                        }
                    }
                )
            }
        }
    }

    private suspend fun readValues(spreadsheetId: String, range: String, byColumns: Boolean): List<List<String>> {
        val range: ValueRange = call { token ->
            http.get("$SHEETS_API/$spreadsheetId/values/${encodeRange(range)}") {
                bearerAuth(token)
                parameter("majorDimension", if (byColumns) "COLUMNS" else "ROWS")
                parameter("valueRenderOption", "FORMATTED_VALUE")
            }
        }
        return range.values.map { row -> row.map { it.jsonPrimitive.content } }
    }

    private suspend fun writeRow(spreadsheetId: String, range: String, values: List<String>) {
        call<UpdateResponse> { token ->
            http.put("$SHEETS_API/$spreadsheetId/values/${encodeRange(range)}") {
                bearerAuth(token)
                parameter("valueInputOption", "RAW")
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { putJsonArray("values") { addJsonArray { values.forEach { add(it) } } } })
            }
        }
    }

    private fun sheetProperties(title: String) = buildJsonObject {
        put("title", title)
        putJsonObject("gridProperties") { put("frozenRowCount", 1) }
    }

    /**
     * Runs [request] with a bearer token, retrying once with a refreshed token on 401, and maps
     * every failure to a [SheetsException] whose message can be shown as-is.
     */
    private suspend inline fun <reified T> call(crossinline request: suspend (token: String) -> HttpResponse): T {
        try {
            var response = request(tokens.accessToken(forceRefresh = false))
            if (response.status == HttpStatusCode.Unauthorized) {
                response = request(tokens.accessToken(forceRefresh = true))
            }
            if (!response.status.isSuccess()) throw errorFor(response)
            return response.body()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SheetsException) {
            throw e
        } catch (e: NotSignedInException) {
            throw SheetsException(e.message ?: tr("Sign in to Google again.", "Inicie sessão no Google novamente."), SheetsException.Kind.NOT_SIGNED_IN, e)
        } catch (e: Exception) {
            throw SheetsException(tr("Couldn't reach Google. Check your connection and try again.", "Não foi possível contactar o Google. Verifique a ligação e tente novamente."), SheetsException.Kind.NETWORK, e)
        }
    }

    private suspend fun errorFor(response: HttpResponse): SheetsException {
        val googleMessage = runCatching {
            lenientJson.parseToJsonElement(response.bodyAsText()).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
        }.getOrNull()
        return when (response.status) {
            HttpStatusCode.Unauthorized -> SheetsException(tr("Your Google session expired. Sign in again.", "A sua sessão Google expirou. Inicie sessão novamente."), SheetsException.Kind.NOT_SIGNED_IN)
            HttpStatusCode.Forbidden -> SheetsException(
                googleMessage ?: tr("The app doesn't have access to that spreadsheet.", "A app não tem acesso a essa folha de cálculo."),
                SheetsException.Kind.PERMISSION,
            )
            HttpStatusCode.NotFound -> SheetsException(tr("That spreadsheet or folder no longer exists.", "Essa folha de cálculo ou pasta já não existe."), SheetsException.Kind.NOT_FOUND)
            else -> SheetsException(googleMessage ?: tr("Google Sheets returned ${response.status.value}.", "O Google Sheets devolveu ${response.status.value}."), SheetsException.Kind.OTHER)
        }
    }

    private fun List<CellValue>.toJson(): JsonArray = buildJsonArray {
        for (cell in this@toJson) {
            when (cell) {
                is CellValue.Number -> add(JsonPrimitive(cell.value))
                is CellValue.Text -> add(JsonPrimitive(InvoiceSheetLayout.escapeText(cell.value)))
            }
        }
    }

    private companion object {
        val lenientJson = Json { ignoreUnknownKeys = true }
    }
}

/** Percent-encodes an A1 range for use as a URL path segment (`'2026-09'!A1` → `%272026-09%27%21A1`). */
internal fun encodeRange(range: String): String = buildString {
    for (byte in range.encodeToByteArray()) {
        val c = byte.toInt().toChar()
        if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_' || c == '.' || c == '~') {
            append(c)
        } else {
            append('%')
            append(((byte.toInt() shr 4) and 0xF).digitToChar(16).uppercaseChar())
            append((byte.toInt() and 0xF).digitToChar(16).uppercaseChar())
        }
    }
}

@Serializable
private data class DriveFileList(val files: List<DriveFile> = emptyList())

@Serializable
private data class DriveFile(val id: String, val name: String = "", val modifiedTime: String? = null, val webViewLink: String? = null) {
    fun toDriveItem() = DriveItem(
        id = id,
        name = name,
        modifiedAtEpochMillis = modifiedTime?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() },
    )
}

@Serializable
private data class CreatedSpreadsheet(val spreadsheetId: String, val properties: SheetTitle? = null)

@Serializable
private data class SpreadsheetInfo(val sheets: List<SheetEntry> = emptyList())

@Serializable
private data class SheetEntry(val properties: SheetTitle? = null)

@Serializable
private data class SheetTitle(val title: String? = null)

@Serializable
private data class ValueRange(val values: List<List<kotlinx.serialization.json.JsonElement>> = emptyList())

@Serializable
private class AppendResponse

@Serializable
private class UpdateResponse

@Serializable
private class BatchUpdateResponse
