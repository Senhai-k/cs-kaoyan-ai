$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$FrontendDir = Join-Path $RepoRoot 'frontend'

Set-Location $FrontendDir

if (Test-Path (Join-Path $FrontendDir 'start-dev-server.mjs')) {
    & node.exe .\start-dev-server.mjs
} else {
    & npm.cmd run dev -- --host 127.0.0.1 --port 5173
}
