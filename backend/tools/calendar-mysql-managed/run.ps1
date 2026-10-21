param([ValidateSet('All','Multi')][string] $Stage = 'All', [string[]] $Cases = @())
# Reuses the unchanged Calendar scenario implementations in a newly owned, disposable MySQL server.
# No pre-existing database, account, container or volume is adopted or removed.
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$nonce = [Guid]::NewGuid().ToString('N').Substring(0,12)
$container = 'kkiri-calendar-' + $nonce
$work = Join-Path $repoRoot ('backend/build/calendar-mysql-managed/' + $nonce)
$single = Join-Path $repoRoot ('backend/build/calendar-mysql-concurrency/' + $nonce)
$multi = Join-Path $repoRoot ('backend/build/calendar-mysql-multi-instance/' + $nonce)
New-Item -ItemType Directory -Force -Path $work,$single,$multi | Out-Null
if ((& docker context inspect --format '{{.Endpoints.docker.Host}}') -notmatch '^(npipe://|unix://)') { throw 'Local Docker required' }
foreach ($port in 3307,8081,8082) {
    $probe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$port)
    try { $probe.Start() } finally { $probe.Stop() }
}
$owned = $false
$prior = @{}
$keys = 'MYSQL_ROOT_PASSWORD','KKIRI_CAL_DB','KKIRI_CAL_USER','KKIRI_CAL_PASSWORD','KKIRI_CAL_UUID','KKIRI_CAL_HOSTNAME','KKIRI_CAL_JDBC_ZONE','CM_DB','CM_USER','CM_PASSWORD','CM_JWT','CM_JDBC_ZONE','CM_MANAGED_OWNER','CM_MANAGED_UUID'
foreach ($key in $keys) { $prior[$key] = [Environment]::GetEnvironmentVariable($key,'Process') }
function Set-RunEnvironment([hashtable] $values) {
    foreach ($key in $values.Keys) { [Environment]::SetEnvironmentVariable($key,$values[$key],'Process') }
}
function Root-Sql([string] $sql) {
    $result = $sql | & docker exec -i $container sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=SOCKET -uroot --batch --skip-column-names 2>/dev/null'
    if ($LASTEXITCODE -ne 0) { throw 'Disposable MySQL operation failed; SQL and credentials omitted' }
    return $result
}
try {
    $env:MYSQL_ROOT_PASSWORD = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    & docker create --name $container --hostname kkiri-calendar-managed --label "kkiri.calendar.owner=$nonce" --publish '127.0.0.1:3307:3306' --tmpfs '/var/lib/mysql:rw,size=1073741824' --env MYSQL_ROOT_PASSWORD mysql:8.0.46-oraclelinux9 --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Disposable Calendar container creation failed' }
    $owned = $true
    & docker start $container | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Disposable Calendar container start failed' }
    $deadline = [DateTime]::UtcNow.AddSeconds(120)
    do {
        try { $version = Root-Sql 'SELECT VERSION();' } catch { $version = '' }
        if ($version -eq '8.0.46') { break }
        if ([DateTime]::UtcNow -gt $deadline) { throw 'Disposable MySQL readiness timeout' }
        Start-Sleep -Milliseconds 500
    } while ($true)
    $meta = @(& docker inspect $container | ConvertFrom-Json)[0]
    $ports = @($meta.NetworkSettings.Ports.'3306/tcp')
    if ($meta.Config.Labels.'kkiri.calendar.owner' -ne $nonce -or $meta.Config.Image -ne 'mysql:8.0.46-oraclelinux9' -or
            $meta.Config.Hostname -ne 'kkiri-calendar-managed' -or $ports.Count -ne 1 -or
            $ports[0].HostIp -ne '127.0.0.1' -or $ports[0].HostPort -ne '3307') { throw 'Disposable Calendar identity mismatch' }
    $uuid = Root-Sql 'SELECT @@server_uuid;'
    @{owner=$nonce;container=$container;version=$version;uuid=$uuid;host='127.0.0.1';port=3307;single=$single;multi=$multi} |
        ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $work 'environment.json')
    $jar = Join-Path $repoRoot 'backend/build/libs/backend-0.0.1-SNAPSHOT.jar'
    $lib = Join-Path $work 'lib'; $classes = Join-Path $work 'classes'; $singleClasses = Join-Path $work 'single-classes'
    New-Item -ItemType Directory -Path $lib,$classes,$singleClasses | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($jar)
    try { foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '^BOOT-INF/lib/[^/]+\.jar$') {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $lib $entry.Name),$false)
        }
    } } finally { $archive.Dispose() }
    $cp = (Join-Path $repoRoot 'backend/build/classes/java/main') + ';' + (Join-Path $repoRoot 'backend/build/resources/main') + ';' + $lib + '/*'
    # The single verifier has its own restricted @EnableJpaRepositories configuration.
    # Keep it outside the Boot server classpath so component scanning cannot import it.
    & javac -encoding UTF-8 -cp $cp -d $singleClasses (Join-Path $repoRoot 'backend/tools/calendar-mysql-concurrency/CalendarMysqlValidation.java')
    if ($LASTEXITCODE -ne 0) { throw 'Single Calendar harness compilation failed' }
    $sources = @(Get-ChildItem (Join-Path $repoRoot 'backend/tools/calendar-mysql-multi-instance') -Filter '*.java' | Select-Object -ExpandProperty FullName)
    $ErrorActionPreference = 'Continue' # Windows PowerShell treats javac informational stderr as NativeCommandError.
    & javac -encoding UTF-8 -cp $cp -d $classes $sources
    $ErrorActionPreference = 'Stop'
    if ($LASTEXITCODE -ne 0) { throw 'Calendar harness compilation failed' }
    $singleDb = 'calendarverify'+$nonce; $singleUser = 'calverify'+$nonce
    $multiDb = 'calendarmulti'+$nonce; $multiUser = 'calmulti'+$nonce
    foreach ($name in @($singleDb,$multiDb)) {
        if ((Root-Sql "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$name';") -ne '0') { throw 'Refusing nonempty-existing schema identity' }
    }
    $singlePassword = [Guid]::NewGuid().ToString('N')+[Guid]::NewGuid().ToString('N')
    $multiPassword = [Guid]::NewGuid().ToString('N')+[Guid]::NewGuid().ToString('N')
    Root-Sql "CREATE DATABASE $singleDb CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER '$singleUser'@'%' IDENTIFIED BY '$singlePassword'; GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES ON $singleDb.* TO '$singleUser'@'%'; CREATE DATABASE $multiDb CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER '$multiUser'@'%' IDENTIFIED BY '$multiPassword'; GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES ON $multiDb.* TO '$multiUser'@'%';" | Out-Null
    Set-RunEnvironment @{KKIRI_CAL_DB=$singleDb;KKIRI_CAL_USER=$singleUser;KKIRI_CAL_PASSWORD=$singlePassword;KKIRI_CAL_UUID=$uuid;KKIRI_CAL_HOSTNAME='kkiri-calendar-managed';KKIRI_CAL_JDBC_ZONE='UTC';CM_DB=$multiDb;CM_USER=$multiUser;CM_PASSWORD=$multiPassword;CM_JWT=[Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([Guid]::NewGuid().ToString('N')+[Guid]::NewGuid().ToString('N')));CM_JDBC_ZONE='UTC';CM_MANAGED_OWNER=$nonce;CM_MANAGED_UUID=$uuid}
    $classpath = $classes+';'+$cp
    & java '-Duser.timezone=Asia/Seoul' -cp ($singleClasses+';'+$cp) com.kkirikkiri.calendar.crawler.CalendarMysqlValidation $repoRoot $single Initialize
    if ($LASTEXITCODE -ne 0) { throw 'Single-JVM schema initialization failed' }
    & java '-Duser.timezone=Asia/Seoul' -cp $classpath com.kkirikkiri.calendar.crawler.CalendarMultiInstance $repoRoot $multi Initialize
    if ($LASTEXITCODE -ne 0) { throw 'Multi-JVM schema initialization failed' }
    foreach ($pair in @(@($singleDb,$singleUser),@($multiDb,$multiUser))) {
        $schema=$pair[0]; $account=$pair[1]
        Root-Sql @"
DELIMITER //
CREATE TRIGGER $schema.calendar_validation_fail BEFORE INSERT ON $schema.calendar_event FOR EACH ROW
BEGIN IF NEW.title = 'CALENDAR_DB_FAIL' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled calendar storage failure'; END IF; END//
DELIMITER ;
REVOKE CREATE,ALTER,INDEX,REFERENCES ON $schema.* FROM '$account'@'%';
"@ | Out-Null
    }
    $failed = @()
    if ($Stage -eq 'All') { foreach ($run in @(@{name='verify';mode='Verify';jvm='Asia/Seoul';jdbc='UTC'},
            @{name='seoul-utc';mode='Matrix';jvm='Asia/Seoul';jdbc='UTC'},
            @{name='seoul-seoul';mode='Matrix';jvm='Asia/Seoul';jdbc='Asia/Seoul'},
            @{name='utc-utc';mode='Matrix';jvm='UTC';jdbc='UTC'})) {
        $out=Join-Path $single $run.name; New-Item -ItemType Directory -Path $out | Out-Null
        $env:KKIRI_CAL_JDBC_ZONE=$run.jdbc
        & java ("-Duser.timezone="+$run.jvm) -cp ($singleClasses+';'+$cp) com.kkirikkiri.calendar.crawler.CalendarMysqlValidation $repoRoot $out $run.mode
        if ($LASTEXITCODE -ne 0) { $failed += $run.name }
    } }
    if ($Cases.Count) {
        & java '-Duser.timezone=Asia/Seoul' -cp $classpath com.kkirikkiri.calendar.crawler.CalendarMultiInstance $repoRoot $multi Verify ($Cases -join ',')
    } else {
        & java '-Duser.timezone=Asia/Seoul' -cp $classpath com.kkirikkiri.calendar.crawler.CalendarMultiInstance $repoRoot $multi Verify
    }
    if ($LASTEXITCODE -ne 0) { $failed += 'multi' }
    @{failed=$failed;single=$single;multi=$multi;artifactSha256=(Get-FileHash $jar -Algorithm SHA256).Hash} | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $work 'result.json')
    if ($failed.Count) { throw ('Calendar cases failed/incomplete: '+($failed -join ',')) }
    Write-Output "Calendar managed verification complete: $work"
} finally {
    if ($owned) {
        $cleanupMeta = @(& docker inspect $container | ConvertFrom-Json)[0]
        if ($LASTEXITCODE -ne 0 -or $cleanupMeta.Config.Labels.'kkiri.calendar.owner' -ne $nonce -or
                $cleanupMeta.Config.Image -ne 'mysql:8.0.46-oraclelinux9' -or $cleanupMeta.Name -ne ('/'+$container)) { throw 'Refusing cleanup: Calendar owner mismatch' }
        & docker rm --force --volumes $container | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Disposable Calendar container cleanup failed' }
        @{owner=$nonce;containerRemoved=$true;temporaryDataRemoved=$true} | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $work 'cleanup.json')
    }
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key,$prior[$key],'Process') }
}
