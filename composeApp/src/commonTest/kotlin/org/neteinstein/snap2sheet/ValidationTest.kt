package org.neteinstein.snap2sheet

import kotlinx.datetime.LocalDate
import org.neteinstein.snap2sheet.domain.model.Invoice
import org.neteinstein.snap2sheet.domain.model.InvoiceStatus
import org.neteinstein.snap2sheet.domain.validation.InvoiceValidator
import org.neteinstein.snap2sheet.domain.validation.Nif
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

fun sampleInvoice(
    merchantName: String = "Continente",
    nifEmitente: String = "500100209",
    atcud: String = "AAJFJMM9-4821",
    issueDate: LocalDate? = LocalDate(2026, 9, 18),
    taxBase: Double = 14.98,
    vat: Double = 3.44,
    total: Double = 18.42,
    warnings: List<String> = emptyList(),
) = Invoice(
    id = "inv-1",
    merchantName = merchantName,
    documentType = "FS",
    documentNumber = "FS 2026/004821",
    issueDate = issueDate,
    atcud = atcud,
    nifEmitente = nifEmitente,
    nifAdquirente = "999999990",
    taxBase = taxBase,
    vat = vat,
    total = total,
    status = InvoiceStatus.NEEDS_REVIEW,
    scannedAtEpochMillis = 0L,
    warnings = warnings,
)

class ValidationTest {

    @Test
    fun nif_checksTheMod11CheckDigit() {
        assertTrue(Nif.isValid("500100209"))
        assertTrue(Nif.isValid("999 999 990"))
        assertFalse(Nif.isValid("500100200"))
        assertFalse(Nif.isValid("12345"))
        assertFalse(Nif.isValid("50010020a"))
    }

    @Test
    fun nif_formatsNineDigitsInGroupsOfThree() {
        assertEquals("500 100 209", Nif.format("500100209"))
        assertEquals("ES-B123", Nif.format("ES-B123"))
    }

    @Test
    fun validator_acceptsAConsistentInvoice() {
        assertEquals(emptyList(), InvoiceValidator.warnings(sampleInvoice()))
    }

    @Test
    fun validator_flagsEachProblem() {
        val warnings = InvoiceValidator.warnings(
            sampleInvoice(merchantName = " ", nifEmitente = "500100200", atcud = "0", issueDate = null, total = 20.00)
        )

        assertEquals(5, warnings.size, warnings.joinToString())
    }

    @Test
    fun allWarnings_keepsTheScanWarningsAndDeduplicates() {
        val invoice = sampleInvoice(nifEmitente = "500100200")
        val fieldWarning = InvoiceValidator.warnings(invoice).single()

        val all = InvoiceValidator.allWarnings(invoice, listOf("cancelled", fieldWarning))

        assertEquals(listOf("cancelled", fieldWarning), all)
    }
}
