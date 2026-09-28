package org.neteinstein.snap2sheet

import kotlinx.datetime.LocalDate
import org.neteinstein.snap2sheet.domain.sheets.CellValue
import org.neteinstein.snap2sheet.domain.sheets.InvoiceColumn
import org.neteinstein.snap2sheet.domain.sheets.InvoiceSheetLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvoiceSheetLayoutTest {

    private val invoice = sampleInvoice()

    @Test
    fun rowFor_usesTheDefaultColumnOrderWithoutHeaderMatching() {
        val row = InvoiceSheetLayout.rowFor(invoice, headers = listOf("Whatever"), matchByHeader = false)

        assertEquals(InvoiceColumn.entries.size, row.size)
        assertEquals(CellValue.Text("2026-09-18"), row[0])
        assertEquals(CellValue.Number(18.42), row[InvoiceColumn.TOTAL.ordinal])
    }

    @Test
    fun rowFor_linksTheUploadedPhoto() {
        val withLink = invoice.copy(driveFileLink = "https://drive.google.com/file/d/x/view")

        val row = InvoiceSheetLayout.rowFor(withLink, listOf("Total", "Comprovativo"), matchByHeader = true)

        assertEquals(CellValue.Text("https://drive.google.com/file/d/x/view"), row[1])
    }

    @Test
    fun photoFileName_isReadableAndSafe() {
        assertEquals("2026-09-18 Continente FS 2026-004821.jpg", InvoiceSheetLayout.photoFileName(invoice, "jpg"))
        assertEquals(
            "500100209 A-B.pdf",
            InvoiceSheetLayout.photoFileName(sampleInvoice(merchantName = "", issueDate = null).copy(documentNumber = "A/B"), "pdf"),
        )
    }

    @Test
    fun rowFor_matchesTheUsersOwnHeadersIgnoringCaseAndAccents() {
        val headers = listOf("Notes", "TOTAL", "data", "Fornecedor", "Numero Documento", "base tributavel", "IVA")

        val row = InvoiceSheetLayout.rowFor(invoice, headers, matchByHeader = true)

        assertEquals(
            listOf(
                CellValue.Text(""),
                CellValue.Number(18.42),
                CellValue.Text("2026-09-18"),
                CellValue.Text("Continente"),
                CellValue.Text("FS 2026/004821"),
                CellValue.Number(14.98),
                CellValue.Number(3.44),
            ),
            row,
        )
    }

    @Test
    fun rowFor_fallsBackToDefaultOrderWhenNoHeaderIsRecognised() {
        val row = InvoiceSheetLayout.rowFor(invoice, listOf("A", "B"), matchByHeader = true)

        assertEquals(InvoiceColumn.entries.size, row.size)
    }

    @Test
    fun atcudColumn() {
        assertEquals(InvoiceColumn.ATCUD.ordinal, InvoiceSheetLayout.atcudColumnIndex(listOf("x"), matchByHeader = false))
        assertEquals(2, InvoiceSheetLayout.atcudColumnIndex(listOf("Data", "Total", "atcud"), matchByHeader = true))
        assertNull(InvoiceSheetLayout.atcudColumnIndex(listOf("Data", "Total"), matchByHeader = true))
    }

    @Test
    fun isDuplicate_comparesAtcudsIgnoringCaseAndBlanks() {
        assertTrue(InvoiceSheetLayout.isDuplicate(invoice, listOf("other", " aajfjmm9-4821 ")))
        assertFalse(InvoiceSheetLayout.isDuplicate(invoice, listOf("other")))
        assertFalse(InvoiceSheetLayout.isDuplicate(sampleInvoice(atcud = "0"), listOf("0")))
    }

    @Test
    fun escapeText_neutralisesFormulaInjection() {
        assertEquals("'=HYPERLINK(\"x\")", InvoiceSheetLayout.escapeText("=HYPERLINK(\"x\")"))
        assertEquals("'-1", InvoiceSheetLayout.escapeText("-1"))
        assertEquals("FS 1/2", InvoiceSheetLayout.escapeText("FS 1/2"))
    }

    @Test
    fun monthlyTab_usesTheInvoiceDateAndFallsBackToToday() {
        val today = LocalDate(2026, 10, 2)
        assertEquals("2026-09", InvoiceSheetLayout.monthlyTabFor(invoice, monthlyTabs = true, fallbackDate = today))
        assertEquals("2026-10", InvoiceSheetLayout.monthlyTabFor(sampleInvoice(issueDate = null), monthlyTabs = true, fallbackDate = today))
        assertNull(InvoiceSheetLayout.monthlyTabFor(invoice, monthlyTabs = false, fallbackDate = today))
    }

    @Test
    fun a1Helpers() {
        assertEquals("A", InvoiceSheetLayout.columnLetter(0))
        assertEquals("Z", InvoiceSheetLayout.columnLetter(25))
        assertEquals("AA", InvoiceSheetLayout.columnLetter(26))
        assertEquals("'Tom''s'", InvoiceSheetLayout.quotedTab("Tom's"))
    }
}
