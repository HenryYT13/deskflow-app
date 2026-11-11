# DeskFlow macOS — Native Swift + SwiftUI Desktop Application

Native macOS desktop application for the DeskFlow ecosystem built with **Swift 5.9 + SwiftUI** targeting macOS 13.0+.

## Features
- `NavigationSplitView` sidebar with native macOS toolbar and menu bar commands (`⌘R` Refresh, `⇧⌘T` Health Check).
- Full feature parity: Pairing, Dashboard, Focus Mode & Math Challenge, Sticky Notes (with `.alert` delete confirmation and authoritative backend sync), Countdowns, 24h Weather Forecast, Bidirectional Morse Converter + ESP32 Buzzer playback, ESP32 OLED & Clock Studio, and **Scheduled Dark Mode** (`20:00–06:00` overnight & same-day ranges).

## Installation & Gatekeeper Guide
See [macOS Installation & Gatekeeper Guide](../../docs/MACOS_INSTALL.md) for step-by-step instructions on installing the app and using macOS's built-in **Open Anyway** authorization.

## Building & Testing (macOS with Xcode 15+)
```bash
cd macos
swift test
./scripts/build_macos_dmg.sh
./scripts/verify_macos_bundle.sh
```
