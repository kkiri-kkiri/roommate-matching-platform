param([switch] $Provision, [switch] $PrepareOnly, [switch] $Initialize, [switch] $ReproduceOnly, [switch] $FormerFailuresOnly, [switch] $AdditionalOnly)
$ErrorActionPreference = 'Stop'
if (@($Initialize,$ReproduceOnly,$FormerFailuresOnly,$AdditionalOnly | Where-Object { $_ }).Count -gt 1) { throw 'Choose one validation stage.' }
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$workRoot = Join-Path $repoRoot 'backend/build/user-mysql-concurrency'
$containerName = 'kkiri-user-mysql-test'
New-Item -ItemType Directory -Force -Path $workRoot | Out-Null
if ($Provision) {
    $existing = & docker ps -a --filter "name=^/$containerName`$" --format '{{.Names}}'
    if ($LASTEXITCODE -ne 0) { throw 'Docker inventory failed.' }
    if ($existing) { throw 'User container already exists; provisioning refuses replacement.' }
    $env:MYSQL_ROOT_PASSWORD = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $env:MYSQL_PASSWORD = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    try {
        & docker run -d --name $containerName --label 'kkiri.validation=user-only' -p '127.0.0.1:3308:3306' -e MYSQL_ROOT_PASSWORD -e MYSQL_PASSWORD -e MYSQL_DATABASE=kkiriusertest -e MYSQL_USER=kkiri_user_test mysql:8.0.46-oraclelinux9 --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci
        if ($LASTEXITCODE -ne 0) { throw 'User container creation failed.' }
    } finally { Remove-Item Env:MYSQL_ROOT_PASSWORD,Env:MYSQL_PASSWORD -ErrorAction SilentlyContinue }
    return
}
$bootJar = Join-Path $repoRoot 'backend/build/libs/backend-0.0.1-SNAPSHOT.jar'
$libRoot = Join-Path $workRoot ('lib-' + (Get-FileHash -LiteralPath $bootJar -Algorithm SHA256).Hash)
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
$classpath = (Join-Path $repoRoot 'backend/build/classes/java/main') + ';' + $libRoot + '/*'
& javac -encoding UTF-8 -cp $classpath -d $classRoot (Join-Path $PSScriptRoot 'UserMysqlConcurrency.java')
if ($LASTEXITCODE -ne 0) { throw 'Harness compilation failed; no database accessed.' }
if ($PrepareOnly) { Write-Output 'User harness compiled; no database accessed.'; return }
# Inspect only the dedicated User container. Never inspect Auth secrets or connect to its database.
$metadata = (& docker inspect $containerName | ConvertFrom-Json)[0]
if ($LASTEXITCODE -ne 0 -or !$metadata.State.Running -or $metadata.Config.Image -ne 'mysql:8.0.46-oraclelinux9' -or $metadata.Config.Labels.'kkiri.validation' -ne 'user-only') { throw 'Unexpected User container identity.' }
$binding = $metadata.NetworkSettings.Ports.'3306/tcp'
if ($binding.Count -ne 1 -or $binding[0].HostIp -ne '127.0.0.1' -or $binding[0].HostPort -ne '3308') { throw 'Unexpected User port binding.' }
$uuidFile = & docker exec $containerName cat /var/lib/mysql/auto.cnf
if ($LASTEXITCODE -ne 0) { throw 'Cannot read User server UUID.' }
$uuidLine = @($uuidFile | Where-Object { $_ -match '^server-uuid=' })
if ($uuidLine.Count -ne 1) { throw 'Invalid User server UUID.' }
$env:KKIRI_USER_TEST_UUID = $uuidLine[0].Substring(12).Trim()
$env:KKIRI_USER_TEST_HOSTNAME = $metadata.Config.Hostname
$env:KKIRI_USER_TEST_DB_PASSWORD = ($metadata.Config.Env | Where-Object { $_.StartsWith('MYSQL_PASSWORD=') }).Substring(15)
$env:KKIRI_USER_TEST_CONTAINER_ID = $metadata.Id
try {
    $mode = if ($Initialize) { 'Initialize' } elseif ($FormerFailuresOnly) { 'FormerFailures' } elseif ($AdditionalOnly) { 'Additional' } elseif ($ReproduceOnly) { 'Reproduce' } else { 'Verify' }
    & java '-Duser.timezone=Asia/Seoul' -cp ($classRoot + ';' + $classpath) UserMysqlConcurrency $repoRoot $mode
    if ($LASTEXITCODE -ne 0) { throw 'User MySQL validation did not pass; inspect the new evidence directory.' }
} finally {
    Remove-Item Env:KKIRI_USER_TEST_UUID,Env:KKIRI_USER_TEST_HOSTNAME,Env:KKIRI_USER_TEST_DB_PASSWORD,Env:KKIRI_USER_TEST_CONTAINER_ID -ErrorAction SilentlyContinue
}
