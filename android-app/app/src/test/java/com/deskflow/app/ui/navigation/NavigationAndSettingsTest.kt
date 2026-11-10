package com.deskflow.app.ui.navigation

import android.content.Context
import android.content.SharedPreferences
import com.deskflow.app.data.local.SecurePreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class NavigationAndSettingsTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private val inMemoryStorage = mutableMapOf<String, Any?>()

    @Before
    fun setUp() {
        inMemoryStorage.clear()
        mockContext = Mockito.mock(Context::class.java)
        mockPrefs = Mockito.mock(SharedPreferences::class.java)
        mockEditor = Mockito.mock(SharedPreferences.Editor::class.java)

        Mockito.`when`(mockContext.getSharedPreferences(Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(mockPrefs)

        Mockito.`when`(mockPrefs.edit()).thenReturn(mockEditor)

        Mockito.`when`(mockPrefs.getString(Mockito.anyString(), Mockito.nullable(String::class.java)))
            .thenAnswer { inv ->
                val key = inv.getArgument<String>(0)
                val def = inv.getArgument<String?>(1)
                inMemoryStorage[key] as? String ?: def
            }

        Mockito.`when`(mockPrefs.getBoolean(Mockito.anyString(), Mockito.anyBoolean()))
            .thenAnswer { inv ->
                val key = inv.getArgument<String>(0)
                val def = inv.getArgument<Boolean>(1)
                inMemoryStorage[key] as? Boolean ?: def
            }

        Mockito.`when`(mockEditor.putString(Mockito.anyString(), Mockito.nullable(String::class.java)))
            .thenAnswer { inv ->
                val key = inv.getArgument<String>(0)
                val value = inv.getArgument<String?>(1)
                if (value != null) inMemoryStorage[key] = value else inMemoryStorage.remove(key)
                mockEditor
            }

        Mockito.`when`(mockEditor.putBoolean(Mockito.anyString(), Mockito.anyBoolean()))
            .thenAnswer { inv ->
                val key = inv.getArgument<String>(0)
                val value = inv.getArgument<Boolean>(1)
                inMemoryStorage[key] = value
                mockEditor
            }

        Mockito.doNothing().`when`(mockEditor).apply()
    }

    @Test
    fun testHomeIsStrictlyCenteredAtPosition3Of5() {
        val items = BottomTab.items
        assertEquals("Primary navigation must have exactly 5 destinations", 5, items.size)
        assertEquals("Position 1 (index 0) must be Focus", BottomTab.Focus, items[0])
        assertEquals("Position 2 (index 1) must be Control", BottomTab.Control, items[1])
        assertEquals("Position 3 (index 2) MUST be Home (Dead Center)", BottomTab.Home, items[2])
        assertEquals("Position 4 (index 3) must be Spotify", BottomTab.Spotify, items[3])
        assertEquals("Position 5 (index 4) must be Timetable", BottomTab.Timetable, items[4])
    }

    @Test
    fun testNavigationPositionPersistence() {
        val prefs = SecurePreferences(mockContext)

        // Default position should be "bottom"
        assertEquals("bottom", prefs.navBarPosition)
        assertEquals("bottom", prefs.navBarPositionFlow.value)

        // Switch to "top"
        prefs.navBarPosition = "top"
        assertEquals("top", prefs.navBarPosition)
        assertEquals("top", prefs.navBarPositionFlow.value)

        // Re-read with a new instance to simulate app restart
        val restoredPrefs = SecurePreferences(mockContext)
        assertEquals("top", restoredPrefs.navBarPosition)

        // Switch back to "bottom"
        prefs.navBarPosition = "bottom"
        assertEquals("bottom", prefs.navBarPosition)
    }

    @Test
    fun testAppThemePersistence() {
        val prefs = SecurePreferences(mockContext)

        // Default theme should be "system"
        assertEquals("system", prefs.appTheme)
        assertEquals("system", prefs.appThemeFlow.value)

        // Switch to "dark"
        prefs.appTheme = "dark"
        assertEquals("dark", prefs.appTheme)
        assertEquals("dark", prefs.appThemeFlow.value)

        // Switch to "light"
        prefs.appTheme = "light"
        assertEquals("light", prefs.appTheme)
        assertEquals("light", prefs.appThemeFlow.value)

        // Simulate app restart
        val restoredPrefs = SecurePreferences(mockContext)
        assertEquals("light", restoredPrefs.appTheme)
    }

    @Test
    fun testWaterdropIndexResolution() {
        val items = BottomTab.items
        val focusIndex = items.indexOfFirst { it.route == BottomTab.Focus.route }
        val controlIndex = items.indexOfFirst { it.route == BottomTab.Control.route }
        val homeIndex = items.indexOfFirst { it.route == BottomTab.Home.route }
        val spotifyIndex = items.indexOfFirst { it.route == BottomTab.Spotify.route }
        val timetableIndex = items.indexOfFirst { it.route == BottomTab.Timetable.route }

        assertEquals(0, focusIndex)
        assertEquals(1, controlIndex)
        assertEquals(2, homeIndex) // Center
        assertEquals(3, spotifyIndex)
        assertEquals(4, timetableIndex)
    }
}
