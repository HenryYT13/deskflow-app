# DeskFlow Linux — Native GTK4 + Libadwaita Desktop Application

Native GNOME/Linux desktop control center for the DeskFlow ecosystem built with **Python 3 + PyGObject (`Gtk 4.0` & `Adw 1`)**.

## Features
- **Device Pairing & Auth**: Pair via 6-digit code (`POST /api/app/pair`), persistent credentials in `~/.config/deskflow/settings.json`.
- **Dashboard & Live Telemetry**: Real-time ESP32 Station, Server, Laptop, and Focus Mode status.
- **Focus Mode & Math Challenge**: Start timed sessions, request and verify arithmetic unlock challenges.
- **Sticky Notes (Authoritative CRUD)**: Create, pin/unpin to ESP32 OLED, and delete notes with confirmation dialog (`Adw.AlertDialog`) and authoritative backend confirmation.
- **Exam Countdowns**: Create, pin, and delete countdowns.
- **24-Hour Weather Forecast**: 3-hour interval forecast with rain probability and rain alerts.
- **Morse Code Studio**: Bidirectional Text ↔ Morse converter (with Vietnamese diacritic support) and ESP32 hardware buzzer playback.
- **ESP32 Station & Media**: Switch all 9 OLED views, Clock Style Studio (styles 1–7), WS2812B LED ring control, Spotify & Timetable status.
- **Scheduled Dark Mode**: Supports `System`, `Light`, `Dark`, and `Scheduled` (`HH:MM` start/end window including overnight ranges like `20:00–06:00`) via `Adw.StyleManager`.

## Running & Testing
```bash
# Run unit & integration tests
python3 -m unittest discover -s linux/tests -v

# Run GUI self-test (verifies all 8 GTK4 + Libadwaita pages)
./linux/bin/deskflow --self-test

# Launch GUI application
./linux/bin/deskflow

# Build .deb, .rpm, .AppImage, and .tar.gz packages into artifacts/linux/
./linux/packaging/build_packages.sh
```
