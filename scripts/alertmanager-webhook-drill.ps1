param(
    [string]$AlertmanagerBase = 'http://127.0.0.1:19093',
    [int]$ListenPort = 19094,
    [int]$TimeoutSeconds = 30
)

$ErrorActionPreference = 'Stop'

function Receive-Webhook([System.Net.Sockets.TcpListener]$Listener) {
    $accept = $Listener.AcceptTcpClientAsync()
    if (-not $accept.Wait([TimeSpan]::FromSeconds($TimeoutSeconds))) {
        throw "Webhook was not delivered within $TimeoutSeconds seconds."
    }

    $client = $accept.Result
    try {
        $stream = $client.GetStream()
        $stream.ReadTimeout = $TimeoutSeconds * 1000
        $bytes = [System.Collections.Generic.List[byte]]::new()
        $buffer = [byte[]]::new(8192)
        $headerEnd = -1
        $contentLength = 0

        while ($true) {
            $read = $stream.Read($buffer, 0, $buffer.Length)
            if ($read -le 0) { break }
            for ($index = 0; $index -lt $read; $index++) {
                $bytes.Add($buffer[$index])
            }

            $raw = [Text.Encoding]::UTF8.GetString($bytes.ToArray())
            if ($headerEnd -lt 0) {
                $headerEnd = $raw.IndexOf("`r`n`r`n", [StringComparison]::Ordinal)
                if ($headerEnd -ge 0) {
                    $headers = $raw.Substring(0, $headerEnd)
                    if ($headers -notmatch '(?im)^Content-Length:\s*(\d+)\s*$') {
                        throw 'Webhook request did not include Content-Length.'
                    }
                    $contentLength = [int]$Matches[1]
                }
            }
            if ($headerEnd -ge 0 -and $bytes.Count -ge ($headerEnd + 4 + $contentLength)) { break }
        }

        if ($headerEnd -lt 0) { throw 'Webhook request headers were incomplete.' }
        $bodyOffset = $headerEnd + 4
        $bodyBytes = $bytes.GetRange($bodyOffset, $contentLength).ToArray()
        $body = [Text.Encoding]::UTF8.GetString($bodyBytes) | ConvertFrom-Json

        $response = [Text.Encoding]::ASCII.GetBytes("HTTP/1.1 200 OK`r`nContent-Length: 2`r`nConnection: close`r`n`r`nOK")
        $stream.Write($response, 0, $response.Length)
        $stream.Flush()
        return $body
    } finally {
        $client.Dispose()
    }
}

function Send-DrillAlert([string]$StartsAt, [string]$EndsAt) {
    $alerts = @(
        @{
            labels = @{
                alertname = 'MilestoneFWebhookDrill'
                severity = 'warning'
                service = 'deployment-drill'
            }
            annotations = @{ summary = 'Milestone F webhook delivery drill' }
            startsAt = $StartsAt
            endsAt = $EndsAt
            generatorURL = 'http://localhost/milestone-f-drill'
        }
    )
    $payload = ConvertTo-Json -InputObject $alerts -Depth 6 -Compress

    Invoke-RestMethod -Method Post -Uri "$AlertmanagerBase/api/v2/alerts" `
        -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($payload)) `
        -TimeoutSec 10 | Out-Null
}

$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $ListenPort)
$listener.Start()
try {
    $startsAt = (Get-Date).ToUniversalTime().AddSeconds(-1).ToString('o')
    Send-DrillAlert -StartsAt $startsAt -EndsAt (Get-Date).ToUniversalTime().AddMinutes(5).ToString('o')
    $firing = Receive-Webhook -Listener $listener
    if ($firing.status -ne 'firing' -or $firing.alerts[0].labels.alertname -ne 'MilestoneFWebhookDrill') {
        throw 'The firing webhook payload did not match the drill alert.'
    }

    Send-DrillAlert -StartsAt $startsAt -EndsAt (Get-Date).ToUniversalTime().AddSeconds(-1).ToString('o')
    $resolved = Receive-Webhook -Listener $listener
    if ($resolved.status -ne 'resolved' -or $resolved.alerts[0].labels.alertname -ne 'MilestoneFWebhookDrill') {
        throw 'The resolved webhook payload did not match the drill alert.'
    }

    [pscustomobject]@{
        Alertmanager = 'REACHABLE'
        FiringDelivery = 'VERIFIED'
        ResolvedDelivery = 'VERIFIED'
        Receiver = $firing.receiver
    } | Format-List
} finally {
    $listener.Stop()
}
