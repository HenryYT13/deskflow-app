#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MACOS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
OUT_DIR="$MACOS_DIR/dist"
VERSION="${1:-${VERSION:-1.0.4}}"
VERSION="${VERSION#v}"

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "Error: Native macOS build script must be executed on Darwin (macOS)."
  exit 1
fi

KEYCHAIN_PATH=""
cleanup() {
  local ec=$?
  if [[ -n "$KEYCHAIN_PATH" && -f "$KEYCHAIN_PATH" ]]; then
    echo "[CLEANUP] Removing temporary build keychain..."
    security delete-keychain "$KEYCHAIN_PATH" >/dev/null 2>&1 || true
  fi
  exit "$ec"
}
trap cleanup EXIT

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

cd "$MACOS_DIR"
echo "=========================================================================="
echo "          Building DeskFlow macOS v${VERSION} (Native Swift + SwiftUI)    "
echo "=========================================================================="

echo "[1/6] Compiling native macOS arm64 Swift binary..."
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
  DeskFlow/Services/FocusWindowLock.swift \
  DeskFlow/Services/Localization.swift \
  DeskFlow/Services/SelfTestRunner.swift \
  DeskFlow/Views/MainSplitView.swift \
  -o "$BIN_PATH"

if [[ ! -x "$BIN_PATH" ]]; then
  echo "Error: Compiled binary not found at $BIN_PATH"
  exit 1
fi

echo "[2/6] Assembling Deskflow.app bundle..."
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
    <string>${VERSION}</string>
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

echo "[3/6] Applying Code Signature..."
ENTITLEMENTS_FILE="$MACOS_DIR/DeskFlow/Resources/DeskFlow.entitlements"

# Check if Apple Developer ID signing credentials are provided in GitHub Secrets / environment
SIGN_IDENTITY="${APPLE_SIGNING_IDENTITY:-}"
if [[ -z "$SIGN_IDENTITY" && -n "${APPLE_CERTIFICATE_BASE64:-}" && -n "${APPLE_CERTIFICATE_PASSWORD:-}" ]]; then
  echo "[SIGNING] Importing Apple Developer ID certificate into temporary keychain..."
  KEYCHAIN_PATH="${RUNNER_TEMP:-/tmp}/deskflow_signing_${RANDOM}.keychain"
  KEYCHAIN_PWD="build_${RANDOM}"
  
  security create-keychain -p "$KEYCHAIN_PWD" "$KEYCHAIN_PATH"
  security set-keychain-settings -lut 21600 "$KEYCHAIN_PATH"
  security unlock-keychain -p "$KEYCHAIN_PWD" "$KEYCHAIN_PATH"
  
  CERT_P12="${RUNNER_TEMP:-/tmp}/dev_id_${RANDOM}.p12"
  echo "$APPLE_CERTIFICATE_BASE64" | base64 --decode > "$CERT_P12"
  security import "$CERT_P12" -k "$KEYCHAIN_PATH" -P "$APPLE_CERTIFICATE_PASSWORD" -T /usr/bin/codesign
  security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$KEYCHAIN_PWD" "$KEYCHAIN_PATH" >/dev/null 2>&1 || true
  rm -f "$CERT_P12"
  
  security list-keychains -d user -s "$KEYCHAIN_PATH" $(security list-keychains -d user | tr -d '"')
  SIGN_IDENTITY=$(security find-identity -v -p codesigning "$KEYCHAIN_PATH" | grep -m1 "Developer ID Application" | awk -F'"' '{print $2}' || true)
fi

if [[ -n "$SIGN_IDENTITY" ]]; then
  echo "[SIGNING] Signing with Apple Developer ID identity: $SIGN_IDENTITY"
  codesign --force --deep --options runtime --timestamp \
    --entitlements "$ENTITLEMENTS_FILE" \
    --sign "$SIGN_IDENTITY" \
    "$APP_DIR"
else
  echo "=========================================================================="
  echo "[SIGNING] Apple Developer ID identity not detected in environment."
  echo "Applying strict, clean ad-hoc code signature (--sign -)."
  echo "Note: Without an active Apple Developer Program membership (\$99/yr),"
  echo "Gatekeeper will assess this distribution as unidentified."
  echo "Users can launch via the official macOS per-app 'Open Anyway' mechanism."
  echo "See docs/MACOS_INSTALL.md for step-by-step instructions."
  echo "=========================================================================="
  codesign --force --deep --sign - \
    --entitlements "$ENTITLEMENTS_FILE" \
    "$APP_DIR"
fi

echo "[4/6] Running native macOS test suite and self-tests..."
"$APP_DIR/Contents/MacOS/DeskFlow" --version
"$APP_DIR/Contents/MacOS/DeskFlow" --run-tests
"$APP_DIR/Contents/MacOS/DeskFlow" --self-test

echo "[5/6] Packaging Release Artifacts (Portable ZIP & Liquid Glass DMG)..."
# 1. Portable ZIP (preserves symlinks, execution bit +x, and code signature metadata via ditto)
ZIP_PATH="$OUT_DIR/DeskFlow-${VERSION}-macOS.zip"
echo "  Creating $ZIP_PATH using ditto..."
(cd "$OUT_DIR" && ditto -c -k --sequesterRsrc --keepParent "Deskflow.app" "$ZIP_PATH")

# 2. Liquid Glass DMG
TMP_DMG="$OUT_DIR/Deskflow_temp.dmg"
DMG_PATH="$OUT_DIR/DeskFlow-${VERSION}-macOS.dmg"
rm -f "$TMP_DMG" "$DMG_PATH"

diskutil unmount force /Volumes/Deskflow >/dev/null 2>&1 || true
hdiutil detach /Volumes/Deskflow >/dev/null 2>&1 || true

hdiutil create -size 200m -fs HFS+ -volname "Deskflow" -ov "$TMP_DMG"

DEV_ATTACH=$(hdiutil attach -readwrite -noverify "$TMP_DMG")
DEV_NODE=$(echo "$DEV_ATTACH" | grep -E '^/dev/' | head -n 1 | awk '{print $1}')

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
            set the bounds of container window to {200, 200, 880, 625}
            set viewOptions to the icon view options of container window
            set arrangement of viewOptions to not arranged
            set icon size of viewOptions to 128
            try
                set background picture of viewOptions to file ".background:background.png"
            end try
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
hdiutil detach "$DEV_NODE" -force || hdiutil detach "/Volumes/Deskflow" -force || true

hdiutil convert "$TMP_DMG" -format UDZO -imagekey zlib-level=9 -o "$DMG_PATH"
rm -f "$TMP_DMG"

# Notarization support (future-proof if credentials present)
if [[ -n "$SIGN_IDENTITY" ]]; then
  if [[ -n "${APPLE_API_KEY_BASE64:-}" && -n "${APPLE_API_KEY_ID:-}" && -n "${APPLE_API_ISSUER:-}" ]]; then
    echo "[NOTARIZATION] Submitting DMG to Apple Notary Service via API Key..."
    P8_FILE="${RUNNER_TEMP:-/tmp}/AuthKey_${APPLE_API_KEY_ID}.p8"
    echo "$APPLE_API_KEY_BASE64" | base64 --decode > "$P8_FILE"
    xcrun notarytool submit "$DMG_PATH" --key "$P8_FILE" --key-id "$APPLE_API_KEY_ID" --issuer "$APPLE_API_ISSUER" --wait || true
    rm -f "$P8_FILE"
    xcrun stapler staple "$DMG_PATH" || true
  elif [[ -n "${APPLE_ID:-}" && -n "${APPLE_APP_SPECIFIC_PASSWORD:-}" && -n "${APPLE_TEAM_ID:-}" ]]; then
    echo "[NOTARIZATION] Submitting DMG to Apple Notary Service via App Password..."
    xcrun notarytool submit "$DMG_PATH" --apple-id "$APPLE_ID" --password "$APPLE_APP_SPECIFIC_PASSWORD" --team-id "$APPLE_TEAM_ID" --wait || true
    xcrun stapler staple "$DMG_PATH" || true
  fi
fi

echo "[6/6] Generating SHA256 release checksums..."
(cd "$OUT_DIR" && shasum -a 256 "DeskFlow-${VERSION}-macOS.dmg" "DeskFlow-${VERSION}-macOS.zip" > "SHA256SUMS-macOS.txt")
cat "$OUT_DIR/SHA256SUMS-macOS.txt"

echo "Build and packaging completed successfully:"
ls -lh "$OUT_DIR"
