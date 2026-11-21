import Foundation

// MARK: - Scheduled Dark Mode Evaluator

public enum AppThemeMode: String, CaseIterable, Codable, Identifiable {
    case system = "system"
    case light = "light"
    case dark = "dark"
    case scheduled = "scheduled"

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .system: return "System Default"
        case .light: return "Light"
        case .dark: return "Dark"
        case .scheduled: return "Scheduled"
        }
    }
}

public struct ScheduledDarkModeEvaluator {
    public static func parseMinutes(_ timeStr: String, fallback: Int) -> Int {
        let trimmed = timeStr.trimmingCharacters(in: .whitespacesAndNewlines)
        let parts = trimmed.split(separator: ":")
        guard parts.count == 2,
              let hour = Int(parts[0]),
              let minute = Int(parts[1]),
              (0...23).contains(hour),
              (0...59).contains(minute) else {
            return fallback
        }
        return hour * 60 + minute
    }

    public static func normalizeTimeStr(_ raw: String?, fallback: String = "20:00") -> String {
        let fbMin = parseMinutes(fallback, fallback: 20 * 60)
        let mins = parseMinutes(raw ?? "", fallback: fbMin)
        return String(format: "%02d:%02d", mins / 60, mins % 60)
    }

    public static func isScheduledDarkActive(
        nowMinutes: Int,
        startStr: String = "20:00",
        endStr: String = "06:00",
        fallbackWhenEqual: Bool = false
    ) -> Bool {
        let startMin = parseMinutes(startStr, fallback: 20 * 60)
        let endMin = parseMinutes(endStr, fallback: 6 * 60)
        let current = ((nowMinutes % 1440) + 1440) % 1440

        if startMin == endMin {
            return fallbackWhenEqual
        }
        if startMin < endMin {
            // Same-day range, e.g. 09:00 -> 17:00
            return current >= startMin && current < endMin
        }
        // Overnight range across midnight, e.g. 20:00 -> 06:00
        return current >= startMin || current < endMin
    }

    public static func resolveDarkTheme(
        mode: AppThemeMode,
        systemIsDark: Bool,
        startStr: String,
        endStr: String,
        nowMinutes: Int
    ) -> Bool {
        switch mode {
        case .system:
            return systemIsDark
        case .light:
            return false
        case .dark:
            return true
        case .scheduled:
            return isScheduledDarkActive(
                nowMinutes: nowMinutes,
                startStr: startStr,
                endStr: endStr,
                fallbackWhenEqual: false
            )
        }
    }
}

// MARK: - Bidirectional ITU Morse Code Converter

public struct MorseCodeConverter {
    public static let charToMorse: [Character: String] = [
        "A": ".-", "B": "-...", "C": "-.-.", "D": "-..", "E": ".",
        "F": "..-.", "G": "--.", "H": "....", "I": "..", "J": ".---",
        "K": "-.-", "L": ".-..", "M": "--", "N": "-.", "O": "---",
        "P": ".--.", "Q": "--.-", "R": ".-.", "S": "...", "T": "-",
        "U": "..-", "V": "...-", "W": ".--", "X": "-..-", "Y": "-.--",
        "Z": "--..",
        "0": "-----", "1": ".----", "2": "..---", "3": "...--", "4": "....-",
        "5": ".....", "6": "-....", "7": "--...", "8": "---..", "9": "----.",
        ".": ".-.-.-", ",": "--..--", "?": "..--..", "!": "-.-.--",
        "-": "-....-", "/": "-..-.", "@": ".--.-."
    ]

    public static let morseToChar: [String: Character] = {
        var map: [String: Character] = [:]
        for (k, v) in charToMorse {
            map[v] = k
        }
        return map
    }()

    public static func stripDiacritics(_ text: String) -> String {
        let replaced = text
            .replacingOccurrences(of: "đ", with: "d")
            .replacingOccurrences(of: "Đ", with: "D")
        return replaced.folding(options: .diacriticInsensitive, locale: .current)
    }

    public static func textToMorse(_ text: String) -> String {
        let clean = stripDiacritics(text).uppercased().trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return "" }
        let words = clean.split(whereSeparator: { $0.isWhitespace })
        let encodedWords: [String] = words.compactMap { word in
            let letters = word.compactMap { charToMorse[$0] }
            return letters.isEmpty ? nil : letters.joined(separator: " ")
        }
        return encodedWords.joined(separator: " / ")
    }

    public static func morseToText(_ morse: String) -> String {
        let normalized = morse.replacingOccurrences(of: "|", with: "/").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else { return "" }
        let words = normalized.split(separator: "/")
        let decodedWords: [String] = words.compactMap { wordSlice in
            let tokens = wordSlice.split(whereSeparator: { $0.isWhitespace })
            guard !tokens.isEmpty else { return nil }
            let chars = tokens.map { String(morseToChar[String($0)] ?? "?") }
            return chars.joined()
        }
        return decodedWords.joined(separator: " ")
    }
}

// MARK: - API DTOs

public struct HealthResponse: Codable {
    public let status: String?
    public let service: String?
    public let version: String?
}

public struct PairRequest: Codable {
    public let pairingCode: String
    public let deviceName: String
    public let platform: String

    enum CodingKeys: String, CodingKey {
        case pairingCode = "pairing_code"
        case deviceName = "device_name"
        case platform
    }
}

public struct PairResponse: Codable {
    public let success: Bool?
    public let deviceToken: String?
    public let deviceId: String?
    public let deviceName: String?
    public let scopes: [String]?

    enum CodingKeys: String, CodingKey {
        case success
        case deviceToken = "device_token"
        case deviceId = "device_id"
        case deviceName = "device_name"
        case scopes
    }
}

public struct NoteItem: Codable, Identifiable, Equatable {
    public let id: String
    public let content: String?
    public let text: String?
    public let color: String?
    public let pinned: Bool?
    public let isPinned: Bool?
    public let isActiveOnStation: Bool?
    public let createdAt: String?

    enum CodingKeys: String, CodingKey {
        case id, content, text, color, pinned
        case isPinned = "is_pinned"
        case isActiveOnStation = "is_active_on_station"
        case createdAt = "created_at"
    }

    public var displayText: String {
        if let c = content, !c.isEmpty { return c }
        return text ?? ""
    }

    public var effectivePinned: Bool {
        return (pinned ?? false) || (isPinned ?? false) || (isActiveOnStation ?? false)
    }
}

public struct NotesListResponse: Codable {
    public let success: Bool?
    public let notes: [NoteItem]?
    public let data: [NoteItem]?
    public let deletedId: String?
    public let activeNoteId: String?
    public let activeStationNoteId: String?

    enum CodingKeys: String, CodingKey {
        case success, notes, data
        case deletedId = "deleted_id"
        case activeNoteId = "active_note_id"
        case activeStationNoteId = "active_station_note_id"
    }

    public var allNotes: [NoteItem] {
        if let n = notes { return n }
        return data ?? []
    }
}

public struct CountdownItem: Codable, Identifiable, Equatable {
    public let id: String
    public let name: String?
    public let title: String?
    public let targetDate: String?
    public let daysLeft: Int?
    public let isPinned: Bool?

    enum CodingKeys: String, CodingKey {
        case id, name, title
        case targetDate = "target_date"
        case daysLeft = "days_left"
        case isPinned = "is_pinned"
    }

    public var displayName: String {
        if let n = name, !n.isEmpty { return n }
        return title ?? "Event"
    }
}

public struct CountdownsListResponse: Codable {
    public let success: Bool?
    public let countdowns: [CountdownItem]?
    public let data: [CountdownItem]?

    public var allCountdowns: [CountdownItem] {
        if let c = countdowns { return c }
        return data ?? []
    }
}

public struct WeatherInterval: Codable, Identifiable, Equatable {
    public var id: String { "\(displayTime)-\(temp ?? 0)" }
    public let time: String?
    public let dtTxt: String?
    public let temp: Double?
    public let description: String?
    public let weatherDesc: String?
    public let pop: Double?
    public let rainProb: Double?
    public let isRain: Bool?

    enum CodingKeys: String, CodingKey {
        case time, temp, description, pop
        case dtTxt = "dt_txt"
        case weatherDesc = "weather_desc"
        case rainProb = "rain_prob"
        case isRain = "is_rain"
    }

    public var displayTime: String {
        if let t = time, !t.isEmpty { return t }
        return dtTxt ?? ""
    }

    public var displayDescription: String {
        if let d = description, !d.isEmpty { return d }
        return weatherDesc ?? ""
    }

    public var effectiveRainProb: Double {
        if let rp = rainProb { return rp }
        return (pop ?? 0.0) * 100.0
    }
}

public struct WeatherForecastNestedData: Codable {
    public let city: String?
    public let intervals: [WeatherInterval]?
}

public struct WeatherForecastResponse: Codable {
    public let success: Bool?
    public let city: String?
    public let intervals: [WeatherInterval]?
    public let data: WeatherForecastNestedData?

    public var allIntervals: [WeatherInterval] {
        if let iv = intervals, !iv.isEmpty { return iv }
        return data?.intervals ?? []
    }
}

public struct FocusChallengeDto: Codable {
    public let active: Bool?
    public let challengeId: String?
    public let question: String?
    public let expiresIn: Int?

    enum CodingKeys: String, CodingKey {
        case active
        case challengeId = "challenge_id"
        case question
        case expiresIn = "expires_in"
    }
}

public struct FocusStatusResponse: Codable {
    public let enabled: Bool?
    public let focusActive: Bool?
    public let version: Int?
    public let challenge: FocusChallengeDto?

    enum CodingKeys: String, CodingKey {
        case enabled
        case focusActive = "focus_active"
        case version
        case challenge
    }

    public var isActive: Bool {
        return (focusActive ?? false) || (enabled ?? false)
    }
}

public struct VerifyChallengeResponse: Codable {
    public let success: Bool?
    public let correct: Bool?
    public let focusActive: Bool?
    public let newChallenge: FocusChallengeDto?

    enum CodingKeys: String, CodingKey {
        case success, correct
        case focusActive = "focus_active"
        case newChallenge = "new_challenge"
    }
}

public struct AlarmItem: Codable, Identifiable, Equatable {
    public let id: String
    public let time: String?
    public let label: String?
    public let enabled: Bool?
    public let repeatDays: [Int]?
    public let buzzerDuration: Int?
    public let mathChallenge: Bool?

    enum CodingKeys: String, CodingKey {
        case id, time, label, enabled
        case repeatDays = "repeat_days"
        case buzzerDuration = "buzzer_duration"
        case mathChallenge = "math_challenge"
    }

    public var displayTime: String { time ?? "07:00" }
    public var displayLabel: String { label ?? "Alarm" }
    public var isEnabled: Bool { enabled ?? true }
    public var repeatSummary: String {
        guard let days = repeatDays, !days.isEmpty else { return "Once" }
        let names = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
        return days.compactMap { (0..<7).contains($0) ? names[$0] : nil }.joined(separator: ", ")
    }
}

public struct AlarmsListResponse: Codable {
    public let success: Bool?
    public let alarms: [AlarmItem]?
}

public struct PairedDeviceItem: Codable, Identifiable, Equatable {
    public let deviceId: String
    public let deviceName: String?
    public let platform: String?
    public let isCurrent: Bool?
    public let lastSeenAt: String?

    public var id: String { deviceId }

    enum CodingKeys: String, CodingKey {
        case deviceId = "device_id"
        case deviceName = "device_name"
        case platform
        case isCurrent = "is_current"
        case lastSeenAt = "last_seen_at"
    }
}

public struct DevicesListResponse: Codable {
    public let success: Bool?
    public let devices: [PairedDeviceItem]?
}

public struct SelectedDeviceResponse: Codable {
    public let success: Bool?
    public let selectedDeviceId: String?

    enum CodingKeys: String, CodingKey {
        case success
        case selectedDeviceId = "selected_device_id"
    }
}

// MARK: - Spotify Models

public struct SpotifyStatusDto: Codable, Equatable {
    public let isPlaying: Bool
    public let connected: Bool
    public let track: String?
    public let artist: String?
    public let album: String?
    public let progressMs: Int
    public let durationMs: Int
    public let progressPct: Int
    public let currentLyric: String?
    public let nextLyric: String?
    public let lyricsWindow: [String]
    public let activeLyricIndex: Int
    public let coverUrl: String?

    public init(
        isPlaying: Bool = false,
        connected: Bool = false,
        track: String? = nil,
        artist: String? = nil,
        album: String? = nil,
        progressMs: Int = 0,
        durationMs: Int = 1,
        progressPct: Int = 0,
        currentLyric: String? = nil,
        nextLyric: String? = nil,
        lyricsWindow: [String] = [],
        activeLyricIndex: Int = 2,
        coverUrl: String? = nil
    ) {
        self.isPlaying = isPlaying
        self.connected = connected
        self.track = track
        self.artist = artist
        self.album = album
        self.progressMs = progressMs
        self.durationMs = durationMs
        self.progressPct = progressPct
        self.currentLyric = currentLyric
        self.nextLyric = nextLyric
        self.lyricsWindow = lyricsWindow
        self.activeLyricIndex = activeLyricIndex
        self.coverUrl = coverUrl
    }

    enum CodingKeys: String, CodingKey {
        case isPlaying = "is_playing"
        case connected
        case track
        case artist
        case album
        case progressMs = "progress_ms"
        case durationMs = "duration_ms"
        case progressPct = "progress_pct"
        case currentLyric = "lyric_curr"
        case nextLyric = "lyric_next"
        case lyricsWindow = "lyrics_window"
        case activeLyricIndex = "active_lyric_index"
        case coverUrl = "cover_url"
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.isPlaying = try container.decodeIfPresent(Bool.self, forKey: .isPlaying) ?? false
        self.connected = try container.decodeIfPresent(Bool.self, forKey: .connected) ?? false
        self.track = try container.decodeIfPresent(String.self, forKey: .track)
        self.artist = try container.decodeIfPresent(String.self, forKey: .artist)
        self.album = try container.decodeIfPresent(String.self, forKey: .album)
        self.progressMs = try container.decodeIfPresent(Int.self, forKey: .progressMs) ?? 0
        self.durationMs = try container.decodeIfPresent(Int.self, forKey: .durationMs) ?? 1
        self.progressPct = try container.decodeIfPresent(Int.self, forKey: .progressPct) ?? 0
        self.currentLyric = try container.decodeIfPresent(String.self, forKey: .currentLyric)
        self.nextLyric = try container.decodeIfPresent(String.self, forKey: .nextLyric)
        self.lyricsWindow = try container.decodeIfPresent([String].self, forKey: .lyricsWindow) ?? []
        self.activeLyricIndex = try container.decodeIfPresent(Int.self, forKey: .activeLyricIndex) ?? 2
        self.coverUrl = try container.decodeIfPresent(String.self, forKey: .coverUrl)
    }
}

public struct SpotifyPlaybackRequest: Codable {
    public let command: String
    public init(command: String) { self.command = command }
}

public struct SpotifyVolumeRequest: Codable {
    public let volume: Int
    public init(volume: Int) { self.volume = volume }
}

// ================= PAIR SESSION MODELS =================

public struct PairSessionCreateRequest: Codable {
    public let deviceName: String
    public let platform: String

    enum CodingKeys: String, CodingKey {
        case deviceName = "device_name"
        case platform
    }

    public init(deviceName: String, platform: String = "macos") {
        self.deviceName = deviceName
        self.platform = platform
    }
}

public struct PairSessionCreateResponse: Codable {
    public let success: Bool?
    public let sessionId: String?
    public let qrPayload: String?
    public let expiresIn: Int?

    enum CodingKeys: String, CodingKey {
        case success
        case sessionId = "session_id"
        case qrPayload = "qr_payload"
        case expiresIn = "expires_in"
    }

    public init(success: Bool? = nil, sessionId: String? = nil, qrPayload: String? = nil, expiresIn: Int? = nil) {
        self.success = success
        self.sessionId = sessionId
        self.qrPayload = qrPayload
        self.expiresIn = expiresIn
    }
}

public struct PairSessionStatusResponse: Codable {
    public let success: Bool?
    public let status: String?
    public let deviceId: String?
    public let deviceToken: String?
    public let deviceName: String?
    public let scopes: [String]?
    public let pairedAt: String?

    enum CodingKeys: String, CodingKey {
        case success
        case status
        case deviceId = "device_id"
        case deviceToken = "device_token"
        case deviceName = "device_name"
        case scopes
        case pairedAt = "paired_at"
    }

    public init(
        success: Bool? = nil,
        status: String? = nil,
        deviceId: String? = nil,
        deviceToken: String? = nil,
        deviceName: String? = nil,
        scopes: [String]? = nil,
        pairedAt: String? = nil
    ) {
        self.success = success
        self.status = status
        self.deviceId = deviceId
        self.deviceToken = deviceToken
        self.deviceName = deviceName
        self.scopes = scopes
        self.pairedAt = pairedAt
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.success = try? container.decodeIfPresent(Bool.self, forKey: .success)
        self.status = try? container.decodeIfPresent(String.self, forKey: .status)
        self.deviceId = try? container.decodeIfPresent(String.self, forKey: .deviceId)
        self.deviceToken = try? container.decodeIfPresent(String.self, forKey: .deviceToken)
        self.deviceName = try? container.decodeIfPresent(String.self, forKey: .deviceName)
        self.scopes = try? container.decodeIfPresent([String].self, forKey: .scopes)
        if let str = try? container.decodeIfPresent(String.self, forKey: .pairedAt) {
            self.pairedAt = str
        } else if let dbl = try? container.decodeIfPresent(Double.self, forKey: .pairedAt) {
            self.pairedAt = String(dbl)
        } else {
            self.pairedAt = nil
        }
    }
}

// ================= TIMETABLE (TKB) MODELS =================

public struct DayScheduleDto: Codable, Equatable {
    public let day: String?
    public let displayDay: String?
    public let morning: [String]?
    public let afternoon: [String]?
    public let uniformMorning: String?
    public let uniformAfternoon: String?
    public let hasAfternoon: Bool?
    public let hasAnyClass: Bool?

    enum CodingKeys: String, CodingKey {
        case day
        case displayDay = "display_day"
        case morning
        case afternoon
        case uniformMorning = "dp_sang"
        case uniformAfternoon = "dp_chieu"
        case hasAfternoon = "has_afternoon"
        case hasAnyClass = "has_any_class"
    }

    public init(
        day: String? = nil,
        displayDay: String? = nil,
        morning: [String]? = nil,
        afternoon: [String]? = nil,
        uniformMorning: String? = nil,
        uniformAfternoon: String? = nil,
        hasAfternoon: Bool? = nil,
        hasAnyClass: Bool? = nil
    ) {
        self.day = day
        self.displayDay = displayDay
        self.morning = morning
        self.afternoon = afternoon
        self.uniformMorning = uniformMorning
        self.uniformAfternoon = uniformAfternoon
        self.hasAfternoon = hasAfternoon
        self.hasAnyClass = hasAnyClass
    }
}

public struct TimetableDataWrapper: Codable, Equatable {
    public let weekNumber: String?
    public let today: DayScheduleDto?
    public let tomorrow: DayScheduleDto?

    enum CodingKeys: String, CodingKey {
        case weekNumber = "week_number"
        case today
        case tomorrow
    }

    public init(weekNumber: String? = nil, today: DayScheduleDto? = nil, tomorrow: DayScheduleDto? = nil) {
        self.weekNumber = weekNumber
        self.today = today
        self.tomorrow = tomorrow
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        if let str = try? container.decode(String.self, forKey: .weekNumber) {
            self.weekNumber = str
        } else if let num = try? container.decode(Int.self, forKey: .weekNumber) {
            self.weekNumber = "\(num)"
        } else {
            self.weekNumber = nil
        }
        self.today = try? container.decodeIfPresent(DayScheduleDto.self, forKey: .today)
        self.tomorrow = try? container.decodeIfPresent(DayScheduleDto.self, forKey: .tomorrow)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(weekNumber, forKey: .weekNumber)
        try container.encodeIfPresent(today, forKey: .today)
        try container.encodeIfPresent(tomorrow, forKey: .tomorrow)
    }
}

public struct TimetableResponse: Codable {
    public let success: Bool?
    public let data: TimetableDataWrapper?
    public let error: String?

    enum CodingKeys: String, CodingKey {
        case success
        case data
        case error
    }

    public init(success: Bool? = nil, data: TimetableDataWrapper? = nil, error: String? = nil) {
        self.success = success
        self.data = data
        self.error = error
    }
}

public struct GoogleCalendarSummaryDto: Codable, Equatable, Identifiable {
    public let id: String
    public let summary: String
    public let primary: Bool?
    public let accessRole: String?

    enum CodingKeys: String, CodingKey {
        case id, summary, primary
        case accessRole = "access_role"
    }
}

public struct CalendarEventDto: Codable, Equatable, Identifiable {
    public let id: String
    public let title: String
    public let date: String
    public let startTime: String
    public let endTime: String
    public let allDay: Bool
    public let isToday: Bool
    public let isOngoing: Bool
    public let location: String
    public let source: String
    public let canDelete: Bool
    public let calendarId: String

    enum CodingKeys: String, CodingKey {
        case id, title, date, location, source
        case startTime = "start_time"
        case endTime = "end_time"
        case allDay = "all_day"
        case isToday = "is_today"
        case isOngoing = "is_ongoing"
        case canDelete = "can_delete"
        case calendarId = "calendar_id"
    }
}

public struct CalendarStatusDto: Codable, Equatable {
    public let connected: Bool
    public let oauthConnected: Bool
    public let icalConnected: Bool
    public let icalUrl: String
    public let googleClientId: String
    public let hasGoogleClientSecret: Bool
    public let googleRedirectUri: String
    public let connectedEmail: String
    public let selectedCalendarId: String
    public let selectedCalendarName: String
    public let calendars: [GoogleCalendarSummaryDto]
    public let lastError: String?
    public let events: [CalendarEventDto]
    public let todayCount: Int

    enum CodingKeys: String, CodingKey {
        case connected, calendars, events
        case oauthConnected = "oauth_connected"
        case icalConnected = "ical_connected"
        case icalUrl = "ical_url"
        case googleClientId = "google_client_id"
        case hasGoogleClientSecret = "has_google_client_secret"
        case googleRedirectUri = "google_redirect_uri"
        case connectedEmail = "connected_email"
        case selectedCalendarId = "selected_calendar_id"
        case selectedCalendarName = "selected_calendar_name"
        case lastError = "last_error"
        case todayCount = "today_count"
    }

    public init(
        connected: Bool = false,
        oauthConnected: Bool = false,
        icalConnected: Bool = false,
        icalUrl: String = "",
        googleClientId: String = "",
        hasGoogleClientSecret: Bool = false,
        googleRedirectUri: String = "https://10th012013.duckdns.org/api/app/calendar/oauth/callback",
        connectedEmail: String = "",
        selectedCalendarId: String = "primary",
        selectedCalendarName: String = "Primary",
        calendars: [GoogleCalendarSummaryDto] = [],
        lastError: String? = nil,
        events: [CalendarEventDto] = [],
        todayCount: Int = 0
    ) {
        self.connected = connected
        self.oauthConnected = oauthConnected
        self.icalConnected = icalConnected
        self.icalUrl = icalUrl
        self.googleClientId = googleClientId
        self.hasGoogleClientSecret = hasGoogleClientSecret
        self.googleRedirectUri = googleRedirectUri
        self.connectedEmail = connectedEmail
        self.selectedCalendarId = selectedCalendarId
        self.selectedCalendarName = selectedCalendarName
        self.calendars = calendars
        self.lastError = lastError
        self.events = events
        self.todayCount = todayCount
    }
}

