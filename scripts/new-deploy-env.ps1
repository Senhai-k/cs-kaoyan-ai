param(
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$examplePath = Join-Path $RepoRoot 'deploy\.env.example'
$envPath = Join-Path $RepoRoot 'deploy\.env'

if ((Test-Path -LiteralPath $envPath) -and -not $Force) {
    throw 'deploy/.env already exists. Use -Force only when rotating every deployment secret.'
}
if (-not (Test-Path -LiteralPath $examplePath -PathType Leaf)) {
    throw 'deploy/.env.example is missing.'
}

function New-RandomSecret([int]$ByteCount) {
    $bytes = [byte[]]::new($ByteCount)
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$replacements = @{
    MYSQL_PASSWORD = New-RandomSecret 32
    MYSQL_ROOT_PASSWORD = New-RandomSecret 32
    ADMIN_PASSWORD = New-RandomSecret 24
    AGENT_INTERNAL_TOKEN = New-RandomSecret 32
}
$lines = Get-Content -LiteralPath $examplePath -Encoding UTF8 | ForEach-Object {
    if ($_ -match '^([^#=]+)=') {
        $key = $Matches[1]
        if ($replacements.ContainsKey($key)) {
            return "$key=$($replacements[$key])"
        }
    }
    return $_
}
[System.IO.File]::WriteAllLines($envPath, $lines, [System.Text.UTF8Encoding]::new($false))

[pscustomobject]@{
    Environment = $envPath
    Generated = $true
    SecretsPrinted = $false
} | Format-List
