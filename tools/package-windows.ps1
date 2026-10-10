# BlueJ light modifications, Copyright (C) 2026 Prof. Ing. Raffaele Mele. Modified 2026-10-08. GNU GPLv2 with Classpath Exception; original notices retained.
param(
    [Parameter(Mandatory=$true)][string]$JdkPath,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [string]$MingwPath='C:/msys64/mingw64/bin'
)
$ErrorActionPreference = 'Stop'
$projectDir = Split-Path $PSScriptRoot -Parent
$jdk = (Resolve-Path -LiteralPath $JdkPath).Path
$destination = [System.IO.Path]::GetFullPath($OutputDirectory)
$imagePath = Join-Path $destination 'BlueJ light'
if (Test-Path -LiteralPath $imagePath) { throw "Destination already exists: $imagePath" }
$libDir = Join-Path $projectDir 'bluej/build/resources/main/lib'
if (!(Test-Path -LiteralPath (Join-Path $libDir 'boot.jar'))) { throw 'Build :bluej:assemble first.' }
& (Join-Path $jdk 'bin/jpackage.exe') --type app-image --name 'BlueJ light' `
    --app-version '5.6.2' --vendor 'Prof. Ing. Raffaele Mele' `
    --copyright "$([char]0xA9) 2026 - Prof. Ing. Raffaele Mele" `
    --input $libDir --main-jar boot.jar --main-class bluej.Boot `
    --runtime-image $jdk --dest $destination --icon (Join-Path $projectDir 'bluej/icons/bluej-light.ico') `
    --java-options '--module-path=$APPDIR/javafx' `
    --java-options '--add-modules=javafx.controls,javafx.fxml,javafx.media,javafx.swing,javafx.web' `
    --java-options '--add-exports=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED' `
    --java-options '--add-exports=javafx.graphics/com.sun.javafx.scene.input=ALL-UNNAMED'
if ($LASTEXITCODE -ne 0) { throw 'jpackage failed.' }
$moduleNames = @{'base'='base';'controls'='controls';'fxml'='fxml';'graphics'='graphics';'media'='media';'swing'='swing';'web'='web'}
[System.IO.Directory]::CreateDirectory((Join-Path $imagePath 'app/javafx')) | Out-Null
foreach ($module in $moduleNames.Keys) {
    $jar = Get-ChildItem -LiteralPath $libDir -Filter "javafx-$module-*-win.jar" | Select-Object -First 1
    if (!$jar) { throw "Missing JavaFX $module library" }
    Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $imagePath "runtime/lib/javafx.$module.jar")
    Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $imagePath "app/javafx/javafx.$module.jar")
}
Copy-Item -LiteralPath (Join-Path $projectDir 'LICENSE.txt'),(Join-Path $projectDir 'README.md') -Destination $imagePath
Copy-Item -LiteralPath (Join-Path $projectDir 'LICENSING.md') -Destination $imagePath
Copy-Item -LiteralPath (Join-Path $projectDir 'README.it.md') -Destination $imagePath
[System.IO.Directory]::CreateDirectory((Join-Path $imagePath 'examples')) | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'Exercises/Sum') -Destination (Join-Path $imagePath 'examples') -Recurse
Copy-Item -LiteralPath (Join-Path $projectDir 'bluej/doc') -Destination (Join-Path $imagePath 'doc') -Recurse
Copy-Item -LiteralPath (Join-Path $projectDir 'bluej/icons/CREDITS.txt') -Destination (Join-Path $imagePath 'doc/LIGHT-ICON-CREDITS.txt')
& (Join-Path $PSScriptRoot 'build-launcher.ps1') -Destination (Join-Path $imagePath 'BlueJ light.exe') -MingwPath $MingwPath
Write-Output $imagePath
