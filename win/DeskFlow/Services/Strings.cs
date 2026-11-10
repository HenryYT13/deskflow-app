using System;
using System.Collections.Generic;

namespace DeskFlow.Services;

public static class Strings
{
    private static readonly Dictionary<string, string> En = new(StringComparer.OrdinalIgnoreCase)
    {
        ["app_title"] = "Deskflow",
        ["nav_dashboard"] = "Dashboard & Telemetry",
        ["nav_focus"] = "Focus Mode & Challenge",
        ["nav_alarms"] = "Alarms & Sound",
        ["nav_notes"] = "Sticky Notes & Tools",
        ["nav_countdowns"] = "Exam Countdowns",
        ["nav_weather"] = "Weather Forecast",
        ["nav_morse"] = "Morse Code Studio",
        ["nav_station"] = "Hardware & ESP32 Station",
        ["nav_settings"] = "Settings & Appearance",

        // Categories (matching Android hierarchy)
        ["cat_hardware"] = "Hardware & Station",
        ["cat_hardware_desc"] = "LED lighting, display screens, and buzzer",
        ["cat_alarms"] = "Alarms & Sound",
        ["cat_alarms_desc"] = "Math alarm status, solve challenge, and scheduling",
        ["cat_tools"] = "Productivity & AI Tools",
        ["cat_tools_desc"] = "Sticky notes, exam countdown, AI quotes & Focus Mode",
        ["cat_settings"] = "Remote Configuration",
        ["cat_settings_desc"] = "Dark mode schedule, rain alert, battery thresholds",
        ["cat_system"] = "System & Telemetry",
        ["cat_system_desc"] = "ESP32 battery, voltage, server and laptop status",

        // Actions & Buttons
        ["btn_pair"] = "Pair Device",
        ["btn_unpair"] = "Unpair",
        ["btn_refresh_all"] = "Refresh Station Data",
        ["btn_apply"] = "Apply",
        ["btn_save"] = "Save",
        ["btn_delete"] = "Delete",
        ["btn_rename"] = "Rename",
        ["btn_unlink"] = "Unlink",
        ["btn_apply_schedule"] = "Apply Schedule",
        ["btn_apply_esp32_theme"] = "Apply to ESP32 Display",
        ["btn_generate_quote"] = "Generate AI Quote",
        ["btn_custom_color"] = "Set Custom Color",
        ["btn_lamp_off"] = "Lamp Off",
        ["btn_emergency_stop"] = "Emergency Stop",

        // Sections
        ["lbl_app_theme"] = "Application Theme",
        ["lbl_esp_theme"] = "ESP32 Display Appearance",
        ["lbl_language"] = "Display Language",
        ["lbl_theme_system"] = "System Default",
        ["lbl_theme_light"] = "Light Mode",
        ["lbl_theme_dark"] = "Dark Mode",
        ["lbl_theme_scheduled"] = "Scheduled Dark Mode",
        ["lbl_esp_off"] = "Light (Off)",
        ["lbl_esp_always"] = "Dark (Always On)",
        ["lbl_esp_scheduled"] = "Scheduled (NVS)",

        ["lbl_hourly_weather"] = "Hourly Forecast (48h)",
        ["lbl_quote_card"] = "Groq AI Motivational Quote",
        ["lbl_now_playing"] = "Currently Playing Music",
        ["lbl_esp_status"] = "ESP32 Station Status & Specs"
    };

    private static readonly Dictionary<string, string> Vi = new(StringComparer.OrdinalIgnoreCase)
    {
        ["app_title"] = "Deskflow",
        ["nav_dashboard"] = "Bảng điều khiển & Giám sát",
        ["nav_focus"] = "Chế độ tập trung & Thử thách",
        ["nav_alarms"] = "Báo thức & Còi báo",
        ["nav_notes"] = "Ghi chú & Công cụ",
        ["nav_countdowns"] = "Đếm ngược ngày thi",
        ["nav_weather"] = "Dự báo thời tiết",
        ["nav_morse"] = "Phòng thu mã Morse",
        ["nav_station"] = "Phần cứng & Trạm ESP32",
        ["nav_settings"] = "Cài đặt & Giao diện",

        // Categories (matching Android hierarchy)
        ["cat_hardware"] = "Phần cứng & Trạm ESP32",
        ["cat_hardware_desc"] = "Điều khiển đèn LED, màn hình và còi bíp",
        ["cat_alarms"] = "Báo thức & Còi báo",
        ["cat_alarms_desc"] = "Báo thức toán học, dập còi và hẹn giờ",
        ["cat_tools"] = "Công cụ học tập & AI",
        ["cat_tools_desc"] = "Giấy nhớ, đếm ngược thi, danh ngôn & Chế độ tập trung",
        ["cat_settings"] = "Cấu hình trạm từ xa",
        ["cat_settings_desc"] = "Lịch Dark Mode, cảnh báo mưa, ngưỡng bảo vệ pin",
        ["cat_system"] = "Giám sát hệ thống",
        ["cat_system_desc"] = "Pin trạm, điện áp, thông số server và laptop",

        // Actions & Buttons
        ["btn_pair"] = "Ghép nối thiết bị",
        ["btn_unpair"] = "Hủy ghép nối",
        ["btn_refresh_all"] = "Làm mới dữ liệu",
        ["btn_apply"] = "Áp dụng",
        ["btn_save"] = "Lưu",
        ["btn_delete"] = "Xóa",
        ["btn_rename"] = "Đổi tên",
        ["btn_unlink"] = "Hủy liên kết",
        ["btn_apply_schedule"] = "Áp dụng lịch trình",
        ["btn_apply_esp32_theme"] = "Áp dụng lên màn hình ESP32",
        ["btn_generate_quote"] = "Tạo câu danh ngôn AI",
        ["btn_custom_color"] = "Đặt màu tùy chỉnh",
        ["btn_lamp_off"] = "Tắt đèn",
        ["btn_emergency_stop"] = "Dập còi khẩn cấp",

        // Sections
        ["lbl_app_theme"] = "Giao diện ứng dụng",
        ["lbl_esp_theme"] = "Giao diện màn hình ESP32",
        ["lbl_language"] = "Ngôn ngữ hiển thị",
        ["lbl_theme_system"] = "Theo hệ thống",
        ["lbl_theme_light"] = "Giao diện sáng",
        ["lbl_theme_dark"] = "Giao diện tối",
        ["lbl_theme_scheduled"] = "Chế độ tối theo lịch",
        ["lbl_esp_off"] = "Sáng (Tắt Dark Mode)",
        ["lbl_esp_always"] = "Tối (Luôn bật)",
        ["lbl_esp_scheduled"] = "Tối theo lịch (Lưu NVS)",

        ["lbl_hourly_weather"] = "Dự báo thời tiết theo giờ (48h)",
        ["lbl_quote_card"] = "Danh ngôn tạo động lực Groq AI",
        ["lbl_now_playing"] = "Đang phát nhạc",
        ["lbl_esp_status"] = "Thông số & Trạng thái ESP32"
    };

    public static string CurrentLanguage { get; set; } = "vi";

    public static string Get(string key, string? fallback = null)
    {
        var dict = CurrentLanguage.Equals("vi", StringComparison.OrdinalIgnoreCase) ? Vi : En;
        if (dict.TryGetValue(key, out var val)) return val;
        if (En.TryGetValue(key, out var valEn)) return valEn;
        return fallback ?? key;
    }
}
