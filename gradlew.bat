@echo off
where gradle >nul 2>nul
if %ERRORLEVEL% EQU 0 (
  gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle 8.13 is not installed. Run the project from Android Studio or install Gradle 8.13 and add it to PATH.
exit /b 1
