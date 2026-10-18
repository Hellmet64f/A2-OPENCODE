@echo off
title A2 Miguel - Instalador e Inicializador no PC
color 0C

echo ======================================================================
echo                  A2 MIGUEL - INSTALADOR OFICIAL PARA PC
echo ======================================================================
echo.
echo 1. Criando pasta dedicada em Downloads...

set TARGET_DIR=%USERPROFILE%\Downloads\A2-Miguel-PC

if not exist "%TARGET_DIR%" mkdir "%TARGET_DIR%"
if not exist "%TARGET_DIR%\Videos" mkdir "%TARGET_DIR%\Videos"
if not exist "%TARGET_DIR%\Filmes" mkdir "%TARGET_DIR%\Filmes"
if not exist "%TARGET_DIR%\Series" mkdir "%TARGET_DIR%\Series"

echo [OK] Pasta configurada em: %TARGET_DIR%
echo.
echo 2. Copiando arquivos do aplicativo A2 Miguel...

if exist "%~dp0app-pc" (
    copy /Y "%~dp0app-pc\index.html" "%TARGET_DIR%\" >nul
    copy /Y "%~dp0app-pc\style.css" "%TARGET_DIR%\" >nul
    copy /Y "%~dp0app-pc\app.js" "%TARGET_DIR%\" >nul
    echo [OK] Arquivos copiados da pasta app-pc!
) else if exist "%~dp0index.html" (
    copy /Y "%~dp0index.html" "%TARGET_DIR%\" >nul
    copy /Y "%~dp0style.css" "%TARGET_DIR%\" >nul
    copy /Y "%~dp0app.js" "%TARGET_DIR%\" >nul
    echo [OK] Arquivos copiados do diretorio local!
)

:: Copia tambem o proprio executavel para dentro da pasta
copy /Y "%~f0" "%TARGET_DIR%\Abrir-no-PC.bat" >nul 2>&1

echo.
echo 3. Criando atalho na Area de Trabalho (Desktop)...

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ws = New-Object -ComObject WScript.Shell; $desktop = [System.Environment]::GetFolderPath('Desktop'); $shortcutPath = [System.IO.Path]::Combine($desktop, 'A2 Miguel - PC.lnk'); $s = $ws.CreateShortcut($shortcutPath); $localHtml = [System.IO.Path]::Combine($env:USERPROFILE, 'Downloads\A2-Miguel-PC\index.html'); $edge = [System.IO.Path]::Combine($env:ProgramFiles, 'Microsoft\Edge\Application\msedge.exe'); if (!(Test-Path $edge)) { $edge = [System.IO.Path]::Combine(${env:ProgramFiles(x86)}, 'Microsoft\Edge\Application\msedge.exe') }; if (!(Test-Path $edge)) { $edge = [System.IO.Path]::Combine($env:ProgramFiles, 'Google\Chrome\Application\chrome.exe') }; if (Test-Path $edge) { $s.TargetPath = $edge; $s.Arguments = '--app=\"file:///' + $localHtml.Replace('\', '/') + '\" --window-size=1280,720'; } else { $s.TargetPath = $localHtml; }; $s.Description = 'A2 Miguel - Filmes, Series e Animes'; $s.Save();"

echo [OK] Atalho 'A2 Miguel - PC' criado na Area de Trabalho!
echo.
echo ======================================================================
echo       TUDO PRONTO! ABRINDO APLICATIVO EM JANELA NATIVA DEDICADA...
echo ======================================================================
echo.

set LOCAL_APP=file:///%TARGET_DIR:\=/%/index.html

:: 1. Tenta abrir no Microsoft Edge em modo janela nativa de aplicativo
if exist "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" (
    start "" "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" --app="%LOCAL_APP%" --window-size=1280,720
    exit
)

if exist "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" (
    start "" "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" --app="%LOCAL_APP%" --window-size=1280,720
    exit
)

:: 2. Tenta abrir no Google Chrome em modo janela nativa de aplicativo
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
