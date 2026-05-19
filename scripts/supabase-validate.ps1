param(
    [string]$EnvFile = ".env.supabase"
)

$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")

. (Join-Path $PSScriptRoot "supabase-env.ps1") -EnvFile $EnvFile

Push-Location $repoRoot
try {
    .\mvnw.cmd -DskipTests test-compile
}
finally {
    Pop-Location
}
