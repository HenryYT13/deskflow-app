#include "clock_styles.h"

// Global instances
ClockConfig clockCfg;
AnalogHandState analogState;
FlipDigitState flipState;
StyleTransitionState styleTransition;

static String lastDrawnTimeKey = "";
static String lastDrawnDateKey = "";

uint16_t clockHexToRGB565(const String& hex) {
  if (hex.length() < 7 || hex[0] != '#') return COL_CYAN;
  long number = strtol(hex.c_str() + 1, NULL, 16);
  uint8_t r = (number >> 16) & 0xFF;
  uint8_t g = (number >> 8) & 0xFF;
  uint8_t b = number & 0xFF;
  return ((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >> 3);
}

String getDayOfWeekName(int wday, const String& lang) {
  if (lang == "en") {
    const char* days[] = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    return (wday >= 0 && wday <= 6) ? days[wday] : "Day";
  } else {
    const char* days[] = {"Chu Nhat", "Thu 2", "Thu 3", "Thu 4", "Thu 5", "Thu 6", "Thu 7"};
    return (wday >= 0 && wday <= 6) ? days[wday] : "Thu";
  }
}

String getFormattedClockTime(const struct tm& t, bool withSeconds, const String& fmt, String& outAmPm) {
  int h = t.tm_hour;
  int m = t.tm_min;
  int s = t.tm_sec;
  outAmPm = "";

  if (fmt == "12h") {
    outAmPm = (h >= 12) ? "PM" : "AM";
    h = h % 12;
    if (h == 0) h = 12;
  }

  char buf[16];
  if (withSeconds) {
    snprintf(buf, sizeof(buf), "%02d:%02d:%02d", h, m, s);
  } else {
    snprintf(buf, sizeof(buf), "%02d:%02d", h, m);
  }
  return String(buf);
}

String getFormattedClockDate(const struct tm& t, const String& lang) {
  String dayName = getDayOfWeekName(t.tm_wday, lang);
  char buf[24];
  if (lang == "en") {
    const char* mons[] = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    const char* mStr = (t.tm_mon >= 0 && t.tm_mon <= 11) ? mons[t.tm_mon] : "Jan";
    snprintf(buf, sizeof(buf), "%s, %s %02d", dayName.c_str(), mStr, t.tm_mday);
  } else {
    snprintf(buf, sizeof(buf), "%s, %02d/%02d", dayName.c_str(), t.tm_mday, t.tm_mon + 1);
  }
  return String(buf);
}

void loadClockConfigNVS() {
  clockCfg.style = prefs.getString("clk_style", STYLE_DIGITAL_CLASSIC);
  clockCfg.timeFormat = prefs.getString("clk_fmt", "24h");
  clockCfg.showSeconds = prefs.getBool("clk_sec", true);
  clockCfg.showDate = prefs.getBool("clk_date", true);
  clockCfg.showAmPm = prefs.getBool("clk_ampm", false);
  clockCfg.language = prefs.getString("clk_lang", "vi");
  clockCfg.animationEnabled = prefs.getBool("clk_anim", true);
  clockCfg.animationSpeed = prefs.getFloat("clk_spd", 1.0f);
  clockCfg.accentHex = prefs.getString("clk_col", "#38BDF8");
  clockCfg.configVersion = prefs.getInt("clk_ver", 1);
  clockCfg.accentColor = clockHexToRGB565(clockCfg.accentHex);

  addLog("CLOCK", "NVS Loaded: Style=" + clockCfg.style + " Fmt=" + clockCfg.timeFormat + " Ver=" + String(clockCfg.configVersion));
}

void saveClockConfigNVS() {
  prefs.putString("clk_style", clockCfg.style);
  prefs.putString("clk_fmt", clockCfg.timeFormat);
  prefs.putBool("clk_sec", clockCfg.showSeconds);
  prefs.putBool("clk_date", clockCfg.showDate);
  prefs.putBool("clk_ampm", clockCfg.showAmPm);
  prefs.putString("clk_lang", clockCfg.language);
  prefs.putBool("clk_anim", clockCfg.animationEnabled);
  prefs.putFloat("clk_spd", clockCfg.animationSpeed);
  prefs.putString("clk_col", clockCfg.accentHex);
  prefs.putInt("clk_ver", clockCfg.configVersion);

  addLog("CLOCK", "NVS Saved: Style=" + clockCfg.style + " Ver=" + String(clockCfg.configVersion));
}

void sendClockStyleAck(const String& status, const String& err) {
  DynamicJsonDocument doc(384);
  doc["device_id"] = "DeskFlow-ESP32";
  doc["config_version"] = clockCfg.configVersion;
  doc["status"] = status;
  doc["style"] = clockCfg.style;
  doc["time_format"] = clockCfg.timeFormat;
  doc["show_seconds"] = clockCfg.showSeconds;
  doc["show_date"] = clockCfg.showDate;
  doc["show_ampm"] = clockCfg.showAmPm;
  doc["language"] = clockCfg.language;
  doc["animation_enabled"] = clockCfg.animationEnabled;
  doc["animation_speed"] = clockCfg.animationSpeed;
  doc["accent_color"] = clockCfg.accentHex;
  
  if (err.length() > 0) {
    doc["error"] = err;
  } else {
    doc["error"] = nullptr;
  }

  String out;
  serializeJson(doc, out);
  mqtt.publish("deskflow/clock_style_ack", out.c_str());
  addLog("CLOCK_ACK", out);
}

void sendClockStyleRequest() {
  DynamicJsonDocument doc(256);
  doc["device_id"] = "DeskFlow-ESP32";
  doc["current_version"] = clockCfg.configVersion;
  doc["current_style"] = clockCfg.style;
  String out;
  serializeJson(doc, out);
  mqtt.publish("deskflow/clock_style_request", out.c_str());
  addLog("CLOCK_REQ", "Yeu cau cau hinh dong ho moi nhat: " + out);
}

void handleClockConfigPayload(DynamicJsonDocument& doc) {
  int newVer = doc["config_version"] | clockCfg.configVersion;
  String reqStyle = doc["style"] | clockCfg.style;
  String errReason = "";

  // Validate style
  bool validStyle = (reqStyle == STYLE_DIGITAL_CLASSIC ||
                     reqStyle == STYLE_DIGITAL_MINIMAL ||
                     reqStyle == STYLE_DIGITAL_NEON ||
                     reqStyle == STYLE_ANALOG ||
                     reqStyle == STYLE_FLIP ||
                     reqStyle == STYLE_BINARY ||
                     reqStyle == STYLE_WORD);

  if (!validStyle) {
    errReason = "Invalid style '" + reqStyle + "', falling back to " + STYLE_DIGITAL_CLASSIC;
    reqStyle = STYLE_DIGITAL_CLASSIC;
  }

  // Trigger smooth transition animation if style changed
  if (reqStyle != clockCfg.style && clockCfg.animationEnabled) {
    styleTransition.inTransition = true;
    styleTransition.startTime = millis();
    styleTransition.step = 0;
    styleTransition.oldStyle = clockCfg.style;
    styleTransition.targetStyle = reqStyle;
  }

  clockCfg.style = reqStyle;
  clockCfg.configVersion = newVer;

  if (doc.containsKey("time_format")) clockCfg.timeFormat = doc["time_format"].as<String>();
  if (doc.containsKey("show_seconds")) clockCfg.showSeconds = doc["show_seconds"].as<bool>();
  if (doc.containsKey("show_date")) clockCfg.showDate = doc["show_date"].as<bool>();
  if (doc.containsKey("show_ampm")) clockCfg.showAmPm = doc["show_ampm"].as<bool>();
  if (doc.containsKey("language")) clockCfg.language = doc["language"].as<String>();
  if (doc.containsKey("animation_enabled")) clockCfg.animationEnabled = doc["animation_enabled"].as<bool>();
  if (doc.containsKey("animation_speed")) clockCfg.animationSpeed = doc["animation_speed"].as<float>();
  if (doc.containsKey("accent_color")) {
    clockCfg.accentHex = doc["accent_color"].as<String>();
    clockCfg.accentColor = clockHexToRGB565(clockCfg.accentHex);
  }

  // Reset internal states on configuration update
  analogState.reset();
  flipState.reset();
  lastDrawnTimeKey = "";
  lastDrawnDateKey = "";

  saveClockConfigNVS();

  if (errReason.length() > 0) {
    sendClockStyleAck("error", errReason);
  } else {
    sendClockStyleAck("applied", "");
  }
}

bool isClockAnimationActive() {
  if (!clockCfg.animationEnabled) return false;
  if (styleTransition.inTransition) return true;
  if (clockCfg.style == STYLE_FLIP) {
    for (int i = 0; i < 6; i++) {
      if (flipState.flipProgress[i] > 0) return true;
    }
  }
  return false;
}

void updateClockAnimations() {
  if (!clockCfg.animationEnabled) return;

  // 1. Style Transition Animation (Fade/Wipe effect)
  if (styleTransition.inTransition) {
    unsigned long elapsed = millis() - styleTransition.startTime;
    unsigned long dur = (unsigned long)(180.0f / clockCfg.animationSpeed);
    if (elapsed >= dur) {
      styleTransition.inTransition = false;
      lastDrawnTimeKey = "";
      lastDrawnDateKey = "";
    }
  }

  // 2. Flip digit animation progression
  if (clockCfg.style == STYLE_FLIP) {
    unsigned long now = millis();
    unsigned long frameInterval = (unsigned long)(40.0f / clockCfg.animationSpeed);
    for (int i = 0; i < 6; i++) {
      if (flipState.flipProgress[i] > 0) {
        if (now - flipState.lastAnimTime[i] >= frameInterval) {
          flipState.lastAnimTime[i] = now;
          flipState.flipProgress[i]++;
          if (flipState.flipProgress[i] > 3) {
            flipState.flipProgress[i] = 0; // Completed
          }
        }
      }
    }
  }
}

// ================= RENDER IMPLEMENTATIONS =================

void drawClockArea(int x, int y, int w, int h, bool fullScreen) {
  if (!tft) return;

  struct tm timeinfo;
  if (!getLocalTime(&timeinfo, 20)) {
    // Fallback if NTP not synchronized
    timeinfo.tm_hour = 12;
    timeinfo.tm_min = 0;
    timeinfo.tm_sec = 0;
    timeinfo.tm_mday = 1;
    timeinfo.tm_mon = 0;
    timeinfo.tm_wday = 1;
  }

  updateClockAnimations();

  // If in transition, draw smooth transition wipe
  if (styleTransition.inTransition) {
    tft->fillRect(x, y, w, h, COL_BG);
    printCenteredText("~ DeskFlow Clock ~", y + h / 2 - 6, 1, clockCfg.accentColor, COL_BG);
    return;
  }

  if (clockCfg.style == STYLE_DIGITAL_MINIMAL) {
    drawDigitalMinimal(x, y, w, h, timeinfo, fullScreen);
  } else if (clockCfg.style == STYLE_DIGITAL_NEON) {
    drawDigitalNeon(x, y, w, h, timeinfo, fullScreen);
  } else if (clockCfg.style == STYLE_ANALOG) {
    drawAnalogClock(x, y, w, h, timeinfo, fullScreen);
  } else if (clockCfg.style == STYLE_FLIP) {
    drawFlipClock(x, y, w, h, timeinfo, fullScreen);
  } else if (clockCfg.style == STYLE_BINARY) {
    drawBinaryClock(x, y, w, h, timeinfo, fullScreen);
  } else if (clockCfg.style == STYLE_WORD) {
    drawWordClock(x, y, w, h, timeinfo, fullScreen);
  } else {
    // Default: digital_classic
    drawDigitalClassic(x, y, w, h, timeinfo, fullScreen);
  }
}

// 1. Digital Classic
void drawDigitalClassic(int x, int y, int w, int h, const struct tm& t, bool fullScreen) {
  String ampm = "";
  String curT = getFormattedClockTime(t, false, clockCfg.timeFormat, ampm);
  String dateStr = getFormattedClockDate(t, clockCfg.language);
  bool showAmPm = (clockCfg.timeFormat == "12h" || clockCfg.showAmPm) && (ampm.length() > 0);
  String key = curT + "_" + ampm + "_" + String(t.tm_sec) + "_" + dateStr + "_" + (fullScreen ? "F" : "B");

  if (key == lastDrawnTimeKey) return;

  tft->fillRect(x, y, w, h, COL_BG);

  if (fullScreen) {
    int textSize = 5; // 5 * 30px = 150px
    int timeX = 22;
    int timeY = y + 70;

    tft->setTextSize(textSize);
    tft->setTextColor(clockCfg.accentColor, COL_BG);
    tft->setCursor(timeX, timeY);
    tft->print(curT);

    // Indicators: AM/PM and Seconds placed neatly at subX = 176
    int subX = 176;
    if (showAmPm && clockCfg.showSeconds) {
      // Both: AM/PM on top, Seconds on bottom
      tft->setTextSize(2);
      tft->setTextColor(COL_AMBER, COL_BG);
      tft->setCursor(subX, timeY + 2);
      tft->print(ampm);

      char sBuf[6];
      snprintf(sBuf, sizeof(sBuf), ":%02d", t.tm_sec);
      tft->setTextSize(2);
      tft->setTextColor(COL_MUTED, COL_BG);
      tft->setCursor(subX, timeY + 22);
      tft->print(sBuf);
    } else if (showAmPm) {
      tft->setTextSize(2);
      tft->setTextColor(COL_AMBER, COL_BG);
      tft->setCursor(subX, timeY + 12);
      tft->print(ampm);
    } else if (clockCfg.showSeconds) {
      char sBuf[6];
      snprintf(sBuf, sizeof(sBuf), ":%02d", t.tm_sec);
      tft->setTextSize(2);
      tft->setTextColor(COL_MUTED, COL_BG);
      tft->setCursor(subX, timeY + 12);
      tft->print(sBuf);
    }

    if (clockCfg.showDate) {
      printCenteredText(dateStr, timeY + 56, 2, COL_WHITE, COL_BG);
    }
  } else {
    // Standard banner view (e.g. h=68)
    int textSize = 4; // 5 * 24px = 120px
    int timeX = 14;
    int timeY = clockCfg.showDate ? y + 6 : y + 14;

    tft->setTextSize(textSize);
    tft->setTextColor(clockCfg.accentColor, COL_BG);
    tft->setCursor(timeX, timeY);
    tft->print(curT);

    int subX = timeX + 124;
    if (showAmPm && clockCfg.showSeconds) {
      tft->setTextSize(1);
      tft->setTextColor(COL_AMBER, COL_BG);
      tft->setCursor(subX, timeY + 2);
      tft->print(ampm);

      char sBuf[6];
      snprintf(sBuf, sizeof(sBuf), ":%02d", t.tm_sec);
      tft->setTextSize(1);
      tft->setTextColor(COL_MUTED, COL_BG);
      tft->setCursor(subX, timeY + 18);
      tft->print(sBuf);
    } else if (showAmPm) {
      tft->setTextSize(2);
      tft->setTextColor(COL_AMBER, COL_BG);
      tft->setCursor(subX, timeY + 6);
      tft->print(ampm);
    } else if (clockCfg.showSeconds) {
      char sBuf[6];
      snprintf(sBuf, sizeof(sBuf), ":%02d", t.tm_sec);
      tft->setTextSize(2);
      tft->setTextColor(COL_MUTED, COL_BG);
      tft->setCursor(subX, timeY + 6);
      tft->print(sBuf);
    }

    if (clockCfg.showDate) {
      printCenteredText(dateStr, y + 46, 1, COL_MUTED, COL_BG);
    }
  }

  lastDrawnTimeKey = key;
}

// 2. Digital Minimal
void drawDigitalMinimal(int x, int y, int w, int h, const struct tm& t, bool fullScreen) {
  String ampm = "";
  String curT = getFormattedClockTime(t, clockCfg.showSeconds, clockCfg.timeFormat, ampm);
  bool showAmPm = (clockCfg.timeFormat == "12h" || clockCfg.showAmPm) && (ampm.length() > 0);
  String dateStr = getFormattedClockDate(t, clockCfg.language);
  String key = curT + "_" + ampm + "_" + dateStr + "_" + (fullScreen ? "F" : "B");

  if (key == lastDrawnTimeKey) return;

  tft->fillRect(x, y, w, h, COL_BG);

  if (fullScreen) {
    int timeY = y + 70;
    int size = clockCfg.showSeconds ? 4 : 5;

    int textW = curT.length() * 6 * size;
    int timeX = (w - textW) / 2;
    if (timeX < 4) timeX = 4;

    tft->setTextSize(size);
    tft->setTextColor(COL_WHITE, COL_BG);
    tft->setCursor(timeX, timeY);
    tft->print(curT);

    if (showAmPm) {
      tft->setTextSize(2);
      tft->setTextColor(COL_AMBER, COL_BG);
      tft->setCursor(timeX + textW + 4, timeY + (size * 8 - 16) / 2);
      tft->print(ampm);
    }

    if (clockCfg.showDate) {
      int dateY = timeY + (size * 8) + 18;
      printCenteredText(dateStr, dateY, 2, COL_MUTED, COL_BG);
    }
  } else {
    int size = clockCfg.showSeconds ? 3 : 4;
    int textW = curT.length() * 6 * size;
    int timeX = (w - textW) / 2;
    if (timeX < 4) timeX = 4;
    int timeY = clockCfg.showDate ? y + 8 : y + 16;

    tft->setTextSize(size);
    tft->setTextColor(COL_WHITE, COL_BG);
    tft->setCursor(timeX, timeY);
    tft->print(curT);

    if (showAmPm) {
      tft->setTextSize(1);
      tft->setTextColor(COL_AMBER, COL_BG);
      tft->setCursor(timeX + textW + 4, timeY + 4);
      tft->print(ampm);
    }

    if (clockCfg.showDate) {
      printCenteredText(dateStr, y + 46, 1, COL_MUTED, COL_BG);
    }
  }

  lastDrawnTimeKey = key;
}

// 3. Digital Neon
void drawDigitalNeon(int x, int y, int w, int h, const struct tm& t, bool fullScreen) {
  String ampm = "";
  String curT = getFormattedClockTime(t, false, clockCfg.timeFormat, ampm);
  bool showAmPm = (clockCfg.timeFormat == "12h" || clockCfg.showAmPm) && (ampm.length() > 0);
  String dateStr = getFormattedClockDate(t, clockCfg.language);
  String key = curT + "_" + ampm + "_" + String(t.tm_sec) + "_" + dateStr + "_" + (fullScreen ? "F" : "B");

  if (key == lastDrawnTimeKey) return;

  tft->fillRect(x, y, w, h, COL_BG);

  int boxX = x + 8;
  int boxY = y + 4;
  int boxW = w - 16;
  int boxH = h - 8;

  // Dual glowing neon outline
  uint16_t glowDim = (clockCfg.accentColor >> 1) & 0x7BEF;
  tft->drawRoundRect(boxX, boxY, boxW, boxH, 8, glowDim);
  tft->drawRoundRect(boxX + 1, boxY + 1, boxW - 2, boxH - 2, 7, clockCfg.accentColor);

  if (fullScreen) {
    int timeX = boxX + 20;
    int timeY = boxY + 50;

    tft->setTextSize(5);
    tft->setTextColor(clockCfg.accentColor, COL_BG);
    tft->setCursor(timeX, timeY);
    tft->print(curT);

    int subX = timeX + 154;
    if (showAmPm && clockCfg.showSeconds) {
      tft->setTextSize(2);
      tft->setTextColor(COL_AMBER, COL_BG);
      tft->setCursor(subX, timeY + 2);
      tft->print(ampm);

      char sBuf[6];
      snprintf(sBuf, sizeof(sBuf), ":%02d", t.tm_sec);
      tft->setTextSize(2);
      tft->setTextColor(COL_NEON_MAGENTA, COL_BG);
      tft->setCursor(subX, timeY + 22);
      tft->print(sBuf);
    } else if (showAmPm) {
      tft->setTextSize(2);
      tft->setTextColor(COL_AMBER, COL_BG);
      tft->setCursor(subX, timeY + 12);
      tft->print(ampm);
    } else if (clockCfg.showSeconds) {
      char sBuf[6];
      snprintf(sBuf, sizeof(sBuf), ":%02d", t.tm_sec);
      tft->setTextSize(2);
      tft->setTextColor(COL_NEON_MAGENTA, COL_BG);
      tft->setCursor(subX, timeY + 12);
      tft->print(sBuf);
    }

    if (clockCfg.showDate) {
      printCenteredText(dateStr, boxY + boxH - 32, 2, COL_WHITE, COL_BG);
    }
  } else {
    int timeX = boxX + 12;
    int timeY = clockCfg.showDate ? boxY + 6 : boxY + 12;

    tft->setTextSize(4);
    tft->setTextColor(clockCfg.accentColor, COL_BG);
    tft->setCursor(timeX, timeY);
    tft->print(curT);

    int subX = timeX + 124;
    if (showAmPm && clockCfg.showSeconds) {
      tft->setTextSize(1);
      tft->setTextColor(COL_AMBER, COL_BG);
      tft->setCursor(subX, timeY + 2);
      tft->print(ampm);

      char sBuf[6];
      snprintf(sBuf, sizeof(sBuf), ":%02d", t.tm_sec);
      tft->setTextSize(1);
      tft->setTextColor(COL_NEON_MAGENTA, COL_BG);
      tft->setCursor(subX, timeY + 16);
      tft->print(sBuf);
    } else if (showAmPm) {
      tft->setTextSize(2);
      tft->setTextColor(COL_AMBER, COL_BG);
      tft->setCursor(subX, timeY + 6);
      tft->print(ampm);
    } else if (clockCfg.showSeconds) {
      char sBuf[6];
      snprintf(sBuf, sizeof(sBuf), ":%02d", t.tm_sec);
      tft->setTextSize(2);
      tft->setTextColor(COL_NEON_MAGENTA, COL_BG);
      tft->setCursor(subX, timeY + 6);
      tft->print(sBuf);
    }

    if (clockCfg.showDate) {
      printCenteredText(dateStr, boxY + boxH - 14, 1, COL_MUTED, COL_BG);
    }
  }

  lastDrawnTimeKey = key;
}

// 4. Analog Clock (Anti-flicker Differential Hand Drawing + Date Support)
void drawAnalogClock(int x, int y, int w, int h, const struct tm& t, bool fullScreen) {
  int cx = x + w / 2;
  int cy = fullScreen ? (y + h / 2 - (clockCfg.showDate ? 16 : 0)) : (y + h / 2);
  int r = fullScreen ? 85 : min(w, h) / 2 - 4;

  static int lastR = -1;
  static int lastCx = -1, lastCy = -1;
  static String lastAnalogDate = "";

  String dateStr = getFormattedClockDate(t, clockCfg.language);

  // Redraw clock dial face only if geometry shifted or view invalidated
  if (lastR != r || lastCx != cx || lastCy != cy || analogState.lastHx == -1) {
    tft->fillRect(x, y, w, h, COL_BG);
    tft->drawCircle(cx, cy, r, COL_BORDER);
    tft->drawCircle(cx, cy, r - 1, COL_BORDER);

    // 12 hour tick marks
    for (int i = 0; i < 12; i++) {
      float a = i * 30.0f * 0.0174533f;
      int x1 = cx + (int)(cosf(a) * (r - (i % 3 == 0 ? 8 : 4)));
      int y1 = cy + (int)(sinf(a) * (r - (i % 3 == 0 ? 8 : 4)));
      int x2 = cx + (int)(cosf(a) * (r - 1));
      int y2 = cy + (int)(sinf(a) * (r - 1));
      tft->drawLine(x1, y1, x2, y2, i % 3 == 0 ? clockCfg.accentColor : COL_MUTED);
    }
    tft->fillCircle(cx, cy, 3, COL_WHITE);
    lastR = r;
    lastCx = cx;
    lastCy = cy;
    lastAnalogDate = "";
  }

  // Erase previous hands with COL_BG
  if (analogState.lastHx >= 0) tft->drawLine(cx, cy, analogState.lastHx, analogState.lastHy, COL_BG);
  if (analogState.lastMx >= 0) tft->drawLine(cx, cy, analogState.lastMx, analogState.lastMy, COL_BG);
  if (analogState.lastSx >= 0) tft->drawLine(cx, cy, analogState.lastSx, analogState.lastSy, COL_BG);

  // Compute new hand coordinates
  float hAngle = (t.tm_hour % 12 + t.tm_min / 60.0f) * 30.0f - 90.0f;
  float mAngle = (t.tm_min + t.tm_sec / 60.0f) * 6.0f - 90.0f;
  float sAngle = t.tm_sec * 6.0f - 90.0f;

  float hRad = hAngle * 0.0174533f;
  float mRad = mAngle * 0.0174533f;
  float sRad = sAngle * 0.0174533f;

  int hLen = (int)(r * 0.52f);
  int mLen = (int)(r * 0.76f);
  int sLen = (int)(r * 0.88f);

  int hx = cx + (int)(cosf(hRad) * hLen);
  int hy = cy + (int)(sinf(hRad) * hLen);
  int mx = cx + (int)(cosf(mRad) * mLen);
  int my = cy + (int)(sinf(mRad) * mLen);
  int sx = cx + (int)(cosf(sRad) * sLen);
  int sy = cy + (int)(sinf(sRad) * sLen);

  // Draw new hands
  tft->drawLine(cx, cy, hx, hy, COL_WHITE);
  tft->drawLine(cx, cy, mx, my, clockCfg.accentColor);
  if (clockCfg.showSeconds) {
    tft->drawLine(cx, cy, sx, sy, COL_AMBER);
  }

  // Center pivot pin
  tft->fillCircle(cx, cy, 2, COL_WHITE);

  // Date display
  if (clockCfg.showDate && dateStr != lastAnalogDate) {
    if (fullScreen) {
      tft->fillRect(x, cy + r + 8, w, 24, COL_BG);
      printCenteredText(dateStr, cy + r + 12, 2, COL_WHITE, COL_BG);
    } else {
      // In banner view, display day number inside dial
      tft->fillRect(cx - 14, cy + 8, 28, 12, COL_CARD);
      tft->drawRect(cx - 14, cy + 8, 28, 12, COL_BORDER);
      char dBuf[4];
      snprintf(dBuf, sizeof(dBuf), "%02d", t.tm_mday);
      tft->setTextSize(1);
      tft->setTextColor(COL_WHITE, COL_CARD);
      tft->setCursor(cx - 5, cy + 10);
      tft->print(dBuf);
    }
    lastAnalogDate = dateStr;
  }

  // Cache last positions
  analogState.lastHx = hx; analogState.lastHy = hy;
  analogState.lastMx = mx; analogState.lastMy = my;
  analogState.lastSx = clockCfg.showSeconds ? sx : -1;
  analogState.lastSy = clockCfg.showSeconds ? sy : -1;
}

// 5. Flip Clock (2D Card Split-Flap Representation with AM/PM and Date Support)
void drawFlipClock(int x, int y, int w, int h, const struct tm& t, bool fullScreen) {
  char digits[7];
  String ampm = "";
  int hr = t.tm_hour;
  bool showAmPm = (clockCfg.timeFormat == "12h" || clockCfg.showAmPm);
  if (clockCfg.timeFormat == "12h") {
    ampm = (hr >= 12) ? "PM" : "AM";
    hr = hr % 12;
    if (hr == 0) hr = 12;
  }

  if (clockCfg.showSeconds) {
    snprintf(digits, sizeof(digits), "%02d%02d%02d", hr, t.tm_min, t.tm_sec);
  } else {
    snprintf(digits, sizeof(digits), "%02d%02d  ", hr, t.tm_min);
  }

  String dateStr = getFormattedClockDate(t, clockCfg.language);
  static int lastDigitCount = -1;
  static bool lastWasFullScreen = false;

  int digitCount = clockCfg.showSeconds ? 6 : 4;

  // Clear area on configuration change or view change to avoid phantom cards/colons
  if (digitCount != lastDigitCount || fullScreen != lastWasFullScreen || lastDrawnTimeKey == "") {
    tft->fillRect(x, y, w, h, COL_BG);
    lastDigitCount = digitCount;
    lastWasFullScreen = fullScreen;
    flipState.reset();
  }

  int cardW = fullScreen ? (clockCfg.showSeconds ? 28 : 42) : (clockCfg.showSeconds ? 22 : 32);
  int cardH = fullScreen ? 54 : 36;
  int gap = fullScreen ? 4 : 3;
  int colonW = fullScreen ? 8 : 6;
  int totalW = digitCount * cardW + (digitCount / 2 - 1) * colonW + (digitCount - 1) * gap;
  int startX = x + (w - totalW) / 2;
  int startY = fullScreen ? (y + (clockCfg.showDate ? 60 : 80)) : (clockCfg.showDate ? y + 6 : y + (h - cardH) / 2);

  // Draw cards
  for (int i = 0; i < digitCount; i++) {
    int dx = startX + i * (cardW + gap) + (i >= 2 ? colonW : 0) + (i >= 4 ? colonW : 0);

    // Colon separator (Draw each time to ensure visibility)
    if (i == 2 || i == 4) {
      int colX = dx - colonW + 1;
      tft->fillCircle(colX + colonW / 2, startY + cardH / 3, fullScreen ? 2 : 1, COL_AMBER);
      tft->fillCircle(colX + colonW / 2, startY + (cardH * 2) / 3, fullScreen ? 2 : 1, COL_AMBER);
    }

    char curChar = digits[i];
    if (curChar != flipState.lastDigit[i]) {
      if (clockCfg.animationEnabled) {
        flipState.flipProgress[i] = 1;
        flipState.lastAnimTime[i] = millis();
      }
      flipState.lastDigit[i] = curChar;

      // Redraw card background & borders
      tft->fillRoundRect(dx, startY, cardW, cardH, 4, COL_CARD);
      tft->drawRoundRect(dx, startY, cardW, cardH, 4, COL_BORDER);

      // Split flap center cut line
      int midY = startY + cardH / 2;
      tft->drawFastHLine(dx, midY, cardW, COL_BG);

      // Render character
      tft->setTextSize(fullScreen ? (clockCfg.showSeconds ? 3 : 4) : 2);
      tft->setTextColor(COL_WHITE, COL_CARD);
      int charSize = fullScreen ? (clockCfg.showSeconds ? 3 : 4) : 2;
      int charW = 6 * charSize;
      int charH = 8 * charSize;
      int charX = dx + (cardW - charW) / 2;
      int charY = startY + (cardH - charH) / 2;
      tft->setCursor(charX, charY);
      tft->print(curChar);
    }

    // Animation visual shade
    int midY = startY + cardH / 2;
    if (flipState.flipProgress[i] == 1) {
      tft->drawFastHLine(dx + 2, midY - 2, cardW - 4, COL_BORDER);
    } else if (flipState.flipProgress[i] == 2) {
      tft->drawFastHLine(dx + 2, midY + 2, cardW - 4, COL_BORDER);
    }
  }

  // AM/PM indicator for Flip Clock
  if (showAmPm && ampm.length() > 0) {
    if (fullScreen) {
      tft->setTextSize(2);
      tft->setTextColor(COL_AMBER, COL_BG);
      tft->setCursor(startX + totalW - 28, startY - 20);
      tft->print(ampm);
    } else {
      tft->setTextSize(1);
      tft->setTextColor(COL_AMBER, COL_BG);
      tft->setCursor(startX + totalW + 3, startY + 4);
      tft->print(ampm);
    }
  }

  // Date display below flip cards
  if (clockCfg.showDate) {
    if (fullScreen) {
      printCenteredText(dateStr, startY + cardH + 20, 2, COL_WHITE, COL_BG);
    } else {
      printCenteredText(dateStr, y + 48, 1, COL_MUTED, COL_BG);
    }
  }

  lastDrawnTimeKey = String(digits) + "_" + dateStr;
}

// 6. Binary Clock (BCD - Hours, Minutes, Seconds + Date Support)
void drawBinaryClock(int x, int y, int w, int h, const struct tm& t, bool fullScreen) {
  String ampm = "";
  int hr = t.tm_hour;
  bool showAmPm = (clockCfg.timeFormat == "12h" || clockCfg.showAmPm);
  if (clockCfg.timeFormat == "12h") {
    ampm = (hr >= 12) ? "PM" : "AM";
    hr = hr % 12;
    if (hr == 0) hr = 12;
  }

  int vals[6] = {
    hr / 10, hr % 10,
    t.tm_min / 10, t.tm_min % 10,
    t.tm_sec / 10, t.tm_sec % 10
  };

  int colCount = clockCfg.showSeconds ? 6 : 4;
  int radius = fullScreen ? 6 : 3;
  int stepX = fullScreen ? 28 : (w - 40) / colCount;
  int stepY = fullScreen ? 20 : (h - 22) / 4;
  int startX = fullScreen ? x + 38 : x + 24;
  int startY = fullScreen ? (y + (clockCfg.showDate ? 40 : 60)) : (y + (clockCfg.showDate ? 4 : 8));

  String dateStr = getFormattedClockDate(t, clockCfg.language);
  String key = String(hr) + String(t.tm_min) + String(t.tm_sec) + "_" + dateStr + "_" + (fullScreen ? "F" : "B");
  if (key == lastDrawnTimeKey) return;

  // Clean background area
  tft->fillRect(x, y, w, h, COL_BG);

  // Bit value legend (8, 4, 2, 1) on the left
  const char* leg[] = {"8", "4", "2", "1"};
  for (int r = 0; r < 4; r++) {
    tft->setTextSize(1);
    tft->setTextColor(COL_MUTED, COL_BG);
    tft->setCursor(fullScreen ? x + 16 : x + 8, startY + r * stepY - 3);
    tft->print(leg[r]);
  }

  // Draw binary indicator circles
  for (int c = 0; c < colCount; c++) {
    int val = vals[c];
    for (int row = 0; row < 4; row++) {
      int bitVal = 1 << (3 - row); // 8, 4, 2, 1
      bool active = (val & bitVal) != 0;
      int cx = startX + c * stepX;
      int cy = startY + row * stepY;

      if (active) {
        tft->fillCircle(cx, cy, radius, clockCfg.accentColor);
      } else {
        tft->drawCircle(cx, cy, radius, COL_BORDER);
      }
    }
  }

  // Column labels H, M, S
  tft->setTextSize(1);
  tft->setTextColor(COL_AMBER, COL_BG);
  tft->setCursor(startX + stepX / 2 - 4, startY + 4 * stepY + 2);
  tft->print("H");
  tft->setCursor(startX + 2 * stepX + stepX / 2 - 4, startY + 4 * stepY + 2);
  tft->print("M");
  if (clockCfg.showSeconds) {
    tft->setCursor(startX + 4 * stepX + stepX / 2 - 4, startY + 4 * stepY + 2);
    tft->print("S");
  }

  // AM/PM badge
  if (showAmPm && ampm.length() > 0) {
    tft->setTextSize(1);
    tft->setTextColor(COL_AMBER, COL_BG);
    tft->setCursor(fullScreen ? x + 195 : x + 200, startY);
    tft->print(ampm);
  }

  // Date display
  if (clockCfg.showDate) {
    if (fullScreen) {
      printCenteredText(dateStr, startY + 4 * stepY + 26, 2, COL_WHITE, COL_BG);
    } else {
      printCenteredText(dateStr, y + 54, 1, COL_MUTED, COL_BG);
    }
  }

  lastDrawnTimeKey = key;
}

// 7. Word Clock (Safe Non-accented Vietnamese or Clean English + AM/PM & Date Support)
void drawWordClock(int x, int y, int w, int h, const struct tm& t, bool fullScreen) {
  int hr = t.tm_hour;
  bool isPm = (hr >= 12);
  bool showAmPm = (clockCfg.timeFormat == "12h" || clockCfg.showAmPm);
  if (clockCfg.timeFormat == "12h") {
    hr = hr % 12;
    if (hr == 0) hr = 12;
  }
  int m = t.tm_min;

  String line1 = "";
  String line2 = "";

  if (clockCfg.language == "en") {
    const char* hWords[] = {"TWELVE", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN", "ELEVEN", "TWELVE"};
    int idx = hr % 12;
    if (idx == 0) idx = 12;
    line1 = hWords[idx];

    if (m == 0) line2 = "O'CLOCK";
    else if (m == 15) line2 = "FIFTEEN";
    else if (m == 30) line2 = "THIRTY";
    else if (m == 45) line2 = "FORTY FIVE";
    else {
      char b[16];
      snprintf(b, sizeof(b), "MINUTES %02d", m);
      line2 = String(b);
    }
    if (showAmPm) {
      line2 += isPm ? " PM" : " AM";
    }
  } else {
    // Vietnamese unaccented uppercase for clean Adafruit_GFX rendering
    const char* hWords[] = {"MUOI HAI", "MOT", "HAI", "BA", "BON", "NAM", "SAU", "BAY", "TAM", "CHIN", "MUOI", "MUOI MOT", "MUOI HAI"};
    int idx = hr % 12;
    if (idx == 0) idx = 12;
    line1 = String(hWords[idx]) + " GIO";

    if (m == 0) line2 = "DUNG";
    else if (m == 15) line2 = "MUOI LAM PHUT";
    else if (m == 30) line2 = "BA MUOI PHUT";
    else if (m == 45) line2 = "BON LAM PHUT";
    else {
      char b[16];
      snprintf(b, sizeof(b), "%d PHUT", m);
      line2 = String(b);
    }
    if (showAmPm) {
      line2 += isPm ? " CHIEU" : " SANG";
    }
  }

  String dateStr = getFormattedClockDate(t, clockCfg.language);
  String key = line1 + " " + line2 + "_" + dateStr + "_" + (fullScreen ? "F" : "B");
  if (key == lastDrawnTimeKey) return;

  tft->fillRect(x, y, w, h, COL_BG);

  int size = fullScreen ? 2 : 1;
  int cy = y + h / 2 - (clockCfg.showDate ? (fullScreen ? 12 : 6) : 0);

  printCenteredText(line1, cy - (size * 8) - 2, size, COL_WHITE, COL_BG);
  printCenteredText(line2, cy + 4, size, clockCfg.accentColor, COL_BG);

  if (clockCfg.showDate) {
    if (fullScreen) {
      printCenteredText(dateStr, cy + 28, 2, COL_MUTED, COL_BG);
    } else {
      printCenteredText(dateStr, y + 54, 1, COL_MUTED, COL_BG);
    }
  }

  lastDrawnTimeKey = key;
}
