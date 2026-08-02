@echo off
set SCRIPT_DIR=%~dp0
set ROOT_DIR=%SCRIPT_DIR%
if not exist "%ROOT_DIR%gradlew.bat" (
    set ROOT_DIR=%SCRIPT_DIR%..\
)

call "%ROOT_DIR%gradlew.bat" :kindex-cli:jvmRun --args="%*" -q
