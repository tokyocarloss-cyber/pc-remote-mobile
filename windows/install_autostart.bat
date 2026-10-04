@echo off
setlocal
set "EXE=%~dp0dist\NEXUS PC Remote.exe"
if not exist "%EXE%" (
 echo Primeiro execute build_companion.bat
 pause
 exit /b 1
)
set "STARTUP=%APPDATA%\Microsoft\Windows\Start Menu\Programs\Startup"
copy /Y "%EXE%" "%STARTUP%\NEXUS PC Remote.exe" >nul
echo NEXUS PC Remote configurado para iniciar com o Windows.
pause
