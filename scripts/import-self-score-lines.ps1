param(
    [string]$BatchPath,
    [string]$ApiBase = 'http://127.0.0.1:18888',
    [string]$Username = $(if ($env:ADMIN_USERNAME) { $env:ADMIN_USERNAME } else { 'admin' }),
    [string]$Password = $env:ADMIN_PASSWORD
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($Password)) { throw 'ADMIN_PASSWORD is required.' }
$RepoRoot = Split-Path -Parent $PSScriptRoot
if (-not $BatchPath) {
    $BatchPath = Join-Path $RepoRoot 'database\self-score-lines-2026-reviewed.json'
}
if (-not (Test-Path -LiteralPath $BatchPath -PathType Leaf)) {
    return [pscustomobject]@{ Imported = $false; Reason = 'reviewed batch file not found'; Path = $BatchPath }
}

$batchJson = Get-Content -Raw -Encoding UTF8 -LiteralPath $BatchPath
$batch = $batchJson | ConvertFrom-Json
if ($batch.stats.schools -ne 34) {
    throw "Reviewed self-score-line batch must cover 34 schools, found $($batch.stats.schools)."
}

$loginJson = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Method Post -Uri "$ApiBase/api/auth/login" -ContentType 'application/json; charset=utf-8' `
    -Body ([Text.Encoding]::UTF8.GetBytes($loginJson)) -TimeoutSec 20
if (-not $login.data.token) {
    throw 'Admin login did not return a token.'
}

$headers = @{ Authorization = "Bearer $($login.data.token)" }
$response = Invoke-RestMethod -Method Post -Uri "$ApiBase/api/catalog-imports/self-score-lines" -Headers $headers `
    -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($batchJson)) -TimeoutSec 180
if ($response.code -ne 200) {
    throw "Self-score-line import failed: $($response.message)"
}

[pscustomobject]@{
    Imported = $true
    Year = $response.data.year
    Schools = $response.data.schools
    Available = $response.data.available
    Unavailable = $response.data.unavailable
    SchoolsCreated = $response.data.schoolsCreated
    ScoreLinesCreated = $response.data.scoreLinesCreated
    DocumentsCreated = $response.data.documentsCreated
    ExistingRecords = $response.data.existingRecords
}
