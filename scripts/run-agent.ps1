$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$AgentDir = Join-Path $RepoRoot 'agent-service'
$Python = Join-Path $AgentDir '.venv\Scripts\python.exe'
$ModelDir = Join-Path $AgentDir 'models\bge-small-zh-v1.5'
$RerankerModelDir = Join-Path $AgentDir 'models\bge-reranker-base'

if (-not (Test-Path -LiteralPath $Python -PathType Leaf)) {
    throw 'Agent virtual environment is missing. See README.md, section "LangGraph Agent".'
}
if (-not (Test-Path -LiteralPath (Join-Path $ModelDir 'config.json') -PathType Leaf)) {
    throw 'Local BGE model is missing. See README.md, section "LangGraph Agent".'
}

$env:AGENT_EMBEDDING_MODEL = $ModelDir
$env:AGENT_RERANKER_MODEL = $RerankerModelDir
$env:AGENT_DATA_DIR = Join-Path $AgentDir 'data'
$env:AGENT_SPRING_BASE_URL = 'http://127.0.0.1:18888'
$env:PYTHONDONTWRITEBYTECODE = '1'
Set-Location $AgentDir
& $Python -m uvicorn app.main:app --host 127.0.0.1 --port 18889
