param([switch] $ReadServerMetadata)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$workRoot = Join-Path $repoRoot 'backend/build/chat-mysql-validation'
$completedPath = Join-Path $workRoot '202610191135f9f3.environment.json'
$earlierPath = Join-Path $workRoot '202610195a7b340c.environment.json'
$completed = Get-Content -LiteralPath $completedPath -Raw -Encoding UTF8 | ConvertFrom-Json
$earlier = Get-Content -LiteralPath $earlierPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($completed.username -ne 'kkirichattest_1135f9f3' -or
    $completed.currentDb -ne 'kkirichatvalidation202610191135f9f3' -or
    $completed.legacyDb -ne 'kkirichatlegacy202610191135f9f3' -or
    $completed.serverUuid -ne 'fc8cfa74-c4e9-11f0-bf28-00a554698b2d') {
    throw 'Unexpected completed manifest; stopped without connecting.'
}
$identity = [Security.Principal.WindowsIdentity]::GetCurrent()
$record = [ordered]@{
    diagnosisDate = (Get-Date).ToString('o')
    windowsIdentity = $identity.Name
    windowsSid = $identity.User.Value
    powershellVersion = $PSVersionTable.PSVersion.ToString()
    powershellEdition = $PSVersionTable.PSEdition
    is64BitProcess = [Environment]::Is64BitProcess
    powershell7Available = [bool](Get-Command pwsh -ErrorAction SilentlyContinue)
    completedAccount = $completed.username
    completedCredentialStorage = $completed.credentialStorage
    completedManifestHasCredentialFile = ($null -ne $completed.PSObject.Properties['credentialFile'])
    completedCredentialFileExists = (Test-Path -LiteralPath (Join-Path $workRoot '202610191135f9f3.credential.clixml'))
    passwordInProcess = [bool]$env:KKIRI_CHAT_TEST_PASSWORD
    credentialFiles = @()
    serverMetadataRequested = [bool]$ReadServerMetadata
    passwordResetPerformed = $false
    accountOrGrantChangesPerformed = $false
    databaseChangesPerformed = $false
    performanceOrMigrationRun = $false
}
foreach ($file in Get-ChildItem -LiteralPath $workRoot -Filter '*.clixml' -File) {
    $item = [ordered]@{ fileName=$file.Name; fileLength=$file.Length }
    try { $item.owner=(Get-Acl -LiteralPath $file.FullName).Owner } catch { $item.ownerRead='unavailable' }
    $rawXml=$null; $restored=$null
    try {
        $rawXml=[IO.File]::ReadAllText($file.FullName)
        [xml]$xml=$rawXml
        $item.passwordPropertyPresent=($xml.SelectNodes('//*[@N="Password"]').Count -gt 0)
        $item.secureStringElementCount=$xml.SelectNodes('//*[local-name()="SS"]').Count
        $item.dpapiPayloadMarkerPresent=($rawXml -match '(?i)01000000d08c9ddf0115d1118c7a00c04fc297eb')
        $restored=Import-Clixml -LiteralPath $file.FullName -ErrorAction Stop
        $item.importSucceeded=$true
        $item.restoredType=$restored.GetType().FullName
        $item.isUsablePSCredential=($restored -is [Management.Automation.PSCredential])
        $item.hasSecurePassword=($null -ne $restored.PSObject.Properties['Password'] -and $restored.Password -is [Security.SecureString])
        $item.usernameMatchesCompletedAccount=($restored.UserName -eq $completed.username)
        $item.usernameMatchesEarlierAccount=($restored.UserName -eq $earlier.username)
        $item.pathMatchesEarlierManifest=([IO.Path]::GetFullPath($earlier.credentialFile) -eq $file.FullName)
    } catch {
        $item.importSucceeded=$false
        $item.failureType=$_.Exception.GetType().FullName
        $item.rawErrorSuppressed=$true
    } finally { $rawXml=$null; $restored=$null; $xml=$null }
    $record.credentialFiles += $item
}

# SELECT-only diagnostic connection. Never invoke the provisioning or application runner.
function Invoke-MetadataRead([string] $Sql, [hashtable] $DbCredentials) {
    $start=New-Object Diagnostics.ProcessStartInfo
    $start.FileName=(Get-Command mysql -ErrorAction Stop).Source
    $start.Arguments='--no-defaults --protocol=TCP --host=127.0.0.1 --port=3306 --connect-timeout=5 --batch --raw --skip-column-names --user=' + $DbCredentials.DB_USERNAME
    $start.UseShellExecute=$false
    $start.CreateNoWindow=$true
    $start.RedirectStandardInput=$true
    $start.RedirectStandardOutput=$true
    $start.RedirectStandardError=$true
    $start.EnvironmentVariables['MYSQL_PWD']=$DbCredentials.DB_PASSWORD
    $process=New-Object Diagnostics.Process
    $process.StartInfo=$start
    try {
        [void]$process.Start()
        $process.StandardInput.WriteLine($Sql)
        $process.StandardInput.Close()
        $stdout=$process.StandardOutput.ReadToEnd()
        $stderr=$process.StandardError.ReadToEnd()
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) {
            if ($stderr -match 'ERROR\s+(\d+)') { throw ('MYSQL_CODE_' + $matches[1]) }
            throw 'MYSQL_CLIENT_FAILED_RAW_ERROR_SUPPRESSED'
        }
        return $stdout.Trim()
    } finally {
        $start.EnvironmentVariables.Remove('MYSQL_PWD')
        $process.Dispose()
        $stderr=$null
    }
}
if ($ReadServerMetadata) {
    $dbCredentials=@{}
    try {
        foreach ($line in [IO.File]::ReadLines((Join-Path $repoRoot 'backend/.env'))) {
            if ($line -match '^\s*(DB_URL|DB_USERNAME|DB_PASSWORD)\s*=\s*(.*)\s*$') {
                $dbCredentials[$matches[1]]=$matches[2].Trim().Trim('"').Trim("'")
            }
        }
        $line=$null
        if ($dbCredentials.DB_URL -notmatch '^jdbc:mysql://(?:localhost|127\.0\.0\.1):3306/(?:kkirikkiri|kkiri_kkiri)(?:\?|$)' -or
            $dbCredentials.DB_USERNAME -notmatch '^[A-Za-z0-9_]+$' -or !$dbCredentials.DB_PASSWORD) {
            throw 'UNEXPECTED_LOCAL_DB_CONFIGURATION_STOPPED'
        }
        $serverIdentity=(Invoke-MetadataRead 'SELECT @@server_uuid,@@hostname,VERSION(),@@port;' $dbCredentials) -split "`t"
        if ($serverIdentity.Count -ne 4 -or $serverIdentity[0] -ne $completed.serverUuid -or
            $serverIdentity[1] -ne $completed.hostname -or $serverIdentity[2] -ne '8.0.44' -or $serverIdentity[3] -ne '3306') {
            throw 'SERVER_IDENTITY_MISMATCH_STOPPED'
        }
        $record.serverIdentityVerified=$true
        $record.serverUuid=$serverIdentity[0]
        # No authentication_string/password hash, business data, or SHOW CREATE USER is requested.
        $accountJson=Invoke-MetadataRead "SELECT JSON_OBJECT('user',User,'host',Host,'plugin',plugin,'locked',account_locked,'passwordExpired',password_expired) FROM mysql.user WHERE User IN ('kkirichattest_1135f9f3','kkirichattest_5a7b340c');" $dbCredentials
        $record.testAccounts=@($accountJson -split '\r?\n' | Where-Object { $_ } | ForEach-Object { $_ | ConvertFrom-Json })
        $schemaNames=Invoke-MetadataRead "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME IN ('kkirichatvalidation202610191135f9f3','kkirichatlegacy202610191135f9f3','kkirichatvalidation202610195a7b340c','kkirichatlegacy202610195a7b340c');" $dbCredentials
        $record.testSchemas=@($schemaNames -split '\r?\n' | Where-Object { $_ })
        foreach ($account in $record.testAccounts) {
            if ($account.user -eq $completed.username -and $account.host -eq '127.0.0.1') {
                $record.completedAccountGrants=@((Invoke-MetadataRead "SHOW GRANTS FOR 'kkirichattest_1135f9f3'@'127.0.0.1';" $dbCredentials) -split '\r?\n')
            }
        }
        $record.serverRoleConfiguration=(Invoke-MetadataRead "SELECT JSON_OBJECT('mandatoryRoles',@@global.mandatory_roles,'skipNameResolve',@@global.skip_name_resolve);" $dbCredentials | ConvertFrom-Json)
        $record.inheritedRoleCount=[int](Invoke-MetadataRead "SELECT COUNT(*) FROM mysql.role_edges WHERE TO_USER='kkirichattest_1135f9f3';" $dbCredentials)
        $record.defaultRoleCount=[int](Invoke-MetadataRead "SELECT COUNT(*) FROM mysql.default_roles WHERE USER='kkirichattest_1135f9f3';" $dbCredentials)
        $record.serverMetadataResult='READ_SUCCESS'
    } catch {
        $record.serverMetadataResult='FAILED_OR_STOPPED'
        $classification=$_.Exception.Message
        $record.failureClassification=if($classification -match '^(MYSQL_CODE_[0-9]+|[A-Z_]+)$'){$classification}else{'UNCLASSIFIED_RAW_ERROR_SUPPRESSED'}
    } finally { $dbCredentials.Clear(); $line=$null; $classification=$null }
}
$record | ConvertTo-Json -Depth 7
