# Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception.
param(
    [Parameter(Mandatory=$true)][string]$ImagePath,
    [string]$WorkName='crash-recovery-verification'
)
$ErrorActionPreference='Stop'
if($WorkName -notmatch '^[a-z0-9-]+$'){throw 'Invalid verification name'}
$repository=Split-Path $PSScriptRoot -Parent
$image=(Resolve-Path -LiteralPath $ImagePath).Path
$work=Join-Path $repository "bench/$WorkName"
if(Test-Path -LiteralPath $work){throw 'Use a fresh verification directory'}
[IO.Directory]::CreateDirectory($work) | Out-Null
$classes=Join-Path $work 'classes'
[IO.Directory]::CreateDirectory($classes) | Out-Null
$fixture=Join-Path $work 'SmokeProject'
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'SmokeProject') -Destination $fixture -Recurse
$homeDir=Join-Path $work 'home'
[IO.Directory]::CreateDirectory($homeDir) | Out-Null
$java=Join-Path $image 'runtime/bin'
$sources=@('BootSmoke','AdvancedSmoke','EditorCompletionSmoke','CrashRecoverySmoke') |
    ForEach-Object { Join-Path $PSScriptRoot "$_.java" }
& "$java/javac.exe" -cp "$image/app/*" -d $classes @sources
if($LASTEXITCODE -ne 0){throw 'Cannot compile recovery harness'}
$harness=Join-Path $work 'crash-recovery.jar'
& "$java/jar.exe" --create --file $harness -C $classes .
if($LASTEXITCODE -ne 0){throw 'Cannot package recovery harness'}
$config=Join-Path $image 'app/BlueJ light.cfg'
$original=[IO.File]::ReadAllBytes($config)
$process=$null
try {
    $test=[Text.Encoding]::UTF8.GetString($original).Replace('app.mainclass=bluej.Boot','app.mainclass=CrashRecoverySmoke')
    $test=$test.Replace('[JavaOptions]',('app.classpath='+$harness.Replace('\','/')+"`n`n[JavaOptions]"))
    [IO.File]::WriteAllText($config,$test,[Text.UTF8Encoding]::new($false))
    foreach($phase in @('checkpoint','recover')){
        $process=Start-Process -FilePath "$image/BlueJ light.exe" -WorkingDirectory $repository `
            -ArgumentList @("`"$fixture`"","`"$homeDir`"",$phase) -WindowStyle Hidden -PassThru
        if(!$process.WaitForExit(90000)){$process.Kill($true);throw "Recovery phase timed out: $phase"}
        $expectedExit=if($phase -eq 'checkpoint'){91}else{0}
        $log=Join-Path $homeDir 'bluej-light/bluej-debuglog.txt'
        $result=Get-Content -LiteralPath $log -Raw
        if($process.ExitCode -ne $expectedExit){Get-Content -LiteralPath $log -Tail 60;throw "Unexpected $phase exit: $($process.ExitCode)"}
        $marker=if($phase -eq 'checkpoint'){'CRASH_CHECKPOINT_OK'}else{'CRASH_RECOVERY_OK'}
        if(!$result.Contains($marker)){throw "Missing $marker"}
        $result -split "`n" | Where-Object { $_.Contains($marker) }
    }
    if([IO.File]::ReadAllText("$fixture/School.java") -ne [IO.File]::ReadAllText("$homeDir/checkpoint.java.txt")){
        throw 'Normal exit did not save restored source'
    }
    if(Test-Path -LiteralPath "$homeDir/bluej-light/recovery-session.properties"){
        throw 'Normal exit left an unclean session journal'
    }
    'CRASH_SAVED_OK recovered source saved on normal exit'
}
finally {
    if($process -and !$process.HasExited){$process.Kill($true)}
    [IO.File]::WriteAllBytes($config,$original)
}
