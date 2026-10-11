@echo off
setlocal
cd /d "%~dp0"
title NEXUS PC Remote - Instalacao
where py >nul 2>&1
if %errorlevel%==0 (
  py -m pip install --user pillow
  py nexus_desktop.py
) else (
  python -m pip install --user pillow
  python nexus_desktop.py
)
if errorlevel 1 (
  echo.
  echo Confira se Python 3.11+ esta instalado e tente novamente.
  pause
)
