# Lume Gallery 8.0.0 — verification

## Changes checked

Responsive three-column album covers, local smart content collections, true-black dark appearance and a scrollable asynchronous photo/video metadata sheet. The new categories reuse existing Lume AI index entries and reject stale fingerprints and archived media. Camera data comes from the selected file, not from assumptions about the current device.

## Results

- Release build, all 32 app tests and Android Lint passed. Lint: zero errors and 28 warnings.
- Eight new tests cover fresh/stale/archive filtering, token boundaries, real collection filtering, album density with enlarged text, true-black appearance, EXIF camera/lens/exposure reading without changing source bytes, secure/scrollable details, and formatting missing metadata. All prior 24 app tests also passed.
- Android UI previews were rendered using synthetic sample media. No private reference recording frames, faces, locations or filenames are included in the code or APK.
- APK signature verified with the same certificate as previous versions. ZIP 16KB alignment passed and all three ARM64 native libraries retain at least 16KB ELF LOAD alignment.
- Packaged DEX inspection confirms MediaPipe's stats factory still calls its upstream DummyLogger rather than remote logging. No new permissions, external model providers, map services or analytics were added.

## Artifact

File: `LumeGallery-v8.0.0.apk`  
Bytes: 133687575  
SHA-256: `bbbec35a3f1f190a418f6cc47e7a26ce3f376a1ef02857c8f3478b292cf5ce05`  
Certificate SHA-256: `79f748d439cfe5c39ca391d7ca146a85d6d84666b4114d45a8cc698b5877fcb7`

## Limits

No physical-device testing is claimed. Video metadata depends on device/container support and the reported frame rate may not describe every frame of variable-rate video. Smart category accuracy is not guaranteed; no new model accuracy benchmark was performed. Categories populate after local indexing, not after merely installing the APK. Keystore/device authentication, OnePlus job scheduling, video codecs and full model inference still require phone validation. No complete parity with proprietary gallery apps, network maps, cloud backup, Google Lens or ProXDR integration is claimed.
