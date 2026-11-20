param(
    [Parameter(Mandatory)] [string] $Generator,
    [Parameter(Mandatory)] [string] $Inter,
    [Parameter(Mandatory)] [string] $MaterialSymbolsRounded
)

$ErrorActionPreference = 'Stop'
$Generator = (Resolve-Path -LiteralPath $Generator).Path
$Inter = (Resolve-Path -LiteralPath $Inter).Path
$MaterialSymbolsRounded = (Resolve-Path -LiteralPath $MaterialSymbolsRounded).Path
$target = Join-Path $PSScriptRoot '../src/main/resources/assets/visual_client/msdf'
New-Item -ItemType Directory -Force $target | Out-Null

& $Generator -varfont "${Inter}?opsz=14&wght=600" -type msdf -format png -size 48 -pxrange 8 -outerpxpadding 1 -square4 -chars '[0x20,0x7E], [0xA0,0xAC], [0xAE,0xFF], [0x400,0x45F], [0x2010,0x201F], 0x2022, 0x2026' -imageout "$target/inter.png" -json "$target/inter.json"
if ($LASTEXITCODE -ne 0) { throw "Inter atlas generation failed ($LASTEXITCODE)." }

# Cancel, Wi-Fi, music note, brush, check small, notifications, tune, view_agenda, speed, bolt, manage search.
& $Generator -varfont "${MaterialSymbolsRounded}?FILL=1&GRAD=0&opsz=24&wght=700" -type msdf -format png -size 64 -pxrange 8 -outerpxpadding 1 -square4 -nokerning -chars '0xE888, 0xE63E, 0xE405, 0xE3AE, 0xF88B, 0xE7F5, 0xE429, 0xE8E9, 0xE9E4, 0xEA0B, 0xF02F, 0xE8B8' -imageout "$target/symbols.png" -json "$target/symbols.json"
if ($LASTEXITCODE -ne 0) { throw "Material Symbols atlas generation failed ($LASTEXITCODE)." }
