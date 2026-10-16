param([switch] $PrepareOnly, [string[]] $Cases=@())
# Opt-in real two-Boot-JVM HTTP suite. From repository root:
# .\backend\tools\calendar-mysql-multi-instance\run.ps1 [-PrepareOnly]
# -Cases <comma-separated case names> selects focused reproductions; assertions are unchanged.
# All outputs are temporary under backend/build/calendar-mysql-multi-instance/<nonce>.
# Uses the existing identified MySQL server only; never opens prior Auth/User schemas.
# Runtime account is DML-only. No production sources/migrations or existing evidence are changed.
# Local fixture HTTP, SMTP refusal, controlled TaskScheduler calls; automatic cron is removed.
# Requires free loopback ports 8081/8082 and pre-existing MySQL Asia/Seoul time-zone support.
$ErrorActionPreference='Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$nonce=[Guid]::NewGuid().ToString('N').Substring(0,12)
$work=Join-Path $repoRoot ('backend/build/calendar-mysql-multi-instance/'+$nonce)
New-Item -ItemType Directory -Path $work | Out-Null
$output=Join-Path $work 'output'; $initScript=Join-Path $work 'init.gradle'
[IO.File]::WriteAllText($initScript,("allprojects { layout.buildDirectory.set(new File('"+$output.Replace('\','/')+"')) }"),[Text.UTF8Encoding]::new($false))
Push-Location (Join-Path $repoRoot 'backend')
try {
    & .\gradlew.bat --offline --no-daemon --project-cache-dir (Join-Path $work 'gradle-cache') --init-script $initScript classes bootJar *> (Join-Path $work 'build.log')
    if($LASTEXITCODE -ne 0){throw "Build failed before DB access: $work"}
} finally {Pop-Location}
$lib=Join-Path $work 'lib';$classes=Join-Path $work 'classes'
New-Item -ItemType Directory -Path $lib,$classes | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=[IO.Compression.ZipFile]::OpenRead((Join-Path $output 'libs/backend-0.0.1-SNAPSHOT.jar'))
try {foreach($entry in $archive.Entries){if($entry.FullName -match '^BOOT-INF/lib/[^/]+\.jar$'){[IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $lib $entry.Name),$false)}}} finally {$archive.Dispose()}
$cp=(Join-Path $output 'classes/java/main')+';'+(Join-Path $output 'resources/main')+';'+$lib+'/*'
& javac -encoding UTF-8 -cp $cp -d $classes @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' | Select-Object -ExpandProperty FullName)
if($LASTEXITCODE -ne 0){throw 'Harness compilation failed before DB access'}
if($PrepareOnly){Write-Output "Prepared without DB access: $work";return}
$container='kkiri-auth-mysql-test'
$meta=@(& docker inspect $container | ConvertFrom-Json)[0]
if($LASTEXITCODE -ne 0 -or !$meta.State.Running -or $meta.Id -ne 'e15a46faca0b46489287bdb797586f516cc79902c3e0aa777706fcdedce6b6d9' -or $meta.Config.Image -ne 'mysql:8.0.46-oraclelinux9' -or $meta.Config.Hostname -ne 'e15a46faca0b'){throw 'Unexpected MySQL container'}
if(@($meta.NetworkSettings.Ports.'3306/tcp' | Where-Object {$_.HostPort -ne '3307'}).Count -or !@($meta.NetworkSettings.Ports.'3306/tcp').Count){throw 'Unexpected MySQL port'}
$meta=$null
$uuid=& docker exec $container cat /var/lib/mysql/auto.cnf
if($LASTEXITCODE -ne 0 -or ($uuid -join "`n") -notmatch 'server-uuid=53ab727e-c847-11f1-9aaf-bad504fada53'){throw 'Unexpected server UUID'}
function Invoke-CalendarProvision([string] $sql){
    $answer=$sql | & docker exec -i $container sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=SOCKET -uroot --batch --skip-column-names 2>/dev/null'
    if($LASTEXITCODE -ne 0){throw 'Calendar-only provisioning failed; credentials/SQL omitted'}
    return $answer
}
$schema='calendarmulti'+$nonce;$account='calmulti'+$nonce
if($schema -notmatch '^calendarmulti[0-9a-f]{12}$' -or $account -notmatch '^calmulti[0-9a-f]{12}$'){throw 'Unsafe names'}
if((Invoke-CalendarProvision 'SELECT VERSION();') -ne '8.0.46'){throw 'Unexpected server version'}
if((Invoke-CalendarProvision "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$schema';") -ne '0'){throw 'Fresh schema already exists'}
foreach($port in @(8081,8082)){$probe=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$port);try{$probe.Start()}finally{$probe.Stop()}}
$password=[Guid]::NewGuid().ToString('N')+[Guid]::NewGuid().ToString('N')
$jwt=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(([Guid]::NewGuid().ToString('N')+[Guid]::NewGuid().ToString('N'))))
$envValues=@{CM_DB=$schema;CM_USER=$account;CM_PASSWORD=$password;CM_JWT=$jwt;CM_JDBC_ZONE='UTC'};$oldEnv=@{}
foreach($key in $envValues.Keys){$oldEnv[$key]=[Environment]::GetEnvironmentVariable($key,'Process');[Environment]::SetEnvironmentVariable($key,$envValues[$key],'Process')}
try {
    Invoke-CalendarProvision "CREATE DATABASE $schema CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER '$account'@'%' IDENTIFIED BY '$password'; GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES ON $schema.* TO '$account'@'%';" | Out-Null
    $javaArgs=@('-Duser.timezone=Asia/Seoul','-cp',($classes+';'+$cp),'com.kkirikkiri.calendar.crawler.CalendarMultiInstance',$repoRoot,$work)
    & java @javaArgs Initialize
    if($LASTEXITCODE -ne 0){throw "Schema initialization failed: $work"}
    Invoke-CalendarProvision @"
DELIMITER //
CREATE TRIGGER $schema.calendar_multi_fail BEFORE INSERT ON $schema.calendar_event FOR EACH ROW
BEGIN IF NEW.title = 'CALENDAR_DB_FAIL' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled calendar storage failure'; END IF; END//
DELIMITER ;
REVOKE CREATE,ALTER,INDEX,REFERENCES ON $schema.* FROM '$account'@'%';
"@ | Out-Null
    if($Cases.Count){& java @javaArgs Verify ($Cases -join ',')}else{& java @javaArgs Verify}
    if($LASTEXITCODE -ne 0){throw "Calendar multi-JVM verification failed/incomplete: $work"}
    Write-Output "Calendar multi-JVM verification completed: $schema; empty schema/account retained."
} finally {
    foreach($key in $oldEnv.Keys){[Environment]::SetEnvironmentVariable($key,$oldEnv[$key],'Process')}
    $envValues=$null;$password=$null;$jwt=$null
}
