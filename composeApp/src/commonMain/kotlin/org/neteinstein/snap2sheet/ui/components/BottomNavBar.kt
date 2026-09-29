package org.neteinstein.snap2sheet.ui.components

import org.neteinstein.snap2sheet.platform.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.neteinstein.snap2sheet.ui.theme.FaturaColors

enum class BottomNavTab { HOME, HISTORY, SETTINGS }

private val NAV_ITEM_WIDTH = 64.dp

@Composable
fun BottomNavBar(
    selected: BottomNavTab,
    onHome: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(FaturaColors.Surface)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(88.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(FaturaColors.Border))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                NavItem(tr("Home", "Início"), selected == BottomNavTab.HOME, onHome) { tint -> FaturaIcons.Grid(tint = tint, size = 21.dp) }
                NavItem(tr("History", "Histórico"), selected == BottomNavTab.HISTORY, onHistory) { tint -> FaturaIcons.Clock(tint = tint, size = 21.dp) }
                androidx.compose.foundation.layout.Spacer(Modifier.width(NAV_ITEM_WIDTH))
                NavItem(tr("Settings", "Definições"), selected == BottomNavTab.SETTINGS, onSettings) { tint -> FaturaIcons.Gear(tint = tint, size = 21.dp) }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-26).dp)
                .size(58.dp)
                .background(FaturaColors.Accent, CircleShape)
                .clickable(onClick = onScan),
            contentAlignment = Alignment.Center,
        ) {
            FaturaIcons.Camera(tint = Color.White, size = 24.dp)
        }
    }
}

@Composable
private fun NavItem(label: String, selected: Boolean, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    val tint = if (selected) FaturaColors.Accent else FaturaColors.Subtle
    Column(
        modifier = Modifier.width(NAV_ITEM_WIDTH).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon(tint)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tint,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
