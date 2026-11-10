package com.deskflow.app.ui.navigation

import com.deskflow.app.data.api.FocusApiService
import com.deskflow.app.data.local.ActivityStore
import com.deskflow.app.data.local.SecurePreferences
import com.deskflow.app.data.repository.ApiResult
import com.deskflow.app.data.repository.FocusRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Regression tests covering the complete pairing flow and UI transition:
 * 1. Initial unpaired state -> target is Screen.Pairing.route
 * 2. Successful pairing -> tokens stored, scopes updated, state transitions to paired, target is Screen.Main.route
 * 3. Bottom navigation rendering immediately after pairing -> BottomTab.items contains 4 valid non-null tabs
 * 4. App restart with already-paired device -> starts at Screen.Main.route without crash
 * 5. Invalid pairing code -> remains unpaired, error handled safely
 */
class PairingNavigationFlowTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: FocusApiService
    private lateinit var securePreferences: SecurePreferences
    private lateinit var activityStore: ActivityStore
    private lateinit var repository: FocusRepository

    private var storedToken: String? = null
    private var storedDeviceId: String? = null
    private var storedDeviceName: String = "Test Device"
    private var storedScopes: List<String> = emptyList()
    private var hasCompletedOnboarding: Boolean = true

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FocusApiService::class.java)

        securePreferences = Mockito.mock(SecurePreferences::class.java)
        activityStore = Mockito.mock(ActivityStore::class.java)

        Mockito.`when`(securePreferences.isPaired).thenAnswer { !storedToken.isNullOrBlank() }
        Mockito.`when`(securePreferences.deviceToken).thenAnswer { storedToken }
        Mockito.`when`(securePreferences.deviceId).thenAnswer { storedDeviceId }
        Mockito.`when`(securePreferences.deviceName).thenAnswer { storedDeviceName }
        Mockito.`when`(securePreferences.hasCompletedOnboarding).thenAnswer { hasCompletedOnboarding }
        Mockito.`when`(securePreferences.language).thenReturn("vi")
        Mockito.`when`(securePreferences.scopes).thenAnswer { storedScopes }
        Mockito.`when`(securePreferences.hasScope(Mockito.anyString())).thenAnswer { inv ->
            storedScopes.contains(inv.getArgument(0) as String)
        }

        Mockito.doAnswer { inv ->
            storedToken = inv.getArgument(0)
            null
        }.`when`(securePreferences).deviceToken = Mockito.any()

        Mockito.doAnswer { inv ->
            storedDeviceId = inv.getArgument(0)
            null
        }.`when`(securePreferences).deviceId = Mockito.any()

        Mockito.doAnswer { inv ->
            storedScopes = inv.getArgument(0)
            null
        }.`when`(securePreferences).scopes = Mockito.anyList()

        repository = FocusRepository(
            apiService = apiService,
            securePreferences = securePreferences,
            activityStore = activityStore
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    /**
     * Determines the start destination route exactly as MainActivity does.
     */
    private fun computeStartDestination(prefs: SecurePreferences): String {
        return when {
            !prefs.hasCompletedOnboarding -> Screen.Onboarding.route
            !prefs.isPaired -> Screen.Pairing.route
            else -> Screen.Main.route
        }
    }

    @Test
    fun testInitialUnpairedAppState() {
        // Unpaired state: no token
        storedToken = null
        assertFalse(securePreferences.isPaired)
        assertFalse(repository.isPaired.value)

        // Destination must be Pairing screen
        val destination = computeStartDestination(securePreferences)
        assertEquals(Screen.Pairing.route, destination)
    }

    @Test
    fun testSuccessfulPairingAndTransitionToMain() = runTest {
        // Initially unpaired
        storedToken = null
        assertEquals(Screen.Pairing.route, computeStartDestination(securePreferences))

        // Mock pairing response
        val pairResponse = """
            {
                "success": true,
                "device_id": "dev_12345",
                "device_token": "token_abcde12345",
                "device_name": "Test Phone",
                "platform": "android",
                "scopes": ["focus", "control", "spotify"],
                "paired_at": "2026-11-03T18:00:00Z"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(pairResponse))
        // Mock subsequent status call
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"enabled": false, "version": 1}"""))

        val result = repository.pairDevice("TESTCODE", "Test Phone")
        assertTrue("Pairing must succeed", result is ApiResult.Success)

        // Verify stored state
        assertEquals("token_abcde12345", storedToken)
        assertEquals("dev_12345", storedDeviceId)
        assertEquals(listOf("focus", "control", "spotify"), storedScopes)
        assertTrue(securePreferences.isPaired)
        assertTrue(repository.isPaired.value)

        // Transition destination is now Screen.Main
        val postPairingDestination = computeStartDestination(securePreferences)
        assertEquals(Screen.Main.route, postPairingDestination)

        // Now simulate entering DeskFlowUnifiedScreen:
        // 1. Initial selectedTab
        val initialSelectedTab = BottomTab.Home.route
        assertEquals("tab_home", initialSelectedTab)

        // 2. Render bottom navigation: must contain all 5 tabs, none null, no NPE
        val tabs = BottomTab.items
        assertEquals(5, tabs.size)
        tabs.forEach { tab ->
            assertNotNull(tab)
            assertNotNull(tab.route)
            assertTrue(tab.titleRes > 0)
            assertNotNull(tab.icon)
            // Simulated isSelected logic from DeskFlowUnifiedScreen:88
            val isSelected = initialSelectedTab == tab.route
            if (tab == BottomTab.Home) {
                assertTrue(isSelected)
            } else {
                assertFalse(isSelected)
            }
        }
    }

    @Test
    fun testAppRestartWithAlreadyPairedDevice() {
        // Simulate already-paired app startup
        storedToken = "persisted_valid_token"
        storedDeviceId = "dev_persisted"
        storedScopes = listOf("focus", "control", "spotify")

        // In a real app restart, repository is instantiated with already-paired preferences
        val freshRepository = FocusRepository(
            apiService = apiService,
            securePreferences = securePreferences,
            activityStore = activityStore
        )

        assertTrue(securePreferences.isPaired)
        assertTrue(freshRepository.isPaired.value)

        val destination = computeStartDestination(securePreferences)
        assertEquals(Screen.Main.route, destination)

        // BottomTab must be immediately usable
        assertEquals("tab_home", BottomTab.Home.route)
        val tabs = BottomTab.items
        assertEquals(5, tabs.size)
        tabs.forEach { assertNotNull(it.route) }
    }

    @Test
    fun testInvalidPairingResponseDoesNotTransition() = runTest {
        storedToken = null

        // 403 pair_code_invalid
        mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("""
            {"error": {"code": "pair_code_invalid"}}
        """.trimIndent()))

        val result = repository.pairDevice("BADCODE1", "Test Phone")
        assertTrue(result is ApiResult.Error)
        assertEquals("pair_code_invalid", (result as ApiResult.Error).code)

        // Must remain unpaired
        assertNull(storedToken)
        assertFalse(securePreferences.isPaired)
        assertEquals(Screen.Pairing.route, computeStartDestination(securePreferences))
    }
}
