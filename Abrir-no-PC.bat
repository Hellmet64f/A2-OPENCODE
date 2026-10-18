@echo off
title A2 Miguel - Filmes e Series
color 0C
echo ========================================================
echo        A2 MIGUEL - INICIANDO APLICATIVO NO PC
echo ========================================================
echo.
echo Abrindo em modo aplicativo nativo no seu Windows...
echo.

set APP_URL=https://ais-pre-6r5myv5rfy2ejl7ibgbd7v-719019642347.us-west2.run.app

:: 1. Tenta abrir com o Microsoft Edge no modo App dedicado (janela nativa sem abas)
if exist "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" (
    start "" "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" --app=%APP_URL% --window-size=1280,720
    exit
)

if exist "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" (
    start "" "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" --app=%APP_URL% --window-size=1280,720
    exit
)

:: 2. Tenta abrir com o Google Chrome no modo App dedicado
if exist "%ProgramFiles%\Google\Chrome\Application\chrome.exe" (
    start "" "%ProgramFiles%\Google\Chrome\Application\chrome.exe" --app=%APP_URL% --window-size=1280,720
    exit
)

if exist "%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe" (
    start "" "%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe" --app=%APP_URL% --window-size=1280,720
    exit
)

:: 3. Abre no navegador padrao
start %APP_URL%
exit
