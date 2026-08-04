# Bring up Raiz Memory and expose it publicly, then print the URL.
#
# Demo-day convenience: the quick tunnel's hostname is regenerated on every
# start, so this script is the fastest honest way to get a fresh public URL
# (see docs/deploy-public.md for why we do not promise a stable one).
#
#   pwsh scripts/serve-public.ps1
#   pwsh scripts/serve-public.ps1 -Port 8091 -SkipTunnel
#
# Stop everything with Ctrl+C, or: Get-Process raiz-memory,cloudflared | Stop-Process

param(
    [int]$Port = 8091,
    [switch]$SkipTunnel
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$indexer = Join-Path $repo 'raiz-memory'
$cloudflared = 'C:\Program Files (x86)\cloudflared\cloudflared.exe'

if (-not (Test-Path (Join-Path $indexer '.env'))) {
    throw "raiz-memory/.env is missing. Copy .env.example and set RPC_URL + CONTRACT_IDS."
}

# Reuse an instance that is already listening rather than fighting it for the
# port (and for the file lock on the binary, which is how you get a confusing
# "access denied" from cargo build).
$listening = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($listening) {
    Write-Host "Raiz Memory already listening on $Port (PID $($listening.OwningProcess))"
} else {
    Push-Location $indexer
    try {
        Write-Host 'Building raiz-memory...'
        cargo build --quiet
        if ($LASTEXITCODE -ne 0) { throw 'cargo build failed' }
        $env:PORT = "$Port"
        $proc = Start-Process -FilePath '.\target\debug\raiz-memory.exe' -NoNewWindow -PassThru
        Write-Host "Started raiz-memory (PID $($proc.Id)) on $Port"
    } finally {
        Pop-Location
    }
}

Write-Host 'Waiting for /health...'
$deadline = (Get-Date).AddSeconds(90)
do {
    Start-Sleep -Seconds 2
    try {
        $health = (Invoke-WebRequest "http://localhost:$Port/health" -UseBasicParsing -TimeoutSec 5).Content
    } catch { $health = $null }
} while (-not $health -and (Get-Date) -lt $deadline)

if (-not $health) { throw "raiz-memory did not answer /health on port $Port within 90s" }
Write-Host "  $health"

# Coverage is the honest part: print what this instance actually holds, so
# nobody demos against an index that is still backfilling.
$coverage = (Invoke-WebRequest "http://localhost:$Port/coverage" -UseBasicParsing -TimeoutSec 15).Content | ConvertFrom-Json
foreach ($c in $coverage.contracts) {
    $line = "  {0}... {1,6} events, from ledger {2}" -f $c.contractId.Substring(0, 8), $c.eventCount, $c.firstEventLedger
    if ($c.backfill.clamped) { $line += "  (clamped: $($c.backfill.unreachableLedgers) ledgers already unreachable)" }
    Write-Host $line
}

if ($SkipTunnel) { Write-Host "`nLocal only: http://localhost:$Port"; return }

if (-not (Test-Path $cloudflared)) {
    throw "cloudflared not found at $cloudflared. Install it: winget install --id Cloudflare.cloudflared"
}

$log = Join-Path $env:TEMP "cloudflared-raiz-$Port.log"
Remove-Item $log -ErrorAction SilentlyContinue
Start-Process -FilePath $cloudflared `
    -ArgumentList 'tunnel', '--url', "http://localhost:$Port", '--no-autoupdate' `
    -RedirectStandardError $log -NoNewWindow | Out-Null

Write-Host 'Waiting for the tunnel hostname...'
$deadline = (Get-Date).AddSeconds(60)
do {
    Start-Sleep -Seconds 2
    $url = (Select-String -Path $log -Pattern 'https://[a-z0-9-]+\.trycloudflare\.com' -ErrorAction SilentlyContinue |
        Select-Object -First 1).Matches.Value
} while (-not $url -and (Get-Date) -lt $deadline)

if (-not $url) { throw "cloudflared did not print a hostname within 60s. Log: $log" }

# Prove the public path really serves before announcing it.
$public = (Invoke-WebRequest "$url/health" -UseBasicParsing -TimeoutSec 30).Content

Write-Host ''
Write-Host "PUBLIC URL: $url"
Write-Host "  verified: $public"
Write-Host ''
Write-Host 'Set this as the wallet event source (Ajustes), or keep the phone on'
Write-Host "  adb reverse tcp:$Port tcp:$Port  ->  http://localhost:$Port"
Write-Host 'This hostname dies with the process. Re-run to get a new one.'
