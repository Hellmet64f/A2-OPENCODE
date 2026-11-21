param(
    [int]$Port = 18420,
    [string]$InstallDir = ""
)

$ErrorActionPreference = "Continue"
Add-Type -AssemblyName System.Web
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12

if ([string]::IsNullOrWhiteSpace($InstallDir)) {
    $InstallDir = Split-Path -Parent $MyInvocation.MyCommand.Path
}

$AppDir = Join-Path $InstallDir "app"
$DataDir = Join-Path $InstallDir "dados"
$VideosDir = Join-Path $InstallDir "Videos"
$FilmesDir = Join-Path $VideosDir "Filmes"
$SeriesDir = Join-Path $VideosDir "Series"
$DataFile = Join-Path $DataDir "a2_dados.json"
$AdBlockFile = Join-Path $DataDir "adblock_rules.txt"

foreach ($d in @($AppDir, $DataDir, $VideosDir, $FilmesDir, $SeriesDir)) {
    if (-not (Test-Path $d)) {
        New-Item -ItemType Directory -Path $d -Force | Out-Null
    }
}

$Script:BaseUrl = "https://www.pobreflixtv.delivery"
$Script:UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

$Script:BaselineAdRules = @(
    "googlesyndication.com", "doubleclick.net", "googleadservices.com", "google-analytics.com",
    "googletagmanager.com", "adservice.google.com", "pagead2.googlesyndication.com", "popads.net",
    "popcash.net", "propellerads.com", "propellerclick.com", "onclickads.net", "exoclick.com",
    "exosrv.com", "realsrv.com", "syndication.realsrv.com", "adsterra.com", "highperformanceformat.com",
    "effectivegatecpm.com", "monetag.com", "al5sm.com", "trafficjunky.net", "trafficfactory.biz",
    "adcash.com", "acscdn.com", "yllix.com", "taboola.com", "outbrain.com", "mgid.com",
    "revcontent.com", "juicyads.com", "plugrush.com", "hilltopads.net", "richads.com", "clickadu.com",
    "admaven.com", "bidvertiser.com", "infolinks.com", "criteo.com", "criteo.net", "pubmatic.com",
    "rubiconproject.com", "openx.net", "appnexus.com", "adnxs.com", "smartadserver.com",
    "mediavine.com", "adthrive.com", "1xbet", "bet365", "betano", "blaze.com", "sportingbet",
    "pixbet", "estrelabet", "cassino", "casino", "aposta", "tigrinho", "fortune-tiger",
    "oldnessdernly.qpon", "parklogic.com", "pushwoosh.com", "onesignal.com", "hotjar.com",
    "histats.com", "amung.us", "whos.amung.us", "adf.ly", "ouo.io", "shorte.st", "linkvertise.com"
)

$Script:AdRules = New-Object System.Collections.Generic.HashSet[string]
foreach ($r in $Script:BaselineAdRules) { [void]$Script:AdRules.Add($r.ToLowerInvariant()) }
$Script:BlockedCount = 0
$Script:LastAdblockUpdate = 0

if (Test-Path $AdBlockFile) {
    try {
        Get-Content -Path $AdBlockFile -Encoding UTF8 | ForEach-Object {
            $line = $_.Trim().ToLowerInvariant()
            if ($line.Length -ge 4 -and -not $line.StartsWith("#")) {
                [void]$Script:AdRules.Add($line)
            }
        }
        $Script:LastAdblockUpdate = [int64]((Get-Item $AdBlockFile).LastWriteTimeUtc - (Get-Date "1970-01-01")).TotalMilliseconds
    } catch {}
}

function Update-AdBlockRules {
    $sources = @(
        "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=plain&showintro=0&mimetype=plaintext",
        "https://raw.githubusercontent.com/AdAway/adaway.github.io/master/hosts.txt"
    )
    $added = New-Object System.Collections.Generic.List[string]
    foreach ($src in $sources) {
        try {
            $wc = New-Object System.Net.WebClient
            $wc.Encoding = [System.Text.Encoding]::UTF8
            $wc.Headers.Add("User-Agent", "A2Miguel-AdBlock/2.0")
            $body = $wc.DownloadString($src)
            foreach ($raw in ($body -split "`n")) {
                if ($added.Count -ge 1500) { break }
                $line = $raw.Trim().ToLowerInvariant()
                if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#") -or $line.StartsWith("!")) { continue }
                $line = ($line -split "#")[0].Trim()
                $parts = $line -split "\s+"
                $dom = ""
                if ($parts.Length -ge 2 -and ($parts[0] -eq "127.0.0.1" -or $parts[0] -eq "0.0.0.0")) {
                    $dom = $parts[1]
                } elseif ($parts.Length -eq 1) {
                    $dom = $parts[0].TrimStart("|").Split("^")[0]
                }
                if ($dom -and $dom -ne "localhost" -and $dom.Contains(".") -and $dom.Length -ge 4 -and $dom.Length -le 100) {
                    if ($dom -notmatch "pobreflixtv|tmdb\.org|byse|mixdrop|streamtape|googleapis|gstatic") {
                        if ($Script:AdRules.Add($dom)) {
                            [void]$added.Add($dom)
                        }
                    }
                }
            }
        } catch {}
    }
    if ($added.Count -gt 0) {
        try {
            $Script:AdRules | Set-Content -Path $AdBlockFile -Encoding UTF8
            $Script:LastAdblockUpdate = [int64](([DateTime]::UtcNow) - (Get-Date "1970-01-01")).TotalMilliseconds
        } catch {}
    }
    return $Script:AdRules.Count
}

if (-not (Test-Path $AdBlockFile)) {
    [void](Update-AdBlockRules)
}

function Normalize-MediaUrl([string]$raw) {
    if ([string]::IsNullOrWhiteSpace($raw)) { return "" }
    $t = $raw.Trim().Replace("pobreflixtv.city", "pobreflixtv.delivery")
    if ($t.StartsWith("http://") -or $t.StartsWith("https://")) { return $t }
    if ($t.StartsWith("//")) { return "https:$t" }
    if ($t.StartsWith("/")) { return "$($Script:BaseUrl)$t" }
    return "$($Script:BaseUrl)/$t"
}

function Clean-HtmlText([string]$raw) {
    if ([string]::IsNullOrEmpty($raw)) { return "" }
    $noTags = [System.Text.RegularExpressions.Regex]::Replace($raw, "<[^>]+>", " ")
    $decoded = [System.Net.WebUtility]::HtmlDecode($noTags)
    return ([System.Text.RegularExpressions.Regex]::Replace($decoded, "\s+", " ")).Trim()
}

function Fetch-WebText([string]$url, [string]$referer = "") {
    $req = [System.Net.HttpWebRequest]::Create($url)
    $req.Method = "GET"
    $req.UserAgent = $Script:UserAgent
    $req.Timeout = 15000
    $req.ReadWriteTimeout = 15000
    $req.AllowAutoRedirect = $true
    $req.AutomaticDecompression = [System.Net.DecompressionMethods]::GZip -bor [System.Net.DecompressionMethods]::Deflate
    if ($referer) { $req.Referer = $referer }
    $resp = $req.GetResponse()
    try {
        $stream = $resp.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
        return $reader.ReadToEnd()
    } finally {
        $resp.Close()
    }
}

function Post-FormText([string]$url, [string]$formBody, [string]$referer = "") {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($formBody)
    $req = [System.Net.HttpWebRequest]::Create($url)
    $req.Method = "POST"
    $req.ContentType = "application/x-www-form-urlencoded; charset=UTF-8"
    $req.ContentLength = $bytes.Length
    $req.UserAgent = $Script:UserAgent
    $req.Timeout = 15000
    $req.Headers.Add("X-Requested-With", "XMLHttpRequest")
    if ($referer) { $req.Referer = $referer }
    $reqStream = $req.GetRequestStream()
    $reqStream.Write($bytes, 0, $bytes.Length)
    $reqStream.Close()
    $resp = $req.GetResponse()
    try {
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream(), [System.Text.Encoding]::UTF8)
        return $reader.ReadToEnd()
    } finally {
        $resp.Close()
    }
}

function Parse-CardsFromHtml([string]$html) {
    $list = New-Object System.Collections.Generic.List[object]
    $seen = New-Object System.Collections.Generic.HashSet[string]
    if ([string]::IsNullOrWhiteSpace($html)) { return @() }

    $linkMatches = [System.Text.RegularExpressions.Regex]::Matches($html, '<a[^>]+href=["'']([^"'']+)["''][^>]*>(.*?)</a>', [System.Text.RegularExpressions.RegexOptions]::Singleline -bor [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    foreach ($m in $linkMatches) {
        $href = Normalize-MediaUrl $m.Groups[1].Value
        if (-not ($href.Contains("/filmes/") -or $href.Contains("/series/") -or $href.Contains("/episodios/"))) { continue }
        $inner = $m.Groups[2].Value
        $imgM = [System.Text.RegularExpressions.Regex]::Match($inner, '<img[^>]+(?:src|data-src|data-lazy-src)=["'']([^"'']+)["'']', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        if (-not $imgM.Success) { continue }
        $cover = Normalize-MediaUrl $imgM.Groups[1].Value
        if ([string]::IsNullOrWhiteSpace($cover) -or $cover.StartsWith("data:image")) { continue }

        $altM = [System.Text.RegularExpressions.Regex]::Match($inner, 'alt=["'']([^"'']+)["'']', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        $titleM = [System.Text.RegularExpressions.Regex]::Match($inner, 'title=["'']([^"'']+)["'']', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        $rawTitle = $inner
        if ($altM.Success -and $altM.Groups[1].Value.Trim()) { $rawTitle = $altM.Groups[1].Value }
        elseif ($titleM.Success -and $titleM.Groups[1].Value.Trim()) { $rawTitle = $titleM.Groups[1].Value }

        $title = Clean-HtmlText $rawTitle
        if ([string]::IsNullOrWhiteSpace($title)) { $title = "Sem Título" }

        $yearM = [System.Text.RegularExpressions.Regex]::Match($inner, '\b(19\d{2}|20\d{2})\b')
        $year = if ($yearM.Success) { $yearM.Groups[1].Value } else { "" }

        if ($seen.Add($href)) {
            [void]$list.Add(@{
                title = $title
                cover = $cover
                url = $href
                isSeries = ($href.Contains("/series/") -or $href.Contains("/episodios/"))
                year = $year
            })
        }
    }
    return $list.ToArray()
}

function Send-JsonResponse($ctx, $obj, [int]$statusCode = 200) {
    $json = $obj | ConvertTo-Json -Depth 8
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $ctx.Response.StatusCode = $statusCode
    $ctx.Response.ContentType = "application/json; charset=utf-8"
    $ctx.Response.Headers.Add("Cache-Control", "no-store")
    $ctx.Response.ContentLength64 = $bytes.Length
    $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
    $ctx.Response.Close()
}

function Send-TextResponse($ctx, [string]$text, [string]$contentType = "text/html; charset=utf-8", [int]$statusCode = 200) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($text)
    $ctx.Response.StatusCode = $statusCode
    $ctx.Response.ContentType = $contentType
    $ctx.Response.ContentLength64 = $bytes.Length
    $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
    $ctx.Response.Close()
}

function Read-RequestBody($ctx) {
    $reader = New-Object System.IO.StreamReader($ctx.Request.InputStream, [System.Text.Encoding]::UTF8)
    return $reader.ReadToEnd()
}

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://127.0.0.1:$Port/")
$listener.Start()
Write-Host "[A2 Miguel PC] Servidor iniciado em http://127.0.0.1:$Port/"

while ($listener.IsListening) {
    try {
        $ctx = $listener.GetContext()
        $path = $ctx.Request.Url.AbsolutePath
        $query = [System.Web.HttpUtility]::ParseQueryString($ctx.Request.Url.Query)

        if ($path -eq "/" -or $path -eq "/index.html") {
            $html = [System.IO.File]::ReadAllText((Join-Path $AppDir "index.html"), [System.Text.Encoding]::UTF8)
            Send-TextResponse $ctx $html "text/html; charset=utf-8"
        }
        elseif ($path -eq "/style.css") {
            $css = [System.IO.File]::ReadAllText((Join-Path $AppDir "style.css"), [System.Text.Encoding]::UTF8)
            Send-TextResponse $ctx $css "text/css; charset=utf-8"
        }
        elseif ($path -eq "/app.js") {
            $js = [System.IO.File]::ReadAllText((Join-Path $AppDir "app.js"), [System.Text.Encoding]::UTF8)
            Send-TextResponse $ctx $js "application/javascript; charset=utf-8"
        }
        elseif ($path -eq "/api/health") {
            Send-JsonResponse $ctx @{
                ok = $true
                installDir = $InstallDir
                adblockRules = $Script:AdRules.Count
            }
        }
        elseif ($path -eq "/api/adblock/status") {
            Send-JsonResponse $ctx @{
                count = $Script:AdRules.Count
                baselineCount = $Script:BaselineAdRules.Count
                blockedCount = $Script:BlockedCount
                lastUpdate = $Script:LastAdblockUpdate
                autoUpdate = $true
                dataFile = $DataFile
            }
        }
        elseif ($path -eq "/api/adblock/update") {
            $count = Update-AdBlockRules
            Send-JsonResponse $ctx @{
                count = $count
                baselineCount = $Script:BaselineAdRules.Count
                blockedCount = $Script:BlockedCount
                lastUpdate = $Script:LastAdblockUpdate
                autoUpdate = $true
                dataFile = $DataFile
            }
        }
        elseif ($path -eq "/api/data") {
            if ($ctx.Request.HttpMethod -eq "POST") {
                $body = Read-RequestBody $ctx
                if (-not [string]::IsNullOrWhiteSpace($body)) {
                    [System.IO.File]::WriteAllText($DataFile, $body, [System.Text.Encoding]::UTF8)
                }
                Send-JsonResponse $ctx @{ saved = $true; path = $DataFile }
            } else {
                if (Test-Path $DataFile) {
                    $rawJson = [System.IO.File]::ReadAllText($DataFile, [System.Text.Encoding]::UTF8)
                    Send-TextResponse $ctx $rawJson "application/json; charset=utf-8"
                } else {
                    Send-JsonResponse $ctx @{
                        app = "A2 Miguel"
                        version = 2
                        history = @()
                        recentSearches = @()
                        preferences = @{ zoomPreset = "normal"; zoomScale = 1.0; adBlockAutoUpdate = $true }
                    }
                }
            }
        }
        elseif ($path -eq "/api/catalog") {
            $cat = $query["category"]
            $page = 1
            if ($query["page"]) { [void][int]::TryParse($query["page"], [ref]$page) }
            if ($page -lt 1) { $page = 1 }
            $targetUrl = "$($Script:BaseUrl)/"
            if ($cat -eq "filmes") {
                $targetUrl = if ($page -gt 1) { "$($Script:BaseUrl)/filmes/page/$page/" } else { "$($Script:BaseUrl)/filmes/" }
            } elseif ($cat -eq "series") {
                $targetUrl = if ($page -gt 1) { "$($Script:BaseUrl)/series/page/$page/" } else { "$($Script:BaseUrl)/series/" }
            }
            $items = @()
            try {
                $html = Fetch-WebText $targetUrl $Script:BaseUrl
                $items = Parse-CardsFromHtml $html
            } catch {}
            Send-JsonResponse $ctx @{ items = $items; page = $page; category = $cat }
        }
        elseif ($path -eq "/api/search") {
            $q = ($query["q"] | Out-String).Trim()
            $items = @()
            if ($q) {
                try {
                    $ajaxUrl = "$($Script:BaseUrl)/index.php?app=videobox&module=video&controller=index&do=buscarContent"
                    $encQ = [System.Uri]::EscapeDataString($q)
                    $rawResp = Post-FormText $ajaxUrl "q=$encQ" "$($Script:BaseUrl)/buscar/"
                    $parsed = $rawResp | ConvertFrom-Json
                    if ($parsed -and $parsed.html) {
                        $items = Parse-CardsFromHtml $parsed.html
                    }
                } catch {}
                if ($items.Length -eq 0) {
                    try {
                        $encQ = [System.Uri]::EscapeDataString($q)
                        $html = Fetch-WebText "$($Script:BaseUrl)/buscar/?q=$encQ" $Script:BaseUrl
                        $items = Parse-CardsFromHtml $html
                    } catch {}
                }
            }
            Send-JsonResponse $ctx @{ items = $items; query = $q }
        }
        elseif ($path -eq "/api/series") {
            $targetUrl = Normalize-MediaUrl $query["url"]
            $videoId = ""
            $seasons = New-Object System.Collections.Generic.SortedSet[int]
            $fallbackEps = New-Object System.Collections.Generic.List[object]
            $synopsis = ""
            try {
                $html = Fetch-WebText $targetUrl $Script:BaseUrl
                $vidM = [System.Text.RegularExpressions.Regex]::Match($html, 'data-video-id=["''](\d+)["'']')
                if ($vidM.Success) { $videoId = $vidM.Groups[1].Value }
                $sMatches = [System.Text.RegularExpressions.Regex]::Matches($html, 'data-season=["''](\d+)["'']')
                foreach ($sm in $sMatches) {
                    $num = [int]$sm.Groups[1].Value
                    if ($num -ge 1 -and $num -le 200) { [void]$seasons.Add($num) }
                }
                if ($seasons.Count -eq 0) { [void]$seasons.Add(1) }

                $synM = [System.Text.RegularExpressions.Regex]::Match($html, '<meta[^>]+name=["'']description["''][^>]+content=["'']([^"'']+)["'']', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
                if ($synM.Success) { $synopsis = Clean-HtmlText $synM.Groups[1].Value }

                $epMatches = [System.Text.RegularExpressions.Regex]::Matches($html, '<a[^>]+href=["'']([^"'']*/episodios/[^"'']+)["''][^>]*>(.*?)</a>', [System.Text.RegularExpressions.RegexOptions]::Singleline -bor [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
                $epSeen = New-Object System.Collections.Generic.HashSet[string]
                $idx = 1
                foreach ($em in $epMatches) {
                    $epUrl = Normalize-MediaUrl $em.Groups[1].Value
                    if (-not $epSeen.Add($epUrl)) { continue }
                    $seM = [System.Text.RegularExpressions.Regex]::Match($epUrl, '-(\d+)x(\d+)-')
                    $sNum = if ($seM.Success) { [int]$seM.Groups[1].Value } else { 1 }
                    $eNum = if ($seM.Success) { [int]$seM.Groups[2].Value } else { $idx }
                    [void]$fallbackEps.Add(@{
                        title = Clean-HtmlText $em.Groups[2].Value
                        url = $epUrl
                        season = $sNum
                        number = $eNum
                    })
                    $idx++
                }
            } catch {}
            Send-JsonResponse $ctx @{
                videoId = $videoId
                seasons = @($seasons)
                synopsis = $synopsis
                fallbackEpisodes = $fallbackEps.ToArray()
            }
        }
        elseif ($path -eq "/api/episodes") {
            $vid = $query["id"]
            $season = if ($query["season"]) { $query["season"] } else { "1" }
            $audio = if ($query["audio"] -eq "Legendado") { "Legendado" } else { "Dublado" }
            $eps = New-Object System.Collections.Generic.List[object]
            if ($vid) {
                try {
                    $url = "$($Script:BaseUrl)/index.php?app=videobox&module=video&controller=view&do=episodesList&id=$([System.Uri]::EscapeDataString($vid))&season=$season&audio=$audio"
                    $raw = Fetch-WebText $url $Script:BaseUrl
                    $parsed = $raw | ConvertFrom-Json
                    if ($parsed -and $parsed.episodes) {
                        $i = 1
                        foreach ($e in $parsed.episodes) {
                            $n = $i
                            if ($e.number) { [void][int]::TryParse([string]$e.number, [ref]$n) }
                            [void]$eps.Add(@{
                                title = Clean-HtmlText ([string]$e.title)
                                url = Normalize-MediaUrl ([string]$e.url)
                                season = [int]$season
                                number = $n
                                audio = $audio
                            })
                            $i++
                        }
                    }
                } catch {}
            }
            Send-JsonResponse $ctx @{ episodes = ($eps.ToArray() | Sort-Object number) }
        }
        elseif ($path -eq "/api/player") {
            $targetUrl = Normalize-MediaUrl $query["url"]
            $vid = ($query["id"] | Out-String).Trim()
            if (-not $vid -and $targetUrl) {
                try {
                    $pageHtml = Fetch-WebText $targetUrl $Script:BaseUrl
                    $vidM = [System.Text.RegularExpressions.Regex]::Match($pageHtml, 'data-video-id=["''](\d+)["'']')
                    if ($vidM.Success) { $vid = $vidM.Groups[1].Value }
                } catch {}
            }

            $servers = New-Object System.Collections.Generic.List[object]
            $nextUrl = $null
            $prevUrl = $null

            if ($vid) {
                try {
                    $pdUrl = "$($Script:BaseUrl)/index.php?app=videobox&module=video&controller=view&do=playerData&id=$vid"
                    $rawPd = Fetch-WebText $pdUrl $targetUrl
                    $pd = $rawPd | ConvertFrom-Json
                    if ($pd -and $pd.players) {
                        $parseQueryPairs = {
                            param([string]$qStr)
                            $map = @{}
                            if (-not $qStr) { return $map }
                            $cleanQ = $qStr.Replace("&amp;", "&")
                            foreach ($pair in ($cleanQ -split "&")) {
                                $parts = $pair -split "=", 2
                                if ($parts.Length -eq 2 -and $parts[1]) {
                                    $map[$parts[0].ToLowerInvariant().Trim()] = $parts[1].Trim()
                                }
                            }
                            return $map
                        }

                        $dubMap = & $parseQueryPairs ([string]$pd.servers_dub)
                        $legMap = & $parseQueryPairs ([string]$pd.servers_leg)

                        foreach ($pairAudio in @(@{ map = $dubMap; label = "Dublado" }, @{ map = $legMap; label = "Legendado" })) {
                            foreach ($p in $pd.players) {
                                $lbl = [string]$p.label
                                $key = ([System.Text.RegularExpressions.Regex]::Replace($lbl.ToLowerInvariant(), "[^a-z0-9]", ""))
                                $token = $pairAudio.map[$key]
                                if ($token -and $p.url) {
                                    [void]$servers.Add(@{
                                        name = "$lbl ($($pairAudio.label))"
                                        embedUrl = ([string]$p.url) + $token
                                        audio = $pairAudio.label
                                    })
                                }
                            }
                        }

                        if ($pd.navigation) {
                            if ($pd.navigation.next) { $nextUrl = Normalize-MediaUrl ([string]$pd.navigation.next) }
                            if ($pd.navigation.prev) { $prevUrl = Normalize-MediaUrl ([string]$pd.navigation.prev) }
                        }
                    }
                } catch {}
            }

            Send-JsonResponse $ctx @{
                videoId = $vid
                servers = $servers.ToArray()
                nextUrl = $nextUrl
                prevUrl = $prevUrl
            }
        }
        elseif ($path -eq "/api/proxy") {
            $targetUrl = ($query["url"] | Out-String).Trim()
            if ([string]::IsNullOrWhiteSpace($targetUrl)) {
                Send-TextResponse $ctx "<html><body style='background:#000;color:#fff'>URL vazia</body></html>"
                continue
            }

            $blocked = $false
            $lowerUrl = $targetUrl.ToLowerInvariant()
            if (-not ($lowerUrl -match "pobreflixtv|byse|mixdrop|streamtape")) {
                foreach ($rule in $Script:AdRules) {
                    if ($lowerUrl.Contains($rule)) { $blocked = $true; break }
                }
            }
            if ($blocked) {
                $Script:BlockedCount++
                Send-TextResponse $ctx "<html><body style='background:#000;color:#10b981;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh'>Bloqueado pelo AdBlock A2 Miguel</body></html>"
                continue
            }

            try {
                $html = Fetch-WebText $targetUrl $Script:BaseUrl
                # Remove scripts de anúncios conhecidos e popup handlers
                $html = [System.Text.RegularExpressions.Regex]::Replace($html, '<script[^>]+src=["''][^"'']*(googlesyndication|doubleclick|popads|popcash|adsterra|monetag|propeller|exoclick|parklogic|histats|amung\.us|taboola|outbrain|1xbet|betano|bet365|tigrinho)[^"'']*["''][^>]*>\s*</script>', '', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
                $Script:BlockedCount++

                $uriObj = [System.Uri]$targetUrl
                $baseTag = "<base href=`"$($uriObj.Scheme)://$($uriObj.Authority)/`">"
                $shieldScript = "<script>(function(){window.open=function(){return null;};try{Object.defineProperty(window,'onbeforeunload',{set:function(){},get:function(){return null;}});}catch(e){}})();</script>"

                if ($html -match "(?i)<head[^>]*>") {
                    $html = [System.Text.RegularExpressions.Regex]::Replace($html, "(?i)(<head[^>]*>)", "`$1$baseTag$shieldScript", 1)
                } else {
                    $html = "$baseTag$shieldScript$html"
                }
                Send-TextResponse $ctx $html "text/html; charset=utf-8"
            } catch {
                $safeTarget = [System.Net.WebUtility]::HtmlEncode($targetUrl)
                $fallback = @"
<!DOCTYPE html><html><body style="background:#090a0f;color:#f5f7fc;font-family:sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;gap:14px;text-align:center;padding:24px;">
<h3>Este servidor exige abertura direta sem proxy</h3>
<p style="color:#9aa1b8;font-size:13px;">Troque para outro servidor na barra superior ou abra o link protegido:</p>
<a href="$safeTarget" target="_blank" style="background:#e50914;color:#fff;padding:10px 18px;border-radius:8px;text-decoration:none;font-weight:700;">Abrir Player Direto</a>
</body></html>
"@
                Send-TextResponse $ctx $fallback "text/html; charset=utf-8"
            }
        }
        elseif ($path -eq "/api/download") {
            $body = Read-RequestBody $ctx | ConvertFrom-Json
            $folderKey = if ($body.folder -eq "filmes") { $FilmesDir } elseif ($body.folder -eq "series") { $SeriesDir } else { $VideosDir }
            $fileName = ([string]$body.fileName) -replace '[\\/:*?"<>|]', '_'
            if (-not $fileName) { $fileName = "A2_Video_$(Get-Date -Format 'yyyyMMdd_HHmmss').mp4" }
            $infoPath = Join-Path $folderKey ($fileName -replace '\.mp4$', '.txt')
            $summary = "A2 Miguel PC - Download & Salvo`r`nTítulo: $($body.title)`r`nQualidade: $($body.quality)`r`nLink do Servidor: $($body.url)`r`nData: $(Get-Date)"
            [System.IO.File]::WriteAllText($infoPath, $summary, [System.Text.Encoding]::UTF8)
            Start-Process "explorer.exe" "`"$folderKey`""
            Send-JsonResponse $ctx @{ ok = $true; message = "📥 Salvo em Downloads\A2-Miguel-PC\Videos e pasta aberta!" }
        }
        elseif ($path -eq "/api/open-folder") {
            Start-Process "explorer.exe" "`"$InstallDir`""
            Send-JsonResponse $ctx @{ ok = $true }
        }
        else {
            Send-TextResponse $ctx "Not Found" "text/plain" 404
        }
    } catch {
        try {
            if ($ctx -and $ctx.Response) {
                $ctx.Response.StatusCode = 500
                $ctx.Response.Close()
            }
        } catch {}
    }
}
