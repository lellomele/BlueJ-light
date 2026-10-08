# BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained.
param(
    [Parameter(Mandatory=$true)][string]$ImagePath,
    [string]$ExternalJdkPath,
    [switch]$PortablePreferences,
    [string]$WorkName='windows-verification'
)
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path $PSScriptRoot -Parent
$image = (Resolve-Path -LiteralPath $ImagePath).Path
$javaBin = if($ExternalJdkPath){Join-Path (Resolve-Path -LiteralPath $ExternalJdkPath).Path 'bin'}else{Join-Path $image 'runtime/bin'}
if($WorkName -notmatch '^[a-z0-9-]+$'){throw 'Invalid verification name.'}
$work = Join-Path $projectDir ('bench/'+$WorkName)
[System.IO.Directory]::CreateDirectory($work) | Out-Null
$classes = Join-Path $work 'classes'
[System.IO.Directory]::CreateDirectory($classes) | Out-Null
$fixture = Join-Path $work 'SmokeProject'
if (Test-Path -LiteralPath $fixture) { throw 'Use a fresh verification directory.' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'SmokeProject') -Destination $fixture -Recurse
$homeDir = if($PortablePreferences){Join-Path $image 'data'}else{Join-Path $work 'home'}
[System.IO.Directory]::CreateDirectory($homeDir) | Out-Null
& (Join-Path $javaBin 'javac.exe') -cp (Join-Path $image 'app/*') -d $classes (Join-Path $PSScriptRoot 'BootSmoke.java')
if ($LASTEXITCODE -ne 0) { throw 'Cannot compile verification harness.' }
$harness = Join-Path $work 'smoke-workflow.jar'
& (Join-Path $javaBin 'jar.exe') --create --file $harness -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Cannot package verification harness.' }
$config = Join-Path $image 'app/BlueJ light.cfg'
$original = [System.IO.File]::ReadAllBytes($config)
$previousJdk = $env:BLUEJ_LIGHT_JDK
try {
    if($ExternalJdkPath){$env:BLUEJ_LIGHT_JDK=(Resolve-Path -LiteralPath $ExternalJdkPath).Path}
    $testConfig = [System.Text.Encoding]::UTF8.GetString($original).Replace('app.mainclass=bluej.Boot','app.mainclass=BootSmoke')
    $testConfig = $testConfig.Replace('[JavaOptions]',('app.classpath=' + $harness.Replace('\','/') + "`n`n[JavaOptions]"))
    [System.IO.File]::WriteAllText($config, $testConfig, [System.Text.UTF8Encoding]::new($false))
    $process = Start-Process -FilePath (Join-Path $image 'BlueJ light.exe') -WorkingDirectory $projectDir `
        -ArgumentList @("`"$fixture`"", "`"$(if($PortablePreferences){'@portable'}else{$homeDir})`"") -WindowStyle Hidden -PassThru
    if (!$process.WaitForExit(180000)) {
        Stop-Process -Id $process.Id
        throw 'Packaged workflow verification timed out.'
    }
    if ($process.ExitCode -ne 0) { throw "Packaged application exited with code $($process.ExitCode)." }
    $log = Join-Path $homeDir 'bluej-light/bluej-debuglog.txt'
    $result = Get-Content -LiteralPath $log -Raw
    if (!$result.Contains('SMOKE_OK') -or $result.Contains('IllegalAccessError')) {
        throw "Packaged workflow failed. Log: $log"
    }
    Write-Output ($result -split "`n" | Where-Object { $_ -match 'SMOKE_OK' })
    $properties = Get-Content -LiteralPath (Join-Path $homeDir 'bluej-light/bluej.properties') -Raw
    if ($properties -match '(?m)^(blackbox\.|bluej\.uid|session\.numeditors\.)') {
        throw 'Fresh configuration contains telemetry properties.'
    }
    Write-Output 'PRIVACY_OK no telemetry properties created'
}
finally {
    $env:BLUEJ_LIGHT_JDK=$previousJdk
    [System.IO.File]::WriteAllBytes($config, $original)
}
