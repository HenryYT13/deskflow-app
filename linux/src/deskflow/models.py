"""Data models, Scheduled Dark Mode evaluator, Morse converter, and persistent settings for DeskFlow Linux."""

from __future__ import annotations

import json
import os
import stat
import unicodedata
from dataclasses import dataclass, field, asdict
from datetime import datetime, time
from pathlib import Path
from typing import Any, Dict, List, Optional


DEFAULT_API_URL = "https://api.10th012013.duckdns.org"


@dataclass
class FocusLockState:
    """Tracks whether the window is hard-locked by an active Focus session.

    Kept as a plain dataclass so it can be unit-tested without GTK.
    """

    locked: bool = False

    def set_locked(self, locked: bool) -> None:
        self.locked = bool(locked)

    def should_block_close(self) -> bool:
        return self.locked

    def should_block_quit(self) -> bool:
        return self.locked


class ScheduledDarkModeEvaluator:
    """Evaluates Scheduled Dark Mode across same-day, overnight, and equal-time windows."""

    @staticmethod
    def parse_minutes(time_str: str, fallback_minutes: int) -> int:
        if not time_str or not isinstance(time_str, str):
            return fallback_minutes
        parts = time_str.strip().split(":")
        if len(parts) != 2:
            return fallback_minutes
        try:
            hour = int(parts[0])
            minute = int(parts[1])
        except ValueError:
            return fallback_minutes
        if not (0 <= hour <= 23 and 0 <= minute <= 59):
            return fallback_minutes
        return hour * 60 + minute

    @staticmethod
    def normalize_time_str(time_str: Optional[str], fallback: str = "20:00") -> str:
        fallback_min = ScheduledDarkModeEvaluator.parse_minutes(fallback, 20 * 60)
        parsed = ScheduledDarkModeEvaluator.parse_minutes(time_str or "", fallback_min)
        return f"{parsed // 60:02d}:{parsed % 60:02d}"

    @staticmethod
    def normalize_theme(theme: Optional[str]) -> str:
        cleaned = (theme or "").strip().lower()
        if cleaned in ("light", "dark", "scheduled"):
            return cleaned
        return "system"

    @classmethod
    def is_scheduled_dark_active(
        cls,
        now_minutes: int,
        start_str: str = "20:00",
        end_str: str = "06:00",
        fallback_when_equal: bool = False,
    ) -> bool:
        start_min = cls.parse_minutes(start_str, 20 * 60)
        end_min = cls.parse_minutes(end_str, 6 * 60)
        current = now_minutes % 1440
        if start_min == end_min:
            return fallback_when_equal
        if start_min < end_min:
            # Same-day window, e.g. 09:00 -> 17:00
            return start_min <= current < end_min
        # Overnight window across midnight, e.g. 20:00 -> 06:00
        return current >= start_min or current < end_min

    @classmethod
    def resolve_dark_theme(
        cls,
        theme_preference: str,
        system_is_dark: bool,
        start_str: str = "20:00",
        end_str: str = "06:00",
        now_minutes: Optional[int] = None,
    ) -> bool:
        mode = cls.normalize_theme(theme_preference)
        if mode == "light":
            return False
        if mode == "dark":
            return True
        if mode == "scheduled":
            if now_minutes is None:
                now = datetime.now()
                now_minutes = now.hour * 60 + now.minute
            return cls.is_scheduled_dark_active(now_minutes, start_str, end_str, False)
        return system_is_dark


class MorseCodeConverter:
    """Bidirectional ITU Morse Code converter with Vietnamese diacritic normalization."""

    CHAR_TO_MORSE: Dict[str, str] = {
        "A": ".-", "B": "-...", "C": "-.-.", "D": "-..", "E": ".",
        "F": "..-.", "G": "--.", "H": "....", "I": "..", "J": ".---",
        "K": "-.-", "L": ".-..", "M": "--", "N": "-.", "O": "---",
        "P": ".--.", "Q": "--.-", "R": ".-.", "S": "...", "T": "-",
        "U": "..-", "V": "...-", "W": ".--", "X": "-..-", "Y": "-.--",
        "Z": "--..",
        "0": "-----", "1": ".----", "2": "..---", "3": "...--", "4": "....-",
        "5": ".....", "6": "-....", "7": "--...", "8": "---..", "9": "----.",
        ".": ".-.-.-", ",": "--..--", "?": "..--..", "'": ".----.",
        "!": "-.-.--", "/": "-..-.", "(": "-.--.", ")": "-.--.-",
        "&": ".-...", ":": "---...", ";": "-.-.-.", "=": "-...-",
        "+": ".-.-.", "-": "-....-", "_": "..--.-", '"': ".-..-.",
        "$": "...-..-", "@": ".--.-.",
    }
    MORSE_TO_CHAR: Dict[str, str] = {v: k for k, v in CHAR_TO_MORSE.items()}

    @classmethod
    def strip_diacritics(cls, text: str) -> str:
        text = text.replace("đ", "d").replace("Đ", "D")
        normalized = unicodedata.normalize("NFD", text)
        return "".join(ch for ch in normalized if unicodedata.category(ch) != "Mn")

    @classmethod
    def text_to_morse(cls, text: str) -> str:
        clean = cls.strip_diacritics(text or "").upper().strip()
        if not clean:
            return ""
        words = clean.split()
        encoded_words: List[str] = []
        for word in words:
            chars = [cls.CHAR_TO_MORSE[ch] for ch in word if ch in cls.CHAR_TO_MORSE]
            if chars:
                encoded_words.append(" ".join(chars))
        return " / ".join(encoded_words)

    @classmethod
    def morse_to_text(cls, morse: str) -> str:
        clean = (morse or "").strip()
        if not clean:
            return ""
        word_tokens = [w.strip() for w in clean.replace("|", "/").split("/")]
        decoded_words: List[str] = []
        for w_tok in word_tokens:
            if not w_tok:
                continue
            letters = [cls.MORSE_TO_CHAR.get(code, "?") for code in w_tok.split()]
            decoded_words.append("".join(letters))
        return " ".join(decoded_words)


@dataclass
class NoteItem:
    id: str
    content: str
    color: str = "#FDE047"
    pinned: bool = False
    created_at: str = ""

    @classmethod
    def from_dict(cls, raw: Dict[str, Any], active_id: Optional[str] = None) -> "NoteItem":
        nid = str(raw.get("id", "")).strip()
        content = str(raw.get("content") or raw.get("text") or "").strip()
        pinned = bool(
            raw.get("pinned")
            or raw.get("is_pinned")
            or raw.get("is_active_on_station")
            or (active_id and nid == active_id)
        )
        return cls(
            id=nid,
            content=content,
            color=str(raw.get("color") or "#FDE047"),
            pinned=pinned,
            created_at=str(raw.get("created_at") or ""),
        )


@dataclass
class CountdownItem:
    id: str
    name: str
    target_date: str
    days_left: int = 0
    pinned: bool = False

    @classmethod
    def from_dict(cls, raw: Dict[str, Any], active_id: Optional[str] = None) -> "CountdownItem":
        cid = str(raw.get("id", "")).strip()
        name = str(raw.get("name") or raw.get("title") or "").strip()
        pinned = bool(
            raw.get("is_pinned")
            or raw.get("pinned")
            or raw.get("is_active_on_station")
            or (active_id and cid == active_id)
        )
        return cls(
            id=cid,
            name=name,
            target_date=str(raw.get("target_date") or ""),
            days_left=int(raw.get("days_left") or 0),
            pinned=pinned,
        )


@dataclass
class AlarmItem:
    id: str
    time: str
    label: str = "Alarm"
    enabled: bool = True
    repeat_days: List[int] = field(default_factory=list)
    buzzer_duration: int = 250
    math_challenge: bool = True

    @classmethod
    def from_dict(cls, raw: Dict[str, Any]) -> "AlarmItem":
        return cls(
            id=str(raw.get("id", "")),
            time=str(raw.get("time", "07:00")),
            label=str(raw.get("label", "Alarm")),
            enabled=bool(raw.get("enabled", True)),
            repeat_days=list(raw.get("repeat_days", [])),
            buzzer_duration=int(raw.get("buzzer_duration", 250)),
            math_challenge=bool(raw.get("math_challenge", True)),
        )


@dataclass
class PairedDeviceItem:
    device_id: str
    device_name: str
    platform: str
    is_current: bool = False
    last_seen_at: Optional[str] = None

    @classmethod
    def from_dict(cls, raw: Dict[str, Any]) -> "PairedDeviceItem":
        return cls(
            device_id=str(raw.get("device_id", "")),
            device_name=str(raw.get("device_name", "Device")),
            platform=str(raw.get("platform", "unknown")),
            is_current=bool(raw.get("is_current", False)),
            last_seen_at=raw.get("last_seen_at"),
        )


@dataclass
class WeatherInterval:
    time: str
    temp: float
    description: str
    rain_prob: float
    is_rain: bool

    @classmethod
    def from_dict(cls, raw: Dict[str, Any]) -> "WeatherInterval":
        t_str = str(raw.get("time") or raw.get("dt_txt") or "")
        desc = str(raw.get("description") or raw.get("weather_desc") or raw.get("weather_main") or "")
        pop = float(raw.get("pop") or 0.0)
        rain_prob = float(raw.get("rain_prob") if raw.get("rain_prob") is not None else pop * 100.0)
        return cls(
            time=t_str,
            temp=float(raw.get("temp") or 0.0),
            description=desc,
            rain_prob=rain_prob,
            is_rain=bool(raw.get("is_rain", False)),
        )


@dataclass
class TimetableDay:
    day: str = ""
    display_day: str = ""
    morning: List[str] = field(default_factory=list)
    dp_sang: str = "Tự do"
    has_afternoon: bool = False
    afternoon: List[str] = field(default_factory=list)
    dp_chieu: str = ""
    has_any_class: bool = False

    @classmethod
    def from_dict(cls, raw: Dict[str, Any]) -> "TimetableDay":
        return cls(
            day=str(raw.get("day") or ""),
            display_day=str(raw.get("display_day") or raw.get("day") or ""),
            morning=list(raw.get("morning") or []),
            dp_sang=str(raw.get("dp_sang") or "Tự do"),
            has_afternoon=bool(raw.get("has_afternoon", False)),
            afternoon=list(raw.get("afternoon") or []),
            dp_chieu=str(raw.get("dp_chieu") or ""),
            has_any_class=bool(raw.get("has_any_class", False)),
        )


@dataclass
class DualDayTimetable:
    week_number: str = "N/A"
    today: TimetableDay = field(default_factory=TimetableDay)
    tomorrow: TimetableDay = field(default_factory=TimetableDay)

    @classmethod
    def from_dict(cls, raw: Dict[str, Any]) -> "DualDayTimetable":
        today_raw = raw.get("today") if isinstance(raw.get("today"), dict) else {}
        tomorrow_raw = raw.get("tomorrow") if isinstance(raw.get("tomorrow"), dict) else {}
        return cls(
            week_number=str(raw.get("week_number") or "N/A"),
            today=TimetableDay.from_dict(today_raw),
            tomorrow=TimetableDay.from_dict(tomorrow_raw),
        )


@dataclass
class SpotifyStatus:
    is_playing: bool = False
    connected: bool = False
    track: Optional[str] = None
    artist: Optional[str] = None
    album: Optional[str] = None
    progress_ms: int = 0
    duration_ms: int = 1
    progress_pct: int = 0
    current_lyric: Optional[str] = None
    next_lyric: Optional[str] = None
    lyrics_window: List[str] = field(default_factory=list)
    active_lyric_index: int = 2
    cover_url: Optional[str] = None

    @classmethod
    def from_dict(cls, raw: Dict[str, Any]) -> "SpotifyStatus":
        return cls(
            is_playing=bool(raw.get("is_playing", False)),
            connected=bool(raw.get("connected", False)),
            track=raw.get("track"),
            artist=raw.get("artist"),
            album=raw.get("album"),
            progress_ms=int(raw.get("progress_ms") or 0),
            duration_ms=int(raw.get("duration_ms") or 1),
            progress_pct=int(raw.get("progress_pct") or 0),
            current_lyric=raw.get("lyric_curr"),
            next_lyric=raw.get("lyric_next"),
            lyrics_window=list(raw.get("lyrics_window") or []),
            active_lyric_index=int(raw.get("active_lyric_index") or 2),
            cover_url=raw.get("cover_url"),
        )


@dataclass
class LinuxAppConfig:
    api_url: str = DEFAULT_API_URL
    device_token: Optional[str] = None
    device_id: Optional[str] = None
    device_name: str = "DeskFlow Linux GTK4"
    scopes: List[str] = field(default_factory=lambda: ["focus", "control", "spotify"])
    app_theme: str = "system"  # "system", "light", "dark", "scheduled"
    dark_mode_start: str = "20:00"
    dark_mode_end: str = "06:00"
    language: str = "vi"
    esp32_language: str = "vi"

    @property
    def is_paired(self) -> bool:
        return bool(self.device_token and self.device_token.strip())


class ConfigStore:
    """Secure local JSON preferences store for DeskFlow Linux."""

    def __init__(self, config_path: Optional[Path] = None) -> None:
        if config_path is None:
            xdg = os.environ.get("XDG_CONFIG_HOME")
            base = Path(xdg) if xdg else (Path.home() / ".config")
            config_path = base / "deskflow" / "settings.json"
        self.config_path = config_path
        self.config = self._load()

    def _load(self) -> LinuxAppConfig:
        if not self.config_path.exists():
            return LinuxAppConfig()
        try:
            raw = json.loads(self.config_path.read_text(encoding="utf-8"))
            api_url = str(raw.get("api_url") or DEFAULT_API_URL).rstrip("/")
            migrated = False
            if not api_url or "focus-api.10th012013.duckdns.org" in api_url:
                api_url = DEFAULT_API_URL
                migrated = True

            cfg = LinuxAppConfig(
                api_url=api_url,
                device_token=raw.get("device_token"),
                device_id=raw.get("device_id"),
                device_name=str(raw.get("device_name") or "DeskFlow Linux GTK4"),
                scopes=list(raw.get("scopes") or ["focus", "control", "spotify"]),
                app_theme=ScheduledDarkModeEvaluator.normalize_theme(raw.get("app_theme")),
                dark_mode_start=ScheduledDarkModeEvaluator.normalize_time_str(
                    raw.get("dark_mode_start"), "20:00"
                ),
                dark_mode_end=ScheduledDarkModeEvaluator.normalize_time_str(
                    raw.get("dark_mode_end"), "06:00"
                ),
                language=str(raw.get("language") or "vi"),
                esp32_language="en" if str(raw.get("esp32_language") or "vi").lower().startswith("en") else "vi",
            )
            if migrated:
                self.config = cfg
                self.save()
            return cfg
        except Exception:
            return LinuxAppConfig()

    def save(self) -> None:
        self.config.app_theme = ScheduledDarkModeEvaluator.normalize_theme(self.config.app_theme)
        self.config.dark_mode_start = ScheduledDarkModeEvaluator.normalize_time_str(
            self.config.dark_mode_start, "20:00"
        )
        self.config.dark_mode_end = ScheduledDarkModeEvaluator.normalize_time_str(
            self.config.dark_mode_end, "06:00"
        )
        self.config.api_url = (self.config.api_url or DEFAULT_API_URL).rstrip("/")
        self.config_path.parent.mkdir(parents=True, exist_ok=True)
        self.config_path.write_text(
            json.dumps(asdict(self.config), indent=2, ensure_ascii=False),
            encoding="utf-8",
        )
        try:
            os.chmod(self.config_path, stat.S_IRUSR | stat.S_IWUSR)
        except OSError:
            pass

    def clear_auth(self) -> None:
        self.config.device_token = None
        self.config.device_id = None
        self.save()


STRINGS = {
    "en": {
        "app_title": "Deskflow",
        "dashboard": "Dashboard & Telemetry",
        "focus": "Focus Mode & Challenge",
        "alarms": "Alarms & Scheduling",
        "station": "Station Hardware & Media",
        "tools": "Tools & AI Studio",
        "weather": "Weather Forecast",
        "timetable": "Class Timetable",
        "settings": "Settings & Devices",
        "categories_title": "Available Feature Categories",
        "categories_desc": "Quickly access and manage all Deskflow ecosystem capabilities.",
        "esp_telemetry_title": "ESP32 Station Hardware Telemetry",
        "esp_telemetry_desc": "Direct telemetry from the physical Deskflow Station microcontroller.",
        "music_player_title": "Now Playing (Spotify & System)",
        "refresh_btn": "Refresh Station Data",
        "pair_device": "Pair Device",
        "unpair_device": "Unpair Device",
        "theme_mode": "Application Theme",
        "esp_theme_mode": "ESP32 Display Appearance",
        "language_selector": "Display Language",
        "apply_schedule": "Apply Schedule",
        "save_url": "Save URL",
        "test_connection": "Test Connection",
        "refresh_devices": "Refresh Devices",
        "rename": "Rename",
        "unlink": "Unlink",
        "clock_studio": "Clock Face Studio (7 Watch Face Styles)",
        "oled_switcher": "OLED Display View Switcher",
        "apply_oled": "Apply to Station OLED",
        "morse_studio": "Morse Code Studio",
        "play_morse": "Play on Buzzer",
        "stop_morse": "Stop Playback",
        "quote_title": "AI Study Quotes (Groq AI)",
        "generate_quote": "Generate Quote",
        "notes_title": "Sticky Notes",
        "countdowns_title": "Exam Countdowns",
        "lamp_title": "WS2812B Custom LED & Lamp",
        "custom_color": "Custom Color",
        "set_color": "Set Color",
        "lamp_off": "Lamp Off",
        "brightness": "LED Brightness",
    },
    "vi": {
        "app_title": "Deskflow",
        "dashboard": "Bảng điều khiển & Thông số",
        "focus": "Chế độ tập trung & Thử thách",
        "alarms": "Báo thức & Lập lịch",
        "station": "Phần cứng Trạm & Media",
        "tools": "Công cụ & Studio AI",
        "weather": "Dự báo thời tiết",
        "timetable": "Thời khóa biểu",
        "settings": "Cài đặt & Thiết bị",
        "categories_title": "Danh mục tính năng khả dụng",
        "categories_desc": "Truy cập và điều khiển nhanh các phân hệ trong hệ sinh thái Deskflow.",
        "esp_telemetry_title": "Thông số phần cứng Trạm ESP32",
        "esp_telemetry_desc": "Dữ liệu trực tiếp từ vi điều khiển phần cứng Deskflow Station.",
        "music_player_title": "Đang phát nhạc (Spotify & Hệ thống)",
        "refresh_btn": "Làm mới dữ liệu",
        "pair_device": "Ghép nối thiết bị",
        "unpair_device": "Hủy ghép nối",
        "theme_mode": "Giao diện ứng dụng",
        "esp_theme_mode": "Giao diện màn hình ESP32",
        "language_selector": "Ngôn ngữ hiển thị",
        "apply_schedule": "Áp dụng lịch",
        "save_url": "Lưu URL",
        "test_connection": "Kiểm tra kết nối",
        "refresh_devices": "Làm mới thiết bị",
        "rename": "Đổi tên",
        "unlink": "Hủy liên kết",
        "clock_studio": "Studio mặt đồng hồ (7 giao diện OLED)",
        "oled_switcher": "Bộ chuyển đổi trang hiển thị OLED",
        "apply_oled": "Áp dụng lên OLED Trạm",
        "morse_studio": "Phòng thu mã Morse",
        "play_morse": "Phát qua còi chip",
        "stop_morse": "Dừng phát",
        "quote_title": "Châm ngôn học tập AI (Groq AI)",
        "generate_quote": "Tạo câu châm ngôn",
        "notes_title": "Ghi chú dán trạm",
        "countdowns_title": "Đếm ngược kỳ thi",
        "lamp_title": "Đèn bàn LED WS2812B tùy chỉnh",
        "custom_color": "Màu tùy chọn",
        "set_color": "Áp dụng màu",
        "lamp_off": "Tắt đèn",
        "brightness": "Độ sáng LED",
    },
}
