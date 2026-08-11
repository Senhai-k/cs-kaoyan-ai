param(
    [string]$BackupRoot = "",
    [switch]$KeepDrillDatabase
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
if (-not $BackupRoot) {
    $BackupRoot = Join-Path $RepoRoot 'backups\h2'
}
New-Item -ItemType Directory -Force -Path $BackupRoot | Out-Null
$BackupRoot = (Resolve-Path -LiteralPath $BackupRoot).Path

$h2Jar = Get-ChildItem (Join-Path $HOME '.m2\repository\com\h2database\h2') `
    -Recurse -Filter 'h2-*.jar' -File -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1
if (-not $h2Jar) {
    throw 'H2 tool jar was not found. Run mvn.cmd test in backend first.'
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$sourceBase = (Join-Path $RepoRoot 'backend\data\kaoyan').Replace('\', '/')
$sourceUrl = "jdbc:h2:file:$sourceBase;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=YEAR;AUTO_SERVER=TRUE"
$backupFile = Join-Path $BackupRoot "kaoyan-$stamp.sql"
$manifestFile = Join-Path $BackupRoot "kaoyan-$stamp.json"
$drillBase = Join-Path $BackupRoot "restore-drill-$stamp"
$drillUrl = "jdbc:h2:file:$($drillBase.Replace('\', '/'));MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=YEAR"

function Invoke-H2Tool([string[]]$Arguments) {
    $output = & java.exe '-cp' $h2Jar.FullName @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw ($output -join [Environment]::NewLine)
    }
    return @($output)
}

function Get-DatabaseFingerprint([string]$Url) {
    $sql = "SELECT CONCAT_WS('|', (SELECT COUNT(*) FROM school), (SELECT COUNT(*) FROM source_document), (SELECT COUNT(*) FROM document_chunk), (SELECT COUNT(*) FROM source_document_version), (SELECT COUNT(*) FROM admin_user), (SELECT COUNT(*) FROM document_parse_task), (SELECT COUNT(*) FROM web_capture_task), (SELECT COUNT(*) FROM document_publication_batch), (SELECT COUNT(*) FROM document_publication_batch_item), (SELECT COUNT(*) FROM web_capture_change), (SELECT COUNT(*) FROM web_capture_schedule), (SELECT COUNT(*) FROM national_score_line), (SELECT COUNT(*) FROM school_score_line), (SELECT COUNT(*) FROM admission_result_import_batch), (SELECT COUNT(*) FROM admission_result_candidate)) AS fingerprint"
    $output = Invoke-H2Tool @('org.h2.tools.Shell', '-url', $Url, '-user', 'sa', '-sql', $sql)
    $fingerprint = $output | ForEach-Object { $_.ToString().Trim() } |
        Where-Object { $_ -match '^\d+(\|\d+){14}$' } |
        Select-Object -First 1
    if (-not $fingerprint) {
        throw "Could not read database fingerprint: $($output -join ' ')"
    }
    return $fingerprint
}

$sourceFingerprint = Get-DatabaseFingerprint $sourceUrl
Invoke-H2Tool @('org.h2.tools.Script', '-url', $sourceUrl, '-user', 'sa', '-script', $backupFile) | Out-Null
if (-not (Test-Path -LiteralPath $backupFile)) {
    throw 'H2 backup file was not created.'
}

Invoke-H2Tool @('org.h2.tools.RunScript', '-url', $drillUrl, '-user', 'sa', '-script', $backupFile) | Out-Null
$restoredFingerprint = Get-DatabaseFingerprint $drillUrl
if ($sourceFingerprint -ne $restoredFingerprint) {
    throw "Restore verification failed: source=$sourceFingerprint restored=$restoredFingerprint"
}

$hash = (Get-FileHash -LiteralPath $backupFile -Algorithm SHA256).Hash.ToLowerInvariant()
$manifest = [ordered]@{
    schemaVersion = 10
    createdAt = (Get-Date).ToUniversalTime().ToString('o')
    source = 'backend/data/kaoyan'
    backupFile = Split-Path -Leaf $backupFile
    sha256 = $hash
    sourceFingerprint = $sourceFingerprint
    restoredFingerprint = $restoredFingerprint
    verified = $true
}
$manifest | ConvertTo-Json | Set-Content -LiteralPath $manifestFile -Encoding UTF8

if (-not $KeepDrillDatabase) {
    $drillFiles = @("$drillBase.mv.db", "$drillBase.trace.db")
    foreach ($path in $drillFiles) {
        $fullPath = [System.IO.Path]::GetFullPath($path)
        if ($fullPath.StartsWith($BackupRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
            Remove-Item -LiteralPath $fullPath -Force -ErrorAction SilentlyContinue
        }
    }
}

[pscustomobject]@{
    Backup = $backupFile
    Manifest = $manifestFile
    Sha256 = $hash
    Fingerprint = $sourceFingerprint
    RestoreVerified = $true
} | Format-List
