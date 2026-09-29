package org.neteinstein.snap2sheet.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** One complete set of brand colors; [FaturaColors] resolves to the light or dark one. */
class FaturaPalette(
    val accent: Color,
    val accentSoft: Color,
    val ink: Color,
    val muted: Color,
    val mutedStrong: Color,
    val subtle: Color,
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val border: Color,
    val borderStrong: Color,
    val divider: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
)

/**
 * Brand tokens lifted directly from the Fatura design (Main/Home/Review/... .dc.html): a cool
 * neutral ground with a single blue accent, plus status colors for the sync/review/failed chips.
 */
val LightPalette = FaturaPalette(
    accent = Color(0xFF2E5AAC),
    accentSoft = Color(0xFFEAF0FB),
    ink = Color(0xFF12151B),
    muted = Color(0xFF5B6472),
    mutedStrong = Color(0xFF8B93A1),
    subtle = Color(0xFF98A0AC),
    background = Color(0xFFF3F4F6),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF8F9FB),
    border = Color(0xFFE4E7ED),
    borderStrong = Color(0xFFC7CBD3),
    divider = Color(0xFFEEF0F3),
    success = Color(0xFF1F8A5F),
    successSoft = Color(0xFFE7F5EE),
    warning = Color(0xFFB7791F),
    warningSoft = Color(0xFFFBF0DF),
    danger = Color(0xFFB42318),
    dangerSoft = Color(0xFFFDEAEA),
)

/** Same roles on a dark ground: text inverts, accents lighten for contrast, "soft" tints go deep. */
val DarkPalette = FaturaPalette(
    accent = Color(0xFF6E97E0),
    accentSoft = Color(0xFF1B2A44),
    ink = Color(0xFFE7E9ED),
    muted = Color(0xFFA6ACB8),
    mutedStrong = Color(0xFF8B93A1),
    subtle = Color(0xFF6F7784),
    background = Color(0xFF101215),
    surface = Color(0xFF181B20),
    surfaceMuted = Color(0xFF20242B),
    border = Color(0xFF31363F),
    borderStrong = Color(0xFF454B56),
    divider = Color(0xFF262A31),
    success = Color(0xFF3FBF8A),
    successSoft = Color(0xFF14322A),
    warning = Color(0xFFE0A84A),
    warningSoft = Color(0xFF3A2E17),
    danger = Color(0xFFF07167),
    dangerSoft = Color(0xFF3A1A17),
)

val LocalFaturaPalette = staticCompositionLocalOf { LightPalette }

/** Theme-aware color tokens; read inside a composable and they follow the active [FaturaTheme]. */
object FaturaColors {
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalFaturaPalette.current.accent
    val AccentSoft: Color @Composable @ReadOnlyComposable get() = LocalFaturaPalette.current.accentSoft

    val Ink: Color @Composable @ReadOnlyComposable get() = LocalFaturaPalette.current.ink
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalFaturaPalette.current.muted
    val MutedStrong: Color @Composable @ReadOnlyComposable get() = LocalFaturaPalette.current.mutedStrong
    val Subtle: Color @Composable @ReadOnlyComposable get() = LocalFaturaPalette.current.subtle

    val Background: Color @Composable @ReadOnlyComposable get() = LocalFaturaPalette.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalFaturaPalette.current.surface
    val SurfaceMuted: Color @Composable @ReadOnlyComposable get() = LocalFaturaPalette.current.surfaceMuted
    val Border: Color @Composable @ReadOnlyComposable get() = LocalFaturaPalette.current.border
    val BorderStrong: Color @Composable @ReadOnlyComposable get() = LocalFaturaPalette.current.borderStrong
    val Divider: Color @Composable @ReadOnlyComposable get() = LocalFaturaPalette.current.divider

    val Success: Color @Composable @ReadOnlyComposable get() = LocalFaturaPalette.current.success
    val SuccessSoft: Color @Composable @ReadOnlyComposable get() = LocalFaturaPalette.current.successSoft
    val Warning: Color @Composable @ReadOnlyComposable get() = LocalFaturaPalette.current.warning
    val WarningSoft: Color @Composable @ReadOnlyComposable get() = LocalFaturaPalette.current.warningSoft
    val Danger: Color @Composable @ReadOnlyComposable get() = LocalFaturaPalette.current.danger
    val DangerSoft: Color @Composable @ReadOnlyComposable get() = LocalFaturaPalette.current.dangerSoft
}
