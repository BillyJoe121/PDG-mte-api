param(
    [string]$EnvFile = ".env.supabase"
)

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$envPath = Join-Path $repoRoot $EnvFile

if (Test-Path -LiteralPath $envPath) {
    Get-Content -LiteralPath $envPath | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) {
            return
        }
        $parts = $line.Split("=", 2)
        if ($parts.Length -ne 2) {
            return
        }
        $name = $parts[0].Trim()
        $value = $parts[1].Trim().Trim("'").Trim('"')
        if ($name) {
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

if (-not $env:SPRING_PROFILES_ACTIVE) {
    $env:SPRING_PROFILES_ACTIVE = "supabase"
}
if (-not $env:DB_DRIVER) {
    $env:DB_DRIVER = "org.postgresql.Driver"
}
if (-not $env:JPA_DDL_AUTO) {
    $env:JPA_DDL_AUTO = "validate"
}
if (-not $env:FLYWAY_ENABLED) {
    $env:FLYWAY_ENABLED = "true"
}
if (-not $env:FLYWAY_BASELINE_ON_MIGRATE) {
    $env:FLYWAY_BASELINE_ON_MIGRATE = "false"
}

$dbUrl = if ($env:SUPABASE_DB_URL) { $env:SUPABASE_DB_URL } else { $env:DB_URL }
$dbUser = if ($env:SUPABASE_DB_USERNAME) { $env:SUPABASE_DB_USERNAME } else { $env:DB_USERNAME }
$dbPassword = if ($env:SUPABASE_DB_PASSWORD) { $env:SUPABASE_DB_PASSWORD } else { $env:DB_PASSWORD }

$missing = @()
if (-not $dbUrl) { $missing += "DB_URL or SUPABASE_DB_URL" }
if (-not $dbUser) { $missing += "DB_USERNAME or SUPABASE_DB_USERNAME" }
if (-not $dbPassword) { $missing += "DB_PASSWORD or SUPABASE_DB_PASSWORD" }

if ($missing.Count -gt 0) {
    throw "Missing required Supabase environment variables: $($missing -join ', '). Create .env.supabase from supabase.env.example."
}

if ($dbUrl -notlike "jdbc:postgresql://*") {
    throw "DB_URL must be a PostgreSQL JDBC URL. Current value does not start with jdbc:postgresql://"
}

if ($dbUrl -notmatch "sslmode=require") {
    Write-Warning "DB_URL does not include sslmode=require. Supabase direct connections should include it."
}

Write-Host "Supabase environment loaded for profile '$env:SPRING_PROFILES_ACTIVE'."
