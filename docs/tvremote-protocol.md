# Luma TV Remote — webOS protocol notes

`tvremote/` is an Android phone app that controls an LG webOS TV on the same LAN.
It uses LG's public SSAP WebSocket API over ports **3000** (`ws`) and **3001**
(`wss`). No LG account, ThinQ cloud login or internet connection is required.

## TV setup

1. On the TV: **Settings → General → LG Connect Apps → On**.
2. Keep the phone and TV on the same Wi‑Fi / Ethernet LAN.
3. Enter the TV's IP address in the app, or use **Scan network**.
4. Accept the pairing prompt on the TV. The generated client key is stored in
   the app's private `SharedPreferences`, one key per TV IP.

If the TV is on another subnet, scanning will not find it; enter the IP manually.
Some corporate/guest Wi‑Fi networks block client-to-client traffic — use a home
network or disable AP isolation.

## What version 0.1.0 supports

| Feature | Status | Implementation |
| --- | --- | --- |
| D‑pad, Home, Back, Menu, Exit | Yes | pointer input socket `type:button` |
| Volume, mute, channels, power off | Yes | SSAP audio/tv/system endpoints |
| Play / pause / stop / rewind / fast forward | Yes | `media.controls/*` |
| Mouse cursor | Yes | `getPointerInputSocket` → `move`, `click`, `scroll` |
| Keyboard typing | Yes | `com.webos.service.ime/insertText`, `sendEnterKey`, `deleteCharacters` |
| Installed app list + launch | Yes | `listLaunchPoints` / `system.launcher/launch` |
| Local app filtering | Yes | on-device filter of the TV's app list |
| TV-wide search | Best effort | `com.webos.service.search/search`, then `com.webos.app.search` with a query param |
| ThinQ cloud devices (AC, fridge, etc.) | No | private LG cloud API, outside this app's scope |
| Wake-on-LAN power on | Not in v0.1.0 | needs TV MAC + router support; may be added later |
| webOS TV `.ipk` app | No | different SDK/toolchain; this app runs on the phone |

### Known limitations

- Search is not a single documented SSAP endpoint. The app tries the system
  search service, then launches the built-in search app. Some firmware versions
  ignore the query parameter; in that case use the **Keyboard** tab while the TV
  search box is focused.
- YouTube's custom on-screen keyboard silently ignores `insertText` on some
  firmware. The app cannot detect every case, so typed text may appear to do
  nothing inside YouTube.
- Cursor control only affects surfaces that accept mouse input. Apps that draw
  their own pointer may ignore `move`/`click`.

## Protocol attribution

The SSAP handshake, registration payload, endpoint URIs and pointer-input
command shapes are based on the Apache‑2.0 licensed
[`home-assistant-libs/aiowebostv`](https://github.com/home-assistant-libs/aiowebostv)
project. Its registration manifest signature and permission list are reused so
the TV grants a normal remote-control permission set.

Additional endpoint and keyboard-payload references:

- [Connect-SDK-Windows — WebOsTvKeyboardInput.cs](https://github.com/ConnectSDK/Connect-SDK-Windows/blob/master/Service/WebOs/WebOsTvKeyboardInput.cs) (Apache‑2.0)
- [webOS Open Source Edition — maliit-framework-webos](https://github.com/webosose/maliit-framework-webos)

Those projects are Apache‑2.0 licensed and are compatible with this repository's
GPL‑3.0 license. Their copyright notices remain with their respective projects;
this file records the attribution required by Apache‑2.0 §4.
