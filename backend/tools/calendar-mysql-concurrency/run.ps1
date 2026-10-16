param([switch] $PrepareOnly, [ValidateSet('Verify','FormerTimezones','Matrix','Plans','All')] [string] $Stage='Verify',
    [ValidateSet('Asia/Seoul','UTC')] [string] $JvmZone='Asia/Seoul',
    [ValidateSet('UTC','Asia/Seoul')] [string] $JdbcZone='UTC')
# Opt-in, single-JVM MySQL verifier. Usage from repository root:
# .\backend\tools\calendar-mysql-concurrency\run.ps1 [-PrepareOnly]
# Reuses the existing Docker MySQL SERVER, never its Auth/User schemas/accounts.
# Creates a fresh calendarverify<nonce> schema/account; no DROP/TRUNCATE or global DB settings.
# Results/build caches live only in ignored backend/build/calendar-mysql-concurrency/.
# -Stage All: original two regressions, original 24 cases, then three separate JVM timezone runs.
# -Stage Plans: focused actual-SQL EXPLAIN comparison (same data, normal optimizer vs IGNORE INDEX baseline).
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$nonce = [Guid]::NewGuid().ToString('N').Substring(0,12)
$work = Join-Path $repoRoot ('backend/build/calendar-mysql-concurrency/' + $nonce)
New-Item -ItemType Directory -Path $work | Out-Null
$initScript = Join-Path $work 'build.gradle'
$output = Join-Path $work 'output'
$init = "allprojects { layout.buildDirectory.set(new File('" + $output.Replace('\','/') + "')) }"
[IO.File]::WriteAllText($initScript,$init,[Text.UTF8Encoding]::new($false))
Push-Location (Join-Path $repoRoot 'backend')
try {
    & .\gradlew.bat --offline --no-daemon --project-cache-dir (Join-Path $work 'gradle-cache') --init-script $initScript classes bootJar *> (Join-Path $work 'build.log')
    if ($LASTEXITCODE -ne 0) { throw "Build failed before DB access; inspect $work/build.log" }
} finally { Pop-Location }
$lib = Join-Path $work 'lib'; $classes = Join-Path $work 'classes'
New-Item -ItemType Directory -Path $lib,$classes | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead((Join-Path $output 'libs/backend-0.0.1-SNAPSHOT.jar'))
try {
    foreach($entry in $archive.Entries) {
        if($entry.FullName -match '^BOOT-INF/lib/[^/]+\.jar$') {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $lib $entry.Name),$false)
        }
    }
} finally { $archive.Dispose() }
$cp = (Join-Path $output 'classes/java/main') + ';' + $lib + '/*'
& javac -encoding UTF-8 -cp $cp -d $classes (Join-Path $PSScriptRoot 'CalendarMysqlValidation.java')
if($LASTEXITCODE -ne 0) { throw 'Verifier compilation failed before DB access.' }
if($PrepareOnly) { Write-Output "Prepared without DB access: $work"; return }

$container = 'kkiri-auth-mysql-test'
$meta = @(& docker inspect $container | ConvertFrom-Json)[0]
if($LASTEXITCODE -ne 0 -or !$meta.State.Running -or $meta.Config.Image -ne 'mysql:8.0.46-oraclelinux9' -or
    $meta.Id -ne 'e15a46faca0b46489287bdb797586f516cc79902c3e0aa777706fcdedce6b6d9' -or $meta.Config.Hostname -ne 'e15a46faca0b') { throw 'Unexpected MySQL server identity; stop.' }
$ports = @($meta.NetworkSettings.Ports.'3306/tcp')
if(!$ports.Count -or @($ports | Where-Object {$_.HostPort -ne '3307'}).Count) { throw 'Unexpected MySQL port.' }
$uuid = & docker exec $container cat /var/lib/mysql/auto.cnf
if($LASTEXITCODE -ne 0 -or ($uuid -join "`n") -notmatch 'server-uuid=53ab727e-c847-11f1-9aaf-bad504fada53') { throw 'Unexpected server UUID.' }
$meta = $null
function Invoke-CalendarRootSql([string] $sql) {
    # Local socket, no default schema, no root secret on the host command line or in files.
    $answer = $sql | & docker exec -i $container sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=SOCKET -uroot --batch --skip-column-names 2>/dev/null'
    if($LASTEXITCODE -ne 0) { throw 'Calendar-only provisioning SQL failed; credentials/SQL omitted.' }
    return $answer
}
$version = Invoke-CalendarRootSql 'SELECT VERSION();'
if($version -ne '8.0.46') { throw 'Unexpected MySQL version.' }
$schema = 'calendarverify' + $nonce; $account = 'calverify' + $nonce
$password = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
if($schema -notmatch '^calendarverify[0-9a-f]{12}$' -or $account -notmatch '^calverify[0-9a-f]{12}$') { throw 'Unsafe schema/account.' }
# Only checks the newly generated name. No SHOW DATABASES or existing-schema queries.
$exists = Invoke-CalendarRootSql "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$schema';"
if($exists -ne '0') { throw 'Fresh Calendar schema already exists; refusing reuse.' }
$oldEnv = @{}
$environment = @{
    KKIRI_CAL_DB=$schema; KKIRI_CAL_USER=$account; KKIRI_CAL_PASSWORD=$password;
    KKIRI_CAL_UUID='53ab727e-c847-11f1-9aaf-bad504fada53'; KKIRI_CAL_HOSTNAME='e15a46faca0b'
    KKIRI_CAL_JDBC_ZONE=$JdbcZone
}
foreach($key in $environment.Keys) { $oldEnv[$key]=[Environment]::GetEnvironmentVariable($key,'Process'); [Environment]::SetEnvironmentVariable($key,$environment[$key],'Process') }
try {
    Invoke-CalendarRootSql "CREATE DATABASE $schema CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER '$account'@'%' IDENTIFIED BY '$password'; GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES ON $schema.* TO '$account'@'%';" | Out-Null
    $javaArgs = @("-Duser.timezone=$JvmZone",'-cp',($classes+';'+$cp),'com.kkirikkiri.calendar.crawler.CalendarMysqlValidation',$repoRoot,$work)
    & java @javaArgs Initialize
    if($LASTEXITCODE -ne 0) { throw 'Calendar schema initialization failed; refusing tests.' }
    # Exercise the actual manual migration, only against the freshly created schema.
    $migrationPath = Join-Path $repoRoot 'backend/migrations/20261016_calendar_time_indexes.sql'
    $migration = ((Get-Content $migrationPath | Where-Object { !$_.TrimStart().StartsWith('--') }) -join "`n").Trim()
    if($migration -notmatch '^ALTER TABLE calendar_event\s+ADD INDEX idx_calendar_crawl_identity' -or ($migration.ToCharArray() | Where-Object {$_ -eq ';'}).Count -ne 1) { throw 'Unexpected Calendar migration; review required.' }
    Invoke-CalendarRootSql "ALTER TABLE $schema.calendar_event DROP INDEX idx_calendar_crawl_identity, DROP INDEX idx_calendar_period;" | Out-Null
    Invoke-CalendarRootSql ($migration.Replace('ALTER TABLE calendar_event',"ALTER TABLE $schema.calendar_event")) | Out-Null
    # Actual MySQL storage error after earlier inserts, not a mocked Repository exception.
    Invoke-CalendarRootSql @"
DELIMITER //
CREATE TRIGGER $schema.calendar_validation_fail BEFORE INSERT ON $schema.calendar_event FOR EACH ROW
BEGIN IF NEW.title = 'CALENDAR_DB_FAIL' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='controlled calendar storage failure'; END IF; END//
DELIMITER ;
REVOKE CREATE,ALTER,INDEX,REFERENCES ON $schema.* FROM '$account'@'%';
"@ | Out-Null
    if($Stage -eq 'All') {
        $runs = @(
            @{Name='former'; Stage='FormerTimezones'; Jvm='Asia/Seoul'; Jdbc='UTC'},
            @{Name='verify'; Stage='Verify'; Jvm='Asia/Seoul'; Jdbc='UTC'},
            @{Name='seoul-utc'; Stage='Matrix'; Jvm='Asia/Seoul'; Jdbc='UTC'},
            @{Name='seoul-seoul'; Stage='Matrix'; Jvm='Asia/Seoul'; Jdbc='Asia/Seoul'},
            @{Name='utc-utc'; Stage='Matrix'; Jvm='UTC'; Jdbc='UTC'}
        )
        $failedRuns = @()
        foreach($run in $runs) {
            $runOut = Join-Path $work $run.Name
            New-Item -ItemType Directory -Path $runOut | Out-Null
            [Environment]::SetEnvironmentVariable('KKIRI_CAL_JDBC_ZONE',$run.Jdbc,'Process')
            & java ("-Duser.timezone="+$run.Jvm) -cp ($classes+';'+$cp) com.kkirikkiri.calendar.crawler.CalendarMysqlValidation $repoRoot $runOut $run.Stage
            if($LASTEXITCODE -ne 0) { $failedRuns += $run.Name; Write-Output "Calendar $($run.Name) failed/incomplete; inspect $runOut" }
        }
        if($failedRuns.Count) { throw "Calendar verification failed/incomplete: $($failedRuns -join ', '); inspect $work" }
    } else {
        & java @javaArgs $Stage
        if($LASTEXITCODE -ne 0) { throw "Calendar MySQL verification failed/incomplete; inspect $work" }
    }
    Write-Output "Calendar verification complete; empty dedicated schema retained: $schema; account: $account"
} finally {
    foreach($key in $oldEnv.Keys) { [Environment]::SetEnvironmentVariable($key,$oldEnv[$key],'Process') }
    $password=$null; $environment=$null
}
