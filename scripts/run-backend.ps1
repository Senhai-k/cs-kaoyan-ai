$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$BackendDir = Join-Path $RepoRoot 'backend'
$Jar = Join-Path $BackendDir 'target\cs-kaoyan-ai-backend-0.0.1-SNAPSHOT.jar'
$RuntimeDir = Join-Path $BackendDir 'target\runtime'
$RuntimeJar = Join-Path $RuntimeDir 'cs-kaoyan-ai-backend-runtime.jar'

Set-Location $BackendDir

$provider = if ($env:AI_PROVIDER) { $env:AI_PROVIDER } else { 'agent' }
$agentEndpoint = if ($env:AI_AGENT_ENDPOINT) { $env:AI_AGENT_ENDPOINT } else { 'http://127.0.0.1:18889' }
$allowFakeIp = if ($env:OFFICIAL_LINK_DISCOVERY_ALLOW_FAKE_IP) { $env:OFFICIAL_LINK_DISCOVERY_ALLOW_FAKE_IP } else { 'true' }

if ([string]::IsNullOrWhiteSpace($env:ADMIN_PASSWORD)) {
    throw 'ADMIN_PASSWORD is required. Use scripts/start-dev.ps1 to generate a temporary local password.'
}

if (-not (Test-Path $Jar)) {
    & mvn.cmd package
}

New-Item -ItemType Directory -Path $RuntimeDir -Force | Out-Null
Copy-Item -LiteralPath $Jar -Destination $RuntimeJar -Force

& java.exe -jar $RuntimeJar --server.port=18888 "--app.ai.provider=$provider" "--app.ai.agent.endpoint=$agentEndpoint" "--app.official-link-discovery.allow-fake-ip=$allowFakeIp"
