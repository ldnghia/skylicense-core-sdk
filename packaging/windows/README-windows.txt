LicenseCore SDK - Windows x64 native package
=============================================

Contents of this zip:
  licensecore.dll       - the SDK (Kotlin/Native shared lib), release build
  licensecore.lib        - MSVC import library (generated from licensecore.def)
  include/licensecore.h  - C ABI header; every call, its JSON shapes, and threading/lifetime
                            rules are documented there

Distribution: internal/private use only (not a public release) - see the source repo's README
for the current access/licensing terms. A repo-root LICENSE file did not exist as of Phase 9; if
one is added before a release, the packaging script copies it into this zip automatically.

Linking
-------
MSVC (Visual Studio, cl.exe):
  #include "licensecore.h"
  link against licensecore.lib; ship licensecore.dll next to your .exe (or on PATH).

MinGW / gcc:
  licensecore.lib is NOT needed - link directly against licensecore.dll, e.g.
  gcc -I include -o app.exe main.c -L . -llicensecore
  (see samples/windows-c-smoke in the source repo for a working example).

.NET (Framework or 6+)
  P/Invoke directly against licensecore.dll (DllImport("licensecore.dll")); no import lib needed.
  A NuGet package (LicenseCore.Native.win-x64) is published alongside this zip for projects that
  prefer package-managed native dependencies - see docs/release-process.md in the source repo.

Signing
-------
licensecore.dll is Authenticode-signed when a code-signing certificate is available for the
release (see docs/security-threat-model.md and Phase 9's Unresolved Questions in the build plan
for the current status). Verify with:
  signtool verify /pa licensecore.dll
If the release notes say "unsigned (RC)", Windows SmartScreen/AV may flag the DLL on first run;
this is expected until a cert is available, not a sign of tampering - verify the SHA256SUMS.txt
entry instead.

Lifetime note (repeated from licensecore.h - easy to miss)
------------------------------------------------------------
Never FreeLibrary() this DLL. The Kotlin/Native runtime it embeds cannot be unloaded cleanly.
Load it once per process and keep it loaded for the process's lifetime.
