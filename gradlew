#!/usr/bin/env sh
# Project-local Gradle bootstrap. This is intentionally not the stock Gradle wrapper.
set -eu
GRADLE_VERSION="8.13"
GRADLE_HOME_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/ultraboost-dists/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME_DIR/gradle-$GRADLE_VERSION/bin/gradle"

if [ -x "$GRADLE_BIN" ]; then
  exec "$GRADLE_BIN" "$@"
fi

if command -v gradle >/dev/null 2>&1; then
  INSTALLED_VERSION="$(gradle --version 2>/dev/null | awk '/^Gradle / { print $2; exit }' || true)"
  if [ "$INSTALLED_VERSION" = "$GRADLE_VERSION" ]; then
    exec gradle "$@"
  fi
fi

command -v curl >/dev/null 2>&1 || { echo "Gradle 8.13 bulunamadı; önce Gradle kur veya curl yükle." >&2; exit 1; }
command -v unzip >/dev/null 2>&1 || { echo "Gradle kurulumu için unzip gerekli." >&2; exit 1; }
mkdir -p "$GRADLE_HOME_DIR"
TMP_ZIP="$GRADLE_HOME_DIR/gradle.zip"
echo "Gradle $GRADLE_VERSION indiriliyor..."
curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$TMP_ZIP"
unzip -oq "$TMP_ZIP" -d "$GRADLE_HOME_DIR"
rm -f "$TMP_ZIP"
exec "$GRADLE_BIN" "$@"
