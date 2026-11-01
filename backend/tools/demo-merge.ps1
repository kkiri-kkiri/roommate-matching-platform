param(
    [Parameter(Mandatory=$true)][ValidateSet('CloneBase','Seal','Preview','Apply','Verify','AuditSources','RestoreCheck','RuntimeClone','ExportPatch','ReplayCheck','VerifyFailedReplay')][string]$Action,
    [Parameter(Mandatory=$true)][string]$Schema,
    [string]$LatestSchema='kkirikkiri_demo50_20261101',
    [string]$LatestReceipt,
    [string]$LatestJournal,
    [string]$CopySchema,
    [ValidateSet('127.0.0.1','localhost')][string]$HostName='127.0.0.1',
    [ValidateRange(1,65535)][int]$Port=3306,
    [string]$ExpectedToken,
    [switch]$UseLocalConfiguration
)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
if($Schema -cnotmatch '^kkirikkiri_demo50_merge_[a-zA-Z0-9_]{1,34}$' -or $LatestSchema -cnotmatch '^kkirikkiri_demo50_[a-zA-Z0-9_]{1,40}$' -or $Schema -eq $LatestSchema) {throw 'Only a separate isolated merge target is allowed.'}
if($Action -eq 'Apply' -and $ExpectedToken -cnotmatch '^[a-f0-9]{64}$'){throw 'Exact reviewed preview token required.'}
$backend=Split-Path -Parent $PSScriptRoot
$classpathFile=Join-Path $backend 'build/demo-data/classpath.txt'
if(!(Test-Path -LiteralPath $classpathFile)){throw 'Run gradlew.bat prepareDemoData first.'}
$state=Join-Path $backend ('local-data/demo50/'+$Schema)
$logs=Join-Path $backend ('build/demo50/'+$Schema)
if(!$LatestReceipt){$LatestReceipt=Join-Path $backend ('local-data/demo50/'+$LatestSchema+'/receipt.json')}
if(!$LatestJournal){$LatestJournal=Join-Path $backend 'local-data/exhibition-20261031/exhibition.json'}
if(!(Test-Path -LiteralPath $LatestReceipt) -or !(Test-Path -LiteralPath $LatestJournal)){throw 'Verified latest source receipt and corresponding journal required.'}
$username=$env:DEMO_DB_USERNAME;$password=$env:DEMO_DB_PASSWORD
if($UseLocalConfiguration){
    $cfg=@{}; Get-Content -Encoding UTF8 (Join-Path $backend '.env') | ForEach-Object {if($_ -match '^\s*([A-Za-z_][A-Za-z0-9_.-]*)\s*=\s*(.*)$'){$cfg[$matches[1]]=$matches[2]}}
    if($cfg.DB_URL -notmatch '^jdbc:mysql://(?:localhost|127\.0\.0\.1):3306/kkirikkiri(?:\?.*)?$' -or $Port -ne 3306){throw 'Unexpected local source configuration.'}
    $username=$cfg.DB_USERNAME;$password=$cfg.DB_PASSWORD
}
if(!$username -or !$password){throw 'Provide private DEMO_DB_USERNAME and DEMO_DB_PASSWORD.'}
$sourcePassword=$password
$mysql=(Get-Command mysql -ErrorAction Stop).Source;$dump=(Get-Command mysqldump -ErrorAction Stop).Source
$java=if($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{(Get-Command java -ErrorAction Stop).Source}
. (Join-Path $PSScriptRoot 'demo-native.ps1')
function Sql([string]$Database,[string]$Text='', [string]$InputFile=''){
    if($Database -ne 'information_schema' -and $Database -ne $Schema -and $Database -ne $CopySchema){throw 'Unexpected write target.'}
    if($Database -eq 'kkirikkiri' -or $Database -eq $LatestSchema){throw 'Original databases are read-only sources.'}
    Invoke-Native $mysql @('--protocol=TCP',"--host=$HostName","--port=$Port","--user=$username","--database=$Database",'--batch','--raw','--skip-column-names','--default-character-set=utf8mb4') @{'MYSQL_PWD'=$password} $Text $InputFile
}
function New-IsolatedSchema([string]$Name){
    if($Name -cnotmatch '^kkirikkiri_demo50_(merge|restore|runtime|replay)_[a-zA-Z0-9_]+$'){throw 'Only isolated schemas may be created.'}
    if((Sql 'information_schema' "SELECT COUNT(*) FROM schemata WHERE schema_name='$Name';").Trim() -ne '0'){throw 'Existing schema must never be overwritten or deleted.'}
    $null=Sql 'information_schema' "CREATE DATABASE $Name CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
}
function Backup([string]$Database,[string]$Path){
    if($Database -notin @('kkirikkiri',$Schema) -or (Test-Path -LiteralPath $Path)){throw 'Unknown backup source or existing backup file.'}
    $null=Invoke-Native $dump @('--protocol=TCP',"--host=$HostName","--port=$Port","--user=$username",'--single-transaction','--skip-lock-tables','--no-tablespaces','--set-gtid-purged=OFF','--default-character-set=utf8mb4',"--result-file=$Path",$Database) @{'MYSQL_PWD'=$password}
    (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash | Set-Content -Encoding ASCII ($Path+'.sha256')
}
if($Action -eq 'CloneBase'){
    if(Test-Path -LiteralPath $state){throw 'A new unused merge state directory is required.'}
    if((Sql 'information_schema' "SELECT COUNT(*) FROM schemata WHERE schema_name='$Schema';").Trim() -ne '0'){throw 'Existing schema is never overwritten.'}
    New-Item -ItemType Directory -Path $state,$logs -Force | Out-Null
    Backup 'kkirikkiri' (Join-Path $state 'base-before.sql')
    New-IsolatedSchema $Schema
    $null=Sql $Schema '' (Join-Path $state 'base-before.sql')
    $migration=Get-Content -Raw -Encoding UTF8 (Join-Path $backend 'migrations/20261101_profile_survey_legacy_columns.sql')
    $null=Sql $Schema ("SET @kkiri_legacy_survey_approval='BACKUP_VERIFIED_WRITERS_STOPPED';"+[Environment]::NewLine+$migration)
}elseif($Action -eq 'Seal'){
    if(!(Test-Path -LiteralPath (Join-Path $state 'base-before.sql')) -or (Test-Path -LiteralPath (Join-Path $state 'merge.json'))){throw 'Seal is only for an unused prepared base clone; no database is recreated.'}
    $backup=Join-Path $state 'base-before.sql'
    if((Get-FileHash -Algorithm SHA256 -LiteralPath $backup).Hash -ne (Get-Content -Raw -Encoding ASCII ($backup+'.sha256')).Trim()){throw 'Prepared base backup checksum mismatch.'}
}
if($Action -in @('CloneBase','Seal')){
    # New accounts only. Never overwrite even a failed credential artifact.
    $bytes=New-Object byte[] 24;$rng=[Security.Cryptography.RandomNumberGenerator]::Create();try{$rng.GetBytes($bytes)}finally{$rng.Dispose()}
    $newPassword=if($env:DEMO_NEW_USER_PASSWORD){$env:DEMO_NEW_USER_PASSWORD}else{[Convert]::ToBase64String($bytes)+'!Aa1'}
    $credential=[Management.Automation.PSCredential]::new('new-demo-accounts',(ConvertTo-SecureString -AsPlainText -Force $newPassword))
    $credentialFile='new-user-login-'+[guid]::NewGuid().ToString('N')+'.clixml'
    $credential | Export-Clixml -LiteralPath (Join-Path $state $credentialFile)
    $credentialFile | Set-Content -Encoding ASCII (Join-Path $state 'credential-file.txt')
}else{
    if(!(Test-Path -LiteralPath (Join-Path $state 'merge.json'))){throw 'A sealed verified base clone is required.'}
    New-Item -ItemType Directory -Path $logs -Force | Out-Null
    $newPassword=''
    if($Action -eq 'Apply'){
        $credentialFile=(Get-Content -Raw -Encoding ASCII (Join-Path $state 'credential-file.txt')).Trim()
        if($credentialFile -notmatch '^new-user-login-[a-f0-9]+\.clixml$'){throw 'Invalid credential file name.'}
        $credential=Import-Clixml -LiteralPath (Join-Path $state $credentialFile);$newPassword=$credential.GetNetworkCredential().Password
    }
}
$privatePasswords=@($newPassword)
if($Action -in @('RestoreCheck','RuntimeClone','ReplayCheck')){
    $prefix=if($Action -eq 'RestoreCheck'){'restore'}elseif($Action -eq 'ReplayCheck'){'replay'}else{'runtime'}
    if($CopySchema -cnotmatch ('^kkirikkiri_demo50_'+$prefix+'_[a-zA-Z0-9_]+$')){throw 'A new isolated restore/runtime schema is required.'}
    $backup=if($Action -in @('RestoreCheck','ReplayCheck')){Join-Path $state 'base-before.sql'}else{Join-Path $state 'merged-after.sql'}
    if($Action -eq 'RuntimeClone'){Backup $Schema $backup}
    $expected=(Get-Content -Raw -Encoding ASCII ($backup+'.sha256')).Trim()
    if((Get-FileHash -Algorithm SHA256 -LiteralPath $backup).Hash -ne $expected){throw 'Backup checksum mismatch.'}
    New-IsolatedSchema $CopySchema;$null=Sql $CopySchema '' $backup
    if($Action -eq 'ReplayCheck'){
        $migration=Get-Content -Raw -Encoding UTF8 (Join-Path $backend 'migrations/20261101_profile_survey_legacy_columns.sql')
        $null=Sql $CopySchema ("SET @kkiri_legacy_survey_approval='BACKUP_VERIFIED_WRITERS_STOPPED';"+[Environment]::NewLine+$migration)
        $patchName=(Get-Content -Raw -Encoding ASCII (Join-Path $state 'reviewed-patch-file.txt')).Trim()
        if($patchName -cnotmatch '^reviewed-merge-[a-f0-9]{32}\.sql$'){throw 'Invalid private patch name.'}
        $patch=Join-Path $state $patchName
        if((Get-FileHash -Algorithm SHA256 -LiteralPath $patch).Hash -ne (Get-Content -Raw -Encoding ASCII ($patch+'.sha256')).Trim()){throw 'Private reviewed patch checksum mismatch.'}
        $reviewed=Get-Content -Raw -Encoding UTF8 (Join-Path $state 'merge.json') | ConvertFrom-Json
        if($reviewed.status -ne 'COMPLETE' -or $reviewed.plan.token -cnotmatch '^[a-f0-9]{64}$'){throw 'Completed reviewed merge required.'}
        $sqlText="SET @kkiri_demo_merge_approval='"+$reviewed.plan.token+"';"+[Environment]::NewLine+(Get-Content -Raw -Encoding UTF8 $patch)
        Write-Output (Sql $CopySchema $sqlText)
        Write-Output (Sql $CopySchema $sqlText)
    }
}
$child=@{SPRING_PROFILES_ACTIVE='local';SPRING_PROFILES_INCLUDE='';SPRING_APPLICATION_JSON='';DEMO_DB_SCHEMA=$Schema;
    DEMO_DB_URL="jdbc:mysql://$HostName`:$Port/$Schema`?useSSL=false&allowPublicKeyRetrieval=true";DEMO_DB_USERNAME=$username;DEMO_DB_PASSWORD=$password;
    DEMO_BASE_URL="jdbc:mysql://$HostName`:$Port/kkirikkiri`?useSSL=false&allowPublicKeyRetrieval=true";DEMO_BASE_USERNAME=$username;DEMO_BASE_PASSWORD=$password;
    DEMO_LATEST_URL="jdbc:mysql://$HostName`:$Port/$LatestSchema`?useSSL=false&allowPublicKeyRetrieval=true";DEMO_LATEST_USERNAME=$username;DEMO_LATEST_PASSWORD=$password;
    DEMO_WRITES='CONFIRMED_ISOLATED_OFFLINE';DEMO_NEW_USER_PASSWORD=$newPassword}
if($Action -eq 'VerifyFailedReplay' -and $CopySchema -cnotmatch '^kkirikkiri_demo50_replay_[a-zA-Z0-9_]+$'){throw 'Only a separate failed rehearsal copy can be checked.'}
if($Action -in @('RestoreCheck','RuntimeClone','ReplayCheck','VerifyFailedReplay')){$child.DEMO_RESTORE_URL="jdbc:mysql://$HostName`:$Port/$CopySchema`?useSSL=false&allowPublicKeyRetrieval=true";$child.DEMO_RESTORE_USERNAME=$username;$child.DEMO_RESTORE_PASSWORD=$password}
$mode=@{CloneBase='seal';Seal='seal';Preview='preview';Apply='apply';Verify='verify';AuditSources='audit-sources';RestoreCheck='verify-restore';RuntimeClone='verify-runtime';ExportPatch='export-patch';ReplayCheck='verify-replay';VerifyFailedReplay='verify-replay-baseline'}[$Action]
$classpath=(Get-Content -Raw -Encoding UTF8 $classpathFile).Trim()
$arguments=@('-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-cp',$classpath,'exhibition.DemoMergeTool',$mode,"--state=$state","--latest-receipt=$LatestReceipt","--latest-journal=$LatestJournal")
if($ExpectedToken){$arguments+="--expect=$ExpectedToken"}
$result=Invoke-Native $java $arguments $child
$result | Set-Content -Encoding UTF8 (Join-Path $logs ($mode+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.log'))
Write-Output $result
