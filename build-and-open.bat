@echo off
cd /d "C:\Users\admin\OneDrive - VINACADEMY LLC\Desktop\android\app-jetpact-compose-koltin-hello"

echo Running Gradle build...
call gradlew.bat assembleDebug

REM Check if build failed
if %ERRORLEVEL% neq 0 (
    echo.
    echo ❌ Build failed with error code %ERRORLEVEL%.
    echo Please check the messages above.
    pause
    exit /b %ERRORLEVEL%
)

REM If build succeeded, open APK output directory
start "" "C:\Users\admin\OneDrive - VINACADEMY LLC\Desktop\android\app-jetpact-compose-koltin-hello\app\build\outputs\apk\debug"

echo.
echo ✅ Build completed successfully.
pause
