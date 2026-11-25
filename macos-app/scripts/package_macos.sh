#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-1.5.1}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
SRC_MIRROR="/tmp/vidow-macos-src"
BUILD_SCRATCH="/tmp/vidow-swift-build-rel"
STAGE_DIR="/tmp/vidow-macos-stage"
BINS_DIR="/tmp/vidow-macos-bins"
APP_BUNDLE="$STAGE_DIR/Vidow.app"
DMG_OUT="/tmp/Vidow-v${VERSION}-macOS-arm64.dmg"

echo "==> Mirroring sources to /tmp (avoids iCloud Desktop mtime glitches)..."
rm -rf "$SRC_MIRROR"
mkdir -p "$SRC_MIRROR"
rsync -a --exclude '.build' --exclude 'dist' "$PROJECT_DIR/" "$SRC_MIRROR/"

echo "==> Building Release binary (arm64)..."
cd "$SRC_MIRROR"
swift build -c release --scratch-path "$BUILD_SCRATCH"
BIN_PATH="$BUILD_SCRATCH/release/Vidow"

echo "==> Running core unit self-tests..."
"$BIN_PATH" --self-test

echo "==> Generating Scheme C AppIcon.icns..."
rm -rf "$STAGE_DIR"
mkdir -p "$STAGE_DIR/AppIcon.iconset"
swift "$SCRIPT_DIR/generate_icon.swift" "$STAGE_DIR/icon_1024.png"

for sz in 16 32 128 256 512; do
  sips -z "$sz" "$sz" "$STAGE_DIR/icon_1024.png" --out "$STAGE_DIR/AppIcon.iconset/icon_${sz}x${sz}.png" >/dev/null
  sz2=$((sz * 2))
  sips -z "$sz2" "$sz2" "$STAGE_DIR/icon_1024.png" --out "$STAGE_DIR/AppIcon.iconset/icon_${sz}x${sz}@2x.png" >/dev/null
done
iconutil -c icns "$STAGE_DIR/AppIcon.iconset" -o "$STAGE_DIR/AppIcon.icns"

echo "==> Assembling Vidow.app bundle..."
mkdir -p "$APP_BUNDLE/Contents/MacOS"
mkdir -p "$APP_BUNDLE/Contents/Resources/bin"
mkdir -p "$APP_BUNDLE/Contents/Resources/en.lproj"
mkdir -p "$APP_BUNDLE/Contents/Resources/zh-Hans.lproj"
mkdir -p "$APP_BUNDLE/Contents/Resources/ja.lproj"
mkdir -p "$APP_BUNDLE/Contents/Resources/ko.lproj"

cp "$BIN_PATH" "$APP_BUNDLE/Contents/MacOS/Vidow"
chmod +x "$APP_BUNDLE/Contents/MacOS/Vidow"
cp "$STAGE_DIR/AppIcon.icns" "$APP_BUNDLE/Contents/Resources/AppIcon.icns"

# Bundle self-contained yt-dlp, ffmpeg, ffprobe
for tool in yt-dlp ffmpeg ffprobe; do
  if [[ -f "$BINS_DIR/$tool" ]]; then
    cp "$BINS_DIR/$tool" "$APP_BUNDLE/Contents/Resources/bin/$tool"
    chmod +x "$APP_BUNDLE/Contents/Resources/bin/$tool"
  else
    echo "ERROR: Missing $BINS_DIR/$tool" >&2
    exit 1
  fi
done

cat > "$APP_BUNDLE/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>en</string>
    <key>CFBundleExecutable</key>
    <string>Vidow</string>
    <key>CFBundleIconFile</key>
    <string>AppIcon</string>
    <key>CFBundleIdentifier</key>
    <string>com.vidfit.vidow.macos</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>Vidow</string>
    <key>CFBundleDisplayName</key>
    <string>Vidow</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>CFBundleShortVersionString</key>
    <string>${VERSION}</string>
    <key>CFBundleVersion</key>
    <string>13</string>
    <key>LSMinimumSystemVersion</key>
    <string>14.0</string>
    <key>NSHighResolutionCapable</key>
    <true/>
    <key>NSPrincipalClass</key>
    <string>NSApplication</string>
    <key>CFBundleLocalizations</key>
    <array>
        <string>en</string>
        <string>zh-Hans</string>
        <string>ja</string>
        <string>ko</string>
    </array>
    <key>NSAppTransportSecurity</key>
    <dict>
        <key>NSAllowsArbitraryLoads</key>
        <true/>
    </dict>
</dict>
</plist>
PLIST

echo "==> Ad-hoc code signing Vidow.app..."
codesign --force --deep --sign - "$APP_BUNDLE"

echo "==> Creating DMG installer ($DMG_OUT)..."
DMG_ROOT="$STAGE_DIR/dmg_root"
mkdir -p "$DMG_ROOT"
cp -R "$APP_BUNDLE" "$DMG_ROOT/Vidow.app"
ln -s /Applications "$DMG_ROOT/Applications"

rm -f "$DMG_OUT"
hdiutil create \
  -volname "Vidow ${VERSION}" \
  -srcfolder "$DMG_ROOT" \
  -ov \
  -format UDZO \
  "$DMG_OUT"

echo "==> Done!"
ls -lh "$DMG_OUT"
shasum -a 256 "$DMG_OUT"
