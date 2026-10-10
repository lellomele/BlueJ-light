# Copyright (C) 2026 Prof. Ing. Raffaele Mele. GPLv2 with Classpath Exception.
param([string]$MingwPath='C:/msys64/mingw64/bin')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$sources=Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'thirdparty/astyle-3.6.19/src') -Filter '*.cpp'
$destination=Join-Path $project 'bluej/lib/formatter/astyle.exe'
& (Join-Path $MingwPath 'g++.exe') '-std=c++17' '-O2' '-s' '-static' @($sources.FullName) '-o' $destination
if($LASTEXITCODE -ne 0){throw 'Cannot build bundled Java formatter'}
& $destination '--version'
