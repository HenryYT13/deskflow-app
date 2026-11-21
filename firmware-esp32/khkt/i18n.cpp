#include "i18n.h"
#include <Preferences.h>

// Shared NVS handle opened in khkt.ino (namespace "deskflow").
extern Preferences prefs;

bool stationEnglish = false;
bool pendingLocalLangSync = false;

const char* tr(const char* vi, const char* en) {
  if (stationEnglish) return (en != nullptr) ? en : vi;
  return (vi != nullptr) ? vi : en;
}

void setStationLanguage(bool english) {
  stationEnglish = english;
  prefs.putBool("lang_en", stationEnglish);
  prefs.putString("clk_lang", stationEnglish ? "en" : "vi");
}

void loadStationLanguage() {
  stationEnglish = prefs.getBool("lang_en", false);
}

void setPendingLocalLangSync(bool pending) {
  pendingLocalLangSync = pending;
  prefs.putBool("lang_dirty", pending);
}

void loadPendingLocalLangSync() {
  pendingLocalLangSync = prefs.getBool("lang_dirty", false);
}
