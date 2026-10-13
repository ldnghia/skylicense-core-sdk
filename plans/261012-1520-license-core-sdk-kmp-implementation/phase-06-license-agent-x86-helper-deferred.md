---
phase: 6
title: "License Agent x86 Helper (Deferred)"
status: deferred
priority: P3
effort: "TBD (rough 3-4d when scheduled)"
dependencies: [5]
---

# Phase 6: License Agent x86 Helper (Deferred)

> **STATUS: DEFERRED - not in scope yet.** User decision: "để giai đoạn code SDK thật" (wait until the real SDK coding phase). This file only reserves the phase and records the intended shape so it is not forgotten. **No implementation steps, no internals design.** Do not start until user explicitly un-defers.

## Context Links
- [Phase 5 C ABI + JSON shapes](./phase-05-windows-mingwx64-dll.md) (the agent will reuse them)
- [plan.md](./plan.md)

## Overview
- Problem: Kotlin/Native has no x86 (32-bit) Windows target; an x64 DLL cannot load into an x86 process.
- Intended solution (locked by PM): x86 apps are NOT recompiled; they talk to `license-agent.exe`, a small x64 helper process that hosts `licensecore.dll`, over a local named pipe.

## Intended Shape (placeholder only - to be designed when un-deferred)
- Process: `license-agent.exe` (x64), hosts the Phase 5 DLL.
- Channel: Windows named pipe, local machine only.
- Message format: reuse Phase 5 JSON result shapes (`{"state":..,"info":..,"error":..}`) so no second schema exists (DRY).
- Client side for x86 apps: thin x86 client lib (language TBD by consumer stacks) speaking the pipe protocol.

## Known Questions to Answer When Un-deferred (not now)
- Agent lifecycle (per-app spawn vs per-user vs Windows service), pipe ACL/authentication of callers, framing/versioning, multi-app sharing of one license slot, x86 client language/packaging, installer.

## Out of Scope Here
- Any code, build config, IPC framing, security design.
- Windows ARM64 (K/N cannot target it) - separate known gap, unresolved by design.

## Dependencies
- Requires Phase 5 C ABI frozen at `lc_abi_version() == 1`.

## Success Criteria (for this placeholder)
- [ ] Phase remains listed in plan.md with status "Deferred - not in scope yet".
- [ ] Phase 5 JSON shapes documented so the agent can reuse them later.

## Risk Assessment
| Risk | L x I | Mitigation |
|------|-------|-----------|
| Phase 5 ABI changes break future agent | L x M | `lc_abi_version()`; ABI changes require version bump |
| x86 customers onboard before agent exists | M x M | Docs (Phase 10) state x86 support status explicitly |

## Next Steps
Revisit only on explicit user request.
