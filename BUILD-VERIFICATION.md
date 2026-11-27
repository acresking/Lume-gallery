# Lume Gallery 7.0.0 — verification

## Changes checked

Quick media filters scoped to the current album, rotation-state restoration, selectable date groups, reversible select-all, nearest-date navigation, and single-tap clean viewing. Date-group membership is cached during timeline construction instead of repeatedly formatting the entire library when selecting groups. Viewer action heights adapt to large text; duplicate pager-selection notifications no longer rebuild the toolbar during layout.

## Results

- Signed release build, Android Lint and all 24 app tests passed.
- Six new experience tests cover scope/reset of quick filters, rotation, date-group/select-all reversal, nearest group navigation, 320dp controls at enlarged font, large-font viewer controls/clean mode, and drag versus single-tap behavior. Prior gallery, editor, AI validation, encryption and background-job checks also passed.
- Android Lint: zero errors, 28 warnings, mainly resource/text conventions and performance suggestions.
- UI preview rendered through Android/Robolectric. No physical phone testing is claimed.
- Signature verified with the same certificate as v6 and earlier builds. APK versionCode is 7; debugging is disabled. Backup/cleartext restrictions are retained.
- ZIP alignment verified for 16KB pages. All three ARM64 native libraries retain ELF LOAD alignment of at least 16KB.
- Packaged DEX disassembly confirms MediaPipe's stats factory calls its upstream DummyLogger rather than its remote ProtoLogger. The build-time privacy transform and models are unchanged.

## Artifact

File: `LumeGallery-v7.0.0.apk`  
Bytes: 133671191  
SHA-256: `2dd9c52f45b5101ad096b20e472c6dc05a5e4fc7bc6a6c2e1400dc75fcb6b73a`  
Certificate SHA-256: `79f748d439cfe5c39ca391d7ca146a85d6d84666b4114d45a8cc698b5877fcb7`

## Limits

Android Keystore migration, biometric/device authentication, OnePlus background scheduling, full AI inference and video codecs still need device validation. The new date picker navigates existing authorised media; it does not write EXIF or infer locations. No cloud synchronization, proprietary vendor integrations, or complete feature parity with other galleries is claimed. See PRIVACY.md for storage and protection boundaries.
