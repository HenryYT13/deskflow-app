#ifndef ARCADE_GAMES_H
#define ARCADE_GAMES_H

#include <Arduino.h>
#include <Preferences.h>
#include "clock_styles.h"

extern Preferences prefs;
extern void printCenteredText(String text, int y, int size, uint16_t color, uint16_t bg);
extern void drawUtf8Text(int x, int y, const String& text, int size, uint16_t fg, uint16_t bg, int maxW);
extern void triggerBuzz(unsigned long durationMs, bool force);
extern bool buzzerEnabled;
extern const char* tr(const char* vi, const char* en);

// 0..8 mini-games inside the single "game" view
const int ARCADE_GAME_COUNT = 9;
bool inGameLobby = true;
int arcadeLobbyCursor = 0;
int activeMiniGame = 0; // 0..8
bool gameOver = false;
unsigned long gameOverAtMs = 0;
int gameScore = 0;
int gameHighScores[ARCADE_GAME_COUNT] = {0};
int gameLastDrawnScore = -999;

// 0: Dino
float dinoY = 0, dinoVel = 0, dinoLastDrawnY = 0;
bool dinoDuck = false;
int dinoObsX = 240, dinoObsW = 14, dinoObsH = 22, dinoLastDrawnObsX = -999, dinoLastDrawnObsW = 14;

// 1: Flappy
float flappyY = 140, flappyVel = 0, flappyLastY = 140;
int flappyPipeX = 240, flappyGapY = 145, flappyLastPipeX = -999;

// 2: Snake
const int SNAKE_COLS = 17, SNAKE_ROWS = 16, SNAKE_X0 = 18, SNAKE_Y0 = 74, SNAKE_CELL = 12;
int snakeX[80], snakeY[80], snakeLen = 4, snakeDir = 0, foodX = 10, foodY = 8;
unsigned long lastSnakeTick = 0;

// 3: 2048 Mini
int g2048[4][4];
bool g2048VertAxis = false; // false = Left/Right, true = Up/Down
bool g2048NeedRedraw = true;

// 4: Tetris Mini (10x16 grid)
const int TET_W = 10, TET_H = 16, TET_CELL = 12, TET_X0 = 60, TET_Y0 = 72;
uint8_t tetBoard[16][10];
int tetX = 3, tetY = 0, tetType = 0, tetRot = 0;
unsigned long lastTetDrop = 0;
bool tetNeedRedraw = true;
const uint16_t TET_COLS[7] = {0x07FF, 0xFFE0, 0xA2FA, 0x15F0, 0xF800, 0x03B9, 0xFD20};
const uint16_t TET_SHAPES[7][4] = {
  {0x0F00, 0x2222, 0x00F0, 0x4444}, // I
  {0xCC00, 0xCC00, 0xCC00, 0xCC00}, // O
  {0x4E00, 0x4640, 0x0E40, 0x4C40}, // T
  {0x6C00, 0x4620, 0x06C0, 0x8C40}, // S
  {0xC600, 0x2640, 0x0C60, 0x4C80}, // Z
  {0x8E00, 0x6440, 0x0E20, 0x44C0}, // J
  {0x2E00, 0x4460, 0x0E80, 0xC440}  // L
};

// 5: Space Invaders
float invShipX = 120;
int invBulletX = -1, invBulletY = -1;
int invEnemyX[12], invEnemyY[12];
bool invEnemyAlive[12];
int invEnemyDir = 3;
int invBombX = -1, invBombY = -1;
unsigned long lastInvTick = 0;
bool invNeedStatic = true;

// 6: Ping Pong vs AI
float pongBallX = 120, pongBallY = 160, pongVX = 2.6f, pongVY = 2.8f;
float pongPlayerX = 95, pongAiX = 95;
float pongLastBallX = 120, pongLastBallY = 160, pongLastPX = 95, pongLastAX = 95;
int pongPlayerPts = 0, pongAiPts = 0;
unsigned long lastPongTick = 0;

// 7: Reaction Tester
int reactPhase = 0; // 0=ready, 1=wait_red, 2=go_green, 3=result, 4=too_early
unsigned long reactGoAt = 0, reactSwitchAt = 0;
int reactResultMs = 0;
bool reactDirty = true;

// 8: Lucky Toolbox (0=Dice, 1=Coin, 2=Random Subject)
int luckyMode = 0;
int luckyResultIdx = 0;
bool luckyRolling = false;
unsigned long luckyRollUntil = 0;
bool luckyDirty = true;

inline const char* arcadeGameTitle(int idx) {
  static const char* VI[ARCADE_GAME_COUNT] = {
    "Dino Runner", "Flappy Bird", "Rắn Săn Mồi", "2048 Mini", "Xếp Gạch (Tetris)",
    "Bắn Ruồi (Invaders)", "Ping Pong AI", "Đo Phản Xạ (ms)", "Vòng Quay May Mắn"
  };
  static const char* EN[ARCADE_GAME_COUNT] = {
    "Dino Runner", "Flappy Bird", "Snake", "2048 Mini", "Tetris Mini",
    "Space Invaders", "Ping Pong vs AI", "Reaction Tester", "Lucky Toolbox"
  };
  int c = constrain(idx, 0, ARCADE_GAME_COUNT - 1);
  return tr(VI[c], EN[c]);
}

inline void spawnSnakeFood() {
  for (int tries = 0; tries < 40; tries++) {
    int fx = random(0, SNAKE_COLS), fy = random(0, SNAKE_ROWS);
    bool hit = false;
    for (int i = 0; i < snakeLen; i++) if (snakeX[i] == fx && snakeY[i] == fy) { hit = true; break; }
    if (!hit) { foodX = fx; foodY = fy; return; }
  }
}

inline void spawn2048Tile() {
  int emptyR[16], emptyC[16], cnt = 0;
  for (int r = 0; r < 4; r++)
    for (int c = 0; c < 4; c++)
      if (g2048[r][c] == 0) { emptyR[cnt] = r; emptyC[cnt] = c; cnt++; }
  if (cnt > 0) {
    int pick = random(cnt);
    g2048[emptyR[pick]][emptyC[pick]] = (random(10) < 9) ? 2 : 4;
  }
}

inline bool tetFits(int nx, int ny, int nrot) {
  uint16_t mask = TET_SHAPES[tetType][nrot & 3];
  for (int r = 0; r < 4; r++) {
    for (int c = 0; c < 4; c++) {
      if (mask & (0x8000 >> (r * 4 + c))) {
        int gx = nx + c, gy = ny + r;
        if (gx < 0 || gx >= TET_W || gy >= TET_H) return false;
        if (gy >= 0 && tetBoard[gy][gx] > 0) return false;
      }
    }
  }
  return true;
}

inline void spawnTetPiece() {
  tetType = random(7);
  tetRot = 0;
  tetX = 3;
  tetY = 0;
  tetNeedRedraw = true;
}

inline void resetActiveGame() {
  gameOver = false;
  gameOverAtMs = 0;
  gameScore = 0;
  gameLastDrawnScore = -999;

  dinoY = 0; dinoVel = 0; dinoDuck = false;
  dinoObsX = 240; dinoObsW = 14; dinoObsH = 22; dinoLastDrawnY = 0; dinoLastDrawnObsX = -999; dinoLastDrawnObsW = 14;

  flappyY = 140; flappyVel = 0; flappyPipeX = 240; flappyGapY = random(108, 215);
  flappyLastY = 140; flappyLastPipeX = -999;

  snakeLen = 4; snakeDir = 0;
  for (int i = 0; i < snakeLen; i++) { snakeX[i] = 6 - i; snakeY[i] = 8; }
  spawnSnakeFood(); lastSnakeTick = millis();

  memset(g2048, 0, sizeof(g2048));
  spawn2048Tile(); spawn2048Tile();
  g2048VertAxis = false; g2048NeedRedraw = true;

  memset(tetBoard, 0, sizeof(tetBoard));
  spawnTetPiece(); lastTetDrop = millis(); tetNeedRedraw = true;

  invShipX = 120; invBulletX = -1; invBulletY = -1; invBombX = -1; invBombY = -1;
  invEnemyDir = 3;
  for (int r = 0; r < 2; r++) {
    for (int c = 0; c < 6; c++) {
      int idx = r * 6 + c;
      invEnemyX[idx] = 28 + c * 32;
      invEnemyY[idx] = 74 + r * 24;
      invEnemyAlive[idx] = true;
    }
  }
  lastInvTick = millis(); invNeedStatic = true;

  pongBallX = 120; pongBallY = 160; pongVX = (random(2) ? 2.5f : -2.5f); pongVY = 2.7f;
  pongPlayerX = 95; pongAiX = 95; pongLastBallX = 120; pongLastBallY = 160;
  pongLastPX = 95; pongLastAX = 95; pongPlayerPts = 0; pongAiPts = 0; lastPongTick = millis();

  reactPhase = 0; reactResultMs = 0; reactDirty = true;
  luckyRolling = false; luckyDirty = true;
}

inline void triggerGameOver() {
  gameOver = true;
  gameOverAtMs = millis();
  if (gameScore > gameHighScores[activeMiniGame]) {
    gameHighScores[activeMiniGame] = gameScore;
    prefs.putInt(("game_hi_" + String(activeMiniGame)).c_str(), gameScore);
  }
  if (buzzerEnabled) triggerBuzz(120, false);
}

inline void drawArcadeLobby(bool full) {
  static int lastCursor = -1;
  if (!full && lastCursor == arcadeLobbyCursor) return;
  lastCursor = arcadeLobbyCursor;

  tft->fillRect(0, 26, 240, 268, COL_BG);
  printCenteredText(tr("ARCADE GAME HUB", "ARCADE GAME HUB"), 32, 2, COL_CYAN, COL_BG);
  printCenteredText(tr("<< / >> Chọn Game  |  PLAY Chơi", "<< / >> Pick Game  |  PLAY Start"), 52, 1, COL_MUTED, COL_BG);

  int totalItems = ARCADE_GAME_COUNT + 1; // +1 = Exit Hub
  int rowsPerPage = 4;
  int page = arcadeLobbyCursor / rowsPerPage;
  int first = page * rowsPerPage;

  for (int r = 0; r < rowsPerPage; r++) {
    int idx = first + r;
    if (idx >= totalItems) break;
    int y = 70 + r * 50;
    bool sel = (idx == arcadeLobbyCursor);
    uint16_t bg = sel ? COL_CYAN : COL_CARD;
    uint16_t fg = sel ? COL_BG : COL_WHITE;
    tft->fillRoundRect(14, y, 212, 44, 7, bg);
    tft->drawRoundRect(14, y, 212, 44, 7, sel ? COL_WHITE : COL_BORDER);
    if (idx < ARCADE_GAME_COUNT) {
      String lbl = String(sel ? "> " : "  ") + String(idx + 1) + ". " + arcadeGameTitle(idx);
      drawUtf8Text(22, y + 8, lbl, 1, fg, bg, 196);
      String sub = String("Best: ") + String(gameHighScores[idx]);
      drawUtf8Text(26, y + 25, sub, 1, sel ? COL_BG : COL_AMBER, bg, 180);
    } else {
      drawUtf8Text(22, y + 14, String(sel ? "> " : "  ") + tr("Thoát Arcade Hub", "Exit Arcade Hub"), 1, sel ? COL_BG : 0xF800, bg, 196);
    }
  }
  printCenteredText(String(tr("Trang ", "Page ")) + String(page + 1) + "/3  •  " + tr("Giữ << thoát", "Hold << exit"), 278, 1, COL_MUTED, COL_BG);
}

inline void drawGameHeaderBanner() {
  if (inGameLobby) {
    drawArcadeLobby(true);
    return;
  }
  tft->fillRect(0, 26, 240, 268, COL_BG);
  printCenteredText(arcadeGameTitle(activeMiniGame), 32, 1, COL_CYAN, COL_BG);
  if (activeMiniGame == 0) {
    tft->drawFastHLine(10, 250, 220, COL_BORDER);
  } else if (activeMiniGame == 1) {
    tft->drawFastHLine(10, 68, 220, COL_BORDER);
    tft->drawFastHLine(10, 262, 220, COL_BORDER);
  } else if (activeMiniGame == 2) {
    tft->drawRect(SNAKE_X0 - 2, SNAKE_Y0 - 2, SNAKE_COLS * SNAKE_CELL + 4, SNAKE_ROWS * SNAKE_CELL + 4, COL_BORDER);
    tft->fillCircle(SNAKE_X0 + foodX * SNAKE_CELL + SNAKE_CELL / 2, SNAKE_Y0 + foodY * SNAKE_CELL + SNAKE_CELL / 2, 4, 0xF800);
    for (int i = 0; i < snakeLen; i++) {
      tft->fillRoundRect(SNAKE_X0 + snakeX[i] * SNAKE_CELL + 1, SNAKE_Y0 + snakeY[i] * SNAKE_CELL + 1, SNAKE_CELL - 2, SNAKE_CELL - 2, 2, i == 0 ? COL_WHITE : COL_GREEN);
    }
  } else if (activeMiniGame == 4) {
    tft->drawRect(TET_X0 - 2, TET_Y0 - 2, TET_W * TET_CELL + 4, TET_H * TET_CELL + 4, COL_BORDER);
    tetNeedRedraw = true;
  } else if (activeMiniGame == 6) {
    tft->drawRect(12, 68, 216, 194, COL_BORDER);
  }
  printCenteredText(tr("<< / >> Chơi  •  Giữ << về Hub", "<< / >> Play  •  Hold << Hub"), 278, 1, COL_MUTED, COL_BG);
}

inline uint16_t color2048(int v) {
  switch (v) {
    case 2: return 0x2965; case 4: return 0x3A29; case 8: return 0xFD20;
    case 16: return 0xFB20; case 32: return 0xF800; case 64: return 0xD800;
    case 128: return 0xFFE0; case 256: return 0x15F0; default: return 0x3DFE;
  }
}

inline bool slide2048Line(int line[4]) {
  int tmp[4] = {0, 0, 0, 0}, p = 0;
  bool changed = false;
  for (int i = 0; i < 4; i++) if (line[i] != 0) tmp[p++] = line[i];
  for (int i = 0; i < 3; i++) {
    if (tmp[i] != 0 && tmp[i] == tmp[i + 1]) {
      tmp[i] *= 2;
      gameScore += tmp[i];
      tmp[i + 1] = 0;
      i++;
    }
  }
  int out[4] = {0, 0, 0, 0}; p = 0;
  for (int i = 0; i < 4; i++) if (tmp[i] != 0) out[p++] = tmp[i];
  for (int i = 0; i < 4; i++) {
    if (line[i] != out[i]) changed = true;
    line[i] = out[i];
  }
  return changed;
}

inline void move2048(int dir) {
  // dir: 0=Left, 1=Right, 2=Up, 3=Down
  bool moved = false;
  for (int i = 0; i < 4; i++) {
    int line[4];
    for (int j = 0; j < 4; j++) {
      if (dir == 0) line[j] = g2048[i][j];
      else if (dir == 1) line[j] = g2048[i][3 - j];
      else if (dir == 2) line[j] = g2048[j][i];
      else line[j] = g2048[3 - j][i];
    }
    if (slide2048Line(line)) moved = true;
    for (int j = 0; j < 4; j++) {
      if (dir == 0) g2048[i][j] = line[j];
      else if (dir == 1) g2048[i][3 - j] = line[j];
      else if (dir == 2) g2048[j][i] = line[j];
      else g2048[3 - j][i] = line[j];
    }
  }
  if (moved) {
    spawn2048Tile();
    if (gameScore > gameHighScores[3]) gameHighScores[3] = gameScore;
    if (buzzerEnabled) triggerBuzz(18, false);
  }
  g2048NeedRedraw = true;
  // Check if any move remains
  bool anyZero = false, anyMerge = false;
  for (int r = 0; r < 4; r++) {
    for (int c = 0; c < 4; c++) {
      if (g2048[r][c] == 0) anyZero = true;
      if (r < 3 && g2048[r][c] == g2048[r + 1][c]) anyMerge = true;
      if (c < 3 && g2048[r][c] == g2048[r][c + 1]) anyMerge = true;
    }
  }
  if (!anyZero && !anyMerge) triggerGameOver();
}

inline void drawGameView(bool viewChanged) {
  if (viewChanged) {
    inGameLobby = true;
    drawArcadeLobby(true);
    return;
  }
  if (inGameLobby) {
    drawArcadeLobby(false);
    return;
  }

  if (gameOver) {
    if (gameLastDrawnScore != -2) {
      gameLastDrawnScore = -2;
      tft->fillRoundRect(24, 96, 192, 96, 8, COL_CARD);
      tft->drawRoundRect(24, 96, 192, 96, 8, 0xF800);
      printCenteredText("GAME OVER!", 106, 2, 0xF800, COL_CARD);
      printCenteredText("Score: " + String(gameScore) + "  Hi: " + String(gameHighScores[activeMiniGame]), 134, 1, COL_WHITE, COL_CARD);
      printCenteredText(tr("PLAY: Chơi lại", "PLAY: Retry"), 154, 1, COL_CYAN, COL_CARD);
      printCenteredText(tr("<< / >>: Chọn Game Khác", "<< / >>: Game Lobby"), 170, 1, COL_AMBER, COL_CARD);
    }
    return;
  }

  if (activeMiniGame == 0) {
    const int GROUND_Y = 250, DINO_X = 36, DINO_W = 18, DINO_H = 22;
    if (dinoY > 0 || dinoVel > 0) {
      dinoY += dinoVel;
      dinoVel -= dinoDuck ? 2.1f : 0.92f;
      if (dinoY <= 0) { dinoY = 0; dinoVel = 0; }
    }
    int speedPx = 5 + min(7, gameScore / 150);
    dinoObsX -= speedPx;
    if (dinoObsX + dinoObsW < 10) {
      dinoObsX = 240 + random(15, 70);
      dinoObsW = random(11, 18); dinoObsH = random(18, 32);
      gameScore += 10;
    } else gameScore++;
    if (gameScore > gameHighScores[0]) gameHighScores[0] = gameScore;

    int oldH = (dinoDuck && dinoLastDrawnY <= 0.5f) ? 13 : DINO_H;
    int oldTop = GROUND_Y - oldH - (int)dinoLastDrawnY;
    tft->fillRect(DINO_X - 2, min(oldTop - 2, GROUND_Y - DINO_H - 4), DINO_W + 6, DINO_H + 5, COL_BG);
    tft->drawFastHLine(10, GROUND_Y, 220, COL_BORDER);
    if (dinoLastDrawnObsX > -40 && dinoLastDrawnObsX < 245) {
      tft->fillRect(max(10, dinoLastDrawnObsX - 2), GROUND_Y - 36, dinoLastDrawnObsW + 6, 35, COL_BG);
    }
    int hNow = (dinoDuck && dinoY <= 0.5f) ? 13 : DINO_H;
    int topNow = GROUND_Y - hNow - (int)dinoY;
    tft->fillRoundRect(DINO_X, topNow, DINO_W, hNow, 3, COL_WHITE);
    tft->fillRect(DINO_X + 12, topNow + 3, 3, 3, COL_BG);
    dinoLastDrawnY = dinoY;
    if (dinoObsX < 238) {
      int drawW = min(dinoObsW, 240 - max(10, dinoObsX));
      if (drawW > 0) tft->fillRoundRect(max(10, dinoObsX), GROUND_Y - dinoObsH, drawW, dinoObsH, 2, COL_GREEN);
    }
    dinoLastDrawnObsX = dinoObsX; dinoLastDrawnObsW = dinoObsW;
    if (dinoObsX < DINO_X + DINO_W - 3 && dinoObsX + dinoObsW > DINO_X + 3) {
      if (topNow + hNow > GROUND_Y - dinoObsH + 2) triggerGameOver();
    }
  } else if (activeMiniGame == 1) {
    const int BIRD_X = 56, PIPE_W = 24, GAP_H = 68, TOP_Y = 70, BOT_Y = 260;
    flappyVel += 0.72f; if (flappyVel > 6.5f) flappyVel = 6.5f;
    flappyY += flappyVel;
    flappyPipeX -= 4;
    if (flappyPipeX + PIPE_W < 10) {
      flappyPipeX = 242; flappyGapY = random(108, 215); gameScore += 10;
      if (gameScore > gameHighScores[1]) gameHighScores[1] = gameScore;
    }
    tft->fillRect(BIRD_X - 7, max(TOP_Y, (int)flappyLastY - 7), 16, 16, COL_BG);
    if (flappyLastPipeX > -PIPE_W && flappyLastPipeX < 244) {
      int oldLeft = max(10, flappyLastPipeX), oldRight = min(240, flappyLastPipeX + PIPE_W);
      if (flappyPipeX > flappyLastPipeX) {
        if (oldRight > oldLeft) tft->fillRect(oldLeft, TOP_Y, oldRight - oldLeft, BOT_Y - TOP_Y, COL_BG);
      } else {
        int newRight = max(10, min(240, flappyPipeX + PIPE_W));
        if (oldRight > newRight) tft->fillRect(newRight, TOP_Y, oldRight - newRight, BOT_Y - TOP_Y, COL_BG);
      }
    }
    if (flappyPipeX < 240 && flappyPipeX + PIPE_W > 10) {
      int px = max(10, flappyPipeX), pw = min(flappyPipeX + PIPE_W, 240) - px;
      if (pw > 0) {
        int upperH = (flappyGapY - GAP_H / 2) - TOP_Y, lowerY = flappyGapY + GAP_H / 2, lowerH = BOT_Y - lowerY;
        if (upperH > 0) tft->fillRect(px, TOP_Y, pw, upperH, COL_GREEN);
        if (lowerH > 0) tft->fillRect(px, lowerY, pw, lowerH, COL_GREEN);
      }
    }
    flappyLastPipeX = flappyPipeX;
    tft->fillCircle(BIRD_X, (int)flappyY, 6, COL_AMBER);
    tft->fillCircle(BIRD_X + 3, (int)flappyY - 2, 2, COL_BG);
    flappyLastY = flappyY;
    if (flappyY - 6 <= TOP_Y || flappyY + 6 >= BOT_Y) triggerGameOver();
    else if (flappyPipeX < BIRD_X + 6 && flappyPipeX + PIPE_W > BIRD_X - 6) {
      if (flappyY - 6 < flappyGapY - GAP_H / 2 || flappyY + 6 > flappyGapY + GAP_H / 2) triggerGameOver();
    }
  } else if (activeMiniGame == 2) {
    unsigned long stepMs = max(95UL, 210UL - (unsigned long)(gameScore / 2));
    if (millis() - lastSnakeTick >= stepMs) {
      lastSnakeTick = millis();
      int nx = snakeX[0] + (snakeDir == 0 ? 1 : (snakeDir == 2 ? -1 : 0));
      int ny = snakeY[0] + (snakeDir == 1 ? 1 : (snakeDir == 3 ? -1 : 0));
      if (nx < 0 || nx >= SNAKE_COLS || ny < 0 || ny >= SNAKE_ROWS) triggerGameOver();
      else {
        for (int i = 0; i < snakeLen - 1; i++) if (snakeX[i] == nx && snakeY[i] == ny) { triggerGameOver(); break; }
      }
      if (!gameOver) {
        bool ate = (nx == foodX && ny == foodY);
        if (!ate) {
          int tx = snakeX[snakeLen - 1], ty = snakeY[snakeLen - 1];
          tft->fillRect(SNAKE_X0 + tx * SNAKE_CELL, SNAKE_Y0 + ty * SNAKE_CELL, SNAKE_CELL, SNAKE_CELL, COL_BG);
        }
        for (int i = min(snakeLen, 79); i > 0; i--) { snakeX[i] = snakeX[i - 1]; snakeY[i] = snakeY[i - 1]; }
        snakeX[0] = nx; snakeY[0] = ny;
        if (ate) {
          if (snakeLen < 78) snakeLen++;
          gameScore += 10;
          if (gameScore > gameHighScores[2]) gameHighScores[2] = gameScore;
          if (buzzerEnabled) triggerBuzz(25, false);
          spawnSnakeFood();
          tft->fillCircle(SNAKE_X0 + foodX * SNAKE_CELL + SNAKE_CELL / 2, SNAKE_Y0 + foodY * SNAKE_CELL + SNAKE_CELL / 2, 4, 0xF800);
        }
        tft->fillRoundRect(SNAKE_X0 + snakeX[0] * SNAKE_CELL + 1, SNAKE_Y0 + snakeY[0] * SNAKE_CELL + 1, SNAKE_CELL - 2, SNAKE_CELL - 2, 2, COL_WHITE);
        if (snakeLen > 1) tft->fillRoundRect(SNAKE_X0 + snakeX[1] * SNAKE_CELL + 1, SNAKE_Y0 + snakeY[1] * SNAKE_CELL + 1, SNAKE_CELL - 2, SNAKE_CELL - 2, 2, COL_GREEN);
      }
    }
  } else if (activeMiniGame == 3) {
    // 2048 Mini
    if (g2048NeedRedraw) {
      g2048NeedRedraw = false;
      tft->fillRect(14, 68, 212, 206, COL_BG);
      for (int r = 0; r < 4; r++) {
        for (int c = 0; c < 4; c++) {
          int bx = 20 + c * 51, by = 72 + r * 47;
          int v = g2048[r][c];
          uint16_t col = (v == 0) ? COL_CARD : color2048(v);
          tft->fillRoundRect(bx, by, 46, 42, 5, col);
          tft->drawRoundRect(bx, by, 46, 42, 5, COL_BORDER);
          if (v > 0) {
            String vs = String(v);
            tft->setTextSize(v >= 100 ? 1 : 2);
            tft->setTextColor(COL_WHITE, col);
            int tw = vs.length() * (v >= 100 ? 6 : 12);
            tft->setCursor(bx + (46 - tw) / 2, by + (v >= 100 ? 17 : 13));
            tft->print(vs);
          }
        }
      }
      printCenteredText(g2048VertAxis ? tr("PLAY đổi: Đang [DỌC: <<Lên | >>Xuống]", "PLAY axis: [VERT: <<Up | >>Down]") : tr("PLAY đổi: Đang [NGANG: <<Trái | >>Phải]", "PLAY axis: [HORIZ: <<Left | >>Right]"), 262, 1, COL_AMBER, COL_BG);
    }
  } else if (activeMiniGame == 4) {
    // Tetris Mini
    if (millis() - lastTetDrop >= 460) {
      lastTetDrop = millis();
      if (tetFits(tetX, tetY + 1, tetRot)) {
        tetY++;
      } else {
        uint16_t mask = TET_SHAPES[tetType][tetRot & 3];
        for (int r = 0; r < 4; r++)
          for (int c = 0; c < 4; c++)
            if ((mask & (0x8000 >> (r * 4 + c))) && tetY + r >= 0 && tetY + r < TET_H)
              tetBoard[tetY + r][tetX + c] = tetType + 1;
        // Clear full lines
        for (int r = TET_H - 1; r >= 0; r--) {
          bool full = true;
          for (int c = 0; c < TET_W; c++) if (tetBoard[r][c] == 0) { full = false; break; }
          if (full) {
            gameScore += 100;
            if (gameScore > gameHighScores[4]) gameHighScores[4] = gameScore;
            if (buzzerEnabled) triggerBuzz(40, false);
            for (int rr = r; rr > 0; rr--) memcpy(tetBoard[rr], tetBoard[rr - 1], TET_W);
            memset(tetBoard[0], 0, TET_W);
            r++;
          }
        }
        spawnTetPiece();
        if (!tetFits(tetX, tetY, tetRot)) triggerGameOver();
      }
      tetNeedRedraw = true;
    }
    if (tetNeedRedraw && !gameOver) {
      tetNeedRedraw = false;
      for (int r = 0; r < TET_H; r++) {
        for (int c = 0; c < TET_W; c++) {
          uint8_t cell = tetBoard[r][c];
          uint16_t col = cell ? TET_COLS[(cell - 1) % 7] : COL_BG;
          tft->fillRect(TET_X0 + c * TET_CELL, TET_Y0 + r * TET_CELL, TET_CELL - 1, TET_CELL - 1, col);
        }
      }
      uint16_t mask = TET_SHAPES[tetType][tetRot & 3];
      for (int r = 0; r < 4; r++)
        for (int c = 0; c < 4; c++)
          if (mask & (0x8000 >> (r * 4 + c)))
            tft->fillRect(TET_X0 + (tetX + c) * TET_CELL, TET_Y0 + (tetY + r) * TET_CELL, TET_CELL - 1, TET_CELL - 1, TET_COLS[tetType]);
    }
  } else if (activeMiniGame == 5) {
    // Space Invaders
    if (millis() - lastInvTick >= 45) {
      lastInvTick = millis();
      tft->fillRect(14, 70, 212, 195, COL_BG);
      if (invBulletY > 70) {
        invBulletY -= 7;
        tft->fillRect(invBulletX - 1, invBulletY, 3, 7, COL_AMBER);
        for (int i = 0; i < 12; i++) {
          if (invEnemyAlive[i] && abs(invBulletX - invEnemyX[i]) < 11 && abs(invBulletY - invEnemyY[i]) < 10) {
            invEnemyAlive[i] = false;
            invBulletY = -1;
            gameScore += 20;
            if (gameScore > gameHighScores[5]) gameHighScores[5] = gameScore;
            if (buzzerEnabled) triggerBuzz(20, false);
            break;
          }
        }
      } else invBulletY = -1;

      bool edgeHit = false;
      int aliveCnt = 0;
      for (int i = 0; i < 12; i++) {
        if (!invEnemyAlive[i]) continue;
        aliveCnt++;
        invEnemyX[i] += invEnemyDir;
        if (invEnemyX[i] < 24 || invEnemyX[i] > 216) edgeHit = true;
      }
      if (edgeHit) {
        invEnemyDir = -invEnemyDir;
        for (int i = 0; i < 12; i++) {
          invEnemyY[i] += 10;
          if (invEnemyAlive[i] && invEnemyY[i] >= 232) triggerGameOver();
        }
      }
      if (aliveCnt == 0) {
        for (int r = 0; r < 2; r++)
          for (int c = 0; c < 6; c++) { int idx = r * 6 + c; invEnemyX[idx] = 28 + c * 32; invEnemyY[idx] = 74 + r * 24; invEnemyAlive[idx] = true; }
      }
      for (int i = 0; i < 12; i++) {
        if (!invEnemyAlive[i]) continue;
        tft->fillRoundRect(invEnemyX[i] - 8, invEnemyY[i] - 6, 16, 12, 3, COL_GREEN);
      }
      if (invBombY < 0 && random(100) < 4) {
        int shooter = random(12);
        if (invEnemyAlive[shooter]) { invBombX = invEnemyX[shooter]; invBombY = invEnemyY[shooter] + 8; }
      }
      if (invBombY > 0) {
        invBombY += 4;
        tft->fillCircle(invBombX, invBombY, 3, 0xF800);
        if (invBombY >= 242 && abs(invBombX - (int)invShipX) < 13) triggerGameOver();
        if (invBombY > 260) invBombY = -1;
      }
      tft->fillTriangle((int)invShipX, 238, (int)invShipX - 12, 254, (int)invShipX + 12, 254, COL_CYAN);
    }
  } else if (activeMiniGame == 6) {
    // Ping Pong vs AI
    if (millis() - lastPongTick >= 33) {
      lastPongTick = millis();
      tft->fillCircle((int)pongLastBallX, (int)pongLastBallY, 4, COL_BG);
      tft->fillRect((int)pongLastPX, 248, 44, 6, COL_BG);
      tft->fillRect((int)pongLastAX, 76, 44, 6, COL_BG);

      // AI follows ball with capped speed
      float aiCenter = pongAiX + 22.0f;
      if (pongBallX > aiCenter + 3) pongAiX += 2.3f;
      else if (pongBallX < aiCenter - 3) pongAiX -= 2.3f;
      pongAiX = constrain(pongAiX, 16.0f, 180.0f);

      pongBallX += pongVX; pongBallY += pongVY;
      if (pongBallX <= 18 || pongBallX >= 222) pongVX = -pongVX;
      if (pongBallY >= 244 && pongBallY <= 254 && pongBallX >= pongPlayerX - 4 && pongBallX <= pongPlayerX + 48) {
        pongVY = -fabs(pongVY) * 1.03f;
        pongVX += (pongBallX - (pongPlayerX + 22)) * 0.08f;
        gameScore += 10;
        if (gameScore > gameHighScores[6]) gameHighScores[6] = gameScore;
        if (buzzerEnabled) triggerBuzz(15, false);
      } else if (pongBallY <= 84 && pongBallY >= 74 && pongBallX >= pongAiX - 4 && pongBallX <= pongAiX + 48) {
        pongVY = fabs(pongVY);
      } else if (pongBallY < 70) {
        pongPlayerPts++; gameScore += 50; pongBallX = 120; pongBallY = 160; pongVX = 2.6f; pongVY = 2.7f;
      } else if (pongBallY > 258) {
        pongAiPts++;
        if (pongAiPts >= 3) triggerGameOver();
        else { pongBallX = 120; pongBallY = 160; pongVX = -2.5f; pongVY = -2.7f; }
      }
      tft->fillRect((int)pongAiX, 76, 44, 6, COL_AMBER);
      tft->fillRect((int)pongPlayerX, 248, 44, 6, COL_CYAN);
      tft->fillCircle((int)pongBallX, (int)pongBallY, 4, COL_WHITE);
      pongLastBallX = pongBallX; pongLastBallY = pongBallY; pongLastPX = pongPlayerX; pongLastAX = pongAiX;
    }
  } else if (activeMiniGame == 7) {
    // Reaction Tester
    if (reactPhase == 1 && millis() >= reactSwitchAt) {
      reactPhase = 2; reactGoAt = millis(); reactDirty = true;
    }
    if (reactDirty) {
      reactDirty = false;
      uint16_t boxCol = (reactPhase == 1) ? 0xF800 : ((reactPhase == 2) ? COL_GREEN : COL_CARD);
      tft->fillRoundRect(20, 76, 200, 178, 12, boxCol);
      tft->drawRoundRect(20, 76, 200, 178, 12, COL_BORDER);
      if (reactPhase == 0) {
        printCenteredText(tr("ĐO TỐC ĐỘ PHẢN XẠ", "REACTION TESTER"), 118, 1, COL_CYAN, boxCol);
        printCenteredText(tr("Nhấn PLAY để bắt đầu!", "Press PLAY to start!"), 148, 1, COL_WHITE, boxCol);
      } else if (reactPhase == 1) {
        printCenteredText(tr("CHỜ MÀU XANH...", "WAIT FOR GREEN..."), 150, 2, COL_WHITE, boxCol);
      } else if (reactPhase == 2) {
        printCenteredText(tr("NHẤN PLAY NGAY!", "PRESS PLAY NOW!"), 150, 2, COL_BG, boxCol);
      } else if (reactPhase == 3) {
        printCenteredText(String(reactResultMs) + " ms", 132, 3, COL_AMBER, boxCol);
        printCenteredText("PLAY: Thử lại  |  >>/<<: Hub", 184, 1, COL_MUTED, boxCol);
      } else if (reactPhase == 4) {
        printCenteredText(tr("BẤM SỚM QUÁ!", "TOO EARLY!"), 136, 2, 0xF800, boxCol);
        printCenteredText(tr("Nhấn PLAY để thử lại", "Press PLAY to retry"), 178, 1, COL_WHITE, boxCol);
      }
    }
  } else if (activeMiniGame == 8) {
    // Lucky Toolbox
    if (luckyRolling) {
      if (millis() < luckyRollUntil) {
        luckyResultIdx = random(6);
        luckyDirty = true;
      } else {
        luckyRolling = false;
        if (buzzerEnabled) triggerBuzz(45, false);
        luckyDirty = true;
      }
    }
    if (luckyDirty) {
      luckyDirty = false;
      tft->fillRoundRect(20, 72, 200, 186, 12, COL_CARD);
      tft->drawRoundRect(20, 72, 200, 186, 12, COL_BORDER);
      const char* modes[] = {tr("XÚC XẮC (1-6)", "DICE ROLL (1-6)"), tr("TUNG ĐỒNG XU", "COIN FLIP"), tr("RANDOM MÔN HỌC", "RANDOM SUBJECT")};
      printCenteredText(modes[luckyMode], 88, 1, COL_CYAN, COL_CARD);
      if (luckyMode == 0) {
        printCenteredText(String((luckyResultIdx % 6) + 1), 132, 4, COL_AMBER, COL_CARD);
      } else if (luckyMode == 1) {
        bool heads = (luckyResultIdx % 2) == 0;
        printCenteredText(heads ? tr("SẤP (HEADS)", "HEADS") : tr("NGỬA (TAILS)", "TAILS"), 146, 2, heads ? COL_GREEN : COL_AMBER, COL_CARD);
      } else {
        static const char* SUBJS[6] = {"Toán học", "Ngữ văn", "Tiếng Anh", "Vật lí", "Hóa học", "Tin học"};
        printCenteredText(SUBJS[luckyResultIdx % 6], 144, 2, COL_GREEN, COL_CARD);
      }
      printCenteredText(tr("<< / >> Đổi chế độ  |  PLAY Quay", "<< / >> Mode  |  PLAY Spin"), 226, 1, COL_MUTED, COL_CARD);
    }
  }

  if (!gameOver && (gameScore / 5 != gameLastDrawnScore)) {
    gameLastDrawnScore = gameScore / 5;
    tft->fillRect(14, 48, 212, 16, COL_BG);
    String hdr = "SCORE: " + String(gameScore) + "   HI: " + String(gameHighScores[activeMiniGame]);
    if (activeMiniGame == 6) hdr = "YOU " + String(pongPlayerPts) + " - " + String(pongAiPts) + " AI";
    drawUtf8Text(16, 50, hdr, 1, COL_AMBER, COL_BG, 210);
  }
}

// Handles physical buttons while inside srvStats.activeView == "game"
// Returns true if the caller should NOT process global view/menu shortcuts.
inline void handleArcadeButtons(int curPlay, int curNext, int curPrev, bool& exitToMain) {
  static bool prevPlayLow = false, prevNextLow = false, prevPrevLow = false;
  static unsigned long playDownAt = 0, prevDownAt = 0;
  static bool prevHoldExitSent = false, playHoldDropSent = false;

  bool playEdge = (curPlay == LOW && !prevPlayLow);
  bool nextEdge = (curNext == LOW && !prevNextLow);
  bool prevEdge = (curPrev == LOW && !prevPrevLow);
  prevPlayLow = (curPlay == LOW);
  prevNextLow = (curNext == LOW);
  prevPrevLow = (curPrev == LOW);

  if (curPlay == LOW) {
    if (playEdge) { playDownAt = millis(); playHoldDropSent = false; }
    else if (!inGameLobby && !gameOver && activeMiniGame == 4 && !playHoldDropSent && millis() - playDownAt >= 360) {
      playHoldDropSent = true;
      while (tetFits(tetX, tetY + 1, tetRot)) tetY++;
      lastTetDrop = 0; // force lock next tick
      tetNeedRedraw = true;
    }
  }

  if (curPrev == LOW) {
    if (prevEdge) { prevDownAt = millis(); prevHoldExitSent = false; }
    else if (!prevHoldExitSent && millis() - prevDownAt >= 1500) {
      prevHoldExitSent = true;
      if (buzzerEnabled) triggerBuzz(60, false);
      if (inGameLobby) {
        exitToMain = true;
      } else {
        inGameLobby = true;
        drawArcadeLobby(true);
      }
      return;
    }
  }

  if (inGameLobby) {
    int totalItems = ARCADE_GAME_COUNT + 1;
    if (nextEdge) { arcadeLobbyCursor = (arcadeLobbyCursor + 1) % totalItems; if (buzzerEnabled) triggerBuzz(20, false); }
    else if (prevEdge) { arcadeLobbyCursor = (arcadeLobbyCursor - 1 + totalItems) % totalItems; if (buzzerEnabled) triggerBuzz(20, false); }
    else if (playEdge) {
      if (buzzerEnabled) triggerBuzz(35, false);
      if (arcadeLobbyCursor >= ARCADE_GAME_COUNT) {
        exitToMain = true;
      } else {
        activeMiniGame = arcadeLobbyCursor;
        inGameLobby = false;
        resetActiveGame();
        drawGameHeaderBanner();
      }
    }
    return;
  }

  if (gameOver) {
    if (millis() - gameOverAtMs >= 450) {
      if (playEdge) { resetActiveGame(); drawGameHeaderBanner(); }
      else if (nextEdge || prevEdge) { inGameLobby = true; drawArcadeLobby(true); }
    }
    return;
  }

  // Active gameplay controls (PREV & NEXT NEVER switch display views!)
  if (activeMiniGame == 0) {
    if ((playEdge || nextEdge) && dinoY <= 1.0f) { dinoVel = 10.2f; if (buzzerEnabled) triggerBuzz(18, false); }
    dinoDuck = (curPrev == LOW);
  } else if (activeMiniGame == 1) {
    if (playEdge || nextEdge) { flappyVel = -6.8f; if (buzzerEnabled) triggerBuzz(16, false); }
  } else if (activeMiniGame == 2) {
    if (nextEdge) snakeDir = (snakeDir + 1) % 4;
    if (prevEdge) snakeDir = (snakeDir + 3) % 4;
    if (playEdge && buzzerEnabled) triggerBuzz(15, false);
  } else if (activeMiniGame == 3) {
    if (playEdge) { g2048VertAxis = !g2048VertAxis; g2048NeedRedraw = true; if (buzzerEnabled) triggerBuzz(15, false); }
    else if (prevEdge) move2048(g2048VertAxis ? 2 : 0);
    else if (nextEdge) move2048(g2048VertAxis ? 3 : 1);
  } else if (activeMiniGame == 4) {
    if (prevEdge && tetFits(tetX - 1, tetY, tetRot)) { tetX--; tetNeedRedraw = true; }
    else if (nextEdge && tetFits(tetX + 1, tetY, tetRot)) { tetX++; tetNeedRedraw = true; }
    else if (playEdge && tetFits(tetX, tetY, (tetRot + 1) & 3)) { tetRot = (tetRot + 1) & 3; tetNeedRedraw = true; }
  } else if (activeMiniGame == 5) {
    if (curPrev == LOW) invShipX = max(24.0f, invShipX - 3.5f);
    if (curNext == LOW) invShipX = min(216.0f, invShipX + 3.5f);
    if (playEdge && invBulletY < 0) { invBulletX = (int)invShipX; invBulletY = 232; if (buzzerEnabled) triggerBuzz(14, false); }
  } else if (activeMiniGame == 6) {
    if (curPrev == LOW) pongPlayerX = max(16.0f, pongPlayerX - 4.5f);
    if (curNext == LOW) pongPlayerX = min(180.0f, pongPlayerX + 4.5f);
  } else if (activeMiniGame == 7) {
    if (playEdge) {
      if (reactPhase == 0 || reactPhase == 3 || reactPhase == 4) {
        reactPhase = 1; reactSwitchAt = millis() + random(1400, 3800); reactDirty = true;
      } else if (reactPhase == 1) {
        reactPhase = 4; reactDirty = true;
      } else if (reactPhase == 2) {
        reactResultMs = (int)(millis() - reactGoAt);
        gameScore = max(1, 2000 - reactResultMs);
        if (gameScore > gameHighScores[7]) {
          gameHighScores[7] = gameScore;
          prefs.putInt("game_hi_7", gameScore);
        }
        reactPhase = 3; reactDirty = true;
        if (buzzerEnabled) triggerBuzz(45, false);
      }
    } else if ((prevEdge || nextEdge) && (reactPhase == 0 || reactPhase == 3 || reactPhase == 4)) {
      inGameLobby = true; drawArcadeLobby(true);
    }
  } else if (activeMiniGame == 8) {
    if (playEdge) { luckyRolling = true; luckyRollUntil = millis() + 650; }
    else if (nextEdge) { luckyMode = (luckyMode + 1) % 3; luckyDirty = true; }
    else if (prevEdge) { luckyMode = (luckyMode + 2) % 3; luckyDirty = true; }
  }
}

#endif
