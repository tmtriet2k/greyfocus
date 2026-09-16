#!/bin/sh
# Builds the APK, tags the current commit and publishes a GitHub release with the APK attached.
# Usage: ./scripts/release.sh v1.2.0
set -e
cd "$(dirname "$0")/.."
TAG="${1:?usage: release.sh vX.Y.Z}"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

./gradlew -q assembleRelease
mkdir -p dist
APK="dist/GreyFocus-$TAG.apk"
cp app/build/outputs/apk/release/app-release.apk "$APK"

git tag -a "$TAG" -m "GreyFocus $TAG"
git push origin "$TAG"
gh release create "$TAG" --title "GreyFocus $TAG" --generate-notes "$APK"
echo "Released $TAG with $APK"
