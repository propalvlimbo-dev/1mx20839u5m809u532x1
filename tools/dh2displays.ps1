#Requires -Version 5.1
<#
.SYNOPSIS
    Конвертер голограмм DecentHolograms: старый тип (armor stand) -> новый тип Displays (2.10+).

.DESCRIPTION
    Читает plugins/DecentHolograms/holograms/*.yml и пишет plugins/DecentHolograms/displays/*.yml
    в формате display-сущностей (TEXT). Координаты, display-range и update-interval переносятся.

    Что переносится:
      * все текстовые строки первой страницы (цвета & #RRGGBB и градиенты <#RRGGBB>..</#RRGGBB> работают как есть);
      * location (world/x/y/z/yaw/pitch), display-range, update-interval, enabled.

    Что НЕ переносится (новый тип этого не умеет):
      * страницы (pages) — берётся только первая;
      * клики/действия страниц (actions);
      * строки #ICON:, #HEAD:, #SMALLHEAD:, #ENTITY:, #EMPTY: — они пропускаются,
        вместо них создаются отдельные ITEM-дисплеи (/dh displays create item <имя>).

.PARAMETER Source
    Папка со старыми голограммами. По умолчанию: .\holograms (текущая папка) —
    запускай скрипт из plugins\DecentHolograms или укажи путь явно.

.PARAMETER Destination
    Папка для новых дисплеев. По умолчанию: .\displays (текущая папка).

.PARAMETER BackgroundAlpha
    Прозрачность плашки под текстом, 0-255 (0 = без плашки). По умолчанию 0.

.PARAMETER SeeThrough
    Текст видно сквозь блоки.

.PARAMETER NoShadow
    Отключить тень у текста.

.PARAMETER Force
    Перезаписывать уже существующие файлы в папке displays.

.PARAMETER DryRun
    Ничего не записывать, только показать, что получится.

.EXAMPLE
    .\dh2displays.ps1 -Source "C:\server\plugins\DecentHolograms\holograms" -Destination "C:\server\plugins\DecentHolograms\displays"

.EXAMPLE
    .\dh2displays.ps1 -DryRun
#>
[CmdletBinding()]
param(
    [string]$Source = (Join-Path (Get-Location) 'holograms'),
    [string]$Destination = (Join-Path (Get-Location) 'displays'),
    [ValidateRange(0, 255)]
    [int]$BackgroundAlpha = 0,
    [switch]$SeeThrough,
    [switch]$NoShadow,
    [switch]$Force,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$Invariant = [System.Globalization.CultureInfo]::InvariantCulture

function Read-Utf8Lines {
    param([string]$Path)
    return [System.IO.File]::ReadAllLines($Path, [System.Text.Encoding]::UTF8)
}

function Get-YamlScalar {
    param([string]$Value)
    if ($null -eq $Value) { return '' }
    $v = $Value.Trim()
    if ($v.Length -ge 2) {
        $first = $v.Substring(0, 1)
        $last = $v.Substring($v.Length - 1, 1)
        if ($first -eq "'" -and $last -eq "'") {
            return $v.Substring(1, $v.Length - 2).Replace("''", "'")
        }
        if ($first -eq '"' -and $last -eq '"') {
            return $v.Substring(1, $v.Length - 2).Replace('\"', '"')
        }
    }
    return $v
}

function Get-OptionValue {
    param([string[]]$Lines, [string]$Key)
    $pattern = '^\s*' + [regex]::Escape($Key) + '\s*:\s*(.+)$'
    foreach ($line in $Lines) {
        $m = [regex]::Match($line, $pattern)
        if ($m.Success) {
            return (Get-YamlScalar $m.Groups[1].Value)
        }
    }
    return $null
}

function Convert-LocationString {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) { return $null }
    $parts = $Value.Trim().Split(':')
    if ($parts.Count -lt 4) { return $null }
    try {
        $yaw = 0.0
        $pitch = 0.0
        if ($parts.Count -ge 5) { $yaw = [double]::Parse($parts[4], $Invariant) }
        if ($parts.Count -ge 6) { $pitch = [double]::Parse($parts[5], $Invariant) }
        return @{
            World = $parts[0]
            X     = [double]::Parse($parts[1], $Invariant)
            Y     = [double]::Parse($parts[2], $Invariant)
            Z     = [double]::Parse($parts[3], $Invariant)
            Yaw   = $yaw
            Pitch = $pitch
        }
    } catch {
        return $null
    }
}

function Get-FirstPageContent {
    param([string[]]$Lines)
    $content = New-Object System.Collections.Generic.List[string]
    $skipped = New-Object System.Collections.Generic.List[string]
    $hasPages = $false
    foreach ($line in $Lines) {
        if ($line -match '^\s*pages\s*:\s*$') { $hasPages = $true; break }
    }

    $inBlock = $false
    $started = $false

    foreach ($line in $Lines) {
        if (-not $inBlock) {
            if ($hasPages) {
                if ($line -match '^\s*pages\s*:\s*$') { $inBlock = $true }
            } else {
                if ($line -match '^\s*lines\s*:\s*$') { $inBlock = $true; $started = $true }
            }
            continue
        }
        if ($hasPages -and $line -match '^\s*-\s*lines\s*:\s*$') {
            if ($started) { break }
            $started = $true
            continue
        }
        if ($line -match '^\S' -and $line -notmatch '^-') { break }
        if (-not $started) { continue }
        if ($line -match '^\s*actions\s*:\s*') { break }

        $m = [regex]::Match($line, '^\s*-\s*content\s*:\s*(.+)$')
        if ($m.Success) {
            $value = Get-YamlScalar $m.Groups[1].Value
            if ($value -match '^#(ICON|HEAD|SMALLHEAD|ENTITY|EMPTY)\b') {
                [void]$skipped.Add($value)
            } elseif ($value.Length -gt 0) {
                [void]$content.Add($value)
            }
        }
    }

    return @{ Content = $content; Skipped = $skipped }
}

function Quote-Yaml {
    param([string]$Value)
    return "'" + $Value.Replace("'", "''") + "'"
}

function New-DisplayYaml {
    param(
        [string]$SourceName,
        $Location,
        [string]$Range,
        [string]$Interval,
        [string]$Enabled,
        [string[]]$Lines,
        [int]$Alpha,
        [bool]$Through,
        [bool]$Shadow
    )

    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine('# DecentHolograms 2.10+ — голограмма нового типа (display entity)')
    [void]$sb.AppendLine('# Сконвертировано из классической голограммы: ' + $SourceName)
    [void]$sb.AppendLine('# Перенести в нужную точку: /dh displays movehere ' + [System.IO.Path]::GetFileNameWithoutExtension($SourceName))
    [void]$sb.AppendLine('type: TEXT')
    [void]$sb.AppendLine('location:')
    [void]$sb.AppendLine('  world: ' + (Quote-Yaml $Location.World))
    [void]$sb.AppendLine('  x: ' + $Location.X.ToString('0.0###', $Invariant))
    [void]$sb.AppendLine('  y: ' + $Location.Y.ToString('0.0###', $Invariant))
    [void]$sb.AppendLine('  z: ' + $Location.Z.ToString('0.0###', $Invariant))
    [void]$sb.AppendLine('  yaw: ' + $Location.Yaw.ToString('0.0###', $Invariant))
    [void]$sb.AppendLine('  pitch: ' + $Location.Pitch.ToString('0.0###', $Invariant))
    [void]$sb.AppendLine('settings:')
    [void]$sb.AppendLine('  enabled: ' + $(if ($Enabled -eq 'false') { 'false' } else { 'true' }))
    [void]$sb.AppendLine('  display-range: ' + $(if ([string]::IsNullOrWhiteSpace($Range)) { '48.0' } else { $Range }))
    [void]$sb.AppendLine('  update-interval: ' + $(if ([string]::IsNullOrWhiteSpace($Interval)) { '20' } else { $Interval }))
    [void]$sb.AppendLine('attributes:')
    [void]$sb.AppendLine('  billboard:')
    [void]$sb.AppendLine('    value-type: billboard_constraints')
    [void]$sb.AppendLine('    value: CENTER')
    [void]$sb.AppendLine('  background-color:')
    [void]$sb.AppendLine('    value-type: rgba')
    [void]$sb.AppendLine('    value:')
    [void]$sb.AppendLine('      red: 8')
    [void]$sb.AppendLine('      green: 8')
    [void]$sb.AppendLine('      blue: 12')
    [void]$sb.AppendLine('      alpha: ' + $Alpha)
    [void]$sb.AppendLine('  text-shadow:')
    [void]$sb.AppendLine('    value-type: boolean')
    [void]$sb.AppendLine('    value: ' + $(if ($Shadow) { 'true' } else { 'false' }))
    [void]$sb.AppendLine('  see-through:')
    [void]$sb.AppendLine('    value-type: boolean')
    [void]$sb.AppendLine('    value: ' + $(if ($Through) { 'true' } else { 'false' }))
    [void]$sb.AppendLine('  alignment:')
    [void]$sb.AppendLine('    value-type: text_alignment')
    [void]$sb.AppendLine('    value: CENTER')
    [void]$sb.AppendLine('  line-width:')
    [void]$sb.AppendLine('    value-type: integer')
    [void]$sb.AppendLine('    value: 400')
    [void]$sb.AppendLine('  text-opacity:')
    [void]$sb.AppendLine('    value-type: integer')
    [void]$sb.AppendLine('    value: 255')
    [void]$sb.AppendLine('  scale:')
    [void]$sb.AppendLine('    value-type: vector3f')
    [void]$sb.AppendLine('    value:')
    [void]$sb.AppendLine('      x: 1.0')
    [void]$sb.AppendLine('      y: 1.0')
    [void]$sb.AppendLine('      z: 1.0')
    [void]$sb.AppendLine('pages:')
    [void]$sb.AppendLine('  - lines:')
    if ($Lines.Count -eq 0) {
        [void]$sb.AppendLine('      - ' + (Quote-Yaml '&7—'))
    } else {
        foreach ($line in $Lines) {
            [void]$sb.AppendLine('      - ' + (Quote-Yaml $line))
        }
    }
    return $sb.ToString()
}

Write-Host ''
Write-Host 'DecentHolograms: armor stand -> displays (2.10+)' -ForegroundColor Cyan
Write-Host ('Источник:     ' + $Source)
Write-Host ('Назначение:   ' + $Destination)
Write-Host ''

if (-not (Test-Path -LiteralPath $Source)) {
    Write-Error ('Папка со старыми голограммами не найдена: ' + $Source)
    exit 1
}

$files = @(Get-ChildItem -LiteralPath $Source -Filter '*.yml' -File -ErrorAction SilentlyContinue)
if ($files.Count -eq 0) {
    Write-Warning ('В папке нет .yml файлов: ' + $Source)
    exit 0
}

if (-not $DryRun -and -not (Test-Path -LiteralPath $Destination)) {
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
}

$converted = 0
$failed = 0

foreach ($file in $files) {
    $raw = Read-Utf8Lines -Path $file.FullName
    $location = Convert-LocationString (Get-OptionValue -Lines $raw -Key 'location')
    if ($null -eq $location) {
        Write-Warning ('Пропуск ' + $file.Name + ': не удалось разобрать location')
        $failed++
        continue
    }

    $page = Get-FirstPageContent -Lines $raw
    $pageLines = $page.Content.ToArray()
    $rangeValue = Get-OptionValue -Lines $raw -Key 'display-range'
    $intervalValue = Get-OptionValue -Lines $raw -Key 'update-interval'
    $enabledValue = Get-OptionValue -Lines $raw -Key 'enabled'
    $shadow = -not ([bool]$NoShadow)

    $yaml = New-DisplayYaml -SourceName $file.Name -Location $location -Range $rangeValue `
        -Interval $intervalValue -Enabled $enabledValue -Lines $pageLines `
        -Alpha $BackgroundAlpha -Through ([bool]$SeeThrough) -Shadow $shadow

    $target = Join-Path $Destination ($file.BaseName + '.yml')
    $note = ''
    if ($page.Skipped.Count -gt 0) {
        $note = '  (пропущено не-текстовых строк: ' + $page.Skipped.Count + ')'
    }

    if ($DryRun) {
        Write-Host ('[dry] ' + $file.Name + ' -> ' + $target + $note) -ForegroundColor Yellow
        $converted++
        continue
    }

    if ((Test-Path -LiteralPath $target) -and -not $Force) {
        Write-Warning ('Уже существует, пропуск: ' + $target + '  (перезаписать: -Force)')
        continue
    }

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($target, $yaml, $utf8NoBom)
    Write-Host ('[ok]  ' + $file.Name + ' -> ' + (Split-Path $target -Leaf) + $note) -ForegroundColor Green
    $converted++
}

Write-Host ''
Write-Host ('Готово. Сконвертировано: ' + $converted + ', с ошибками: ' + $failed) -ForegroundColor Cyan
Write-Host 'Дальше: /dh reload  ->  /dh displays list  ->  /dh displays movehere <имя>' -ForegroundColor Gray
Write-Host 'Старую папку holograms не удаляй, пока не проверишь новые голограммы.' -ForegroundColor Gray
