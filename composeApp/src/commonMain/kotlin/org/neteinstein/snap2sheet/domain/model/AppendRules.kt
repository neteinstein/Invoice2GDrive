package org.neteinstein.snap2sheet.domain.model

import kotlinx.serialization.Serializable

/** How a saved invoice is appended to the destination spreadsheet. */
@Serializable
data class AppendRules(
    val matchColumnsByHeader: Boolean = true,
    val skipDuplicateInvoices: Boolean = true,
    val newSheetTabEachMonth: Boolean = false,
)
