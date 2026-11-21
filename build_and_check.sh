#!/bin/bash

echo "🚀 Starting Android Gradle Build..."
echo "🧹 Cleaning old builds..."

# 1. Clean and build the debug APK
./gradlew clean assembleDebug

# Define the standard output path for the APK
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

echo ""
echo "------------------------------------------------"

# 2. Check if the APK was successfully generated
if [ -f "$APK_PATH" ]; then
    echo "✅ Build Successful!"
    echo "📦 APK located at: $APK_PATH"
    echo ""
    
    # 3. Verify SDK Compatibility using AAPT2
    echo "🔍 VERIFYING SDK COMPATIBILITY (Android 15/16)..."
    if command -v aapt2 &> /dev/null; then
        COMPILE_SDK=$(aapt2 dump badging "$APK_PATH" | grep -o "compileSdkVersion='[0-9]*'")
        TARGET_SDK=$(aapt2 dump badging "$APK_PATH" | grep -o "targetSdkVersion:'[0-9]*'")
        
        echo "   👉 $COMPILE_SDK"
        echo "   👉 $TARGET_SDK"
        
        if [[ "$TARGET_SDK" == *"35"* || "$TARGET_SDK" == *"36"* ]]; then
            echo "   ✅ Target SDK is correctly configured for Android 15/16."
        else
            echo "   ⚠️ WARNING: Target SDK is NOT 35 or 36. Update your build.gradle.kts!"
        fi
    else
        echo "   ⚠️ aapt2 not found in PATH. Skipping SDK version read."
    fi
    echo ""

    # 4. Verify 16 KB Page Alignment (CRITICAL for Android 15+)
    echo "🔍 VERIFYING 16 KB PAGE ALIGNMENT..."
    if command -v zipalign &> /dev/null; then
        # The -p flag checks for page alignment, and 16384 bytes = 16 KB
        if zipalign -c -p 16384 "$APK_PATH" > /dev/null 2>&1; then
            echo "   ✅ APK is properly 16 KB aligned! (Safe for Android 15+)"
        else
            echo "   ❌ FATAL: APK is NOT 16 KB aligned! This WILL crash on Android 15/16."
            echo "   (Ensure 'jniLibs { useLegacyPackaging = false }' is in your build.gradle.kts)"
        fi
    else
        echo "   ⚠️ zipalign not found in PATH. Skipping alignment check."
    fi

else
    echo "❌ Build Failed. Please review the Gradle error logs above."
fi

echo "------------------------------------------------"

