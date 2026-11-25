# Luma Features Layer — inspection and architecture package

**Date:** 2026-11-25  
**Status:** A1, Phase 2 and Phase 3 are complete. Luma Features `0.3.0` is version-controlled in `webos-chromium-addon/`, repackaged locally, installed and runtime-verified on `lumatv`. Phase 3 adds the Luma Cinema media engine while preserving Chromium. The phone Host-to-TV remote handshake and full Cinema UI interaction remain manual user checks.

This package answers your request without changing the working WebOS-Chromium browser.

## Foundation

- Repository: `https://github.com/sarahahzub/WebOS-Chromium`
- Local read-only source: `C:\Users\janak\Downloads\WebOS-Chromium-main\WebOS-Chromium-main`
- The source tree was inspected as downloaded. No Chromium file, patch, launcher, icon, license or notice was edited.

## Documents

| Phase | File | Question answered |
|---|---|---|
| 1 | [00-preservation-report.md](00-preservation-report.md) | What was inspected and what stays untouched? |
| 2 | [01-integration-api-map.md](01-integration-api-map.md) | Which real interfaces exist, and which are only assumed? |
| 3 | [02-feature-compatibility-matrix.md](02-feature-compatibility-matrix.md) | Which of the 11 Luma features fit those interfaces? |
| 4 | [03-architecture.md](03-architecture.md) | Proposed separate Luma Features Layer and its boundaries. |
| 5 | [04-safe-implementation-plan.md](04-safe-implementation-plan.md) | What will be built, in what order, and how it will be tested. |
| 5 | [05-change-requests.md](05-change-requests.md) | Which ideas need core/host or existing-file changes and must wait. |
| 6 | [06-phase2-integration-audit.md](06-phase2-integration-audit.md) | Phase 1 re-verification, supported APIs, 14-feature matrix, architecture and security design. |
| 7 | [07-phase2-implementation.md](07-phase2-implementation.md) | What Phase 2 code changed, what was verified, and what still needs approval. |

## Hard scope rules recorded in these documents

1. Keep Chromium engine, browser UI, tabs, navigation, address bar, menus, bookmarks, history, downloads, GPU rendering, NDL hardware video, Magic Remote and webOS integration intact.
2. Add functionality only as new files/modules inside the shipped extension packaging path.
3. A feature needing Chromium core, NDL/GPU, native host registration, app icon/title or existing-file changes is reported and waits for explicit approval.
4. No telemetry is part of the Luma Features Layer. It is not added to any permission, message, storage, UI or handshake.
5. A1 is applied. The Phase 2 implementation approval covers only the additive Host/extension remote foundation; CDP input, fullscreen, native host, Luna and engine changes are still not approved. B1–B9 remain preserved-only.
6. No build, install, device test, commit or push has been performed.

## Applied decision

- **A1 only** — a separate Luma execution path was added to `source/package/run.sh`, and all Luma code lives in `source/package/ext/luma/**`.
- Backup: `C:\Users\janak\Downloads\WebOS-Chromium-main\WebOS-Chromium-main.backup-A1-20261125-105633`
- Original Chromium exec block is byte-for-byte unchanged; the Luma branch is additive and opt-in via `CN_LUMA=1` or `{"luma":true}` in the launch JSON.
