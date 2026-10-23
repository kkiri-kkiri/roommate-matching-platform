param([switch] $ApprovedIsolatedRun)
$ErrorActionPreference='Stop'
if(!$ApprovedIsolatedRun){throw 'Resource creation requires user approval of RUNBOOK scope and -ApprovedIsolatedRun'}
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$work=Join-Path $repo 'backend/build/matching-calendar-http5'
$container='kkiri-mc-http5-mysql-20261022';$volume='kkiri-mc-http5-data-20261022'
$image='mysql:8.0.46-oraclelinux9';$expectedImageId='sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b'
$db='kkirimc5http20261022';$account='mc5http20261022';$label='matching-calendar-http5-20261022'
$prepared=Get-Content -Raw -Encoding UTF8 (Join-Path $work 'prepared.json') | ConvertFrom-Json
$jarHash=(Get-FileHash -LiteralPath (Join-Path $repo 'backend/build/libs/backend-0.0.1-SNAPSHOT.jar') -Algorithm SHA256).Hash
if($jarHash -ne $prepared.jarSha256){throw 'Prepare the current artifact first'}
if(Test-Path -LiteralPath (Join-Path $work 'environment.json')){throw 'Manifest already exists; no replacement or resume'}
if(Test-Path -LiteralPath (Join-Path $work 'execution-started.json')){throw 'Prior execution exists; review retained resources'}
if($env:DOCKER_HOST -and $env:DOCKER_HOST -notmatch '^npipe://'){throw 'Remote DOCKER_HOST is forbidden'}
$context=(& docker context show);if($LASTEXITCODE -ne 0){throw 'Docker context unavailable'}
$endpoint=(& docker context inspect $context --format '{{.Endpoints.docker.Host}}')
if($LASTEXITCODE -ne 0 -or $endpoint -notmatch '^npipe://'){throw 'Only the local Windows Docker engine is allowed'}
$os=(& docker info --format '{{.OSType}}');if($LASTEXITCODE -ne 0 -or $os -ne 'linux'){throw 'Local Linux engine required'}
$before=@(& docker ps -a --filter "name=^/$container`$" --format '{{.ID}}|{{.Names}}|{{.Image}}|{{.Status}}|{{.Ports}}')
if($LASTEXITCODE -ne 0){throw 'Docker inventory unavailable'}
if(@(& docker ps -a --filter "name=^/$container`$" --format '{{.Names}}').Count -or $LASTEXITCODE -ne 0){throw 'New container name unavailable'}
if(@(& docker volume ls --filter "name=^$volume`$" --format '{{.Name}}').Count -or $LASTEXITCODE -ne 0){throw 'New volume name unavailable'}
$actualImageId=(& docker image inspect $image --format '{{.Id}}')
if($LASTEXITCODE -ne 0 -or $actualImageId -ne $expectedImageId){throw 'Expected locally available MySQL image missing/drifted; no pull'}
# Verify both ports before creating resources. Never stop their current owners.
foreach($port in @(13348,18095,18096)){
    $listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$port)
    try{$listener.Start()}finally{$listener.Stop()}
}
$before | Set-Content -Encoding UTF8 (Join-Path $work 'containers-before.txt')
$rootPassword=[Guid]::NewGuid().ToString('N')+[Guid]::NewGuid().ToString('N')
$priorRoot=[Environment]::GetEnvironmentVariable('MYSQL_ROOT_PASSWORD','Process')
try{
    $env:MYSQL_ROOT_PASSWORD=$rootPassword
    & docker volume create --label "kkiri.validation=$label" $volume | Out-Null
    if($LASTEXITCODE -ne 0){throw 'New volume creation failed; no cleanup'}
    & docker run -d --pull=never --name $container --hostname $container --label "kkiri.validation=$label" `
        -p 127.0.0.1:13348:3306 --mount "type=volume,source=$volume,target=/var/lib/mysql" `
        -e MYSQL_ROOT_PASSWORD $expectedImageId --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci | Out-Null
    if($LASTEXITCODE -ne 0){throw 'New container creation failed; partial resources retained'}
}finally{[Environment]::SetEnvironmentVariable('MYSQL_ROOT_PASSWORD',$priorRoot,'Process')}
function Invoke-NewSql([string]$sql){
    # Root is used only via the NEW container's Unix socket, never host JDBC.
    $prior=$ErrorActionPreference
    try{
        $ErrorActionPreference='Continue'
        $reply=@($sql | & docker exec -i $container sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=SOCKET -uroot --batch --skip-column-names --raw 2>/dev/null')
        $code=$LASTEXITCODE
    }finally{$ErrorActionPreference=$prior}
    if($code -ne 0){throw 'New-container SQL failed; credentials/SQL omitted; resources retained'}
    return $reply
}
$ready=$false
for($attempt=0;$attempt -lt 45;$attempt++){
    try{$ready=(Invoke-NewSql 'SELECT 1;') -eq '1'}catch{$ready=$false}
    if($ready){break};Start-Sleep -Seconds 1
}
if(!$ready){throw 'New MySQL not ready; resources retained'}
$identity=(Invoke-NewSql "SELECT CONCAT(VERSION(),'|',@@server_uuid,'|',@@hostname,'|',@@port,'|',CURRENT_USER());").Split('|')
if($identity[0] -ne '8.0.46' -or $identity[2] -ne $container -or $identity[3] -ne '3306' -or $identity[4] -ne 'root@localhost'){throw 'New server identity mismatch'}
$metadata=@(& docker inspect $container | ConvertFrom-Json)[0]
if($LASTEXITCODE -ne 0 -or $metadata.Image -ne $expectedImageId -or $metadata.Config.Labels.'kkiri.validation' -ne $label){throw 'New container metadata mismatch'}
$bindings=@($metadata.NetworkSettings.Ports.'3306/tcp');$mounts=@($metadata.Mounts | Where-Object {$_.Destination -eq '/var/lib/mysql'})
if($bindings.Count -ne 1 -or $bindings[0].HostIp -ne '127.0.0.1' -or $bindings[0].HostPort -ne '13348' -or $mounts.Count -ne 1 -or $mounts[0].Name -ne $volume){throw 'New resource binding/mount mismatch'}
if((Invoke-NewSql "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$db';") -ne '0' -or
   (Invoke-NewSql "SELECT COUNT(*) FROM mysql.user WHERE User='$account';") -ne '0'){throw 'New schema/account already exists'}
$sha=[Security.Cryptography.SHA256]::Create()
try{$password=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($rootPassword+':'+$account)))).Replace('-','').ToLowerInvariant()}finally{$sha.Dispose()}
Invoke-NewSql "CREATE DATABASE ``$db`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER '$account'@'%' IDENTIFIED BY '$password'; GRANT ALL PRIVILEGES ON ``$db``.* TO '$account'@'%';" | Out-Null
$password=$null;$rootPassword=$null
if((Invoke-NewSql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$db';") -ne '0'){throw 'New DB not empty'}
$manifest=@{container=$container;containerId=$metadata.Id;volume=$volume;image=$image;imageId=$expectedImageId;port=13348;
    hostname=$container;version=$identity[0];uuid=$identity[1];db=$db;user=$account;jarSha256=$jarHash;
    createdAt=(Get-Date).ToUniversalTime().ToString('o');status='retain-no-resource-deletion'}
$metadata=$null
$manifest | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $work 'environment.json')
Write-Output 'Created only the approved NEW MySQL container/volume/empty DB/account; secrets omitted; resources retained.'
