param(
    [switch]$Rebuild,
    [ValidateRange(30, 600)]
    [int]$AgentReadyTimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$BackendPort = 18888
$FrontendPort = 5173
$AgentPort = 18889
$env:AGENT_INTERNAL_TOKEN = [Guid]::NewGuid().ToString('N')
$env:ADMIN_USERNAME = if ($env:ADMIN_USERNAME) { $env:ADMIN_USERNAME } else { 'admin' }
$env:ADMIN_PASSWORD = if ($env:ADMIN_PASSWORD) {
    $env:ADMIN_PASSWORD
} else {
    "Dev-$([Guid]::NewGuid().ToString('N'))"
}
$env:ADMIN_SYNC_PASSWORD_ON_STARTUP = 'true'

function Stop-PortOwner([int]$Port) {
    $pids = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique)
    if (-not $pids) {
        $pattern = "^\s*TCP\s+\S+:$Port\s+\S+\s+LISTENING\s+(\d+)\s*$"
        $pids = @(netstat.exe -ano | ForEach-Object {
            if ($_ -match $pattern) { [int]$Matches[1] }
        } | Select-Object -Unique)
    }
    foreach ($processId in $pids) {
        if ($processId -and $processId -ne $PID) {
            $owner = Get-Process -Id $processId -ErrorAction SilentlyContinue
            if ($owner) {
                try {
                    Stop-Process -Id $processId -Force -ErrorAction Stop
                } catch {
                    if (Get-Process -Id $processId -ErrorAction SilentlyContinue) {
                        throw
                    }
                }
            }
        }
    }
}

function Wait-HttpOk([string]$Url, [int]$Seconds) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 2
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return $true
            }
        } catch {
            Start-Sleep -Milliseconds 700
        }
    } while ((Get-Date) -lt $deadline)
    return $false
}

$backendJar = Join-Path $RepoRoot 'backend\target\cs-kaoyan-ai-backend-0.0.1-SNAPSHOT.jar'
$backendSourceChanged = Test-Path $backendJar -PathType Leaf
if ($backendSourceChanged) {
    $jarTime = (Get-Item -LiteralPath $backendJar).LastWriteTimeUtc
    $backendSourceChanged = [bool](Get-ChildItem (Join-Path $RepoRoot 'backend\src') -Recurse -File |
        Where-Object { $_.LastWriteTimeUtc -gt $jarTime } | Select-Object -First 1)
}
if ($Rebuild -or -not (Test-Path $backendJar) -or $backendSourceChanged) {
    Push-Location (Join-Path $RepoRoot 'backend')
    & mvn.cmd package
    Pop-Location
}

Stop-PortOwner $FrontendPort
Stop-PortOwner $BackendPort
Stop-PortOwner $AgentPort

$powershell = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$backendScript = Join-Path $PSScriptRoot 'run-backend.ps1'
$frontendScript = Join-Path $PSScriptRoot 'run-frontend.ps1'
$agentScript = Join-Path $PSScriptRoot 'run-agent.ps1'

Start-Process -FilePath $powershell -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $backendScript -WorkingDirectory (Join-Path $RepoRoot 'backend') -WindowStyle Hidden | Out-Null
Start-Process -FilePath $powershell -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $agentScript -WorkingDirectory (Join-Path $RepoRoot 'agent-service') -WindowStyle Hidden | Out-Null
Start-Sleep -Seconds 2
Start-Process -FilePath $powershell -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $frontendScript -WorkingDirectory (Join-Path $RepoRoot 'frontend') -WindowStyle Hidden | Out-Null

$backendOk = Wait-HttpOk "http://127.0.0.1:$BackendPort/api/health" 30
$agentOk = Wait-HttpOk "http://127.0.0.1:$AgentPort/api/health" $AgentReadyTimeoutSeconds
$frontendOk = Wait-HttpOk "http://127.0.0.1:$FrontendPort/" 30
$proxyOk = Wait-HttpOk "http://127.0.0.1:$FrontendPort/api/health" 30

$catalogImport = $null
$selfScoreLineImport = $null
if ($backendOk) {
    $catalogScript = Join-Path $PSScriptRoot 'import-catalog-408.ps1'
    $catalogImport = & $catalogScript
    $selfScoreLineScript = Join-Path $PSScriptRoot 'import-self-score-lines.ps1'
    $selfScoreLineImport = & $selfScoreLineScript
}

$agentIndex = $null
if ($backendOk -and $agentOk) {
    $agentIndex = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$AgentPort/api/index/sync" -TimeoutSec 180
}

[pscustomobject]@{
    Backend = if ($backendOk) { 'UP' } else { 'DOWN' }
    Agent = if ($agentOk) { "UP / $($agentIndex.chunks) chunks" } else { 'DOWN' }
    Frontend = if ($frontendOk) { 'UP' } else { 'DOWN' }
    Proxy = if ($proxyOk) { 'UP' } else { 'DOWN' }
    Catalog = if ($catalogImport -and $catalogImport.Imported) {
        "$($catalogImport.InputRecords) records / complete=$($catalogImport.Complete)"
    } else { 'SKIPPED' }
    SchoolScoreLines = if ($selfScoreLineImport -and $selfScoreLineImport.Imported) {
        "$($selfScoreLineImport.Available) available / $($selfScoreLineImport.Unavailable) unavailable"
    } else { 'SKIPPED' }
    AdminUsername = $env:ADMIN_USERNAME
    AdminPassword = $env:ADMIN_PASSWORD
    Url = "http://127.0.0.1:$FrontendPort/"
} | Format-List

if (-not ($backendOk -and $agentOk -and $frontendOk -and $proxyOk)) {
    throw 'Dev servers did not become healthy. Check the backend/frontend windows for logs.'
}
