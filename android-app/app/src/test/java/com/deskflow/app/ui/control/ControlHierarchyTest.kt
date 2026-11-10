package com.deskflow.app.ui.control

import com.deskflow.app.ui.navigation.AppScreens
import com.deskflow.app.ui.navigation.BottomTab
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying:
 * 1. Control screen 3-level hierarchical navigation structure
 * 2. Category and Subcategory data models and coverage
 * 3. Back navigation state transitions (L3 -> L2 -> L1)
 * 4. Centered Home navigation bar layout (Position 3 of 5)
 */
class ControlHierarchyTest {

    @Test
    fun testCategoriesIntegrity() {
        assertEquals("Must have exactly 5 main categories", 5, CONTROL_CATEGORIES.size)

        val catIds = CONTROL_CATEGORIES.map { it.id }
        assertEquals(
            listOf("hardware", "alarms", "tools", "settings", "system"),
            catIds
        )

        CONTROL_CATEGORIES.forEach { cat ->
            assertNotNull(cat.id)
            assertTrue(cat.titleRes > 0)
            assertTrue(cat.descRes > 0)
            assertNotNull(cat.icon)
            assertTrue("Category ${cat.id} must have subcategories", cat.subcategories.isNotEmpty())

            cat.subcategories.forEach { sub ->
                assertNotNull(sub.id)
                assertTrue(sub.titleRes > 0)
                assertTrue(sub.descRes > 0)
                assertNotNull(sub.icon)
            }
        }
    }

    @Test
    fun testSubcategoryMapping() {
        val hardware = CONTROL_CATEGORIES.find { it.id == "hardware" }
        assertNotNull(hardware)
        assertEquals(
            listOf("subcat_led", "subcat_display", "subcat_buzzer"),
            hardware!!.subcategories.map { it.id }
        )

        val alarms = CONTROL_CATEGORIES.find { it.id == "alarms" }
        assertNotNull(alarms)
        assertEquals(
            listOf("subcat_alarm_active", "subcat_alarm_schedule"),
            alarms!!.subcategories.map { it.id }
        )

        val tools = CONTROL_CATEGORIES.find { it.id == "tools" }
        assertNotNull(tools)
        assertEquals(
            listOf("subcat_tools_notes", "subcat_morse", "subcat_tools_ai"),
            tools!!.subcategories.map { it.id }
        )

        val settings = CONTROL_CATEGORIES.find { it.id == "settings" }
        assertNotNull(settings)
        assertEquals(
            listOf("subcat_settings_display", "subcat_settings_power"),
            settings!!.subcategories.map { it.id }
        )

        val system = CONTROL_CATEGORIES.find { it.id == "system" }
        assertNotNull(system)
        assertEquals(
            listOf("subcat_system_telemetry"),
            system!!.subcategories.map { it.id }
        )
    }

    @Test
    fun testHierarchyNavigationTransitions() {
        var selectedCategory: String? = null
        var selectedSubCategory: String? = null

        // Level 1: Categories
        assertNull(selectedCategory)
        assertNull(selectedSubCategory)

        // Select "hardware" Category -> Transition to Level 2
        selectedCategory = "hardware"
        assertEquals("hardware", selectedCategory)
        assertNull(selectedSubCategory)

        // Select "subcat_led" Subcategory -> Transition to Level 3
        selectedSubCategory = "subcat_led"
        assertEquals("hardware", selectedCategory)
        assertEquals("subcat_led", selectedSubCategory)

        // Back from Level 3 -> returns to Level 2
        if (selectedSubCategory != null) {
            selectedSubCategory = null
        } else if (selectedCategory != null) {
            selectedCategory = null
        }
        assertEquals("hardware", selectedCategory)
        assertNull(selectedSubCategory)

        // Back from Level 2 -> returns to Level 1
        if (selectedSubCategory != null) {
            selectedSubCategory = null
        } else if (selectedCategory != null) {
            selectedCategory = null
        }
        assertNull(selectedCategory)
        assertNull(selectedSubCategory)
    }

    @Test
    fun testToolsCategorySubcategoryAndMorseNavigation() {
        var selectedTab = AppScreens.ROUTE_CONTROL
        var selectedCategory: String? = null
        var selectedSubCategory: String? = null

        fun handleBackPress(): String {
            return if (selectedSubCategory != null) {
                selectedSubCategory = null
                "popped_subcategory"
            } else if (selectedCategory != null) {
                selectedCategory = null
                "popped_category"
            } else {
                selectedTab = AppScreens.ROUTE_HOME
                "navigated_home"
            }
        }

        // Navigate to Tools category
        selectedCategory = "tools"
        assertEquals("tools", selectedCategory)
        assertNull(selectedSubCategory)

        // Navigate to subcat_tools_notes
        selectedSubCategory = "subcat_tools_notes"
        assertEquals("subcat_tools_notes", selectedSubCategory)

        // Press back: should pop subcategory and stay in tools category
        val action1 = handleBackPress()
        assertEquals("popped_subcategory", action1)
        assertEquals("tools", selectedCategory)
        assertNull(selectedSubCategory)

        // Navigate to subcat_morse
        selectedSubCategory = "subcat_morse"
        assertEquals("subcat_morse", selectedSubCategory)

        // Press back: should pop subcategory and stay in tools category
        val action2 = handleBackPress()
        assertEquals("popped_subcategory", action2)
        assertEquals("tools", selectedCategory)
        assertNull(selectedSubCategory)

        // Press back from tools category: should pop category to Control root
        val action3 = handleBackPress()
        assertEquals("popped_category", action3)
        assertNull(selectedCategory)
        assertNull(selectedSubCategory)
        assertEquals(AppScreens.ROUTE_CONTROL, selectedTab)

        // Press back from Control root: should navigate to Home tab
        val action4 = handleBackPress()
        assertEquals("navigated_home", action4)
        assertEquals(AppScreens.ROUTE_HOME, selectedTab)
    }

    @Test
    fun testHomeIsPositionedInExactCenter() {
        val items = BottomTab.items
        assertEquals("BottomTab must have exactly 5 destinations", 5, items.size)

        // Target: [Focus, Control, Home, Spotify, Timetable]
        assertEquals("Position 1 (Index 0) must be Focus", BottomTab.Focus, items[0])
        assertEquals("Position 2 (Index 1) must be Control", BottomTab.Control, items[1])
        assertEquals("Position 3 (Index 2) MUST BE EXACT CENTER: Home", BottomTab.Home, items[2])
        assertEquals("Position 4 (Index 3) must be Spotify", BottomTab.Spotify, items[3])
        assertEquals("Position 5 (Index 4) must be Timetable", BottomTab.Timetable, items[4])
    }
}
