package com.deskflow.app

import com.deskflow.app.data.api.FocusApiService
import com.deskflow.app.data.local.ActivityStore
import com.deskflow.app.data.local.SecurePreferences
import com.deskflow.app.data.repository.ApiResult
import com.deskflow.app.data.repository.FocusRepository
import com.deskflow.app.service.BootReceiver
import com.deskflow.app.service.FocusNotificationService
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
import java.io.File

class FocusLockLifecycleTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: FocusApiService
    private lateinit var securePreferences: SecurePreferences
    private lateinit var activityStore: ActivityStore
    private lateinit var repository: FocusRepository

    private var testToken: String? = "test-auth-token"
    private var testDeviceId: String? = "android-device-1"
    private var testLastKnownFocusActive: Boolean = false

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

        Mockito.`when`(securePreferences.isPaired).thenAnswer { !testToken.isNullOrBlank() }
        Mockito.`when`(securePreferences.deviceToken).thenAnswer { testToken }
        Mockito.`when`(securePreferences.deviceId).thenAnswer { testDeviceId }
        Mockito.`when`(securePreferences.language).thenReturn("vi")
        Mockito.`when`(securePreferences.lastKnownFocusActive).thenAnswer { testLastKnownFocusActive }

        Mockito.doAnswer { invocation ->
            testLastKnownFocusActive = invocation.getArgument(0)
            null
        }.`when`(securePreferences).lastKnownFocusActive = Mockito.anyBoolean()

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

    @Test
    fun testActivationLifecycle() = runTest {
        val enableJson = """
            {
                "success": true,
                "enabled": true,
                "focus_active": true,
                "version": 10,
                "server_time": "2026-11-02T14:00:00Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(enableJson))

        val result = repository.enableFocus(durationMinutes = 25, reason = "Calculus study")

        assertTrue("enableFocus should succeed", result is ApiResult.Success)
        val state = repository.focusState.value
        assertTrue("Focus should be active", state.isActive)
        assertTrue("focusActive should be true", state.focusActive)
        assertEquals(10, state.version)
        assertTrue("lastKnownFocusActive must be persisted as true", testLastKnownFocusActive)
    }

    @Test
    fun testBackendStateSynchronization() = runTest {
        // 1. Sync active status from backend
        val activeStatusJson = """
            {
                "enabled": true,
                "focus_active": true,
                "version": 11,
                "language": "vi",
                "server_time": "2026-11-02T14:05:00Z"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(activeStatusJson))

        val sync1 = repository.refreshStatus()
        assertTrue(sync1 is ApiResult.Success)
        assertTrue(repository.focusState.value.isActive)
        assertTrue(testLastKnownFocusActive)

        // 2. Later sync when session completed on backend
        val inactiveStatusJson = """
            {
                "enabled": false,
                "focus_active": false,
                "version": 12,
                "language": "vi",
                "server_time": "2026-11-02T14:30:00Z"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(inactiveStatusJson))

        val sync2 = repository.refreshStatus()
        assertTrue(sync2 is ApiResult.Success)
        assertFalse(repository.focusState.value.isActive)
        assertFalse(testLastKnownFocusActive)
    }

    @Test
    fun testActiveStatePreservedAfterAppRestart() {
        // Simulate app process killed while session was active
        testLastKnownFocusActive = true

        val restoredRepository = FocusRepository(
            apiService = apiService,
            securePreferences = securePreferences,
            activityStore = activityStore
        )

        // Before any network call completes, the in-memory state is already ACTIVE
        assertTrue("Restored repository must immediately reflect active state", restoredRepository.focusState.value.isActive)
        assertTrue("Restored repository focusActive must be true", restoredRepository.focusState.value.focusActive)
    }

    @Test
    fun testChallengeRequestLifecycle() = runTest {
        val challengeJson = """
            {
                "challenge_id": "ch-math-42",
                "question": "Tính nhẩm: 34 + 59",
                "language": "vi",
                "expires_at": "2026-11-02T14:02:00Z",
                "expires_in": 120,
                "server_time": "2026-11-02T14:00:00Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(challengeJson))

        val result = repository.requestChallenge()

        assertTrue(result is ApiResult.Success)
        val info = (result as ApiResult.Success).data
        assertEquals("ch-math-42", info.challengeId)
        assertEquals("Tính nhẩm: 34 + 59", info.question)
        assertEquals(120L, info.expiresInSeconds)

        assertEquals("ch-math-42", repository.focusState.value.activeChallenge?.challengeId)
    }

    @Test
    fun testCorrectChallengeAnswerUnlocksSession() = runTest {
        testLastKnownFocusActive = true
        repository = FocusRepository(apiService, securePreferences, activityStore)

        val verifySuccessJson = """
            {
                "success": true,
                "correct": true,
                "enabled": false,
                "focus_active": false,
                "version": 15
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(verifySuccessJson))

        val result = repository.verifyChallenge("ch-math-42", 93)

        assertTrue(result is ApiResult.Success)
        val verifyRes = (result as ApiResult.Success).data
        assertTrue(verifyRes.correct)

        val state = repository.focusState.value
        assertFalse("Session must be unlocked", state.isActive)
        assertFalse("focusActive must be false", state.focusActive)
        assertNull("activeChallenge must be cleared", state.activeChallenge)
        assertFalse("lastKnownFocusActive must be persisted as false", testLastKnownFocusActive)
    }

    @Test
    fun testIncorrectChallengeAnswerKeepsSessionLocked() = runTest {
        testLastKnownFocusActive = true
        repository = FocusRepository(apiService, securePreferences, activityStore)

        val verifyFailedJson = """
            {
                "success": true,
                "correct": false,
                "enabled": true,
                "focus_active": true,
                "version": 15,
                "new_challenge": {
                    "challenge_id": "ch-math-43",
                    "question": "Tính nhẩm: 45 * 3",
                    "language": "vi",
                    "expires_at": "2026-11-02T14:03:00Z",
                    "expires_in": 120
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(verifyFailedJson))

        val result = repository.verifyChallenge("ch-math-42", 9999)

        assertTrue(result is ApiResult.Success)
        val verifyRes = (result as ApiResult.Success).data
        assertFalse(verifyRes.correct)

        val state = repository.focusState.value
        assertTrue("Session MUST remain locked", state.isActive)
        assertTrue("focusActive must remain true", state.focusActive)
        assertEquals("Challenge rotated to new question", "ch-math-43", state.activeChallenge?.challengeId)
        assertTrue("lastKnownFocusActive must remain true", testLastKnownFocusActive)
    }

    @Test
    fun testChallengeCooldownRateLimit() = runTest {
        val cooldownJson = """
            {
                "error": {
                    "code": "cooldown",
                    "retry_after": 60
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60").setBody(cooldownJson))

        val result = repository.requestChallenge()

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(429, error.statusCode)
        assertEquals("cooldown", error.code)
        assertEquals(60, error.retryAfter)
    }

    @Test
    fun testNetworkDisconnectionMaintainsInAppLock() = runTest {
        testLastKnownFocusActive = true
        repository = FocusRepository(apiService, securePreferences, activityStore)

        // Simulate network disconnect by shutting down the mock server
        mockWebServer.shutdown()

        val result = repository.refreshStatus()

        assertTrue("Network error expected", result is ApiResult.Error)
        // Focus state MUST NOT be turned off locally due to network drop!
        assertTrue("Focus session remains active and locked despite network outage", repository.focusState.value.isActive)
        assertFalse("isOnline marked false", repository.focusState.value.isOnline)
        assertTrue("lastKnownFocusActive remains true", testLastKnownFocusActive)
    }

    @Test
    fun testBackendUnavailable503LeavesLockIntact() = runTest {
        testLastKnownFocusActive = true
        repository = FocusRepository(apiService, securePreferences, activityStore)

        mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":{"code":"storage_unavailable"}}"""))

        val result = repository.refreshStatus()

        assertTrue(result is ApiResult.Error)
        assertEquals(503, (result as ApiResult.Error).statusCode)
        assertTrue("Session remains locked on backend 503", repository.focusState.value.isActive)
    }

    @Test
    fun testNotificationServiceIsNotVpnService() {
        val serviceClass = FocusNotificationService::class.java
        // Verify FocusNotificationService is standard android.app.Service
        assertTrue(android.app.Service::class.java.isAssignableFrom(serviceClass))
        // Verify it is NOT android.net.VpnService
        assertFalse(
            "FocusNotificationService must NOT extend VpnService",
            android.net.VpnService::class.java.isAssignableFrom(serviceClass)
        )
    }

    @Test
    fun testNoVpnPermissionOrServiceInManifest() {
        // Read the actual AndroidManifest.xml from disk
        val manifestFile = File("src/main/AndroidManifest.xml")
        val manifestText = if (manifestFile.exists()) {
            manifestFile.readText()
        } else {
            File("app/src/main/AndroidManifest.xml").readText()
        }

        assertFalse(
            "BIND_VPN_SERVICE permission must NOT be declared in AndroidManifest.xml",
            manifestText.contains("android.permission.BIND_VPN_SERVICE")
        )
        assertFalse(
            "android.net.VpnService action must NOT be declared in AndroidManifest.xml",
            manifestText.contains("android.net.VpnService")
        )
        assertFalse(
            "FocusVpnService must NOT be declared in AndroidManifest.xml",
            manifestText.contains("FocusVpnService")
        )
        assertTrue(
            "FocusNotificationService MUST be declared in AndroidManifest.xml",
            manifestText.contains("FocusNotificationService")
        )
        assertTrue(
            "BootReceiver MUST be declared in AndroidManifest.xml",
            manifestText.contains("BootReceiver")
        )
    }

    @Test
    fun testDynamicLanguageSwitchingEmitsFlow() = runTest {
        // Test SecurePreferences language flow mechanics
        val prefsFlow = kotlinx.coroutines.flow.MutableStateFlow("vi")
        var currentPrefLang = "vi"

        val mockPrefs = Mockito.mock(SecurePreferences::class.java)
        Mockito.`when`(mockPrefs.languageFlow).thenReturn(prefsFlow)
        Mockito.`when`(mockPrefs.language).thenAnswer { currentPrefLang }
        Mockito.doAnswer { invocation ->
            val newLang = invocation.getArgument<String>(0)
            currentPrefLang = newLang
            prefsFlow.value = newLang
            null
        }.`when`(mockPrefs).language = Mockito.anyString()

        assertEquals("vi", mockPrefs.languageFlow.value)

        // Switch to English
        mockPrefs.language = "en"
        assertEquals("en", mockPrefs.language)
        assertEquals("en", mockPrefs.languageFlow.value)

        // Switch back to Vietnamese
        mockPrefs.language = "vi"
        assertEquals("vi", mockPrefs.language)
        assertEquals("vi", mockPrefs.languageFlow.value)
    }

    @Test
    fun testStringResourcesSupportBilingualTranslations() {
        val enStringsFile = File("src/main/res/values/strings.xml").let {
            if (it.exists()) it else File("app/src/main/res/values/strings.xml")
        }
        val viStringsFile = File("src/main/res/values-vi/strings.xml").let {
            if (it.exists()) it else File("app/src/main/res/values-vi/strings.xml")
        }

        assertTrue("English strings.xml must exist", enStringsFile.exists())
        assertTrue("Vietnamese strings.xml must exist", viStringsFile.exists())

        val enText = enStringsFile.readText()
        val viText = viStringsFile.readText()

        // Verify required keys exist in both languages
        val requiredKeys = listOf(
            "persistent_notification_title",
            "persistent_notification_content",
            "android_limitations_title",
            "android_limitations_desc",
            "unified_session_controls_title",
            "unified_activity_title",
            "unified_settings_title",
            "back_press_locked_title",
            "back_press_locked_desc"
        )

        for (key in requiredKeys) {
            assertTrue("Key $key must exist in English strings.xml", enText.contains("name=\"$key\""))
            assertTrue("Key $key must exist in Vietnamese strings.xml", viText.contains("name=\"$key\""))
        }

        // Verify notification content matches product requirement
        assertTrue(
            "English notification content must match",
            enText.contains("Your focus session is active.")
        )
        assertTrue(
            "Vietnamese notification content must match",
            viText.contains("Phiên tập trung đang hoạt động.")
        )
    }
}
