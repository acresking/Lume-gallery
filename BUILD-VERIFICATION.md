# Lume Gallery 9.0.0 — verification

## Changes checked

Local date-based memory cards, selectable pinned collection shortcuts, and a bounded 2–6-photo collage editor with real preview, layout/aspect/spacing/background/full-image settings, source-order rotation and JPEG/PNG copy export. No remote model or new Android permission was added.

## Results

- Release build, all 40 app tests and Android Lint passed. Lint: zero errors and 30 warnings.
- Eight new tests cover actual date membership, archived/future exclusion, leap-year boundaries, pinned preferences, disabling memory UI, date-collection filtering, collage-cell bounds/non-overlap, source pixel preservation/compositing, and editor control selection/large-font layout/secure windows. All prior 32 app tests also passed.
- Android previews used synthetic sample media. No reference recording frames, faces, locations, account identifiers or personal filenames were committed.
- APK signature verified with the same certificate as previous builds. ZIP alignment passed for 16KB pages; all three ARM64 libraries have ELF LOAD alignment of at least 16KB.
- Packaged DEX confirms MediaPipe stats still use its upstream DummyLogger rather than remote logging. Existing privacy controls and local AI setup are retained.

## Artifact

File: `LumeGallery-v9.0.0.apk`  
Bytes: 133703959  
SHA-256: `6e5684f4061a707599a03cc4cdf5f3d83cd1b18f1f605aa005d26ff124ba5327`  
Certificate SHA-256: `79f748d439cfe5c39ca391d7ca146a85d6d84666b4114d45a8cc698b5877fcb7`

## Limits

No physical-device validation is claimed. MediaStore export on a real phone, process interruption, OEM scheduling, codecs, Android Keystore/device authentication and full AI inference still require phone testing. The collage decodes each source to at most 1024 pixels and exports a new image with a maximum 3072-pixel long edge; output dimensions do not guarantee native source detail. Memory dates follow existing gallery capture/added date behavior, not inferred real-world events. There is no Google Photos synchronization, automatic shared album, AI story naming, network map, movie generation or proprietary cloud-editor integration.
