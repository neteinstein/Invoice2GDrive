package org.neteinstein.snap2sheet.domain.qr

import org.neteinstein.snap2sheet.platform.tr

import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.math.round

/** The fields of a fiscal QR code that end up in the spreadsheet. Amounts are in euros. */
data class ScannedInvoiceData(
    val nifEmitente: String,
    val nifAdquirente: String,
    val acquirerCountry: String,
    val documentType: String,
    val documentStatus: String,
    val issueDate: LocalDate,
    val documentNumber: String,
    val atcud: String,
    /** Everything that isn't tax: `O − N`. */
    val taxBase: Double,
    /** Total taxes (`N`): VAT across every fiscal region plus stamp duty. */
    val vat: Double,
    val total: Double,
)

sealed interface QrParseResult {
    /**
     * [warnings] are the problems only the QR payload can reveal (a cancelled document, a VAT
     * breakdown that doesn't add up); field-level checks live in
     * [org.neteinstein.snap2sheet.domain.validation.InvoiceValidator] so they also cover edits.
     */
    data class Success(val data: ScannedInvoiceData, val warnings: List<String>) : QrParseResult
    data class Failure(val reason: String) : QrParseResult
}

/**
 * Decodes the fiscal QR code printed on every Portuguese invoice since 2022 (Portaria n.º
 * 195/2020 and AT's "Especificações Técnicas Código QR"). The payload is a `*`-separated list
 * of `CODE:value` fields, e.g.
 *
 * ```
 * A:500100200*B:999999990*C:PT*D:FS*E:N*F:20260918*G:FS 2026/004821*H:AAJFJMM9-4821*I1:PT*
 * I7:14.98*I8:3.44*N:3.44*O:18.42*Q:abcd*R:1234
 * ```
 *
 * A (NIF emitente), B (NIF adquirente), C (country), D (document type), E (status), F (date,
 * `YYYYMMDD`), G (document id), H (ATCUD), N (total taxes) and O (grand total) are the fields
 * this app needs; I/J/K (per-region VAT breakdown for PT / Azores / Madeira), L (non-taxable
 * amount) and M (stamp duty) are only used to cross-check the totals.
 */
object AtQrCodeParser {

    private val regionFields = listOf("I", "J", "K")

    fun parse(payload: String): QrParseResult {
        val fields = parseFields(payload.trim())
            ?: return QrParseResult.Failure(tr("This QR code isn't a Portuguese invoice (fatura) code.", "Este código QR não é de uma fatura portuguesa."))

        val missing = listOf("A" to "NIF emitente", "D" to "document type", "F" to "date", "G" to "document number", "O" to "total")
            .filter { (code, _) -> fields[code].isNullOrBlank() }
            .map { it.second }
        if (missing.isNotEmpty()) {
            return QrParseResult.Failure(tr("The QR code is missing: ${missing.joinToString()}.", "Ao código QR falta: ${missing.joinToString()}."))
        }

        val issueDate = parseCompactDate(fields.getValue("F"))
            ?: return QrParseResult.Failure(tr("The QR code's date (${fields["F"]}) isn't valid.", "A data do código QR (${fields["F"]}) não é válida."))
        val total = parseAmount(fields.getValue("O"))
            ?: return QrParseResult.Failure(tr("The QR code's total (${fields["O"]}) isn't a number.", "O total do código QR (${fields["O"]}) não é um número."))
        val vatFields = regionFields.flatMap { r -> listOf("${r}4", "${r}6", "${r}8") }.mapNotNull { fields[it]?.let(::parseAmount) }
        val stampDuty = fields["M"]?.let(::parseAmount) ?: 0.0
        val totalTaxes = fields["N"]?.let(::parseAmount) ?: roundCents(vatFields.sum() + stampDuty)

        val warnings = mutableListOf<String>()
        val nifEmitente = fields.getValue("A")
        val nifAdquirente = fields["B"].orEmpty()
        val country = fields["C"].orEmpty()
        val documentType = fields.getValue("D").uppercase()
        val status = fields["E"].orEmpty().uppercase()
        val atcud = fields["H"].orEmpty()

        if (status == "A") warnings += tr("This document was cancelled (anulado) by the issuer.", "Este documento foi anulado pelo emitente.")
        if (vatFields.isNotEmpty() && abs(vatFields.sum() + stampDuty - totalTaxes) > 0.011) {
            warnings += tr("The VAT breakdown doesn't add up to the total taxes.", "A discriminação do IVA não corresponde ao total de impostos.")
        }
        if (totalTaxes > total + 0.001) warnings += tr("Total taxes exceed the invoice total.", "O total de impostos excede o total da fatura.")

        return QrParseResult.Success(
            data = ScannedInvoiceData(
                nifEmitente = nifEmitente,
                nifAdquirente = nifAdquirente,
                acquirerCountry = country,
                documentType = documentType,
                documentStatus = status,
                issueDate = issueDate,
                documentNumber = fields.getValue("G"),
                atcud = atcud,
                taxBase = roundCents(total - totalTaxes),
                vat = roundCents(totalTaxes),
                total = roundCents(total),
            ),
            warnings = warnings,
        )
    }

    /** Null unless the payload looks like an AT code: `*`-separated `CODE:value` pairs starting with `A:`. */
    private fun parseFields(payload: String): Map<String, String>? {
        if (!payload.startsWith("A:")) return null
        val fields = LinkedHashMap<String, String>()
        for (token in payload.split('*')) {
            val separator = token.indexOf(':')
            if (separator <= 0) continue
            fields[token.substring(0, separator).trim().uppercase()] = token.substring(separator + 1).trim()
        }
        return fields.takeIf { "A" in it }
    }

    private fun parseCompactDate(value: String): LocalDate? {
        if (value.length != 8 || !value.all { it.isDigit() }) return null
        return runCatching {
            LocalDate(value.substring(0, 4).toInt(), value.substring(4, 6).toInt(), value.substring(6, 8).toInt())
        }.getOrNull()
    }

    private fun parseAmount(value: String): Double? = value.replace(',', '.').toDoubleOrNull()

    private fun roundCents(value: Double): Double = round(value * 100) / 100
}
