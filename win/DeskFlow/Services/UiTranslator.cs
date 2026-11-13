using System;
using System.Collections.Generic;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;

namespace DeskFlow.Services;

/// <summary>
/// Runtime translation for strings that live directly in MainWindow.xaml.
///
/// The Windows layout hardcodes Vietnamese text in ~130 attributes. Rather than
/// duplicating every label behind x:Uid/resw, the visual tree is walked on every
/// language change and each known Vietnamese string is swapped for its English
/// counterpart (and back again). Unknown strings are left untouched.
/// </summary>
public static class UiTranslator
{
    private static readonly Dictionary<string, string> ViToEn = new(StringComparer.Ordinal)
    {
        // --- Pairing gate ---
        ["Yêu cầu Ghép nối Thiết bị"] = "Device Pairing Required",
        ["Để sử dụng Trung tâm điều khiển DeskFlow, bạn phải ghép nối máy tính này với tài khoản hệ sinh thái."] =
            "To use the DeskFlow Control Center you must pair this PC with your DeskFlow ecosystem account.",
        ["Quét mã QR bằng ứng dụng Android DeskFlow"] = "Scan the QR code with the DeskFlow Android app",
        ["Mở app Android -> Cài đặt -> Quét mã QR liên kết thiết bị khác"] =
            "Open the Android app -> Settings -> Scan QR to link another device",
        ["Tên thiết bị hiển thị"] = "Displayed device name",
        ["Đang chờ quét và phê duyệt từ điện thoại..."] = "Waiting for the phone to scan and approve...",
        ["Tạo lại mã QR"] = "Regenerate QR code",
        ["Hoặc gõ /app pair trên Discord để nhận mã ghép nối 8 ký tự:"] =
            "Or type /app pair in Discord to get an 8-character pairing code:",
        ["Mã 8 ký tự (VD: A1B2C3D4)"] = "8-character code (e.g. A1B2C3D4)",

        // --- Navigation / headers ---
        ["Giám sát hệ thống & Đo từ xa"] = "System Monitoring & Telemetry",
        ["Phần cứng & Trạm ESP32"] = "Hardware & ESP32 Station",
        ["Spotify & Âm nhạc"] = "Spotify & Media",
        ["Báo thức & Còi báo"] = "Alarms & Buzzer",
        ["Công cụ & Groq AI"] = "Tools & Groq AI",
        ["Cài đặt & Giao diện"] = "Settings & Appearance",
        ["Trung tâm điều khiển DeskFlow Windows"] = "DeskFlow Windows Control Center",
        ["Xác thực & Liên kết thiết bị"] = "Device Authentication & Pairing",
        ["Liên kết thiết bị"] = "Pair device",
        ["Hủy liên kết"] = "Unpair",
        ["Làm mới dữ liệu"] = "Refresh all",
        ["Làm mới"] = "Refresh",

        // --- Telemetry ---
        ["Chưa phát nhạc"] = "Nothing playing",
        ["Spotify Desktop • Tạm dừng"] = "Spotify Desktop • Paused",
        ["Thông số ESP32 Station"] = "ESP32 station metrics",
        ["Mạnh"] = "Strong",
        ["Màn hình OLED"] = "OLED screen",
        ["Thông số Server Companion"] = "Companion server metrics",
        ["Dung lượng đĩa"] = "Disk usage",
        ["Thông số Máy tính (Windows PC)"] = "Computer metrics (Windows PC)",
        ["CPU Máy tính"] = "Computer CPU",
        ["RAM Máy tính"] = "Computer RAM",
        ["Pin máy"] = "Battery",
        ["Trạng thái"] = "Status",

        // --- Quotes / weather / ecosystem ---
        ["Danh ngôn tạo động lực (Groq AI Quotes)"] = "Motivational quotes (Groq AI)",
        ["“Kiên trì hôm nay, thành công ngày mai!”"] = "\"Persistence today, success tomorrow!\"",
        ["học tập và kiên trì"] = "study and persistence",
        ["Chủ đề (ví dụ: học tập, sáng tạo)"] = "Topic (e.g. study, creativity)",
        ["Sinh câu danh ngôn AI"] = "Generate AI quote",
        ["Ghim lên ESP32"] = "Pin to ESP32",
        ["Dự báo thời tiết 48h (Weather Forecast)"] = "48-hour weather forecast",
        ["Đang tải dữ liệu thời tiết 48h từ máy chủ..."] = "Loading the 48-hour forecast from the server...",
        ["Tổng quan hệ sinh thái"] = "Ecosystem summary",
        ["Giấy nhớ đã lưu"] = "Saved notes",
        ["Đếm ngược đang chạy"] = "Active countdowns",
        ["Khoảng dự báo thời tiết"] = "Forecast intervals",
        ["Chế độ tập trung"] = "Focus mode",

        // --- Spotify studio ---
        ["TẠM DỪNG"] = "PAUSED",
        ["Lời bài hát thời gian thực (Synced Lyrics)"] = "Live synced lyrics",
        ["Đang đồng bộ lời bài hát từ Spotify / LRCLIB..."] = "Syncing lyrics from Spotify / LRCLIB...",

        // --- Station display ---
        ["Bấm vào bất kỳ mặt đồng hồ hoặc màn hình bên dưới để xem trước. Bấm Áp dụng để gửi sang ESP32."] =
            "Tap any clock face or screen below to preview it, then Apply to send it to the ESP32.",
        ["Áp dụng lên trạm OLED"] = "Apply to the OLED station",
        ["Clock Face Studio (7 kiểu mặt đồng hồ)"] = "Clock Face Studio (7 styles)",
        ["Màu điểm nhấn đồng hồ (Clock Accent Color)"] = "Clock accent colour",
        ["Chuyển màn hình OLED"] = "Switch OLED screen",
        ["Đồng hồ"] = "Clock",
        ["Đếm ngược"] = "Countdown",
        ["Hoạt ảnh"] = "Animation",

        // --- Lamp / buzzer ---
        ["Đèn bàn WS2812B NeoPixel RGB"] = "WS2812B NeoPixel RGB lamp",
        ["Mã màu Hex"] = "Hex colour",
        ["Bảng màu (Palette)"] = "Palette",
        ["Đặt màu tùy biến"] = "Set custom colour",
        ["Đỏ"] = "Red",
        ["Xanh lá"] = "Green",
        ["Xanh dương"] = "Blue",
        ["Vàng"] = "Yellow",
        ["💡 Bật đèn"] = "💡 Turn lamp on",
        ["🌙 Tắt đèn"] = "🌙 Turn lamp off",
        ["Độ sáng LED (%)"] = "LED brightness (%)",
        ["Nhãn báo thức"] = "Alarm label",
        ["Thức dậy buổi sáng"] = "Morning wake-up",
        ["Thời lượng còi (ms)"] = "Buzzer duration (ms)",
        ["Lưu báo thức"] = "Save alarm",
        ["Làm mới danh sách"] = "Refresh list",

        // --- Timetable ---
        ["Đồng bộ lịch học các tiết sáng chiều và đồng phục lên vi điều khiển ESP32."] =
            "Sync morning/afternoon periods and uniforms to the ESP32 station.",
        ["Hiển thị TKB lên ESP32"] = "Show timetable on ESP32",
        ["Lịch học Hôm nay"] = "Today's schedule",
        ["BUỔI SÁNG"] = "MORNING",
        ["Đồng phục: --"] = "Uniform: --",
        ["Đang tải..."] = "Loading...",
        ["Lịch học Ngày mai"] = "Tomorrow's schedule",

        // --- Notes / countdowns / morse ---
        ["Ghi chú dán tường (Sticky Notes)"] = "Sticky notes",
        ["Viết ghi chú ngắn để ghim lên màn hình OLED ESP32..."] =
            "Write a short note to pin on the ESP32 OLED screen...",
        ["Thêm ghi chú"] = "Add note",
        ["Đếm ngược ngày thi & Sự kiện (Countdowns)"] = "Exam & event countdowns",
        ["Tên sự kiện (VD: Thi HK1, Tốt nghiệp)"] = "Event name (e.g. Midterm, Graduation)",
        ["Thêm đếm ngược"] = "Add countdown",
        ["Phòng thu mã Morse song chiều (Live Morse Studio)"] = "Live bidirectional Morse studio",
        ["Chuyển đổi trực tiếp thời gian thực: gõ chữ tự động dịch sang Morse, gõ/bấm Morse tự động dịch sang chữ."] =
            "Live conversion: typing text produces Morse, and typing/tapping Morse produces text.",
        ["Văn bản thường (Plain Text - Live Sync)"] = "Plain text (live sync)",
        ["Mã Morse (Morse Code - Live Sync)"] = "Morse code (live sync)",
        ["Bàn phím gõ nhanh Morse:"] = "Quick Morse keypad:",
        ["␣ [Ký tự]"] = "␣ [Letter]",
        ["⌫ Xóa ký tự"] = "⌫ Delete character",
        ["▶ Phát còi ESP32"] = "▶ Play on ESP32 buzzer",
        ["⏹ Dừng phát"] = "⏹ Stop",

        // --- MIDI ---
        ["Trình phát nhạc MIDI qua còi ESP32 (Buzzer MIDI Jukebox)"] = "MIDI player on the ESP32 buzzer",
        ["Chọn bài mẫu 8-bit hoặc chọn file .mid từ máy tính để phát qua loa còi ESP32:"] =
            "Pick an 8-bit tune or choose a .mid file from this PC to play on the ESP32 buzzer:",
        ["Giai điệu 8-bit có sẵn"] = "Built-in 8-bit tunes",
        ["▶ Phát bài mẫu"] = "▶ Play preset",
        ["📁 Chọn file .midi từ máy..."] = "📁 Choose a .midi file...",

        // --- Focus ---
        ["Chế độ tập trung & Pomodoro (Focus Mode)"] = "Focus mode & Pomodoro",
        ["Bắt đầu 25 phút"] = "Start 25 minutes",
        ["Bắt đầu 50 phút"] = "Start 50 minutes",
        ["Dừng phiên tập trung"] = "Stop focus session",
        ["Bắt đầu với số phút này"] = "Start with these minutes",
        ["Focus Mode đang bật: cửa sổ luôn nổi và không thể đóng. Giải bài toán để mở khoá."] =
            "Focus mode is on: the window stays on top and cannot be closed. Solve the math problem to unlock.",
        ["Giải bài toán để mở khoá sớm:"] = "Solve the problem to unlock early:",
        ["Đáp án (số nguyên)"] = "Answer (whole number)",
        ["Kiểm tra & Mở khoá"] = "Check & unlock",

        // --- Settings ---
        ["Cài đặt & Giao diện ứng dụng"] = "App settings & appearance",
        ["Ngôn ngữ & Chế độ tối ESP32 độc lập"] = "Language & independent ESP32 dark mode",
        ["Ngôn ngữ ứng dụng (Display Language)"] = "App language",
        ["Ngôn ngữ màn hình ESP32 (Device Language)"] = "ESP32 screen language",
        ["Chế độ tối màn hình ESP32 độc lập (Không phụ thuộc theme máy tính):"] =
            "Independent ESP32 dark mode (ignores the PC theme):",
        ["Chế độ ESP32"] = "ESP32 mode",
        ["Đồng bộ theo giờ (Sync)"] = "Scheduled (sync)",
        ["Luôn Sáng (Light Mode)"] = "Always light",
        ["Luôn Tối (Dark Mode)"] = "Always dark",
        ["Áp dụng lên màn hình ESP32"] = "Apply to the ESP32 screen",
        ["Giao diện ứng dụng Windows & Chế độ tối theo lịch"] = "Windows app theme & scheduled dark mode",
        ["Chế độ giao diện Windows"] = "Windows theme mode",
        ["Mặc định hệ thống (System Default)"] = "System default",
        ["Sáng (Light Mode)"] = "Light mode",
        ["Bắt đầu (HH:MM)"] = "Start (HH:MM)",
        ["Lưu lịch hẹn giờ"] = "Save schedule",
        ["Thiết bị kết nối & Chọn máy hiển thị thông số trên ESP32"] = "Linked devices & metrics source",
        ["Máy đang chọn để hiển thị CPU/RAM lên ESP32"] = "Machine whose CPU/RAM is shown on the ESP32",
        ["Làm mới danh sách thiết bị"] = "Refresh device list",
        ["Đổi tên"] = "Rename",
        ["Kết nối máy chủ & Kiểm tra tình trạng (/api/app/health)"] = "Server connection & health check (/api/app/health)",
        ["URL máy chủ Backend"] = "Backend server URL",
    };

    private static readonly Dictionary<string, string> EnToVi =
        new(StringComparer.Ordinal);

    static UiTranslator()
    {
        foreach (var (vi, en) in ViToEn)
        {
            // Keep the first Vietnamese wording if two keys share a translation.
            if (!EnToVi.ContainsKey(en))
            {
                EnToVi[en] = vi;
            }
        }
    }

    /// <summary>Translates a single known label.</summary>
    public static string Translate(string value, bool isVietnamese)
    {
        if (string.IsNullOrEmpty(value)) return value;
        var map = isVietnamese ? EnToVi : ViToEn;
        return map.TryGetValue(value, out var translated) ? translated : value;
    }

    /// <summary>
    /// Walks the visual tree and retranslates every TextBlock, Button, ComboBox
    /// item, header and placeholder currently showing a known string.
    /// </summary>
    public static void Apply(DependencyObject root, bool isVietnamese)
    {
        if (root == null) return;

        switch (root)
        {
            case TextBlock tb when !string.IsNullOrEmpty(tb.Text):
                tb.Text = Translate(tb.Text, isVietnamese);
                break;
            case ContentControl cc when cc.Content is string text && !string.IsNullOrWhiteSpace(text):
                cc.Content = Translate(text, isVietnamese);
                break;
            case TextBox tx when !string.IsNullOrEmpty(tx.PlaceholderText):
                tx.PlaceholderText = Translate(tx.PlaceholderText, isVietnamese);
                break;
            case ComboBox cb when !string.IsNullOrEmpty(cb.Header as string):
                cb.Header = Translate((string)cb.Header, isVietnamese);
                break;
            case FontIcon _:
            case Image _:
                return;   // leaf widgets without localisable text
        }

        int count = VisualTreeHelper.GetChildrenCount(root);
        for (int i = 0; i < count; i++)
        {
            Apply(VisualTreeHelper.GetChild(root, i), isVietnamese);
        }
    }
}
