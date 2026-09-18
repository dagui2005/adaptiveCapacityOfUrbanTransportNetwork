# 路径 1：重生成 network + transitSchedule（含桥接代码）
# 步骤:
#   1) 备份 D 盘文件到 D:\Luan\2025-09\MATSim\guangzhoubaseline\.bak_layer5b\
#   2) 删除旧文件，让 TransitBuilder 重新生成
#   3) 检查 .class 是否含桥接代码（不应再次触发重新编译）
#   4) 后台启动 TransitBuilder，记录 PID

$ErrorActionPreference = 'Stop'

$baseDir = 'D:\Luan\2025-09\MATSim\guangzhoubaseline'
$bakDir  = "$baseDir\.bak_layer5b"
$netXml  = "$baseDir\network_with_transit.xml"
$schXml  = "$baseDir\transitSchedule.xml"

Write-Host '======================================'
Write-Host '  路径 1 启动: 重生成 network + schedule'
Write-Host ("  " + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
Write-Host '======================================'

# [1] 创建备份目录
if (-not (Test-Path $bakDir)) {
    New-Item -ItemType Directory -Path $bakDir | Out-Null
    Write-Host ('  [OK] 备份目录已创建: ' + $bakDir)
}

# [2] 备份文件
foreach ($f in @($netXml, $schXml)) {
    if (Test-Path $f) {
        $name = Split-Path -Leaf $f
        $bakFile = "$bakDir\$name"
        if (-not (Test-Path $bakFile)) {
            Copy-Item $f $bakFile
            $size = (Get-Item $bakFile).Length
            Write-Host ('  [OK] 备份: {0} ({1:N1} MB)' -f $name, ($size/1MB))
        } else {
            $size = (Get-Item $bakFile).Length
            Write-Host ('  [SKIP] 备份已存在: {0} ({1:N1} MB)' -f $name, ($size/1MB))
        }
    } else {
        Write-Host ('  [WARN] 源文件不存在: ' + $f)
    }
}

# [3] 删除旧文件（让 TransitBuilder 重生成）
foreach ($f in @($netXml, $schXml)) {
    if (Test-Path $f) {
        Remove-Item $f -Force
        Write-Host ('  [DEL] 删除旧文件: ' + (Split-Path -Leaf $f))
    }
}

# [4] 检查 .class 状态
Write-Host ''
Write-Host '  --- .class 状态检查 ---'
$classFile = 'simulation\target\classes\org\matsim\network\BusNetworkIntegrator.class'
if (Test-Path $classFile) {
    $classBytes = [System.IO.File]::ReadAllBytes($classFile)
    $classText  = [System.Text.Encoding]::ASCII.GetString($classBytes)
    Write-Host ('  Class file: ' + $classFile)
    Write-Host ('  Modified  : ' + (Get-Item $classFile).LastWriteTime)
    $tokens = @('bridgeSegIndex','leftIsVirt','rightIsVirt','createVirtualLinkBetweenNodes','euclidDist','bridgeLen')
    foreach ($t in $tokens) {
        Write-Host ('    {0,-35}: {1}' -f $t, $classText.Contains($t))
    }
} else {
    Write-Host '  !! Class file not found'
}

Write-Host ''
Write-Host '======================================'
Write-Host '  Backup 完成，可以启动 TransitBuilder'
Write-Host '======================================'