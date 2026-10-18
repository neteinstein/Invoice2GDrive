package org.neteinstein.snap2sheet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.neteinstein.snap2sheet.platform.showsThemeToggle
import org.neteinstein.snap2sheet.ui.theme.FaturaColors

/**
 * End padding that keeps a header's trailing content clear of the floating top-right controls (App.kt):
 * the PT/EN language toggle everywhere, plus the theme toggle on web.
 */
val TopEndControlsInset: Dp = if (showsThemeToggle) 116.dp else 68.dp

@Composable
fun FaturaTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = TopEndControlsInset, top = 14.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    modifier = Modifier.size(36.dp).clickable(onClick = onBack),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        FaturaIcons.Back(tint = FaturaColors.Ink)
                    }
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FaturaColors.Ink)
        }
        trailing?.invoke()
    }
}
