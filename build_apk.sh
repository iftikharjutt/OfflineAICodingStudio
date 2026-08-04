#!/data/data/com.termux/files/usr/bin/bash
set -e

PROJECT_DIR="/data/data/com.termux/files/home/OfflineAICodingStudio"
DIST_DIR="$PROJECT_DIR/dist"
ANDROID_SDK="/data/data/com.termux/files/home/android-sdk"
NATIVE_AAPT2="/data/data/com.termux/files/usr/bin/aapt2"

echo "=================================================================="
echo "OFFLINE AI CODING STUDIO — APK BUILD & PACKAGING"
echo "=================================================================="

mkdir -p "$DIST_DIR"

export ANDROID_HOME="$ANDROID_SDK"

echo "[1/3] Running Gradle assembleDebug with Termux native AAPT2..."
cd "$PROJECT_DIR"
gradle :app:assembleDebug --no-daemon -Dcom.android.build.gradle.aapt2FromMavenOverride="$NATIVE_AAPT2" -Dandroid.aapt2FromMavenOverride="$NATIVE_AAPT2"

echo "[2/3] Checking output APK..."
BUILT_APK="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

if [ -f "$BUILT_APK" ]; then
    OUTPUT_NAME="OfflineAICodingStudio-v1.0.0-debug.apk"
    cp "$BUILT_APK" "$DIST_DIR/$OUTPUT_NAME"
    echo "[3/3] APK successfully generated and copied to:"
    echo "      $DIST_DIR/$OUTPUT_NAME"
    echo "      Size: $(du -h "$DIST_DIR/$OUTPUT_NAME" | cut -f1)"
else
    echo "[ERROR] APK build output not found at $BUILT_APK"
    exit 1
fi

echo "=================================================================="
echo "BUILD SUCCESSFUL"
echo "=================================================================="
