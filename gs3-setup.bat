@echo off
pushd "%CD%"
CD /D "%~dp0"

set extdesc=the Solr Extension

:: The port Jetty runs on:
set SOLR_JETTY_PORT=8983

:: The port Jetty listens on for a "stop" command
set JETTY_STOP_PORT=8079

if "%OS%" == "Windows_NT" goto WinNT
if "%OS%" == "" goto Win95
if "%GSDLLANG%" == "en" echo Setup failed - your PATH has not been set
if "%GSDLLANG%" == "es" echo No se pudo realizar la configuraciÅ¢n - no se ha establecido la RUTA.
if "%GSDLLANG%" == "fr" echo EchÇc de l'installation - votre variable PATH n'a pas ÇtÇ ajustÇe
if "%GSDLLANG%" == "ru" echo ìÅ·Å‚Å†Å≠ÅÆÅ¢Å™Å† Å≠Å• Å„Å§Å†Å´Å†Å·ÅÏ - èìíú Å≠Å• Å°ÅÎÅ´ Å„Å·Å‚Å†Å≠ÅÆÅ¢Å´Å•Å≠
goto End

:WinNT
set GEXT_SOLR=%CD%
set PATH=%GEXT_SOLR%\bin\script;%PATH%
set PATH=%GEXT_SOLR%\lib;%PATH%
set GS_CP_SET=yes
goto Success

:Win95
if "%1" == "SetEnv" goto Win95Env
REM We'll invoke a second copy of the command processor to make
REM sure there's enough environment space
COMMAND /E:2048 /K %0 SetEnv
goto End

:Win95Env
set GEXT_SOLR=%CD%
set PATH="%GEXT_SOLR%\bin\script";"%PATH%"
set PATH="%GEXT_SOLR%\lib";"%PATH%"
set GS_CP_SET=yes
goto Success

:Success

set fulldir=%~dp0

:: strip off everything up to (and including) ext dir
set extdir=%fulldir:*ext\=%

:: remove trailing slash
set extdir=%extdir:\=%

setlocal enabledelayedexpansion
if "x%GSDL3EXTS%" == "x" (
  set GSDL3EXTS=!extdir!
) else (
  set GSDL3EXTS=!GSDL3EXTS!:!extdir!
)
endlocal & set GSDL3EXTS=%GSDL3EXTS%

echo +Your environment is now setup for %extdesc%

:: Back to delayed expansion to avoid problems with environment
:: variables with brackets in them, such as "Program Files (x86)"

echo ++Solr/Jetty server will run on port %SOLR_JETTY_PORT% (+ port %JETTY_STOP_PORT% for shutdown command)"
echo --These port values can be changed by editing:
echo --  %0



:End

popd