package org.neteinstein.snap2sheet.domain.sheets

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import org.neteinstein.snap2sheet.domain.model.Invoice

/** One spreadsheet cell: numbers stay numbers so the sheet can sum them. */
sealed interface CellValue {
    data class Text(val value: String) : CellValue
    data class Number(val value: Double) : CellValue
}

/** The columns Fatura knows how to fill, with the header aliases it recognises for each. */
enum class InvoiceColumn(val defaultHeader: String, vararg aliases: String) {
    DATE("Data", "date", "data da fatura", "invoice date", "data documento"),
    MERCHANT("Fornecedor", "merchant", "comerciante", "empresa", "vendor", "supplier", "loja", "store"),
    NIF_EMITENTE("NIF Emitente", "nif fornecedor", "nif do emitente", "issuer nif", "vendor nif", "nif"),
    NIF_ADQUIRENTE("NIF Adquirente", "nif cliente", "nif do adquirente", "customer nif", "buyer nif"),
    DOCUMENT_TYPE("Tipo de Documento", "tipo", "document type", "type", "tipo documento"),
    DOCUMENT_NUMBER("N.º Documento", "n documento", "numero documento", "nº documento", "document number", "invoice number", "numero", "fatura"),
    ATCUD("ATCUD"),
    TAX_BASE("Base Tributável", "base", "base tributavel", "tax base", "net", "subtotal", "valor sem iva"),
    VAT("IVA", "vat", "tax", "imposto", "impostos"),
    TOTAL("Total", "total com iva", "amount", "valor", "montante"),
    RECEIPT("Comprovativo", "receipt", "link", "ficheiro", "file", "anexo", "attachment", "fatura digital", "documento"),
    ;

    val normalizedAliases: Set<String> = (listOf(defaultHeader) + aliases).map(::normalizeHeader).toSet()

    fun valueOf(invoice: Invoice): CellValue = when (this) {
        DATE -> CellValue.Text(invoice.issueDate?.toString().orEmpty())
        MERCHANT -> CellValue.Text(invoice.merchantName)
        NIF_EMITENTE -> CellValue.Text(invoice.nifEmitente)
        NIF_ADQUIRENTE -> CellValue.Text(invoice.nifAdquirente)
        DOCUMENT_TYPE -> CellValue.Text(invoice.documentType)
        DOCUMENT_NUMBER -> CellValue.Text(invoice.documentNumber)
        ATCUD -> CellValue.Text(invoice.atcud)
        TAX_BASE -> CellValue.Number(invoice.taxBase)
        VAT -> CellValue.Number(invoice.vat)
        TOTAL -> CellValue.Number(invoice.total)
        RECEIPT -> CellValue.Text(invoice.driveFileLink.orEmpty())
    }

    companion object {
        fun forHeader(header: String): InvoiceColumn? {
            val normalized = normalizeHeader(header)
            if (normalized.isEmpty()) return null
            return entries.firstOrNull { normalized in it.normalizedAliases }
        }
    }
}

/**
 * How an [Invoice] becomes a spreadsheet row — which tab it goes to, what the header row is, and
 * which cell holds which field. Kept free of any HTTP so it's shared by the Google Sheets and the
 * gateway and unit-tested on its own.
 */
object InvoiceSheetLayout {

    const val DEFAULT_TAB = "Faturas"

    val defaultHeaders: List<String> = InvoiceColumn.entries.map { it.defaultHeader }

    /** `2026-09` for [monthlyTabs], otherwise `null` meaning "the spreadsheet's first tab". */
    fun monthlyTabFor(invoice: Invoice, monthlyTabs: Boolean, fallbackDate: LocalDate): String? {
        if (!monthlyTabs) return null
        val date = invoice.issueDate ?: fallbackDate
        return "${date.year}-${date.month.number.toString().padStart(2, '0')}"
    }

    /**
     * The row to append. With [matchByHeader] and an existing header row, each header gets the
     * field it names (unknown headers get an empty cell, so the user's own columns survive);
     * otherwise the fields go in [defaultHeaders] order.
     */
    fun rowFor(invoice: Invoice, headers: List<String>, matchByHeader: Boolean): List<CellValue> {
        if (!matchByHeader || headers.none { InvoiceColumn.forHeader(it) != null }) {
            return InvoiceColumn.entries.map { it.valueOf(invoice) }
        }
        val row = headers.map { header -> InvoiceColumn.forHeader(header)?.valueOf(invoice) ?: CellValue.Text("") }
        return row.dropLastWhile { it == CellValue.Text("") }
    }

    /** The Drive file name for an invoice's photo: `2026-09-18 Continente FS 2026-004821.jpg`. */
    fun photoFileName(invoice: Invoice, extension: String): String {
        val parts = listOfNotNull(
            invoice.issueDate?.toString(),
            invoice.merchantName.trim().ifEmpty { null } ?: invoice.nifEmitente.ifEmpty { null },
            invoice.documentNumber.trim().ifEmpty { null },
        )
        val base = parts.joinToString(" ").ifEmpty { "Fatura" }
        return base.replace(Regex("[\\\\/:*?\"<>|]"), "-").replace(Regex("\\s+"), " ").take(120) + "." + extension
    }

    /** Zero-based index of the ATCUD column, or null when the sheet doesn't have one. */
    fun atcudColumnIndex(headers: List<String>, matchByHeader: Boolean): Int? {
        if (!matchByHeader || headers.none { InvoiceColumn.forHeader(it) != null }) return InvoiceColumn.ATCUD.ordinal
        return headers.indexOfFirst { InvoiceColumn.forHeader(it) == InvoiceColumn.ATCUD }.takeIf { it >= 0 }
    }

    fun isDuplicate(invoice: Invoice, existingAtcudColumn: List<String>): Boolean {
        val atcud = invoice.atcud.trim()
        if (atcud.isEmpty() || atcud == "0") return false
        return existingAtcudColumn.any { it.trim().equals(atcud, ignoreCase = true) }
    }

    /**
     * Text starting with `=`, `+`, `-` or `@` would be evaluated as a formula when appended with
     * `USER_ENTERED` — and a QR payload is third-party input. A leading apostrophe keeps it text.
     */
    fun escapeText(value: String): String = if (value.isNotEmpty() && value[0] in "=+-@") "'$value" else value

    /** Spreadsheet column letter for a zero-based index: 0 → A, 25 → Z, 26 → AA. */
    fun columnLetter(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, 'A' + rem)
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    /** A1 range prefix for [tab], quoted so names with spaces or dashes work: `'2026-09'!`. */
    fun quotedTab(tab: String): String = "'" + tab.replace("'", "''") + "'"

}

internal fun normalizeHeader(value: String): String {
    val folded = buildString {
        for (c in value.lowercase()) {
            append(
                when (c) {
                    'á', 'à', 'â', 'ã', 'ä' -> 'a'
                    'é', 'è', 'ê', 'ë' -> 'e'
                    'í', 'ì', 'î', 'ï' -> 'i'
                    'ó', 'ò', 'ô', 'õ', 'ö', 'º', '°' -> 'o'
                    'ú', 'ù', 'û', 'ü' -> 'u'
                    'ç' -> 'c'
                    else -> c
                }
            )
        }
    }
    return folded.replace(Regex("[^a-z0-9]+"), " ").trim()
}
