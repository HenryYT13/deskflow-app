import Foundation

private final class MockURLProtocol: URLProtocol {
    nonisolated(unsafe) static var handler: ((URLRequest) throws -> (HTTPURLResponse, Data))?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let handler = Self.handler else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }
        do {
            let (response, data) = try handler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}

@MainActor
public enum MacSelfTestRunner {
    private static func assertCondition(_ condition: @autoclosure () -> Bool, _ message: String) throws {
        if !condition() {
            throw NSError(domain: "DeskFlowMacTests", code: 1, userInfo: [NSLocalizedDescriptionKey: message])
        }
    }

    public static func runAllTests() async -> Int32 {
        print("[DeskFlow-macOS-Tests] Starting native macOS test suite...")
        do {
            // 1. Scheduled Dark Mode Tests
            try assertCondition(
                !ScheduledDarkModeEvaluator.isScheduledDarkActive(nowMinutes: 19 * 60 + 59, startStr: "20:00", endStr: "06:00"),
                "19:59 should be light"
            )
            try assertCondition(
                ScheduledDarkModeEvaluator.isScheduledDarkActive(nowMinutes: 20 * 60, startStr: "20:00", endStr: "06:00"),
                "20:00 should be dark"
            )
            try assertCondition(
                ScheduledDarkModeEvaluator.isScheduledDarkActive(nowMinutes: 0, startStr: "20:00", endStr: "06:00"),
                "00:00 should be dark"
            )
            try assertCondition(
                !ScheduledDarkModeEvaluator.isScheduledDarkActive(nowMinutes: 6 * 60, startStr: "20:00", endStr: "06:00"),
                "06:00 should be light"
            )
            try assertCondition(
                !ScheduledDarkModeEvaluator.isScheduledDarkActive(nowMinutes: 6 * 60, startStr: "06:00", endStr: "06:00"),
                "Equal start/end should be false"
            )
            print("  [PASS] testScheduledDarkModeOvernightAndSameDay")

            // 2. Morse Code Bidirectional Tests
            let morse = MorseCodeConverter.textToMorse("SOS Đạt")
            try assertCondition(morse == "... --- ... / -.. .- -", "Expected SOS DAT morse, got \(morse)")
            let decoded = MorseCodeConverter.morseToText(morse)
            try assertCondition(decoded == "SOS DAT", "Expected SOS DAT text, got \(decoded)")
            print("  [PASS] testMorseCodeBidirectionalConversion")

            // 3. Pairing & Notes CRUD Tests via MockURLProtocol
            let config = URLSessionConfiguration.ephemeral
            config.protocolClasses = [MockURLProtocol.self]
            let mockSession = URLSession(configuration: config)
            let testDefaults = UserDefaults(suiteName: "DeskFlowUnitTests") ?? .standard
            let client = DeskFlowApiClient(session: mockSession, defaults: testDefaults)
            client.apiUrl = "https://mock.deskflow.local"
            client.deviceToken = nil
            client.deviceId = nil

            // 3a. Empty pairing code rejected locally
            let emptyOk = await client.pairDevice(code: "   ")
            try assertCondition(!emptyOk && !client.isPaired, "Empty pairing code must fail")
            print("  [PASS] testEmptyPairingCodeRejected")

            // 3b. Invalid pairing code (403 pair_code_invalid)
            MockURLProtocol.handler = { req in
                let resp = HTTPURLResponse(url: req.url!, statusCode: 403, httpVersion: nil, headerFields: nil)!
                let data = Data(#"{"error":{"code":"pair_code_invalid"}}"#.utf8)
                return (resp, data)
            }
            let invalidOk = await client.pairDevice(code: "ZZZZ9999")
            try assertCondition(!invalidOk && !client.isPaired, "Invalid code must fail")
            try assertCondition(
                (client.errorBannerMessage ?? "").contains("Invalid, expired, or already-used pairing code"),
                "Error message should be human-readable"
            )
            print("  [PASS] testInvalidPairingCode403")

            // 3c. Malformed pairing response (200 missing token)
            MockURLProtocol.handler = { req in
                let resp = HTTPURLResponse(url: req.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!
                let data = Data(#"{"success":true}"#.utf8)
                return (resp, data)
            }
            let malformedOk = await client.pairDevice(code: "ABCD1234")
            try assertCondition(!malformedOk && !client.isPaired, "Malformed 200 response must not pair")
            print("  [PASS] testMalformedPairingResponse")

            // 3d. Valid pairing request (/api/focus/pair)
            var lastPath = ""
            MockURLProtocol.handler = { req in
                lastPath = req.url?.path ?? ""
                let resp = HTTPURLResponse(url: req.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!
                if lastPath == "/api/focus/pair" {
                    let data = Data(#"{"success":true,"device_token":"mac-tok-123","device_id":"dev_mac_1","device_name":"MacBook-cua-Dada","scopes":["focus","control","spotify"]}"#.utf8)
                    return (resp, data)
                }
                return (resp, Data(#"{"success":true,"notes":[]}"#.utf8))
            }
            let validOk = await client.pairDevice(code: "ABCD1234")
            try assertCondition(validOk && client.isPaired, "Valid pairing code must succeed")
            try assertCondition(client.deviceId == "dev_mac_1", "Device ID must be saved")
            print("  [PASS] testValidPairingRequestContract")

            // 3e. Notes creation & last-note authoritative deletion
            MockURLProtocol.handler = { req in
                let resp = HTTPURLResponse(url: req.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!
                let data = Data(#"{"success":true,"notes":[{"id":"n-1","content":"Mac Note","pinned":true}]}"#.utf8)
                return (resp, data)
            }
            await client.fetchNotes()
            try assertCondition(client.notes.count == 1, "Should have 1 note")

            // Simulate 500 failure on delete -> note must remain
            MockURLProtocol.handler = { req in
                let resp = HTTPURLResponse(url: req.url!, statusCode: 500, httpVersion: nil, headerFields: nil)!
                return (resp, Data(#"{"error":{"code":"internal"}}"#.utf8))
            }
            let delFail = await client.deleteNote(noteId: "n-1")
            try assertCondition(!delFail && client.notes.count == 1, "500 delete failure must preserve local note")

            // Simulate 200 on deleting last note -> notes becomes empty []
            MockURLProtocol.handler = { req in
                let resp = HTTPURLResponse(url: req.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!
                return (resp, Data(#"{"success":true,"deleted_id":"n-1","notes":[]}"#.utf8))
            }
            let delOk = await client.deleteNote(noteId: "n-1")
            try assertCondition(delOk && client.notes.isEmpty, "Deleting last note must clear notes array")
            print("  [PASS] testNotesAuthoritativeDeleteAndLastNoteClear")

            // 3f. Clock style changes send style code string
            var lastClockStylePayload = ""
            MockURLProtocol.handler = { req in
                var bodyData = req.httpBody
                if bodyData == nil, let stream = req.httpBodyStream {
                    stream.open()
                    var buffer = [UInt8](repeating: 0, count: 1024)
                    var d = Data()
                    while stream.hasBytesAvailable {
                        let readLen = stream.read(&buffer, maxLength: buffer.count)
                        if readLen > 0 {
                            d.append(buffer, count: readLen)
                        } else {
                            break
                        }
                    }
                    stream.close()
                    bodyData = d
                }
                if let data = bodyData,
                   let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let styleStr = json["style"] as? String {
                    lastClockStylePayload = styleStr
                }
                let resp = HTTPURLResponse(url: req.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!
                return (resp, Data(#"{"success":true,"data":{"style":"digital_minimal"}}"#.utf8))
            }
            await client.setClockStyle(style: "digital_minimal")
            try assertCondition(lastClockStylePayload == "digital_minimal" || client.statusBannerMessage?.contains("digital_minimal") == true, "setClockStyle(string) must send code string")
            await client.setClockStyle(style: 5)
            try assertCondition(lastClockStylePayload == "flip" || client.statusBannerMessage?.contains("flip") == true, "setClockStyle(int) must map 5 to flip string code")
            print("  [PASS] testClockStyleStringPayloadContract")

            // 3g. Pairing Session Create and Poll
            MockURLProtocol.handler = { req in
                let resp = HTTPURLResponse(url: req.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!
                if req.url?.path == "/api/focus/pair/session/create" {
                    return (resp, Data(#"{"success":true,"session_id":"sess_123","qr_payload":"deskflow://pair?session=sess_123","expires_in":600}"#.utf8))
                }
                if req.url?.path == "/api/focus/pair/session/sess_123" {
                    return (resp, Data(#"{"success":true,"status":"approved","device_id":"dev_sess_1","device_token":"tok_sess_1","device_name":"Mac"}"#.utf8))
                }
                return (resp, Data(#"{"success":true}"#.utf8))
            }
            let sessCreate = await client.createPairingSession()
            try assertCondition(sessCreate?.sessionId == "sess_123", "Session create must decode session_id")
            let sessPoll = await client.pollPairingSession(sessionId: "sess_123")
            try assertCondition(sessPoll?.status == "approved" && client.isPaired, "Session poll approved must set isPaired")
            print("  [PASS] testPairingSessionCreateAndPoll")

            // 3h. Timetable fetching
            MockURLProtocol.handler = { req in
                let resp = HTTPURLResponse(url: req.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!
                let data = Data(#"{"success":true,"data":{"week_number":"10","today":{"day":"Thứ 2","morning":["Tiết 1: Toán"],"dp_sang":"Chính khóa","has_afternoon":false,"afternoon":[],"dp_chieu":"","has_any_class":true},"tomorrow":{"day":"Thứ 3","morning":[],"dp_sang":"Tự do","has_afternoon":false,"afternoon":[],"dp_chieu":"","has_any_class":false}}}"#.utf8)
                return (resp, data)
            }
            await client.fetchTimetable()
            try assertCondition(client.timetableData?.weekNumber == "10", "Timetable week number must match")
            try assertCondition(client.timetableData?.today?.morning?.first == "Tiết 1: Toán", "Timetable today morning must match")
            print("  [PASS] testTimetableFetchContract")

            // Cleanup test defaults
            UserDefaults.standard.removeSuite(named: "DeskFlowUnitTests")

            print("[DeskFlow-macOS-Tests] ALL 10 TEST SUITES PASSED (0 failures).")
            return 0
        } catch {
            print("[DeskFlow-macOS-Tests] FAILED: \(error.localizedDescription)")
            return 1
        }
    }
}
