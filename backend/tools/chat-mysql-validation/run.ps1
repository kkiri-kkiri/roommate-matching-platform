param([switch] $PrepareOnly, [switch] $Provision, [switch] $KeepSession, [string] $EnvironmentFile)
$ErrorActionPreference = 'Stop'
if ($PrepareOnly -and $Provision) { throw 'PrepareOnly cannot provision.' }
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$workRoot = Join-Path $repoRoot 'backend/build/chat-mysql-validation'
New-Item -ItemType Directory -Force -Path $workRoot | Out-Null
$bootJar = Join-Path $repoRoot 'backend/build/libs/backend-0.0.1-SNAPSHOT.jar'
$libRoot = Join-Path $workRoot ('lib-' + (Get-FileHash -LiteralPath $bootJar -Algorithm SHA256).Hash)
$classRoot = Join-Path $workRoot 'classes'
New-Item -ItemType Directory -Force -Path $libRoot,$classRoot | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($bootJar)
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '^BOOT-INF/lib/[^/]+\.jar$') {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $libRoot $entry.Name),$true)
        }
    }
} finally { $archive.Dispose() }
$classpath = (Join-Path $repoRoot 'backend/build/classes/java/main') + ';' + $libRoot + '/*'
$sources = @(Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.java' | ForEach-Object { $_.FullName })
& javac -encoding UTF-8 -cp $classpath -d $classRoot @sources
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed; no database accessed.' }
if ($PrepareOnly) { Write-Output 'Chat MySQL harness compiled; no database accessed.'; return }

# Root credentials are confined to this provisioning helper. They are never passed to Java.
function Invoke-AdminSql([string] $Sql, [hashtable] $Credentials) {
    if ($Credentials.DB_USERNAME -notmatch '^[A-Za-z0-9_]+$') { throw 'Unsupported administrative username.' }
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = (Get-Command mysql).Source
    $start.Arguments = '--no-defaults --protocol=TCP --host=127.0.0.1 --port=3306 --connect-timeout=5 --batch --raw --skip-column-names --default-character-set=utf8mb4 --user=' + $Credentials.DB_USERNAME
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    $start.EnvironmentVariables['MYSQL_PWD'] = $Credentials.DB_PASSWORD
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $start
    try {
        [void] $process.Start()
        $process.StandardInput.WriteLine($Sql)
        $process.StandardInput.Close()
        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw 'Administrative SQL failed; raw SQL/error suppressed. Inspect the environment manifest for possibly created resources; do not reprovision.' }
        return $stdout.Trim()
    } finally { $process.Dispose() }
}
if ($Provision) {
    if ($EnvironmentFile) { throw 'Provision cannot reuse an environment file.' }
    $freeBytes = [IO.DriveInfo]::new('C:\').AvailableFreeSpace
    if ($freeBytes -lt 3GB) { throw 'Less than 3 GiB free; refusing provisioning. No cleanup will be performed.' }
    $runId = (Get-Date -Format 'yyyyMMdd') + [Guid]::NewGuid().ToString('N').Substring(0,8)
    $currentDb = 'kkirichatvalidation' + $runId
    $legacyDb = 'kkirichatlegacy' + $runId
    $testUser = 'kkirichattest_' + $runId.Substring(8)
    $EnvironmentFile = Join-Path $workRoot ($runId + '.environment.json')
    # No DB grants contain wildcard characters: schema names contain only letters/numbers.
    foreach ($name in @($currentDb,$legacyDb)) { if ($name -notmatch '^kkirichat(validation|legacy)[0-9a-f]+$') { throw 'Unsafe schema name.' } }
    if (Test-Path -LiteralPath $EnvironmentFile) { throw 'Local name collision; stopped.' }
    Write-Output "Proposed NEW schemas: $currentDb, $legacyDb; NEW account: $testUser@127.0.0.1"
    $admin = @{}
    Get-Content -LiteralPath (Join-Path $repoRoot 'backend/.env') -Encoding UTF8 | ForEach-Object {
        if ($_ -match '^\s*(DB_URL|DB_USERNAME|DB_PASSWORD)\s*=\s*(.*)\s*$') { $admin[$matches[1]] = $matches[2].Trim().Trim('"').Trim("'") }
    }
    try {
        if ($admin.DB_URL -notmatch '^jdbc:mysql://localhost:3306/kkirikkiri(?:\?|$)') { throw 'Unexpected admin endpoint; stopped.' }
        $identity = (Invoke-AdminSql 'SELECT @@server_uuid,@@hostname,VERSION(),@@port;' $admin) -split "`t"
        if ($identity.Count -ne 4 -or $identity[1] -ne 'DESKTOP-8K385BA' -or $identity[2] -ne '8.0.44' -or $identity[3] -ne '3306') { throw 'Unexpected server identity; stopped.' }
        $collision = (Invoke-AdminSql "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('$currentDb','$legacyDb'); SELECT COUNT(*) FROM mysql.user WHERE User='$testUser';" $admin) -split '\r?\n'
        if ($collision.Count -ne 2 -or $collision[0] -ne '0' -or $collision[1] -ne '0') { throw 'Existing DB/account name found; stopped without writes.' }
        $schemaDefaults = (Invoke-AdminSql "SELECT DEFAULT_CHARACTER_SET_NAME,DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='kkirikkiri';" $admin) -split "`t"
        if ($schemaDefaults.Count -ne 2 -or $schemaDefaults[0] -ne 'utf8mb4' -or $schemaDefaults[1] -notmatch '^utf8mb4_[a-z0-9_]+$') { throw 'Unsupported schema defaults; stopped.' }
        $manifest = [ordered]@{runId=$runId;host='127.0.0.1';port=3306;serverUuid=$identity[0];hostname=$identity[1];version=$identity[2];currentDb=$currentDb;legacyDb=$legacyDb;username=$testUser;accountHost='127.0.0.1';collation=$schemaDefaults[1];credentialStorage='process-memory-only';state='names-absent-approved-creation-starting';createdAt=(Get-Date).ToString('o');initialFreeBytes=$freeBytes}
        $manifest | ConvertTo-Json | Set-Content -LiteralPath $EnvironmentFile -Encoding UTF8
        $password = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
        $permissions = 'SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES'
        $provisionSql = "CREATE DATABASE ``$currentDb`` CHARACTER SET utf8mb4 COLLATE $($schemaDefaults[1]); CREATE DATABASE ``$legacyDb`` CHARACTER SET utf8mb4 COLLATE $($schemaDefaults[1]); CREATE USER '$testUser'@'127.0.0.1' IDENTIFIED BY '$password'; GRANT $permissions ON ``$currentDb``.* TO '$testUser'@'127.0.0.1'; GRANT $permissions ON ``$legacyDb``.* TO '$testUser'@'127.0.0.1';"
        $null = Invoke-AdminSql $provisionSql $admin
        $manifest.state = 'created-retain-no-automatic-deletion'
        $manifest | ConvertTo-Json | Set-Content -LiteralPath $EnvironmentFile -Encoding UTF8
        $env:KKIRI_CHAT_TEST_PASSWORD = $password
        Write-Output "Created approved resources. Manifest: $EnvironmentFile"
    } finally { $admin.Clear(); $password = $null; $provisionSql = $null }
}
if (!$EnvironmentFile) { throw 'Use -PrepareOnly, approved -Provision, or -EnvironmentFile pointing to previously created resources.' }
$environment = Get-Content -LiteralPath $EnvironmentFile -Raw -Encoding UTF8 | ConvertFrom-Json
if ($environment.state -ne 'created-retain-no-automatic-deletion') { throw 'Incomplete provisioning; no tests will run.' }
if (!$env:KKIRI_CHAT_TEST_PASSWORD) { throw 'Test password must be supplied in process memory. No password is saved or reset automatically.' }
try {
    $mode = 'Full'
    do {
        & javac -encoding UTF-8 -cp $classpath -d $classRoot @sources
        if ($LASTEXITCODE -ne 0) { throw 'Harness compilation failed.' }
        & java '-Duser.timezone=Asia/Seoul' '-Xmx512m' -cp ($classRoot + ';' + $classpath) ChatMysqlValidation $repoRoot $EnvironmentFile $mode
        $testExitCode = $LASTEXITCODE
        if (!$KeepSession) { break }
        Write-Output 'Memory-only session retained. Enter Regression or Stop. Credentials are never printed.'
        $reply = [Console]::ReadLine()
        if ($reply -eq 'Regression') { $mode = 'Regression' } else { break }
    } while ($true)
    if ($testExitCode -ne 0) { throw 'MySQL validation has failures; read this run evidence. All new resources are retained.' }
} finally { Remove-Item Env:KKIRI_CHAT_TEST_PASSWORD -ErrorAction SilentlyContinue }
