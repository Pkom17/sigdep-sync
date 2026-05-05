@echo off
REM Run the SIGDEP-3 edge agent on Windows.
REM spring-dotenv auto-loads .\.env at startup for application properties.
REM
REM Note: Spring decides which profile is active *before* property sources
REM are attached, so SPRING_PROFILES_ACTIVE must be a real environment
REM variable. Same goes for JAVA_HOME, which has to point at a JDK 17+
REM install. We extract those two lines from .env if present.
REM
REM Usage:
REM   run.bat           Run the packaged fat JAR (production-like)
REM   run.bat --dev     Run via maven (faster restart, picks up code changes)

setlocal EnableDelayedExpansion
cd /d "%~dp0"

if not exist ".env" (
  echo ERROR: .env not found in %CD%
  echo        Copy .env.example to .env and fill in the values for this site.
  exit /b 1
)

REM Pull JAVA_HOME from .env if set (overrides any system JAVA_HOME).
for /f "usebackq tokens=1,* delims==" %%A in (`findstr /b /c:"JAVA_HOME=" .env`) do (
  if not "%%B"=="" set "JAVA_HOME=%%B"
)

if defined JAVA_HOME (
  set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
) else (
  for %%I in (java.exe) do set "JAVA_BIN=%%~$PATH:I"
)

if not defined JAVA_BIN (
  echo ERROR: no java.exe found. Set JAVA_HOME in .env to a JDK 17+ install.
  exit /b 1
)
if not exist "%JAVA_BIN%" (
  echo ERROR: %JAVA_BIN% does not exist. Set JAVA_HOME in .env to a JDK 17+ install.
  exit /b 1
)

REM Check Java version >= 17. java -version writes to stderr, hence 2^>^&1.
for /f "tokens=3" %%V in ('"%JAVA_BIN%" -version 2^>^&1 ^| findstr /i "version"') do (
  set "JAVA_VERSION_RAW=%%~V"
)
for /f "tokens=1 delims=." %%M in ("!JAVA_VERSION_RAW!") do set "JAVA_MAJOR=%%M"

if not defined JAVA_MAJOR (
  echo ERROR: could not determine Java major version from "%JAVA_BIN%".
  exit /b 1
)
if !JAVA_MAJOR! LSS 17 (
  echo ERROR: %JAVA_BIN% is Java !JAVA_MAJOR!; this project requires JDK 17 or newer.
  echo        Set JAVA_HOME in .env to a JDK 17+ install.
  exit /b 1
)

REM Pull SPRING_PROFILES_ACTIVE from .env if set.
for /f "usebackq tokens=1,* delims==" %%A in (`findstr /b /c:"SPRING_PROFILES_ACTIVE=" .env`) do (
  if not "%%B"=="" set "SPRING_PROFILES_ACTIVE=%%B"
)

if "%~1"=="--dev" (
  call mvn -B spring-boot:run
  exit /b %ERRORLEVEL%
)

set "JAR=target\sigdep-sync-0.1.0-SNAPSHOT.jar"
if not exist "%JAR%" (
  echo ERROR: %JAR% not found. Run 'mvn -DskipTests package' first.
  exit /b 1
)

"%JAVA_BIN%" -jar "%JAR%"
exit /b %ERRORLEVEL%
