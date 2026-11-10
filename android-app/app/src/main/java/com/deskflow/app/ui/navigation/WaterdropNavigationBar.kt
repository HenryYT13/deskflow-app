package com.deskflow.app.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Material 3 NavigationBar for DeskFlow's 5 primary destinations:
 * Focus, Control, Home (centered), Spotify, Timetable.
 * Includes animated selection indicator, smooth icon scale/offset transitions,
 * clear labels, and proper touch targets.
 */
@Composable
fun WaterdropNavigationBar(
    tabs: List<BottomTab>,
    selectedRoute: String,
    onTabSelected: (BottomTab) -> Unit,
    isTop: Boolean,
    modifier: Modifier = Modifier
) {
    val selectedIndex = tabs.indexOfFirst { it.route == selectedRoute }.let {
        if (it == -1) 2 else it // Default to center (Home) when on a secondary screen
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        shape = if (isTop) {
            RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
        } else {
            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        },
        modifier = modifier
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selectedIndex

                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.14f else 1.0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMedium,
                        dampingRatio = Spring.DampingRatioMediumBouncy
                    ),
                    label = "NavIconScale_${tab.route}"
                )

                val iconOffsetY by animateDpAsState(
                    targetValue = if (isSelected) (-3).dp else 0.dp,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "NavIconOffset_${tab.route}"
                )

                val labelColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 200),
                    label = "NavLabelColor_${tab.route}"
                )

                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onTabSelected(tab) },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = stringResource(tab.titleRes),
                            modifier = Modifier
                                .offset(y = iconOffsetY)
                                .scale(iconScale)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(tab.titleRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = labelColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}
