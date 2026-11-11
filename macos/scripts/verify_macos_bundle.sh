#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MACOS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT_DIR="$MACOS_DIR/dist"
APP_DIR="$OUT_DIR/Deskflow.app"

echo "=========================================================="
echo "          DeskFlow macOS Release Bundle Validation        "
echo "=========================================================="

if [[ ! -d "$APP_DIR" ]]; then
  echo "Error: App bundle directory does not exist: $APP_DIR"
  exit 1
fi

echo "[1/6] Validating Main Executable & Architecture..."
MAIN_BIN="$APP_DIR/Contents/MacOS/DeskFlow"
if [[ ! -f "$MAIN_BIN" ]]; then
  echo "Error: Missing main executable at $MAIN_BIN"
  exit 1
fi
if [[ ! -x "$MAIN_BIN" ]]; then
  echo "Error: Main binary $MAIN_BIN is not executable (+x)"
  exit 1
fi

BIN_INFO=$(file "$MAIN_BIN")
echo "  Executable: $BIN_INFO"
if [[ "$BIN_INFO" != *"Mach-O 64-bit"* ]] || [[ "$BIN_INFO" != *"arm64"* ]]; then
  echo "Error: Main binary is not Mach-O 64-bit arm64 ($BIN_INFO)"
  exit 1
fi

echo "[2/6] Validating Info.plist and Bundle Metadata..."
PLIST="$APP_DIR/Contents/Info.plist"
if [[ ! -f "$PLIST" ]]; then
  echo "Error: Info.plist missing at $PLIST"
  exit 1
fi

BUNDLE_ID=$(/usr/libexec/PlistBuddy -c "Print :CFBundleIdentifier" "$PLIST")
EXEC_NAME=$(/usr/libexec/PlistBuddy -c "Print :CFBundleExecutable" "$PLIST")
BUNDLE_NAME=$(/usr/libexec/PlistBuddy -c "Print :CFBundleName" "$PLIST")
MIN_SYS=$(/usr/libexec/PlistBuddy -c "Print :LSMinimumSystemVersion" "$PLIST")

echo "  CFBundleIdentifier:     $BUNDLE_ID"
echo "  CFBundleExecutable:     $EXEC_NAME"
echo "  CFBundleName:           $BUNDLE_NAME"
echo "  LSMinimumSystemVersion: $MIN_SYS"

if [[ "$BUNDLE_ID" != "com.deskflow.Station" ]]; then
  echo "Error: Invalid CFBundleIdentifier '$BUNDLE_ID' (expected 'com.deskflow.Station')"
  exit 1
fi
if [[ "$EXEC_NAME" != "DeskFlow" ]]; then
  echo "Error: Invalid CFBundleExecutable '$EXEC_NAME' (expected 'DeskFlow')"
  exit 1
fi

echo "[3/6] Validating Required Bundle Resources..."
if [[ ! -s "$APP_DIR/Contents/Resources/AppIcon.icns" ]]; then
  echo "Error: AppIcon.icns missing or empty at $APP_DIR/Contents/Resources/AppIcon.icns"
  exit 1
fi
if [[ ! -f "$APP_DIR/Contents/PkgInfo" ]] || [[ "$(cat "$APP_DIR/Contents/PkgInfo")" != "APPL????" ]]; then
  echo "Error: PkgInfo missing or invalid"
  exit 1
fi
echo "  AppIcon.icns: $(wc -c < "$APP_DIR/Contents/Resources/AppIcon.icns" | tr -d ' ') bytes"
echo "  PkgInfo:      $(cat "$APP_DIR/Contents/PkgInfo")"

echo "[4/6] Validating Code Signature with --verify --deep --strict..."
codesign --verify --deep --strict --verbose=2 "$APP_DIR"
codesign -dv --verbose=4 "$APP_DIR"

echo "[5/6] Checking Sealed Entitlements..."
codesign -d --entitlements :- "$APP_DIR" 2>/dev/null || echo "  (Ad-hoc / standard signature verified)"

echo "[6/6] Validating Packaging Artifacts (DMG and ZIP)..."
DMG_FILES=("$OUT_DIR"/*.dmg)
ZIP_FILES=("$OUT_DIR"/*.zip)

if [[ ! -e "${DMG_FILES[0]}" ]]; then
  echo "Error: No .dmg artifacts found in $OUT_DIR"
  exit 1
fi
if [[ ! -e "${ZIP_FILES[0]}" ]]; then
  echo "Error: No .zip artifacts found in $OUT_DIR"
  exit 1
fi

for dmg in "${DMG_FILES[@]}"; do
  echo "  Checking DMG: $(basename "$dmg") ($(wc -c < "$dmg" | tr -d ' ') bytes)"
  hdiutil imageinfo "$dmg" >/dev/null
done

for zipfile in "${ZIP_FILES[@]}"; do
  echo "  Checking ZIP: $(basename "$zipfile") ($(wc -c < "$zipfile" | tr -d ' ') bytes)"
  unzip -l "$zipfile" | grep -q "Deskflow.app/Contents/MacOS/DeskFlow" || {
    echo "Error: $zipfile does not contain Deskflow.app/Contents/MacOS/DeskFlow"
    exit 1
  }
done

echo "=========================================================="
echo " SUCCESS: DeskFlow macOS bundle passed all release checks "
echo "=========================================================="
