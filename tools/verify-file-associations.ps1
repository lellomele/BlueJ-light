# Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-12. GPLv2 with Classpath Exception.
param(
    [Parameter(Mandatory=$true)][string]$InstallerDirectory,
    [string]$ExternalJdkPath,
    [string]$WorkName='file-associations-verification'
)
$ErrorActionPreference='Stop'
if($WorkName -notmatch '^[a-z0-9-]+$'){throw 'Invalid verification name'}
$repository=Split-Path $PSScriptRoot -Parent
$work=Join-Path $repository "bench/$WorkName"
if(Test-Path -LiteralPath $work){throw 'Use a fresh verification directory'}
[IO.Directory]::CreateDirectory($work)|Out-Null
$installers=(Resolve-Path -LiteralPath $InstallerDirectory).Path
$base=[Microsoft.Win32.RegistryKey]::OpenBaseKey([Microsoft.Win32.RegistryHive]::CurrentUser,[Microsoft.Win32.RegistryView]::Registry64)
$prefix='Software\BlueJLightInstallerVerification'
$existing=$base.OpenSubKey($prefix)
if($null -ne $existing){$existing.Dispose();$base.Dispose();throw 'Verification registry area already exists; leave it intact'}
$created=$false
function Value([string]$path,[string]$name){
    $key=$base.OpenSubKey("$prefix\$path")
    if($null -eq $key){throw "Missing verification key: $path"}
    try { return $key.GetValue($name,$null) } finally { $key.Dispose() }
}
function Install([string]$variant,[string]$destination,[bool]$register){
    $installer=Join-Path $installers "BlueJ-light-5.7.0-win64-$variant.exe"
    $tasks=if($register){'registerbluej'}else{'!registerbluej'}
    $process=Start-Process -FilePath $installer -ArgumentList @('/VERYSILENT','/SUPPRESSMSGBOXES','/NORESTART','/CURRENTUSER','/verify=1',"/TASKS=$tasks", "/DIR=`"$destination`"", "/LOG=`"$work/$variant-$register.log`"") -WindowStyle Hidden -PassThru
    if(!$process.WaitForExit(180000)){$process.Kill($true);throw 'Installer timed out'}
    if($process.ExitCode -ne 0){throw "Installer failed: $variant exit=$($process.ExitCode)"}
}
function Uninstall([string]$destination){
    $uninstaller=Join-Path $destination 'unins000.exe'
    $process=Start-Process -FilePath $uninstaller -ArgumentList @('/VERYSILENT','/SUPPRESSMSGBOXES','/NORESTART','/verify=1') -WindowStyle Hidden -PassThru
    if(!$process.WaitForExit(90000)){$process.Kill($true);throw 'Uninstaller timed out'}
    if($process.ExitCode -ne 0){throw "Uninstaller failed: $($process.ExitCode)"}
}
try {
    $key=$base.CreateSubKey($prefix);$key.Dispose();$created=$true
    $key=$base.CreateSubKey("$prefix\Classes\.bluej")
    try {$key.SetValue('', 'ExistingBlueJ.Project')} finally {$key.Dispose()}
    $key=$base.CreateSubKey("$prefix\Classes\.bluej\OpenWithProgids")
    try {$key.SetValue('ExistingBlueJ.Project','')} finally {$key.Dispose()}
    $key=$base.CreateSubKey("$prefix\RegisteredApplications")
    try {$key.SetValue('Existing BlueJ','Software\ExistingBlueJ\Capabilities')} finally {$key.Dispose()}
    foreach($variant in @('with-jdk','without-jdk')){
        $destination=Join-Path $work "Installed $variant"
        Install $variant $destination $false
        $key=$base.OpenSubKey("$prefix\Classes\BlueJLight.Project")
        if($null -ne $key){$key.Dispose();throw 'Registration created with task disabled'}
        Install $variant $destination $true
        $expected='"'+(Join-Path $destination 'BlueJ light.exe')+'" "%1"'
        if((Value 'Classes\BlueJLight.Project\shell\open\command' '') -ne $expected){throw 'Incorrect project command quoting'}
        if((Value 'Classes\Applications\BlueJ light.exe\shell\open\command' '') -ne $expected){throw 'Incorrect Open with command'}
        if((Value 'Classes\BlueJLight.Project\DefaultIcon' '') -ne ('"'+(Join-Path $destination 'BlueJ light.exe')+'",0')){throw 'Incorrect icon'}
        if((Value 'Classes\.bluej\OpenWithProgids' 'BlueJLight.Project') -ne ''){throw 'Missing project candidate'}
        if((Value 'BlueJLight\Capabilities\FileAssociations' '.bluej') -ne 'BlueJLight.Project'){throw 'Missing default-app capability'}
        if((Value 'RegisteredApplications' 'BlueJ light') -ne "$prefix\BlueJLight\Capabilities"){throw 'Incorrect application registration'}
        if((Value 'Classes\.bluej' '') -ne 'ExistingBlueJ.Project'){throw 'Existing default was overwritten'}
        if((Value 'Classes\.bluej\OpenWithProgids' 'ExistingBlueJ.Project') -ne ''){throw 'Other handler removed'}
        $parameters=@{ImagePath=$destination;OpenProjectFile=$true;WorkName="$WorkName-open-$variant"}
        if($variant -eq 'without-jdk'){
            if(!$ExternalJdkPath){throw 'External JDK is required for without-JDK verification'}
            $parameters.ExternalJdkPath=$ExternalJdkPath
        }
        & (Join-Path $PSScriptRoot 'verify-windows.ps1') @parameters
        Uninstall $destination
        foreach($path in @('Classes\BlueJLight.Project','Classes\Applications\BlueJ light.exe','BlueJLight\Capabilities')){
            $key=$base.OpenSubKey("$prefix\$path")
            if($null -ne $key){$key.Dispose();throw "Private registration survived uninstall: $path"}
        }
        if((Value 'Classes\.bluej' '') -ne 'ExistingBlueJ.Project'){throw 'Uninstall changed existing default'}
        if((Value 'RegisteredApplications' 'Existing BlueJ') -ne 'Software\ExistingBlueJ\Capabilities'){throw 'Uninstall removed another application'}
        $key=$base.OpenSubKey("$prefix\Classes\.bluej\OpenWithProgids")
        try {if($key.GetValueNames() -contains 'BlueJLight.Project'){throw 'Open with value survived uninstall'}} finally {$key.Dispose()}
        "FILE_ASSOCIATIONS_OK $variant disabledTask=true registration=true quotedPaths=true projectFileOpen=true priorDefaultPreserved=true uninstallCleanup=true"
    }
}
finally {
    if($created){$base.DeleteSubKeyTree($prefix)}
    $base.Dispose()
}
