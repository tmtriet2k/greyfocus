#!/bin/sh
# Builds GreyFocus, installs it on the connected phone and grants the one permission
# that can only be granted over adb. Requires USB debugging enabled on the phone.
set -e
cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
PKG=com.indiedev2k.greyfocus

echo "==> Building"
./gradlew -q assembleDebug

echo "==> Installing"
adb install -r app/build/outputs/apk/debug/app-debug.apk

echo "==> Granting WRITE_SECURE_SETTINGS (lets the app toggle system greyscale)"
adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS

echo "==> Opening the app"
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true

cat <<MSG

Done. On the phone:
  1. In GreyFocus tap "Open settings" and switch on "GreyFocus greyscale"
     (Settings > Accessibility > Installed apps/services).
  2. Pick apps with "Choose apps" and add websites such as youtube.com.
MSG
