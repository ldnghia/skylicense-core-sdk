# Builds and runs the C smoke test against the mingwX64-built licensecore.dll's real C ABI
# (lc_create/lc_device_id/lc_get_state/lc_destroy). Run after
# ./gradlew :core:linkReleaseSharedMingwX64 has produced the DLL.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$mingwBin = "$env:USERPROFILE\.konan\dependencies\msys2-mingw-w64-x86_64-2\bin"
$dllDir = "..\..\core\build\bin\mingwX64\releaseShared"
$includeDir = "..\..\include"

& "$mingwBin\dlltool.exe" -d "$dllDir\licensecore.def" -l "$dllDir\licensecore.dll.a" -D licensecore.dll
& "$mingwBin\gcc.exe" -I "$includeDir" -o smoke_test.exe main.c -L "$dllDir" -llicensecore
Copy-Item "$dllDir\licensecore.dll" . -Force
.\smoke_test.exe
