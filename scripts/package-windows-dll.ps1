# Packages the release mingwX64 build of licensecore.dll into the Windows distribution zip
# (Phase 9). Expects to run on the windows release.yml runner, AFTER:
#   ./gradlew.bat :core:linkReleaseSharedMingwX64
# which produces core/build/bin/mingwX64/releaseShared/licensecore.dll (and a Kotlin/Native
# -generated licensecore.def, which this script does NOT use - see packaging/windows/licensecore.def's
# own comment for why a hand-maintained .def is used for the import lib instead).
#
# Requires `lib.exe` on PATH (from a Visual Studio Build Tools / MSVC dev shell) to generate the
# MSVC import library - NOT the MinGW dlltool used by samples/windows-c-smoke/build-c-smoke.ps1,
# which is a different (gcc-only) toolchain and import-lib format.
#
# Usage: pwsh scripts/package-windows-dll.ps1 -Version 0.1.0-rc.1 [-SignToolThumbprint <thumbprint>]
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$SignToolThumbprint = $null
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot\..

$dllDir = "core\build\bin\mingwX64\releaseShared"
$dll = "$dllDir\licensecore.dll"
if (-not (Test-Path $dll)) {
    throw "licensecore.dll not found at $dll - run './gradlew.bat :core:linkReleaseSharedMingwX64' first."
}

$outDir = "build\packaging\windows"
New-Item -ItemType Directory -Force -Path $outDir, "$outDir\include" | Out-Null

if ($SignToolThumbprint) {
    signtool sign /sha1 $SignToolThumbprint /fd SHA256 /tr http://timestamp.digicert.com /td SHA256 $dll
    Write-Host "Signed $dll with thumbprint $SignToolThumbprint"
} else {
    Write-Warning "No -SignToolThumbprint given - shipping licensecore.dll UNSIGNED. OK for an internal RC per Phase 9's risk-acceptance (Unresolved Questions); block GA on this."
}

lib.exe /def:packaging\windows\licensecore.def /machine:x64 /out:"$outDir\licensecore.lib"

Copy-Item $dll "$outDir\licensecore.dll" -Force
Copy-Item include\licensecore.h "$outDir\include\licensecore.h" -Force
Copy-Item packaging\windows\README-windows.txt "$outDir\README-windows.txt" -Force
if (Test-Path LICENSE) {
    Copy-Item LICENSE "$outDir\LICENSE" -Force
}

$zipName = "licensecore-win-x64-$Version.zip"
Compress-Archive -Path "$outDir\*" -DestinationPath $zipName -Force
Write-Host "Packaged $zipName"
