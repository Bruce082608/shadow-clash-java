@echo off
setlocal
cd /d "%~dp0"

set "JAVAC=javac"
set "JAVA=java"
if exist ".jdk\bin\javac.exe" set "JAVAC=.jdk\bin\javac.exe"
if exist ".jdk\bin\java.exe" set "JAVA=.jdk\bin\java.exe"

if not exist out mkdir out
"%JAVAC%" -encoding UTF-8 -d out src\main\java\com\shadowclash\ShadowClash.java
if errorlevel 1 exit /b %ERRORLEVEL%
"%JAVA%" -cp out com.shadowclash.ShadowClash
