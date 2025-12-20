@echo off
REM Batch script to install and monitor the Android app
REM Usage: test_app.bat

echo Checking for connected Android devices...
adb devices

echo.
echo Installing debug APK...
adb install -r "MPDroid\build\outputs\apk\debug\MPDroid-debug.apk"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo Starting logcat monitoring (press Ctrl+C to stop)...
    echo Launch the app manually and watch for errors below:
    echo.
    
    REM Clear logcat and monitor for errors
    adb logcat -c
    adb logcat | findstr /C:"AndroidRuntime" /C:"FATAL" /C:"MPDApplication" /C:"Exception" /C:"Error"
) else (
    echo Installation failed!
    pause
)

