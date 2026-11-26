# Lume Gallery 6.0.0 — verification

Version 6 adds local automatic people/place organisation, extra AES-GCM protection for face/album metadata, an optional Android-authenticated UI lock, secure windows and removal of MediaPipe remote usage logging.

The working environment restarted during preparation. Source was reconstructed from the saved v5 source and the recorded v6 changes. A fresh release build and automated checks are in progress; final results will replace this checkpoint. Do not interpret previous build results as validation of these exact reconstructed bytes.

No physical-device validation is claimed. Android Keystore operation, biometric/device authentication, OEM background-job scheduling, codecs and full model inference require testing on supported phones. Host model checks in tests/ refer to earlier model experiments and do not establish device performance.

No signing keys, user media, model weights, databases or local build caches are included in the public repository.
