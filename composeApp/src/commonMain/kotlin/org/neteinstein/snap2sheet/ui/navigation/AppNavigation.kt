package org.neteinstein.snap2sheet.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.koin.compose.koinInject
import org.neteinstein.snap2sheet.data.repository.AccountRepository
import org.neteinstein.snap2sheet.data.repository.FolderRepository
import org.neteinstein.snap2sheet.data.repository.SettingsRepository
import org.neteinstein.snap2sheet.data.repository.SpreadsheetRepository
import org.neteinstein.snap2sheet.ui.screens.history.HistoryScreen
import org.neteinstein.snap2sheet.ui.screens.home.HomeScreen
import org.neteinstein.snap2sheet.ui.screens.photo.PhotoScreen
import org.neteinstein.snap2sheet.ui.screens.review.ReviewScreen
import org.neteinstein.snap2sheet.ui.screens.scan.ScanScreen
import org.neteinstein.snap2sheet.ui.screens.settings.SettingsScreen
import org.neteinstein.snap2sheet.ui.screens.signin.SignInScreen
import org.neteinstein.snap2sheet.ui.screens.targets.SaveTargetsMode
import org.neteinstein.snap2sheet.ui.screens.targets.SaveTargetsScreen
import org.neteinstein.snap2sheet.ui.screens.welcome.WelcomeScreen

/**
 * The journey: Welcome → Connect Google → Setup (default spreadsheet + Drive folder) → Home →
 * Scan QR → Photo of the invoice → Review → (Save targets for this invoice) → Home, while the
 * save runs in the background. Plus History and Settings.
 */
sealed class Screen(val route: String) {
    data object Welcome : Screen("welcome")
    data object SignIn : Screen("signin")

    /** First launch: pick the default spreadsheet and Drive folder. */
    data object Setup : Screen("setup")
    data object Home : Screen("home")
    data object Scan : Screen("scan")
    data object Photo : Screen("photo")
    data object Review : Screen("review")

    /** Where the invoice under review goes — defaults preselected, overridable for this invoice. */
    data object SaveTargets : Screen("targets/save")

    /** Changing the defaults from Settings. */
    data object Defaults : Screen("targets/defaults")
    data object History : Screen("history")
    data object Settings : Screen("settings")
}

@Composable
fun AppNavigation(
    accountRepository: AccountRepository = koinInject(),
    settingsRepository: SettingsRepository = koinInject(),
    spreadsheetRepository: SpreadsheetRepository = koinInject(),
    folderRepository: FolderRepository = koinInject(),
) {
    val navController = rememberNavController()
    val account by accountRepository.account.collectAsStateWithLifecycle()
    fun hasDefaults() = spreadsheetRepository.selected.value != null && folderRepository.selected.value != null

    // Decided once: returning users skip onboarding, signed-in users land on Home (or finish setup).
    val startDestination = remember {
        when {
            accountRepository.account.value != null -> if (hasDefaults()) Screen.Home.route else Screen.Setup.route
            settingsRepository.onboardingCompleted.value -> Screen.SignIn.route
            else -> Screen.Welcome.route
        }
    }

    // Signing out (from Settings, or a revoked session) drops the user back at the sign-in screen.
    LaunchedEffect(account) {
        val route = navController.currentBackStackEntry?.destination?.route
        if (account == null && route != null && route != Screen.Welcome.route && route != Screen.SignIn.route) {
            navController.navigate(Screen.SignIn.route) { popUpTo(0) { inclusive = true } }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(onContinue = {
                settingsRepository.setOnboardingCompleted(true)
                navController.navigate(Screen.SignIn.route)
            })
        }

        composable(Screen.SignIn.route) {
            SignInScreen(
                onBack = if (navController.previousBackStackEntry != null) ({ navController.popBackStack() }) else null,
                onSignedIn = {
                    if (hasDefaults()) navController.goHome()
                    else navController.navigate(Screen.Setup.route) { popUpTo(0) { inclusive = true } }
                },
            )
        }

        composable(Screen.Setup.route) {
            SaveTargetsScreen(mode = SaveTargetsMode.SETUP, onBack = null, onDone = { navController.goHome() })
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onScan = { navController.navigate(Screen.Scan.route) },
                onHistory = { navController.navigate(Screen.History.route) },
                onSettings = { navController.navigate(Screen.Settings.route) },
            )
        }

        composable(Screen.Scan.route) {
            ScanScreen(
                onBack = { navController.popBackStack() },
                onDraftReady = {
                    navController.navigate(Screen.Photo.route) { popUpTo(Screen.Scan.route) { inclusive = true } }
                },
            )
        }

        composable(Screen.Photo.route) {
            PhotoScreen(
                onBack = { navController.popIfCurrent(Screen.Photo) },
                onContinue = {
                    // Coming back from Review to change the photo: return to it rather than stacking another.
                    if (!navController.popBackStack(Screen.Review.route, inclusive = false)) {
                        navController.navigate(Screen.Review.route)
                    }
                },
            )
        }

        composable(Screen.Review.route) {
            ReviewScreen(
                onBack = { navController.popIfCurrent(Screen.Review) },
                onChangePhoto = { navController.navigate(Screen.Photo.route) },
                onChooseDestination = { navController.navigate(Screen.SaveTargets.route) },
                onSaved = { navController.goHome() },
            )
        }

        composable(Screen.SaveTargets.route) {
            SaveTargetsScreen(
                mode = SaveTargetsMode.SAVE,
                onBack = { navController.popIfCurrent(Screen.SaveTargets) },
                onDone = { navController.goHome() },
            )
        }

        composable(Screen.Defaults.route) {
            SaveTargetsScreen(
                mode = SaveTargetsMode.DEFAULTS,
                onBack = { navController.popIfCurrent(Screen.Defaults) },
                onDone = { navController.popIfCurrent(Screen.Defaults) },
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onChangeDefaults = { navController.navigate(Screen.Defaults.route) },
            )
        }
    }
}

/** Pops [screen] only while it's on top — guards against two callbacks both navigating back. */
private fun NavHostController.popIfCurrent(screen: Screen) {
    if (currentBackStackEntry?.destination?.route == screen.route) popBackStack()
}

/** Back to Home as the only entry on the stack — after sign-in/setup, and after a save is queued. */
private fun NavHostController.goHome() {
    if (!popBackStack(Screen.Home.route, inclusive = false)) {
        navigate(Screen.Home.route) { popUpTo(0) { inclusive = true } }
    }
}
