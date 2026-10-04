@echo off
title PC Remote Windows
cd /d "%~dp0"
echo Seu IPv4:
ipconfig | findstr /i "IPv4"
echo.
python pc_remote_server.py
pause
