param(
    [string]$CatalogPath,
    [string]$ApiBase = 'http://127.0.0.1:18888',
    [string]$Username = $(if ($env:ADMIN_USERNAME) { $env:ADMIN_USERNAME } else { 'admin' }),
    [string]$Password = $env:ADMIN_PASSWORD,
    [switch]$RequireComplete
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($Password)) { throw 'ADMIN_PASSWORD is required.' }
$RepoRoot = Split-Path -Parent $PSScriptRoot
if (-not $CatalogPath) {
    $CatalogPath = Join-Path $RepoRoot 'database\catalog-408-2026.json'
}
if (-not (Test-Path -LiteralPath $CatalogPath -PathType Leaf)) {
    return [pscustomobject]@{ Imported = $false; Reason = 'catalog file not found'; Path = $CatalogPath }
}

$catalogJson = Get-Content -Raw -Encoding UTF8 -LiteralPath $CatalogPath
$catalog = $catalogJson | ConvertFrom-Json
if ($RequireComplete -and -not $catalog.stats.complete) {
    throw 'Catalog batch is incomplete. Export a legal CHSI login session and rerun the collector without page or school limits.'
}

$loginJson = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
$loginBody = [Text.Encoding]::UTF8.GetBytes($loginJson)
$login = Invoke-RestMethod -Method Post -Uri "$ApiBase/api/auth/login" -ContentType 'application/json; charset=utf-8' -Body $loginBody -TimeoutSec 20
if (-not $login.data.token) {
    throw 'Admin login did not return a token.'
}

$catalogBody = [Text.Encoding]::UTF8.GetBytes($catalogJson)
$headers = @{ Authorization = "Bearer $($login.data.token)" }
$response = Invoke-RestMethod -Method Post -Uri "$ApiBase/api/catalog-imports/408" -Headers $headers `
    -ContentType 'application/json; charset=utf-8' -Body $catalogBody -TimeoutSec 180
if ($response.code -ne 200) {
    throw "Catalog import failed: $($response.message)"
}

[pscustomobject]@{
    Imported = $true
    Year = $response.data.year
    Complete = $response.data.complete
    InputRecords = $response.data.inputRecords
    SchoolsCreated = $response.data.schoolsCreated
    MajorsCreated = $response.data.majorsCreated
    ExamSubjectsCreated = $response.data.examSubjectsCreated
    AdmissionPlansCreated = $response.data.admissionPlansCreated
    RetestRulesCreated = $response.data.retestRulesCreated
    ExistingRecords = $response.data.existingRecords
}
