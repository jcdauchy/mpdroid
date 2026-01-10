@echo off
REM Build MPDroid Release APK
REM This script builds a signed release APK for distribution

setlocal enabledelayedexpansion

echo ========================================
echo MPDroid Release Builder
echo ========================================
echo.

REM Check if keystore.properties exists
if not exist "keystore.properties" (
    echo WARNING: keystore.properties not found!
    echo.
    echo The release build requires a keystore for signing.
    echo Please create keystore.properties based on keystore.properties.template
    echo.
    echo The build will continue but may fail if signing is required.
    echo.
    pause
)

REM Clean previous build (optional, comment out if you want incremental builds)
echo Cleaning previous build artifacts...
call gradlew.bat clean
if errorlevel 1 (
    echo WARNING: Clean failed, continuing anyway...
    echo.
)

REM Build release APK
echo ========================================
echo Building Release APK...
echo ========================================
echo.

call gradlew.bat :MPDroid:assembleRelease

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo Build successful!
    echo ========================================
    echo.
    
    REM Check if APK was created
    set "APK_PATH=MPDroid\build\outputs\apk\release\MPDroid-release.apk"
    set "FULL_APK_PATH=%CD%\%APK_PATH%"
    
    if exist "!APK_PATH!" (
        echo Release APK location:
        echo   !FULL_APK_PATH!
        echo.
        
        REM Get file size
        for %%A in ("!APK_PATH!") do (
            set "SIZE=%%~zA"
            set /a SIZE_MB=!SIZE!/1024/1024
            echo File size: !SIZE_MB! MB ^(!SIZE! bytes^)
        )
        echo.
        
        echo To install on device, use:
        echo   install_release.cmd --no-build
        echo.
        echo Or manually:
        echo   adb install -r "!APK_PATH!"
        echo.
    ) else (
        echo WARNING: APK file not found at expected location: !APK_PATH!
        echo.
        echo Checking for alternative APK locations...
        for /r MPDroid\build\outputs\apk %%F in (*.apk) do (
            echo   %%F
            set "FOUND_APK=%%F"
        )
        if defined FOUND_APK (
            echo.
            echo Found APK at: !FOUND_APK!
        )
        echo.
    )
) else (
    echo.
    echo ========================================
    echo Build failed!
    echo ========================================
    echo.
    echo Check the error messages above for details.
    echo.
    pause
    exit /b 1
)
