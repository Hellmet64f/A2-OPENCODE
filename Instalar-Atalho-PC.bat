@echo off
title A2 Miguel - Criar Atalho no Computador
color 0A
echo =========================================================
echo       A2 MIGUEL - INSTALADOR DE ATALHO PARA PC
echo =========================================================
echo.
echo Criando atalho dedicado na Area de Trabalho do Windows...

set APP_URL=https://ais-pre-6r5myv5rfy2ejl7ibgbd7v-719019642347.us-west2.run.app

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ws = New-Object -ComObject WScript.Shell; $desktop = [System.Environment]::GetFolderPath('Desktop'); $shortcutPath = [System.IO.Path]::Combine($desktop, 'A2 Miguel - Filmes e Series.lnk'); $s = $ws.CreateShortcut($shortcutPath); $edge = [System.IO.Path]::Combine($env:ProgramFiles, 'Microsoft\Edge\Application\msedge.exe'); if (!(Test-Path $edge)) { $edge = [System.IO.Path]::Combine(${env:ProgramFiles(x86)}, 'Microsoft\Edge\Application\msedge.exe') }; if (!(Test-Path $edge)) { $edge = [System.IO.Path]::Combine($env:ProgramFiles, 'Google\Chrome\Application\chrome.exe') }; if (!(Test-Path $edge)) { $edge = [System.IO.Path]::Combine(${env:ProgramFiles(x86)}, 'Google\Chrome\Application\chrome.exe') }; if (Test-Path $edge) { $s.TargetPath = $edge; $s.Arguments = '--app=' + '%APP_URL%' + ' --window-size=1280,720'; } else { $s.TargetPath = '%APP_URL%'; }; $s.Description = 'A2 Miguel - Filmes, Series e Animes'; $s.Save();"

echo.
echo =========================================================
echo  SUCESSO! Atalho criado na sua Area de Trabalho (Desktop)!
echo  Voce ja pode abrir o A2 Miguel diretamente com 2 cliques!
echo =========================================================
echo.
pause
