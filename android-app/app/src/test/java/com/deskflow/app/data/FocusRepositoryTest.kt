package com.deskflow.app.data

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

class FocusRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: FocusApiService
    private lateinit var securePreferences: SecurePreferences
    private lateinit var activityStore: ActivityStore
    private lateinit var repository: FocusRepository

    // In-memory test state for preferences
    private var testToken: String? = null
    private var testDeviceId: String? = null
    private var testDeviceName: String = "Test Phone"
    private var testPairedAt: String? = null
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

        // Mock securePreferences methods
        Mockito.`when`(securePreferences.isPaired).thenAnswer { !testToken.isNullOrBlank() }
        Mockito.`when`(securePreferences.deviceToken).thenAnswer { testToken }
        Mockito.`when`(securePreferences.deviceId).thenAnswer { testDeviceId }
        Mockito.`when`(securePreferences.deviceName).thenAnswer { testDeviceName }
        Mockito.`when`(securePreferences.language).thenReturn("vi")
        Mockito.`when`(securePreferences.lastKnownFocusActive).thenAnswer { testLastKnownFocusActive }

        // Handle setters via doAnswer
        Mockito.doAnswer { invocation ->
            testToken = invocation.getArgument(0)
            null
        }.`when`(securePreferences).deviceToken = Mockito.any()

        Mockito.doAnswer { invocation ->
            testDeviceId = invocation.getArgument(0)
            null
        }.`when`(securePreferences).deviceId = Mockito.any()

        Mockito.doAnswer { invocation ->
            testDeviceName = invocation.getArgument(0)
            null
        }.`when`(securePreferences).deviceName = Mockito.any()

        Mockito.doAnswer { invocation ->
            testPairedAt = invocation.getArgument(0)
            null
        }.`when`(securePreferences).pairedAt = Mockito.any()

        Mockito.doAnswer { invocation ->
            testLastKnownFocusActive = invocation.getArgument(0)
            null
        }.`when`(securePreferences).lastKnownFocusActive = Mockito.anyBoolean()

        Mockito.doAnswer {
            testToken = null
            testDeviceId = null
            testPairedAt = null
            testLastKnownFocusActive = false
            null
        }.`when`(securePreferences).clearAuth()

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
    fun testPairDeviceSuccess() = runTest {
        val jsonBody = """
            {
                "success": true,
                "device_token": "token-xyz-12345",
                "device_id": "dev-and-42",
                "device_name": "Test Phone",
                "paired_at": "2026-11-02T13:00:00Z"
            }
        """.trimIndent()

        // Status response after pairing
        val statusBody = """
            {
                "enabled": false,
                "focus_active": false,
                "version": 1,
                "server_time": "2026-11-02T13:00:00Z"
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(jsonBody))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(statusBody))

        val result = repository.pairDevice("123456", "Test Phone")

        assertTrue("Expected Success result", result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals("token-xyz-12345", data.deviceToken)
        assertEquals("dev-and-42", data.deviceId)

        assertEquals("token-xyz-12345", testToken)
        assertEquals("dev-and-42", testDeviceId)
        assertTrue(repository.isPaired.value)
    }

    @Test
    fun testPairDeviceInvalidCode() = runTest {
        val errorJson = """
            {
                "detail": "Mã ghép đôi không hợp lệ hoặc đã hết hạn."
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(400).setBody(errorJson))

        val result = repository.pairDevice("999999")

        assertTrue("Expected Error result", result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(400, error.statusCode)
        assertFalse(repository.isPaired.value)
        assertNull(testToken)
    }

    @Test
    fun testRefreshStatusActiveWithChallenge() = runTest {
        testToken = "valid-token"

        val statusBody = """
            {
                "enabled": true,
                "focus_active": true,
                "version": 5,
                "language": "vi",
                "server_time": "2026-11-02T13:00:00Z",
                "challenge": {
                    "active": true,
                    "challenge_id": "ch-test-1",
                    "question": "Tính nhẩm: 18 * 4",
                    "language": "vi",
                    "expires_at": "2026-11-02T13:02:00Z",
                    "expires_in": 120
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(statusBody))

        val result = repository.refreshStatus()

        assertTrue(result is ApiResult.Success)
        val state = repository.focusState.value
        assertTrue(state.enabled)
        assertTrue(state.focusActive)
        assertTrue(state.isActive)
        assertEquals(5, state.version)
        assertNotNull(state.activeChallenge)
        assertEquals("ch-test-1", state.activeChallenge?.challengeId)
        assertEquals("Tính nhẩm: 18 * 4", state.activeChallenge?.question)
    }

    @Test
    fun testUnauthorizedRevokesLocalSession() = runTest {
        testToken = "expired-token"

        val errorBody = """{"detail": "Thiết bị không tồn tại hoặc đã bị xóa."}"""
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody(errorBody))

        val result = repository.refreshStatus()

        assertTrue(result is ApiResult.Error)
        assertEquals(401, (result as ApiResult.Error).statusCode)
        assertFalse(repository.isPaired.value)
        assertNull(testToken)
    }

    @Test
    fun testVerifyChallengeCorrectAnswer() = runTest {
        testToken = "valid-token"

        val verifyBody = """
            {
                "success": true,
                "correct": true,
                "enabled": false,
                "focus_active": false,
                "version": 6
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(verifyBody))

        val result = repository.verifyChallenge("ch-test-1", 72)

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertTrue(data.correct)
        assertFalse(data.focusActive)

        val state = repository.focusState.value
        assertFalse(state.isActive)
        assertNull(state.activeChallenge)
    }

    @Test
    fun testVerifyChallengeIncorrectAnswer() = runTest {
        testToken = "valid-token"

        val verifyBody = """
            {
                "success": true,
                "correct": false,
                "enabled": true,
                "focus_active": true,
                "version": 6,
                "new_challenge": {
                    "challenge_id": "ch-test-2",
                    "question": "Tính nhẩm: 25 + 38",
                    "language": "vi",
                    "expires_at": "2026-11-02T13:03:00Z",
                    "expires_in": 120
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(verifyBody))

        val result = repository.verifyChallenge("ch-test-1", 999)

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertFalse(data.correct)

        val state = repository.focusState.value
        assertEquals("ch-test-2", state.activeChallenge?.challengeId)
    }

    @Test
    fun testChallengeCooldownError() = runTest {
        testToken = "valid-token"

        val cooldownBody = """
            {
                "detail": "Bạn đã nhập sai quá nhiều lần. Vui lòng chờ 60 giây.",
                "retry_after": 60
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60").setBody(cooldownBody))

        val result = repository.requestChallenge()

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(429, error.statusCode)
        assertEquals(60, error.retryAfter)
    }

    @Test
    fun testBackendErrorEnvelopeCooldown() = runTest {
        testToken = "valid-token"

        val backendEnvelope = """
            {
                "error": {
                    "code": "cooldown",
                    "retry_after": 60
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60").setBody(backendEnvelope))

        val result = repository.requestChallenge()

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals(429, error.statusCode)
        assertEquals("cooldown", error.code)
        assertEquals(60, error.retryAfter)
    }

    @Test
    fun testUnpairDevice() = runTest {
        testToken = "valid-token"
        testDeviceId = "dev-1"

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"status":"UNPAIRED"}"""))

        val result = repository.unpairDevice()

        assertTrue(result is ApiResult.Success)
        assertNull(testToken)
        assertNull(testDeviceId)
        assertFalse(repository.isPaired.value)
    }

    @Test
    fun testClassifyUrlFailClosedOnNetworkFailure() = runTest {
        // Shutdown server to trigger network exception
        mockWebServer.shutdown()

        val result = repository.classifyUrl("https://example.com")

        assertTrue(result is ApiResult.Error)
        assertEquals("network_fail_closed", (result as ApiResult.Error).code)
    }

    @Test
    fun testAlarmFlows() = runTest {
        testToken = "valid-token"
        testDeviceId = "dev-1"
        Mockito.`when`(securePreferences.hasScope(Mockito.anyString())).thenReturn(true)

        // Mock alarm status
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
                "active": true,
                "session_id": "alarm-sess-123",
                "question": "12 + 7",
                "failed_attempts": 0,
                "is_locked": false,
                "epoch": 2
            }
        """.trimIndent()))

        val statusResult = repository.refreshAlarm()
        assertTrue(statusResult is ApiResult.Success)
        val alarm = repository.alarmState.value
        assertTrue(alarm.active)
        assertEquals("alarm-sess-123", alarm.sessionId)
        assertEquals("12 + 7", alarm.question)

        // Mock solve alarm
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
                "success": true,
                "message": "Alarm solved successfully",
                "epoch": 2
            }
        """.trimIndent()))

        val solveResult = repository.solveAlarm(19)
        assertTrue(solveResult is ApiResult.Success)
        assertFalse(repository.alarmState.value.active)

        // Re-enable active alarm for cancel test
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
                "active": true,
                "session_id": "alarm-sess-456",
                "question": "5 * 5",
                "failed_attempts": 0,
                "is_locked": false,
                "epoch": 3
            }
        """.trimIndent()))
        repository.refreshAlarm()

        // Mock cancel alarm
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
                "success": true,
                "message": "Alarm cancelled",
                "epoch": 3
            }
        """.trimIndent()))

        val cancelResult = repository.cancelAlarm()
        assertTrue(cancelResult is ApiResult.Success)
        assertFalse(repository.alarmState.value.active)

        // Mock emergency stop
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
                "success": true,
                "message": "Emergency stop sent"
            }
        """.trimIndent()))

        val stopResult = repository.emergencyStop()
        assertTrue(stopResult is ApiResult.Success)
    }

    @Test
    fun testSpotifyFlows() = runTest {
        testToken = "valid-token"
        testDeviceId = "dev-1"
        Mockito.`when`(securePreferences.hasScope(Mockito.anyString())).thenReturn(true)

        // Mock spotify status
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
                "is_playing": true,
                "connected": true,
                "track": "Song Title",
                "artist": "Famous Artist",
                "album": "Great Album",
                "progress_pct": 65,
                "progress_ms": 30000,
                "duration_ms": 180000,
                "lyric_curr": "La la la test lyrics"
            }
        """.trimIndent()))

        val statusResult = repository.refreshSpotify()
        assertTrue(statusResult is ApiResult.Success)
        val spotify = repository.spotifyState.value
        assertTrue(spotify.isPlaying)
        assertEquals("Song Title", spotify.track)
        assertEquals("Famous Artist", spotify.artist)
        assertEquals(65, spotify.progressPct)
        assertEquals("La la la test lyrics", spotify.currentLyric)

        // Mock pause
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"success": true, "message": "Command pause executed"}
        """.trimIndent()))
        // Mock subsequent refreshSpotify
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"is_playing": false, "connected": true, "track": "Song Title"}
        """.trimIndent()))

        val controlResult = repository.controlSpotify("pause")
        assertTrue(controlResult is ApiResult.Success)

        // Mock set volume
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"success": true, "message": "Volume set to 80"}
        """.trimIndent()))

        val volResult = repository.setSpotifyVolume(80)
        assertTrue(volResult is ApiResult.Success)
    }

    @Test
    fun testTimetableFetch() = runTest {
        testToken = "valid-token"
        testDeviceId = "dev-1"
        Mockito.`when`(securePreferences.hasScope(Mockito.anyString())).thenReturn(true)

        // Mock timetable
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
                "success": true,
                "data": {
                    "today": {
                        "day": "Thứ 3",
                        "morning": ["Toán", "Lý"],
                        "afternoon": ["Tin học"],
                        "dp_sang": "Đồng phục sơ mi"
                    }
                }
            }
        """.trimIndent()))

        val ttResult = repository.refreshTimetable()
        assertTrue(ttResult is ApiResult.Success)
        val tt = repository.timetableState.value
        assertNotNull(tt)
        assertEquals("Thứ 3", tt?.today?.day)
        assertEquals(2, tt?.today?.morning?.size)
        assertEquals("Toán", tt?.today?.morning?.get(0))
        assertEquals("Đồng phục sơ mi", tt?.today?.uniformMorning)
    }

    @Test
    fun testFocusHistoryFetch() = runTest {
        testToken = "valid-token"
        testDeviceId = "dev-1"

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
                "success": true,
                "history": [
                    {
                        "id": "sess-1",
                        "start_time": "2026-11-03T10:00:00Z",
                        "end_time": "2026-11-03T10:25:00Z",
                        "duration_seconds": 1500,
                        "status": "completed",
                        "reason": "Challenge solved successfully"
                    },
                    {
                        "id": "sess-2",
                        "start_time": "2026-11-03T08:00:00Z",
                        "end_time": "2026-11-03T08:10:00Z",
                        "duration_seconds": 600,
                        "status": "interrupted",
                        "reason": "Early unlock"
                    }
                ],
                "current_session": null
            }
        """.trimIndent()))

        val res = repository.refreshFocusHistory()
        assertTrue(res is ApiResult.Success)
        val history = repository.focusHistory.value
        assertEquals(2, history.size)
        assertEquals("sess-1", history[0].id)
        assertEquals("completed", history[0].status)
        assertEquals(1500L, history[0].durationSeconds)
        assertEquals("interrupted", history[1].status)
    }

    @Test
    fun testSpotify1PercentVolumeControl() = runTest {
        testToken = "valid-token"
        testDeviceId = "dev-1"
        Mockito.`when`(securePreferences.hasScope("spotify")).thenReturn(true)

        val testVolumes = listOf(0, 1, 37, 42, 99, 100)
        for (vol in testVolumes) {
            mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""
                {"success": true, "volume": $vol}
            """.trimIndent()))

            val res = repository.setSpotifyVolume(vol)
            assertTrue("Volume $vol% should succeed", res is ApiResult.Success)
            assertEquals("Progress percentage should reflect exact integer $vol%", vol, repository.spotifyState.value.progressPct)
        }
    }

    @Test
    fun testStationControlOperations() = runTest {
        testToken = "valid-token"
        testDeviceId = "dev-1"
        Mockito.`when`(securePreferences.hasScope("control")).thenReturn(true)

        // switchDisplayView
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success": true, "view": "weather"}"""))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success": true, "data": {"station": {"current_view": "weather"}}}"""))
        val viewRes = repository.switchDisplayView("weather")
        assertTrue(viewRes is ApiResult.Success)

        // setLampColor
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success": true, "hex": "#FF5500"}"""))
        val colorRes = repository.setLampColor("#FF5500")
        assertTrue(colorRes is ApiResult.Success)

        // setLampDim
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success": true, "led_bright": 60}"""))
        val dimRes = repository.setLampDim(60)
        assertTrue(dimRes is ApiResult.Success)

        // triggerBuzz
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success": true, "duration_ms": 250}"""))
        val buzzRes = repository.triggerBuzz(250)
        assertTrue(buzzRes is ApiResult.Success)

        // scheduleAlarm
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success": true, "target_time": "06:30"}"""))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"active": false}"""))
        val alarmRes = repository.scheduleAlarm("06:30")
        assertTrue(alarmRes is ApiResult.Success)

        // sendStickyNote
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success": true, "note": "Hoc bai"}"""))
        val noteRes = repository.sendStickyNote("Hoc bai")
        assertTrue(noteRes is ApiResult.Success)

        // setExamCountdown
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success": true, "name": "Thi HK", "days_left": 10}"""))
        val examRes = repository.setExamCountdown("Thi HK", "25/12")
        assertTrue(examRes is ApiResult.Success)

        // generateQuote
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"success": true, "quote": "Hoc, hoc nua, hoc mai", "topic": "hoc tap", "char_limit": 75}
        """.trimIndent()))
        val quoteRes = repository.generateQuote("hoc tap", 75)
        assertTrue(quoteRes is ApiResult.Success)
        assertEquals("Hoc, hoc nua, hoc mai", (quoteRes as ApiResult.Success).data.quote)

        // startTimer
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"success": true, "duration": "25m"}"""))
        val timerRes = repository.startTimer("25m", "Tap trung")
        assertTrue(timerRes is ApiResult.Success)
    }

    @Test
    fun testApiHealthCheckSuccess() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"status": "healthy", "service": "deskflow-unified-backend", "version": "1.0.0"}
        """.trimIndent()))

        val healthRes = repository.testApiHealth()
        assertTrue("Health check must succeed on HTTP 200", healthRes.isSuccess)
        assertEquals(200, healthRes.statusCode)
        assertEquals("deskflow-unified-backend", healthRes.service)
        assertEquals("1.0.0", healthRes.version)
        assertTrue("Latency must be non-negative", healthRes.latencyMs >= 0)
    }

    @Test
    fun testApiHealthCheckHttpError() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody("""
            {"detail": "service unavailable"}
        """.trimIndent()))

        val healthRes = repository.testApiHealth()
        assertFalse("Health check must report failure on HTTP 503", healthRes.isSuccess)
        assertEquals(503, healthRes.statusCode)
        assertEquals("HTTP_503", healthRes.errorType)
    }

    @Test
    fun testSpotify5LineLyricsAndCoverUrl() = runTest {
        testToken = "valid-token"
        testDeviceId = "dev-1"
        Mockito.`when`(securePreferences.hasScope("spotify")).thenReturn(true)

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""
            {
                "is_playing": true,
                "connected": true,
                "track": "Starboy",
                "artist": "The Weeknd",
                "album": "Starboy",
                "progress_ms": 45000,
                "duration_ms": 230000,
                "progress_pct": 20,
                "lyric_curr": "Look what you've done",
                "lyric_next": "I'm a motherfuckin' starboy",
                "lyrics_window": [
                    "I'm tryna put you in the worst mood, ah",
                    "P1 cleaner than your church shoes, ah",
                    "Look what you've done",
                    "I'm a motherfuckin' starboy",
                    "Every day a nigga try to test me, ah"
                ],
                "active_lyric_index": 2,
                "cover_url": "https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452"
            }
        """.trimIndent()))

        val res = repository.refreshSpotify()
        assertTrue(res is ApiResult.Success)
        val data = (res as ApiResult.Success).data
        assertEquals("Starboy", data.track)
        assertEquals("https://i.scdn.co/image/ab67616d0000b2734718e2b124f79258be7bc452", data.coverUrl)
        assertEquals(5, data.lyricsWindow.size)
        assertEquals("Look what you've done", data.lyricsWindow[2])
        assertEquals(2, data.activeLyricIndex)
    }

    // =========================================================================
    // Morse Code & View Switching API Tests
    // =========================================================================

    @Test
    fun testPlayMorseSuccessMatchesContract() = runTest {
        testToken = "valid-control-token"
        testDeviceId = "dev-test-1"

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success": true, "pattern": "... --- ...", "unit_ms": 120, "freq": 2000}""")
        )

        val res = repository.playMorse(pattern = "... --- ...", unitMs = 120, freq = 2000)
        assertTrue("playMorse must succeed", res is ApiResult.Success)
        val recorded = mockWebServer.takeRequest()
        assertEquals("/api/app/control/morse/play", recorded.path)
        assertEquals("POST", recorded.method)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"pattern\":\"... --- ...\""))
        assertTrue(body.contains("\"unit_ms\":120"))
        assertTrue(body.contains("\"freq\":2000"))
    }

    @Test
    fun testPlayMorseInvalidPattern400() = runTest {
        testToken = "valid-token"

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error": {"code": "invalid_morse_pattern"}}""")
        )

        val res = repository.playMorse(pattern = "", unitMs = 80, freq = 2400)
        assertTrue(res is ApiResult.Error)
        val err = res as ApiResult.Error
        assertEquals("invalid_morse_pattern", err.code)
        assertEquals(400, err.statusCode)
    }

    @Test
    fun testPlayMorseTokenInvalid401() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error": {"code": "token_invalid"}}""")
        )

        val res = repository.playMorse(pattern = ".-", unitMs = 80)
        assertTrue(res is ApiResult.Error)
        val err = res as ApiResult.Error
        assertEquals("token_invalid", err.code)
        assertEquals(401, err.statusCode)
    }

    @Test
    fun testPlayMorseEndpointNotReady405() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(405)
                .setBody("""{"error": {"code": "invalid_request"}}""")
        )

        val res = repository.playMorse(pattern = ".-", unitMs = 80)
        assertTrue(res is ApiResult.Error)
        val err = res as ApiResult.Error
        assertEquals(405, err.statusCode)
    }

    @Test
    fun testPlayMorseServerInternalError500() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error": {"code": "internal"}}""")
        )

        val res = repository.playMorse(pattern = ".-", unitMs = 80)
        assertTrue(res is ApiResult.Error)
        val err = res as ApiResult.Error
        assertEquals(500, err.statusCode)
    }

    @Test
    fun testStopMorseSuccess() = runTest {
        testToken = "valid-token"

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success": true, "message": "Morse playback stopped."}""")
        )

        val res = repository.stopMorse()
        assertTrue("stopMorse must succeed", res is ApiResult.Success)
        val recorded = mockWebServer.takeRequest()
        assertEquals("/api/app/control/morse/stop", recorded.path)
        assertEquals("POST", recorded.method)
    }

    @Test
    fun testStopMorseTokenInvalid401() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error": {"code": "token_invalid"}}""")
        )

        val res = repository.stopMorse()
        assertTrue(res is ApiResult.Error)
        val err = res as ApiResult.Error
        assertEquals("token_invalid", err.code)
        assertEquals(401, err.statusCode)
    }

    @Test
    fun testSwitchDisplayViewClockPassesThroughDirectly() = runTest {
        testToken = "valid-token"

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success": true, "data": {"current_screen": "clock"}}""")
        )

        val res = repository.switchDisplayView("clock")
        assertTrue("switchDisplayView('clock') must succeed", res is ApiResult.Success)
        val recorded = mockWebServer.takeRequest()
        assertEquals("/api/app/control/display/view", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue("View 'clock' must be sent directly in the payload", body.contains("\"view\":\"clock\""))
    }

    @Test
    fun testSwitchDisplayViewValidViews() = runTest {
        testToken = "valid-token"

        listOf("main", "stats", "tkb", "weather", "lyrics", "clock", "notes", "countdown", "focus").forEach { viewKey ->
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"success": true, "data": {"current_screen": "$viewKey"}}""")
            )

            val res = repository.switchDisplayView(viewKey)
            assertTrue(res is ApiResult.Success)
            val recorded = mockWebServer.takeRequest()
            val body = recorded.body.readUtf8()
            assertTrue(body.contains("\"view\":\"$viewKey\""))
        }
    }

    @Test
    fun testApprovePairSessionSuccess() = runTest {
        testToken = "valid-token"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success": true, "device_id": "desk-123", "device_name": "My Laptop"}""")
        )

        val res = repository.approvePairSession(
            serverUrl = null,
            sessionId = "sess_abc123",
            deviceName = "My Laptop"
        )
        assertTrue(res is ApiResult.Success)
        val data = (res as ApiResult.Success).data
        assertTrue(data.success)
        assertEquals("desk-123", data.deviceId)
        assertEquals("My Laptop", data.deviceName)

        val recorded = mockWebServer.takeRequest()
        assertEquals("/api/focus/pair/session/sess_abc123/approve", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"device_name\":\"My Laptop\""))
        assertTrue(body.contains("\"platform\":\"desktop\""))
    }

    @Test
    fun testRejectPairSessionSuccess() = runTest {
        testToken = "valid-token"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"success": true}""")
        )

        val res = repository.rejectPairSession(
            serverUrl = null,
            sessionId = "sess_abc123"
        )
        assertTrue(res is ApiResult.Success)
        val recorded = mockWebServer.takeRequest()
        assertEquals("/api/focus/pair/session/sess_abc123/reject", recorded.path)
    }

    @Test
    fun testEnableFocusSupportsCustomShortDuration() = runTest {
        testToken = "valid-token"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"success": true, "enabled": true, "focus_active": true,
                       "duration_minutes": 2, "remaining_seconds": 120}"""
                )
        )

        val res = repository.enableFocus(durationMinutes = 2, reason = "Học nhanh")

        assertTrue(res is ApiResult.Success)
        val recorded = mockWebServer.takeRequest()
        assertEquals("/api/focus/enable", recorded.path)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"duration_minutes\":2"))

        val state = (res as ApiResult.Success).data
        assertTrue(state.isActive)
        assertEquals(2, state.durationMinutes)
        assertEquals(120L, state.remainingSeconds)
    }
}
