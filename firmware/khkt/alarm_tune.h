// DeskFlow Station - dedicated wake-up alarm tune.
//
// The math alarm used to beep on/off with a fixed 2600 Hz tone. It now plays a
// dedicated ascending "DeskFlow Alert" motif so it is instantly recognisable and
// clearly different from the Morse / melody / test-buzz sounds.
#ifndef DESKFLOW_ALARM_TUNE_H
#define DESKFLOW_ALARM_TUNE_H

#include <Arduino.h>

#ifndef BUZZER_PIN
#define BUZZER_PIN 32
#endif

struct AlarmTuneNote {
  uint16_t freq;   // 0 = rest
  uint16_t durMs;
};

#define ALARM_TUNE_NOTE_COUNT 18

// Exposure for the UI / diagnostics.
extern const AlarmTuneNote ALARM_TUNE[ALARM_TUNE_NOTE_COUNT];

// Starts the alarm motif from the beginning (no-op if already running).
void alarmTuneStart();

// Silences the buzzer and resets the motif.
void alarmTuneStop();

// Non-blocking state machine; call from loop(). `audible` honours the mute
// setting so the visual strobe still works when the buzzer is muted.
// Returns true while the motif is still running.
bool alarmTuneUpdate(bool audible);

bool alarmTuneIsPlaying();

#endif  // DESKFLOW_ALARM_TUNE_H
