@echo off
title A2 Miguel - Filmes e Series no PC
color 0C

set TARGET_DIR=%USERPROFILE%\Downloads\A2-Miguel-PC

:: Cria pastas se não existirem
if not exist "%TARGET_DIR%" mkdir "%TARGET_DIR%"
if not exist "%TARGET_DIR%\Videos" mkdir "%TARGET_DIR%\Videos"
if not exist "%TARGET_DIR%\Filmes" mkdir "%TARGET_DIR%\Filmes"
if not exist "%TARGET_DIR%\Series" mkdir "%TARGET_DIR%\Series"

:: Atualiza automaticamente os arquivos mais recentes com multi-servidor e catálogo completo
if exist "%~dp0app-pc" (
    copy /Y "%~dp0app-pc\index.html" "%TARGET_DIR%\" >nul 2>&1
    copy /Y "%~dp0app-pc\style.css" "%TARGET_DIR%\" >nul 2>&1
    copy /Y "%~dp0app-pc\app.js" "%TARGET_DIR%\" >nul 2>&1
) else if exist "%~dp0index.html" (
    copy /Y "%~dp0index.html" "%TARGET_DIR%\" >nul 2>&1
    copy /Y "%~dp0style.css" "%TARGET_DIR%\" >nul 2>&1
    copy /Y "%~dp0app.js" "%TARGET_DIR%\" >nul 2>&1
)

echo ========================================================
echo        A2 MIGUEL - APLICATIVO NATIVO PARA PC
echo ========================================================
echo.
echo Abrindo em modo janela dedicada de aplicativo no Windows...
echo [Dica] Pressione a tecla D dentro do reprodutor para abrir a
echo janela de download, selecao de pasta, qualidade e nome!
echo [Dica] Use os botoes de Servidor 1, 2 e 3 caso queira alternar a transmissao!
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
