# DeskFlow ESP32 Station Firmware (`khkt.ino`)

Arduino C++ firmware for the ESP32-WROOM-32 DeskFlow Station (`SSD1306 128×64 OLED`, `DHT11`, `WS2812B LED Ring`, `Touch TTP223`, `Buzzer GPIO26`).

## Compiling with Arduino CLI
```bash
arduino-cli compile --fqbn esp32:esp32:esp32 firmware/khkt --output-dir artifacts/firmware
```
Output binaries: `artifacts/firmware/khkt.ino.bin`, `khkt.ino.elf`, `khkt.ino.merged.bin`
