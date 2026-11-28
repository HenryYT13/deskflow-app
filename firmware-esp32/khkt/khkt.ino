#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <HTTPClient.h>
#include <HTTPUpdate.h>
#include <WebServer.h>
#include <PubSubClient.h>
#include <Preferences.h>
#include <ArduinoJson.h>
#include <vector>
#include <time.h>

#define DESKFLOW_FW_VERSION "2.4.10"

// Thanh ghi ngắt Brownout để chống crash khi tụt áp
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

// ================= THƯ VIỆN MÀN HÌNH ST7789 SPI =================
#include <SPI.h>
#include <Adafruit_GFX.h>
#include <Adafruit_ST7789.h>
#include <Adafruit_NeoPixel.h>
#include "vn_font.h"
#include "clock_styles.h"
#include "melody.h"
#include "i18n.h"
#include "alarm_tune.h"

#define TFT_CS   21
#define TFT_DC   19
#define TFT_RST  4
#define TFT_MOSI 23
#define TFT_SCLK 18

Adafruit_ST7789* hwTft = nullptr;
Adafruit_GFX* tft = nullptr;

#define PIN_BATTERY 34
#define BTN_PLAY    14
#define BTN_NEXT    27
#define BTN_PREV    26
#define LED_PIN     2
#define BUZZER_PIN  32
#define NUM_LEDS    24

Adafruit_NeoPixel strip(NUM_LEDS, LED_PIN, NEO_GRB + NEO_KHZ800);

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
#define MORSE_TEST_FREQ 1000 // Hz used by the "test buzzer" menu row

// Spotify volume mirrored from backend (deskflow/display -> spotify_update)
int spotifyVolume = 50; // 0 - 100%
bool spotifyVolumeKnown = false;

// Hardware Settings Menu State Machine
bool inSettingsMenu = false;
enum MenuState {
  MENU_MAIN = 0,
  MENU_ADJUST_LED = 1,
  MENU_BUZZER_CONFIG = 2,
  MENU_VOLUME = 3,
  MENU_LED_COLOR = 4,
  MENU_PAIR_DEVICE = 5,
  MENU_LED_EFFECT = 6,
  MENU_LED_SPEED = 7,
  MENU_LED_KELVIN = 8
};
MenuState menuState = MENU_MAIN;
int menuCurrentItem = 0;
const int MENU_MAIN_ITEM_COUNT = 13;
int ledKelvin = 4500;
String pairCodeValue = "";
String pairQrBits = "";
int pairQrSize = 0;
bool pairCodeLoading = false;
bool pairFetchFailed = false;
unsigned long pairCodeFetchedAt = 0;
unsigned long pairReqStartedAt = 0;
// 4 entries per page keeps the 240x320 panel readable; moving past the last
// entry flips to the next page instead of squeezing all rows together.
const int MENU_ROWS_PER_PAGE = 4;
int menuPage = 0;

// 8 built-in lamp colours (including RGB rainbow), selectable from hardware menu.
const char* LED_PRESETS[] = {"#FFFFFF", "#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF", "#00FFFF", "RGB"};
const char* LED_PRESET_NAMES[] = {"TRẮNG", "ĐỎ", "XANH LÁ", "XANH DƯƠNG", "VÀNG", "TÍM", "XANH NGỌC", "RGB CẦU VỒNG"};
const char* LED_PRESET_NAMES_EN[] = {"WHITE", "RED", "GREEN", "BLUE", "YELLOW", "PURPLE", "CYAN", "RGB RAINBOW"};
const int LED_PRESET_COUNT = 8;
int ledPresetIndex = 0;
int colorPage = 0;

// 8 LED effect modes selectable directly on ESP32
const char* LED_EFFECT_IDS[] = {"solid", "rainbow", "breathing", "chase", "sparkle", "fire", "clock", "police"};
const char* LED_EFFECT_NAMES[] = {"MÀU TĨNH", "RGB CẦU VỒNG", "NHỊP THỞ", "SAO BĂNG", "LẤP LÁNH", "LỬA TRẠI", "ĐỒNG HỒ 24", "CẢNH SÁT"};
const char* LED_EFFECT_NAMES_EN[] = {"SOLID", "RGB RAINBOW", "BREATHING", "COMET CHASE", "SPARKLE", "CAMPFIRE", "CLOCK RING", "POLICE"};
const int LED_EFFECT_COUNT = 8;
int ledEffectIndex = 0;
int effectPage = 0;
int buzzerCurrentItem = 0;
const int MENU_BUZZER_ITEM_COUNT = 5;

// View Selector Menu State Machine
bool inViewMenu = false;
int viewMenuIndex = 0;
const int VIEW_MENU_COUNT = 18;
const int VIEW_ROWS_PER_PAGE = 6;
int viewMenuPage = 0;
struct ViewMenuItem {
  const char* id;
  const char* title;
};
const ViewMenuItem VIEW_MENU_ITEMS[VIEW_MENU_COUNT] = {
  { "main",      "1. Tổng quan" },
  { "stats",     "2. Thông số máy" },
  { "tkb",       "3. Thời khóa biểu" },
  { "calendar",  "4. Lịch Google" },
  { "weather",   "5. Thời tiết & AQI" },
  { "lyrics",    "6. Lời bài hát" },
  { "vinyl",     "7. Đĩa than Spotify" },
  { "notes",     "8. Ghi chú" },
  { "countdown", "9. Đếm ngược" },
  { "focus",     "10. Tập trung" },
  { "clock",     "11. Đồng hồ" },
  { "matrix",    "12. Màn hình Matrix" },
  { "github",    "13. GitHub CI & Repo" },
  { "formulas",  "14. Công thức & BTH" },
  { "game",      "15. Arcade Game Hub" },
  { "analog",    "16. Đồng hồ Kim" },
  { "lunar",     "17. Lịch Âm & Năm" },
  { "stopwatch", "18. Bấm giờ (#30)" }
};

// English mirror used when the station language is EN.
const char* const VIEW_MENU_TITLES_EN[VIEW_MENU_COUNT] = {
  "1. Dashboard",
  "2. System stats",
  "3. Timetable",
  "4. Google Calendar",
  "5. Weather & AQI",
  "6. Lyrics",
  "7. Spotify Vinyl",
  "8. Notes",
  "9. Countdown",
  "10. Focus",
  "11. Clock",
  "12. Matrix Rain",
  "13. GitHub CI & Repo",
  "14. Formulas & Table",
  "15. Arcade Game Hub",
  "16. Analog Clock",
  "17. Lunar & Year",
  "18. Stopwatch (#30)"
};

const char* viewMenuTitle(int index) {
  if (index < 0 || index >= VIEW_MENU_COUNT) return "";
  return stationEnglish ? VIEW_MENU_TITLES_EN[index] : VIEW_MENU_ITEMS[index].title;
}

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

// Google Calendar Month Grid + Day Detail Storage
struct StationCalEvent {
  uint16_t year = 2026;
  uint8_t month = 11;
  uint8_t day = 1;
  String timeStr;
  String title;
  String location;
};
const int MAX_STATION_CAL_EVENTS = 90;
StationCalEvent stationCalEvents[MAX_STATION_CAL_EVENTS];
int stationCalEventCount = 0;
bool stationCalConnected = false;

int calCurYear = 2026, calCurMonth = 11, calTodayDay = 21;
int calCurFirstWd = 0, calCurDaysInMonth = 30;
int calNextYear = 2026, calNextMonth = 12;
int calNextFirstWd = 2, calNextDaysInMonth = 31;

bool calShowNextMonth = false;
int calCursorDay = 0;
bool calInDayDetail = false;
int calDetailIdx = 0;
bool calForceRedraw = true;

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
unsigned long lastAlarmStrobeTime = 0;
bool alarmStrobeOn = false;
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
  int weatherAqi = 2;
  String weatherAqiLabel = "Kha";
  float weatherUv = 4.0;
  String weatherUvLabel = "TB";
  String quoteText = "Kien tri hom nay, thanh cong ngay mai!";

  String tkbDay = "Hom nay";
  String tkbMorning = "Toan, Van, Anh";
  bool tkbHasAfternoon = false;
  String tkbAfternoon = "";
  String tkbUniform = "Ao trang";
  String liveLabel = "";
  String liveSubject = "";
  int liveMinsLeft = 0;
  String dismissalTime = "";
  int minsToDismissal = 0;

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
  String lampColor2 = "#A855F7";
} srvStats;

void parseHexColor(const String& hex, uint8_t& r, uint8_t& g, uint8_t& b) {
  String clean = hex;
  clean.trim();
  if (clean.startsWith("#")) clean = clean.substring(1);
  else if (clean.startsWith("0x") || clean.startsWith("0X")) clean = clean.substring(2);
  clean.toUpperCase();

  // Named color shortcuts
  if (clean == "RED") { r = 255; g = 0; b = 0; return; }
  if (clean == "GREEN") { r = 0; g = 255; b = 0; return; }
  if (clean == "BLUE") { r = 0; g = 0; b = 255; return; }
  if (clean == "WHITE") { r = 255; g = 255; b = 255; return; }
  if (clean == "YELLOW") { r = 255; g = 255; b = 0; return; }
  if (clean == "CYAN") { r = 0; g = 255; b = 255; return; }
  if (clean == "MAGENTA" || clean == "PINK") { r = 255; g = 0; b = 255; return; }
  if (clean == "ORANGE") { r = 255; g = 128; b = 0; return; }
  if (clean == "PURPLE") { r = 139; g = 92; b = 246; return; }
  if (clean == "WARM" || clean == "WARMW" || clean == "WARMWITHE" || clean == "FFC7AC") {
    // Yellowish white ("trang am") - also the default preset in every app.
    r = 255; g = 199; b = 172; return;
  }
  if (clean == "COOL") { r = 200; g = 220; b = 255; return; }

  // 3-char shorthand: #RGB -> #RRGGBB (e.g. #F00 -> #FF0000)
  if (clean.length() == 3) {
    char rC = clean.charAt(0);
    char gC = clean.charAt(1);
    char bC = clean.charAt(2);
    char buf[7] = { rC, rC, gC, gC, bC, bC, '\0' };
    clean = String(buf);
  }

  // 8-char hex (ARGB / RGBA): strip alpha prefix
  if (clean.length() == 8) {
    clean = clean.substring(2);
  }

  if (clean.length() == 6) {
    long num = strtol(clean.c_str(), nullptr, 16);
    r = (num >> 16) & 0xFF;
    g = (num >> 8) & 0xFF;
    b = num & 0xFF;
  } else {
    r = 255; g = 255; b = 255;
  }
}

String ledEffect = "solid"; // solid, breathing, chase, police, sparkle, fire, strobe, ambient, clock
bool ledRgbMode = false; // Orthogonal RGB color wheel combinable with any effect
bool ledGradientMode = false; // Dual-color gradient (lampColor -> lampColor2)
int ledSpeed = 50; // 5 - 100
unsigned long lastLedEffectFrame = 0;
uint16_t ledEffectStep = 0;
bool isWakeupCheckActive = false;
unsigned long wakeupCheckDeadlineMs = 0;
String pendingOtaUrl = "";
String pendingOtaVersion = "";
String customBootTitle = "DeskFlow Station";
String customBootSub = "v" DESKFLOW_FW_VERSION;
String customBootBmp = ""; // 16x16 1-bit bitmap encoded as 64 hex chars (or 32x32 as 256 hex chars)

// (#67) Live notification ticker + (#75) Screenshot + (#157) Webhook LED flash
String notifTickerText = "";
unsigned long notifTickerUntilMs = 0;
bool pendingScreenshotCapture = false;
unsigned long webhookFlashUntilMs = 0;
String webhookFlashHex = "#22C55E";

// v2.4.0 state (#315, #317, #52)
int phoneBattPct = -1;
bool phoneBattCharging = false;
String incomingCallCaller = "";
unsigned long incomingCallUntil = 0;
int clockFontMode = 0;

#include "arcade_games.h"
#include "extra_views.h"
#include "v24_views.h"

void triggerBuzz(unsigned long durationMs, bool force);

void wheelRgb(byte pos, uint8_t& r, uint8_t& g, uint8_t& b) {
  pos = 255 - pos;
  if (pos < 85) {
    r = 255 - pos * 3; g = 0; b = pos * 3;
  } else if (pos < 170) {
    pos -= 85; r = 0; g = pos * 3; b = 255 - pos * 3;
  } else {
    pos -= 170; r = pos * 3; g = 255 - pos * 3; b = 0;
  }
}

uint32_t wheelColor(byte pos, int brightPct) {
  uint8_t r = 0, g = 0, b = 0;
  wheelRgb(pos, r, g, b);
  r = (uint16_t)r * brightPct / 100;
  g = (uint16_t)g * brightPct / 100;
  b = (uint16_t)b * brightPct / 100;
  return strip.Color(r, g, b);
}

void getPixelRgbForIndex(int idx, uint16_t phase, uint8_t& r, uint8_t& g, uint8_t& b) {
  if (ledRgbMode) {
    wheelRgb(((idx * 256 / max(1, NUM_LEDS)) + phase) & 255, r, g, b);
    return;
  }
  uint8_t r1 = 255, g1 = 255, b1 = 255;
  parseHexColor(srvStats.lampColor, r1, g1, b1);
  if (!ledGradientMode) {
    r = r1; g = g1; b = b1;
    return;
  }
  uint8_t r2 = 168, g2 = 85, b2 = 247;
  parseHexColor(srvStats.lampColor2, r2, g2, b2);
  float angle = ((idx * 256 / max(1, NUM_LEDS) + phase) & 255) / 255.0f;
  float t = (angle <= 0.5f) ? (angle * 2.0f) : ((1.0f - angle) * 2.0f);
  r = (uint8_t)(r1 + (r2 - r1) * t);
  g = (uint8_t)(g1 + (g2 - g1) * t);
  b = (uint8_t)(b1 + (b2 - b1) * t);
}

void updateLampLed() {
  if (!ledState || ledBrightness <= 0) {
    strip.clear();
    strip.show();
    return;
  }
  if ((ledEffect != "solid" && ledEffect != "ambient") || (ledRgbMode && ledEffect == "solid") || (ledGradientMode && ledEffect == "solid")) {
    return; // Driven continuously by updateLedEffects()
  }
  uint8_t r = 255, g = 255, b = 255;
  parseHexColor(srvStats.lampColor, r, g, b);
  uint8_t scaledR = (uint16_t)r * ledBrightness / 100;
  uint8_t scaledG = (uint16_t)g * ledBrightness / 100;
  uint8_t scaledB = (uint16_t)b * ledBrightness / 100;
  strip.setBrightness(255);
  for (int i = 0; i < NUM_LEDS; i++) {
    strip.setPixelColor(i, strip.Color(scaledR, scaledG, scaledB));
  }
  strip.show();
}

void updateLedEffects() {
  if (webhookFlashUntilMs > millis()) {
    uint8_t wr = 34, wg = 197, wb = 94;
    parseHexColor(webhookFlashHex, wr, wg, wb);
    bool pulseOn = ((millis() / 180) % 2) == 0;
    strip.setBrightness(255);
    uint32_t wc = pulseOn ? strip.Color((uint16_t)wr * ledBrightness / 100, (uint16_t)wg * ledBrightness / 100, (uint16_t)wb * ledBrightness / 100) : 0;
    for (int i = 0; i < NUM_LEDS; i++) strip.setPixelColor(i, wc);
    strip.show();
    return;
  }
  if (!ledState || ledBrightness <= 0 || isAlarmActive || ledBlinkMode) return;
  bool isContinuousWave = (ledEffect == "solid" && (ledRgbMode || ledGradientMode));
  if (!isContinuousWave && (ledEffect == "solid" || ledEffect == "ambient")) return;

  unsigned long interval = (ledEffect == "clock") ? 16 : map(constrain(ledSpeed, 5, 100), 5, 100, 60, 16);
  if (millis() - lastLedEffectFrame < interval) return;
  lastLedEffectFrame = millis();
  ledEffectStep++;

  strip.setBrightness(255);

  if (ledEffect == "clock") {
    // (#76) 24-LED Analog Ring Clock (12 even hour ticks, hour=Amber, min=Cyan, sec=Green)
    struct tm tInfo;
    float k = ledBrightness / 100.0f;
    for (int i = 0; i < NUM_LEDS; i++) {
      if ((i % 2) == 0) strip.setPixelColor(i, strip.Color((uint8_t)(18 * k), (uint8_t)(18 * k), (uint8_t)(24 * k)));
      else strip.setPixelColor(i, 0);
    }
    if (getLocalTime(&tInfo, 5)) {
      int hIdx = ((tInfo.tm_hour % 12) * 2 + (tInfo.tm_min >= 30 ? 1 : 0)) % NUM_LEDS;
      int mIdx = (tInfo.tm_min * NUM_LEDS / 60) % NUM_LEDS;
      int sIdx = (tInfo.tm_sec * NUM_LEDS / 60) % NUM_LEDS;
      strip.setPixelColor(sIdx, strip.Color((uint8_t)(30 * k), (uint8_t)(220 * k), (uint8_t)(110 * k)));
      strip.setPixelColor(mIdx, strip.Color(0, (uint8_t)(220 * k), (uint8_t)(255 * k)));
      strip.setPixelColor(hIdx, strip.Color((uint8_t)(255 * k), (uint8_t)(150 * k), 0));
    }
    strip.show();
    return;
  }

  if (ledEffect == "rainbow" || isContinuousWave) {
    for (int i = 0; i < NUM_LEDS; i++) {
      uint8_t rr, rg, rb;
      getPixelRgbForIndex(i, ledEffectStep * 5, rr, rg, rb);
      strip.setPixelColor(i, strip.Color((uint16_t)rr * ledBrightness / 100, (uint16_t)rg * ledBrightness / 100, (uint16_t)rb * ledBrightness / 100));
    }
  } else if (ledEffect == "breathing") {
    float wave = (sin(ledEffectStep * 0.14f) + 1.0f) * 0.5f;
    float factor = (0.08f + wave * 0.92f) * (ledBrightness / 100.0f);
    for (int i = 0; i < NUM_LEDS; i++) {
      uint8_t rr, rg, rb;
      getPixelRgbForIndex(i, ledEffectStep * 2, rr, rg, rb);
      strip.setPixelColor(i, strip.Color((uint8_t)(rr * factor), (uint8_t)(rg * factor), (uint8_t)(rb * factor)));
    }
  } else if (ledEffect == "chase") {
    int head = ledEffectStep % NUM_LEDS;
    int tailLen = max(4, NUM_LEDS / 3);
    for (int i = 0; i < NUM_LEDS; i++) {
      int dist = (head - i + NUM_LEDS) % NUM_LEDS;
      float tail = (dist < tailLen) ? (1.0f - (float)dist / tailLen) : 0.0f;
      float k = tail * (ledBrightness / 100.0f);
      uint8_t rr, rg, rb;
      getPixelRgbForIndex(i, ledEffectStep * 4, rr, rg, rb);
      strip.setPixelColor(i, strip.Color((uint8_t)(rr * k), (uint8_t)(rg * k), (uint8_t)(rb * k)));
    }
  } else if (ledEffect == "police") {
    bool leftRed = ((ledEffectStep / 4) % 2) == 0;
    uint8_t amp = (uint8_t)(255 * ledBrightness / 100);
    for (int i = 0; i < NUM_LEDS; i++) {
      if (i < NUM_LEDS / 2) strip.setPixelColor(i, leftRed ? strip.Color(amp, 0, 0) : 0);
      else strip.setPixelColor(i, !leftRed ? strip.Color(0, 0, amp) : 0);
    }
  } else if (ledEffect == "sparkle") {
    for (int i = 0; i < NUM_LEDS; i++) {
      uint32_t c = strip.getPixelColor(i);
      uint8_t r = ((c >> 16) & 0xFF) * 3 / 4;
      uint8_t g = ((c >> 8) & 0xFF) * 3 / 4;
      uint8_t b = (c & 0xFF) * 3 / 4;
      strip.setPixelColor(i, strip.Color(r, g, b));
    }
    int px = random(NUM_LEDS);
    uint8_t rr, rg, rb;
    getPixelRgbForIndex(px, random(255), rr, rg, rb);
    float k = ledBrightness / 100.0f;
    strip.setPixelColor(px, strip.Color((uint8_t)(rr * k), (uint8_t)(rg * k), (uint8_t)(rb * k)));
  } else if (ledEffect == "fire") {
    for (int i = 0; i < NUM_LEDS; i++) {
      int flicker = random(65, 255);
      if (ledRgbMode || ledGradientMode) {
        uint8_t rr, rg, rb;
        getPixelRgbForIndex(i, ledEffectStep * 3, rr, rg, rb);
        float k = (flicker / 255.0f) * (ledBrightness / 100.0f);
        strip.setPixelColor(i, strip.Color((uint8_t)(rr * k), (uint8_t)(rg * k), (uint8_t)(rb * k)));
      } else {
        uint8_t r = (uint16_t)flicker * ledBrightness / 100;
        uint8_t g = (uint16_t)(flicker / 4) * ledBrightness / 100;
        uint8_t b = (uint16_t)(flicker / 20) * ledBrightness / 100;
        strip.setPixelColor(i, strip.Color(r, g, b));
      }
    }
  } else if (ledEffect == "strobe") {
    bool on = (ledEffectStep % 2) == 0;
    for (int i = 0; i < NUM_LEDS; i++) {
      if (!on) {
        strip.setPixelColor(i, 0);
      } else {
        uint8_t rr, rg, rb;
        getPixelRgbForIndex(i, ledEffectStep * 29, rr, rg, rb);
        float k = ledBrightness / 100.0f;
        strip.setPixelColor(i, strip.Color((uint8_t)(rr * k), (uint8_t)(rg * k), (uint8_t)(rb * k)));
      }
    }
  }
  strip.show();
}

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
void publishDeviceStatus();  // forward declaration (defined after the web UI)

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

bool hasNonAscii(const String& str) {
  for (size_t i = 0; i < str.length(); i++) {
    if ((uint8_t)str[i] >= 0x80) return true;
  }
  return false;
}

String truncateUtf8ToWidth(const String& text, int maxWidthPx, const VnFont& font) {
  if (vnTextWidth(font, text.c_str()) <= maxWidthPx) return text;
  const char* s = text.c_str();
  size_t len = text.length();
  size_t bestLen = 0;
  size_t i = 0;
  while (i < len) {
    uint8_t b = (uint8_t)s[i];
    size_t charLen = 1;
    if ((b & 0xE0) == 0xC0) charLen = 2;
    else if ((b & 0xF0) == 0xE0) charLen = 3;
    else if ((b & 0xF8) == 0xF0) charLen = 4;
    if (i + charLen > len) break;
    String candidate = text.substring(0, i + charLen) + "...";
    if (vnTextWidth(font, candidate.c_str()) > maxWidthPx) break;
    bestLen = i + charLen;
    i += charLen;
  }
  return text.substring(0, bestLen) + "...";
}

void drawUtf8Text(int x, int y, const String& text, int size, uint16_t color, uint16_t bg, int maxWidthPx = 0) {
  if (!tft) return;
  if (size >= 3 && !hasNonAscii(text)) {
    String out = text;
    if (maxWidthPx > 0) {
      int maxChars = maxWidthPx / (6 * size);
      if ((int)out.length() > maxChars && maxChars > 3) {
        out = out.substring(0, maxChars - 3) + "...";
      } else if ((int)out.length() > maxChars) {
        out = out.substring(0, maxChars);
      }
    }
    tft->setTextSize(size);
    tft->setTextColor(color, bg);
    tft->setCursor(x, y);
    tft->print(out);
    return;
  }
  const VnFont& font = (size >= 2) ? vnFont17 : vnFont12;
  String out = (maxWidthPx > 0) ? truncateUtf8ToWidth(text, maxWidthPx, font) : text;
  // Align Google Sans cap-height with existing GFX coordinates
  int yAdj = max(0, y - 5);
  vnDrawText(tft, font, x, yAdj, out.c_str(), color, bg);
}

void printVietnameseCentered(String text, int y, uint16_t color, const VnFont& font = vnFont12, int maxWidthPx = 230) {
  if (!tft) return;
  String out = truncateUtf8ToWidth(text, maxWidthPx, font);
  int w = vnTextWidth(font, out.c_str());
  int x = (240 - w) / 2;
  if (x < 0) x = 0;
  int yAdj = max(0, y - 5);
  vnDrawText(tft, font, x, yAdj, out.c_str(), color, themeBg);
}

void printVietnameseWrappedCentered(String text, int centerY, uint16_t color, int maxWidth = 224, int lineSpacing = 22) {
  if (!tft) return;
  text.trim();

  int wordCount = 0;
  String words[50];
  int lastIdx = 0;
  for (size_t i = 0; i <= text.length(); i++) {
    if (i == text.length() || text[i] == ' ') {
      if (i > lastIdx && wordCount < 50) {
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
    if (vnTextWidth(vnFont17, testLine.c_str()) <= maxWidth) {
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

  int totalH = (lineCount > 0) ? ((lineCount - 1) * lineSpacing + vnFont17.lineHeight) : 0;
  int startY = centerY - (totalH / 2);

  for (int i = 0; i < lineCount; i++) {
    int w = vnTextWidth(vnFont17, lines[i].c_str());
    int x = (240 - w) / 2;
    if (x < 0) x = 0;
    vnDrawText(tft, vnFont17, x, startY + (i * lineSpacing), lines[i].c_str(), color, themeBg);
  }
}

void printCenteredText(String text, int y, int size, uint16_t color, uint16_t bg) {
  if (!tft) return;
  if (size >= 3 && !hasNonAscii(text)) {
    int charW = 6 * size;
    int textW = (int)text.length() * charW;
    int x = (240 - textW) / 2;
    if (x < 0) x = 0;

    tft->setTextSize(size);
    tft->setTextColor(color, bg);
    tft->setCursor(x, y);
    tft->print(text);
    return;
  }
  const VnFont& font = (size >= 2) ? vnFont17 : vnFont12;
  int w = vnTextWidth(font, text.c_str());
  int x = (240 - w) / 2;
  if (x < 0) x = 0;
  int yAdj = max(0, y - 5);
  vnDrawText(tft, font, x, yAdj, text.c_str(), color, bg);
}

void printCenteredText(String text, int y, int size, uint16_t color) {
  printCenteredText(text, y, size, color, themeBg);
}

void printBigWrappedCenteredText(String text, int centerY, int size, uint16_t color, int maxCharsPerLine, int lineSpacing, uint16_t bg) {
  if (!tft) return;
  text.trim();
  bool useVnFont = (size <= 2) || hasNonAscii(text);
  const VnFont& font = (size >= 2) ? vnFont17 : vnFont12;
  int maxWidthPx = min(224, maxCharsPerLine * (size >= 2 ? 10 : 7));

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
    bool fits = useVnFont ? (vnTextWidth(font, testLine.c_str()) <= maxWidthPx) : ((int)testLine.length() <= maxCharsPerLine);
    if (fits) {
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

  int charH = useVnFont ? font.lineHeight : (size * 8);
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
  text.trim();
  bool useVnFont = (size <= 2) || hasNonAscii(text);
  const VnFont& font = (size >= 2) ? vnFont17 : vnFont12;
  int maxWidthPx = min(224, maxCharsPerLine * (size >= 2 ? 10 : 7));

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
    bool fits = useVnFont ? (vnTextWidth(font, testLine.c_str()) <= maxWidthPx) : ((int)testLine.length() <= maxCharsPerLine);
    if (fits) {
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

bool navBeepEnabled = false; // Keep navigation/button moves silent by default

void triggerBuzz(unsigned long durationMs = 250, bool force = false) {
  if ((!buzzerEnabled || !navBeepEnabled) && !force) return;
  isBuzzing = true;
  buzzStartTime = millis();
  buzzDuration = durationMs;
  tone(BUZZER_PIN, 2400);
  addLog("BUZZER", ">>> BUZZER TRIGGERED: " + String(durationMs) + "ms");
}

// Plays a short tone at an explicit frequency (used by the buzzer test row).
void triggerBuzzTone(unsigned long durationMs, unsigned int freqHz) {
  if (!buzzerEnabled) return;
  isBuzzing = true;
  buzzStartTime = millis();
  buzzDuration = durationMs;
  tone(BUZZER_PIN, freqHz);
  addLog("BUZZER", ">>> TEST TONE " + String(freqHz) + "Hz " + String(durationMs) + "ms");
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
unsigned int morseUnitMs = 60;
unsigned int morseFrequency = 1000;

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

void startMorsePlayback(const String& pattern, unsigned int unitMs = 60, unsigned int freq = 1000) {
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

void publishDeviceStatus();
void screenNeedsUpdateAfterLanguage();

void publishStationLanguageUpdate() {
  if (!mqtt.connected()) return;
  DynamicJsonDocument doc(256);
  doc["action"] = "station_language_update";
  doc["device_id"] = "DeskFlow-ESP32";
  doc["language"] = stationEnglish ? "en" : "vi";
  String out;
  serializeJson(doc, out);
  mqtt.publish("deskflow/status_update", out.c_str());
  addLog("I18N", "Synced station language to server: " + String(stationEnglish ? "en" : "vi"));
}

void applyStationLanguage(bool english, bool syncToServer) {
  setStationLanguage(english);
  clockCfg.language = english ? "en" : "vi";
  saveClockConfigNVS();
  if (syncToServer) {
    setPendingLocalLangSync(true);
    if (mqtt.connected()) {
      sendClockStyleAck("applied", "");
      publishStationLanguageUpdate();
      publishDeviceStatus();
    }
  } else {
    setPendingLocalLangSync(false);
  }
  screenNeedsUpdateAfterLanguage();
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

int getCalDayEvents(uint16_t y, uint8_t m, uint8_t d, int* outIndices, int maxOut) {
  int cnt = 0;
  for (int i = 0; i < stationCalEventCount; i++) {
    if (stationCalEvents[i].year == y && stationCalEvents[i].month == m && stationCalEvents[i].day == d) {
      if (outIndices && cnt < maxOut) outIndices[cnt] = i;
      cnt++;
    }
  }
  return cnt;
}

void ensureCalCursorValid() {
  int activeDim = calShowNextMonth ? calNextDaysInMonth : calCurDaysInMonth;
  if (activeDim <= 0) activeDim = 30;
  if (calCursorDay < 1 || calCursorDay > activeDim) {
    calShowNextMonth = false;
    calCursorDay = (calTodayDay >= 1 && calTodayDay <= calCurDaysInMonth) ? calTodayDay : 1;
  }
}

void toggleCalendarSelectOrExit() {
  ensureCalCursorValid();
  if (!calInDayDetail) {
    calInDayDetail = true;
    calDetailIdx = 0;
    addLog("CAL", "Mo chi tiet ngay " + String(calCursorDay));
  } else {
    calInDayDetail = false;
    addLog("CAL", "Thoat chi tiet ve grid thang");
  }
  calForceRedraw = true;
  screenNeedUpdate = true;
  triggerBuzz(35);
}

void cycleNextCalendar() {
  ensureCalCursorValid();
  if (calInDayDetail) {
    uint16_t ay = calShowNextMonth ? calNextYear : calCurYear;
    uint8_t am = calShowNextMonth ? calNextMonth : calCurMonth;
    int totalDayEv = getCalDayEvents(ay, am, calCursorDay, nullptr, 0);
    if (totalDayEv > 0) {
      calDetailIdx = (calDetailIdx + 1) % totalDayEv;
      calForceRedraw = true;
      screenNeedUpdate = true;
      triggerBuzz(22);
    } else {
      triggerBuzz(15);
    }
    return;
  }

  int activeDim = calShowNextMonth ? calNextDaysInMonth : calCurDaysInMonth;
  if (calCursorDay < activeDim) {
    calCursorDay++;
  } else if (!calShowNextMonth && calNextDaysInMonth > 0) {
    calShowNextMonth = true;
    calCursorDay = 1;
    calForceRedraw = true;
  } else {
    calShowNextMonth = false;
    calCursorDay = 1;
    calForceRedraw = true;
  }
  screenNeedUpdate = true;
  triggerBuzz(20);
}

void cyclePrevCalendar() {
  ensureCalCursorValid();
  if (calInDayDetail) {
    uint16_t ay = calShowNextMonth ? calNextYear : calCurYear;
    uint8_t am = calShowNextMonth ? calNextMonth : calCurMonth;
    int totalDayEv = getCalDayEvents(ay, am, calCursorDay, nullptr, 0);
    if (totalDayEv > 0) {
      calDetailIdx = (calDetailIdx - 1 + totalDayEv) % totalDayEv;
      calForceRedraw = true;
      screenNeedUpdate = true;
      triggerBuzz(22);
    } else {
      triggerBuzz(15);
    }
    return;
  }

  if (calCursorDay > 1) {
    calCursorDay--;
  } else if (calShowNextMonth) {
    calShowNextMonth = false;
    calCursorDay = calCurDaysInMonth;
    calForceRedraw = true;
  } else if (calNextDaysInMonth > 0) {
    calShowNextMonth = true;
    calCursorDay = calNextDaysInMonth;
    calForceRedraw = true;
  } else {
    calCursorDay = calCurDaysInMonth;
  }
  screenNeedUpdate = true;
  triggerBuzz(20);
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

extern String lastMenuSignature;
bool menuForceFullRedraw = true;

void drawSettingsMenu() {
  static int lastDrawnState = -1;
  static int lastDrawnPage = -1;
  bool fullClear = menuForceFullRedraw || (lastDrawnState != (int)menuState) || (lastDrawnPage != menuPage);
  menuForceFullRedraw = false;
  lastDrawnState = (int)menuState;
  lastDrawnPage = menuPage;
  if (fullClear) {
    tft->fillScreen(COL_BG);
    // Header Box
    tft->fillRoundRect(10, 8, 220, 32, 6, COL_CARD);
    tft->drawRoundRect(10, 8, 220, 32, 6, COL_CYAN);
    printCenteredText(tr("CÀI ĐẶT PHẦN CỨNG", "HARDWARE SETTINGS"), 18, 1, COL_CYAN, COL_CARD);
  }

  if (menuState == MENU_MAIN) {
    // 4 rows per page, large enough to read; PREV/NEXT past the edge pages.
    int pageCount = (MENU_MAIN_ITEM_COUNT + MENU_ROWS_PER_PAGE - 1) / MENU_ROWS_PER_PAGE;
    if (menuPage >= pageCount) menuPage = pageCount - 1;
    if (menuPage < 0) menuPage = 0;
    int firstItem = menuPage * MENU_ROWS_PER_PAGE;

    int startY = 46;
    int cardH = 52;
    int gap = 7;

    for (int rowIdx = 0; rowIdx < MENU_ROWS_PER_PAGE; rowIdx++) {
      int i = firstItem + rowIdx;
      if (i >= MENU_MAIN_ITEM_COUNT) break;
      int y = startY + rowIdx * (cardH + gap);
      bool selected = (menuCurrentItem == i);
      uint16_t bCol = selected ? COL_AMBER : COL_BORDER;

      tft->fillRoundRect(12, y, 216, cardH, 6, COL_CARD);
      tft->drawRoundRect(12, y, 216, cardH, 6, bCol);
      tft->drawRoundRect(11, y - 1, 218, cardH + 2, 7, selected ? COL_AMBER : COL_BG);

      uint16_t titleCol = selected ? COL_AMBER : COL_WHITE;
      String pfx = selected ? "> " : "  ";
      char buf[96];

      if (i == 0) {
        snprintf(buf, sizeof(buf), "%s%s: %s", pfx.c_str(),
                 tr("ĐÈN LED", "LED RING"), ledState ? tr("BẬT", "ON") : tr("TẮT", "OFF"));
        drawUtf8Text(20, y + 10, String(buf), 1, titleCol, COL_CARD, 200);
        snprintf(buf, sizeof(buf), "%d%%  %s", ledBrightness, tr("PLAY: Bật/Tắt", "PLAY: On/Off"));
        drawUtf8Text(24, y + 28, String(buf), 1, COL_MUTED, COL_CARD, 196);
      } else if (i == 1) {
        snprintf(buf, sizeof(buf), "%s%s: %s", pfx.c_str(), tr("MÀU ĐÈN", "LAMP COLOR"),
                 (ledEffect == "rainbow") ? "RGB" : (stationEnglish ? LED_PRESET_NAMES_EN[ledPresetIndex] : LED_PRESET_NAMES[ledPresetIndex]));
        drawUtf8Text(20, y + 10, String(buf), 1, titleCol, COL_CARD, 200);
        drawUtf8Text(24, y + 28, tr("PLAY: Chọn 7 màu + RGB", "PLAY: Pick 7 colors + RGB"), 1, COL_MUTED, COL_CARD, 196);
      } else if (i == 2) {
        snprintf(buf, sizeof(buf), "%s%s: %s", pfx.c_str(), tr("CHẾ ĐỘ MÀU", "COLOR MODE"),
                 stationEnglish ? LED_EFFECT_NAMES_EN[ledEffectIndex] : LED_EFFECT_NAMES[ledEffectIndex]);
        drawUtf8Text(20, y + 10, String(buf), 1, titleCol, COL_CARD, 200);
        drawUtf8Text(24, y + 28, tr("PLAY: Tĩnh, RGB, Nhịp thở...", "PLAY: Solid, RGB, Chase..."), 1, COL_MUTED, COL_CARD, 196);
      } else if (i == 3) {
        snprintf(buf, sizeof(buf), "%s%s: %d%%", pfx.c_str(), tr("ĐỘ SÁNG LED", "LED BRIGHTNESS"), ledBrightness);
        drawUtf8Text(20, y + 10, String(buf), 1, titleCol, COL_CARD, 200);
        drawUtf8Text(24, y + 28, tr("Chỉnh độ sáng vòng LED", "Adjust ring brightness (0-100%)"), 1, COL_MUTED, COL_CARD, 196);
      } else if (i == 4) {
        snprintf(buf, sizeof(buf), "%s%s: %d%%", pfx.c_str(), tr("TỐC ĐỘ LED", "LED SPEED"), ledSpeed);
        drawUtf8Text(20, y + 10, String(buf), 1, titleCol, COL_CARD, 200);
        drawUtf8Text(24, y + 28, tr("Slider tốc độ hiệu ứng (5-100%)", "Effect speed slider (5-100%)"), 1, COL_MUTED, COL_CARD, 196);
      } else if (i == 5) {
        snprintf(buf, sizeof(buf), "%s%s: %dK", pfx.c_str(), tr("NHIỆT ĐỘ MÀU", "COLOR TEMP"), ledKelvin);
        drawUtf8Text(20, y + 10, String(buf), 1, titleCol, COL_CARD, 200);
        drawUtf8Text(24, y + 28, tr("Slider 2700K (Ấm) -> 6500K (Lạnh)", "Slider 2700K (Warm) -> 6500K"), 1, COL_MUTED, COL_CARD, 196);
      } else if (i == 6) {
        snprintf(buf, sizeof(buf), "%s%s: %s", pfx.c_str(), tr("CÒI CHÍP", "BUZZER"),
                 buzzerEnabled ? tr("BẬT", "ON") : tr("TẮT (MUTE)", "OFF (MUTE)"));
        drawUtf8Text(20, y + 10, String(buf), 1, titleCol, COL_CARD, 200);
        snprintf(buf, sizeof(buf), tr("Độ dài beep: %d ms", "Beep length: %d ms"), buzzerDurationSetting);
        drawUtf8Text(24, y + 28, String(buf), 1, COL_MUTED, COL_CARD, 196);
      } else if (i == 7) {
        if (spotifyVolumeKnown) {
          snprintf(buf, sizeof(buf), "%s%s: %d%%", pfx.c_str(),
                   tr("ÂM LƯỢNG SPOTIFY", "SPOTIFY VOLUME"), spotifyVolume);
        } else {
          snprintf(buf, sizeof(buf), "%s%s: --", pfx.c_str(),
                   tr("ÂM LƯỢNG SPOTIFY", "SPOTIFY VOLUME"));
        }
        drawUtf8Text(20, y + 10, String(buf), 1, titleCol, COL_CARD, 200);
        drawUtf8Text(24, y + 28, tr("PLAY: Vào | -/+ : 5%", "PLAY: Open | PREV/NEXT: 5%"), 1, COL_MUTED, COL_CARD, 196);
      } else if (i == 8) {
        String modeStr = tr("TẮT (LIGHT)", "OFF (LIGHT)");
        if (darkModeSetting == "always_on" || darkModeSetting == "always") modeStr = tr("LUÔN BẬT (DARK)", "ALWAYS (DARK)");
        else if (darkModeSetting == "scheduled" || darkModeSetting == "custom") modeStr = tr("THEO LỊCH (AUTO)", "SCHEDULED");
        snprintf(buf, sizeof(buf), "%s%s: %s", pfx.c_str(), tr("CHẾ ĐỘ TỐI", "DARK MODE"), modeStr.c_str());
        drawUtf8Text(20, y + 10, String(buf), 1, titleCol, COL_CARD, 200);
        snprintf(buf, sizeof(buf), tr("Lịch: %s - %s", "Schedule: %s - %s"), darkModeStart.c_str(), darkModeEnd.c_str());
        drawUtf8Text(24, y + 28, String(buf), 1, COL_MUTED, COL_CARD, 196);
      } else if (i == 9) {
        snprintf(buf, sizeof(buf), "%s%s: %s", pfx.c_str(), tr("NGÔN NGỮ", "LANGUAGE"),
                 stationEnglish ? "ENGLISH" : "TIẾNG VIỆT");
        drawUtf8Text(20, y + 10, String(buf), 1, titleCol, COL_CARD, 200);
        drawUtf8Text(24, y + 28, tr("PLAY: Đổi VI <-> EN", "PLAY: Switch VI <-> EN"), 1, COL_MUTED, COL_CARD, 196);
      } else if (i == 10) {
        snprintf(buf, sizeof(buf), "%s%s", pfx.c_str(), tr("LIÊN KẾT THIẾT BỊ", "PAIR NEW DEVICE"));
        drawUtf8Text(20, y + 10, String(buf), 1, titleCol, COL_CARD, 200);
        drawUtf8Text(24, y + 28, tr("Hiện QR & Mã Full Access", "Show Full Access QR & Code"), 1, COL_MUTED, COL_CARD, 196);
      } else if (i == 11) {
        snprintf(buf, sizeof(buf), "%s%s", pfx.c_str(), tr("CẬP NHẬT OTA", "OTA UPDATE"));
        drawUtf8Text(20, y + 10, String(buf), 1, titleCol, COL_CARD, 200);
        drawUtf8Text(24, y + 28, tr("PLAY: Kiểm tra & Nạp mới", "PLAY: Check & Flash"), 1, COL_MUTED, COL_CARD, 196);
      } else if (i == 12) {
        snprintf(buf, sizeof(buf), "%s%s", pfx.c_str(), tr("THOÁT CÀI ĐẶT", "EXIT SETTINGS"));
        drawUtf8Text(20, y + 10, String(buf), 1, titleCol, COL_CARD, 200);
        drawUtf8Text(24, y + 28, tr("PLAY hoặc giữ >>/<<", "PLAY or hold >>/<<"), 1, COL_MUTED, COL_CARD, 196);
      }
    }

    tft->drawFastHLine(10, 288, 220, COL_BORDER);
    String pageStr = String(menuPage + 1) + "/" + String(pageCount) + "  " +
                     tr("PREV/NEXT: Chọn | PLAY: Vào", "PREV/NEXT: Select | PLAY: Open");
    printCenteredText(pageStr, 298, 1, COL_MUTED, COL_BG);
    printCenteredText(tr("Giữ NEXT để thoát", "Hold NEXT to exit"), 310, 1, COL_MUTED, COL_BG);

  } else if (menuState == MENU_ADJUST_LED) {
    printCenteredText(tr("CHỈNH ĐỘ SÁNG LED (%)", "LED BRIGHTNESS (%)"), 52, 1, COL_CYAN, COL_BG);

    tft->fillRoundRect(16, 75, 208, 195, 8, COL_CARD);
    tft->drawRoundRect(16, 75, 208, 195, 8, COL_BORDER);

    tft->setTextSize(4);
    tft->setTextColor(COL_AMBER, COL_CARD);
    String pctStr = String(ledBrightness) + "%";
    int pw = (int)pctStr.length() * 24;
    tft->setCursor((240 - pw) / 2, 110);
    tft->print(pctStr);

    drawProgressBarSmooth(28, 175, 184, 16, ledBrightness, COL_AMBER);

    printCenteredText(tr("PREV: -5% | NEXT: +5%", "PREV: -5% | NEXT: +5%"), 210, 1, COL_WHITE, COL_CARD);
    printCenteredText(tr("Nhấn PLAY để Lưu & Thoát", "Press PLAY to save & exit"), 235, 1, COL_CYAN, COL_CARD);

    tft->drawFastHLine(10, 296, 220, COL_BORDER);
    printCenteredText(tr("Giữ NEXT: Quay lại menu", "Hold NEXT: back to menu"), 304, 1, COL_MUTED, COL_BG);

  } else if (menuState == MENU_LED_COLOR) {
    // 4 colours per page in a 2x2 grid: large, readable cells on the 240x320
    // panel. Moving past the 4th cell flips to the next page.
    const int COLOR_PER_PAGE = 4;
    int colorPages = (LED_PRESET_COUNT + COLOR_PER_PAGE - 1) / COLOR_PER_PAGE;
    if (colorPage >= colorPages) colorPage = colorPages - 1;
    if (colorPage < 0) colorPage = 0;
    int firstColor = colorPage * COLOR_PER_PAGE;

    String title = String(tr("MÀU ĐÈN LED", "LED LAMP COLOR")) + " (" +
                   String(colorPage + 1) + "/" + String(colorPages) + ")";
    printCenteredText(title, 50, 2, COL_CYAN, COL_BG);

    int cellW = 100;
    int cellH = 86;
    int gridX = 14;
    int gridY = 92;
    int stepX = 110;
    int stepY = 96;

    for (int slot = 0; slot < COLOR_PER_PAGE; slot++) {
      int i = firstColor + slot;
      if (i >= LED_PRESET_COUNT) break;
      int col = slot % 2;
      int row = slot / 2;
      int x = gridX + col * stepX;
      int y = gridY + row * stepY;
      bool selected = (i == ledPresetIndex);

      tft->fillRoundRect(x, y, cellW, cellH, 8, COL_CARD);
      tft->drawRoundRect(x, y, cellW, cellH, 8, selected ? COL_AMBER : COL_BORDER);
      tft->drawRoundRect(x - 1, y - 1, cellW + 2, cellH + 2, 9, selected ? COL_AMBER : COL_BG);

      if (String(LED_PRESETS[i]) == "RGB") {
        tft->fillCircle(x + cellW / 2 - 7, y + 29, 12, 0xF800);
        tft->fillCircle(x + cellW / 2 + 7, y + 29, 12, 0x07E0);
        tft->fillCircle(x + cellW / 2, y + 37, 12, 0x001F);
      } else {
        uint8_t r, g, b;
        parseHexColor(LED_PRESETS[i], r, g, b);
        tft->fillCircle(x + cellW / 2, y + 32, 20, hwTft->color565(r, g, b));
      }

      const char* name = stationEnglish ? LED_PRESET_NAMES_EN[i] : LED_PRESET_NAMES[i];
      uint16_t nameCol = selected ? COL_AMBER : COL_WHITE;
      int nameW = vnTextWidth(vnFont12, name);
      drawUtf8Text(x + max(4, (cellW - nameW) / 2), y + 60, String(name), 1, nameCol, COL_CARD, cellW - 8);
    }

    tft->drawFastHLine(10, 296, 220, COL_BORDER);
    printCenteredText(tr("PREV/NEXT: Chọn | PLAY: Áp dụng", "PREV/NEXT: Select | PLAY: Apply"), 304, 1, COL_MUTED, COL_BG);

  } else if (menuState == MENU_LED_EFFECT) {
    const int EFF_PER_PAGE = 4;
    int effPages = (LED_EFFECT_COUNT + EFF_PER_PAGE - 1) / EFF_PER_PAGE;
    if (effectPage >= effPages) effectPage = effPages - 1;
    if (effectPage < 0) effectPage = 0;
    int firstEff = effectPage * EFF_PER_PAGE;

    String title = String(tr("CHẾ ĐỘ MÀU LED", "LED COLOR MODE")) + " (" +
                   String(effectPage + 1) + "/" + String(effPages) + ")";
    printCenteredText(title, 50, 1, COL_CYAN, COL_BG);

    int startY = 74;
    int cardH = 48;
    int gap = 7;
    for (int slot = 0; slot < EFF_PER_PAGE; slot++) {
      int i = firstEff + slot;
      if (i >= LED_EFFECT_COUNT) break;
      int y = startY + slot * (cardH + gap);
      bool selected = (i == ledEffectIndex);
      bool active = (ledEffect == String(LED_EFFECT_IDS[i]));

      tft->fillRoundRect(14, y, 212, cardH, 6, COL_CARD);
      tft->drawRoundRect(14, y, 212, cardH, 6, selected ? COL_AMBER : (active ? COL_GREEN : COL_BORDER));
      tft->drawRoundRect(13, y - 1, 214, cardH + 2, 7, selected ? COL_AMBER : COL_BG);

      String pfx = selected ? "> " : "  ";
      String label = pfx + String(stationEnglish ? LED_EFFECT_NAMES_EN[i] : LED_EFFECT_NAMES[i]);
      drawUtf8Text(22, y + 10, label, 1, selected ? COL_AMBER : (active ? COL_GREEN : COL_WHITE), COL_CARD, 196);
      if (active) {
        drawUtf8Text(26, y + 28, tr("[ĐANG CHẠY]", "[ACTIVE]"), 1, COL_GREEN, COL_CARD, 180);
      }
    }
    tft->drawFastHLine(10, 296, 220, COL_BORDER);
    printCenteredText(tr("PREV/NEXT: Chọn | PLAY: Áp dụng", "PREV/NEXT: Select | PLAY: Apply"), 304, 1, COL_MUTED, COL_BG);

  } else if (menuState == MENU_LED_SPEED) {
    printCenteredText(tr("TỐC ĐỘ HIỆU ỨNG LED (%)", "LED EFFECT SPEED (%)"), 52, 1, COL_CYAN, COL_BG);
    tft->fillRoundRect(16, 75, 208, 195, 8, COL_CARD);
    tft->drawRoundRect(16, 75, 208, 195, 8, COL_BORDER);
    tft->setTextSize(4);
    tft->setTextColor(COL_CYAN, COL_CARD);
    String spdStr = String(ledSpeed) + "%";
    int sw = (int)spdStr.length() * 24;
    tft->setCursor((240 - sw) / 2, 110);
    tft->print(spdStr);
    drawProgressBarSmooth(28, 175, 184, 16, ledSpeed, COL_CYAN);
    printCenteredText(tr("PREV: -5% | NEXT: +5%", "PREV: -5% | NEXT: +5%"), 210, 1, COL_WHITE, COL_CARD);
    printCenteredText(tr("Nhấn PLAY để Lưu & Thoát", "Press PLAY to Save & Exit"), 235, 1, COL_AMBER, COL_CARD);
    tft->drawFastHLine(10, 296, 220, COL_BORDER);

  } else if (menuState == MENU_LED_KELVIN) {
    printCenteredText(tr("NHIỆT ĐỘ MÀU KELVIN", "COLOR TEMP (KELVIN)"), 52, 1, COL_AMBER, COL_BG);
    tft->fillRoundRect(16, 75, 208, 195, 8, COL_CARD);
    tft->drawRoundRect(16, 75, 208, 195, 8, COL_BORDER);
    tft->setTextSize(3);
    tft->setTextColor(COL_AMBER, COL_CARD);
    String kStr = String(ledKelvin) + "K";
    int kw = (int)kStr.length() * 18;
    tft->setCursor((240 - kw) / 2, 114);
    tft->print(kStr);
    int kPct = map(ledKelvin, 2700, 6500, 0, 100);
    drawProgressBarSmooth(28, 175, 184, 16, kPct, COL_AMBER);
    printCenteredText(tr("PREV: Ấm | NEXT: Lạnh", "PREV: Warm | NEXT: Cool"), 210, 1, COL_WHITE, COL_CARD);
    printCenteredText(tr("Nhấn PLAY để Lưu & Thoát", "Press PLAY to Save & Exit"), 235, 1, COL_CYAN, COL_CARD);
    tft->drawFastHLine(10, 296, 220, COL_BORDER);

  } else if (menuState == MENU_VOLUME) {
    printCenteredText(tr("ÂM LƯỢNG SPOTIFY (%)", "SPOTIFY VOLUME (%)"), 52, 1, COL_CYAN, COL_BG);

    tft->fillRoundRect(16, 75, 208, 195, 8, COL_CARD);
    tft->drawRoundRect(16, 75, 208, 195, 8, COL_BORDER);

    tft->setTextSize(4);
    tft->setTextColor(COL_GREEN, COL_CARD);
    String volStr = spotifyVolumeKnown ? (String(spotifyVolume) + "%") : "--%";
    int vw = (int)volStr.length() * 24;
    tft->setCursor((240 - vw) / 2, 110);
    tft->print(volStr);

    drawProgressBarSmooth(28, 175, 184, 16, spotifyVolume, COL_GREEN);

    printCenteredText(tr("PREV: -5% | NEXT: +5%", "PREV: -5% | NEXT: +5%"), 210, 1, COL_WHITE, COL_CARD);
    printCenteredText(tr("Nhấn PLAY để Thoát", "Press PLAY to exit"), 235, 1, COL_CYAN, COL_CARD);
    if (!spotifyVolumeKnown) {
      printCenteredText(tr("Chưa có dữ liệu Spotify", "No Spotify data yet"), 258, 1, COL_MUTED, COL_CARD);
    }

    tft->drawFastHLine(10, 296, 220, COL_BORDER);
    printCenteredText(tr("Giữ NEXT: Quay lại menu", "Hold NEXT: back to menu"), 304, 1, COL_MUTED, COL_BG);

  } else if (menuState == MENU_BUZZER_CONFIG) {
    printCenteredText(tr("CẤU HÌNH CÒI CHÍP", "BUZZER CONFIG"), 46, 1, COL_CYAN, COL_BG);

    int startY = 62;
    int cardH = 40;
    int gap = 5;

    for (int i = 0; i < MENU_BUZZER_ITEM_COUNT; i++) {
      int y = startY + i * (cardH + gap);
      bool selected = (buzzerCurrentItem == i);
      uint16_t bCol = selected ? COL_AMBER : COL_BORDER;

      tft->fillRoundRect(14, y, 212, cardH, 6, COL_CARD);
      tft->drawRoundRect(14, y, 212, cardH, 6, bCol);
      tft->drawRoundRect(13, y - 1, 214, cardH + 2, 7, selected ? COL_AMBER : COL_BG);

      uint16_t titleCol = selected ? COL_AMBER : COL_WHITE;
      String pfx = selected ? "> " : "  ";
      char buf[96];

      if (i == 0) {
        snprintf(buf, sizeof(buf), "%s%s: %s", pfx.c_str(), tr("CÒI BÍP", "BUZZER"),
                 buzzerEnabled ? tr("[BẬT]", "[ON]") : tr("[TẮT]", "[OFF]"));
        drawUtf8Text(22, y + 6, String(buf), 1, titleCol, COL_CARD, 196);
        drawUtf8Text(24, y + 22, tr("PLAY: Bật / tắt âm thanh", "PLAY: On / off sound"), 1, COL_MUTED, COL_CARD, 194);
      } else if (i == 1) {
        snprintf(buf, sizeof(buf), "%s%s: %d ms", pfx.c_str(), tr("ĐỘ DÀI BEEP", "BEEP LENGTH"), buzzerDurationSetting);
        drawUtf8Text(22, y + 6, String(buf), 1, titleCol, COL_CARD, 196);
        drawUtf8Text(24, y + 22, tr("PLAY: Đổi độ dài bíp", "PLAY: Cycle beep length"), 1, COL_MUTED, COL_CARD, 194);
      } else if (i == 2) {
        snprintf(buf, sizeof(buf), "%s%s: %s", pfx.c_str(), tr("BEEP DI CHUYỂN", "NAV BEEP"),
                 navBeepEnabled ? tr("[BẬT]", "[ON]") : tr("[TẮT]", "[OFF]"));
        drawUtf8Text(22, y + 6, String(buf), 1, titleCol, COL_CARD, 196);
        drawUtf8Text(24, y + 22, tr("PLAY: Bật/tắt tiếng bấm nút", "PLAY: Toggle button beep"), 1, COL_MUTED, COL_CARD, 194);
      } else if (i == 3) {
        snprintf(buf, sizeof(buf), "%s%s", pfx.c_str(), tr("THỬ TIẾNG CÒI (TEST)", "TEST BUZZER"));
        drawUtf8Text(22, y + 6, String(buf), 1, titleCol, COL_CARD, 196);
        snprintf(buf, sizeof(buf), tr("PLAY: Phát thử %dHz", "PLAY: Test %dHz"), MORSE_TEST_FREQ);
        drawUtf8Text(24, y + 22, String(buf), 1, COL_MUTED, COL_CARD, 194);
      } else if (i == 4) {
        snprintf(buf, sizeof(buf), "%s%s", pfx.c_str(), tr("QUAY LẠI MENU CHÍNH", "BACK TO MAIN MENU"));
        drawUtf8Text(22, y + 6, String(buf), 1, titleCol, COL_CARD, 196);
        drawUtf8Text(24, y + 22, tr("Trở về cài đặt chính", "Return to settings"), 1, COL_MUTED, COL_CARD, 194);
      }
    }

    tft->drawFastHLine(10, 296, 220, COL_BORDER);
    printCenteredText(tr("PREV/NEXT: Chọn | PLAY: Chọn", "PREV/NEXT: Select | PLAY: Activate"), 304, 1, COL_MUTED, COL_BG);
  } else if (menuState == MENU_PAIR_DEVICE) {
    printCenteredText(tr("LIÊN KẾT THIẾT BỊ (FULL)", "PAIR DEVICE (FULL ACCESS)"), 46, 1, COL_CYAN, COL_BG);

    if (pairCodeLoading || pairCodeValue.length() == 0) {
      tft->fillRoundRect(20, 74, 200, 172, 10, COL_CARD);
      tft->drawRoundRect(20, 74, 200, 172, 10, COL_BORDER);
      printCenteredText(pairCodeLoading ? tr("Đang tạo mã QR...", "Generating QR...") : tr("Chưa nhận được mã", "No pairing code"), 148, 1, COL_AMBER, COL_CARD);
      printCenteredText(tr("NEXT/PREV: Thử lại", "NEXT/PREV: Retry"), 170, 1, COL_MUTED, COL_CARD);
    } else {
      int qrArea = 152;
      int cell = max(2, min(5, qrArea / max(1, pairQrSize)));
      int drawSz = pairQrSize * cell;
      int boxPad = 6;
      int boxX = (240 - drawSz) / 2 - boxPad;
      int boxY = 62;
      tft->fillRoundRect(boxX, boxY, drawSz + boxPad * 2, drawSz + boxPad * 2, 6, 0xFFFF);
      int startX = boxX + boxPad;
      int startY = boxY + boxPad;
      int totalBits = pairQrSize * pairQrSize;
      for (int b = 0; b < totalBits; b++) {
        int hexIdx = b >> 2;
        if (hexIdx >= (int)pairQrBits.length()) break;
        char hc = pairQrBits.charAt(hexIdx);
        int nib = (hc >= '0' && hc <= '9') ? (hc - '0') : ((hc >= 'A' && hc <= 'F') ? (hc - 'A' + 10) : ((hc >= 'a' && hc <= 'f') ? (hc - 'a' + 10) : 0));
        bool on = (nib >> (3 - (b & 3))) & 1;
        if (on) {
          tft->fillRect(startX + (b % pairQrSize) * cell, startY + (b / pairQrSize) * cell, cell, cell, 0x0000);
        }
      }

      int codeY = boxY + drawSz + boxPad * 2 + 8;
      tft->fillRoundRect(22, codeY, 196, 42, 8, COL_CARD);
      tft->drawRoundRect(22, codeY, 196, 42, 8, COL_GREEN);
      tft->setTextSize(3);
      tft->setTextColor(COL_WHITE, COL_CARD);
      int cw = pairCodeValue.length() * 18;
      tft->setCursor(max(26, (240 - cw) / 2), codeY + 9);
      tft->print(pairCodeValue);
      tft->setTextSize(1);
      printCenteredText(tr("FULL ACCESS • 10 PHÚT • DÙNG 1 LẦN", "FULL ACCESS • 10 MIN • ONE-TIME"), codeY + 50, 1, COL_GREEN, COL_BG);
    }

    tft->drawFastHLine(10, 294, 220, COL_BORDER);
    printCenteredText(tr("PLAY: Quay lại  |  NEXT: Mã mới", "PLAY: Back  |  NEXT: New code"), 304, 1, COL_MUTED, COL_BG);
  }
}

void drawViewMenu() {
  static int lastDrawnViewPage = -1;
  static int lastDrawnViewIdx = -1;
  int pageCount = (VIEW_MENU_COUNT + VIEW_ROWS_PER_PAGE - 1) / VIEW_ROWS_PER_PAGE;
  if (viewMenuPage >= pageCount) viewMenuPage = pageCount - 1;
  if (viewMenuPage < 0) viewMenuPage = 0;

  bool fullClear = menuForceFullRedraw || (lastDrawnViewPage != viewMenuPage);
  menuForceFullRedraw = false;
  if (fullClear) {
    tft->fillScreen(COL_BG);
    // Header Box
    tft->fillRoundRect(10, 8, 220, 30, 6, COL_CARD);
    tft->drawRoundRect(10, 8, 220, 30, 6, COL_CYAN);
    printCenteredText(tr("DANH SÁCH MÀN HÌNH", "SCREEN LIST"), 16, 1, COL_CYAN, COL_CARD);
  }
  int firstItem = viewMenuPage * VIEW_ROWS_PER_PAGE;

  int startY = 46;
  int tileW = 108;
  int tileH = 74;
  int gapX = 8;
  int gapY = 8;

  for (int rowIdx = 0; rowIdx < VIEW_ROWS_PER_PAGE; rowIdx++) {
    int i = firstItem + rowIdx;
    if (i >= VIEW_MENU_COUNT) break;
    if (!fullClear && i != viewMenuIndex && i != lastDrawnViewIdx) continue;
    int col = rowIdx % 2;
    int row = rowIdx / 2;
    int x = 8 + col * (tileW + gapX);
    int y = startY + row * (tileH + gapY);
    bool selected = (viewMenuIndex == i);
    bool isCurrent = (srvStats.activeView == VIEW_MENU_ITEMS[i].id);

    uint16_t bg = selected ? COL_CYAN : COL_CARD;
    uint16_t border = selected ? COL_WHITE : (isCurrent ? COL_GREEN : COL_BORDER);
    uint16_t textCol = selected ? COL_BG : (isCurrent ? COL_GREEN : COL_WHITE);

    tft->fillRoundRect(x, y, tileW, tileH, 8, bg);
    tft->drawRoundRect(x, y, tileW, tileH, 8, border);

    String idxBadge = String(i + 1) + (isCurrent ? " *" : "");
    drawUtf8Text(x + 8, y + 7, idxBadge, 1, selected ? COL_BG : COL_CYAN, bg, tileW - 14);

    // Strip redundant "N. " prefix since tile already displays idxBadge at top-left,
    // then word-wrap onto 2 lines so no title gets truncated with "..."
    String title = viewMenuTitle(i);
    int dotPos = title.indexOf(". ");
    if (dotPos >= 0 && dotPos <= 3) title = title.substring(dotPos + 2);
    String line1 = title, line2 = "";
    if (title.length() > 13) {
      int sp = title.lastIndexOf(' ', 13);
      if (sp <= 0) sp = title.indexOf(' ');
      if (sp > 0) {
        line1 = title.substring(0, sp);
        line2 = title.substring(sp + 1);
      }
    }
    drawUtf8Text(x + 8, y + 25, line1, 1, textCol, bg, tileW - 14);
    if (line2.length() > 0) {
      drawUtf8Text(x + 8, y + 40, line2, 1, textCol, bg, tileW - 14);
    }

    if (isCurrent) {
      drawUtf8Text(x + 8, y + 57, tr("ĐANG MỞ", "ACTIVE"), 1, selected ? COL_BG : COL_GREEN, bg, tileW - 14);
    }
  }

  if (fullClear) {
    tft->drawFastHLine(10, 288, 220, COL_BORDER);
    String pageStr = String(viewMenuPage + 1) + "/" + String(pageCount) + "  " +
                     tr("[Prev/Next]: Tìm  [Play]: Chọn", "[Prev/Next]: Browse  [Play]: Select");
    printCenteredText(pageStr, 300, 1, COL_MUTED, COL_BG);
  }
  lastDrawnViewPage = viewMenuPage;
  lastDrawnViewIdx = viewMenuIndex;
}

void drawNotesView() {
  printCenteredText(tr("GHI CHÚ ĐANG GHIM", "PINNED NOTES"), 36, 2, COL_CYAN, COL_BG);

  if (stationNotesCount == 0) {
    tft->fillRoundRect(14, 75, 212, 195, 8, COL_CARD);
    tft->drawRoundRect(14, 75, 212, 195, 8, COL_BORDER);
    printCenteredText(tr("Chưa có ghi chú nào", "No notes yet"), 120, 1, COL_MUTED, COL_CARD);
    printCenteredText(tr("Thêm ghi chú từ app DeskFlow", "Add notes in the DeskFlow app"), 150, 1, COL_WHITE, COL_CARD);
    printCenteredText(tr("Ghi chú sẽ tự động đồng bộ!", "Notes sync automatically!"), 175, 1, COL_AMBER, COL_CARD);
  } else {
    if (activeNoteIndex >= stationNotesCount) activeNoteIndex = 0;
    auto& note = stationNotes[activeNoteIndex];

    String pageStr = String(tr("Ghi chú: ", "Note: ")) + (activeNoteIndex + 1) + "/" + stationNotesCount;
    printCenteredText(pageStr, 58, 1, COL_MUTED, COL_BG);

    tft->fillRoundRect(14, 75, 212, 195, 8, COL_CARD);
    tft->drawRoundRect(14, 75, 212, 195, 8, note.isPinned ? COL_CYAN : COL_BORDER);

    if (note.isPinned) {
      tft->fillRoundRect(22, 85, 100, 18, 4, COL_CYAN);
      drawUtf8Text(28, 90, tr("* ĐANG GHIM *", "* PINNED *"), 1, COL_BG, COL_CYAN, 90);
    } else {
      drawUtf8Text(24, 90, tr("Ghi chú cá nhân", "Personal note"), 1, COL_MUTED, COL_CARD, 192);
    }

    drawUtf8Text(24, 112, note.title, 1, COL_AMBER, COL_CARD, 192);

    tft->drawFastHLine(24, 126, 192, COL_BORDER);

    printBigWrappedCenteredText(note.content, 185, 1, COL_WHITE, 26, 16, COL_CARD);
  }

  printCenteredText("< PREV               NEXT >", 280, 1, COL_MUTED, COL_BG);
}

void drawCountdownView() {
  printCenteredText(tr("SỰ KIỆN ĐẾM NGƯỢC", "EVENT COUNTDOWN"), 36, 2, COL_AMBER, COL_BG);

  if (stationCountdownsCount == 0) {
    tft->fillRoundRect(14, 75, 212, 195, 8, COL_CARD);
    tft->drawRoundRect(14, 75, 212, 195, 8, COL_BORDER);
    printCenteredText(tr("Chưa có sự kiện đếm ngược", "No countdown events yet"), 120, 1, COL_MUTED, COL_CARD);
    printCenteredText(tr("Tạo sự kiện từ app DeskFlow", "Create one in the DeskFlow app"), 150, 1, COL_WHITE, COL_CARD);
    printCenteredText(tr("Hỗ trợ đếm ngược ngày thi", "Day-accurate countdown"), 175, 1, COL_CYAN, COL_CARD);
  } else {
    if (activeCountdownIndex >= stationCountdownsCount) activeCountdownIndex = 0;
    auto& cd = stationCountdowns[activeCountdownIndex];

    String pageStr = String(tr("Sự kiện: ", "Event: ")) + (activeCountdownIndex + 1) + "/" + stationCountdownsCount;
    printCenteredText(pageStr, 58, 1, COL_MUTED, COL_BG);

    tft->fillRoundRect(14, 75, 212, 195, 8, COL_CARD);
    tft->drawRoundRect(14, 75, 212, 195, 8, cd.isPinned ? COL_AMBER : COL_BORDER);

    if (cd.isPinned) {
      tft->fillRoundRect(22, 85, 120, 18, 4, COL_AMBER);
      drawUtf8Text(28, 90, tr("* ĐANG GHIM TRẠM *", "* PINNED *"), 1, COL_BG, COL_AMBER, 110);
    } else {
      drawUtf8Text(24, 90, tr("Sự kiện quan trọng", "Important event"), 1, COL_MUTED, COL_CARD, 192);
    }

    printCenteredText(cd.title, 115, 2, COL_WHITE, COL_CARD);

    String dStr = String(tr("Ngày đích: ", "Target date: ")) + cd.targetDate.substring(0, 10);
    printCenteredText(dStr, 142, 1, COL_MUTED, COL_CARD);

    if (cd.isExpired || cd.daysLeft <= 0) {
      tft->fillRoundRect(24, 162, 192, 52, 6, 0x6000);
      tft->drawRoundRect(24, 162, 192, 52, 6, 0xF800);
      printCenteredText(tr("ĐÃ KẾT THÚC", "FINISHED"), 182, 2, 0xF800, 0x6000);
    } else {
      tft->fillRoundRect(24, 162, 192, 52, 6, COL_BG);
      tft->drawRoundRect(24, 162, 192, 52, 6, COL_AMBER);
      String daysStr = String(tr("CÒN ", "IN ")) + cd.daysLeft + tr(" NGÀY", " DAYS");
      printCenteredText(daysStr, 180, 2, COL_AMBER, COL_BG);
    }
  }

  printCenteredText("< PREV               NEXT >", 280, 1, COL_MUTED, COL_BG);
}

String translateCalLabel(const String& raw) {
  if (!stationEnglish) return raw;
  if (raw == "Hôm nay") return "Today";
  if (raw == "Ngày mai") return "Tomorrow";
  if (raw == "Cả ngày") return "All day";
  return raw;
}

void drawCalendarView(bool viewChanged) {
  static bool lastDetail = false;
  static bool lastNextM = false;
  static int lastCursor = -1;

  if (viewChanged) {
    calInDayDetail = false;
    calShowNextMonth = false;
    calCursorDay = calTodayDay;
    calForceRedraw = true;
  }

  ensureCalCursorValid();
  uint16_t ay = calShowNextMonth ? calNextYear : calCurYear;
  uint8_t am = calShowNextMonth ? calNextMonth : calCurMonth;
  int firstWd = calShowNextMonth ? calNextFirstWd : calCurFirstWd;
  int dim = calShowNextMonth ? calNextDaysInMonth : calCurDaysInMonth;

  bool fullRedraw = viewChanged || calForceRedraw || (calInDayDetail != lastDetail) || (calShowNextMonth != lastNextM);
  if (fullRedraw && !viewChanged) {
    tft->fillRect(0, 26, 240, 270, COL_BG);
  }
  calForceRedraw = false;

  if (calInDayDetail) {
    // --- DAY DETAIL VIEW (when a day is selected via PLAY) ---
    int wd = (firstWd + calCursorDay - 1) % 7; // 0=Sun..6=Sat
    static const char* VI_WD_FULL[] = {"CN", "T2", "T3", "T4", "T5", "T6", "T7"};
    static const char* EN_WD_FULL[] = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    bool isToday = (!calShowNextMonth && calCursorDay == calTodayDay);

    String hdr = (isToday ? String(tr("HÔM NAY • ", "TODAY • ")) : String("")) +
                 String(stationEnglish ? EN_WD_FULL[wd] : VI_WD_FULL[wd]) +
                 ", " + String(calCursorDay) + "/" + String(am) + "/" + String(ay);
    printCenteredText(hdr, 34, 2, isToday ? COL_GREEN : COL_CYAN, COL_BG);

    int idxs[12];
    int totalEv = getCalDayEvents(ay, am, calCursorDay, idxs, 12);

    if (totalEv == 0) {
      tft->fillRoundRect(14, 72, 212, 196, 8, COL_CARD);
      tft->drawRoundRect(14, 72, 212, 196, 8, isToday ? COL_GREEN : COL_BORDER);
      printCenteredText(tr("Không có việc nào", "Nothing scheduled"), 142, 1, COL_WHITE, COL_CARD);
      printCenteredText(tr("Ngày này trống lịch", "Free day!"), 170, 1, COL_MUTED, COL_CARD);
      printCenteredText(tr("[PLAY]: Quay lại lịch tháng", "[PLAY]: Back to month grid"), 282, 1, COL_MUTED, COL_BG);
    } else {
      int shownCnt = min(totalEv, 12);
      if (calDetailIdx >= shownCnt) calDetailIdx = 0;
      String cntStr = String(tr("Việc: ", "Event: ")) + String(calDetailIdx + 1) + "/" + String(totalEv);
      printCenteredText(cntStr, 55, 1, COL_MUTED, COL_BG);

      auto& ev = stationCalEvents[idxs[calDetailIdx]];
      tft->fillRoundRect(12, 72, 216, 114, 8, COL_CARD);
      tft->drawRoundRect(12, 72, 216, 114, 8, isToday ? COL_GREEN : COL_CYAN);

      String tStr = translateCalLabel(ev.timeStr);
      tft->fillRoundRect(20, 80, 196, 26, 6, COL_BG);
      printCenteredText(tStr, 86, 2, COL_AMBER, COL_BG);

      // Wrap title cleanly inside card (up to 2 lines)
      String ttl = ev.title;
      ttl.trim();
      int maxW = 196;
      if (vnTextWidth(vnFont17, ttl.c_str()) <= maxW) {
        drawUtf8Text(20, 116, ttl, 2, COL_WHITE, COL_CARD, maxW);
      } else {
        int splitPos = -1;
        for (size_t i = 0; i < ttl.length(); i++) {
          if (ttl[i] == ' ' && vnTextWidth(vnFont12, ttl.substring(0, i).c_str()) <= maxW) {
            splitPos = (int)i;
          }
        }
        if (splitPos > 0) {
          drawUtf8Text(20, 116, ttl.substring(0, splitPos), 1, COL_WHITE, COL_CARD, maxW);
          drawUtf8Text(20, 134, ttl.substring(splitPos + 1), 1, COL_WHITE, COL_CARD, maxW);
        } else {
          drawUtf8Text(20, 120, ttl, 1, COL_WHITE, COL_CARD, maxW);
        }
      }

      if (ev.location.length() > 0) {
        drawUtf8Text(20, 162, String("@ ") + ev.location, 1, COL_MUTED, COL_CARD, maxW);
      } else {
        drawUtf8Text(20, 162, tr("Bấm << / >> xem việc khác", "Use << / >> for more"), 1, COL_MUTED, COL_CARD, maxW);
      }

      // Summary list of events in that day (Y=192..268)
      tft->fillRoundRect(12, 192, 216, 76, 8, COL_CARD);
      tft->drawRoundRect(12, 192, 216, 76, 8, COL_BORDER);
      int listStart = (calDetailIdx / 3) * 3;
      for (int row = 0; row < 3; row++) {
        int j = listStart + row;
        if (j >= shownCnt) break;
        auto& item = stationCalEvents[idxs[j]];
        bool cur = (j == calDetailIdx);
        int ry = 200 + row * 22;
        String line = (cur ? "> " : "  ") + translateCalLabel(item.timeStr) + " " + item.title;
        drawUtf8Text(18, ry, line, 1, cur ? COL_CYAN : COL_WHITE, COL_CARD, 202);
      }

      printCenteredText(tr("<< / >>: Đổi việc   [PLAY]: Thoát", "<< / >>: Event   [PLAY]: Back"), 282, 1, COL_MUTED, COL_BG);
    }

    lastDetail = calInDayDetail;
    lastNextM = calShowNextMonth;
    lastCursor = calCursorDay;
    return;
  }

  // --- MONTH GRID VIEW ---
  int numRows = (firstWd + dim + 6) / 7;
  if (numRows < 5) numRows = 5;
  int rowH = (numRows >= 6) ? 27 : 31;
  int rowStep = rowH + 2;

  auto drawDayCell = [&](int dNum) {
    if (dNum < 1 || dNum > dim) return;
    int slot = firstWd + (dNum - 1);
    int col = slot % 7;
    int row = slot / 7;
    int x = 8 + col * 32;
    int y = 70 + row * rowStep;

    int evCnt = getCalDayEvents(ay, am, dNum, nullptr, 0);
    bool isCursor = (dNum == calCursorDay);
    bool isToday = (!calShowNextMonth && dNum == calTodayDay);

    uint16_t bg = isCursor ? COL_CYAN : (evCnt > 0 ? COL_CARD : COL_BG);
    uint16_t border = isCursor ? COL_WHITE : (isToday ? COL_GREEN : (evCnt > 0 ? COL_BORDER : COL_BG));
    uint16_t fg = isCursor ? COL_BG : (isToday ? COL_GREEN : (evCnt > 0 ? COL_WHITE : COL_MUTED));

    tft->fillRoundRect(x, y, 30, rowH, 4, bg);
    tft->drawRoundRect(x, y, 30, rowH, 4, border);
    if (isToday && !isCursor) {
      tft->drawRoundRect(x + 1, y + 1, 28, rowH - 2, 3, COL_GREEN);
    }

    char numBuf[4];
    snprintf(numBuf, sizeof(numBuf), "%d", dNum);
    int tw = vnTextWidth(vnFont12, numBuf);
    drawUtf8Text(x + max(1, (30 - tw) / 2), y + 7, String(numBuf), 1, fg, bg, 28);

    if (evCnt > 0) {
      int dots = min(evCnt, 3);
      uint16_t dotCol = isCursor ? COL_BG : COL_AMBER;
      int startDotX = x + 15 - (dots - 1) * 3;
      int dotY = y + rowH - 5;
      for (int k = 0; k < dots; k++) {
        tft->fillCircle(startDotX + k * 6, dotY, 2, dotCol);
      }
    }
  };

  if (fullRedraw) {
    String mTitle = String(tr("THÁNG ", "MONTH ")) + String(am) + "/" + String(ay);
    printCenteredText(mTitle, 32, 2, COL_CYAN, COL_BG);

    static const char* VI_WD_HDR[7] = {"CN", "T2", "T3", "T4", "T5", "T6", "T7"};
    static const char* EN_WD_HDR[7] = {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};
    for (int c = 0; c < 7; c++) {
      const char* wLbl = stationEnglish ? EN_WD_HDR[c] : VI_WD_HDR[c];
      int tw = vnTextWidth(vnFont12, wLbl);
      drawUtf8Text(8 + c * 32 + max(1, (30 - tw) / 2), 54, String(wLbl), 1, (c == 0) ? COL_AMBER : COL_MUTED, COL_BG, 28);
    }
    tft->drawFastHLine(8, 67, 224, COL_BORDER);

    for (int dNum = 1; dNum <= dim; dNum++) {
      drawDayCell(dNum);
    }

    printCenteredText(tr("<< / >>: Ngày   [PLAY]: Xem việc", "<< / >>: Day   [PLAY]: Open"), 282, 1, COL_MUTED, COL_BG);
  } else if (calCursorDay != lastCursor) {
    drawDayCell(lastCursor);
    drawDayCell(calCursorDay);
  }

  // Bottom selected-day quick preview bar (Y=246..274)
  tft->fillRoundRect(8, 246, 224, 28, 6, COL_CARD);
  tft->drawRoundRect(8, 246, 224, 28, 6, COL_CYAN);
  int idxs[6];
  int cnt = getCalDayEvents(ay, am, calCursorDay, idxs, 6);
  String previewStr = String(calCursorDay) + "/" + String(am) + " (" + String(cnt) + tr(" việc): ", "): ");
  if (!stationCalConnected && stationCalEventCount == 0) {
    previewStr = tr("Chưa liên kết Google Calendar", "Link Google Calendar in App");
  } else if (cnt == 0) {
    previewStr += tr("Trống lịch", "Free day");
  } else {
    auto& firstEv = stationCalEvents[idxs[0]];
    previewStr += translateCalLabel(firstEv.timeStr) + " " + firstEv.title;
  }
  drawUtf8Text(14, 255, previewStr, 1, COL_WHITE, COL_CARD, 212);

  lastDetail = calInDayDetail;
  lastNextM = calShowNextMonth;
  lastCursor = calCursorDay;
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
  String badgeText = tr("CHƯA KÍCH HOẠT", "NOT ACTIVE");
  uint16_t timeCol = COL_MUTED;
  uint16_t barCol = COL_MUTED;

  if (focusPhase == "EXPIRED_LOCKED") {
    cardBorderCol = 0xF800; // Red
    badgeBg = 0xF800;
    badgeFg = 0xFFFF;
    badgeText = tr("HẾT GIỜ - ĐÃ KHÓA", "TIME UP - LOCKED");
    timeCol = 0xF800;
    barCol = 0xF800;
  } else if (focusPhase == "BREAK") {
    cardBorderCol = COL_CYAN;
    badgeBg = COL_CYAN;
    badgeFg = COL_BG;
    badgeText = tr("GIỜ GIẢI LAO", "BREAK TIME");
    timeCol = COL_CYAN;
    barCol = COL_CYAN;
  } else if (focusEnabled && (focusPhase == "WORK" || focusPhase == "FOCUS")) {
    cardBorderCol = COL_AMBER;
    badgeBg = COL_AMBER;
    badgeFg = COL_BG;
    badgeText = (focusSessionType == "pomodoro") ? "POMODORO WORK" : tr("ĐANG TẬP TRUNG", "FOCUSING");
    timeCol = COL_WHITE;
    barCol = COL_GREEN;
  }

  // Draw static elements on first entry or state change
  if (firstEntry) {
    printCenteredText(tr("CHẾ ĐỘ TẬP TRUNG", "FOCUS MODE"), 34, 2, COL_AMBER, COL_BG);

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
      s1 = String(tr("Chu kỳ: ", "Cycle: ")) + String(focusCycleCount) + " (Pomodoro)";
    } else if (focusEnabled) {
      s1 = tr("Phiên tập trung cố định", "Fixed focus session");
    } else {
      s1 = tr("Chưa có phiên tập trung", "No focus session");
    }
    printCenteredText(s1, 168, 1, COL_WHITE, COL_CARD);

    String s2 = "";
    uint16_t s2Col = COL_MUTED;
    if (focusPhase == "EXPIRED_LOCKED") {
      s2 = tr("Đang khóa thiết bị & web!", "Devices & web locked!");
      s2Col = 0xF800; // Red
    } else if (focusActive) {
      s2 = tr("Đang chặn các web giải trí", "Blocking distracting sites");
      s2Col = COL_GREEN;
    } else if (focusEnabled && !focusActive) {
      s2 = tr("Tạm dừng chặn web (Nghỉ)", "Blocking paused (break)");
      s2Col = COL_CYAN;
    } else {
      s2 = tr("Web giải trí được phép", "Distracting sites allowed");
      s2Col = COL_MUTED;
    }
    printCenteredText(s2, 186, 1, s2Col, COL_CARD);

    String s3 = "";
    uint16_t s3Col = COL_MUTED;
    if (focusPhase == "EXPIRED_LOCKED") {
      s3 = tr("Giải toán trên PC/App để mở", "Solve on PC/app to unlock");
      s3Col = COL_AMBER;
    } else if (focusEnabled) {
      s3 = tr("Đồng bộ từ Python Backend", "Synced from Python backend");
      s3Col = COL_MUTED;
    } else {
      s3 = tr("Bật tập trung từ PC hoặc App", "Start focus from PC or app");
      s3Col = COL_MUTED;
    }
    printCenteredText(s3, 204, 1, s3Col, COL_CARD);
  }

  // 5. Backend Sync Freshness
  if (firstEntry || isStale != lastFocusStale) {
    tft->fillRect(20, 240, 200, 18, COL_CARD);
    if (isStale) {
      printCenteredText(tr("! Mất đồng bộ Backend !", "! Backend sync lost !"), 244, 1, 0xF800, COL_CARD);
    } else {
      printCenteredText(tr("* Đồng bộ Backend: Tốt *", "* Backend sync: OK *"), 244, 1, COL_MUTED, COL_CARD);
    }
    lastFocusStale = isStale;
  }

  lastFocusPhase = focusPhase;
  lastFocusSessionType = focusSessionType;
}

// Language changes must repaint every label, including an open menu.
void screenNeedsUpdateAfterLanguage() {
  lastRenderedView = "";
  screenNeedUpdate = true;
}

void drawCustomBootLogo() {
  if (!tft) return;
  tft->fillScreen(COL_BG);
  if (customBootBmp.length() >= 64) {
    // Draw 16x16 bitmap (64 hex chars = 256 bits) scaled 4x4 -> 64x64 centered at (88, 72)
    int scale = (customBootBmp.length() >= 256) ? 2 : 4;
    int dim = (customBootBmp.length() >= 256) ? 32 : 16;
    int ox = (240 - dim * scale) / 2;
    int oy = 66;
    for (int y = 0; y < dim; y++) {
      int rowHexIdx = y * (dim / 4);
      for (int bIdx = 0; bIdx < dim / 4; bIdx++) {
        char ch = customBootBmp[rowHexIdx + bIdx];
        int nib = (ch >= '0' && ch <= '9') ? (ch - '0') : (ch >= 'a' && ch <= 'f') ? (ch - 'a' + 10) : (ch >= 'A' && ch <= 'F') ? (ch - 'A' + 10) : 0;
        for (int bit = 0; bit < 4; bit++) {
          if (nib & (8 >> bit)) {
            int x = bIdx * 4 + bit;
            tft->fillRect(ox + x * scale, oy + y * scale, scale, scale, COL_WHITE);
          }
        }
      }
    }
  } else {
    // Default crisp vector DeskFlow ring mark
    tft->fillRoundRect(92, 68, 56, 56, 14, COL_WHITE);
    tft->drawCircle(120, 96, 17, COL_BG);
    tft->drawCircle(120, 96, 16, COL_BG);
    tft->drawLine(120, 96, 120, 84, COL_BG);
    tft->drawLine(120, 96, 129, 102, COL_BG);
  }
  printCenteredText(customBootTitle, 142, 2, COL_CYAN, COL_BG);
  printCenteredText(customBootSub, 172, 1, COL_MUTED, COL_BG);
}

// Arcade game implementation moved to arcade_games.h

void drawWakeupCheckView(bool viewChanged) {
  if (viewChanged) {
    tft->fillRect(0, 26, 240, 268, COL_BG);
    tft->fillRoundRect(16, 62, 208, 170, 12, COL_CARD);
    tft->drawRoundRect(16, 62, 208, 170, 12, COL_AMBER);
    printCenteredText(tr("BẠN CÒN THỨC CHỨ?", "ARE YOU AWAKE?"), 82, 2, COL_AMBER, COL_CARD);
    printCenteredText(tr("Bấm nút BẤT KỲ để xác nhận", "Press ANY button to confirm"), 130, 1, COL_WHITE, COL_CARD);
    printCenteredText(tr("Nếu không báo thức sẽ reo lại!", "Or alarm will ring again!"), 155, 1, COL_MUTED, COL_CARD);
  }
  long rem = (wakeupCheckDeadlineMs > millis()) ? (long)((wakeupCheckDeadlineMs - millis()) / 1000) : 0;
  tft->fillRect(70, 185, 100, 30, COL_CARD);
  printCenteredText(String(rem) + "s", 190, 2, COL_CYAN, COL_CARD);
}

void performOtaUpdate(const String& url, const String& ver) {
  noTone(BUZZER_PIN);
  isBuzzing = false;
  if (!url.length() || WiFi.status() != WL_CONNECTED) return;
  addLog("OTA", "Starting OTA -> " + ver + " (" + url + ")");
  if (mqtt.connected()) {
    mqtt.publish("deskflow/status_update", "{\"action\":\"ota_progress\",\"status\":\"downloading\"}");
  }
  if (tft) {
    tft->fillScreen(COL_BG);
    printCenteredText("DESKFLOW OTA", 90, 2, COL_CYAN, COL_BG);
    printCenteredText("Updating v" + ver + "...", 135, 1, COL_WHITE, COL_BG);
    printCenteredText("Do NOT unplug power!", 165, 1, COL_AMBER, COL_BG);
  }
  WiFiClientSecure secClient;
  secClient.setInsecure();
  httpUpdate.setFollowRedirects(HTTPC_STRICT_FOLLOW_REDIRECTS);
  t_httpUpdate_return ret = httpUpdate.update(secClient, url);
  if (ret == HTTP_UPDATE_FAILED) {
    String err = httpUpdate.getLastErrorString();
    addLog("OTA", "Failed: " + err);
    if (mqtt.connected()) {
      String p = "{\"action\":\"ota_progress\",\"status\":\"failed:" + err + "\"}";
      mqtt.publish("deskflow/status_update", p.c_str());
    }
    if (tft) {
      tft->fillScreen(COL_BG);
      printCenteredText("OTA FAILED", 120, 2, 0xF800, COL_BG);
      printCenteredText(err.substring(0, 34), 155, 1, COL_WHITE, COL_BG);
      delay(2500);
      lastRenderedView = "";
      screenNeedUpdate = true;
    }
  }
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

  // (#67) Live bottom notification ticker
  static String lastFooterMsg = "";
  String footerNow = (notifTickerText.length() > 0 && millis() < notifTickerUntilMs) ? notifTickerText : "DeskFlow Station OS";
  if (viewChanged || footerNow != lastFooterMsg) {
    lastFooterMsg = footerNow;
    tft->fillRect(6, 299, 228, 19, COL_BG);
    if (footerNow != "DeskFlow Station OS") {
      drawUtf8Text(10, 303, String("! ") + footerNow, 1, COL_AMBER, COL_BG, 220);
    } else {
      printCenteredText("DeskFlow Station OS", 304, 1, COL_MUTED, COL_BG);
    }
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
    tft->fillRect(12, 3, 14, 16, COL_BG);
    if (srvStats.internet) {
      drawUtf8Text(14, 8, "v", 1, COL_GREEN, COL_BG);
    } else {
      drawUtf8Text(14, 8, "x", 1, 0xF800, COL_BG);
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
    tft->fillRect(26, 3, 52, 16, COL_BG);
    drawUtf8Text(28, 8, wCompact, 1, COL_CYAN, COL_BG, 50);
    lastHeaderWeather = wCompact;
  }

  String curTimeStr = getFormattedRealTime(true);
  if (viewChanged || curTimeStr != lastHeaderTime) {
    tft->fillRect(79, 3, 56, 16, COL_BG);
    drawUtf8Text(82, 8, curTimeStr, 1, COL_MUTED, COL_BG);
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
    tft->fillRect(136, 3, 50, 16, COL_BG);
    if (curFocusIndicator.length() > 0) {
      uint16_t fCol = COL_AMBER;
      if (focusPhase == "BREAK") fCol = COL_GREEN;
      else if (focusPhase == "EXPIRED_LOCKED") fCol = 0xF800; // Red
      drawUtf8Text(138, 8, curFocusIndicator, 1, fCol, COL_BG, 48);
    }
    lastHeaderFocus = curFocusIndicator;
  }

  int curBatPct = calculateBatteryPct(readBatteryVoltage());
  if (viewChanged || curBatPct != lastHeaderBat) {
    tft->fillRect(188, 3, 44, 16, COL_BG);
    drawUtf8Text(188, 8, "Z", 1, COL_AMBER, COL_BG);
    char batBuf[8];
    snprintf(batBuf, sizeof(batBuf), "%d%%", curBatPct);
    drawUtf8Text(199, 8, String(batBuf), 1, COL_MUTED, COL_BG);
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

    String wStr = srvStats.weatherCity + " . " + srvStats.weatherText + " . AQI " + String(srvStats.weatherAqi) + " UV " + String(srvStats.weatherUv, 0);
    if (viewChanged || wStr != lastMainWeather) {
      tft->fillRect(0, 118, 240, 18, COL_BG);
      printCenteredText(wStr, 120, 1, COL_MUTED, COL_BG);
      lastMainWeather = wStr;
    }

    if (viewChanged || srvStats.currentTrack != lastMainTrack || srvStats.currentArtist != lastMainArtist) {
      tft->fillRoundRect(15, 142, 210, 48, 8, COL_CARD);
      tft->drawRoundRect(15, 142, 210, 48, 8, COL_BORDER);

      drawUtf8Text(24, 154, "SP", 1, COL_GREEN, COL_CARD);

      drawUtf8Text(46, 154, srvStats.currentTrack, 1, COL_WHITE, COL_CARD, 170);

      if (srvStats.currentArtist.length() > 0 && srvStats.currentArtist != "N/A") {
        drawUtf8Text(46, 168, srvStats.currentArtist, 1, COL_MUTED, COL_CARD, 170);
      } else {
        drawUtf8Text(46, 168, "Spotify Desktop", 1, COL_MUTED, COL_CARD, 170);
      }

      lastMainTrack = srvStats.currentTrack;
      lastMainArtist = srvStats.currentArtist;
    }

    static String lastMainNote = "";
    static String lastMainCd = "";

    if (viewChanged || srvStats.quoteText != lastMainQuote || srvStats.stickyNote != lastMainNote || (srvStats.countdownName + String(srvStats.countdownDays)) != lastMainCd) {
      tft->fillRect(10, 196, 220, 92, COL_BG);

      if (srvStats.hasCountdown && srvStats.countdownName.length() > 0) {
        String cdTxt = srvStats.countdownName + ": " + String(srvStats.countdownDays) + tr(" ngày", " days");
        printCenteredText(cdTxt, 198, 1, COL_AMBER, COL_BG);

        if (srvStats.stickyNote.length() > 0) {
          printCenteredText(tr("~ GHI CHÚ GHIM ~", "~ PINNED NOTE ~"), 212, 1, COL_CYAN, COL_BG);
          printBigWrappedTopCenteredText(srvStats.stickyNote, 226, 1, COL_WHITE, 28, 13, COL_BG);
        } else {
          printCenteredText(tr("~ DAILY MOTIVATION ~", "~ DAILY MOTIVATION ~"), 212, 1, COL_MUTED, COL_BG);
          printBigWrappedTopCenteredText(srvStats.quoteText, 226, 1, COL_WHITE, 28, 13, COL_BG);
        }
      } else {
        if (srvStats.stickyNote.length() > 0) {
          printCenteredText(tr("~ GHI CHÚ GHIM ~", "~ PINNED NOTE ~"), 202, 1, COL_CYAN, COL_BG);
          printBigWrappedTopCenteredText(srvStats.stickyNote, 218, 1, COL_WHITE, 28, 13, COL_BG);
        } else {
          printCenteredText(tr("~ DAILY MOTIVATION ~", "~ DAILY MOTIVATION ~"), 202, 1, COL_AMBER, COL_BG);
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
      printCenteredText(tr("THÔNG SỐ MÁY", "HARDWARE STATS"), 38, 2, COL_PURPLE, COL_BG);
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
      tft->fillRect(16, 65, 208, 16, COL_BG);
      const char* lpLbl = tr("Laptop: ", "Laptop: ");
      drawUtf8Text(16, 70, lpLbl, 1, COL_WHITE, COL_BG);
      char lpBuf[48];
      snprintf(lpBuf, sizeof(lpBuf), "%d%% | %.1f%% | %.1f%%", srvStats.lp_bat, srvStats.lp_ram, srvStats.lp_cpu);
      drawUtf8Text(16 + vnTextWidth(vnFont12, lpLbl), 70, String(lpBuf), 1, COL_CYAN, COL_BG);
      drawProgressBarSmooth(16, 84, 208, 8, constrain((int)srvStats.lp_cpu, 0, 100), COL_CYAN);
      lastLpCpu = srvStats.lp_cpu;
      lastLpRam = srvStats.lp_ram;
      lastLpBat = srvStats.lp_bat;
    }

    // Dynamic Server stats - live 1-second refresh
    if (viewChanged || srvStats.cpu != lastSrvCpu || srvStats.ram_pct != lastSrvRam || srvStats.uptime != lastSrvUptime) {
      tft->fillRect(16, 105, 208, 16, COL_BG);
      const char* srvLbl = tr("Server: ", "Server: ");
      drawUtf8Text(16, 110, srvLbl, 1, COL_WHITE, COL_BG);
      char srvBuf[48];
      snprintf(srvBuf, sizeof(srvBuf), "%s | %.1f%% | %.1f%%", srvStats.uptime.c_str(), srvStats.ram_pct, srvStats.cpu);
      drawUtf8Text(16 + vnTextWidth(vnFont12, srvLbl), 110, String(srvBuf), 1, COL_GREEN, COL_BG, 208 - vnTextWidth(vnFont12, srvLbl));
      drawProgressBarSmooth(16, 124, 208, 8, constrain((int)srvStats.cpu, 0, 100), COL_GREEN);
      lastSrvCpu = srvStats.cpu;
      lastSrvRam = srvStats.ram_pct;
      lastSrvUptime = srvStats.uptime;
    }

    // Dynamic ESP32 Battery voltage and pct
    float vEsp = readBatteryVoltage();
    int pctEsp = calculateBatteryPct(vEsp);
    if (viewChanged || pctEsp != lastEspBatPct) {
      tft->fillRect(16, 163, 208, 16, COL_BG);
      const char* espLbl = tr("ESP32 Pin: ", "ESP32 batt: ");
      drawUtf8Text(16, 168, espLbl, 1, COL_WHITE, COL_BG);
      char espBuf[32];
      snprintf(espBuf, sizeof(espBuf), "%.2fV (%d%%)", vEsp, pctEsp);
      drawUtf8Text(16 + vnTextWidth(vnFont12, espLbl), 168, String(espBuf), 1, COL_AMBER, COL_BG);
      drawProgressBarSmooth(16, 184, 208, 8, constrain(pctEsp, 0, 100), COL_AMBER);
      lastEspBatPct = pctEsp;
    }
  }
  // 4. VIEW TKB
  else if (srvStats.activeView == "tkb") {
    static String lastLiveSig = "";
    String liveSig = srvStats.liveLabel + "|" + srvStats.liveSubject + "|" + String(srvStats.liveMinsLeft) + "|" + srvStats.dismissalTime + "|" + String(srvStats.minsToDismissal);
    if (viewChanged || liveSig != lastLiveSig) {
      lastLiveSig = liveSig;
      if (viewChanged) {
        printCenteredText(tr("THỜI KHÓA BIỂU", "TIMETABLE"), 30, 2, COL_AMBER, COL_BG);
      }

      // (#1) Live period & dismissal countdown pill
      tft->fillRoundRect(10, 52, 220, 24, 5, COL_CARD);
      tft->drawRoundRect(10, 52, 220, 24, 5, COL_CYAN);
      String liveBar = srvStats.liveLabel.length() > 0
        ? (srvStats.liveLabel + (srvStats.liveSubject.length() > 0 ? (": " + srvStats.liveSubject) : "") + " (" + String(srvStats.liveMinsLeft) + "p)")
        : tr("Hôm nay", "Today");
      if (srvStats.dismissalTime.length() > 0 && srvStats.minsToDismissal > 0) {
        liveBar += " • Về " + srvStats.dismissalTime;
      }
      drawUtf8Text(16, 58, liveBar, 1, COL_CYAN, COL_CARD, 208);

      auto drawTkbRow = [&](int& y, const char* label, uint16_t labelCol, const String& value, uint16_t valCol) {
        if (y > 278) return;
        String lbl(label);
        drawUtf8Text(14, y, lbl, 1, labelCol, COL_BG, 0);
        int lblW = vnTextWidth(vnFont12, lbl.c_str());
        int valX = 14 + lblW;
        int firstMaxW = max(60, 228 - valX);

        String v = value;
        v.trim();
        if (v.length() == 0) {
          y += 16;
          return;
        }
        auto strWidth = [&](const String& s) -> int {
          return vnTextWidth(vnFont12, s.c_str());
        };
        if (strWidth(v) <= firstMaxW) {
          drawUtf8Text(valX, y, v, 1, valCol, COL_BG, 0);
          y += 16;
          return;
        }
        int splitPos = -1;
        for (size_t i = 0; i < v.length(); i++) {
          if (v[i] == ' ' && strWidth(v.substring(0, i)) <= firstMaxW) {
            splitPos = (int)i;
          }
        }
        if (splitPos <= 0) {
          drawUtf8Text(valX, y, v, 1, valCol, COL_BG, firstMaxW);
          y += 16;
        } else {
          String line1 = v.substring(0, splitPos);
          String line2 = v.substring(splitPos + 1);
          line2.trim();
          drawUtf8Text(valX, y, line1, 1, valCol, COL_BG, 0);
          y += 15;
          if (y <= 280 && line2.length() > 0) {
            drawUtf8Text(24, y, line2, 1, valCol, COL_BG, 204);
            y += 16;
          }
        }
      };

      if (viewChanged) {
        int curY = 82;
        drawUtf8Text(12, curY, srvStats.tkbDay + ":", 1, COL_CYAN, COL_BG, 216);
        curY += 16;
        drawTkbRow(curY, tr("* Sáng: ", "* AM: "), COL_AMBER, srvStats.tkbMorning, COL_WHITE);
        if (srvStats.tkbHasAfternoon && srvStats.tkbAfternoon.length() > 0) {
          drawTkbRow(curY, tr("* Chiều: ", "* PM: "), COL_AMBER, srvStats.tkbAfternoon, COL_WHITE);
        }
        drawTkbRow(curY, tr("* Đ.phục: ", "* Uniform: "), COL_GREEN, srvStats.tkbUniform, COL_CYAN);

        curY += 4;
        tft->drawFastHLine(12, curY, 216, COL_BORDER);
        curY += 8;

        drawUtf8Text(12, curY, srvStats.tkbTomDay + ":", 1, COL_CYAN, COL_BG, 216);
        curY += 16;
        drawTkbRow(curY, tr("* Sáng: ", "* AM: "), COL_AMBER, srvStats.tkbTomMorning, COL_WHITE);
        if (srvStats.tkbTomHasAfternoon && srvStats.tkbTomAfternoon.length() > 0) {
          drawTkbRow(curY, tr("* Chiều: ", "* PM: "), COL_AMBER, srvStats.tkbTomAfternoon, COL_WHITE);
        }
        drawTkbRow(curY, tr("* Đ.phục: ", "* Uniform: "), COL_GREEN, srvStats.tkbTomUniform, COL_CYAN);
      }
    }
  }
  // 5. VIEW WEATHER (#69 AQI & UV)
  else if (srvStats.activeView == "weather") {
    static String lastWText = "";
    static int lastWAqi = -1;
    if (viewChanged || srvStats.weatherText != lastWText || srvStats.weatherAqi != lastWAqi) {
      lastWText = srvStats.weatherText;
      lastWAqi = srvStats.weatherAqi;
      tft->fillRect(0, 26, 240, 268, COL_BG);
      drawWeatherWebIcon(120, 82);
      String wTxt = srvStats.weatherText;
      wTxt.replace("*", "°");
      printVietnameseCentered(wTxt, 134, COL_AMBER, vnFont17);
      printCenteredText(srvStats.weatherCity, 168, 1, COL_MUTED, COL_BG);

      // Left Card: AQI (centered inside x=16..116)
      tft->fillRoundRect(16, 192, 100, 56, 8, COL_CARD);
      tft->drawRoundRect(16, 192, 100, 56, 8, COL_CYAN);
      drawUtf8Text(26, 202, "AQI: " + String(srvStats.weatherAqi), 1, COL_CYAN, COL_CARD, 84);
      drawUtf8Text(24, 224, srvStats.weatherAqiLabel.length() ? srvStats.weatherAqiLabel : tr("Tốt", "Good"), 1, COL_WHITE, COL_CARD, 86);

      // Right Card: UV (centered inside x=124..224)
      tft->fillRoundRect(124, 192, 100, 56, 8, COL_CARD);
      tft->drawRoundRect(124, 192, 100, 56, 8, COL_AMBER);
      drawUtf8Text(134, 202, "UV: " + String(srvStats.weatherUv, 1), 1, COL_AMBER, COL_CARD, 84);
      drawUtf8Text(134, 224, srvStats.weatherUvLabel.length() ? srvStats.weatherUvLabel : tr("Thấp", "Low"), 1, COL_WHITE, COL_CARD, 84);

      // (#24) AQI Advice banner
      String advice = (srvStats.weatherAqi >= 4)
        ? tr("Không khí kém — Nên đeo khẩu trang", "Poor air — Wear a mask")
        : tr("Không khí ổn — Thoáng mát", "Air quality OK");
      printCenteredText(advice, 266, 1, (srvStats.weatherAqi >= 4) ? COL_AMBER : COL_GREEN, COL_BG);
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
      tft->fillRect(0, 26, 240, 22, COL_BG);
      printVietnameseCentered(srvStats.currentTrack, 32, COL_MUTED);
      lastTrack = srvStats.currentTrack;
    }

    String lyricTxt = srvStats.lyricCurr.length() > 0 ? srvStats.lyricCurr : tr("Chưa phát nhạc", "No music playing");
    if (lyricTxt != lastLyric) {
      tft->fillRect(0, 48, 240, 142, COL_BG);
      printVietnameseWrappedCentered(lyricTxt, 115, COL_AMBER, 224, 20);
      lastLyric = lyricTxt;
    }

    if (srvStats.lyricNext != lastNext) {
      tft->fillRect(0, 190, 240, 36, COL_BG);
      if (srvStats.lyricNext.length() > 0) {
        printVietnameseCentered(srvStats.lyricNext, 202, COL_MUTED);
      } else {
        printCenteredText("~ ~ ~", 202, 1, COL_MUTED, COL_BG);
      }
      lastNext = srvStats.lyricNext;
    }

    // Music progress bar: positioned ~30 pixels ABOVE the bottom separator line.
    // Bottom separator is at Y = 296, bar height is 8, so Y = 296 - 30 - 8 = 258.
    const int separatorY = 296;
    const int musicBarH = 8;
    const int musicBarY = separatorY - 30 - musicBarH; // 258
    if (srvStats.progressPct != lastPct) {
      drawProgressBarSmooth(25, musicBarY, 190, musicBarH, srvStats.progressPct, COL_GREEN);
      lastPct = srvStats.progressPct;
    }
  }
  // 7. VIEW MATH ALARM
  else if (srvStats.activeView == "math_alarm") {
    if (viewChanged) {
      printCenteredText("! MATH ALARM !", 40, 2, 0xF800, COL_BG);
      printCenteredText(tr("Giải toán để tắt chuông:", "Solve the math to silence:"), 80, 1, COL_WHITE, COL_BG);
      printCenteredText(currentMathQuestion, 125, 3, COL_AMBER, COL_BG);
      printCenteredText(tr("Dùng lệnh /solve trên Discord", "Use /solve on Discord"), 185, 1, COL_CYAN, COL_BG);
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
  // 12. VIEW GOOGLE CALENDAR (DEDICATED)
  else if (srvStats.activeView == "calendar") {
    drawCalendarView(viewChanged);
  }
  // 13. VIEW ARCADE GAME HUB
  else if (srvStats.activeView == "game") {
    drawGameView(viewChanged);
  }
  // 14. VIEW MATRIX RAIN (#51)
  else if (srvStats.activeView == "matrix") {
    drawMatrixRainView(viewChanged);
  }
  // 15. VIEW SPOTIFY VINYL (#55)
  else if (srvStats.activeView == "vinyl") {
    drawSpotifyVinylView(viewChanged);
  }
  // 16. VIEW GITHUB CONTRIBUTIONS & CI (#57)
  else if (srvStats.activeView == "github") {
    drawGitHubView(viewChanged);
  }
  // 17. VIEW FORMULAS & PERIODIC TABLE (#11)
  else if (srvStats.activeView == "formulas") {
    drawFormulasView(viewChanged);
  }
  else if (srvStats.activeView == "analog") {
    if (viewChanged) playSlideTransition();
    drawAnalogClockView();
  }
  else if (srvStats.activeView == "lunar") {
    if (viewChanged) playSlideTransition();
    drawLunarYearView();
  }
  else if (srvStats.activeView == "stopwatch") {
    if (viewChanged) playSlideTransition();
    drawStopwatchView();
  }
  // 18. VIEW WAKEUP VERIFICATION (ANTI-OVERSLEEP)
  else if (srvStats.activeView == "wakeup_check") {
    drawWakeupCheckView(viewChanged);
  }
  drawIncomingCallOverlay();
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

  DynamicJsonDocument doc(32768);
  if (deserializeJson(doc, payload, length)) {
    addLog("MQTT_ERR", "deserializeJson failed (length=" + String(length) + ")");
    return;
  }

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
    if (doc.containsKey("aqi")) srvStats.weatherAqi = doc["aqi"] | 2;
    if (doc.containsKey("aqi_label")) srvStats.weatherAqiLabel = doc["aqi_label"].as<String>();
    if (doc.containsKey("uv")) srvStats.weatherUv = doc["uv"] | 4.0f;
    if (doc.containsKey("uv_label")) srvStats.weatherUvLabel = doc["uv_label"].as<String>();
    lastRenderedView = "";
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
    screenNeedsUpdateAfterLanguage();
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
      screenNeedsUpdateAfterLanguage();
    }
    else if (topicStr == "deskflow/display" && action == "station_language") {
      String lang = doc["language"] | "";
      lang.trim();
      lang.toLowerCase();
      if (lang == "vi" || lang == "en") {
        applyStationLanguage(lang == "en", false);
        addLog("I18N", String("Station language from server: ") + (stationEnglish ? "EN" : "VI"));
      }
    }
    else if (topicStr == "deskflow/display" && action == "switch_view") {
      if (doc.containsKey("view")) srvStats.activeView = doc["view"].as<String>();
      if (doc.containsKey("math_q")) currentMathQuestion = doc["math_q"].as<String>();
      screenNeedUpdate = true;
      publishDeviceStatus();
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
          updateLampLed();
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
    else if (topicStr == "deskflow/display" && action == "sync_calendar") {
      stationCalConnected = doc["connected"] | false;
      if (doc.containsKey("year")) calCurYear = doc["year"] | 2026;
      if (doc.containsKey("month")) calCurMonth = doc["month"] | 11;
      if (doc.containsKey("today")) calTodayDay = doc["today"] | 1;
      if (doc.containsKey("first_wd")) calCurFirstWd = doc["first_wd"] | 0;
      if (doc.containsKey("days_in_month")) calCurDaysInMonth = doc["days_in_month"] | 30;
      if (doc.containsKey("next_year")) calNextYear = doc["next_year"] | calCurYear;
      if (doc.containsKey("next_month")) calNextMonth = doc["next_month"] | ((calCurMonth % 12) + 1);
      if (doc.containsKey("next_first_wd")) calNextFirstWd = doc["next_first_wd"] | 0;
      if (doc.containsKey("next_days_in_month")) calNextDaysInMonth = doc["next_days_in_month"] | 31;

      stationCalEventCount = 0;
      if (doc.containsKey("events") && doc["events"].is<JsonArray>()) {
        JsonArray arr = doc["events"].as<JsonArray>();
        for (JsonObject ev : arr) {
          if (stationCalEventCount >= MAX_STATION_CAL_EVENTS) break;
          stationCalEvents[stationCalEventCount].year = ev["y"] | calCurYear;
          stationCalEvents[stationCalEventCount].month = ev["m"] | calCurMonth;
          stationCalEvents[stationCalEventCount].day = ev["d"] | 1;
          stationCalEvents[stationCalEventCount].timeStr = ev["t"].as<String>();
          stationCalEvents[stationCalEventCount].title = ev["s"].as<String>();
          stationCalEvents[stationCalEventCount].location = ev["l"].as<String>();
          stationCalEventCount++;
        }
      }
      if (calCursorDay <= 0) {
        calCursorDay = calTodayDay;
        calShowNextMonth = false;
      }
      calForceRedraw = true;
      if (srvStats.activeView == "calendar") screenNeedUpdate = true;
      addLog("CAL", "Synced " + String(stationCalEventCount) + " events for Month " + String(calCurMonth));
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
      if (act == "on") {
        ledBlinkMode = false;
        ledState = true;
        prefs.putBool("led_state", true);
        updateLampLed();
        addLog("LED", "LED ON");
      } else if (act == "off") {
        ledBlinkMode = false;
        ledState = false;
        prefs.putBool("led_state", false);
        updateLampLed();
        addLog("LED", "LED OFF");
      } else if (act == "toggle") {
        ledBlinkMode = false;
        ledState = !ledState;
        prefs.putBool("led_state", ledState);
        updateLampLed();
        addLog("LED", "Toggle LED: " + String(ledState ? "ON" : "OFF"));
      } else if (act == "blink") {
        ledBlinkMode = !ledBlinkMode;
        if (!ledBlinkMode) {
          ledState = false;
          prefs.putBool("led_state", false);
          updateLampLed();
        }
        addLog("LED", "Blink Mode: " + String(ledBlinkMode ? "ON" : "OFF"));
      }
    }
    else if (topicStr == "deskflow/display" && action == "led_color") {
      String hexCol = doc["hex"] | "#FFFFFF";
      srvStats.lampColor = hexCol;
      prefs.putString("lamp_col", hexCol);
      ledRgbMode = false;
      bool wantGrad = doc["gradient"] | false;
      ledGradientMode = wantGrad;
      prefs.putBool("led_rgb", false);
      prefs.putBool("led_grad", wantGrad);
      if (doc.containsKey("hex2")) {
        srvStats.lampColor2 = doc["hex2"].as<String>();
        prefs.putString("lamp_col2", srvStats.lampColor2);
      }
      for (int i = 0; i < LED_PRESET_COUNT; i++) {
        if (hexCol.equalsIgnoreCase(LED_PRESETS[i])) { ledPresetIndex = i; break; }
      }
      ledState = true;
      ledBlinkMode = false;
      prefs.putBool("led_state", true);
      updateLampLed();
      screenNeedUpdate = true;
      addLog("LED", "Lamp Color set to: " + hexCol);
    }
    else if (topicStr == "deskflow/display" && action == "led_off") {
      ledState = false;
      ledBlinkMode = false;
      prefs.putBool("led_state", false);
      updateLampLed();
      screenNeedUpdate = true;
      addLog("LED", "Lamp turned off");
    }
    else if ((topicStr == "deskflow/display" || topicStr == "deskflow/hardware") && action == "led_effect") {
      String eff = doc["effect"] | "solid";
      eff.toLowerCase();
      if (eff == "rainbow") {
        ledEffect = "solid";
        ledRgbMode = true;
        ledGradientMode = false;
      } else {
        ledEffect = eff;
        if (doc.containsKey("rgb")) {
          ledRgbMode = doc["rgb"].as<bool>();
        }
        if (doc.containsKey("gradient")) {
          ledGradientMode = doc["gradient"].as<bool>();
        }
        if (ledRgbMode) {
          ledGradientMode = false;
        }
        if (doc.containsKey("hex2")) {
          srvStats.lampColor2 = doc["hex2"].as<String>();
          prefs.putString("lamp_col2", srvStats.lampColor2);
        }
      }
      prefs.putString("led_eff", ledEffect);
      prefs.putBool("led_rgb", ledRgbMode);
      prefs.putBool("led_grad", ledGradientMode);
      if (doc.containsKey("speed")) {
        ledSpeed = constrain((int)doc["speed"], 5, 100);
        prefs.putInt("led_spd", ledSpeed);
      }
      ledState = true;
      ledBlinkMode = false;
      prefs.putBool("led_state", true);
      updateLampLed();
      addLog("LED", "Strip effect -> " + ledEffect + (ledRgbMode ? " +RGB" : (ledGradientMode ? " +GRAD" : "")));
    }
    else if (topicStr == "deskflow/display" && action == "boot_logo") {
      if (doc.containsKey("title")) {
        customBootTitle = doc["title"].as<String>();
        prefs.putString("boot_title", customBootTitle);
      }
      if (doc.containsKey("sub")) {
        customBootSub = doc["sub"].as<String>();
        prefs.putString("boot_sub", customBootSub);
      }
      if (doc.containsKey("bitmap")) {
        customBootBmp = doc["bitmap"].as<String>();
        prefs.putString("boot_bmp", customBootBmp);
      }
      addLog("BOOT", "Custom boot logo updated in NVS");
    }
    else if (topicStr == "deskflow/display" && action == "game_mode") {
      int m = doc["mode"] | 0;
      activeMiniGame = constrain(m, 0, ARCADE_GAME_COUNT - 1);
      inGameLobby = false;
      srvStats.activeView = "game";
      lastRenderedView = "";
      screenNeedUpdate = true;
      resetActiveGame();
      drawGameHeaderBanner();
    }
    else if (topicStr == "deskflow/display" && action == "github_stats") {
      ghUsername = doc["username"] | "HenryYT13";
      ghRepos = doc["repos"] | 0;
      ghFollowers = doc["followers"] | 0;
      ghLatestRepo = doc["latest_repo"] | "deskflow-app";
      ghCiStatus = doc["ci_status"] | "PASS";
      ghDirty = true;
      screenNeedUpdate = true;
    }
    else if (topicStr == "deskflow/display" && action == "notif_ticker") {
      notifTickerText = doc["text"] | "";
      notifTickerUntilMs = millis() + 25000UL;
      screenNeedUpdate = true;
    }
    else if (topicStr == "deskflow/display" && action == "webhook_flash") {
      webhookFlashHex = doc["color"] | "#22C55E";
      webhookFlashUntilMs = millis() + 2600UL;
      String sum = doc["summary"] | "";
      if (sum.length() > 0) {
        notifTickerText = sum;
        notifTickerUntilMs = millis() + 25000UL;
      }
      if (buzzerEnabled) triggerBuzz(65, false);
    }
    else if (topicStr == "deskflow/display" && action == "take_screenshot") {
      pendingScreenshotCapture = true;
    }
    else if (topicStr == "deskflow/display" && action == "ota_update") {
      pendingOtaUrl = doc["url"] | "";
      pendingOtaVersion = doc["version"] | DESKFLOW_FW_VERSION;
      addLog("OTA", "Queued OTA v" + pendingOtaVersion);
    }
    else if (topicStr == "deskflow/display" && action == "pair_code_result") {
      pairCodeValue = doc["code"] | "";
      pairQrSize = doc["qr_size"] | 0;
      pairQrBits = doc["qr_bits"] | "";
      pairCodeLoading = false;
      pairCodeFetchedAt = millis();
      if (inSettingsMenu && menuState == MENU_PAIR_DEVICE) {
        screenNeedUpdate = true;
        triggerBuzz(45);
      }
      addLog("PAIR", "Received Full Access pair code + QR (" + String(pairQrSize) + "x" + String(pairQrSize) + ")");
    }
    else if ((topicStr == "deskflow/display" || topicStr == "deskflow/hardware") && (action == "dim" || action == "lamp_dim")) {
      int b = -1;
      if (doc.containsKey("led_bright")) b = doc["led_bright"].as<int>();
      else if (doc.containsKey("led_brightness")) b = doc["led_brightness"].as<int>();
      else if (doc.containsKey("led")) b = doc["led"].as<int>();
      if (b >= 0) {
        ledBrightness = constrain(b, 0, 100);
        prefs.putInt("led_bright", ledBrightness);
        if (ledBrightness > 0) {
          ledState = true;
          prefs.putBool("led_state", true);
        } else {
          ledState = false;
          prefs.putBool("led_state", false);
        }
        updateLampLed();
        screenNeedUpdate = true;
        addLog("LED", "Set brightness: " + String(ledBrightness) + "%");
      }
    }

    if (action == "spotify_volume") {
      // Authoritative volume from the backend (set from any app or the station menu).
      if (doc.containsKey("volume")) {
        spotifyVolume = constrain((int)doc["volume"], 0, 100);
        spotifyVolumeKnown = true;
        if (inSettingsMenu && menuState == MENU_VOLUME) screenNeedUpdate = true;
        addLog("SPOTIFY", "Volume -> " + String(spotifyVolume) + "%");
      }
      return;
    }

    if (action == "spotify_update") {
      if (doc.containsKey("volume") && !doc["volume"].isNull()) {
        spotifyVolume = constrain((int)doc["volume"], 0, 100);
        spotifyVolumeKnown = true;
      }
      srvStats.currentTrack = doc["track"] | "Unknown Track";
      srvStats.currentArtist = doc["artist"] | "N/A";
      srvStats.progressPct = doc["progress_pct"] | 0;
      srvStats.lyricCurr = doc["lyric_curr"] | "";
      srvStats.lyricNext = doc["lyric_next"] | "";
      
      if (!inSettingsMenu && !inViewMenu && srvStats.activeView == "lyrics") {
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
    if (doc.containsKey("live_label")) srvStats.liveLabel = doc["live_label"].as<String>();
    if (doc.containsKey("live_subject")) srvStats.liveSubject = doc["live_subject"].as<String>();
    if (doc.containsKey("live_mins_left")) srvStats.liveMinsLeft = doc["live_mins_left"] | 0;
    if (doc.containsKey("dismissal_time")) srvStats.dismissalTime = doc["dismissal_time"].as<String>();
    if (doc.containsKey("mins_to_dismissal")) srvStats.minsToDismissal = doc["mins_to_dismissal"] | 0;

    screenNeedUpdate = true;
  } 
  else if (topicStr == "deskflow/alarm") {
    String action = doc["action"] | "";
    if (action == "stop_alarm") {
      isAlarmActive = false;
      isBuzzing = false;
      stopMorsePlayback();
      melodyStop();
      alarmTuneStop();
      stopBuzzer();
      updateLampLed();
      addLog("ALARM", ">>> DA GIAI DUNG! TAT CHUONG.");
      srvStats.activeView = "tkb";
      screenNeedUpdate = true;
    } else if (action == "math_alarm_trigger") {
      stopMorsePlayback();
      alarmTuneStop();
      isAlarmActive = true;
      lastAlarmBeepTime = millis();
      lastAlarmStrobeTime = 0;
      alarmStrobeOn = false;
      if (doc.containsKey("math_q")) {
        currentMathQuestion = doc["math_q"].as<String>();
      }
      srvStats.activeView = "math_alarm";
      screenNeedUpdate = true;
      addLog("ALARM", ">>> MATH ALARM START: KEU LIEN TUC CHO TOI KHI GIAI!");
    } else if (action == "math_alarm_wakeup_check") {
      isWakeupCheckActive = true;
      int toSec = doc["timeout_sec"] | 45;
      wakeupCheckDeadlineMs = millis() + (unsigned long)toSec * 1000UL;
      srvStats.activeView = "wakeup_check";
      lastRenderedView = "";
      screenNeedUpdate = true;
      triggerBuzz(120, true);
      addLog("ALARM", "Wakeup anti-oversleep check triggered (" + String(toSec) + "s)");
    } else if (action == "wakeup_confirmed") {
      isWakeupCheckActive = false;
      if (srvStats.activeView == "wakeup_check") {
        srvStats.activeView = "main";
        lastRenderedView = "";
        screenNeedUpdate = true;
      }
    } else if (action == "morse_play") {
      String pattern = doc["pattern"] | "";
      unsigned int unitMs = doc["unit_ms"] | 60;
      unsigned int freq = doc["freq"] | 1000;
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
      if (pendingLocalLangSync) {
        sendClockStyleAck("applied", "");
        publishStationLanguageUpdate();
      }
      sendClockStyleRequest();
      sendHardwareSettingsRequest();
      publishDeviceStatus();
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

  DynamicJsonDocument doc(320);
  doc["battery_pct"] = pct;
  doc["voltage"] = serialized(String(v, 2));
  doc["wifi_rssi"] = WiFi.RSSI();
  doc["fw_version"] = DESKFLOW_FW_VERSION;
  // Report the screen currently shown so every client can stay in sync.
  doc["active_view"] = srvStats.activeView;
  doc["language"] = stationEnglish ? "en" : "vi";

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
        <div style="display:flex; align-items:center; gap:8px;">
          <button class="term-btn" onclick="toggleLanguage()" id="btnLang">VI / EN</button>
          <div class="badge" id="hudConn">CONNECTED</div>
        </div>
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
        <button class="btn btn-green" onclick="controlLed('on')">💡 Bật đèn</button>
        <button class="btn btn-blue" onclick="controlLed('off')">🌙 Tắt đèn</button>
        <button class="btn btn-amber" id="btnLedBlink" onclick="controlLed('blink')">✨ Nháy LED</button>
      </div>

      <div style="margin-top:10px;">
        <div style="font-size:11px; color:var(--muted); margin-bottom:4px;">
          <span data-lang-vi>Độ sáng vòng LED</span><span data-lang-en>LED ring brightness</span>:
          <b id="hudLedBright">100%</b>
        </div>
        <input type="range" id="ledBrightRange" min="0" max="100" step="5" value="100"
               style="width:100%;" oninput="setLedBrightness(this.value)">
      </div>

      <div style="margin-top:10px;">
        <div style="font-size:11px; color:var(--muted); margin-bottom:4px;">
          <span data-lang-vi>Âm lượng Spotify</span><span data-lang-en>Spotify volume</span>:
          <b id="hudSpotifyVol">--%</b>
        </div>
        <div class="btn-row">
          <button class="btn btn-blue" onclick="sendAct('volume_down')">🔉 -5%</button>
          <button class="btn btn-green" onclick="sendAct('volume_up')">🔊 +5%</button>
          <button class="btn" style="background:#f59e0b; color:#000;" onclick="triggerBuzz()">🔔 Bíp Còi</button>
        </div>
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
    function setLedBrightness(v) {
      document.getElementById('hudLedBright').innerText = v + '%';
      fetch('/api/led_bright?value=' + v);
    }
    function toggleLanguage() { fetch('/api/lang?toggle=1').then(() => location.reload()); }
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

    // Hide the English duplicates until the station reports EN.
    fetch('/api/status').then(r => r.json()).then(d => {
      const en = (d.language === 'en');
      document.querySelectorAll('[data-lang-vi]').forEach(e => e.style.display = en ? 'none' : '');
      document.querySelectorAll('[data-lang-en]').forEach(e => e.style.display = en ? '' : 'none');
    }).catch(() => {});

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

        if (typeof d.ledBrightness === 'number') {
          document.getElementById('hudLedBright').innerText = d.ledBrightness + '%';
          const range = document.getElementById('ledBrightRange');
          if (document.activeElement !== range) range.value = d.ledBrightness;
        }
        if (typeof d.spotifyVolume === 'number') {
          document.getElementById('hudSpotifyVol').innerText = d.spotifyVolume + '%';
        }
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
  triggerBuzz(250, true); 
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
    unsigned int unit = 60;
    if (server.hasArg("unit")) unit = server.arg("unit").toInt();
    unsigned int freq = 1000;
    if (server.hasArg("freq")) freq = server.arg("freq").toInt();
    startMorsePlayback(pattern, unit, freq);
    server.send(200, "text/plain", "Morse playing\n");
    return;
  }
  server.send(400, "text/plain", "Missing pattern or act=stop\n");
}

void handleApiLedBright() {
  if (server.hasArg("value")) {
    int v = constrain(server.arg("value").toInt(), 0, 100);
    ledBrightness = v;
    ledState = v > 0;
    prefs.putInt("led_bright", ledBrightness);
    prefs.putBool("led_state", ledState);
    updateLampLed();
    screenNeedUpdate = true;
    addLog("LED", "Portal brightness: " + String(v) + "%");
    server.send(200, "text/plain", "OK");
  } else {
    server.send(400, "text/plain", "Missing value");
  }
}

void handleApiLang() {
  if (server.hasArg("toggle")) {
    applyStationLanguage(!stationEnglish, true);
  } else if (server.hasArg("en")) {
    applyStationLanguage(server.arg("en") == "1" || server.arg("en") == "true", true);
  }
  screenNeedUpdate = true;
  server.send(200, "application/json", String("{\"english\":") + (stationEnglish ? "true" : "false") + "}");
}

void handleApiLed() {
  if (server.hasArg("act")) {
    String act = server.arg("act");
    if (act == "blink") {
      ledBlinkMode = !ledBlinkMode;
      if (!ledBlinkMode) {
        ledState = false;
        prefs.putBool("led_state", false);
        updateLampLed();
      }
    } else if (act == "on") {
      ledBlinkMode = false;
      ledState = true;
      prefs.putBool("led_state", true);
      updateLampLed();
    } else if (act == "off") {
      ledBlinkMode = false;
      ledState = false;
      prefs.putBool("led_state", false);
      updateLampLed();
    } else if (act == "toggle") {
      ledBlinkMode = false;
      ledState = !ledState;
      prefs.putBool("led_state", ledState);
      updateLampLed();
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
  doc["ledBrightness"] = ledBrightness;
  doc["spotifyVolume"] = spotifyVolume;
  doc["language"] = stationEnglish ? "en" : "vi";
  doc["morseUnitMs"] = morseUnitMs;
  doc["morseFrequency"] = morseFrequency;
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
    srvStats.activeView = "calendar";
  } else if (srvStats.activeView == "calendar") {
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
  } else if (srvStats.activeView == "clock") {
    srvStats.activeView = "game";
  } else {
    srvStats.activeView = "main";
  }

  screenNeedUpdate = true;
  addLog("VIEW", "Chuyen sang View: " + srvStats.activeView);
  publishDeviceStatus();  // let every app know the station screen changed
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
extern String lastMenuSignature;
void triggerPairCodeRequestFromMenu() {
  pairCodeLoading = true;
  pairFetchFailed = false;
  pairCodeValue = "";
  pairQrBits = "";
  pairQrSize = 0;
  pairReqStartedAt = millis();
  screenNeedUpdate = true;
  drawSettingsMenu();
  triggerBuzz(30);

  if (mqtt.connected()) {
    mqtt.publish("deskflow/pair_request", "{\"action\":\"request_pair_code\",\"full_access\":true}");
  }

  // Reliable HTTPS direct fetch (instant, works even if MQTT broker queue drops)
  if (WiFi.status() == WL_CONNECTED) {
    WiFiClientSecure client;
    client.setInsecure();
    HTTPClient http;
    http.setTimeout(6000);
    if (http.begin(client, "https://df.api.thetechdude.io.vn/api/app/station/pair-code")) {
      int code = http.GET();
      if (code == 200) {
        String body = http.getString();
        DynamicJsonDocument pdoc(4096);
        if (!deserializeJson(pdoc, body) && pdoc["success"].as<bool>()) {
          pairCodeValue = pdoc["code"] | "";
          pairQrSize = pdoc["qr_size"] | 0;
          pairQrBits = pdoc["qr_bits"] | "";
          pairCodeLoading = false;
          pairFetchFailed = (pairCodeValue.length() == 0);
          pairCodeFetchedAt = millis();
          lastMenuSignature = "";
          screenNeedUpdate = true;
          triggerBuzz(45);
          addLog("PAIR", "HTTPS fetched code " + pairCodeValue + " (" + String(pairQrSize) + "x" + String(pairQrSize) + ")");
        }
      }
      http.end();
    }
  }
  if (pairCodeLoading && WiFi.status() != WL_CONNECTED) {
    pairCodeLoading = false;
    pairFetchFailed = true;
    lastMenuSignature = "";
    screenNeedUpdate = true;
  }
}

void triggerSelfOtaFromMenu() {
  noTone(BUZZER_PIN);
  isBuzzing = false;
  if (WiFi.status() != WL_CONNECTED) {
    if (tft) {
      tft->fillScreen(COL_BG);
      printCenteredText("DESKFLOW OTA", 95, 2, 0xF800, COL_BG);
      printCenteredText(tr("Chưa kết nối Wi-Fi!", "Wi-Fi not connected!"), 135, 1, COL_WHITE, COL_BG);
      delay(1800);
      lastMenuSignature = "";
      screenNeedUpdate = true;
    }
    return;
  }
  if (tft) {
    tft->fillScreen(COL_BG);
    printCenteredText("DESKFLOW OTA", 95, 2, COL_CYAN, COL_BG);
    printCenteredText(tr("Đang kéo bản mới từ Server...", "Checking & pulling firmware..."), 135, 1, COL_WHITE, COL_BG);
  }
  // Free MQTT TLS socket RAM before starting HTTPS OTA
  if (mqtt.connected()) mqtt.disconnect();
  delay(80);
  String ver = "latest";
  String binUrl = "https://df.api.thetechdude.io.vn/api/app/control/ota/firmware.bin";
  {
    WiFiClientSecure client;
    client.setInsecure();
    HTTPClient http;
    if (http.begin(client, "https://df.api.thetechdude.io.vn/api/app/station/ota-trigger")) {
      http.setTimeout(25000);
      int code = http.POST("{}");
      if (code == 200) {
        DynamicJsonDocument doc(512);
        if (deserializeJson(doc, http.getString()) == DeserializationError::Ok) {
          ver = doc["version"] | "latest";
          binUrl = doc["bin_url"] | "https://df.api.thetechdude.io.vn/api/app/control/ota/firmware.bin";
        }
      }
      http.end();
    }
  }
  performOtaUpdate(binUrl, ver);
  lastMenuSignature = "";
  screenNeedUpdate = true;
}

void handleMenuPlay() {
  if (menuState == MENU_MAIN) {
    if (menuCurrentItem == 0) {
      // LED ring on/off
      ledBlinkMode = false;
      ledState = !ledState;
      prefs.putBool("led_state", ledState);
      updateLampLed();
      screenNeedUpdate = true;
      triggerBuzz(35);
      addLog("LED", String("Menu toggle LED: ") + (ledState ? "ON" : "OFF"));
    } else if (menuCurrentItem == 1) {
      menuState = MENU_LED_COLOR;
      colorPage = ledPresetIndex / 4;
      screenNeedUpdate = true;
      triggerBuzz(35);
    } else if (menuCurrentItem == 2) {
      menuState = MENU_LED_EFFECT;
      effectPage = ledEffectIndex / 4;
      screenNeedUpdate = true;
      triggerBuzz(35);
    } else if (menuCurrentItem == 3) {
      menuState = MENU_ADJUST_LED;
      screenNeedUpdate = true;
      triggerBuzz(35);
    } else if (menuCurrentItem == 4) {
      menuState = MENU_LED_SPEED;
      screenNeedUpdate = true;
      triggerBuzz(35);
    } else if (menuCurrentItem == 5) {
      menuState = MENU_LED_KELVIN;
      screenNeedUpdate = true;
      triggerBuzz(35);
    } else if (menuCurrentItem == 6) {
      menuState = MENU_BUZZER_CONFIG;
      buzzerCurrentItem = 0;
      screenNeedUpdate = true;
      triggerBuzz(35);
    } else if (menuCurrentItem == 7) {
      menuState = MENU_VOLUME;
      screenNeedUpdate = true;
      triggerBuzz(35);
    } else if (menuCurrentItem == 8) {
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
    } else if (menuCurrentItem == 9) {
      applyStationLanguage(!stationEnglish, true);
      triggerBuzz(50);
      addLog("I18N", String("Station language: ") + (stationEnglish ? "EN" : "VI"));
    } else if (menuCurrentItem == 10) {
      menuState = MENU_PAIR_DEVICE;
      triggerPairCodeRequestFromMenu();
      addLog("PAIR", "Requested Full Access pair code & QR from backend");
    } else if (menuCurrentItem == 11) {
      triggerSelfOtaFromMenu();
    } else if (menuCurrentItem == 12) {
      inSettingsMenu = false;
      menuState = MENU_MAIN;
      lastRenderedView = "";
      screenNeedUpdate = true;
      triggerBuzz(35);
      addLog("MENU", "Thoat menu cai dat qua muc EXIT.");
    }
  } else if (menuState == MENU_LED_COLOR) {
    if (String(LED_PRESETS[ledPresetIndex]) == "RGB") {
      ledEffect = "rainbow";
      ledEffectIndex = 1;
      ledRgbMode = true;
      ledGradientMode = false;
      prefs.putString("led_eff", "rainbow");
    } else {
      srvStats.lampColor = LED_PRESETS[ledPresetIndex];
      ledEffect = "solid";
      ledEffectIndex = 0;
      ledRgbMode = false;
      ledGradientMode = false;
      prefs.putString("lamp_col", srvStats.lampColor);
      prefs.putString("led_eff", "solid");
    }
    prefs.putInt("led_color_idx", ledPresetIndex);
    ledState = true;
    ledBlinkMode = false;
    prefs.putBool("led_state", true);
    updateLampLed();
    screenNeedUpdate = true;
    triggerBuzz(45);
    addLog("LED", "Preset color: " + String(LED_PRESETS[ledPresetIndex]));
  } else if (menuState == MENU_LED_EFFECT) {
    ledEffect = String(LED_EFFECT_IDS[ledEffectIndex]);
    ledRgbMode = (ledEffect == "rainbow");
    ledGradientMode = false;
    if (ledEffect == "rainbow") ledPresetIndex = 7;
    prefs.putString("led_eff", ledEffect);
    ledState = true;
    ledBlinkMode = false;
    prefs.putBool("led_state", true);
    updateLampLed();
    screenNeedUpdate = true;
    triggerBuzz(45);
    addLog("LED", "Color mode: " + ledEffect);
  } else if (menuState == MENU_ADJUST_LED) {
    prefs.putInt("led_bright", ledBrightness);
    ledState = ledBrightness > 0;
    prefs.putBool("led_state", ledState);
    publishHardwareSettingsUpdate();
    updateLampLed();
    menuState = MENU_MAIN;
    screenNeedUpdate = true;
    triggerBuzz(50);
  } else if (menuState == MENU_LED_SPEED) {
    prefs.putInt("led_spd", ledSpeed);
    menuState = MENU_MAIN;
    screenNeedUpdate = true;
    triggerBuzz(50);
  } else if (menuState == MENU_LED_KELVIN) {
    menuState = MENU_MAIN;
    screenNeedUpdate = true;
    triggerBuzz(50);
  } else if (menuState == MENU_VOLUME) {
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
      triggerBuzz(buzzerDurationSetting, true);
    } else if (buzzerCurrentItem == 2) {
      navBeepEnabled = !navBeepEnabled;
      prefs.putBool("nav_beep", navBeepEnabled);
      screenNeedUpdate = true;
      if (navBeepEnabled) triggerBuzz(35, true);
    } else if (buzzerCurrentItem == 3) {
      triggerBuzzTone(buzzerDurationSetting, MORSE_TEST_FREQ);
    } else if (buzzerCurrentItem == 4) {
      menuState = MENU_MAIN;
      screenNeedUpdate = true;
      triggerBuzz(35);
    }
  } else if (menuState == MENU_PAIR_DEVICE) {
    menuState = MENU_MAIN;
    screenNeedUpdate = true;
    triggerBuzz(35);
  }
}

void handleMenuNext() {
  if (menuState == MENU_MAIN) {
    menuCurrentItem = (menuCurrentItem + 1) % MENU_MAIN_ITEM_COUNT;
    // Moving past the last row of a page flips to the next page.
    menuPage = menuCurrentItem / MENU_ROWS_PER_PAGE;
    screenNeedUpdate = true;
    triggerBuzz(20);
  } else if (menuState == MENU_PAIR_DEVICE) {
    triggerPairCodeRequestFromMenu();
  } else if (menuState == MENU_ADJUST_LED) {
    ledBrightness = min(100, ledBrightness + 5);
    bool oldState = ledState;
    ledState = true;
    updateLampLed();
    ledState = oldState;
    prefs.putInt("led_bright", ledBrightness);
    screenNeedUpdate = true;
    triggerBuzz(20);
  } else if (menuState == MENU_LED_COLOR) {
    ledPresetIndex = (ledPresetIndex + 1) % LED_PRESET_COUNT;
    colorPage = ledPresetIndex / 4;
    screenNeedUpdate = true;
    triggerBuzz(20);
  } else if (menuState == MENU_LED_EFFECT) {
    ledEffectIndex = (ledEffectIndex + 1) % LED_EFFECT_COUNT;
    effectPage = ledEffectIndex / 4;
    screenNeedUpdate = true;
    triggerBuzz(20);
  } else if (menuState == MENU_LED_SPEED) {
    ledSpeed = min(100, ledSpeed + 5);
    screenNeedUpdate = true;
  } else if (menuState == MENU_LED_KELVIN) {
    ledKelvin = min(6500, ledKelvin + 200);
    float t = (float)(ledKelvin - 2700) / 3800.0f;
    uint8_t kr = 255, kg = (uint8_t)(165 + t * 80), kb = (uint8_t)(75 + t * 180);
    char hexBuf[10];
    snprintf(hexBuf, sizeof(hexBuf), "#%02X%02X%02X", kr, kg, kb);
    srvStats.lampColor = String(hexBuf);
    ledEffect = "solid";
    ledRgbMode = false;
    ledGradientMode = false;
    ledBlinkMode = false;
    ledState = true;
    updateLampLed();
    screenNeedUpdate = true;
  } else if (menuState == MENU_VOLUME) {
    spotifyVolumeKnown = true;
    spotifyVolume = min(100, spotifyVolume + 5);
    sendButtonAction("volume_up");
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
    menuPage = menuCurrentItem / MENU_ROWS_PER_PAGE;
    screenNeedUpdate = true;
    triggerBuzz(20);
  } else if (menuState == MENU_ADJUST_LED) {
    ledBrightness = max(0, ledBrightness - 5);
    bool oldState = ledState;
    ledState = true;
    updateLampLed();
    ledState = oldState;
    prefs.putInt("led_bright", ledBrightness);
    screenNeedUpdate = true;
    triggerBuzz(20);
  } else if (menuState == MENU_LED_COLOR) {
    ledPresetIndex = (ledPresetIndex - 1 + LED_PRESET_COUNT) % LED_PRESET_COUNT;
    colorPage = ledPresetIndex / 4;
    screenNeedUpdate = true;
    triggerBuzz(20);
  } else if (menuState == MENU_LED_EFFECT) {
    ledEffectIndex = (ledEffectIndex - 1 + LED_EFFECT_COUNT) % LED_EFFECT_COUNT;
    effectPage = ledEffectIndex / 4;
    screenNeedUpdate = true;
    triggerBuzz(20);
  } else if (menuState == MENU_LED_SPEED) {
    ledSpeed = max(5, ledSpeed - 5);
    screenNeedUpdate = true;
  } else if (menuState == MENU_LED_KELVIN) {
    ledKelvin = max(2700, ledKelvin - 200);
    float t = (float)(ledKelvin - 2700) / 3800.0f;
    uint8_t kr = 255, kg = (uint8_t)(165 + t * 80), kb = (uint8_t)(75 + t * 180);
    char hexBuf[10];
    snprintf(hexBuf, sizeof(hexBuf), "#%02X%02X%02X", kr, kg, kb);
    srvStats.lampColor = String(hexBuf);
    ledEffect = "solid";
    ledRgbMode = false;
    ledGradientMode = false;
    ledBlinkMode = false;
    ledState = true;
    updateLampLed();
    screenNeedUpdate = true;
  } else if (menuState == MENU_VOLUME) {
    spotifyVolumeKnown = true;
    spotifyVolume = max(0, spotifyVolume - 5);
    sendButtonAction("volume_down");
    screenNeedUpdate = true;
    triggerBuzz(20);
  } else if (menuState == MENU_BUZZER_CONFIG) {
    buzzerCurrentItem = (buzzerCurrentItem - 1 + MENU_BUZZER_ITEM_COUNT) % MENU_BUZZER_ITEM_COUNT;
    screenNeedUpdate = true;
    triggerBuzz(20);
  } else if (menuState == MENU_PAIR_DEVICE) {
    triggerPairCodeRequestFromMenu();
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

  // Quick physical acknowledge for Anti-Oversleep Wakeup Check
  if (isWakeupCheckActive && (curPlay == LOW || curNext == LOW || curPrev == LOW)) {
    isWakeupCheckActive = false;
    triggerBuzz(60, true);
    if (mqtt.connected()) {
      mqtt.publish("deskflow/status_update", "{\"action\":\"confirm_awake\"}");
    }
    srvStats.activeView = "main";
    lastRenderedView = "";
    screenNeedUpdate = true;
    addLog("ALARM", "User pressed button -> Wakeup confirmed!");
    delay(220);
    return;
  }

  // Unified Arcade Hub button handler: PREV/NEXT NEVER switch global screens here
  if (!inSettingsMenu && !inViewMenu && srvStats.activeView == "game") {
    bool exitToMain = false;
    handleArcadeButtons(curPlay, curNext, curPrev, exitToMain);
    if (exitToMain) {
      srvStats.activeView = "main";
      inGameLobby = true;
      lastRenderedView = "";
      screenNeedUpdate = true;
      publishDeviceStatus();
    }
    return;
  }

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
      } else if (inSettingsMenu) {
        // A long PLAY is still a valid selection while the hardware settings
        // menu is open. Previously this branch did nothing, which made PLAY
        // appear dead in the settings menu.
        handleMenuPlay();
        addLog("BUTTON", "PLAY hold: activate selected settings item");
      } else {
        triggerBuzz(80, false);
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
      } else if (srvStats.activeView == "calendar") {
        toggleCalendarSelectOrExit();
      } else if (srvStats.activeView == "stopwatch") {
        if (swRunning) { swElapsedMs += millis() - swStartMs; swRunning = false; }
        else { swStartMs = millis(); swRunning = true; }
        screenNeedUpdate = true;
      } else if (srvStats.activeView == "formulas") {
        formulaTab = 1 - formulaTab;
        formulaIdx = 0;
        screenNeedUpdate = true;
        triggerBuzz(30);
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
        // Always start on the first row (LED on/off) for predictable navigation.
        menuCurrentItem = 0;
        menuPage = 0;
        lastRenderedView = "";
        screenNeedUpdate = true;
        addLog("MENU", "Mo menu cai dat (Mau den).");
      } else {
        // Hold >> again: exit immediately from the main list or any submenu.
        inSettingsMenu = false;
        menuState = MENU_MAIN;
        lastRenderedView = "";
        screenNeedUpdate = true;
        addLog("MENU", "Giu >>: thoat menu cai dat.");
      }
    }
  } else if (curNext == HIGH && nextPressed) {
    nextPressed = false;
    if (!nextLongTriggered) {
      if (inSettingsMenu) {
        handleMenuNext();
      } else if (inViewMenu) {
        viewMenuIndex = (viewMenuIndex + 1) % VIEW_MENU_COUNT;
        viewMenuPage = viewMenuIndex / VIEW_ROWS_PER_PAGE;
        triggerBuzz(20);
        screenNeedUpdate = true;
      } else {
        if (srvStats.activeView == "notes") {
          cycleNextNote();
        } else if (srvStats.activeView == "countdown") {
          cycleNextCountdown();
        } else if (srvStats.activeView == "calendar") {
          cycleNextCalendar();
        } else if (srvStats.activeView == "stopwatch") {
          if (swLapCount < 4) swLaps[swLapCount++] = swRunning ? (swElapsedMs + (millis() - swStartMs)) : swElapsedMs;
          screenNeedUpdate = true;
        } else if (srvStats.activeView == "formulas") {
          formulaIdx++;
          screenNeedUpdate = true;
          triggerBuzz(20);
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
      if (inSettingsMenu) {
        // Hold <<: exit immediately from the main list or any submenu.
        inSettingsMenu = false;
        menuState = MENU_MAIN;
        lastRenderedView = "";
        screenNeedUpdate = true;
        addLog("MENU", "Giu <<: thoat menu cai dat.");
      } else if (!inViewMenu) {
        inViewMenu = true;
        for (int i = 0; i < VIEW_MENU_COUNT; i++) {
          if (srvStats.activeView == VIEW_MENU_ITEMS[i].id) {
            viewMenuIndex = i;
            break;
          }
        }
        viewMenuPage = viewMenuIndex / VIEW_ROWS_PER_PAGE;
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
  } else if (curPrev == HIGH && prevPressed) {
    prevPressed = false;
    if (!prevLongTriggered) {
      if (inSettingsMenu) {
        handleMenuPrev();
      } else if (inViewMenu) {
        viewMenuIndex = (viewMenuIndex - 1 + VIEW_MENU_COUNT) % VIEW_MENU_COUNT;
        viewMenuPage = viewMenuIndex / VIEW_ROWS_PER_PAGE;
        triggerBuzz(20);
        screenNeedUpdate = true;
      } else {
        if (srvStats.activeView == "notes") {
          cyclePrevNote();
        } else if (srvStats.activeView == "countdown") {
          cyclePrevCountdown();
        } else if (srvStats.activeView == "calendar") {
          cyclePrevCalendar();
        } else if (srvStats.activeView == "stopwatch") {
          swRunning = false; swElapsedMs = 0; swLapCount = 0;
          screenNeedUpdate = true;
        } else if (srvStats.activeView == "formulas") {
          formulaIdx = (formulaIdx + 11) % 12;
          screenNeedUpdate = true;
          triggerBuzz(20);
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
String lastMenuSignature = "";

void setup() {
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);

  Serial.begin(115200);
  delay(100);

  initBuzzer();

  analogReadResolution(12);
  analogSetPinAttenuation(PIN_BATTERY, ADC_11db);

  strip.begin();
  strip.clear();
  strip.show();

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
  hwTft = new Adafruit_ST7789(&SPI, TFT_CS, TFT_DC, TFT_RST);
  tft = hwTft;
  prefs.begin("deskflow", false);
  customBootTitle = prefs.getString("boot_title", "DeskFlow Station");
  customBootSub = prefs.getString("boot_sub", "v" DESKFLOW_FW_VERSION);
  customBootBmp = prefs.getString("boot_bmp", "");

  if (hwTft) {
    hwTft->init(240, 320);
    hwTft->setSPISpeed(80000000);
    hwTft->invertDisplay(false);
    hwTft->setRotation(0);
    drawCustomBootLogo();
  }

  WiFi.mode(WIFI_AP_STA);
  WiFi.setAutoReconnect(true);
  WiFi.persistent(true);
  WiFi.setTxPower(WIFI_POWER_17dBm);
  WiFi.setSleep(false);
  WiFi.softAP("DeskFlow-Station", "", 1, 0, 4);
  WiFi.softAPsetHostname("deskflow-station");

  configTime(25200, 0, "pool.ntp.org", "time.google.com");

  wifiListJson = prefs.getString("wifi_array", "[]");
  loadStationLanguage();
  loadPendingLocalLangSync();
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
  navBeepEnabled = prefs.getBool("nav_beep", false);
  ledEffect = prefs.getString("led_eff", "solid");
  for (int ei = 0; ei < LED_EFFECT_COUNT; ei++) {
    if (ledEffect == String(LED_EFFECT_IDS[ei])) { ledEffectIndex = ei; break; }
  }
  buzzerDurationSetting = prefs.getUInt("buzz_dur", 40);
  srvStats.lampColor = prefs.getString("lamp_col", "#FFFFFF");
  srvStats.lampColor2 = prefs.getString("lamp_col2", "#A855F7");
  ledEffect = prefs.getString("led_eff", "solid");
  ledRgbMode = prefs.getBool("led_rgb", false);
  ledGradientMode = prefs.getBool("led_grad", false);
  ledSpeed = prefs.getInt("led_spd", 50);
  for (int gi = 0; gi < ARCADE_GAME_COUNT; gi++) {
    gameHighScores[gi] = prefs.getInt(("game_hi_" + String(gi)).c_str(), 0);
  }
  ledPresetIndex = prefs.getInt("led_color_idx", 0);
  if (ledPresetIndex < 0 || ledPresetIndex >= LED_PRESET_COUNT) ledPresetIndex = 0;
  for (int i = 0; i < LED_PRESET_COUNT; i++) {
    if (srvStats.lampColor.equalsIgnoreCase(LED_PRESETS[i])) { ledPresetIndex = i; break; }
  }
  ledState = prefs.getBool("led_state", true); // Default to ON so NeoPixel turns on on boot!
  updateLampLed();

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
  server.on("/api/led_bright", handleApiLedBright);
  server.on("/api/lang", handleApiLang);
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

  // 1. Math Alarm: dedicated "DeskFlow Alert" tune + red LED strobe until solved
  if (isAlarmActive) {
    if (!alarmTuneIsPlaying()) {
      alarmTuneStart();
    }
    alarmTuneUpdate(buzzerEnabled);

    unsigned long now = millis();
    if (now - lastAlarmStrobeTime >= 420) {
      lastAlarmStrobeTime = now;
      alarmStrobeOn = !alarmStrobeOn;
      strip.setBrightness(255);
      for (int i = 0; i < NUM_LEDS; i++) {
        strip.setPixelColor(i, alarmStrobeOn ? strip.Color(255, 0, 0) : strip.Color(0, 0, 0));
      }
      strip.show();
    }
  }
  // 2. Tiếng bíp đơn lẻ theo lệnh /buzz
  else if (isBuzzing && (millis() - buzzStartTime >= buzzDuration)) {
    isBuzzing = false;
    stopBuzzer();
  }

  // 3. Non-blocking Morse playback (the alarm uses its own dedicated tune)
  updateMorsePlayback();
  if (!isAlarmActive) {
    melodyUpdate(buzzerEnabled);
  } else {
    melodyStop();
  }

  if (ledBlinkMode) {
    if (millis() - lastLedBlinkTime >= LED_BLINK_INTERVAL) {
      lastLedBlinkTime = millis();
      ledState = !ledState;
      if (!ledState || ledBrightness <= 0) {
        strip.clear();
        strip.show();
      } else {
        uint8_t br = 255, bg = 255, bb = 255;
        if (ledRgbMode) {
          wheelRgb((lastLedBlinkTime / 6) & 255, br, bg, bb);
        } else {
          parseHexColor(srvStats.lampColor, br, bg, bb);
        }
        uint32_t c = strip.Color((uint16_t)br * ledBrightness / 100, (uint16_t)bg * ledBrightness / 100, (uint16_t)bb * ledBrightness / 100);
        strip.setBrightness(255);
        for (int i = 0; i < NUM_LEDS; i++) strip.setPixelColor(i, c);
        strip.show();
      }
    }
  } else {
    updateLedEffects();
  }

  if (pendingOtaUrl.length() > 0) {
    String u = pendingOtaUrl;
    String v = pendingOtaVersion;
    pendingOtaUrl = "";
    performOtaUpdate(u, v);
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

  unsigned long refreshThreshold = (srvStats.activeView == "game" || srvStats.activeView == "matrix" ||
                                    srvStats.activeView == "vinyl" || srvStats.activeView == "analog" ||
                                    srvStats.activeView == "stopwatch" || isClockAnimationActive())
                                     ? 16
                                     : 1000;

  if (pendingScreenshotCapture) {
    pendingScreenshotCapture = false;
    captureAndUploadScreenshot();
  }

  // Timeout guard if pair code stayed loading via MQTT
  if (inSettingsMenu && menuState == MENU_PAIR_DEVICE && pairCodeLoading && pairCodeValue.length() == 0 && (millis() - pairReqStartedAt > 7500)) {
    pairCodeLoading = false;
    pairFetchFailed = true;
  }

  if (inSettingsMenu || inViewMenu) {
    screenNeedUpdate = false;
    String menuSignature = String(inSettingsMenu ? "S" : "V") + "|" +
                           String((int)menuState) + "|" + String(menuCurrentItem) + "|" +
                           String(menuPage) + "|" + String(buzzerCurrentItem) + "|" +
                           String(ledPresetIndex) + "|" + String(colorPage) + "|" + String(ledEffectIndex) + "|" + String(effectPage) + "|" + ledEffect + "|" + String(viewMenuIndex) + "|" +
                           String(viewMenuPage) + "|" + String(ledState ? 1 : 0) + "|" +
                           String(ledBrightness) + "|" + String(ledSpeed) + "|" + String(ledKelvin) + "|" + String(stationEnglish ? 1 : 0) + "|" +
                           String(buzzerEnabled ? 1 : 0) + "|" + String(navBeepEnabled ? 1 : 0) + "|" + String(buzzerDurationSetting) + "|" +
                           darkModeSetting + "|" + String(spotifyVolumeKnown ? spotifyVolume : -1) + "|" +
                           srvStats.activeView + "|" +
                           String(pairCodeLoading ? 1 : 0) + "|" + String(pairFetchFailed ? 1 : 0) + "|" +
                           pairCodeValue + "|" + String(pairQrSize);
    if (menuSignature != lastMenuSignature) {
      if (lastMenuSignature.length() == 0) menuForceFullRedraw = true;
      lastMenuSignature = menuSignature;
      lastScreenDraw = millis();
      drawPhysicalScreen();
    }
  } else if (screenNeedUpdate || (millis() - lastScreenDraw >= refreshThreshold)) {
    lastScreenDraw = millis();
    screenNeedUpdate = false;
    lastMenuSignature = "";   // force a full repaint next time a menu opens
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