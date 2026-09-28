package org.neteinstein.snap2sheet.domain.model

import kotlinx.serialization.Serializable

/** The Google account Fatura saves invoices on behalf of. */
@Serializable
data class GoogleAccount(
    val email: String,
    val initials: String,
    /** A local-only demo account: spreadsheets live in memory and nothing reaches Google. */
    val isDemo: Boolean = false,
) {
    companion object {
        /** Derives a 1-2 letter avatar label from a display name, falling back to the email. */
        fun initialsFrom(displayName: String?, email: String): String {
            val source = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: email
            val words = source.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                words.size >= 2 -> "${words[0].first()}${words[1].first()}".uppercase()
                words.size == 1 -> words[0].take(2).uppercase()
                else -> "?"
            }
        }
    }
}
