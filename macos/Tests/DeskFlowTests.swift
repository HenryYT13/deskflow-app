import XCTest
@testable import DeskFlow

final class DeskFlowTests: XCTestCase {

    func testScheduledDarkModeOvernightAndSameDay() {
        // Overnight 20:00 -> 06:00
        XCTAssertFalse(ScheduledDarkModeEvaluator.isScheduledDarkActive(nowMinutes: 19 * 60 + 59, startStr: "20:00", endStr: "06:00"))
        XCTAssertTrue(ScheduledDarkModeEvaluator.isScheduledDarkActive(nowMinutes: 20 * 60, startStr: "20:00", endStr: "06:00"))
        XCTAssertTrue(ScheduledDarkModeEvaluator.isScheduledDarkActive(nowMinutes: 0, startStr: "20:00", endStr: "06:00"))
        XCTAssertTrue(ScheduledDarkModeEvaluator.isScheduledDarkActive(nowMinutes: 5 * 60 + 59, startStr: "20:00", endStr: "06:00"))
        XCTAssertFalse(ScheduledDarkModeEvaluator.isScheduledDarkActive(nowMinutes: 6 * 60, startStr: "20:00", endStr: "06:00"))

        // Same-day 09:00 -> 17:00
        XCTAssertFalse(ScheduledDarkModeEvaluator.isScheduledDarkActive(nowMinutes: 8 * 60 + 59, startStr: "09:00", endStr: "17:00"))
        XCTAssertTrue(ScheduledDarkModeEvaluator.isScheduledDarkActive(nowMinutes: 9 * 60, startStr: "09:00", endStr: "17:00"))
        XCTAssertFalse(ScheduledDarkModeEvaluator.isScheduledDarkActive(nowMinutes: 17 * 60, startStr: "09:00", endStr: "17:00"))

        // Equal start & end -> false
        XCTAssertFalse(ScheduledDarkModeEvaluator.isScheduledDarkActive(nowMinutes: 6 * 60, startStr: "06:00", endStr: "06:00"))
    }

    func testMorseCodeBidirectionalConversion() {
        let morse = MorseCodeConverter.textToMorse("SOS Đạt")
        XCTAssertEqual(morse, "... --- ... / -.. .- -")
        let decoded = MorseCodeConverter.morseToText(morse)
        XCTAssertEqual(decoded, "SOS DAT")
    }

    func testNotesListResponseDecodingIncludingEmptyLastNoteDelete() throws {
        let emptyJson = """
        {"success": true, "deleted_id": "note-1", "notes": []}
        """.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(NotesListResponse.self, from: emptyJson)
        XCTAssertEqual(decoded.deletedId, "note-1")
        XCTAssertTrue(decoded.allNotes.isEmpty)
    }

    func testWeatherForecastUnifiedDecoding() throws {
        let json = """
        {
            "success": true,
            "intervals": [
                {
                    "time": "2026-11-07 18:00:00",
                    "temp": 29.0,
                    "description": "light rain",
                    "rain_prob": 80.0,
                    "is_rain": true
                }
            ]
        }
        """.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(WeatherForecastResponse.self, from: json)
        XCTAssertEqual(decoded.allIntervals.count, 1)
        XCTAssertEqual(decoded.allIntervals[0].displayTime, "2026-11-07 18:00:00")
        XCTAssertEqual(decoded.allIntervals[0].displayDescription, "light rain")
        XCTAssertEqual(decoded.allIntervals[0].effectiveRainProb, 80.0, accuracy: 0.01)
    }

    func testClockStyleIdToCodeMapping() {
        XCTAssertEqual(DeskFlowApiClient.clockStyleIdToCode[1], "digital_classic")
        XCTAssertEqual(DeskFlowApiClient.clockStyleIdToCode[2], "digital_minimal")
        XCTAssertEqual(DeskFlowApiClient.clockStyleIdToCode[3], "digital_neon")
        XCTAssertEqual(DeskFlowApiClient.clockStyleIdToCode[4], "analog")
        XCTAssertEqual(DeskFlowApiClient.clockStyleIdToCode[5], "flip")
        XCTAssertEqual(DeskFlowApiClient.clockStyleIdToCode[6], "binary")
        XCTAssertEqual(DeskFlowApiClient.clockStyleIdToCode[7], "word")
    }

    func testTimetableResponseDecoding() throws {
        let json = """
        {
            "success": true,
            "data": {
                "week_number": "10",
                "today": {
                    "day": "Thứ 2",
                    "display_day": "Thứ 2",
                    "morning": ["Tiết 1: Toán", "Tiết 2: Ngữ Văn"],
                    "afternoon": ["Tiết 1: GDQP"],
                    "dp_sang": "Chính khóa",
                    "dp_chieu": "Thể dục",
                    "has_afternoon": true,
                    "has_any_class": true
                },
                "tomorrow": {
                    "day": "Thứ 3",
                    "display_day": "Thứ 3",
                    "morning": ["Tiết 1: Hóa Học"],
                    "afternoon": [],
                    "dp_sang": "Tự do",
                    "dp_chieu": "",
                    "has_afternoon": false,
                    "has_any_class": true
                }
            }
        }
        """.data(using: .utf8)!
        let decoded = try JSONDecoder().decode(TimetableResponse.self, from: json)
        XCTAssertEqual(decoded.success, true)
        XCTAssertEqual(decoded.data?.weekNumber, "10")
        XCTAssertEqual(decoded.data?.today?.day, "Thứ 2")
        XCTAssertEqual(decoded.data?.today?.morning?.count, 2)
        XCTAssertEqual(decoded.data?.today?.uniformMorning, "Chính khóa")
        XCTAssertEqual(decoded.data?.today?.uniformAfternoon, "Thể dục")
        XCTAssertEqual(decoded.data?.tomorrow?.morning?.count, 1)
        XCTAssertEqual(decoded.data?.tomorrow?.hasAfternoon, false)
    }

    func testPairSessionModelsDecoding() throws {
        let createJson = """
        {"success": true, "session_id": "s_123", "qr_payload": "deskflow://pair?session=s_123", "expires_in": 600}
        """.data(using: .utf8)!
        let createDecoded = try JSONDecoder().decode(PairSessionCreateResponse.self, from: createJson)
        XCTAssertEqual(createDecoded.sessionId, "s_123")
        XCTAssertEqual(createDecoded.expiresIn, 600)

        let statusJson = """
        {"success": true, "status": "approved", "device_id": "dev_1", "device_token": "tok_1", "device_name": "MacBook", "scopes": ["focus", "control"], "paired_at": "2026-11-11T03:51:00.123456+00:00"}
        """.data(using: .utf8)!
        let statusDecoded = try JSONDecoder().decode(PairSessionStatusResponse.self, from: statusJson)
        XCTAssertEqual(statusDecoded.status, "approved")
        XCTAssertEqual(statusDecoded.deviceId, "dev_1")
        XCTAssertEqual(statusDecoded.deviceToken, "tok_1")
        XCTAssertEqual(statusDecoded.pairedAt, "2026-11-11T03:51:00.123456+00:00")
    }
}
