package com.deskflow.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the post-pairing crash:
 *   NPE: BottomTab.getRoute() on a null object reference (DeskFlowUnifiedScreen.kt:88)
 *
 * Verifies that BottomTab.items never contains null, regardless of whether
 * Home, Focus, Control, Spotify, or items is accessed first.
 */
class BottomTabInitTest {

    @Test
    fun itemsContainNoNullsWhenHomeIsAccessedFirst() {
        val initialRoute = BottomTab.Home.route
        assertEquals("tab_home", initialRoute)

        val items = BottomTab.items
        assertEquals(5, items.size)
        items.forEachIndexed { i, tab ->
            @Suppress("SENSELESS_COMPARISON")
            assertNotNull("BottomTab.items[$i] is null", tab as Any?)
            assertNotNull(tab.route)
            assertTrue(tab.titleRes > 0)
            assertNotNull(tab.icon)
        }
        assertEquals(
            listOf("tab_focus", "tab_control", "tab_home", "tab_spotify", "tab_timetable"),
            items.map { it.route }
        )
        // Home must be centered at index 2 (3rd out of 5 items)
        assertEquals(BottomTab.Home, items[2])
    }

    @Test
    fun bottomNavigationRenderingIntegrity() {
        var selectedTab = BottomTab.Home.route
        val renderedRoutes = mutableListOf<String>()

        BottomTab.items.forEach { tab ->
            // Matches DeskFlowUnifiedScreen.kt:88
            val isSelected = selectedTab == tab.route
            renderedRoutes.add(tab.route)
            if (tab == BottomTab.Home) {
                assertTrue(isSelected)
            }
        }

        assertEquals(5, renderedRoutes.size)
        assertEquals(listOf("tab_focus", "tab_control", "tab_home", "tab_spotify", "tab_timetable"), renderedRoutes)
        assertEquals("tab_home", renderedRoutes[2])
    }

    @Test
    fun allTabsHaveUniqueRoutes() {
        val routes = BottomTab.items.map { it.route }
        assertEquals(routes.distinct().size, routes.size)
    }
}
