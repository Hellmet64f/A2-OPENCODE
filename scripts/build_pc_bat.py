#!/usr/bin/env python3
"""
Gera o arquivo único autossuficiente A2-Miguel-PC.bat a partir de:
  - scripts/server.ps1
  - app-pc/index.html
  - app-pc/style.css
  - app-pc/app.js

Quando o usuário baixa o .zip do GitHub e extrai, existe APENAS A2-Miguel-PC.bat.
Ao dar dois cliques nele no Windows:
  1. Cria %USERPROFILE%\Downloads\A2-Miguel-PC\{app,dados,Videos\Filmes,Videos\Series}
  2. Grava os arquivos decodificados via PowerShell (UTF-8)
  3. Cria o atalho oficial "A2 Miguel PC.lnk" na Área de Trabalho (Desktop)
  4. Escolhe uma porta livre (18420..18440), inicia server.ps1 em segundo plano sem poluir a tela
  5. Abre o app dedicado no Microsoft Edge (--app=...) ou Google Chrome (--app=...) ou navegador padrão.
"""
import base64
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

FILES = {
    "SERVER_PS1_B64": ROOT / "scripts" / "server.ps1",
    "INDEX_HTML_B64": ROOT / "app-pc" / "index.html",
    "STYLE_CSS_B64": ROOT / "app-pc" / "style.css",
    "APP_JS_B64": ROOT / "app-pc" / "app.js",
}


def encode_file(path: Path) -> str:
    raw = path.read_bytes()
    return base64.b64encode(raw).decode("ascii")


def build_bat() -> str:
    payloads = {k: encode_file(p) for k, p in FILES.items()}

    # O código .bat instala tudo na pasta Downloads\A2-Miguel-PC e usa PowerShell para
    # decodificar os payloads embutidos logo abaixo após o marcador :::PAYLOAD_END:::
    bat_lines = [
        "@echo off",
        "chcp 65001 >nul",
        "setlocal EnableDelayedExpansion",
        "title A2 Miguel PC - Instalador e Launcher Automatico",
        'set "INSTALL_DIR=%USERPROFILE%\\Downloads\\A2-Miguel-PC"',
        'set "APP_DIR=%INSTALL_DIR%\\app"',
        'set "DATA_DIR=%INSTALL_DIR%\\dados"',
        'set "VIDEOS_DIR=%INSTALL_DIR%\\Videos"',
        "",
        "echo.",
        "echo  =========================================================",
        "echo    A2 MIGUEL PC - Instalando e iniciando automaticamente",
        "echo  =========================================================",
        "echo  Pasta no computador: %INSTALL_DIR%",
        "echo.",
        "",
        'if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"',
        'if not exist "%APP_DIR%" mkdir "%APP_DIR%"',
        'if not exist "%DATA_DIR%" mkdir "%DATA_DIR%"',
        'if not exist "%VIDEOS_DIR%\\Filmes" mkdir "%VIDEOS_DIR%\\Filmes"',
        'if not exist "%VIDEOS_DIR%\\Series" mkdir "%VIDEOS_DIR%\\Series"',
        "",
        'copy /Y "%~f0" "%INSTALL_DIR%\\A2-Miguel-PC.bat" >nul 2>&1',
        'set "A2_BAT_SRC=%~f0"',
        "",
        'powershell -NoProfile -ExecutionPolicy Bypass -Command "'
        "$batPath = $env:A2_BAT_SRC; "
        "$installDir = $env:INSTALL_DIR; "
        "$appDir = $env:APP_DIR; "
        "$lines = Get-Content -Path $batPath -Encoding UTF8; "
        "$map = @{}; "
        "foreach ($ln in $lines) { "
        "  if ($ln -match '^::B64:([A-Z0-9_]+):(.*)$') { $map[$matches[1]] = $matches[2] } "
        "}; "
        "function WriteB64($key, $dest) { "
        "  if ($map.ContainsKey($key)) { "
        "    [System.IO.File]::WriteAllBytes($dest, [System.Convert]::FromBase64String($map[$key])) "
        "  } "
        "}; "
        "WriteB64 'SERVER_PS1_B64' (Join-Path $installDir 'server.ps1'); "
        "WriteB64 'INDEX_HTML_B64' (Join-Path $appDir 'index.html'); "
        "WriteB64 'STYLE_CSS_B64' (Join-Path $appDir 'style.css'); "
        "WriteB64 'APP_JS_B64' (Join-Path $appDir 'app.js'); "
        "$desktop = [Environment]::GetFolderPath('Desktop'); "
        "$lnkPath = Join-Path $desktop 'A2 Miguel PC.lnk'; "
        "try { "
        "  $ws = New-Object -ComObject WScript.Shell; "
        "  $sc = $ws.CreateShortcut($lnkPath); "
        "  $sc.TargetPath = (Join-Path $installDir 'A2-Miguel-PC.bat'); "
        "  $sc.WorkingDirectory = $installDir; "
        "  $sc.Description = 'A2 Miguel - Filmes e Series no PC'; "
        "  $sc.Save(); "
        "} catch {}; "
        "$port = 18420; "
        "for ($p = 18420; $p -le 18440; $p++) { "
        "  try { "
        "    $tcp = New-Object System.Net.Sockets.TcpClient; "
        "    $ar = $tcp.BeginConnect('127.0.0.1', $p, $null, $null); "
        "    $ok = $ar.AsyncWaitHandle.WaitOne(120, $false); "
        "    if ($ok -and $tcp.Connected) { $tcp.Close(); continue } "
        "    $tcp.Close(); $port = $p; break "
        "  } catch { $port = $p; break } "
        "}; "
        "$srvScript = Join-Path $installDir 'server.ps1'; "
        "Start-Process -FilePath 'powershell.exe' -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-WindowStyle','Hidden','-File',$srvScript,'-Port',$port,'-InstallDir',$installDir) -WindowStyle Hidden; "
        "Start-Sleep -Milliseconds 1200; "
        "$appUrl = ('http://127.0.0.1:' + $port + '/'); "
        "$edge = @((Join-Path $env:ProgramFiles 'Microsoft\\Edge\\Application\\msedge.exe'), (Join-Path ${env:ProgramFiles(x86)} 'Microsoft\\Edge\\Application\\msedge.exe')) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1; "
        "$chrome = @((Join-Path $env:ProgramFiles 'Google\\Chrome\\Application\\chrome.exe'), (Join-Path ${env:ProgramFiles(x86)} 'Google\\Chrome\\Application\\chrome.exe'), (Join-Path $env:LocalAppData 'Google\\Chrome\\Application\\chrome.exe')) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1; "
        "if ($edge) { Start-Process $edge -ArgumentList ('--app=' + $appUrl) } "
        "elseif ($chrome) { Start-Process $chrome -ArgumentList ('--app=' + $appUrl) } "
        "else { Start-Process $appUrl }"
        '"',
        "",
        "echo  Pronto! O A2 Miguel PC foi aberto e o atalho foi criado na Area de Trabalho.",
        "timeout /t 3 >nul",
        "exit /b 0",
        "",
        ":::A2_MIGUEL_EMBEDDED_PAYLOADS:::",
    ]

    for key, b64 in payloads.items():
        bat_lines.append(f"::B64:{key}:{b64}")

    return "\r\n".join(bat_lines) + "\r\n"


def main():
    out_path = ROOT / "A2-Miguel-PC.bat"
    content = build_bat()
    out_path.write_bytes(content.encode("utf-8"))
    print(f"Generated {out_path} ({len(content)} bytes)")


if __name__ == "__main__":
    main()
