$ErrorActionPreference = 'Continue'

$batFile  = 'C:\Users\LQP\IdeaProjects\adaptiveCapacityOfUrbanTransportNetwork\out\run_transitbuilder_layer2.bat'
$logFile  = 'C:\Users\LQP\IdeaProjects\adaptiveCapacityOfUrbanTransportNetwork\out\transitBuilder_layer3.log'
$errFile  = 'C:\Users\LQP\IdeaProjects\adaptiveCapacityOfUrbanTransportNetwork\out\transitBuilder_layer3_err.log'

# 删除旧 log
if (Test-Path $logFile) { Remove-Item $logFile -Force }
if (Test-Path $errFile) { Remove-Item $errFile -Force }

# 启动 TransitBuilder 后台运行
$proc = Start-Process -FilePath 'cmd.exe' `
                     -ArgumentList '/c', $batFile `
                     -PassThru `
                     -RedirectStandardOutput $logFile `
                     -RedirectStandardError $errFile `
                     -WindowStyle Hidden

Write-Host ('Started TransitBuilder')
Write-Host ('  PID: ' + $proc.Id)
Write-Host ('  Log: ' + $logFile)

# 保存 PID 以备查询
$proc.Id | Out-File 'C:\Users\LQP\IdeaProjects\adaptiveCapacityOfUrbanTransportNetwork\out\transitbuilder_layer3.pid'

# 等 5 秒确认启动
Start-Sleep -Seconds 5
$running = -not $proc.HasExited
Write-Host ('  After 5s, running: ' + $running)

if (Test-Path $logFile) {
    $size = (Get-Item $logFile).Length
    Write-Host ('  Log size: ' + $size + ' bytes')
    if ($size -gt 0) {
        Write-Host '  --- First 20 lines of log ---'
        Get-Content -LiteralPath $logFile -Encoding UTF8 -TotalCount 20 | ForEach-Object { Write-Host ('    ' + $_) }
    }
}