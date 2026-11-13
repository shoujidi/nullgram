# Nullgram source attribution

Nullgram is an independent modified Android client based on **Niagram** (formerly NiagramX):
https://github.com/HSSkyBoy/Niagram

Base revision: c819d178d0fed124117558054cddbf64dc746cfa (Niagram 13.1.1, build 1311).
Base update date: 2026-11-10. Modified distribution name: Nullgram.

Niagram derives from NagramX, Nagram, Nekogram and Telegram Android. Their copyright notices,
source history, licenses and third-party notices remain applicable and are preserved.
Nullgram is not an official release of, or endorsed by, those projects or Telegram.

The covered source and Nullgram changes are provided under GNU GPL version 3; see LICENSE.
Third-party components and artwork retain their own applicable notices and licenses.
The optional Nullgram vector launcher artwork is independently drawn and licensed under GPLv3.
The default and all 15 upstream launcher choices retain their original Niagram artwork and notices.
Nagram-exclusive restricted icon artwork is not copied into this distribution.
Internal Java/Kotlin package names retain their upstream namespaces to avoid unrelated rewrites.

Recipients of a binary receive the matching Git source tag and build instructions alongside
the APK. Clone recursively to include the pinned submodules; GitHub automatic source ZIPs omit them. A patch alone is not the full source distribution. Local private keys and account
credentials are excluded; recipients can build and sign their own APK with their own key.

The installation package remains `top.nkbe.niagram`, including its original service configuration.
Changes: independent application display name; original Niagram icon choices plus optional Nullgram icons;
source/license attribution; the two
repeat features; single-account keyword reminders and a default-off lifecycle-controlled switch.
