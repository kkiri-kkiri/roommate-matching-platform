# Read-only. No database connection, credentials, engine startup, pull or resource mutation.
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$work=Join-Path $repo 'backend/build/matching-calendar-http5'
New-Item -ItemType Directory -Force -Path $work | Out-Null
$result=@{checkedAtUtc=[DateTime]::UtcNow.ToString('o');readOnly=$true;status='CHECKING';stage='engine';dbConnections=0;httpRequests=0}
function Docker-Read([string[]]$arguments){
    $prior=$ErrorActionPreference
    try{$ErrorActionPreference='Continue';$lines=@(& docker @arguments 2>&1|ForEach-Object {$_.ToString()});$exitCode=$LASTEXITCODE}finally{$ErrorActionPreference=$prior}
    if($exitCode -ne 0){throw 'Docker read-only query unavailable; no engine startup or resource mutation attempted'}
    return $lines
}
try{
    if($env:DOCKER_HOST -and $env:DOCKER_HOST -notmatch '^npipe://'){throw 'Remote Docker prohibited'}
    $endpoint=@(Docker-Read @('context','inspect','--format','{{.Endpoints.docker.Host}}'))
    if($endpoint.Count -ne 1 -or $endpoint[0] -notmatch '^npipe://'){throw 'Local Docker pipe unavailable'}
    $result.endpoint=$endpoint[0]
    $os=@(Docker-Read @('info','--format','{{.OSType}}'));if($os.Count -ne 1 -or $os[0] -ne 'linux'){throw 'Local Linux engine required'}
    $result.stage='names'
    $names=@(Docker-Read @('ps','-a','--format','{{.Names}}'))
    if($names -contains 'kkiri-mc-http5-mysql-20261022'){throw 'New container name collision; no reuse'}
    $volumes=@(Docker-Read @('volume','ls','--format','{{.Name}}'))
    if($volumes -contains 'kkiri-mc-http5-data-20261022'){throw 'New volume name collision; no reuse'}
    $result.stage='image'
    $fingerprint=@(Docker-Read @('image','inspect','mysql:8.0.46-oraclelinux9','--format','{{.Id}} {{.Os}}/{{.Architecture}}'))
    if($fingerprint.Count -ne 1 -or $fingerprint[0] -ne 'sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b linux/amd64'){throw 'Expected local image unavailable; no pull'}
    $result.image=$fingerprint[0];$result.stage='ports'
    foreach($port in @(13348,18095,18096)){$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$port);try{$listener.Start()}finally{$listener.Stop()}}
    $result.status='READY';$result.stage='complete'
}catch{$result.status='BLOCKED';$result.reason=$_.Exception.Message}
finally{[IO.File]::WriteAllText((Join-Path $work 'preflight.json'),($result|ConvertTo-Json -Depth 5),(New-Object Text.UTF8Encoding($false)))}
Write-Output ($result|ConvertTo-Json -Compress)
if($result.status -ne 'READY'){exit 1}
