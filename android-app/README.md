# DeskFlow Android — Material You 3 Primary Control Center

Primary mobile control center for the DeskFlow ecosystem built with **Kotlin + Jetpack Compose + Material 3**.

## Key Features
- **Material You 3 Design System**: Complete Light, Dark, Dynamic Color (Android 12+), and **Scheduled Dark Mode** (`system`, `light`, `dark`, `scheduled` with configurable `HH:MM` start/end times and overnight window support).
- **Navigation Architecture**: Material 3 `NavigationBar` with 5 primary tabs (`Focus`, `Control`, `Home`, `Spotify`, `Timetable`) + TopBar Screen Switcher (`ModalBottomSheet`) for quick access to all 10 screens (`Dashboard`, `Focus`, `Control`, `Spotify`, `Timetable`, `Morse`, `ClockStyle`, `Activity`, `Settings`, `Home`). The redundant global bottom-right screen-switcher FAB (`Icons.Default.GridView`) has been completely removed.
- **Authoritative Notes & Countdowns CRUD**: Confirmation dialog before deletion, duplicate in-flight request protection, and authoritative backend HTTP 200 sync (including clearing local state when deleting the last note).
- **Morse Code Converter & Hardware Playback**: Bidirectional ITU Morse code converter with live ESP32 buzzer playback.

## Building & Testing
```bash
cd android-app
./gradlew testDebugUnitTest assembleDebug
```
Output APK: `artifacts/android/DeskFlow-debug.apk`
