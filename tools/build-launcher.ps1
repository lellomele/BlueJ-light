# Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GPLv2 with Classpath Exception.
param(
    [Parameter(Mandatory=$true)][string]$Destination,
    [string]$MingwPath='C:/msys64/mingw64/bin'
)
$ErrorActionPreference='Stop'
$destinationPath=[System.IO.Path]::GetFullPath($Destination)
$build=Join-Path (Split-Path $PSScriptRoot -Parent) 'bench/launcher'
$compiled=Join-Path $build 'BlueJ light.exe'
[System.IO.Directory]::CreateDirectory($build) | Out-Null
Push-Location (Join-Path $PSScriptRoot 'windows')
try {
    & (Join-Path $MingwPath 'windres.exe') 'external-jdk-launcher.rc' (Join-Path $build 'resource.o')
    if($LASTEXITCODE -ne 0){throw 'Cannot compile launcher resource.'}
    & (Join-Path $MingwPath 'g++.exe') '-std=c++17' '-O2' '-Wall' '-Wextra' '-static' '-municode' '-mwindows' `
        'external-jdk-launcher.cpp' (Join-Path $build 'resource.o') '-o' $compiled `
        '-lshell32' '-lole32' '-luuid' '-ladvapi32'
    if($LASTEXITCODE -ne 0){throw 'Cannot compile launcher.'}
    Copy-Item -LiteralPath $compiled -Destination $destinationPath -Force
} finally { Pop-Location }
