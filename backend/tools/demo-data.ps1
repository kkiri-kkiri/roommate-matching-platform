param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('Clone','Bootstrap','Preview','Apply','Verify','VerifySource','FreshPreview','FreshApply')][string]$Action,
    [Parameter(Mandatory=$true)][string]$Schema,
    [ValidateSet('127.0.0.1','localhost')][string]$HostName='127.0.0.1',
    [ValidateRange(1,65535)][int]$Port=3306,
    [string]$Anchor='2026-10-31',
    [string]$ExpectedToken,
    [string]$SourceJournal,
    [switch]$UseLocalConfiguration
)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
if($Schema -cnotmatch '^kkirikkiri_demo50_[a-zA-Z0-9_]{1,40}$') { throw 'Only a new isolated kkirikkiri_demo50_* schema is allowed; normal/original DBs are forbidden.' }
if($Action -in @('Apply','FreshApply') -and $ExpectedToken -cnotmatch '^[a-f0-9]{64}$') { throw 'An exact reviewed preview token is required.' }
$backend=Split-Path -Parent $PSScriptRoot
$classpathFile=Join-Path $backend 'build/demo-data/classpath.txt'
if(!(Test-Path -LiteralPath $classpathFile)) { throw 'Build the data tool first: gradlew.bat prepareDemoData' }
$state=Join-Path $backend ('local-data/demo50/'+$Schema)
$logs=Join-Path $backend ('build/demo50/'+$Schema)
$targetUrl="jdbc:mysql://$HostName`:$Port/$Schema`?useSSL=false&allowPublicKeyRetrieval=true"
$sourceSchema='kkirikkiri_exhibition_20261031'
$sourceUrl="jdbc:mysql://$HostName`:$Port/$sourceSchema`?useSSL=false&allowPublicKeyRetrieval=true"
$username=$env:DEMO_DB_USERNAME; $password=$env:DEMO_DB_PASSWORD
$sourceUsername=$env:DEMO_SOURCE_USERNAME; $sourcePassword=$env:DEMO_SOURCE_PASSWORD
if($UseLocalConfiguration) {
    $configuration=@{}
    Get-Content -Encoding UTF8 (Join-Path $backend '.env') | ForEach-Object {
        if($_ -match '^\s*([A-Za-z_][A-Za-z0-9_.-]*)\s*=\s*(.*)$') { $configuration[$matches[1]]=$matches[2] }
    }
    if($configuration.DB_URL -notmatch '^jdbc:mysql://(?:localhost|127\.0\.0\.1):3306/kkirikkiri(?:\?.*)?$' -or $Port -ne 3306) { throw 'Unexpected local configuration; use explicit environment credentials instead.' }
    $username=$configuration.DB_USERNAME; $password=$configuration.DB_PASSWORD
    $sourceUsername=$username; $sourcePassword=$password
}
if(!$username -or !$password) { throw 'Set DEMO_DB_USERNAME and DEMO_DB_PASSWORD privately.' }
if(!$SourceJournal) { $SourceJournal=Join-Path $backend 'local-data/exhibition-20261031/exhibition.json' }
$mysql=(Get-Command mysql -ErrorAction Stop).Source
$java=if($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java -ErrorAction Stop).Source }
New-Item -ItemType Directory -Force $state,$logs | Out-Null
. (Join-Path $PSScriptRoot 'demo-native.ps1')
function Sql([string]$Text,[string]$Database='information_schema') {
    if($Database -notin @('information_schema',$Schema)) { throw 'Unexpected SQL target' }
    Invoke-Native $mysql @('--protocol=TCP',"--host=$HostName","--port=$Port","--user=$username","--database=$Database",'--batch','--raw','--skip-column-names','--default-character-set=utf8mb4') @{'MYSQL_PWD'=$password} $Text
}
if($Action -in @('Clone','Bootstrap')) {
    $exists=(Sql "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$Schema';").Trim()
    if($exists -ne '0' -or (Test-Path (Join-Path $state 'receipt.json')) -or (Test-Path (Join-Path $state 'source-30.sql'))) { throw 'Schema/state already exists. No overwrite, drop or automatic cleanup is permitted.' }
    if($Action -eq 'Clone') {
        if(!$sourceUsername -or !$sourcePassword -or !(Test-Path -LiteralPath $SourceJournal)) { throw 'Source credentials and original source journal are required.' }
        $dump=(Get-Command mysqldump -ErrorAction Stop).Source
        $backup=Join-Path $state 'source-30.sql'
        $null=Invoke-Native $dump @('--protocol=TCP',"--host=$HostName","--port=$Port","--user=$sourceUsername",'--single-transaction','--skip-lock-tables','--no-tablespaces','--set-gtid-purged=OFF','--default-character-set=utf8mb4',"--result-file=$backup",$sourceSchema) @{'MYSQL_PWD'=$sourcePassword}
        [pscustomobject]@{file='source-30.sql';sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $backup).Hash} | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $state 'backup-checksum.json')
    }
    $null=Sql "CREATE DATABASE $Schema CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
    if($Action -eq 'Clone') {
        $null=Invoke-Native $mysql @('--protocol=TCP',"--host=$HostName","--port=$Port","--user=$username","--database=$Schema",'--default-character-set=utf8mb4') @{'MYSQL_PWD'=$password} '' $backup
    }
}
$classpath=(Get-Content -Raw -Encoding UTF8 $classpathFile).Trim()
$mode=@{Clone='seal-clone';Bootstrap='bootstrap';Preview='preview';Apply='apply';Verify='verify';VerifySource='verify-source';FreshPreview='fresh-preview';FreshApply='fresh-apply'}[$Action]
$child=@{
    SPRING_PROFILES_ACTIVE='local';SPRING_PROFILES_INCLUDE='';SPRING_APPLICATION_JSON='';
    DEMO_DB_SCHEMA=$Schema;DEMO_DB_URL=$targetUrl;DEMO_DB_USERNAME=$username;DEMO_DB_PASSWORD=$password;
    DEMO_WRITES='CONFIRMED_ISOLATED_OFFLINE'
}
if($sourceUsername -and $sourcePassword) { $child.DEMO_SOURCE_URL=$sourceUrl;$child.DEMO_SOURCE_USERNAME=$sourceUsername;$child.DEMO_SOURCE_PASSWORD=$sourcePassword }
if($env:DEMO_NEW_USER_PASSWORD) { $child.DEMO_NEW_USER_PASSWORD=$env:DEMO_NEW_USER_PASSWORD }
$arguments=@('-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-cp',$classpath,'exhibition.DemoDataTool',$mode,"--state=$state","--anchor=$Anchor")
if($Action -eq 'Clone') { $arguments+="--source-journal=$SourceJournal" }
if($ExpectedToken) { $arguments+="--expect=$ExpectedToken" }
$result=Invoke-Native $java $arguments $child
$result | Set-Content -Encoding UTF8 (Join-Path $logs ($mode+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.log'))
Write-Output $result
