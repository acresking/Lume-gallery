# Lume Gallery 6.0.0 — verification

The exact reconstructed source was built and checked after the environment restart.

- Release build and Android Lint completed successfully: zero errors, 25 warnings (mainly text/resource conventions and efficiency suggestions).
- 18 app tests passed: gallery navigation/state, narrow layouts/enlarged text, local model validation/index behavior, AES-GCM roundtrip/nonce variation/tampering/wrong-key rejection, charging/network-free job constraints and opt-out, secure windows, and replacement of stale group membership after image changes.
- 2 build-privacy tests passed: redirecting MediaPipe's factory to its upstream dummy logger, and rejecting an unexpected upstream factory shape.
- The packaged DEX was disassembled: TasksStatsLoggerFactory invokes TasksStatsDummyLogger; it does not invoke TasksStatsProtoLogger. Vendor transport scheduling/discovery components are absent from the merged APK manifest.
- APK signature verified. Debugging is disabled, cleartext traffic is disabled and backup is disabled. The certificate is identical to previous official builds for upgrade continuity; its legacy certificate label does not imply the APK is debuggable.
- ZIP alignment passed with `zipalign -c -P 16 -v 4`. All three ARM64 native libraries have ELF LOAD alignment of at least 16KB.
- The original user artwork is retained. No private signing material, user media, model weights, databases or build caches are committed.

## Signed artifact

Filename: `LumeGallery-v6.0.0.apk`  
Bytes: 133671191  
SHA-256: `ab3695f4bb5f15e8e553a0a807e7318e97cf25edc722e1d0d01f0f65242a7959`  
Certificate SHA-256: `79f748d439cfe5c39ca391d7ca146a85d6d84666b4114d45a8cc698b5877fcb7`

## Scope and remaining device checks

No physical-device testing is claimed. Real Android Keystore behavior/migration, biometric or device-credential authentication, OnePlus background scheduling, permission prompts, codecs and full AI inference need phone validation. Host model experiments under tests/ are not device performance measurements. The UI lock protects the app interface; it does not turn shared MediaStore originals into an encrypted vault. Places use coarse EXIF GPS, with no remote place-name service. See PRIVACY.md.
