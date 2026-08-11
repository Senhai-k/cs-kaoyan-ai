[CmdletBinding()]
param(
    [string]$EnvFile = '',
    [string]$BackendUrl = 'http://127.0.0.1:18888',
    [int]$TimeoutSeconds = 900,
    [switch]$RestartAgent
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $PSScriptRoot '..\deploy\.env'
}

function Read-DotEnv([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Environment file not found: $Path"
    }
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -match '^\s*(?:#|$)') { continue }
        if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') { continue }
        $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$Matches[1]] = $value
    }
    return $values
}

function Require-PositiveRate([hashtable]$Values, [string]$Name) {
    if (-not $Values.ContainsKey($Name) -or [string]::IsNullOrWhiteSpace($Values[$Name])) {
        throw "$Name is required for a measured planner experiment"
    }
    $parsed = 0.0
    if (-not [double]::TryParse($Values[$Name], [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref]$parsed) -or $parsed -le 0) {
        throw "$Name must be a positive USD per million token rate"
    }
}

function Invoke-Json([string]$Method, [string]$Uri, [hashtable]$Headers, [object]$Body = $null) {
    $params = @{ Method = $Method; Uri = $Uri; Headers = $Headers; TimeoutSec = 30 }
    if ($null -ne $Body) {
        $params.ContentType = 'application/json'
        $params.Body = ($Body | ConvertTo-Json -Depth 10 -Compress)
    }
    return Invoke-RestMethod @params
}

$values = Read-DotEnv $EnvFile
foreach ($name in @('AGENT_OPENAI_API_KEY', 'AGENT_OPENAI_MODEL')) {
    if (-not $values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($values[$name])) {
        throw "$name is required for a real planner A/B experiment"
    }
}
$pricingMode = if ($values.ContainsKey('AGENT_PLANNER_PRICING_MODE')) {
    [string]$values['AGENT_PLANNER_PRICING_MODE']
} else {
    'METERED'
}
$pricingMode = $pricingMode.Trim().ToUpperInvariant()
if ($pricingMode -notin @('METERED', 'UNMETERED')) {
    throw 'AGENT_PLANNER_PRICING_MODE must be METERED or UNMETERED'
}
if ($pricingMode -eq 'METERED') {
    Require-PositiveRate $values 'AGENT_PLANNER_INPUT_COST_PER_MILLION_USD'
    Require-PositiveRate $values 'AGENT_PLANNER_OUTPUT_COST_PER_MILLION_USD'
}

if ($RestartAgent) {
    $composeFile = Join-Path $PSScriptRoot '..\deploy\compose.yml'
    docker compose --env-file $EnvFile -f $composeFile up -d --no-deps --force-recreate agent
}

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
do {
    try {
        $health = Invoke-RestMethod -UseBasicParsing -Uri 'http://127.0.0.1:18889/api/health' -TimeoutSec 5
        if ($health.status -eq 'UP') { break }
    } catch {
        # Agent may still be loading the local embedding and reranker models.
    }
    Start-Sleep -Seconds 3
} while ((Get-Date) -lt $deadline)
if ($null -eq $health -or $health.status -ne 'UP') {
    throw 'Agent did not become healthy before the timeout'
}

$username = [string]$values['ADMIN_USERNAME']
$password = [string]$values['ADMIN_PASSWORD']
if ([string]::IsNullOrWhiteSpace($username) -or [string]::IsNullOrWhiteSpace($password)) {
    throw 'ADMIN_USERNAME and ADMIN_PASSWORD are required to submit an admin operation'
}
$login = Invoke-Json 'Post' "$BackendUrl/api/auth/login" @{} @{ username = $username; password = $password }
$token = [string]$login.data.token
if ([string]::IsNullOrWhiteSpace($token)) { throw 'Admin login did not return a token' }
$headers = @{ Authorization = "Bearer $token" }
$job = Invoke-Json 'Post' "$BackendUrl/api/ai/agent/operations/jobs" $headers @{ operation_type = 'PLANNER_EVALUATION' }
$job = $job.data

do {
    Start-Sleep -Seconds 2
    $current = (Invoke-Json 'Get' "$BackendUrl/api/ai/agent/operations/jobs/$($job.id)" $headers).data
    if ($current.status -in @('COMPLETED', 'FAILED', 'CANCELLED')) { break }
} while ((Get-Date) -lt $deadline)
if ($null -eq $current -or $current.status -notin @('COMPLETED', 'FAILED', 'CANCELLED')) {
    throw "Planner A/B job timed out: $($job.id)"
}

$result = $current.result
$gate = $result.quality_gate
[pscustomobject]@{
    JobId = $current.id
    JobStatus = $current.status
    QualityGate = $gate.status
    DeterministicExactMatch = $result.deterministic.exact_match_rate
    DeterministicLatencyMs = $result.deterministic.average_latency_ms
    LlmStatus = $result.llm.status
    LlmExactMatch = $result.llm.exact_match_rate
    LlmTargetRecall = $result.llm.target_recall
    LlmLatencyMs = $result.llm.average_latency_ms
    LlmTokens = $result.llm.total_tokens
    PricingMode = $result.llm_readiness.pricingMode
    LlmCostStatus = $result.llm.cost_status
    LlmEstimatedCostUsd = $result.llm.estimated_cost_usd
    LlmInvalidProposalRate = $result.llm.invalid_proposal_rate
} | Format-List

if ($current.status -ne 'COMPLETED') { throw "Planner A/B job failed: $($current.error)" }
if ($gate.status -ne 'PASSED') { throw 'Planner A/B completed but quality-gate-v3 failed' }
