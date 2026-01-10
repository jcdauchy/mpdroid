@echo off
REM Install MPDroid Release APK on device
REM Uses ADB path and default device from .cursorconfig

setlocal

REM Configuration
set ADB_PATH=C:\Users\jcdau\AppData\Local\Android\Sdk\platform-tools\adb.exe
set DEVICE_ID=R5CY91XF6QP
set BUILD_TYPE=RELEASE
set APK_PATH=MPDroid\build\outputs\apk\release\MPDroid-release.apk

echo ========================================
echo MPDroid Release Installer
echo ========================================
echo.

REM Check if we should build or use existing APK
set BUILD_APK=1
if "%1"=="--no-build" (
    set BUILD_APK=0
    echo Skipping build, using existing APK...
    echo.
)

REM Build release APK if needed
if %BUILD_APK%==1 (
    echo Building %BUILD_TYPE% APK...
    call gradlew.bat :MPDroid:assembleRelease
    if errorlevel 1 (
        echo.
        echo ERROR: Build failed!
        pause
        exit /b 1
    )
    echo Build successful!
    echo.
)

REM Check if APK exists
if not exist "%APK_PATH%" (
    echo ERROR: APK not found at %APK_PATH%
    echo Please build the project first.
    pause
    exit /b 1
)

REM Check device connection
echo Checking device connection...
"%ADB_PATH%" devices | findstr /C:"%DEVICE_ID%" | findstr /C:"device" >nul
if errorlevel 1 (
    echo.
    echo ERROR: Device %DEVICE_ID% not found or not authorized!
    echo.
    echo Available devices:
    "%ADB_PATH%" devices
    echo.
    echo Please connect your device and authorize USB debugging.
    pause
    exit /b 1
)

echo Device %DEVICE_ID% found and connected.
echo.

REM Install APK
echo Installing APK on device...
"%ADB_PATH%" -s %DEVICE_ID% install -r "%APK_PATH%"
if errorlevel 1 (
    echo.
    echo ERROR: Installation failed!
    pause
    exit /b 1
)

echo.
echo ========================================
echo Installation successful!
echo ========================================
echo.

