@echo off
setlocal
cd /d "%~dp0"

set "JAVAC=javac"
if exist ".jdk\bin\javac.exe" set "JAVAC=.jdk\bin\javac.exe"
set "FX_LIB=.javafx\lib"

if not exist "%FX_LIB%\javafx.controls.jar" (
  echo JavaFX SDK not found at %FX_LIB%.
  echo Download JavaFX SDK for Windows x64 and unpack it into .javafx, or run this project from the prepared workspace.
  exit /b 1
)

if not exist out mkdir out
"%JAVAC%" --module-path "%FX_LIB%" --add-modules javafx.controls,javafx.swing -encoding UTF-8 -d out src\main\java\com\shadowclash\*.java
exit /b %ERRORLEVEL%
