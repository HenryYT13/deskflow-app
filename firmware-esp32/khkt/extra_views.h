#ifndef EXTRA_VIEWS_H
#define EXTRA_VIEWS_H

#include <Arduino.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include "clock_styles.h"

extern Adafruit_ST7789* hwTft;
extern Adafruit_GFX* tft;
extern void printCenteredText(String text, int y, int size, uint16_t color, uint16_t bg);
extern void drawUtf8Text(int x, int y, const String& text, int size, uint16_t fg, uint16_t bg, int maxW);
extern void triggerBuzz(unsigned long durationMs, bool force);
extern const char* tr(const char* vi, const char* en);
extern String getFormattedRealTime(bool withAmPm);

// (#11) Quick Formulas & Periodic Table State
int formulaTab = 0; // 0 = Formulas, 1 = Periodic Table
int formulaIdx = 0;

inline void drawFormulasView(bool viewChanged) {
  static int lastTab = -1, lastIdx = -1;
  if (viewChanged) { lastTab = -1; lastIdx = -1; }
  if (!viewChanged && lastTab == formulaTab && lastIdx == formulaIdx) return;
  lastTab = formulaTab; lastIdx = formulaIdx;

  tft->fillRect(0, 26, 240, 268, COL_BG);
  printCenteredText(formulaTab == 0 ? tr("CÔNG THỨC NHANH", "QUICK FORMULAS") : tr("BẢNG TUẦN HOÀN", "PERIODIC TABLE"), 32, 1, COL_CYAN, COL_BG);

  tft->fillRoundRect(14, 52, 212, 206, 10, COL_CARD);
  tft->drawRoundRect(14, 52, 212, 206, 10, COL_BORDER);

  if (formulaTab == 0) {
    struct FItem { const char* subj; const char* title; const char* f1; const char* f2; };
    static const FItem ITEMS[8] = {
      {"Toán học", "Phương trình bậc 2", "delta = b^2 - 4ac", "x = (-b +- sqrt(d))/(2a)"},
      {"Toán học", "Hệ thức Vi-ét", "x1 + x2 = -b / a", "x1 * x2 = c / a"},
      {"Toán học", "Tam giác vuông", "a^2 + b^2 = c^2", "sin^2(a) + cos^2(a) = 1"},
      {"Vật lí", "Định luật Ôm & Công suất", "I = U / R   |   P = U * I", "Q = I^2 * R * t"},
      {"Vật lí", "Cơ năng & Động năng", "Wd = 1/2 m*v^2", "Wt = m*g*h  |  v = v0 + a*t"},
      {"Vật lí", "Thấu kính & Sóng", "1/f = 1/d + 1/d'", "lambda = v * T = v / f"},
      {"Hóa học", "Số mol & Nồng độ", "n = m/M = V(dkc)/24.79", "CM = n / V   |  C% = mct/mdd"},
      {"Hóa học", "Tỉ khối & Khí lí tưởng", "d(A/B) = M_A / M_B", "p * V = n * R * T"},
    };
    const FItem& it = ITEMS[formulaIdx % 8];
    tft->fillRoundRect(24, 64, 78, 18, 4, COL_CYAN);
    drawUtf8Text(30, 68, it.subj, 1, COL_BG, COL_CYAN, 70);
    drawUtf8Text(150, 68, String((formulaIdx % 8) + 1) + "/8", 1, COL_MUTED, COL_CARD, 60);
    drawUtf8Text(24, 94, it.title, 1, COL_AMBER, COL_CARD, 192);
    tft->drawFastHLine(24, 114, 192, COL_BORDER);
    printCenteredText(it.f1, 134, 1, COL_WHITE, COL_CARD);
    printCenteredText(it.f2, 166, 1, COL_GREEN, COL_CARD);
  } else {
    struct Elem { uint8_t z; const char* sym; const char* name; const char* mass; const char* val; };
    static const Elem ELS[12] = {
      {1, "H", "Hydro", "1.0", "Hoa tri: I"},
      {6, "C", "Cacbon", "12.0", "Hoa tri: II, IV"},
      {7, "N", "Nito", "14.0", "Hoa tri: III, V"},
      {8, "O", "Oxi", "16.0", "Hoa tri: II"},
      {11, "Na", "Natri", "23.0", "Hoa tri: I"},
      {12, "Mg", "Magie", "24.0", "Hoa tri: II"},
      {13, "Al", "Nhom", "27.0", "Hoa tri: III"},
      {16, "S", "Luu huynh", "32.0", "Hoa tri: II, IV, VI"},
      {17, "Cl", "Clo", "35.5", "Hoa tri: I"},
      {20, "Ca", "Canxi", "40.0", "Hoa tri: II"},
      {26, "Fe", "Sat", "56.0", "Hoa tri: II, III"},
      {29, "Cu", "Dong", "64.0", "Hoa tri: I, II"},
    };
    const Elem& e = ELS[formulaIdx % 12];
    drawUtf8Text(24, 66, "Z = " + String(e.z), 1, COL_MUTED, COL_CARD, 80);
    drawUtf8Text(155, 66, String((formulaIdx % 12) + 1) + "/12", 1, COL_MUTED, COL_CARD, 60);
    printCenteredText(e.sym, 96, 4, COL_AMBER, COL_CARD);
    printCenteredText(e.name, 152, 2, COL_WHITE, COL_CARD);
    printCenteredText(String("M = ") + e.mass + " g/mol  |  " + e.val, 192, 1, COL_GREEN, COL_CARD);
  }
  printCenteredText(tr("<< / >> Đổi mục  |  PLAY Đổi tab", "<< / >> Item  |  PLAY Tab"), 272, 1, COL_MUTED, COL_BG);
}

// (#51) Matrix Digital Rain Screensaver Clock
inline void drawMatrixRainView(bool viewChanged) {
  static int colY[15] = {0};
  static unsigned long lastFrame = 0;
  if (viewChanged) {
    tft->fillRect(0, 26, 240, 268, COL_BG);
    for (int i = 0; i < 15; i++) colY[i] = random(30, 260);
  }
  if (millis() - lastFrame < 80) return;
  lastFrame = millis();

  static const char HEX_POOL[] = "0123456789ABCDEF$*";
  tft->setTextSize(1);
  for (int i = 0; i < 15; i++) {
    int x = 8 + i * 15;
    int prevY = colY[i] - 36;
    if (prevY >= 28 && prevY < 280) {
      tft->fillRect(x, prevY, 8, 10, COL_BG);
    }
    colY[i] += 10;
    if (colY[i] > 280) colY[i] = 30;
    if (!(colY[i] >= 106 && colY[i] <= 178 && x >= 34 && x <= 198)) {
      tft->setTextColor(COL_GREEN, COL_BG);
      tft->setCursor(x, colY[i]);
      tft->print(HEX_POOL[random(sizeof(HEX_POOL) - 1)]);
    }
  }
  // Center floating clock box
  tft->fillRoundRect(34, 108, 172, 68, 8, COL_CARD);
  tft->drawRoundRect(34, 108, 172, 68, 8, COL_GREEN);
  printCenteredText(getFormattedRealTime(true), 124, 3, COL_GREEN, COL_CARD);
  printCenteredText("MATRIX NODE // LIVE", 156, 1, COL_MUTED, COL_CARD);
}

// (#55) Spotify Vinyl Spinning Disc Player View
extern struct ServerStats srvStats;
inline void drawSpotifyVinylView(bool viewChanged) {
  static float angleDeg = 0.0f;
  static unsigned long lastSpin = 0;
  static String lastTrack = "";
  static int lastPct = -1;

  if (viewChanged || srvStats.currentTrack != lastTrack) {
    lastTrack = srvStats.currentTrack;
    lastPct = -1;
    tft->fillRect(0, 26, 240, 268, COL_BG);
    // Vinyl base circle
    int cx = 120, cy = 122;
    tft->fillCircle(cx, cy, 62, 0x10A2);
    tft->drawCircle(cx, cy, 62, COL_BORDER);
    tft->drawCircle(cx, cy, 50, 0x2965);
    tft->drawCircle(cx, cy, 38, 0x2965);
    tft->fillCircle(cx, cy, 20, COL_AMBER);
    tft->fillCircle(cx, cy, 5, COL_BG);
    // Tonearm
    tft->drawLine(196, 62, 168, 114, COL_WHITE);
    tft->drawLine(197, 62, 169, 114, COL_WHITE);
    tft->fillCircle(196, 62, 6, COL_MUTED);
    // Track text
    drawUtf8Text(16, 198, srvStats.currentTrack, 1, COL_WHITE, COL_BG, 208);
    drawUtf8Text(16, 218, srvStats.currentArtist, 1, COL_MUTED, COL_BG, 208);
    printCenteredText(tr("<< Trước  |  PLAY  |  >> Tiếp", "<< Prev  |  PLAY  |  >> Next"), 272, 1, COL_MUTED, COL_BG);
  }

  if (millis() - lastSpin >= 110) {
    lastSpin = millis();
    int cx = 120, cy = 122;
    // Erase old marker dot, advance angle, draw glowing dot on LP groove
    int oldX = cx + (int)(cosf(angleDeg * 0.01745f) * 44);
    int oldY = cy + (int)(sinf(angleDeg * 0.01745f) * 44);
    tft->fillCircle(oldX, oldY, 3, 0x10A2);
    angleDeg = fmodf(angleDeg + 18.0f, 360.0f);
    int newX = cx + (int)(cosf(angleDeg * 0.01745f) * 44);
    int newY = cy + (int)(sinf(angleDeg * 0.01745f) * 44);
    tft->fillCircle(newX, newY, 3, COL_CYAN);
  }

  if (srvStats.progressPct != lastPct) {
    lastPct = srvStats.progressPct;
    tft->fillRoundRect(20, 244, 200, 8, 4, COL_CARD);
    int w = (200 * constrain(lastPct, 0, 100)) / 100;
    if (w > 4) tft->fillRoundRect(20, 244, w, 8, 4, COL_GREEN);
  }
}

// (#57) GitHub Contributions & CI Screen State
String ghUsername = "HenryYT13";
int ghRepos = 0;
int ghFollowers = 0;
String ghLatestRepo = "deskflow-app";
String ghCiStatus = "PASS";
bool ghDirty = true;

inline void drawGitHubView(bool viewChanged) {
  if (!viewChanged && !ghDirty) return;
  ghDirty = false;
  tft->fillRect(0, 26, 240, 268, COL_BG);
  printCenteredText("GITHUB // @" + ghUsername, 32, 1, COL_CYAN, COL_BG);

  tft->fillRoundRect(14, 52, 212, 82, 8, COL_CARD);
  tft->drawRoundRect(14, 52, 212, 82, 8, COL_BORDER);
  drawUtf8Text(24, 62, "Repos: " + String(ghRepos) + "  |  Followers: " + String(ghFollowers), 1, COL_WHITE, COL_CARD, 192);
  drawUtf8Text(24, 82, "Latest: " + ghLatestRepo, 1, COL_AMBER, COL_CARD, 192);

  uint16_t ciCol = (ghCiStatus == "PASS") ? COL_GREEN : ((ghCiStatus == "FAIL") ? 0xF800 : COL_AMBER);
  tft->fillRoundRect(24, 104, 108, 20, 4, ciCol);
  drawUtf8Text(30, 109, "CI: " + ghCiStatus, 1, COL_BG, ciCol, 96);

  // Activity Heatmap Grid (14 cols x 5 rows)
  tft->fillRoundRect(14, 144, 212, 104, 8, COL_CARD);
  tft->drawRoundRect(14, 144, 212, 104, 8, COL_BORDER);
  drawUtf8Text(24, 152, "Contributions", 1, COL_MUTED, COL_CARD, 120);
  uint16_t shades[4] = {0x1945, 0x03A0, 0x05E0, 0x07E0};
  int seedBase = ghRepos * 7 + ghFollowers * 3 + ghLatestRepo.length();
  for (int c = 0; c < 14; c++) {
    for (int r = 0; r < 5; r++) {
      int lv = (seedBase + c * 5 + r * 3) % 4;
      tft->fillRoundRect(24 + c * 14, 170 + r * 14, 11, 11, 2, shades[lv]);
    }
  }
  printCenteredText("Synced from GitHub API", 268, 1, COL_MUTED, COL_BG);
}

// (#75) Half-Res Offscreen Screenshot Capture (120x160 RGB565 RLE -> HTTPS Upload)
class HalfCaptureGFX : public Adafruit_GFX {
public:
  uint16_t* buf;
  HalfCaptureGFX(uint16_t* b) : Adafruit_GFX(240, 320), buf(b) {}
  void drawPixel(int16_t x, int16_t y, uint16_t color) override {
    int hx = x >> 1, hy = y >> 1;
    if (hx >= 0 && hx < 120 && hy >= 0 && hy < 160) buf[hy * 120 + hx] = color;
  }
  void fillRect(int16_t x, int16_t y, int16_t w, int16_t h, uint16_t color) override {
    int x0 = max(0, x >> 1), y0 = max(0, y >> 1);
    int x1 = min(119, (x + w - 1) >> 1), y1 = min(159, (y + h - 1) >> 1);
    for (int yy = y0; yy <= y1; yy++)
      for (int xx = x0; xx <= x1; xx++)
        buf[yy * 120 + xx] = color;
  }
  void drawFastHLine(int16_t x, int16_t y, int16_t w, uint16_t color) override { fillRect(x, y, w, 1, color); }
  void drawFastVLine(int16_t x, int16_t y, int16_t h, uint16_t color) override { fillRect(x, y, 1, h, color); }
  void fillScreen(uint16_t color) override {
    for (int i = 0; i < 120 * 160; i++) buf[i] = color;
  }
};

extern void drawPhysicalScreen();
extern String lastRenderedView;
extern void addLog(String tag, String msg);

inline void captureAndUploadScreenshot() {
  if (WiFi.status() != WL_CONNECTED || !hwTft) return;
  uint16_t* fb = (uint16_t*)calloc(120 * 160, sizeof(uint16_t));
  if (!fb) { addLog("SHOT", "Out of heap for capture"); return; }

  HalfCaptureGFX cap(fb);
  Adafruit_GFX* saved = tft;
  String savedLastView = lastRenderedView;
  tft = &cap;
  lastRenderedView = "";
  drawPhysicalScreen();
  tft = saved;
  lastRenderedView = savedLastView;

  // Simple RGB565 RLE stream [count_1_255, hi, lo]
  std::vector<uint8_t> rle;
  rle.reserve(8192);
  int total = 120 * 160;
  int i = 0;
  while (i < total) {
    uint16_t px = fb[i];
    uint8_t cnt = 1;
    while (i + cnt < total && cnt < 250 && fb[i + cnt] == px) cnt++;
    rle.push_back(cnt);
    rle.push_back((uint8_t)(px >> 8));
    rle.push_back((uint8_t)(px & 0xFF));
    i += cnt;
  }
  free(fb);

  WiFiClientSecure client;
  client.setInsecure();
  HTTPClient http;
  if (http.begin(client, "https://df.api.thetechdude.io.vn/api/app/station/screenshot?w=120&h=160")) {
    http.addHeader("Content-Type", "application/octet-stream");
    int code = http.POST(rle.data(), rle.size());
    addLog("SHOT", "Uploaded " + String(rle.size()) + "B screenshot -> HTTP " + String(code));
    http.end();
  }
}

#endif
