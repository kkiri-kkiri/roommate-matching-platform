param([switch] $PrepareOnly, [switch] $Migrate, [switch] $FormerFailuresOnly, [switch] $TokenLockOnly)
$ErrorActionPreference = 'Stop'
if (@($Migrate,$FormerFailuresOnly,$TokenLockOnly | Where-Object { $_ }).Count -gt 1) { throw 'Choose one explicit validation stage.' }
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$backendRoot = Join-Path $repoRoot 'backend'
$workRoot = Join-Path $backendRoot 'build/auth-mysql-concurrency'
$bootJar = Join-Path $backendRoot 'build/libs/backend-0.0.1-SNAPSHOT.jar'
$artifactHash = (Get-FileHash -LiteralPath $bootJar -Algorithm SHA256).Hash
$libRoot = Join-Path $workRoot ('lib-' + $artifactHash)
$classRoot = Join-Path $workRoot 'classes'
New-Item -ItemType Directory -Force -Path $libRoot,$classRoot | Out-Null
# Build current production classes separately: backend/gradlew.bat test bootJar.
# This opt-in harness never starts BackendApplication or changes the normal H2 test task.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($bootJar)
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '^BOOT-INF/lib/[^/]+\.jar$') {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $libRoot $entry.Name),$true)
        }
    }
} finally { $archive.Dispose() }
$classpath = (Join-Path $backendRoot 'build/classes/java/main') + ';' + $libRoot + '/*'
$javaSources = @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' | Select-Object -ExpandProperty FullName)
& javac -encoding UTF-8 -cp $classpath -d $classRoot $javaSources
if ($LASTEXITCODE -ne 0) { throw 'Harness compilation failed; no DB connection was made.' }
if ($PrepareOnly) { Write-Output 'Harness compiled; no DB connection was made.'; return }

# Read ONLY this container. Do not print inspect JSON: it contains credentials.
$target = docker inspect kkiri-auth-mysql-test | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or @($target).Count -ne 1) { throw 'Test container inspection failed.' }
$target = @($target)[0]
if ($target.Id -ne 'e15a46faca0b46489287bdb797586f516cc79902c3e0aa777706fcdedce6b6d9' -or
    $target.State.Status -ne 'running' -or $target.Config.Image -ne 'mysql:8.0.46-oraclelinux9') {
    throw 'Test container identity changed. Stop.'
}
$bindings = @($target.NetworkSettings.Ports.'3306/tcp')
if ($bindings.Count -eq 0 -or @($bindings | Where-Object { $_.HostPort -ne '3307' }).Count -ne 0) {
    throw 'Test port mapping changed. Stop.'
}
if ('MYSQL_DATABASE=kkiri_auth_test' -notin $target.Config.Env -or 'MYSQL_USER=kkiri_test' -notin $target.Config.Env) {
    throw 'Test database/account metadata changed. Stop.'
}
$uuidFile = docker exec kkiri-auth-mysql-test cat /var/lib/mysql/auto.cnf
if ($LASTEXITCODE -ne 0 -or ($uuidFile -join "`n") -notmatch 'server-uuid=53ab727e-c847-11f1-9aaf-bad504fada53') {
    throw 'Container server UUID changed. Stop.'
}
$oldPassword = [Environment]::GetEnvironmentVariable('KKIRI_AUTH_TEST_DB_PASSWORD','Process')
try {
    if ([string]::IsNullOrWhiteSpace($oldPassword)) {
        $entry = @($target.Config.Env | Where-Object { $_.StartsWith('MYSQL_PASSWORD=') })
        if ($entry.Count -ne 1) { throw 'Inject KKIRI_AUTH_TEST_DB_PASSWORD into this process.' }
        $env:KKIRI_AUTH_TEST_DB_PASSWORD = $entry[0].Substring('MYSQL_PASSWORD='.Length)
    }
    $entry = $null
    $target = $null
    $mainClass = if ($Migrate) { 'AuthMysqlMigration' } else { 'AuthMysqlConcurrency' }
    $javaArgs = @('-Duser.timezone=Asia/Seoul', '-cp', ($classRoot + ';' + $classpath), $mainClass, $repoRoot)
    if ($FormerFailuresOnly) { $javaArgs += 'FormerFailuresOnly' }
    if ($TokenLockOnly) { $javaArgs += 'TokenLockOnly' }
    & java @javaArgs
    if ($LASTEXITCODE -ne 0) { throw 'MySQL harness reported failed/incomplete tests. See the new results directory.' }
} finally {
    [Environment]::SetEnvironmentVariable('KKIRI_AUTH_TEST_DB_PASSWORD',$oldPassword,'Process')
    $entry = $null
    $target = $null
}
