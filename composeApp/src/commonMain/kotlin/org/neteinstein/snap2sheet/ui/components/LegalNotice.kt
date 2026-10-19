package org.neteinstein.snap2sheet.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import org.neteinstein.snap2sheet.platform.tr
import org.neteinstein.snap2sheet.ui.theme.FaturaColors

private const val TERMS_URL = "https://neteinstein.github.io/Invoice2Sheet/terms.html"
private const val PRIVACY_URL = "https://neteinstein.github.io/Invoice2Sheet/privacy.html"

/** "By continuing you agree to the Terms of Service and Privacy Policy.", both documents linked. */
@Composable
fun LegalNotice(modifier: Modifier = Modifier) {
    val linkStyles = TextLinkStyles(SpanStyle(color = FaturaColors.Accent, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline))
    val text = buildAnnotatedString {
        append(tr("By continuing you agree to the ", "Ao continuar, aceita os "))
        withLink(LinkAnnotation.Url(TERMS_URL, linkStyles)) { append(tr("Terms of Service", "Termos de Serviço")) }
        append(tr(" and ", " e a "))
        withLink(LinkAnnotation.Url(PRIVACY_URL, linkStyles)) { append(tr("Privacy Policy", "Política de Privacidade")) }
        append(".")
    }
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodySmall, color = FaturaColors.Muted, textAlign = TextAlign.Center)
}
