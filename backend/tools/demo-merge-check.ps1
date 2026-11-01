param(
    [Parameter(Mandatory=$true)][string]$Schema,
    [Parameter(Mandatory=$true)][string]$MergeSchema,
    [switch]$UseLocalConfiguration
)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
if($Schema -cnotmatch '^kkirikkiri_demo50_runtime_[a-zA-Z0-9_]+$' -or $MergeSchema -cnotmatch '^kkirikkiri_demo50_merge_[a-zA-Z0-9_]+$'){throw 'Only a restored runtime copy of a completed isolated merge is permitted.'}
$backend=Split-Path -Parent $PSScriptRoot
$state=Join-Path $backend ('local-data/demo50/'+$MergeSchema)
$logs=Join-Path $backend 'build/demo50/merge-browser'
$receipt=Get-Content -Raw -Encoding UTF8 (Join-Path $state 'merge.json') | ConvertFrom-Json
if($receipt.status -ne 'COMPLETE' -or !(Test-Path (Join-Path $state 'ui-projection.json'))){throw 'Verify the completed merge first.'}
if(!(Test-Path (Join-Path $backend 'build/demo-merge-ui/index.html'))){throw 'Build the frontend for http://127.0.0.1:18082/api into backend/build/demo-merge-ui first.'}
$username=$env:DEMO_DB_USERNAME;$password=$env:DEMO_DB_PASSWORD
if($UseLocalConfiguration){
    $cfg=@{};Get-Content -Encoding UTF8 (Join-Path $backend '.env') | ForEach-Object {if($_ -match '^\s*([A-Za-z_][A-Za-z0-9_.-]*)\s*=\s*(.*)$'){$cfg[$matches[1]]=$matches[2]}}
    if($cfg.DB_URL -notmatch '^jdbc:mysql://(?:localhost|127\.0\.0\.1):3306/kkirikkiri(?:\?.*)?$'){throw 'Unexpected local configuration.'}
    $username=$cfg.DB_USERNAME;$password=$cfg.DB_PASSWORD
}
if(!$username -or !$password){throw 'Private MySQL credentials required.'}
$credentialFile=(Get-Content -Raw -Encoding ASCII (Join-Path $state 'credential-file.txt')).Trim()
if($credentialFile -notmatch '^new-user-login-[a-f0-9]+\.clixml$'){throw 'Invalid private credential file name.'}
$credential=Import-Clixml -LiteralPath (Join-Path $state $credentialFile);$newPassword=$credential.GetNetworkCredential().Password
$bytes=New-Object byte[] 32;$rng=[Security.Cryptography.RandomNumberGenerator]::Create();try{$rng.GetBytes($bytes)}finally{$rng.Dispose()}
$jwt=[Convert]::ToBase64String($bytes);$privatePasswords=@($newPassword,$jwt);$sourcePassword=$password
New-Item -ItemType Directory -Path $logs -Force | Out-Null
foreach($port in @(18082,13000)){$probe=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,$port);try{$probe.Start()}finally{$probe.Stop()}}
. (Join-Path $PSScriptRoot 'demo-native.ps1')
$classpath=((Get-Content -Raw -Encoding UTF8 (Join-Path $backend 'build/demo-data/classpath.txt')).Trim().Split(';') | Where-Object {$_ -notmatch '[/\\]classes[/\\]java[/\\]exhibition$'}) -join ';'
$url="jdbc:mysql://127.0.0.1:3306/$Schema`?useSSL=false&allowPublicKeyRetrieval=true"
$java=if($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{(Get-Command java).Source}
# Resolve Oracle's javapath shim before launching, so ownership refers to the actual JVM.
$probeInfo=[Diagnostics.ProcessStartInfo]::new();$probeInfo.FileName=$java;$probeInfo.Arguments='-XshowSettings:properties -version'
$probeInfo.UseShellExecute=$false;$probeInfo.CreateNoWindow=$true;$probeInfo.RedirectStandardOutput=$true;$probeInfo.RedirectStandardError=$true
$javaProbe=[Diagnostics.Process]::Start($probeInfo);$probeOut=$javaProbe.StandardOutput.ReadToEndAsync();$probeErr=$javaProbe.StandardError.ReadToEndAsync();$javaProbe.WaitForExit()
$resolvedHome=@(($probeOut.Result+$probeErr.Result) -split '\r?\n' | ForEach-Object {if($_ -match '^\s*java\.home = (.+)$'){$matches[1]}})
if($javaProbe.ExitCode -ne 0 -or $resolvedHome.Count -ne 1 -or !(Test-Path (Join-Path $resolvedHome[0] 'bin/java.exe'))){throw 'Cannot resolve the actual Java installation.'}
$info=[Diagnostics.ProcessStartInfo]::new();$info.FileName=Join-Path $resolvedHome[0] 'bin/java.exe'
$info.WorkingDirectory=$backend;$info.UseShellExecute=$false;$info.CreateNoWindow=$true
$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
$info.StandardOutputEncoding=[Text.UTF8Encoding]::new($false);$info.StandardErrorEncoding=[Text.UTF8Encoding]::new($false)
$info.Arguments='-Dfile.encoding=UTF-8 -cp "'+$classpath+'" com.kkirikkiri.BackendApplication --server.address=127.0.0.1 --server.port=18082 --spring.profiles.active=local --spring.datasource.url='+$url+' --spring.jpa.hibernate.ddl-auto=validate --spring.sql.init.mode=never --spring.jpa.defer-datasource-initialization=false --app.seed.mode=NONE --app.calendar.crawl-on-startup=false --app.community.image-cleanup.enabled=false --app.cors.allowed-origins=http://127.0.0.1:13000 --logging.level.root=INFO --logging.level.org.springframework=INFO --logging.level.org.hibernate=OFF'
$environment=@{SPRING_PROFILES_ACTIVE='local';SPRING_PROFILES_INCLUDE='';SPRING_APPLICATION_JSON='';DB_URL=$url;SPRING_DATASOURCE_URL=$url;DB_USERNAME=$username;DB_PASSWORD=$password;JWT_SECRET=$jwt;AI_API_KEY='local-validation';AI_BASE_URL='http://127.0.0.1:1';AI_MODEL='local-validation';SMTP_HOST='127.0.0.1';SMTP_PORT='1';SMTP_USERNAME='';SMTP_PASSWORD='';CALENDAR_CRAWL_ON_STARTUP='false'}
foreach($key in $environment.Keys){$info.EnvironmentVariables[$key]=$environment[$key]}
$server=$null;$http=$null
try{
    $server=[Diagnostics.Process]::Start($info);$started=$server.StartTime
    $stdout=$server.StandardOutput.ReadToEndAsync();$stderr=$server.StandardError.ReadToEndAsync()
    [pscustomobject]@{pid=$server.Id;started=$started.ToString('o');main='com.kkirikkiri.BackendApplication';schema=$Schema;port=18082} | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $logs 'owned-server.json')
    Add-Type -AssemblyName System.Net.Http;$http=[Net.Http.HttpClient]::new();$http.Timeout=[TimeSpan]::FromSeconds(2)
    $ready=$false
    for($attempt=0;$attempt -lt 60;$attempt++){
        if($server.HasExited){throw 'Owned normal BackendApplication exited before readiness.'}
        $response=$null
        try{$response=$http.GetAsync('http://127.0.0.1:18082/api/roommates?page=0&size=100').GetAwaiter().GetResult();if([int]$response.StatusCode -eq 200){$ready=$true;break}}catch{}finally{if($response){$response.Dispose()}}
        Start-Sleep -Seconds 1
    }
    if(!$ready){throw 'Owned BackendApplication readiness failed.'}
    $node=(Get-Command node).Source
    $result=Invoke-Native $node @((Join-Path $backend '../frontend/e2e/demo-merge-check.cjs')) @{
        DEMO_TEST_SCHEMA=$Schema;DEMO_MERGE_STATE=$state;DEMO_NEW_LOGIN_PASSWORD=$newPassword;DEMO_TEST_JWT_SECRET=$jwt
    }
    Write-Output $result
}finally{
    # Stop only the process created above, before disposing the HTTP pool or waiting for output EOF.
    if($server -and !$server.HasExited -and $server.StartTime -eq $started){$server.Kill();if(!$server.WaitForExit(10000)){throw 'Owned server did not exit in time.'}}
    if($server){
        if($stdout.Wait(5000)){$stdout.Result | Set-Content -Encoding UTF8 (Join-Path $logs 'backend.log')}
        if($stderr.Wait(5000)){$stderr.Result | Set-Content -Encoding UTF8 (Join-Path $logs 'backend.err.log')}
    }
    if($http){$http.Dispose()}
}
