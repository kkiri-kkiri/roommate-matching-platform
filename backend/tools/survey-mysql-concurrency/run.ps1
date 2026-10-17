param([switch] $Provision, [switch] $PrepareOnly)
$ErrorActionPreference = 'Stop'
if ($Provision -and $PrepareOnly) { throw 'Choose Provision or PrepareOnly.' }
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$containerName = 'kkiri-survey-mysql-test'
if ($Provision) {
    $existing = & docker ps -a --filter "name=^/$containerName`$" --format '{{.Names}}'
    if ($LASTEXITCODE -ne 0) { throw 'Docker inventory failed.' }
    if ($existing) { throw 'Survey container already exists; replacement is refused.' }
    $env:MYSQL_ROOT_PASSWORD = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    $env:MYSQL_PASSWORD = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    try {
        & docker run -d --name $containerName --label 'kkiri.validation=survey-only' -p '127.0.0.1:3311:3306' -e MYSQL_ROOT_PASSWORD -e MYSQL_PASSWORD -e MYSQL_DATABASE=kkirisurveytest -e MYSQL_USER=kkiri_survey_test mysql:8.0.46-oraclelinux9 --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci
        if ($LASTEXITCODE -ne 0) { throw 'Survey container creation failed.' }
    } finally { Remove-Item Env:MYSQL_ROOT_PASSWORD,Env:MYSQL_PASSWORD -ErrorAction SilentlyContinue }
    return
}
$workRoot = Join-Path $repoRoot 'backend/build/survey-mysql-concurrency'
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
& javac -encoding UTF-8 -cp $classpath -d $classRoot (Join-Path $PSScriptRoot 'SurveyMysqlConcurrency.java') (Join-Path $repoRoot 'backend/src/test/java/com/kkirikkiri/survey/SurveyTestData.java')
if ($LASTEXITCODE -ne 0) { throw 'Survey harness compilation failed; no database accessed.' }
if ($PrepareOnly) { Write-Output 'Survey harness compiled; no database accessed.'; return }
# No configurable URL and no .env loading: only the separately labelled Survey container is accepted.
$inspection = & docker inspect $containerName
if ($LASTEXITCODE -ne 0) { throw 'Dedicated Survey container is unavailable. No database accessed.' }
$metadata = ($inspection | ConvertFrom-Json)[0]
if (!$metadata.State.Running -or $metadata.Config.Image -ne 'mysql:8.0.46-oraclelinux9' -or $metadata.Config.Labels.'kkiri.validation' -ne 'survey-only') { throw 'Unexpected Survey container identity.' }
$binding = $metadata.NetworkSettings.Ports.'3306/tcp'
if ($binding.Count -ne 1 -or $binding[0].HostIp -ne '127.0.0.1' -or $binding[0].HostPort -ne '3311') { throw 'Unexpected Survey port binding.' }
$uuidFile = & docker exec $containerName cat /var/lib/mysql/auto.cnf
if ($LASTEXITCODE -ne 0) { throw 'Cannot read Survey server UUID.' }
$uuidLine = @($uuidFile | Where-Object { $_ -match '^server-uuid=' })
if ($uuidLine.Count -ne 1) { throw 'Invalid Survey server UUID.' }
$env:KKIRI_SURVEY_TEST_UUID = $uuidLine[0].Substring(12).Trim()
$env:KKIRI_SURVEY_TEST_HOSTNAME = $metadata.Config.Hostname
$passwordLine = @($metadata.Config.Env | Where-Object { $_.StartsWith('MYSQL_PASSWORD=') })
if ($passwordLine.Count -ne 1) { throw 'Dedicated Survey credentials missing.' }
$env:KKIRI_SURVEY_TEST_PASSWORD = $passwordLine[0].Substring(15)
try {
    & java '-Duser.timezone=Asia/Seoul' -cp ($classRoot + ';' + $classpath) SurveyMysqlConcurrency $workRoot
    if ($LASTEXITCODE -ne 0) { throw 'Survey MySQL validation failed. Inspect evidence.json in the reported run directory.' }
} finally { Remove-Item Env:KKIRI_SURVEY_TEST_UUID,Env:KKIRI_SURVEY_TEST_HOSTNAME,Env:KKIRI_SURVEY_TEST_PASSWORD -ErrorAction SilentlyContinue }
