param([ValidateSet('Prepare','InitAuth','AuthService','AuthConstraints','UserService','UserVerify','UserAdditional','SurveyService','AuthHttp','UserHttp','UserSecurity','Audit')] [string] $Stage='Prepare')
$ErrorActionPreference='Stop'
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$work=Join-Path $repo 'backend/build/auth-user-survey-mysql'
$manifest=Get-Content -Raw -Encoding UTF8 (Join-Path $work 'environment.json') | ConvertFrom-Json
if($manifest.container -ne 'kkiri-aus-mysql-20261021' -or $manifest.port -ne 13326 -or $manifest.version -ne '8.0.46' -or $manifest.targets.Count -ne 5){throw 'Unapproved manifest'}
$jar=Join-Path $repo 'backend/build/libs/backend-0.0.1-SNAPSHOT.jar'
$lib=Join-Path $repo ('backend/build/user-mysql-multi-instance/lib-'+(Get-FileHash $jar -Algorithm SHA256).Hash)
if(!(Test-Path -LiteralPath $lib)){throw 'Run existing User HTTP PrepareOnly first to extract current JAR dependencies'}
$classes=Join-Path $work 'classes';New-Item -ItemType Directory -Force -Path $classes | Out-Null
$cp=(Join-Path $repo 'backend/build/classes/java/main')+';'+(Join-Path $repo 'backend/build/resources/main')+';'+$lib+'/*'
if($Stage -eq 'Prepare'){
    & node (Join-Path $PSScriptRoot 'prepare.cjs');if($LASTEXITCODE -ne 0){throw 'Source adaptation failed'}
    $sources=@(Get-ChildItem (Join-Path $work 'sources') -Filter '*.java' | Select-Object -ExpandProperty FullName)
    $savedPreference=$ErrorActionPreference
    try{$ErrorActionPreference='Continue'; & javac -encoding UTF-8 -cp $cp -d $classes $sources; $compileExit=$LASTEXITCODE}finally{$ErrorActionPreference=$savedPreference}
    if($compileExit -ne 0){throw 'Approved target verifier compilation failed'}
    Write-Output 'Approved-target validation sources compiled without DB access.';return
}
$metadata=@(& docker inspect $manifest.container | ConvertFrom-Json)[0]
if($LASTEXITCODE -ne 0 -or !$metadata.State.Running -or $metadata.Config.Image -ne $manifest.image -or $metadata.Config.Hostname -ne $manifest.hostname -or $metadata.Config.Labels.'kkiri.validation' -ne 'auth-user-survey-20261021'){throw 'Approved container identity mismatch'}
$bindings=@($metadata.NetworkSettings.Ports.'3306/tcp')
if($bindings.Count -ne 1 -or $bindings[0].HostIp -ne '127.0.0.1' -or $bindings[0].HostPort -ne '13326'){throw 'Approved loopback binding mismatch'}
$ready=$false
for($attempt=0;$attempt -lt 30;$attempt++){
    $probe=@('SELECT 1;' | & docker exec -i $manifest.container sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=SOCKET -uroot --batch --skip-column-names 2>/dev/null')
    if($LASTEXITCODE -eq 0 -and $probe.Count -eq 1 -and $probe[0] -eq '1'){$ready=$true;break}
    Start-Sleep -Seconds 1
}
if(!$ready){throw 'Approved MySQL server not ready; no validation started'}
$uuid=@(& docker exec $manifest.container cat /var/lib/mysql/auto.cnf | Where-Object {$_ -match '^server-uuid='})
if($LASTEXITCODE -ne 0 -or $uuid.Count -ne 1 -or $uuid[0].Substring(12).Trim() -ne $manifest.uuid){throw 'Approved server UUID mismatch'}
$rootSecret=@($metadata.Config.Env | Where-Object {$_.StartsWith('MYSQL_ROOT_PASSWORD=')})[0].Substring(20);$metadata=$null
$channels=@('KKIRI_AUTH_TEST_DB_PASSWORD','KKIRI_USER_TEST_DB_PASSWORD','KKIRI_SURVEY_TEST_PASSWORD','AUS_AUTH_HTTP_PASSWORD','AUS_USER_HTTP_PASSWORD')
$values=@{AUS_UUID=$manifest.uuid;KKIRI_USER_TEST_UUID=$manifest.uuid;KKIRI_USER_TEST_HOSTNAME=$manifest.hostname;KKIRI_USER_TEST_CONTAINER_ID=$manifest.container;KKIRI_SURVEY_TEST_UUID=$manifest.uuid;KKIRI_SURVEY_TEST_HOSTNAME=$manifest.hostname}
for($i=0;$i -lt 5;$i++){
    $sha=[Security.Cryptography.SHA256]::Create()
    try{$values[$channels[$i]]=([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($rootSecret+':'+$manifest.targets[$i].user)))).Replace('-','').ToLowerInvariant()}finally{$sha.Dispose()}
}
$rootSecret=$null
$values.KKIRI_UM_ORIGINAL_PASSWORD=$values.KKIRI_USER_TEST_DB_PASSWORD
if($Stage -eq 'UserSecurity'){$values.AUS_HTTP_REUSE='UserSecurity'}
$saved=@{};foreach($key in $values.Keys){$saved[$key]=[Environment]::GetEnvironmentVariable($key,'Process');[Environment]::SetEnvironmentVariable($key,$values[$key],'Process')}
try{
    $argsList=@('-Duser.timezone=Asia/Seoul','-cp',($classes+';'+$cp))
    switch($Stage){
        InitAuth {$argsList+=@('AusChecks','init-auth',$repo)}
        AuthService {$argsList+=@('AuthMysqlConcurrency',$repo)}
        AuthConstraints {$argsList+=@('AusChecks','auth-constraints',$repo)}
        UserService {$argsList+=@('UserMysqlConcurrency',$repo,'Initialize')}
        UserVerify {$argsList+=@('UserMysqlConcurrency',$repo,'Verify')}
        UserAdditional {$argsList+=@('UserMysqlConcurrency',$repo,'Additional')}
        SurveyService {$argsList+=@('SurveyMysqlConcurrency',(Join-Path $work 'survey-service'))}
        AuthHttp {$argsList+=@('AuthMultiInstance',$repo,(Get-FileHash $jar -Algorithm SHA256).Hash)}
        UserHttp {$argsList+=@('UserMultiInstance',$repo,(Get-FileHash $jar -Algorithm SHA256).Hash)}
        UserSecurity {$argsList+=@('UserMultiInstance',$repo,(Get-FileHash $jar -Algorithm SHA256).Hash,'jwt-infrastructure-security-regression')}
        Audit {$argsList+=@('AusChecks','audit',$repo)}
    }
    # Intentionally no root secret is passed to Java or HTTP children.
    $javaPreference=$ErrorActionPreference
    try{$ErrorActionPreference='Continue'; & java @argsList; $javaExit=$LASTEXITCODE}finally{$ErrorActionPreference=$javaPreference}
    if($javaExit -ne 0){throw "Stage $Stage failed/incomplete; evidence and all resources retained"}
}finally{foreach($key in $saved.Keys){[Environment]::SetEnvironmentVariable($key,$saved[$key],'Process')};$values=$null}
