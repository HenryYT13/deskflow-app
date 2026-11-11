// Minimal MIDI player (monophonic) for the buzzer. See ../README.md.
#ifndef DESKFLOW_MELODY_H
#define DESKFLOW_MELODY_H
#include <Arduino.h>

// Sample: format-0 SMF, 480 tpq, 120 BPM (C-E-G-C arpeggio jingle).
static const uint8_t SAMPLE_MELODY_MID[] PROGMEM = {
  0x4D, 0x54, 0x68, 0x64, 0x00, 0x00, 0x00, 0x06, 0x00, 0x00, 0x00, 0x01,
  0x01, 0xE0, 0x4D, 0x54, 0x72, 0x6B, 0x00, 0x00, 0x00, 0x41, 0x00, 0xFF,
  0x51, 0x03, 0x07, 0xA1, 0x20, 0x00, 0x90, 0x3C, 0x5A, 0x81, 0x52, 0x80,
  0x3C, 0x00, 0x1E, 0x90, 0x40, 0x5A, 0x81, 0x52, 0x80, 0x40, 0x00, 0x1E,
  0x90, 0x43, 0x5A, 0x81, 0x52, 0x80, 0x43, 0x00, 0x1E, 0x90, 0x48, 0x5A,
  0x83, 0x42, 0x80, 0x48, 0x00, 0x1E, 0x90, 0x43, 0x5A, 0x81, 0x52, 0x80,
  0x43, 0x00, 0x1E, 0x90, 0x48, 0x5A, 0x85, 0x32, 0x80, 0x48, 0x00, 0x00,
  0xFF, 0x2F, 0x00
};

#define MELODY_MAX_NOTES 512
struct MelodyNote { uint16_t freq; uint16_t durMs; };  // freq 0 = rest
static MelodyNote melodyNotes[MELODY_MAX_NOTES];
static int melodyCount = 0;
static int melodyIdx = 0;
static bool melodyPlaying = false;
static bool melodyToneOn = false;
static unsigned long melodyStepStart = 0;
static String melodyCurrentTitle = "";

static uint32_t _mRead(const uint8_t* d, size_t len, size_t& p, int n) {
  uint32_t v = 0;
  for (int i = 0; i < n && p < len; i++) v = (v << 8) | pgm_read_byte(d + p++);
  return v;
}
static uint32_t _mVlq(const uint8_t* d, size_t len, size_t& p) {
  uint32_t v = 0;
  for (int i = 0; i < 4 && p < len; i++) {
    uint8_t b = pgm_read_byte(d + p++);
    v = (v << 7) | (b & 0x7F);
    if (!(b & 0x80)) break;
  }
  return v;
}
static uint16_t _mFreq(uint8_t note) {
  return (uint16_t)(440.0f * powf(2.0f, ((int)note - 69) / 12.0f) + 0.5f);
}

// Parses the first track holding notes into melodyNotes[]. Returns note count (0 on error).
static int melodyParse(const uint8_t* d, size_t len) {
  melodyCount = 0;
  size_t p = 0;
  if (len < 22 || _mRead(d, len, p, 4) != 0x4D546864UL) return 0;  // "MThd"
  _mRead(d, len, p, 4);
  _mRead(d, len, p, 2);                       // format
  uint16_t ntrks = _mRead(d, len, p, 2);
  uint16_t tpq = _mRead(d, len, p, 2);
  if (tpq == 0 || (tpq & 0x8000)) return 0;   // SMPTE unsupported
  uint32_t tempo = 500000;                    // us per quarter
  for (uint16_t t = 0; t < ntrks && p + 8 <= len; t++) {
    uint32_t id = _mRead(d, len, p, 4);
    uint32_t tlen = _mRead(d, len, p, 4);
    size_t end = p + tlen;
    if (end > len) end = len;
    if (id != 0x4D54726BUL) { p = end; continue; }  // "MTrk"
    uint8_t running = 0;
    int8_t curNote = -1;
    uint32_t lastTick = 0, tick = 0;
    int startCount = melodyCount;
    while (p < end && melodyCount < MELODY_MAX_NOTES) {
      tick += _mVlq(d, len, p);
      if (p >= end) break;
      uint8_t st = pgm_read_byte(d + p);
      if (st & 0x80) { p++; if (st < 0xF0) running = st; } else st = running;
      if (st == 0xFF) {
        uint8_t type = _mRead(d, len, p, 1);
        uint32_t ml = _mVlq(d, len, p);
        if (type == 0x51 && ml == 3) { size_t q = p; tempo = _mRead(d, len, q, 3); }
        if (type == 0x2F) break;
        p += ml;
      } else if (st == 0xF0 || st == 0xF7) {
        p += _mVlq(d, len, p);
      } else if (st >= 0x80) {
        uint8_t hi = st & 0xF0;
        uint8_t a = _mRead(d, len, p, 1);
        uint8_t b = (hi == 0xC0 || hi == 0xD0) ? 0 : _mRead(d, len, p, 1);
        if ((st & 0x0F) != 9 && hi == 0x90 && b > 0) {  // note on (skip drum channel 10)
          if (curNote >= 0) {  // legato: close previous note
            melodyNotes[melodyCount++] = { _mFreq(curNote), (uint16_t)min<uint32_t>(((uint64_t)(tick - lastTick) * tempo / tpq) / 1000, 65535) };
            if (melodyCount >= MELODY_MAX_NOTES) break;
          } else if (tick > lastTick) {
            melodyNotes[melodyCount++] = { 0, (uint16_t)min<uint32_t>(((uint64_t)(tick - lastTick) * tempo / tpq) / 1000, 65535) };
            if (melodyCount >= MELODY_MAX_NOTES) break;
          }
          curNote = a; lastTick = tick;
        } else if (curNote >= 0 && (hi == 0x80 || (hi == 0x90 && b == 0)) && a == (uint8_t)curNote) {
          melodyNotes[melodyCount++] = { _mFreq(curNote), (uint16_t)min<uint32_t>(((uint64_t)(tick - lastTick) * tempo / tpq) / 1000, 65535) };
          curNote = -1; lastTick = tick;
        }
      } else break;
    }
    if (melodyCount > startCount) break;  // use first track with notes
    p = end;
  }
  return melodyCount;
}

static void melodyStop() {
  if (melodyPlaying || melodyToneOn) noTone(BUZZER_PIN);
  melodyPlaying = false;
  melodyToneOn = false;
}

// Start playback. bool enabled = honour mute setting.
static bool melodyStart(const uint8_t* d, size_t len) {
  melodyStop();
  if (melodyParse(d, len) == 0) return false;
  melodyIdx = 0;
  melodyPlaying = true;
  melodyToneOn = false;
  melodyStepStart = 0;  // first update starts note 0 immediately
  return true;
}

// Call from loop(); non-blocking. Returns true while playing.
static bool melodyUpdate(bool buzzerOn) {
  if (!melodyPlaying) return false;
  unsigned long now = millis();
  if (melodyStepStart != 0 && now - melodyStepStart < melodyNotes[melodyIdx - 1].durMs) return true;
  if (melodyIdx >= melodyCount) { melodyStop(); return false; }
  MelodyNote n = melodyNotes[melodyIdx++];
  melodyStepStart = now ? now : 1;
  if (n.freq && buzzerOn) { tone(BUZZER_PIN, n.freq); melodyToneOn = true; }
  else { noTone(BUZZER_PIN); melodyToneOn = false; }
  return true;
}

#endif
