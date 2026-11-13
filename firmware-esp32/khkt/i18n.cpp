#include "i18n.h"
#include <Preferences.h>

// Shared NVS handle opened in khkt.ino (namespace "deskflow").
extern Preferences prefs;

bool stationEnglish = false;

const char* tr(const char* vi, const char* en) {
  if (stationEnglish) return (en != nullptr) ? en : vi;
  return (vi != nullptr) ? vi : en;
}

void setStationLanguage(bool english) {
  stationEnglish = english;
  prefs.putBool("lang_en", stationEnglish);
}

void loadStationLanguage() {
  stationEnglish = prefs.getBool("lang_en", false);
}
