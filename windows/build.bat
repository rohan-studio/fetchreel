@echo off
echo ========================================================
echo       Fetchreel - Windows Standalone Executable Build
echo ========================================================
echo.

cd /d "%~dp0"

echo [1/3] Checking dependencies...
python -m pip install -r requirements.txt --quiet
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to install dependencies.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/3] Building standalone Fetchreel.exe with PyInstaller...
pyinstaller --clean fetchreel.spec
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] PyInstaller build failed.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [3/3] Finalizing build...
if exist "dist\Fetchreel.exe" (
    copy /Y "dist\Fetchreel.exe" "Fetchreel.exe" >nul
    echo.
    echo ========================================================
    echo  SUCCESS! Standalone Fetchreel.exe created successfully:
    echo  %~dp0Fetchreel.exe
    echo ========================================================
) else (
    echo [ERROR] Output binary not found in dist directory.
    pause
    exit /b 1
)

echo.
pause
