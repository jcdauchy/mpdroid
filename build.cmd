@echo off
echo Building MPDroid with Gradle...
call gradlew.bat assembleDebug
if %ERRORLEVEL% EQU 0 (
    echo.
    echo Build successful!
) else (
    echo.
    echo Build failed!
    pause
)
