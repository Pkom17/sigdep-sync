@echo off
REM Run the SIGDEP-3 edge agent on Windows.
REM spring-dotenv auto-loads .\.env at startup for application properties.
REM
REM Note: Spring decides which profile is active *before* property sources
REM are attached, so SPRING_PROFILES_ACTIVE must be a real environment
REM variable. We extract that line from .env if present.
REM
REM Usage:
REM   run.bat           Run the packaged fat JAR (production-like)
REM   run.bat --dev     Run via maven (faster restart, picks up code changes)

setlocal
cd /d "%~dp0"

if not exist ".env" (
  echo ERROR: .env not found in %CD%
  echo        Copy .env.example to .env and fill in the values for this site.
  exit /b 1
)

for /f "usebackq tokens=1,* delims==" %%A in (`findstr /b /c:"SPRING_PROFILES_ACTIVE=" .env`) do (
  if not "%%B"=="" set "SPRING_PROFILES_ACTIVE=%%B"
)

if "%~1"=="--dev" (
  call mvn -B spring-boot:run
  exit /b %ERRORLEVEL%
)

set JAR=target\sigdep-sync-0.1.0-SNAPSHOT.jar
if not exist "%JAR%" (
  echo ERROR: %JAR% not found. Run 'mvn -DskipTests package' first.
  exit /b 1
)

java -jar "%JAR%"
exit /b %ERRORLEVEL%
