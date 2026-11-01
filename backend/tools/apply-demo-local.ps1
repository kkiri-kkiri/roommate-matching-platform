param(
    [Parameter(Mandatory=$true)][ValidateSet('Preflight','Backup','Apply','Verify','VerifyRuntime','BackupAfterRuntime')][string]$Action,
    [Parameter(Mandatory=$true)][ValidatePattern('^[0-9]{8}_[a-zA-Z0-9_]{1,16}$')][string]$Run,
    [switch]$ApprovedPersonalLocal
)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
if(!$ApprovedPersonalLocal){throw 'Explicit authorization for the personal localhost:3306/kkirikkiri database is required.'}
$backend=Split-Path -Parent $PSScriptRoot
$review=Join-Path $backend 'local-data/demo50/kkirikkiri_demo50_merge_20261101'
$state=Join-Path $backend ('local-data/demo50/local-apply-'+$Run)
$logs=Join-Path $backend ('build/demo50/local-apply-'+$Run)
$patchName='reviewed-merge-708964627961413581e99641535d7185.sql'
$patch=Join-Path $review $patchName
$expectedHash='7977fc06f3d45f846c2e38005cad475e17450d29cce1fb110a9c6ac4c2f1f737'
if((Get-FileHash -Algorithm SHA256 -LiteralPath $patch).Hash.ToLowerInvariant() -ne $expectedHash){throw 'Reviewed patch checksum differs; stop.'}
if((Get-Content -Raw -Encoding ASCII ($patch+'.sha256')).Trim().ToLowerInvariant() -ne $expectedHash){throw 'Reviewed checksum record differs.'}
$cfg=@{};Get-Content -Encoding UTF8 (Join-Path $backend '.env') | ForEach-Object {if($_ -match '^\s*([A-Za-z_][A-Za-z0-9_.-]*)\s*=\s*(.*)$'){$cfg[$matches[1]]=$matches[2]}}
if($cfg.DB_URL -notmatch '^jdbc:mysql://(?:localhost|127\.0\.0\.1):3306/kkirikkiri(?:\?.*)?$'){throw 'Only the existing personal local configuration is allowed.'}
$username=$cfg.DB_USERNAME;$password=$cfg.DB_PASSWORD;$sourcePassword=$password
if(!$username -or !$password){throw 'Private local connection configuration missing.'}
$mysql=(Get-Command mysql -ErrorAction Stop).Source;$dump=(Get-Command mysqldump -ErrorAction Stop).Source
$java=Join-Path $env:JAVA_HOME 'bin/java.exe'
if(!(Test-Path -LiteralPath $java)){throw 'JAVA_HOME must identify the actual JDK 21 installation.'}
$classpath=(Get-Content -Raw -Encoding UTF8 (Join-Path $backend 'build/demo-data/classpath.txt')).Trim()
New-Item -ItemType Directory -Path $logs -Force | Out-Null
. (Join-Path $PSScriptRoot 'demo-native.ps1')
function Sql([string]$Schema,[string]$Text='', [string]$InputFile='') {
    if($Schema -notin @('information_schema','kkirikkiri') -and $Schema -cnotmatch '^kkirikkiri_demo50_final_restore_[a-zA-Z0-9_]+$'){throw 'Unexpected SQL target.'}
    Invoke-Native $mysql @('--protocol=TCP','--host=127.0.0.1','--port=3306',"--user=$username","--database=$Schema",'--batch','--raw','--skip-column-names','--default-character-set=utf8mb4') @{'MYSQL_PWD'=$password} $Text $InputFile
}
function Verify([string]$Mode,[string]$Copy='-') {
    $output=Join-Path $state ($Mode+'-'+[guid]::NewGuid().ToString('N')+'.json')
    $result=Invoke-Native $java @('-Dfile.encoding=UTF-8','-cp',$classpath,'exhibition.DemoLocalVerification',$Mode,$review,$output,$Copy) @{'DEMO_DB_USERNAME'=$username;'DEMO_DB_PASSWORD'=$password}
    Write-Output $result
}
function Assert-Offline {
    $listeners=@(Get-NetTCPConnection -State Listen -LocalPort 3306 -ErrorAction Stop)
    if(!$listeners.Count){throw 'Local MySQL listener missing.'}
    foreach($listener in $listeners){if((Get-Process -Id $listener.OwningProcess -ErrorAction Stop).ProcessName -ne 'mysqld'){throw 'The local port is not owned by mysqld; stop.'}}
    $writers=@(Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object {$_.Name -in @('java.exe','javaw.exe') -and ($_.CommandLine -match 'com\.kkirikkiri\..*Application|exhibition\..*(Tool|Expansion)|roommate-matching-platform.*backend.*\.jar')})
    if($writers.Count){throw 'A project backend/data process is running; stop before applying.'}
    $hostName=$env:COMPUTERNAME
    if($hostName -cnotmatch '^[a-zA-Z0-9_-]+$'){throw 'Unexpected local computer identity.'}
    $checks=(Sql 'information_schema' ("SELECT LOWER(@@hostname)=LOWER('"+$hostName+"'),@@port=3306,@@foreign_key_checks=1,@@unique_checks=1; SELECT COUNT(*) FROM processlist WHERE id<>CONNECTION_ID() AND command<>'Daemon'; SELECT COUNT(*) FROM events WHERE status='ENABLED';")).Trim() -split '\r?\n'
    if($checks.Count -ne 3 -or $checks[0] -ne "1`t1`t1`t1" -or $checks[1] -ne '0' -or $checks[2] -ne '0'){throw 'Local identity, session integrity, writer sessions or scheduled events differ; stop.'}
}
function Backup([string]$Schema,[string]$Name) {
    if($Schema -notin @('kkirikkiri','kkirikkiri_exhibition_20261031','kkirikkiri_demo50_20261101')){throw 'Unexpected backup source.'}
    $path=Join-Path $state $Name
    if(Test-Path -LiteralPath $path){throw 'Never overwrite an existing backup.'}
    $null=Invoke-Native $dump @('--protocol=TCP','--host=127.0.0.1','--port=3306',"--user=$username",'--single-transaction','--skip-lock-tables','--no-tablespaces','--set-gtid-purged=OFF','--default-character-set=utf8mb4','--routines','--events','--triggers','--hex-blob',"--result-file=$path",$Schema) @{'MYSQL_PWD'=$password}
    (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash | Set-Content -Encoding ASCII ($path+'.sha256')
}
function Check-Backup([string]$Name) {
    $path=Join-Path $state $Name
    if((Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash -ne (Get-Content -Raw -Encoding ASCII ($path+'.sha256')).Trim()){throw 'New backup checksum mismatch.'}
}
function Check-Provenance {
    foreach($item in (Get-Content -Raw -Encoding UTF8 (Join-Path $state 'provenance.json') | ConvertFrom-Json)){
        if((Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $backend $item.path)).Hash -ne $item.hash){throw 'A reviewed source/provenance file changed; stop.'}
    }
}
function Save-Phase([string]$Phase) {
    [pscustomobject]@{status=$Phase;database='localhost:3306/kkirikkiri';run=$Run;reviewSchema='kkirikkiri_demo50_merge_20261101';patch=$patchName;sha256=$expectedHash;userAuthorizedPersonalLocal=$true} | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $state 'apply.json')
}
if($Action -in @('Preflight','Backup')){
    Assert-Offline
    if($Action -eq 'Backup' -and (Test-Path -LiteralPath $state)){throw 'A new unused backup directory is required.'}
    New-Item -ItemType Directory -Path $state -Force | Out-Null
    Verify 'preflight'
    if($Action -eq 'Preflight'){exit}
    Save-Phase 'BACKUP_IN_PROGRESS'
    $paths=@('local-data/exhibition-20261031/exhibition.json','local-data/exhibition-20261031/seed-users.properties','local-data/exhibition-20261031/credentials.clixml',
        'local-data/demo50/kkirikkiri_demo50_20261101/receipt.json','local-data/demo50/kkirikkiri_demo50_merge_20261101/merge.json',
        'src/exhibition/java/exhibition/ExhibitionCatalog.java','src/exhibition/java/exhibition/ExhibitionCampusCopy.java','migrations/20261101_profile_survey_legacy_columns.sql')
    $provenance=@(foreach($path in $paths){$full=Join-Path $backend $path;[pscustomobject]@{path=$path;hash=(Get-FileHash -Algorithm SHA256 -LiteralPath $full).Hash}})
    $provenance | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $state 'provenance.json')
    $sourceCopies=Join-Path $state 'source-files';New-Item -ItemType Directory -Path $sourceCopies | Out-Null
    foreach($path in $paths){$destination=Join-Path $sourceCopies $path;New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null;Copy-Item -LiteralPath (Join-Path $backend $path) -Destination $destination}
    foreach($entry in @(@('kkirikkiri','base-before.sql','base'),@('kkirikkiri_exhibition_20261031','exhibition-before.sql','exhibition'),@('kkirikkiri_demo50_20261101','latest50-before.sql','latest'))){
        Backup $entry[0] $entry[1];Check-Backup $entry[1]
        $copy='kkirikkiri_demo50_final_restore_'+$entry[2]+'_'+$Run
        if($copy.Length -gt 64 -or (Sql 'information_schema' "SELECT COUNT(*) FROM schemata WHERE schema_name='$copy';").Trim() -ne '0'){throw 'A new unused restore schema is required.'}
        $null=Sql 'information_schema' "CREATE DATABASE $copy CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
        $null=Sql $copy '' (Join-Path $state $entry[1]);Verify 'restore' ($entry[0]+':'+$copy)
    }
    Verify 'preflight';Check-Provenance;Save-Phase 'BACKUP_VERIFIED'
    Write-Output 'New backups of base, original exhibition and latest fifty-user source restored and verified. No base database write performed.'
}else{
    $record=Get-Content -Raw -Encoding UTF8 (Join-Path $state 'apply.json') | ConvertFrom-Json
    if($record.database -ne 'localhost:3306/kkirikkiri' -or $record.sha256 -ne $expectedHash -or !$record.userAuthorizedPersonalLocal){throw 'An exact approved local apply record is required.'}
    $lock=[IO.File]::Open((Join-Path $state 'apply.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try{
        Check-Provenance
        if($Action -eq 'Apply'){
            if($record.status -ne 'BACKUP_VERIFIED'){throw 'Apply only starts from a verified unused backup checkpoint.'}
            Assert-Offline;foreach($name in @('base-before.sql','exhibition-before.sql','latest50-before.sql')){Check-Backup $name};Verify 'preflight'
            $reviewed=Get-Content -Raw -Encoding UTF8 (Join-Path $review 'merge.json') | ConvertFrom-Json
            if($reviewed.plan.token -cnotmatch '^[a-f0-9]{64}$'){throw 'Invalid reviewed token.'}
            $migration=Get-Content -Raw -Encoding UTF8 (Join-Path $backend 'migrations/20261101_profile_survey_legacy_columns.sql')
            $patchText=Get-Content -Raw -Encoding UTF8 $patch
            Save-Phase 'APPLYING'
            $sqlText="SET @kkiri_legacy_survey_approval='BACKUP_VERIFIED_WRITERS_STOPPED';"+[Environment]::NewLine+$migration+[Environment]::NewLine+"SET @kkiri_demo_merge_approval='"+$reviewed.plan.token+"';"+[Environment]::NewLine+$patchText+[Environment]::NewLine+$patchText
            $result=Sql 'kkirikkiri' $sqlText
            $result | Set-Content -Encoding UTF8 (Join-Path $state 'apply-result.log')
            if($result -notmatch 'Reviewed patch verified and committed' -or $result -notmatch 'Already complete: 0 DML statements'){throw 'Unexpected apply/repeat result; stop for review.'}
            Verify 'after-apply';Backup 'kkirikkiri' 'base-after-before-api.sql';Check-Backup 'base-after-before-api.sql'
            Save-Phase 'DB_APPLIED'
            ('local-apply-'+$Run) | Set-Content -Encoding ASCII (Join-Path $backend 'local-data/demo50/active-local-apply.txt')
            Write-Output $result
        }elseif($Action -eq 'Verify'){
            if($record.status -ne 'DB_APPLIED'){throw 'Completed apply required.'};Verify 'after-apply'
        }elseif($Action -eq 'VerifyRuntime'){
            if($record.status -notin @('DB_APPLIED','RUNTIME_VERIFIED')){throw 'Completed apply required.'};Verify 'after-runtime';Save-Phase 'RUNTIME_VERIFIED'
        }elseif($Action -eq 'BackupAfterRuntime'){
            if($record.status -ne 'RUNTIME_VERIFIED'){throw 'Verified runtime checkpoint required.'};Assert-Offline;Verify 'after-runtime';Backup 'kkirikkiri' 'base-after-runtime.sql';Check-Backup 'base-after-runtime.sql'
        }
    }finally{$lock.Dispose()}
}
