#include <WiFi.h>
#include <WebServer.h>
#include <PubSubClient.h>
#include <Preferences.h>
#include <ArduinoJson.h>
#include <time.h>

// Thanh ghi ngắt Brownout để chống crash khi tụt áp
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

// ================= THƯ VIỆN MÀN HÌNH ST7789 SPI =================
#include <SPI.h>
#include <Adafruit_GFX.h>
#include <Adafruit_ST7789.h>
#include "clock_styles.h"
#include "melody.h"

#define TFT_CS   21
#define TFT_DC   19
#define TFT_RST  4
#define TFT_MOSI 23
#define TFT_SCLK 18

Adafruit_ST7789* tft = nullptr;

#define PIN_BATTERY 34
#define BTN_PLAY    14
#define BTN_NEXT    27
#define BTN_PREV    26
#define LED_PIN     12
#define BUZZER_PIN  32

// Dynamic Theme Color Palette RGB565 (Initialized to Dark Mode)
uint16_t themeBg     = 0x0863;  // #0b0f19
uint16_t themeCard   = 0x1106;  // #151f32
uint16_t themeBorder = 0x218A;  // #23334d
uint16_t themeText   = 0xFFFF;  // #ffffff
uint16_t themeMuted  = 0x94F2;  // #94a3b8
uint16_t themeCyan   = 0x3DFE;  // #38bdf8
uint16_t themeGreen  = 0x15F0;  // #10b981
uint16_t themeAmber  = 0xFD20;  // #f59e0b
uint16_t themePurple = 0xA2FA;  // #a855f7

// Dark Mode Settings & State
String darkModeSetting = "off"; // "off", "always_on", "scheduled"
String darkModeStart = "22:00";
String darkModeEnd = "06:00";
bool lastEffectiveDarkState = true;

// Hardware Configuration (LED, Buzzer & Versioning)
int appliedHardwareConfigVersion = 0;
int ledBrightness = 100; // 0 - 100%
bool buzzerEnabled = true;
unsigned int buzzerDurationSetting = 40; // ms

// Hardware Settings Menu State Machine
bool inSettingsMenu = false;
enum MenuState {
  MENU_MAIN = 0,
  MENU_ADJUST_LED = 1,
  MENU_BUZZER_CONFIG = 2
};
MenuState menuState = MENU_MAIN;
int menuCurrentItem = 0;
const int MENU_MAIN_ITEM_COUNT = 4;
int buzzerCurrentItem = 0;
const int MENU_BUZZER_ITEM_COUNT = 4;

// View Selector Menu State Machine
bool inViewMenu = false;
bool viewMenuNeedsRedraw = false;
int viewMenuIndex = 0;
const int VIEW_MENU_COUNT = 9;
struct ViewMenuItem {
  const char* id;
  const char* title;
};
const ViewMenuItem VIEW_MENU_ITEMS[VIEW_MENU_COUNT] = {
  { "main",      "1. Tong quan" },
  { "stats",     "2. Thong so may" },
  { "tkb",       "3. Thoi khoa bieu" },
  { "weather",   "4. Thoi tiet" },
  { "lyrics",    "5. Loi bai hat" },
  { "notes",     "6. Ghi chu" },
  { "countdown", "7. Dem nguoc" },
  { "focus",     "8. Tap trung" },
  { "clock",     "9. Dong ho" }
};

// Dedicated Notes Storage
struct NoteItem {
  String id;
  String title;
  String content;
  bool isPinned = false;
};
const int MAX_STATION_NOTES = 10;
NoteItem stationNotes[MAX_STATION_NOTES];
int stationNotesCount = 0;
int activeNoteIndex = 0;

// Dedicated Exam Countdown Storage
struct CountdownItem {
  String id;
  String title;
  String targetDate;
  int daysLeft = 0;
  bool isExpired = false;
  bool isPinned = false;
};
const int MAX_STATION_COUNTDOWNS = 10;
CountdownItem stationCountdowns[MAX_STATION_COUNTDOWNS];
int stationCountdownsCount = 0;
int activeCountdownIndex = 0;

Preferences prefs;
WebServer server(80);
WiFiClient espClient;
PubSubClient mqtt(espClient);

String wifiListJson = "[]";
String mqttBroker = "10th012013.duckdns.org";
int mqttPort = 1883;

// Quản lý Buzzer thường
bool isBuzzing = false;
unsigned long buzzStartTime = 0;
unsigned long buzzDuration = 250;

// Quản lý Báo thức liên tục (Math Alarm)
bool isAlarmActive = false;
unsigned long lastAlarmBeepTime = 0;
bool alarmBeepState = false;
String currentMathQuestion = "";

bool ledState = false;
bool ledBlinkMode = false;
unsigned long lastLedBlinkTime = 0;
const unsigned long LED_BLINK_INTERVAL = 150;

struct ServerStats {
  float cpu = 0.0;
  float ram_pct = 0.0;
  int ram_used = 0;
  int ram_total = 0;
  String uptime = "0h 0m";

  float lp_cpu = 0.0;
  float lp_ram = 0.0;
  int lp_bat = 0;

  bool internet = false;
  String activeView = "main";
  String currentTrack = "Chua phat nhac";
  String currentArtist = "N/A";
  int progressPct = 0;
  String lyricCurr = "";
  String lyricNext = "";
  String weatherText = "28*C";
  String weatherCity = "Ha Long";
  String quoteText = "Kien tri hom nay, thanh cong ngay mai!";

  String tkbDay = "Hom nay";
  String tkbMorning = "Toan, Van, Anh";
  bool tkbHasAfternoon = false;
  String tkbAfternoon = "";
  String tkbUniform = "Ao trang";

  String tkbTomDay = "Ngay mai";
  String tkbTomMorning = "Van, Dia, GDCD, Mi thuat, Anh";
  bool tkbTomHasAfternoon = false;
  String tkbTomAfternoon = "";
  String tkbTomUniform = "Trang";

  String stickyNote = "";
  String stickyNoteId = "";
  String countdownName = "";
  int countdownDays = -1;
  bool hasCountdown = false;
  String countdownId = "";
  String lampColor = "#FFFFFF";
} srvStats;

String serverLogs = "[SYSTEM] DeskFlow Station Ready.\n";
bool screenNeedUpdate = true;
String lastRenderedView = "";

// Focus Mode authoritative state from deskflow/focus/status (Display-Only)
bool focusEnabled = false;
bool focusActive = false;
String focusPhase = "IDLE";
String focusSessionType = "timed";
long focusRemainingSeconds = 0;
long focusTotalSeconds = 0;
int focusCycleCount = 0;
unsigned long focusStatusReceivedMillis = 0;
void drawFocusView();

String removeAccents(String str) {
  bool isPureAscii = true;
  for (size_t i = 0; i < str.length(); i++) {
    if ((uint8_t)str[i] >= 0x80) {
      isPureAscii = false;
      break;
    }
  }
  if (isPureAscii) return str;

  String out = "";
  size_t len = str.length();
  for (size_t i = 0; i < len; i++) {
    uint8_t c1 = (uint8_t)str[i];
    if (c1 < 0x80) {
      out += (char)c1;
    } else if (c1 == 0xC3 && i + 1 < len) {
      uint8_t c2 = (uint8_t)str[++i];
      if ((c2 >= 0xA0 && c2 <= 0xA5)) out += 'a';
      else if ((c2 >= 0x80 && c2 <= 0x85)) out += 'A';
      else if ((c2 >= 0xA8 && c2 <= 0xAB)) out += 'e';
      else if ((c2 >= 0x88 && c2 <= 0x8B)) out += 'E';
      else if ((c2 >= 0xAC && c2 <= 0xAF)) out += 'i';
      else if ((c2 >= 0x8C && c2 <= 0x8F)) out += 'I';
      else if ((c2 >= 0xB2 && c2 <= 0xB6)) out += 'o';
      else if ((c2 >= 0x92 && c2 <= 0x96)) out += 'O';
      else if ((c2 >= 0xB9 && c2 <= 0xBC)) out += 'u';
      else if ((c2 >= 0x99 && c2 <= 0x9C)) out += 'U';
      else if (c2 == 0xBD) out += 'y';
      else if (c2 == 0x9D) out += 'Y';
      else out += ' ';
    } else if (c1 == 0xC4 && i + 1 < len) {
      uint8_t c2 = (uint8_t)str[++i];
      if (c2 == 0x90) out += 'D';
      else if (c2 == 0x91) out += 'd';
      else out += ' ';
    } else if (c1 == 0xE1 && i + 2 < len) {
      uint8_t c2 = (uint8_t)str[++i];
      uint8_t c3 = (uint8_t)str[++i];
      if (c2 == 0xBB) {
        if (c3 >= 0x88 && c3 <= 0x8D) out += 'i';
        else if (c3 >= 0xB0 && c3 <= 0xB7) out += 'u';
        else if (c3 >= 0xB8 && c3 <= 0xBF) out += 'y';
        else out += ' ';
      } else {
        out += ' ';
      }
    }
  }
  return out;
}

void printCenteredText(String text, int y, int size, uint16_t color, uint16_t bg) {
  if (!tft) return;
  text = removeAccents(text);
  int charW = 6 * size;
  int textW = (int)text.length() * charW;
  int x = (240 - textW) / 2;
  if (x < 0) x = 0;

  tft->setTextSize(size);
  tft->setTextColor(color, bg);
  tft->setCursor(x, y);
  tft->print(text);
}

void printCenteredText(String text, int y, int size, uint16_t color) {
  printCenteredText(text, y, size, color, themeBg);
}

void printBigWrappedCenteredText(String text, int centerY, int size, uint16_t color, int maxCharsPerLine, int lineSpacing, uint16_t bg) {
  if (!tft) return;
  text = removeAccents(text);
  text.trim();

  int wordCount = 0;
  String words[45];
  int lastIdx = 0;
  for (size_t i = 0; i <= text.length(); i++) {
    if (i == text.length() || text[i] == ' ') {
      if (i > lastIdx && wordCount < 45) {
        words[wordCount++] = text.substring(lastIdx, i);
      }
      lastIdx = i + 1;
    }
  }

  String lines[6];
  int lineCount = 0;
  String curLine = "";

  for (int i = 0; i < wordCount; i++) {
    String testLine = (curLine.length() == 0) ? words[i] : (curLine + " " + words[i]);
    if (testLine.length() <= maxCharsPerLine) {
      curLine = testLine;
    } else {
      if (curLine.length() > 0 && lineCount < 6) {
        lines[lineCount++] = curLine;
      }
      curLine = words[i];
    }
  }
  if (curLine.length() > 0 && lineCount < 6) {
    lines[lineCount++] = curLine;
  }

  int charH = (size == 1) ? 8 : (size * 8);
  int totalH = (lineCount > 0) ? ((lineCount - 1) * lineSpacing + charH) : 0;
  int startY = centerY - (totalH / 2);

  for (int i = 0; i < lineCount; i++) {
    printCenteredText(lines[i], startY + (i * lineSpacing), size, color, bg);
  }
}

void printBigWrappedCenteredText(String text, int centerY, int size, uint16_t color, int maxCharsPerLine = 17, int lineSpacing = 20) {
  printBigWrappedCenteredText(text, centerY, size, color, maxCharsPerLine, lineSpacing, themeBg);
}

void printBigWrappedTopCenteredText(String text, int startY, int size, uint16_t color, int maxCharsPerLine, int lineSpacing, uint16_t bg) {
  if (!tft) return;
  text = removeAccents(text);
  text.trim();

  int wordCount = 0;
  String words[45];
  int lastIdx = 0;
  for (size_t i = 0; i <= text.length(); i++) {
    if (i == text.length() || text[i] == ' ') {
      if (i > lastIdx && wordCount < 45) {
        words[wordCount++] = text.substring(lastIdx, i);
      }
      lastIdx = i + 1;
    }
  }

  String lines[6];
  int lineCount = 0;
  String curLine = "";

  for (int i = 0; i < wordCount; i++) {
    String testLine = (curLine.length() == 0) ? words[i] : (curLine + " " + words[i]);
    if (testLine.length() <= maxCharsPerLine) {
      curLine = testLine;
    } else {
      if (curLine.length() > 0 && lineCount < 6) {
        lines[lineCount++] = curLine;
      }
      curLine = words[i];
    }
  }
  if (curLine.length() > 0 && lineCount < 6) {
    lines[lineCount++] = curLine;
  }

  for (int i = 0; i < lineCount; i++) {
    printCenteredText(lines[i], startY + (i * lineSpacing), size, color, bg);
  }
}

void printBigWrappedTopCenteredText(String text, int startY, int size, uint16_t color, int maxCharsPerLine = 17, int lineSpacing = 20) {
  printBigWrappedTopCenteredText(text, startY, size, color, maxCharsPerLine, lineSpacing, themeBg);
}

void drawProgressBarSmooth(int x, int y, int w, int h, int pct, uint16_t fgCol) {
  tft->fillRoundRect(x, y, w, h, h / 2, COL_CARD);
  tft->drawRoundRect(x, y, w, h, h / 2, COL_BORDER);
  int fillW = (w * constrain(pct, 0, 100)) / 100;
  if (fillW >= h) {
    tft->fillRoundRect(x, y, fillW, h, h / 2, fgCol);
  } else if (fillW > 0) {
    tft->fillRoundRect(x, y, h, h, h / 2, fgCol);
  }
}

void drawWeatherWebIcon(int cx, int cy) {
  tft->fillCircle(cx + 16, cy - 8, 14, 0xFD20);
  tft->fillCircle(cx + 16, cy - 8, 11, 0xFFE0);
  tft->fillCircle(cx - 18, cy + 10, 16, 0xFFFF);
  tft->fillCircle(cx + 4, cy + 2, 22, 0xFFFF);
  tft->fillCircle(cx + 26, cy + 12, 14, 0xFFFF);
  tft->fillRoundRect(cx - 18, cy + 10, 46, 18, 4, 0xFFFF);
}

void addLog(String tag, String msg) {
  unsigned long s = millis() / 1000;
  char buf[16];
  sprintf(buf, "[%02d:%02d] ", (int)(s / 60), (int)(s % 60));
  String line = String(buf) + "[" + tag + "] " + msg + "\n";
  serverLogs += line;
  if (serverLogs.length() > 4500) serverLogs = serverLogs.substring(2000);
  Serial.print(line);
}

void initBuzzer() {
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, HIGH);
}

void triggerBuzz(unsigned long durationMs = 250, bool force = false) {
  if (!buzzerEnabled && !force) return;
  isBuzzing = true;
  buzzStartTime = millis();
  buzzDuration = durationMs;
  tone(BUZZER_PIN, 2400);
  addLog("BUZZER", ">>> BUZZER TRIGGERED: " + String(durationMs) + "ms");
}

void stopBuzzer() {
  noTone(BUZZER_PIN);
  digitalWrite(BUZZER_PIN, HIGH);
}

// ================= NON-BLOCKING MORSE PLAYER =================
String morseSequence = "";
int morseSeqIndex = 0;
bool isMorsePlaying = false;
bool morseToneActive = false;
unsigned long morseStepStartTime = 0;
unsigned long morseStepDuration = 0;
unsigned int morseUnitMs = 80;
unsigned int morseFrequency = 2400;

void stopMorsePlayback() {
  if (isMorsePlaying || morseToneActive) {
    isMorsePlaying = false;
    morseToneActive = false;
    morseSequence = "";
    morseSeqIndex = 0;
    noTone(BUZZER_PIN);
    digitalWrite(BUZZER_PIN, HIGH);
    addLog("MORSE", "Mã Morse: Đã dừng phát.");
  }
}

void startMorsePlayback(const String& pattern, unsigned int unitMs = 80, unsigned int freq = 2400) {
  stopMorsePlayback();
  if (pattern.length() == 0) return;

  // Safe limits: max 500 chars, unitMs 20-500ms
  morseSequence = pattern.substring(0, 500);
  morseSeqIndex = 0;
  morseUnitMs = constrain(unitMs, 20, 500);
  morseFrequency = constrain(freq, 400, 4000);
  isMorsePlaying = true;
  morseToneActive = false;
  morseStepStartTime = millis();
  morseStepDuration = 0;
  addLog("MORSE", "Mã Morse: Bắt đầu phát (" + String(morseSequence.length()) + " ký tự, " + String(morseUnitMs) + "ms/unit)");
}

void updateMorsePlayback() {
  if (!isMorsePlaying) return;

  if (isAlarmActive) {
    stopMorsePlayback();
    return;
  }

  unsigned long now = millis();
  if (now - morseStepStartTime < morseStepDuration) {
    return;
  }

  if (morseToneActive) {
    noTone(BUZZER_PIN);
    digitalWrite(BUZZER_PIN, HIGH);
    morseToneActive = false;
    morseStepStartTime = now;
    morseStepDuration = morseUnitMs; // 1 unit gap between elements
    return;
  }

  while (morseSeqIndex < morseSequence.length()) {
    char c = morseSequence.charAt(morseSeqIndex++);
    if (c == '.') {
      morseToneActive = true;
      morseStepStartTime = now;
      morseStepDuration = morseUnitMs;
      if (buzzerEnabled) tone(BUZZER_PIN, morseFrequency);
      return;
    } else if (c == '-') {
      morseToneActive = true;
      morseStepStartTime = now;
      morseStepDuration = morseUnitMs * 3;
      if (buzzerEnabled) tone(BUZZER_PIN, morseFrequency);
      return;
    } else if (c == ' ') {
      morseStepStartTime = now;
      morseStepDuration = morseUnitMs * 2;
      return;
    } else if (c == '/' || c == '|') {
      morseStepStartTime = now;
      morseStepDuration = morseUnitMs * 6;
      return;
    }
  }

  stopMorsePlayback();
  addLog("MORSE", "Mã Morse: Đã phát xong chuỗi.");
}

bool isDarkModeActive() {
  if (darkModeSetting == "always" || darkModeSetting == "always_on") {
    return true;
  }
  if (darkModeSetting == "off") {
    return false;
  }
  if (darkModeSetting == "scheduled" || darkModeSetting == "custom") {
    struct tm tmInfo;
    if (!getLocalTime(&tmInfo, 20) || tmInfo.tm_year < (2020 - 1900)) {
      return false;
    }
    int curMinutes = tmInfo.tm_hour * 60 + tmInfo.tm_min;

    int startH = 22, startM = 0;
    int endH = 6, endM = 0;
    if (sscanf(darkModeStart.c_str(), "%d:%d", &startH, &startM) < 2) {
      startH = 22; startM = 0;
    }
    if (sscanf(darkModeEnd.c_str(), "%d:%d", &endH, &endM) < 2) {
      endH = 6; endM = 0;
    }
    startH = constrain(startH, 0, 23);
    startM = constrain(startM, 0, 59);
    endH = constrain(endH, 0, 23);
    endM = constrain(endM, 0, 59);

    int startMinutes = startH * 60 + startM;
    int endMinutes = endH * 60 + endM;

    if (startMinutes == endMinutes) {
      // Safe, predictable behavior for start == end (0-minute span): inactive (Light mode)
      return false;
    } else if (startMinutes < endMinutes) {
      // Daytime span within single day (e.g. 06:30 to 18:30 or 20:00 to 23:00)
      return (curMinutes >= startMinutes && curMinutes < endMinutes);
    } else {
      // Overnight span crossing midnight (e.g. 22:00 to 06:00)
      return (curMinutes >= startMinutes || curMinutes < endMinutes);
    }
  }
  return false;
}

void updateThemePalette() {
  bool active = isDarkModeActive();
  if (active) {
    themeBg = 0x0863;
    themeCard = 0x1106;
    themeBorder = 0x218A;
    themeText = 0xFFFF;
    themeMuted = 0x94F2;
    themeCyan = 0x3DFE;
    themeGreen = 0x15F0;
    themeAmber = 0xFD20;
    themePurple = 0xA2FA;
  } else {
    themeBg = 0xFFFF;
    themeCard = 0xEF7D;
    themeBorder = 0xC618;
    themeText = 0x0863;
    themeMuted = 0x632C;
    themeCyan = 0x03B9;
    themeGreen = 0x0D69;
    themeAmber = 0xD3A0;
    themePurple = 0x7996;
  }
  if (active != lastEffectiveDarkState) {
    lastEffectiveDarkState = active;
    lastRenderedView = "";
    screenNeedUpdate = true;
  }
}

void sendHardwareSettingsAck(int version, const String& status, const String& err = "") {
  DynamicJsonDocument doc(256);
  doc["action"] = "hardware_settings_ack";
  doc["hardware_config_version"] = version;
  doc["status"] = status;
  if (err.length() > 0) {
    doc["error"] = err;
  } else {
    doc["error"] = nullptr;
  }
  String out;
  serializeJson(doc, out);
  mqtt.publish("deskflow/status_update", out.c_str());
  mqtt.publish("deskflow/hardware_settings_ack", out.c_str());
  addLog("HW_ACK", out);
}

void sendHardwareSettingsRequest() {
  DynamicJsonDocument doc(256);
  doc["action"] = "hardware_settings_request";
  doc["device_id"] = "DeskFlow-ESP32";
  doc["current_version"] = appliedHardwareConfigVersion;
  String out;
  serializeJson(doc, out);
  mqtt.publish("deskflow/status_update", out.c_str());
  mqtt.publish("deskflow/hardware_settings_request", out.c_str());
  addLog("HW_REQ", "Yeu cau cau hinh phan cung moi nhat: " + out);
}

void publishHardwareSettingsUpdate() {
  DynamicJsonDocument doc(256);
  doc["action"] = "hardware_settings_update";
  doc["device_id"] = "DeskFlow-ESP32";
  doc["base_version"] = appliedHardwareConfigVersion;
  doc["led_brightness"] = ledBrightness;
  doc["buzzer_enabled"] = buzzerEnabled;
  doc["buzzer_duration"] = buzzerDurationSetting;
  doc["darkmode_mode"] = darkModeSetting;
  doc["darkmode_start"] = darkModeStart;
  doc["darkmode_end"] = darkModeEnd;
  String out;
  serializeJson(doc, out);
  mqtt.publish("deskflow/status_update", out.c_str());
  addLog("HW", "Gui yeu cau cap nhat cai dat phan cung: " + out);
}

void cycleNextNote() {
  if (stationNotesCount <= 0) return;
  activeNoteIndex = (activeNoteIndex + 1) % stationNotesCount;
  screenNeedUpdate = true;
  triggerBuzz(25);
  addLog("NOTE", "Xem ghi chu tiep theo: " + String(activeNoteIndex + 1) + "/" + String(stationNotesCount));
}

void cyclePrevNote() {
  if (stationNotesCount <= 0) return;
  activeNoteIndex = (activeNoteIndex - 1 + stationNotesCount) % stationNotesCount;
  screenNeedUpdate = true;
  triggerBuzz(25);
  addLog("NOTE", "Xem ghi chu truoc: " + String(activeNoteIndex + 1) + "/" + String(stationNotesCount));
}

void cycleNextCountdown() {
  if (stationCountdownsCount <= 0) return;
  activeCountdownIndex = (activeCountdownIndex + 1) % stationCountdownsCount;
  screenNeedUpdate = true;
  triggerBuzz(25);
  addLog("COUNTDOWN", "Xem dem nguoc tiep theo: " + String(activeCountdownIndex + 1) + "/" + String(stationCountdownsCount));
}

void cyclePrevCountdown() {
  if (stationCountdownsCount <= 0) return;
  activeCountdownIndex = (activeCountdownIndex - 1 + stationCountdownsCount) % stationCountdownsCount;
  screenNeedUpdate = true;
  triggerBuzz(25);
  addLog("COUNTDOWN", "Xem dem nguoc truoc: " + String(activeCountdownIndex + 1) + "/" + String(stationCountdownsCount));
}

float readBatteryVoltage() {
  const int NUM_SAMPLES = 16;
  uint32_t sampleSumRaw = 0;
  uint32_t sampleSumMv = 0;

  for (int i = 0; i < NUM_SAMPLES; i++) {
    int r = analogRead(PIN_BATTERY);
    uint32_t mv = analogReadMilliVolts(PIN_BATTERY);
    if (i >= 2) {
      sampleSumRaw += r;
      sampleSumMv += mv;
    }
    delayMicroseconds(100);
  }

  int rawAvg = sampleSumRaw / (NUM_SAMPLES - 2);
  float adcMv = (float)sampleSumMv / (NUM_SAMPLES - 2);
  float v = 0.0f;

  if (adcMv > 10.0f) {
    v = (adcMv / 1000.0f) * 2.0f;
  } else if (rawAvg > 0) {
    // Fallback if millivolts reading is unavailable
    v = ((float)rawAvg / 4095.0f) * 2.50f * 2.0f;
  }

  static unsigned long lastBatDiagTime = 0;
  if (millis() - lastBatDiagTime >= 5000) {
    lastBatDiagTime = millis();
    int pct = calculateBatteryPct(v);
    Serial.printf("[BAT_DIAG] RAW: %d | ADC: %.1f mV | BAT: %.2f V | PCT: %d%%\n", rawAvg, adcMv, v, pct);
  }

  return v;
}

int calculateBatteryPct(float v) {
  if (v < 2.50f) return 0;
  if (v >= 4.20f) return 100;
  if (v <= 3.20f) return 0;

  int pct = 0;
  if (v >= 4.05f) {
    pct = 90 + (int)((v - 4.05f) / (4.20f - 4.05f) * 10.0f);
  } else if (v >= 3.90f) {
    pct = 75 + (int)((v - 3.90f) / (4.05f - 3.90f) * 15.0f);
  } else if (v >= 3.80f) {
    pct = 55 + (int)((v - 3.80f) / (3.90f - 3.80f) * 20.0f);
  } else if (v >= 3.70f) {
    pct = 35 + (int)((v - 3.70f) / (3.80f - 3.70f) * 20.0f);
  } else if (v >= 3.60f) {
    pct = 18 + (int)((v - 3.60f) / (3.70f - 3.60f) * 17.0f);
  } else if (v >= 3.45f) {
    pct = 7 + (int)((v - 3.45f) / (3.60f - 3.45f) * 11.0f);
  } else {
    pct = (int)((v - 3.20f) / (3.45f - 3.20f) * 7.0f);
  }
  return constrain(pct, 0, 100);
}

bool checkLocalInternet() {
  if (WiFi.status() != WL_CONNECTED) return false;
  return mqtt.connected();
}

String getFormattedRealTime(bool withAmPm = true) {
  struct tm timeinfo;
  if (!getLocalTime(&timeinfo, 20)) {
    return withAmPm ? "12:00 PM" : "12:00";
  }
  int h12 = timeinfo.tm_hour % 12;
  if (h12 == 0) h12 = 12;
  const char* ampm = (timeinfo.tm_hour >= 12) ? "PM" : "AM";

  char buf[16];
  if (withAmPm) {
    sprintf(buf, "%02d:%02d %s", h12, timeinfo.tm_min, ampm);
  } else {
    sprintf(buf, "%02d:%02d", h12, timeinfo.tm_min);
  }
  return String(buf);
}

String getAmPmSuffix() {
  struct tm timeinfo;
  if (!getLocalTime(&timeinfo, 20)) return "PM";
  return (timeinfo.tm_hour >= 12) ? "PM" : "AM";
}

void drawSettingsMenu() {
  tft->fillScreen(COL_BG);

  // Header Box
  tft->fillRoundRect(10, 8, 220, 32, 6, COL_CARD);
  tft->drawRoundRect(10, 8, 220, 32, 6, COL_CYAN);
  printCenteredText("CAI DAT PHAN CUNG", 18, 1, COL_CYAN, COL_CARD);

  if (menuState == MENU_MAIN) {
    int startY = 48;
    int cardH = 54;
    int gap = 8;

    for (int i = 0; i < MENU_MAIN_ITEM_COUNT; i++) {
      int y = startY + i * (cardH + gap);
      bool selected = (menuCurrentItem == i);
      uint16_t bCol = selected ? COL_AMBER : COL_BORDER;

      tft->fillRoundRect(12, y, 216, cardH, 6, COL_CARD);
      tft->drawRoundRect(12, y, 216, cardH, 6, bCol);
      if (selected) {
        tft->drawRoundRect(11, y - 1, 218, cardH + 2, 7, COL_AMBER);
      }

      tft->setTextSize(1);
      tft->setTextColor(selected ? COL_AMBER : COL_WHITE, COL_CARD);
      tft->setCursor(20, y + 10);

      if (i == 0) {
        tft->printf("%s DO SANG LED: %d%%", selected ? ">" : " ", ledBrightness);
        tft->setCursor(24, y + 28);
        tft->setTextColor(COL_MUTED, COL_CARD);
        tft->print("Chinh den LED GPIO 12 (PWM)");
      } else if (i == 1) {
        tft->printf("%s COI CHIP: %s", selected ? ">" : " ", buzzerEnabled ? "BAT" : "TAT (MUTE)");
        tft->setCursor(24, y + 28);
        tft->setTextColor(COL_MUTED, COL_CARD);
        tft->printf("Do dai beep: %d ms", buzzerDurationSetting);
      } else if (i == 2) {
        String modeStr = "TAT (LIGHT)";
        if (darkModeSetting == "always_on" || darkModeSetting == "always") modeStr = "LUON BAT (DARK)";
        else if (darkModeSetting == "scheduled" || darkModeSetting == "custom") modeStr = "THEO LICH (AUTO)";
        tft->printf("%s CHE DO TOI: %s", selected ? ">" : " ", modeStr.c_str());
        tft->setCursor(24, y + 28);
        tft->setTextColor(COL_MUTED, COL_CARD);
        tft->printf("Lich: %s - %s", darkModeStart.c_str(), darkModeEnd.c_str());
      } else if (i == 3) {
        tft->printf("%s THOAT CAI DAT", selected ? ">" : " ");
        tft->setCursor(24, y + 28);
        tft->setTextColor(COL_MUTED, COL_CARD);
        tft->print("Quay lai man hinh chinh");
      }
    }

    tft->drawFastHLine(10, 296, 220, COL_BORDER);
    printCenteredText("PREV/NEXT: Chon | PLAY: Vao", 304, 1, COL_MUTED, COL_BG);

  } else if (menuState == MENU_ADJUST_LED) {
    printCenteredText("CHINH DO SANG LED (PWM)", 52, 1, COL_CYAN, COL_BG);

    tft->fillRoundRect(16, 75, 208, 195, 8, COL_CARD);
    tft->drawRoundRect(16, 75, 208, 195, 8, COL_BORDER);

    tft->setTextSize(4);
    tft->setTextColor(COL_AMBER, COL_CARD);
    String pctStr = String(ledBrightness) + "%";
    int pw = (int)pctStr.length() * 24;
    tft->setCursor((240 - pw) / 2, 110);
    tft->print(pctStr);

    drawProgressBarSmooth(28, 175, 184, 16, ledBrightness, COL_AMBER);

    printCenteredText("PREV: -10% | NEXT: +10%", 210, 1, COL_WHITE, COL_CARD);
    printCenteredText("Nhan PLAY de Luu & Thoat", 235, 1, COL_CYAN, COL_CARD);

    tft->drawFastHLine(10, 296, 220, COL_BORDER);
    printCenteredText("Giu NEXT: Quay lai menu", 304, 1, COL_MUTED, COL_BG);

  } else if (menuState == MENU_BUZZER_CONFIG) {
    printCenteredText("CAU HINH COI CHIP", 48, 1, COL_CYAN, COL_BG);

    int startY = 68;
    int cardH = 48;
    int gap = 8;

    for (int i = 0; i < MENU_BUZZER_ITEM_COUNT; i++) {
      int y = startY + i * (cardH + gap);
      bool selected = (buzzerCurrentItem == i);
      uint16_t bCol = selected ? COL_AMBER : COL_BORDER;

      tft->fillRoundRect(14, y, 212, cardH, 6, COL_CARD);
      tft->drawRoundRect(14, y, 212, cardH, 6, bCol);
      if (selected) {
        tft->drawRoundRect(13, y - 1, 214, cardH + 2, 7, COL_AMBER);
      }

      tft->setTextSize(1);
      tft->setTextColor(selected ? COL_AMBER : COL_WHITE, COL_CARD);
      tft->setCursor(22, y + 10);

      if (i == 0) {
        tft->printf("%s COI BIP: %s", selected ? ">" : " ", buzzerEnabled ? "[BAT]" : "[TAT / MUTE]");
        tft->setCursor(24, y + 28);
        tft->setTextColor(COL_MUTED, COL_CARD);
        tft->print("PLAY: Bat / tat am thanh");
      } else if (i == 1) {
        tft->printf("%s DO DAI BEEP: %d ms", selected ? ">" : " ", buzzerDurationSetting);
        tft->setCursor(24, y + 28);
        tft->setTextColor(COL_MUTED, COL_CARD);
        tft->print("PLAY: Thay doi do dai bip");
      } else if (i == 2) {
        tft->printf("%s THU TIENG COI (TEST)", selected ? ">" : " ");
        tft->setCursor(24, y + 28);
        tft->setTextColor(COL_MUTED, COL_CARD);
        tft->print("PLAY: Phat am thu 2400Hz");
      } else if (i == 3) {
        tft->printf("%s QUAY LAI MENU CHINH", selected ? ">" : " ");
        tft->setCursor(24, y + 28);
        tft->setTextColor(COL_MUTED, COL_CARD);
        tft->print("Tro ve danh sach cai dat");
      }
    }

    tft->drawFastHLine(10, 296, 220, COL_BORDER);
    printCenteredText("PREV/NEXT: Chon | PLAY: Chon", 304, 1, COL_MUTED, COL_BG);
  }
}

void drawViewMenu() {
  static bool firstDraw = true;

  if (viewMenuNeedsRedraw || firstDraw) {
    tft->fillScreen(COL_BG);

    // Header Box
    tft->fillRoundRect(10, 8, 220, 30, 6, COL_CARD);
    tft->drawRoundRect(10, 8, 220, 30, 6, COL_CYAN);
    printCenteredText("DANH SACH MAN HINH", 16, 1, COL_CYAN, COL_CARD);

    // Footer
    tft->drawFastHLine(10, 296, 220, COL_BORDER);
    printCenteredText("[Prev/Next]: Tim   [Play]: Chon", 304, 1, COL_MUTED, COL_BG);

    firstDraw = false;
    viewMenuNeedsRedraw = false;
  }

  int startY = 44;
  int cardH = 24;
  int gap = 3;

  for (int i = 0; i < VIEW_MENU_COUNT; i++) {
    int y = startY + i * (cardH + gap);
    bool selected = (viewMenuIndex == i);
    bool isCurrent = (srvStats.activeView == VIEW_MENU_ITEMS[i].id);

    uint16_t bg = selected ? COL_CYAN : COL_CARD;
    uint16_t border = selected ? COL_WHITE : (isCurrent ? COL_GREEN : COL_BORDER);
    uint16_t textCol = selected ? COL_BG : (isCurrent ? COL_GREEN : COL_WHITE);

    tft->fillRoundRect(12, y, 216, cardH, 4, bg);
    tft->drawRoundRect(12, y, 216, cardH, 4, border);

    tft->setTextSize(1);
    tft->setTextColor(textCol, bg);
    tft->setCursor(18, y + 8);

    if (selected) {
      tft->printf("> %s", VIEW_MENU_ITEMS[i].title);
    } else {
      tft->printf("  %s", VIEW_MENU_ITEMS[i].title);
    }

    if (isCurrent) {
      tft->setCursor(168, y + 8);
      tft->setTextColor(selected ? COL_BG : COL_GREEN, bg);
      tft->print("[DANG MO]");
    }
  }
}

void drawNotesView() {
  printCenteredText("GHI CHU DANG GHIM", 36, 2, COL_CYAN, COL_BG);

  if (stationNotesCount == 0) {
    tft->fillRoundRect(14, 75, 212, 195, 8, COL_CARD);
    tft->drawRoundRect(14, 75, 212, 195, 8, COL_BORDER);
    printCenteredText("Chua co ghi chu nao", 120, 1, COL_MUTED, COL_CARD);
    printCenteredText("Them ghi chu tu app DeskFlow", 150, 1, COL_WHITE, COL_CARD);
    printCenteredText("Ghi chu se tu dong dong bo!", 175, 1, COL_AMBER, COL_CARD);
  } else {
    if (activeNoteIndex >= stationNotesCount) activeNoteIndex = 0;
    auto& note = stationNotes[activeNoteIndex];

    String pageStr = String("Ghi chu: ") + (activeNoteIndex + 1) + "/" + stationNotesCount;
    printCenteredText(pageStr, 58, 1, COL_MUTED, COL_BG);

    tft->fillRoundRect(14, 75, 212, 195, 8, COL_CARD);
    tft->drawRoundRect(14, 75, 212, 195, 8, note.isPinned ? COL_CYAN : COL_BORDER);

    if (note.isPinned) {
      tft->fillRoundRect(22, 85, 100, 18, 4, COL_CYAN);
      tft->setTextSize(1);
      tft->setTextColor(COL_BG, COL_CYAN);
      tft->setCursor(28, 90);
      tft->print("* DANG GHIM *");
    } else {
      tft->setTextSize(1);
      tft->setTextColor(COL_MUTED, COL_CARD);
      tft->setCursor(24, 90);
      tft->print("Ghi chu ca nhan");
    }

    tft->setTextSize(1);
    tft->setTextColor(COL_AMBER, COL_CARD);
    tft->setCursor(24, 112);
    tft->print(removeAccents(note.title).substring(0, 26));

    tft->drawFastHLine(24, 126, 192, COL_BORDER);

    printBigWrappedCenteredText(note.content, 185, 1, COL_WHITE, 26, 16, COL_CARD);
  }

  printCenteredText("< PREV               NEXT >", 280, 1, COL_MUTED, COL_BG);
}

void drawCountdownView() {
  printCenteredText("SU KIEN DEM NGUOC", 36, 2, COL_AMBER, COL_BG);

  if (stationCountdownsCount == 0) {
    tft->fillRoundRect(14, 75, 212, 195, 8, COL_CARD);
    tft->drawRoundRect(14, 75, 212, 195, 8, COL_BORDER);
    printCenteredText("Chua co su kien dem nguoc", 120, 1, COL_MUTED, COL_CARD);
    printCenteredText("Tao su kien tu app DeskFlow", 150, 1, COL_WHITE, COL_CARD);
    printCenteredText("Ho tro dem nguoc ngay thi", 175, 1, COL_CYAN, COL_CARD);
  } else {
    if (activeCountdownIndex >= stationCountdownsCount) activeCountdownIndex = 0;
    auto& cd = stationCountdowns[activeCountdownIndex];

    String pageStr = String("Su kien: ") + (activeCountdownIndex + 1) + "/" + stationCountdownsCount;
    printCenteredText(pageStr, 58, 1, COL_MUTED, COL_BG);

    tft->fillRoundRect(14, 75, 212, 195, 8, COL_CARD);
    tft->drawRoundRect(14, 75, 212, 195, 8, cd.isPinned ? COL_AMBER : COL_BORDER);

    if (cd.isPinned) {
      tft->fillRoundRect(22, 85, 120, 18, 4, COL_AMBER);
      tft->setTextSize(1);
      tft->setTextColor(COL_BG, COL_AMBER);
      tft->setCursor(28, 90);
      tft->print("* DANG GHIM TRAM *");
    } else {
      tft->setTextSize(1);
      tft->setTextColor(COL_MUTED, COL_CARD);
      tft->setCursor(24, 90);
      tft->print("Su kien quan trong");
    }

    printCenteredText(removeAccents(cd.title).substring(0, 22), 115, 2, COL_WHITE, COL_CARD);

    String dStr = String("Ngay dich: ") + cd.targetDate.substring(0, 10);
    printCenteredText(dStr, 142, 1, COL_MUTED, COL_CARD);

    if (cd.isExpired || cd.daysLeft <= 0) {
      tft->fillRoundRect(24, 162, 192, 52, 6, 0x6000);
      tft->drawRoundRect(24, 162, 192, 52, 6, 0xF800);
      printCenteredText("DA KET THUC", 182, 2, 0xF800, 0x6000);
    } else {
      tft->fillRoundRect(24, 162, 192, 52, 6, COL_BG);
      tft->drawRoundRect(24, 162, 192, 52, 6, COL_AMBER);
      String daysStr = String("CON ") + cd.daysLeft + " NGAY";
      printCenteredText(daysStr, 180, 2, COL_AMBER, COL_BG);
    }
  }

  printCenteredText("< PREV               NEXT >", 280, 1, COL_MUTED, COL_BG);
}

void drawFocusView() {
  static String lastFocusPhase = "";
  static String lastFocusTimeStr = "";
  static int lastFocusPct = -1;
  static bool lastFocusStale = false;
  static String lastFocusSessionType = "";

  static String lastTrackedView = "";
  bool firstEntry = (lastTrackedView != srvStats.activeView);
  if (firstEntry) {
    lastTrackedView = srvStats.activeView;
    lastFocusPhase = "";
    lastFocusTimeStr = "";
    lastFocusPct = -1;
    lastFocusStale = false;
    lastFocusSessionType = "";
  }

  // Calculate elapsed time for smooth display interpolation
  long elapsedSec = (focusStatusReceivedMillis > 0) ? ((millis() - focusStatusReceivedMillis) / 1000) : 0;
  long displaySec = 0;
  if (focusEnabled && focusPhase != "EXPIRED_LOCKED" && focusPhase != "IDLE") {
    displaySec = (focusRemainingSeconds > elapsedSec) ? (focusRemainingSeconds - elapsedSec) : 0;
  }

  // Calculate progress percentage
  int pct = 0;
  if (focusEnabled && focusTotalSeconds > 0) {
    if (focusPhase == "EXPIRED_LOCKED") {
      pct = 100;
    } else {
      pct = (int)(((focusTotalSeconds - displaySec) * 100) / focusTotalSeconds);
      pct = constrain(pct, 0, 100);
    }
  }

  // Format time string
  char timeBuf[16];
  if (!focusEnabled || focusPhase == "IDLE") {
    snprintf(timeBuf, sizeof(timeBuf), "--:--");
  } else if (focusPhase == "EXPIRED_LOCKED") {
    snprintf(timeBuf, sizeof(timeBuf), "00:00");
  } else if (displaySec >= 3600) {
    int h = displaySec / 3600;
    int m = (displaySec % 3600) / 60;
    int s = displaySec % 60;
    snprintf(timeBuf, sizeof(timeBuf), "%02d:%02d:%02d", h, m, s);
  } else {
    int m = displaySec / 60;
    int s = displaySec % 60;
    snprintf(timeBuf, sizeof(timeBuf), "%02d:%02d", m, s);
  }
  String timeStr = String(timeBuf);

  // Freshness check: Backend publishes every 1s when active, or on transition.
  // If no message received for >60s, mark as stale / lost sync.
  bool isStale = (focusStatusReceivedMillis == 0) || (millis() - focusStatusReceivedMillis > 60000);

  // Colors & Badges based on authoritative phase
  uint16_t cardBorderCol = COL_BORDER;
  uint16_t badgeBg = COL_BORDER;
  uint16_t badgeFg = COL_WHITE;
  String badgeText = "CHUA KICH HOAT";
  uint16_t timeCol = COL_MUTED;
  uint16_t barCol = COL_MUTED;

  if (focusPhase == "EXPIRED_LOCKED") {
    cardBorderCol = 0xF800; // Red
    badgeBg = 0xF800;
    badgeFg = 0xFFFF;
    badgeText = "HET GIO - DA KHOA";
    timeCol = 0xF800;
    barCol = 0xF800;
  } else if (focusPhase == "BREAK") {
    cardBorderCol = COL_CYAN;
    badgeBg = COL_CYAN;
    badgeFg = COL_BG;
    badgeText = "GIO GIAI LAO";
    timeCol = COL_CYAN;
    barCol = COL_CYAN;
  } else if (focusEnabled && (focusPhase == "WORK" || focusPhase == "FOCUS")) {
    cardBorderCol = COL_AMBER;
    badgeBg = COL_AMBER;
    badgeFg = COL_BG;
    badgeText = (focusSessionType == "pomodoro") ? "POMODORO WORK" : "DANG TAP TRUNG";
    timeCol = COL_WHITE;
    barCol = COL_GREEN;
  }

  // Draw static elements on first entry or state change
  if (firstEntry) {
    printCenteredText("CHEDO TAP TRUNG", 34, 2, COL_AMBER, COL_BG);

    tft->fillRoundRect(14, 56, 212, 222, 8, COL_CARD);
    tft->drawRoundRect(14, 56, 212, 222, 8, cardBorderCol);

    tft->drawFastHLine(24, 156, 192, COL_BORDER);
    tft->drawFastHLine(24, 230, 192, COL_BORDER);

    printCenteredText("< PREV               NEXT >", 284, 1, COL_MUTED, COL_BG);
  } else if (focusPhase != lastFocusPhase) {
    tft->drawRoundRect(14, 56, 212, 222, 8, cardBorderCol);
  }

  // 1. Phase Badge
  if (firstEntry || focusPhase != lastFocusPhase || focusSessionType != lastFocusSessionType) {
    tft->fillRect(20, 64, 200, 24, COL_CARD);
    int badgeW = 156;
    int badgeH = 20;
    int badgeX = (240 - badgeW) / 2;
    tft->fillRoundRect(badgeX, 66, badgeW, badgeH, 4, badgeBg);
    printCenteredText(badgeText, 72, 1, badgeFg, badgeBg);
  }

  // 2. Large Time Display
  if (firstEntry || timeStr != lastFocusTimeStr || focusPhase != lastFocusPhase) {
    tft->fillRect(20, 94, 200, 36, COL_CARD);
    int timeFontSize = (timeStr.length() > 5) ? 3 : 4;
    int timeY = (timeFontSize == 4) ? 98 : 102;
    printCenteredText(timeStr, timeY, timeFontSize, timeCol, COL_CARD);
    lastFocusTimeStr = timeStr;
  }

  // 3. Progress Bar
  if (firstEntry || pct != lastFocusPct || focusPhase != lastFocusPhase) {
    drawProgressBarSmooth(26, 138, 188, 8, pct, barCol);
    lastFocusPct = pct;
  }

  // 4. Session & Blocking Info
  if (firstEntry || focusPhase != lastFocusPhase || focusSessionType != lastFocusSessionType) {
    tft->fillRect(20, 166, 200, 58, COL_CARD);

    String s1 = "";
    if (focusSessionType == "pomodoro") {
      s1 = "Chu ky: " + String(focusCycleCount) + " (Pomodoro)";
    } else if (focusEnabled) {
      s1 = "Phien tap trung co dinh";
    } else {
      s1 = "Chua co phien tap trung";
    }
    printCenteredText(s1, 168, 1, COL_WHITE, COL_CARD);

    String s2 = "";
    uint16_t s2Col = COL_MUTED;
    if (focusPhase == "EXPIRED_LOCKED") {
      s2 = "Dang khoa thiet bi & web!";
      s2Col = 0xF800; // Red
    } else if (focusActive) {
      s2 = "Dang chan cac web giai tri";
      s2Col = COL_GREEN;
    } else if (focusEnabled && !focusActive) {
      s2 = "Tam dung chan web (Nghi)";
      s2Col = COL_CYAN;
    } else {
      s2 = "Web giai tri duoc phep";
      s2Col = COL_MUTED;
    }
    printCenteredText(s2, 186, 1, s2Col, COL_CARD);

    String s3 = "";
    uint16_t s3Col = COL_MUTED;
    if (focusPhase == "EXPIRED_LOCKED") {
      s3 = "Giai toan tren PC/App de mo";
      s3Col = COL_AMBER;
    } else if (focusEnabled) {
      s3 = "Dong bo tu Python Backend";
      s3Col = COL_MUTED;
    } else {
      s3 = "Bat tap trung tu PC hoac App";
      s3Col = COL_MUTED;
    }
    printCenteredText(s3, 204, 1, s3Col, COL_CARD);
  }

  // 5. Backend Sync Freshness
  if (firstEntry || isStale != lastFocusStale) {
    tft->fillRect(20, 240, 200, 18, COL_CARD);
    if (isStale) {
      printCenteredText("! Mat dong bo Backend !", 244, 1, 0xF800, COL_CARD);
    } else {
      printCenteredText("* Dong bo Backend: Tot *", 244, 1, COL_MUTED, COL_CARD);
    }
    lastFocusStale = isStale;
  }

  lastFocusPhase = focusPhase;
  lastFocusSessionType = focusSessionType;
}

void drawPhysicalScreen() {
  if (!tft) return;

  if (inSettingsMenu) {
    drawSettingsMenu();
    return;
  }

  if (inViewMenu) {
    drawViewMenu();
    return;
  }

  bool viewChanged = false;
  if (srvStats.activeView != lastRenderedView) {
    tft->fillScreen(COL_BG);
    lastRenderedView = srvStats.activeView;
    viewChanged = true;
    tft->drawFastHLine(10, 24, 220, COL_BORDER);
    tft->drawFastHLine(10, 296, 220, COL_BORDER);
    printCenteredText("DeskFlow Station OS", 304, 1, COL_MUTED, COL_BG);
  }

  // 1. HEADER
  static String lastHeaderTime = "";
  static bool lastHeaderNet = false;
  static String lastHeaderWeather = "";
  static int lastHeaderBat = -1;

  if (viewChanged) {
    lastHeaderTime = "";
    lastHeaderWeather = "";
    lastHeaderBat = -1;
  }

  if (viewChanged || srvStats.internet != lastHeaderNet) {
    tft->setTextSize(1);
    tft->setCursor(14, 8);
    if (srvStats.internet) {
      tft->setTextColor(COL_GREEN, COL_BG);
      tft->print("v ");
    } else {
      tft->setTextColor(0xF800, COL_BG);
      tft->print("x ");
    }
    lastHeaderNet = srvStats.internet;
  }

  String wCompact = srvStats.weatherText;
  int starIdx = wCompact.indexOf('*');
  if (starIdx == -1) starIdx = wCompact.indexOf('C');
  if (starIdx != -1 && starIdx < 6) {
    wCompact = wCompact.substring(0, starIdx + 1);
  } else if (wCompact.length() > 6) {
    wCompact = wCompact.substring(0, 6);
  }

  if (viewChanged || wCompact != lastHeaderWeather) {
    tft->setTextSize(1);
    tft->setTextColor(COL_CYAN, COL_BG);
    tft->setCursor(28, 8);
    tft->print(wCompact + "  ");
    lastHeaderWeather = wCompact;
  }

  String curTimeStr = getFormattedRealTime(true);
  if (viewChanged || curTimeStr != lastHeaderTime) {
    tft->fillRect(80, 5, 50, 12, COL_BG);
    tft->setTextSize(1);
    tft->setTextColor(COL_MUTED, COL_BG);
    tft->setCursor(84, 8);
    tft->print(curTimeStr);
    lastHeaderTime = curTimeStr;
  }

  // Header Focus Mode Indicator (Presentation-only display)
  static String lastHeaderFocus = "";
  if (viewChanged) {
    lastHeaderFocus = "";
  }
  String curFocusIndicator = "";
  if (focusEnabled) {
    long elapsedSec = (millis() - focusStatusReceivedMillis) / 1000;
    long displaySec = (focusRemainingSeconds > elapsedSec) ? (focusRemainingSeconds - elapsedSec) : 0;
    int remM = displaySec / 60;
    if (focusPhase == "BREAK") {
      curFocusIndicator = "B:" + String(remM) + "m";
    } else if (focusPhase == "EXPIRED_LOCKED") {
      curFocusIndicator = "LOCK";
    } else {
      curFocusIndicator = "F:" + String(remM) + "m";
    }
  }
  if (viewChanged || curFocusIndicator != lastHeaderFocus) {
    tft->fillRect(135, 5, 48, 12, COL_BG);
    if (curFocusIndicator.length() > 0) {
      tft->setTextSize(1);
      if (focusPhase == "BREAK") {
        tft->setTextColor(COL_GREEN, COL_BG);
      } else if (focusPhase == "EXPIRED_LOCKED") {
        tft->setTextColor(0xF800, COL_BG); // Red
      } else {
        tft->setTextColor(COL_AMBER, COL_BG);
      }
      tft->setCursor(137, 8);
      tft->print(curFocusIndicator);
    }
    lastHeaderFocus = curFocusIndicator;
  }

  int curBatPct = calculateBatteryPct(readBatteryVoltage());
  if (viewChanged || curBatPct != lastHeaderBat) {
    tft->setTextSize(1);
    tft->setTextColor(COL_AMBER, COL_BG);
    tft->setCursor(188, 8);
    tft->print("Z ");
    tft->setTextColor(COL_MUTED, COL_BG);
    tft->setCursor(200, 8);
    tft->printf("%2d%", curBatPct);
    lastHeaderBat = curBatPct;
  }

  // 2. VIEW MAIN
  if (srvStats.activeView == "main") {
    static String lastMainTime = "";
    static String lastMainWeather = "";
    static String lastMainTrack = "";
    static String lastMainArtist = "";
    static String lastMainQuote = "";

    if (viewChanged) {
      lastMainWeather = "";
      lastMainTrack = "";
      lastMainArtist = "";
      lastMainQuote = "";
    }

    // Render active synchronized Clock Style in main banner
    drawClockArea(0, 48, 240, 68, false);

    String wStr = srvStats.weatherCity + " . " + srvStats.weatherText;
    if (viewChanged || wStr != lastMainWeather) {
      tft->fillRect(0, 118, 240, 18, COL_BG);
      printCenteredText(wStr, 120, 1, COL_MUTED, COL_BG);
      lastMainWeather = wStr;
    }

    if (viewChanged || srvStats.currentTrack != lastMainTrack || srvStats.currentArtist != lastMainArtist) {
      tft->fillRoundRect(15, 142, 210, 48, 8, COL_CARD);
      tft->drawRoundRect(15, 142, 210, 48, 8, COL_BORDER);

      tft->setTextSize(1);
      tft->setTextColor(COL_GREEN, COL_CARD);
      tft->setCursor(24, 154);
      tft->print("SP ");

      tft->setTextColor(COL_WHITE, COL_CARD);
      tft->setCursor(46, 154);
      tft->print(removeAccents(srvStats.currentTrack).substring(0, 24));

      tft->setTextColor(COL_MUTED, COL_CARD);
      tft->setCursor(46, 168);
      if (srvStats.currentArtist.length() > 0 && srvStats.currentArtist != "N/A") {
        tft->print(removeAccents(srvStats.currentArtist).substring(0, 24));
      } else {
        tft->print("Spotify Desktop");
      }

      lastMainTrack = srvStats.currentTrack;
      lastMainArtist = srvStats.currentArtist;
    }

    static String lastMainNote = "";
    static String lastMainCd = "";

    if (viewChanged || srvStats.quoteText != lastMainQuote || srvStats.stickyNote != lastMainNote || (srvStats.countdownName + String(srvStats.countdownDays)) != lastMainCd) {
      tft->fillRect(10, 196, 220, 92, COL_BG);

      if (srvStats.hasCountdown && srvStats.countdownName.length() > 0) {
        String cdTxt = removeAccents(srvStats.countdownName) + ": " + String(srvStats.countdownDays) + " ngay";
        printCenteredText(cdTxt, 198, 1, COL_AMBER, COL_BG);

        if (srvStats.stickyNote.length() > 0) {
          printCenteredText("~ GHI CHU GHIM ~", 212, 1, COL_CYAN, COL_BG);
          printBigWrappedTopCenteredText(srvStats.stickyNote, 226, 1, COL_WHITE, 28, 13, COL_BG);
        } else {
          printCenteredText("~ DAILY MOTIVATION ~", 212, 1, COL_MUTED, COL_BG);
          printBigWrappedTopCenteredText(srvStats.quoteText, 226, 1, COL_WHITE, 28, 13, COL_BG);
        }
      } else {
        if (srvStats.stickyNote.length() > 0) {
          printCenteredText("~ GHI CHU GHIM ~", 202, 1, COL_CYAN, COL_BG);
          printBigWrappedTopCenteredText(srvStats.stickyNote, 218, 1, COL_WHITE, 28, 13, COL_BG);
        } else {
          printCenteredText("~ DAILY MOTIVATION ~", 202, 1, COL_AMBER, COL_BG);
          printBigWrappedTopCenteredText(srvStats.quoteText, 218, 1, COL_WHITE, 28, 13, COL_BG);
        }
      }
      lastMainQuote = srvStats.quoteText;
      lastMainNote = srvStats.stickyNote;
      lastMainCd = srvStats.countdownName + String(srvStats.countdownDays);
    }
  }
  // 3. VIEW STATS
  else if (srvStats.activeView == "stats") {
    static float lastLpCpu = -999.0;
    static float lastLpRam = -999.0;
    static int lastLpBat = -999;
    static float lastSrvCpu = -999.0;
    static float lastSrvRam = -999.0;
    static String lastSrvUptime = "";
    static int lastEspBatPct = -999;

    if (viewChanged) {
      printCenteredText("HARDWARE STATS", 38, 2, COL_PURPLE, COL_BG);
      tft->drawFastHLine(16, 150, 208, COL_BORDER);
      lastLpCpu = -999.0;
      lastLpRam = -999.0;
      lastLpBat = -999;
      lastSrvCpu = -999.0;
      lastSrvRam = -999.0;
      lastSrvUptime = "";
      lastEspBatPct = -999;
    }

    // Dynamic Laptop stats - live 1-second refresh
    if (viewChanged || srvStats.lp_cpu != lastLpCpu || srvStats.lp_ram != lastLpRam || srvStats.lp_bat != lastLpBat) {
      tft->fillRect(16, 68, 208, 14, COL_BG);
      tft->setTextSize(1);
      tft->setTextColor(COL_WHITE, COL_BG);
      tft->setCursor(16, 70);
      tft->print("Laptop: ");
      tft->setTextColor(COL_CYAN, COL_BG);
      tft->printf("%d%% | %.1f%% | %.1f%%", srvStats.lp_bat, srvStats.lp_ram, srvStats.lp_cpu);
      drawProgressBarSmooth(16, 84, 208, 8, constrain((int)srvStats.lp_cpu, 0, 100), COL_CYAN);
      lastLpCpu = srvStats.lp_cpu;
      lastLpRam = srvStats.lp_ram;
      lastLpBat = srvStats.lp_bat;
    }

    // Dynamic Server stats - live 1-second refresh
    if (viewChanged || srvStats.cpu != lastSrvCpu || srvStats.ram_pct != lastSrvRam || srvStats.uptime != lastSrvUptime) {
      tft->fillRect(16, 108, 208, 14, COL_BG);
      tft->setTextSize(1);
      tft->setTextColor(COL_WHITE, COL_BG);
      tft->setCursor(16, 110);
      tft->print("Server: ");
      tft->setTextColor(COL_GREEN, COL_BG);
      tft->printf("%s | %.1f%% | %.1f%%", srvStats.uptime.c_str(), srvStats.ram_pct, srvStats.cpu);
      drawProgressBarSmooth(16, 124, 208, 8, constrain((int)srvStats.cpu, 0, 100), COL_GREEN);
      lastSrvCpu = srvStats.cpu;
      lastSrvRam = srvStats.ram_pct;
      lastSrvUptime = srvStats.uptime;
    }

    // Dynamic ESP32 Battery voltage and pct
    float vEsp = readBatteryVoltage();
    int pctEsp = calculateBatteryPct(vEsp);
    if (viewChanged || pctEsp != lastEspBatPct) {
      tft->fillRect(16, 166, 208, 14, COL_BG);
      tft->setTextSize(1);
      tft->setTextColor(COL_WHITE, COL_BG);
      tft->setCursor(16, 168);
      tft->print("ESP32 Pin: ");
      tft->setTextColor(COL_AMBER, COL_BG);
      tft->printf("%.2fV (%d%%)", vEsp, pctEsp);
      drawProgressBarSmooth(16, 184, 208, 8, constrain(pctEsp, 0, 100), COL_AMBER);
      lastEspBatPct = pctEsp;
    }
  }
  // 4. VIEW TKB
  else if (srvStats.activeView == "tkb") {
    if (viewChanged) {
      printCenteredText("THOI KHOA BIEU", 36, 2, COL_AMBER, COL_BG);

      int curY = 62;
      tft->setTextSize(1);
      tft->setTextColor(COL_CYAN, COL_BG);
      tft->setCursor(14, curY);
      tft->print(removeAccents(srvStats.tkbDay) + ":");
      curY += 15;

      tft->setTextColor(COL_AMBER, COL_BG);
      tft->setCursor(16, curY);
      tft->print("* Sang: ");
      tft->setTextColor(COL_WHITE, COL_BG);
      tft->setCursor(62, curY);
      tft->print(removeAccents(srvStats.tkbMorning).substring(0, 28));
      curY += 15;

      if (srvStats.tkbHasAfternoon && srvStats.tkbAfternoon.length() > 0) {
        tft->setTextColor(COL_AMBER, COL_BG);
        tft->setCursor(16, curY);
        tft->print("* Chieu: ");
        tft->setTextColor(COL_WHITE, COL_BG);
        tft->setCursor(62, curY);
        tft->print(removeAccents(srvStats.tkbAfternoon).substring(0, 28));
        curY += 15;
      }

      tft->setTextColor(COL_GREEN, COL_BG);
      tft->setCursor(16, curY);
      tft->print("* D.phuc: ");
      tft->setTextColor(COL_CYAN, COL_BG);
      tft->setCursor(72, curY);
      tft->print(removeAccents(srvStats.tkbUniform).substring(0, 26));
      curY += 22;

      tft->drawFastHLine(14, curY, 212, COL_BORDER);
      curY += 10;

      tft->setTextColor(COL_CYAN, COL_BG);
      tft->setCursor(14, curY);
      tft->print(removeAccents(srvStats.tkbTomDay) + ":");
      curY += 15;

      tft->setTextColor(COL_AMBER, COL_BG);
      tft->setCursor(16, curY);
      tft->print("* Sang: ");
      tft->setTextColor(COL_WHITE, COL_BG);
      tft->setCursor(62, curY);
      tft->print(removeAccents(srvStats.tkbTomMorning).substring(0, 28));
      curY += 15;

      if (srvStats.tkbTomHasAfternoon && srvStats.tkbTomAfternoon.length() > 0) {
        tft->setTextColor(COL_AMBER, COL_BG);
        tft->setCursor(16, curY);
        tft->print("* Chieu: ");
        tft->setTextColor(COL_WHITE, COL_BG);
        tft->setCursor(62, curY);
        tft->print(removeAccents(srvStats.tkbTomAfternoon).substring(0, 28));
        curY += 15;
      }

      tft->setTextColor(COL_GREEN, COL_BG);
      tft->setCursor(16, curY);
      tft->print("* D.phuc: ");
      tft->setTextColor(COL_CYAN, COL_BG);
      tft->setCursor(72, curY);
      tft->print(removeAccents(srvStats.tkbTomUniform).substring(0, 26));
    }
  }
  // 5. VIEW WEATHER
  else if (srvStats.activeView == "weather") {
    if (viewChanged) {
      drawWeatherWebIcon(120, 105);
      printCenteredText(srvStats.weatherText, 160, 3, COL_AMBER, COL_BG);
      printCenteredText(srvStats.weatherCity, 200, 1, COL_MUTED, COL_BG);
    }
  }
  // 6. VIEW LYRICS
  else if (srvStats.activeView == "lyrics") {
    static String lastTrack = "";
    static String lastLyric = "";
    static String lastNext = "";
    static int lastPct = -1;

    if (viewChanged) {
      lastTrack = "";
      lastLyric = "";
      lastNext = "";
      lastPct = -1;
    }

    if (srvStats.currentTrack != lastTrack) {
      tft->fillRect(0, 36, 240, 22, COL_BG);
      printCenteredText(removeAccents(srvStats.currentTrack).substring(0, 32), 42, 1, COL_MUTED, COL_BG);
      lastTrack = srvStats.currentTrack;
    }

    String lyricTxt = srvStats.lyricCurr.length() > 0 ? removeAccents(srvStats.lyricCurr) : "Chua phat nhac";
    if (lyricTxt != lastLyric) {
      tft->fillRect(0, 58, 240, 105, COL_BG);
      if (lyricTxt.length() <= 18) {
        printCenteredText(lyricTxt, 105, 2, COL_AMBER, COL_BG);
      } else {
        printBigWrappedCenteredText(lyricTxt, 105, 2, COL_AMBER, 17, 20);
      }
      lastLyric = lyricTxt;
    }

    if (srvStats.lyricNext != lastNext) {
      tft->fillRect(0, 166, 240, 20, COL_BG);
      if (srvStats.lyricNext.length() > 0) {
        String nextTxt = removeAccents(srvStats.lyricNext);
        printCenteredText(nextTxt.substring(0, 30), 170, 1, COL_MUTED, COL_BG);
      } else {
        printCenteredText("~ ~ ~", 170, 1, COL_MUTED, COL_BG);
      }
      lastNext = srvStats.lyricNext;
    }

    if (srvStats.progressPct != lastPct) {
      drawProgressBarSmooth(25, 208, 190, 8, srvStats.progressPct, COL_GREEN);
      lastPct = srvStats.progressPct;
    }
  }
  // 7. VIEW MATH ALARM
  else if (srvStats.activeView == "math_alarm") {
    if (viewChanged) {
      printCenteredText("! MATH ALARM !", 40, 2, 0xF800, COL_BG);
      printCenteredText("Giai toan de tat chuong:", 80, 1, COL_WHITE, COL_BG);
      printCenteredText(currentMathQuestion, 125, 3, COL_AMBER, COL_BG);
      printCenteredText("Dung lenh /solve tren Discord", 185, 1, COL_CYAN, COL_BG);
    }
  }
  // 8. VIEW CLOCK FULLSCREEN
  else if (srvStats.activeView == "clock") {
    drawClockArea(0, 28, 240, 266, true);
  }
  // 9. VIEW NOTES (DEDICATED)
  else if (srvStats.activeView == "notes") {
    drawNotesView();
  }
  // 10. VIEW COUNTDOWN (DEDICATED)
  else if (srvStats.activeView == "countdown") {
    drawCountdownView();
  }
  // 11. VIEW FOCUS (DEDICATED)
  else if (srvStats.activeView == "focus") {
    drawFocusView();
  }
}

void saveNetworkToArray(String ssid, String pass) {
  DynamicJsonDocument doc(8192);
  deserializeJson(doc, wifiListJson);
  JsonArray arr = doc.as<JsonArray>();

  bool found = false;
  for (JsonObject item : arr) {
    if (item["ssid"].as<String>() == ssid) {
      item["pass"] = pass;
      found = true;
      break;
    }
  }

  if (!found) {
    JsonObject newItem = arr.createNestedObject();
    newItem["ssid"] = ssid;
    newItem["pass"] = pass;
  }

  String newJson;
  serializeJson(doc, newJson);
  wifiListJson = newJson;
  prefs.putString("wifi_array", wifiListJson);
  addLog("WIFI", "Da luu mang [" + ssid + "] vao NVS.");
}

void removeNetworkFromArray(String ssid) {
  DynamicJsonDocument doc(8192);
  deserializeJson(doc, wifiListJson);
  JsonArray arr = doc.as<JsonArray>();

  for (size_t i = 0; i < arr.size(); i++) {
    if (arr[i]["ssid"].as<String>() == ssid) {
      arr.remove(i);
      break;
    }
  }

  String newJson;
  serializeJson(doc, newJson);
  wifiListJson = newJson;
  prefs.putString("wifi_array", wifiListJson);
  addLog("WIFI", "Da xoa mang [" + ssid + "].");
}

void autoConnectMultiWiFi() {
  DynamicJsonDocument doc(8192);
  deserializeJson(doc, wifiListJson);
  JsonArray savedArr = doc.as<JsonArray>();

  if (savedArr.size() == 0) return;

  WiFi.disconnect(false, false);
  delay(50);
  int n = WiFi.scanNetworks(false, false);
  if (n <= 0) {
    WiFi.scanDelete();
    return;
  }

  String bestSSID = "";
  String bestPass = "";
  int bestRSSI = -999;

  for (int i = 0; i < n; ++i) {
    String foundSSID = WiFi.SSID(i);
    int currentRSSI = WiFi.RSSI(i);
    for (JsonObject saved : savedArr) {
      if (saved["ssid"].as<String>() == foundSSID) {
        if (currentRSSI > bestRSSI) {
          bestRSSI = currentRSSI;
          bestSSID = foundSSID;
          bestPass = saved["pass"].as<String>();
        }
      }
    }
  }
  WiFi.scanDelete();

  if (bestSSID.length() > 0) {
    addLog("WIFI", "Ket noi: " + bestSSID);
    WiFi.begin(bestSSID.c_str(), bestPass.c_str());
  }
}

void onMqttMessage(char* topic, byte* payload, unsigned int length) {
  String topicStr = String(topic);

  if (topicStr != "deskflow/display" && topicStr != "deskflow/schedule") {
    String body = "";
    for (unsigned int i = 0; i < length; i++) body += (char)payload[i];
    addLog("MQTT_RX", topicStr + " -> " + body);
  }

  DynamicJsonDocument doc(8192);
  if (deserializeJson(doc, payload, length)) return;

  if (topicStr == "deskflow/telemetry") {
    if (doc.containsKey("server")) {
      srvStats.cpu = doc["server"]["cpu"] | 0.0;
      srvStats.ram_pct = doc["server"]["ram_pct"] | 0.0;
      srvStats.ram_used = doc["server"]["ram_used_mb"] | 0;
      srvStats.uptime = doc["server"]["uptime"] | "0h 0m";
    }
    if (doc.containsKey("laptop")) {
      srvStats.lp_cpu = doc["laptop"]["cpu"] | 0.0;
      srvStats.lp_ram = doc["laptop"]["ram_pct"] | 0.0;
      srvStats.lp_bat = doc["laptop"]["battery"] | 0;
    }
    screenNeedUpdate = true;
  } 
  else if (topicStr == "deskflow/weather") {
    srvStats.weatherText = doc["weather"] | "28*C";
    if (doc.containsKey("city")) srvStats.weatherCity = doc["city"].as<String>();
    screenNeedUpdate = true;
    addLog("WEATHER", "Cap nhat: " + srvStats.weatherCity + " " + srvStats.weatherText);
  } 
  else if (topicStr == "deskflow/laptop_stats") {
    srvStats.lp_cpu = doc["cpu"] | 0.0;
    srvStats.lp_ram = doc["ram_pct"] | 0.0;
    srvStats.lp_bat = doc["battery"] | 0;
    screenNeedUpdate = true;
  } 
  else if (topicStr == "deskflow/quote") {
    if (doc.containsKey("quote")) {
      srvStats.quoteText = doc["quote"].as<String>();
      screenNeedUpdate = true;
      addLog("QUOTE", "Cap nhat quote moi: " + srvStats.quoteText);
    }
  }
  else if (topicStr == "deskflow/clock_style") {
    handleClockConfigPayload(doc);
    screenNeedUpdate = true;
  }
  else if (topicStr == "deskflow/focus/status") {
    focusEnabled = doc["enabled"] | false;
    focusActive = doc["focus_active"] | false;
    focusPhase = doc["phase"] | (focusEnabled ? "WORK" : "IDLE");
    focusSessionType = doc["session_type"] | (doc["cycle_count"] ? "pomodoro" : "timed");
    focusRemainingSeconds = doc["remaining_seconds"] | 0;
    focusTotalSeconds = doc["total_seconds"] | 0;
    focusCycleCount = doc["cycle_count"] | 0;
    focusStatusReceivedMillis = millis();
    screenNeedUpdate = true;
    addLog("FOCUS", "Sync: phase=" + focusPhase + " rem=" + String(focusRemainingSeconds) + "s");
  }
  else if (topicStr == "deskflow/schedule" || topicStr == "deskflow/display") {
    String action = doc["action"] | "";

    if (topicStr == "deskflow/display" && action == "clock_style") {
      handleClockConfigPayload(doc);
      screenNeedUpdate = true;
    }
    else if (topicStr == "deskflow/display" && action == "switch_view") {
      if (doc.containsKey("view")) srvStats.activeView = doc["view"].as<String>();
      if (doc.containsKey("math_q")) currentMathQuestion = doc["math_q"].as<String>();
      screenNeedUpdate = true;
    }
    else if (topicStr == "deskflow/display" && action == "sticky_note") {
      if (doc.containsKey("note")) {
        srvStats.stickyNote = doc["note"].as<String>();
        srvStats.stickyNoteId = doc["note_id"] | (doc["id"] | "");
        prefs.putString("stk_note", srvStats.stickyNote);
        prefs.putString("stk_id", srvStats.stickyNoteId);
        screenNeedUpdate = true;
        addLog("NOTE", "Sticky note updated: " + srvStats.stickyNote);
      }
    }
    else if (topicStr == "deskflow/display" && action == "clear_sticky_note") {
      srvStats.stickyNote = "";
      srvStats.stickyNoteId = "";
      prefs.putString("stk_note", "");
      prefs.putString("stk_id", "");
      for (int i = 0; i < stationNotesCount; i++) {
        stationNotes[i].isPinned = false;
      }
      screenNeedUpdate = true;
      addLog("NOTE", "Sticky note cleared from station display.");
    }
    else if (topicStr == "deskflow/display" && action == "exam_countdown") {
      srvStats.countdownName = doc["name"] | "";
      srvStats.countdownDays = doc["days_left"] | 0;
      srvStats.hasCountdown = (srvStats.countdownName.length() > 0);
      srvStats.countdownId = doc["countdown_id"] | (doc["id"] | "");
      prefs.putString("cd_name", srvStats.countdownName);
      prefs.putInt("cd_days", srvStats.countdownDays);
      prefs.putBool("cd_has", srvStats.hasCountdown);
      prefs.putString("cd_id", srvStats.countdownId);
      screenNeedUpdate = true;
      addLog("COUNTDOWN", "Countdown set: " + srvStats.countdownName + " (" + String(srvStats.countdownDays) + " days)");
    }
    else if (topicStr == "deskflow/display" && action == "config_darkmode") {
      String mode = doc["mode"] | "off";
      String start = doc["start"] | "22:00";
      String end = doc["end"] | "06:00";
      darkModeSetting = mode;
      darkModeStart = start;
      darkModeEnd = end;
      prefs.putString("dk_mode", darkModeSetting);
      prefs.putString("dk_start", darkModeStart);
      prefs.putString("dk_end", darkModeEnd);
      updateThemePalette();
      screenNeedUpdate = true;
      addLog("THEME", "Dark mode config updated: " + mode + " (" + start + " -> " + end + ")");
    }
    else if (topicStr == "deskflow/display" && action == "hardware_settings") {
      int incomingVer = doc["hardware_config_version"] | 0;
      bool forceSync = doc["force_sync"] | false;
      if (incomingVer > 0 && incomingVer < appliedHardwareConfigVersion && !forceSync) {
        addLog("HW", "Bo qua cau hinh cu: v" + String(incomingVer) + " < hien tai v" + String(appliedHardwareConfigVersion));
        sendHardwareSettingsAck(incomingVer, "rejected", "stale_version");
        return;
      }

      if (doc.containsKey("led_brightness")) {
        ledBrightness = doc["led_brightness"] | 100;
        prefs.putInt("led_bright", ledBrightness);
        if (ledState) {
          analogWrite(LED_PIN, (ledBrightness * 255) / 100);
        }
      }
      if (doc.containsKey("buzzer_enabled")) {
        buzzerEnabled = doc["buzzer_enabled"] | true;
        prefs.putBool("buzz_en", buzzerEnabled);
      }
      if (doc.containsKey("buzzer_duration")) {
        buzzerDurationSetting = doc["buzzer_duration"] | 40;
        prefs.putUInt("buzz_dur", buzzerDurationSetting);
      }
      if (doc.containsKey("darkmode_mode")) {
        darkModeSetting = doc["darkmode_mode"] | "off";
        prefs.putString("dk_mode", darkModeSetting);
      }
      if (doc.containsKey("darkmode_start")) {
        darkModeStart = doc["darkmode_start"] | "22:00";
        prefs.putString("dk_start", darkModeStart);
      }
      if (doc.containsKey("darkmode_end")) {
        darkModeEnd = doc["darkmode_end"] | "06:00";
        prefs.putString("dk_end", darkModeEnd);
      }

      if (incomingVer > 0) {
        appliedHardwareConfigVersion = incomingVer;
        prefs.putInt("hw_ver", appliedHardwareConfigVersion);
      }
      updateThemePalette();
      screenNeedUpdate = true;
      addLog("HW", "Hardware settings updated (v" + String(appliedHardwareConfigVersion) + ") from backend.");
      sendHardwareSettingsAck(appliedHardwareConfigVersion, "applied");
    }
    else if (topicStr == "deskflow/display" && action == "sync_notes") {
      JsonArray arr = doc["notes"].as<JsonArray>();
      stationNotesCount = 0;
      for (JsonObject n : arr) {
        if (stationNotesCount >= MAX_STATION_NOTES) break;
        stationNotes[stationNotesCount].id = n["id"].as<String>();
        stationNotes[stationNotesCount].title = n["title"].as<String>();
        stationNotes[stationNotesCount].content = n["content"].as<String>();
        stationNotes[stationNotesCount].isPinned = n["is_pinned"] | false;
        stationNotesCount++;
      }
      bool foundPinnedNote = false;
      for (int i = 0; i < stationNotesCount; i++) {
        if (stationNotes[i].isPinned) {
          srvStats.stickyNote = stationNotes[i].content;
          srvStats.stickyNoteId = stationNotes[i].id;
          prefs.putString("stk_note", srvStats.stickyNote);
          prefs.putString("stk_id", srvStats.stickyNoteId);
          foundPinnedNote = true;
          break;
        }
      }
      if (!foundPinnedNote) {
        srvStats.stickyNote = "";
        srvStats.stickyNoteId = "";
        prefs.putString("stk_note", "");
        prefs.putString("stk_id", "");
      }
      screenNeedUpdate = true;
      addLog("NOTES", "Synced " + String(stationNotesCount) + " notes.");
    }
    else if (topicStr == "deskflow/display" && action == "sync_countdowns") {
      JsonArray arr = doc["countdowns"].as<JsonArray>();
      stationCountdownsCount = 0;
      for (JsonObject c : arr) {
        if (stationCountdownsCount >= MAX_STATION_COUNTDOWNS) break;
        stationCountdowns[stationCountdownsCount].id = c["id"].as<String>();
        stationCountdowns[stationCountdownsCount].title = c["title"].as<String>();
        stationCountdowns[stationCountdownsCount].targetDate = c["target_date"].as<String>();
        stationCountdowns[stationCountdownsCount].daysLeft = c["days_left"] | 0;
        stationCountdowns[stationCountdownsCount].isExpired = c["is_expired"] | false;
        stationCountdowns[stationCountdownsCount].isPinned = c["is_pinned"] | false;
        stationCountdownsCount++;
      }
      bool foundPinnedCd = false;
      for (int i = 0; i < stationCountdownsCount; i++) {
        if (stationCountdowns[i].isPinned) {
          srvStats.countdownName = stationCountdowns[i].title;
          srvStats.countdownDays = stationCountdowns[i].daysLeft;
          srvStats.hasCountdown = !stationCountdowns[i].isExpired;
          srvStats.countdownId = stationCountdowns[i].id;
          prefs.putString("cd_name", srvStats.countdownName);
          prefs.putInt("cd_days", srvStats.countdownDays);
          prefs.putBool("cd_has", srvStats.hasCountdown);
          prefs.putString("cd_id", srvStats.countdownId);
          foundPinnedCd = true;
          break;
        }
      }
      if (!foundPinnedCd) {
        srvStats.countdownName = "";
        srvStats.hasCountdown = false;
        srvStats.countdownDays = -1;
        srvStats.countdownId = "";
        prefs.putString("cd_name", "");
        prefs.putInt("cd_days", -1);
        prefs.putBool("cd_has", false);
        prefs.putString("cd_id", "");
      }
      screenNeedUpdate = true;
      addLog("COUNTDOWN", "Synced " + String(stationCountdownsCount) + " countdowns.");
    }
    else if (topicStr == "deskflow/display" && action == "clear_countdown") {
      srvStats.countdownName = "";
      srvStats.hasCountdown = false;
      srvStats.countdownDays = -1;
      srvStats.countdownId = "";
      prefs.putString("cd_name", "");
      prefs.putInt("cd_days", -1);
      prefs.putBool("cd_has", false);
      prefs.putString("cd_id", "");
      for (int i = 0; i < stationCountdownsCount; i++) {
        stationCountdowns[i].isPinned = false;
      }
      screenNeedUpdate = true;
      addLog("COUNTDOWN", "Countdown cleared");
    }
    else if (topicStr == "deskflow/display" && action == "led") {
      String act = doc["act"] | "";
      if (act == "toggle") {
        ledBlinkMode = false;
        ledState = !ledState;
        analogWrite(LED_PIN, ledState ? ((ledBrightness * 255) / 100) : 0);
        addLog("LED", "Toggle LED: " + String(ledState ? "ON" : "OFF"));
      } else if (act == "blink") {
        ledBlinkMode = !ledBlinkMode;
        if (!ledBlinkMode) {
          ledState = false;
          analogWrite(LED_PIN, 0);
        }
        addLog("LED", "Blink Mode: " + String(ledBlinkMode ? "ON" : "OFF"));
      }
    }
    else if (topicStr == "deskflow/display" && action == "led_color") {
      String hexCol = doc["hex"] | "#FFFFFF";
      srvStats.lampColor = hexCol;
      prefs.putString("lamp_col", hexCol);
      ledState = true;
      analogWrite(LED_PIN, ((ledBrightness * 255) / 100));
      screenNeedUpdate = true;
      addLog("LED", "Lamp Color set to: " + hexCol);
    }
    else if (topicStr == "deskflow/display" && action == "led_off") {
      ledState = false;
      ledBlinkMode = false;
      analogWrite(LED_PIN, 0);
      screenNeedUpdate = true;
      addLog("LED", "Lamp turned off");
    }

    if (action == "spotify_update") {
      srvStats.currentTrack = doc["track"] | "Unknown Track";
      srvStats.currentArtist = doc["artist"] | "N/A";
      srvStats.progressPct = doc["progress_pct"] | 0;
      srvStats.lyricCurr = doc["lyric_curr"] | "";
      srvStats.lyricNext = doc["lyric_next"] | "";
      
      if (srvStats.activeView == "lyrics") {
        drawPhysicalScreen();
      } else {
        screenNeedUpdate = true;
      }
      return;
    }

    if (doc.containsKey("day_name")) srvStats.tkbDay = doc["day_name"].as<String>();
    if (doc.containsKey("morning")) srvStats.tkbMorning = doc["morning"].as<String>();
    if (doc.containsKey("has_afternoon")) srvStats.tkbHasAfternoon = doc["has_afternoon"].as<bool>();
    if (doc.containsKey("afternoon")) srvStats.tkbAfternoon = doc["afternoon"].as<String>();
    if (doc.containsKey("uniform")) srvStats.tkbUniform = doc["uniform"].as<String>();

    if (doc.containsKey("tom_day")) srvStats.tkbTomDay = doc["tom_day"].as<String>();
    if (doc.containsKey("tom_morning")) srvStats.tkbTomMorning = doc["tom_morning"].as<String>();
    if (doc.containsKey("tom_has_afternoon")) srvStats.tkbTomHasAfternoon = doc["tom_has_afternoon"].as<bool>();
    if (doc.containsKey("tom_afternoon")) srvStats.tkbTomAfternoon = doc["tom_afternoon"].as<String>();
    if (doc.containsKey("tom_uniform")) srvStats.tkbTomUniform = doc["tom_uniform"].as<String>();

    screenNeedUpdate = true;
  } 
  else if (topicStr == "deskflow/alarm") {
    String action = doc["action"] | "";
    if (action == "stop_alarm") {
      isAlarmActive = false;
      isBuzzing = false;
      stopMorsePlayback();
      melodyStop();
      stopBuzzer();
      digitalWrite(LED_PIN, LOW);
      addLog("ALARM", ">>> DA GIAI DUNG! TAT CHUONG.");
      srvStats.activeView = "tkb";
      screenNeedUpdate = true;
    } else if (action == "math_alarm_trigger") {
      stopMorsePlayback();
      isAlarmActive = true;
      lastAlarmBeepTime = millis();
      if (doc.containsKey("math_q")) {
        currentMathQuestion = doc["math_q"].as<String>();
      }
      srvStats.activeView = "math_alarm";
      screenNeedUpdate = true;
      addLog("ALARM", ">>> MATH ALARM START: KEU LIEN TUC CHO TOI KHI GIAI!");
    } else if (action == "morse_play") {
      String pattern = doc["pattern"] | "";
      unsigned int unitMs = doc["unit_ms"] | 80;
      unsigned int freq = doc["freq"] | 2400;
      startMorsePlayback(pattern, unitMs, freq);
    } else if (action == "morse_stop") {
      stopMorsePlayback();
    } else if (action == "melody_play") {
      stopMorsePlayback();
      String title = doc["title"] | "Custom Melody";
      if (doc.containsKey("notes")) {
        JsonArray arr = doc["notes"].as<JsonArray>();
        int count = arr.size();
        if (count > 0) {
          melodyStop();
          int nNotes = min(count, (int)MELODY_MAX_NOTES);
          for (int i = 0; i < nNotes; i++) {
            JsonArray pair = arr[i].as<JsonArray>();
            uint16_t freq = pair[0] | 0;
            uint16_t dur = pair[1] | 100;
            melodyNotes[i] = { freq, dur };
          }
          melodyCount = nNotes;
          melodyCurrentTitle = title;
          melodyIdx = 0;
          melodyPlaying = true;
          melodyToneOn = false;
          melodyStepStart = 0;
          addLog("MELODY", "Phat nhac: " + title + " (" + String(melodyCount) + " not)");
        }
      } else {
        melodyCurrentTitle = "Sample Melody";
        if (melodyStart(SAMPLE_MELODY_MID, sizeof(SAMPLE_MELODY_MID))) {
          addLog("MELODY", "Phat giai dieu mau (" + String(melodyCount) + " not)");
        } else {
          addLog("MELODY", "Loi doc file MIDI");
        }
      }
      screenNeedUpdate = true;
    } else if (action == "melody_stop") {
      melodyStop();
      stopBuzzer();
      addLog("MELODY", "Da dung phat nhac MIDI.");
      screenNeedUpdate = true;
    } else {
      unsigned long d = doc["duration"] | 250;
      triggerBuzz(d, true);
    }
  }
}

void reconnectMqtt() {
  if (!mqtt.connected() && WiFi.status() == WL_CONNECTED) {
    if (mqttBroker.length() == 0) {
      mqttBroker = "10th012013.duckdns.org";
      mqtt.setServer(mqttBroker.c_str(), mqttPort);
    }

    String clientId = "DeskFlow-Station-" + String(random(0xffff), HEX);
    addLog("MQTT", "Dang ket noi toi [" + mqttBroker + ":" + String(mqttPort) + "]...");

    if (mqtt.connect(clientId.c_str())) {
      addLog("MQTT", ">>> DA KET NOI BROKER THANH CONG!");
      mqtt.subscribe("deskflow/display");
      mqtt.subscribe("deskflow/telemetry");
      mqtt.subscribe("deskflow/laptop_stats");
      mqtt.subscribe("deskflow/schedule");
      mqtt.subscribe("deskflow/alarm");
      mqtt.subscribe("deskflow/solve");
      mqtt.subscribe("deskflow/weather");
      mqtt.subscribe("deskflow/quote");
      mqtt.subscribe("deskflow/clock_style");
      mqtt.subscribe("deskflow/focus/status");
      sendClockStyleRequest();
      sendHardwareSettingsRequest();
      updateThemePalette();
    } else {
      int rc = mqtt.state();
      addLog("MQTT", "Loi ket noi! rc=" + String(rc));
    }
  }
}

void publishDeviceStatus() {
  if (!mqtt.connected()) return;
  float v = readBatteryVoltage();
  int pct = calculateBatteryPct(v);

  DynamicJsonDocument doc(256);
  doc["battery_pct"] = pct;
  doc["voltage"] = serialized(String(v, 2));
  doc["wifi_rssi"] = WiFi.RSSI();

  String out;
  serializeJson(doc, out);
  mqtt.publish("deskflow/status_update", out.c_str());
}

void sendButtonAction(const char* action) {
  addLog("BUTTON", String(action));
  DynamicJsonDocument doc(128);
  doc["action"] = action;
  String out;
  serializeJson(doc, out);
  mqtt.publish("deskflow/button_action", out.c_str());
}

// ================= GIAO DIỆN WEB =================
const char HTML_PAGE[] PROGMEM = R"rawliteral(
<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="utf-8">
  <title>DeskFlow — Station Portal</title>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <style>
    :root {
      --bg: #0b0f19; --card: #151f32; --border: #23334d;
      --accent: #38bdf8; --green: #10b981; --purple: #a855f7;
      --text: #f8fafc; --muted: #94a3b8; --red: #ef4444; --amber: #f59e0b;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: system-ui, sans-serif; background: var(--bg); color: var(--text); padding: 14px 10px; display: flex; justify-content: center; }
    .wrap { width: 100%; max-width: 600px; display: flex; flex-direction: column; gap: 14px; }
    .card { background: var(--card); border: 1px solid var(--border); border-radius: 12px; padding: 14px; box-shadow: 0 4px 14px rgba(0,0,0,0.4); }
    .topbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
    .title { font-size: 16px; font-weight: bold; color: var(--accent); }
    .badge { font-size: 11px; font-weight: 600; padding: 3px 8px; border-radius: 6px; border: 1px solid var(--green); color: var(--green); background: rgba(16,185,129,0.15); }
    .badge-ip { font-size: 11px; font-weight: bold; font-family: monospace; padding: 3px 8px; border-radius: 6px; border: 1px solid #38bdf8; color: #38bdf8; background: rgba(56,189,248,0.12); }
    
    .screen-bezel {
      background: #1e293b;
      border: 3px solid #334155;
      border-radius: 18px;
      padding: 10px;
      width: 270px;
      height: 350px;
      margin: 10px auto;
      box-shadow: 0 0 20px rgba(0, 0, 0, 0.8), inset 0 0 10px rgba(0,0,0,0.5);
      display: flex;
      justify-content: center;
      align-items: center;
    }
    .screen-display {
      background: #000;
      width: 240px;
      height: 320px;
      border-radius: 8px;
      position: relative;
      overflow: hidden;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      padding: 10px;
      color: #fff;
      font-family: monospace;
      border: 1px solid #111;
    }
    .scr-header { display: flex; justify-content: space-between; font-size: 11px; color: var(--muted); border-bottom: 1px solid #222; padding-bottom: 4px; }
    .scr-body { flex: 1; display: flex; flex-direction: column; justify-content: center; align-items: center; text-align: center; }
    .scr-footer { font-size: 10px; color: #555; text-align: center; border-top: 1px solid #222; padding-top: 4px; }

    .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin: 10px 0; }
    .box { background: #090d16; border: 1px solid #1e293b; padding: 10px; border-radius: 8px; }
    .val { font-size: 18px; font-weight: bold; font-family: monospace; color: var(--accent); margin-top: 2px; }
    .lbl { font-size: 11px; color: var(--muted); }
    
    .btn-row { display: flex; gap: 8px; margin-top: 6px; }
    .btn { flex: 1; padding: 10px 4px; border: none; border-radius: 6px; font-size: 12px; font-weight: bold; cursor: pointer; color: white; transition: 0.15s; }
    .btn:active { transform: scale(0.96); }
    .btn-blue { background: #0284c7; }
    .btn-green { background: #059669; }
    .btn-purple { background: #7c3aed; }
    .btn-amber { background: var(--amber); color: #000; }
    .btn-action { background: #3b82f6; color: white; border: none; padding: 8px 14px; border-radius: 6px; font-weight: bold; cursor: pointer; }
    
    .form-col { display: flex; flex-direction: column; gap: 8px; }
    .input-row { display: flex; gap: 8px; }
    input, select { flex: 1; background: #090d16; border: 1px solid var(--border); border-radius: 6px; padding: 8px 10px; color: white; font-size: 13px; }
    
    .saved-list { display: flex; flex-direction: column; gap: 6px; margin-top: 10px; }
    .saved-item { display: flex; justify-content: space-between; align-items: center; background: #090d16; border: 1px solid var(--border); padding: 8px 12px; border-radius: 6px; font-size: 13px; }
    .del-btn { background: transparent; border: 1px solid var(--red); color: var(--red); padding: 2px 8px; border-radius: 4px; font-size: 11px; cursor: pointer; }

    .term { background: #05080e; border: 1px solid #1a2638; border-radius: 8px; padding: 10px; height: 180px; overflow-y: auto; font-family: monospace; font-size: 11px; color: #38bdf8; white-space: pre-wrap; }
    .term-btn { background: transparent; border: 1px solid var(--border); color: var(--muted); padding: 2px 6px; border-radius: 4px; font-size: 11px; cursor: pointer; }
  </style>
</head>
<body>
  <div class="wrap">
    <div class="card" style="padding: 10px 14px; margin-bottom: -2px;">
      <div style="display:flex; justify-content:space-between; align-items:center;">
        <div style="display:flex; align-items:center; gap:8px;">
          <span style="font-size:12px; color:var(--muted);">🌐 LOCAL IP:</span>
          <span class="badge-ip" id="hudLocalIp">192.168.4.1</span>
        </div>
        <div class="badge" id="hudConn">CONNECTED</div>
      </div>
    </div>

    <div class="card">
      <div class="topbar">
        <div class="title">📺 ST7789 Live Screen Emulator</div>
        <div class="badge" id="hudScreenView">VIEW: MAIN</div>
      </div>
      
      <div class="screen-bezel">
        <div class="screen-display" id="scrDisplay">
          <div class="scr-header">
            <span id="scrTime">12:00 PM</span>
            <span id="scrHeaderRight">⚡ 100%</span>
          </div>
          <div class="scr-body" id="scrBody"></div>
          <div class="scr-footer" id="scrFooter">DeskFlow Station OS</div>
        </div>
      </div>

      <div style="font-size:12px; color:var(--muted); margin: 4px 0 4px;">Điều Khiển Nhanh:</div>
      <div class="btn-row">
        <button class="btn btn-purple" onclick="sendAct('press_prev')">⏮ Prev</button>
        <button class="btn btn-blue" onclick="sendAct('press_play')">⏯ Play/Pause</button>
        <button class="btn btn-green" onclick="sendAct('press_next')">⏭ Next</button>
      </div>

      <div style="margin-top:8px;">
        <button class="btn" style="background:#e11d48; width:100%; padding:10px;" onclick="sendAct('hold_play')">
          🔄 Giữ Nút Play (Chuyển Màn Hình)
        </button>
      </div>

      <div style="margin-top:8px;" class="btn-row">
        <button class="btn btn-blue" id="btnLedToggle" onclick="controlLed('toggle')">💡 Bật/Tắt LED</button>
        <button class="btn btn-amber" id="btnLedBlink" onclick="controlLed('blink')">✨ Nháy LED</button>
        <button class="btn" style="background:#f59e0b; color:#000;" onclick="triggerBuzz()">🔔 Bíp Còi</button>
      </div>
    </div>

    <div class="card">
      <div class="topbar">
        <div class="title" style="font-size:14px; color:#fff;">📊 Realtime System Telemetry</div>
      </div>
      
      <div style="font-size:11px; font-weight:bold; color:var(--accent); margin:6px 0 2px;">💻 LAPTOP STATS</div>
      <div style="display:grid; grid-template-columns: 1fr 1fr 1fr; gap:6px;">
        <div class="box"><div class="lbl">PIN</div><div class="val" id="hudLpBat" style="font-size:15px; color:#10b981;">--%</div></div>
        <div class="box"><div class="lbl">RAM</div><div class="val" id="hudLpRam" style="font-size:15px;">--%</div></div>
        <div class="box"><div class="lbl">CPU</div><div class="val" id="hudLpCpu" style="font-size:15px;">--%</div></div>
      </div>

      <div style="font-size:11px; font-weight:bold; color:var(--purple); margin:10px 0 2px;">🖥 DEBIAN SERVER</div>
      <div style="display:grid; grid-template-columns: 1fr 1fr 1fr; gap:6px;">
        <div class="box"><div class="lbl">UPTIME</div><div class="val" id="hudSrvUp" style="font-size:14px; color:#f59e0b;">--</div></div>
        <div class="box"><div class="lbl">RAM</div><div class="val" id="hudRam" style="font-size:15px;">--%</div></div>
        <div class="box"><div class="lbl">CPU</div><div class="val" id="hudCpu" style="font-size:15px;">--%</div></div>
      </div>

      <div style="font-size:11px; font-weight:bold; color:#94a3b8; margin:10px 0 2px;">⚡ ESP32 STATION</div>
      <div style="display:grid; grid-template-columns: 1fr; gap:6px;">
        <div class="box"><div class="lbl">PIN TRẠM (GPIO 34)</div><div class="val" id="hudBat" style="font-size:14px;">--V</div></div>
      </div>
    </div>

    <div class="card">
      <div class="topbar">
        <div class="title" style="font-size:14px; color:#fff;">📶 Quản Lý Nhiều Wi-Fi</div>
        <button class="term-btn" onclick="scanWifi()">🔄 Quét</button>
      </div>
      <div class="form-col">
        <div class="input-row">
          <select id="wifiList"><option value="">-- Đang quét Wi-Fi... --</option></select>
        </div>
        <div class="input-row">
          <input type="password" id="wifiPass" placeholder="Mật khẩu Wi-Fi">
          <button class="btn-action" style="background:#059669;" onclick="connectWifi()">Lưu & Nối</button>
        </div>
      </div>
      <div class="saved-list" id="savedWifiList"></div>
    </div>

    <div class="card">
      <div class="topbar"><div class="title" style="font-size:14px; color:#fff;">⚙ IP Broker MQTT</div></div>
      <div class="input-row">
        <input type="text" id="cfgBroker" placeholder="10th012013.duckdns.org">
        <input type="number" id="cfgPort" style="max-width:80px;" value="1883">
        <button class="btn-action" onclick="saveBroker()">Lưu</button>
      </div>
    </div>

    <div class="card">
      <div class="topbar">
        <div class="title" style="font-size:12px; color:var(--muted); text-transform:uppercase;">MQTT & Event Logs</div>
        <button class="term-btn" onclick="clearLogs()">Xóa log</button>
      </div>
      <div class="term" id="termLogs"></div>
    </div>
  </div>

  <script>
    function controlLed(a) { fetch('/api/led?act=' + a); }
    function triggerBuzz() { 
      fetch('/api/buzz?t=' + Date.now())
        .then(r => r.text())
        .then(res => console.log('Buzzer OK:', res))
        .catch(e => console.log(e));
    }
    function sendAct(a) { fetch('/api/action?type=' + a); }
    function clearLogs() { fetch('/api/clear_logs').then(() => document.getElementById('termLogs').innerText = ""); }

    function loadSavedWifi() {
      fetch('/api/get_saved_wifi').then(r => r.json()).then(list => {
        const box = document.getElementById('savedWifiList');
        box.innerHTML = '';
        if (list.length === 0) { box.innerHTML = '<div style="font-size:12px; color:var(--muted);">Chưa có mạng lưu</div>'; return; }
        list.forEach(i => {
          const row = document.createElement('div');
          row.className = 'saved-item';
          row.innerHTML = '<span>📶 ' + i.ssid + '</span><button class="del-btn" onclick="deleteWifi(\'' + encodeURIComponent(i.ssid) + '\')">Xóa</button>';
          box.appendChild(row);
        });
      });
    }

    function scanWifi() {
      const sel = document.getElementById('wifiList');
      sel.innerHTML = '<option value="">⏳ Đang quét...</option>';
      fetch('/api/scan_wifi')
        .then(r => r.json())
        .then(res => {
          sel.innerHTML = '';
          if (!res || res.length === 0) { 
            sel.innerHTML = '<option value="">Không tìm thấy Wi-Fi (Thử lại)</option>'; 
            return; 
          }
          res.forEach(w => {
            const o = document.createElement('option');
            o.value = w.ssid;
            o.innerText = w.ssid + ' (' + w.rssi + ' dBm)';
            sel.appendChild(o);
          });
        })
        .catch(() => {
          sel.innerHTML = '<option value="">Lỗi quét Wi-Fi</option>';
        });
    }

    function connectWifi() {
      const s = document.getElementById('wifiList').value;
      const p = document.getElementById('wifiPass').value;
      if (!s) { alert('Chọn Wi-Fi!'); return; }
      fetch('/api/connect_wifi?ssid=' + encodeURIComponent(s) + '&pass=' + encodeURIComponent(p)).then(() => loadSavedWifi());
    }

    function deleteWifi(s) {
      if (confirm('Xóa Wi-Fi này?')) fetch('/api/delete_wifi?ssid=' + s).then(() => loadSavedWifi());
    }

    function saveBroker() {
      const ip = document.getElementById('cfgBroker').value;
      const port = document.getElementById('cfgPort').value;
      fetch('/api/config_broker?ip=' + encodeURIComponent(ip) + '&port=' + port).then(r => r.text()).then(t => alert(t));
    }

    scanWifi();
    loadSavedWifi();

    function renderVirtualScreen(d) {
      const view = d.view || "main";
      document.getElementById('hudScreenView').innerText = 'VIEW: ' + view.toUpperCase();
      document.getElementById('scrHeaderRight').innerText = '⚡ ' + d.batPct + '%';

      const now = new Date();
      let hours = now.getHours();
      const ampm = hours >= 12 ? 'PM' : 'AM';
      hours = hours % 12;
      hours = hours ? hours : 12;
      const timeStr = String(hours).padStart(2, '0') + ':' + String(now.getMinutes()).padStart(2, '0') + ' ' + ampm;
      document.getElementById('scrTime').innerText = timeStr;
      const body = document.getElementById('scrBody');

      if (view === 'main') {
        body.innerHTML = `
          <div style="font-size:32px; font-weight:bold; color:#38bdf8;">${timeStr}</div>
          <div style="font-size:13px; color:#94a3b8; margin:6px 0;">Hạ Long • ${d.weatherText || '28°C Nắng'}</div>
          <div style="background:#111; padding:8px; border-radius:6px; width:90%; margin-top:10px; border:1px solid #222;">
            <div style="font-size:12px; color:#10b981; overflow:hidden; white-space:nowrap;">🎵 ${d.track}</div>
          </div>
        `;
      } else if (view === 'stats') {
        body.innerHTML = `
          <div style="font-size:14px; font-weight:bold; color:#a855f7; margin-bottom:10px;">HARDWARE STATS</div>
          <div style="width:100%; font-size:11px; text-align:left; padding:0 8px;">
            <div>Laptop: <b style="color:#38bdf8;">${d.lp_bat}% | ${d.lp_ram}% | ${d.lp_cpu}%</b></div>
            <div style="background:#222; height:6px; border-radius:3px; margin:2px 0 8px;">
              <div style="background:#38bdf8; width:${Math.min(100, Math.max(0, d.lp_bat))}%; height:100%;"></div>
            </div>

            <div>Server: <b style="color:#10b981;">${d.uptime} | ${d.ram}% | ${d.cpu}%</b></div>
            <div style="background:#222; height:6px; border-radius:3px; margin:2px 0;">
              <div style="background:#10b981; width:${Math.min(100, Math.max(0, d.ram))}%; height:100%;"></div>
            </div>
          </div>
        `;
      } else if (view === 'tkb') {
        const hasAfternoon = d.tkbHasAfternoon && d.tkbAfternoon && d.tkbAfternoon.length > 0;
        const tomHasAfternoon = d.tkbTomHasAfternoon && d.tkbTomAfternoon && d.tkbTomAfternoon.length > 0;
        body.innerHTML = `
          <div style="font-size:13px; font-weight:bold; color:#f59e0b; margin-bottom:4px;">THỜI KHÓA BIỂU</div>
          <div style="width:100%; font-size:10.5px; text-align:left; padding:0 6px; line-height:1.4;">
            <div style="color:#38bdf8; font-weight:bold;">${d.tkbDay || 'Hôm nay'}:</div>
            <div>• <b style="color:#facc15;">Sáng:</b> <span style="color:#fff;">${d.tkbMorning || 'Nghỉ'}</span></div>
            ${hasAfternoon ? `<div>• <b style="color:#facc15;">Chiều:</b> <span style="color:#fff;">${d.tkbAfternoon}</span></div>` : ''}
            <div>• <b style="color:#10b981;">Đ.phục:</b> <span style="color:#38bdf8;">${d.tkbUniform || 'Tự do'}</span></div>
            
            <div style="border-top:1px solid #23334d; margin:6px 0;"></div>

            <div style="color:#38bdf8; font-weight:bold;">${d.tkbTomDay || 'Ngày mai'}:</div>
            <div>• <b style="color:#facc15;">Sáng:</b> <span style="color:#fff;">${d.tkbTomMorning || 'Nghỉ'}</span></div>
            ${tomHasAfternoon ? `<div>• <b style="color:#facc15;">Chiều:</b> <span style="color:#fff;">${d.tkbTomAfternoon}</span></div>` : ''}
            <div>• <b style="color:#10b981;">Đ.phục:</b> <span style="color:#38bdf8;">${d.tkbUniform || 'Tự do'}</span></div>
          </div>
        `;
      } else if (view === 'weather') {
        body.innerHTML = `
          <div style="font-size:42px;">⛅</div>
          <div style="font-size:20px; font-weight:bold; color:#facc15; margin:6px 0;">${d.weatherText || '28°C'}</div>
          <div style="font-size:13px; color:#94a3b8;">${d.weatherCity || 'Hạ Long'}</div>
        `;
      } else if (view === 'lyrics') {
        body.innerHTML = `
          <div style="font-size:11px; color:#94a3b8; max-width:200px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;">${d.track}</div>
          <div style="font-size:16px; font-weight:bold; color:#facc15; margin:10px 0 6px; padding:0 4px; line-height: 1.3;">
            ${d.lyricCurr ? d.lyricCurr : '♪ ♪ ♪'}
          </div>
          <div style="font-size:11px; color:#64748b; padding:0 4px;">
            ${d.lyricNext ? d.lyricNext : ''}
          </div>
          <div style="width:80%; background:#222; height:4px; border-radius:2px; margin-top:10px;">
            <div style="background:#10b981; width:${d.progressPct}%; height:100%;"></div>
          </div>
        `;
      }
    }

    setInterval(() => {
      fetch('/api/status').then(r => r.json()).then(d => {
        renderVirtualScreen(d);

        document.getElementById('hudLpBat').innerText = (d.lp_bat || 0) + '%';
        document.getElementById('hudLpRam').innerText = (d.lp_ram || 0) + '%';
        document.getElementById('hudLpCpu').innerText = (d.lp_cpu || 0) + '%';

        document.getElementById('hudSrvUp').innerText = d.uptime || '0h';
        document.getElementById('hudRam').innerText = d.ram + '%';
        document.getElementById('hudCpu').innerText = d.cpu + '%';

        document.getElementById('hudBat').innerText = d.batV + 'V (' + d.batPct + '%)';
        document.getElementById('hudConn').innerText = d.conn;
        if (d.ip) {
          document.getElementById('hudLocalIp').innerText = d.ip;
        }

        const btnToggle = document.getElementById('btnLedToggle');
        if (d.ledBlink) { btnToggle.innerText = '💡 LED: NHÁY ✨'; btnToggle.style.background = '#f59e0b'; }
        else if (d.ledState) { btnToggle.innerText = '💡 LED: BẬT'; btnToggle.style.background = '#10b981'; }
        else { btnToggle.innerText = '💡 LED: TẮT'; btnToggle.style.background = '#0284c7'; }
      }).catch(e => console.log(e));

      fetch('/api/logs').then(r => r.text()).then(t => {
        const box = document.getElementById('termLogs');
        const atBottom = box.scrollHeight - box.clientHeight <= box.scrollTop + 30;
        box.innerText = t;
        if (atBottom) box.scrollTop = box.scrollHeight;
      }).catch(e => console.log(e));
    }, 1000);
  </script>
</body>
</html>
)rawliteral";

void handleRoot() { server.send(200, "text/html", HTML_PAGE); }
void handleApiBuzz() { 
  triggerBuzz(250); 
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "text/plain", "Buzzed OK \n"); 
}

void handleApiMorse() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  if (server.hasArg("act") && server.arg("act") == "stop") {
    stopMorsePlayback();
    server.send(200, "text/plain", "Morse stopped\n");
    return;
  }
  if (server.hasArg("pattern")) {
    String pattern = server.arg("pattern");
    unsigned int unit = 80;
    if (server.hasArg("unit")) unit = server.arg("unit").toInt();
    startMorsePlayback(pattern, unit, 2400);
    server.send(200, "text/plain", "Morse playing\n");
    return;
  }
  server.send(400, "text/plain", "Missing pattern or act=stop\n");
}

void handleApiLed() {
  if (server.hasArg("act")) {
    String act = server.arg("act");
    if (act == "blink") {
      ledBlinkMode = !ledBlinkMode;
      if (!ledBlinkMode) {
        ledState = false;
        analogWrite(LED_PIN, 0);
      }
    } else if (act == "toggle") {
      ledBlinkMode = false;
      ledState = !ledState;
      analogWrite(LED_PIN, ledState ? ((ledBrightness * 255) / 100) : 0);
    }
    server.send(200, "text/plain", "LED OK \n");
  } else server.send(400, "text/plain", "Missing act");
}

void handleScanWifi() {
  int n = WiFi.scanNetworks(false, true);
  
  if (n < 0) {
    server.send(200, "application/json", "[]");
    return;
  }

  String json = "[";
  for (int i = 0; i < n; ++i) {
    if (WiFi.SSID(i).length() == 0) continue;
    if (json.length() > 1) json += ",";
    json += "{\"ssid\":\"" + WiFi.SSID(i) + "\",\"rssi\":" + String(WiFi.RSSI(i)) + "}";
  }
  json += "]";
  WiFi.scanDelete();
  server.send(200, "application/json", json);
}

void handleConnectWifi() {
  if (server.hasArg("ssid")) {
    String ssid = server.arg("ssid");
    String pass = server.arg("pass");
    saveNetworkToArray(ssid, pass);
    WiFi.begin(ssid.c_str(), pass.c_str());
    server.send(200, "text/plain", "OK");
  } else server.send(400, "text/plain", "Missing ssid");
}

void handleGetSavedWifi() {
  DynamicJsonDocument doc(8192);
  deserializeJson(doc, wifiListJson);
  JsonArray arr = doc.as<JsonArray>();
  String json = "[";
  for (size_t i = 0; i < arr.size(); i++) {
    if (i > 0) json += ",";
    json += "{\"ssid\":\"" + arr[i]["ssid"].as<String>() + "\"}";
  }
  json += "]";
  server.send(200, "application/json", json);
}

void handleDeleteWifi() {
  if (server.hasArg("ssid")) {
    removeNetworkFromArray(server.arg("ssid"));
    server.send(200, "text/plain", "OK");
  } else server.send(400, "text/plain", "Missing ssid");
}

void handleConfigBroker() {
  if (server.hasArg("ip")) {
    mqttBroker = server.arg("ip");
    if (mqttBroker.length() == 0) mqttBroker = "10th012013.duckdns.org";
    if (server.hasArg("port")) mqttPort = server.arg("port").toInt();
    prefs.putString("broker", mqttBroker);
    prefs.putInt("b_port", mqttPort);
    mqtt.setServer(mqttBroker.c_str(), mqttPort);
    server.send(200, "text/plain; charset=utf-8", "Đã lưu Broker: " + mqttBroker);
  } else server.send(400, "text/plain", "Missing ip");
}

void handleApiStatus() {
  float v = readBatteryVoltage();
  DynamicJsonDocument doc(1024);
  doc["cpu"] = srvStats.cpu;
  doc["ram"] = srvStats.ram_pct;
  doc["ram_used"] = srvStats.ram_used;
  doc["uptime"] = srvStats.uptime;

  doc["lp_cpu"] = srvStats.lp_cpu;
  doc["lp_ram"] = srvStats.lp_ram;
  doc["lp_bat"] = srvStats.lp_bat;

  doc["batV"] = serialized(String(v, 2));
  doc["batPct"] = calculateBatteryPct(v);
  doc["net"] = srvStats.internet;
  doc["mqtt"] = mqtt.connected();
  doc["ledState"] = ledState;
  doc["ledBlink"] = ledBlinkMode;
  doc["view"] = srvStats.activeView;
  doc["focus_enabled"] = focusEnabled;
  doc["focus_active"] = focusActive;
  doc["focus_phase"] = focusPhase;
  doc["focus_session_type"] = focusSessionType;
  doc["focus_rem"] = focusRemainingSeconds;
  doc["track"] = srvStats.currentTrack;
  doc["artist"] = srvStats.currentArtist;
  doc["progressPct"] = srvStats.progressPct;
  doc["lyricCurr"] = srvStats.lyricCurr;
  doc["lyricNext"] = srvStats.lyricNext;
  doc["weatherText"] = srvStats.weatherText;
  doc["weatherCity"] = srvStats.weatherCity;

  doc["tkbDay"] = srvStats.tkbDay;
  doc["tkbMorning"] = srvStats.tkbMorning;
  doc["tkbHasAfternoon"] = srvStats.tkbHasAfternoon;
  doc["tkbAfternoon"] = srvStats.tkbAfternoon;
  doc["tkbUniform"] = srvStats.tkbUniform;

  doc["tkbTomDay"] = srvStats.tkbTomDay;
  doc["tkbTomMorning"] = srvStats.tkbTomMorning;
  doc["tkbTomHasAfternoon"] = srvStats.tkbTomHasAfternoon;
  doc["tkbTomAfternoon"] = srvStats.tkbTomAfternoon;
  doc["tkbTomUniform"] = srvStats.tkbTomUniform;

  if (WiFi.status() == WL_CONNECTED) {
    doc["conn"] = "CONNECTED (" + WiFi.SSID() + ")";
    doc["ip"] = WiFi.localIP().toString();
  } else {
    doc["conn"] = "AP MODE";
    doc["ip"] = "192.168.4.1";
  }
  String out;
  serializeJson(doc, out);
  server.send(200, "application/json", out);
}

void cycleNextView() {
  if (srvStats.activeView == "main") {
    srvStats.activeView = "stats";
  } else if (srvStats.activeView == "stats") {
    srvStats.activeView = "tkb";
  } else if (srvStats.activeView == "tkb") {
    srvStats.activeView = "weather";
  } else if (srvStats.activeView == "weather") {
    srvStats.activeView = "lyrics";
  } else if (srvStats.activeView == "lyrics") {
    srvStats.activeView = "notes";
  } else if (srvStats.activeView == "notes") {
    srvStats.activeView = "countdown";
  } else if (srvStats.activeView == "countdown") {
    srvStats.activeView = "focus";
  } else if (srvStats.activeView == "focus") {
    srvStats.activeView = "clock";
  } else {
    srvStats.activeView = "main";
  }

  screenNeedUpdate = true;
  addLog("VIEW", "Chuyen sang View: " + srvStats.activeView);
}

void handleApiAction() {
  if (server.hasArg("view")) {
    srvStats.activeView = server.arg("view");
    screenNeedUpdate = true;
    addLog("VIEW", "Chuyen sang View truc tiep: " + srvStats.activeView);
    server.send(200, "text/plain", "OK");
    return;
  }
  if (server.hasArg("type")) {
    String act = server.arg("type");
    if (act == "hold_play") {
      cycleNextView();
      if (srvStats.activeView != "focus") {
        sendButtonAction(act.c_str());
      }
    } else {
      sendButtonAction(act.c_str());
    }
    server.send(200, "text/plain", "OK");
  }
}

void handleApiLogs() { server.send(200, "text/plain; charset=utf-8", serverLogs); }
void handleClearLogs() { serverLogs = "[SYSTEM] Log cleared.\n"; server.send(200, "text/plain", "OK"); }

// ================= XỬ LÝ 3 NÚT BẤM VẬT LÝ TRÊN MẶT TRẠM =================
void handleMenuPlay() {
  if (menuState == MENU_MAIN) {
    if (menuCurrentItem == 0) {
      menuState = MENU_ADJUST_LED;
      screenNeedUpdate = true;
      triggerBuzz(35);
    } else if (menuCurrentItem == 1) {
      menuState = MENU_BUZZER_CONFIG;
      buzzerCurrentItem = 0;
      screenNeedUpdate = true;
      triggerBuzz(35);
    } else if (menuCurrentItem == 2) {
      // Cycle Dark Mode: off -> always_on -> scheduled -> off
      if (darkModeSetting == "off") {
        darkModeSetting = "always_on";
      } else if (darkModeSetting == "always_on" || darkModeSetting == "always") {
        darkModeSetting = "scheduled";
      } else {
        darkModeSetting = "off";
      }
      prefs.putString("dk_mode", darkModeSetting);
      updateThemePalette();
      publishHardwareSettingsUpdate();
      screenNeedUpdate = true;
      triggerBuzz(45);
    } else if (menuCurrentItem == 3) {
      inSettingsMenu = false;
      lastRenderedView = "";
      screenNeedUpdate = true;
      triggerBuzz(35);
      addLog("MENU", "Thoat menu cai dat.");
    }
  } else if (menuState == MENU_ADJUST_LED) {
    prefs.putInt("led_bright", ledBrightness);
    publishHardwareSettingsUpdate();
    if (!ledState) {
      analogWrite(LED_PIN, 0);
    }
    menuState = MENU_MAIN;
    screenNeedUpdate = true;
    triggerBuzz(50);
  } else if (menuState == MENU_BUZZER_CONFIG) {
    if (buzzerCurrentItem == 0) {
      buzzerEnabled = !buzzerEnabled;
      prefs.putBool("buzz_en", buzzerEnabled);
      publishHardwareSettingsUpdate();
      screenNeedUpdate = true;
      if (buzzerEnabled) triggerBuzz(35);
    } else if (buzzerCurrentItem == 1) {
      static const unsigned int durOpts[] = {20, 40, 60, 80, 100, 150, 200, 250, 300, 500};
      const int optCount = sizeof(durOpts) / sizeof(durOpts[0]);
      int nextIdx = 0;
      for (int i = 0; i < optCount; i++) {
        if (buzzerDurationSetting < durOpts[i]) {
          nextIdx = i;
          break;
        }
      }
      if (buzzerDurationSetting >= durOpts[optCount - 1]) {
        nextIdx = 0;
      }
      buzzerDurationSetting = durOpts[nextIdx];
      prefs.putUInt("buzz_dur", buzzerDurationSetting);
      publishHardwareSettingsUpdate();
      screenNeedUpdate = true;
      triggerBuzz(buzzerDurationSetting);
    } else if (buzzerCurrentItem == 2) {
      triggerBuzz(buzzerDurationSetting);
    } else if (buzzerCurrentItem == 3) {
      menuState = MENU_MAIN;
      screenNeedUpdate = true;
      triggerBuzz(35);
    }
  }
}

void handleMenuNext() {
  if (menuState == MENU_MAIN) {
    menuCurrentItem = (menuCurrentItem + 1) % MENU_MAIN_ITEM_COUNT;
    screenNeedUpdate = true;
    triggerBuzz(20);
  } else if (menuState == MENU_ADJUST_LED) {
    ledBrightness = min(100, ledBrightness + 10);
    analogWrite(LED_PIN, (ledBrightness * 255) / 100);
    screenNeedUpdate = true;
    triggerBuzz(20);
  } else if (menuState == MENU_BUZZER_CONFIG) {
    buzzerCurrentItem = (buzzerCurrentItem + 1) % MENU_BUZZER_ITEM_COUNT;
    screenNeedUpdate = true;
    triggerBuzz(20);
  }
}

void handleMenuPrev() {
  if (menuState == MENU_MAIN) {
    menuCurrentItem = (menuCurrentItem - 1 + MENU_MAIN_ITEM_COUNT) % MENU_MAIN_ITEM_COUNT;
    screenNeedUpdate = true;
    triggerBuzz(20);
  } else if (menuState == MENU_ADJUST_LED) {
    ledBrightness = max(0, ledBrightness - 10);
    analogWrite(LED_PIN, (ledBrightness * 255) / 100);
    screenNeedUpdate = true;
    triggerBuzz(20);
  } else if (menuState == MENU_BUZZER_CONFIG) {
    buzzerCurrentItem = (buzzerCurrentItem - 1 + MENU_BUZZER_ITEM_COUNT) % MENU_BUZZER_ITEM_COUNT;
    screenNeedUpdate = true;
    triggerBuzz(20);
  }
}

void handleHardwareButtons() {
  static unsigned long playPressTime = 0;
  static bool playPressed = false;
  static bool playLongTriggered = false;

  static unsigned long nextPressTime = 0;
  static bool nextPressed = false;
  static bool nextLongTriggered = false;

  static unsigned long prevPressTime = 0;
  static bool prevPressed = false;
  static bool prevLongTriggered = false;

  static unsigned long lastDebounceTime = 0;

  if (millis() - lastDebounceTime < 30) return;
  lastDebounceTime = millis();

  bool curPlay = digitalRead(BTN_PLAY);
  bool curNext = digitalRead(BTN_NEXT);
  bool curPrev = digitalRead(BTN_PREV);

  // 1. PLAY BUTTON (Short = Play/Pause / Select, Long >= 700ms = Cycle View)
  if (curPlay == LOW && !playPressed) {
    playPressed = true;
    playPressTime = millis();
    playLongTriggered = false;
  } else if (curPlay == LOW && playPressed) {
    if (!playLongTriggered && (millis() - playPressTime >= 700)) {
      playLongTriggered = true;
      if (inViewMenu) {
        srvStats.activeView = VIEW_MENU_ITEMS[viewMenuIndex].id;
        inViewMenu = false;
        lastRenderedView = "";
        screenNeedUpdate = true;
        triggerBuzz(60);
        addLog("VIEW", "Da chon man hinh (hold): " + srvStats.activeView);
      } else if (!inSettingsMenu) {
        triggerBuzz(80);
        cycleNextView();
        if (srvStats.activeView != "focus") {
          sendButtonAction("hold_play");
        }
      }
    }
  } else if (curPlay == HIGH && playPressed) {
    playPressed = false;
    if (!playLongTriggered) {
      if (inSettingsMenu) {
        handleMenuPlay();
      } else if (inViewMenu) {
        srvStats.activeView = VIEW_MENU_ITEMS[viewMenuIndex].id;
        inViewMenu = false;
        lastRenderedView = "";
        screenNeedUpdate = true;
        triggerBuzz(60);
        addLog("VIEW", "Da chon man hinh: " + srvStats.activeView);
      } else {
        triggerBuzz(30);
        sendButtonAction("press_play");
      }
    }
  }

  // 2. NEXT BUTTON (Short = Next track/item, Long >= 800ms = Settings Menu)
  if (curNext == LOW && !nextPressed) {
    nextPressed = true;
    nextPressTime = millis();
    nextLongTriggered = false;
  } else if (curNext == LOW && nextPressed) {
    if (!nextLongTriggered && (millis() - nextPressTime >= 800)) {
      nextLongTriggered = true;
      triggerBuzz(80);
      if (inViewMenu) {
        inViewMenu = false;
      }
      if (!inSettingsMenu) {
        inSettingsMenu = true;
        menuState = MENU_MAIN;
        menuCurrentItem = 0;
        lastRenderedView = "";
        screenNeedUpdate = true;
        addLog("MENU", "Mo menu cai dat phan cung.");
      } else {
        if (menuState != MENU_MAIN) {
          menuState = MENU_MAIN;
          screenNeedUpdate = true;
        } else {
          inSettingsMenu = false;
          lastRenderedView = "";
          screenNeedUpdate = true;
          addLog("MENU", "Thoat menu cai dat.");
        }
      }
    }
  } else if (curNext == HIGH && nextPressed) {
    nextPressed = false;
    if (!nextLongTriggered) {
      if (inSettingsMenu) {
        handleMenuNext();
      } else if (inViewMenu) {
        viewMenuIndex = (viewMenuIndex + 1) % VIEW_MENU_COUNT;
        triggerBuzz(20);
        screenNeedUpdate = true;
      } else {
        if (srvStats.activeView == "notes") {
          cycleNextNote();
        } else if (srvStats.activeView == "countdown") {
          cycleNextCountdown();
        } else {
          triggerBuzz(30);
          sendButtonAction("press_next");
        }
      }
    }
  }

  // 3. PREV BUTTON (Short = Prev track/item, Long >= 700ms = View Selector Menu)
  if (curPrev == LOW && !prevPressed) {
    prevPressed = true;
    prevPressTime = millis();
    prevLongTriggered = false;
  } else if (curPrev == LOW && prevPressed) {
    if (!prevLongTriggered && (millis() - prevPressTime >= 700)) {
      prevLongTriggered = true;
      triggerBuzz(80);
      if (!inSettingsMenu) {
        if (!inViewMenu) {
          inViewMenu = true;
          viewMenuNeedsRedraw = true;
          for (int i = 0; i < VIEW_MENU_COUNT; i++) {
            if (srvStats.activeView == VIEW_MENU_ITEMS[i].id) {
              viewMenuIndex = i;
              break;
            }
          }
          lastRenderedView = "";
          screenNeedUpdate = true;
          addLog("MENU", "Mo menu danh sach man hinh.");
        } else {
          inViewMenu = false;
          lastRenderedView = "";
          screenNeedUpdate = true;
          addLog("MENU", "Thoat menu danh sach man hinh.");
        }
      }
    }
  } else if (curPrev == HIGH && prevPressed) {
    prevPressed = false;
    if (!prevLongTriggered) {
      if (inSettingsMenu) {
        handleMenuPrev();
      } else if (inViewMenu) {
        viewMenuIndex = (viewMenuIndex - 1 + VIEW_MENU_COUNT) % VIEW_MENU_COUNT;
        triggerBuzz(20);
        screenNeedUpdate = true;
      } else {
        if (srvStats.activeView == "notes") {
          cyclePrevNote();
        } else if (srvStats.activeView == "countdown") {
          cyclePrevCountdown();
        } else {
          triggerBuzz(30);
          sendButtonAction("press_prev");
        }
      }
    }
  }
}

unsigned long lastStatusPub = 0;
unsigned long lastWatchdogCheck = 0;
unsigned long lastScreenDraw = 0;
unsigned long lastMqttRetry = 0;

void setup() {
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);

  Serial.begin(115200);
  delay(100);

  initBuzzer();

  analogReadResolution(12);
  analogSetPinAttenuation(PIN_BATTERY, ADC_11db);

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  pinMode(BTN_PLAY, INPUT_PULLUP);
  pinMode(BTN_NEXT, INPUT_PULLUP);
  pinMode(BTN_PREV, INPUT_PULLUP);

  pinMode(TFT_RST, OUTPUT);
  digitalWrite(TFT_RST, HIGH);
  delay(10);
  digitalWrite(TFT_RST, LOW);
  delay(20);
  digitalWrite(TFT_RST, HIGH);
  delay(50);

  SPI.begin(TFT_SCLK, -1, TFT_MOSI, TFT_CS);
  tft = new Adafruit_ST7789(&SPI, TFT_CS, TFT_DC, TFT_RST);
  if (tft) {
    tft->init(240, 320);
    tft->setSPISpeed(80000000);
    tft->invertDisplay(false);
    tft->setRotation(0);
    tft->fillScreen(COL_BG);
    printCenteredText("DeskFlow Booting...", 150, 2, COL_CYAN, COL_BG);
  }

  WiFi.mode(WIFI_AP_STA);
  WiFi.setAutoReconnect(true);
  WiFi.persistent(true);
  WiFi.setTxPower(WIFI_POWER_17dBm);
  WiFi.setSleep(false);
  WiFi.softAP("DeskFlow-Station", "", 1, 0, 4);
  WiFi.softAPsetHostname("deskflow-station");

  configTime(25200, 0, "pool.ntp.org", "time.google.com");

  prefs.begin("deskflow", false);
  wifiListJson = prefs.getString("wifi_array", "[]");
  loadClockConfigNVS();

  darkModeSetting = prefs.getString("dk_mode", "off");
  darkModeStart = prefs.getString("dk_start", "22:00");
  darkModeEnd = prefs.getString("dk_end", "06:00");
  updateThemePalette();

  appliedHardwareConfigVersion = prefs.getInt("hw_ver", 0);
  if (appliedHardwareConfigVersion < 0 || appliedHardwareConfigVersion > 10000000) {
    appliedHardwareConfigVersion = 0;
    prefs.putInt("hw_ver", 0);
  }
  ledBrightness = prefs.getInt("led_bright", 100);
  buzzerEnabled = prefs.getBool("buzz_en", true);
  buzzerDurationSetting = prefs.getUInt("buzz_dur", 40);

  srvStats.stickyNote = prefs.getString("stk_note", "");
  srvStats.stickyNoteId = prefs.getString("stk_id", "");
  srvStats.countdownName = prefs.getString("cd_name", "");
  srvStats.countdownDays = prefs.getInt("cd_days", -1);
  srvStats.hasCountdown = prefs.getBool("cd_has", false);
  srvStats.countdownId = prefs.getString("cd_id", "");

  if (srvStats.stickyNote.length() > 0) {
    stationNotes[0].id = srvStats.stickyNoteId;
    stationNotes[0].title = srvStats.stickyNote.substring(0, 20);
    stationNotes[0].content = srvStats.stickyNote;
    stationNotes[0].isPinned = true;
    stationNotesCount = 1;
  }
  if (srvStats.countdownName.length() > 0) {
    stationCountdowns[0].id = srvStats.countdownId;
    stationCountdowns[0].title = srvStats.countdownName;
    stationCountdowns[0].targetDate = "2026-11-20";
    stationCountdowns[0].daysLeft = srvStats.countdownDays;
    stationCountdowns[0].isPinned = true;
    stationCountdownsCount = 1;
  }

  mqttBroker = prefs.getString("broker", "10th012013.duckdns.org");
  if (mqttBroker.length() == 0) {
    mqttBroker = "10th012013.duckdns.org";
  }
  mqttPort = prefs.getInt("b_port", 1883);

  mqtt.setServer(mqttBroker.c_str(), mqttPort);
  mqtt.setCallback(onMqttMessage);
  mqtt.setSocketTimeout(1);
  mqtt.setBufferSize(8192);

  server.on("/", handleRoot);
  server.on("/api/buzz", handleApiBuzz);
  server.on("/api/morse", handleApiMorse);
  server.on("/api/led", handleApiLed);
  server.on("/api/scan_wifi", handleScanWifi);
  server.on("/api/connect_wifi", handleConnectWifi);
  server.on("/api/get_saved_wifi", handleGetSavedWifi);
  server.on("/api/delete_wifi", handleDeleteWifi);
  server.on("/api/config_broker", handleConfigBroker);
  server.on("/api/status", handleApiStatus);
  server.on("/api/action", handleApiAction);
  server.on("/api/logs", handleApiLogs);
  server.on("/api/clear_logs", handleClearLogs);
  server.begin();

  autoConnectMultiWiFi();

  addLog("BOOT", "Portal san sang tai http://192.168.4.1");
}

void loop() {
  server.handleClient();
  handleHardwareButtons();

  // 1. Math Alarm reo liên tục dồn dập (Bíp 160ms, Nghỉ 100ms) cho tới khi giải
  if (isAlarmActive) {
    unsigned long now = millis();
    if (!alarmBeepState && (now - lastAlarmBeepTime >= 100)) {
      alarmBeepState = true;
      lastAlarmBeepTime = now;
      tone(BUZZER_PIN, 2600);
      digitalWrite(LED_PIN, HIGH);
    } else if (alarmBeepState && (now - lastAlarmBeepTime >= 160)) {
      alarmBeepState = false;
      lastAlarmBeepTime = now;
      noTone(BUZZER_PIN);
      digitalWrite(BUZZER_PIN, HIGH);
      digitalWrite(LED_PIN, LOW);
    }
  }
  // 2. Tiếng bíp đơn lẻ theo lệnh /buzz
  else if (isBuzzing && (millis() - buzzStartTime >= buzzDuration)) {
    isBuzzing = false;
    stopBuzzer();
  }

  // 3. Non-blocking Morse playback
  updateMorsePlayback();
  melodyUpdate(buzzerEnabled);

  if (ledBlinkMode) {
    if (millis() - lastLedBlinkTime >= LED_BLINK_INTERVAL) {
      lastLedBlinkTime = millis();
      ledState = !ledState;
      analogWrite(LED_PIN, ledState ? ((ledBrightness * 255) / 100) : 0);
    }
  }

  // Recalculate palette immediately when valid NTP time becomes available
  static bool wasTimeValid = false;
  time_t currentEpoch = time(nullptr);
  bool isTimeNowValid = (currentEpoch >= 1577836800); // Year >= 2020
  if (isTimeNowValid != wasTimeValid) {
    wasTimeValid = isTimeNowValid;
    if (isTimeNowValid) {
      updateThemePalette();
      addLog("NTP", "NTP time synchronized: " + getFormattedRealTime() + ". Palette updated immediately.");
    }
  }

  static unsigned long lastThemeCheck = 0;
  if (millis() - lastThemeCheck >= 10000) {
    lastThemeCheck = millis();
    updateThemePalette();
  }

  unsigned long refreshThreshold = isClockAnimationActive() ? 50 : 1000;
  if (screenNeedUpdate || (millis() - lastScreenDraw >= refreshThreshold)) {
    lastScreenDraw = millis();
    screenNeedUpdate = false;
    drawPhysicalScreen();
  }

  static bool wasConnected = false;
  static unsigned long lastWifiRetry = 0;

  if (WiFi.status() == WL_CONNECTED) {
    if (!wasConnected) {
      wasConnected = true;
      addLog("WIFI", ">>> KET NOI THANH CONG! IP: " + WiFi.localIP().toString());
      updateThemePalette();
    }

    if (!mqtt.connected()) {
      if (millis() - lastMqttRetry >= 5000) {
        lastMqttRetry = millis();
        reconnectMqtt();
      }
    } else {
      mqtt.loop();
    }
  } else {
    if (wasConnected) {
      wasConnected = false;
      addLog("WIFI", "Mat ket noi STA.");
    }
    if (millis() - lastWifiRetry > 15000) {
      lastWifiRetry = millis();
      if (wifiListJson.length() > 5) {
        autoConnectMultiWiFi();
      }
    }
  }

  if (millis() - lastWatchdogCheck > 15000) {
    lastWatchdogCheck = millis();
    bool currentNet = checkLocalInternet();
    if (currentNet != srvStats.internet) {
      srvStats.internet = currentNet;
      screenNeedUpdate = true;
      if (mqtt.connected()) {
        String p = "{\"device\":\"station\",\"internet\":" + String(srvStats.internet ? "true" : "false") + "}";
        mqtt.publish("deskflow/device_watchdog", p.c_str());
      }
    }
  }

  if (millis() - lastStatusPub > 10000) {
    lastStatusPub = millis();
    publishDeviceStatus();
  }
}