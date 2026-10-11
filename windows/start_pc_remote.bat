@echo off
cd /d "%~dp0"
py companion_v2.py
if errorlevel 1 pause
