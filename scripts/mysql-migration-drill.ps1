param(
    [int]$MySqlPort = 33306,
    [int]$BackendPort = 18890,
    [switch]$KeepWorkspace
)

$ErrorActionPreference = 'Stop'
$RepoRoot = Split-Path -Parent $PSScriptRoot
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$drillRoot = Join-Path $RepoRoot "backups\mysql-drill-$stamp"
$dataDir = Join-Path $drillRoot 'data'
$configFile = Join-Path $drillRoot 'my.ini'
$mysqlLog = Join-Path $drillRoot 'mysql.log'
$backendLog = Join-Path $drillRoot 'backend.log'
$mysqlProcess = $null
$backendProcess = $null
$mysqlAdmin = $null
$succeeded = $false

function Wait-TcpPort([int]$Port, [int]$Seconds) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        $client = [System.Net.Sockets.TcpClient]::new()
        try {
            $connection = $client.ConnectAsync('127.0.0.1', $Port)
            if ($connection.Wait(500) -and $client.Connected) {
                return $true
            }
        } catch {
            # The server may still be initializing.
        } finally {
            $client.Dispose()
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    return $false
}

function Wait-HttpOk([string]$Url, [int]$Seconds) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    do {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 2
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return $true
            }
        } catch {
            Start-Sleep -Milliseconds 700
        }
    } while ((Get-Date) -lt $deadline)
    return $false
}

try {
    foreach ($port in @($MySqlPort, $BackendPort)) {
        if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
            throw "Port $port is already in use. Choose another drill port."
        }
    }

    $mysqld = (Get-Command mysqld.exe -ErrorAction Stop).Source
    $mysql = Join-Path (Split-Path -Parent $mysqld) 'mysql.exe'
    $mysqlAdmin = Join-Path (Split-Path -Parent $mysqld) 'mysqladmin.exe'
    $mysqlBase = Split-Path -Parent (Split-Path -Parent $mysqld)
    $backendJar = Join-Path $RepoRoot 'backend\target\cs-kaoyan-ai-backend-0.0.1-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $backendJar)) {
        throw 'Backend jar is missing. Run mvn.cmd package in backend first.'
    }

    New-Item -ItemType Directory -Force -Path $dataDir | Out-Null
    $config = @"
[mysqld]
basedir="$($mysqlBase.Replace('\', '/'))"
datadir="$($dataDir.Replace('\', '/'))"
port=$MySqlPort
bind-address=127.0.0.1
mysqlx=OFF
skip-log-bin
log-error="$($mysqlLog.Replace('\', '/'))"
pid-file="$((Join-Path $drillRoot 'mysql.pid').Replace('\', '/'))"
"@
    Set-Content -LiteralPath $configFile -Value $config -Encoding ASCII

    $initializeOutput = & $mysqld "--defaults-file=$configFile" '--initialize-insecure' 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Temporary MySQL initialization failed: $($initializeOutput -join [Environment]::NewLine)"
    }

    $mysqlProcess = Start-Process -FilePath $mysqld -ArgumentList "--defaults-file=$configFile" -PassThru -WindowStyle Hidden
    if (-not (Wait-TcpPort $MySqlPort 45)) {
        throw "Temporary MySQL did not start: $(Get-Content -LiteralPath $mysqlLog -Raw -ErrorAction SilentlyContinue)"
    }

    $createOutput = & $mysql '--protocol=TCP' '--host=127.0.0.1' "--port=$MySqlPort" '--user=root' `
        '--execute=CREATE DATABASE cs_kaoyan_ai CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;' 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Could not create drill database: $($createOutput -join [Environment]::NewLine)"
    }

    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = (Get-Command java.exe -ErrorAction Stop).Source
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $jdbcUrl = "jdbc:mysql://127.0.0.1:$MySqlPort/cs_kaoyan_ai?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
    $startInfo.Arguments = @(
        '-jar'
        "`"$backendJar`""
        '--spring.profiles.active=mysql'
        "`"--spring.datasource.url=$jdbcUrl`""
        '--spring.datasource.username=root'
        '--spring.datasource.password='
        "--server.port=$BackendPort"
        "`"--logging.file.name=$backendLog`""
    ) -join ' '
    $backendProcess = [System.Diagnostics.Process]::Start($startInfo)

    if (-not (Wait-HttpOk "http://127.0.0.1:$BackendPort/api/health" 60)) {
        $logs = @(
            Get-Content -LiteralPath $backendLog -Raw -ErrorAction SilentlyContinue
        ) -join [Environment]::NewLine
        throw "Backend did not become healthy on the migrated schema: $logs"
    }

    $query = @'
SELECT CONCAT(version, '|', description, '|', success) AS migration
FROM flyway_schema_history
ORDER BY installed_rank;
SELECT CONCAT(
  SUM(table_name = 'school'), '|',
  SUM(table_name = 'source_document'), '|',
  SUM(table_name = 'source_document_version'), '|',
  SUM(table_name = 'admin_session'), '|',
  SUM(table_name = 'document_parse_task'), '|',
  SUM(table_name = 'web_capture_task'), '|',
  SUM(table_name = 'document_publication_batch'), '|',
  SUM(table_name = 'document_publication_batch_item'), '|',
  SUM(table_name = 'web_capture_change'), '|',
  SUM(table_name = 'web_capture_schedule'), '|',
  SUM(table_name = 'national_score_line'), '|',
  SUM(table_name = 'school_score_line'), '|',
  SUM(table_name = 'admission_result_import_batch'), '|',
  SUM(table_name = 'admission_result_candidate')
) AS required_tables
FROM information_schema.tables
WHERE table_schema = 'cs_kaoyan_ai';
SELECT CONCAT('rbac|', COUNT(*)) AS rbac_column
FROM information_schema.columns
WHERE table_schema = 'cs_kaoyan_ai'
  AND table_name = 'admin_user'
  AND column_name = 'role';
SELECT CONCAT(
  (SELECT COUNT(*) FROM school), '|',
  (SELECT COUNT(*) FROM document_source), '|',
  (SELECT COUNT(*) FROM source_document), '|',
  (SELECT COUNT(*) FROM document_chunk), '|',
  (SELECT COUNT(*) FROM exam_subject), '|',
  (SELECT COUNT(*) FROM admission_plan), '|',
  (SELECT COUNT(*) FROM score_line), '|',
  (SELECT COUNT(*) FROM retest_rule), '|',
  (SELECT COUNT(*) FROM national_score_line), '|',
  (SELECT COUNT(*) FROM school WHERE is_self_determined_score = 1)
) AS verified_focus_data;
'@
    $verification = & $mysql '--batch' '--skip-column-names' '--protocol=TCP' '--host=127.0.0.1' `
        "--port=$MySqlPort" '--user=root' '--database=cs_kaoyan_ai' "--execute=$query" 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Migration verification query failed: $($verification -join [Environment]::NewLine)"
    }
    $lines = @($verification | ForEach-Object { $_.ToString().Trim() } | Where-Object { $_ })
    if ($lines -notcontains '1|baseline|1' -or
        $lines -notcontains '2|source document version|1' -or
        $lines -notcontains '3|admin rbac|1' -or
        $lines -notcontains '4|persistent admin session|1' -or
        $lines -notcontains '5|document parse task|1' -or
        $lines -notcontains '6|controlled web capture|1' -or
        $lines -notcontains '7|document publication batch|1' -or
        $lines -notcontains '8|web capture change|1' -or
        $lines -notcontains '9|web capture schedule|1' -or
        $lines -notcontains '10|verified focus school data|1' -or
        $lines -notcontains '11|expand major research direction|1' -or
        $lines -notcontains '12|national score line baseline|1' -or
        $lines -notcontains '13|school score line|1' -or
        $lines -notcontains '14|admission result import draft|1' -or
        $lines -notcontains '15|retest rule scope|1' -or
        $lines -notcontains '1|1|1|1|1|1|1|1|1|1|1|1|1|1' -or
        $lines -notcontains 'rbac|1' -or
        $lines -notcontains '10|18|9|9|3|3|1|2|6|6') {
        throw "Unexpected migration verification result: $($lines -join ', ')"
    }

    $succeeded = $true
    [pscustomobject]@{
        MySql = "isolated 127.0.0.1:$MySqlPort"
        BackendHealth = 'UP'
        FlywayV1 = 'SUCCESS'
        FlywayV2 = 'SUCCESS'
        FlywayV3 = 'SUCCESS'
        FlywayV4 = 'SUCCESS'
        FlywayV5 = 'SUCCESS'
        FlywayV6 = 'SUCCESS'
        FlywayV7 = 'SUCCESS'
        FlywayV8 = 'SUCCESS'
        FlywayV9 = 'SUCCESS'
        FlywayV10 = 'SUCCESS'
        FlywayV11 = 'SUCCESS'
        FlywayV12 = 'SUCCESS'
        FlywayV13 = 'SUCCESS'
        FlywayV14 = 'SUCCESS'
        FlywayV15 = 'SUCCESS'
        VerifiedFocusData = '10 schools|18 sources|9 documents|9 chunks|3 subjects|3 plans|1 score line|2 retest rules|6 national lines|6 self-determined schools'
        RequiredTables = 'school|source_document|source_document_version|admin_session|document_parse_task|web_capture_task|document_publication_batch|document_publication_batch_item|web_capture_change|web_capture_schedule|national_score_line|school_score_line|admission_result_import_batch|admission_result_candidate'
        RbacColumn = 'admin_user.role'
        Verified = $true
    } | Format-List
} finally {
    if ($backendProcess -and -not $backendProcess.HasExited) {
        Stop-Process -Id $backendProcess.Id -Force -ErrorAction SilentlyContinue
        [void]$backendProcess.WaitForExit(5000)
    }
    if ($mysqlProcess -and -not $mysqlProcess.HasExited) {
        try {
            & $mysqlAdmin '--protocol=TCP' '--host=127.0.0.1' "--port=$MySqlPort" '--user=root' 'shutdown' 2>$null
            [void]$mysqlProcess.WaitForExit(15000)
        } catch {
            # Force termination below if graceful shutdown is unavailable.
        }
        if (-not $mysqlProcess.HasExited) {
            Stop-Process -Id $mysqlProcess.Id -Force -ErrorAction SilentlyContinue
            [void]$mysqlProcess.WaitForExit(5000)
        }
    }
    if (($succeeded -and -not $KeepWorkspace) -and (Test-Path -LiteralPath $drillRoot)) {
        $resolvedRoot = [System.IO.Path]::GetFullPath($drillRoot)
        $resolvedBackups = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot 'backups'))
        if ($resolvedRoot.StartsWith($resolvedBackups, [System.StringComparison]::OrdinalIgnoreCase)) {
            for ($attempt = 1; $attempt -le 10 -and (Test-Path -LiteralPath $resolvedRoot); $attempt++) {
                try {
                    Remove-Item -LiteralPath $resolvedRoot -Recurse -Force -ErrorAction Stop
                } catch {
                    if ($attempt -eq 10) { throw }
                    Start-Sleep -Milliseconds 500
                }
            }
        }
    }
    if (-not $succeeded) {
        Write-Warning "Drill workspace retained for diagnosis: $drillRoot"
    }
}
