@echo off
cd /d "%~dp0"
title NEXUS PC Remote
py nexus_desktop.py
if errorlevel 1 python nexus_desktop.py
if errorlevel 1 pause
