@echo off
setlocal enabledelayedexpansion
REM Batch script to install and monitor the Android app
REM Usage: test_app.bat

SET ADB_PATH="C:\Users\jcdau\AppData\Local\Android\Sdk\platform-tools\adb.exe"

echo Checking for connected Android devices...
%ADB_PATH% devices
echo.

REM Find first online device (status is "device", not "offline" or "unauthorized")
set DEVICE_COUNT=0
set FIRST_DEVICE=
for /f "tokens=1,2" %%a in ('%ADB_PATH% devices') do (
    if "%%b"=="device" (
        set /a DEVICE_COUNT+=1
        if !DEVICE_COUNT! equ 1 set FIRST_DEVICE=%%a
    )
)

if %DEVICE_COUNT% equ 0 (
    echo ERROR: No online Android device found!
    echo Please connect a device or start an emulator
    pause
    exit /b 1
)

if %DEVICE_COUNT% equ 1 (
    echo Found 1 device: !FIRST_DEVICE!
    set TARGET_FLAG=-s !FIRST_DEVICE!
    goto :install
)

echo Found %DEVICE_COUNT% devices. Please select target:
echo.
echo [1] Use first device (!FIRST_DEVICE!)
echo [2] Emulator (-e)
echo [3] USB Device (-d)
echo [4] Specific Device Serial (-s)
set /p choice="Enter choice (1-4): "

set TARGET_FLAG=
if "!choice!"=="1" (
    set TARGET_FLAG=-s !FIRST_DEVICE!
) else if "!choice!"=="2" (
    set TARGET_FLAG=-e
) else if "!choice!"=="3" (
    set TARGET_FLAG=-d
) else if "!choice!"=="4" (
    set /p serial="Enter device serial: "
    set TARGET_FLAG=-s !serial!
) else (
    echo Invalid choice. Using first device.
    set TARGET_FLAG=-s !FIRST_DEVICE!
)

:install
echo.
echo Installing debug APK...
%ADB_PATH% !TARGET_FLAG! install -r "MPDroid\build\outputs\apk\debug\MPDroid-debug.apk"

if errorlevel 1 (
    echo.
    echo Installation failed!
    pause
    exit /b 1
)

echo.
echo ========================================
echo Installation successful!
echo ========================================
echo.
echo Starting logcat monitoring...
echo.
echo Options:
echo   - Press Ctrl+C to stop monitoring
echo   - Close this window to stop monitoring
echo   - Monitoring will show errors/crashes in real-time
echo.
echo Launch the app on your device now!
echo.
pause

REM Clear logcat and monitor for errors
%ADB_PATH% !TARGET_FLAG! logcat -c
echo.
echo Logcat cleared. Starting monitoring (press Ctrl+C or close window to stop)...
echo.
%ADB_PATH% !TARGET_FLAG! logcat | findstr /C:"AndroidRuntime" /C:"FATAL" /C:"MPDApplication" /C:"Exception" /C:"Error" /C:"MPDConnection" /C:"NetworkCallback"

echo.
echo Logcat monitoring stopped.
pause
