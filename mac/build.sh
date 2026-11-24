#!/bin/sh
# build.sh: Ableton Live Remote (Mantra).app, swiftc straight, no Xcode. Run by GitHub Actions only
# (.github/workflows/mac.yml, SIGN=- NO_INSTALL=1); the Mac downloads the release mac-vN and signs it "Mantra Apps".
set -e
cd "$(dirname "$0")"
V=$(sed -n 's/^let VERSION = \([0-9]*\)/\1/p' app/App.swift)
NAME="Ableton Live Remote (Mantra)"
APP="build/$NAME.app"
rm -rf "$APP"; mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
swiftc -O -swift-version 5 -parse-as-library -o "$APP/Contents/MacOS/AbletonLiveRemote" app/*.swift
[ -x "$APP/Contents/MacOS/AbletonLiveRemote" ] || { echo "the app did not build"; exit 1; }
cat > "$APP/Contents/Info.plist" <<PL
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>CFBundleName</key><string>$NAME</string>
<key>CFBundleDisplayName</key><string>$NAME</string>
<key>CFBundleIconFile</key><string>AppIcon</string>
<key>CFBundleIdentifier</key><string>community.auroville.abletonliveremote</string>
<key>CFBundleExecutable</key><string>AbletonLiveRemote</string>
<key>CFBundlePackageType</key><string>APPL</string>
<key>CFBundleShortVersionString</key><string>$V</string>
<key>CFBundleVersion</key><string>$V</string>
<key>LSMinimumSystemVersion</key><string>14.0</string>
<key>NSHighResolutionCapable</key><true/>
<key>NSLocalNetworkUsageDescription</key><string>Ableton Live Remote on your phone reaches this Mac over the home Wi-Fi.</string>
<key>NSBonjourServices</key><array><string>_mantralive._tcp</string></array>
</dict></plist>
PL
I=build/AppIcon.iconset; rm -rf $I
swift icon/make_icon.swift $I
iconutil -c icns $I -o "$APP/Contents/Resources/AppIcon.icns"
codesign --force -s "${SIGN:-Mantra Apps}" "$APP"
echo "built $APP (v$V)"
