#!/usr/bin/env sh
set -eu
GRADLE_VERSION="8.14"
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
INSTALL_DIR="$GRADLE_USER_HOME/pro-remote/gradle-$GRADLE_VERSION"
GRADLE_BIN="$INSTALL_DIR/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$INSTALL_DIR"
  TMP_DIR="${TMPDIR:-/tmp}/pro-remote-gradle-$$"
  mkdir -p "$TMP_DIR"
  ZIP="$TMP_DIR/gradle-$GRADLE_VERSION-bin.zip"
  URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  echo "Downloading Gradle $GRADLE_VERSION..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL "$URL" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP" "$URL"
  else
    echo "curl or wget is required to bootstrap Gradle." >&2
    exit 1
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$ZIP" -d "$TMP_DIR"
  else
    echo "unzip is required to bootstrap Gradle." >&2
    exit 1
  fi
  rm -rf "$INSTALL_DIR"
  mv "$TMP_DIR/gradle-$GRADLE_VERSION" "$INSTALL_DIR"
  rm -rf "$TMP_DIR"
fi
exec "$GRADLE_BIN" "$@"
