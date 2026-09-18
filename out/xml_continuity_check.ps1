# 独立 XML 节点连续性检查脚本 v2
$ErrorActionPreference = 'Continue'

$netXml = 'D:\Luan\2025-09\MATSim\guangzhoubaseline\network_with_transit.xml'

Write-Host '======================================'
Write-Host '  XML 节点连续性检查 (新网络)'
Write-Host ("  " + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
Write-Host ("  File: " + $netXml)
Write-Host ("  Size: " + [Math]::Round((Get-Item $netXml).Length/1MB,2) + " MB")
Write-Host '======================================'

Write-Host ''
Write-Host '  [Step 1] 流式解析所有 link...'

# 使用 Select-String 找所有 <link 开头的行
$linkLines = Select-String -LiteralPath $netXml -Pattern '<link ' -Encoding UTF8
$totalLinkLines = $linkLines.Count
Write-Host ("  Total link lines: " + $totalLinkLines)

# 解析 (使用 here-string 避免转义问题)
$linkFrom = @{}
$linkTo = @{}
$busVirtCount = 0
$realLinkCount = 0

# 用单引号正则避免 $ 符号问题
$linkPattern = '^\s*<link\s+id="([^"]+)"\s+from="([^"]+)"\s+to="([^"]+)"'

foreach ($ll in $linkLines) {
    $lineText = $ll.Line
    if ($lineText -match $linkPattern) {
        $lid = $Matches[1]
        $fr  = $Matches[2]
        $to  = $Matches[3]
        $linkFrom[$lid] = $fr
        $linkTo[$lid] = $to
        if ($lid.StartsWith('bus_virt')) {
            $busVirtCount++
        } else {
            $realLinkCount++
        }
    }
}
Write-Host ("  Real links : " + $realLinkCount)
Write-Host ("  bus_virt_* : " + $busVirtCount)

Write-Host ''
Write-Host '  [Step 2] 按 lineId 分组...'

$groups = @{}
$noMatchCount = 0

# bus_virt_<lineId>_seg<segId>_<fromN>_<toN>
$segPattern = '^bus_virt_(.+?)_seg(\d+)_(\S+)_(\S+)$'

foreach ($key in $linkFrom.Keys) {
    if (-not $key.StartsWith('bus_virt')) { continue }
    if ($key -match $segPattern) {
        $lineId = $Matches[1]
        $segId  = [int]$Matches[2]
        $fromN  = $Matches[3]
        $toN    = $Matches[4]
        if (-not $groups.ContainsKey($lineId)) {
            $groups[$lineId] = New-Object System.Collections.ArrayList
        }
        $entry = New-Object PSObject -Property @{
            linkId = $key
            segId  = $segId
            fromN  = $fromN
            toN    = $toN
        }
        $groups[$lineId].Add($entry) | Out-Null
    } else {
        $noMatchCount++
    }
}
Write-Host ("  Total line groups: " + $groups.Count)
Write-Host ("  bus_virt_* without seg pattern: " + $noMatchCount)

Write-Host ''
Write-Host '  [Step 3] 节点连续性检查...'

$totalGroups = $groups.Count
$brokenGroups = 0
$totalLinksChecked = 0
$totalBrokenConnections = 0
$examples = New-Object System.Collections.ArrayList

foreach ($gKey in $groups.Keys) {
    $arr = $groups[$gKey]
    $sorted = $arr | Sort-Object segId
    $groupBroken = 0
    for ($i = 1; $i -lt $sorted.Count; $i++) {
        $prev = $sorted[$i - 1]
        $cur  = $sorted[$i]
        $totalLinksChecked++
        if ($cur.fromN -ne $prev.toN) {
            $groupBroken++
            $totalBrokenConnections++
            if ($examples.Count -lt 10) {
                $exEntry = New-Object PSObject -Property @{
                    lineId   = $gKey
                    prevLink = $prev.linkId
                    prevToN  = $prev.toN
                    curLink  = $cur.linkId
                    curFromN = $cur.fromN
                }
                $examples.Add($exEntry) | Out-Null
            }
        }
    }
    if ($groupBroken -gt 0) { $brokenGroups++ }
}

Write-Host ("  Total links checked (i>=1): " + $totalLinksChecked)
Write-Host ("  Total broken connections: " + $totalBrokenConnections)
$brokenPct = if ($totalLinksChecked -gt 0) { $totalBrokenConnections * 100.0 / $totalLinksChecked } else { 0 }
Write-Host ("  Broken rate: " + [Math]::Round($brokenPct, 2) + "%")
$groupsWithBreakPct = if ($totalGroups -gt 0) { $brokenGroups * 100.0 / $totalGroups } else { 0 }
Write-Host ("  Groups with >=1 break: " + $brokenGroups + " / " + $totalGroups + " (" + [Math]::Round($groupsWithBreakPct,1) + "%)")

Write-Host ''
Write-Host '  [Examples of broken connections]:'
foreach ($ex in $examples) {
    Write-Host ("    line=" + $ex.lineId)
    Write-Host ("      prev: " + $ex.prevLink + " toN=" + $ex.prevToN)
    Write-Host ("      cur : " + $ex.curLink + " fromN=" + $ex.curFromN)
}

Write-Host ''
Write-Host '======================================'
Write-Host '  END'
Write-Host '======================================'