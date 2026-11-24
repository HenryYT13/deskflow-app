import Foundation
import Combine
import IOKit.ps
import Darwin

@MainActor
public final class DeskFlowApiClient: ObservableObject {
    public static let defaultBaseURL = "https://df.api.thetechdude.io.vn"

    private static let errorMessages: [String: String] = [
        "pair_code_invalid": "Invalid, expired, or already-used pairing code. Generate a new code in Discord (/pair).",
        "invalid_request": "Invalid request rejected by server (check endpoint URL and parameters).",
        "rate_limited": "Too many attempts. Please wait before trying again.",
        "cooldown": "Cooldown active due to repeated failed attempts.",
        "token_invalid": "Device session expired or revoked. Please pair again.",
        "insufficient_scope": "This device lacks required scope permissions (HTTP 403).",
        "storage_unavailable": "Backend storage is temporarily unavailable (HTTP 503)."
    ]

    private let session: URLSession
    private let defaults: UserDefaults

    @Published public var apiUrl: String {
        didSet { defaults.set(apiUrl, forKey: "deskflow.apiUrl") }
    }
    @Published public var deviceToken: String? {
        didSet { defaults.set(deviceToken, forKey: "deskflow.deviceToken") }
    }
    @Published public var deviceId: String? {
        didSet { defaults.set(deviceId, forKey: "deskflow.deviceId") }
    }
    @Published public var deviceName: String {
        didSet { defaults.set(deviceName, forKey: "deskflow.deviceName") }
    }
    @Published public var themeMode: AppThemeMode {
        didSet { defaults.set(themeMode.rawValue, forKey: "deskflow.themeMode") }
    }
    @Published public var darkModeStart: String {
        didSet {
            let norm = ScheduledDarkModeEvaluator.normalizeTimeStr(darkModeStart, fallback: "20:00")
            defaults.set(norm, forKey: "deskflow.darkModeStart")
        }
    }
    @Published public var darkModeEnd: String {
        didSet {
            let norm = ScheduledDarkModeEvaluator.normalizeTimeStr(darkModeEnd, fallback: "06:00")
            defaults.set(norm, forKey: "deskflow.darkModeEnd")
        }
    }
    private var suppressLanguageSync: Bool = false
    private var lastServerLanguage: String? = nil

    @Published public var appLanguage: String {
        didSet {
            defaults.set(appLanguage, forKey: "deskflow.language")
            L10n.lang = appLanguage
            if !suppressLanguageSync && oldValue != appLanguage {
                let lang = appLanguage
                lastServerLanguage = lang
                Task { await setClockLanguage(lang) }
            }
        }
    }
    @Published public var esp32ThemeMode: String {
        didSet {
            defaults.set(esp32ThemeMode, forKey: "deskflow.esp32ThemeMode")
            Task { await setEsp32Theme(mode: esp32ThemeMode) }
        }
    }
    @Published public var clockAccentColor: String {
        didSet { defaults.set(clockAccentColor, forKey: "deskflow.clockAccentColor") }
    }

    // Published domain state
    @Published public private(set) var timetableData: TimetableDataWrapper?
    @Published public private(set) var isFetchingTimetable: Bool = false
    @Published public private(set) var calendarState: CalendarStatusDto = CalendarStatusDto()
    @Published public private(set) var isSyncingCalendar: Bool = false
    @Published public private(set) var notes: [NoteItem] = []
    @Published public private(set) var deletingNoteIds: Set<String> = []
    @Published public private(set) var countdowns: [CountdownItem] = []
    @Published public private(set) var alarms: [AlarmItem] = []
    @Published public private(set) var pairedDevices: [PairedDeviceItem] = []
    @Published public private(set) var selectedDisplayDeviceId: String?
    @Published public private(set) var weatherIntervals: [WeatherInterval] = []
    @Published public private(set) var focusActive: Bool = false
    @Published public private(set) var activeChallengeId: String?
    @Published public private(set) var activeChallengeQuestion: String?
    @Published public var statusBannerMessage: String?
    @Published public var errorBannerMessage: String?
    @Published public private(set) var healthStatusText: String = "Not tested yet"

    // Live Ecosystem Status for ESP32, Server, and Computer
    @Published public private(set) var esp32BatteryPct: Int = 100
    @Published public private(set) var esp32Voltage: Double = 4.12
    @Published public private(set) var esp32WifiRssi: Int = -55
    @Published public private(set) var esp32CurrentView: String = "main"

    // Published Spotify State
    @Published public private(set) var spotifyState: SpotifyStatusDto = SpotifyStatusDto()

    @Published public private(set) var serverCpu: Double = 0.0
    @Published public private(set) var serverRamPct: Double = 0.0
    @Published public private(set) var serverDiskStr: String = "13.2 / 19.1GB"
    @Published public private(set) var serverUptime: String = "N/A"

    @Published public private(set) var compDeviceName: String = "MacBook"
    @Published public private(set) var compCpu: Double = 0.0
    @Published public private(set) var compRam: Double = 0.0
    @Published public private(set) var compBattery: Int = 100
    @Published public private(set) var compIsOnline: Bool = true

    public var isPaired: Bool {
        guard let token = deviceToken else { return false }
        return !token.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    public init(session: URLSession = .shared, defaults: UserDefaults = .standard) {
        self.session = session
        self.defaults = defaults
        let storedUrl = defaults.string(forKey: "deskflow.apiUrl")
        if storedUrl == nil || storedUrl!.isEmpty || storedUrl!.contains("10th012013.duckdns.org") {
            self.apiUrl = Self.defaultBaseURL
            defaults.set(Self.defaultBaseURL, forKey: "deskflow.apiUrl")
        } else {
            self.apiUrl = storedUrl!
        }
        self.deviceToken = defaults.string(forKey: "deskflow.deviceToken")
        let storedId = defaults.string(forKey: "deskflow.deviceId")
        if let sid = storedId, !sid.isEmpty {
            self.deviceId = sid
        } else {
            let cleanHost = (Host.current().localizedName ?? "client")
                .folding(options: .diacriticInsensitive, locale: .current)
                .components(separatedBy: CharacterSet.alphanumerics.inverted)
                .joined(separator: "_")
                .lowercased()
            let generated = "mac_\(cleanHost)"
            self.deviceId = generated
            defaults.set(generated, forKey: "deskflow.deviceId")
        }
        let storedName = defaults.string(forKey: "deskflow.deviceName")
        if let sn = storedName, !sn.isEmpty {
            self.deviceName = sn
        } else {
            let name = Host.current().localizedName ?? "DeskFlow macOS"
            self.deviceName = name
            defaults.set(name, forKey: "deskflow.deviceName")
        }
        let rawTheme = defaults.string(forKey: "deskflow.themeMode") ?? AppThemeMode.system.rawValue
        self.themeMode = AppThemeMode(rawValue: rawTheme) ?? .system
        self.darkModeStart = ScheduledDarkModeEvaluator.normalizeTimeStr(
            defaults.string(forKey: "deskflow.darkModeStart"),
            fallback: "20:00"
        )
        self.darkModeEnd = ScheduledDarkModeEvaluator.normalizeTimeStr(
            defaults.string(forKey: "deskflow.darkModeEnd"),
            fallback: "06:00"
        )
        let initialLang = defaults.string(forKey: "deskflow.language") ?? "vi"
        self.appLanguage = initialLang
        L10n.lang = initialLang
        self.esp32ThemeMode = defaults.string(forKey: "deskflow.esp32ThemeMode") ?? "sync"
        self.clockAccentColor = defaults.string(forKey: "deskflow.clockAccentColor") ?? "#38BDF8"
    }

    private func logDiagnostic(_ message: String) {
        let ts = ISO8601DateFormatter().string(from: Date())
        FileHandle.standardError.write(Data("[\(ts)] [DeskFlow.macOS] \(message)\n".utf8))
    }

    private func buildRequest(
        path: String,
        method: String = "GET",
        body: [String: Any]? = nil,
        authenticated: Bool = true
    ) throws -> URLRequest {
        let cleanBase = apiUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/ "))
        guard let url = URL(string: "\(cleanBase)\(path)") else {
            throw URLError(.badURL)
        }
        var req = URLRequest(url: url, timeoutInterval: 10.0)
        req.cachePolicy = .reloadIgnoringLocalAndRemoteCacheData
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
        req.setValue("no-cache", forHTTPHeaderField: "Pragma")
        req.setValue("DeskFlow-macOS-SwiftUI/2.2.0", forHTTPHeaderField: "User-Agent")
        let hasAuth = authenticated && !(deviceToken ?? "").isEmpty
        if hasAuth, let token = deviceToken {
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let body = body {
            req.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        let bodyKeys = body?.keys.sorted() ?? []
        logDiagnostic("[HTTP] -> \(method) \(url.absoluteString) (auth=\(hasAuth ? "bearer" : "none"), keys=\(bodyKeys))")
        return req
    }

    public static func parseErrorMessage(statusCode: Int, data: Data) -> (code: String, message: String) {
        if let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            if let errDict = obj["error"] as? [String: Any],
               let code = errDict["code"] as? String {
                let explicit = (errDict["message"] as? String) ?? (obj["message"] as? String)
                let msg = explicit ?? errorMessages[code] ?? "Server error: \(code) (HTTP \(statusCode))"
                return (code, msg)
            }
            if let errStr = obj["error"] as? String {
                let explicit = (obj["message"] as? String) ?? (obj["detail"] as? String)
                let msg = explicit ?? errorMessages[errStr] ?? "\(errStr) (HTTP \(statusCode))"
                return (errStr, msg)
            }
            if let detail = obj["detail"] as? String {
                return ("http_\(statusCode)", detail)
            }
        }
        return ("http_\(statusCode)", "HTTP Error \(statusCode)")
    }

    // MARK: - Health & Pairing (/api/app/health & /api/focus/pair)

    @discardableResult
    public func testHealth() async -> Bool {
        let start = Date()
        do {
            let req = try buildRequest(path: "/api/app/health", authenticated: false)
            let (data, response) = try await session.data(for: req)
            let latencyMs = Int(Date().timeIntervalSince(start) * 1000)
            guard let http = response as? HTTPURLResponse else { return false }
            logDiagnostic("[HTTP] <- GET /api/app/health status=\(http.statusCode) latency=\(latencyMs)ms")
            if http.statusCode == 200,
               let decoded = try? JSONDecoder().decode(HealthResponse.self, from: data) {
                healthStatusText = "ONLINE • HTTP 200 • \(latencyMs) ms • \(decoded.service ?? "deskflow") v\(decoded.version ?? "1.0.0")"
                errorBannerMessage = nil
                return true
            } else {
                healthStatusText = "HTTP \(http.statusCode) (\(latencyMs) ms)"
                return false
            }
        } catch {
            healthStatusText = "OFFLINE: \(error.localizedDescription)"
            logDiagnostic("[HTTP] Health check failed: \(error.localizedDescription)")
            return false
        }
    }

    @discardableResult
    public func pairDevice(code: String) async -> Bool {
        let clean = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        logDiagnostic("[Pairing] Validating pairing code (length=\(clean.count))")
        guard !clean.isEmpty else {
            errorBannerMessage = "Pairing code cannot be empty. Generate a code in Discord (/pair)."
            logDiagnostic("[Pairing] Rejected empty pairing code.")
            return false
        }
        let isAlnum = clean.unicodeScalars.allSatisfy { CharacterSet.alphanumerics.contains($0) }
        guard clean.count >= 6 && isAlnum else {
            errorBannerMessage = "Pairing code must be at least 6 alphanumeric characters."
            logDiagnostic("[Pairing] Rejected invalid pairing code format (length=\(clean.count)).")
            return false
        }
        do {
            let req = try buildRequest(
                path: "/api/focus/pair",
                method: "POST",
                body: [
                    "pairing_code": clean,
                    "code": clean,
                    "device_name": deviceName,
                    "platform": "macos",
                    "client_version": "2.2.0"
                ],
                authenticated: false
            )
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse else { return false }
            logDiagnostic("[HTTP] <- POST /api/focus/pair status=\(http.statusCode) bytes=\(data.count)")

            if http.statusCode == 200 {
                guard let decoded = try? JSONDecoder().decode(PairResponse.self, from: data),
                      let token = decoded.deviceToken, !token.isEmpty,
                      let id = decoded.deviceId, !id.isEmpty else {
                    errorBannerMessage = "Malformed pairing response from server (missing device_token or device_id)."
                    logDiagnostic("[Pairing] Server 200 response missing device_token or device_id.")
                    return false
                }
                deviceToken = token
                deviceId = id
                if let name = decoded.deviceName, !name.isEmpty {
                    deviceName = name
                }
                statusBannerMessage = "Paired with DeskFlow backend!"
                errorBannerMessage = nil
                logDiagnostic("[Pairing] Successfully paired device_id=\(id)")
                await refreshAll()
                return true
            } else {
                let parsedErr = Self.parseErrorMessage(statusCode: http.statusCode, data: data)
                errorBannerMessage = parsedErr.message
                logDiagnostic("[Pairing] Failed status=\(http.statusCode) code=\(parsedErr.code) msg=\(parsedErr.message)")
                return false
            }
        } catch {
            errorBannerMessage = "Server unreachable: \(error.localizedDescription)"
            logDiagnostic("[Pairing] Network error: \(error.localizedDescription)")
            return false
        }
    }

    public func unpairDevice() async {
        var body: [String: Any] = [:]
        if let did = deviceId { body["device_id"] = did }
        if let req = try? buildRequest(path: "/api/focus/unpair", method: "POST", body: body) {
            _ = try? await session.data(for: req)
        }
        deviceToken = nil
        deviceId = nil
        statusBannerMessage = "Device unpaired."
    }

    // MARK: - Dynamic Pairing Session (QR & Polling)

    public func createPairingSession() async -> PairSessionCreateResponse? {
        do {
            let req = try buildRequest(
                path: "/api/focus/pair/session/create",
                method: "POST",
                body: [
                    "device_name": deviceName,
                    "platform": "macos"
                ],
                authenticated: false
            )
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse else { return nil }
            logDiagnostic("[PairingSession] <- POST /api/focus/pair/session/create status=\(http.statusCode)")
            if http.statusCode == 200 {
                return try? JSONDecoder().decode(PairSessionCreateResponse.self, from: data)
            }
            return nil
        } catch {
            logDiagnostic("[PairingSession] Create network error: \(error.localizedDescription)")
            return nil
        }
    }

    public func pollPairingSession(sessionId: String) async -> PairSessionStatusResponse? {
        let cleanId = sessionId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanId.isEmpty else { return nil }
        do {
            let req = try buildRequest(
                path: "/api/focus/pair/session/\(cleanId)",
                method: "GET",
                authenticated: false
            )
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse else { return nil }
            if http.statusCode == 200 {
                do {
                    let decoded = try JSONDecoder().decode(PairSessionStatusResponse.self, from: data)
                    if decoded.status == "approved",
                       let token = decoded.deviceToken, !token.isEmpty,
                       let did = decoded.deviceId, !did.isEmpty {
                        self.deviceToken = token
                        self.deviceId = did
                        if let dname = decoded.deviceName, !dname.isEmpty {
                            self.deviceName = dname
                        }
                        self.statusBannerMessage = appLanguage == "vi" ? "Ghép nối thành công!" : "Paired with DeskFlow backend!"
                        self.errorBannerMessage = nil
                        logDiagnostic("[PairingSession] Approved and credentials saved for device_id=\(did)")
                        Task { await self.refreshAll() }
                    }
                    return decoded
                } catch {
                    logDiagnostic("[PairingSession] JSON decode error: \(error.localizedDescription)")
                    return nil
                }
            }
            return nil
        } catch {
            return nil
        }
    }

    // MARK: - Notes CRUD & Authoritative Deletion (/api/app/control/notes)

    public func fetchNotes() async {
        do {
            let req = try buildRequest(path: "/api/app/control/notes")
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse else { return }
            if http.statusCode == 401 {
                deviceToken = nil
                return
            }
            guard http.statusCode == 200 else { return }
            let decoded = try JSONDecoder().decode(NotesListResponse.self, from: data)
            self.notes = decoded.allNotes
        } catch {
            errorBannerMessage = "Could not load notes: \(error.localizedDescription)"
        }
    }

    public func createNote(text: String, color: String = "#FDE047") async {
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else {
            errorBannerMessage = "Note content cannot be empty."
            return
        }
        do {
            let req = try buildRequest(
                path: "/api/app/control/notes",
                method: "POST",
                body: ["text": clean, "content": clean, "color": color]
            )
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse else { return }
            if http.statusCode == 200,
               let decoded = try? JSONDecoder().decode(NotesListResponse.self, from: data) {
                self.notes = decoded.allNotes
                statusBannerMessage = "Sticky note created."
                errorBannerMessage = nil
            } else {
                errorBannerMessage = Self.parseErrorMessage(statusCode: http.statusCode, data: data).message
            }
        } catch {
            errorBannerMessage = "Create note error: \(error.localizedDescription)"
        }
    }

    @discardableResult
    public func deleteNote(noteId: String) async -> Bool {
        let cleanId = noteId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanId.isEmpty else {
            errorBannerMessage = "Note ID cannot be empty."
            return false
        }
        guard !deletingNoteIds.contains(cleanId) else {
            return false
        }
        deletingNoteIds.insert(cleanId)
        defer { deletingNoteIds.remove(cleanId) }

        do {
            let encoded = cleanId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? cleanId
            let req = try buildRequest(path: "/api/app/control/notes/\(encoded)", method: "DELETE")
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse else { return false }

            if http.statusCode == 200,
               let decoded = try? JSONDecoder().decode(NotesListResponse.self, from: data) {
                self.notes = decoded.allNotes
                statusBannerMessage = "Sticky note deleted."
                errorBannerMessage = nil
                return true
            } else {
                if http.statusCode == 401 {
                    deviceToken = nil
                }
                errorBannerMessage = Self.parseErrorMessage(statusCode: http.statusCode, data: data).message
                return false
            }
        } catch {
            errorBannerMessage = "Network error deleting note: \(error.localizedDescription)"
            return false
        }
    }

    public func pinNote(noteId: String) async {
        let encoded = noteId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? noteId
        do {
            let req = try buildRequest(path: "/api/app/control/notes/\(encoded)/pin", method: "POST", body: [:])
            let (_, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                statusBannerMessage = "Note pinned to ESP32 OLED!"
                await fetchNotes()
            }
        } catch {
            errorBannerMessage = "Failed to pin note: \(error.localizedDescription)"
        }
    }

    public func unpinNote() async {
        do {
            let req = try buildRequest(path: "/api/app/control/notes/unpin", method: "POST", body: [:])
            let (_, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                statusBannerMessage = "Unpinned note from ESP32 OLED."
                await fetchNotes()
            }
        } catch {
            errorBannerMessage = "Failed to unpin note: \(error.localizedDescription)"
        }
    }

    // MARK: - Countdowns

    public func fetchCountdowns() async {
        do {
            let req = try buildRequest(path: "/api/app/control/countdowns")
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return }
            let decoded = try JSONDecoder().decode(CountdownsListResponse.self, from: data)
            self.countdowns = decoded.allCountdowns
        } catch {}
    }

    public func createCountdown(name: String, targetDate: String) async {
        do {
            let req = try buildRequest(
                path: "/api/app/control/countdowns",
                method: "POST",
                body: ["name": name, "title": name, "target_date": targetDate]
            )
            let (data, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200,
               let decoded = try? JSONDecoder().decode(CountdownsListResponse.self, from: data) {
                self.countdowns = decoded.allCountdowns
                statusBannerMessage = "Countdown created."
            }
        } catch {
            errorBannerMessage = "Create countdown failed: \(error.localizedDescription)"
        }
    }

    public func deleteCountdown(countdownId: String) async {
        let encoded = countdownId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? countdownId
        do {
            let req = try buildRequest(path: "/api/app/control/countdowns/\(encoded)", method: "DELETE")
            let (data, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200,
               let decoded = try? JSONDecoder().decode(CountdownsListResponse.self, from: data) {
                self.countdowns = decoded.allCountdowns
                statusBannerMessage = "Countdown deleted."
            }
        } catch {
            errorBannerMessage = "Delete countdown failed: \(error.localizedDescription)"
        }
    }

    // MARK: - Weather, Focus (/api/focus/*), Morse & Station Control

    public func fetchWeatherForecast(city: String? = nil) async {
        var path = "/api/app/control/weather/forecast"
        if let c = city, !c.isEmpty,
           let q = c.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) {
            path += "?city=\(q)"
        }
        do {
            let req = try buildRequest(path: path, authenticated: false)
            let (data, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200,
               let decoded = try? JSONDecoder().decode(WeatherForecastResponse.self, from: data) {
                self.weatherIntervals = decoded.allIntervals
            }
        } catch {}
    }

    public func fetchFocusStatus() async {
        do {
            let req = try buildRequest(path: "/api/focus/status")
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse else { return }
            if http.statusCode == 401 {
                deviceToken = nil
                return
            }
            if http.statusCode == 200,
               let decoded = try? JSONDecoder().decode(FocusStatusResponse.self, from: data) {
                focusActive = decoded.isActive
            }
        } catch {}
    }

    public func startFocus(durationMinutes: Int, reason: String) async {
        do {
            let req = try buildRequest(
                path: "/api/focus/enable",
                method: "POST",
                body: [
                    "duration_minutes": durationMinutes,
                    "break_minutes": 5,
                    "session_type": "focus",
                    "reason": reason,
                    "language": "vi"
                ]
            )
            let (_, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                focusActive = true
                statusBannerMessage = "Focus Mode active (\(durationMinutes) min)."
            }
        } catch {
            errorBannerMessage = "Start Focus failed: \(error.localizedDescription)"
        }
    }

    public func requestChallenge() async {
        do {
            let req = try buildRequest(
                path: "/api/focus/challenge",
                method: "POST",
                body: ["language": "vi", "force_new": true]
            )
            let (data, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200,
               let ch = try? JSONDecoder().decode(FocusChallengeDto.self, from: data) {
                activeChallengeId = ch.challengeId
                activeChallengeQuestion = ch.question
            }
        } catch {
            errorBannerMessage = "Challenge error: \(error.localizedDescription)"
        }
    }

    public func verifyChallenge(answer: Int) async {
        guard let cid = activeChallengeId else { return }
        do {
            let req = try buildRequest(
                path: "/api/focus/verify",
                method: "POST",
                body: ["challenge_id": cid, "answer": answer, "language": "vi"]
            )
            let (data, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200,
               let decoded = try? JSONDecoder().decode(VerifyChallengeResponse.self, from: data) {
                if decoded.correct == true {
                    focusActive = false
                    activeChallengeId = nil
                    activeChallengeQuestion = nil
                    statusBannerMessage = "Correct! Focus Mode unlocked."
                } else {
                    activeChallengeId = decoded.newChallenge?.challengeId
                    activeChallengeQuestion = decoded.newChallenge?.question
                    errorBannerMessage = "Incorrect answer. New challenge issued."
                }
            }
        } catch {
            errorBannerMessage = "Verify error: \(error.localizedDescription)"
        }
    }

    public func playMorse(pattern: String, unitMs: Int = 60, freq: Int = 1000) async {
        do {
            let req = try buildRequest(
                path: "/api/app/control/morse/play",
                method: "POST",
                body: ["pattern": pattern, "unit_ms": unitMs, "freq": freq]
            )
            let (_, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                statusBannerMessage = "Playing Morse pattern on ESP32 Station."
            }
        } catch {
            errorBannerMessage = "Morse play failed: \(error.localizedDescription)"
        }
    }

    public func stopMorse() async {
        if let req = try? buildRequest(path: "/api/app/control/morse/stop", method: "POST", body: [:]) {
            _ = try? await session.data(for: req)
            statusBannerMessage = "Stopped Morse playback."
        }
    }

    public func playMelodyPreset(presetId: String) async {
        do {
            let req = try buildRequest(
                path: "/api/app/control/melody/play",
                method: "POST",
                body: ["preset_id": presetId]
            )
            let (_, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                statusBannerMessage = appLanguage == "vi" ? "Đang phát nhạc trên còi ESP32." : "Playing melody on ESP32 buzzer."
            }
        } catch {
            errorBannerMessage = "Lỗi phát nhạc: \(error.localizedDescription)"
        }
    }

    public func playMidiFile(data: Data, filename: String) async {
        do {
            let b64 = data.base64EncodedString()
            let title = filename.replacingOccurrences(of: ".mid", with: "").replacingOccurrences(of: ".midi", with: "")
            let req = try buildRequest(
                path: "/api/app/control/melody/play",
                method: "POST",
                body: ["midi_base64": b64, "filename": filename, "title": title]
            )
            let (_, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                statusBannerMessage = appLanguage == "vi" ? "Đang phát file MIDI: \(filename) trên ESP32." : "Playing MIDI: \(filename) on ESP32."
            }
        } catch {
            errorBannerMessage = "Lỗi gửi file MIDI: \(error.localizedDescription)"
        }
    }

    public func stopMelody() async {
        if let req = try? buildRequest(path: "/api/app/control/melody/stop", method: "POST", body: [:]) {
            _ = try? await session.data(for: req)
            statusBannerMessage = appLanguage == "vi" ? "Đã dừng phát nhạc trên ESP32." : "Stopped melody playback."
        }
    }

    public static let clockStyleIdToCode: [Int: String] = [
        1: "digital_classic",
        2: "digital_minimal",
        3: "digital_neon",
        4: "analog",
        5: "flip",
        6: "binary",
        7: "word"
    ]

    public func switchDisplayView(view: String) async {
        if let req = try? buildRequest(
            path: "/api/app/control/display/view",
            method: "POST",
            body: ["view": view],
            authenticated: false
        ) {
            _ = try? await session.data(for: req)
            statusBannerMessage = appLanguage == "vi" ? "Đã chuyển màn hình ESP32 sang \(view)." : "Switched ESP32 OLED to \(view)."
        }
    }

    public func setClockStyle(style: String, accentColor: String? = nil) async {
        let code = style.trimmingCharacters(in: .whitespacesAndNewlines)
        let color = accentColor ?? clockAccentColor
        let body: [String: Any] = [
            "style": code,
            "accent_color": color
        ]
        guard let req = try? buildRequest(
            path: "/api/app/control/clock-style",
            method: "POST",
            body: body,
            authenticated: false
        ) else { return }
        do {
            let (_, res) = try await session.data(for: req)
            if let http = res as? HTTPURLResponse, http.statusCode == 200 {
                statusBannerMessage = appLanguage == "vi" ? "Đã áp dụng mặt đồng hồ: \(code)" : "Applied clock style \(code)."
            } else {
                let codeErr = (res as? HTTPURLResponse)?.statusCode ?? -1
                errorBannerMessage = "Lỗi đổi mặt đồng hồ (HTTP \(codeErr))"
            }
        } catch {
            errorBannerMessage = "Lỗi kết nối khi đổi mặt đồng hồ: \(error.localizedDescription)"
        }
    }

    public func setClockStyle(style: Int, accentColor: String? = nil) async {
        let code = Self.clockStyleIdToCode[style] ?? "digital_classic"
        await setClockStyle(style: code, accentColor: accentColor)
    }

    public func setClockLanguage(_ language: String) async {
        let lang = language.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().hasPrefix("en") ? "en" : "vi"
        guard let req = try? buildRequest(
            path: "/api/app/control/clock-style",
            method: "POST",
            body: ["language": lang],
            authenticated: false
        ) else { return }
        _ = try? await session.data(for: req)
    }

    public func fetchClockLanguage() async -> String? {
        guard let req = try? buildRequest(path: "/api/app/control/clock-style", method: "GET", authenticated: false) else { return nil }
        do {
            let (data, res) = try await session.data(for: req)
            if let http = res as? HTTPURLResponse, http.statusCode == 200,
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                let payload = (json["data"] as? [String: Any]) ?? json
                if let lang = payload["language"] as? String {
                    let norm = lang.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                    if norm == "vi" || norm == "en" { return norm }
                }
            }
        } catch {}
        return nil
    }

    public func setClockAccentColor(_ hex: String) async {
        let clean = hex.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        self.clockAccentColor = clean
        guard let req = try? buildRequest(
            path: "/api/app/control/clock-style",
            method: "POST",
            body: ["accent_color": clean],
            authenticated: false
        ) else { return }
        _ = try? await session.data(for: req)
        statusBannerMessage = appLanguage == "vi" ? "Đã đổi màu nhấn đồng hồ: \(clean)" : "Clock accent color updated to \(clean)."
    }

    // MARK: - Timetable (/api/app/control/timetable/today)

    public func fetchTimetable() async {
        isFetchingTimetable = true
        defer { isFetchingTimetable = false }
        do {
            let req = try buildRequest(path: "/api/app/control/timetable/today", method: "GET", authenticated: true)
            let (data, response) = try await session.data(for: req)
            guard let http = response as? HTTPURLResponse else { return }
            if http.statusCode == 200 {
                let decoded = try JSONDecoder().decode(TimetableResponse.self, from: data)
                self.timetableData = decoded.data
                logDiagnostic("[Timetable] Successfully loaded schedule for week \(decoded.data?.weekNumber ?? "N/A")")
            } else {
                logDiagnostic("[Timetable] Server returned status \(http.statusCode)")
            }
        } catch {
            logDiagnostic("[Timetable] Fetch error: \(error.localizedDescription)")
        }
    }

    public func refreshAll() async {
        await fetchFocusStatus()
        await fetchNotes()
        await fetchCountdowns()
        await fetchWeatherForecast()
        await fetchAlarms()
        await fetchPairedDevices()
        await fetchSelectedDisplayDevice()
        await fetchSystemStatus()
        await fetchSpotifyStatus()
        await fetchTimetable()
        await fetchCalendarStatus()
    }

    // MARK: - Google Calendar (/api/app/calendar/*)

    public func fetchCalendarStatus() async {
        guard let req = try? buildRequest(path: "/api/app/calendar/status", method: "GET", authenticated: true) else { return }
        do {
            let (data, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200,
               let decoded = try? JSONDecoder().decode(CalendarStatusDto.self, from: data) {
                self.calendarState = decoded
            }
        } catch {}
    }

    public func syncCalendar() async {
        isSyncingCalendar = true
        defer { isSyncingCalendar = false }
        guard let req = try? buildRequest(path: "/api/app/calendar/sync", method: "POST", body: [:], authenticated: true) else { return }
        do {
            let (data, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200,
               let decoded = try? JSONDecoder().decode(CalendarStatusDto.self, from: data) {
                self.calendarState = decoded
                statusBannerMessage = appLanguage == "vi" ? "Đã đồng bộ Google Calendar!" : "Google Calendar synced!"
            } else {
                errorBannerMessage = "Sync calendar failed"
            }
        } catch {
            errorBannerMessage = error.localizedDescription
        }
    }

    public func updateCalendarConfig(
        icalUrl: String? = nil,
        googleClientId: String? = nil,
        googleClientSecret: String? = nil,
        googleRedirectUri: String? = nil,
        selectedCalendarId: String? = nil
    ) async {
        var body: [String: Any] = [:]
        if let v = icalUrl { body["ical_url"] = v }
        if let v = googleClientId { body["google_client_id"] = v }
        if let v = googleClientSecret { body["google_client_secret"] = v }
        if let v = googleRedirectUri { body["google_redirect_uri"] = v }
        if let v = selectedCalendarId { body["selected_calendar_id"] = v }

        guard let req = try? buildRequest(path: "/api/app/calendar/config", method: "POST", body: body, authenticated: true) else { return }
        do {
            let (data, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200,
               let decoded = try? JSONDecoder().decode(CalendarStatusDto.self, from: data) {
                self.calendarState = decoded
                statusBannerMessage = appLanguage == "vi" ? "Đã lưu cấu hình Google Calendar!" : "Saved Google Calendar settings!"
            } else {
                errorBannerMessage = String(data: data, encoding: .utf8) ?? "Save failed"
            }
        } catch {
            errorBannerMessage = error.localizedDescription
        }
    }

    public func startCalendarOAuth() async -> String? {
        guard let req = try? buildRequest(path: "/api/app/calendar/oauth/start", method: "POST", body: [:], authenticated: true) else { return nil }
        do {
            let (data, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200,
               let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let url = obj["auth_url"] as? String {
                return url
            }
            let raw = String(data: data, encoding: .utf8) ?? ""
            errorBannerMessage = raw.contains("google_oauth_credentials_missing")
                ? (appLanguage == "vi" ? "Hãy nhập Google Client ID & Secret trước!" : "Please enter Google Client ID & Secret first!")
                : raw
        } catch {
            errorBannerMessage = error.localizedDescription
        }
        return nil
    }

    public func disconnectCalendarOAuth() async {
        guard let req = try? buildRequest(path: "/api/app/calendar/oauth/disconnect", method: "POST", body: [:], authenticated: true) else { return }
        do {
            let (data, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200,
               let decoded = try? JSONDecoder().decode(CalendarStatusDto.self, from: data) {
                self.calendarState = decoded
                statusBannerMessage = appLanguage == "vi" ? "Đã ngắt Google OAuth." : "Disconnected Google OAuth."
            }
        } catch {}
    }

    public func createCalendarEvent(title: String, dateStr: String, startTime: String, endTime: String, allDay: Bool, location: String) async {
        let body: [String: Any] = [
            "title": title,
            "date": dateStr,
            "start_time": startTime,
            "end_time": endTime,
            "all_day": allDay,
            "location": location
        ]
        guard let req = try? buildRequest(path: "/api/app/calendar/events", method: "POST", body: body, authenticated: true) else { return }
        do {
            let (data, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200,
               let decoded = try? JSONDecoder().decode(CalendarStatusDto.self, from: data) {
                self.calendarState = decoded
                statusBannerMessage = appLanguage == "vi" ? "Đã thêm sự kiện!" : "Event created!"
            } else {
                errorBannerMessage = String(data: data, encoding: .utf8) ?? "Create failed"
            }
        } catch {
            errorBannerMessage = error.localizedDescription
        }
    }

    public func deleteCalendarEvent(eventId: String, calendarId: String?) async {
        let encoded = eventId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? eventId
        let q = (calendarId ?? "").isEmpty ? "" : "?calendar_id=\(calendarId!.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")"
        guard let req = try? buildRequest(path: "/api/app/calendar/events/\(encoded)\(q)", method: "DELETE", authenticated: true) else { return }
        do {
            let (data, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200,
               let decoded = try? JSONDecoder().decode(CalendarStatusDto.self, from: data) {
                self.calendarState = decoded
                statusBannerMessage = appLanguage == "vi" ? "Đã xóa sự kiện." : "Event deleted."
            }
        } catch {}
    }

    public func fetchSpotifyStatus() async {
        guard let req = try? buildRequest(path: "/api/app/spotify/status", method: "GET", authenticated: !(deviceToken ?? "").isEmpty) else { return }
        do {
            let (data, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                let decoder = JSONDecoder()
                if let st = try? decoder.decode(SpotifyStatusDto.self, from: data) {
                    await MainActor.run { self.spotifyState = st }
                }
            }
        } catch {}
    }

    public func controlSpotify(command: String) async {
        guard let req = try? buildRequest(
            path: "/api/app/spotify/playback",
            method: "POST",
            body: ["command": command],
            authenticated: true
        ) else { return }
        _ = try? await session.data(for: req)
        await fetchSpotifyStatus()
    }

    public func setSpotifyVolume(volume: Int) async {
        guard let req = try? buildRequest(
            path: "/api/app/spotify/volume",
            method: "POST",
            body: ["volume": max(0, min(100, volume))],
            authenticated: true
        ) else { return }
        _ = try? await session.data(for: req)
        await fetchSpotifyStatus()
    }

    public func fetchAlarms() async {
        guard let req = try? buildRequest(path: "/api/app/control/alarms", method: "GET") else { return }
        do {
            let (data, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                let res = try JSONDecoder().decode(AlarmsListResponse.self, from: data)
                self.alarms = res.alarms ?? []
            }
        } catch {
            errorBannerMessage = "Failed to load alarms: \(error.localizedDescription)"
        }
    }

    public func saveAlarm(time: String, label: String, repeatDays: [Int], buzzerDuration: Int = 250, alarmId: String? = nil) async {
        let path = alarmId != nil ? "/api/app/control/alarms/\(alarmId!)" : "/api/app/control/alarms"
        let method = alarmId != nil ? "PUT" : "POST"
        var body: [String: Any] = [
            "time": time,
            "label": label,
            "repeat_days": repeatDays,
            "buzzer_duration": buzzerDuration,
            "math_challenge": true
        ]
        if let aid = alarmId { body["id"] = aid }
        guard let req = try? buildRequest(path: path, method: method, body: body) else { return }
        do {
            let (_, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                statusBannerMessage = "Alarm \(time) saved."
                await fetchAlarms()
            }
        } catch {
            errorBannerMessage = "Failed to save alarm: \(error.localizedDescription)"
        }
    }

    public func deleteAlarm(alarmId: String) async {
        guard let req = try? buildRequest(path: "/api/app/control/alarms/\(alarmId)", method: "DELETE") else { return }
        do {
            let (_, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                statusBannerMessage = "Alarm deleted."
                await fetchAlarms()
            }
        } catch {
            errorBannerMessage = "Failed to delete alarm: \(error.localizedDescription)"
        }
    }

    public func toggleAlarm(alarmId: String, enabled: Bool?) async {
        var body: [String: Any] = [:]
        if let e = enabled { body["enabled"] = e }
        guard let req = try? buildRequest(path: "/api/app/control/alarms/\(alarmId)/toggle", method: "POST", body: body) else { return }
        do {
            let (_, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                statusBannerMessage = "Alarm updated."
                await fetchAlarms()
            }
        } catch {
            errorBannerMessage = "Toggle failed: \(error.localizedDescription)"
        }
    }

    public func triggerBuzz(durationMs: Int = 40) async {
        guard let req = try? buildRequest(path: "/api/app/control/alarm/buzz", method: "POST", body: ["duration": durationMs]) else { return }
        do {
            let (_, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                statusBannerMessage = "Triggered buzzer (\(durationMs)ms)."
            }
        } catch {
            errorBannerMessage = "Buzzer trigger failed: \(error.localizedDescription)"
        }
    }

    public func emergencyStop() async {
        guard let req = try? buildRequest(path: "/api/app/control/alarm/emergency-stop", method: "POST", body: [:]) else { return }
        do {
            let (_, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                statusBannerMessage = "Alarm silenced."
            }
        } catch {
            errorBannerMessage = "Emergency stop failed: \(error.localizedDescription)"
        }
    }

    public func setLampColor(hex: String) async {
        guard let req = try? buildRequest(path: "/api/app/control/lamp/color", method: "POST", body: ["hex": hex]) else { return }
        do {
            let (_, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                statusBannerMessage = "Lamp set to \(hex)."
            }
        } catch {
            errorBannerMessage = "Lamp color failed: \(error.localizedDescription)"
        }
    }

    /// Explicitly turns the WS2812B ring on (keeps the last stored colour).
    public func setLed(action: String) async {
        guard let req = try? buildRequest(path: "/api/app/control/led", method: "POST", body: ["action": action]) else { return }
        do {
            let (_, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                statusBannerMessage = action == "on" ? "Lamp turned on." : "Lamp turned off."
            }
        } catch {
            errorBannerMessage = "LED control failed: \(error.localizedDescription)"
        }
    }

    public func turnLampOff() async {
        guard let req = try? buildRequest(path: "/api/app/control/lamp/off", method: "POST", body: [:]) else { return }
        do {
            let (_, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                statusBannerMessage = "Lamp turned off."
            }
        } catch {
            errorBannerMessage = "Failed to turn lamp off: \(error.localizedDescription)"
        }
    }

    /// LED ring brightness only (0-100%). The ST7789 backlight is fixed hardware.
    public func setLampDim(led: Int) async {
        guard let req = try? buildRequest(path: "/api/app/control/lamp/dim", method: "POST", body: ["led": led]) else { return }
        _ = try? await session.data(for: req)
    }

    public func fetchPairedDevices() async {
        guard let req = try? buildRequest(path: "/api/focus/devices", method: "GET") else { return }
        do {
            let (data, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                let res = try JSONDecoder().decode(DevicesListResponse.self, from: data)
                self.pairedDevices = res.devices ?? []
            }
        } catch {
            errorBannerMessage = "Failed to fetch devices: \(error.localizedDescription)"
        }
    }

    public var allAvailableDevices: [PairedDeviceItem] {
        var list = pairedDevices
        let curId = deviceId ?? "mac_local"
        if !list.contains(where: { $0.deviceId == curId }) {
            list.insert(PairedDeviceItem(
                deviceId: curId,
                deviceName: "\(deviceName) (Máy Mac này)",
                platform: "macos",
                isCurrent: true,
                lastSeenAt: nil
            ), at: 0)
        }
        return list
    }

    public func renameDevice(deviceId: String, newName: String) async {
        let cleanName = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanName.isEmpty else { return }

        // 1. Call app control rename endpoint
        if let req1 = try? buildRequest(
            path: "/api/app/control/device/rename",
            method: "POST",
            body: ["device_id": deviceId, "name": cleanName],
            authenticated: false
        ) {
            _ = try? await session.data(for: req1)
        }

        // 2. Also call focus auth rename endpoint
        if let req2 = try? buildRequest(
            path: "/api/focus/devices/\(deviceId)/rename",
            method: "POST",
            body: ["name": cleanName],
            authenticated: false
        ) {
            _ = try? await session.data(for: req2)
        }

        // Update local name if renaming this Mac
        if deviceId == self.deviceId {
            self.deviceName = cleanName
            defaults.set(cleanName, forKey: "deskflow.deviceName")
        }

        statusBannerMessage = appLanguage == "vi" ? "Đã đổi tên thiết bị thành: \"\(cleanName)\"" : "Device renamed to \(cleanName)."
        await fetchPairedDevices()
        await reportTelemetry()
        await fetchSystemStatus()
    }

    public func unpairDevice(deviceId: String? = nil) async {
        let body = deviceId != nil ? ["device_id": deviceId!] : [:]
        guard let req = try? buildRequest(path: "/api/focus/unpair", method: "POST", body: body) else { return }
        do {
            let (_, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                if deviceId == nil || deviceId == self.deviceId {
                    deviceToken = nil
                    self.deviceId = nil
                }
                statusBannerMessage = "Device unlinked."
                await fetchPairedDevices()
            }
        } catch {
            errorBannerMessage = "Unlink failed: \(error.localizedDescription)"
        }
    }

    public func fetchSelectedDisplayDevice() async {
        guard let req = try? buildRequest(path: "/api/app/control/device/selected", method: "GET", authenticated: false) else { return }
        do {
            let (data, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                let res = try JSONDecoder().decode(SelectedDeviceResponse.self, from: data)
                self.selectedDisplayDeviceId = res.selectedDeviceId
            }
        } catch {
            // Ignore optional fetch error
        }
    }

    public func setSelectedDisplayDevice(deviceId: String) async {
        guard let req = try? buildRequest(
            path: "/api/app/control/device/selected",
            method: "POST",
            body: ["device_id": deviceId],
            authenticated: false
        ) else { return }
        do {
            let (_, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200 {
                self.selectedDisplayDeviceId = deviceId
                statusBannerMessage = appLanguage == "vi" ? "Đã đặt máy hiển thị thông số: \(deviceId)" : "Selected display device updated."
                await reportTelemetry()
                await fetchSystemStatus()
            }
        } catch {
            errorBannerMessage = "Update display device failed: \(error.localizedDescription)"
        }
    }

    public static func readLocalMetrics() -> (cpu: Double, ram: Double, battery: Int) {
        // 1. Battery
        var bat = 100
        if let blob = IOPSCopyPowerSourcesInfo()?.takeRetainedValue(),
           let sources = IOPSCopyPowerSourcesList(blob)?.takeRetainedValue() as? [CFTypeRef] {
            for s in sources {
                if let desc = IOPSGetPowerSourceDescription(blob, s)?.takeUnretainedValue() as? [String: Any],
                   let cap = desc[kIOPSCurrentCapacityKey as String] as? Int {
                    bat = cap
                    break
                }
            }
        }

        // 2. RAM
        var ramPct = 50.0
        var vmStats = vm_statistics64()
        var count = mach_msg_type_number_t(MemoryLayout<vm_statistics64>.size / MemoryLayout<integer_t>.size)
        let kerr = withUnsafeMutablePointer(to: &vmStats) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                host_statistics64(mach_host_self(), HOST_VM_INFO64, $0, &count)
            }
        }
        if kerr == KERN_SUCCESS {
            let pageSize = Double(vm_kernel_page_size)
            let active = Double(vmStats.active_count) * pageSize
            let wire = Double(vmStats.wire_count) * pageSize
            let compressed = Double(vmStats.compressor_page_count) * pageSize
            let total = Double(ProcessInfo.processInfo.physicalMemory)
            if total > 0 {
                ramPct = min(100.0, max(0.0, ((active + wire + compressed) / total) * 100.0))
            }
        }

        // 3. CPU
        var cpuPct = 12.0
        var cpuLoad = host_cpu_load_info()
        var cpuCount = mach_msg_type_number_t(MemoryLayout<host_cpu_load_info>.size / MemoryLayout<integer_t>.size)
        let cpuErr = withUnsafeMutablePointer(to: &cpuLoad) {
            $0.withMemoryRebound(to: integer_t.self, capacity: Int(cpuCount)) {
                host_statistics(mach_host_self(), HOST_CPU_LOAD_INFO, $0, &cpuCount)
            }
        }
        if cpuErr == KERN_SUCCESS {
            let user = Double(cpuLoad.cpu_ticks.0)
            let sys = Double(cpuLoad.cpu_ticks.1)
            let idle = Double(cpuLoad.cpu_ticks.2)
            let nice = Double(cpuLoad.cpu_ticks.3)
            let total = user + sys + idle + nice
            if total > 0 {
                cpuPct = min(100.0, max(1.0, ((user + sys + nice) / total) * 100.0))
            }
        }

        return (cpu: round(cpuPct * 10) / 10, ram: round(ramPct * 10) / 10, battery: bat)
    }

    public func reportTelemetry(cpu: Double? = nil, ram: Double? = nil, battery: Int? = nil) async {
        let metrics = Self.readLocalMetrics()
        let sendCpu = cpu ?? metrics.cpu
        let sendRam = ram ?? metrics.ram
        let sendBat = battery ?? metrics.battery
        let did = deviceId ?? "mac_client"
        guard let req = try? buildRequest(
            path: "/api/app/control/telemetry",
            method: "POST",
            body: [
                "cpu": sendCpu,
                "ram_pct": sendRam,
                "battery": sendBat,
                "device_id": did,
                "device_name": deviceName,
                "timestamp": Date().timeIntervalSince1970
            ],
            authenticated: false
        ) else { return }
        _ = try? await session.data(for: req)
    }

    public func fetchSystemStatus() async {
        guard let req = try? buildRequest(path: "/api/app/control/status", method: "GET") else { return }
        do {
            let (data, res) = try await session.data(for: req)
            if let http = res as? HTTPURLResponse, http.statusCode == 200,
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                let statusData = (json["data"] as? [String: Any]) ?? json
                if let station = statusData["station"] as? [String: Any] {
                    self.esp32BatteryPct = (station["battery_pct"] as? Int) ?? 100
                    self.esp32Voltage = (station["voltage"] as? Double) ?? 4.12
                    self.esp32WifiRssi = (station["wifi_rssi"] as? Int) ?? -55
                    self.esp32CurrentView = (station["current_view"] as? String) ?? "main"
                }
                if let server = statusData["server"] as? [String: Any] {
                    self.serverCpu = (server["cpu"] as? Double) ?? 0.0
                    self.serverRamPct = (server["ram_pct"] as? Double) ?? 0.0
                    self.serverDiskStr = (server["disk_str"] as? String) ?? "\(server["disk_used_gb"] ?? 0) / \(server["disk_total_gb"] ?? 20)GB"
                    self.serverUptime = (server["uptime"] as? String) ?? "N/A"
                }
                if let comp = (statusData["computer"] as? [String: Any]) ?? (statusData["laptop"] as? [String: Any]) {
                    self.compDeviceName = (comp["device_name"] as? String) ?? self.deviceName
                    self.compCpu = (comp["cpu"] as? Double) ?? 0.0
                    self.compRam = (comp["ram"] as? Double) ?? (comp["ram_pct"] as? Double) ?? 0.0
                    self.compBattery = (comp["battery"] as? Int) ?? 100
                    self.compIsOnline = (comp["online"] as? Bool) ?? true
                }
                if let srvLang = await fetchClockLanguage(), srvLang != self.lastServerLanguage {
                    self.lastServerLanguage = srvLang
                    if self.appLanguage != srvLang {
                        self.suppressLanguageSync = true
                        self.appLanguage = srvLang
                        self.suppressLanguageSync = false
                    }
                }
            }
        } catch {}
    }

    public func generateQuote(topic: String = "học tập và kiên trì", limit: Int = 75) async -> (quote: String, author: String)? {
        guard let req = try? buildRequest(
            path: "/api/app/control/quote/generate",
            method: "POST",
            body: ["topic": topic, "limit": limit]
        ) else { return nil }
        do {
            let (data, response) = try await session.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 200,
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                let q = (json["quote"] as? String) ?? (json["text"] as? String) ?? ""
                let a = (json["author"] as? String) ?? "Groq AI"
                return (quote: q, author: a)
            }
        } catch {}
        return nil
    }

    public func setEsp32Theme(mode: String, start: String = "22:00", end: String = "06:00") async {
        let serverMode: String
        let modeLower = mode.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        switch modeLower {
        case "dark", "always_on", "always", "on":
            serverMode = "always_on"
        case "light", "off":
            serverMode = "off"
        default:
            serverMode = "scheduled"
        }

        var success = false
        // 1. Try theme endpoint
        if let req1 = try? buildRequest(
            path: "/api/app/control/theme",
            method: "POST",
            body: ["theme": serverMode, "start": start, "end": end],
            authenticated: false
        ) {
            if let (_, res) = try? await session.data(for: req1),
               let http = res as? HTTPURLResponse, http.statusCode == 200 {
                success = true
            }
        }

        // 2. Also update settings endpoint
        if let req2 = try? buildRequest(
            path: "/api/app/control/settings",
            method: "POST",
            body: [
                "darkmode_mode": serverMode,
                "darkmode_start": start,
                "darkmode_end": end
            ],
            authenticated: false
        ) {
            if let (_, res) = try? await session.data(for: req2),
               let http = res as? HTTPURLResponse, http.statusCode == 200 {
                success = true
            }
        }

        if success {
            let label: String
            if serverMode == "always_on" {
                label = appLanguage == "vi" ? "Luôn Tối (Dark Mode)" : "Always Dark"
            } else if serverMode == "off" {
                label = appLanguage == "vi" ? "Luôn Sáng (Light Mode)" : "Always Light"
            } else {
                label = appLanguage == "vi" ? "Theo lịch (Scheduled)" : "Scheduled"
            }
            statusBannerMessage = appLanguage == "vi" ? "Đã cài đặt màn hình ESP32: \(label)" : "ESP32 display theme set to \(label)"
            logDiagnostic("[ESP32] Theme set successfully to \(serverMode)")
        } else {
            errorBannerMessage = appLanguage == "vi" ? "Không thể cập nhật theme ESP32. Vui lòng kiểm tra kết nối." : "Failed to update ESP32 theme."
        }
    }
}
