@echo off
title A2 Miguel - Filmes e Series no PC
color 0C

set TARGET_DIR=%USERPROFILE%\Downloads\A2-Miguel-PC

:: Se ainda não foi instalado na pasta Downloads, executa o instalador completo
if not exist "%TARGET_DIR%\index.html" (
    if exist "%~dp0Instalar-e-Rodar-no-PC.bat" (
        call "%~dp0Instalar-e-Rodar-no-PC.bat"
        exit
    )
)

echo ========================================================
echo        A2 MIGUEL - APLICATIVO NATIVO PARA PC
echo ========================================================
echo.
echo Abrindo em modo janela dedicada de aplicativo no Windows...
echo [Dica] Pressione a tecla D dentro do reprodutor para abrir a
echo janela de download, selecao de pasta, qualidade e nome!
echo.

set LOCAL_APP=file:///%TARGET_DIR:\=/%/index.html

:: 1. Tenta abrir com o Microsoft Edge no modo App dedicado (janela nativa sem abas)
if exist "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" (
    start "" "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" --app="%LOCAL_APP%" --window-size=1280,720
    exit
)

if exist "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" (
    start "" "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" --app="%LOCAL_APP%" --window-size=1280,720
    exit
)

:: 2. Tenta abrir com o Google Chrome no modo App dedicado
if exist "%ProgramFiles%\Google\Chrome\Application\chrome.exe" (
    start "" "%ProgramFiles%\Google\Chrome\Application\chrome.exe" --app="%LOCAL_APP%" --window-size=1280,720
    exit
)

if exist "%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe" (
    start "" "%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe" --app="%LOCAL_APP%" --window-size=1280,720
    exit
)

:: 3. Abre no navegador padrao
start "" "%TARGET_DIR%\index.html"
exit
