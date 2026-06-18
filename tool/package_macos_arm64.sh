#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="$ROOT_DIR/build/vaptool-mac-arm64"
APP_NAME="VapToolMac"
JDK_HOME="${JDK_HOME:-/opt/homebrew/opt/openjdk@21}"
JAVA_BIN="$JDK_HOME/bin/java"
JAVAC_BIN="$JDK_HOME/bin/javac"
JAR_BIN="$JDK_HOME/bin/jar"
JPACKAGE_BIN="$JDK_HOME/bin/jpackage"
FFMPEG_BIN="${FFMPEG_BIN:-/opt/homebrew/bin/ffmpeg}"
MP4EDIT_BIN="${MP4EDIT_BIN:-/opt/homebrew/bin/mp4edit}"

if [[ ! -x "$JAVA_BIN" || ! -x "$JAVAC_BIN" || ! -x "$JPACKAGE_BIN" ]]; then
  echo "Missing arm64 JDK. Install it with: brew install openjdk@21" >&2
  exit 1
fi

if [[ ! -x "$FFMPEG_BIN" || ! -x "$MP4EDIT_BIN" ]]; then
  echo "Missing ffmpeg or mp4edit. Install them with: brew install ffmpeg bento4" >&2
  exit 1
fi

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/input" "$BUILD_DIR/mac" "$BUILD_DIR/dist"

"$JAVAC_BIN" --release 8 -encoding UTF-8 \
  -d "$BUILD_DIR/classes" \
  $(find "$ROOT_DIR/Android/PlayerProj/animtool/src/main/java" -name '*.java')

"$JAR_BIN" --create \
  --file "$BUILD_DIR/input/vaptool.jar" \
  --main-class com.tencent.qgame.playerproj.animtool.Main \
  -C "$BUILD_DIR/classes" .

cp "$FFMPEG_BIN" "$BUILD_DIR/mac/ffmpeg"
cp "$MP4EDIT_BIN" "$BUILD_DIR/mac/mp4edit"
chmod +x "$BUILD_DIR/mac/ffmpeg" "$BUILD_DIR/mac/mp4edit"

"$JPACKAGE_BIN" \
  --type app-image \
  --name "$APP_NAME" \
  --app-version 2.0.6 \
  --vendor Tencent \
  --input "$BUILD_DIR/input" \
  --main-jar vaptool.jar \
  --main-class com.tencent.qgame.playerproj.animtool.Main \
  --dest "$BUILD_DIR/dist" \
  --mac-package-identifier tencent.VapToolMac \
  --mac-package-name "$APP_NAME" \
  --app-content "$BUILD_DIR/mac"

codesign --force --deep --sign - "$BUILD_DIR/dist/$APP_NAME.app"

(
  cd "$BUILD_DIR/dist"
  ditto -c -k --keepParent "$APP_NAME.app" "$ROOT_DIR/build/${APP_NAME}-mac-arm64.zip"
)

echo "$BUILD_DIR/dist/$APP_NAME.app"
echo "$ROOT_DIR/build/${APP_NAME}-mac-arm64.zip"
