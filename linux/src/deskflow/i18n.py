"""Runtime Vietnamese ⇄ English translation for the Linux GTK client.

The GTK UI declares its labels inline in Vietnamese. Instead of threading a
translation call through every widget, this module keeps a table of the strings
the UI actually shows and swaps them on the live widget tree whenever the user
changes the interface language.
"""

from __future__ import annotations

from typing import Dict, List, Optional, Tuple

VI_TO_EN: Dict[str, str] = {
    # Pairing gate
    "Yêu cầu Ghép nối Thiết bị": "Device Pairing Required",
    "Quét mã QR để ghép nối tức thì": "Scan the QR code to pair instantly",
    "Đang tạo phiên mã QR...": "Creating a QR session...",
    "Làm mới mã QR": "Refresh QR code",
    "Hoặc nhập mã ghép nối 8 ký tự thủ công": "Or enter the 8-character pairing code manually",
    "Mã ghép nối (8 ký tự, VD: A1B2C3D4)": "Pairing code (8 characters, e.g. A1B2C3D4)",
    "Máy chủ DeskFlow": "DeskFlow server",
    # Telemetry
    "Thông số ESP32 Station": "ESP32 station metrics",
    "Đang tải Pin &amp; Sóng Wi-Fi từ trạm ESP32...": "Loading battery & Wi-Fi from the ESP32 station...",
    "Thông số Server Companion": "Companion server metrics",
    "Đang tải CPU, RAM &amp; Dung lượng (? / 20GB) máy chủ...": "Loading server CPU, RAM & storage (? / 20GB)...",
    "Thông số Máy tính (đang đặt để show spec)": "Computer metrics (selected for display)",
    "Đang đồng bộ CPU, RAM &amp; Pin máy tính...": "Syncing computer CPU, RAM & battery...",
    # Spotify
    "Đang phát nhạc (Spotify)": "Now playing (Spotify)",
    "Chưa phát nhạc": "Nothing playing",
    "Spotify Desktop • Tạm dừng": "Spotify Desktop • Paused",
    "Trình phát Spotify (Spotify Studio)": "Spotify player (Spotify Studio)",
    "TẠM DỪNG": "PAUSED",
    "ĐANG PHÁT": "NOW PLAYING",
    "Tiến trình bài hát": "Track progress",
    "Điều khiển phát nhạc": "Playback controls",
    "Âm lượng Spotify (0 - 100%)": "Spotify volume (0 - 100%)",
    "Lời bài hát thời gian thực (Synced Lyrics)": "Live synced lyrics",
    "Lời hiện tại": "Current line",
    "Đang chờ bài hát có lời...": "Waiting for a track with lyrics...",
    "Câu tiếp theo": "Next line",
    # Timetable
    "Thông tin tuần học": "Week information",
    "Đang tải dữ liệu thời khóa biểu...": "Loading timetable data...",
    "Hiển thị TKB lên màn hình ESP32": "Show timetable on the ESP32 screen",
    "HÔM NAY": "TODAY",
    "NGÀY KẾ TIẾP": "NEXT SCHOOL DAY",
    "Lịch học hôm nay": "Today's schedule",
    "Lịch học kế tiếp": "Next school day",
    "Tiết học sáng": "Morning periods",
    "Tiết học chiều": "Afternoon periods",
    "Đang cập nhật...": "Updating...",
    "Đồng phục sáng": "AM uniform",
    "Đồng phục chiều": "PM uniform",
    "Quy định đồng phục": "Uniform rules",
    # Notes / quotes / midi
    "Chủ đề câu danh ngôn": "Quote topic",
    "“Kiên trì hôm nay, thành công ngày mai!”": "\"Persistence today, success tomorrow!\"",
    "Phát nhạc MIDI trên còi ESP32": "Play MIDI on the ESP32 buzzer",
    "Giai điệu có sẵn": "Built-in tunes",
    "Chọn bài rồi bấm Phát.": "Pick a tune, then press Play.",
    "Điều khiển phát nhạc": "Playback controls",
    "Phát bài đã chọn, hoặc chọn file .mid từ máy tính.": "Play the selected tune, or pick a .mid file from this computer.",
    "▶ Phát bài mẫu": "▶ Play preset",
    "📂 Chọn file .mid": "📂 Choose a .mid file",
    "⏹ Dừng": "⏹ Stop",
    # Station / clock
    "Màu điểm nhấn đồng hồ (Accent Color)": "Clock accent colour",
    "Mã màu Hex (#RRGGBB)": "Hex colour (#RRGGBB)",
    "Áp dụng màu": "Apply colour",
    "Bảng màu gợi ý": "Suggested palette",
    "7 màu đèn có sẵn trên ESP32": "7 built-in ESP32 lamp colours",
    "Đèn LED trạm ESP32 (GPIO 2)": "ESP32 station lamp (GPIO 2)",
    # Settings
    "Display Language (Ngôn ngữ hiển thị)": "Display language",
    "Ngôn ngữ màn hình ESP32 (Device Language)": "ESP32 screen language",
    "Ngôn ngữ hiển thị của đồng hồ/OLED trên trạm, tách biệt ngôn ngữ ứng dụng.": "Language of the station clock/OLED, independent of the app language.",
    "Màn hình đang hoạt động trên trạm": "Currently active station screen",
    # Focus
    "Chế độ tập trung": "Focus mode",
    "Chế độ Tập trung": "Focus mode",
}

EN_TO_VI: Dict[str, str] = {}
for _vi, _en in VI_TO_EN.items():
    EN_TO_VI.setdefault(_en, _vi)


def translate(value: str, language: str) -> str:
    """Translate one label to the requested language (idempotent)."""
    if not value:
        return value
    table = VI_TO_EN if language != "vi" else EN_TO_VI
    return table.get(value, value)


def _iter_widgets(widget) -> List[object]:
    import gi  # local import so the module can be unit-tested without GTK

    gi.require_version("Gtk", "4.0")
    from gi.repository import Gtk

    found: List[object] = []
    stack = [widget]
    while stack:
        current = stack.pop()
        found.append(current)
        child = current.get_first_child() if isinstance(current, Gtk.Widget) else None
        while child is not None:
            stack.append(child)
            child = child.get_next_sibling()
    return found


def apply_to_widget_tree(root, language: str) -> int:
    """Translate every recognised label in a GTK widget tree.

    Returns the number of widgets whose text changed.
    """
    if root is None:
        return 0

    import gi

    gi.require_version("Adw", "1")
    gi.require_version("Gtk", "4.0")
    from gi.repository import Adw, Gtk

    changed = 0
    for widget in _iter_widgets(root):
        try:
            # Adw.PreferencesGroup / ActionRow / EntryRow / ComboRow all expose
            # a title and most of them a subtitle.
            if isinstance(widget, (Adw.PreferencesGroup, Adw.ActionRow, Adw.EntryRow, Adw.ComboRow, Adw.SpinRow)):
                if hasattr(widget, "get_title"):
                    title = widget.get_title()
                    new_title = translate(title, language)
                    if new_title != title:
                        widget.set_title(new_title)
                        changed += 1
                if hasattr(widget, "get_subtitle"):
                    subtitle = widget.get_subtitle()
                    if subtitle:
                        new_sub = translate(subtitle, language)
                        if new_sub != subtitle:
                            widget.set_subtitle(new_sub)
                            changed += 1
            elif isinstance(widget, Gtk.Label):
                text = widget.get_text()
                new_text = translate(text, language)
                if new_text != text:
                    widget.set_text(new_text)
                    changed += 1
            elif isinstance(widget, Gtk.Button):
                label = widget.get_label()
                if label:
                    new_label = translate(label, language)
                    if new_label != label:
                        widget.set_label(new_label)
                        changed += 1
            elif isinstance(widget, Gtk.CheckButton):
                label = widget.get_label()
                if label:
                    new_label = translate(label, language)
                    if new_label != label:
                        widget.set_label(new_label)
                        changed += 1
        except Exception:
            # Never let a single exotic widget break the language switch.
            continue

    return changed
