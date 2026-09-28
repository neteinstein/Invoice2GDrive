package org.neteinstein.snap2sheet.domain.model

import kotlinx.serialization.Serializable

/** A Google Drive item Fatura saves to: a spreadsheet invoices are appended to, or a folder photos are uploaded to. */
@Serializable
data class DriveItem(
    val id: String,
    val name: String,
    /** Last modification time as reported by Google Drive, or null when unknown. */
    val modifiedAtEpochMillis: Long? = null,
)
