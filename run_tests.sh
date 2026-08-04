#!/bin/bash
set -e

PROJECT_DIR="/data/data/com.termux/files/home/OfflineAICodingStudio"
ANDROID_SDK="/data/data/com.termux/files/home/android-sdk"
NATIVE_AAPT2="/data/data/com.termux/files/usr/bin/aapt2"

echo "=================================================================="
echo "OFFLINE AI CODING STUDIO — UNIT TEST RUNNER"
echo "=================================================================="

export ANDROID_HOME="$ANDROID_SDK"

cd "$PROJECT_DIR"
gradle testDebugUnitTest --no-daemon -Dcom.android.build.gradle.aapt2FromMavenOverride="$NATIVE_AAPT2"

echo "=================================================================="
echo "ALL UNIT TESTS PASSED SUCCESSFULLY"
echo "=================================================================="
