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

$Script:BaseUrl = "https://www.pobreflixtv.codes"
$Script:AniChanBaseUrl = "https://anichan.to"
$Script:UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

$Script:BaselineAdRules = @(
    "googlesyndication.com", "doubleclick.net", "googleadservices.com", "google-analytics.com",
    "googletagmanager.com", "adservice.google.com", "pagead2.googlesyndication.com", "popads.net",
    "popcash.net", "propellerads.com", "propellerclick.com", "onclickads.net", "onclickalgo.com", "exoclick.com",
    "exosrv.com", "realsrv.com", "syndication.realsrv.com", "adsterra.com", "highperformanceformat.com",
    "effectivegatecpm.com", "monetag.com", "al5sm.com", "trafficjunky.net", "trafficfactory.biz", "trafficstars.com",
    "adcash.com", "acscdn.com", "yllix.com", "taboola.com", "outbrain.com", "mgid.com",
    "revcontent.com", "juicyads.com", "plugrush.com", "hilltopads.net", "richads.com", "clickadu.com",
    "clickaine.com", "clksite.com", "admaven.com", "bidvertiser.com", "infolinks.com", "criteo.com", "criteo.net", "pubmatic.com",
    "rubiconproject.com", "openx.net", "appnexus.com", "adnxs.com", "smartadserver.com",
    "mediavine.com", "adthrive.com", "tsyndicate.com", "bnmla.com", "rtmark.net",
    "soakysecrets.com", "mdstats.info", "adtelligent.com", "ero-advertising.com",
    "1xbet", "bet365", "betano", "blaze.com", "sportingbet",
    "pixbet", "estrelabet", "bet7k", "superbet", "kto.com", "novibet", "rivalo", "parimatch",
    "bodog", "1win", "pin-up", "brazino", "cassino", "casino", "aposta", "tigrinho", "fortune-tiger",
    "oldnessdernly.qpon", "parklogic.com", "sedoparking.com", "parkingcrew.net", "bodis.com",
    "pushwoosh.com", "onesignal.com", "hotjar.com", "histats.com", "amung.us", "whos.amung.us",
    "adf.ly", "ouo.io", "shorte.st", "linkvertise.com", "chaturbate.com", "stripchat.com",
    "livejasmin.com", "belygylykijife.com", "proudfisherman.com", "glaringgoalie.com", "xads.js", "popunder"
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
                    if ($dom -notmatch "pobreflixtv|anichan|anilist|tmdb\.org|byse|bysesuk|mixdrop|mxdrop|mxcontent|mxdcontent|streamtape|playmogo|dood|d000d|d0000d|ds2play|dsvplay|myvidplay|vide0|filemoon|vidhide|streamwish|googleapis|gstatic|cloudflare") {
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
    $t = $raw.Trim().Replace("pobreflixtv.city", "pobreflixtv.codes").Replace("pobreflixtv.delivery", "pobreflixtv.codes")
    if ($t.StartsWith("http://") -or $t.StartsWith("https://")) { return $t }
    if ($t.StartsWith("//")) { return "https:$t" }
    if ($t.StartsWith("/")) { return "$($Script:BaseUrl)$t" }
    return "$($Script:BaseUrl)/$t"
}

function Strip-Accents([string]$text) {
    if ([string]::IsNullOrWhiteSpace($text)) { return "" }
    $norm = $text.Normalize([System.Text.NormalizationForm]::FormD)
    $sb = New-Object System.Text.StringBuilder
    foreach ($c in $norm.ToCharArray()) {
        if ([System.Globalization.CharUnicodeInfo]::GetUnicodeCategory($c) -ne [System.Globalization.UnicodeCategory]::NonSpacingMark) {
            [void]$sb.Append($c)
        }
    }
    return $sb.ToString().Normalize([System.Text.NormalizationForm]::FormC).Trim()
}

function Slugify-Title([string]$text) {
    $clean = (Strip-Accents $text).ToLowerInvariant()
    $slug = [System.Text.RegularExpressions.Regex]::Replace($clean, '[^a-z0-9]+', '-').Trim('-')
    if ([string]::IsNullOrWhiteSpace($slug)) { return "anime" }
    return $slug
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
        if (-not ($href.Contains("/filmes/") -or $href.Contains("/series/") -or $href.Contains("/episodios/") -or $href.Contains("/animes/"))) { continue }
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
        $isAnime = $href.Contains("/animes/")

        if ($seen.Add($href)) {
            [void]$list.Add(@{
                title = $title
                cover = $cover
                url = $href
                isAnime = $isAnime
                isSeries = ($isAnime -or $href.Contains("/series/") -or $href.Contains("/episodios/"))
                year = $year
            })
        }
    }
    return $list.ToArray()
}

function Parse-AniChanResults([string]$rawJson) {
    $list = New-Object System.Collections.Generic.List[object]
    $seen = New-Object System.Collections.Generic.HashSet[string]
    if ([string]::IsNullOrWhiteSpace($rawJson)) { return @() }
    try {
        $parsed = $rawJson | ConvertFrom-Json
        if (-not $parsed -or -not $parsed.results) { return @() }
        foreach ($r in $parsed.results) {
            $id = [int]$r.id
            if ($id -le 0) { continue }
            $title = [string]$r.title
            if ([string]::IsNullOrWhiteSpace($title)) { $title = [string]$r.titleRomaji }
            if ([string]::IsNullOrWhiteSpace($title)) { $title = "Anime #$id" }
            $poster = [string]$r.poster
            if ([string]::IsNullOrWhiteSpace($poster)) { continue }
            $slug = Slugify-Title $title
            $url = "$($Script:AniChanBaseUrl)/anime/$id/$slug"
            $year = ""
            if ($r.startDate -and $r.startDate.year) { $year = [string]$r.startDate.year }
            if ($seen.Add($url)) {
                [void]$list.Add(@{
                    title = $title
                    cover = $poster
                    url = $url
                    isAnime = $true
                    isSeries = $true
                    year = $year
                })
            }
        }
    } catch {}
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
            $itemsList = New-Object System.Collections.Generic.List[object]
            $seenUrls = New-Object System.Collections.Generic.HashSet[string]

            if ($cat -eq "animes") {
                foreach ($ep in @("$($Script:AniChanBaseUrl)/api/catalog/airing", "$($Script:AniChanBaseUrl)/api/catalog/trending")) {
                    try {
                        $rawAni = Fetch-WebText $ep "$($Script:AniChanBaseUrl)/"
                        foreach ($it in (Parse-AniChanResults $rawAni)) {
                            if ($seenUrls.Add([string]$it.url)) { [void]$itemsList.Add($it) }
                        }
                    } catch {}
                }
            } else {
                $targetUrl = "$($Script:BaseUrl)/"
                if ($cat -eq "filmes") {
                    $targetUrl = if ($page -gt 1) { "$($Script:BaseUrl)/filmes/page/$page/" } else { "$($Script:BaseUrl)/filmes/" }
                } elseif ($cat -eq "series") {
                    $targetUrl = if ($page -gt 1) { "$($Script:BaseUrl)/series/page/$page/" } else { "$($Script:BaseUrl)/series/" }
                }
                try {
                    $html = Fetch-WebText $targetUrl $Script:BaseUrl
                    foreach ($it in (Parse-CardsFromHtml $html)) {
                        if ($seenUrls.Add([string]$it.url)) { [void]$itemsList.Add($it) }
                    }
                } catch {}

                if ($cat -eq "home" -and $page -eq 1) {
                    try {
                        $rawAni = Fetch-WebText "$($Script:AniChanBaseUrl)/api/catalog/trending" "$($Script:AniChanBaseUrl)/"
                        foreach ($it in (Parse-AniChanResults $rawAni)) {
                            if ($seenUrls.Add([string]$it.url)) { [void]$itemsList.Add($it) }
                        }
                    } catch {}
                }
            }
            Send-JsonResponse $ctx @{ items = $itemsList.ToArray(); page = $page; category = $cat }
        }
        elseif ($path -eq "/api/search") {
            $q = ($query["q"] | Out-String).Trim()
            $pfItems = @()
            $aniItems = @()
            if ($q) {
                $qNoAccents = Strip-Accents $q
                $attempts = if ($qNoAccents -and $qNoAccents -ne $q) { @($q, $qNoAccents) } else { @($q) }
                foreach ($att in $attempts) {
                    try {
                        $ajaxUrl = "$($Script:BaseUrl)/index.php?app=videobox&module=video&controller=index&do=buscarContent"
                        $encQ = [System.Uri]::EscapeDataString($att)
                        $rawResp = Post-FormText $ajaxUrl "q=$encQ" "$($Script:BaseUrl)/buscar/"
                        $parsed = $rawResp | ConvertFrom-Json
                        if ($parsed -and $parsed.html) {
                            $pfItems = Parse-CardsFromHtml $parsed.html
                        }
                    } catch {}
                    if ($pfItems.Length -eq 0) {
                        try {
                            $encQ = [System.Uri]::EscapeDataString($att)
                            $html = Fetch-WebText "$($Script:BaseUrl)/buscar/?q=$encQ" $Script:BaseUrl
                            $pfItems = Parse-CardsFromHtml $html
                        } catch {}
                    }
                    if ($pfItems.Length -gt 0) { break }
                }

                try {
                    $encAniQ = [System.Uri]::EscapeDataString($q)
                    $rawAni = Fetch-WebText "$($Script:AniChanBaseUrl)/api/search?q=$encAniQ" "$($Script:AniChanBaseUrl)/"
                    $aniItems = Parse-AniChanResults $rawAni
                } catch {}
            }

            $combined = New-Object System.Collections.Generic.List[object]
            $seenSearch = New-Object System.Collections.Generic.HashSet[string]
            $maxCount = [Math]::Max($pfItems.Length, $aniItems.Length)
            for ($i = 0; $i -lt $maxCount; $i++) {
                if ($i -lt $pfItems.Length -and $seenSearch.Add([string]$pfItems[$i].url)) { [void]$combined.Add($pfItems[$i]) }
                if ($i -lt $aniItems.Length -and $seenSearch.Add([string]$aniItems[$i].url)) { [void]$combined.Add($aniItems[$i]) }
            }
            Send-JsonResponse $ctx @{ items = $combined.ToArray(); query = $q }
        }
        elseif ($path -eq "/api/series") {
            $targetUrl = Normalize-MediaUrl $query["url"]
            $videoId = ""
            $seasons = New-Object System.Collections.Generic.SortedSet[int]
            $fallbackEps = New-Object System.Collections.Generic.List[object]
            $synopsis = ""

            if ($targetUrl.Contains("anichan.to")) {
                $aniMatch = [System.Text.RegularExpressions.Regex]::Match($targetUrl, 'anichan\.to/anime/(\d+)(?:/([^/?#]+))?')
                if ($aniMatch.Success) {
                    $aniId = $aniMatch.Groups[1].Value
                    $slug = if ($aniMatch.Groups[2].Success -and $aniMatch.Groups[2].Value) { $aniMatch.Groups[2].Value } else { "anime" }
                    $videoId = "anichan_$aniId"
                    [void]$seasons.Add(1)
                    $epCount = 1
                    $dubAvail = $false
                    try {
                        $rawMeta = Fetch-WebText "$($Script:AniChanBaseUrl)/api/watch/episodes?anilistId=$aniId" $targetUrl
                        $metaObj = $rawMeta | ConvertFrom-Json
                        if ($metaObj -and $metaObj.episodes) { $epCount = [Math]::Max(1, [Math]::Min(2000, [int]$metaObj.episodes)) }
                        if ($metaObj -and $metaObj.dubAvailable) { $dubAvail = [bool]$metaObj.dubAvailable }
                    } catch {}
                    $synopsis = if ($dubAvail) { "Anime disponível no AniChan com opções Dublado e Legendado sem anúncios." } else { "Anime disponível no AniChan em Alta Definição sem anúncios." }
                    for ($eIdx = 1; $eIdx -le $epCount; $eIdx++) {
                        [void]$fallbackEps.Add(@{
                            title = "Episódio $eIdx"
                            url = "$($Script:AniChanBaseUrl)/anime/$aniId/$slug/$eIdx"
                            season = 1
                            number = $eIdx
                            audio = if ($dubAvail) { "Dublado / Legendado" } else { "Legendado" }
                        })
                    }
                }
            } else {
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
            }
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
            $servers = New-Object System.Collections.Generic.List[object]
            $nextUrl = $null
            $prevUrl = $null

            if ($targetUrl.Contains("anichan.to")) {
                $vid = "anichan"
                [void]$servers.Add(@{
                    name = "AniChan HD (Player Direto)"
                    embedUrl = $targetUrl
                    audio = "Dub / Leg"
                })
            } else {
                if (-not $vid -and $targetUrl) {
                    try {
                        $pageHtml = Fetch-WebText $targetUrl $Script:BaseUrl
                        $vidM = [System.Text.RegularExpressions.Regex]::Match($pageHtml, 'data-video-id=["''](\d+)["'']')
                        if ($vidM.Success) { $vid = $vidM.Groups[1].Value }
                    } catch {}
                }

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
                                        $tok = $parts[1].Trim()
                                        if ($tok -and $tok -notmatch '^(?i)(hide|null|none)$') {
                                            $map[$parts[0].ToLowerInvariant().Trim()] = $tok
                                        }
                                    }
                                }
                                return $map
                            }

                            $dubMap = & $parseQueryPairs ([string]$pd.servers_dub)
                            $legMap = & $parseQueryPairs ([string]$pd.servers_leg)
                            $rawServers = New-Object System.Collections.Generic.List[object]

                            foreach ($pairAudio in @(@{ map = $dubMap; label = "Dublado" }, @{ map = $legMap; label = "Legendado" })) {
                                foreach ($p in $pd.players) {
                                    $lbl = [string]$p.label
                                    $key = ([System.Text.RegularExpressions.Regex]::Replace($lbl.ToLowerInvariant(), "[^a-z0-9]", ""))
                                    $token = $pairAudio.map[$key]
                                    if ($token -and $p.url) {
                                        $embed = ([string]$p.url) + $token
                                        $prio = if ($lbl -match "(?i)byse") { 0 } elseif ($lbl -match "(?i)mixdrop|mxdrop") { 1 } elseif ($lbl -match "(?i)streamtape") { 2 } elseif ($lbl -match "(?i)dood") { 10 } else { 5 }
                                        $audioPrio = if ($pairAudio.label -eq "Dublado") { 0 } else { 1 }
                                        [void]$rawServers.Add(@{
                                            name = "$lbl ($($pairAudio.label))"
                                            embedUrl = $embed
                                            audio = $pairAudio.label
                                            sortKey = ($audioPrio * 20) + $prio
                                        })
                                    }
                                }
                            }

                            foreach ($s in ($rawServers | Sort-Object sortKey)) {
                                [void]$servers.Add(@{
                                    name = $s.name
                                    embedUrl = $s.embedUrl
                                    audio = $s.audio
                                })
                            }

                            if ($pd.navigation) {
                                if ($pd.navigation.next) { $nextUrl = Normalize-MediaUrl ([string]$pd.navigation.next) }
                                if ($pd.navigation.prev) { $prevUrl = Normalize-MediaUrl ([string]$pd.navigation.prev) }
                            }
                        }
                    } catch {}
                }
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
            $targetHost = ""
            try { $targetHost = ([System.Uri]$targetUrl).Host.ToLowerInvariant() } catch {}
            $isMediaAsset = ($lowerUrl.Split('?')[0].Split('#')[0] -match '\.(mp4|m3u8|ts|m4s|mpd|webm|vtt|srt|woff|woff2|ttf|css)$')
            $isTrustedHost = $targetHost -match "pobreflixtv|anichan|byse|bysesuk|mixdrop|mxdrop|mxcontent|mxdcontent|streamtape|playmogo|dood|d000d|d0000d|ds2play|dsvplay|myvidplay|vide0|filemoon|vidhide|streamwish"
            if (-not $isMediaAsset -and $lowerUrl -match "/xads\.js|/adblock\.js|popunder|onclick\.js|/vast\.|/vpaid\.|parklogic|sedoparking") {
                $blocked = $true
            }
            if (-not $blocked -and -not $isTrustedHost -and $targetHost) {
                foreach ($rule in $Script:AdRules) {
                    if ($rule.Contains(".")) {
                        if ($targetHost -eq $rule -or $targetHost.EndsWith(".$rule")) { $blocked = $true; break }
                    } elseif ($targetHost.Contains($rule)) {
                        $blocked = $true; break
                    }
                }
            }
            if ($blocked) {
                $Script:BlockedCount++
                Send-TextResponse $ctx "<html><body style='background:#000;color:#10b981;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh'>🛡️ Bloqueado pelo AdBlock A2 Miguel</body></html>"
                continue
            }

            try {
                $html = Fetch-WebText $targetUrl $Script:BaseUrl
                if ($html -match "(?i)parklogic\.com|This page has been created by ParkLogic|sedoparking") {
                    $Script:BlockedCount++
                    $parkSafe = @"
<!DOCTYPE html><html><body style="background:#090a0f;color:#f5f7fc;font-family:sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;gap:10px;text-align:center;padding:24px;">
<h3 style="color:#10b981;">🛡️ Domínio de Anúncio / ParkLogic Bloqueado</h3>
<p style="color:#9aa1b8;font-size:13px;">Selecione outro servidor ativo na barra superior (Byse ou MixDrop) para assistir normalmente.</p>
</body></html>
"@
                    Send-TextResponse $ctx $parkSafe "text/html; charset=utf-8"
                    continue
                }

                # Remove scripts de anúncios conhecidos, trackers e popup handlers
                $html = [System.Text.RegularExpressions.Regex]::Replace($html, '<script[^>]+src=["''][^"'']*(googlesyndication|doubleclick|popads|popcash|adsterra|monetag|propeller|exoclick|parklogic|sedoparking|histats|amung\.us|taboola|outbrain|soakysecrets|mdstats\.info|belygylykijife|proudfisherman|glaringgoalie|xads\.js|adblock\.js|onclick|1xbet|betano|bet365|tigrinho)[^"'']*["''][^>]*>\s*</script>', '', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
                $html = [System.Text.RegularExpressions.Regex]::Replace($html, 'target=["'']_blank["'']', 'target="_self"', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
                $Script:BlockedCount++

                $uriObj = [System.Uri]$targetUrl
                $baseTag = "<base href=`"$($uriObj.Scheme)://$($uriObj.Authority)/`">"
                $shieldScript = "<style>html,body,#videoPlayerWrap,#os_player,#video_player,.video-js,#videojs{width:100%!important;height:100%!important;margin:0!important;padding:0!important;overflow:hidden!important;background:#000!important;}video,.video-js .vjs-tech{width:100%!important;height:100%!important;object-fit:contain!important;display:block!important;visibility:visible!important;}</style><script>(function(){try{Object.defineProperty(window,'open',{value:function(){return null;},writable:false,configurable:false});}catch(e){window.open=function(){return null;};}try{window.minimalUserResponseInMiliseconds=99999999;window.check=function(){};}catch(e){}try{Object.defineProperty(window,'onbeforeunload',{set:function(){},get:function(){return null;}});}catch(e){}var _origAdd=EventTarget.prototype.addEventListener;EventTarget.prototype.addEventListener=function(t,fn,o){if((this===document||this===window||this===document.body)&&(t==='click'||t==='mousedown'||t==='touchstart')&&fn&&/open|wpn63s|dtnoppu|popunder|belygylykijife|soakysecrets/i.test(fn.toString())){return;}return _origAdd.call(this,t,fn,o);};function isSafe(el){if(!el||el===document.body||el===document.documentElement)return true;var tag=(el.tagName||'').toUpperCase();if(tag==='VIDEO'||tag==='IFRAME'||tag==='BUTTON')return true;var cls=typeof el.className==='string'?el.className.toLowerCase():'';if(cls.indexOf('vjs-')!==-1||cls.indexOf('plyr')!==-1||cls.indexOf('captcha-player')!==-1)return true;return Boolean(el.querySelector&&el.querySelector('video,iframe,.video-js,#video_player,.plyr'));}setInterval(function(){var vw=window.innerWidth||800,vh=window.innerHeight||500;document.querySelectorAll('a[target=""_blank""],iframe[src*=""ads""],div,a').forEach(function(el){if(el.tagName==='A'&&el.getAttribute('target')==='_blank'){el.removeAttribute('target');}if(isSafe(el))return;var st=window.getComputedStyle(el),z=parseInt(st.zIndex,10)||0;if((st.position==='fixed'||st.position==='absolute')&&z>=5){var r=el.getBoundingClientRect();if(r.width>=vw*0.55&&r.height>=vh*0.45&&(parseFloat(st.opacity)<0.1||st.backgroundColor==='transparent'||st.backgroundColor==='rgba(0, 0, 0, 0)'||el.tagName==='A')){el.style.pointerEvents='none';el.style.display='none';}}});},900);})();</script>"

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
<h3>Este servidor exige carregamento direto</h3>
<p style="color:#9aa1b8;font-size:13px;">Troque para outro servidor na barra superior (ex: Byse / MixDrop):</p>
<a href="$safeTarget" target="_blank" rel="noopener noreferrer" style="background:#e50914;color:#fff;padding:10px 18px;border-radius:8px;text-decoration:none;font-weight:700;">Abrir Player</a>
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
