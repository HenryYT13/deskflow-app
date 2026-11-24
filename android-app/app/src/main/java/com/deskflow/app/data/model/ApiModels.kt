package com.deskflow.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PairRequest(
    @SerialName("pairing_code") val pairingCode: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("platform") val platform: String = "android",
    @SerialName("client_version") val clientVersion: String = com.deskflow.app.BuildConfig.VERSION_NAME
)

@Serializable
data class PairResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("device_token") val deviceToken: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("platform") val platform: String? = null,
    @SerialName("scopes") val scopes: List<String> = listOf("focus"),
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("paired_at") val pairedAt: String? = null,
    @SerialName("focus_active") val focusActive: Boolean = false
)

@Serializable
data class PairSessionApproveRequest(
    @SerialName("device_name") val deviceName: String,
    @SerialName("scopes") val scopes: List<String>,
    @SerialName("platform") val platform: String
)

@Serializable
data class PairSessionApproveResponse(
    @SerialName("success") val success: Boolean = true,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("device_name") val deviceName: String? = null
)

@Serializable
data class ChallengeDto(
    @SerialName("active") val active: Boolean = true,
    @SerialName("challenge_id") val challengeId: String,
    @SerialName("question") val question: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = 120
)

@Serializable
data class StatusResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("enabled") val enabled: Boolean = false,
    @SerialName("focus_active") val focusActive: Boolean = false,
    @SerialName("phase") val phase: String? = null,
    @SerialName("session_type") val sessionType: String? = null,
    @SerialName("remaining_seconds") val remainingSeconds: Long? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("cycle_count") val cycleCount: Int? = null,
    @SerialName("version") val version: Int = 0,
    @SerialName("language") val language: String? = "vi",
    @SerialName("server_time") val serverTime: String? = null,
    @SerialName("challenge") val challenge: ChallengeDto? = null
)

@Serializable
data class EnableRequest(
    @SerialName("duration_minutes") val durationMinutes: Int = 25,
    @SerialName("break_minutes") val breakMinutes: Int = 5,
    @SerialName("session_type") val sessionType: String = "focus",
    @SerialName("reason") val reason: String = "Study Session",
    @SerialName("language") val language: String = "vi"
)

@Serializable
data class ClassifyUrlRequest(
    @SerialName("url") val url: String,
    @SerialName("title") val title: String? = null,
    @SerialName("language") val language: String = "vi"
)

@Serializable
data class ClassifyUrlResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("classification") val classification: String,
    @SerialName("action") val action: String,
    @SerialName("reason") val reason: String,
    @SerialName("category") val category: String? = null,
    @SerialName("confidence") val confidence: Double? = 1.0,
    @SerialName("domain") val domain: String
)

@Serializable
data class ChallengeRequest(
    @SerialName("language") val language: String = "vi",
    @SerialName("force_new") val forceNew: Boolean = false
)

@Serializable
data class ChallengeResponse(
    @SerialName("challenge_id") val challengeId: String,
    @SerialName("question") val question: String,
    @SerialName("language") val language: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("expires_in") val expiresIn: Long = 120,
    @SerialName("server_time") val serverTime: String? = null
)

@Serializable
data class VerifyRequest(
    @SerialName("challenge_id") val challengeId: String,
    @SerialName("answer") val answer: Int
)

@Serializable
data class VerifyResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("correct") val correct: Boolean = false,
    @SerialName("enabled") val enabled: Boolean = false,
    @SerialName("focus_active") val focusActive: Boolean = false,
    @SerialName("version") val version: Int = 0,
    @SerialName("message") val message: String? = null,
    @SerialName("new_challenge") val newChallenge: ChallengeResponse? = null,
    @SerialName("error") val error: String? = null
)

@Serializable
data class DeviceDto(
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("platform") val platform: String,
    @SerialName("paired_at") val pairedAt: String,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("is_current") val isCurrent: Boolean = false
)

@Serializable
data class SelectedDeviceResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("selected_device_id") val selectedDeviceId: String? = null
)

@Serializable
data class DevicesResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("devices") val devices: List<DeviceDto> = emptyList()
)

@Serializable
data class UnpairResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("revoked") val revoked: Boolean = false
)

@Serializable
data class ErrorEnvelope(
    @SerialName("error") val error: ErrorDetail
)

@Serializable
data class ErrorDetail(
    @SerialName("code") val code: String,
    @SerialName("retry_after") val retryAfter: Int? = null
)

// ================= CONTROL & ALARM MODELS =================

@Serializable
data class AlarmActiveDto(
    @SerialName("active") val active: Boolean = false,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("epoch") val epoch: Int? = null,
    @SerialName("question") val question: String? = null,
    @SerialName("created_at") val createdAt: Double? = null,
    @SerialName("failed_attempts") val failedAttempts: Int = 0,
    @SerialName("cooldown_until") val cooldownUntil: Double = 0.0,
    @SerialName("is_locked") val isLocked: Boolean = false,
    @SerialName("wakeup_pending") val wakeupPending: Boolean = false,
    @SerialName("anti_oversleep_enabled") val antiOversleepEnabled: Boolean = true
)

@Serializable
data class NotificationItemDto(
    @SerialName("id") val id: String = "",
    @SerialName("kind") val kind: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("body") val body: String = "",
    @SerialName("priority") val priority: String = "default",
    @SerialName("timestamp") val timestamp: Double = 0.0
)

@Serializable
data class NotificationsResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("notifications") val notifications: List<NotificationItemDto> = emptyList()
)

@Serializable
data class AlarmSolveRequest(
    @SerialName("session_id") val sessionId: String,
    @SerialName("answer") val answer: Int
)

@Serializable
data class AlarmCancelRequest(
    @SerialName("session_id") val sessionId: String
)

@Serializable
data class ActionSuccessResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("epoch") val epoch: Int? = null,
    @SerialName("message") val message: String? = null
)

@Serializable
data class TimetableResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("data") val data: TimetableDataWrapper? = null,
    @SerialName("error") val error: String? = null
)

@Serializable
data class TimetableDataWrapper(
    @SerialName("today") val today: DayScheduleDto? = null,
    @SerialName("tomorrow") val tomorrow: DayScheduleDto? = null
)

@Serializable
data class DayScheduleDto(
    @SerialName("day") val day: String = "",
    @SerialName("display_day") val displayDay: String? = null,
    @SerialName("morning") val morning: List<String> = emptyList(),
    @SerialName("afternoon") val afternoon: List<String> = emptyList(),
    @SerialName("dp_sang") val uniformMorning: String = "Tự do",
    @SerialName("dp_chieu") val uniformAfternoon: String = "",
    @SerialName("has_afternoon") val hasAfternoon: Boolean = false,
    @SerialName("has_any_class") val hasAnyClass: Boolean = false
)

// ================= SPOTIFY MODELS =================

@Serializable
data class AppHealthDto(
    @SerialName("status") val status: String = "healthy",
    @SerialName("service") val service: String = "deskflow-unified-backend",
    @SerialName("version") val version: String = "1.0.0"
)

@Serializable
data class SpotifyStatusDto(
    @SerialName("is_playing") val isPlaying: Boolean = false,
    @SerialName("connected") val connected: Boolean = false,
    @SerialName("track") val track: String? = null,
    @SerialName("artist") val artist: String? = null,
    @SerialName("album") val album: String? = null,
    @SerialName("progress_ms") val progressMs: Long = 0,
    @SerialName("duration_ms") val durationMs: Long = 1,
    @SerialName("progress_pct") val progressPct: Int = 0,
    @SerialName("lyric_curr") val currentLyric: String? = null,
    @SerialName("lyric_next") val nextLyric: String? = null,
    @SerialName("lyrics_window") val lyricsWindow: List<String> = emptyList(),
    @SerialName("active_lyric_index") val activeLyricIndex: Int = 2,
    @SerialName("cover_url") val coverUrl: String? = null
)

@Serializable
data class SpotifyPlaybackRequest(
    @SerialName("command") val command: String
)

@Serializable
data class SpotifyVolumeRequest(
    @SerialName("volume") val volume: Int
)

// ================= FOCUS HISTORY MODELS =================

@Serializable
data class FocusSessionDto(
    @SerialName("id") val id: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Long = 0,
    @SerialName("status") val status: String = "active",
    @SerialName("reason") val reason: String? = null
)

@Serializable
data class FocusHistoryResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("history") val history: List<FocusSessionDto> = emptyList(),
    @SerialName("current_session") val currentSession: FocusSessionDto? = null
)

// ================= STATION CONTROL & TELEMETRY MODELS =================

@Serializable
data class StationStatusDto(
    @SerialName("battery_pct") val batteryPct: Int = 100,
    @SerialName("voltage") val voltage: Double = 4.12,
    @SerialName("wifi_rssi") val wifiRssi: Int = -55,
    @SerialName("current_view") val currentView: String = "main",
    @SerialName("weather") val weather: String = "N/A",
    @SerialName("math_active") val mathActive: Boolean = false
)

@Serializable
data class ServerMetricsDto(
    @SerialName("cpu") val cpu: Double = 0.0,
    @SerialName("ram_pct") val ramPct: Double = 0.0,
    @SerialName("ram_used_mb") val ramUsedMb: Int = 0,
    @SerialName("ram_total_mb") val ramTotalMb: Int = 0,
    @SerialName("disk_used_gb") val diskUsedGb: Double = 0.0,
    @SerialName("disk_total_gb") val diskTotalGb: Double = 20.0,
    @SerialName("disk_pct") val diskPct: Double = 0.0,
    @SerialName("disk_str") val diskStr: String = "13.2 / 19.1GB",
    @SerialName("uptime") val uptime: String = "N/A"
)

@Serializable
data class LaptopStatusDto(
    @SerialName("cpu") val cpu: Double = 0.0,
    @SerialName("ram") val ram: Double = 0.0,
    @SerialName("temp") val temp: Double = 0.0,
    @SerialName("battery") val battery: Int = 0,
    @SerialName("device_name") val deviceName: String = "Máy tính",
    @SerialName("online") val online: Boolean = false
)

@Serializable
data class SystemStatusWrapper(
    @SerialName("station") val station: StationStatusDto = StationStatusDto(),
    @SerialName("server") val server: ServerMetricsDto = ServerMetricsDto(),
    @SerialName("laptop") val laptop: LaptopStatusDto = LaptopStatusDto()
)

@Serializable
data class SystemStatusResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("data") val data: SystemStatusWrapper? = null
)

@Serializable
data class StationSettingsDto(
    @SerialName("darkmode_mode") val darkmodeMode: String = "off",
    @SerialName("darkmode_start") val darkmodeStart: String = "22:00",
    @SerialName("darkmode_end") val darkmodeEnd: String = "06:00",
    @SerialName("led_brightness") val ledBrightness: Int = 100,
    @SerialName("buzzer_enabled") val buzzerEnabled: Boolean = true,
    @SerialName("buzzer_duration") val buzzerDuration: Int = 40,
    @SerialName("weather_alert_enabled") val weatherAlertEnabled: Boolean = true,
    @SerialName("weather_alert_time") val weatherAlertTime: String = "06:15",
    @SerialName("weather_city") val weatherCity: String = "Ha Long",
    @SerialName("battery_low_thresh") val batteryLowThresh: Int = 30,
    @SerialName("battery_high_thresh") val batteryHighThresh: Int = 80,
    @SerialName("quote_schedules") val quoteSchedules: List<String> = listOf("06:30", "12:00", "20:00"),
    @SerialName("clock_style") val clockStyle: String = "modern"
)

@Serializable
data class StationSettingsResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("data") val data: StationSettingsDto? = null
)

@Serializable
data class ClockConfigDto(
    @SerialName("style") val style: String = "digital_classic",
    @SerialName("time_format") val timeFormat: String = "24h",
    @SerialName("show_seconds") val showSeconds: Boolean = true,
    @SerialName("show_date") val showDate: Boolean = true,
    @SerialName("show_ampm") val showAmPm: Boolean = false,
    @SerialName("language") val language: String = "vi",
    @SerialName("animation_enabled") val animationEnabled: Boolean = true,
    @SerialName("animation_speed") val animationSpeed: Float = 1.0f,
    @SerialName("accent_color") val accentColor: String = "#38BDF8",
    @SerialName("config_version") val configVersion: Int = 1,
    @SerialName("applied_by_station") val appliedByStation: Boolean = false,
    @SerialName("last_applied_style") val lastAppliedStyle: String = "digital_classic",
    @SerialName("last_error") val lastError: String? = null
)

@Serializable
data class ClockConfigResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("data") val data: ClockConfigDto? = null
)

@Serializable
data class ViewSwitchRequest(
    @SerialName("view") val view: String
)

@Serializable
data class LampColorRequest(
    @SerialName("hex") val hex: String
)

@Serializable
data class LampDimRequest(
    @SerialName("led") val led: Int? = null
)

@Serializable
data class LedControlRequest(
    @SerialName("action") val action: String
)

@Serializable
data class ClockStyleRequest(
    @SerialName("style") val style: String
)

@Serializable
data class BuzzRequest(
    @SerialName("duration") val duration: Int = 250
)

@Serializable
data class AlarmScheduleRequest(
    @SerialName("time") val time: String
)

@Serializable
data class StickyNoteRequest(
    @SerialName("note") val note: String
)

@Serializable
data class ExamCountdownRequest(
    @SerialName("name") val name: String,
    @SerialName("date") val date: String
)

@Serializable
data class QuoteGenerateRequest(
    @SerialName("topic") val topic: String = "học tập và kiên trì",
    @SerialName("limit") val limit: Int = 75,
    @SerialName("pin") val pin: Boolean = false
)

@Serializable
data class QuoteResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("quote") val quote: String = "",
    @SerialName("topic") val topic: String = "",
    @SerialName("char_limit") val charLimit: Int = 75
)

@Serializable
data class TimerStartRequest(
    @SerialName("duration") val duration: String = "25m",
    @SerialName("note") val note: String = "Tập trung học"
)

// --- Notes & Countdowns & Weather CRUD Models ---
@Serializable
data class NoteItemDto(
    @SerialName("id") val id: String,
    @SerialName("text") val text: String = "",
    @SerialName("content") val content: String = "",
    @SerialName("title") val title: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("color") val color: String? = "#FDE047",
    @SerialName("is_pinned") val isPinned: Boolean = false
) {
    val displayText: String
        get() = text.ifBlank { content }
}

@Serializable
data class NotesListResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("notes") val notes: List<NoteItemDto> = emptyList(),
    @SerialName("data") val data: List<NoteItemDto> = emptyList(),
    @SerialName("active_note_id") val activeNoteId: String? = null,
    @SerialName("active_station_note_id") val activeStationNoteId: String? = null
) {
    val allNotes: List<NoteItemDto>
        get() = if (notes.isNotEmpty()) notes else data
    val currentActiveNoteId: String?
        get() = activeStationNoteId ?: activeNoteId
}

@Serializable
data class CreateNoteRequest(
    @SerialName("text") val text: String,
    @SerialName("content") val content: String = text,
    @SerialName("color") val color: String = "#FDE047"
)

@Serializable
data class CountdownItemDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("target_date") val targetDate: String = "",
    @SerialName("days_left") val daysLeft: Int = 0,
    @SerialName("is_expired") val isExpired: Boolean = false,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    @SerialName("is_active_on_station") val isActiveOnStation: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
) {
    val displayName: String
        get() = name.ifBlank { title }
}

@Serializable
data class CountdownsListResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("countdowns") val countdowns: List<CountdownItemDto> = emptyList(),
    @SerialName("data") val data: List<CountdownItemDto> = emptyList(),
    @SerialName("active_countdown_id") val activeCountdownId: String? = null,
    @SerialName("active_station_countdown_id") val activeStationCountdownId: String? = null
) {
    val allCountdowns: List<CountdownItemDto>
        get() = if (countdowns.isNotEmpty()) countdowns else data
    val currentActiveId: String?
        get() = activeCountdownId ?: activeStationCountdownId
}

@Serializable
data class CreateCountdownRequest(
    @SerialName("name") val name: String,
    @SerialName("title") val title: String = name,
    @SerialName("target_date") val targetDate: String
)

@Serializable
data class WeatherIntervalDto(
    @SerialName("time") val time: String = "",
    @SerialName("dt_txt") val dtTxt: String = "",
    @SerialName("timestamp") val timestamp: Long = 0,
    @SerialName("dt") val dt: Long = 0,
    @SerialName("temp") val temp: Double = 0.0,
    @SerialName("description") val description: String = "",
    @SerialName("weather_desc") val weatherDesc: String = "",
    @SerialName("weather_main") val weatherMain: String = "",
    @SerialName("is_rain") val isRain: Boolean = false,
    @SerialName("rain_volume_mm") val rainVolumeMm: Double = 0.0,
    @SerialName("pop") val pop: Double = 0.0,
    @SerialName("rain_prob") val rainProb: Double = 0.0
) {
    val displayTime: String
        get() = time.ifBlank { dtTxt }
    val displayDescription: String
        get() = description.ifBlank { weatherDesc.ifBlank { weatherMain } }
    val effectivePop: Double
        get() = if (pop > 0.0) pop else (rainProb / 100.0)
}

@Serializable
data class WeatherForecastDataDto(
    @SerialName("city") val city: String = "",
    @SerialName("available") val available: Boolean = false,
    @SerialName("intervals") val intervals: List<WeatherIntervalDto> = emptyList(),
    @SerialName("error") val error: String? = null
)

@Serializable
data class WeatherForecastResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("city") val city: String = "",
    @SerialName("current_alert_time") val currentAlertTime: String? = null,
    @SerialName("intervals") val intervals: List<WeatherIntervalDto> = emptyList(),
    @SerialName("data") val data: WeatherForecastDataDto? = null,
    @SerialName("error") val error: String? = null
) {
    val allIntervals: List<WeatherIntervalDto>
        get() = if (intervals.isNotEmpty()) intervals else (data?.intervals ?: emptyList())
    val resolvedCity: String
        get() = city.ifBlank { data?.city ?: "" }
}

// --- Morse Code Models ---
@Serializable
data class MorsePlayRequest(
    @SerialName("pattern") val pattern: String,
    @SerialName("unit_ms") val unitMs: Int = 80,
    @SerialName("freq") val freq: Int = 2400
)

// --- Melody / MIDI Models ---
@Serializable
data class MelodyPlayRequest(
    @SerialName("preset_id") val presetId: String? = null,
    @SerialName("midi_base64") val midiBase64: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("filename") val filename: String? = null
)

// --- Google Calendar Models ---
@Serializable
data class GoogleCalendarSummaryDto(
    @SerialName("id") val id: String = "primary",
    @SerialName("summary") val summary: String = "Primary",
    @SerialName("primary") val primary: Boolean = false,
    @SerialName("access_role") val accessRole: String = "reader"
)

@Serializable
data class CalendarEventDto(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("date") val date: String = "",
    @SerialName("start_time") val startTime: String = "",
    @SerialName("end_time") val endTime: String = "",
    @SerialName("all_day") val allDay: Boolean = false,
    @SerialName("is_today") val isToday: Boolean = false,
    @SerialName("is_ongoing") val isOngoing: Boolean = false,
    @SerialName("location") val location: String = "",
    @SerialName("source") val source: String = "ical",
    @SerialName("can_delete") val canDelete: Boolean = false,
    @SerialName("calendar_id") val calendarId: String = "primary"
)

@Serializable
data class CalendarStatusDto(
    @SerialName("success") val success: Boolean = true,
    @SerialName("connected") val connected: Boolean = false,
    @SerialName("oauth_connected") val oauthConnected: Boolean = false,
    @SerialName("ical_connected") val icalConnected: Boolean = false,
    @SerialName("ical_url") val icalUrl: String = "",
    @SerialName("google_client_id") val googleClientId: String = "",
    @SerialName("has_google_client_secret") val hasGoogleClientSecret: Boolean = false,
    @SerialName("google_redirect_uri") val googleRedirectUri: String = "https://df.api.thetechdude.io.vn/api/app/calendar/oauth/callback",
    @SerialName("connected_email") val connectedEmail: String = "",
    @SerialName("selected_calendar_id") val selectedCalendarId: String = "primary",
    @SerialName("selected_calendar_name") val selectedCalendarName: String = "Primary",
    @SerialName("calendars") val calendars: List<GoogleCalendarSummaryDto> = emptyList(),
    @SerialName("last_error") val lastError: String? = null,
    @SerialName("events") val events: List<CalendarEventDto> = emptyList(),
    @SerialName("today_count") val todayCount: Int = 0
)

@Serializable
data class CalendarConfigRequest(
    @SerialName("ical_url") val icalUrl: String? = null,
    @SerialName("google_client_id") val googleClientId: String? = null,
    @SerialName("google_client_secret") val googleClientSecret: String? = null,
    @SerialName("google_redirect_uri") val googleRedirectUri: String? = null,
    @SerialName("selected_calendar_id") val selectedCalendarId: String? = null
)

@Serializable
data class CreateCalendarEventRequest(
    @SerialName("title") val title: String,
    @SerialName("date") val date: String,
    @SerialName("start_time") val startTime: String = "",
    @SerialName("end_time") val endTime: String = "",
    @SerialName("all_day") val allDay: Boolean = false,
    @SerialName("location") val location: String = ""
)

@Serializable
data class CalendarOAuthStartResponse(
    @SerialName("success") val success: Boolean = false,
    @SerialName("auth_url") val authUrl: String? = null,
    @SerialName("redirect_uri") val redirectUri: String? = null
)

