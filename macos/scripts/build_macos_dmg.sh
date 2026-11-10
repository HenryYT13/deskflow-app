#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MACOS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT_DIR="$MACOS_DIR/dist"
VERSION="${1:-${VERSION:-1.0.3}}"
VERSION="${VERSION#v}"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "Error: Native macOS build script must be executed on Darwin (macOS)."
  exit 1
fi

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

cd "$MACOS_DIR"
echo "[1/5] Compiling native macOS arm64 Swift + SwiftUI binary..."
mkdir -p "$MACOS_DIR/.build/release"
BIN_PATH="$MACOS_DIR/.build/release/DeskFlow"

SDK_PATH="$(xcrun --show-sdk-path)"
if [[ -d "/Library/Developer/CommandLineTools/SDKs/MacOSX26.5.sdk" ]] && [[ ! -f "/Library/Developer/CommandLineTools/usr/lib/swift/host/plugins/libSwiftUIMacros.dylib" ]]; then
  echo "Using MacOSX26.5.sdk (CommandLineTools without libSwiftUIMacros.dylib)..."
  SDK_PATH="/Library/Developer/CommandLineTools/SDKs/MacOSX26.5.sdk"
fi

swiftc -O \
  -sdk "$SDK_PATH" \
  -target arm64-apple-macos13.0 \
  -parse-as-library \
  DeskFlow/DeskFlowApp.swift \
  DeskFlow/Models/ApiModels.swift \
  DeskFlow/Services/DeskFlowApiClient.swift \
  DeskFlow/Services/Localization.swift \
  DeskFlow/Services/SelfTestRunner.swift \
  DeskFlow/Views/MainSplitView.swift \
  -o "$BIN_PATH"

if [[ ! -x "$BIN_PATH" ]]; then
  echo "Error: Compiled binary not found at $BIN_PATH"
  exit 1
fi

echo "[2/5] Assembling Deskflow.app bundle..."
APP_DIR="$OUT_DIR/Deskflow.app"
mkdir -p "$APP_DIR/Contents/MacOS" "$APP_DIR/Contents/Resources"
cp -f "$BIN_PATH" "$APP_DIR/Contents/MacOS/DeskFlow"
chmod 0755 "$APP_DIR/Contents/MacOS/DeskFlow"

if [[ -d "$MACOS_DIR/DeskFlow/Resources/AppIcon.iconset" ]] && command -v iconutil >/dev/null 2>&1; then
  echo "Generating native macOS AppIcon.icns..."
  iconutil -c icns "$MACOS_DIR/DeskFlow/Resources/AppIcon.iconset" -o "$APP_DIR/Contents/Resources/AppIcon.icns"
fi

cat > "$APP_DIR/Contents/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>en</string>
    <key>CFBundleExecutable</key>
    <string>DeskFlow</string>
    <key>CFBundleIdentifier</key>
    <string>com.deskflow.Station</string>
    <key>CFBundleIconFile</key>
    <string>AppIcon</string>
    <key>CFBundleIconName</key>
    <string>AppIcon</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>Deskflow</string>
    <key>CFBundleDisplayName</key>
    <string>Deskflow</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>${VERSION}</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>LSMinimumSystemVersion</key>
    <string>13.0</string>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>NSPrincipalClass</key>
    <string>NSApplication</string>
</dict>
</plist>
EOF

printf "APPL????" > "$APP_DIR/Contents/PkgInfo"

echo "[3/5] Signing DeskFlow.app (ad-hoc development signature)..."
codesign --force --deep --sign - "$APP_DIR"
codesign --verify --verbose=2 "$APP_DIR"

echo "[4/5] Running native macOS test suite and GUI launch verification..."
"$APP_DIR/Contents/MacOS/DeskFlow" --version
"$APP_DIR/Contents/MacOS/DeskFlow" --run-tests
"$APP_DIR/Contents/MacOS/DeskFlow" --self-test

echo "[5/5] Creating DeskFlow-${VERSION}-macOS.dmg with Liquid Glass Finder styling..."
TMP_DMG="$OUT_DIR/Deskflow_temp.dmg"
DMG_PATH="$OUT_DIR/DeskFlow-${VERSION}-macOS.dmg"
rm -f "$TMP_DMG" "$DMG_PATH"

diskutil unmount force /Volumes/Deskflow >/dev/null 2>&1 || true
hdiutil detach /Volumes/Deskflow >/dev/null 2>&1 || true

hdiutil create -size 200m -fs HFS+ -volname "Deskflow" -ov "$TMP_DMG"

DEV_ATTACH=$(hdiutil attach -readwrite -noverify "$TMP_DMG")
DEV_NODE=$(echo "$DEV_ATTACH" | egrep '^/dev/' | head -n 1 | awk '{print $1}')

cp -R "$APP_DIR" "/Volumes/Deskflow/Deskflow.app"
ln -s /Applications "/Volumes/Deskflow/ "

mkdir -p "/Volumes/Deskflow/.background"
if [[ -f "$MACOS_DIR/DeskFlow/Resources/dmg_background.png" ]]; then
  cp -f "$MACOS_DIR/DeskFlow/Resources/dmg_background.png" "/Volumes/Deskflow/.background/background.png"
fi

if [[ -f "$APP_DIR/Contents/Resources/AppIcon.icns" ]]; then
  cp -f "$APP_DIR/Contents/Resources/AppIcon.icns" "/Volumes/Deskflow/.VolumeIcon.icns"
  if command -v SetFile >/dev/null 2>&1; then
    SetFile -c icnC "/Volumes/Deskflow/.VolumeIcon.icns" || true
    SetFile -a C "/Volumes/Deskflow" || true
  fi
fi

osascript <<'EOF' || true
tell application "Finder"
    try
        tell disk "Deskflow"
            open
            delay 1
            set current view of container window to icon view
            set toolbar visible of container window to false
            set statusbar visible of container window to false
            -- bounds: {left, top, right, bottom} -> 680 x 425 window
            set the bounds of container window to {200, 200, 880, 625}
            set viewOptions to the icon view options of container window
            set arrangement of viewOptions to not arranged
            set icon size of viewOptions to 128
            try
                set background picture of viewOptions to file ".background:background.png"
            end try
            -- Position Deskflow.app at (209, 190) and Applications at (472, 190)
            set position of item "Deskflow.app" to {209, 190}
            set position of item " " to {472, 190}
            close
            open
            update without registering applications
            delay 2
            close
        end tell
    end try
end tell
EOF

sync
sleep 2
hdiutil detach "$DEV_NODE" -force || true

hdiutil convert "$TMP_DMG" -format UDZO -imagekey zlib-level=9 -o "$DMG_PATH"
rm -f "$TMP_DMG"

echo "macOS build & packaging complete:"
ls -lh "$OUT_DIR"
file "$APP_DIR/Contents/MacOS/DeskFlow"
