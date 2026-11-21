package com.deskflow.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.deskflow.app.R
import com.deskflow.app.data.local.ActivityStore
import com.deskflow.app.data.local.SecurePreferences
import com.deskflow.app.data.repository.FocusRepository
import com.deskflow.app.ui.activity.ActivityScreen
import com.deskflow.app.ui.control.ControlScreen
import com.deskflow.app.ui.dashboard.DashboardScreen
import com.deskflow.app.ui.focus.FocusScreen
import com.deskflow.app.ui.home.HomeScreen
import com.deskflow.app.ui.morse.MorseConverterScreen
import com.deskflow.app.ui.navigation.AppScreens
import com.deskflow.app.ui.navigation.BottomTab
import com.deskflow.app.ui.calendar.CalendarScreen
import com.deskflow.app.ui.navigation.WaterdropNavigationBar
import com.deskflow.app.ui.settings.SettingsScreen
import com.deskflow.app.ui.spotify.SpotifyScreen
import com.deskflow.app.ui.timetable.TimetableScreen
import com.deskflow.app.ui.i18n.AppStrings

private fun getScreenOrder(route: String): Int = when (route) {
    AppScreens.ROUTE_FOCUS -> 0
    AppScreens.ROUTE_CONTROL -> 1
    AppScreens.ROUTE_HOME -> 2
    AppScreens.ROUTE_SPOTIFY -> 3
    AppScreens.ROUTE_TIMETABLE -> 4
    AppScreens.ROUTE_CALENDAR -> 5
    AppScreens.ROUTE_DASHBOARD -> 6
    AppScreens.ROUTE_ACTIVITY -> 7
    AppScreens.ROUTE_SETTINGS -> 8
    AppScreens.ROUTE_MORSE -> 9
    else -> 2
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeskFlowUnifiedScreen(
    repository: FocusRepository,
    securePreferences: SecurePreferences,
    activityStore: ActivityStore,
    onUnpaired: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Default selected tab is Home (center item in 5-item NavigationBar)
    var selectedTab by rememberSaveable { mutableStateOf(AppScreens.ROUTE_HOME) }
    var showBackBlockedDialog by remember { mutableStateOf(false) }
    var showScreenSwitcher by remember { mutableStateOf(false) }

    val focusState by repository.focusState.collectAsState()
    val navBarPos by securePreferences.navBarPositionFlow.collectAsState()
    val isTopNav = navBarPos == "top"

    // Intercept system back button
    BackHandler(enabled = selectedTab != AppScreens.ROUTE_HOME || focusState.isActive) {
        if (selectedTab != AppScreens.ROUTE_HOME) {
            selectedTab = AppScreens.ROUTE_HOME
        } else if (focusState.isActive) {
            showBackBlockedDialog = true
        }
    }

    if (showBackBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showBackBlockedDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.back_press_locked_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.back_press_locked_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBackBlockedDialog = false
                        selectedTab = AppScreens.ROUTE_FOCUS
                    }
                ) {
                    Text(stringResource(R.string.nav_focus))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackBlockedDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showScreenSwitcher) {
        ScreenSwitcherModalSheet(
            currentRoute = selectedTab,
            onSelectScreen = { route ->
                selectedTab = route
            },
            onDismiss = { showScreenSwitcher = false }
        )
    }

    // Global bottom-right screen-switcher FAB has been removed per Section 4.
    // Screen switching is preserved via the TopBar grid button and the 5-tab Material 3 NavigationBar.
    Scaffold(
        topBar = {
            if (isTopNav) {
                WaterdropNavigationBar(
                    tabs = BottomTab.items,
                    selectedRoute = selectedTab,
                    onTabSelected = { tab ->
                        selectedTab = tab.route
                    },
                    isTop = true,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                )
            }
        },
        bottomBar = {
            if (!isTopNav) {
                WaterdropNavigationBar(
                    tabs = BottomTab.items,
                    selectedRoute = selectedTab,
                    onTabSelected = { tab ->
                        selectedTab = tab.route
                    },
                    isTop = false,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val forward = getScreenOrder(targetState) >= getScreenOrder(initialState)
                (slideInHorizontally(animationSpec = tween(280)) { if (forward) it / 3 else -it / 3 } +
                 fadeIn(animationSpec = tween(280))).togetherWith(
                    slideOutHorizontally(animationSpec = tween(240)) { if (forward) -it / 4 else it / 4 } +
                    fadeOut(animationSpec = tween(200))
                )
            },
            label = "ScreenTransition",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { targetRoute ->
            when (targetRoute) {
                AppScreens.ROUTE_HOME -> HomeScreen(
                    repository = repository,
                    securePreferences = securePreferences,
                    onNavigateToFocus = { selectedTab = AppScreens.ROUTE_FOCUS },
                    onNavigateToTimetable = { selectedTab = AppScreens.ROUTE_TIMETABLE },
                    onNavigateToControl = { selectedTab = AppScreens.ROUTE_CONTROL },
                    onNavigateToSpotify = { selectedTab = AppScreens.ROUTE_SPOTIFY },
                    onNavigateToDashboard = { selectedTab = AppScreens.ROUTE_DASHBOARD },
                    onNavigateToActivity = { selectedTab = AppScreens.ROUTE_ACTIVITY },
                    onNavigateToSettings = { selectedTab = AppScreens.ROUTE_SETTINGS },
                    onOpenScreenSwitcher = { showScreenSwitcher = true },
                    onUnpair = onUnpaired
                )
                AppScreens.ROUTE_FOCUS -> FocusScreen(
                    repository = repository,
                    onOpenScreenSwitcher = { showScreenSwitcher = true }
                )
                AppScreens.ROUTE_TIMETABLE -> TimetableScreen(
                    repository = repository,
                    onOpenScreenSwitcher = { showScreenSwitcher = true }
                )
                AppScreens.ROUTE_CALENDAR -> CalendarScreen(
                    repository = repository,
                    onOpenScreenSwitcher = { showScreenSwitcher = true }
                )
                AppScreens.ROUTE_CONTROL -> ControlScreen(
                    repository = repository,
                    securePreferences = securePreferences,
                    onNavigateHome = { selectedTab = AppScreens.ROUTE_HOME },
                    onOpenScreenSwitcher = { showScreenSwitcher = true }
                )
                AppScreens.ROUTE_SPOTIFY -> SpotifyScreen(
                    repository = repository,
                    securePreferences = securePreferences,
                    onOpenScreenSwitcher = { showScreenSwitcher = true }
                )
                AppScreens.ROUTE_DASHBOARD -> DashboardScreen(
                    repository = repository,
                    securePreferences = securePreferences,
                    onNavigateToFocusTab = { selectedTab = AppScreens.ROUTE_FOCUS },
                    onOpenScreenSwitcher = { showScreenSwitcher = true }
                )
                AppScreens.ROUTE_ACTIVITY -> ActivityScreen(
                    activityStore = activityStore,
                    onOpenScreenSwitcher = { showScreenSwitcher = true }
                )
                AppScreens.ROUTE_SETTINGS -> SettingsScreen(
                    repository = repository,
                    securePreferences = securePreferences,
                    onBack = { selectedTab = AppScreens.ROUTE_HOME },
                    onUnpaired = onUnpaired,
                    onOpenScreenSwitcher = { showScreenSwitcher = true }
                )
                AppScreens.ROUTE_MORSE -> MorseConverterScreen(
                    repository = repository,
                    onBack = { selectedTab = AppScreens.ROUTE_HOME }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenSwitcherModalSheet(
    currentRoute: String,
    onSelectScreen: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.screen_switcher_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.screen_switcher_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                items(AppScreens.allScreens, key = { it.route }) { item ->
                    val isSelected = currentRoute == item.route
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        border = if (isSelected) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectScreen(item.route)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(item.titleRes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isSelected) {
                                    Text(
                                        text = AppStrings.t("Đang mở"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
