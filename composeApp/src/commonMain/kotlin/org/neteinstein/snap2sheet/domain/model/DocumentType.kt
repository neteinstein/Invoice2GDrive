package org.neteinstein.snap2sheet.domain.model

/** AT document type codes (field `D` of the fiscal QR code) and their Portuguese names. */
object DocumentType {
    private val labels = mapOf(
        "FT" to "Fatura",
        "FS" to "Fatura simplificada",
        "FR" to "Fatura-recibo",
        "ND" to "Nota de débito",
        "NC" to "Nota de crédito",
        "VD" to "Venda a dinheiro",
        "TV" to "Talão de venda",
        "TD" to "Talão de devolução",
        "AA" to "Alienação de ativos",
        "DA" to "Devolução de ativos",
        "RP" to "Prémio ou recibo de prémio",
        "RE" to "Estorno ou recibo de estorno",
        "CS" to "Imputação a co-seguradoras",
        "LD" to "Imputação a co-seguradora líder",
        "RA" to "Resseguro aceite",
        "RC" to "Recibo (IVA de caixa)",
        "RG" to "Outros recibos",
        "CM" to "Consulta de mesa",
        "CC" to "Crédito de consignação",
        "FC" to "Fatura de consignação",
        "FO" to "Folha de obra",
        "NE" to "Nota de encomenda",
        "OR" to "Orçamento",
        "PF" to "Fatura pró-forma",
        "OU" to "Outros",
        "GR" to "Guia de remessa",
        "GT" to "Guia de transporte",
        "GA" to "Guia de movimentação de ativos próprios",
        "GC" to "Guia de consignação",
        "GD" to "Guia de devolução",
    )

    /** "Fatura-recibo (FR)", or just the code when it isn't one AT defines. */
    fun label(code: String): String = labels[code.uppercase()]?.let { "$it (${code.uppercase()})" } ?: code

    fun isKnown(code: String): Boolean = code.uppercase() in labels
}
