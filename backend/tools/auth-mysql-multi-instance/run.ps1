param([switch] $PrepareOnly, [string[]] $Cases = @())
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$backendRoot = Join-Path $repoRoot 'backend'
$bootJar = Join-Path $backendRoot 'build/libs/backend-0.0.1-SNAPSHOT.jar'
$artifactHash = (Get-FileHash -LiteralPath $bootJar -Algorithm SHA256).Hash
$workRoot = Join-Path $backendRoot 'build/auth-mysql-multi-instance'
$libRoot = Join-Path $workRoot ('lib-' + $artifactHash)
$classRoot = Join-Path $workRoot 'classes'
New-Item -ItemType Directory -Force -Path $libRoot,$classRoot | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($bootJar)
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '^BOOT-INF/lib/[^/]+\.jar$') {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $libRoot $entry.Name),$true)
        }
    }
} finally { $archive.Dispose() }
$classpath = (Join-Path $backendRoot 'build/classes/java/main') + ';' + (Join-Path $backendRoot 'build/resources/main') + ';' + $libRoot + '/*'
# Reuse only the prior SQL splitter/JSON writer. Their test main methods are never invoked.
$sources = @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' | Select-Object -ExpandProperty FullName)
$sources += (Join-Path $PSScriptRoot '../auth-mysql-concurrency/AuthMysqlConcurrency.java')
$sources += (Join-Path $PSScriptRoot '../auth-mysql-concurrency/AuthMysqlMigration.java')
& javac -encoding UTF-8 -cp $classpath -d $classRoot $sources
if ($LASTEXITCODE -ne 0) { throw 'Harness compilation failed; no DB connection was made.' }
if ($PrepareOnly) { Write-Output 'Harness compiled; no DB connection was made.'; return }

# Never print inspect JSON or its environment: credentials remain in memory.
$target = @(docker inspect kkiri-auth-mysql-test | ConvertFrom-Json)
if ($LASTEXITCODE -ne 0 -or $target.Count -ne 1) { throw 'Container inspection failed.' }
$target = $target[0]
if ($target.Id -ne 'e15a46faca0b46489287bdb797586f516cc79902c3e0aa777706fcdedce6b6d9' -or
    $target.State.Status -ne 'running' -or $target.Config.Image -ne 'mysql:8.0.46-oraclelinux9') { throw 'Container identity changed.' }
$bindings = @($target.NetworkSettings.Ports.'3306/tcp')
if ($bindings.Count -eq 0 -or @($bindings | Where-Object { $_.HostPort -ne '3307' }).Count -ne 0) { throw 'Port mapping changed.' }
if ('MYSQL_DATABASE=kkiri_auth_test' -notin $target.Config.Env -or 'MYSQL_USER=kkiri_test' -notin $target.Config.Env) { throw 'Container metadata changed.' }
$uuidFile = docker exec kkiri-auth-mysql-test cat /var/lib/mysql/auto.cnf
if ($LASTEXITCODE -ne 0 -or ($uuidFile -join "`n") -notmatch 'server-uuid=53ab727e-c847-11f1-9aaf-bad504fada53') { throw 'UUID changed.' }
$previousRoot = [Environment]::GetEnvironmentVariable('KKIRI_MI_ROOT_PASSWORD','Process')
try {
    $entry = @($target.Config.Env | Where-Object { $_.StartsWith('MYSQL_ROOT_PASSWORD=') })
    if ($entry.Count -ne 1 -or $entry[0].Length -le 'MYSQL_ROOT_PASSWORD='.Length) { throw 'Test-container root credential unavailable.' }
    $env:KKIRI_MI_ROOT_PASSWORD = $entry[0].Substring('MYSQL_ROOT_PASSWORD='.Length)
    $target = $null; $entry = $null
    $javaArgs = @('-Duser.timezone=Asia/Seoul','-cp',($classRoot + ';' + $classpath),'AuthMultiInstance',$repoRoot,$artifactHash)
    if ($Cases.Count -gt 0) { $javaArgs += ($Cases -join ',') }
    & java @javaArgs
    if ($LASTEXITCODE -ne 0) { throw 'Multi-instance verification failed/incomplete. Inspect the new results directory.' }
} finally {
    [Environment]::SetEnvironmentVariable('KKIRI_MI_ROOT_PASSWORD',$previousRoot,'Process')
    $target = $null; $entry = $null
}
