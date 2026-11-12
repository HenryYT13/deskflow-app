#include "alarm_tune.h"

// "DeskFlow Alert" - two rising triads with a short breath, then a held top note.
// Total length is < 3.5 s and it loops until the alarm is solved, so loop()
// stays fully non-blocking.
const AlarmTuneNote ALARM_TUNE[ALARM_TUNE_NOTE_COUNT] = {
  { 880, 160 },   // A5
  { 1175, 160 },  // D6
  { 1568, 160 },  // G6
  { 2093, 300 },  // C7
  { 0, 140 },     // breath
  { 2093, 140 },
  { 1568, 140 },
  { 2093, 340 },
  { 0, 480 },     // pause before the repeat

  { 988, 160 },   // B5
  { 1319, 160 },  // E6
  { 1760, 160 },  // A6
  { 2349, 300 },  // D7
  { 0, 140 },
  { 2349, 140 },
  { 1760, 140 },
  { 2349, 340 },
  { 0, 560 }
};

static bool playing = false;
static uint8_t idx = 0;          // index of the note about to be played
static bool toneActive = false;  // true while the buzzer is sounding
static unsigned long stepStart = 0;
static uint16_t stepMs = 0;
static uint16_t lastNoteMs = 0;  // used to size the silence tail

bool alarmTuneIsPlaying() { return playing; }

void alarmTuneStop() {
  if (toneActive) {
    noTone(BUZZER_PIN);
    digitalWrite(BUZZER_PIN, HIGH);
  }
  playing = false;
  toneActive = false;
  idx = 0;
  stepStart = 0;
  stepMs = 0;
  lastNoteMs = 0;
}

void alarmTuneStart() {
  if (playing) return;
  playing = true;
  toneActive = false;
  idx = 0;
  stepStart = 0;
  stepMs = 0;
  lastNoteMs = 0;
}

bool alarmTuneUpdate(bool audible) {
  if (!playing) return false;

  unsigned long now = millis();

  // Still inside the current step: nothing to do.
  if (stepStart != 0 && (now - stepStart) < stepMs) return true;

  // The tone of the previous note just finished: play its short silence tail.
  if (toneActive) {
    noTone(BUZZER_PIN);
    digitalWrite(BUZZER_PIN, HIGH);
    toneActive = false;
    stepStart = now ? now : 1;
    stepMs = (uint16_t)((lastNoteMs * 12UL) / 100UL);
    if (stepMs < 10) stepMs = 10;
    return true;
  }

  // Otherwise move on to the next note (the motif loops forever).
  AlarmTuneNote n = ALARM_TUNE[idx];
  idx = (uint8_t)((idx + 1) % ALARM_TUNE_NOTE_COUNT);
  lastNoteMs = (n.durMs < 20) ? 20 : n.durMs;
  stepStart = now ? now : 1;

  if (n.freq == 0 || !audible) {
    toneActive = false;
    stepMs = lastNoteMs;
    return true;
  }

  // Gate time: 88% tone so repeated pitches stay articulated.
  stepMs = (uint16_t)((lastNoteMs * 88UL) / 100UL);
  if (stepMs < 20) stepMs = 20;
  tone(BUZZER_PIN, n.freq);
  toneActive = true;
  return true;
}
