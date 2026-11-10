import Foundation

/// Lightweight runtime localization. `tr` returns the Vietnamese string when the
/// app language is "vi", otherwise the English original.
public enum L10n {
    public static var lang: String = UserDefaults.standard.string(forKey: "deskflow.language") ?? "vi"

    static let vi: [String: String] = [
        "6-Character Pairing Code": "Mã ghép nối 6 ký tự",
        "Add Countdown": "Thêm đếm ngược",
        "Add Note": "Thêm ghi chú",
        "Backend-Authoritative Alarms": "Báo thức do máy chủ quản lý",
        "Cancel": "Hủy",
        "CONNECT ONLINE": "KẾT NỐI TRỰC TUYẾN",
        "Delete": "Xóa",
        "Duration Presets:": "Thời lượng nhanh:",
        "Emergency Stop": "Dừng khẩn cấp",
        "Event Name": "Tên sự kiện",
        "Exam & Event Countdowns": "Đếm ngược kỳ thi & sự kiện",
        "Fri": "T6", "Mon": "T2", "Tue": "T3", "Wed": "T4", "Thu": "T5", "Sat": "T7", "Sun": "CN",
        "Integer Answer": "Đáp án (số nguyên)",
        "Label": "Nhãn",
        "Morse Code Studio & ESP32 Buzzer": "Mã Morse Studio & còi ESP32",
        "Morse Pattern (. - /)": "Mã Morse (. - /)",
        "No alarms configured on server. Create one above.": "Chưa có báo thức nào trên máy chủ. Hãy tạo ở trên.",
        "No sticky notes saved.": "Chưa có giấy nhớ nào.",
        "Plain Text": "Văn bản thường",
        "Play on ESP32": "Phát trên ESP32",
        "Refresh": "Làm mới",
        "Refresh All Telemetry": "Làm mới toàn bộ số liệu",
        "Repeat:": "Lặp lại:",
        "Request Challenge": "Yêu cầu thử thách",
        "Save Alarm": "Lưu báo thức",
        "Session Goal": "Mục tiêu phiên",
        "Start 25m Focus": "Bắt đầu tập trung 25 phút",
        "Start 50m Deep Work": "Bắt đầu làm việc sâu 50 phút",
        "Station": "Trạm",
        "Sticky Notes": "Giấy nhớ",
        "Stop Playback": "Dừng phát",
        "Stop Session": "Dừng phiên",
        "Switch the active page displayed on the ESP32 station:": "Chuyển trang đang hiển thị trên trạm ESP32:",
        "Target Date (YYYY-MM-DD or DD/MM)": "Ngày mục tiêu (YYYY-MM-DD hoặc DD/MM)",
        "Test Backend Health": "Kiểm tra kết nối máy chủ",
        "Time (HH:MM)": "Giờ (HH:MM)",
        "Unpin ESP32 Note": "Bỏ ghim ghi chú ESP32",
        "Verify Answer": "Xác minh đáp án",
        "Write a sticky note for ESP32 OLED...": "Viết giấy nhớ cho màn hình OLED ESP32...",
        "Text → Morse": "Văn bản → Morse",
        "Morse → Text": "Morse → Văn bản",
        "• (Dot)": "• (Chấm)",
        "— (Dash)": "— (Gạch)",
        "[Ký tự] (Space)": "[Ký tự] (Cách)",
    ]
}

/// Translate an English UI string according to the current app language.
public func tr(_ en: String) -> String {
    L10n.lang == "vi" ? (L10n.vi[en] ?? en) : en
}
