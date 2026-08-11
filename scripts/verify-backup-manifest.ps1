param(
    [Parameter(Mandatory = $true)]
    [string]$Manifest
)

$ErrorActionPreference = 'Stop'
$manifestPath = (Resolve-Path -LiteralPath $Manifest).Path
$manifestDir = Split-Path -Parent $manifestPath
$metadata = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
if (-not $metadata.backupFile -or -not $metadata.sha256) {
    throw 'Backup manifest is missing backupFile or sha256.'
}
$fingerprintSegmentsByVersion = @{
    7 = 11
    9 = 13
    10 = 15
}
$schemaVersion = [int]$metadata.schemaVersion
if (-not $fingerprintSegmentsByVersion.ContainsKey($schemaVersion)) {
    throw "Unsupported backup manifest schemaVersion: $($metadata.schemaVersion)"
}
if (-not $metadata.verified -or -not $metadata.sourceFingerprint -or
    $metadata.sourceFingerprint -ne $metadata.restoredFingerprint) {
    throw 'Backup manifest does not contain a successful restore verification.'
}
$fingerprintParts = @($metadata.sourceFingerprint.ToString().Split('|'))
$expectedSegments = $fingerprintSegmentsByVersion[$schemaVersion]
if ($fingerprintParts.Count -ne $expectedSegments -or
    @($fingerprintParts | Where-Object { $_ -notmatch '^\d+$' }).Count -gt 0) {
    throw "Backup manifest fingerprint does not match the version $schemaVersion contract."
}
if ([System.IO.Path]::GetFileName($metadata.backupFile) -ne $metadata.backupFile) {
    throw 'Backup manifest must reference a file in the manifest directory.'
}
$backupPath = Join-Path $manifestDir $metadata.backupFile
if (-not (Test-Path -LiteralPath $backupPath -PathType Leaf)) {
    throw "Backup file not found: $backupPath"
}
$actual = (Get-FileHash -LiteralPath $backupPath -Algorithm SHA256).Hash.ToLowerInvariant()
$expected = $metadata.sha256.ToString().ToLowerInvariant()
if ($actual -ne $expected) {
    throw "Backup SHA-256 mismatch: expected=$expected actual=$actual"
}

[pscustomobject]@{
    Manifest = $manifestPath
    Backup = $backupPath
    Sha256 = $actual
    Fingerprint = $metadata.sourceFingerprint
    Verified = $true
} | Format-List
