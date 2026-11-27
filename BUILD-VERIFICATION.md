# Lume Gallery 11.0.0 — verification

## Changes checked

Regular gallery windows now allow screenshots/recording regardless of the legacy protectScreen/appLock capture preferences. Only the isolated, non-exported vault and credential gate force FLAG_SECURE. The vault authenticates through Android device password/PIN and uses its own credential-bound AES-256-GCM Keystore key. It imports verified encrypted copies without deleting originals, previews internally, exports an explicit confirmed copy, and confirms permanent deletion. It locks in the background and after four minutes; dialogs close on lock. Previews use temporary private cache files, cleared on close/background/startup.

The chunked encrypted format authenticates metadata, file ID, chunk order and end-of-file. Imports verify the decrypted copy with SHA-256 before final rename. Working memory for encryption/decryption is bounded by chunk size. A key requires credential authentication in the preceding five minutes; long operations may fail safely after expiry. Device Keystore/credential behavior needs real-phone validation.

Photo EXIF and video ISO-6709 locations can resolve to city/country through Android Geocoder, with a visible network disclosure and opt-out. Missing names are explained. External maps open only on a tap and use validated saved coordinates. Place-group membership and custom names are preserved. No live location permission or map SDK was added.

Pinch changes the actual RecyclerView density between two and five columns, persists the preference and preserves the scroll anchor. Recycled cells remain square and date headers follow the current span count. Manual density controls remain available.

## Results

- Release build and all 61 app tests passed; Android Lint: zero errors, 33 warnings. The new storage advisory recommends allocatable-byte APIs instead of usable-space checks; imports also handle write failures without deleting sources. Earlier gesture accessibility and redraw/localization warnings remain documented.
- Twelve new tests cover city/country formatting, invalid/map coordinates, geocoding opt-out, video GPS parsing, encrypted round trips, wrong keys, tampering, truncation, appended bytes, reordered chunks, secure vault window before authentication, actual verified import with untouched source, density/header bounds, square recycled tiles and real two-finger scale events. Prior capture-security assertions were updated to the new requested regular-gallery policy; all prior functional tests pass.
- APK signature verified with the previous certificate; ZIP 16KB alignment and all three ARM64 ELF LOAD alignments passed. Packaged DEX still invokes MediaPipe's upstream DummyLogger, without ProtoLogger calls.
- Test media and locations are synthetic. No user recordings, private media, signing keys or coordinates from references are committed.

## Artifact

File: `LumeGallery-v11.0.0.apk`  
Bytes: 133753143  
SHA-256: `211293f8a3da437347825a4f9af34825840c3822bac5d7542ccbf5d723cffdef`  
Certificate SHA-256: `79f748d439cfe5c39ca391d7ca146a85d6d84666b4114d45a8cc698b5877fcb7`

## Limits

No physical-device validation is claimed. Android device-password/Keystore enrollment, expiry and invalidation, OEM capture protection, real media codecs, MediaStore publishing, Geocoder availability/results and real gesture feel require phone testing. Unit crypto tests use an injected JCE key; they do not simulate hardware authentication. Names depend on the platform service and may require network; coarse place-group labels may refer to a nearby area. No in-app map tiles or Google Photos synchronization exist.

Vault import makes a copy; originals remain visible outside the vault until independently deleted. A decrypted private cache file exists while previewing, and after process death may remain until the next vault launch. Uninstall/data clearing or key invalidation can destroy access; there is no cloud recovery. Exporting a public copy intentionally allows access and capture outside the vault. These behaviors are explained in the UI and privacy policy.
