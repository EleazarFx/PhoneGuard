package mw.phoneguard.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PhoneGuardColorScheme = darkColorScheme(
    primary = GuardTeal,
    onPrimary = BackgroundBlack,
    secondary = TextMuted,
    onSecondary = TextPrimary,
    background = BackgroundBlack,
    onBackground = TextPrimary,
    surface = PanelDark,
    onSurface = TextPrimary,
    surfaceVariant = PanelLight,
    onSurfaceVariant = TextMuted,
    error = AlertRed,
    onError = TextPrimary,
    outline = LineColor
)

@Composable
fun PhoneGuardTheme(
    // We default to dark mode because it's a security app,
    // but allow overriding it if needed later.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = PhoneGuardColorScheme // Force our custom scheme always

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        Surface(
            color = colorScheme.background,
            contentColor = colorScheme.onBackground
        ) {
            content()
        }
    }
}