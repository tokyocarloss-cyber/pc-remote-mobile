@echo off
cd /d "%~dp0"
echo Use a opcao Iniciar com Windows no painel do NEXUS.
if exist "dist\NEXUS PC Remote.exe" (start "" "dist\NEXUS PC Remote.exe") else (py companion_v2.py)
