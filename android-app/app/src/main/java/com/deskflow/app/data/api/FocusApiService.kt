package com.deskflow.app.data.api

import com.deskflow.app.data.model.*
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface FocusApiService {

    @POST("api/focus/pair")
    suspend fun pair(
        @Body request: PairRequest
    ): Response<PairResponse>

    @GET("api/focus/status")
    suspend fun getStatus(): Response<StatusResponse>

    @POST("api/focus/enable")
    suspend fun enable(
        @Body request: EnableRequest
    ): Response<StatusResponse>

    @POST("api/focus/classify-url")
    suspend fun classifyUrl(
        @Body request: ClassifyUrlRequest
    ): Response<ClassifyUrlResponse>

    @POST("api/focus/challenge")
    suspend fun requestChallenge(
        @Body request: ChallengeRequest
    ): Response<ChallengeResponse>

    @POST("api/focus/verify")
    suspend fun verifyChallenge(
        @Body request: VerifyRequest
    ): Response<VerifyResponse>

    @GET("api/focus/devices")
    suspend fun getDevices(): Response<DevicesResponse>

    @POST("api/focus/unpair")
    suspend fun unpair(
        @Body body: Map<String, String> = emptyMap()
    ): Response<UnpairResponse>

    // --- Focus History ---
    @GET("api/focus/history")
    suspend fun getFocusHistory(): Response<FocusHistoryResponse>

    // --- Control / Alarm & Buzz ---
    @GET("api/app/control/alarm/active")
    suspend fun getActiveAlarm(): Response<AlarmActiveDto>

    @POST("api/app/control/alarm/solve")
    suspend fun solveAlarm(
        @Body request: AlarmSolveRequest
    ): Response<ActionSuccessResponse>

    @POST("api/app/control/alarm/cancel")
    suspend fun cancelAlarm(
        @Body request: AlarmCancelRequest
    ): Response<ActionSuccessResponse>

    @POST("api/app/control/alarm/emergency-stop")
    suspend fun emergencyStop(): Response<ActionSuccessResponse>

    @POST("api/app/control/alarm/buzz")
    suspend fun triggerBuzz(
        @Body request: BuzzRequest
    ): Response<ActionSuccessResponse>

    @POST("api/app/control/morse/play")
    suspend fun playMorse(
        @Body request: MorsePlayRequest
    ): Response<ActionSuccessResponse>

    @POST("api/app/control/morse/stop")
    suspend fun stopMorse(): Response<ActionSuccessResponse>

    @POST("api/app/control/alarm/schedule")
    suspend fun scheduleAlarm(
        @Body request: AlarmScheduleRequest
    ): Response<ActionSuccessResponse>

    // --- Control / Station Telemetry & Settings ---
    @GET("api/app/control/status")
    suspend fun getSystemStatus(): Response<SystemStatusResponse>

    @GET("api/app/control/settings")
    suspend fun getSettings(): Response<StationSettingsResponse>

    @POST("api/app/control/settings")
    suspend fun updateSettings(
        @Body settings: StationSettingsDto
    ): Response<StationSettingsResponse>

    // --- Control / Display & Screen ---
    @POST("api/app/control/display/view")
    suspend fun switchDisplayView(
        @Body request: ViewSwitchRequest
    ): Response<ActionSuccessResponse>

    @POST("api/app/control/display/close-tkb")
    suspend fun closeTkb(): Response<ActionSuccessResponse>

    // --- Control / Lighting & LEDs ---
    @POST("api/app/control/lamp/color")
    suspend fun setLampColor(
        @Body request: LampColorRequest
    ): Response<ActionSuccessResponse>

    @POST("api/app/control/lamp/dim")
    suspend fun setLampDim(
        @Body request: LampDimRequest
    ): Response<ActionSuccessResponse>

    @POST("api/app/control/lamp/off")
    suspend fun turnLampOff(): Response<ActionSuccessResponse>

    @POST("api/app/control/led")
    suspend fun controlLed(
        @Body request: LedControlRequest
    ): Response<ActionSuccessResponse>

    @GET("api/app/control/clock-style")
    suspend fun getClockConfig(): Response<ClockConfigResponse>

    @POST("api/app/control/clock-style")
    suspend fun updateClockConfig(
        @Body request: ClockConfigDto
    ): Response<ClockConfigResponse>

    @POST("api/app/control/clock-style")
    suspend fun setClockStyle(
        @Body request: ClockStyleRequest
    ): Response<ActionSuccessResponse>

    @POST("api/app/control/telemetry")
    suspend fun reportTelemetry(
        @Body request: Map<String, @JvmSuppressWildcards Any>
    ): Response<ActionSuccessResponse>

    // --- Control / Tools ---
    @POST("api/app/control/sticky-note")
    suspend fun sendStickyNote(
        @Body request: StickyNoteRequest
    ): Response<ActionSuccessResponse>

    @POST("api/app/control/exam-countdown")
    suspend fun setExamCountdown(
        @Body request: ExamCountdownRequest
    ): Response<ActionSuccessResponse>

    // --- Control / Persistent Notes CRUD & Pinning ---
    @GET("api/app/control/notes")
    suspend fun getNotes(): Response<NotesListResponse>

    @POST("api/app/control/notes")
    suspend fun createNote(
        @Body request: CreateNoteRequest
    ): Response<NotesListResponse>

    @DELETE("api/app/control/notes/{id}")
    suspend fun deleteNote(
        @Path("id") id: String
    ): Response<NotesListResponse>

    @POST("api/app/control/notes/{id}/pin")
    suspend fun pinNote(
        @Path("id") id: String
    ): Response<ActionSuccessResponse>

    @POST("api/app/control/notes/unpin")
    suspend fun unpinNote(): Response<ActionSuccessResponse>

    // --- Control / Countdowns CRUD & Pinning ---
    @GET("api/app/control/countdowns")
    suspend fun getCountdowns(): Response<CountdownsListResponse>

    @POST("api/app/control/countdowns")
    suspend fun createCountdown(
        @Body request: CreateCountdownRequest
    ): Response<CountdownsListResponse>

    @DELETE("api/app/control/countdowns/{id}")
    suspend fun deleteCountdown(
        @Path("id") id: String
    ): Response<CountdownsListResponse>

    @POST("api/app/control/countdowns/{id}/pin")
    suspend fun pinCountdown(
        @Path("id") id: String
    ): Response<ActionSuccessResponse>

    // --- Weather Forecast ---
    @GET("api/app/control/weather/forecast")
    suspend fun getWeatherForecast(
        @Query("city") city: String? = null
    ): Response<WeatherForecastResponse>

    @POST("api/app/control/quote/generate")
    suspend fun generateQuote(
        @Body request: QuoteGenerateRequest
    ): Response<QuoteResponse>

    @POST("api/app/control/timer/start")
    suspend fun startTimer(
        @Body request: TimerStartRequest
    ): Response<ActionSuccessResponse>

    @GET("api/app/control/timetable/today")
    suspend fun getTodayTimetable(): Response<TimetableResponse>

    // --- Health & Diagnostic ---
    @GET("api/app/health")
    suspend fun getHealth(): Response<AppHealthDto>

    // --- Spotify ---
    @GET("api/app/spotify/status")
    suspend fun getSpotifyStatus(): Response<SpotifyStatusDto>

    @POST("api/app/spotify/playback")
    suspend fun controlSpotifyPlayback(
        @Body request: SpotifyPlaybackRequest
    ): Response<ActionSuccessResponse>

    @POST("api/app/spotify/volume")
    suspend fun setSpotifyVolume(
        @Body request: SpotifyVolumeRequest
    ): Response<ActionSuccessResponse>
}
