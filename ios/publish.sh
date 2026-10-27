#!/bin/sh
# Builds a development-signed IPA and serves it over a temporary Cloudflare quick tunnel
# (a random https://….trycloudflare.com address that exists only while this runs).
# Open the printed address in Safari on a team-registered iPhone and tap Install. Ctrl-C stops it.
set -e
cd "$(dirname "$0")"
xcodegen generate -q
# A new build number every time: iOS caches widget settings and previews per build number.
xcodebuild -scheme IsraelWeather -configuration Release -destination generic/platform=iOS \
  -archivePath build/IsraelWeather.xcarchive -allowProvisioningUpdates archive -quiet \
  CURRENT_PROJECT_VERSION="$(date +%y%m%d%H%M)"
rm -rf build/export build/ota
xcodebuild -exportArchive -archivePath build/IsraelWeather.xcarchive -exportPath build/export \
  -exportOptionsPlist ExportOptions.plist -allowProvisioningUpdates -quiet
mkdir -p build/ota && cp build/export/IsraelWeather.ipa build/ota/

PORT=8765
python3 -m http.server $PORT --bind 127.0.0.1 --directory build/ota >/dev/null 2>&1 &
SERVER=$!
cloudflared tunnel --url http://127.0.0.1:$PORT >build/tunnel.log 2>&1 &
TUNNEL=$!
trap 'kill $SERVER $TUNNEL 2>/dev/null' EXIT INT TERM

until URL=$(grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' build/tunnel.log | head -1) && [ -n "$URL" ]; do sleep 1; done
VERSION=$(/usr/libexec/PlistBuddy -c "Print CFBundleVersion" build/IsraelWeather.xcarchive/Products/Applications/IsraelWeather.app/Info.plist)

cat >build/ota/manifest.plist <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict><key>items</key><array><dict>
<key>assets</key><array><dict><key>kind</key><string>software-package</string><key>url</key><string>$URL/IsraelWeather.ipa</string></dict></array>
<key>metadata</key><dict><key>bundle-identifier</key><string>com.bennybar.israelweather</string>
<key>bundle-version</key><string>$VERSION</string><key>kind</key><string>software</string><key>title</key><string>Israel Weather</string></dict>
</dict></array></dict></plist>
PLIST

cat >build/ota/index.html <<HTML
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Israel Weather</title><style>body{font:17px -apple-system,system-ui;text-align:center;padding:60px 20px;background:#f4f6ee;color:#1a1d26}
a{display:inline-block;margin-top:24px;padding:14px 28px;border-radius:14px;background:#1a1d26;color:#fff;text-decoration:none;font-weight:600}</style>
</head><body><h1>Israel Weather</h1><a href="itms-services://?action=download-manifest&amp;url=$URL/manifest.plist">Install</a></body></html>
HTML

echo "Open in Safari on your iPhone: $URL"
wait $TUNNEL
