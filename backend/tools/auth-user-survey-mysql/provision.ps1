param([switch] $ResumeApprovedCreation)
$ErrorActionPreference = 'Stop'
$container = 'kkiri-aus-mysql-20261021'
$volume = 'kkiri-aus-mysql-data-20261021'
$image = 'mysql:8.0.46-oraclelinux9'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$work = Join-Path $repo 'backend/build/auth-user-survey-mysql'
New-Item -ItemType Directory -Force -Path $work | Out-Null
$before = @(& docker ps -a --format '{{.ID}}|{{.Names}}|{{.Image}}|{{.Status}}|{{.Ports}}')
if ($LASTEXITCODE -ne 0) { throw 'Docker inventory unavailable' }
$before | Set-Content -Encoding UTF8 (Join-Path $work 'containers-before.txt')
if (!$ResumeApprovedCreation) {
if (@(& docker ps -a --filter "name=^/$container`$" --format '{{.Names}}').Count) { throw 'Approved container already exists; no replacement' }
if (@(& docker volume ls --filter "name=^$volume`$" --format '{{.Name}}').Count) { throw 'Approved volume already exists; no replacement' }
$rootPassword = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
$oldRoot = [Environment]::GetEnvironmentVariable('MYSQL_ROOT_PASSWORD','Process')
try {
    $env:MYSQL_ROOT_PASSWORD = $rootPassword
    & docker volume create --label kkiri.validation=auth-user-survey-20261021 $volume | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Volume creation failed' }
    & docker run -d --name $container --hostname $container --label kkiri.validation=auth-user-survey-20261021 -p 127.0.0.1:13326:3306 --mount "type=volume,source=$volume,target=/var/lib/mysql" -e MYSQL_ROOT_PASSWORD $image --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Container creation failed; resources retained' }
} finally { [Environment]::SetEnvironmentVariable('MYSQL_ROOT_PASSWORD',$oldRoot,'Process') }
} else {
    $metadata = @(& docker inspect $container | ConvertFrom-Json)[0]
    if ($LASTEXITCODE -ne 0 -or $metadata.Config.Image -ne $image -or
        $metadata.Config.Labels.'kkiri.validation' -ne 'auth-user-survey-20261021' -or
        $metadata.Config.Hostname -ne $container -or !$metadata.State.Running -or
        @($metadata.Mounts | Where-Object {$_.Name -eq $volume -and $_.Destination -eq '/var/lib/mysql'}).Count -ne 1 -or
        @($metadata.NetworkSettings.Ports.'3306/tcp' | Where-Object {$_.HostIp -eq '127.0.0.1' -and $_.HostPort -eq '13326'}).Count -ne 1) { throw 'Approved partial creation identity mismatch' }
    $rootPassword = @($metadata.Config.Env | Where-Object {$_.StartsWith('MYSQL_ROOT_PASSWORD=')})[0].Substring(20)
    $metadata=$null
}
function Invoke-ApprovedSql([string] $sql) {
    $reply = $sql | & docker exec -i $container sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=SOCKET -uroot --batch --skip-column-names 2>/dev/null'
    if ($LASTEXITCODE -ne 0) { throw 'Approved-container SQL failed; SQL/credentials omitted' }
    return $reply
}
$ready = $false
for ($attempt=0; $attempt -lt 60; $attempt++) {
    try { $ready = (Invoke-ApprovedSql 'SELECT 1;') -eq '1' } catch { $ready = $false }
    if ($ready) { break }
    Start-Sleep -Seconds 1
}
if (!$ready) { throw 'MySQL not ready; newly created resources retained' }
$identity = (Invoke-ApprovedSql "SELECT CONCAT(VERSION(),'|',@@server_uuid,'|',@@hostname,'|',@@port);").Split('|')
if ($identity[0] -ne '8.0.46' -or $identity[2] -ne $container -or $identity[3] -ne '3306') { throw 'New server identity mismatch' }
$targets = @(
    @{db='kkiriausauth20261021';user='ausauth20261021'},
    @{db='kkiriaususer20261021';user='aususer20261021'},
    @{db='kkiriaussurvey20261021';user='aussurvey20261021'},
    @{db='kkiri_auth_mi_a51021202601';user='kkiri_mi_a51021202601'},
    @{db='kkiri_user_mi_a51021202602';user='kkiri_umi_a51021202602'}
)
foreach ($target in $targets) {
    $db=$target.db; $account=$target.user
    if ((Invoke-ApprovedSql "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$db';") -ne '0') { throw 'Approved schema exists; stop' }
    if ((Invoke-ApprovedSql "SELECT COUNT(*) FROM mysql.user WHERE User='$account';") -ne '0') { throw 'Approved account exists; stop' }
    $sha=[Security.Cryptography.SHA256]::Create()
    try { $password=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($rootPassword+':'+$account)))).Replace('-','').ToLowerInvariant() } finally {$sha.Dispose()}
    $grantDb=$db.Replace('_','\_')
    Invoke-ApprovedSql "CREATE DATABASE ``$db`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER '$account'@'%' IDENTIFIED BY '$password'; GRANT ALL PRIVILEGES ON ``$grantDb``.* TO '$account'@'%';" | Out-Null
    if ((Invoke-ApprovedSql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$db';") -ne '0') { throw 'New schema not empty' }
    $password=$null
}
$manifest=@{git=(& git -C $repo rev-parse HEAD); container=$container; volume=$volume; image=$image; port=13326; hostname=$container; version=$identity[0]; uuid=$identity[1]; targets=$targets; createdAt=(Get-Date).ToUniversalTime().ToString('o'); status='created-retain-no-automatic-deletion'}
$manifest | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 (Join-Path $work 'environment.json')
$rootPassword=$null
Write-Output 'Created approved container/volume and five empty schemas with schema-only accounts. Secrets omitted; resources retained.'
