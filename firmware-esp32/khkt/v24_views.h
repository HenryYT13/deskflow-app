#ifndef DESKFLOW_V24_VIEWS_H
#define DESKFLOW_V24_VIEWS_H

#include <math.h>
#include <time.h>

extern Arduino_GFX* tft;
extern PubSubClient mqttClient;
extern int phoneBattPct;
extern bool phoneBattCharging;
extern String incomingCallCaller;
extern unsigned long incomingCallUntil;
extern int clockFontMode; // #52: 0=Pixel, 1=Retro, 2=Rounded, 3=Minimalist

// #30 Stopwatch state
bool swRunning = false;
unsigned long swStartMs = 0;
unsigned long swElapsedMs = 0;
unsigned long swLaps[4] = {0, 0, 0, 0};
int swLapCount = 0;

// #35 Water Reminder every 45m
unsigned long lastWaterRemindMs = 0;
bool waterPopupActive = false;
unsigned long waterPopupUntil = 0;

// #108 / #109 LED Flash helper
inline void flashLedEvent(uint32_t color, int times) {
  for (int t = 0; t < times; t++) {
    for (int i = 0; i < 24; i++) strip.setPixelColor(i, color);
    strip.show();
    delay(90);
    strip.clear();
    strip.show();
    delay(70);
  }
}

// #53 View transition effect
inline void playSlideTransition() {
  for (int x = 0; x < 240; x += 48) {
    tft->fillRect(x, 0, 48, 320, COL_BG);
    delay(8);
  }
}

// #3 Analog Clock 60fps smooth sweep
inline void drawAnalogClockView() {
  struct tm ti;
  if (!getLocalTime(&ti)) return;
  int cx = 120, cy = 155, r = 92;
  tft->fillCircle(cx, cy, r + 4, COL_CARD);
  tft->drawCircle(cx, cy, r + 4, COL_CYAN);
  for (int m = 0; m < 12; m++) {
    float a = (m * 30.0f - 90.0f) * DEG_TO_RAD;
    int x1 = cx + cosf(a) * (r - 8), y1 = cy + sinf(a) * (r - 8);
    int x2 = cx + cosf(a) * (r - 1), y2 = cy + sinf(a) * (r - 1);
    tft->drawLine(x1, y1, x2, y2, COL_WHITE);
  }
  float secFrac = ti.tm_sec + (millis() % 1000) / 1000.0f;
  float sa = (secFrac * 6.0f - 90.0f) * DEG_TO_RAD;
  float ma = ((ti.tm_min + secFrac / 60.0f) * 6.0f - 90.0f) * DEG_TO_RAD;
  float ha = (((ti.tm_hour % 12) + ti.tm_min / 60.0f) * 30.0f - 90.0f) * DEG_TO_RAD;
  tft->drawLine(cx, cy, cx + cosf(ha) * 48, cy + sinf(ha) * 48, COL_WHITE);
  tft->drawLine(cx, cy, cx + cosf(ma) * 70, cy + sinf(ma) * 70, COL_CYAN);
  tft->drawLine(cx, cy, cx + cosf(sa) * 80, cy + sinf(sa) * 80, COL_AMBER);
  tft->fillCircle(cx, cy, 4, COL_AMBER);
  printCenteredText(tr("ĐỒNG HỒ KIM (#3)", "ANALOG CLOCK"), 24, 1, COL_CYAN, COL_BG);
  if (phoneBattPct >= 0) {
    printCenteredText("Phone: " + String(phoneBattPct) + (phoneBattCharging ? "%+" : "%"), 276, 1, COL_GREEN, COL_BG);
  }
}

// #25 Vietnamese Lunar Calendar + #26 Countdown + #33 Year Progress
inline void drawLunarYearView() {
  struct tm ti;
  if (!getLocalTime(&ti)) return;
  tft->fillScreen(COL_BG);
  printCenteredText(tr("LỊCH ÂM & NĂM 2026", "LUNAR & YEAR PROGRESS"), 18, 1, COL_CYAN, COL_BG);

  // Approximate Lunar day offset for quick glance + Year progress (#33)
  int dayOfYear = ti.tm_yday + 1;
  int yearPct = (dayOfYear * 100) / 365;
  tft->fillRoundRect(14, 48, 212, 68, 8, COL_CARD);
  tft->drawRoundRect(14, 48, 212, 68, 8, COL_AMBER);
  printCenteredText(tr("TIẾN ĐỘ NĂM (#33): ", "YEAR PROGRESS: ") + String(yearPct) + "%", 62, 1, COL_WHITE, COL_CARD);
  tft->fillRoundRect(26, 86, 188, 14, 4, COL_BG);
  tft->fillRoundRect(26, 86, (188 * yearPct) / 100, 14, 4, COL_AMBER);

  // #26 Countdowns
  tft->fillRoundRect(14, 128, 212, 82, 8, COL_CARD);
  printCenteredText(tr("ĐẾM NGƯỢC SỰ KIỆN (#26)", "EVENT COUNTDOWNS"), 140, 1, COL_CYAN, COL_CARD);
  int daysToNoel = max(0, 359 - dayOfYear);
  int daysToTet = max(0, 413 - dayOfYear); // 2027-02-17 Tet
  printCenteredText("Noel: " + String(daysToNoel) + tr(" ngày", "d") + " | Tet: " + String(daysToTet) + tr(" ngày", "d"), 168, 1, COL_GREEN, COL_CARD);

  // #25 Lunar indicator
  tft->fillRoundRect(14, 222, 212, 62, 8, COL_CARD);
  printCenteredText(tr("ÂM LỊCH VN (#25) • Bính Ngọ", "VN LUNAR (#25)"), 236, 1, COL_WHITE, COL_CARD);
  printCenteredText(String(ti.tm_mday) + "/" + String(ti.tm_mon + 1) + " DL", 258, 1, COL_MUTED, COL_CARD);
}

// #30 Stopwatch view
inline void drawStopwatchView() {
  tft->fillScreen(COL_BG);
  printCenteredText(tr("BẤM GIỜ THỂ THAO (#30)", "STOPWATCH (#30)"), 18, 1, COL_CYAN, COL_BG);
  unsigned long cur = swRunning ? (swElapsedMs + (millis() - swStartMs)) : swElapsedMs;
  unsigned long sec = cur / 1000;
  unsigned long ms = (cur % 1000) / 10;
  char buf[24];
  snprintf(buf, sizeof(buf), "%02lu:%02lu.%02lu", sec / 60, sec % 60, ms);
  printCenteredText(String(buf), 76, 3, swRunning ? COL_GREEN : COL_WHITE, COL_BG);
  for (int i = 0; i < swLapCount && i < 4; i++) {
    unsigned long ls = swLaps[i] / 1000;
    snprintf(buf, sizeof(buf), "Lap %d: %02lu:%02lu.%02lu", i + 1, ls / 60, ls % 60, (swLaps[i] % 1000) / 10);
    printCenteredText(String(buf), 140 + i * 24, 1, COL_AMBER, COL_BG);
  }
  printCenteredText("[OK]: Start/Stop  [NEXT]: Lap  [PREV]: Reset", 288, 1, COL_MUTED, COL_BG);
}

// #43 Emergency Restart Backend
inline void triggerEmergencyBackendRestart() {
  mqttClient.publish("deskflow/system/restart", "{\"source\":\"esp32\"}");
  tft->fillRoundRect(20, 120, 200, 60, 8, COL_RED);
  printCenteredText(tr("ĐÃ GỬI LỆNH RESTART VPS!", "VPS RESTART SENT!"), 144, 1, COL_WHITE, COL_RED);
  delay(1200);
}

// #317 Incoming Call Overlay
inline void drawIncomingCallOverlay() {
  if (millis() < incomingCallUntil && incomingCallCaller.length() > 0) {
    tft->fillRoundRect(16, 100, 208, 100, 10, COL_GREEN);
    tft->drawRoundRect(16, 100, 208, 100, 10, COL_WHITE);
    printCenteredText(tr("CUỘC GỌI ĐẾN (#317)", "INCOMING CALL"), 118, 1, COL_BG, COL_GREEN);
    printCenteredText(incomingCallCaller, 152, 2, COL_BG, COL_GREEN);
  }
}

#endif
