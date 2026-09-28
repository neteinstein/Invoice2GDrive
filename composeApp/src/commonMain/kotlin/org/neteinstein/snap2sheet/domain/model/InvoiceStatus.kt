package org.neteinstein.snap2sheet.domain.model

import kotlinx.serialization.Serializable

/** Where a scanned invoice stands in the save pipeline (photo → Drive folder, row → spreadsheet). */
@Serializable
enum class InvoiceStatus {
    /** Waiting for, or in the middle of, a background save — including automatic retries. */
    QUEUED,

    /** Row appended (and photo uploaded) with no validation warnings. */
    SYNCED,

    /** Saved, but despite validation warnings — worth a second look. */
    NEEDS_REVIEW,

    /** Skipped: the ATCUD was already in the spreadsheet. Nothing was uploaded. */
    DUPLICATE,

    /** Gave up (offline for too long, session expired, sheet deleted…); can be retried by hand. */
    FAILED;

    val isFinished: Boolean get() = this != QUEUED
}
