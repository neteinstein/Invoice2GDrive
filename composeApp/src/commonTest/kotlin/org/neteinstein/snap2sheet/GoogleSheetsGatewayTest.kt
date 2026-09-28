package org.neteinstein.snap2sheet

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import org.neteinstein.snap2sheet.data.remote.AppendOutcome
import org.neteinstein.snap2sheet.data.remote.GoogleSheetsGateway
import org.neteinstein.snap2sheet.data.remote.SheetsException
import org.neteinstein.snap2sheet.domain.model.AppendRules
import org.neteinstein.snap2sheet.testing.FixedClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GoogleSheetsGatewayTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val tokensIssued = mutableListOf<Boolean>()

    private fun gateway(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): GoogleSheetsGateway {
        val http = HttpClient(MockEngine { request -> requests += request; handler(request) }) {
            expectSuccess = false
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return GoogleSheetsGateway(
            http = http,
            tokens = { forceRefresh -> tokensIssued += forceRefresh; if (forceRefresh) "fresh" else "stale" },
            clock = FixedClock(),
            timeZone = TimeZone.UTC,
        )
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private val HttpRequestData.bodyText: String get() = (body as TextContent).text

    @Test
    fun append_writesHeadersToAnEmptySheetThenAppendsTheRow() = runTest {
        val gateway = gateway { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/sheet-1") -> json("""{"sheets":[{"properties":{"title":"Sheet1"}}]}""")
                path.endsWith("/values/%27Sheet1%27%211%3A1") -> json("""{"range":"Sheet1!1:1"}""")
                request.method == HttpMethod.Put -> json("{}")
                path.endsWith("/values/%27Sheet1%27%21G2%3AG") -> json("""{"values":[["OTHER-1"]]}""")
                path.endsWith(":append") -> json("{}")
                else -> error("Unexpected request ${request.method.value} $path")
            }
        }

        val outcome = gateway.appendInvoice("sheet-1", sampleInvoice(merchantName = "=cmd"), AppendRules())

        assertEquals(AppendOutcome.Appended("Sheet1"), outcome)
        val headerWrite = requests.single { it.method == HttpMethod.Put }
        assertTrue("\"ATCUD\"" in headerWrite.bodyText)
        assertEquals("RAW", headerWrite.url.parameters["valueInputOption"])
        val append = requests.last()
        assertTrue(append.url.encodedPath.endsWith("/values/%27Sheet1%27%21A1:append"), append.url.encodedPath)
        assertEquals("USER_ENTERED", append.url.parameters["valueInputOption"])
        assertEquals("INSERT_ROWS", append.url.parameters["insertDataOption"])
        assertTrue("\"'=cmd\"" in append.bodyText, append.bodyText)
        assertTrue("18.42" in append.bodyText)
        assertTrue(requests.all { it.headers[HttpHeaders.Authorization] == "Bearer stale" })
    }

    @Test
    fun append_skipsInvoicesWhoseAtcudIsAlreadyInTheSheet() = runTest {
        val gateway = gateway { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/sheet-1") -> json("""{"sheets":[{"properties":{"title":"Faturas"}}]}""")
                path.endsWith("%211%3A1") -> json("""{"values":[["Data","ATCUD","Total"]]}""")
                path.endsWith("%21B2%3AB") -> json("""{"values":[["AAJFJMM9-4821"]]}""")
                else -> error("Unexpected request ${request.method.value} $path")
            }
        }

        val outcome = gateway.appendInvoice("sheet-1", sampleInvoice(), AppendRules())

        assertIs<AppendOutcome.Duplicate>(outcome)
        assertTrue(requests.none { it.url.encodedPath.endsWith(":append") })
    }

    @Test
    fun append_createsTheMonthlyTabWhenMissing() = runTest {
        val gateway = gateway { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/sheet-1") -> json("""{"sheets":[{"properties":{"title":"Faturas"}}]}""")
                path.endsWith(":batchUpdate") -> json("{}")
                path.endsWith("%211%3A1") -> json("{}")
                request.method == HttpMethod.Put -> json("{}")
                path.contains("%272026-09%27%21G2") -> json("{}")
                path.endsWith(":append") -> json("{}")
                else -> error("Unexpected request ${request.method.value} $path")
            }
        }

        val outcome = gateway.appendInvoice("sheet-1", sampleInvoice(), AppendRules(newSheetTabEachMonth = true))

        assertEquals(AppendOutcome.Appended("2026-09"), outcome)
        assertTrue("\"title\":\"2026-09\"" in requests.single { it.url.encodedPath.endsWith(":batchUpdate") }.bodyText)
    }

    @Test
    fun findDuplicate_reportsTheTabWithoutAppending() = runTest {
        val gateway = gateway { request ->
            val path = request.url.encodedPath
            when {
                path.endsWith("/sheet-1") -> json("""{"sheets":[{"properties":{"title":"Faturas"}}]}""")
                path.endsWith("%211%3A1") -> json("""{"values":[["Data","ATCUD"]]}""")
                path.endsWith("%21B2%3AB") -> json("""{"values":[["aajfjmm9-4821"]]}""")
                else -> error("Unexpected request ${request.method.value} $path")
            }
        }

        assertEquals("Faturas", gateway.findDuplicate("sheet-1", sampleInvoice(), AppendRules()))
        assertEquals(null, gateway.findDuplicate("sheet-1", sampleInvoice(), AppendRules(skipDuplicateInvoices = false)))
    }

    @Test
    fun uploadFile_sendsAMultipartRelatedBodyIntoTheFolder() = runTest {
        val gateway = gateway { json("""{"id":"file-9","webViewLink":"https://drive.google.com/file/d/file-9/view"}""") }

        val uploaded = gateway.uploadFile("folder-1", "2026-09-18 Continente.jpg", "image/jpeg", byteArrayOf(1, 2, 3))

        assertEquals("file-9", uploaded.id)
        val request = requests.single()
        assertEquals("multipart", request.url.parameters["uploadType"])
        assertTrue(request.body.contentType.toString().startsWith("multipart/related; boundary="))
        val body = (request.body as io.ktor.http.content.ByteArrayContent).bytes().decodeToString()
        assertTrue("\"parents\":[\"folder-1\"]" in body, body)
        assertTrue("Content-Type: image/jpeg" in body)
    }

    @Test
    fun listFolders_queriesDriveForFolders() = runTest {
        val gateway = gateway { json("""{"files":[{"id":"f","name":"Faturas"}]}""") }

        assertEquals("Faturas", gateway.listFolders().single().name)
        assertTrue("application/vnd.google-apps.folder" in requests.single().url.parameters["q"].orEmpty())
    }

    @Test
    fun calls_retryOnceWithAFreshTokenAfter401() = runTest {
        val gateway = gateway { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer stale") json("{}", HttpStatusCode.Unauthorized)
            else json("""{"files":[{"id":"a","name":"Despesas","modifiedTime":"2026-09-18T10:00:00Z"}]}""")
        }

        val list = gateway.listSpreadsheets()

        assertEquals("Despesas", list.single().name)
        assertEquals(listOf(false, true), tokensIssued)
        assertTrue("mimeType='application/vnd.google-apps.spreadsheet'" in requests.first().url.parameters["q"].orEmpty())
    }

    @Test
    fun errors_areMappedToSheetsExceptions() = runTest {
        val gateway = gateway { json("""{"error":{"code":404,"message":"Requested entity was not found."}}""", HttpStatusCode.NotFound) }

        val error = assertFailsWith<SheetsException> { gateway.appendInvoice("gone", sampleInvoice(), AppendRules()) }

        assertEquals(SheetsException.Kind.NOT_FOUND, error.kind)
    }

    @Test
    fun createSpreadsheet_addsTheHeaderRow() = runTest {
        val gateway = gateway { request ->
            if (request.method == HttpMethod.Post) json("""{"spreadsheetId":"new-id","properties":{"title":"Faturas 2026"}}""")
            else json("{}")
        }

        val created = gateway.createSpreadsheet("Faturas 2026")

        assertEquals("new-id", created.id)
        val headerWrite = requests.single { it.method == HttpMethod.Put }
        assertTrue(headerWrite.url.encodedPath.endsWith("/new-id/values/%27Faturas%27%21A1"), headerWrite.url.encodedPath)
    }
}
