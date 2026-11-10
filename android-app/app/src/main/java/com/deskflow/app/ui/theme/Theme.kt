package com.deskflow.app.ui.theme

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

private val DarkColorScheme = darkColorScheme(
    primary = WarmGoldPrimary,
    onPrimary = WarmGoldOnPrimary,
    primaryContainer = WarmGoldContainer,
    onPrimaryContainer = WarmGoldOnContainer,
    secondary = WarmGoldSecondary,
    onSecondary = WarmGoldOnSecondary,
    secondaryContainer = WarmGoldSecondaryContainer,
    onSecondaryContainer = WarmGoldOnSecondaryContainer,
    tertiary = WarmCyanTertiary,
    onTertiary = WarmCyanOnTertiary,
    tertiaryContainer = WarmCyanTertiaryContainer,
    onTertiaryContainer = WarmCyanOnTertiaryContainer,
    background = WarmDarkBackground,
    onBackground = WarmCreamText,
    surface = WarmDarkSurface,
    onSurface = WarmCreamText,
    surfaceVariant = WarmDarkSurfaceVariant,
    onSurfaceVariant = WarmMutedBeige,
    surfaceDim = WarmDarkSurfaceDim,
    surfaceBright = WarmDarkSurfaceBright,
    surfaceContainerLowest = WarmDarkSurfaceContainerLowest,
    surfaceContainerLow = WarmDarkSurfaceContainerLow,
    surfaceContainer = WarmDarkSurfaceContainer,
    surfaceContainerHigh = WarmDarkSurfaceContainerHigh,
    surfaceContainerHighest = WarmDarkSurfaceContainerHighest,
    outline = WarmDarkOutline,
    outlineVariant = WarmDarkOutlineVariant,
    error = WarmAccentRed,
    onError = Color.White,
    errorContainer = WarmDarkErrorContainer,
    onErrorContainer = WarmDarkOnErrorContainer
)

private val LightColorScheme = lightColorScheme(
    primary = WarmLightPrimary,
    onPrimary = WarmLightOnPrimary,
    primaryContainer = WarmLightPrimaryContainer,
    onPrimaryContainer = WarmLightOnPrimaryContainer,
    secondary = WarmLightSecondary,
    onSecondary = WarmLightOnSecondary,
    secondaryContainer = WarmLightSecondaryContainer,
    onSecondaryContainer = WarmLightOnSecondaryContainer,
    tertiary = WarmLightTertiary,
    onTertiary = WarmLightOnTertiary,
    tertiaryContainer = WarmLightTertiaryContainer,
    onTertiaryContainer = WarmLightOnTertiaryContainer,
    background = WarmLightBackground,
    onBackground = WarmLightText,
    surface = WarmLightSurface,
    onSurface = WarmLightText,
    surfaceVariant = WarmLightSurfaceVariant,
    onSurfaceVariant = WarmLightTextSecondary,
    surfaceDim = WarmLightSurfaceDim,
    surfaceBright = WarmLightSurfaceBright,
    surfaceContainerLowest = WarmLightSurfaceContainerLowest,
    surfaceContainerLow = WarmLightSurfaceContainerLow,
    surfaceContainer = WarmLightSurfaceContainer,
    surfaceContainerHigh = WarmLightSurfaceContainerHigh,
    surfaceContainerHighest = WarmLightSurfaceContainerHighest,
    outline = WarmLightOutline,
    outlineVariant = WarmLightOutlineVariant,
    error = WarmLightError,
    onError = Color.White,
    errorContainer = WarmLightErrorContainer,
    onErrorContainer = WarmLightOnErrorContainer
)

/**
 * Pure, deterministic evaluator for Scheduled Dark Mode.
 * Supports same-day ranges (e.g. 08:00 -> 18:00), overnight spans (e.g. 20:00 -> 06:00),
 * equal start/end handling, and exact boundary delay computation.
 */
object ScheduledDarkModeEvaluator {

    fun parseMinutes(timeStr: String, fallbackMinutes: Int): Int {
        val parts = timeStr.trim().split(":")
        if (parts.size != 2) return fallbackMinutes
        val h = parts[0].toIntOrNull() ?: return fallbackMinutes
        val m = parts[1].toIntOrNull() ?: return fallbackMinutes
        if (h !in 0..23 || m !in 0..59) return fallbackMinutes
        return h * 60 + m
    }

    fun isScheduledDarkActive(
        nowMinutes: Int,
        startStr: String,
        endStr: String,
        fallbackWhenEqual: Boolean = false
    ): Boolean {
        val startMin = parseMinutes(startStr, 20 * 60)
        val endMin = parseMinutes(endStr, 6 * 60)
        val cur = ((nowMinutes % 1440) + 1440) % 1440

        return when {
            startMin == endMin -> fallbackWhenEqual
            startMin < endMin -> cur in startMin until endMin
            else -> cur >= startMin || cur < endMin // Overnight range e.g. 20:00 -> 06:00
        }
    }

    fun isScheduledDarkActiveNow(
        startStr: String,
        endStr: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
        fallbackWhenEqual: Boolean = false
    ): Boolean {
        val now = LocalTime.now(zoneId)
        val nowMinutes = now.hour * 60 + now.minute
        return isScheduledDarkActive(nowMinutes, startStr, endStr, fallbackWhenEqual)
    }

    /**
     * Computes milliseconds until the next start or end transition boundary,
     * capped to a maximum polling interval so clock/timezone shifts are caught rapidly.
     */
    fun millisUntilNextBoundary(
        now: ZonedDateTime,
        startStr: String,
        endStr: String
    ): Long {
        val startMin = parseMinutes(startStr, 20 * 60)
        val endMin = parseMinutes(endStr, 6 * 60)
        if (startMin == endMin) return 30_000L

        val startLocal = LocalTime.of(startMin / 60, startMin % 60)
        val endLocal = LocalTime.of(endMin / 60, endMin % 60)

        val candidates = listOf(
            now.with(startLocal),
            now.with(endLocal),
            now.plusDays(1).with(startLocal),
            now.plusDays(1).with(endLocal)
        ).filter { it.isAfter(now) }

        val nextBoundary = candidates.minByOrNull { Duration.between(now, it).toMillis() }
            ?: return 15_000L

        val deltaMs = Duration.between(now, nextBoundary).toMillis().coerceAtLeast(500L)
        return deltaMs.coerceAtMost(15_000L)
    }

    fun resolveDarkTheme(
        themePreference: String,
        systemDark: Boolean,
        startStr: String,
        endStr: String,
        nowMinutes: Int
    ): Boolean {
        return when (themePreference.trim().lowercase()) {
            "light" -> false
            "dark" -> true
            "scheduled" -> isScheduledDarkActive(
                nowMinutes = nowMinutes,
                startStr = startStr,
                endStr = endStr,
                fallbackWhenEqual = false
            )
            else -> systemDark
        }
    }
}

@Composable
fun DeskFlowTheme(
    themeMode: String = "system",
    themePreference: String = themeMode,
    darkModeStart: String = "20:00",
    darkModeEnd: String = "06:00",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val systemDark = isSystemInDarkTheme()

    // Reactive clock tick counter updated on boundary timers, system time/timezone broadcasts, and lifecycle resume
    var timeTick by remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(context, themePreference) {
        if (themePreference != "scheduled") {
            onDispose { }
        } else {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    timeTick = System.currentTimeMillis()
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
            context.registerReceiver(receiver, filter)
            onDispose {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Exception) {
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                timeTick = System.currentTimeMillis()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(themePreference, darkModeStart, darkModeEnd) {
        if (themePreference == "scheduled") {
            while (isActive) {
                val now = ZonedDateTime.now(ZoneId.systemDefault())
                val waitMs = ScheduledDarkModeEvaluator.millisUntilNextBoundary(
                    now = now,
                    startStr = darkModeStart,
                    endStr = darkModeEnd
                )
                delay(waitMs)
                timeTick = System.currentTimeMillis()
            }
        }
    }

    val darkTheme = remember(themePreference, systemDark, darkModeStart, darkModeEnd, timeTick) {
        val now = LocalTime.now(ZoneId.systemDefault())
        val nowMinutes = now.hour * 60 + now.minute
        ScheduledDarkModeEvaluator.resolveDarkTheme(
            themePreference = themePreference,
            systemDark = systemDark,
            startStr = darkModeStart,
            endStr = darkModeEnd,
            nowMinutes = nowMinutes
        )
    }

    val baseScheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Smooth color transition when switching between Light and Dark themes
    val animatedBackground by animateColorAsState(
        targetValue = baseScheme.background,
        animationSpec = tween(durationMillis = 280),
        label = "bgColor"
    )
    val animatedSurface by animateColorAsState(
        targetValue = baseScheme.surface,
        animationSpec = tween(durationMillis = 280),
        label = "surfaceColor"
    )
    val colorScheme = baseScheme.copy(
        background = animatedBackground,
        surface = animatedSurface
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.surface.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = DeskFlowShapes,
        content = content
    )
}
