function Invoke-Native([string]$File,[string[]]$Arguments,[hashtable]$Environment,[string]$InputText='', [string]$InputFile='') {
    $info=[Diagnostics.ProcessStartInfo]::new(); $info.FileName=$File
    $quoted=@(foreach($argument in $Arguments) {
        if($argument -match '["\r\n]' -or $argument.EndsWith('\')) { throw 'Unsupported native argument' }
        '"'+$argument+'"'
    })
    $info.Arguments=$quoted -join ' '; $info.WorkingDirectory=$backend; $info.UseShellExecute=$false
    $info.RedirectStandardInput=$true; $info.RedirectStandardOutput=$true; $info.RedirectStandardError=$true
    $info.StandardOutputEncoding=[Text.UTF8Encoding]::new($false); $info.StandardErrorEncoding=[Text.UTF8Encoding]::new($false)
    foreach($key in $Environment.Keys) { $info.EnvironmentVariables[$key]=$Environment[$key] }
    $process=[Diagnostics.Process]::Start($info)
    $output=$process.StandardOutput.ReadToEndAsync(); $errors=$process.StandardError.ReadToEndAsync()
    if($InputFile) { $stream=[IO.File]::OpenRead($InputFile); try { $stream.CopyTo($process.StandardInput.BaseStream) } finally { $stream.Dispose() } }
    elseif($InputText) { $bytes=[Text.Encoding]::UTF8.GetBytes($InputText); $process.StandardInput.BaseStream.Write($bytes,0,$bytes.Length) }
    $process.StandardInput.Close(); $process.WaitForExit()
    $safe=$output.Result+[Environment]::NewLine+$errors.Result
    foreach($secret in (@($password,$sourcePassword,$env:DEMO_NEW_USER_PASSWORD)+@($privatePasswords))) { if($secret) { $safe=$safe.Replace($secret,'<redacted>') } }
    if($process.ExitCode -ne 0) {
        $safe | Set-Content -Encoding UTF8 (Join-Path $logs 'last-failure.log')
        throw 'Data step failed. Inspect the private build/demo50 log. Never delete the receipt or retry an incomplete apply.'
    }
    return $output.Result
}
