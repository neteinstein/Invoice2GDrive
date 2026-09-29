package org.neteinstein.snap2sheet.domain.validation

import org.neteinstein.snap2sheet.platform.tr

import org.neteinstein.snap2sheet.domain.model.DocumentType
import org.neteinstein.snap2sheet.domain.model.Invoice
import kotlin.math.abs

/**
 * Field-level sanity checks run on an invoice before it's saved — on the scanned values and on
 * whatever the user edited on the Review screen. None of them block saving: an invoice with
 * warnings is saved as [org.neteinstein.snap2sheet.domain.model.InvoiceStatus.NEEDS_REVIEW].
 */
object InvoiceValidator {

    fun warnings(invoice: Invoice): List<String> = buildList {
        if (invoice.merchantName.isBlank()) add(tr("No merchant name.", "Sem nome do fornecedor."))
        if (!Nif.isValid(invoice.nifEmitente)) add(tr("The issuer's NIF (${invoice.nifEmitente}) fails the check digit.", "O NIF do emitente (${invoice.nifEmitente}) não passa a validação do dígito de controlo."))
        val customer = Nif.normalize(invoice.nifAdquirente)
        if (customer.length == 9 && customer.all { it.isDigit() } && !Nif.isValid(customer)) {
            add(tr("The customer's NIF (${invoice.nifAdquirente}) fails the check digit.", "O NIF do adquirente (${invoice.nifAdquirente}) não passa a validação do dígito de controlo."))
        }
        if (!DocumentType.isKnown(invoice.documentType)) add(tr("Unknown document type \"${invoice.documentType}\".", "Tipo de documento desconhecido \"${invoice.documentType}\"."))
        if (invoice.issueDate == null) add(tr("No invoice date.", "Sem data da fatura."))
        if (invoice.atcud.isBlank() || invoice.atcud == "0") add(tr("No ATCUD, so duplicates can't be detected.", "Sem ATCUD, pelo que não é possível detetar duplicados."))
        if (abs(invoice.taxBase + invoice.vat - invoice.total) > 0.011) add(tr("Base + IVA doesn't add up to the total.", "Base + IVA não corresponde ao total."))
    }

    /** Warnings stored on the saved invoice: the scan's own plus the field-level ones, deduplicated. */
    fun allWarnings(invoice: Invoice, sourceWarnings: List<String>): List<String> = (sourceWarnings + warnings(invoice)).distinct()
}
