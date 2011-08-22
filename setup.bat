@echo off
pushd "%CD%"
CD /D "%~dp0"

set extdesc=the Solr Extension

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
if "x%GSDLEXTS%" == "x" (
  set GSDLEXTS=!extdir!
) else (
  set GSDLEXTS=!GSDLEXTS!:!extdir!
)
endlocal & set GSDLEXTS=%GSDLEXTS%

echo +Your environment is now setup for %extdesc%

:: Back to delayed expansion to avoid problems with environment
:: variables with brackets in them, such as "Program Files (x86)"


:End

popd