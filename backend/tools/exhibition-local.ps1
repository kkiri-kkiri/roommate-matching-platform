param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('Create','Bootstrap','Preview','Apply','TextPreview','TextApply','Start','Stop','Status','Backup','Verify','ApiCheck','BrowserCheck','OriginalSnapshot')]
    [string]$Action,
    [string]$ExpectedToken
)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
$backendRoot=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $backendRoot
$schema='kkirikkiri_exhibition_20261031'
$dbUser='kkiri_exh_20261031'
$stateRoot=Join-Path $backendRoot 'local-data/exhibition-20261031'
$logRoot=Join-Path $backendRoot 'build/exhibition-live'
$java='C:\Program Files\Java\jdk-21.0.11\bin\java.exe'
$mysql='C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$dump='C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe'
$legacy=Join-Path $stateRoot 'seed-users.properties'
$journal=Join-Path $stateRoot 'exhibition.json'
$secretFile=Join-Path $stateRoot 'credentials.clixml'
$serverFile=Join-Path $stateRoot 'server.json'
$url="jdbc:mysql://127.0.0.1:3306/$schema`?useSSL=false&allowPublicKeyRetrieval=true"
New-Item -ItemType Directory -Force $logRoot | Out-Null

function Read-DevelopmentConfiguration {
    $result=@{}
    Get-Content -Encoding UTF8 (Join-Path $backendRoot '.env') | ForEach-Object {
        $entry=[regex]::Match($_,'^\s*([A-Za-z_][A-Za-z0-9_.-]*)\s*=\s*(.*)$')
        if($entry.Success) { $result[$entry.Groups[1].Value]=$entry.Groups[2].Value }
    }
    if($result['DB_URL'] -notmatch '^jdbc:mysql://(?:localhost|127\.0\.0\.1):3306/kkirikkiri(?:\?.*)?$') {
        throw 'Development DB is not the previously verified local kkirikkiri schema; stopped.'
    }
    return $result
}
function New-RandomSecret {
    $bytes=New-Object byte[] 24
    $random=[Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($bytes) } finally { $random.Dispose() }
    return ([BitConverter]::ToString($bytes).Replace('-','').ToLowerInvariant()+'Aa1!')
}
function Get-Secrets {
    if(!(Test-Path -LiteralPath $secretFile)) { throw 'Exhibition credentials are missing. Do not reuse another DB state.' }
    return Import-Clixml -LiteralPath $secretFile
}
function Invoke-Native([string]$File,[string]$Arguments,[hashtable]$Environment=@{},[string]$InputText='') {
    $info=[Diagnostics.ProcessStartInfo]::new()
    $info.FileName=$File; $info.Arguments=$Arguments; $info.WorkingDirectory=$backendRoot
    $info.UseShellExecute=$false
    $info.RedirectStandardInput=$true; $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
    $info.StandardOutputEncoding=[Text.UTF8Encoding]::new($false)
    $info.StandardErrorEncoding=[Text.UTF8Encoding]::new($false)
    foreach($key in $Environment.Keys) { $info.EnvironmentVariables[$key]=$Environment[$key] }
    $process=[Diagnostics.Process]::Start($info)
    $outTask=$process.StandardOutput.ReadToEndAsync(); $errTask=$process.StandardError.ReadToEndAsync()
    if($InputText) { $bytes=[Text.Encoding]::UTF8.GetBytes($InputText); $process.StandardInput.BaseStream.Write($bytes,0,$bytes.Length) }
    $process.StandardInput.Close(); $process.WaitForExit()
    return [pscustomobject]@{ExitCode=$process.ExitCode;Output=$outTask.Result;Error=$errTask.Result}
}
function Invoke-Sql([string]$Sql,[string]$Database=$schema,[switch]$Admin,[switch]$AllowFailure) {
    if($Admin) { $configuration=Read-DevelopmentConfiguration; $name=$configuration['DB_USERNAME']; $password=$configuration['DB_PASSWORD'] }
    else { $secrets=Get-Secrets; $name=$secrets.Database.UserName; $password=$secrets.Database.GetNetworkCredential().Password }
    if($Database -notin @($schema,'kkirikkiri','information_schema')) { throw 'Unrecognized DB target' }
    $result=Invoke-Native $mysql "--protocol=TCP --host=127.0.0.1 --port=3306 --user=$name --database=$Database --batch --raw --default-character-set=utf8mb4" @{'MYSQL_PWD'=$password} $Sql
    if($result.ExitCode -ne 0 -and !$AllowFailure) { throw ('MySQL step failed: '+$result.Error.Replace($password,'<redacted>')) }
    return $result
}
function Child-Environment([bool]$Bootstrap) {
    $secrets=Get-Secrets
    $values=@{
        SPRING_PROFILES_ACTIVE='local'; SPRING_PROFILES_INCLUDE=''; SPRING_PROFILES_DEFAULT='local'; SPRING_APPLICATION_JSON='';
        SPRING_CONFIG_LOCATION=''; SPRING_CONFIG_ADDITIONAL_LOCATION='';
        APP_SEED_MODE='NONE'; DB_URL=$url; DB_USERNAME=$secrets.Database.UserName; DB_PASSWORD=$secrets.Database.GetNetworkCredential().Password;
        SPRING_DATASOURCE_URL=$url; SPRING_DATASOURCE_USERNAME=$secrets.Database.UserName; SPRING_DATASOURCE_PASSWORD=$secrets.Database.GetNetworkCredential().Password;
        JWT_SECRET=[Net.NetworkCredential]::new('', $secrets.Jwt).Password;
        CALENDAR_CRAWL_ON_STARTUP='false'; EXHIBITION_ENVIRONMENT='local-exhibition'; EXHIBITION_DB_SCHEMA=$schema;
        EXHIBITION_DB_URL=$url; EXHIBITION_DB_USERNAME=$secrets.Database.UserName; EXHIBITION_DB_PASSWORD=$secrets.Database.GetNetworkCredential().Password;
        EXHIBITION_NEW_USER_PASSWORD=$secrets.Demo.GetNetworkCredential().Password;
        EXHIBITION_WRITES=''; EXHIBITION_OFFLINE=''; APP_SEED_NEW_USER_PASSWORD=''
    }
    if($Bootstrap) { $values.APP_SEED_MODE='CREATE'; $values.APP_SEED_NEW_USER_PASSWORD=$secrets.Demo.GetNetworkCredential().Password }
    return $values
}
function Invoke-Tool([string]$Mode,[string]$Token='',[bool]$TextOnly=$false) {
    $classpath=(Get-Content -Raw -Encoding UTF8 build/exhibition/classpath.txt).Trim()
    $child=Child-Environment $false
    $args='-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "'+$classpath+'" exhibition.ExhibitionTool '+$Mode+' --anchor=2026-10-31 --reports "--legacy-manifest='+$legacy+'" "--journal='+$journal+'"'
    if($TextOnly) { $args+=' --refresh-text' }
    if($Mode -eq 'apply') {
        if($Token -notmatch '^[a-f0-9]{64}$') { throw 'Apply requires the exact reviewed token' }
        if(Test-Path -LiteralPath $serverFile) { throw 'Stop the exhibition backend before apply' }
        $child.EXHIBITION_WRITES='ALLOW_REVIEWED_PLAN'; $child.EXHIBITION_OFFLINE='CONFIRMED'
        $args+=' --expect='+$Token
    }
    $result=Invoke-Native $java $args $child
    ($result.Output+$result.Error) | Set-Content -Encoding UTF8 (Join-Path $logRoot ($Mode+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'.log'))
    if($TextOnly -and $Mode -eq 'preview') { $result.Output | Set-Content -Encoding UTF8 (Join-Path $logRoot 'text-preview-latest.log') }
    Write-Output $result.Output
    if($result.ExitCode -ne 0) { throw ('Exhibition '+$Mode+' failed; examine its log. Do not reset the journal or rerun an incomplete apply.') }
}
function Start-Exhibition([bool]$Bootstrap) {
    if(Test-Path -LiteralPath $serverFile) { throw 'A recorded server exists; use Status/Stop first' }
    $port=if($Bootstrap){18081}else{8080}
    $listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$port)
    try { $listener.Start() } finally { $listener.Stop() }
    if($Bootstrap) {
        $count=(Invoke-Sql "SELECT COUNT(*) AS n FROM information_schema.tables WHERE table_schema=DATABASE();").Output.Trim().Split("`n")[-1].Trim()
        if($count -ne '0' -or (Test-Path -LiteralPath $legacy) -or (Test-Path -LiteralPath $journal)) { throw 'Bootstrap is only allowed on the newly created empty schema and unused provenance paths' }
    }
    $child=Child-Environment $Bootstrap
    $before=@{}
    foreach($key in $child.Keys) { $before[$key]=[Environment]::GetEnvironmentVariable($key,'Process'); [Environment]::SetEnvironmentVariable($key,$child[$key],'Process') }
    $ddl=if($Bootstrap){'update'}else{'validate'}
    $sql=if($Bootstrap){'always'}else{'never'}
    $defer=if($Bootstrap){'true'}else{'false'}
    $args=@('-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','-jar','build/libs/backend-0.0.1-SNAPSHOT.jar',
        '--spring.profiles.active=local','--spring.application.name=backend-exhibition-20261031',"--server.port=$port",'--server.address=127.0.0.1',
        "--spring.datasource.url=$url","--spring.datasource.username=$dbUser",("--app.seed.mode="+$child.APP_SEED_MODE),
        "--spring.jpa.hibernate.ddl-auto=$ddl","--spring.sql.init.mode=$sql","--spring.jpa.defer-datasource-initialization=$defer",
        "--app.seed.manifest=$legacy",'--app.calendar.crawl-on-startup=false',"--app.community.image-directory=$stateRoot/uploads",
        '--logging.level.root=INFO','--logging.level.org.hibernate.SQL=OFF','--logging.level.org.hibernate.orm.jdbc.bind=OFF',
        '--logging.level.org.springframework.jdbc=INFO','--spring.jpa.show-sql=false','--debug=false','--trace=false',
        '--logging.level.org.springframework.boot=INFO')
    $stdout=Join-Path $logRoot ('server-'+$port+'.log'); $stderr=Join-Path $logRoot ('server-'+$port+'.err.log')
    try {
        $process=Start-Process -FilePath $java -ArgumentList $args -WorkingDirectory $backendRoot -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
        [pscustomobject]@{pid=$process.Id;started=$process.StartTime.ToUniversalTime().ToString('O');schema=$schema;port=$port;bootstrap=$Bootstrap} | ConvertTo-Json | Set-Content -Encoding UTF8 $serverFile
    } finally { foreach($key in $before.Keys) { [Environment]::SetEnvironmentVariable($key,$before[$key],'Process') } }
    Write-Output ('Started exhibition backend PID='+$process.Id+' schema='+$schema+' port='+$port+' seed='+$child.APP_SEED_MODE)
}
function Stop-Exhibition {
    if(!(Test-Path -LiteralPath $serverFile)) { throw 'No exhibition server recorded; no process stopped' }
    $record=Get-Content -Raw -Encoding UTF8 $serverFile | ConvertFrom-Json
    $process=Get-Process -Id $record.pid -ErrorAction SilentlyContinue
    if($process) {
        $command=Get-CimInstance Win32_Process -Filter ('ProcessId='+$record.pid)
        if($command.CommandLine -notlike '*--spring.application.name=backend-exhibition-20261031*' -or $process.StartTime.ToUniversalTime().ToString('O') -ne $record.started) { throw 'PID ownership mismatch; no process stopped' }
        Stop-Process -Id $record.pid
        $process.WaitForExit()
    }
    Remove-Item -LiteralPath $serverFile
    Write-Output ('Stopped recorded exhibition backend PID='+$record.pid)
}

switch($Action) {
 'Create' {
    if((Test-Path -LiteralPath $stateRoot)) { throw 'Exhibition state directory already exists; creation will not adopt or overwrite it' }
    $existing=Invoke-Sql "SELECT COUNT(*) AS n FROM information_schema.schemata WHERE schema_name='$schema'; SELECT COUNT(*) AS n FROM mysql.user WHERE user='$dbUser'; SELECT @@partial_revokes AS partial_revokes;" 'information_schema' -Admin
    $lines=@($existing.Output.Trim().Split("`n") | ForEach-Object {$_.Trim()})
    if($lines[1] -ne '0' -or $lines[3] -ne '0') { throw 'Exhibition DB or account already exists; no existing resource will be adopted' }
    New-Item -ItemType Directory -Path $stateRoot | Out-Null
    $dbPassword=New-RandomSecret; $demoPassword=New-RandomSecret; $jwtBytes=New-Object byte[] 48
    $random=[Security.Cryptography.RandomNumberGenerator]::Create(); try{$random.GetBytes($jwtBytes)}finally{$random.Dispose()}
    $secrets=[pscustomobject]@{
        Database=[Management.Automation.PSCredential]::new($dbUser,(ConvertTo-SecureString $dbPassword -AsPlainText -Force));
        Demo=[Management.Automation.PSCredential]::new('seed_user01@hknu.ac.kr',(ConvertTo-SecureString $demoPassword -AsPlainText -Force));
        Jwt=(ConvertTo-SecureString ([Convert]::ToBase64String($jwtBytes)) -AsPlainText -Force)
    }
    $secrets | Export-Clixml -LiteralPath $secretFile
    $grantSchema=if($lines[5] -eq '0'){$schema.Replace('_','\_')}else{$schema}
    $create="CREATE DATABASE ``$schema`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER '$dbUser'@'127.0.0.1' IDENTIFIED BY '$dbPassword'; GRANT ALL PRIVILEGES ON ``$grantSchema``.* TO '$dbUser'@'127.0.0.1';"
    $null=Invoke-Sql $create 'information_schema' -Admin
    $null=Invoke-Sql 'SELECT DATABASE();'
    $denied=Invoke-Sql 'SELECT COUNT(*) FROM kkirikkiri.user;' -AllowFailure
    if($denied.ExitCode -eq 0) { throw 'Unexpected old DB access; do not continue' }
    Write-Output ('Created isolated schema '+$schema+' and scoped account. Old DB SELECT access denied as expected. Credentials stored with Windows DPAPI.')
 }
 'Bootstrap' { Start-Exhibition $true }
 'Preview' { Invoke-Tool 'preview' }
 'Apply' { Invoke-Tool 'apply' $ExpectedToken }
 'TextPreview' { Invoke-Tool 'preview' '' $true }
 'TextApply' { Invoke-Tool 'apply' $ExpectedToken $true }
 'Start' { Start-Exhibition $false }
 'Stop' { Stop-Exhibition }
 'Status' {
    if(Test-Path -LiteralPath $serverFile) { Get-Content -Encoding UTF8 $serverFile } else { Write-Output 'Exhibition backend stopped' }
    Write-Output (Invoke-Sql 'SELECT DATABASE() AS schema_name,VERSION() AS mysql_version;').Output
 }
 'Backup' {
    if(Test-Path -LiteralPath $serverFile) { throw 'Stop the exhibition backend to back up DB and provenance at the same point' }
    $backup=Join-Path $stateRoot ('backups/'+(Get-Date -Format 'yyyyMMdd-HHmmss'))
    New-Item -ItemType Directory -Path $backup -Force | Out-Null
    $secrets=Get-Secrets; $outputFile=Join-Path $backup 'exhibition.sql'
    $args="--protocol=TCP --host=127.0.0.1 --port=3306 --user=$dbUser --single-transaction --no-tablespaces --set-gtid-purged=OFF --routines --triggers --events --result-file=$outputFile $schema"
    $result=Invoke-Native $dump $args @{'MYSQL_PWD'=$secrets.Database.GetNetworkCredential().Password}
    if($result.ExitCode -ne 0) { throw 'Exhibition dump failed; no apply should proceed' }
    foreach($file in @($legacy,$journal,$secretFile)) { if(Test-Path -LiteralPath $file) { Copy-Item -LiteralPath $file -Destination $backup } else { ('ABSENT: '+[IO.Path]::GetFileName($file)) | Add-Content -Encoding UTF8 (Join-Path $backup 'absent-sources.txt') } }
    Get-ChildItem -File -LiteralPath $backup | Get-FileHash -Algorithm SHA256 | Select-Object Hash,Path | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $backup 'hashes.json')
    Write-Output ('Backed up exhibition DB and provenance: '+$backup)
 }
 'OriginalSnapshot' {
    $configuration=Read-DevelopmentConfiguration
    $result=Invoke-Native $dump "--protocol=TCP --host=127.0.0.1 --port=3306 --user=$($configuration['DB_USERNAME']) --single-transaction --no-tablespaces --set-gtid-purged=OFF --no-create-info --skip-comments --skip-extended-insert --skip-add-locks --skip-lock-tables --order-by-primary --hex-blob --compact kkirikkiri" @{'MYSQL_PWD'=$configuration['DB_PASSWORD']}
    if($result.ExitCode -ne 0) { throw 'Read-only original DB snapshot failed' }
    $sha=[Security.Cryptography.SHA256]::Create()
    try { $fingerprint=[BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($result.Output))).Replace('-','').ToLowerInvariant() } finally { $sha.Dispose() }
    $counts=(Invoke-Sql 'START TRANSACTION READ ONLY; SELECT (SELECT COUNT(*) FROM user) users,(SELECT COUNT(*) FROM community_post) posts,(SELECT COUNT(*) FROM chat_room) rooms,(SELECT COUNT(*) FROM chat_message) messages; ROLLBACK;' 'kkirikkiri' -Admin).Output
    Write-Output ('Original database data SHA256='+$fingerprint); Write-Output $counts
 }
 'Verify' {
    $query=Get-Content -Raw -Encoding UTF8 (Join-Path $PSScriptRoot 'exhibition-counts.sql')
    $result=Invoke-Sql $query
    $result.Output | Set-Content -Encoding UTF8 (Join-Path $logRoot 'database-verification.txt')
    Write-Output $result.Output
 }
 {$_ -in 'ApiCheck','BrowserCheck'} {
    if(!(Test-Path -LiteralPath $serverFile)) { throw 'No exhibition backend recorded; no HTTP requests sent' }
    $record=Get-Content -Raw -Encoding UTF8 $serverFile | ConvertFrom-Json
    $process=Get-Process -Id $record.pid
    $command=Get-CimInstance Win32_Process -Filter ('ProcessId='+$record.pid)
    if($record.schema -ne $schema -or $record.port -ne 8080 -or $record.bootstrap -or
       $command.CommandLine -notlike '*--spring.application.name=backend-exhibition-20261031*' -or
       $process.StartTime.ToUniversalTime().ToString('O') -ne $record.started) { throw 'Backend ownership mismatch; no HTTP requests sent' }
    $listener=Get-NetTCPConnection -State Listen -LocalPort 8080
    if(@($listener | Where-Object {$_.OwningProcess -ne $record.pid}).Count -gt 0) { throw 'Port ownership mismatch; no HTTP requests sent' }
    $secrets=Get-Secrets
    $verificationScript=if($Action -eq 'ApiCheck'){'tools/exhibition-api-check.cjs'}else{'tools/exhibition-browser-check.cjs'}
    $result=Invoke-Native 'C:\Program Files\nodejs\node.exe' $verificationScript @{
        EXHIBITION_ENVIRONMENT='local-exhibition'; EXHIBITION_API_CONFIRMED_SCHEMA=$schema;
        EXHIBITION_DEMO_PASSWORD=$secrets.Demo.GetNetworkCredential().Password
    }
    Write-Output $result.Output
    if($result.ExitCode -ne 0) { throw ('Exhibition verification failed: '+$result.Error) }
 }
}
