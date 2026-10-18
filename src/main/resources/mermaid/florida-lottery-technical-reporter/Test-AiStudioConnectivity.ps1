<#
.SYNOPSIS
    Florida Lottery Network & Google AI Studio Connectivity Diagnostic Tool

.DESCRIPTION
    Inspects Windows 11 proxy settings (WinINet, WinHTTP, PAC files, and environment variables)
    and tests reachability to Google AI Studio (generativelanguage.googleapis.com)
    via direct socket and enterprise proxy routes.

.AUTHOR
    Ryan Stokes (Programmer Analyst III - Florida Lottery)
#>

Write-Host "=========================================================================" -ForegroundColor Cyan
Write-Host "   FLORIDA LOTTERY: GOOGLE AI STUDIO NETWORK CONNECTIVITY DIAGNOSTIC    " -ForegroundColor Cyan
Write-Host "=========================================================================" -ForegroundColor Cyan
Write-Host ""

# -----------------------------------------------------------------------------
# 1. INSPECT WINDOWS PROXY SETTINGS (WININET & REGISTRY)
# -----------------------------------------------------------------------------
Write-Host "[1/5] Checking Windows 11 Internet Settings (WinINet)..." -ForegroundColor Yellow

$regPath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings"
$proxyEnable = (Get-ItemProperty -Path $regPath -Name "ProxyEnable" -ErrorAction SilentlyContinue).ProxyEnable
$proxyServer = (Get-ItemProperty -Path $regPath -Name "ProxyServer" -ErrorAction SilentlyContinue).ProxyServer
$autoConfigUrl = (Get-ItemProperty -Path $regPath -Name "AutoConfigURL" -ErrorAction SilentlyContinue).AutoConfigURL

if ($proxyEnable -eq 1) {
    Write-Host "   [FOUND] Windows Proxy is ENABLED" -ForegroundColor Green
    Write-Host "   -> Proxy Server Address: $proxyServer" -ForegroundColor Cyan
} else {
    Write-Host "   [INFO] Windows Proxy Server is NOT explicitly enabled in registry." -ForegroundColor DarkGray
}

if (![string]::IsNullOrEmpty($autoConfigUrl)) {
    Write-Host "   [FOUND] Automatic PAC Script URL: $autoConfigUrl" -ForegroundColor Cyan
}

# -----------------------------------------------------------------------------
# 2. INSPECT SYSTEM WINHTTP & ENVIRONMENT VARIABLES
# -----------------------------------------------------------------------------
Write-Host "`n[2/5] Checking System WinHTTP & Environment Variables..." -ForegroundColor Yellow

$winHttp = netsh winhttp show proxy 2>&1
if ($winHttp -match "Proxy Server\(s\)\s*:\s*(.+)") {
    Write-Host "   [WinHTTP System Proxy]: $($Matches[1].Trim())" -ForegroundColor Cyan
} else {
    Write-Host "   [WinHTTP System Proxy]: Direct access (no proxy server)" -ForegroundColor DarkGray
}

$envHttpsProxy = $env:HTTPS_PROXY
$envHttpProxy = $env:HTTP_PROXY
if (![string]::IsNullOrEmpty($envHttpsProxy)) {
    Write-Host "   [ENV: HTTPS_PROXY]: $envHttpsProxy" -ForegroundColor Green
} elseif (![string]::IsNullOrEmpty($envHttpProxy)) {
    Write-Host "   [ENV: HTTP_PROXY]:  $envHttpProxy" -ForegroundColor Green
} else {
    Write-Host "   [ENV]: No HTTP_PROXY / HTTPS_PROXY environment variables set." -ForegroundColor DarkGray
}

# -----------------------------------------------------------------------------
# 3. DNS RESOLUTION TEST
# -----------------------------------------------------------------------------
Write-Host "`n[3/5] Testing DNS Resolution for Google AI Studio..." -ForegroundColor Yellow
$targetHost = "generativelanguage.googleapis.com"

try {
    $dns = Resolve-DnsName -Name $targetHost -Type A -ErrorAction Stop
    $ips = ($dns | Select-Object -ExpandProperty IPAddress) -join ", "
    Write-Host "   [SUCCESS] Resolved $targetHost to IP(s): $ips" -ForegroundColor Green
} catch {
    Write-Host "   [FAILURE] Failed to resolve DNS for $targetHost : $_" -ForegroundColor Red
}

# -----------------------------------------------------------------------------
# 4. DIRECT TCP SOCKET TEST (PORT 443)
# -----------------------------------------------------------------------------
Write-Host "`n[4/5] Testing Direct TCP Port 443 Handshake (Without Proxy)..." -ForegroundColor Yellow

$directResult = Test-NetConnection -ComputerName $targetHost -Port 443 -WarningAction SilentlyContinue

if ($directResult.TcpTestSucceeded) {
    Write-Host "   [SUCCESS] Direct outbound TCP port 443 connection SUCCEEDED!" -ForegroundColor Green
    Write-Host "   -> Your machine has open direct internet egress to Google." -ForegroundColor Green
} else {
    Write-Host "   [BLOCKED] Direct outbound TCP port 443 connection FAILED / REFUSED!" -ForegroundColor Red
    Write-Host "   -> This confirms the Florida Lottery firewall is blocking direct outbound port 443 traffic." -ForegroundColor Yellow
    Write-Host "   -> All outbound traffic MUST route through an enterprise proxy." -ForegroundColor Yellow
}

# -----------------------------------------------------------------------------
# 5. .NET / WINDOWS DEFAULT PROXY DISCOVERY & HTTPS HANDSHAKE
# -----------------------------------------------------------------------------
Write-Host "`n[5/5] Testing .NET Default Web Proxy Route..." -ForegroundColor Yellow

$testUri = New-Object System.Uri("https://generativelanguage.googleapis.com")
$systemProxy = [System.Net.WebRequest]::DefaultWebProxy.GetProxy($testUri)

if ($systemProxy.AbsoluteUri -ne $testUri.AbsoluteUri) {
    Write-Host "   [RESOLVED] Windows routes this URL through proxy: $($systemProxy.Authority)" -ForegroundColor Green
} else {
    Write-Host "   [INFO] Windows DefaultWebProxy routed as Direct." -ForegroundColor DarkGray
}

# Quick API Ping using .NET HttpClient
Write-Host "`n[Testing Live HTTPS Ping to AI Studio Endpoint]..." -ForegroundColor Yellow

try {
    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.UseProxy = $true
    $client = New-Object System.Net.Http.HttpClient($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(10)

    # Ping endpoint (will return 400 Bad Request or 403 Forbidden without API key, which proves network connectivity!)
    $response = $client.GetAsync("https://generativelanguage.googleapis.com/v1beta/models").Result
    $status = [int]$response.StatusCode

    Write-Host "   [NETWORK HANDSHAKE CONFIRMED] Received HTTP $status from Google AI Studio!" -ForegroundColor Green
    Write-Host "   -> Network path is ACTIVE and REACHABLE." -ForegroundColor Green
} catch {
    Write-Host "   [CONNECTION FAILED]: $($_.Exception.InnerException.Message)" -ForegroundColor Red
}

Write-Host "`n=========================================================================" -ForegroundColor Cyan
Write-Host "                          RECOMMENDED ACTIONS                            " -ForegroundColor Cyan
Write-Host "=========================================================================" -ForegroundColor Cyan

if ($directResult.TcpTestSucceeded) {
    Write-Host "Option A (Direct): Direct connection is open. You can run your Java app directly:" -ForegroundColor White
    Write-Host "   java -cp target/classes;target/dependency/* com.fllottery.util.ExtractAndResearchProcsInventory`n" -ForegroundColor Gray
} elseif (![string]::IsNullOrEmpty($proxyServer)) {
    Write-Host "Option B (Windows Proxy Active): Windows has proxy configured ($proxyServer)." -ForegroundColor White
    Write-Host "   Your updated Java code with EnterpriseProxyResolver will automatically use it!`n" -ForegroundColor Green
} else {
    Write-Host "Option C (Enterprise Proxy Required): Create or verify 'proxy.properties' in:" -ForegroundColor White
    Write-Host "   C:\ocioldapps1\Cached\DbCache\proxy.properties" -ForegroundColor Yellow
    Write-Host "   Content: proxy.host=proxy.lottery.state.fl.us" -ForegroundColor Gray
    Write-Host "            proxy.port=8080`n" -ForegroundColor Gray
}
