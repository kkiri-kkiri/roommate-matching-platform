param([ValidateSet('Prepare','PrepareRecovery','P0','ContinueP0','P1','RecoverPatchNull','Audit','Diagnose')][string]$Stage='Prepare',[switch]$ApprovedIsolatedRun)
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$work=Join-Path $repo 'backend/build/matching-calendar-http5'
$jar=Join-Path $repo 'backend/build/libs/backend-0.0.1-SNAPSHOT.jar'
$hash=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
$artifact=Join-Path $work ('artifact-'+$hash)
$main=Join-Path $artifact 'main';$lib=Join-Path $artifact 'lib';$classes=Join-Path $work 'classes'
$utf8=New-Object Text.UTF8Encoding($false)
function Json($file,$value){[IO.File]::WriteAllText($file,($value|ConvertTo-Json -Depth 30),$utf8)}
if($Stage -in @('Prepare','PrepareRecovery')){
    if($Stage -eq 'PrepareRecovery'){
        if(!$ApprovedIsolatedRun){throw 'Approved original scope required'}
        $old=Get-Content -Raw -Encoding UTF8 (Join-Path $work 'P0/matching/summary.json')|ConvertFrom-Json
        if($old.httpRequests -ne 0 -or !$old.processesStopped -or !$old.allTablesEmpty -or !$old.ddlUnchanged){throw 'Only zero-HTTP startup failure can recover without rerunning cases'}
        if(Test-Path -LiteralPath (Join-Path $work 'P1/calendar/summary.json')){
            $last=Get-Content -Raw -Encoding UTF8 (Join-Path $work 'P1/calendar/summary.json')|ConvertFrom-Json
            if(!$last.processesStopped -or !$last.allTablesEmpty){throw 'Previous phase not cleaned'}
        }
    }elseif(Test-Path -LiteralPath (Join-Path $work 'execution-started.json')){throw 'One-shot execution already started'}
    New-Item -ItemType Directory -Force -Path $main,$lib,$classes | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive=[IO.Compression.ZipFile]::OpenRead($jar)
    try{foreach($entry in $archive.Entries){
        $target=$null
        if($entry.FullName -match '^BOOT-INF/lib/[^/]+\.jar$'){$target=Join-Path $lib $entry.Name}
        elseif($entry.FullName.StartsWith('BOOT-INF/classes/') -and !$entry.FullName.EndsWith('/')){
            $target=[IO.Path]::GetFullPath((Join-Path $main $entry.FullName.Substring(17)))
            if(!$target.StartsWith($main+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Unsafe archive entry'}
            if($entry.FullName.EndsWith('.class')){
                $current=Join-Path $repo ('backend/build/classes/java/main/'+$entry.FullName.Substring(17));$sha=[Security.Cryptography.SHA256]::Create();$stream=$entry.Open()
                try{$entryHash=([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-','')}finally{$stream.Dispose();$sha.Dispose()}
                if(!(Test-Path -LiteralPath $current) -or $entryHash -ne (Get-FileHash -LiteralPath $current).Hash){throw 'JAR/current classes differ; rebuild must be reviewed separately'}
            }
        }
        if($target){New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($target)) | Out-Null;[IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$target,$true)}
    }}finally{$archive.Dispose()}
    $savedRecovery=$env:MC5_PREPARE_RECOVERY
    try{if($Stage -eq 'PrepareRecovery'){$env:MC5_PREPARE_RECOVERY='true'};& node (Join-Path $PSScriptRoot 'prepare.cjs');if($LASTEXITCODE -ne 0){throw 'Source adaptation failed'}}finally{$env:MC5_PREPARE_RECOVERY=$savedRecovery}
    $cp=$main+';'+$lib+'/*';$sources=@(Get-ChildItem (Join-Path $work 'sources') -Filter '*.java')
    foreach($domain in @('matching','calendar')){
        $domainClasses=Join-Path $classes $domain;New-Item -ItemType Directory -Force -Path $domainClasses|Out-Null
        $domainSources=@($sources|Where-Object {if($domain -eq 'calendar'){$_.Name -like 'Calendar*'}else{$_.Name -notlike 'Calendar*'}}|Select-Object -ExpandProperty FullName)
        & javac -encoding UTF-8 -cp $cp -d $domainClasses $domainSources
        if($LASTEXITCODE -ne 0){throw 'Compile failed; no DB or HTTP access'}
    }
    $matching=@(& java -cp ((Join-Path $classes 'matching')+';'+$cp) kmi.MatchingHttp5 --list);if($LASTEXITCODE -ne 0){throw 'Matching inventory failed'}
    $calendar=@(& java -cp ((Join-Path $classes 'calendar')+';'+$cp) com.kkirikkiri.calendar.crawler.CalendarHttp5 --list);if($LASTEXITCODE -ne 0){throw 'Calendar inventory failed'}
    $matchingCases=[string[]](ConvertFrom-Json -InputObject ($matching -join "`n"))
    $calendarCases=[string[]](ConvertFrom-Json -InputObject ($calendar -join "`n"))
    if($matchingCases.Count -ne 14 -or $calendarCases.Count -ne 15){throw 'Unexpected scenario inventory'}
    Json (Join-Path $work 'prepared.json') @{jarSha256=$hash;matching=$matchingCases;calendar=$calendarCases;dbConnections=0;httpRequests=0;compiledAtUtc=[DateTime]::UtcNow.ToString('o')}
    Write-Output 'Prepare PASS: compiled, JAR/current classes match; no Docker/DB/HTTP access.'
    return
}
if(!$ApprovedIsolatedRun){throw 'Explicit approval of the reviewed resource plan and execution scope is required'}
$prepared=Get-Content -Raw -Encoding UTF8 (Join-Path $work 'prepared.json')|ConvertFrom-Json
$manifest=Get-Content -Raw -Encoding UTF8 (Join-Path $work 'environment.json')|ConvertFrom-Json
if($hash -ne $prepared.jarSha256 -or $hash -ne $manifest.jarSha256 -or $manifest.container -cne 'kkiri-mc-http5-mysql-20261022' -or $manifest.db -cne 'kkirimc5http20261022' -or $manifest.user -cne 'mc5http20261022' -or $manifest.port -ne 13348 -or $manifest.volume -cne 'kkiri-mc-http5-data-20261022' -or $manifest.version -ne '8.0.46' -or $manifest.uuid -notmatch '^[a-f0-9-]{36}$'){throw 'Approved identity/artifact mismatch'}
foreach($entry in (Get-Content -Raw -Encoding UTF8 (Join-Path $work 'source-adaptations.json')|ConvertFrom-Json)){
    if((Get-FileHash -LiteralPath (Join-Path $repo $entry.source)).Hash -ne $entry.sourceSha256 -or (Get-FileHash -LiteralPath (Join-Path $work ('sources/'+[IO.Path]::GetFileName($entry.source)))).Hash -ne $entry.generatedSha256){throw 'Prepared source drift'}
}
if($Stage -eq 'P0' -and (Test-Path -LiteralPath (Join-Path $work 'execution-started.json'))){throw 'P0 already started; preserve original results and review failures first'}
if($Stage -eq 'ContinueP0'){
    if(Test-Path -LiteralPath (Join-Path $work 'p0-continuation-started.json')){throw 'Continuation already started'}
    $old=Get-Content -Raw -Encoding UTF8 (Join-Path $work 'P0/matching/summary.json')|ConvertFrom-Json
    if($old.httpRequests -ne 0 -or !$old.processesStopped -or !$old.allTablesEmpty -or !$old.ddlUnchanged){throw 'Recovery preconditions failed'}
}
if($Stage -in @('P1','RecoverPatchNull')){
    if($Stage -eq 'P1' -and (Test-Path -LiteralPath (Join-Path $work 'p1-started.json'))){throw 'P1 already started'}
    $review=Get-Content -Raw -Encoding UTF8 (Join-Path $work 'p0-reviewed.json')|ConvertFrom-Json
    if($review.status -cne 'P0_REVIEWED_PASS' -or $review.cases -ne 17){throw 'P0 review required before P1'}
    foreach($entry in $review.evidence){if((Get-FileHash -LiteralPath (Join-Path $work $entry.path)).Hash -ne $entry.sha256){throw 'P0 review evidence changed'}}
}
if($Stage -eq 'RecoverPatchNull'){
    if(Test-Path -LiteralPath (Join-Path $work 'patch-null-recovery-started.json')){throw 'Null PATCH recovery already started'}
    $old=Get-Content -Raw -Encoding UTF8 (Join-Path $work 'P1/calendar/summary.json')|ConvertFrom-Json
    $records=[object[]](ConvertFrom-Json -InputObject ([IO.File]::ReadAllText((Join-Path $work 'P1/calendar/cases.json'))))
    $failed=@($records|Where-Object {$_.status -ne 'PASS'})
    if(!$old.processesStopped -or !$old.allTablesEmpty -or $old.scenarios -ne 6 -or $old.passed -ne 5 -or $failed.Count -ne 1 -or $failed[0].name -cne 'patch-null-merge'){throw 'Only the recorded HH:mm assertion failure can recover'}
}
if($env:DOCKER_HOST -and $env:DOCKER_HOST -notmatch '^npipe://'){throw 'Remote Docker prohibited'}
$endpoint=& docker context inspect --format '{{.Endpoints.docker.Host}}';if($LASTEXITCODE -ne 0 -or $endpoint -notmatch '^npipe://'){throw 'Local Docker pipe required'}
$metadata=@(& docker inspect $manifest.container|ConvertFrom-Json)[0]
if($LASTEXITCODE -ne 0 -or $metadata.Id -cne $manifest.containerId -or !$metadata.State.Running -or $metadata.Image -cne $manifest.imageId -or $metadata.Config.Hostname -cne $manifest.container -or $metadata.Config.Labels.'kkiri.validation' -cne 'matching-calendar-http5-20261022'){throw 'New resource ownership mismatch'}
$bindings=@($metadata.NetworkSettings.Ports.'3306/tcp');$mounts=@($metadata.Mounts|Where-Object {$_.Destination -eq '/var/lib/mysql'})
if($bindings.Count -ne 1 -or $bindings[0].HostIp -ne '127.0.0.1' -or $bindings[0].HostPort -ne '13348' -or $mounts.Count -ne 1 -or $mounts[0].Name -cne $manifest.volume){throw 'New binding/volume mismatch'}
$rootEntry=@($metadata.Config.Env|Where-Object {$_.StartsWith('MYSQL_ROOT_PASSWORD=')});if($rootEntry.Count -ne 1){throw 'New credential unavailable'}
$sha=[Security.Cryptography.SHA256]::Create()
try{$password=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($rootEntry[0].Substring(20)+':'+$manifest.user)))).Replace('-','').ToLowerInvariant()}finally{$sha.Dispose()}
$metadata=$null;$rootEntry=$null
$jwt=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([Guid]::NewGuid().ToString()+[Guid]::NewGuid().ToString()))
$values=@{MC5_APPROVED='true';MC5_EXISTING_SCHEMA=($(if($Stage -eq 'ContinueP0'){'true'}else{$null}));MC5_PATCH_NULL_RECOVERY=($(if($Stage -eq 'RecoverPatchNull'){'true'}else{$null}));KM_DATABASE=$manifest.db;KM_USER=$manifest.user;KM_PASSWORD=$password;KM_UUID=$manifest.uuid;KM_HOSTNAME=$manifest.container;KM_JWT=$jwt;CM_DB=$manifest.db;CM_USER=$manifest.user;CM_PASSWORD=$password;CM_JWT=$jwt;MC5_IMAGES=(Join-Path $work 'images');JAVA_TOOL_OPTIONS=$null;JDK_JAVA_OPTIONS=$null;_JAVA_OPTIONS=$null;KM_ROOT_PASSWORD=$null}
$saved=@{};foreach($key in $values.Keys){$saved[$key]=[Environment]::GetEnvironmentVariable($key,'Process');[Environment]::SetEnvironmentVariable($key,$values[$key],'Process')}
$password=$null;$jwt=$null
try{
    foreach($port in @(18095,18096)){$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$port);try{$listener.Start()}finally{$listener.Stop()}}
    $cp=(Join-Path $classes 'matching')+';'+$main+';'+$lib+'/*'
    if($Stage -eq 'Audit'){
        & java '-Duser.timezone=Asia/Seoul' -cp $cp kmi.McHttp5Audit (Join-Path $work ('audit-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'.json'))
        if($LASTEXITCODE -ne 0){throw 'New-schema audit failed'}
        return
    }
    if($Stage -eq 'Diagnose'){
        $out=Join-Path $work ('diagnostic-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'));New-Item -ItemType Directory -Path $out|Out-Null
        & java '-Duser.timezone=Asia/Seoul' -cp $cp kmi.McHttp5Audit $out '--diagnose'
        if($LASTEXITCODE -ne 0){throw 'Diagnostic failed; inspect redacted evidence'}
        return
    }
    $marker=if($Stage -eq 'P0'){'execution-started.json'}elseif($Stage -eq 'ContinueP0'){'p0-continuation-started.json'}elseif($Stage -eq 'RecoverPatchNull'){'patch-null-recovery-started.json'}else{'p1-started.json'}
    Json (Join-Path $work $marker) @{atUtc=[DateTime]::UtcNow.ToString('o');containerId=$manifest.containerId;priority=$Stage}
    $phases=if($Stage -eq 'RecoverPatchNull'){@('calendar')}else{@('matching','calendar')}
    foreach($phase in $phases){
        $priority=if($Stage -eq 'ContinueP0'){'P0'}elseif($Stage -eq 'RecoverPatchNull'){'P1'}else{$Stage}
        $folder=if($Stage -eq 'ContinueP0' -and $phase -eq 'matching'){'P0-continue1/matching'}elseif($Stage -eq 'RecoverPatchNull'){'P1-null-recovery/calendar'}else{$priority+'/'+$phase}
        $out=Join-Path $work $folder;New-Item -ItemType Directory -Path $out | Out-Null
        $cp=(Join-Path $classes $phase)+';'+$main+';'+$lib+'/*'
        if($phase -eq 'matching'){& java '-Duser.timezone=Asia/Seoul' -cp $cp kmi.MatchingHttp5 $out $priority}
        else{& java '-Duser.timezone=Asia/Seoul' -cp $cp com.kkirikkiri.calendar.crawler.CalendarHttp5 $repo $out $priority}
        if($LASTEXITCODE -ne 0){throw 'Phase failed/incomplete; inspect original evidence before any continuation. Resources retained.'}
    }
}finally{foreach($key in $saved.Keys){[Environment]::SetEnvironmentVariable($key,$saved[$key],'Process')};$values=$null}
