param(
    [Parameter(Mandatory = $true)]
    [string]$Name
)

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$instancesRoot = Join-Path $repoRoot "docker\instances"
$templateRoot = Join-Path $instancesRoot "_template"
$targetRoot = Join-Path $instancesRoot $Name

if (Test-Path $targetRoot) {
    throw "Instance '$Name' already exists at $targetRoot"
}

New-Item -ItemType Directory -Path $targetRoot | Out-Null
Get-ChildItem -Path $templateRoot -Force | ForEach-Object {
    Copy-Item -Path $_.FullName -Destination $targetRoot -Recurse -Force
}

$envExample = Join-Path $targetRoot "bot.env.example"
$envFile = Join-Path $targetRoot "bot.env"
if ((Test-Path $envExample) -and -not (Test-Path $envFile)) {
    Copy-Item -Path $envExample -Destination $envFile
}

& (Join-Path $PSScriptRoot "render-compose.ps1")

Write-Host "Created bot instance scaffold at $targetRoot"
Write-Host "Edit $envFile with the bot token before starting Docker."
