package edu.git.gams.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = BrandBlue,
    onPrimary = TextPrimary,
    primaryContainer = ButtonPrimary,
    onPrimaryContainer = TextPrimary,
    secondary = BrandAccent,
    onSecondary = TextPrimary,
    background = BackgroundTop,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = InputSurface,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder
)

private val LightColorScheme = darkColorScheme(
    primary = BrandBlue,
    onPrimary = TextPrimary,
    primaryContainer = ButtonPrimary,
    onPrimaryContainer = TextPrimary,
    secondary = BrandAccent,
    onSecondary = TextPrimary,
    background = BackgroundTop,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = InputSurface,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder
)

@Composable
fun GAMSTheme(
    darkTheme: Boolean = true, // GAMS dark midnight aesthetic
    dynamicColor: Boolean = false, // Keep custom curated branding
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> DarkColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = BackgroundTop.toArgb()
                window.navigationBarColor = BackgroundBottom.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}