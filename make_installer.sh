#!/bin/bash
# Build a macOS (.dmg) or Linux (.deb/.rpm) Tracker installer.
#
# jpackage can only build an installer for the OS it runs on, so this must be
# run on the target platform. A JDK 17+ is required.
#
#   macOS : ./make_installer.sh            -> installer/Tracker-<ver>.dmg
#   Linux : ./make_installer.sh            -> installer/Tracker-<ver>.deb
#
# The app is self-contained: tracker.jar already bundles the video engine
# (Xuggle + native ffmpeg), the FlatLaf look & feel and all resources, so only a
# Java runtime has to be bundled, which jpackage does.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
JAR="$ROOT/tracker-improved.jar"
OUT="$ROOT/installer"
MAIN="org.opensourcephysics.cabrillo.tracker.Tracker"
APP_NAME="Tracker"
VERSION="${1:-1.0.0}"

# macOS: the native Aqua look & feel is used by default, so no extra flags.
# Linux: GTK look & feel needs the GTK libraries present on the build host.
MODULES="java.base,java.desktop,java.logging,java.prefs,java.xml,java.net.http,java.scripting,java.sql,java.naming,java.management,jdk.unsupported"

if [ ! -f "$JAR" ]; then
  echo "tracker-improved.jar not found - run build.py first" >&2
  exit 1
fi
if ! command -v jpackage >/dev/null 2>&1; then
  echo "jpackage not found - install a JDK 17 or newer" >&2
  exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT/input"
cp "$JAR" "$OUT/input/"

echo "=== jlink runtime ==="
jlink --add-modules "$MODULES" --output "$OUT/runtime" \
      --strip-debug --no-header-files --no-man-pages --compress zip-6

ICON_ARG=()
ICON="$ROOT/tracker/src/org/opensourcephysics/cabrillo/tracker/resources/images/tracker_icon_256.png"
if [ -f "$ICON" ]; then
  if [ "$(uname)" = "Darwin" ]; then
    # jpackage on macOS wants an .icns; build one from the PNG if possible
    ICNS="$OUT/tracker.icns"
    if command -v iconutil >/dev/null 2>&1; then
      ICONSET="$OUT/tracker.iconset"
      mkdir -p "$ICONSET"
      for s in 16 32 64 128 256 512; do
        sips -z $s $s "$ICON" --out "$ICONSET/icon_${s}x${s}.png" >/dev/null 2>&1 || true
      done
      iconutil -c icns "$ICONSET" -o "$ICNS" >/dev/null 2>&1 || true
      rm -rf "$ICONSET"
    fi
    [ -f "$ICNS" ] && ICON_ARG=(--icon "$ICNS")
  else
    ICON_ARG=(--icon "$ICON")
  fi
fi

case "$(uname)" in
  Darwin) PKG="dmg" ;;
  Linux)  PKG="deb" ;;
  *)      PKG="app-image" ;;
esac

echo "=== jpackage ($PKG) ==="
jpackage \
  --type "$PKG" \
  --name "$APP_NAME" \
  --app-version "$VERSION" \
  --input "$OUT/input" \
  --main-jar "$(basename "$JAR")" \
  --main-class "$MAIN" \
  --dest "$OUT" \
  --vendor "Tracker (Open Source Physics)" \
  --description "Tracker video analysis and modeling tool" \
  --java-options "-Xmx1024m" \
  --runtime-image "$OUT/runtime" \
  "${ICON_ARG[@]}"

echo
echo "done. contents of $OUT:"
ls -la "$OUT"
