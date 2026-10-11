@echo off
setlocal
cd /d "%~dp0"
py -m pip install --disable-pip-version-check --prefer-binary -r requirements.txt
if errorlevel 1 exit /b 1
py -m pip install --disable-pip-version-check --prefer-binary pyinstaller==6.20.0 vgamepad==0.1.0
if errorlevel 1 exit /b 1
py -c "import pystray, PIL, tkinterdnd2, mss; print('Runtime dependencies OK')"
if errorlevel 1 exit /b 1
py -m PyInstaller --noconfirm --clean --onefile --windowed --name "NEXUS PC Remote" --collect-all tkinterdnd2 --collect-all pystray --collect-all PIL --collect-all mss --collect-all cryptography --collect-all vgamepad --hidden-import pystray._win32 --hidden-import pystray._base companion_v2.py
if errorlevel 1 exit /b 1
py -m PyInstaller.utils.cliutils.archive_viewer "dist\NEXUS PC Remote.exe" -l > archive.txt
findstr /i /c:"pystray" archive.txt >nul
if errorlevel 1 exit /b 1
findstr /i /c:"PIL" archive.txt >nul
if errorlevel 1 exit /b 1
findstr /i /c:"tkinterdnd2" archive.txt >nul
if errorlevel 1 exit /b 1
findstr /i /c:"mss" archive.txt >nul
if errorlevel 1 exit /b 1
del archive.txt >nul 2>&1
echo.
echo Pronto: dist\NEXUS PC Remote.exe
pause
