param([ValidateSet('Prepare','Execute','Continue','Audit')] [string] $Stage='Prepare', [switch] $ApprovedIsolatedRun)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$work=Join-Path $repo 'backend/build/auth-user-survey-http5'
$jar=Join-Path $repo 'backend/build/libs/backend-0.0.1-SNAPSHOT.jar'
$hash=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
$lib=Join-Path $work ('lib-'+$hash)
$classes=Join-Path $work 'classes'
New-Item -ItemType Directory -Force -Path $lib,$classes | Out-Null
if($Stage -eq 'Prepare') {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive=[IO.Compression.ZipFile]::OpenRead($jar)
    try {
        $mainRoot=Join-Path $repo 'backend/build/classes/java/main'
        $entries=@($archive.Entries | Where-Object {$_.FullName -match '^BOOT-INF/classes/.+\.class$'})
        if($entries.Count -eq 0){throw 'JAR main classes absent'}
        foreach($entry in $entries){
            $file=Join-Path $mainRoot $entry.FullName.Substring('BOOT-INF/classes/'.Length)
            if(!(Test-Path -LiteralPath $file)){throw 'JAR/current main class missing; rebuild separately'}
            $sha=[Security.Cryptography.SHA256]::Create();$stream=$entry.Open()
            try{$entryHash=([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-','')}finally{$stream.Dispose();$sha.Dispose()}
            if($entryHash -ne (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash){throw 'JAR/current main classes differ; rebuild separately'}
        }
        foreach($entry in $archive.Entries){if($entry.FullName -match '^BOOT-INF/lib/[^/]+\.jar$'){
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $lib $entry.Name),$true)
        }}
    }finally{$archive.Dispose()}
    & node (Join-Path $PSScriptRoot 'prepare.cjs');if($LASTEXITCODE -ne 0){throw 'Source adaptation failed'}
    $cp=$mainRoot+';'+(Join-Path $repo 'backend/build/resources/main')+';'+$lib+'/*'
    $sources=@(Get-ChildItem (Join-Path $work 'sources') -Filter '*.java' | Select-Object -ExpandProperty FullName)
    $prior=$ErrorActionPreference
    try{$ErrorActionPreference='Continue'; & javac -encoding UTF-8 -cp $cp -d $classes $sources;$code=$LASTEXITCODE}finally{$ErrorActionPreference=$prior}
    if($code -ne 0){throw 'Harness compilation failed; no DB/HTTP was accessed'}
    $plan=@(& java -cp ($classes+';'+$cp) AusHttp5 --list)
    if($LASTEXITCODE -ne 0){throw 'Scenario inventory failed; no DB/HTTP was accessed'}
    $scenarios=($plan -join "`n") | ConvertFrom-Json
    if($scenarios.Count -ne 59 -or @($scenarios | Where-Object {$_.priority -eq 'P0'}).Count -ne 13){throw 'Review scenario inventory drift'}
    $plan | Set-Content -Encoding UTF8 (Join-Path $work 'planned-scenarios.json')
    @{jarSha256=$hash; jarClassesCompared=$entries.Count; preparedAt=(Get-Date).ToUniversalTime().ToString('o')} |
        ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $work 'prepared.json')
    Write-Output 'Prepare PASS: JAR/main classes match; harness compiled; no DB/HTTP/Docker access.'
    return
}
if(!$ApprovedIsolatedRun){throw 'Execute needs the user-approved resource/execution scope and -ApprovedIsolatedRun'}
$manifest=Get-Content -Raw -Encoding UTF8 (Join-Path $work 'environment.json') | ConvertFrom-Json
if($manifest.container -ne 'kkiri-aus-http5-mysql-20261022' -or $manifest.volume -ne 'kkiri-aus-http5-data-20261022' -or
   $manifest.port -ne 13327 -or $manifest.db -ne 'kkiriaus5http20261022' -or $manifest.user -ne 'aus5http20261022' -or
   $manifest.version -ne '8.0.46' -or $manifest.image -ne 'mysql:8.0.46-oraclelinux9' -or
   $manifest.uuid -notmatch '^[a-f0-9-]{36}$' -or $manifest.jarSha256 -ne $hash){throw 'Approved identity/artifact mismatch'}
$prepared=Get-Content -Raw -Encoding UTF8 (Join-Path $work 'prepared.json') | ConvertFrom-Json
if($prepared.jarSha256 -ne $hash){throw 'Prepare current artifact first'}
# Refuse stale/unreviewed generated source; no regeneration during Execute.
$adaptations=Get-Content -Raw -Encoding UTF8 (Join-Path $work 'source-adaptations.json') | ConvertFrom-Json
foreach($entry in $adaptations){
    if((Get-FileHash -LiteralPath (Join-Path $repo $entry.source) -Algorithm SHA256).Hash -ne $entry.sourceSha256 -or
       (Get-FileHash -LiteralPath (Join-Path $work ('sources/'+[IO.Path]::GetFileName($entry.source))) -Algorithm SHA256).Hash -ne $entry.generatedSha256){throw 'Prepared source drift; run Prepare'}
}
$metadata=@(& docker inspect $manifest.container | ConvertFrom-Json)[0]
if($LASTEXITCODE -ne 0 -or !$metadata.State.Running -or $metadata.Id -ne $manifest.containerId -or
   $metadata.Config.Hostname -ne $manifest.container -or $metadata.Image -ne $manifest.imageId -or
   $metadata.Config.Labels.'kkiri.validation' -ne 'auth-user-survey-http5-20261022'){throw 'New container identity mismatch'}
$bindings=@($metadata.NetworkSettings.Ports.'3306/tcp')
if($bindings.Count -ne 1 -or $bindings[0].HostIp -ne '127.0.0.1' -or $bindings[0].HostPort -ne '13327'){throw 'New loopback binding mismatch'}
$mounts=@($metadata.Mounts | Where-Object {$_.Destination -eq '/var/lib/mysql'})
if($mounts.Count -ne 1 -or $mounts[0].Name -ne $manifest.volume){throw 'New volume mismatch'}
$rootEntry=@($metadata.Config.Env | Where-Object {$_.StartsWith('MYSQL_ROOT_PASSWORD=')})
if($rootEntry.Count -ne 1){throw 'New container credential unavailable'}
$sha=[Security.Cryptography.SHA256]::Create()
try{$password=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($rootEntry[0].Substring(20)+':'+$manifest.user)))).Replace('-','').ToLowerInvariant()}finally{$sha.Dispose()}
$rootEntry=$null;$metadata=$null
$values=@{KKIRI_MI_DATABASE=$manifest.db;KKIRI_MI_USER=$manifest.user;KKIRI_MI_PASSWORD=$password;AUS5_UUID=$manifest.uuid;AUS5_IMAGE_DIR=(Join-Path $work 'images')}
$password=$null;$saved=@{}
foreach($key in $values.Keys){$saved[$key]=[Environment]::GetEnvironmentVariable($key,'Process');[Environment]::SetEnvironmentVariable($key,$values[$key],'Process')}
try{
    $cp=$classes+';'+(Join-Path $repo 'backend/build/classes/java/main')+';'+(Join-Path $repo 'backend/build/resources/main')+';'+$lib+'/*'
    $prior=$ErrorActionPreference
    try{
        $ErrorActionPreference='Continue'
        if($Stage -eq 'Audit'){& java '-Duser.timezone=Asia/Seoul' -cp $cp AusHttp5Audit (Join-Path $work ('audit-'+(Get-Date).ToUniversalTime().ToString('yyyyMMddHHmmss')+'.json'))}
        elseif($Stage -eq 'Continue'){& java '-Duser.timezone=Asia/Seoul' -cp $cp AusHttp5 $repo $hash '--continue' (Join-Path $work 'runs/2026-10-22-a0115b20ea32')}
        else{& java '-Duser.timezone=Asia/Seoul' -cp $cp AusHttp5 $repo $hash}
        $code=$LASTEXITCODE
    }finally{$ErrorActionPreference=$prior}
    if($code -ne 0){throw 'HTTP5 FAIL/INCOMPLETE: evidence and new resources retained'}
}finally{foreach($key in $saved.Keys){[Environment]::SetEnvironmentVariable($key,$saved[$key],'Process')};$values=$null}
