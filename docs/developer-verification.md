# Android Developer Verification — Luma checklist

Google's **developer verification** requirement does not change Android's source code
licensing (AOSP remains open, and Luma stays GPL-3.0). It changes **who may install an
unverified APK** on a *certified* Android device.

This file keeps everything needed to register Luma when the global rollout reaches India.

## Timeline (official)

| Date | Change |
| --- | --- |
| Aug 2025 | Announced |
| Mar 2026 | Verification open to all developers |
| Aug 2026 | Limited distribution accounts + "advanced flow" for sideloading unverified apps |
| **Sep 30, 2026** | Protections start in Brazil, Indonesia, Singapore, Thailand — for participating stores (Play, Galaxy Store, GetApps, V-Appstore, OPPO/HONOR/Transsion stores) |
| **2027 and beyond** | Global expansion to all apps on certified Android devices, including direct sideloading |

Until Luma is registered, users on certified devices can still install it with **ADB** or by
enabling Android's **advanced flow** for unverified apps.

Sources:
- <https://developer.android.com/developer-verification>
- <https://developer.android.com/developer-verification/guides/open-source-app-registration>
- <https://developer.android.com/developer-verification/guides/limited-distribution>

## Luma's registration data

| Field | Value |
| --- | --- |
| Package name | `com.luma.gallery` |
| Signing certificate SHA-256 | `096c4886c2abbcbc5de4c79da9279cb8c896efa2db45527f919ef28dbec17506` |
| Certificate subject | `CN=Janaki Ram, OU=Luma, O=Luma, L=Hyderabad, ST=Telangana, C=IN` |
| Certificate validity | 2026-11-11 → 2056-11-03 |
| Current release | 1.1.1 (versionCode 3) |
| Min / target SDK | 26 / 36 |

Print the fingerprint again at any time (never share the keystore itself):

```bash
keytool -list -v -keystore luma-release.jks -alias luma
```

`apksigner verify --print-certs app-release.apk` prints the same SHA-256 for the built APK.

## Register (hobbyist / limited distribution — free, no government ID)

1. Open <https://android.google.com/developerconsole/developers> and sign in with the
   Google account that should own Luma.
2. Create a **limited distribution account** (students, teachers, hobbyists — up to **20
   devices**, no fee, no ID upload). Choose this if Luma stays a personal project.
   Choose a **full distribution** account instead if you want unlimited installs or plan to
   publish on Google Play; full accounts carry a registration fee and identity checks.
3. Open **Android developer verification** → **Register package name**.
4. Enter `com.luma.gallery`.
5. **Add key** → paste the SHA-256 fingerprint from the table above (no colons, lowercase).
6. Save. New installs of the GitHub APK then behave exactly as today — no extra prompts.

## If F-Droid (or any repo) re-signs the APK

F-Droid builds and signs with its own key. To keep that install path clean:

1. Download the F-Droid-signed APK.
2. Extract its certificate fingerprint:
   `apksigner verify --print-certs app-release.apk`
3. In the console, open the registered app → **Add key** → paste that fingerprint.
4. If prompted to prove ownership after 50+ installs, follow the console's verification steps.

Repositories that integrate the **Android Developer Console API** can register package name
and keys on the developer's behalf; you still need a verified console account linked to the
app. For API-assisted flows the console may ask you to add an `adi-registration.properties`
file inside `app/src/main/assets/` — paste the snippet it gives you, rebuild, and upload the
new APK. Nothing needs to be added to the source tree until that step is requested.

## Do not

- Never commit `luma-release.jks` or `keystore.properties` (already in `.gitignore`).
- Never re-sign a future update with a different key — Play/verification trust is bound to
  the SHA-256 above; changing it breaks updates for existing users.
- Do not register someone else's package name; verification is per package + key.

## What users can do meanwhile

- Install with `adb install -r Luma-x.y.z.apk` (ADB installs are not blocked), or
- Enable the **advanced flow** for unverified apps when prompted, or
- Install from a store that already verifies Luma after registration.

## Future distribution options (GitHub-only for now)

Decision as of 1.1.1: Luma ships **only from GitHub Releases**. Nothing is submitted to any
store. Recorded here so the research is not lost when we revisit it.

F-Droid main-repo eligibility notes:

- ✅ GPL-3.0, public repo, FOSS-only dependencies (Compose, Media3, Coil, NanoHTTPD, ZXing).
- ⚠️ `app/src/main/assets/web` holds a **prebuilt** React bundle — the F-Droid recipe must run
  `npm ci && npm run build` in `webui/` instead of using the committed assets.
- ⚠️ No `fastlane/metadata/android/en-US/` yet — description, icon, phone screenshots and a
  changelog per versionCode are required.
- ⚠️ Policy asks for ~3 months of active development; revisit after that.
- ⚠️ F-Droid signs with its own key unless the build is reproducible, so switching from the
  GitHub APK to an F-Droid build would require a reinstall (vault/trash data lost) unless
  reproducible builds are set up.
- ℹ️ F-Droid is **not** a Google "participating store", so from 2027 installs through it still
  depend on package registration; F-Droid integrates the Android Developer Console API to
  register package names and keys on the developer's behalf.

Lighter-weight alternatives if we want reach before that:

1. **Obtainium** — zero work, users point it at the GitHub repo and get auto-updates.
2. **IzzyOnDroid** — third-party repo, accepts the developer-signed APK, short review.
3. **Self-hosted F-Droid repo** — `fdroidserver` + GitHub Pages, own key, no review.

## Release checklist reminder

When a new version is published:

- [ ] Same signing key (`luma-release.jks`, alias `luma`) — fingerprint unchanged
- [ ] Version bumped in `app/build.gradle.kts`
- [ ] `./gradlew assembleRelease`
- [ ] Install over the previous build to confirm the update path
- [ ] `gh release create vX.Y.Z …` with the APK and notes
