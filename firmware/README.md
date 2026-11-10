# DeskFlow ESP32 Station Firmware (`khkt.ino`)

Arduino C++ firmware for the ESP32-WROOM-32 DeskFlow Station (`SSD1306 128×64 OLED`, `DHT11`, `WS2812B LED Ring`, `Touch TTP223`, `Buzzer GPIO26`).

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
- Backend hook: `alarm_engine.play_melody()` / `stop_melody()` publish these commands.
- Parser was verified on a host build; firmware itself not compiled (no arduino-cli available).
