$ErrorActionPreference = 'Continue'
$pidFile = 'C:\Users\LQP\IdeaProjects\adaptiveCapacityOfUrbanTransportNetwork\out\transitbuilder_layer3.pid'
$logFile = 'C:\Users\LQP\IdeaProjects\adaptiveCapacityOfUrbanTransportNetwork\out\transitBuilder_layer3.log'
$errFile = 'C:\Users\LQP\IdeaProjects\adaptiveCapacityOfUrbanTransportNetwork\out\transitBuilder_layer3_err.log'

$startWait = 30
if ($args.Count -gt 0) { [int]$startWait = $args[0] }

Write-Host ("Sleep " + $startWait + "s then monitor...")
Start-Sleep -Seconds $startWait

$pidVal = Get-Content -LiteralPath $pidFile -ErrorAction SilentlyContinue
$proc = $null
if ($pidVal) {
    $proc = Get-Process -Id $pidVal -ErrorAction SilentlyContinue
}

if ($proc) {
    Write-Host ("PID " + $pidVal + " running, CPU=" + $proc.CPU + "s, Mem=" + [Math]::Round($proc.WorkingSet64/1MB,1) + "MB")
} else {
    Write-Host ("PID " + $pidVal + " 已退出 (HasExited=True)")
}

if (Test-Path $logFile) {
    $size = (Get-Item $logFile).Length
    Write-Host ("Log size: " + $size + " bytes (" + [Math]::Round($size/1KB,1) + " KB)")
    if ($size -gt 0) {
        Write-Host '--- Last 40 lines of stdout log ---'
        Get-Content -LiteralPath $logFile -Encoding UTF8 -Tail 40 | ForEach-Object { Write-Host ("  " + $_) }
    }
}

if (Test-Path $errFile) {
    $size = (Get-Item $errFile).Length
    if ($size -gt 0) {
        Write-Host ("--- ERR log size: " + $size + " bytes ---")
        Get-Content -LiteralPath $errFile -Encoding UTF8 -Tail 20 | ForEach-Object { Write-Host ("  " + $_) }
    }
}

# 顺便看下 D 盘输出文件大小
$baseDir = 'D:\Luan\2025-09\MATSim\guangzhoubaseline'
$netXml = Join-Path $baseDir 'network_with_transit.xml'
$schXml = Join-Path $baseDir 'transitSchedule.xml'
foreach ($f in @($netXml, $schXml)) {
    if (Test-Path $f) {
        Write-Host ("  D: " + (Split-Path -Leaf $f) + " = " + [Math]::Round((Get-Item $f).Length/1MB,2) + " MB")
    }
}