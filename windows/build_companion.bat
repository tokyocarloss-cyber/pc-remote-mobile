@echo off
setlocal
cd /d "%~dp0"
py -m pip install -r requirements.txt pyinstaller
if errorlevel 1 exit /b 1
py -m PyInstaller --noconfirm --clean --onefile --windowed --name "PC Remote" --collect-all tkinterdnd2 companion.py
if errorlevel 1 exit /b 1
echo.
echo Pronto: dist\PC Remote.exe
pause
