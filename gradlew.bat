@echo off
setlocal
set "GRADLE_VERSION=9.5.0"
set "GRADLE_SHA256=553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746"
if not defined GRADLE_USER_HOME set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "DIST_ROOT=%GRADLE_USER_HOME%\wrapper\dists\spoolmaker-gradle-%GRADLE_VERSION%"
set "DIST_ZIP=%DIST_ROOT%\gradle-%GRADLE_VERSION%-bin.zip"
set "GRADLE_BIN=%DIST_ROOT%\gradle-%GRADLE_VERSION%\bin\gradle.bat"
set "DIST_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"

if not exist "%GRADLE_BIN%" (
    if not exist "%DIST_ROOT%" mkdir "%DIST_ROOT%"
    echo Downloading and verifying Gradle %GRADLE_VERSION% ...
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
      "$ErrorActionPreference='Stop';" ^
      "if (-not (Test-Path -LiteralPath '%DIST_ZIP%')) { Invoke-WebRequest -UseBasicParsing -Uri '%DIST_URL%' -OutFile '%DIST_ZIP%' };" ^
      "$actual=(Get-FileHash -Algorithm SHA256 -LiteralPath '%DIST_ZIP%').Hash.ToLowerInvariant();" ^
      "if ($actual -ne '%GRADLE_SHA256%') { Remove-Item -Force -LiteralPath '%DIST_ZIP%'; throw 'Gradle archive checksum mismatch.' };" ^
      "if (Test-Path -LiteralPath '%DIST_ROOT%\gradle-%GRADLE_VERSION%') { Remove-Item -Recurse -Force -LiteralPath '%DIST_ROOT%\gradle-%GRADLE_VERSION%' };" ^
      "Expand-Archive -Force -LiteralPath '%DIST_ZIP%' -DestinationPath '%DIST_ROOT%'"
    if errorlevel 1 exit /b 1
)

call "%GRADLE_BIN%" %*
exit /b %ERRORLEVEL%
