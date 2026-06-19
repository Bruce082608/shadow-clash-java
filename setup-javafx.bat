@echo off
setlocal
cd /d "%~dp0"

set "FX_VERSION=17.0.19"
set "FX_ZIP=.javafx-download\openjfx-%FX_VERSION%_windows-x64_bin-sdk.zip"
set "FX_URL=https://download2.gluonhq.com/openjfx/%FX_VERSION%/openjfx-%FX_VERSION%_windows-x64_bin-sdk.zip"

if exist ".javafx\lib\javafx.controls.jar" (
  echo JavaFX SDK already exists in .javafx.
  exit /b 0
)

if not exist ".javafx-download" mkdir ".javafx-download"
echo Downloading JavaFX SDK %FX_VERSION%...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri '%FX_URL%' -OutFile '%FX_ZIP%'"
if errorlevel 1 exit /b %ERRORLEVEL%

echo Extracting JavaFX SDK...
if exist ".javafx" rmdir /s /q ".javafx"
if exist ".javafx-unpacked" rmdir /s /q ".javafx-unpacked"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force -Path '%FX_ZIP%' -DestinationPath '.javafx-unpacked'; $dir = Get-ChildItem '.javafx-unpacked' -Directory | Select-Object -First 1; Move-Item -Force $dir.FullName '.javafx'"
if errorlevel 1 exit /b %ERRORLEVEL%
if exist ".javafx-unpacked" rmdir /s /q ".javafx-unpacked"

echo JavaFX SDK is ready.
