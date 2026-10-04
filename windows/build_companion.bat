@echo off
setlocal
cd /d "%~dp0"
set "VGAMEPAD_SKIP_VIGEMBUS_INSTALL=true"
py -m pip install --disable-pip-version-check --prefer-binary -r requirements.txt
if errorlevel 1 exit /b 1
py -m pip install --disable-pip-version-check --prefer-binary pyinstaller
if errorlevel 1 exit /b 1
py -m PyInstaller --noconfirm --clean --onefile --windowed --name "NEXUS PC Remote" --collect-all tkinterdnd2 --collect-all vgamepad companion.py
if errorlevel 1 exit /b 1
echo.
echo Pronto: dist\NEXUS PC Remote.exe
pause
