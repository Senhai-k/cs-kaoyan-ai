param(
    [string]$EnvFile = "deploy/.env.example",
    [string]$Registry = "",
    [switch]$Build,
    [switch]$Start
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$ComposeFile = Join-Path $RepoRoot 'deploy/compose.yml'
$ResolvedEnv = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $EnvFile))
$ResolvedRoot = [System.IO.Path]::GetFullPath($RepoRoot)

if (-not $ResolvedEnv.StartsWith($ResolvedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'EnvFile must stay inside the repository.'
}

$requiredFiles = @(
    'backend/Dockerfile',
    'agent-service/Dockerfile',
    'frontend/Dockerfile',
    'deploy/compose.yml',
    'deploy/nginx.conf',
    'deploy/prometheus.yml',
    'deploy/alerts.yml',
    'deploy/alertmanager.yml',
    'deploy/blackbox.yml',
    'database/catalog-408-2026.json',
    'scripts/alertmanager-webhook-drill.ps1'
    'scripts/complete-catalog-408.ps1'
)
foreach ($relative in $requiredFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $RepoRoot $relative) -PathType Leaf)) {
        throw "Missing deployment file: $relative"
    }
}
if (-not (Test-Path -LiteralPath $ResolvedEnv -PathType Leaf)) {
    throw "Environment file not found: $ResolvedEnv"
}

$entries = @{}
Get-Content -LiteralPath $ResolvedEnv -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#') -and $line.Contains('=')) {
        $key, $value = $line.Split('=', 2)
        $entries[$key.Trim()] = $value.Trim()
    }
}
foreach ($key in 'MYSQL_DATABASE','MYSQL_USERNAME','MYSQL_PASSWORD','MYSQL_ROOT_PASSWORD','ADMIN_USERNAME','ADMIN_PASSWORD','AGENT_INTERNAL_TOKEN') {
    if (-not $entries.ContainsKey($key) -or [string]::IsNullOrWhiteSpace($entries[$key])) {
        throw "Missing required deployment setting: $key"
    }
}
if ($entries.ContainsKey('ALERT_WEBHOOK_URL') -and $entries['ALERT_WEBHOOK_URL'] -notmatch '^https?://') {
    throw 'ALERT_WEBHOOK_URL must use http or https.'
}

if ($Start) {
    $placeholder = $entries.GetEnumerator() | Where-Object { $_.Value -match '^replace-with-' }
    if ($placeholder) {
        throw "Replace placeholder secrets before starting: $($placeholder.Key -join ', ')"
    }
    $modelConfig = Join-Path $RepoRoot 'agent-service/models/bge-small-zh-v1.5/config.json'
    if (-not (Test-Path -LiteralPath $modelConfig -PathType Leaf)) {
        throw 'Local BGE model is required before starting the deployment.'
    }
    $rerankerConfig = Join-Path $RepoRoot 'agent-service/models/bge-reranker-base/config.json'
    if (-not (Test-Path -LiteralPath $rerankerConfig -PathType Leaf)) {
        throw 'Local BGE reranker model is required before starting the deployment.'
    }
}

if ($Registry) {
    $env:CONTAINER_REGISTRY = $Registry
}
$effectiveRegistry = if ($env:CONTAINER_REGISTRY) {
    $env:CONTAINER_REGISTRY
} elseif ($entries.ContainsKey('CONTAINER_REGISTRY') -and $entries['CONTAINER_REGISTRY']) {
    $entries['CONTAINER_REGISTRY']
} else {
    'docker.io'
}

& docker compose --env-file $ResolvedEnv -f $ComposeFile config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose configuration is invalid.' }

if ($Build -or $Start) {
    & docker compose --env-file $ResolvedEnv -f $ComposeFile build backend agent frontend
    if ($LASTEXITCODE -ne 0) { throw 'Deployment image build failed.' }
}
if ($Start) {
    & docker compose --env-file $ResolvedEnv -f $ComposeFile up -d
    if ($LASTEXITCODE -ne 0) { throw 'Deployment startup failed.' }

    $deadline = (Get-Date).AddMinutes(5)
    $indexSync = $null
    do {
        $syncOutput = & docker compose --env-file $ResolvedEnv -f $ComposeFile ps -a --format json index-sync
        if ($LASTEXITCODE -ne 0) { throw 'Could not inspect the index bootstrap job.' }
        if ($syncOutput) { $indexSync = $syncOutput | ConvertFrom-Json }
        if ($indexSync -and $indexSync.State -eq 'exited') { break }
        Start-Sleep -Seconds 5
    } while ((Get-Date) -lt $deadline)
    if (-not $indexSync -or $indexSync.State -ne 'exited' -or $indexSync.ExitCode -ne 0) {
        & docker compose --env-file $ResolvedEnv -f $ComposeFile logs --no-color --tail 100 index-sync
        throw 'Catalog bootstrap or Agent index synchronization failed.'
    }

    $persistentNames = @('mysql', 'backend', 'agent', 'frontend', 'blackbox', 'prometheus', 'alertmanager')
    $serviceOutput = & docker compose --env-file $ResolvedEnv -f $ComposeFile ps --format json @persistentNames
    if ($LASTEXITCODE -ne 0) { throw 'Could not inspect deployment services.' }
    $services = @($serviceOutput | ConvertFrom-Json)
    $notReady = @($services | Where-Object {
        $_.State -ne 'running' -or ($_.Health -and $_.Health -ne 'healthy')
    })
    if ($services.Count -ne $persistentNames.Count -or $notReady.Count -gt 0) {
        throw 'One or more persistent deployment services are not ready.'
    }
}

[pscustomobject]@{
    Compose = 'VALID'
    Environment = $ResolvedEnv
    Registry = $effectiveRegistry
    Images = if ($Build -or $Start) { 'BUILT' } else { 'NOT_REQUESTED' }
    Services = if ($Start) { 'STARTED' } else { 'NOT_REQUESTED' }
} | Format-List
