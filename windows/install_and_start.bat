@echo off
setlocal
cd /d "%~dp0"
title PC Remote - Instalacao
echo Instalando componente de captura de tela...
py -m pip install --user pillow
if errorlevel 1 python -m pip install --user pillow
echo.
echo Liberando PC Remote no Firewall do Windows...
netsh advfirewall firewall delete rule name="PC Remote" >nul 2>&1
netsh advfirewall firewall add rule name="PC Remote" dir=in action=allow protocol=TCP localport=8765 >nul 2>&1
echo.
python pc_remote_server.py
if errorlevel 1 py pc_remote_server.py
pause
