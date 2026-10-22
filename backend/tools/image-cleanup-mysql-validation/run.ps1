param(
    [switch] $PrepareOnly,
    [switch] $CheckEnvironment,
    [switch] $ApprovedProvisionAndDelete
)
$ErrorActionPreference = 'Stop'
if ($PrepareOnly -and $ApprovedProvisionAndDelete) { throw 'Preparation and provisioning must be separate invocations.' }
if (!$PrepareOnly -and !$ApprovedProvisionAndDelete) { throw 'Select -PrepareOnly, or use -ApprovedProvisionAndDelete only after approval.' }
$javaOptionPrevious = @{}
foreach ($name in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')) {
    $javaOptionPrevious[$name] = [Environment]::GetEnvironmentVariable($name,'Process')
    [Environment]::SetEnvironmentVariable($name,$null,'Process')
}
try {
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$work = Join-Path $repo 'backend/build/image-cleanup-mysql-validation'
$jar = Join-Path $repo 'backend/build/libs/backend-0.0.1-SNAPSHOT.jar'
$image = 'mysql:8.0.46-oraclelinux9'
$container = 'kkiri-image-cleanup-mysql-20261021'
$port = 13336
$utf8 = New-Object System.Text.UTF8Encoding($false)
function Write-Json($Path, $Value) {
    [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 20), $utf8)
}
function Read-Docker([string[]] $DockerArguments) {
    $saved = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $lines = @(& docker @DockerArguments 2>&1 | ForEach-Object { $_.ToString() })
        $nativeExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $saved }
    if ($nativeExit -ne 0) { throw 'Read-only Docker query failed; confirm engine availability. No changes performed.' }
    return $lines
}
function Read-Environment {
    # Read-only: do not bind a listener, start Docker Desktop, pull, or reuse a container.
    $listeners = @([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() |
        Where-Object { $_.Port -eq $port } | ForEach-Object { $_.ToString() })
    $state = @{ checkedAtUtc=[DateTime]::UtcNow.ToString('o'); container=$container;
        containerCollision=$null; port=$port; listeners=$listeners; status='CHECKING'; stage='context';
        localImage=$null; dockerEndpoint=$null; readOnly=$true; mysqlConnections=0 }
    try {
        $contextText = @(Read-Docker -DockerArguments @('context','inspect','--format','{{.Endpoints.docker.Host}}'))
        if ($contextText.Count -ne 1 -or $contextText[0] -notmatch '^npipe://') { throw 'Only a local Docker pipe is allowed.' }
        $state.dockerEndpoint=$contextText[0]; $state.stage='container-list'
        $names = @(Read-Docker -DockerArguments @('ps','-a','--format','{{.Names}}'))
        $state.containerCollision=($names -contains $container); $state.stage='local-image'
        $imageText = @(Read-Docker -DockerArguments @('image','inspect',$image,'--format','{{.Id}} {{.Os}}/{{.Architecture}}'))
        if ($imageText.Count -ne 1 -or $imageText[0] -notmatch 'linux/amd64$') { throw 'Requested local Linux/amd64 image unavailable; no pull attempted.' }
        $state.localImage=$imageText[0]; $state.stage='collision-check'
        if ($state.containerCollision -or $listeners.Count -gt 0) { throw 'Container/port collision; no existing resource will be reused or stopped.' }
        $state.status='READY'; $state.stage='complete'
    } catch { $state.status='BLOCKED'; throw }
    finally { Write-Json (Join-Path $work 'environment-preflight.json') $state }
    return $state
}
if (!(Test-Path -LiteralPath $jar -PathType Leaf)) { throw 'Existing bootJar required. This tool does not build or run Gradle tests.' }
$jarHash = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
$artifact = Join-Path $work ('artifact-' + $jarHash)
$classes = Join-Path $work 'classes'
$generated = Join-Path $work 'generated'
$main = Join-Path $artifact 'main'
$lib = Join-Path $artifact 'lib'
New-Item -ItemType Directory -Force -Path $classes,$generated,$main,$lib | Out-Null
Write-Json (Join-Path $work 'prepare.json') @{status='PREPARING';atUtc=[DateTime]::UtcNow.ToString('o');mysqlExecuted=0;jdbcConnections=0}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($jar)
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '^BOOT-INF/lib/[^/]+\.jar$') {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $lib $entry.Name),$true)
        } elseif ($entry.FullName.StartsWith('BOOT-INF/classes/') -and !$entry.FullName.EndsWith('/')) {
            $relative = $entry.FullName.Substring('BOOT-INF/classes/'.Length)
            $target = [IO.Path]::GetFullPath((Join-Path $main $relative))
            if (!$target.StartsWith($main + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe JAR entry.' }
            New-Item -ItemType Directory -Force -Path ([IO.Path]::GetDirectoryName($target)) | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$target,$true)
        }
    }
} finally { $archive.Dispose() }
# Reuse CCRA's existing underlying JDBC identity/query/JSON helpers, never its scenario runner.
$supportPath = Join-Path $repo 'backend/tools/matching-mysql-multi-instance/MatchingMultiSupport.java'
$support = [IO.File]::ReadAllText($supportPath)
foreach ($token in @('127.0.0.1:3311/','kkiri_matching_mi_','kkiri_kmi_','"Wrong account");}','static Connection open()throws Exception{')) {
    if (!$support.Contains($token)) { throw 'Reusable CCRA support changed; adapter review required.' }
}
$support = $support.Replace('127.0.0.1:3311/','127.0.0.1:13336/').Replace('kkiri_matching_mi_','kkiriimgcleanup').Replace('kkiri_kmi_','imgcleanup_')
$support = $support.Replace('?connectionTimeZone=UTC','?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC')
$support = $support.Replace('"Wrong account");}', '"Wrong account");ImageCleanupMysql.strictGuard(c);}')
$support = $support.Replace('static Connection open()throws Exception{','static Connection open()throws Exception{ImageCleanupMysql.requireApproved();')
[IO.File]::WriteAllText((Join-Path $generated 'MatchingMultiSupport.java'),$support,$utf8)
$cp = $main + ';' + $lib + '/*'
$sources = @((Join-Path $generated 'MatchingMultiSupport.java'),
    (Join-Path $PSScriptRoot 'ImageCleanupRuntime.java'),(Join-Path $PSScriptRoot 'ImageCleanupMysql.java'))
$savedPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    $javaVersion = @(& java -version 2>&1 | ForEach-Object { $_.ToString() })
    $versionExit = $LASTEXITCODE
} finally { $ErrorActionPreference = $savedPreference }
if ($versionExit -ne 0 -or ($javaVersion -join ' ') -notmatch 'version "21\.') { throw 'Java 21 required.' }
# PowerShell 5 may promote harmless native javac stderr; the native exit code is authoritative.
$savedPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    $diagnostics = @(& javac -encoding UTF-8 -cp $cp -d $classes @sources 2>&1 | ForEach-Object { $_.ToString() })
    $compileExit = $LASTEXITCODE
} finally { $ErrorActionPreference = $savedPreference }
[IO.File]::WriteAllLines((Join-Path $work 'compile.log'),[string[]]$diagnostics,$utf8)
if ($compileExit -ne 0) { throw 'Compilation failed. Inspect compile.log. No DB accessed.' }
$prepared = @(& java -cp ($classes + ';' + $cp) kmi.ImageCleanupMysql PREPARE)
if ($LASTEXITCODE -ne 0 -or $prepared.Count -ne 1) { throw 'DB-free linkage/catalog preparation failed.' }
$preparedState = $prepared[0] | ConvertFrom-Json
if ($preparedState.mysqlExecuted -ne 0 -or $preparedState.jdbcConnections -ne 0) { throw 'Preparation boundary violated.' }
$fingerprints = [ordered]@{}
foreach ($dir in @('backend/src/main','backend/migrations','backend/tools/image-cleanup-mysql-validation')) {
    Get-ChildItem -LiteralPath (Join-Path $repo $dir) -File -Recurse | ForEach-Object {
        $fingerprints[$_.FullName.Substring($repo.Length+1)] = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
    }
}
$fingerprints['backend/tools/matching-mysql-multi-instance/MatchingMultiSupport.java'] = (Get-FileHash -LiteralPath $supportPath).Hash
Write-Json (Join-Path $work 'source-hashes.json') $fingerprints
Write-Json (Join-Path $work 'prepare.json') @{
    status='PREPARED_NOT_EXECUTED'; atUtc=[DateTime]::UtcNow.ToString('o'); jarSha256=$jarHash;
    java=$javaVersion; compileExit=$compileExit; catalog=$preparedState; mysqlExecuted=0; jdbcConnections=0;
    productionChanges=0; usesJarClasses=$true; gitCommands=0; container=$container; image=$image; port=$port
}
if ($CheckEnvironment -or $ApprovedProvisionAndDelete) { $null = Read-Environment }
if ($PrepareOnly) { Write-Output 'Prepared 13 scenarios; actual MySQL executions 0; JDBC connections 0.'; return }

# Everything below is opt-in future execution. NEVER entered by -PrepareOnly.
$owner = [Guid]::NewGuid().ToString('N')
$run = Join-Path $work ('run-' + $owner)
New-Item -ItemType Directory -Path $run | Out-Null
[IO.File]::WriteAllText((Join-Path $run 'owner.txt'),$owner,$utf8)
Copy-Item -LiteralPath (Join-Path $work 'prepare.json') -Destination (Join-Path $run 'artifact.json')
Copy-Item -LiteralPath (Join-Path $work 'source-hashes.json') -Destination (Join-Path $run 'source-hashes.json')
Copy-Item -LiteralPath (Join-Path $work 'environment-preflight.json') -Destination (Join-Path $run 'environment-preflight.json')
$envNames = @('ICMV_APPROVED','ICMV_OWNER','ICMV_CONTAINER','ICMV_RUN','ICMV_ROOT_PASSWORD','MYSQL_ROOT_PASSWORD',
    'KM_UUID','KM_HOSTNAME','KM_DATABASE','KM_USER','KM_PASSWORD','ICMV_PHASE','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')
$previous = @{}
foreach ($name in $envNames) { $previous[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
$created = $false
$validationExit = $null
try {
    foreach ($name in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','KM_DATABASE','KM_USER','KM_PASSWORD','ICMV_PHASE')) {
        [Environment]::SetEnvironmentVariable($name,$null,'Process')
    }
    $env:ICMV_APPROVED='CREATE_NEW_ISOLATED_ONLY'; $env:ICMV_OWNER=$owner
    $env:ICMV_CONTAINER=$container; $env:ICMV_RUN=$run
    $env:ICMV_ROOT_PASSWORD=[Guid]::NewGuid().ToString('N')+[Guid]::NewGuid().ToString('N')
    $env:MYSQL_ROOT_PASSWORD=$env:ICMV_ROOT_PASSWORD
    $containerId = (& docker create --name $container --hostname kkiri-imgcleanup --label "kkiri.image-cleanup.owner=$owner" --memory 1g --cpus 2 --tmpfs /var/lib/mysql:rw,size=768m -p 127.0.0.1:13336:3306 --env MYSQL_ROOT_PASSWORD $image).Trim()
    if ($LASTEXITCODE -ne 0 -or $containerId -notmatch '^[a-f0-9]{64}$') { throw 'New container creation failed.' }
    $created = $true
    $null = & docker start $containerId
    if ($LASTEXITCODE -ne 0) { throw 'New container startup failed.' }
    $ready = $false
    for ($attempt=0; $attempt -lt 90; $attempt++) {
        $ErrorActionPreference='Continue'
        & docker exec $containerId mysqladmin --protocol=TCP --host=127.0.0.1 ping --silent 2>$null | Out-Null
        $pingExit=$LASTEXITCODE; $ErrorActionPreference='Stop'
        if ($pingExit -eq 0) { $ready=$true; break }
        $state=(& docker inspect $containerId | ConvertFrom-Json)[0]
        if (!$state.State.Running) { throw 'New MySQL exited before readiness; no scenario pass recorded.' }
        Start-Sleep -Seconds 1
    }
    if (!$ready) { throw 'New MySQL readiness timeout.' }
    $target=(& docker inspect $containerId | ConvertFrom-Json)[0]
    $bindings=@($target.NetworkSettings.Ports.'3306/tcp')
    if ($target.Id -cne $containerId -or $target.Name -cne ('/'+$container) -or
        $target.Config.Labels.'kkiri.image-cleanup.owner' -cne $owner -or $target.Config.Image -cne $image -or
        $bindings.Count -ne 1 -or $bindings[0].HostIp -cne '127.0.0.1' -or $bindings[0].HostPort -cne '13336' -or
        @($target.Mounts | Where-Object { $_.Type -ne 'tmpfs' }).Count -ne 0 -or
        @($target.HostConfig.Tmpfs.PSObject.Properties.Name).Count -ne 1 -or !$target.HostConfig.Tmpfs.'/var/lib/mysql') {
        throw 'New-container isolation verification failed.'
    }
    $uuid=@(& docker exec $containerId cat /var/lib/mysql/auto.cnf | Where-Object { $_ -match '^server-uuid=' })
    if ($LASTEXITCODE -ne 0 -or $uuid.Count -ne 1) { throw 'New server UUID unavailable.' }
    $env:KM_UUID=$uuid[0].Substring(12).Trim(); $env:KM_HOSTNAME='kkiri-imgcleanup'
    Write-Json (Join-Path $run 'container.json') @{containerId=$containerId;owner=$owner;name=$container;image=$image;imageId=$target.Image;port=$port;uuid=$env:KM_UUID;existingVolumesMounted=$false}
    & java '-Duser.timezone=Asia/Seoul' '-Xmx512m' -cp ($classes+';'+$cp) kmi.ImageCleanupMysql PROVISION $run
    $validationExit=$LASTEXITCODE
    if ($validationExit -ne 0) { throw 'MySQL validation failed or recorded FINDING/INCOMPLETE. Inspect run evidence; do not count as all-pass.' }
} finally {
    try {
        if ($created) {
            $target=(& docker inspect $containerId | ConvertFrom-Json)[0]
            if ($LASTEXITCODE -ne 0 -or $target.Id -cne $containerId -or $target.Name -cne ('/'+$container) -or $target.Config.Labels.'kkiri.image-cleanup.owner' -cne $owner) {
                throw 'Owned-resource cleanup refused: identity mismatch.'
            }
            $null=& docker rm -f -v $containerId
            if ($LASTEXITCODE -ne 0) { throw 'Owned container removal failed.' }
            Write-Json (Join-Path $run 'cleanup.json') @{owner=$owner;containerId=$containerId;removed=$true;existingResourcesChanged=$false;validationExit=$validationExit;localEvidenceAndFilesRetained=$true}
        }
    } finally {
        foreach ($name in $envNames) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
    }
}
} finally {
    foreach ($name in $javaOptionPrevious.Keys) { [Environment]::SetEnvironmentVariable($name,$javaOptionPrevious[$name],'Process') }
}
