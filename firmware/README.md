# DeskFlow ESP32 Station Firmware (`khkt.ino`)

Arduino C++ firmware for the ESP32-WROOM-32 DeskFlow Station (`ST7789 240×320 TFT`, `WS2812B LED ring on GPIO2`, `3-button panel`, `Buzzer GPIO32`).

## Files
| File | Purpose |
|---|---|
| `khkt.ino` | Main sketch: Wi-Fi/MQTT, display, menus, HTTP portal |
| `clock_styles.cpp/.h` | Seven clock faces + config persistence |
| `melody.h` | Non-blocking MIDI jingle player |
| `alarm_tune.cpp/.h` | Dedicated "DeskFlow Alert" wake-up alarm tune |
| `i18n.cpp/.h` | Vietnamese / English runtime switch for all station screens |

## Compiling with Arduino CLI
```bash
arduino-cli compile --fqbn esp32:esp32:esp32 firmware/khkt --output-dir artifacts/firmware
```
Output binaries: `artifacts/firmware/khkt.ino.bin`, `khkt.ino.elf`, `khkt.ino.merged.bin`

## MIDI melody playback (`melody.h`)
Minimal non-blocking monophonic SMF player on the buzzer pin (`BUZZER_PIN`). Supports format 0/1 files with PPQ timing, tempo meta events, up to 128 notes (first track containing notes; channel 10/drums ignored). Respects the buzzer mute setting.

- Sample melody is embedded as `SAMPLE_MELODY_MID[]` (PROGMEM). To use your own, convert a `.mid` with `xxd -i song.mid` and replace the array (or pass another array to `melodyStart(data, len)`).
- MQTT topic `deskflow/alarm`:
  - `{"action":"melody_play"}` starts the melody
  - `{"action":"melody_stop"}` stops it (`stop_alarm` also stops it)
  - `{"action":"morse_play","pattern":".-","unit_ms":60,"freq":1000}` (defaults 60 ms / 1000 Hz)

## Alarm tune (`alarm_tune.cpp`)
`math_alarm_trigger` plays a dedicated two-phrase motif instead of the old 2600 Hz
beep, and strobes the WS2812B ring red. It is non-blocking (`alarmTuneUpdate()` from
`loop()`), loops until `stop_alarm`, and respects the buzzer mute setting (the LED
strobe keeps working while muted).

## Station menus (hold NEXT to open, hold again to leave)
Main list: LED ring on/off · LED brightness (0-100%) · Buzzer · Spotify volume ·
Dark mode · Language (VI/EN). `PREV`/`NEXT` change the value, `PLAY` confirms.

## LED control
MQTT `deskflow/display` → `{"action":"led","act":"on"|"off"|"toggle"|"blink"}` and
`{"action":"dim","led_bright":0-100}`. Screen brightness was removed: the ST7789
backlight is hard-wired to 3.3 V.

## HTTP portal
`http://<station-ip>/` now exposes LED on/off, a 0-100% brightness slider, Spotify
volume +/-5% (relayed to the backend) and a VI/EN switch, alongside the live screen
mirror, telemetry and Wi-Fi manager.
- Backend hook: `alarm_engine.play_melody()` / `stop_melody()` publish these commands.
- Parser was verified on a host build; firmware itself not compiled (no arduino-cli available).
