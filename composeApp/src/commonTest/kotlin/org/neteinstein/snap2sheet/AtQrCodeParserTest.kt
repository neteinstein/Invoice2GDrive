package org.neteinstein.snap2sheet

import kotlinx.datetime.LocalDate
import org.neteinstein.snap2sheet.domain.qr.AtQrCodeParser
import org.neteinstein.snap2sheet.domain.qr.QrParseResult
import org.neteinstein.snap2sheet.testing.SAMPLE_QR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AtQrCodeParserTest {

    private fun parseOk(payload: String) = assertIs<QrParseResult.Success>(AtQrCodeParser.parse(payload))

    @Test
    fun parse_readsEveryFieldTheSheetNeeds() {
        val result = parseOk(SAMPLE_QR)

        with(result.data) {
            assertEquals("500100209", nifEmitente)
            assertEquals("999999990", nifAdquirente)
            assertEquals("PT", acquirerCountry)
            assertEquals("FS", documentType)
            assertEquals(LocalDate(2026, 9, 18), issueDate)
            assertEquals("FS 2026/004821", documentNumber)
            assertEquals("AAJFJMM9-4821", atcud)
            assertEquals(14.98, taxBase)
            assertEquals(3.44, vat)
            assertEquals(18.42, total)
        }
        assertEquals(emptyList(), result.warnings)
    }

    @Test
    fun parse_sumsTaxesAcrossFiscalRegionsWhenNIsMissing() {
        val payload = "A:500100209*B:999999990*C:PT*D:FT*E:N*F:20260101*G:FT A/1*H:X-1*" +
            "I1:PT*I3:10.00*I4:0.60*I7:10.00*I8:2.30*J1:PT-AC*J7:10.00*J8:1.60*O:34.50"

        val result = parseOk(payload)

        assertEquals(4.50, result.data.vat)
        assertEquals(30.00, result.data.taxBase)
    }

    @Test
    fun parse_acceptsDecimalCommasAndSurroundingWhitespace() {
        val result = parseOk("  A:500100209*D:FS*F:20260918*G:FS 1/1*N:1,15*O:6,15  ")

        assertEquals(6.15, result.data.total)
        assertEquals(5.00, result.data.taxBase)
    }

    @Test
    fun parse_warnsAboutCancelledDocuments() {
        val result = parseOk(SAMPLE_QR.replace("E:N", "E:A"))

        assertTrue(result.warnings.any { "cancelled" in it })
    }

    @Test
    fun parse_warnsWhenTheVatBreakdownDoesNotMatchTheTotalTaxes() {
        val result = parseOk(SAMPLE_QR.replace("I8:3.44", "I8:3.00"))

        assertTrue(result.warnings.any { "VAT breakdown" in it })
    }

    @Test
    fun parse_rejectsQrCodesThatAreNotInvoices() {
        assertIs<QrParseResult.Failure>(AtQrCodeParser.parse("https://example.com"))
        assertIs<QrParseResult.Failure>(AtQrCodeParser.parse(""))
    }

    @Test
    fun parse_rejectsPayloadsMissingMandatoryFields() {
        val result = assertIs<QrParseResult.Failure>(AtQrCodeParser.parse(SAMPLE_QR.replace("*O:18.42", "")))

        assertTrue("total" in result.reason)
    }

    @Test
    fun parse_rejectsImpossibleDates() {
        assertIs<QrParseResult.Failure>(AtQrCodeParser.parse(SAMPLE_QR.replace("F:20260918", "F:20261341")))
    }
}
