<#
    fix-bcases-menu.ps1

    Убирает из plugins\BCases\menu копии меню BMenu 2.x.

    Зачем: BCases собран против BMenu 1.6 (org.by1337.bmenu.MenuLoader) и
    читает plugins\BCases\menu именно им. Наши меню написаны в формате
    BMenu 2.x (on_click списком, oneOf, [layer], st:), поэтому 1.6 при
    старте пишет в лог:

        Errors in 'vault': Errors in 'on_click': Expected a Map, but found List.

    Сами меню при этом работают: их грузит BMenu 2.7 из plugins\BMenu\menu.
    Ошибки — это шум от второй, лишней копии.

    Скрипт ничего не удаляет насовсем: переносит копии в
    plugins\BCases\menu\_backup_bmenu2.

    Запуск (из корня сервера, где лежат папки plugins/):
        powershell -ExecutionPolicy Bypass -File .\fix-bcases-menu.ps1
        powershell -ExecutionPolicy Bypass -File .\fix-bcases-menu.ps1 -DryRun

    Свои меню BCases (default.yml, keylist.yml) скрипт не трогает.
#>

param(
    [string]$ServerRoot = ".",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$menu = Join-Path $ServerRoot "plugins\BCases\menu"
if (-not (Test-Path $menu)) {
    Write-Host "Не найдена папка: $menu" -ForegroundColor Red
    Write-Host "Запустите скрипт из корня сервера или укажите -ServerRoot 'C:\путь\к\серверу'." -ForegroundColor Yellow
    exit 1
}

# Всё, что приехало из BMenu 2.x и не является меню BCases.
$targets = @(
    "bauc",
    "elytrix",
    "admin",
    "select",
    "random-colors",
    "include-example",
    "example-seller.yml",
    "animation-54.yml"
)

$backup = Join-Path $menu "_backup_bmenu2"
$moved = @()
$skipped = @()

foreach ($name in $targets) {
    $path = Join-Path $menu $name
    if (Test-Path $path) {
        if ($DryRun) {
            Write-Host "[dry-run] перенёс бы: $name" -ForegroundColor Cyan
        } else {
            if (-not (Test-Path $backup)) {
                New-Item -ItemType Directory -Path $backup | Out-Null
            }
            Move-Item -LiteralPath $path -Destination (Join-Path $backup $name) -Force
            Write-Host "перенесено: $name" -ForegroundColor Green
        }
        $moved += $name
    } else {
        $skipped += $name
    }
}

Write-Host ""
Write-Host ("Найдено и обработано: {0}" -f $moved.Count) -ForegroundColor White
if ($skipped.Count -gt 0) {
    Write-Host ("Не найдено (это нормально): {0}" -f ($skipped -join ", ")) -ForegroundColor DarkGray
}
if (-not $DryRun -and $moved.Count -gt 0) {
    Write-Host ""
    Write-Host "Теперь полный stop -> start сервера." -ForegroundColor Yellow
    Write-Host "В логе больше не должно быть 'Expected a Map, but found List'." -ForegroundColor Yellow
    Write-Host "Вернуть копии можно из: $backup" -ForegroundColor DarkGray
}
if (-not $DryRun -and $moved.Count -eq 0) {
    Write-Host "Копий BMenu 2.x в plugins\BCases\menu нет — чистить нечего." -ForegroundColor Green
}
