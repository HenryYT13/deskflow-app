package com.deskflow.app.data

import com.deskflow.app.data.api.FocusApiService
import com.deskflow.app.data.local.ActivityStore
import com.deskflow.app.data.local.SecurePreferences
import com.deskflow.app.data.repository.ApiResult
import com.deskflow.app.data.repository.FocusRepository
import com.deskflow.app.ui.theme.ScheduledDarkModeEvaluator
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

class NotesAndScheduledThemeTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: FocusApiService
    private lateinit var securePreferences: SecurePreferences
    private lateinit var activityStore: ActivityStore
    private lateinit var repository: FocusRepository

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

        Mockito.`when`(securePreferences.isPaired).thenReturn(true)
        Mockito.`when`(securePreferences.deviceToken).thenReturn("valid-token")
        Mockito.`when`(securePreferences.deviceId).thenReturn("dev-android-1")
        Mockito.`when`(securePreferences.deviceName).thenReturn("Test Phone")
        Mockito.`when`(securePreferences.language).thenReturn("vi")
        Mockito.`when`(securePreferences.hasScope(Mockito.anyString())).thenReturn(true)

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

    // =========================================================================
    // 1. Scheduled Dark Mode Tests
    // =========================================================================

    @Test
    fun testScheduledDarkModeOvernightRange() {
        // 20:00 (1200) -> 06:00 (360)
        val start = "20:00"
        val end = "06:00"

        // 19:59 -> false (Light)
        assertFalse(ScheduledDarkModeEvaluator.isScheduledDarkActive(19 * 60 + 59, start, end))
        // 20:00 -> true (Dark starts inclusive)
        assertTrue(ScheduledDarkModeEvaluator.isScheduledDarkActive(20 * 60, start, end))
        // 23:30 -> true (Dark)
        assertTrue(ScheduledDarkModeEvaluator.isScheduledDarkActive(23 * 60 + 30, start, end))
        // 00:00 -> true (Midnight Dark)
        assertTrue(ScheduledDarkModeEvaluator.isScheduledDarkActive(0, start, end))
        // 05:59 -> true (Dark before end)
        assertTrue(ScheduledDarkModeEvaluator.isScheduledDarkActive(5 * 60 + 59, start, end))
        // 06:00 -> false (Light starts at end exclusive)
        assertFalse(ScheduledDarkModeEvaluator.isScheduledDarkActive(6 * 60, start, end))
        // 12:00 -> false (Midday Light)
        assertFalse(ScheduledDarkModeEvaluator.isScheduledDarkActive(12 * 60, start, end))
    }

    @Test
    fun testScheduledDarkModeSameDayRange() {
        // 09:00 (540) -> 17:00 (1020)
        val start = "09:00"
        val end = "17:00"

        assertFalse(ScheduledDarkModeEvaluator.isScheduledDarkActive(8 * 60 + 59, start, end))
        assertTrue(ScheduledDarkModeEvaluator.isScheduledDarkActive(9 * 60, start, end))
        assertTrue(ScheduledDarkModeEvaluator.isScheduledDarkActive(13 * 60, start, end))
        assertTrue(ScheduledDarkModeEvaluator.isScheduledDarkActive(16 * 60 + 59, start, end))
        assertFalse(ScheduledDarkModeEvaluator.isScheduledDarkActive(17 * 60, start, end))
        assertFalse(ScheduledDarkModeEvaluator.isScheduledDarkActive(22 * 60, start, end))
    }

    @Test
    fun testScheduledDarkModeEqualStartEnd() {
        // Equal start and end times (e.g. 06:00 == 06:00) -> deterministic false (disabled window)
        assertFalse(ScheduledDarkModeEvaluator.isScheduledDarkActive(6 * 60, "06:00", "06:00"))
        assertFalse(ScheduledDarkModeEvaluator.isScheduledDarkActive(18 * 60, "06:00", "06:00"))
    }

    @Test
    fun testResolveDarkThemeModes() {
        // System mode respects systemIsDark
        assertTrue(ScheduledDarkModeEvaluator.resolveDarkTheme("system", true, "20:00", "06:00", 12 * 60))
        assertFalse(ScheduledDarkModeEvaluator.resolveDarkTheme("system", false, "20:00", "06:00", 23 * 60))

        // Light mode forces light
        assertFalse(ScheduledDarkModeEvaluator.resolveDarkTheme("light", true, "20:00", "06:00", 23 * 60))

        // Dark mode forces dark
        assertTrue(ScheduledDarkModeEvaluator.resolveDarkTheme("dark", false, "20:00", "06:00", 12 * 60))

        // Scheduled mode uses time window regardless of systemIsDark
        assertTrue(ScheduledDarkModeEvaluator.resolveDarkTheme("scheduled", false, "22:00", "06:00", 23 * 60))
        assertFalse(ScheduledDarkModeEvaluator.resolveDarkTheme("scheduled", true, "22:00", "06:00", 14 * 60))
    }

    @Test
    fun testPreferenceNormalizers() {
        assertEquals("scheduled", SecurePreferences.normalizeTheme("SCHEDULED"))
        assertEquals("dark", SecurePreferences.normalizeTheme("dark"))
        assertEquals("light", SecurePreferences.normalizeTheme("light"))
        assertEquals("system", SecurePreferences.normalizeTheme("invalid_mode"))

        assertEquals("20:00", SecurePreferences.normalizeTimeStr("20:00", "22:00"))
        assertEquals("06:05", SecurePreferences.normalizeTimeStr("6:5", "06:00"))
        assertEquals("06:00", SecurePreferences.normalizeTimeStr("29:99", "06:00"))
        assertEquals("20:00", SecurePreferences.normalizeTimeStr("bad", "20:00"))
    }

    // =========================================================================
    // 2. Notes CRUD & Authoritative Deletion Tests
    // =========================================================================

    @Test
    fun testDeleteNoteSuccessAndDeleteLastNoteClearsLocalState() = runTest {
        // 1. Seed 2 notes via getNotes
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                    "success": true,
                    "notes": [
                        {"id": "note-1", "content": "Note 1", "pinned": true},
                        {"id": "note-2", "content": "Note 2", "pinned": false}
                    ]
                }
                """.trimIndent()
            )
        )
        val getRes = repository.getNotes()
        assertTrue(getRes is ApiResult.Success)
        assertEquals(2, repository.getCachedNotes().size)

        // 2. Delete note-1 -> backend returns remaining [note-2]
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                    "success": true,
                    "deleted_id": "note-1",
                    "notes": [
                        {"id": "note-2", "content": "Note 2", "pinned": false}
                    ]
                }
                """.trimIndent()
            )
        )
        val del1 = repository.deleteNote("note-1")
        assertTrue("Expected deleteNote to succeed", del1 is ApiResult.Success)
        assertEquals(1, repository.getCachedNotes().size)
        assertEquals("note-2", repository.getCachedNotes()[0].id)

        // Verify HTTP DELETE path
        val req1 = mockWebServer.takeRequest() // getNotes
        assertEquals("GET", req1.method)
        val req2 = mockWebServer.takeRequest() // deleteNote
        assertEquals("DELETE", req2.method)
        assertEquals("/api/app/control/notes/note-1", req2.path)

        // 3. Delete last note (note-2) -> backend returns empty notes: []
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                    "success": true,
                    "deleted_id": "note-2",
                    "notes": []
                }
                """.trimIndent()
            )
        )
        val del2 = repository.deleteNote("note-2")
        assertTrue("Expected deleting last note to succeed", del2 is ApiResult.Success)
        assertTrue(
            "Deleting the last note MUST clear localNotes to empty list",
            repository.getCachedNotes().isEmpty()
        )
    }

    @Test
    fun testDeleteNoteFailureDoesNotMutateLocalState() = runTest {
        // Seed 1 note
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                    "success": true,
                    "notes": [
                        {"id": "note-keep", "content": "Important note", "pinned": true}
                    ]
                }
                """.trimIndent()
            )
        )
        repository.getNotes()
        assertEquals(1, repository.getCachedNotes().size)

        // Simulate 404 Not Found
        mockWebServer.enqueue(
            MockResponse().setResponseCode(404).setBody(
                """{"error": "note_not_found", "message": "Note not found"}"""
            )
        )
        val err404 = repository.deleteNote("note-keep")
        assertTrue(err404 is ApiResult.Error)
        assertEquals(404, (err404 as ApiResult.Error).statusCode)
        assertEquals(1, repository.getCachedNotes().size)

        // Simulate 500 Internal Error
        mockWebServer.enqueue(
            MockResponse().setResponseCode(500).setBody(
                """{"error": "internal_error", "message": "Database write failed"}"""
            )
        )
        val err500 = repository.deleteNote("note-keep")
        assertTrue(err500 is ApiResult.Error)
        assertEquals(500, (err500 as ApiResult.Error).statusCode)
        assertEquals(1, repository.getCachedNotes().size)

        // Simulate 403 Forbidden
        mockWebServer.enqueue(
            MockResponse().setResponseCode(403).setBody(
                """{"error": "forbidden", "message": "Missing control scope"}"""
            )
        )
        val err403 = repository.deleteNote("note-keep")
        assertTrue(err403 is ApiResult.Error)
        assertEquals(403, (err403 as ApiResult.Error).statusCode)
        assertEquals(1, repository.getCachedNotes().size)
    }

    @Test
    fun testWeatherForecastUnifiedContractParsing() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                    "success": true,
                    "intervals": [
                        {
                            "time": "2026-11-07 15:00:00",
                            "timestamp": 1762527600,
                            "temp": 29.5,
                            "humidity": 72,
                            "description": "mây rải rác",
                            "rain_prob": 35.0,
                            "is_rain": false
                        }
                    ]
                }
                """.trimIndent()
            )
        )

        val res = repository.getWeatherForecast()
        assertTrue(res is ApiResult.Success)
        val intervals = (res as ApiResult.Success).data.allIntervals
        assertEquals(1, intervals.size)
        assertEquals("2026-11-07 15:00:00", intervals[0].displayTime)
        assertEquals("mây rải rác", intervals[0].displayDescription)
        assertEquals(35.0, intervals[0].rainProb, 0.01)
        assertFalse(intervals[0].isRain)
    }
}
