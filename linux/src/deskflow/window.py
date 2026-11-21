"""Native GTK4 + Libadwaita Main Window for DeskFlow Linux Desktop."""

from __future__ import annotations

import calendar as py_calendar
import threading
from datetime import datetime, timedelta
from typing import Any, Callable, Dict, List, Optional

import gi

gi.require_version("Gtk", "4.0")
gi.require_version("Adw", "1")
from gi.repository import Adw, Gdk, Gio, GLib, Gtk

import io
from .api_client import ApiResult, DeskFlowApiClient, logger
from .i18n import apply_to_widget_tree
from .models import (
    AlarmItem,
    CalendarEventItem,
    CalendarStatus,
    ConfigStore,
    CountdownItem,
    DualDayTimetable,
    FocusLockState,
    MorseCodeConverter,
    NoteItem,
    PairedDeviceItem,
    ScheduledDarkModeEvaluator,
    TimetableDay,
    WeatherInterval,
)


CUSTOM_CSS = """
.deskflow-card {
    background-color: alpha(@card_bg_color, 0.96);
    border-radius: 14px;
    padding: 16px;
    border: 1px solid alpha(@borders, 0.45);
}
.deskflow-badge-online {
    background-color: alpha(#10B981, 0.18);
    color: #10B981;
    border-radius: 999px;
    padding: 4px 10px;
    font-weight: 700;
}
.deskflow-badge-offline {
    background-color: alpha(#EF4444, 0.18);
    color: #EF4444;
    border-radius: 999px;
    padding: 4px 10px;
    font-weight: 700;
}
.deskflow-badge-pinned {
    background-color: alpha(#F59E0B, 0.22);
    color: #F59E0B;
    border-radius: 999px;
    padding: 2px 8px;
    font-weight: 700;
}
.deskflow-mono {
    font-family: "JetBrains Mono", "Monospace", monospace;
}
.deskflow-qr-frame {
    background-color: #FFFFFF;
    border-radius: 12px;
    padding: 10px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}
.oled-accent-text {
    color: #38BDF8 !important;
}
.oled-bezel {
    background-color: #060911;
    border: 2px solid #00E5FF;
    border-radius: 12px;
    padding: 14px;
}
.oled-glass {
    background-color: #030509;
    border-radius: 8px;
    padding: 12px;
    border: 1px solid rgba(0, 229, 255, 0.3);
}
.oled-text-large {
    color: #00E5FF;
    font-size: 24px;
    font-weight: 800;
    font-family: monospace;
}
.oled-text-mid {
    color: #FFFFFF;
    font-size: 13px;
    font-weight: 700;
    font-family: monospace;
}
.oled-text-sub {
    color: #94A3B8;
    font-size: 10px;
    font-family: monospace;
}
.oled-mini-box {
    background-color: #0A0E17;
    border-radius: 6px;
    border: 1px solid rgba(255, 255, 255, 0.12);
    padding: 6px 10px;
}
"""


class DeskFlowMainWindow(Adw.ApplicationWindow):
    """Primary GTK4 + Libadwaita window with full DeskFlow ecosystem control."""

    PAGES = [
        ("dashboard", "Giám sát hệ thống & Đo từ xa", "computer-symbolic"),
        ("station", "Phần cứng & Trạm ESP32", "preferences-desktop-display-symbolic"),
        ("tkb", "Thời khóa biểu (TKB)", "x-office-calendar-symbolic"),
        ("calendar", "Lịch Google (Calendar)", "x-office-calendar-symbolic"),
        ("spotify", "Spotify & Âm nhạc", "audio-x-generic-symbolic"),
        ("alarms", "Báo thức & Còi báo", "alarm-symbolic"),
        ("tools", "Công cụ & Groq AI", "document-edit-symbolic"),
        ("settings", "Cài đặt & Giao diện", "emblem-system-symbolic"),
    ]

    # (key, Vietnamese title, English title, Vietnamese desc, English desc)
    DISPLAY_VIEWS = [
        ("main", "Màn hình chính", "Main Dashboard",
         "Mặc định: đồng hồ, thời tiết, nhạc & danh ngôn",
         "Default: clock, weather, music & quotes"),
        ("clock", "Mặt đồng hồ", "Clock Face",
         "Đồng hồ số kèm ngày tháng", "Digital clock with date & day"),
        ("weather", "Thời tiết", "Weather",
         "Nhiệt độ và dự báo Hạ Long", "Ha Long temperature & forecast"),
        ("notes", "Ghi chú", "Sticky Note",
         "Ghi chú được ghim lên trạm", "Pinned reminder on station"),
        ("countdown", "Đếm ngược", "Countdown",
         "Đếm ngược ngày thi", "Exam deadline countdown"),
        ("focus", "Tập trung", "Focus Mode",
         "Bộ hẹn giờ tập trung", "Focus session timer"),
        ("stats", "Thông số máy chủ", "Server Stats",
         "CPU & RAM của máy chủ", "Host CPU & RAM utilization"),
        ("tkb", "Thời khóa biểu", "Timetable",
         "Lịch học và đồng phục", "Class schedule & uniform"),
        ("calendar", "Lịch Google", "Google Calendar",
         "Sự kiện sắp tới từ Google Calendar", "Upcoming Google Calendar events"),
        ("lyrics", "Lời bài hát", "Spotify Lyrics",
         "Lời bài hát và đang phát", "Live lyrics & now playing"),
    ]

    CLOCK_STYLES = [
        (1, "digital_classic", "Digital Classic", "Bold digits with seconds & date", "12:30 :45"),
        (2, "digital_minimal", "Minimalist", "Clean borderless typography", "12:30:45"),
        (3, "digital_neon", "Neon Cyber", "Cyber rounded neon frame", "[ 12:30 ]"),
        (4, "analog", "Analog Dial", "Round 3-hand analog clock", "(● 12:30)"),
        (5, "flip", "Flip Clock", "Retro split-card flip display", "[ 12 ]:[ 30 ]"),
        (6, "binary", "Binary Matrix", "BCD 4×4 LED matrix", ":: :: :: ::"),
        (7, "word", "Word Clock", "Spelled out text clock", "TWELVE 30"),
    ]

    def __init__(
        self,
        application: Adw.Application,
        config_store: Optional[ConfigStore] = None,
        api_client: Optional[DeskFlowApiClient] = None,
    ) -> None:
        super().__init__(application=application)
        self.set_title("Deskflow")
        self.set_default_size(1120, 740)
        self.set_icon_name("com.deskflow.Station")

        self.config_store = config_store or ConfigStore()
        self.api_client = api_client or DeskFlowApiClient(self.config_store)
        self._active_challenge_id: Optional[str] = None
        self.focus_lock = FocusLockState()
        self._deleting_note_ids: set[str] = set()
        self._deleting_countdown_ids: set[str] = set()
        self.preview_oled_view: str = "clock"
        self.live_station_view: str = ""
        self.preview_clock_style: int = 1
        self.clock_accent_color: str = "#38BDF8"
        self._suppress_lang_sync: bool = False
        self._last_server_language: Optional[str] = None
        self._qr_session_id: Optional[str] = None
        self._qr_poll_source_id: Optional[int] = None
        self._is_polling_qr: bool = False
        self.clock_accent_provider = Gtk.CssProvider()

        self._load_css()
        self._build_ui()
        self.apply_theme_preference()
        self._apply_clock_accent_color(self.clock_accent_color, notify_backend=False)

        # Focus Mode hard lock: closing the window is refused while a session is active.
        self.connect("close-request", self._on_close_request)
        GLib.timeout_add_seconds(2, self._initial_focus_lock_check)

        # Periodic timer (every 15s) to evaluate Scheduled Dark Mode boundaries
        GLib.timeout_add_seconds(15, self._on_theme_timer_tick)
        # Keep the Focus lock in sync when a session ends or is unlocked elsewhere.
        GLib.timeout_add_seconds(15, self._on_focus_poll_tick)

    def _load_css(self) -> None:
        provider = Gtk.CssProvider()
        provider.load_from_string(CUSTOM_CSS)
        display = Gdk.Display.get_default()
        if display is not None:
            Gtk.StyleContext.add_provider_for_display(
                display, provider, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION
            )
            Gtk.StyleContext.add_provider_for_display(
                display, self.clock_accent_provider, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION + 10
            )

    def _build_ui(self) -> None:
        self.toast_overlay = Adw.ToastOverlay()
        self.set_content(self.toast_overlay)

        root_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=0)
        self.toast_overlay.set_child(root_box)

        # HeaderBar
        self.header_bar = Adw.HeaderBar()
        self.title_widget = Adw.WindowTitle(
            title="Deskflow",
            subtitle=self._connection_subtitle(),
        )
        self.header_bar.set_title_widget(self.title_widget)

        refresh_btn = Gtk.Button(icon_name="view-refresh-symbolic")
        refresh_btn.set_tooltip_text("Refresh All Station Data (Ctrl+R)")
        refresh_btn.connect("clicked", lambda *_: self.refresh_all_async())
        self.header_bar.pack_end(refresh_btn)

        root_box.append(self.header_bar)

        # Main deck: switches between Pairing Gate (when unpaired) and Main Dashboard (when paired)
        self.main_deck = Gtk.Stack()
        self.main_deck.set_transition_type(Gtk.StackTransitionType.CROSSFADE)
        self.main_deck.set_transition_duration(200)
        self.main_deck.set_vexpand(True)
        root_box.append(self.main_deck)

        self.pairing_gate = self._build_pairing_gate_page()
        self.main_deck.add_named(self.pairing_gate, "gate")

        # Split layout: Sidebar + Content Stack
        paned = Gtk.Paned(orientation=Gtk.Orientation.HORIZONTAL)
        paned.set_position(250)
        paned.set_shrink_start_child(False)
        paned.set_shrink_end_child(False)
        paned.set_vexpand(True)
        self.main_deck.add_named(paned, "main")

        # Sidebar
        sidebar_scroll = Gtk.ScrolledWindow()
        sidebar_scroll.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)
        sidebar_scroll.set_size_request(240, -1)

        self.sidebar_list = Gtk.ListBox()
        self.sidebar_list.set_selection_mode(Gtk.SelectionMode.SINGLE)
        self.sidebar_list.add_css_class("navigation-sidebar")
        self.sidebar_list.connect("row-selected", self._on_sidebar_row_selected)
        sidebar_scroll.set_child(self.sidebar_list)
        paned.set_start_child(sidebar_scroll)

        # Content stack
        self.content_stack = Gtk.Stack()
        self.content_stack.set_transition_type(Gtk.StackTransitionType.CROSSFADE)
        self.content_stack.set_transition_duration(180)
        self.content_stack.set_hexpand(True)
        self.content_stack.set_vexpand(True)
        paned.set_end_child(self.content_stack)

        # Build pages
        self.content_stack.add_named(self._build_dashboard_page(), "dashboard")
        self.content_stack.add_named(self._build_station_page(), "station")
        self.content_stack.add_named(self._build_spotify_page(), "spotify")
        self.content_stack.add_named(self._build_alarms_page(), "alarms")
        self.content_stack.add_named(self._build_tkb_page(), "tkb")
        self.content_stack.add_named(self._build_calendar_page(), "calendar")
        self.content_stack.add_named(self._build_tools_page(), "tools")
        self.content_stack.add_named(self._build_settings_page(), "settings")
        # Direct alias used by the dashboard "weather" shortcut button.
        self.content_stack.add_named(self._build_weather_page(), "weather")

        for page_id, title, icon_name in self.PAGES:
            row = Gtk.ListBoxRow()
            row.page_id = page_id  # type: ignore[attr-defined]
            box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)
            box.set_margin_top(10)
            box.set_margin_bottom(10)
            box.set_margin_start(12)
            box.set_margin_end(12)
            icon = Gtk.Image.new_from_icon_name(icon_name)
            lbl = Gtk.Label(label=title, xalign=0.0)
            lbl.set_hexpand(True)
            box.append(icon)
            box.append(lbl)
            row.title_label = lbl  # type: ignore[attr-defined]
            row.set_child(box)
            self.sidebar_list.append(row)

        first_row = self.sidebar_list.get_row_at_index(0)
        if first_row is not None:
            self.sidebar_list.select_row(first_row)

        self._sync_pairing_state()

        # Strict 1-second background telemetry reporting loop
        GLib.timeout_add_seconds(1, self._on_telemetry_tick)

    def _on_telemetry_tick(self) -> bool:
        cpu = 0.0
        ram = 0.0
        bat = 100
        try:
            import psutil
            cpu = float(psutil.cpu_percent(interval=None))
            ram = float(psutil.virtual_memory().percent)
            sensors = psutil.sensors_battery()
            if sensors:
                bat = int(sensors.percent)
        except Exception:
            pass

        def _do_telemetry():
            try:
                self.api_client.report_telemetry(cpu=cpu, ram_pct=ram, battery=bat)
            except Exception:
                pass

            try:
                res = self.api_client.get_station_status()
                if res.ok and isinstance(res.data, dict):
                    data = res.data.get("data") if ("data" in res.data and isinstance(res.data["data"], dict)) else res.data
                    station = data.get("station") or {}
                    server = data.get("server") or {}
                    computer = data.get("computer") or data.get("laptop") or {}

                    bat_pct = station.get("battery_pct", 100)
                    volt = station.get("voltage", 4.12)
                    rssi = station.get("wifi_rssi", -55)
                    esp_sub = f"Pin: {bat_pct}% ({volt}V) • Sóng Wi-Fi: {rssi} dBm • View: {station.get('current_view', 'N/A')}"

                    srv_cpu = server.get("cpu", 0.0)
                    srv_ram = server.get("ram_pct", 0.0)
                    srv_disk = server.get("disk_str") or f"{server.get('disk_used_gb', 0)} / {server.get('disk_total_gb', 20)}GB"
                    srv_sub = f"CPU: {srv_cpu}% • RAM: {srv_ram}% • Dung lượng: {srv_disk} • Uptime: {server.get('uptime', 'N/A')}"

                    comp_name = computer.get("device_name", self.config_store.config.device_name)
                    comp_cpu = computer.get("cpu", cpu)
                    comp_ram = computer.get("ram", ram)
                    comp_bat = computer.get("battery", bat)
                    comp_on = "Online" if computer.get("online", True) else "Offline"
                    comp_sub = f"Thiết bị: {comp_name} • CPU: {comp_cpu}% • RAM: {comp_ram}% • Pin: {comp_bat}% • Trạng thái: {comp_on}"

                    station_view = str(station.get("current_view") or "").strip().lower()
                    srv_lang_raw = str(station.get("language") or "").strip().lower()
                    if srv_lang_raw not in ("vi", "en"):
                        try:
                            clk_res = self.api_client.get_clock_config()
                            if clk_res.ok and isinstance(clk_res.data, dict):
                                clk_data = clk_res.data.get("data") if isinstance(clk_res.data.get("data"), dict) else clk_res.data
                                cand = str(clk_data.get("language") or "").strip().lower()
                                if cand in ("vi", "en"):
                                    srv_lang_raw = cand
                        except Exception:
                            pass

                    def _update_ui():
                        self.esp_status_row.set_subtitle(esp_sub)
                        self.server_status_row.set_subtitle(srv_sub)
                        self.computer_status_row.set_subtitle(comp_sub)
                        # Show which screen the station is really on, but never
                        # overwrite the preview the user picked: doing that made
                        # the preview snap back every second.
                        self.live_station_view = station_view
                        self._update_active_view_badge()
                        if srv_lang_raw in ("vi", "en") and srv_lang_raw != self._last_server_language:
                            self._last_server_language = srv_lang_raw
                            self._apply_server_language(srv_lang_raw)
                        return False

                    GLib.idle_add(_update_ui)

                    # Spotify telemetry poll
                    try:
                        res_sp = self.api_client.get_spotify_status()
                        if res_sp.ok and isinstance(res_sp.data, dict):
                            sp_data = res_sp.data.get("data") if ("data" in res_sp.data and isinstance(res_sp.data["data"], dict)) else res_sp.data
                            GLib.idle_add(lambda: (self._apply_spotify_data(sp_data), False)[1])
                    except Exception:
                        pass
            except Exception:
                pass

        threading.Thread(target=_do_telemetry, daemon=True).start()
        return True

    def _connection_subtitle(self) -> str:
        cfg = self.config_store.config
        if cfg.is_paired:
            return f"Paired ({cfg.device_name}) • {cfg.api_url}"
        return f"Unpaired (Inspection Mode) • {cfg.api_url}"

    def show_toast(self, message: str) -> None:
        toast = Adw.Toast.new(message)
        toast.set_timeout(2)
        self.toast_overlay.add_toast(toast)
        GLib.timeout_add(1500, toast.dismiss)

    def _run_bg(
        self,
        worker: Callable[[], Any],
        on_done: Callable[[Any], None],
    ) -> None:
        def _target() -> None:
            result = worker()
            GLib.idle_add(lambda: (on_done(result), False)[1])

        threading.Thread(target=_target, daemon=True).start()

    def _on_sidebar_row_selected(self, _listbox: Gtk.ListBox, row: Optional[Gtk.ListBoxRow]) -> None:
        if row is None:
            return
        page_id = getattr(row, "page_id", "dashboard")
        self.content_stack.set_visible_child_name(page_id)

    def _generate_qr_texture(self, payload: str) -> Optional[Gdk.Texture]:
        try:
            import qrcode
            qr = qrcode.QRCode(
                version=1,
                error_correction=qrcode.constants.ERROR_CORRECT_M,
                box_size=6,
                border=2,
            )
            qr.add_data(payload)
            qr.make(fit=True)
            img = qr.make_image(fill_color="black", back_color="white")
            buf = io.BytesIO()
            img.save(buf, format="PNG")
            data = buf.getvalue()
            gbytes = GLib.Bytes.new(data)
            return Gdk.Texture.new_from_bytes(gbytes)
        except Exception as e:
            logger.error("[QR] Failed to generate QR texture: %s", e)
            return None

    def _start_qr_pairing_session(self) -> None:
        if not hasattr(self, "gate_qr_picture"):
            return
        self._stop_qr_pairing_session()
        self.gate_qr_status.set_text("Đang tạo phiên mã QR...")
        self.gate_qr_picture.set_paintable(None)

        def _on_created(res: ApiResult) -> None:
            if not res.ok or not isinstance(res.data, dict) or not res.data.get("session_id"):
                err = res.error_message or "Không thể tạo phiên ghép nối QR."
                self.gate_qr_status.set_text(f"Lỗi: {err}")
                return
            sess_id = str(res.data["session_id"])
            qr_payload = str(res.data.get("qr_payload") or "")
            self._qr_session_id = sess_id
            tex = self._generate_qr_texture(qr_payload)
            if tex:
                self.gate_qr_picture.set_paintable(tex)
                self.gate_qr_status.set_text("Mở app DeskFlow trên điện thoại > Quét mã QR để ghép nối tự động.")
            else:
                self.gate_qr_status.set_text("Không thể tạo hình ảnh QR.")

            self._is_polling_qr = True
            self._qr_poll_source_id = GLib.timeout_add(1500, self._poll_pair_session_tick)

        self._run_bg(lambda: self.api_client.create_pair_session(platform="linux"), _on_created)

    def _poll_pair_session_tick(self) -> bool:
        if not self._is_polling_qr or not self._qr_session_id or self.config_store.config.is_paired:
            return False

        sess_id = self._qr_session_id

        def _check() -> None:
            res = self.api_client.get_pair_session_status(sess_id)

            def _handle_ui() -> bool:
                if not self._is_polling_qr or self._qr_session_id != sess_id:
                    return False
                if res.ok and isinstance(res.data, dict):
                    status = res.data.get("status")
                    if status == "approved":
                        self._stop_qr_pairing_session()
                        self.show_toast("Đã ghép nối thiết bị thành công qua mã QR!")
                        self._sync_pairing_state()
                        self.refresh_all_async()
                        return False
                    elif status in ("rejected", "expired"):
                        self._stop_qr_pairing_session()
                        self.gate_qr_status.set_text("Phiên mã QR đã hết hạn hoặc bị từ chối. Bấm nút dưới để tạo lại.")
                        return False
                return False

            GLib.idle_add(_handle_ui)

        threading.Thread(target=_check, daemon=True).start()
        return True

    def _stop_qr_pairing_session(self) -> None:
        self._is_polling_qr = False
        if self._qr_poll_source_id is not None:
            try:
                GLib.source_remove(self._qr_poll_source_id)
            except Exception:
                pass
            self._qr_poll_source_id = None

    def _apply_clock_accent_color(self, hex_val: str, notify_backend: bool = True) -> None:
        clean = (hex_val or "").strip().upper()
        if not clean.startswith("#"):
            clean = f"#{clean}"
        if len(clean) != 7:
            return
        self.clock_accent_color = clean
        css = f"""
        .oled-accent-text {{
            color: {clean} !important;
        }}
        """
        self.clock_accent_provider.load_from_string(css)
        self._update_oled_preview()
        if hasattr(self, "clock_accent_entry"):
            if self.clock_accent_entry.get_text().strip().upper() != clean:
                self.clock_accent_entry.set_text(clean)
        if notify_backend:
            self._run_bg(
                lambda: self.api_client.set_clock_style(self.preview_clock_style, accent_color=self.clock_accent_color),
                lambda r: self.show_toast(f"Đã cập nhật màu đồng hồ: {clean}" if r.ok else r.error_message),
            )

    def _sync_pairing_state(self) -> None:
        """Enforces Pairing Gate: if unpaired, blocks main UI until paired."""
        is_paired = self.config_store.config.is_paired
        if is_paired:
            self._stop_qr_pairing_session()
            self.main_deck.set_visible_child_name("main")
            self.title_widget.set_subtitle(self._connection_subtitle())
            if hasattr(self, "unpair_btn"):
                self.unpair_btn.set_sensitive(True)
        else:
            self.main_deck.set_visible_child_name("gate")
            self.title_widget.set_subtitle("Chưa ghép nối • Quét mã QR hoặc nhập mã")
            if hasattr(self, "unpair_btn"):
                self.unpair_btn.set_sensitive(False)
            self._start_qr_pairing_session()

    def _build_pairing_gate_page(self) -> Gtk.Widget:
        scroller = Gtk.ScrolledWindow()
        scroller.set_policy(Gtk.PolicyType.NEVER, Gtk.PolicyType.AUTOMATIC)

        clamp = Adw.Clamp()
        clamp.set_maximum_size(660)
        clamp.set_tightening_threshold(480)
        clamp.set_margin_top(30)
        clamp.set_margin_bottom(30)
        clamp.set_margin_start(24)
        clamp.set_margin_end(24)

        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=20)
        clamp.set_child(box)
        scroller.set_child(clamp)

        # Status Page Banner
        status_page = Adw.StatusPage(
            icon_name="network-wireless-symbolic",
            title="Yêu cầu Ghép nối Thiết bị",
            description="Để sử dụng Trung tâm điều khiển DeskFlow, bạn phải ghép nối máy tính này với tài khoản hệ sinh thái DeskFlow.",
        )
        box.append(status_page)

        # QR Code Pairing Card
        qr_group = Adw.PreferencesGroup(
            title="Quét mã QR để ghép nối tức thì",
            description="Mở ứng dụng DeskFlow trên điện thoại > Quét mã QR để tự động đăng nhập thiết bị.",
        )
        box.append(qr_group)

        qr_card = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=12)
        qr_card.set_halign(Gtk.Align.CENTER)
        qr_card.set_margin_top(8)
        qr_card.set_margin_bottom(8)

        qr_frame = Gtk.Box()
        qr_frame.set_size_request(220, 220)
        qr_frame.set_halign(Gtk.Align.CENTER)
        qr_frame.add_css_class("deskflow-qr-frame")

        self.gate_qr_picture = Gtk.Picture()
        self.gate_qr_picture.set_size_request(200, 200)
        self.gate_qr_picture.set_can_shrink(True)
        self.gate_qr_picture.set_halign(Gtk.Align.CENTER)
        self.gate_qr_picture.set_valign(Gtk.Align.CENTER)
        qr_frame.append(self.gate_qr_picture)
        qr_card.append(qr_frame)

        self.gate_qr_status = Gtk.Label(label="Đang tạo phiên mã QR...")
        self.gate_qr_status.add_css_class("caption")
        self.gate_qr_status.set_wrap(True)
        self.gate_qr_status.set_justify(Gtk.Justification.CENTER)
        qr_card.append(self.gate_qr_status)

        refresh_qr_btn = Gtk.Button(label="Làm mới mã QR")
        refresh_qr_btn.add_css_class("flat")
        refresh_qr_btn.set_halign(Gtk.Align.CENTER)
        refresh_qr_btn.connect("clicked", lambda *_: self._start_qr_pairing_session())
        qr_card.append(refresh_qr_btn)

        qr_group.add(qr_card)

        # Pairing action card (manual 8-character code entry)
        group = Adw.PreferencesGroup(
            title="Hoặc nhập mã ghép nối 8 ký tự thủ công",
            description="Gõ lệnh /app pair trên Discord hoặc mở app DeskFlow trên điện thoại để nhận mã 8 ký tự.",
        )
        box.append(group)

        self.gate_code_entry = Adw.EntryRow(title="Mã ghép nối (8 ký tự, VD: A1B2C3D4)")
        self.gate_code_entry.connect("entry-activated", lambda *_: self._on_gate_pair_clicked())
        group.add(self.gate_code_entry)

        action_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)
        action_box.set_halign(Gtk.Align.CENTER)
        action_box.set_margin_top(14)
        action_box.set_margin_bottom(6)

        self.gate_pair_btn = Gtk.Button(label="Ghép nối thiết bị")
        self.gate_pair_btn.add_css_class("suggested-action")
        self.gate_pair_btn.add_css_class("pill")
        self.gate_pair_btn.set_size_request(220, 44)
        self.gate_pair_btn.connect("clicked", lambda *_: self._on_gate_pair_clicked())
        action_box.append(self.gate_pair_btn)

        group.add(action_box)

        self.gate_status_label = Gtk.Label(label="", wrap=True)
        self.gate_status_label.add_css_class("error")
        self.gate_status_label.set_margin_top(8)
        group.add(self.gate_status_label)

        # Server info row
        server_row = Adw.ActionRow(
            title="Máy chủ DeskFlow",
            subtitle=self.config_store.config.api_url,
        )
        group.add(server_row)

        return scroller

    def _on_gate_pair_clicked(self) -> None:
        code = self.gate_code_entry.get_text().strip().upper()
        if not code:
            self.gate_status_label.set_text("Mã ghép nối không được để trống. Hãy gõ /app pair trên Discord.")
            return

        self.gate_pair_btn.set_sensitive(False)
        self.gate_status_label.set_text("Đang kết nối và xác thực với máy chủ...")

        def _done(res: ApiResult) -> None:
            self.gate_pair_btn.set_sensitive(True)
            if res.ok:
                self.gate_status_label.set_text("")
                self.gate_code_entry.set_text("")
                self.show_toast("Ghép nối thiết bị thành công!")
                self._sync_pairing_state()
                self.refresh_all_async()
            else:
                self.gate_status_label.set_text(f"Lỗi: {res.error_message}")
                self.show_toast(f"Ghép nối thất bại: {res.error_message}")

        self._run_bg(lambda: self.api_client.pair_device(code), _done)

    # =========================================================================
    # Theme & Scheduled Dark Mode Management
    # =========================================================================
    def _on_theme_timer_tick(self) -> bool:
        if self.config_store.config.app_theme == "scheduled":
            self.apply_theme_preference()
        return True

    def _on_focus_poll_tick(self) -> bool:
        """Auto-unlocks when the session expires or is ended on another device."""
        if not self.config_store.config.device_token:
            return True

        def _done(res: ApiResult) -> None:
            if not res.ok or not isinstance(res.data, dict):
                return
            active = bool(res.data.get("focus_active") or res.data.get("enabled"))
            if active == self.focus_lock.locked:
                return
            self._set_focus_lock(active)
            if not active:
                self.focus_state_row.set_subtitle("Inactive — Session ended.")
                self.focus_dash_row.set_subtitle("Inactive — Session ended.")

        self._run_bg(self.api_client.get_focus_status, _done)
        return True

    def apply_theme_preference(self) -> bool:
        """Applies System, Light, Dark, or Scheduled Dark Mode via Adw.StyleManager."""
        cfg = self.config_store.config
        style_mgr = Adw.StyleManager.get_default()
        mode = ScheduledDarkModeEvaluator.normalize_theme(cfg.app_theme)

        if mode == "light":
            style_mgr.set_color_scheme(Adw.ColorScheme.FORCE_LIGHT)
            is_dark = False
            badge = "Theme: Light"
        elif mode == "dark":
            style_mgr.set_color_scheme(Adw.ColorScheme.FORCE_DARK)
            is_dark = True
            badge = "Theme: Dark"
        elif mode == "scheduled":
            now = datetime.now()
            now_min = now.hour * 60 + now.minute
            is_dark = ScheduledDarkModeEvaluator.is_scheduled_dark_active(
                now_min, cfg.dark_mode_start, cfg.dark_mode_end, False
            )
            style_mgr.set_color_scheme(
                Adw.ColorScheme.FORCE_DARK if is_dark else Adw.ColorScheme.FORCE_LIGHT
            )
            state_str = "Dark Active" if is_dark else "Light Active"
            badge = f"Scheduled ({cfg.dark_mode_start}–{cfg.dark_mode_end}: {state_str})"
        else:
            style_mgr.set_color_scheme(Adw.ColorScheme.DEFAULT)
            is_dark = style_mgr.get_dark()
            badge = "Theme: System"

        if hasattr(self, "schedule_status_row"):
            self.schedule_status_row.set_subtitle(
                f"Window {cfg.dark_mode_start} → {cfg.dark_mode_end} • Currently {'DARK' if is_dark else 'LIGHT'}"
            )
        return is_dark

    # =========================================================================
    # Page 1: Dashboard & Telemetry + Pairing
    # =========================================================================
    def _build_dashboard_page(self) -> Gtk.Widget:
        page = Adw.PreferencesPage()

        # Pairing group
        pair_group = Adw.PreferencesGroup(
            title="Device Authentication &amp; Pairing",
            description="Pair this Linux workstation with the DeskFlow backend using a pairing code from Discord (/pair).",
        )
        page.add(pair_group)

        self.pair_status_row = Adw.ActionRow(
            title="Pairing Status",
            subtitle=GLib.markup_escape_text(self._connection_subtitle()),
        )
        self.unpair_btn = Gtk.Button(label="Unpair", valign=Gtk.Align.CENTER)
        self.unpair_btn.add_css_class("destructive-action")
        self.unpair_btn.set_sensitive(self.config_store.config.is_paired)
        self.unpair_btn.connect("clicked", self._on_unpair_clicked)
        self.pair_status_row.add_suffix(self.unpair_btn)
        pair_group.add(self.pair_status_row)

        self.pair_code_entry = Adw.EntryRow(title="Pairing Code (from Discord /pair)")
        self.pair_code_entry.connect("entry-activated", self._on_pair_clicked)
        self.pair_btn = Gtk.Button(label="Pair Device", valign=Gtk.Align.CENTER)
        self.pair_btn.add_css_class("suggested-action")
        self.pair_btn.connect("clicked", self._on_pair_clicked)
        self.pair_code_entry.add_suffix(self.pair_btn)
        pair_group.add(self.pair_code_entry)

        # Available Feature Categories (Features 8 & 10)
        cat_group = Adw.PreferencesGroup(
            title="Available Feature Categories",
            description="Quickly navigate into the DeskFlow ecosystem categories.",
        )
        page.add(cat_group)

        cat_row = Adw.ActionRow(
            title="Hardware &amp; Station",
            subtitle="LED ring lighting, OLED display switcher, and buzzer.",
        )
        cat_btn = Gtk.Button(label="Open", valign=Gtk.Align.CENTER)
        cat_btn.connect("clicked", lambda *_: self.content_stack.set_visible_child_name("station"))
        cat_row.add_suffix(cat_btn)
        cat_group.add(cat_row)

        alarm_cat_row = Adw.ActionRow(
            title="Alarms &amp; Sound",
            subtitle="Math alarms, scheduling, and emergency stop.",
        )
        alarm_btn = Gtk.Button(label="Open", valign=Gtk.Align.CENTER)
        alarm_btn.connect("clicked", lambda *_: self.content_stack.set_visible_child_name("alarms"))
        alarm_cat_row.add_suffix(alarm_btn)
        cat_group.add(alarm_cat_row)

        tools_cat_row = Adw.ActionRow(
            title="Productivity &amp; AI Tools",
            subtitle="Sticky notes, countdowns, AI motivational quotes, and Morse studio.",
        )
        tools_btn = Gtk.Button(label="Open", valign=Gtk.Align.CENTER)
        tools_btn.connect("clicked", lambda *_: self.content_stack.set_visible_child_name("tools"))
        tools_cat_row.add_suffix(tools_btn)
        cat_group.add(tools_cat_row)

        cfg_cat_row = Adw.ActionRow(
            title="Remote Configuration",
            subtitle="Appearance, independent ESP32 dark mode, language, and display devices.",
        )
        cfg_btn = Gtk.Button(label="Open", valign=Gtk.Align.CENTER)
        cfg_btn.connect("clicked", lambda *_: self.content_stack.set_visible_child_name("settings"))
        cfg_cat_row.add_suffix(cfg_btn)
        cat_group.add(cfg_cat_row)

        # Telemetry group
        telemetry_group = Adw.PreferencesGroup(
            title="Live Ecosystem Telemetry",
            description="Real-time metrics from ESP32 Station, Server, and Focus Engine.",
        )
        page.add(telemetry_group)

        self.esp_status_row = Adw.ActionRow(
            title="Thông số ESP32 Station",
            subtitle="Đang tải Pin &amp; Sóng Wi-Fi từ trạm ESP32...",
        )
        telemetry_group.add(self.esp_status_row)

        self.server_status_row = Adw.ActionRow(
            title="Thông số Server Companion",
            subtitle="Đang tải CPU, RAM &amp; Dung lượng (? / 20GB) máy chủ...",
        )
        telemetry_group.add(self.server_status_row)

        self.computer_status_row = Adw.ActionRow(
            title="Thông số Máy tính (đang đặt để show spec)",
            subtitle="Đang đồng bộ CPU, RAM &amp; Pin máy tính...",
        )
        telemetry_group.add(self.computer_status_row)

        # Spotify Now Playing Mini Player
        sp_mini_group = Adw.PreferencesGroup(
            title="Đang phát nhạc (Spotify)",
            description="Trạng thái bài hát và điều khiển Spotify Desktop.",
        )
        page.add(sp_mini_group)

        self.sp_mini_row = Adw.ActionRow(
            title="Chưa phát nhạc",
            subtitle="Spotify Desktop • Tạm dừng",
        )
        sp_icon = Gtk.Image.new_from_icon_name("audio-x-generic-symbolic")
        sp_icon.set_pixel_size(32)
        self.sp_mini_row.add_prefix(sp_icon)

        btn_mini_prev = Gtk.Button(icon_name="media-skip-backward-symbolic", valign=Gtk.Align.CENTER)
        btn_mini_prev.connect(
            "clicked",
            lambda *_: self._run_bg(
                lambda: self.api_client.control_spotify("previous"),
                lambda _: None,
            ),
        )
        self.sp_mini_row.add_suffix(btn_mini_prev)

        self.sp_mini_play_btn = Gtk.Button(icon_name="media-playback-start-symbolic", valign=Gtk.Align.CENTER)
        def _mini_toggle(*_):
            cmd = "pause" if getattr(self, "_sp_is_playing", False) else "play"
            self._run_bg(lambda: self.api_client.control_spotify(cmd), lambda _: None)
        self.sp_mini_play_btn.connect("clicked", _mini_toggle)
        self.sp_mini_row.add_suffix(self.sp_mini_play_btn)

        btn_mini_next = Gtk.Button(icon_name="media-skip-forward-symbolic", valign=Gtk.Align.CENTER)
        btn_mini_next.connect(
            "clicked",
            lambda *_: self._run_bg(
                lambda: self.api_client.control_spotify("next"),
                lambda _: None,
            ),
        )
        self.sp_mini_row.add_suffix(btn_mini_next)

        btn_open_sp = Gtk.Button(label="Mở Spotify Studio", valign=Gtk.Align.CENTER)
        btn_open_sp.add_css_class("suggested-action")
        btn_open_sp.connect("clicked", lambda *_: self.content_stack.set_visible_child_name("spotify"))
        self.sp_mini_row.add_suffix(btn_open_sp)
        sp_mini_group.add(self.sp_mini_row)

        self.focus_dash_row = Adw.ActionRow(
            title="Focus Mode Engine",
            subtitle="Status: Idle",
        )
        telemetry_group.add(self.focus_dash_row)

        return page

    def _on_pair_clicked(self, _widget: Gtk.Widget) -> None:
        code = self.pair_code_entry.get_text().strip()
        logger.info(
            "[UI] Pair button clicked (code_length=%d, api_url=%s)",
            len(code),
            self.api_client.base_url,
        )
        if not code:
            logger.warning("[UI] Pairing aborted: empty pairing code input.")
            self.pair_status_row.set_subtitle(
                GLib.markup_escape_text("Error: Pairing code cannot be empty.")
            )
            self.show_toast("Pairing code cannot be empty. Generate a code in Discord (/pair).")
            return
        self.pair_btn.set_sensitive(False)
        self.pair_status_row.set_subtitle("Pairing with DeskFlow backend...")

        def _done(res: ApiResult) -> None:
            self.pair_btn.set_sensitive(True)
            if res.ok:
                self.pair_code_entry.set_text("")
                self.pair_status_row.set_subtitle(
                    GLib.markup_escape_text(self._connection_subtitle())
                )
                self.title_widget.set_subtitle(self._connection_subtitle())
                self.unpair_btn.set_sensitive(True)
                self.show_toast("Device paired with DeskFlow backend!")
                self._sync_pairing_state()
                self.refresh_all_async()
            else:
                self.pair_status_row.set_subtitle(
                    GLib.markup_escape_text(f"Pairing failed: {res.error_message}")
                )
                self.show_toast(f"Pairing failed: {res.error_message}")

        self._run_bg(lambda: self.api_client.pair_device(code), _done)

    def _on_unpair_clicked(self, _btn: Gtk.Button) -> None:
        def _done(_res: ApiResult) -> None:
            self.pair_status_row.set_subtitle(self._connection_subtitle())
            self.title_widget.set_subtitle(self._connection_subtitle())
            self.unpair_btn.set_sensitive(False)
            self.show_toast("Device unpaired.")
            self._sync_pairing_state()

        self._run_bg(self.api_client.unpair_device, _done)

    # =========================================================================
    # Page: Spotify & Media Studio
    # =========================================================================
    def _build_spotify_page(self) -> Gtk.Widget:
        page = Adw.PreferencesPage()

        player_group = Adw.PreferencesGroup(
            title="Trình phát Spotify (Spotify Studio)",
            description="Điều khiển phát nhạc, danh sách phát Lo-fi và đồng bộ lời bài hát thời gian thực.",
        )
        page.add(player_group)

        # The album art is drawn as the prefix of the track row, so a separate
        # 180x180 picture duplicated the same cover further down the page.
        self._cover_url: Optional[str] = None
        self._cover_request_id = 0

        self.sp_track_row = Adw.ActionRow(
            title="Chưa phát nhạc",
            subtitle="Spotify Desktop Client",
        )
        self.sp_track_icon = Gtk.Image.new_from_icon_name("audio-x-generic-symbolic")
        self.sp_track_icon.set_pixel_size(96)
        self.sp_track_row.add_prefix(self.sp_track_icon)

        self.sp_status_badge = Gtk.Label(label="TẠM DỪNG")
        self.sp_status_badge.add_css_class("badge")
        self.sp_track_row.add_suffix(self.sp_status_badge)
        player_group.add(self.sp_track_row)

        progress_row = Adw.ActionRow(title="Tiến trình bài hát")
        self.sp_progress_bar = Gtk.ProgressBar()
        self.sp_progress_bar.set_fraction(0.0)
        self.sp_progress_bar.set_hexpand(True)
        self.sp_progress_bar.set_valign(Gtk.Align.CENTER)
        progress_row.add_suffix(self.sp_progress_bar)

        self.sp_time_label = Gtk.Label(label="00:00 / 00:00")
        self.sp_time_label.add_css_class("monospace")
        progress_row.add_suffix(self.sp_time_label)
        player_group.add(progress_row)

        ctrl_row = Adw.ActionRow(title="Điều khiển phát nhạc")
        btn_prev = Gtk.Button(icon_name="media-skip-backward-symbolic", valign=Gtk.Align.CENTER)
        btn_prev.connect(
            "clicked",
            lambda *_: self._run_bg(
                lambda: self.api_client.control_spotify("previous"),
                lambda r: self.show_toast("Spotify: Previous" if r.ok else r.error_message),
            ),
        )
        ctrl_row.add_suffix(btn_prev)

        self.sp_play_btn = Gtk.Button(icon_name="media-playback-start-symbolic", valign=Gtk.Align.CENTER)
        self.sp_play_btn.add_css_class("suggested-action")
        def _toggle_play(*_):
            cmd = "pause" if getattr(self, "_sp_is_playing", False) else "play"
            self._run_bg(
                lambda: self.api_client.control_spotify(cmd),
                lambda r: self.show_toast(f"Spotify: {cmd}" if r.ok else r.error_message),
            )
        self.sp_play_btn.connect("clicked", _toggle_play)
        ctrl_row.add_suffix(self.sp_play_btn)

        btn_next = Gtk.Button(icon_name="media-skip-forward-symbolic", valign=Gtk.Align.CENTER)
        btn_next.connect(
            "clicked",
            lambda *_: self._run_bg(
                lambda: self.api_client.control_spotify("next"),
                lambda r: self.show_toast("Spotify: Next" if r.ok else r.error_message),
            ),
        )
        ctrl_row.add_suffix(btn_next)

        btn_refresh = Gtk.Button(icon_name="view-refresh-symbolic", valign=Gtk.Align.CENTER)
        btn_refresh.connect("clicked", lambda *_: self._refresh_spotify_ui())
        ctrl_row.add_suffix(btn_refresh)
        player_group.add(ctrl_row)

        vol_row = Adw.ActionRow(title="Âm lượng Spotify (0 - 100%)")
        self.sp_vol_scale = Gtk.Scale.new_with_range(Gtk.Orientation.HORIZONTAL, 0, 100, 5)
        self.sp_vol_scale.set_value(50)
        self.sp_vol_scale.set_size_request(180, -1)
        self.sp_vol_scale.set_valign(Gtk.Align.CENTER)
        self.sp_vol_scale.connect(
            "value-changed",
            lambda s: self._run_bg(
                lambda: self.api_client.set_spotify_volume(int(s.get_value())),
                lambda _: None,
            ),
        )
        vol_row.add_suffix(self.sp_vol_scale)
        player_group.add(vol_row)

        lyrics_group = Adw.PreferencesGroup(
            title="Lời bài hát thời gian thực (Synced Lyrics)",
            description="Đồng bộ lời bài hát từ Spotify và cơ sở dữ liệu LRCLIB.",
        )
        page.add(lyrics_group)

        self.sp_curr_lyric_row = Adw.ActionRow(
            title="Lời hiện tại",
            subtitle="Đang chờ bài hát có lời...",
        )
        lyrics_group.add(self.sp_curr_lyric_row)

        self.sp_next_lyric_row = Adw.ActionRow(
            title="Câu tiếp theo",
            subtitle="...",
        )
        lyrics_group.add(self.sp_next_lyric_row)

        return page

    def _refresh_spotify_ui(self) -> None:
        def _fetch():
            res = self.api_client.get_spotify_status()
            if res.ok and isinstance(res.data, dict):
                data = res.data.get("data") if ("data" in res.data and isinstance(res.data["data"], dict)) else res.data
                self._apply_spotify_data(data)
        self._run_bg(_fetch, lambda _: None)

    def _apply_spotify_data(self, data: dict) -> None:
        if not data:
            return
        track = data.get("track") or "Chưa phát nhạc"
        artist = data.get("artist") or "Spotify Desktop"
        album = data.get("album") or ""
        is_playing = bool(data.get("is_playing", False))
        self._sp_is_playing = is_playing
        progress_ms = int(data.get("progress_ms") or 0)
        duration_ms = max(1, int(data.get("duration_ms") or 1))
        pct = max(0.0, min(1.0, float(progress_ms) / float(duration_ms)))
        cur_lyric = data.get("lyric_curr") or "Đang chờ bài hát có lời..."
        next_lyric = data.get("lyric_next") or ""

        def _fmt(ms: int) -> str:
            sec = max(0, ms // 1000)
            return f"{sec // 60:02d}:{sec % 60:02d}"

        time_str = f"{_fmt(progress_ms)} / {_fmt(duration_ms)}"

        if hasattr(self, "sp_track_row"):
            cover_url = data.get("cover_url") or data.get("album_art_url") or data.get("image_url")
            if cover_url and cover_url != self._cover_url:
                self._load_cover_art(str(cover_url))
            elif not cover_url and self._cover_url:
                self._cover_url = None
                self.sp_track_icon.set_from_icon_name("audio-x-generic-symbolic")

            self.sp_track_row.set_title(track)
            self.sp_track_row.set_subtitle(f"{artist} • {album}" if album else artist)
            self.sp_status_badge.set_text("ĐANG PHÁT" if is_playing else "TẠM DỪNG")
            self.sp_play_btn.set_icon_name("media-playback-pause-symbolic" if is_playing else "media-playback-start-symbolic")
            self.sp_progress_bar.set_fraction(pct)
            self.sp_time_label.set_text(time_str)
            self.sp_curr_lyric_row.set_subtitle(cur_lyric)
            self.sp_next_lyric_row.set_subtitle(next_lyric)

        if hasattr(self, "sp_mini_row"):
            self.sp_mini_row.set_title(track)
            self.sp_mini_row.set_subtitle(f"{artist} • {'Đang phát' if is_playing else 'Tạm dừng'}")
            self.sp_mini_play_btn.set_icon_name("media-playback-pause-symbolic" if is_playing else "media-playback-start-symbolic")

    def _load_cover_art(self, url: str) -> None:
        """Fetch Spotify album art off the UI thread and paint it when ready."""
        self._cover_url = url
        self._cover_request_id += 1
        request_id = self._cover_request_id

        def _fetch() -> None:
            texture = None
            try:
                import urllib.request

                request = urllib.request.Request(url, headers={"User-Agent": "DeskFlow/1.0"})
                with urllib.request.urlopen(request, timeout=8) as response:
                    payload = response.read(5 * 1024 * 1024 + 1)
                if len(payload) <= 5 * 1024 * 1024:
                    texture = Gdk.Texture.new_from_bytes(GLib.Bytes.new(payload))
            except Exception as exc:
                logger.debug("[Spotify] Cover art unavailable: %s", exc)

            def _apply() -> bool:
                if request_id == self._cover_request_id and texture is not None:
                    self.sp_track_icon.set_from_paintable(texture)
                return False

            GLib.idle_add(_apply)

        threading.Thread(target=_fetch, daemon=True).start()

    # =========================================================================
    # Page: Unified Tools & Groq AI (Notes, Countdowns, Morse, Focus)
    # =========================================================================
    def _build_tools_page(self) -> Gtk.Widget:
        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8)
        switcher = Adw.ViewSwitcher()
        switcher.set_policy(Adw.ViewSwitcherPolicy.WIDE)
        stack = Adw.ViewStack()
        switcher.set_stack(stack)
        box.append(switcher)

        self.tools_tabs: Dict[str, tuple] = {
            "notes": ("Ghi chú & AI", "Notes & AI"),
            "countdowns": ("Đếm ngược", "Countdowns"),
            "morse": ("Mã Morse Studio", "Morse Studio"),
            "focus": ("Chế độ tập trung", "Focus Mode"),
        }
        stack.add_titled_with_icon(self._build_notes_page(), "notes", self._tr(*self.tools_tabs["notes"]), "document-edit-symbolic")
        stack.add_titled_with_icon(self._build_countdowns_page(), "countdowns", self._tr(*self.tools_tabs["countdowns"]), "x-office-calendar-symbolic")
        stack.add_titled_with_icon(self._build_morse_page(), "morse", self._tr(*self.tools_tabs["morse"]), "audio-speakers-symbolic")
        stack.add_titled_with_icon(self._build_focus_page(), "focus", self._tr("Chế độ tập trung", "Focus Mode"), "preferences-system-time-symbolic")
        self.tools_stack = stack

        box.append(stack)
        return box

    # =========================================================================
    # Page 2: Focus Mode & Math Challenge
    # =========================================================================
    def _build_focus_page(self) -> Gtk.Widget:
        page = Adw.PreferencesPage()

        session_group = Adw.PreferencesGroup(
            title="Focus Mode Control",
            description="Synchronizes website blocking across the browser extension, ESP32 OLED, and Android.",
        )
        page.add(session_group)

        self.focus_state_row = Adw.ActionRow(
            title="Current Focus Session",
            subtitle="Inactive — Ready to start.",
        )
        session_group.add(self.focus_state_row)

        self.focus_lock_row = Adw.ActionRow(
            title="Focus Lock (Always on top · no close · no quit)",
            subtitle="Inactive — window can be moved, closed and quit normally.",
        )
        session_group.add(self.focus_lock_row)

        self.focus_duration_spin = Adw.SpinRow.new_with_range(1, 300, 1)
        self.focus_duration_spin.set_title("Session Duration (Minutes)")
        self.focus_duration_spin.set_value(25)
        session_group.add(self.focus_duration_spin)

        self.focus_reason_entry = Adw.EntryRow(title="Session Goal / Reason")
        self.focus_reason_entry.set_text("Deep Work Session")
        start_focus_btn = Gtk.Button(label="Start Focus", valign=Gtk.Align.CENTER)
        start_focus_btn.add_css_class("suggested-action")
        start_focus_btn.connect("clicked", self._on_start_focus_clicked)
        self.focus_reason_entry.add_suffix(start_focus_btn)
        session_group.add(self.focus_reason_entry)

        # Challenge unlock group
        challenge_group = Adw.PreferencesGroup(
            title="Early Unlock Math Challenge",
            description="When Focus Mode is active, solving an arithmetic challenge is required to unlock.",
        )
        page.add(challenge_group)

        self.challenge_question_row = Adw.ActionRow(
            title="Active Math Challenge",
            subtitle="No active challenge. Click 'Request Challenge' to generate one.",
        )
        req_ch_btn = Gtk.Button(label="Request Challenge", valign=Gtk.Align.CENTER)
        req_ch_btn.connect("clicked", self._on_request_challenge_clicked)
        self.challenge_question_row.add_suffix(req_ch_btn)
        challenge_group.add(self.challenge_question_row)

        self.challenge_answer_entry = Adw.EntryRow(title="Challenge Answer (Integer)")
        verify_btn = Gtk.Button(label="Verify & Unlock", valign=Gtk.Align.CENTER)
        verify_btn.add_css_class("suggested-action")
        verify_btn.connect("clicked", self._on_verify_challenge_clicked)
        self.challenge_answer_entry.add_suffix(verify_btn)
        challenge_group.add(self.challenge_answer_entry)

        # History group
        self.focus_history_group = Adw.PreferencesGroup(
            title="Recent Focus History",
            description="Completed and interrupted Focus sessions.",
        )
        page.add(self.focus_history_group)
        self._focus_history_rows: List[Gtk.Widget] = []

        return page

    def _on_start_focus_clicked(self, _btn: Gtk.Button) -> None:
        mins = max(1, min(300, int(self.focus_duration_spin.get_value())))
        reason = self.focus_reason_entry.get_text().strip() or "Deep Work"

        def _done(res: ApiResult) -> None:
            if res.ok:
                self.focus_state_row.set_subtitle(f"ACTIVE • {mins} min ({reason})")
                self.focus_dash_row.set_subtitle(f"ACTIVE • {mins} min ({reason})")
                self._set_focus_lock(True)
                self.show_toast(f"Focus Mode enabled for {mins} minutes!")
                # Re-read authoritative state so the page never stays "Inactive".
                self._refresh_focus_status_async()
            else:
                self.show_toast(f"Could not start Focus Mode: {res.error_message}")

        self._run_bg(lambda: self.api_client.enable_focus(mins, reason), _done)

    def _on_request_challenge_clicked(self, _btn: Gtk.Button) -> None:
        def _done(res: ApiResult) -> None:
            if res.ok and isinstance(res.data, dict):
                self._active_challenge_id = str(res.data.get("challenge_id") or "")
                q = str(res.data.get("question") or "")
                exp = res.data.get("expires_in", 120)
                self.challenge_question_row.set_subtitle(f"Question: {q} (Expires in {exp}s)")
                self.show_toast("Math challenge generated.")
            else:
                self.show_toast(f"Challenge request failed: {res.error_message}")

        self._run_bg(lambda: self.api_client.request_challenge(force_new=True), _done)

    def _on_verify_challenge_clicked(self, _btn: Gtk.Button) -> None:
        if not self._active_challenge_id:
            self.show_toast("Request a challenge first.")
            return
        raw_ans = self.challenge_answer_entry.get_text().strip()
        try:
            ans = int(raw_ans)
        except ValueError:
            self.show_toast("Answer must be an integer.")
            return

        cid = self._active_challenge_id

        def _done(res: ApiResult) -> None:
            if res.ok and isinstance(res.data, dict):
                if res.data.get("correct"):
                    self._active_challenge_id = None
                    self.challenge_answer_entry.set_text("")
                    self.challenge_question_row.set_subtitle("Solved! Focus Mode unlocked.")
                    self.focus_state_row.set_subtitle("Inactive — Unlocked via Math Challenge.")
                    self._set_focus_lock(False)
                    self.show_toast("Correct! Focus Mode unlocked.")
                else:
                    new_ch = res.data.get("new_challenge") or {}
                    if isinstance(new_ch, dict) and new_ch.get("challenge_id"):
                        self._active_challenge_id = str(new_ch["challenge_id"])
                        self.challenge_question_row.set_subtitle(
                            f"Incorrect! New Question: {new_ch.get('question')}"
                        )
                    self.show_toast("Incorrect answer. Try the new question.")
            else:
                self.show_toast(f"Verification failed: {res.error_message}")

        self._run_bg(lambda: self.api_client.verify_challenge(cid, ans), _done)

    # =========================================================================
    # Focus Mode hard lock (always on top · no close · no quit)
    # =========================================================================
    def is_focus_locked(self) -> bool:
        return self.focus_lock.locked

    def _initial_focus_lock_check(self) -> bool:
        """Sync the Focus page + window lock with the backend on launch."""
        self._refresh_focus_status_async()
        return False

    def _refresh_focus_status_async(self) -> None:
        def _done(res: ApiResult) -> None:
            if not (res.ok and isinstance(res.data, dict)):
                return
            data = res.data.get("data") if isinstance(res.data.get("data"), dict) else res.data
            active = bool(data.get("focus_active") or data.get("enabled"))
            self._set_focus_lock(active)

            remaining = data.get("remaining_seconds")
            phase = str(data.get("phase") or ("WORK" if active else "IDLE"))
            if getattr(self, "focus_state_row", None) is not None:
                if active:
                    mins = (int(remaining) // 60) if isinstance(remaining, int) else 0
                    self.focus_state_row.set_subtitle(f"ACTIVE • {phase} • ~{mins} min left")
                else:
                    self.focus_state_row.set_subtitle("Inactive — Ready to start.")
            if getattr(self, "focus_dash_row", None) is not None:
                self.focus_dash_row.set_subtitle(f"ACTIVE • {phase}" if active else "Status: Idle")

        self._run_bg(self.api_client.get_focus_status, _done)

    def _on_close_request(self, *_args: Any) -> bool:
        if not self.focus_lock.should_block_close():
            return False
        self.show_toast(
            "Focus Mode đang bật — giải bài toán để mở khoá trước khi đóng ứng dụng."
        )
        self.present()
        return True

    def _set_focus_lock(self, locked: bool) -> None:
        if self.focus_lock.locked == locked:
            # Still re-assert always-on-top: some window managers drop it
            # when the window is re-presented.
            self._apply_always_on_top(locked)
            return

        self.focus_lock.set_locked(locked)
        self._apply_always_on_top(locked)

        if getattr(self, "focus_lock_row", None) is not None:
            if locked:
                self.focus_lock_row.set_subtitle(
                    "ACTIVE — always on top, close and quit are blocked. "
                    "Solve the math challenge to unlock."
                )
            else:
                self.focus_lock_row.set_subtitle(
                    "Inactive — window can be moved, closed and quit normally."
                )

    def _apply_always_on_top(self, enabled: bool) -> None:
        # GTK3 exposed Window.set_keep_above(); GTK4 removed it.
        legacy = getattr(self, "set_keep_above", None)
        if callable(legacy):
            try:
                legacy(enabled)
                return
            except Exception:
                pass

        try:
            self._set_x11_above(enabled)
        except Exception as exc:  # pragma: no cover - depends on the desktop session
            logger.debug("Always-on-top unavailable: %s", exc)

    def _set_x11_above(self, enabled: bool) -> None:
        """Best-effort _NET_WM_STATE_ABOVE for X11 sessions.

        Wayland compositors deliberately forbid apps from forcing themselves
        on top, so on Wayland the close/quit lock still works but the window
        may not float above every other app.
        """
        display = Gdk.Display.get_default()
        if display is None or "X11" not in type(display).__name__:
            return

        surface = self.get_surface()
        if surface is None:
            return

        try:
            gi.require_version("GdkX11", "4.0")
            from gi.repository import GdkX11  # type: ignore
        except (ValueError, ImportError):
            return

        if not isinstance(surface, GdkX11.X11Surface):
            return
        xid = int(surface.get_xid())
        if xid <= 0:
            return

        import ctypes
        import ctypes.util

        lib_name = ctypes.util.find_library("X11") or "libX11.so.6"
        x11 = ctypes.CDLL(lib_name)

        x11.XOpenDisplay.restype = ctypes.c_void_p
        x11.XOpenDisplay.argtypes = [ctypes.c_char_p]
        x11.XInternAtom.restype = ctypes.c_ulong
        x11.XInternAtom.argtypes = [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_int]
        x11.XDefaultRootWindow.restype = ctypes.c_ulong
        x11.XDefaultRootWindow.argtypes = [ctypes.c_void_p]
        x11.XSendEvent.argtypes = [
            ctypes.c_void_p, ctypes.c_ulong, ctypes.c_int, ctypes.c_long, ctypes.c_void_p
        ]
        x11.XFlush.argtypes = [ctypes.c_void_p]
        x11.XCloseDisplay.argtypes = [ctypes.c_void_p]

        dpy = x11.XOpenDisplay(None)
        if not dpy:
            return

        class _XClientMessageEvent(ctypes.Structure):
            _fields_ = [
                ("type", ctypes.c_int),
                ("serial", ctypes.c_ulong),
                ("send_event", ctypes.c_int),
                ("display", ctypes.c_void_p),
                ("window", ctypes.c_ulong),
                ("message_type", ctypes.c_ulong),
                ("format", ctypes.c_int),
                ("data", ctypes.c_long * 5),
            ]

        try:
            wm_state = x11.XInternAtom(dpy, b"_NET_WM_STATE", False)
            above = x11.XInternAtom(dpy, b"_NET_WM_STATE_ABOVE", False)
            root = x11.XDefaultRootWindow(dpy)

            event = _XClientMessageEvent()
            event.type = 33  # ClientMessage
            event.window = xid
            event.message_type = wm_state
            event.format = 32
            event.data[0] = 1 if enabled else 0  # _NET_WM_STATE_ADD / REMOVE
            event.data[1] = above
            event.data[3] = 1  # source indication: normal application

            mask = (1 << 20) | (1 << 19)  # SubstructureRedirect | SubstructureNotify
            x11.XSendEvent(dpy, root, 0, mask, ctypes.byref(event))
            x11.XFlush(dpy)
        finally:
            x11.XCloseDisplay(dpy)

    # =========================================================================
    # Page: Alarms (Backend-Authoritative)
    # =========================================================================
    def _build_alarms_page(self) -> Gtk.Widget:
        page = Adw.PreferencesPage()

        self.alarms_list_group = Adw.PreferencesGroup(
            title="Backend-Authoritative Alarms",
            description="Persistent alarms stored on server and synchronized across all ecosystem devices.",
        )
        page.add(self.alarms_list_group)

        self.alarms_list_box = Gtk.ListBox()
        self.alarms_list_box.set_selection_mode(Gtk.SelectionMode.NONE)
        self.alarms_list_box.add_css_class("boxed-list")
        self.alarms_list_group.add(self.alarms_list_box)

        # Create Alarm Group
        create_group = Adw.PreferencesGroup(
            title="Create New Alarm",
            description="Schedule an alarm with recurring days and acoustic duration.",
        )
        page.add(create_group)

        self.alarm_time_entry = Adw.EntryRow(title="Alarm Time (24h HH:MM)")
        self.alarm_time_entry.set_text("07:00")
        create_group.add(self.alarm_time_entry)

        self.alarm_label_entry = Adw.EntryRow(title="Label")
        self.alarm_label_entry.set_text("Morning Alarm")
        create_group.add(self.alarm_label_entry)

        # Repeat days row
        days_row = Adw.ActionRow(
            title="Repeat Schedule",
            subtitle="Select days to repeat (uncheck all for one-time)",
        )
        self.repeat_cbs: List[Gtk.CheckButton] = []
        days_box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        day_names = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
        for i, dname in enumerate(day_names):
            cb = Gtk.CheckButton(label=dname)
            cb.set_active(i < 5)
            self.repeat_cbs.append(cb)
            days_box.append(cb)
        days_row.add_suffix(days_box)
        create_group.add(days_row)

        self.alarm_dur_spin = Adw.SpinRow.new_with_range(50, 1000, 50)
        self.alarm_dur_spin.set_title("Buzzer Duration (ms)")
        self.alarm_dur_spin.set_value(250)
        create_group.add(self.alarm_dur_spin)

        save_btn = Gtk.Button(label="Save Alarm", valign=Gtk.Align.CENTER)
        save_btn.add_css_class("suggested-action")
        save_btn.connect("clicked", self._on_save_alarm_clicked)
        self.alarm_dur_spin.add_suffix(save_btn)

        return page

    def _render_alarms_list(self, alarms: List[Dict[str, Any]]) -> None:
        child = self.alarms_list_box.get_first_child()
        while child is not None:
            nxt = child.get_next_sibling()
            self.alarms_list_box.remove(child)
            child = nxt

        if not alarms:
            empty_row = Adw.ActionRow(
                title="No Alarms Configured",
                subtitle="Use the form below to create a persistent alarm.",
            )
            self.alarms_list_box.append(empty_row)
            return

        day_labels = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
        for a in alarms:
            aid = str(a.get("id", ""))
            atime = str(a.get("time", "07:00"))
            alabel = str(a.get("label", "Alarm"))
            aenabled = bool(a.get("enabled", True))
            rep_days = a.get("repeat_days", [])
            if rep_days:
                rep_str = ", ".join(day_labels[d] for d in rep_days if 0 <= d < 7)
            else:
                rep_str = "Once"

            row = Adw.ActionRow(
                title=f"{atime} — {alabel}",
                subtitle=f"Repeat: {rep_str} • Buzzer: {a.get('buzzer_duration', 250)}ms",
            )

            sw = Gtk.Switch(valign=Gtk.Align.CENTER)
            sw.set_active(aenabled)
            sw.connect("notify::active", lambda s, _p, alarm_id=aid: self._on_toggle_alarm(alarm_id, s.get_active()))
            row.add_suffix(sw)

            del_btn = Gtk.Button(icon_name="user-trash-symbolic", valign=Gtk.Align.CENTER)
            del_btn.add_css_class("destructive-action")
            del_btn.connect("clicked", lambda _b, alarm_id=aid: self._on_delete_alarm(alarm_id))
            row.add_suffix(del_btn)

            self.alarms_list_box.append(row)

    def _on_save_alarm_clicked(self, _btn: Gtk.Button) -> None:
        time_str = self.alarm_time_entry.get_text().strip()
        label = self.alarm_label_entry.get_text().strip() or "Alarm"
        dur = int(self.alarm_dur_spin.get_value())
        days = [i for i, cb in enumerate(self.repeat_cbs) if cb.get_active()]

        def _worker() -> ApiResult:
            return self.api_client.save_alarm(time_str=time_str, label=label, repeat_days=days, buzzer_duration=dur)

        def _done(res: ApiResult) -> None:
            if res.ok:
                self.show_toast(f"Alarm {time_str} saved!")
                self.refresh_alarms_async()
            else:
                self.show_toast(f"Failed to save alarm: {res.error_message}")

        self._run_bg(_worker, _done)

    def _on_toggle_alarm(self, alarm_id: str, active: bool) -> None:
        self._run_bg(
            lambda: self.api_client.toggle_alarm(alarm_id, active),
            lambda r: self.show_toast("Alarm updated!" if r.ok else r.error_message),
        )

    def _on_delete_alarm(self, alarm_id: str) -> None:
        def _done(res: ApiResult) -> None:
            if res.ok:
                self.show_toast("Alarm deleted.")
                self.refresh_alarms_async()
            else:
                self.show_toast(f"Delete failed: {res.error_message}")

        self._run_bg(lambda: self.api_client.delete_alarm(alarm_id), _done)

    def refresh_alarms_async(self) -> None:
        def _done(res: ApiResult) -> None:
            if res.ok and isinstance(res.data, dict):
                alarms = res.data.get("alarms", [])
                self._render_alarms_list(alarms)

        self._run_bg(self.api_client.get_alarms, _done)

    # =========================================================================
    # Page: Thời khóa biểu (TKB / Timetable)
    # =========================================================================
    def _build_tkb_page(self) -> Gtk.Widget:
        page = Adw.PreferencesPage()

        header_group = Adw.PreferencesGroup(
            title="Thời khóa biểu (TKB) & Lịch học",
            description="Lịch học các tiết Sáng, Chiều và quy định Đồng phục đồng bộ với vi điều khiển ESP32.",
        )
        page.add(header_group)

        action_row = Adw.ActionRow(
            title="Thông tin tuần học",
            subtitle="Đang tải dữ liệu thời khóa biểu...",
        )
        self.tkb_action_row = action_row

        self.tkb_week_badge = Gtk.Label(label="Tuần: --")
        self.tkb_week_badge.add_css_class("deskflow-badge-pinned")
        self.tkb_week_badge.set_valign(Gtk.Align.CENTER)
        action_row.add_suffix(self.tkb_week_badge)

        btn_sync_esp = Gtk.Button(label="Hiển thị TKB lên màn hình ESP32", valign=Gtk.Align.CENTER)
        btn_sync_esp.add_css_class("suggested-action")
        btn_sync_esp.connect("clicked", lambda *_: self._sync_tkb_to_esp32())
        action_row.add_suffix(btn_sync_esp)

        btn_refresh = Gtk.Button(icon_name="view-refresh-symbolic", valign=Gtk.Align.CENTER)
        btn_refresh.set_tooltip_text("Làm mới thời khóa biểu")
        btn_refresh.connect("clicked", lambda *_: self.refresh_tkb_async())
        action_row.add_suffix(btn_refresh)

        header_group.add(action_row)

        # Day cards, laid out like the macOS timetable view: one card per day,
        # uniform badges on top, then morning / afternoon lesson columns.
        self.tkb_today_card = self._build_tkb_day_card(is_today=True)
        page.add(self.tkb_today_card["container"])

        self.tkb_tomorrow_card = self._build_tkb_day_card(is_today=False)
        page.add(self.tkb_tomorrow_card["container"])

        return page

    # =========================================================================
    # Page: Lịch Google (Google Calendar)
    # =========================================================================
    def _build_calendar_page(self) -> Gtk.Widget:
        page = Adw.PreferencesPage()

        # 1. Status & Quick Actions
        header_group = Adw.PreferencesGroup(
            title=self._tr("Lịch Google (Google Calendar)", "Google Calendar"),
            description=self._tr(
                "Kết nối qua Google OAuth 2.0 (2 chiều) hoặc link Private iCal (.ics) để đồng bộ lịch lên trạm ESP32.",
                "Connect via Google OAuth 2.0 (2-way sync) or Private iCal (.ics) URL and push events to your ESP32 station.",
            ),
        )
        page.add(header_group)

        self.cal_status_row = Adw.ActionRow(
            title=self._tr("Trạng thái kết nối", "Connection Status"),
            subtitle=self._tr("Chưa kết nối Google Calendar", "Not connected to Google Calendar"),
        )
        self.cal_status_badge = Gtk.Label(label="--")
        self.cal_status_badge.add_css_class("deskflow-badge-offline")
        self.cal_status_badge.set_valign(Gtk.Align.CENTER)
        self.cal_status_row.add_suffix(self.cal_status_badge)

        btn_esp = Gtk.Button(label=self._tr("Hiện lên ESP32", "Show on ESP32"), valign=Gtk.Align.CENTER)
        btn_esp.add_css_class("suggested-action")
        btn_esp.connect("clicked", lambda *_: self._run_bg(
            lambda: self.api_client.switch_display_view("calendar"),
            lambda r: self.show_toast("ESP32 -> Google Calendar" if r.ok else r.error_message),
        ))
        self.cal_status_row.add_suffix(btn_esp)

        btn_sync = Gtk.Button(icon_name="view-refresh-symbolic", valign=Gtk.Align.CENTER)
        btn_sync.set_tooltip_text(self._tr("Đồng bộ ngay", "Sync now"))
        btn_sync.connect("clicked", lambda *_: self._on_calendar_sync_clicked())
        self.cal_status_row.add_suffix(btn_sync)
        header_group.add(self.cal_status_row)

        # 2. Google OAuth 2.0 (2-way)
        oauth_group = Adw.PreferencesGroup(
            title=self._tr("Kết nối Google OAuth 2.0 (Đọc + Thêm/Xóa)", "Google OAuth 2.0 (Read & Write)"),
            description=self._tr(
                "Dùng Google Cloud OAuth Client để đăng nhập 1 chạm, chọn lịch và tạo/xóa sự kiện.",
                "Use Google Cloud OAuth Client for 1-click sign-in, calendar selection, and creating/deleting events.",
            ),
        )
        page.add(oauth_group)

        self.cal_oauth_row = Adw.ActionRow(
            title=self._tr("Tài khoản Google", "Google Account"),
            subtitle=self._tr("Chưa đăng nhập OAuth", "Not signed in"),
        )
        self.cal_oauth_connect_btn = Gtk.Button(label=self._tr("Đăng nhập Google", "Connect Google"), valign=Gtk.Align.CENTER)
        self.cal_oauth_connect_btn.add_css_class("suggested-action")
        self.cal_oauth_connect_btn.connect("clicked", lambda *_: self._on_calendar_oauth_start())
        self.cal_oauth_row.add_suffix(self.cal_oauth_connect_btn)

        self.cal_oauth_disconnect_btn = Gtk.Button(label=self._tr("Ngắt OAuth", "Disconnect"), valign=Gtk.Align.CENTER)
        self.cal_oauth_disconnect_btn.add_css_class("destructive-action")
        self.cal_oauth_disconnect_btn.set_visible(False)
        self.cal_oauth_disconnect_btn.connect("clicked", lambda *_: self._on_calendar_oauth_disconnect())
        self.cal_oauth_row.add_suffix(self.cal_oauth_disconnect_btn)
        oauth_group.add(self.cal_oauth_row)

        self.cal_picker_model = Gtk.StringList()
        self.cal_picker_ids: List[str] = []
        self.cal_picker_row = Adw.ComboRow(
            title=self._tr("Lịch đang chọn", "Selected Calendar"),
            subtitle=self._tr("Primary", "Primary"),
            model=self.cal_picker_model,
        )
        self.cal_picker_row.set_visible(False)
        self._suppress_cal_picker = False
        self.cal_picker_row.connect("notify::selected", self._on_calendar_picker_changed)
        oauth_group.add(self.cal_picker_row)

        cred_expander = Adw.ExpanderRow(
            title=self._tr("Cấu hình Google OAuth Credentials", "Google OAuth Credentials Setup"),
            subtitle=self._tr(
                "Redirect URI: https://df.api.thetechdude.io.vn/api/app/calendar/oauth/callback",
                "Redirect URI: https://df.api.thetechdude.io.vn/api/app/calendar/oauth/callback",
            ),
        )
        self.cal_client_id_entry = Adw.EntryRow(title="Google Client ID")
        self.cal_client_secret_entry = Adw.PasswordEntryRow(title="Google Client Secret (để trống nếu giữ nguyên)")
        self.cal_redirect_uri_entry = Adw.EntryRow(title="Redirect URI")
        self.cal_redirect_uri_entry.set_text("https://df.api.thetechdude.io.vn/api/app/calendar/oauth/callback")
        cred_save_row = Adw.ActionRow(
            title=self._tr("Lưu thông tin OAuth", "Save OAuth Credentials"),
        )
        save_oauth_btn = Gtk.Button(label=self._tr("Lưu", "Save"), valign=Gtk.Align.CENTER)
        save_oauth_btn.add_css_class("suggested-action")
        save_oauth_btn.connect("clicked", lambda *_: self._on_save_calendar_oauth_creds())
        cred_save_row.add_suffix(save_oauth_btn)
        cred_expander.add_row(self.cal_client_id_entry)
        cred_expander.add_row(self.cal_client_secret_entry)
        cred_expander.add_row(self.cal_redirect_uri_entry)
        cred_expander.add_row(cred_save_row)
        oauth_group.add(cred_expander)

        # 3. Private iCal (.ics) Quick Link
        ical_group = Adw.PreferencesGroup(
            title=self._tr("Kết nối nhanh qua Private iCal (.ics)", "Quick Link via Private iCal (.ics)"),
            description=self._tr(
                "Dán link 'Địa chỉ bí mật ở định dạng iCal' trong Google Calendar Settings (chỉ xem, không cần OAuth).",
                "Paste 'Secret address in iCal format' from Google Calendar Settings (read-only, zero OAuth needed).",
            ),
        )
        page.add(ical_group)

        self.cal_ical_entry = Adw.EntryRow(title="Secret iCal (.ics) URL")
        save_ical_btn = Gtk.Button(label=self._tr("Lưu & Đồng bộ", "Save & Sync"), valign=Gtk.Align.CENTER)
        save_ical_btn.add_css_class("suggested-action")
        save_ical_btn.connect("clicked", lambda *_: self._on_save_calendar_ical())
        self.cal_ical_entry.add_suffix(save_ical_btn)
        ical_group.add(self.cal_ical_entry)

        # 4. Create Event (requires OAuth)
        create_group = Adw.PreferencesGroup(
            title=self._tr("Thêm sự kiện lên Google Calendar", "Create Google Calendar Event"),
            description=self._tr("Khả dụng khi đã đăng nhập Google OAuth.", "Available when Google OAuth is connected."),
        )
        page.add(create_group)
        self.cal_new_title = Adw.EntryRow(title=self._tr("Tên sự kiện", "Event Title"))
        self.cal_new_date = Adw.EntryRow(title=self._tr("Ngày (YYYY-MM-DD)", "Date (YYYY-MM-DD)"))
        self.cal_new_date.set_text(datetime.now().strftime("%Y-%m-%d"))
        self.cal_new_start = Adw.EntryRow(title=self._tr("Giờ bắt đầu (HH:MM)", "Start Time (HH:MM)"))
        self.cal_new_start.set_text("09:00")
        self.cal_new_end = Adw.EntryRow(title=self._tr("Giờ kết thúc (HH:MM)", "End Time (HH:MM)"))
        self.cal_new_end.set_text("10:00")
        self.cal_new_location = Adw.EntryRow(title=self._tr("Địa điểm (tuỳ chọn)", "Location (optional)"))
        self.cal_new_allday = Adw.SwitchRow(title=self._tr("Sự kiện cả ngày", "All-day event"))

        create_btn_row = Adw.ActionRow(title=self._tr("Tạo trên Google Calendar", "Create Event"))
        create_btn = Gtk.Button(label=self._tr("Thêm sự kiện", "Add Event"), valign=Gtk.Align.CENTER)
        create_btn.add_css_class("suggested-action")
        create_btn.connect("clicked", lambda *_: self._on_create_calendar_event())
        create_btn_row.add_suffix(create_btn)

        create_group.add(self.cal_new_title)
        create_group.add(self.cal_new_date)
        create_group.add(self.cal_new_start)
        create_group.add(self.cal_new_end)
        create_group.add(self.cal_new_location)
        create_group.add(self.cal_new_allday)
        create_group.add(create_btn_row)

        # 4b. Interactive Month Grid Calendar
        today_dt = datetime.now().date()
        self._cal_view_year = today_dt.year
        self._cal_view_month = today_dt.month
        self._cal_selected_date = today_dt.strftime("%Y-%m-%d")

        month_group = Adw.PreferencesGroup(
            title=self._tr("Lịch theo tháng (Month Grid)", "Monthly Calendar Grid"),
            description=self._tr("Bấm vào một ngày trên lưới để xem các việc và giờ cụ thể.", "Click any day on the grid to inspect its events & hours."),
        )
        page.add(month_group)

        month_nav_row = Adw.ActionRow(title="--/--")
        self.cal_month_nav_row = month_nav_row
        prev_m_btn = Gtk.Button(icon_name="go-previous-symbolic", valign=Gtk.Align.CENTER)
        prev_m_btn.connect("clicked", lambda *_: self._shift_calendar_month(-1))
        today_m_btn = Gtk.Button(label=self._tr("Hôm nay", "Today"), valign=Gtk.Align.CENTER)
        today_m_btn.connect("clicked", lambda *_: self._jump_calendar_today())
        next_m_btn = Gtk.Button(icon_name="go-next-symbolic", valign=Gtk.Align.CENTER)
        next_m_btn.connect("clicked", lambda *_: self._shift_calendar_month(1))
        month_nav_row.add_suffix(prev_m_btn)
        month_nav_row.add_suffix(today_m_btn)
        month_nav_row.add_suffix(next_m_btn)
        month_group.add(month_nav_row)

        self.cal_month_grid = Gtk.Grid(column_homogeneous=True, row_spacing=4, column_spacing=4)
        self.cal_month_grid.set_margin_top(8)
        self.cal_month_grid.set_margin_bottom(8)
        self.cal_month_grid.set_margin_start(4)
        self.cal_month_grid.set_margin_end(4)
        month_group.add(self.cal_month_grid)

        self.cal_selected_day_group = Adw.PreferencesGroup(
            title=self._tr("Việc trong ngày đã chọn", "Selected Day Events"),
        )
        page.add(self.cal_selected_day_group)
        self._cal_selected_rows: List[Gtk.Widget] = []

        # 5. Day-by-Day Upcoming Events Agenda
        self.cal_events_group = Adw.PreferencesGroup(
            title=self._tr("Lịch trình từng ngày (30 ngày)", "Daily Agenda (30 days)"),
            description=self._tr("Hiển thị từng ngày bạn có những sự kiện gì • tự đồng bộ lên ESP32.", "Shows what you have scheduled each day • auto-syncs to ESP32."),
        )
        page.add(self.cal_events_group)
        self._cal_event_rows: List[Gtk.Widget] = []
        self._apply_calendar_ui(self.api_client.calendar_status)

        return page

    def _shift_calendar_month(self, delta: int) -> None:
        m = self._cal_view_month + delta
        y = self._cal_view_year
        if m < 1:
            m = 12
            y -= 1
        elif m > 12:
            m = 1
            y += 1
        self._cal_view_year = y
        self._cal_view_month = m
        self._render_calendar_month_grid(self.api_client.calendar_status)

    def _jump_calendar_today(self) -> None:
        t = datetime.now().date()
        self._cal_view_year = t.year
        self._cal_view_month = t.month
        self._cal_selected_date = t.strftime("%Y-%m-%d")
        self._render_calendar_month_grid(self.api_client.calendar_status)

    def _select_calendar_day(self, date_str: str) -> None:
        self._cal_selected_date = date_str
        self._render_calendar_month_grid(self.api_client.calendar_status)

    def _render_calendar_month_grid(self, st: CalendarStatus) -> None:
        if not hasattr(self, "cal_month_grid"):
            return
        while (child := self.cal_month_grid.get_first_child()) is not None:
            self.cal_month_grid.remove(child)

        y, m = self._cal_view_year, self._cal_view_month
        self.cal_month_nav_row.set_title(self._tr(f"Tháng {m:02d}/{y}", f"{py_calendar.month_name[m]} {y}"))

        by_date: Dict[str, List[CalendarEventItem]] = {}
        for ev in st.events:
            by_date.setdefault(ev.date, []).append(ev)

        today_str = datetime.now().date().strftime("%Y-%m-%d")
        wd_headers = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"] if self.config_store.config.language == "vi" else ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
        for c, w_txt in enumerate(wd_headers):
            lbl = Gtk.Label(label=w_txt)
            lbl.add_css_class("heading")
            self.cal_month_grid.attach(lbl, c, 0, 1, 1)

        # Sunday-first week index (0=Sun..6=Sat)
        mon_first_wd, dim = py_calendar.monthrange(y, m)
        sun_first_wd = (mon_first_wd + 1) % 7
        for day_num in range(1, dim + 1):
            slot = sun_first_wd + (day_num - 1)
            col = slot % 7
            row = 1 + (slot // 7)
            d_str = f"{y:04d}-{m:02d}-{day_num:02d}"
            items = by_date.get(d_str, [])
            label_txt = f"{day_num}\n•{len(items)}" if items else f"{day_num}"
            btn = Gtk.Button(label=label_txt)
            if d_str == self._cal_selected_date:
                btn.add_css_class("suggested-action")
            elif d_str == today_str:
                btn.add_css_class("accent")
            btn.connect("clicked", lambda _b, ds=d_str: self._select_calendar_day(ds))
            self.cal_month_grid.attach(btn, col, row, 1, 1)

        # Render Selected Day Details
        for r in self._cal_selected_rows:
            self.cal_selected_day_group.remove(r)
        self._cal_selected_rows.clear()
        sel_items = by_date.get(self._cal_selected_date, [])
        self.cal_selected_day_group.set_title(
            self._tr(f"Ngày {self._cal_selected_date} ({len(sel_items)} việc)", f"Date {self._cal_selected_date} ({len(sel_items)} events)")
        )
        if not sel_items:
            r0 = Adw.ActionRow(title=self._tr("Trống lịch — không có việc nào", "Free day — nothing scheduled"))
            self.cal_selected_day_group.add(r0)
            self._cal_selected_rows.append(r0)
        else:
            for ev in sel_items:
                t_str = self._tr("Cả ngày", "All day") if ev.all_day else (f"{ev.start_time}–{ev.end_time}" if ev.end_time else ev.start_time)
                sub = t_str + (f" • @ {ev.location}" if ev.location else "")
                r = Adw.ActionRow(title=ev.title, subtitle=sub)
                if ev.can_delete:
                    del_btn = Gtk.Button(icon_name="user-trash-symbolic", valign=Gtk.Align.CENTER)
                    del_btn.add_css_class("destructive-action")
                    del_btn.connect("clicked", lambda _b, item=ev: self._on_delete_calendar_event(item))
                    r.add_suffix(del_btn)
                self.cal_selected_day_group.add(r)
                self._cal_selected_rows.append(r)

    def refresh_calendar_async(self) -> None:
        def _done(res: ApiResult) -> None:
            if res.ok:
                self._apply_calendar_ui(self.api_client.calendar_status)
        self._run_bg(self.api_client.get_calendar_status, _done)

    def _on_calendar_sync_clicked(self) -> None:
        def _done(res: ApiResult) -> None:
            if res.ok:
                self._apply_calendar_ui(self.api_client.calendar_status)
                self.show_toast(self._tr("Đã đồng bộ Google Calendar!", "Google Calendar synced!"))
            else:
                self.show_toast(res.error_message)
        self._run_bg(self.api_client.sync_calendar, _done)

    def _on_save_calendar_ical(self) -> None:
        url = self.cal_ical_entry.get_text().strip()
        def _done(res: ApiResult) -> None:
            if res.ok:
                self._apply_calendar_ui(self.api_client.calendar_status)
                self.show_toast(self._tr("Đã lưu & đồng bộ iCal!", "Saved & synced iCal URL!"))
            else:
                self.show_toast(res.error_message)
        self._run_bg(lambda: self.api_client.update_calendar_config(ical_url=url), _done)

    def _on_save_calendar_oauth_creds(self) -> None:
        cid = self.cal_client_id_entry.get_text().strip()
        csec = self.cal_client_secret_entry.get_text().strip()
        ruri = self.cal_redirect_uri_entry.get_text().strip()
        kwargs: Dict[str, Any] = {"google_client_id": cid, "google_redirect_uri": ruri}
        if csec:
            kwargs["google_client_secret"] = csec
        def _done(res: ApiResult) -> None:
            if res.ok:
                self.cal_client_secret_entry.set_text("")
                self._apply_calendar_ui(self.api_client.calendar_status)
                self.show_toast(self._tr("Đã lưu OAuth Credentials!", "OAuth credentials saved!"))
            else:
                self.show_toast(res.error_message)
        self._run_bg(lambda: self.api_client.update_calendar_config(**kwargs), _done)

    def _on_calendar_oauth_start(self) -> None:
        def _done(res: ApiResult) -> None:
            if res.ok and isinstance(res.data, dict) and res.data.get("auth_url"):
                try:
                    Gio.AppInfo.launch_default_for_uri(res.data["auth_url"], None)
                    self.show_toast(self._tr(
                        "Đã mở trình duyệt đăng nhập Google. Xong hãy bấm Làm mới!",
                        "Opened Google sign-in in browser. Click Sync after completing!",
                    ))
                except Exception as exc:
                    self.show_toast(f"Open browser failed: {exc}")
            else:
                msg = res.error_message
                if "google_oauth_credentials_missing" in msg:
                    msg = self._tr(
                        "Hãy nhập Google Client ID & Client Secret ở mục Cấu hình OAuth bên dưới trước!",
                        "Please enter Google Client ID & Secret in OAuth Credentials below first!",
                    )
                self.show_toast(msg)
        self._run_bg(self.api_client.start_calendar_oauth, _done)

    def _on_calendar_oauth_disconnect(self) -> None:
        def _done(res: ApiResult) -> None:
            if res.ok:
                self._apply_calendar_ui(self.api_client.calendar_status)
                self.show_toast(self._tr("Đã ngắt Google OAuth.", "Disconnected Google OAuth."))
            else:
                self.show_toast(res.error_message)
        self._run_bg(self.api_client.disconnect_calendar_oauth, _done)

    def _on_calendar_picker_changed(self, combo: Adw.ComboRow, _pspec: Any) -> None:
        if self._suppress_cal_picker:
            return
        idx = combo.get_selected()
        if idx < 0 or idx >= len(self.cal_picker_ids):
            return
        cal_id = self.cal_picker_ids[idx]
        def _done(res: ApiResult) -> None:
            if res.ok:
                self._apply_calendar_ui(self.api_client.calendar_status)
        self._run_bg(lambda: self.api_client.update_calendar_config(selected_calendar_id=cal_id), _done)

    def _on_create_calendar_event(self) -> None:
        title = self.cal_new_title.get_text().strip()
        d_str = self.cal_new_date.get_text().strip()
        st = self.cal_new_start.get_text().strip()
        et = self.cal_new_end.get_text().strip()
        loc = self.cal_new_location.get_text().strip()
        all_day = self.cal_new_allday.get_active()
        if not title or not d_str:
            self.show_toast(self._tr("Nhập tên sự kiện và ngày (YYYY-MM-DD).", "Enter title and date (YYYY-MM-DD)."))
            return
        def _done(res: ApiResult) -> None:
            if res.ok:
                self.cal_new_title.set_text("")
                self.cal_new_location.set_text("")
                self._apply_calendar_ui(self.api_client.calendar_status)
                self.show_toast(self._tr("Đã thêm vào Google Calendar!", "Event created on Google Calendar!"))
            else:
                self.show_toast(res.error_message)
        self._run_bg(
            lambda: self.api_client.create_calendar_event(title, d_str, st, et, all_day, loc),
            _done,
        )

    def _on_delete_calendar_event(self, ev: CalendarEventItem) -> None:
        def _done(res: ApiResult) -> None:
            if res.ok:
                self._apply_calendar_ui(self.api_client.calendar_status)
                self.show_toast(self._tr("Đã xóa sự kiện.", "Event deleted."))
            else:
                self.show_toast(res.error_message)
        self._run_bg(lambda: self.api_client.delete_calendar_event(ev.id, ev.calendar_id), _done)

    def _apply_calendar_ui(self, st: CalendarStatus) -> None:
        if not hasattr(self, "cal_status_row"):
            return
        if st.connected:
            self.cal_status_badge.set_text(f"{st.today_count} hôm nay / {len(st.events)} sắp tới")
            self.cal_status_badge.remove_css_class("deskflow-badge-offline")
            self.cal_status_badge.add_css_class("deskflow-badge-online")
            modes = []
            if st.oauth_connected:
                modes.append(f"OAuth ({st.connected_email or 'Google'})")
            if st.ical_connected:
                modes.append("Private iCal")
            sub = " • ".join(modes)
            if st.last_error:
                sub += f" • ⚠ {st.last_error}"
            self.cal_status_row.set_subtitle(sub)
        else:
            self.cal_status_badge.set_text("OFFLINE")
            self.cal_status_badge.remove_css_class("deskflow-badge-online")
            self.cal_status_badge.add_css_class("deskflow-badge-offline")
            self.cal_status_row.set_subtitle(
                self._tr("Chưa kết nối OAuth hoặc link iCal (.ics)", "Connect Google OAuth or Private iCal (.ics) below")
            )

        self.cal_oauth_disconnect_btn.set_visible(st.oauth_connected)
        self.cal_oauth_row.set_subtitle(
            f"{st.connected_email} ({st.selected_calendar_name})" if st.oauth_connected
            else self._tr("Chưa đăng nhập Google OAuth", "Not signed in with Google OAuth")
        )
        if st.google_client_id and not self.cal_client_id_entry.get_text():
            self.cal_client_id_entry.set_text(st.google_client_id)
        if st.google_redirect_uri:
            self.cal_redirect_uri_entry.set_text(st.google_redirect_uri)
        if st.ical_url and not self.cal_ical_entry.get_text():
            self.cal_ical_entry.set_text(st.ical_url)

        # Populate calendars dropdown cleanly
        if st.oauth_connected and st.calendars:
            self._suppress_cal_picker = True
            while self.cal_picker_model.get_n_items() > 0:
                self.cal_picker_model.remove(0)
            self.cal_picker_ids = []
            sel_idx = 0
            for idx, c in enumerate(st.calendars):
                self.cal_picker_model.append(str(c.get("summary") or c.get("id") or "Calendar"))
                self.cal_picker_ids.append(str(c.get("id") or "primary"))
                if c.get("id") == st.selected_calendar_id:
                    sel_idx = idx
            self.cal_picker_row.set_selected(sel_idx)
            self.cal_picker_row.set_visible(True)
            self._suppress_cal_picker = False
        else:
            self.cal_picker_row.set_visible(False)

        self._render_calendar_month_grid(st)

        # Day-by-Day Agenda grouping
        for row in self._cal_event_rows:
            self.cal_events_group.remove(row)
        self._cal_event_rows.clear()

        today_d = datetime.now().date()
        tomorrow_d = today_d + timedelta(days=1)
        vi_wd = ["Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7", "Chủ nhật"]
        en_wd = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]

        by_date: Dict[str, List[CalendarEventItem]] = {}
        today_iso = today_d.strftime("%Y-%m-%d")
        by_date[today_iso] = []
        for ev in st.events:
            d_key = ev.date or today_iso
            by_date.setdefault(d_key, []).append(ev)

        def _format_day_header(d_str: str, count: int) -> str:
            try:
                d_obj = datetime.strptime(d_str, "%Y-%m-%d").date()
                wd_lbl = self._tr(vi_wd[d_obj.weekday()], en_wd[d_obj.weekday()])
                dm = d_obj.strftime("%d/%m/%Y")
                if d_obj == today_d:
                    prefix = self._tr("Hôm nay", "Today")
                elif d_obj == tomorrow_d:
                    prefix = self._tr("Ngày mai", "Tomorrow")
                else:
                    prefix = wd_lbl
                return f"{prefix} • {wd_lbl}, {dm} ({count})" if d_obj in (today_d, tomorrow_d) else f"{wd_lbl}, {dm} ({count})"
            except Exception:
                return f"{d_str} ({count})"

        for d_key in sorted(by_date.keys()):
            day_items = by_date[d_key]
            day_exp = Adw.ExpanderRow(
                title=_format_day_header(d_key, len(day_items)),
                subtitle=self._tr("Không có sự kiện nào", "Nothing scheduled") if not day_items else ", ".join(e.title for e in day_items[:3]),
            )
            day_exp.set_expanded(d_key == today_iso or len(day_items) > 0)
            if d_key == today_iso:
                badge = Gtk.Label(label=self._tr("HÔM NAY", "TODAY"))
                badge.add_css_class("deskflow-badge-online")
                badge.set_valign(Gtk.Align.CENTER)
                day_exp.add_suffix(badge)

            if not day_items:
                empty_sub = Adw.ActionRow(
                    title=self._tr("Hôm nay trống lịch", "Free day — no events"),
                )
                day_exp.add_row(empty_sub)
            else:
                for ev in day_items:
                    t_str = self._tr("Cả ngày", "All day") if ev.all_day else (f"{ev.start_time}–{ev.end_time}" if ev.end_time else ev.start_time)
                    sub = t_str + (f" • @ {ev.location}" if ev.location else "")
                    row = Adw.ActionRow(title=ev.title, subtitle=sub)
                    if ev.is_ongoing:
                        on_lbl = Gtk.Label(label=self._tr("ĐANG DIỄN RA", "ONGOING"))
                        on_lbl.add_css_class("deskflow-badge-online")
                        on_lbl.set_valign(Gtk.Align.CENTER)
                        row.add_suffix(on_lbl)
                    if ev.can_delete:
                        del_btn = Gtk.Button(icon_name="user-trash-symbolic", valign=Gtk.Align.CENTER)
                        del_btn.add_css_class("destructive-action")
                        del_btn.connect("clicked", lambda _b, item=ev: self._on_delete_calendar_event(item))
                        row.add_suffix(del_btn)
                    day_exp.add_row(row)

            self.cal_events_group.add(day_exp)
            self._cal_event_rows.append(day_exp)

    def _build_tkb_day_card(self, is_today: bool) -> Dict[str, Any]:
        accent = "#38BDF8" if is_today else "#A78BFA"
        container = Adw.PreferencesGroup(
            title=("Lịch học hôm nay" if is_today else "Lịch học kế tiếp"),
            description=("Đồng phục và tiết học của ngày hiện tại." if is_today
                         else "Ngày học gần nhất tiếp theo có tiết."),
        )

        card = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=10)
        card.add_css_class("deskflow-card")
        card.set_margin_top(4)
        card.set_margin_bottom(4)

        header = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        title_lbl = Gtk.Label(xalign=0.0)
        title_lbl.set_markup(f'<span weight="bold" foreground="{accent}">{("HÔM NAY" if is_today else "NGÀY KẾ TIẾP")}</span>')
        header.append(title_lbl)
        day_lbl = Gtk.Label(label="")
        day_lbl.add_css_class("caption")
        day_lbl.set_hexpand(True)
        day_lbl.set_xalign(0.0)
        header.append(day_lbl)

        today_badge = Gtk.Label(label="HÔM NAY")
        today_badge.add_css_class("deskflow-badge-pinned")
        today_badge.set_visible(is_today)
        header.append(today_badge)
        card.append(header)

        uniform_row = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        morning_uniform = Gtk.Label(xalign=0.0)
        morning_uniform.add_css_class("deskflow-badge-pinned")
        afternoon_uniform = Gtk.Label(xalign=0.0)
        afternoon_uniform.add_css_class("deskflow-badge-online")
        afternoon_uniform.set_visible(False)
        uniform_row.append(morning_uniform)
        uniform_row.append(afternoon_uniform)
        card.append(uniform_row)

        columns = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=14)
        columns.set_homogeneous(True)

        morning_col = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        morning_title = Gtk.Label(label="Tiết học sáng", xalign=0.0)
        morning_title.add_css_class("heading")
        morning_body = Gtk.Label(label="Đang cập nhật...", xalign=0.0)
        morning_body.set_wrap(True)
        morning_body.add_css_class("caption")
        morning_col.append(morning_title)
        morning_col.append(morning_body)

        afternoon_col = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        afternoon_title = Gtk.Label(label="Tiết học chiều", xalign=0.0)
        afternoon_title.add_css_class("heading")
        afternoon_body = Gtk.Label(label="Đang cập nhật...", xalign=0.0)
        afternoon_body.set_wrap(True)
        afternoon_body.add_css_class("caption")
        afternoon_col.append(afternoon_title)
        afternoon_col.append(afternoon_body)

        columns.append(morning_col)
        columns.append(afternoon_col)
        card.append(columns)

        container.add(card)
        return {
            "container": container,
            "header_title": title_lbl,
            "day": day_lbl,
            "uniform_morning": morning_uniform,
            "uniform_afternoon": afternoon_uniform,
            "morning": morning_body,
            "afternoon": afternoon_body,
        }

    def _sync_tkb_to_esp32(self) -> None:
        self._run_bg(
            lambda: self.api_client.switch_display_view("tkb"),
            lambda r: self.show_toast("Đã gửi yêu cầu hiển thị TKB lên ESP32!" if r.ok else r.error_message),
        )

    def _apply_tkb_ui(self, tt: Optional[DualDayTimetable]) -> None:
        if not tt or not hasattr(self, "tkb_today_card"):
            return
        self.tkb_week_badge.set_text(f"Tuần {tt.week_number}")
        self.tkb_action_row.set_subtitle(f"Tuần học hiện tại: {tt.week_number}")

        def _fill(card: Dict[str, Any], day, heading_prefix: str) -> None:
            day_name = day.display_day or day.day
            card["container"].set_title(f"{heading_prefix} • {day_name}")
            card["day"].set_text(
                f"{day_name} • {len(day.morning)} tiết sáng"
                + (f" • {len(day.afternoon)} tiết chiều" if day.has_afternoon else "")
            )
            card["uniform_morning"].set_text(f"Đồng phục sáng: {day.dp_sang}")
            has_pm_uniform = bool(day.dp_chieu)
            card["uniform_afternoon"].set_visible(has_pm_uniform)
            if has_pm_uniform:
                card["uniform_afternoon"].set_text(f"Đồng phục chiều: {day.dp_chieu}")
            card["morning"].set_text(
                "\n".join(day.morning) if day.morning else "Nghỉ học buổi sáng"
            )
            if day.has_afternoon and day.afternoon:
                card["afternoon"].set_text("\n".join(day.afternoon))
            else:
                card["afternoon"].set_text("Không có tiết học buổi chiều")

        _fill(self.tkb_today_card, tt.today, "Lịch học hôm nay")
        _fill(self.tkb_tomorrow_card, tt.tomorrow, "Lịch học kế tiếp")

        if hasattr(self, "timetable_row"):
            m_today = " • ".join(tt.today.morning) if tt.today.morning else "Nghỉ sáng"
            self.timetable_row.set_subtitle(f"{tt.today.day}: {m_today} | ĐP: {tt.today.dp_sang}")

    def refresh_tkb_async(self) -> None:
        def _done(res_and_tt: Tuple[ApiResult, Optional[DualDayTimetable]]) -> None:
            res, tt = res_and_tt
            if res.ok and tt:
                self._apply_tkb_ui(tt)
            else:
                self.show_toast(f"TKB: {res.error_message or 'Không thể tải lịch học'}")

        self._run_bg(self.api_client.get_timetable_today, _done)

    # =========================================================================
    # Page 3: Sticky Notes (CRUD + Pin + Confirmation Dialog + Authoritative Delete)
    # =========================================================================
    def _build_notes_page(self) -> Gtk.Widget:
        page = Adw.PreferencesPage()

        create_group = Adw.PreferencesGroup(
            title="Create Sticky Note",
            description="Notes persist on the server and can be pinned to the ESP32 OLED screen.",
        )
        page.add(create_group)

        self.note_input_entry = Adw.EntryRow(title="Note Content (max 200 chars)")
        add_btn = Gtk.Button(label="Add Note", valign=Gtk.Align.CENTER)
        add_btn.add_css_class("suggested-action")
        add_btn.connect("clicked", self._on_create_note_clicked)
        self.note_input_entry.add_suffix(add_btn)
        create_group.add(self.note_input_entry)

        unpin_row = Adw.ActionRow(
            title="Station Pinned Note Controls",
            subtitle="Clear the currently pinned sticky note on the ESP32 OLED display.",
        )
        unpin_btn = Gtk.Button(label="Unpin Station Note", valign=Gtk.Align.CENTER)
        unpin_btn.connect("clicked", self._on_unpin_note_clicked)
        unpin_row.add_suffix(unpin_btn)
        refresh_notes_btn = Gtk.Button(icon_name="view-refresh-symbolic", valign=Gtk.Align.CENTER)
        refresh_notes_btn.connect("clicked", lambda *_: self.refresh_notes_async())
        unpin_row.add_suffix(refresh_notes_btn)
        create_group.add(unpin_row)

        self.notes_list_group = Adw.PreferencesGroup(
            title="Saved Sticky Notes",
            description="Pin a note to display it on ESP32, or delete it (with confirmation).",
        )
        page.add(self.notes_list_group)
        self._note_rows: List[Gtk.Widget] = []
        self._render_notes_list(self.api_client.notes)

        # Motivational AI Quote Generator (Feature 9)
        quote_group = Adw.PreferencesGroup(
            title="Motivational AI Quote",
            description="Generate inspiring study &amp; productivity quotes from Groq AI.",
        )
        page.add(quote_group)

        self.quote_topic_entry = Gtk.Entry()
        self.quote_topic_entry.set_placeholder_text("Chủ đề (VD: học tập, sáng tạo, kỷ luật)")
        self.quote_topic_entry.set_text("học tập và kiên trì")
        self.quote_topic_entry.set_hexpand(True)
        self.quote_topic_row = Adw.ActionRow(title="Chủ đề câu danh ngôn")
        self.quote_topic_row.add_suffix(self.quote_topic_entry)
        quote_group.add(self.quote_topic_row)

        self.quote_display_row = Adw.ActionRow(
            title="Current Motivational Quote",
            subtitle="“Kiên trì hôm nay, thành công ngày mai!”",
        )
        self.quote_gen_btn = Gtk.Button(label="Generate AI Quote", valign=Gtk.Align.CENTER)
        self.quote_gen_btn.add_css_class("suggested-action")
        self.quote_gen_btn.connect("clicked", self._on_generate_quote_clicked)
        self.quote_display_row.add_suffix(self.quote_gen_btn)
        quote_group.add(self.quote_display_row)

        return page

    def _render_notes_list(self, notes: List[NoteItem]) -> None:
        for row in self._note_rows:
            self.notes_list_group.remove(row)
        self._note_rows.clear()

        if not notes:
            empty_row = Adw.ActionRow(
                title="No sticky notes saved",
                subtitle="Add a note above to display it here and on your ESP32 Station.",
            )
            self.notes_list_group.add(empty_row)
            self._note_rows.append(empty_row)
            return

        for note in notes:
            title_txt = note.content or "(Empty note)"
            sub_txt = f"ID: {note.id}"
            if note.pinned:
                sub_txt += " • PINNED ON ESP32"
            row = Adw.ActionRow(title=title_txt, subtitle=sub_txt)

            pin_btn = Gtk.Button(
                label="Pinned" if note.pinned else "Pin to ESP32",
                valign=Gtk.Align.CENTER,
            )
            if note.pinned:
                pin_btn.add_css_class("suggested-action")
            pin_btn.connect("clicked", lambda _b, nid=note.id: self._on_pin_note_clicked(nid))
            row.add_suffix(pin_btn)

            del_btn = Gtk.Button(
                icon_name="user-trash-symbolic",
                valign=Gtk.Align.CENTER,
                tooltip_text="Delete Note",
            )
            del_btn.add_css_class("destructive-action")
            is_deleting = note.id in self._deleting_note_ids
            del_btn.set_sensitive(not is_deleting)
            del_btn.connect("clicked", lambda _b, n=note: self._confirm_delete_note(n))
            row.add_suffix(del_btn)

            self.notes_list_group.add(row)
            self._note_rows.append(row)

    def refresh_notes_async(self) -> None:
        def _done(res: ApiResult) -> None:
            if res.ok:
                self._render_notes_list(self.api_client.notes)
            else:
                self.show_toast(f"Failed to load notes: {res.error_message}")

        self._run_bg(self.api_client.get_notes, _done)

    def _on_create_note_clicked(self, _btn: Gtk.Button) -> None:
        text = self.note_input_entry.get_text().strip()
        if not text:
            self.show_toast("Note content cannot be empty.")
            return

        def _done(res: ApiResult) -> None:
            if res.ok:
                self.note_input_entry.set_text("")
                self._render_notes_list(self.api_client.notes)
                self.show_toast("Sticky note created and synced!")
            else:
                self.show_toast(f"Create note failed: {res.error_message}")

        self._run_bg(lambda: self.api_client.create_note(text), _done)

    def _on_pin_note_clicked(self, note_id: str) -> None:
        def _done(res: ApiResult) -> None:
            if res.ok:
                self._render_notes_list(self.api_client.notes)
                self.show_toast("Note pinned to ESP32 OLED!")
            else:
                self.show_toast(f"Pin failed: {res.error_message}")

        self._run_bg(lambda: self.api_client.pin_note(note_id), _done)

    def _on_unpin_note_clicked(self, _btn: Gtk.Button) -> None:
        def _done(res: ApiResult) -> None:
            if res.ok:
                self._render_notes_list(self.api_client.notes)
                self.show_toast("Cleared pinned note on ESP32.")
            else:
                self.show_toast(f"Unpin failed: {res.error_message}")

        self._run_bg(self.api_client.unpin_note, _done)

    def _on_generate_quote_clicked(self, _btn: Gtk.Button) -> None:
        self.quote_gen_btn.set_sensitive(False)
        self.quote_display_row.set_subtitle("Đang tạo danh ngôn từ Groq AI...")
        def _done(res: ApiResult) -> None:
            self.quote_gen_btn.set_sensitive(True)
            if res.ok and isinstance(res.data, dict):
                quote = res.data.get("quote") or res.data.get("data", {}).get("quote", "")
                if quote:
                    self.quote_display_row.set_subtitle(f"“{quote}”")
                    self.show_toast("Quote sent to ESP32 Station!")
                    return
            self.show_toast(f"Quote generation failed: {res.error_message}")
            self.quote_display_row.set_subtitle("Could not generate quote.")
        topic = self.quote_topic_entry.get_text().strip() or "học tập và kiên trì"
        # pin=True makes the backend create + pin the sticky note in one call, so
        # the generated quote really shows up on the station screen.
        self._run_bg(lambda: self.api_client.generate_quote(topic=topic, pin=True), _done)

    def _confirm_delete_note(self, note: NoteItem) -> None:
        if note.id in self._deleting_note_ids:
            return
        dialog = Adw.AlertDialog(
            heading="Delete Sticky Note?",
            body=f"Are you sure you want to permanently delete \"{note.content}\"?\nIf pinned, it will also be cleared from the ESP32 Station.",
        )
        dialog.add_response("cancel", "Cancel")
        dialog.add_response("delete", "Delete")
        dialog.set_response_appearance("delete", Adw.ResponseAppearance.DESTRUCTIVE)
        dialog.set_default_response("cancel")
        dialog.set_close_response("cancel")
        dialog.connect(
            "response",
            lambda _d, resp: self._execute_delete_note(note.id) if resp == "delete" else None,
        )
        dialog.present(self)

    def _execute_delete_note(self, note_id: str) -> None:
        if note_id in self._deleting_note_ids:
            return
        self._deleting_note_ids.add(note_id)
        self._render_notes_list(self.api_client.notes)

        def _done(res: ApiResult) -> None:
            self._deleting_note_ids.discard(note_id)
            # Authoritative update: only reflects backend state after HTTP 200
            self._render_notes_list(self.api_client.notes)
            if res.ok:
                self.show_toast("Sticky note deleted.")
            else:
                self.show_toast(f"Delete failed ({res.status_code}): {res.error_message}")

        self._run_bg(lambda: self.api_client.delete_note(note_id), _done)

    # =========================================================================
    # Page 4: Exam Countdowns
    # =========================================================================
    def _build_countdowns_page(self) -> Gtk.Widget:
        page = Adw.PreferencesPage()

        create_group = Adw.PreferencesGroup(
            title="Add Exam / Event Countdown",
            description="Create a countdown and pin it to the ESP32 Station.",
        )
        page.add(create_group)

        self.cd_name_entry = Adw.EntryRow(title="Event Name (e.g., THPT Quốc Gia)")
        create_group.add(self.cd_name_entry)

        self.cd_date_entry = Adw.EntryRow(title="Target Date (YYYY-MM-DD or DD/MM)")
        add_cd_btn = Gtk.Button(label="Add Countdown", valign=Gtk.Align.CENTER)
        add_cd_btn.add_css_class("suggested-action")
        add_cd_btn.connect("clicked", self._on_create_countdown_clicked)
        self.cd_date_entry.add_suffix(add_cd_btn)
        create_group.add(self.cd_date_entry)

        self.countdowns_list_group = Adw.PreferencesGroup(
            title="Active Countdowns",
            description="Manage saved countdowns.",
        )
        page.add(self.countdowns_list_group)
        self._countdown_rows: List[Gtk.Widget] = []
        self._render_countdowns_list(self.api_client.countdowns)

        return page

    def _render_countdowns_list(self, items: List[CountdownItem]) -> None:
        for row in self._countdown_rows:
            self.countdowns_list_group.remove(row)
        self._countdown_rows.clear()

        if not items:
            empty = Adw.ActionRow(
                title="No countdowns configured",
                subtitle="Add an event above to track remaining days.",
            )
            self.countdowns_list_group.add(empty)
            self._countdown_rows.append(empty)
            return

        for item in items:
            sub = f"Target: {item.target_date} • {item.days_left} days left"
            if item.pinned:
                sub += " • PINNED ON ESP32"
            row = Adw.ActionRow(title=item.name, subtitle=sub)

            pin_btn = Gtk.Button(
                label="Pinned" if item.pinned else "Pin",
                valign=Gtk.Align.CENTER,
            )
            pin_btn.connect("clicked", lambda _b, cid=item.id: self._on_pin_countdown_clicked(cid))
            row.add_suffix(pin_btn)

            del_btn = Gtk.Button(icon_name="user-trash-symbolic", valign=Gtk.Align.CENTER)
            del_btn.add_css_class("destructive-action")
            del_btn.connect("clicked", lambda _b, c=item: self._execute_delete_countdown(c.id))
            row.add_suffix(del_btn)

            self.countdowns_list_group.add(row)
            self._countdown_rows.append(row)

    def _on_create_countdown_clicked(self, _btn: Gtk.Button) -> None:
        name = self.cd_name_entry.get_text().strip()
        dt = self.cd_date_entry.get_text().strip()
        if not name or not dt:
            self.show_toast("Please enter both event name and target date.")
            return

        def _done(res: ApiResult) -> None:
            if res.ok:
                self.cd_name_entry.set_text("")
                self.cd_date_entry.set_text("")
                self._render_countdowns_list(self.api_client.countdowns)
                self.show_toast("Countdown created!")
            else:
                self.show_toast(f"Failed to create countdown: {res.error_message}")

        self._run_bg(lambda: self.api_client.create_countdown(name, dt), _done)

    def _on_pin_countdown_clicked(self, countdown_id: str) -> None:
        def _done(res: ApiResult) -> None:
            if res.ok:
                self._render_countdowns_list(self.api_client.countdowns)
                self.show_toast("Countdown pinned to ESP32!")
            else:
                self.show_toast(f"Pin countdown failed: {res.error_message}")

        self._run_bg(lambda: self.api_client.pin_countdown(countdown_id), _done)

    def _execute_delete_countdown(self, countdown_id: str) -> None:
        def _done(res: ApiResult) -> None:
            self._render_countdowns_list(self.api_client.countdowns)
            if res.ok:
                self.show_toast("Countdown deleted.")
            else:
                self.show_toast(f"Delete countdown failed: {res.error_message}")

        self._run_bg(lambda: self.api_client.delete_countdown(countdown_id), _done)

    # =========================================================================
    # Page 5: Weather Forecast
    # =========================================================================
    def _build_weather_page(self) -> Gtk.Widget:
        page = Adw.PreferencesPage()

        ctrl_group = Adw.PreferencesGroup(
            title="24-Hour Weather Forecast (3-Hour Intervals)",
            description="OpenWeatherMap 5-day/3h forecast unified across Backend, Android, and Desktop.",
        )
        page.add(ctrl_group)

        self.weather_city_entry = Adw.EntryRow(title="City Override (optional, e.g., Ho Chi Minh)")
        fetch_btn = Gtk.Button(label="Fetch Forecast", valign=Gtk.Align.CENTER)
        fetch_btn.add_css_class("suggested-action")
        fetch_btn.connect("clicked", lambda *_: self.refresh_weather_async())
        self.weather_city_entry.add_suffix(fetch_btn)
        ctrl_group.add(self.weather_city_entry)

        self.weather_list_group = Adw.PreferencesGroup(title="Upcoming Intervals")
        page.add(self.weather_list_group)
        self._weather_rows: List[Gtk.Widget] = []

        return page

    def refresh_weather_async(self) -> None:
        city = self.weather_city_entry.get_text().strip() or None

        def _done(pair: Any) -> None:
            res, intervals = pair
            for r in self._weather_rows:
                self.weather_list_group.remove(r)
            self._weather_rows.clear()

            if not res.ok:
                row = Adw.ActionRow(
                    title="Forecast unavailable",
                    subtitle=res.error_message,
                )
                self.weather_list_group.add(row)
                self._weather_rows.append(row)
                return

            if not intervals:
                row = Adw.ActionRow(title="No forecast intervals returned", subtitle="")
                self.weather_list_group.add(row)
                self._weather_rows.append(row)
                return

            for iv in intervals:
                rain_tag = "🌧 RAIN ALERT" if iv.is_rain else f"Rain prob: {iv.rain_prob:.0f}%"
                row = Adw.ActionRow(
                    title=f"{iv.time} — {iv.temp:.1f}°C ({iv.description})",
                    subtitle=rain_tag,
                )
                self.weather_list_group.add(row)
                self._weather_rows.append(row)

        self._run_bg(lambda: self.api_client.get_weather_forecast(city), _done)

    # =========================================================================
    # Page 6: Morse Code Studio & ESP32 Buzzer Playback
    # =========================================================================
    def _build_morse_page(self) -> Gtk.Widget:
        page = Adw.PreferencesPage()

        conv_group = Adw.PreferencesGroup(
            title="Live Bidirectional Morse Code Converter",
            description="Real-time live conversion between text and Morse code with ESP32 hardware playback.",
        )
        page.add(conv_group)

        self._is_updating_morse = False

        self.morse_text_entry = Adw.EntryRow(title="Plain Text Input")
        self.morse_text_entry.set_text("SOS DESKFLOW")
        self.morse_text_entry.connect("changed", self._on_text_changed_live)
        to_morse_btn = Gtk.Button(label="Text → Morse", valign=Gtk.Align.CENTER)
        to_morse_btn.connect("clicked", self._on_text_to_morse)
        self.morse_text_entry.add_suffix(to_morse_btn)
        conv_group.add(self.morse_text_entry)

        self.morse_pattern_entry = Adw.EntryRow(title="Morse Pattern (. - /)")
        self.morse_pattern_entry.set_text("... --- ... / -.. . ... -.- ..-. .-.. --- .--")
        self.morse_pattern_entry.connect("changed", self._on_morse_changed_live)
        to_text_btn = Gtk.Button(label="Morse → Text", valign=Gtk.Align.CENTER)
        to_text_btn.connect("clicked", self._on_morse_to_text)
        self.morse_pattern_entry.add_suffix(to_text_btn)
        conv_group.add(self.morse_pattern_entry)

        keypad_row = Adw.ActionRow(
            title="Quick Morse Keypad",
            subtitle="Tap buttons to append Morse symbols in real time.",
        )
        box = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=6)
        for label, symbol in [("• (Dot)", "."), ("— (Dash)", "-"), ("[Ký tự]", " "), ("/ [Từ]", " / ")]:
            btn = Gtk.Button(label=label, valign=Gtk.Align.CENTER)
            btn.connect("clicked", lambda _b, s=symbol: self._append_morse_symbol(s))
            box.append(btn)
        clear_btn = Gtk.Button(label="Clear", valign=Gtk.Align.CENTER)
        clear_btn.connect("clicked", lambda _b: self._clear_morse())
        box.append(clear_btn)
        keypad_row.add_suffix(box)
        conv_group.add(keypad_row)

        hw_group = Adw.PreferencesGroup(
            title="ESP32 Hardware Buzzer Playback",
            description="Transmits Morse pattern via Backend → MQTT (deskflow/command) → ESP32 GPIO26 buzzer.",
        )
        page.add(hw_group)

        self.morse_unit_spin = Adw.SpinRow.new_with_range(20, 300, 10)
        self.morse_unit_spin.set_title("Dot Unit Duration (ms)")
        self.morse_unit_spin.set_value(60)
        hw_group.add(self.morse_unit_spin)

        self.morse_freq_spin = Adw.SpinRow.new_with_range(400, 4000, 50)
        self.morse_freq_spin.set_title("Tone Frequency (Hz)")
        self.morse_freq_spin.set_value(1000)
        hw_group.add(self.morse_freq_spin)

        action_row = Adw.ActionRow(
            title="Hardware Playback Controls",
            subtitle="Play or immediately stop Morse audio on the physical ESP32 Station.",
        )
        play_btn = Gtk.Button(label="Play on ESP32", valign=Gtk.Align.CENTER)
        play_btn.add_css_class("suggested-action")
        play_btn.connect("clicked", self._on_play_morse_clicked)
        stop_btn = Gtk.Button(label="Stop Playback", valign=Gtk.Align.CENTER)
        stop_btn.add_css_class("destructive-action")
        stop_btn.connect("clicked", self._on_stop_morse_clicked)
        action_row.add_suffix(play_btn)
        action_row.add_suffix(stop_btn)
        hw_group.add(action_row)

        # ---- MIDI melody player (.mid upload + built-in chiptunes) ----
        melody_group = Adw.PreferencesGroup(
            title="Phát nhạc MIDI trên còi ESP32",
            description="Chọn bài 8-bit có sẵn hoặc tải lên file .mid để phát qua còi trạm.",
        )
        page.add(melody_group)

        self.melody_preset_combo = Adw.ComboRow(
            title="Giai điệu có sẵn",
            subtitle="Chọn bài rồi bấm Phát.",
        )
        self.melody_preset_combo.set_model(
            Gtk.StringList.new(
                ["Super Mario", "Rickroll", "Tetris Theme", "Coffin Dance", "Imperial March", "Nokia Tune"]
            )
        )
        melody_group.add(self.melody_preset_combo)

        melody_actions = Adw.ActionRow(
            title="Điều khiển phát nhạc",
            subtitle="Phát bài đã chọn, hoặc chọn file .mid từ máy tính.",
        )
        play_preset_btn = Gtk.Button(label="▶ Phát bài mẫu", valign=Gtk.Align.CENTER)
        play_preset_btn.add_css_class("suggested-action")
        play_preset_btn.connect("clicked", self._on_play_melody_preset)
        melody_actions.add_suffix(play_preset_btn)

        file_btn = Gtk.Button(label="📂 Chọn file .mid", valign=Gtk.Align.CENTER)
        file_btn.connect("clicked", self._on_pick_midi_file)
        melody_actions.add_suffix(file_btn)

        stop_melody_btn = Gtk.Button(label="⏹ Dừng", valign=Gtk.Align.CENTER)
        stop_melody_btn.add_css_class("destructive-action")
        stop_melody_btn.connect("clicked", lambda *_: self._run_bg(
            self.api_client.stop_melody,
            lambda r: self.show_toast("Đã dừng phát nhạc." if r.ok else r.error_message),
        ))
        melody_actions.add_suffix(stop_melody_btn)
        melody_group.add(melody_actions)

        return page

    MELODY_PRESET_IDS = ["mario", "rickroll", "tetris", "coffin", "starwars", "nokia"]

    def _on_play_melody_preset(self, _btn: Gtk.Button) -> None:
        idx = self.melody_preset_combo.get_selected()
        if idx < 0 or idx >= len(self.MELODY_PRESET_IDS):
            idx = 0
        preset_id = self.MELODY_PRESET_IDS[idx]
        self._run_bg(
            lambda: self.api_client.play_melody_preset(preset_id),
            lambda r: self.show_toast(
                f"Đang phát {preset_id} trên còi ESP32!" if r.ok else r.error_message
            ),
        )

    def _on_pick_midi_file(self, _btn: Gtk.Button) -> None:
        dialog = Gtk.FileDialog()
        dialog.set_title("Chọn file MIDI (.mid / .midi)")

        midi_filter = Gtk.FileFilter()
        midi_filter.set_name("MIDI files")
        midi_filter.add_pattern("*.mid")
        midi_filter.add_pattern("*.midi")

        filters = Gio.ListStore.new(Gtk.FileFilter)
        filters.append(midi_filter)
        dialog.set_filters(filters)
        dialog.set_default_filter(midi_filter)

        def _opened(source: Any, result: Any) -> None:
            try:
                gfile = source.open_finish(result)
                path = gfile.get_path()
                if not path:
                    return
                with open(path, "rb") as fh:
                    data = fh.read()
                import os

                filename = os.path.basename(path)
                self._run_bg(
                    lambda: self.api_client.play_melody_file(filename, data),
                    lambda r: self.show_toast(
                        f"Đang phát {filename} trên còi ESP32!" if r.ok else r.error_message
                    ),
                )
            except Exception as exc:
                logger.debug("[MIDI] File dialog closed or failed: %s", exc)

        dialog.open(self, None, _opened)

    def _on_text_changed_live(self, _entry: Any) -> None:
        if getattr(self, "_is_updating_morse", False):
            return
        self._is_updating_morse = True
        try:
            txt = self.morse_text_entry.get_text()
            encoded = MorseCodeConverter.text_to_morse(txt)
            self.morse_pattern_entry.set_text(encoded)
        finally:
            self._is_updating_morse = False

    def _on_morse_changed_live(self, _entry: Any) -> None:
        if getattr(self, "_is_updating_morse", False):
            return
        self._is_updating_morse = True
        try:
            pattern = self.morse_pattern_entry.get_text()
            decoded = MorseCodeConverter.morse_to_text(pattern)
            self.morse_text_entry.set_text(decoded)
        finally:
            self._is_updating_morse = False

    def _append_morse_symbol(self, sym: str) -> None:
        current = self.morse_pattern_entry.get_text()
        self.morse_pattern_entry.set_text(current + sym)

    def _clear_morse(self) -> None:
        self._is_updating_morse = True
        self.morse_text_entry.set_text("")
        self.morse_pattern_entry.set_text("")
        self._is_updating_morse = False

    def _on_text_to_morse(self, _btn: Gtk.Button) -> None:
        txt = self.morse_text_entry.get_text()
        encoded = MorseCodeConverter.text_to_morse(txt)
        self.morse_pattern_entry.set_text(encoded)

    def _on_morse_to_text(self, _btn: Gtk.Button) -> None:
        pattern = self.morse_pattern_entry.get_text()
        decoded = MorseCodeConverter.morse_to_text(pattern)
        self.morse_text_entry.set_text(decoded)

    def _on_play_morse_clicked(self, _btn: Gtk.Button) -> None:
        pattern = self.morse_pattern_entry.get_text().strip()
        unit_ms = int(self.morse_unit_spin.get_value())
        freq = int(self.morse_freq_spin.get_value())
        if not pattern:
            self.show_toast("Morse pattern cannot be empty.")
            return

        def _done(res: ApiResult) -> None:
            if res.ok:
                self.show_toast("Morse pattern sent to ESP32 Station!")
            else:
                self.show_toast(f"Morse play error: {res.error_message}")

        self._run_bg(lambda: self.api_client.play_morse(pattern, unit_ms, freq), _done)

    def _on_stop_morse_clicked(self, _btn: Gtk.Button) -> None:
        def _done(res: ApiResult) -> None:
            if res.ok:
                self.show_toast("Morse playback stopped.")
            else:
                self.show_toast(f"Morse stop error: {res.error_message}")

        self._run_bg(self.api_client.stop_morse, _done)

    # =========================================================================
    # Page 7: ESP32 Station Display, Clock Studio, Lamp, Spotify & Timetable
    # =========================================================================
    def _build_station_page(self) -> Gtk.Widget:
        page = Adw.PreferencesPage()

        # 1. Live ESP32 TFT Display Monitor (320x240 preview)
        monitor_group = Adw.PreferencesGroup(
            title="Live ESP32 TFT Display Monitor (320×240)",
            description="High-contrast preview of the screen or watch face before dispatching to ESP32 silicon.",
        )
        page.add(monitor_group)

        monitor_bezel = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=8)
        monitor_bezel.add_css_class("oled-bezel")

        top_header = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=8)
        dot_label = Gtk.Label(label="●")
        dot_label.add_css_class("deskflow-badge-online")
        title_label = Gtk.Label(label="320×240 TFT (ST7789) MONITOR", xalign=0.0)
        title_label.add_css_class("oled-text-sub")
        self.oled_mode_badge = Gtk.Label(label="CLOCK FACE", xalign=1.0, hexpand=True)
        self.oled_mode_badge.add_css_class("oled-title")
        top_header.append(dot_label)
        top_header.append(title_label)
        top_header.append(self.oled_mode_badge)
        monitor_bezel.append(top_header)

        # OLED Screen Glass
        self.oled_glass = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        self.oled_glass.add_css_class("oled-glass")
        self.oled_glass.set_size_request(320, 240)

        self.oled_screen_title = Gtk.Label(label="DESKFLOW CLOCK", xalign=0.5)
        self.oled_screen_title.add_css_class("oled-text-sub")
        self.oled_screen_main = Gtk.Label(label="12:30 :45", xalign=0.5)
        self.oled_screen_main.add_css_class("oled-text-large")
        self.oled_screen_sub = Gtk.Label(label="SUN 08 NOV • 28°C HCM", xalign=0.5)
        self.oled_screen_sub.add_css_class("oled-text-sub")

        self.oled_glass.append(self.oled_screen_title)
        self.oled_glass.append(self.oled_screen_main)
        self.oled_glass.append(self.oled_screen_sub)
        monitor_bezel.append(self.oled_glass)

        bottom_bar = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=10)
        hint_label = Gtk.Label(
            label="Click any watch face or view below to preview. Click Apply to send to ESP32.",
            xalign=0.0,
            hexpand=True,
        )
        hint_label.add_css_class("caption")
        apply_btn = Gtk.Button(label="Apply to Station OLED")
        apply_btn.add_css_class("suggested-action")
        apply_btn.connect("clicked", self._on_apply_oled_preview)
        bottom_bar.append(hint_label)
        bottom_bar.append(apply_btn)
        monitor_bezel.append(bottom_bar)

        monitor_group.add(monitor_bezel)

        # 2. Clock Face Studio (7 Watch Face Styles with Visual Cards)
        clock_group = Adw.PreferencesGroup(
            title="Clock Face Studio (7 Watch Face Styles)",
            description="Select and preview clock faces for the ESP32 OLED display.",
        )
        page.add(clock_group)

        clock_flow = Gtk.FlowBox()
        clock_flow.set_selection_mode(Gtk.SelectionMode.NONE)
        clock_flow.set_max_children_per_line(4)
        clock_flow.set_column_spacing(10)
        clock_flow.set_row_spacing(10)

        for style_id, _code, name, desc, sample in self.CLOCK_STYLES:
            card_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
            card_box.set_size_request(150, 75)
            card_box.set_margin_top(4)
            card_box.set_margin_bottom(4)
            card_box.set_margin_start(6)
            card_box.set_margin_end(6)

            mini_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=2)
            mini_box.add_css_class("oled-mini-box")
            sample_lbl = Gtk.Label(label=sample, xalign=0.5)
            sample_lbl.add_css_class("oled-text-sub")
            mini_box.append(sample_lbl)

            lbl_title = Gtk.Label(label=f"{style_id}. {name}", xalign=0.0)
            lbl_title.add_css_class("heading")
            lbl_desc = Gtk.Label(label=desc, xalign=0.0)
            lbl_desc.add_css_class("caption")
            lbl_desc.set_wrap(True)

            card_box.append(mini_box)
            card_box.append(lbl_title)
            card_box.append(lbl_desc)

            btn = Gtk.Button()
            btn.set_child(card_box)
            btn.connect("clicked", lambda _b, sid=style_id: self._select_clock_style(sid))
            clock_flow.append(btn)

        clock_group.add(clock_flow)

        # 2b. Clock Accent Color (7 Palette Swatches & Custom Hex)
        accent_group = Adw.PreferencesGroup(
            title="Màu điểm nhấn đồng hồ (Accent Color)",
            description="Tùy chỉnh màu sắc kim và chữ số hiển thị trên màn hình OLED đồng hồ.",
        )
        page.add(accent_group)

        accent_swatches = [
            ("Sky Blue", "#38BDF8"),
            ("Emerald", "#10B981"),
            ("Amber", "#F59E0B"),
            ("Rose", "#EF4444"),
            ("Purple", "#8B5CF6"),
            ("Pink", "#EC4899"),
            ("White", "#FFFFFF"),
        ]
        accent_group.add(
            self._build_preset_flow(
                "Bảng màu gợi ý",
                "Sky Blue, Emerald Green, Amber Orange, Rose Red, Purple, Pink, White.",
                accent_swatches,
                self._apply_clock_accent_color,
                columns=4,
            )
        )

        self.clock_accent_entry = Adw.EntryRow(title="Mã màu Hex (#RRGGBB)")
        self.clock_accent_entry.set_text(self.clock_accent_color)
        apply_accent_btn = Gtk.Button(label="Áp dụng màu", valign=Gtk.Align.CENTER)
        apply_accent_btn.add_css_class("suggested-action")
        apply_accent_btn.connect("clicked", lambda *_: self._apply_clock_accent_color(self.clock_accent_entry.get_text().strip()))
        self.clock_accent_entry.add_suffix(apply_accent_btn)

        try:
            acc_color_dialog = Gtk.ColorDialog()
            self.acc_color_dialog_btn = Gtk.ColorDialogButton(dialog=acc_color_dialog)
            self.acc_color_dialog_btn.set_valign(Gtk.Align.CENTER)
            self.acc_color_dialog_btn.set_tooltip_text("Mở bảng chọn màu chi tiết")
            def _on_acc_color_notify(btn, _pspec):
                rgba = btn.get_rgba()
                hex_val = f"#{int(rgba.red * 255):02X}{int(rgba.green * 255):02X}{int(rgba.blue * 255):02X}"
                self._apply_clock_accent_color(hex_val)
            self.acc_color_dialog_btn.connect("notify::rgba", _on_acc_color_notify)
            self.clock_accent_entry.add_suffix(self.acc_color_dialog_btn)
        except Exception:
            pass

        accent_group.add(self.clock_accent_entry)

        # 3. OLED Display View Switcher (8 Views with Visual Cards)
        view_group = Adw.PreferencesGroup(
            title="ESP32 TFT Display View Switcher (Clock Page System)",
            description="Switch the active screen on the 320×240 TFT (ST7789) display.",
        )
        page.add(view_group)

        self._view_card_labels: List[tuple] = []
        view_flow = Gtk.FlowBox()
        view_flow.set_selection_mode(Gtk.SelectionMode.NONE)
        view_flow.set_max_children_per_line(4)
        view_flow.set_column_spacing(10)
        view_flow.set_row_spacing(10)
        for view_key, vi_title, en_title, vi_desc, en_desc in self.DISPLAY_VIEWS:
            view_label = self._tr(vi_title, en_title)
            view_desc = self._tr(vi_desc, en_desc)
            card_box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
            card_box.set_size_request(140, 65)
            card_box.set_margin_top(6)
            card_box.set_margin_bottom(6)
            card_box.set_margin_start(8)
            card_box.set_margin_end(8)
            lbl_title = Gtk.Label(label=view_label, xalign=0.0)
            lbl_title.add_css_class("heading")
            lbl_desc = Gtk.Label(label=view_desc, xalign=0.0)
            lbl_desc.add_css_class("caption")
            lbl_desc.set_wrap(True)
            self._view_card_labels.append((view_key, lbl_title, lbl_desc))
            card_box.append(lbl_title)
            card_box.append(lbl_desc)

            btn = Gtk.Button()
            btn.set_child(card_box)
            btn.set_tooltip_text(f"Display {view_label} on station: {view_desc}")
            btn.connect("clicked", lambda _b, vk=view_key: self._select_oled_view(vk))
            view_flow.append(btn)
        view_group.add(view_flow)

        self.active_view_row = Adw.ActionRow(
            title=self._tr("Màn hình đang hoạt động trên trạm", "Currently active station screen"),
            subtitle=self._tr("Trạm ESP32 đang hiển thị: Main Dashboard",
                              "The ESP32 station is showing: Main Dashboard"),
        )
        view_group.add(self.active_view_row)

        self.preview_hint_row = Adw.ActionRow(
            title=self._tr("Bản xem trước bên trên", "Preview above"),
            subtitle=self._tr(
                "Bấm một thẻ bên dưới để xem trước, rồi bấm Áp dụng để gửi lên trạm.",
                "Tap a card below to preview it, then Apply to send it to the station.",
            ),
        )
        view_group.add(self.preview_hint_row)

        # Initialize OLED preview
        self._update_oled_preview()

        # Custom LED Lighting
        lamp_group = Adw.PreferencesGroup(
            title="WS2812B NeoPixel LED Lighting",
            description="Control ambient lamp color, hex palettes, and brightness.",
        )
        page.add(lamp_group)

        self.lamp_hex_entry = Adw.EntryRow(title="Lamp Hex Color (#RRGGBB)")
        self.lamp_hex_entry.set_text("#8B5CF6")
        self._lamp_on = True

        self.lamp_preset_flow = self._build_preset_flow(
            "7 màu đèn có sẵn trên ESP32",
            "Trắng, Đỏ, Xanh lá, Xanh dương, Vàng, Tím, Xanh ngọc.",
            [
                ("Trắng", "#FFFFFF"),
                ("Đỏ", "#FF0000"),
                ("Xanh lá", "#00FF00"),
                ("Xanh dương", "#0000FF"),
                ("Vàng", "#FFFF00"),
                ("Tím", "#FF00FF"),
                ("Xanh ngọc", "#00FFFF"),
            ],
            self._apply_lamp_color,
            columns=4,
        )
        lamp_group.add(self.lamp_preset_flow)

        apply_color_btn = Gtk.Button(label="Set Custom Color", valign=Gtk.Align.CENTER)
        apply_color_btn.add_css_class("suggested-action")
        apply_color_btn.connect("clicked", lambda *_: self._apply_lamp_color(self.lamp_hex_entry.get_text().strip()))
        self.lamp_hex_entry.add_suffix(apply_color_btn)

        try:
            color_dialog = Gtk.ColorDialog()
            self.color_dialog_btn = Gtk.ColorDialogButton(dialog=color_dialog)
            self.color_dialog_btn.set_valign(Gtk.Align.CENTER)
            self.color_dialog_btn.set_tooltip_text("Mở bảng màu tuỳ biến (Color Palette)")
            def _on_color_notify(btn, _pspec):
                rgba = btn.get_rgba()
                hex_val = f"#{int(rgba.red * 255):02X}{int(rgba.green * 255):02X}{int(rgba.blue * 255):02X}"
                self._apply_lamp_color(hex_val)
            self.color_dialog_btn.connect("notify::rgba", _on_color_notify)
            self.lamp_hex_entry.add_suffix(self.color_dialog_btn)
        except Exception:
            pass

        power_row = Adw.ActionRow(
            title="Lamp Power",
            subtitle="Turn the WS2812B ring on or off without losing the saved colour.",
        )
        on_btn = Gtk.Button(label="Turn On", valign=Gtk.Align.CENTER)
        on_btn.add_css_class("suggested-action")
        on_btn.connect("clicked", lambda *_: self._set_lamp_power(True))
        power_row.add_suffix(on_btn)
        off_btn = Gtk.Button(label="Turn Off", valign=Gtk.Align.CENTER)
        off_btn.connect("clicked", lambda *_: self._set_lamp_power(False))
        power_row.add_suffix(off_btn)
        lamp_group.add(power_row)

        # LED ring brightness: 0-100% (the ST7789 backlight is fixed hardware)
        self.lamp_bright_spin = Adw.SpinRow.new_with_range(0, 100, 5)
        self.lamp_bright_spin.set_title("LED Ring Brightness (%)")
        self.lamp_bright_spin.set_value(100)
        self.lamp_bright_spin.connect("notify::value", self._on_lamp_brightness_changed)
        lamp_group.add(self.lamp_bright_spin)

        lamp_group.add(self.lamp_hex_entry)

        # Custom Beep & Hardware Buzzer (Phase 4)
        buzzer_group = Adw.PreferencesGroup(
            title="Custom Beep &amp; Hardware Buzzer",
            description="Acoustic alerting with duration control and emergency stop.",
        )
        page.add(buzzer_group)

        self.buzzer_duration_spin = Adw.SpinRow.new_with_range(10, 1000, 10)
        self.buzzer_duration_spin.set_title("Buzzer Duration (ms)")
        self.buzzer_duration_spin.set_value(40)
        buzzer_group.add(self.buzzer_duration_spin)

        buzzer_group.add(
            self._build_preset_flow(
                "Duration Presets",
                "Beep length presets.",
                [(f"{d}ms", str(d)) for d in (40, 80, 150, 250, 500)],
                lambda v: self._on_buzz_preset_clicked(int(v)),
                columns=5,
            )
        )

        test_buzz_row = Adw.ActionRow(
            title="Buzzer Hardware Actions",
            subtitle="Trigger acoustic test or silence sounding alarms.",
        )
        test_buzz_btn = Gtk.Button(label="Test Buzzer", valign=Gtk.Align.CENTER)
        test_buzz_btn.add_css_class("suggested-action")
        test_buzz_btn.connect(
            "clicked",
            lambda *_: self._run_bg(
                lambda: self.api_client.trigger_buzz(int(self.buzzer_duration_spin.get_value())),
                lambda r: self.show_toast(f"Buzz ({int(self.buzzer_duration_spin.get_value())}ms) triggered!" if r.ok else r.error_message),
            ),
        )
        test_buzz_row.add_suffix(test_buzz_btn)

        stop_buzz_btn = Gtk.Button(label="Emergency Stop", valign=Gtk.Align.CENTER)
        stop_buzz_btn.add_css_class("destructive-action")
        stop_buzz_btn.connect(
            "clicked",
            lambda *_: self._run_bg(
                self.api_client.emergency_stop_alarm,
                lambda r: self.show_toast("Alarm silenced!" if r.ok else r.error_message),
            ),
        )
        test_buzz_row.add_suffix(stop_buzz_btn)
        buzzer_group.add(test_buzz_row)

        # Spotify & Timetable summary
        media_group = Adw.PreferencesGroup(
            title="Spotify &amp; Timetable Quick Status",
            description="Live playback and school schedule.",
        )
        page.add(media_group)

        self.spotify_row = Adw.ActionRow(
            title="Spotify Playback",
            subtitle="Press Refresh to load active track.",
        )
        for cmd, icon in [("prev", "media-skip-backward-symbolic"), ("play", "media-playback-start-symbolic"), ("pause", "media-playback-pause-symbolic"), ("next", "media-skip-forward-symbolic")]:
            b = Gtk.Button(icon_name=icon, valign=Gtk.Align.CENTER, tooltip_text=cmd.title())
            b.connect(
                "clicked",
                lambda _b, c=cmd: self._run_bg(
                    lambda: self.api_client.control_spotify(c),
                    lambda r: self.show_toast(f"Spotify: {c}" if r.ok else r.error_message),
                ),
            )
            self.spotify_row.add_suffix(b)
        media_group.add(self.spotify_row)

        self.timetable_row = Adw.ActionRow(
            title="Today's Timetable",
            subtitle="Press Refresh to load subjects and uniform.",
        )
        media_group.add(self.timetable_row)

        return page

    def _select_clock_style(self, style_id: int) -> None:
        self.preview_clock_style = style_id
        self.preview_oled_view = "clock"
        self._update_oled_preview()
        self._run_bg(
            lambda: self.api_client.set_clock_style(style_id, accent_color=self.clock_accent_color),
            lambda r: self.show_toast(f"Watch Face Style #{style_id} activated!" if r.ok else r.error_message),
        )

    def _tr(self, vi: str, en: str) -> str:
        return vi if self.config_store.config.language == "vi" else en

    def _update_active_view_badge(self) -> None:
        """Highlight which station screen is currently live."""
        if not hasattr(self, "active_view_row"):
            return
        view_key_now = getattr(self, "live_station_view", "") or self.preview_oled_view or "main"
        label = view_key_now
        for key, vi_title, en_title, _vi_desc, _en_desc in self.DISPLAY_VIEWS:
            if key == view_key_now:
                label = self._tr(vi_title, en_title)
                break
        self.active_view_row.set_subtitle(
            self._tr(f"Trạm ESP32 đang hiển thị: {label}", f"The ESP32 station is showing: {label}")
        )

    def _select_oled_view(self, view_key: str) -> None:
        self.preview_oled_view = view_key
        self._update_oled_preview()
        self._update_active_view_badge()
        self._run_bg(
            lambda: self.api_client.switch_display_view(view_key),
            lambda r: self.show_toast(f"Switched OLED view to {view_key}" if r.ok else r.error_message),
        )

    def _on_apply_oled_preview(self, _btn: Gtk.Button) -> None:
        if self.preview_oled_view == "main":
            self.oled_mode_badge.set_text("MAIN DASHBOARD")
            self.oled_screen_title.set_text("DESKFLOW STATION (DEFAULT)")
            self.oled_screen_main.set_text("12:45 • Ha Long 28°C")
            self.oled_screen_sub.set_text("♫ Spotify Playing • Quotes & Notes")
        elif self.preview_oled_view == "clock":
            def _apply() -> ApiResult:
                r1 = self.api_client.set_clock_style(self.preview_clock_style, accent_color=self.clock_accent_color)
                if not r1.ok:
                    return r1
                return self.api_client.switch_display_view("clock")
            self._run_bg(
                _apply,
                lambda r: self.show_toast("Applied Watch Face to ESP32!" if r.ok else r.error_message),
            )
        else:
            self._run_bg(
                lambda: self.api_client.switch_display_view(self.preview_oled_view),
                lambda r: self.show_toast(f"Applied {self.preview_oled_view} view to ESP32!" if r.ok else r.error_message),
            )

    def _update_oled_preview(self) -> None:
        if not hasattr(self, "oled_glass"):
            return
        if self.preview_oled_view == "main":
            self.oled_screen_main.remove_css_class("oled-accent-text")
            self.oled_mode_badge.set_text("MAIN DASHBOARD")
            self.oled_screen_title.set_text("DESKFLOW STATION (DEFAULT)")
            self.oled_screen_main.set_text("12:45 • Ha Long 28°C")
            self.oled_screen_sub.set_text("♫ Spotify Playing • Quotes & Notes")
        elif self.preview_oled_view == "clock":
            self.oled_screen_main.add_css_class("oled-accent-text")
            style = self.preview_clock_style
            self.oled_mode_badge.set_text(f"WATCH FACE #{style}")
            if style == 2:
                self.oled_screen_title.set_text("MINIMALIST CLOCK")
                self.oled_screen_main.set_text("12:30:45")
                self.oled_screen_sub.set_text("Sun, 08 Nov • 28°C")
            elif style == 3:
                self.oled_screen_title.set_text("CYBER STATION")
                self.oled_screen_main.set_text("[ 12:30 ]")
                self.oled_screen_sub.set_text("SEC: 45 • NEON GLOW")
            elif style == 4:
                self.oled_screen_title.set_text("ANALOG DIAL")
                self.oled_screen_main.set_text("12:30 PM (●)")
                self.oled_screen_sub.set_text("SUN 08 NOV • 3-HAND")
            elif style == 5:
                self.oled_screen_title.set_text("RETRO FLIP CLOCK")
                self.oled_screen_main.set_text("[ 12 ] : [ 30 ]")
                self.oled_screen_sub.set_text("SEC: 45 • FLIP CARD")
            elif style == 6:
                self.oled_screen_title.set_text("BINARY CLOCK")
                self.oled_screen_main.set_text(":: :: :: :: (12:30)")
                self.oled_screen_sub.set_text("BCD 8-4-2-1 MATRIX")
            elif style == 7:
                self.oled_screen_title.set_text("WORD CLOCK")
                self.oled_screen_main.set_text("TWELVE THIRTY")
                self.oled_screen_sub.set_text("FORTY FIVE SECONDS")
            else:
                self.oled_screen_title.set_text("DIGITAL CLASSIC")
                self.oled_screen_main.set_text("12:30 :45")
                self.oled_screen_sub.set_text("SUN 08 NOV • 28°C HCM")
        elif self.preview_oled_view == "weather":
            self.oled_screen_main.remove_css_class("oled-accent-text")
            self.oled_mode_badge.set_text("WEATHER FORECAST")
            self.oled_screen_title.set_text("HO CHI MINH CITY")
            self.oled_screen_main.set_text("28°C  SUNNY")
            self.oled_screen_sub.set_text("POP: 20% • HUMIDITY: 65%")
        elif self.preview_oled_view == "notes":
            self.oled_screen_main.remove_css_class("oled-accent-text")
            self.oled_mode_badge.set_text("STICKY NOTE")
            self.oled_screen_title.set_text("★ PINNED NOTE")
            self.oled_screen_main.set_text("Meeting at 2:00 PM")
            self.oled_screen_sub.set_text("Review DeskFlow firmware build")
        elif self.preview_oled_view == "countdown":
            self.oled_screen_main.remove_css_class("oled-accent-text")
            self.oled_mode_badge.set_text("TARGET COUNTDOWN")
            self.oled_screen_title.set_text("KHKT 2026 EXAM")
            self.oled_screen_main.set_text("45 DAYS LEFT")
            self.oled_screen_sub.set_text("TARGET: 15/01/2026")
        elif self.preview_oled_view == "focus":
            self.oled_screen_main.remove_css_class("oled-accent-text")
            self.oled_mode_badge.set_text("FOCUS SESSION")
            self.oled_screen_title.set_text("FOCUS ACTIVE")
            self.oled_screen_main.set_text("24:58")
            self.oled_screen_sub.set_text("[████████░░░░] 72% LEFT")
        elif self.preview_oled_view == "stats":
            self.oled_screen_main.remove_css_class("oled-accent-text")
            self.oled_mode_badge.set_text("SERVER TELEMETRY")
            self.oled_screen_title.set_text("HOST & ESP32 STATS")
            self.oled_screen_main.set_text("CPU 24% • RAM 58%")
            self.oled_screen_sub.set_text("UPTIME: 4d 12h • WIFI OK")
        elif self.preview_oled_view == "tkb":
            self.oled_screen_main.remove_css_class("oled-accent-text")
            self.oled_mode_badge.set_text("TIMETABLE")
            self.oled_screen_title.set_text("TKB — SANG THU 2")
            self.oled_screen_main.set_text("Toan • Ly • Hoa")
            self.oled_screen_sub.set_text("Anh • Sinh Hoc")
        elif self.preview_oled_view == "calendar":
            self.oled_screen_main.remove_css_class("oled-accent-text")
            self.oled_mode_badge.set_text("GOOGLE CALENDAR")
            self.oled_screen_title.set_text("TODAY • 09:00-10:30")
            self.oled_screen_main.set_text("Project Sync Meeting")
            self.oled_screen_sub.set_text("@ Google Meet • 2 more today")
        elif self.preview_oled_view == "lyrics":
            self.oled_screen_main.remove_css_class("oled-accent-text")
            self.oled_mode_badge.set_text("SPOTIFY LYRICS")
            self.oled_screen_title.set_text("♫ NOW PLAYING")
            self.oled_screen_main.set_text("Starboy — The Weeknd")
            self.oled_screen_sub.set_text("\"I'm tryna put you in the worst mood...\"")

    # =========================================================================
    # Page 8: Settings, Health Check & Scheduled Dark Mode
    # =========================================================================
    def _build_preset_flow(
        self,
        title: str,
        description: str,
        presets: List[Any],
        on_click: Callable[[str], None],
        columns: int = 4,
    ) -> Gtk.Widget:
        """Preset chips in a wrapping grid.

        Adw.ActionRow.add_suffix() puts every suffix on one line, so rows with
        5-7 buttons were squeezed to one character per line. A FlowBox keeps the
        labels readable when the window is narrow.
        """
        group = Adw.PreferencesGroup(title=title, description=description)
        flow = Gtk.FlowBox()
        flow.set_selection_mode(Gtk.SelectionMode.NONE)
        flow.set_max_children_per_line(columns)
        flow.set_min_children_per_line(min(columns, max(1, len(presets))))
        flow.set_column_spacing(8)
        flow.set_row_spacing(8)
        flow.set_homogeneous(True)

        for label, value in presets:
            btn = Gtk.Button(label=label)
            btn.set_size_request(90, -1)
            btn.connect("clicked", lambda _b, v=value: on_click(v))
            flow.append(btn)

        group.add(flow)
        return group

    def _build_settings_page(self) -> Gtk.Widget:
        page = Adw.PreferencesPage()

        # Theme & Scheduled Dark Mode group
        theme_group = Adw.PreferencesGroup(
            title="Appearance &amp; Scheduled Dark Mode",
            description="Choose System, Light, Dark, or Scheduled Dark Mode with automatic time-window switching.",
        )
        page.add(theme_group)

        self.theme_combo = Adw.ComboRow(
            title="Theme Mode",
            subtitle="Automatically switches between Light and Dark when set to Scheduled.",
        )
        theme_model = Gtk.StringList.new(["System Default", "Light Mode", "Dark Mode", "Scheduled Dark Mode"])
        self.theme_combo.set_model(theme_model)
        mode_to_idx = {"system": 0, "light": 1, "dark": 2, "scheduled": 3}
        self.theme_combo.set_selected(mode_to_idx.get(self.config_store.config.app_theme, 0))
        self.theme_combo.connect("notify::selected", self._on_theme_combo_changed)
        theme_group.add(self.theme_combo)

        # Language Selector (Feature 2)
        self.language_combo = Adw.ComboRow(
            title="Display Language (Ngôn ngữ hiển thị)",
            subtitle="Choose interface language between English and Tiếng Việt.",
        )
        lang_model = Gtk.StringList.new(["English", "Tiếng Việt"])
        self.language_combo.set_model(lang_model)
        self.language_combo.set_selected(1 if self.config_store.config.language == "vi" else 0)
        self.language_combo.connect("notify::selected", self._on_language_combo_changed)
        theme_group.add(self.language_combo)

        # Independent ESP32 Dark Mode Group (Feature 3)
        esp_theme_group = Adw.PreferencesGroup(
            title="ESP32 Display Appearance",
            description="Configure dark mode independently on the ESP32 Station OLED screen.",
        )
        page.add(esp_theme_group)

        self.esp32_theme_combo = Adw.ComboRow(
            title="ESP32 Display Dark Mode",
            subtitle="Controls OLED theme palette independently of the desktop app theme.",
        )
        esp_model = Gtk.StringList.new(["Off (Light Mode)", "Always On (Dark Mode)", "Scheduled (NVS Active)"])
        self.esp32_theme_combo.set_model(esp_model)
        self.esp32_theme_combo.set_selected(0)
        esp_apply_btn = Gtk.Button(label="Apply to ESP32", valign=Gtk.Align.CENTER)
        esp_apply_btn.add_css_class("suggested-action")
        esp_apply_btn.connect("clicked", self._on_apply_esp32_theme_clicked)
        self.esp32_theme_combo.add_suffix(esp_apply_btn)
        esp_theme_group.add(self.esp32_theme_combo)

        self.esp32_language_combo = Adw.ComboRow(
            title="Ngôn ngữ màn hình ESP32 (Device Language)",
            subtitle="Ngôn ngữ hiển thị của đồng hồ/OLED trên trạm, tách biệt ngôn ngữ ứng dụng.",
        )
        self.esp32_language_combo.set_model(Gtk.StringList.new(["Tiếng Việt", "English"]))
        self.esp32_language_combo.set_selected(
            1 if getattr(self.config_store.config, "esp32_language", "vi") == "en" else 0
        )
        self.esp32_language_combo.connect("notify::selected", self._on_esp32_language_changed)
        esp_theme_group.add(self.esp32_language_combo)

        self.dark_start_entry = Adw.EntryRow(title="Dark Mode Start Time (24h HH:MM)")
        self.dark_start_entry.set_text(self.config_store.config.dark_mode_start)
        theme_group.add(self.dark_start_entry)

        self.dark_end_entry = Adw.EntryRow(title="Dark Mode End Time (24h HH:MM)")
        self.dark_end_entry.set_text(self.config_store.config.dark_mode_end)
        save_sched_btn = Gtk.Button(label="Apply Schedule", valign=Gtk.Align.CENTER)
        save_sched_btn.add_css_class("suggested-action")
        save_sched_btn.connect("clicked", self._on_save_schedule_clicked)
        self.dark_end_entry.add_suffix(save_sched_btn)
        theme_group.add(self.dark_end_entry)

        theme_group.add(
            self._build_preset_flow(
                "Quick Schedule Presets",
                "One-click overnight dark mode schedules.",
                [("20:00–06:00", "20:00|06:00"), ("22:00–06:00", "22:00|06:00"), ("21:00–07:00", "21:00|07:00")],
                lambda v: self._apply_schedule_preset(*v.split("|")),
                columns=3,
            )
        )

        self.schedule_status_row = Adw.ActionRow(
            title="Scheduled Dark Mode Evaluator Status",
            subtitle="",
        )
        theme_group.add(self.schedule_status_row)

        # API Connection & Health Check
        api_group = Adw.PreferencesGroup(
            title="Backend Server &amp; Diagnostics",
            description="Configure the DeskFlow API URL and run a live health check.",
        )
        page.add(api_group)

        self.api_url_entry = Adw.EntryRow(title="API Base URL")
        self.api_url_entry.set_text(self.config_store.config.api_url)
        save_api_btn = Gtk.Button(label="Save URL", valign=Gtk.Align.CENTER)
        save_api_btn.connect("clicked", self._on_save_api_url_clicked)
        self.api_url_entry.add_suffix(save_api_btn)
        api_group.add(self.api_url_entry)

        self.health_row = Adw.ActionRow(
            title="Backend Health Check (/api/app/health)",
            subtitle="Click 'Test Connection' to verify server reachability and latency.",
        )
        test_btn = Gtk.Button(label="Test Connection", valign=Gtk.Align.CENTER)
        test_btn.add_css_class("suggested-action")
        test_btn.connect("clicked", self._on_test_health_clicked)
        self.health_row.add_suffix(test_btn)
        api_group.add(self.health_row)

        # Linked Devices & Dynamic Display Selection (Phase 2)
        dev_group = Adw.PreferencesGroup(
            title="Linked Devices &amp; Dynamic Display Selection",
            description="Manage linked devices and choose which device displays the current configuration.",
        )
        page.add(dev_group)

        self.display_device_combo = Adw.ComboRow(
            title="Current Display Device",
            subtitle="Device designated to display current configuration.",
        )
        self.display_device_combo.connect("notify::selected", self._on_display_device_combo_changed)
        dev_group.add(self.display_device_combo)

        refresh_dev_btn = Gtk.Button(label="Refresh Devices", valign=Gtk.Align.CENTER)
        refresh_dev_btn.connect("clicked", lambda *_: self.refresh_devices_async())
        self.display_device_combo.add_suffix(refresh_dev_btn)

        self.devices_list_box = Gtk.ListBox()
        self.devices_list_box.set_selection_mode(Gtk.SelectionMode.NONE)
        self.devices_list_box.add_css_class("boxed-list")
        dev_group.add(self.devices_list_box)

        return page

    def _on_theme_combo_changed(self, combo: Adw.ComboRow, _pspec: Any) -> None:
        idx_to_mode = {0: "system", 1: "light", 2: "dark", 3: "scheduled"}
        new_mode = idx_to_mode.get(combo.get_selected(), "system")
        self.config_store.config.app_theme = new_mode
        self.config_store.save()
        self.apply_theme_preference()

    def _apply_schedule_preset(self, start_str: str, end_str: str) -> None:
        self.dark_start_entry.set_text(start_str)
        self.dark_end_entry.set_text(end_str)
        self.config_store.config.dark_mode_start = start_str
        self.config_store.config.dark_mode_end = end_str
        self.config_store.config.app_theme = "scheduled"
        self.theme_combo.set_selected(3)
        self.config_store.save()
        self.apply_theme_preference()
        self.show_toast(f"Scheduled Dark Mode set to {start_str}–{end_str}")

    def _apply_server_language(self, lang: str) -> None:
        if lang not in ("vi", "en"):
            return
        changed = (
            self.config_store.config.language != lang
            or getattr(self.config_store.config, "esp32_language", "vi") != lang
        )
        if not changed:
            return
        self._suppress_lang_sync = True
        try:
            self.config_store.config.language = lang
            self.config_store.config.esp32_language = lang
            self.config_store.save()
            if hasattr(self, "language_combo"):
                self.language_combo.set_selected(1 if lang == "vi" else 0)
            if hasattr(self, "esp32_language_combo"):
                self.esp32_language_combo.set_selected(1 if lang == "en" else 0)
            self._refresh_language()
        finally:
            self._suppress_lang_sync = False

    def _on_language_combo_changed(self, combo: Adw.ComboRow, _pspec: Any) -> None:
        idx = combo.get_selected()
        new_lang = "vi" if idx == 1 else "en"
        self.config_store.config.language = new_lang
        self.config_store.config.esp32_language = new_lang
        self.config_store.save()
        self._refresh_language()
        if self._suppress_lang_sync:
            return
        self._suppress_lang_sync = True
        try:
            if hasattr(self, "esp32_language_combo"):
                self.esp32_language_combo.set_selected(1 if new_lang == "en" else 0)
        finally:
            self._suppress_lang_sync = False
        self._last_server_language = new_lang
        self._run_bg(
            lambda: self.api_client.set_clock_language(new_lang),
            lambda r: None if r.ok else self.show_toast(r.error_message),
        )

    def _refresh_language(self) -> None:
        """Retranslate the parts of the shell that are safe to update in place."""
        is_vi = self.config_store.config.language == "vi"
        titles = {
            "dashboard": ("Giám sát hệ thống & Đo từ xa", "System Monitoring & Telemetry"),
            "station": ("Phần cứng & Trạm ESP32", "Hardware & ESP32 Station"),
            "spotify": ("Spotify & Âm nhạc", "Spotify & Media"),
            "alarms": ("Báo thức & Còi báo", "Alarms & Buzzer"),
            "tkb": ("Thời khóa biểu (TKB)", "Timetable (TKB)"),
            "calendar": ("Lịch Google (Calendar)", "Google Calendar"),
            "tools": ("Công cụ & Groq AI", "Tools & Groq AI"),
            "focus": ("Chế độ tập trung", "Focus Mode"),
            "settings": ("Cài đặt & Giao diện", "Settings & Appearance"),
        }
        sidebar = getattr(self, "sidebar_list", None)
        if sidebar is not None:
            index = 0
            while True:
                row = sidebar.get_row_at_index(index)
                if row is None:
                    break
                page_id = getattr(row, "page_id", None)
                label = getattr(row, "title_label", None)
                if page_id in titles and label is not None:
                    vi, en = titles[page_id]
                    label.set_text(vi if is_vi else en)
                index += 1
        if hasattr(self, "language_combo"):
            self.language_combo.set_subtitle(
                "Chọn ngôn ngữ giao diện giữa English và Tiếng Việt."
                if is_vi else "Choose interface language between English and Tiếng Việt."
            )

        # Tools tabs
        for tab_id, (vi_title, en_title) in getattr(self, "tools_tabs", {}).items():
            page = self.tools_stack.get_child_by_name(tab_id) if hasattr(self, "tools_stack") else None
            if page is not None:
                self.tools_stack.get_page(page).set_title(vi_title if is_vi else en_title)

        # OLED view cards
        for view_key, title_lbl, desc_lbl in getattr(self, "_view_card_labels", []):
            entry = next((v for v in self.DISPLAY_VIEWS if v[0] == view_key), None)
            if entry is None:
                continue
            _, vi_title, en_title, vi_desc, en_desc = entry
            title_lbl.set_text(vi_title if is_vi else en_title)
            desc_lbl.set_text(vi_desc if is_vi else en_desc)

        if hasattr(self, "active_view_row"):
            self.active_view_row.set_title(
                "Màn hình đang hoạt động trên trạm" if is_vi else "Currently active station screen"
            )
            self._update_active_view_badge()

        # Translate every remaining inline label in the window (Adw rows, entry
        # rows, buttons, labels) so switching to English really changes the UI.
        try:
            apply_to_widget_tree(self.get_content(), "vi" if is_vi else "en")
        except Exception as exc:  # pragma: no cover - GTK runtime only
            logger.debug("[i18n] Widget translation skipped: %s", exc)

        self.show_toast("Đã chuyển ngôn ngữ sang Tiếng Việt" if is_vi else "Language switched to English")

    def _on_esp32_language_changed(self, combo: Adw.ComboRow, _pspec: Any) -> None:
        if self._suppress_lang_sync:
            return
        lang = "en" if combo.get_selected() == 1 else "vi"
        self.config_store.config.esp32_language = lang
        self.config_store.config.language = lang
        self.config_store.save()
        self._suppress_lang_sync = True
        try:
            if hasattr(self, "language_combo"):
                self.language_combo.set_selected(1 if lang == "vi" else 0)
        finally:
            self._suppress_lang_sync = False
        self._refresh_language()
        self._last_server_language = lang
        self._run_bg(
            lambda: self.api_client.set_clock_language(lang),
            lambda r: self.show_toast(
                ("Đã đổi ngôn ngữ sang English" if lang == "en" else "Đã đổi ngôn ngữ sang Tiếng Việt")
                if r.ok else r.error_message
            ),
        )

    def _on_apply_esp32_theme_clicked(self, _btn: Gtk.Button) -> None:
        idx = self.esp32_theme_combo.get_selected()
        mode_map = {0: "off", 1: "always", 2: "custom"}
        mode = mode_map.get(idx, "off")
        s = self.dark_start_entry.get_text().strip() or "22:00"
        e = self.dark_end_entry.get_text().strip() or "06:00"
        self._run_bg(
            lambda: self.api_client.set_esp32_theme(mode, s, e),
            lambda r: self.show_toast("ESP32 Display theme updated!" if r.ok else r.error_message)
        )

    def _on_save_schedule_clicked(self, _btn: Gtk.Button) -> None:
        s_raw = self.dark_start_entry.get_text().strip()
        e_raw = self.dark_end_entry.get_text().strip()
        s_norm = ScheduledDarkModeEvaluator.normalize_time_str(s_raw, "20:00")
        e_norm = ScheduledDarkModeEvaluator.normalize_time_str(e_raw, "06:00")
        self.dark_start_entry.set_text(s_norm)
        self.dark_end_entry.set_text(e_norm)
        self.config_store.config.dark_mode_start = s_norm
        self.config_store.config.dark_mode_end = e_norm
        self.config_store.save()
        self.apply_theme_preference()
        self.show_toast(f"Dark mode schedule saved: {s_norm} → {e_norm}")

    def _on_save_api_url_clicked(self, _btn: Gtk.Button) -> None:
        url = self.api_url_entry.get_text().strip().rstrip("/")
        if not url.startswith("http"):
            self.show_toast("API URL must start with http:// or https://")
            return
        self.config_store.config.api_url = url
        self.config_store.save()
        self.title_widget.set_subtitle(self._connection_subtitle())
        self.pair_status_row.set_subtitle(self._connection_subtitle())
        self.show_toast(f"Saved API URL: {url}")

    def _on_test_health_clicked(self, _btn: Gtk.Button) -> None:
        self.health_row.set_subtitle("Testing connection...")

        def _done(res: ApiResult) -> None:
            if res.ok and isinstance(res.data, dict):
                svc = res.data.get("service", "deskflow")
                ver = res.data.get("version", "1.0.0")
                self.health_row.set_subtitle(
                    f"ONLINE • HTTP {res.status_code} • {res.latency_ms} ms • {svc} v{ver}"
                )
                self.show_toast(f"Backend healthy ({res.latency_ms} ms)")
            else:
                self.health_row.set_subtitle(
                    f"OFFLINE / ERROR • {res.error_code}: {res.error_message}"
                )
                self.show_toast(f"Health check failed: {res.error_message}")

        self._run_bg(self.api_client.check_health, _done)

    # =========================================================================
    # Global Refresh
    # =========================================================================
    def refresh_all_async(self) -> None:
        self.refresh_notes_async()
        self.refresh_weather_async()
        self.refresh_alarms_async()
        self.refresh_devices_async()
        self.refresh_tkb_async()
        self.refresh_calendar_async()

        def _fetch_all() -> Dict[str, Any]:
            return {
                "status": self.api_client.get_focus_status(),
                "system": self.api_client.get_system_status(),
                "countdowns": self.api_client.get_countdowns(),
                "spotify": self.api_client.get_spotify_status(),
                "timetable": self.api_client.get_timetable_today(),
            }

        def _done(results: Dict[str, Any]) -> None:
            self._render_countdowns_list(self.api_client.countdowns)

            st_res = results["status"]
            if st_res.ok and isinstance(st_res.data, dict):
                active = bool(st_res.data.get("focus_active") or st_res.data.get("enabled"))
                txt = "ACTIVE — Distraction Blocking Enabled" if active else "Idle — Ready"
                self.focus_state_row.set_subtitle(txt)
                self.focus_dash_row.set_subtitle(txt)
                self._set_focus_lock(active)

            sys_res = results["system"]
            if sys_res.ok and isinstance(sys_res.data, dict):
                data = sys_res.data.get("data") or {}
                station = data.get("station") or {}
                srv = data.get("server") or {}
                online = "ONLINE" if station.get("online") else "OFFLINE"
                view = station.get("current_view", "clock")
                temp = station.get("temp", "--")
                hum = station.get("humidity", "--")
                self.esp_status_row.set_subtitle(
                    f"{online} • View: {view} • Room: {temp}°C / {hum}%"
                )
                self.server_status_row.set_subtitle(
                    f"Server CPU: {srv.get('cpu', 0)}% • RAM: {srv.get('ram', 0)}% • Temp: {srv.get('temp', 0)}°C"
                )

            sp_res = results["spotify"]
            if sp_res.ok and isinstance(sp_res.data, dict):
                track = sp_res.data.get("track") or "No active track"
                artist = sp_res.data.get("artist") or ""
                playing = "Playing" if sp_res.data.get("is_playing") else "Paused"
                self.spotify_row.set_subtitle(f"{playing}: {track} — {artist}")

            tt_val = results["timetable"]
            if isinstance(tt_val, tuple):
                tt_res, tt_obj = tt_val
                if tt_res.ok and tt_obj:
                    self._apply_tkb_ui(tt_obj)
            elif isinstance(tt_val, ApiResult):
                tt_res = tt_val
                if tt_res.ok and isinstance(tt_res.data, dict):
                    today = (tt_res.data.get("data") or {}).get("today") or {}
                    day_lbl = today.get("day", "Today")
                    morn = ", ".join(today.get("morning") or []) or "None"
                    aft = ", ".join(today.get("afternoon") or []) or "None"
                    self.timetable_row.set_subtitle(f"{day_lbl} • AM: {morn} | PM: {aft}")

        self._run_bg(_fetch_all, _done)

    # -------------------------------------------------------------------------
    # Station & Hardware Helpers
    # -------------------------------------------------------------------------
    def _set_lamp_power(self, on: bool) -> None:
        self._lamp_on = on
        action = "on" if on else "off"
        self._run_bg(
            lambda: self.api_client.set_led(action),
            lambda r: self.show_toast(
                ("Lamp turned on." if on else "Lamp turned off.") if r.ok else r.error_message
            ),
        )

    def _on_lamp_brightness_changed(self, spin, _pspec) -> None:
        value = int(spin.get_value())
        self._run_bg(
            lambda: self.api_client.set_lamp_dim(value),
            lambda r: None if r.ok else self.show_toast(r.error_message),
        )

    def _apply_lamp_color(self, hex_color: str) -> None:
        self.lamp_hex_entry.set_text(hex_color)
        self._run_bg(
            lambda: self.api_client.set_lamp_color(hex_color),
            lambda r: self.show_toast(f"Lamp set to {hex_color}" if r.ok else r.error_message),
        )

    def _on_buzz_preset_clicked(self, duration_ms: int) -> None:
        self.buzzer_duration_spin.set_value(duration_ms)
        self._run_bg(
            lambda: self.api_client.trigger_buzz(duration_ms),
            lambda r: self.show_toast(f"Beep ({duration_ms}ms) sent to station!" if r.ok else r.error_message),
        )

    # -------------------------------------------------------------------------
    # Linked Devices Management & Dynamic Display Selection (Phase 2)
    # -------------------------------------------------------------------------
    def refresh_devices_async(self) -> None:
        def _fetch() -> Dict[str, ApiResult]:
            return {
                "devices": self.api_client.get_paired_devices(),
                "selected": self.api_client.get_selected_display_device(),
            }

        def _done(results: Dict[str, ApiResult]) -> None:
            dev_res = results["devices"]
            sel_res = results["selected"]
            devices: List[Dict[str, Any]] = []
            if dev_res.ok and isinstance(dev_res.data, dict):
                devices = dev_res.data.get("devices", [])
            selected_id: Optional[str] = None
            if sel_res.ok and isinstance(sel_res.data, dict):
                selected_id = sel_res.data.get("selected_device_id")
            self._render_devices_list(devices, selected_id)

        self._run_bg(_fetch, _done)

    def _render_devices_list(self, devices: List[Dict[str, Any]], selected_device_id: Optional[str]) -> None:
        child = self.devices_list_box.get_first_child()
        while child is not None:
            nxt = child.get_next_sibling()
            self.devices_list_box.remove(child)
            child = nxt

        dev_names = [f"{d.get('device_name', 'Device')} ({d.get('platform', 'unknown')})" for d in devices]
        if not dev_names:
            dev_names = ["(No linked devices)"]
        model = Gtk.StringList.new(dev_names)
        self.display_device_combo.set_model(model)
        self._devices_cache = list(devices)
        sel_idx = next((i for i, d in enumerate(devices) if d.get("device_id") == selected_device_id), 0)
        self.display_device_combo.set_selected(sel_idx)

        if not devices:
            empty_row = Adw.ActionRow(
                title="No Linked Devices",
                subtitle="Generate a pairing code in Discord (/pair) to connect devices.",
            )
            self.devices_list_box.append(empty_row)
            return

        for dev in devices:
            did = str(dev.get("device_id", ""))
            dname = str(dev.get("device_name", "Device"))
            plat = str(dev.get("platform", "unknown"))
            is_cur = bool(dev.get("is_current", False))

            title_txt = f"{dname} {'★ Current Device' if is_cur else ''}"
            row = Adw.ActionRow(
                title=title_txt,
                subtitle=f"Platform: {plat} • ID: {did}",
            )

            rename_btn = Gtk.Button(label="Rename", valign=Gtk.Align.CENTER)
            rename_btn.connect("clicked", lambda _b, dev_id=did, cur_name=dname: self._show_rename_dialog(dev_id, cur_name))
            row.add_suffix(rename_btn)

            unlink_btn = Gtk.Button(label="Unlink", valign=Gtk.Align.CENTER)
            unlink_btn.add_css_class("destructive-action")
            unlink_btn.connect("clicked", lambda _b, dev_id=did, name=dname: self._show_unlink_confirm_dialog(dev_id, name))
            row.add_suffix(unlink_btn)

            self.devices_list_box.append(row)

    def _show_rename_dialog(self, device_id: str, current_name: str) -> None:
        dialog = Adw.MessageDialog.new(self, "Rename Device", f"Enter a new display name for '{current_name}':")
        dialog.add_response("cancel", "Cancel")
        dialog.add_response("rename", "Rename")
        dialog.set_response_appearance("rename", Adw.ResponseAppearance.SUGGESTED)

        entry = Gtk.Entry()
        entry.set_text(current_name)
        entry.set_margin_start(24)
        entry.set_margin_end(24)
        entry.set_margin_top(12)
        dialog.set_extra_child(entry)

        def _on_response(_d: Adw.MessageDialog, response: str) -> None:
            if response == "rename":
                new_name = entry.get_text().strip()
                if new_name:
                    self._run_bg(
                        lambda: self.api_client.rename_device(device_id, new_name),
                        lambda r: (self.show_toast(f"Device renamed to {new_name}!" if r.ok else r.error_message), self.refresh_devices_async())
                    )

        dialog.connect("response", _on_response)
        dialog.present()

    def _show_unlink_confirm_dialog(self, device_id: str, device_name: str) -> None:
        dialog = Adw.MessageDialog.new(
            self,
            "Unlink Device?",
            f"Are you sure you want to remove and revoke access for '{device_name}'? It will no longer be able to communicate with DeskFlow until paired again.",
        )
        dialog.add_response("cancel", "Cancel")
        dialog.add_response("unlink", "Unlink")
        dialog.set_response_appearance("unlink", Adw.ResponseAppearance.DESTRUCTIVE)

        def _on_response(_d: Adw.MessageDialog, response: str) -> None:
            if response == "unlink":
                def _done(res: ApiResult) -> None:
                    if res.ok:
                        self.show_toast(f"'{device_name}' unlinked.")
                        self.refresh_devices_async()
                    else:
                        self.show_toast(f"Unlink failed: {res.error_message}")

                self._run_bg(lambda: self.api_client.unpair_device(device_id), _done)

        dialog.connect("response", _on_response)
        dialog.present()

    def _on_display_device_combo_changed(self, combo: Adw.ComboRow, _pspec: Any) -> None:
        idx = combo.get_selected()
        if hasattr(self, "_devices_cache") and 0 <= idx < len(self._devices_cache):
            target_id = self._devices_cache[idx].get("device_id")
            if target_id:
                self._run_bg(
                    lambda: self.api_client.set_selected_display_device(target_id),
                    lambda r: self.show_toast("Current display device updated!" if r.ok else r.error_message)
                )
