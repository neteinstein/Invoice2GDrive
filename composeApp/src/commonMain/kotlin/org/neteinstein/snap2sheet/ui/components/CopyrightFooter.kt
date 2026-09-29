package org.neteinstein.snap2sheet.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import org.neteinstein.snap2sheet.ui.theme.FaturaColors

private const val AUTHOR_URL = "https://www.pedrovicente.pt"

/** "Copyright Pedro Vicente", the name linking to the author's site. */
@Composable
fun CopyrightFooter(modifier: Modifier = Modifier) {
    val linkColor = FaturaColors.Accent
    val text = buildAnnotatedString {
        append("Copyright ")
        withLink(
            LinkAnnotation.Url(
                AUTHOR_URL,
                TextLinkStyles(SpanStyle(color = linkColor, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)),
            ),
        ) { append("Pedro Vicente") }
    }
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = FaturaColors.Muted, textAlign = TextAlign.Center)
    }
}
