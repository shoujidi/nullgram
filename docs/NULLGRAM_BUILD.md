# Nullgram build and source delivery

Modified 2026-11-01 from Niagram revision `bc99e5e9907a2ea47d9e6ed432469b8ff553644f`.
Application display name: Nullgram. Installation ID remains **top.nkbe.niagram**.
Internal namespaces, contact authorities, service configuration and native libraries retain the upstream identity.
The provided debug APK uses a local development signing key. Recipients build with their own private key.

## Build prerequisites

- Windows/Linux, JDK 21; run the included Gradle wrapper (9.7.1).
- Android SDK platform `android-37.0`, Build Tools `37.0.0`.
- NDK `27.2.12479018`, CMake `3.31.6` or the compatible upstream toolchain.
- Network access for declared Gradle/Maven dependencies and the upstream native build tools.
- All pinned submodule sources are included in the matching source archive. The archive is a source snapshot without `.git` metadata.

For a fresh upstream Git clone initialize recursive submodules and apply the preparation patch already
specified by upstream `.github/workflows/pr.yml`:
`TMessagesProj/jni/patches/tgcalls-0001-feat-custom-group-voip-audio-bitrate.patch`.
**The source archive already includes that patch's two modified files. Do not apply it twice.**
Submodule revisions and file hashes are recorded in the accompanying source manifest.

Set Android `sdk.dir` and the existing `KEYSTORE_PATH`, `KEYSTORE_PASS`, `ALIAS_NAME`, `ALIAS_PASS`
properties in your own ignored `local.properties`. Generate your own Android development keystore.
No keystore, password, local properties, account data, caches or build outputs are in the source archive.
The upstream `google-services.json` is retained because the package is retained; no separate Firebase project is required for this local variant.

For the arm64 build set environment `NATIVE_TARGET=arm64-v8a`, then run:

```text
gradlew.bat :TMessagesProj:testDebugUnitTest :TMessagesProj:assembleDebug --max-workers=4
powershell -NoProfile -File Tools/test-repeat.ps1
```

JVM tests use JUnit and Robolectric. Android framework behavior is simulated on API 28 and 36;
this does not substitute for a physical device, Telegram server delivery or actual notification tapping.
See the delivery report for the executed commands and PASS / FAIL / NOT RUN results.

## Use

Open app settings → **Null设置 / Null Settings** → **提醒 / Reminders**.
The first row is **启用提醒**, initially off. Add keywords, then enable while the intended
logged-in account is selected. Only that account is monitored; selecting another account in the app
does not silently rebind it. To rebind, turn off, select the intended account and enable again.

Literal substring matching ignores ASCII English letter case. Limits: 100 keywords, 128 UTF-16
code units per keyword, and the most recent 512 notified dialog/message IDs per bound user.
Identical message text with different IDs is distinct. This bounded history does not promise indefinite
deduplication after IDs have been evicted. Local configuration is retained when the switch is off.

The feature consumes incoming live events from the existing Telegram connection. It does not scan
history and cannot receive messages while the original client itself cannot receive them. No additional
service, network connection or keepalive framework is added. Android notification permission, channel
settings and Do Not Disturb still apply. Notifications already displayed remain after disabling.

The message menu also offers **Quick repeat**, which sends once using the original no-attribution path without a repeat configuration dialog.

Long-press send to use repeat sending. The existing repeat settings, 0.01-second minimum interval,
and unified stop control are retained. Server limits and network conditions still govern delivery.

## License and attribution

See root `LICENSE`, `UPSTREAM.md`, `docs/UPSTREAM_README.md`, and the original third-party
copyright/license files in the included sources. The app's About → GPLv3 dialog shows the source
attribution and full GPLv3 text offline. Distribute the matching full source snapshot and build instructions
alongside the binary, preserving upstream and third-party notices. The feature patch is an additional
review aid, not a replacement for the full corresponding source.

Keeping the package permits an update only when the installed app has a compatible signature.
It does not permit overwriting an upstream official build signed with a different private key.
Installation and any account login are performed manually by the user.

## Icon collection

Nullgram Blue (`NullgramIcon`) is the default. All 15 upstream launcher options remain available,
including their original selection behavior. Three supplied Nullgram artwork variants (Blue, Mint, Black) are included,
for 19 total choices including the optional portrait icon.
The portrait photograph is copyrighted by its provider. This version restores the 768x768 portrait icon at the photograph provider’s request. The original photograph and production master are not published. No photo preview is included in README or release notes. See the in-app source notice and `TMessagesProj/src/main/assets/nullgram/AVATAR_COPYRIGHT.txt`.
The blue startup artwork exits when the first content layout is ready, with a maximum of 2 seconds. Select an icon in **Settings → Chat Settings → App Icon**.
Upstream premium-only choices retain their original restrictions. No restricted Nagram-exclusive
artwork is added. Application display name and installation package are unchanged.

Visible application labels, the main header, version footer, font preview, welcome page and app
permission explanations use Nullgram. The old stored default header `Niagram` is displayed as Nullgram;
custom user titles are preserved. The current device row uses Nullgram, while other client sessions
keep their real server-provided names. Upstream attribution, channels, developer identities and
Telegram service/product names remain truthful. New default save folders use Nullgram; existing
custom folder preferences and existing files are not migrated or renamed.

Watched-person message scopes, online alert limitations and migration are documented in [NULLGRAM_REMINDERS.md](NULLGRAM_REMINDERS.md).
