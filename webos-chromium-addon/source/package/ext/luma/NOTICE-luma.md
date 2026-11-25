Luma Features Layer — notice
============================

This directory is an additive extension for the WebOS-Chromium browser. It does
not replace, rename or modify Chromium, TV Tools, the launcher, NDL/GPU code,
browser UI, bookmarks, history, downloads or any existing licence or notice.

Contents:
  - MV3 extension pages and scripts under this folder only.
  - All persisted state uses chrome.storage.local keys prefixed with "luma.".
  - Diagnostics are local only: no telemetry, analytics, device ID, cloud
    upload, native messaging host, LS2 service or external endpoint.

The extension uses the standard Chrome extension APIs storage and tabs, plus
ordinary DOM media APIs. It follows the integration pattern demonstrated by the
shipped TV Tools extension without changing that extension.

No LG firmware files, Chromium source files or third-party notices are copied
here. Existing notices remain in their original files in the package.
