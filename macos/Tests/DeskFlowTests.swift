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
}
