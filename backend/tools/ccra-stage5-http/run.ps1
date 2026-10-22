param([switch] $PrepareOnly, [switch] $CheckEnvironment, [switch] $ApprovedProvisionAndDelete, [string] $Owner, [string] $OnlyCases, [string] $Attempt)
$ErrorActionPreference = 'Stop'
if ($PrepareOnly -and $ApprovedProvisionAndDelete) { throw 'Preparation and execution must be separate.' }
if (!$PrepareOnly -and !$ApprovedProvisionAndDelete) { throw 'Use -PrepareOnly; provisioning requires explicit approval.' }
if ($Owner -and $Owner -cnotmatch '^[a-f0-9]{32}$') { throw 'Owner must be exactly 32 lowercase hex characters.' }
if ($ApprovedProvisionAndDelete -and !$Owner) { throw 'Pass the exact -Owner from the reviewed provisioning plan.' }
if ($Attempt -and $Attempt -cnotmatch '^[a-z0-9-]{1,30}$') { throw 'Unsafe attempt suffix.' }
if ($OnlyCases -and $OnlyCases -cnotmatch '^[a-z0-9,-]+$') { throw 'Unsafe case selection.' }
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$work = Join-Path $repo 'backend/build/ccra-stage5-http'
$jar = Join-Path $repo 'backend/build/libs/backend-0.0.1-SNAPSHOT.jar'
$image = 'mysql:8.0.46-oraclelinux9'
$container = 'kkiri-ccra-stage5-mysql-20261022'
$dbPort = 13346
$httpPort = 18091
$utf8 = New-Object Text.UTF8Encoding($false)
function Write-Json($Path, $Value) { [IO.File]::WriteAllText($Path,($Value | ConvertTo-Json -Depth 20),$utf8) }
function Docker-Read([string[]] $Arguments) {
    $saved = $ErrorActionPreference
    try { $ErrorActionPreference='Continue'; $lines=@(& docker @Arguments 2>&1 | ForEach-Object { $_.ToString() }); $code=$LASTEXITCODE }
    finally { $ErrorActionPreference=$saved }
    if ($code -ne 0) { throw 'Docker query failed; engine must be available. No automatic engine start or pull.' }
    return $lines
}
function Check-Environment {
    $state=@{status='CHECKING';checkedAtUtc=[DateTime]::UtcNow.ToString('o');container=$container;image=$image;dbPort=$dbPort;httpPort=$httpPort;readOnly=$true;stage='context'}
    try {
        $endpoint=@(Docker-Read @('context','inspect','--format','{{.Endpoints.docker.Host}}'))
        if ($endpoint.Count -ne 1 -or $endpoint[0] -notmatch '^npipe://') { throw 'Local Docker pipe required.' }
        $state.endpoint=$endpoint[0]; $state.stage='container-list'
        $names=@(Docker-Read @('ps','-a','--format','{{.Names}}'))
        if ($names -contains $container) { throw 'Container name collision; never reuse existing resources.' }
        $state.stage='image'
        $local=@(Docker-Read @('image','inspect',$image,'--format','{{.Id}} {{.Os}}/{{.Architecture}}'))
        if ($local.Count -ne 1 -or $local[0] -notmatch 'linux/amd64$') { throw 'Approved local Linux/amd64 image required.' }
        $state.localImage=$local[0]; $state.stage='ports'
        $listeners=@([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | Where-Object { $_.Port -in @($dbPort,$httpPort) })
        if ($listeners.Count -gt 0) { throw 'Dedicated port occupied; do not stop existing processes.' }
        $state.status='READY';$state.stage='complete'
    } catch { $state.status='BLOCKED';throw }
    finally { Write-Json (Join-Path $work 'environment-preflight.json') $state }
}
New-Item -ItemType Directory -Force -Path $work | Out-Null
if (!$Owner) { $Owner=[Guid]::NewGuid().ToString('N') }
Write-Json (Join-Path $work 'provision-plan.json') @{owner=$Owner;container=$container;image=$image;dbPort=$dbPort;httpPort=$httpPort;database=('kkiriccra5'+$Owner.Substring(0,12));account=('ccra5_'+$Owner.Substring(0,12)+'@%');storage=(Join-Path $work ('images-'+$Owner));existingResourcesReused=$false;cleanup='Only matching container ID/name/owner; evidence and local images retained';approvalRequired=$true}
if (!(Test-Path -LiteralPath $jar)) { throw 'Build bootJar first.' }
$hash=(Get-FileHash -LiteralPath $jar).Hash
$artifact=Join-Path $work ('artifact-'+$hash)
$main=Join-Path $artifact 'main';$lib=Join-Path $artifact 'lib'
$classes=Join-Path $work 'classes';$generated=Join-Path $work 'generated'
New-Item -ItemType Directory -Force -Path $main,$lib,$classes,$generated | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=[IO.Compression.ZipFile]::OpenRead($jar)
try {
    foreach ($entry in $archive.Entries) {
        $target=$null
        if ($entry.FullName -match '^BOOT-INF/lib/[^/]+\.jar$') { $target=Join-Path $lib $entry.Name }
        elseif ($entry.FullName.StartsWith('BOOT-INF/classes/') -and !$entry.FullName.EndsWith('/')) {
            $target=[IO.Path]::GetFullPath((Join-Path $main $entry.FullName.Substring(17)))
            if (!$target.StartsWith($main+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe archive entry.' }
        }
        if ($target) { New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($target)) | Out-Null; [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$target,$true) }
    }
} finally { $archive.Dispose() }
$base=Join-Path $repo 'backend/tools/matching-mysql-multi-instance'
$support=[IO.File]::ReadAllText((Join-Path $base 'MatchingMultiSupport.java'))
foreach ($token in @('127.0.0.1:3311/','kkiri_matching_mi_','kkiri_kmi_','"Wrong account");}')) { if (!$support.Contains($token)) { throw 'Support adapter drift.' } }
$support=$support.Replace('127.0.0.1:3311/','127.0.0.1:13346/').Replace('kkiri_matching_mi_','kkiriccra5').Replace('kkiri_kmi_','ccra5_')
$support=$support.Replace('?connectionTimeZone=UTC','?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC')
$support=$support.Replace('"Wrong account");}','"Wrong account");CcraMysqlValidation.strictGuard(c);}')
[IO.File]::WriteAllText((Join-Path $generated 'MatchingMultiSupport.java'),$support,$utf8)
$ccra=[IO.File]::ReadAllText((Join-Path $repo 'backend/tools/ccra-mysql-validation/CcraMysqlValidation.java'))
if (!$ccra.Contains('CcraMysqlValidation.class.getName()')) { throw 'CCRA provision adapter drift.' }
$ccra=$ccra.Replace('CcraMysqlValidation.class.getName()','Stage5Http.class.getName()').Replace('kkiriccra','kkiriccra5').Replace('ccra_','ccra5_').Replace('kkiri-ccra','kkiri-ccra5').Replace('18081','18091')
$ccra=$ccra.Replace('work.resolve("run-"+args[2])','work.resolve("run-"+args[2]+(System.getenv("CCRA5_ATTEMPT")==null?"":"-"+System.getenv("CCRA5_ATTEMPT")))')
[IO.File]::WriteAllText((Join-Path $generated 'CcraMysqlValidation.java'),$ccra,$utf8)
$server=[IO.File]::ReadAllText((Join-Path $base 'MatchingMultiServer.java'))
foreach ($token in @('p.put("logging.level.root","OFF");','@Bean JavaMailSender noMail()','try(var c=context.getBean(DataSource.class).getConnection()){event("ready"')) { if (!$server.Contains($token)) { throw 'Server adapter drift.' } }
$server=$server.Replace('18081','18091').Replace('18082','18092')
$server=$server.Replace('p.put("logging.level.root","OFF");','p.put("app.community.image-cleanup.enabled","false");p.put("app.community.image-directory",System.getenv("CCRA_IMAGES"));p.put("logging.level.root","OFF");')
$server=$server.Replace('@Bean JavaMailSender noMail()','@Bean @Primary com.kkirikkiri.matching.service.AiGatewayClient validationAi(tools.jackson.databind.ObjectMapper mapper){return new CcraMysqlValidation.NoAi(mapper);} @Bean @Primary com.kkirikkiri.calendar.crawler.HknuAcademicCalendarCrawler validationCrawler(){return new CcraMysqlValidation.NoCrawl();} @Bean JavaMailSender noMail()')
$server=$server.Replace('try(var c=context.getBean(DataSource.class).getConnection()){event("ready"','check(context.getBean(com.kkirikkiri.matching.service.AiGatewayClient.class) instanceof CcraMysqlValidation.NoAi,"Real AI configured");check(context.getBean(com.kkirikkiri.calendar.crawler.HknuAcademicCalendarCrawler.class) instanceof CcraMysqlValidation.NoCrawl,"Real crawler configured");try(var c=context.getBean(DataSource.class).getConnection()){event("ready"')
# Observe completion of the actual application message handler, without changing validation/exception behavior.
$server=$server.Replace('String line;while((line=reader.readLine())!=null)', 'context.getBean("clientInboundChannel",org.springframework.messaging.support.AbstractSubscribableChannel.class).addInterceptor(new org.springframework.messaging.support.ExecutorChannelInterceptor(){public void afterMessageHandled(org.springframework.messaging.Message<?> message,org.springframework.messaging.MessageChannel channel,org.springframework.messaging.MessageHandler handler,Exception failure){if(!(handler instanceof org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler))return;var h=org.springframework.messaging.simp.stomp.StompHeaderAccessor.wrap(message);String id=h.getFirstNativeHeader("x-stage5-id");if(id!=null)event("stompHandled",Map.of("id",id,"failed",failure!=null));}});String line;while((line=reader.readLine())!=null)')
[IO.File]::WriteAllText((Join-Path $generated 'MatchingMultiServer.java'),$server,$utf8)
$cp=$main+';'+$lib+'/*'
$sources=@((Join-Path $generated 'MatchingMultiSupport.java'),(Join-Path $generated 'MatchingMultiServer.java'),(Join-Path $generated 'CcraMysqlValidation.java'),(Join-Path $base 'MatchingMultiInstance.java'),(Join-Path $base 'MatchingExtraChecks.java'),(Join-Path $repo 'backend/src/test/java/com/kkirikkiri/survey/SurveyTestData.java'),(Join-Path $PSScriptRoot 'Stage5Http.java'))
$saved=$ErrorActionPreference
try { $ErrorActionPreference='Continue';$diagnostics=@(& javac -encoding UTF-8 -cp $cp -d $classes @sources 2>&1 | ForEach-Object { $_.ToString() });$compileExit=$LASTEXITCODE }
finally { $ErrorActionPreference=$saved }
[IO.File]::WriteAllLines((Join-Path $work 'compile.log'),[string[]]$diagnostics,$utf8)
if ($compileExit -ne 0) { throw 'Compilation failed; no DB accessed. See compile.log.' }
$catalog=@(& java -cp ($classes+';'+$cp) kmi.Stage5Http PREPARE)
if ($LASTEXITCODE -ne 0) { throw 'DB-free catalog check failed.' }
Write-Json (Join-Path $work 'prepare.json') @{status='PREPARED_NOT_EXECUTED';jarSha256=$hash;compileExit=$compileExit;catalog=($catalog | ConvertFrom-Json);mysqlExecuted=0;jdbcConnections=0;atUtc=[DateTime]::UtcNow.ToString('o')}
if ($CheckEnvironment -or $ApprovedProvisionAndDelete) { Check-Environment }
if ($PrepareOnly) { Write-Output 'Stage 5 compiled; no DB connections or HTTP requests executed.';return }
$created=$false
$names=@('KM_ROOT_PASSWORD','MYSQL_ROOT_PASSWORD','KM_UUID','KM_HOSTNAME','CCRA_IMAGES','CCRA_MYSQL_VERSION','CCRA5_APPROVED','CCRA5_CASES','CCRA5_ATTEMPT','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')
$prior=@{};foreach ($name in $names) { $prior[$name]=[Environment]::GetEnvironmentVariable($name,'Process') }
try {
    foreach ($name in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')) { [Environment]::SetEnvironmentVariable($name,$null,'Process') }
    [Environment]::SetEnvironmentVariable('CCRA5_CASES',$OnlyCases,'Process');[Environment]::SetEnvironmentVariable('CCRA5_ATTEMPT',$Attempt,'Process')
    $env:KM_ROOT_PASSWORD=[Guid]::NewGuid().ToString('N')+[Guid]::NewGuid().ToString('N');$env:MYSQL_ROOT_PASSWORD=$env:KM_ROOT_PASSWORD
    $id=@(& docker create --name $container --hostname kkiri-ccra5 --label "kkiri.ccra5.owner=$owner" --memory 1g --cpus 2 --tmpfs /var/lib/mysql:rw,size=768m -p 127.0.0.1:13346:3306 --env MYSQL_ROOT_PASSWORD $image)
    if ($LASTEXITCODE -ne 0 -or $id.Count -ne 1) { throw 'Container creation failed.' }
    $containerId=$id[0].Trim();$created=$true
    $null=& docker start $containerId
    if ($LASTEXITCODE -ne 0) { throw 'Container start failed.' }
    $ready=$false
    for ($readyAttempt=0;$readyAttempt -lt 90;$readyAttempt++) {
        $saved=$ErrorActionPreference
        try { $ErrorActionPreference='Continue';$null=& docker exec $containerId mysqladmin --protocol=TCP --host=127.0.0.1 ping --silent 2>$null;$ping=$LASTEXITCODE }
        finally { $ErrorActionPreference=$saved }
        if ($ping -eq 0) { $ready=$true;break }
        Start-Sleep -Seconds 1
    }
    if (!$ready) { throw 'MySQL readiness timeout.' }
    $inspect=(@(Docker-Read @('inspect',$containerId)) -join "`n" | ConvertFrom-Json)[0]
    $bindings=@($inspect.NetworkSettings.Ports.'3306/tcp')
    if ($inspect.Config.Labels.'kkiri.ccra5.owner' -cne $owner -or $bindings.Count -ne 1 -or $bindings[0].HostIp -ne '127.0.0.1' -or $bindings[0].HostPort -ne '13346' -or @($inspect.Mounts | Where-Object { $_.Type -ne 'tmpfs' }).Count -ne 0) { throw 'Container identity/isolation mismatch.' }
    $uuid=@(Docker-Read @('exec',$containerId,'cat','/var/lib/mysql/auto.cnf') | Where-Object { $_ -match '^server-uuid=' })
    if ($uuid.Count -ne 1) { throw 'Server UUID unavailable.' }
    $env:KM_UUID=$uuid[0].Substring(12).Trim();$env:KM_HOSTNAME='kkiri-ccra5';$env:CCRA_MYSQL_VERSION='8.0.46';$env:CCRA5_APPROVED='true'
    $env:CCRA_IMAGES=Join-Path $work ('images-'+$owner)
    if (Test-Path -LiteralPath $env:CCRA_IMAGES) {
        if (!$Attempt) { throw 'Image root collision; use an explicit retry only for previously owned test files.' }
        $previousCleanup=Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $work ('cleanup-'+$owner+'.json')) | ConvertFrom-Json
        if (!$previousCleanup.removed -or $previousCleanup.owner -cne $owner) { throw 'Previous run ownership unavailable.' }
    } else { New-Item -ItemType Directory -Path $env:CCRA_IMAGES | Out-Null }
    $suffix=if ($Attempt) { '-'+$Attempt } else { '' }
    Write-Json (Join-Path $work ('container-'+$owner+$suffix+'.json')) @{owner=$owner;container=$container;containerId=$containerId;image=$image;imageId=$inspect.Image;serverUuid=$env:KM_UUID;dbPort=$dbPort;httpPort=$httpPort}
    & java '-Duser.timezone=Asia/Seoul' '-Xmx512m' -cp ($classes+';'+$cp) kmi.Stage5Http $repo $work $owner
    if ($LASTEXITCODE -ne 0) { throw 'Stage 5 contains failures/incomplete cases; evidence retained.' }
} finally {
    if ($created) {
        $target=(@(Docker-Read @('inspect',$containerId)) -join "`n" | ConvertFrom-Json)[0]
        if ($target.Id -cne $containerId -or $target.Name -cne ('/'+$container) -or $target.Config.Labels.'kkiri.ccra5.owner' -cne $owner) { throw 'Cleanup ownership mismatch; no removal attempted.' }
        $null=& docker rm -f -v $containerId
        if ($LASTEXITCODE -ne 0) { throw 'Owned container removal failed.' }
        $suffix=if ($Attempt) { '-'+$Attempt } else { '' }
        Write-Json (Join-Path $work ('cleanup-'+$owner+$suffix+'.json')) @{removed=$true;containerId=$containerId;owner=$owner;protectedResourcesChanged=$false;imagesAndEvidenceRetained=$true}
    }
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$prior[$name],'Process') }
}
