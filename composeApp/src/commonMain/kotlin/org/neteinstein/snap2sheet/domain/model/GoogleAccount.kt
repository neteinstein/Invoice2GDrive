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
        /** Initials from a display name ("Pedro Almeida" → "PA"), falling back to the email's local part. */
        fun initialsFor(name: String?, email: String): String {
            val words = name?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
            val fromName = when {
                words.size >= 2 -> "${words.first().first()}${words.last().first()}"
                words.size == 1 -> words.first().take(2)
                else -> null
            }
            val fromEmail = email.substringBefore('@').split('.', '_', '-').filter { it.isNotEmpty() }.let { parts ->
                if (parts.size >= 2) "${parts[0].first()}${parts[1].first()}" else parts.firstOrNull()?.take(2)
            }
            return (fromName ?: fromEmail ?: "?").uppercase()
        }
    }
}
