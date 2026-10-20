param([switch] $ResetApprovedTestPassword)
$ErrorActionPreference = 'Stop'
$repoRoot=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$workRoot=Join-Path $repoRoot 'backend/build/chat-mysql-validation'
$credentialPath=Join-Path $workRoot '202610191135f9f3.credential.clixml'
$metadataPath=Join-Path $workRoot '202610191135f9f3.credential-metadata.json'
$evidencePath=Join-Path $workRoot ('auth-check-' + [Guid]::NewGuid().ToString('N') + '.json')
$accountName='kkirichattest_1135f9f3'
$expectedAccount=$accountName + '@127.0.0.1'
$schemas=@('kkirichatvalidation202610191135f9f3','kkirichatlegacy202610191135f9f3')
$expectedUuid='fc8cfa74-c4e9-11f0-bf28-00a554698b2d'
$manifest=Get-Content -LiteralPath (Join-Path $workRoot '202610191135f9f3.environment.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if ($manifest.username -ne $accountName -or $manifest.currentDb -ne $schemas[0] -or
    $manifest.legacyDb -ne $schemas[1] -or $manifest.serverUuid -ne $expectedUuid -or
    $manifest.host -ne '127.0.0.1' -or $manifest.port -ne 3306 -or $manifest.accountHost -ne '127.0.0.1' -or
    $manifest.state -ne 'created-retain-no-automatic-deletion') {
    throw 'Unexpected approved environment; stopped.'
}
$record=[ordered]@{
    time=(Get-Date).ToString('o'); account=$expectedAccount
    mode=if($ResetApprovedTestPassword){'explicitly-approved-password-reset'}else{'verify-existing-DPAPI-only'}
    windowsIdentity=[Security.Principal.WindowsIdentity]::GetCurrent().Name
    windowsSid=[Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    credentialFile=$credentialPath; credentialRoundTrip=$false; phase='preflight'
    passwordResetAttempted=$false; passwordResetCompleted=$false
    accountHostPluginOrGrantChangesPerformed=$false; databaseChangesPerformed=$false
    performanceOrMigrationRun=$false; logins=@(); protectedSchemaChecks=@(); passed=$false
}

function Invoke-AuthSql([string] $Username, [string] $Password, [string] $Database, [string] $Sql) {
    if ($Username -notmatch '^[a-zA-Z0-9_]+$' -or ($Database -and $Database -notin $schemas)) {
        throw 'Unexpected client account/schema; stopped.'
    }
    $start=New-Object Diagnostics.ProcessStartInfo
    $start.FileName=(Get-Command mysql -ErrorAction Stop).Source
    $start.Arguments='--no-defaults --protocol=TCP --host=127.0.0.1 --port=3306 --connect-timeout=5 --batch --raw --skip-column-names --user=' + $Username
    if ($Database) { $start.Arguments += ' --database=' + $Database }
    $start.UseShellExecute=$false; $start.CreateNoWindow=$true
    $start.RedirectStandardInput=$true; $start.RedirectStandardOutput=$true; $start.RedirectStandardError=$true
    $start.EnvironmentVariables['MYSQL_PWD']=$Password
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
        $process.Dispose(); $stderr=$null
    }
}

function Confirm-ExactGrants([string[]] $Grants) {
    $expected=@(
        'GRANT USAGE ON *.* TO `kkirichattest_1135f9f3`@`127.0.0.1`',
        'GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, REFERENCES, INDEX, ALTER ON `kkirichatlegacy202610191135f9f3`.* TO `kkirichattest_1135f9f3`@`127.0.0.1`',
        'GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, REFERENCES, INDEX, ALTER ON `kkirichatvalidation202610191135f9f3`.* TO `kkirichattest_1135f9f3`@`127.0.0.1`'
    )
    if ($Grants.Count -ne 3 -or (Compare-Object ($expected | Sort-Object) ($Grants | Sort-Object))) {
        throw 'UNEXPECTED_GRANTS_STOPPED'
    }
}

$adminCredentials=@{}; $testCredential=$null; $testPassword=$null; $securePassword=$null; $resetSql=$null
try {
    if ($ResetApprovedTestPassword) {
        # This switch requires explicit user approval for exactly this one pre-existing account.
        if ((Test-Path -LiteralPath $credentialPath) -or (Test-Path -LiteralPath $metadataPath)) {
            throw 'CREDENTIAL_ALREADY_EXISTS_USE_VERIFY_ONLY_NO_AUTOMATIC_SECOND_RESET'
        }
        $diagnosis=(& (Join-Path $PSScriptRoot 'diagnose-auth.ps1') -ReadServerMetadata | Out-String) | ConvertFrom-Json
        if ($diagnosis.serverMetadataResult -ne 'READ_SUCCESS' -or !$diagnosis.serverIdentityVerified -or
            $diagnosis.serverRoleConfiguration.mandatoryRoles -ne '' -or $diagnosis.inheritedRoleCount -ne 0 -or $diagnosis.defaultRoleCount -ne 0) {
            throw 'SERVER_OR_ROLE_PREFLIGHT_FAILED_NO_RESET'
        }
        Confirm-ExactGrants @($diagnosis.completedAccountGrants)
        $target=@($diagnosis.testAccounts | Where-Object { $_.user -eq $accountName -and $_.host -eq '127.0.0.1' })
        if ($target.Count -ne 1 -or $target[0].plugin -ne 'caching_sha2_password' -or $target[0].locked -ne 'N' -or $target[0].passwordExpired -ne 'N') {
            throw 'ACCOUNT_STATE_CHANGED_NO_RESET'
        }
        foreach ($schemaName in $schemas) { if ($schemaName -notin $diagnosis.testSchemas) { throw 'MISSING_TEST_SCHEMA_NO_RESET' } }
        foreach ($line in [IO.File]::ReadLines((Join-Path $repoRoot 'backend/.env'))) {
            if ($line -match '^\s*(DB_URL|DB_USERNAME|DB_PASSWORD)\s*=\s*(.*)\s*$') {
                $adminCredentials[$matches[1]]=$matches[2].Trim().Trim('"').Trim("'")
            }
        }
        $line=$null
        if ($adminCredentials.DB_URL -notmatch '^jdbc:mysql://(?:localhost|127\.0\.0\.1):3306/(?:kkirikkiri|kkiri_kkiri)(?:\?|$)' -or
            $adminCredentials.DB_USERNAME -notmatch '^[A-Za-z0-9_]+$' -or !$adminCredentials.DB_PASSWORD) {
            throw 'UNEXPECTED_LOCAL_DB_CONFIGURATION_NO_RESET'
        }
        $bytes=New-Object byte[] 36
        $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
        $testPassword=[Convert]::ToBase64String($bytes) + 'aA1!'
        [Array]::Clear($bytes,0,$bytes.Length)
        $securePassword=ConvertTo-SecureString -String $testPassword -AsPlainText -Force
        $testCredential=New-Object Management.Automation.PSCredential($accountName,$securePassword)
        # Persist and prove decryption BEFORE changing MySQL. Never overwrite an existing artifact.
        $record.phase='DPAPI-export-before-reset'
        $testCredential | Export-Clixml -LiteralPath $credentialPath -NoClobber
        $fileAcl=Get-Acl -LiteralPath $credentialPath
        $fileAcl.SetAccessRuleProtection($true,$false)
        $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
        $rule=New-Object Security.AccessControl.FileSystemAccessRule($sid,'FullControl','Allow')
        $fileAcl.SetAccessRule($rule)
        Set-Acl -LiteralPath $credentialPath -AclObject $fileAcl
        $restored=Import-Clixml -LiteralPath $credentialPath -ErrorAction Stop
        if ($restored -isnot [Management.Automation.PSCredential] -or $restored.UserName -ne $accountName -or
            $restored.GetNetworkCredential().Password -cne $testPassword) {
            throw 'DPAPI_ROUND_TRIP_FAILED_NO_RESET'
        }
        $restored=$null
        $record.credentialRoundTrip=$true
        $record.phase='DPAPI-round-trip-passed-before-reset'
        $credentialMetadata=[ordered]@{
            account=$expectedAccount; credentialFile=$credentialPath; windowsIdentity=$record.windowsIdentity
            windowsSid=$record.windowsSid; state='staged-before-approved-reset'; createdAt=(Get-Date).ToString('o')
            storage='Windows-DPAPI-PSCredential'; passwordInLogsOrManifest=$false
        }
        $credentialMetadata | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding UTF8
        # Fixed account/Host/plugin, cryptographically generated SQL-safe password. No GRANT/DB DDL/DML.
        if ($testPassword -notmatch '^[A-Za-z0-9+/=]+aA1!$') { throw 'UNEXPECTED_GENERATED_PASSWORD_SHAPE_NO_RESET' }
        $resetSql="ALTER USER 'kkirichattest_1135f9f3'@'127.0.0.1' IDENTIFIED WITH caching_sha2_password BY '$testPassword';"
        $record.phase='approved-password-reset'
        $record.passwordResetAttempted=$true
        $null=Invoke-AuthSql $adminCredentials.DB_USERNAME $adminCredentials.DB_PASSWORD '' $resetSql
        $record.passwordResetCompleted=$true
        $resetSql=$null
        $credentialMetadata.state='approved-reset-completed-verification-pending'
        $credentialMetadata | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding UTF8
    } else {
        $testCredential=Import-Clixml -LiteralPath $credentialPath -ErrorAction Stop
        if ($testCredential -isnot [Management.Automation.PSCredential] -or $testCredential.UserName -ne $accountName) {
            throw 'CREDENTIAL_TYPE_OR_ACCOUNT_MISMATCH_NO_LOGIN'
        }
        $testPassword=$testCredential.GetNetworkCredential().Password
        if (!$testPassword) { throw 'EMPTY_RECOVERED_PASSWORD_NO_LOGIN' }
        $record.credentialRoundTrip=$true
    }
    $record.phase='dedicated-account-login-and-grants'
    foreach ($schemaName in $schemas) {
        $lines=(Invoke-AuthSql $accountName $testPassword $schemaName "SELECT JSON_OBJECT('database',DATABASE(),'account',CURRENT_USER(),'loginUser',USER(),'role',CAST(CURRENT_ROLE() AS CHAR CHARACTER SET utf8mb4),'mandatoryRoles',CAST(@@global.mandatory_roles AS CHAR CHARACTER SET utf8mb4),'serverUuid',@@server_uuid,'version',VERSION(),'port',@@port); SHOW GRANTS;") -split '\r?\n'
        $proof=$lines[0] | ConvertFrom-Json
        $record.logins += $proof
        if ($proof.database -ne $schemaName -or $proof.account -ne $expectedAccount -or $proof.role -ne 'NONE' -or $proof.mandatoryRoles -ne '' -or
            $proof.serverUuid -ne $expectedUuid -or $proof.version -ne '8.0.44' -or $proof.port -ne 3306) {
            throw 'LOGIN_IDENTITY_SCHEMA_OR_ROLE_MISMATCH'
        }
        Confirm-ExactGrants @($lines | Select-Object -Skip 1)
    }
    foreach ($protectedSchema in @('kkirikkiri','kkiri_kkiri')) {
        try {
            $null=Invoke-AuthSql $accountName $testPassword $schemas[0] ('SELECT * FROM `' + $protectedSchema + '`.chat_message LIMIT 0;')
            throw 'PROTECTED_SCHEMA_UNEXPECTED_ACCESS'
        } catch {
            if ($_.Exception.Message -notmatch '^MYSQL_CODE_(1044|1142)$') { throw }
            $record.protectedSchemaChecks += [ordered]@{ schema=$protectedSchema; selectLimitZeroDenied=$true; sqlCode=$_.Exception.Message }
        }
    }
    $record.requiredSchemaGrantsVerified=$true
    $record.noProtectedDatabaseWriteGrants=$true
    $record.passed=$true
    $record.phase='completed'
    if (Test-Path -LiteralPath $metadataPath) {
        if (!$ResetApprovedTestPassword) {
            $credentialMetadata=Get-Content -LiteralPath $metadataPath -Raw -Encoding UTF8 | ConvertFrom-Json
        }
        $credentialMetadata.state='approved-reset-and-native-client-verification-completed'
        $credentialMetadata | ConvertTo-Json | Set-Content -LiteralPath $metadataPath -Encoding UTF8
    }
} catch {
    $classification=$_.Exception.Message
    $record.failureClassification=if($classification -match '^(MYSQL_CODE_[0-9]+|[A-Z_]+)$'){$classification}else{'UNCLASSIFIED_RAW_ERROR_SUPPRESSED'}
    $record.failureType=$_.Exception.GetType().FullName
    $record.failureHResult=$_.Exception.HResult
} finally {
    $adminCredentials.Clear(); $testPassword=$null; $resetSql=$null; $testCredential=$null; $restored=$null
    if ($securePassword) { $securePassword.Dispose() }
    $record | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $evidencePath -Encoding UTF8
    $record | ConvertTo-Json -Depth 6
    Write-Output ('Evidence: ' + $evidencePath)
}
if (!$record.passed) { exit 1 }
