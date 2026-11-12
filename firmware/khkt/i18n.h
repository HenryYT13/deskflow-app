// DeskFlow Station - runtime language switch (Vietnamese / English).
//
// The physical OLED menu is tiny (240x320), so instead of shipping two full
// string tables we pass both label variants at the call site and this helper
// returns the one matching the selected language.
#ifndef DESKFLOW_I18N_H
#define DESKFLOW_I18N_H

#include <Arduino.h>

// true = English, false = Vietnamese (station default).
extern bool stationEnglish;

// Returns `en` when English is selected, otherwise `vi`.
const char* tr(const char* vi, const char* en);

// Persists the choice in NVS under the "lang_en" key.
void setStationLanguage(bool english);

// Reads the persisted choice ("lang_en"), defaulting to Vietnamese.
void loadStationLanguage();

#endif  // DESKFLOW_I18N_H
