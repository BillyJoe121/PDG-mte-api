# Disposable, loopback-only PostgreSQL integration rehearsal. No Windows service installation.
$ErrorActionPreference = 'Stop'
$apiRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$runtimeRoot = Join-Path $apiRoot 'target/local-postgres-runtime'
$binaryRoot = Join-Path $runtimeRoot 'pgsql/bin'
$clusterRoot = Join-Path $runtimeRoot 'data'
$archivePath = Join-Path $runtimeRoot 'postgresql-16.15.zip'
$expectedFingerprint = '25E6FCDFB8CAEC38691BF461125E7564508760666F7B8E5DC6A5F0818F58F81E'
# Fingerprint of the HTTPS vendor archive used for this rehearsal, not an independently signed vendor checksum.
$source = 'https://get.enterprisedb.com/postgresql/postgresql-16.15-1-windows-x64-binaries.zip'
New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
if (-not (Test-Path -LiteralPath (Join-Path $binaryRoot 'pg_ctl.exe'))) {
    if (-not (Test-Path -LiteralPath $archivePath)) {
        & curl.exe --fail --location --silent --show-error --max-time 300 --output $archivePath $source
        if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL archive download failed.' }
    }
    if ((Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash -ne $expectedFingerprint) { throw 'Archive fingerprint differs from the tested binary archive.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($archivePath)
    try {
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName -notmatch '^pgsql/(bin|lib|share)/' -or $entry.FullName.EndsWith('/')) { continue }
            $destination = [IO.Path]::GetFullPath((Join-Path $runtimeRoot $entry.FullName))
            if (-not $destination.StartsWith($runtimeRoot + [IO.Path]::DirectorySeparatorChar)) { throw 'Archive path escapes the runtime directory.' }
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destination, $true)
        }
    } finally { $archive.Dispose() }
}
$version = & (Join-Path $binaryRoot 'postgres.exe') --version
if ($LASTEXITCODE -ne 0 -or $version -ne 'postgres (PostgreSQL) 16.15') { throw 'Expected PostgreSQL 16.15 portable runtime.' }
if (-not (Test-Path -LiteralPath (Join-Path $clusterRoot 'PG_VERSION'))) {
    & (Join-Path $binaryRoot 'initdb.exe') -D $clusterRoot -U mte_test --auth=trust --encoding=UTF8 --locale=C
    if ($LASTEXITCODE -ne 0) { throw 'Local test cluster initialization failed.' }
}
$startedHere = $false
$previousTestUrl = $env:MTE_LOCAL_PG_TEST_URL
Push-Location $apiRoot
try {
    & (Join-Path $binaryRoot 'pg_ctl.exe') -D $clusterRoot status *> $null
    if ($LASTEXITCODE -eq 0) {
        $pidLines = Get-Content -LiteralPath (Join-Path $clusterRoot 'postmaster.pid') -TotalCount 6
        if ($pidLines[3] -ne '55432' -or $pidLines[5] -ne '127.0.0.1') { throw 'Existing cluster is not the expected loopback test instance.' }
    } else {
        if (Get-NetTCPConnection -LocalPort 55432 -State Listen -ErrorAction SilentlyContinue) { throw 'Test port 55432 is occupied by another process.' }
        # Detached output files prevent the long-lived server from holding the shell's pipes open.
        # Wait only for pg_ctl itself; Start-Process -Wait also waits for its server descendants.
        $launcher = Start-Process -FilePath (Join-Path $binaryRoot 'pg_ctl.exe') -ArgumentList @('-D',"`"$clusterRoot`"",'-l',"`"$(Join-Path $runtimeRoot 'server.log')`"",'-o','"-h 127.0.0.1 -p 55432"','-w','start') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $runtimeRoot 'launcher.stdout.log') -RedirectStandardError (Join-Path $runtimeRoot 'launcher.stderr.log')
        $startedHere = $true
        if (-not $launcher.WaitForExit(30000)) { throw 'Local test launcher timed out.' }
        $launcher.Refresh()
        if ($launcher.ExitCode -ne 0) { throw 'Local test server failed to start; inspect launcher.stderr.log.' }
    }
    $exists = & (Join-Path $binaryRoot 'psql.exe') -X -w -h 127.0.0.1 -p 55432 -U mte_test -d postgres -t -A -c "SELECT 1 FROM pg_database WHERE datname='mte_local_validation'"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect the local test database.' }
    if ($exists -ne '1') {
        & (Join-Path $binaryRoot 'createdb.exe') -h 127.0.0.1 -p 55432 -U mte_test mte_local_validation
        if ($LASTEXITCODE -ne 0) { throw 'Cannot create the disposable local test database.' }
    }
    $env:MTE_LOCAL_PG_TEST_URL = 'jdbc:postgresql://127.0.0.1:55432/mte_local_validation'
    & .\mvnw.cmd '-Dtest=PostgresGovernanceIntegrationTest' test
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL integration checks failed; inspect target/surefire-reports.' }
} finally {
    $env:MTE_LOCAL_PG_TEST_URL = $previousTestUrl
    if ($startedHere) { & (Join-Path $binaryRoot 'pg_ctl.exe') -D $clusterRoot -w stop -m fast }
    Pop-Location
}
