@echo off
cd /d "%~dp0"
py -m pip install --user -r requirements.txt
if errorlevel 1 exit /b 1
py companion_v2.py
if errorlevel 1 pause
