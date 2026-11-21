package com.deskflow.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import com.deskflow.app.R

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Pairing : Screen("pairing")
    data object Main : Screen("main")
}

sealed class BottomTab(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector
) {
    data object Home : BottomTab("tab_home", R.string.nav_home, Icons.Default.Home)
    data object Focus : BottomTab("tab_focus", R.string.nav_focus, Icons.Default.Timer)
    data object Timetable : BottomTab("tab_timetable", R.string.nav_timetable, Icons.Default.CalendarMonth)
    data object Control : BottomTab("tab_control", R.string.nav_control, Icons.Default.Tune)
    data object Spotify : BottomTab("tab_spotify", R.string.nav_spotify, Icons.Default.MusicNote)

    companion object {
        val items: List<BottomTab>
            get() = listOf(Focus, Control, Home, Spotify, Timetable)
    }
}

data class NavScreenItem(
    val route: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val icon: ImageVector
)

object AppScreens {
    const val ROUTE_HOME = "tab_home"
    const val ROUTE_FOCUS = "tab_focus"
    const val ROUTE_CONTROL = "tab_control"
    const val ROUTE_SPOTIFY = "tab_spotify"
    const val ROUTE_TIMETABLE = "tab_timetable"
    const val ROUTE_CALENDAR = "screen_calendar"
    const val ROUTE_DASHBOARD = "screen_dashboard"
    const val ROUTE_ACTIVITY = "screen_activity"
    const val ROUTE_SETTINGS = "screen_settings"
    const val ROUTE_MORSE = "screen_morse"

    val allScreens = listOf(
        NavScreenItem(ROUTE_HOME, R.string.nav_home, R.string.app_subtitle, Icons.Default.Home),
        NavScreenItem(ROUTE_FOCUS, R.string.nav_focus, R.string.card_focus_title, Icons.Default.Timer),
        NavScreenItem(ROUTE_CONTROL, R.string.nav_control, R.string.card_control_title, Icons.Default.Tune),
        NavScreenItem(ROUTE_SPOTIFY, R.string.nav_spotify, R.string.card_spotify_title, Icons.Default.MusicNote),
        NavScreenItem(ROUTE_TIMETABLE, R.string.nav_timetable, R.string.timetable_title, Icons.Default.CalendarMonth),
        NavScreenItem(ROUTE_CALENDAR, R.string.nav_calendar, R.string.calendar_desc, Icons.Default.EventNote),
        NavScreenItem(ROUTE_MORSE, R.string.morse_title, R.string.morse_subtitle, Icons.Default.GraphicEq),
        NavScreenItem(ROUTE_DASHBOARD, R.string.nav_dashboard, R.string.card_dashboard_desc, Icons.Default.Dashboard),
        NavScreenItem(ROUTE_ACTIVITY, R.string.nav_activity, R.string.card_activity_desc, Icons.Default.History),
        NavScreenItem(ROUTE_SETTINGS, R.string.nav_settings, R.string.settings_nav_position_title, Icons.Default.Settings)
    )
}
