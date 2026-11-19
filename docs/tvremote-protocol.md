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

## Verified on real hardware

Tested end-to-end on an **LG 55UR7500PSC** running **webOS 4.1.0**
(release **10.3.2**), discovered at `192.168.1.10`:

- `wss://…:3001` handshake succeeded; `ws://…:3000` remains as fallback.
- `PROMPT` pairing accepted on the TV; `client-key` saved per TV IP.
- App reached `Connected · 192.168.1.10`; all seven screens (Remote, Cursor,
  Keyboard, Apps, Inputs & sound, Content search, Cast & mirror) opened.
- `audio/getStatus` returned volume **87**, `soundOutput: tv_speaker`, and the
  foreground app was reported as `com.webos.app.hdmi1`.
- D-pad buttons, cursor/click, keyboard typing and app launch worked as
  expected on this firmware.
- `com.webos.service.connectionmanager/getinfo` returned the Ethernet MAC
  `64:E4:A5:2C:4E:A5` (matching the phone's ARP table), enabling Wake-on-LAN.
- YouTube is installed as `youtube.leanback.v4` and accepted `youtube://`
  deep-launch parameters from `system.launcher/launch`.
- The **Screen Mirroring** button launches `sooltrstudio.screenmirroring.receiver`
  ("Screen Mirroring Media Receiver"), which is installed on this firmware;
  the TV reported it as the foreground app after both a direct SSAP launch and
  the app's own button. Other guessed IDs (`com.webos.app.miracast`, etc.) either
  failed with 500 or did not stay in the foreground.
- The TV exposes a **DLNA MediaRenderer** at `http://192.168.1.10:1107/`
  (`urn:schemas-upnp-org:service:AVTransport:1`). Casting a phone-hosted file
  with `SetAVTransportURI` + `Play` made the TV issue `HEAD` and `GET` for the
  URL and report `CurrentTransportState: PLAYING` — real playback, not just
  opening the media app (verified from the phone's own NanoHTTPD server).
  Both **photos and videos** verified playing (a 2:34 MP4 reported `PLAYING`
  with the phone URL). The client uses generous HTTP timeouts because video
  `Play` can exceed 10s, and the media server answers byte-range requests for
  seekable streaming.

Other webOS versions may differ; the limitations below still apply.

## What version 0.1.0 supports

| Feature | Status | Implementation |
| --- | --- | --- |
| Persistent Home / Back / Recent / Menu bar | Yes | always-visible `NavBar` on Remote and Cursor tabs, so leaving an app (e.g. YouTube video → Home) needs no customization; `type:button` via input socket (Home verified → `com.webos.app.home`) |
| D‑pad, Home, Back, Menu, Exit | Yes | pointer input socket `type:button` |
| Volume, mute, channels, power off | Yes | SSAP audio/tv/system endpoints |
| Play / pause / stop / rewind / fast forward | Yes | `media.controls/*` |
| Mouse cursor | Yes | `getPointerInputSocket` → `move`, `click`, `scroll` |
| Keyboard typing | Yes | `com.webos.service.ime/insertText`, `sendEnterKey`, `deleteCharacters` |
| Installed app list + launch | Yes | `listLaunchPoints` / `system.launcher/launch`; shows each app's real icon fetched from the TV's self-signed HTTPS icon URL via a trust-all OkHttp loader (initials tile fallback) |
| Recent apps & LG Home Hub shortcuts | Yes | Apps tab quick-shortcut card: `RECENT` key opens the TV task switcher; `com.webos.app.homeconnect` launches LG Home Hub (verified foreground) |
| Recently launched apps | Yes | last 8 launched apps stored per install, one-tap relaunch from the Apps tab |
| mDNS auto-discovery | Yes | `NsdManager` on `_airplay._tcp` / `_raop._tcp`, reads MAC from TXT `deviceid` |
| Wi-Fi signal + TV link latency | Yes | RSSI bars (1–5) and TCP connect timing to port 3001 |
| Local app filtering | Yes | on-device filter of the TV's app list |
| TV-wide search | Best effort | `com.webos.service.search/search`, then `com.webos.app.search` with a query param |
| Search inside YouTube / Netflix | Best effort | app deep links (`youtube://results?search_query=…`), then app launch |
| Multi-TV favorites | Yes | one client key + MAC per IP, favorites pinned at the top |
| Custom button layout | Yes | per-TV visibility toggles for button groups |
| Wake-on-LAN power on | Yes | UDP magic packet to the learned TV MAC, ports 7/9 |
| App & input control | Yes | `system.launcher/launch` for HDMI apps, `close` best effort |
| Sound output control | Best effort | `audio/getStatus` + `audio/changeSoundOutput` (`tv_speaker` verified) |
| Content search & discovery | Yes | TV search + recent searches + app deep links |
| Cast photos / videos | Yes | local NanoHTTPD server (byte-range aware) + DLNA AVTransport `SetAVTransportURI`/`Play` with extended timeouts (SSAP media-app launch as fallback) |
| Screen mirroring receiver | Yes | launches `sooltrstudio.screenmirroring.receiver` (verified foreground) |
| Dark theme / settings | Yes | persisted preference |
| Bottom navigation + More menu | Yes | five always-visible destinations; secondary screens in a dialog |
| ThinQ cloud devices (AC, fridge, etc.) | No | private LG cloud API, outside this app's scope |
| webOS TV `.ipk` app | No | different SDK/toolchain; this app runs on the phone |

### Known limitations

- Search is not a single documented SSAP endpoint. The app tries the system
  search service, then launches the built-in search app. Some firmware versions
  ignore the query parameter; in that case use the **Keyboard** tab while the TV
  search box is focused.
- mDNS discovery depends on the TV advertising AirPlay 2 (`_airplay._tcp`). If
  that service is disabled on the TV, use **Scan network** or manual IP entry.
  Discovery runs for 6 seconds; Wi-Fi SSID is hidden by Android without a
  location permission, so the app shows signal bars and RSSI instead.
- YouTube's custom on-screen keyboard silently ignores `insertText` on some
  firmware. The **Keyboard → Search inside an app** buttons try a `youtube://`
  deep link first, then fall back to launching the app; the snackbar reports
  which path was used. If the app ignores the deep link, use the TV's own search
  box with the Cursor/Keyboard tabs.
- Wake-on-LAN requires the TV's network standby to be enabled and the phone and
  TV on the same L2 LAN. The MAC is learned automatically after the first
  successful connection. Software `system/turnOn` is attempted first when
  connected; WoL is the fallback.
- Full phone-screen mirroring and LG Magic Tap need system-level Wi‑Fi Direct /
  NFC APIs that a regular Android app cannot use. The Cast tab serves a selected
  photo/video from the phone over LAN and plays it on the TV through the DLNA
  MediaRenderer (`AVTransport`), which was verified to actually render the file.
  If the firmware rejects the DLNA metadata, the app falls back to launching the
  TV's media app with the URL, which is less reliable.
- `audio/changeSoundOutput` accepted only `tv_speaker` on the tested webOS 4.1
  firmware; other outputs (Bluetooth, optical, ARC) are firmware-dependent and
  the app reports failures honestly.
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
