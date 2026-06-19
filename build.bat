@echo off
setlocal
cd /d "%~dp0"

set "JAVAC=javac"
if exist ".jdk\bin\javac.exe" set "JAVAC=.jdk\bin\javac.exe"

if not exist out mkdir out
"%JAVAC%" -encoding UTF-8 -d out src\main\java\com\shadowclash\ShadowClash.java
exit /b %ERRORLEVEL%
