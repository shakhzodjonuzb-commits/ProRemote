@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "GRADLE_VERSION=8.14"
where gradle >nul 2>nul
if not errorlevel 1 (
  gradle %*
  exit /b %ERRORLEVEL%
)
if "%GRADLE_USER_HOME%"=="" set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "INSTALL_DIR=%GRADLE_USER_HOME%\pro-remote\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=!INSTALL_DIR!\bin\gradle.bat"
if exist "!GRADLE_BIN!" goto run_gradle
set "TMP_DIR=%TEMP%\pro-remote-gradle-%RANDOM%"
mkdir "!TMP_DIR!" 2>nul
 echo Downloading Gradle !GRADLE_VERSION!...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-!GRADLE_VERSION!-bin.zip' -OutFile '!TMP_DIR!\gradle.zip'; Expand-Archive -Path '!TMP_DIR!\gradle.zip' -DestinationPath '!TMP_DIR!' -Force"
if errorlevel 1 exit /b 1
if exist "!INSTALL_DIR!" rmdir /s /q "!INSTALL_DIR!"
if not exist "!GRADLE_USER_HOME!\pro-remote" mkdir "!GRADLE_USER_HOME!\pro-remote"
move "!TMP_DIR!\gradle-!GRADLE_VERSION!" "!INSTALL_DIR!" >nul
if errorlevel 1 exit /b 1
rmdir /s /q "!TMP_DIR!"
:run_gradle
call "!GRADLE_BIN!" %*
exit /b %ERRORLEVEL%
