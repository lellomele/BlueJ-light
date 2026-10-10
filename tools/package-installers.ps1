# BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained.
param(
    [Parameter(Mandatory=$true)][string]$FullImagePath,
    [Parameter(Mandatory=$true)][string]$CompilerPath,
    [Parameter(Mandatory=$true)][string]$MingwPath,
    [Parameter(Mandatory=$true)][string]$OutputDirectory
)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$full=(Resolve-Path -LiteralPath $FullImagePath).Path
$compiler=(Resolve-Path -LiteralPath $CompilerPath).Path
$mingw=(Resolve-Path -LiteralPath $MingwPath).Path
$output=[System.IO.Path]::GetFullPath($OutputDirectory)
$build=Join-Path $project 'bench/installers'
$external=Join-Path $build 'without-jdk'
[System.IO.Directory]::CreateDirectory($external) | Out-Null
[System.IO.Directory]::CreateDirectory($output) | Out-Null
Copy-Item -LiteralPath (Join-Path $full 'app'),(Join-Path $full 'doc') -Destination $external -Recurse -Force
Copy-Item -LiteralPath (Join-Path $full 'README.md'),(Join-Path $full 'LICENSE.txt') -Destination $external
Copy-Item -LiteralPath (Join-Path $full 'LICENSING.md') -Destination $external
Copy-Item -LiteralPath (Join-Path $full 'README.it.md') -Destination $external
Copy-Item -LiteralPath (Join-Path $full 'examples') -Destination $external -Recurse -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'windows/no-jdk-info.txt') -Destination (Join-Path $external 'LEGGIMI-JDK.txt')
Push-Location (Join-Path $PSScriptRoot 'windows')
try {
    & (Join-Path $mingw 'windres.exe') 'external-jdk-launcher.rc' (Join-Path $build 'launcher-resource.o')
    if($LASTEXITCODE -ne 0){throw 'Cannot compile launcher resources.'}
    & (Join-Path $mingw 'g++.exe') '-std=c++17' '-O2' '-Wall' '-Wextra' '-static' '-municode' '-mwindows' `
        'external-jdk-launcher.cpp' (Join-Path $build 'launcher-resource.o') '-o' (Join-Path $external 'BlueJ light.exe') `
        '-lshell32' '-lole32' '-luuid' '-ladvapi32'
    if($LASTEXITCODE -ne 0){throw 'Cannot compile external-JDK launcher.'}
} finally { Pop-Location }
foreach($variant in @('completo','senza-JDK')) {
    $image=if($variant -eq 'completo'){$full}else{$external}
    & $compiler '/Q' "/DImagePath=$image" "/DOutputPath=$output" "/DVariant=$variant" (Join-Path $PSScriptRoot 'windows/setup.iss')
    if($LASTEXITCODE -ne 0){throw "Cannot compile $variant installer."}
}
Get-ChildItem -LiteralPath $output -Filter 'BlueJ-light-5.6.2-win64-*.exe' | Select-Object Name,Length
