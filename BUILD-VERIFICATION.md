# Lume Gallery 10.0.0 — verification

## Changes checked

Settings now have search, category filters, independently wrapped labels and switches, and controls for memory placement, quick filters, viewer filmstrip/hint/swipe and each collection section. Device folders appear earlier; the overflow tool directory preserves access when sections are hidden. Buttons and viewer actions use consistent rounded tonal surfaces.

The viewer accepts a vertical swipe for a read-only metadata sheet. Horizontal, zoomed, multi-pointer and canceled gestures do not open details. The explicit details action is retained. The sheet has grouped tabs, expansion/collapse, local camera/GPS/audio metadata, selectable values and explicit sensitive clipboard copies. No new Android permission or model was added.

## Results

Release build and all 49 app tests passed. Android Lint: zero errors, 32 warnings. Two new touch-accessibility warnings were reviewed: the sheet handle explicitly calls performClick, and the swipe frame also retains an explicit accessible details button. Remaining warnings include existing redraw/localization/dependency recommendations. APK signing, ZIP 16KB alignment, and all three ARM64 ELF LOAD alignments passed.

Nine new tests cover settings search and retained choices; narrow/large-font label-switch bounds; collection visibility without media loss; category filtering; actual memory and filmstrip visibility; gesture direction/disable/cancel/multi-touch; local GPS opt-out and unchanged source bytes; and secure, expandable details with sensitive clipboard copies. All 40 prior app tests also pass, including editing, privacy, navigation, local AI, collage, date groups and smart collections.

Previews use synthetic sample media only. No private reference frames, faces, locations or account identifiers are committed. Android screenshot blocking and encrypted private data remain enabled by default. Packaged DEX is checked for MediaPipe's upstream DummyLogger instead of remote ProtoLogger.

## Artifact

File: `LumeGallery-v10.0.0.apk`  
Bytes: 133720347  
SHA-256: `94f45f4ef2ae6cecdaa0d07b72ba28abbb9a9127604d143d1e3f7d575fb5c653`  
Certificate SHA-256: `79f748d439cfe5c39ca391d7ca146a85d6d84666b4114d45a8cc698b5877fcb7`

## Limits

No physical-device validation is claimed. Real-device gesture feel, MediaStore/OEM permissions, authentication, codecs, background scheduling and AI inference still need phone testing. Metadata depends on what the file and Android expose; unavailable fields are explained rather than invented. GPS display controls affect the details reader; automatic Places has its own setting. There is no network map or Google Photos cloud integration. Existing bounded collage/AI behavior and cleanup safeguards remain unchanged.
