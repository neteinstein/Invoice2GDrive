package org.neteinstein.snap2sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import org.neteinstein.snap2sheet.domain.model.AppTheme
import org.neteinstein.snap2sheet.platform.showsThemeToggle
import org.neteinstein.snap2sheet.platform.systemPrefersDark
import org.neteinstein.snap2sheet.ui.components.FaturaIcons
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterNotNull
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.neteinstein.snap2sheet.data.repository.SettingsRepository
import org.neteinstein.snap2sheet.ui.MessageCenter
import org.neteinstein.snap2sheet.ui.navigation.AppNavigation
import org.neteinstein.snap2sheet.ui.theme.FaturaColors
import org.neteinstein.snap2sheet.ui.theme.FaturaTheme

@Composable
fun App(
    settingsRepository: SettingsRepository = koinInject(),
    messageCenter: MessageCenter = koinInject(),
) {
    val theme by settingsRepository.theme.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Collected rather than keyed on the value: consuming a message must not cancel its snackbar.
    // Messages arriving while one is shown wait their turn (the latest one wins).
    LaunchedEffect(messageCenter) {
        messageCenter.current.filterNotNull().collect { text ->
            messageCenter.consume()
            snackbarHostState.showSnackbar(text)
        }
    }

    FaturaTheme(theme = theme) {
        Box(modifier = Modifier.fillMaxSize()) {
            AppNavigation()
            if (showsThemeToggle) {
                val dark = when (theme) {
                    AppTheme.LIGHT -> false
                    AppTheme.DARK -> true
                    AppTheme.SYSTEM -> systemPrefersDark()
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(FaturaColors.Surface.copy(alpha = 0.9f))
                        .clickable { settingsRepository.setTheme(if (dark) AppTheme.LIGHT else AppTheme.DARK) },
                ) {
                    if (dark) FaturaIcons.Sun(tint = FaturaColors.Ink) else FaturaIcons.Moon(tint = FaturaColors.Ink)
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                // Clear of the bottom nav bar and primary buttons.
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp),
            ) { data ->
                Snackbar(snackbarData = data, containerColor = FaturaColors.Ink, contentColor = FaturaColors.Surface)
            }
        }
    }
}
