[CmdletBinding()]
param(
    [string]$CookieFile = '',
    [string]$EnvFile = '',
    [string]$CandidatePath = '',
    [string]$CatalogPath = '',
    [string]$ApiBase = 'http://127.0.0.1:18888',
    [string]$AgentBase = 'http://127.0.0.1:18889',
    [int]$DelayMs = 2500,
    [bool]$Refresh = $true,
    [switch]$PromptForCookie,
    [switch]$DeleteCookieAfterSuccess
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($CookieFile)) {
    $CookieFile = Join-Path $env:TEMP 'chsi.cookies.txt'
}
if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $RepoRoot 'deploy\.env'
}
if ([string]::IsNullOrWhiteSpace($CandidatePath)) {
    $CandidatePath = Join-Path $RepoRoot 'database\catalog-408-2026.next.json'
}
if ([string]::IsNullOrWhiteSpace($CatalogPath)) {
    $CatalogPath = Join-Path $RepoRoot 'database\catalog-408-2026.json'
}

function Read-DotEnv([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Environment file not found: $Path"
    }
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -match '^\s*(?:#|$)' -or $line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
            continue
        }
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$Matches[1]] = $value
    }
    return $values
}

function Resolve-WorkspacePath([string]$Path, [string]$Label) {
    $resolved = [IO.Path]::GetFullPath($Path)
    $root = [IO.Path]::GetFullPath($RepoRoot).TrimEnd('\') + '\'
    if (-not $resolved.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) {
        throw "$Label must stay inside the repository"
    }
    return $resolved
}

$resolvedCookie = [IO.Path]::GetFullPath($CookieFile)
$cookieHeader = [string]$env:CHSI_COOKIE
if ($PromptForCookie) {
    $secureCookie = Read-Host 'Paste the Cookie request-header value' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureCookie)
    try {
        $cookieHeader = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}
$hasCookieFile = -not $PromptForCookie -and (Test-Path -LiteralPath $resolvedCookie -PathType Leaf)
if ($hasCookieFile -and (Get-Item -LiteralPath $resolvedCookie).Length -eq 0) {
    throw 'CHSI Cookie file is empty'
}
if (-not $hasCookieFile -and [string]::IsNullOrWhiteSpace($cookieHeader)) {
    throw "CHSI login session is required. Use -PromptForCookie or provide a Cookie file: $resolvedCookie"
}
$resolvedCandidate = Resolve-WorkspacePath $CandidatePath 'CandidatePath'
$resolvedCatalog = Resolve-WorkspacePath $CatalogPath 'CatalogPath'
$values = Read-DotEnv ([IO.Path]::GetFullPath($EnvFile))
foreach ($name in @('ADMIN_USERNAME', 'ADMIN_PASSWORD')) {
    if (-not $values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($values[$name])) {
        throw "$name is required in the deployment environment"
    }
}

foreach ($uri in @("$ApiBase/actuator/health/readiness", "$AgentBase/api/health")) {
    $health = Invoke-RestMethod -Uri $uri -TimeoutSec 15
    if ($health.status -ne 'UP') { throw "Service is not ready: $uri" }
}

$collector = Join-Path $RepoRoot 'scripts\catalog\chsi-408-collector.mjs'
$collectorArgs = @(
    $collector,
    "--delay-ms=$DelayMs",
    "--output=$resolvedCandidate"
)
if ($hasCookieFile) { $collectorArgs += "--cookie-file=$resolvedCookie" }
if ($Refresh) { $collectorArgs += '--refresh' }
$previousCookieHeader = [string]$env:CHSI_COOKIE
try {
    if (-not [string]::IsNullOrWhiteSpace($cookieHeader)) { $env:CHSI_COOKIE = $cookieHeader }
    & node @collectorArgs
    if ($LASTEXITCODE -ne 0) { throw 'Complete CHSI catalog collection failed' }
} finally {
    if ([string]::IsNullOrWhiteSpace($previousCookieHeader)) {
        Remove-Item Env:CHSI_COOKIE -ErrorAction SilentlyContinue
    } else {
        $env:CHSI_COOKIE = $previousCookieHeader
    }
    $cookieHeader = $null
}
& node $collector "--verify-file=$resolvedCandidate"
if ($LASTEXITCODE -ne 0) { throw 'Candidate batch SHA-256 verification failed' }

$candidate = Get-Content -Raw -Encoding UTF8 -LiteralPath $resolvedCandidate | ConvertFrom-Json
if (-not $candidate.stats.complete) { throw 'Candidate catalog is not marked complete' }
$candidateCount = @($candidate.records).Count
if ($candidateCount -ne [int]$candidate.stats.records) {
    throw 'Candidate record count does not match stats.records'
}
if ($candidateCount -eq 0) { throw 'Candidate catalog contains no 408 records' }
$candidateSchoolCount = @($candidate.records | ForEach-Object { $_.school.code } | Sort-Object -Unique).Count
if ($candidateSchoolCount -ne [int]$candidate.stats.schools) {
    throw 'Candidate school count does not match stats.schools'
}
$invalid408 = @($candidate.records | Where-Object { $_.subjects.professional.code -ne '408' })
if ($invalid408.Count -gt 0) { throw "Candidate contains $($invalid408.Count) non-408 records" }
if ([string]$candidate.sha256 -notmatch '^[0-9a-f]{64}$') { throw 'Candidate batch SHA-256 is invalid' }

$current = Get-Content -Raw -Encoding UTF8 -LiteralPath $resolvedCatalog | ConvertFrom-Json
$currentCount = @($current.records).Count
if ($candidateCount -lt $currentCount) {
    throw 'Candidate record count regressed below the current catalog'
}

$importScript = Join-Path $PSScriptRoot 'import-catalog-408.ps1'
$importResult = & $importScript -CatalogPath $resolvedCandidate -ApiBase $ApiBase `
    -Username $values['ADMIN_USERNAME'] -Password $values['ADMIN_PASSWORD'] -RequireComplete
if (-not $importResult.Imported) { throw "Catalog import failed: $($importResult.Reason)" }

$backupDir = Join-Path $RepoRoot 'backups'
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
$backupPath = Join-Path $backupDir ("catalog-408-2026-{0}.json" -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
Copy-Item -LiteralPath $resolvedCatalog -Destination $backupPath
Copy-Item -LiteralPath $resolvedCandidate -Destination $resolvedCatalog -Force
Remove-Item -LiteralPath $resolvedCandidate -Force

$index = Invoke-RestMethod -Method Post -Uri "$AgentBase/api/index/sync" -TimeoutSec 180
if ($DeleteCookieAfterSuccess -and $hasCookieFile) { Remove-Item -LiteralPath $resolvedCookie -Force }

[pscustomobject]@{
    Complete = $candidate.stats.complete
    Year = $candidate.year
    Records = $candidateCount
    Schools = $candidate.stats.schools
    ExistingRecords = $importResult.ExistingRecords
    SchoolsCreated = $importResult.SchoolsCreated
    MajorsCreated = $importResult.MajorsCreated
    ExamSubjectsCreated = $importResult.ExamSubjectsCreated
    IndexedChunks = $index.chunks
    Backup = $backupPath
    CookieDeleted = [bool]($DeleteCookieAfterSuccess -and $hasCookieFile)
} | Format-List
