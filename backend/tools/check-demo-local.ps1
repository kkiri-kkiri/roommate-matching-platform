param([Parameter(Mandatory=$true)][ValidatePattern('^[0-9]{8}_[a-zA-Z0-9_]{1,16}$')][string]$Run)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
$backend=Split-Path -Parent $PSScriptRoot
$state=Join-Path $backend ('local-data/demo50/local-apply-'+$Run)
$review=Join-Path $backend 'local-data/demo50/kkirikkiri_demo50_merge_20261101'
$logs=Join-Path $backend ('build/demo50/local-browser-local-apply-'+$Run)
$record=Get-Content -Raw -Encoding UTF8 (Join-Path $state 'apply.json') | ConvertFrom-Json
if($record.status -ne 'DB_APPLIED' -or $record.database -ne 'localhost:3306/kkirikkiri' -or !$record.userAuthorizedPersonalLocal -or $record.sha256 -ne '7977fc06f3d45f846c2e38005cad475e17450d29cce1fb110a9c6ac4c2f1f737'){throw 'An explicitly approved and verified local apply receipt is required.'}
$cfg=@{};Get-Content -Encoding UTF8 (Join-Path $backend '.env') | ForEach-Object {if($_ -match '^\s*([A-Za-z_][A-Za-z0-9_.-]*)\s*=\s*(.*)$'){$cfg[$matches[1]]=$matches[2]}}
if($cfg.DB_URL -notmatch '^jdbc:mysql://(?:localhost|127\.0\.0\.1):3306/kkirikkiri(?:\?.*)?$' -or !$cfg.JWT_SECRET){throw 'Unexpected private local environment.'}
$username=$cfg.DB_USERNAME;$password=$cfg.DB_PASSWORD;$sourcePassword=$password
$credentialFile=(Get-Content -Raw -Encoding ASCII (Join-Path $review 'credential-file.txt')).Trim()
if($credentialFile -cnotmatch '^new-user-login-[a-f0-9]+\.clixml$'){throw 'Unexpected private credential pointer.'}
$credential=Import-Clixml -LiteralPath (Join-Path $review $credentialFile);$newPassword=$credential.GetNetworkCredential().Password
$jwt=$cfg.JWT_SECRET;$privatePasswords=@($newPassword,$jwt)
$workspace=[xml]::new();$workspace.Load((Join-Path $backend '.idea/workspace.xml'))
$config=$workspace.SelectSingleNode('//configuration[@name="BackendApplication"]')
if(!$config -or $config.SelectSingleNode('./option[@name="SPRING_BOOT_MAIN_CLASS"]').GetAttribute('value') -ne 'com.kkirikkiri.BackendApplication' -or $config.SelectSingleNode('./option[@name="ACTIVE_PROFILES"]').GetAttribute('value') -ne 'local'){throw 'Unexpected existing IntelliJ configuration.'}
$flags='--spring.jpa.hibernate.ddl-auto=validate --spring.sql.init.mode=never --spring.jpa.defer-datasource-initialization=false --app.seed.mode=NONE --app.calendar.crawl-on-startup=false --app.community.image-cleanup.enabled=false --logging.level.root=INFO --logging.level.org.springframework.web=INFO --logging.level.org.springframework.security=INFO --logging.level.org.hibernate.SQL=OFF --logging.level.org.hibernate.orm.jdbc.bind=OFF --spring.jpa.show-sql=false'
if($config.SelectSingleNode('./option[@name="PROGRAM_PARAMETERS"]').GetAttribute('value') -ne $flags){throw 'Existing IntelliJ run settings differ from reviewed safe options.'}
if(!(Test-Path (Join-Path $backend 'build/demo-local-ui/index.html'))){throw 'Build the local frontend for http://127.0.0.1:8080/api first.'}
foreach($port in @(8080,13000)){$probe=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$port);try{$probe.Start()}finally{$probe.Stop()}}
New-Item -ItemType Directory -Path $logs -Force | Out-Null
. (Join-Path $PSScriptRoot 'demo-native.ps1')
$classpath=((Get-Content -Raw -Encoding UTF8 (Join-Path $backend 'build/demo-data/classpath.txt')).Trim().Split(';') | Where-Object {$_ -notmatch '[/\\]classes[/\\]java[/\\]exhibition$'}) -join ';'
$java=Join-Path $env:JAVA_HOME 'bin/java.exe';if(!(Test-Path -LiteralPath $java)){throw 'Actual JDK 21 required.'}
$info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=$java;$info.WorkingDirectory=$backend;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true;$info.StandardOutputEncoding=[Text.UTF8Encoding]::new($false);$info.StandardErrorEncoding=[Text.UTF8Encoding]::new($false)
$info.Arguments='-Dfile.encoding=UTF-8 -cp "'+$classpath+'" com.kkirikkiri.BackendApplication --spring.profiles.active=local '+$flags+' --server.address=127.0.0.1 --server.port=8080 --app.cors.allowed-origins=http://127.0.0.1:13000 --logging.level.org.springframework=INFO --logging.level.org.hibernate=OFF'
$environment=@{SPRING_PROFILES_ACTIVE='local';SPRING_PROFILES_INCLUDE='';SPRING_APPLICATION_JSON='';DB_URL=$cfg.DB_URL;SPRING_DATASOURCE_URL=$cfg.DB_URL;DB_USERNAME=$username;DB_PASSWORD=$password;JWT_SECRET=$jwt;APP_SEED_MODE='NONE';CALENDAR_CRAWL_ON_STARTUP='false'}
foreach($key in $environment.Keys){$info.EnvironmentVariables[$key]=$environment[$key]}
$server=$null;$http=$null
try{
    $server=[Diagnostics.Process]::Start($info);$started=$server.StartTime
    $stdout=$server.StandardOutput.ReadToEndAsync();$stderr=$server.StandardError.ReadToEndAsync()
    [pscustomobject]@{pid=$server.Id;started=$started.ToString('o');main='com.kkirikkiri.BackendApplication';schema='kkirikkiri';port=8080} | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $logs 'owned-server.json')
    Add-Type -AssemblyName System.Net.Http;$http=[Net.Http.HttpClient]::new();$http.Timeout=[TimeSpan]::FromSeconds(2);$ready=$false
    for($attempt=0;$attempt -lt 60;$attempt++){
        if($server.HasExited){throw 'Owned BackendApplication exited before readiness.'};$response=$null
        try{$response=$http.GetAsync('http://127.0.0.1:8080/api/roommates?page=0&size=100').GetAwaiter().GetResult();if([int]$response.StatusCode -eq 200){$ready=$true;break}}catch{}finally{if($response){$response.Dispose()}}
        Start-Sleep -Seconds 1
    }
    if(!$ready){throw 'Normal BackendApplication readiness failed.'}
    Write-Output (Invoke-Native (Get-Command node).Source @((Join-Path $backend '../frontend/e2e/demo-merge-check.cjs')) @{
        DEMO_TEST_SCHEMA='kkirikkiri';DEMO_MERGE_STATE=$review;DEMO_LOCAL_APPLY_STATE=$state;DEMO_NEW_LOGIN_PASSWORD=$newPassword;DEMO_TEST_JWT_SECRET=$jwt
    })
}finally{
    if($server -and !$server.HasExited -and $server.StartTime -eq $started){$server.Kill();if(!$server.WaitForExit(10000)){throw 'Owned verification JVM did not exit.'}}
    foreach($item in @(@($stdout,'backend.log'),@($stderr,'backend.err.log'))){if($item[0] -and $item[0].Wait(5000)){$safe=$item[0].Result;foreach($secret in @($password,$newPassword,$jwt)){if($secret){$safe=$safe.Replace($secret,'<redacted>')}};$safe | Set-Content -Encoding UTF8 (Join-Path $logs $item[1])}}
    if($http){$http.Dispose()}
}
