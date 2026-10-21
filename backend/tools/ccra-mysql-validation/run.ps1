param([switch] $PrepareOnly, [switch] $ApprovedProvisionAndDelete, [switch] $DiagnoseOnly, [switch] $Mysql844)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$workName = if ($Mysql844) { 'ccra-mysql844-validation' } else { 'ccra-mysql-validation' }
$work = Join-Path $repo ('backend/build/' + $workName)
$classes = Join-Path $work 'classes'
$generated = Join-Path $work 'generated'
$jar = Join-Path $repo 'backend/build/libs/backend-0.0.1-SNAPSHOT.jar'
$lib = Join-Path $work ('lib-' + (Get-FileHash -LiteralPath $jar).Hash)
New-Item -ItemType Directory -Force -Path $classes,$generated,$lib | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($jar)
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '^BOOT-INF/lib/[^/]+\.jar$') {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $lib $entry.Name),$true)
        }
    }
} finally { $archive.Dispose() }
# Reuse the existing HTTP runner without changing files owned by other validations.
# Only generated build copies get the dedicated endpoint, namespace and stricter guard.
$base = Join-Path $repo 'backend/tools/matching-mysql-multi-instance'
$support = [IO.File]::ReadAllText((Join-Path $base 'MatchingMultiSupport.java'))
if (!$support.Contains('127.0.0.1:3311/') -or !$support.Contains('"Wrong account");}')) { throw 'Reusable support changed; review adapter.' }
$support = $support.Replace('127.0.0.1:3311/','127.0.0.1:13326/').Replace('kkiri_matching_mi_','kkiriccra').Replace('kkiri_kmi_','ccra_')
$support = $support.Replace('?connectionTimeZone=UTC','?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC')
if ($Mysql844) {
    if (!$support.Contains('"8.0.46".equals(r.get("version"))')) { throw 'Version guard changed; review compatibility adapter.' }
    $support = $support.Replace('"8.0.46".equals(r.get("version"))','"8.0.44".equals(r.get("version"))')
}
$support = $support.Replace('"Wrong account");}', '"Wrong account");CcraMysqlValidation.strictGuard(c);}')
[IO.File]::WriteAllText((Join-Path $generated 'MatchingMultiSupport.java'),$support)
$server = [IO.File]::ReadAllText((Join-Path $base 'MatchingMultiServer.java'))
$server = $server.Replace('p.put("logging.level.root","OFF");', 'p.put("app.community.image-cleanup.enabled","false");p.put("app.community.image-directory",System.getenv("CCRA_IMAGES"));p.put("logging.level.root","OFF");')
$server = $server.Replace('@Bean JavaMailSender noMail()', '@Bean @Primary com.kkirikkiri.matching.service.AiGatewayClient validationAi(tools.jackson.databind.ObjectMapper mapper){return new CcraMysqlValidation.NoAi(mapper);} @Bean @Primary com.kkirikkiri.calendar.crawler.HknuAcademicCalendarCrawler validationCrawler(){return new CcraMysqlValidation.NoCrawl();} @Bean JavaMailSender noMail()')
$server = $server.Replace('try(var c=context.getBean(DataSource.class).getConnection()){event("ready"', 'check(context.getBean(com.kkirikkiri.matching.service.AiGatewayClient.class) instanceof CcraMysqlValidation.NoAi,"Real AI configured");check(context.getBean(com.kkirikkiri.calendar.crawler.HknuAcademicCalendarCrawler.class) instanceof CcraMysqlValidation.NoCrawl,"Real crawler configured");try(var c=context.getBean(DataSource.class).getConnection()){event("ready"')
[IO.File]::WriteAllText((Join-Path $generated 'MatchingMultiServer.java'),$server)
$cp = (Join-Path $repo 'backend/build/classes/java/main') + ';' + (Join-Path $repo 'backend/build/resources/main') + ';' + $lib + '/*'
$sources = @((Join-Path $generated 'MatchingMultiSupport.java'),(Join-Path $generated 'MatchingMultiServer.java'),(Join-Path $base 'MatchingMultiInstance.java'),(Join-Path $base 'MatchingExtraChecks.java'),(Join-Path $repo 'backend/src/test/java/com/kkirikkiri/survey/SurveyTestData.java'),(Join-Path $PSScriptRoot 'CcraMysqlValidation.java'))
& javac -encoding UTF-8 -cp $cp -d $classes @sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed; no DB accessed.' }
if ($PrepareOnly) { Write-Output 'CCRA compiled; no DB accessed.'; return }
if (!$ApprovedProvisionAndDelete) { throw 'Explicit creation and deletion approval required.' }
$container = if ($Mysql844) { 'kkiri-ccra-mysql844-20261021' } else { 'kkiri-ccra-mysql-20261021' }
$owner = [Guid]::NewGuid().ToString('N')
$image = if ($Mysql844) { 'mysql:8.0.44' } else { 'mysql@sha256:62fb722c78b24245ddff1796a0fcee4a49cc5b87e0aaaf20c92d1da9e0a2497b' }
$created = $false
$names = @('KM_ROOT_PASSWORD','KM_UUID','KM_HOSTNAME','CCRA_IMAGES','MYSQL_ROOT_PASSWORD','CCRA_MYSQL_VERSION')
$prior = @{}
foreach ($name in $names) { $prior[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
try {
    $context = & docker context inspect
    if ($LASTEXITCODE -ne 0) { throw 'Docker context unavailable.' }
    $endpoint = ($context | ConvertFrom-Json)[0].Endpoints.docker.Host
    if ($endpoint -notmatch '^npipe://') { throw 'Only local Docker pipe allowed.' }
    $existing = @(& docker ps -a --format '{{.Names}}')
    if ($LASTEXITCODE -ne 0 -or $existing -contains $container) { throw 'Container collision or inspection failure; no changes.' }
    foreach ($port in @(13326,18081)) {
        $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,$port)
        try { $listener.Start() } finally { $listener.Stop() }
    }
    $null = & docker image inspect $image
    if ($LASTEXITCODE -ne 0) { throw 'Approved local image missing; no pull performed.' }
    $env:KM_ROOT_PASSWORD = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
    # Docker inherits the value from process environment, never from command arguments.
    $env:MYSQL_ROOT_PASSWORD = $env:KM_ROOT_PASSWORD
    $create = & docker create --name $container --hostname kkiri-ccra --label "kkiri.ccra.owner=$owner" --memory 1g --cpus 2 --tmpfs /var/lib/mysql:rw,size=768m -p 127.0.0.1:13326:3306 --env MYSQL_ROOT_PASSWORD $image
    if ($LASTEXITCODE -ne 0) { throw 'Container creation failed.' }
    $containerId = $create.Trim(); $created = $true
    $null = & docker cp ($containerId + ':/usr/local/bin/docker-entrypoint.sh') (Join-Path $work ('entrypoint-' + $owner + '.sh'))
    if ($LASTEXITCODE -ne 0) { throw 'Entrypoint inspection failed.' }
    if ($DiagnoseOnly) {
        foreach ($binary in @('bash','env')) {
            $targetBinary = Join-Path $work ($binary + '-' + $owner + '.elf')
            $null = & docker cp ($containerId + ':/usr/bin/' + $binary) $targetBinary
            if ($LASTEXITCODE -ne 0) { throw 'ELF inspection failed.' }
            $bytes = [IO.File]::ReadAllBytes($targetBinary)
            $machine = if ($bytes.Length -ge 24) { [BitConverter]::ToUInt16($bytes,18) } else { -1 }
            @{binary=$binary;length=$bytes.Length;machine=$machine} | ConvertTo-Json | Write-Output
        }
        return
    }
    $null = & docker start $containerId
    if ($LASTEXITCODE -ne 0) { throw 'Container startup failed.' }
    $ready = $false
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        $ErrorActionPreference = 'Continue'
        & docker exec $containerId mysqladmin --protocol=TCP --host=127.0.0.1 --port=3306 ping --silent 2>$null | Out-Null
        $pingExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($pingExit -eq 0) { $ready = $true; break }
        $startup = (& docker inspect $containerId | ConvertFrom-Json)[0]
        if (!$startup.State.Running) {
            $startup.State | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $work ('startup-' + $owner + '.json'))
            $ErrorActionPreference = 'Continue'
            $logs = & docker logs $containerId 2>&1
            $ErrorActionPreference = 'Stop'
            $safeLines = @($logs | ForEach-Object { $_.ToString() } | Where-Object { $_ -match '\[ERROR\]|No space left|Permission denied|memory|Aborting|directory|exec format' } | ForEach-Object { $_.Replace($env:KM_ROOT_PASSWORD,'[REDACTED]') })
            $safeLines | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $work ('startup-' + $owner + '.log'))
            throw 'MySQL container exited; sanitized startup diagnostics retained.'
        }
        Start-Sleep -Seconds 1
    }
    if (!$ready) { throw 'MySQL readiness timeout.' }
    $inspect = (& docker inspect $containerId | ConvertFrom-Json)[0]
    $bindings = @($inspect.NetworkSettings.Ports.'3306/tcp')
    if ($inspect.Config.Labels.'kkiri.ccra.owner' -cne $owner -or $inspect.Config.Image -cne $image -or $bindings.Count -ne 1 -or $bindings[0].HostIp -ne '127.0.0.1' -or $bindings[0].HostPort -ne '13326') { throw 'Container identity mismatch.' }
    $uuid = @(& docker exec $containerId cat /var/lib/mysql/auto.cnf | Where-Object { $_ -match '^server-uuid=' })
    if ($LASTEXITCODE -ne 0 -or $uuid.Count -ne 1) { throw 'UUID unavailable.' }
    $env:KM_UUID = $uuid[0].Substring(12).Trim()
    $env:KM_HOSTNAME = 'kkiri-ccra'
    $env:CCRA_MYSQL_VERSION = if ($Mysql844) { '8.0.44' } else { '8.0.46' }
    @{owner=$owner;container=$container;containerId=$containerId;image=$image;imageId=$inspect.Image;port=13326;serverUuid=$env:KM_UUID;expectedVersion=$env:CCRA_MYSQL_VERSION} | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $work ('container-' + $owner + '.json'))
    $env:CCRA_IMAGES = Join-Path $work ('images-' + $owner)
    New-Item -ItemType Directory -Path $env:CCRA_IMAGES | Out-Null
    & java '-Duser.timezone=Asia/Seoul' '-Xmx512m' -cp ($classes + ';' + $cp) kmi.CcraMysqlValidation $repo $work $owner
    $validationExit = $LASTEXITCODE
    if ($validationExit -ne 0) { throw 'Validation failed; evidence retained.' }
} finally {
    if ($created) {
        $target = (& docker inspect $containerId | ConvertFrom-Json)[0]
        if ($LASTEXITCODE -ne 0 -or $target.Id -cne $containerId -or $target.Name -cne ('/' + $container) -or $target.Config.Labels.'kkiri.ccra.owner' -cne $owner) { throw 'Cleanup ownership check failed; no removal attempted.' }
        $null = & docker rm -f -v $containerId
        if ($LASTEXITCODE -ne 0) { throw 'Owned container removal failed.' }
        @{owner=$owner;container=$container;containerId=$containerId;removed=$true;existingContainersChanged=$false} | ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $work ('cleanup-' + $owner + '.json'))
    }
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$prior[$name],'Process') }
}
