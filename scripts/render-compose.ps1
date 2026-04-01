param(
    [string]$OutputPath = ""
)

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$instancesRoot = Join-Path $repoRoot "docker\instances"

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $repoRoot "docker-compose.generated.yml"
}

$instances = Get-ChildItem -Path $instancesRoot -Directory | Where-Object { $_.Name -ne "_template" } | Sort-Object Name
if (-not $instances) {
    throw "No bot instances found under $instancesRoot"
}

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("name: discordmusicbot-local")
$lines.Add("services:")

foreach ($instance in $instances) {
    $serviceName = ($instance.Name.ToLower() -replace "[^a-z0-9_-]", "-")
    $relativeInstancePath = "./docker/instances/$($instance.Name)"

    $lines.Add("  ${serviceName}:")
    $lines.Add("    build:")
    $lines.Add("      context: .")
    $lines.Add("    image: discordmusicbot:local")
    $lines.Add("    container_name: $serviceName")
    $lines.Add("    restart: unless-stopped")
    $lines.Add("    env_file:")
    $lines.Add("      - $relativeInstancePath/bot.env")
    $lines.Add("    environment:")
    $lines.Add("      JMUSICBOT_HOME: /data")
    $lines.Add("    volumes:")
    $lines.Add("      - ${relativeInstancePath}:/data")
}

Set-Content -Path $OutputPath -Value ($lines -join "`n")
Write-Host "Generated $OutputPath with $($instances.Count) bot instance(s)."
