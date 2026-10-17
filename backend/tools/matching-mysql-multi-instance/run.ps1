param([switch] $PrepareOnly)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$backendRoot = Join-Path $repoRoot 'backend'
$jar = Join-Path $backendRoot 'build/libs/backend-0.0.1-SNAPSHOT.jar'
$artifactHash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
$workRoot = Join-Path $backendRoot 'build/matching-mysql-multi-instance'
$libRoot = Join-Path $workRoot ('lib-' + $artifactHash)
$classRoot = Join-Path $workRoot 'classes'
New-Item -ItemType Directory -Force -Path $libRoot,$classRoot | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($jar)
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '^BOOT-INF/lib/[^/]+\.jar$') {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $libRoot $entry.Name),$true)
        }
    }
} finally { $archive.Dispose() }
$classpath = (Join-Path $backendRoot 'build/classes/java/main') + ';' + (Join-Path $backendRoot 'build/resources/main') + ';' + $libRoot + '/*'
$sources = @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' | Select-Object -ExpandProperty FullName)
$sources += (Join-Path $backendRoot 'src/test/java/com/kkirikkiri/survey/SurveyTestData.java')
& javac -encoding UTF-8 -cp $classpath -d $classRoot $sources
if ($LASTEXITCODE -ne 0) { throw 'Harness compilation failed; no database accessed.' }
if ($PrepareOnly) { Write-Output 'Compiled; no database accessed.'; return }
# Inspect stays in memory; never print the container environment or credentials.
$inspection = & docker inspect kkiri-survey-mysql-test
if ($LASTEXITCODE -ne 0) { throw 'Dedicated container unavailable.' }
$target = ($inspection | ConvertFrom-Json)[0]
if (!$target.State.Running -or $target.Config.Image -ne 'mysql:8.0.46-oraclelinux9' -or $target.Config.Labels.'kkiri.validation' -ne 'survey-only') { throw 'Unexpected container identity.' }
$binding = @($target.NetworkSettings.Ports.'3306/tcp')
if ($binding.Count -ne 1 -or $binding[0].HostIp -ne '127.0.0.1' -or $binding[0].HostPort -ne '3311') { throw 'Unexpected endpoint.' }
$uuid = & docker exec kkiri-survey-mysql-test cat /var/lib/mysql/auto.cnf
if ($LASTEXITCODE -ne 0) { throw 'UUID unavailable.' }
$uuidLine = @($uuid | Where-Object { $_ -match '^server-uuid=' })
if ($uuidLine.Count -ne 1) { throw 'Invalid UUID.' }
$names = @('KM_ROOT_PASSWORD','KM_UUID','KM_HOSTNAME')
$prior = @{}
foreach ($name in $names) { $prior[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
try {
    $rootEntry = @($target.Config.Env | Where-Object { $_.StartsWith('MYSQL_ROOT_PASSWORD=') })
    if ($rootEntry.Count -ne 1) { throw 'Test root credential unavailable.' }
    $env:KM_ROOT_PASSWORD = $rootEntry[0].Substring('MYSQL_ROOT_PASSWORD='.Length)
    $env:KM_UUID = $uuidLine[0].Substring(12).Trim()
    $env:KM_HOSTNAME = $target.Config.Hostname
    & java '-Duser.timezone=Asia/Seoul' -cp ($classRoot + ';' + $classpath) kmi.MatchingMultiInstance $workRoot $artifactHash
    if ($LASTEXITCODE -ne 0) { throw 'Validation failed/incomplete. Inspect the new evidence directory.' }
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$prior[$name],'Process') }
}
