#ifndef CLOCK_STYLES_H
#define CLOCK_STYLES_H

#include <Arduino.h>
#include <PubSubClient.h>
#include <Adafruit_GFX.h>
#include <Adafruit_ST7789.h>
#include <ArduinoJson.h>
#include <Preferences.h>
#include <time.h>
#include <math.h>

// Forward declarations of shared globals from khkt.ino
extern Adafruit_GFX* tft;
extern Preferences prefs;
extern PubSubClient mqtt;
extern void addLog(String tag, String msg);
extern String removeAccents(String str);
extern void printCenteredText(String text, int y, int size, uint16_t color, uint16_t bg);

// Dynamic Theme Color Palette RGB565 (Light & Dark Mode)
extern uint16_t themeBg;
extern uint16_t themeCard;
extern uint16_t themeBorder;
extern uint16_t themeText;
extern uint16_t themeMuted;
extern uint16_t themeCyan;
extern uint16_t themeGreen;
extern uint16_t themeAmber;
extern uint16_t themePurple;

#define COL_BG     themeBg
#define COL_CARD   themeCard
#define COL_BORDER themeBorder
#define COL_WHITE  themeText
#define COL_MUTED  themeMuted
#define COL_CYAN   themeCyan
#define COL_GREEN  themeGreen
#define COL_AMBER  themeAmber
#define COL_PURPLE themePurple

#define COL_NEON_MAGENTA 0xF81F
#define COL_NEON_BLUE    0x001F

// Supported Clock Style Identifiers
#define STYLE_DIGITAL_CLASSIC "digital_classic"
#define STYLE_DIGITAL_MINIMAL "digital_minimal"
#define STYLE_DIGITAL_NEON    "digital_neon"
#define STYLE_ANALOG          "analog"
#define STYLE_FLIP            "flip"
#define STYLE_BINARY          "binary"
#define STYLE_WORD            "word"

struct ClockConfig {
  String style = STYLE_DIGITAL_CLASSIC;
  String timeFormat = "24h";       // "12h" or "24h"
  bool showSeconds = true;
  bool showDate = true;
  bool showAmPm = false;
  String language = "vi";          // "vi" or "en"
  bool animationEnabled = true;
  float animationSpeed = 1.0f;     // 0.5 to 2.0
  uint16_t accentColor = COL_CYAN;
  String accentHex = "#38BDF8";
  int configVersion = 1;
};

extern ClockConfig clockCfg;

// State management for smooth anti-flicker differential drawing
struct AnalogHandState {
  int lastHx = -1, lastHy = -1;
  int lastMx = -1, lastMy = -1;
  int lastSx = -1, lastSy = -1;
  void reset() { lastHx = lastHy = lastMx = lastMy = lastSx = lastSy = -1; }
};

struct FlipDigitState {
  char lastDigit[6] = {' ', ' ', ' ', ' ', ' ', ' '}; // HHMMSS
  int flipProgress[6] = {0, 0, 0, 0, 0, 0};            // 0=idle, 1..3=animating
  unsigned long lastAnimTime[6] = {0, 0, 0, 0, 0, 0};
  void reset() {
    for (int i = 0; i < 6; i++) {
      lastDigit[i] = ' ';
      flipProgress[i] = 0;
      lastAnimTime[i] = 0;
    }
  }
};

struct StyleTransitionState {
  bool inTransition = false;
  unsigned long startTime = 0;
  int step = 0;
  String oldStyle = "";
  String targetStyle = "";
};

extern AnalogHandState analogState;
extern FlipDigitState flipState;
extern StyleTransitionState styleTransition;

// Utility functions
uint16_t clockHexToRGB565(const String& hex);
String getDayOfWeekName(int wday, const String& lang);
String getMonthName(int mon, const String& lang);
String getFormattedClockTime(const struct tm& t, bool withSeconds, const String& fmt, String& outAmPm);
String getFormattedClockDate(const struct tm& t, const String& lang);

// Main rendering entry point
void drawClockArea(int x, int y, int w, int h, bool fullScreen);

// Individual style rendering implementations
void drawDigitalClassic(int x, int y, int w, int h, const struct tm& t, bool fullScreen);
void drawDigitalMinimal(int x, int y, int w, int h, const struct tm& t, bool fullScreen);
void drawDigitalNeon(int x, int y, int w, int h, const struct tm& t, bool fullScreen);
void drawAnalogClock(int x, int y, int w, int h, const struct tm& t, bool fullScreen);
void drawFlipClock(int x, int y, int w, int h, const struct tm& t, bool fullScreen);
void drawBinaryClock(int x, int y, int w, int h, const struct tm& t, bool fullScreen);
void drawWordClock(int x, int y, int w, int h, const struct tm& t, bool fullScreen);

// NVS Persistence
void loadClockConfigNVS();
void saveClockConfigNVS();

// MQTT Integration & ACK
void handleClockConfigPayload(DynamicJsonDocument& doc);
void sendClockStyleAck(const String& status, const String& err = "");
void sendClockStyleRequest();

// Animation state handler
bool isClockAnimationActive();
void updateClockAnimations();

#endif // CLOCK_STYLES_H
