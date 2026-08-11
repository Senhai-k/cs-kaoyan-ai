param(
    [int]$BackendPort = 18891,
    [int]$UnavailableAgentPort = 65534
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$drillRoot = Join-Path $RepoRoot "backups\failure-drill-$stamp"
$backendJar = Join-Path $RepoRoot 'backend\target\cs-kaoyan-ai-backend-0.0.1-SNAPSHOT.jar'
$databaseBase = (Join-Path $drillRoot 'failure-drill').Replace('\', '/')
$backendLog = Join-Path $drillRoot 'backend.log'
$drillAdminPassword = "Drill-$([Guid]::NewGuid().ToString('N'))"
$process = $null
$succeeded = $false

function Wait-HttpOk([string]$Url, [int]$Seconds) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 2
            if ($response.StatusCode -eq 200) { return $true }
        } catch {
            Start-Sleep -Milliseconds 600
        }
    } while ((Get-Date) -lt $deadline)
    return $false
}

function Start-DrillBackend {
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = (Get-Command java.exe -ErrorAction Stop).Source
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $jdbcUrl = "jdbc:h2:file:$databaseBase;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=YEAR;AUTO_SERVER=TRUE"
    $startInfo.Arguments = @(
        '-jar'
        "`"$backendJar`""
        "--server.port=$BackendPort"
        "`"--spring.datasource.url=$jdbcUrl`""
        '--spring.datasource.username=sa'
        '--spring.datasource.password='
        '--spring.datasource.driver-class-name=org.h2.Driver'
        '--spring.sql.init.mode=always'
        '--spring.flyway.enabled=false'
        '--app.ai.provider=agent'
        "--app.ai.agent.endpoint=http://127.0.0.1:$UnavailableAgentPort"
        '--app.ai.agent.timeout-seconds=5'
        "--app.admin.password=$drillAdminPassword"
        '--app.admin.session.h2-checkpoint=true'
        "`"--logging.file.name=$backendLog`""
    ) -join ' '
    return [System.Diagnostics.Process]::Start($startInfo)
}

function Stop-DrillBackend {
    if ($script:process -and -not $script:process.HasExited) {
        Stop-Process -Id $script:process.Id -Force -ErrorAction SilentlyContinue
        [void]$script:process.WaitForExit(10000)
    }
    $script:process = $null
}

function Require-Status([scriptblock]$Request, [int]$ExpectedStatus) {
    try {
        & $Request | Out-Null
        if ($ExpectedStatus -ne 200) { throw "Expected HTTP $ExpectedStatus but request succeeded" }
    } catch {
        $status = [int]$_.Exception.Response.StatusCode
        if ($status -ne $ExpectedStatus) { throw }
    }
}

try {
    if (-not (Test-Path -LiteralPath $backendJar -PathType Leaf)) {
        throw 'Backend jar is missing. Run mvn.cmd package first.'
    }
    if (Get-NetTCPConnection -LocalPort $BackendPort -State Listen -ErrorAction SilentlyContinue) {
        throw "Port $BackendPort is already in use."
    }
    New-Item -ItemType Directory -Force -Path $drillRoot | Out-Null

    $process = Start-DrillBackend
    if (-not (Wait-HttpOk "http://127.0.0.1:$BackendPort/api/health" 60)) {
        throw "Drill backend did not start: $(Get-Content -LiteralPath $backendLog -Raw -ErrorAction SilentlyContinue)"
    }

    $known = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$BackendPort/api/ai/chat" `
        -ContentType 'application/json' -Body (@{question='南京大学 408 招生资料'} | ConvertTo-Json)
    if ($known.data.meta.provider -ne 'spring-local-rag' -or $known.data.sources.Count -lt 1) {
        throw 'Agent outage did not fall back to grounded local retrieval.'
    }
    $unknown = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$BackendPort/api/ai/chat" `
        -ContentType 'application/json' -Body (@{question='完全未知且无资料的问题 XYZ-FAILURE-DRILL'} | ConvertTo-Json)
    if ($unknown.data.meta.provider -ne 'spring-fallback' -or $unknown.data.meta.status -ne 'DEGRADED') {
        throw 'Agent outage did not return the explicit no-evidence degradation response.'
    }

    $login = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$BackendPort/api/auth/login" `
        -ContentType 'application/json' -Body (@{username='admin';password=$drillAdminPassword} | ConvertTo-Json)
    $headers = @{Authorization="Bearer $($login.data.token)"}
    Start-Sleep -Milliseconds 500
    Stop-DrillBackend
    $process = Start-DrillBackend
    if (-not (Wait-HttpOk "http://127.0.0.1:$BackendPort/api/health" 60)) {
        throw 'Drill backend did not restart.'
    }
    $history = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$BackendPort/api/source-documents/1/versions" -Headers $headers
    if ($history.code -ne 200) { throw 'Persisted session was not accepted after restart.' }
    Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$BackendPort/api/auth/logout" -Headers $headers | Out-Null
    Require-Status { Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$BackendPort/api/source-documents/1/versions" -Headers $headers } 401

    $latestManifest = Get-ChildItem (Join-Path $RepoRoot 'backups\h2') -Filter '*.json' -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if (-not $latestManifest) { throw 'No H2 backup manifest is available for corruption drill.' }
    $metadata = Get-Content -LiteralPath $latestManifest.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    $sourceBackup = Join-Path $latestManifest.DirectoryName $metadata.backupFile
    $manifestCopy = Join-Path $drillRoot $latestManifest.Name
    $backupCopy = Join-Path $drillRoot $metadata.backupFile
    Copy-Item -LiteralPath $latestManifest.FullName -Destination $manifestCopy
    Copy-Item -LiteralPath $sourceBackup -Destination $backupCopy
    Add-Content -LiteralPath $backupCopy -Value '-- corruption drill' -Encoding ASCII
    $verifyOutput = @()
    $verifyExitCode = 0
    try {
        $verifyOutput = & powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
            (Join-Path $PSScriptRoot 'verify-backup-manifest.ps1') -Manifest $manifestCopy 2>&1
        $verifyExitCode = $LASTEXITCODE
    } catch {
        $verifyOutput = @($_.Exception.Message)
        $verifyExitCode = 1
    }
    if ($verifyExitCode -eq 0 -or ($verifyOutput -join ' ') -notmatch 'SHA-256 mismatch') {
        throw 'Tampered backup was not rejected by manifest verification.'
    }

    $succeeded = $true
    [pscustomobject]@{
        AgentOutageGroundedFallback = $true
        AgentOutageNoEvidenceDegraded = $true
        SessionSurvivedRestart = $true
        LogoutRevokedSharedSession = $true
        TamperedBackupRejected = $true
        Verified = $true
    } | Format-List
} finally {
    Stop-DrillBackend
    if ($succeeded -and (Test-Path -LiteralPath $drillRoot)) {
        $resolved = [System.IO.Path]::GetFullPath($drillRoot)
        $backupRoot = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot 'backups'))
        if ($resolved.StartsWith($backupRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
            Remove-Item -LiteralPath $resolved -Recurse -Force
        }
    }
    if (-not $succeeded) {
        Write-Warning "Failure drill workspace retained: $drillRoot"
    }
}
