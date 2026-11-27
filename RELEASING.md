# Signed releases

Retain the original private signing backup outside the repository to preserve update compatibility. The certificate label/alias originated in early builds; release APKs explicitly disable debugging and retain the same certificate. Builds signed by another developer cannot update the official installation.

Configure these environment variables locally, without echoing or committing them:
LUME_KEYSTORE_PATH, LUME_STORE_PASSWORD, LUME_KEY_ALIAS, LUME_KEY_PASSWORD.

```sh
./gradlew -p buildSrc test
./gradlew lintDebug testDebugUnitTest assembleRelease
```

Verify using apksigner and zipalign with 16KB page support. Official certificate SHA-256:
79f748d439cfe5c39ca391d7ca146a85d6d84666b4114d45a8cc698b5877fcb7

Create a GitHub Release tagged v13.0.0 from the tested commit. Upload the same signed APK as both LumeGallery-v13.0.0.apk and LumeGallery.apk, plus SHA256SUMS.txt. Mark a stable build as latest. Download and verify assets before announcing availability.

Permanent URL after publication:
https://github.com/acresking/Lume-gallery/releases/latest/download/LumeGallery.apk

CI debug artifacts expire and are not the permanent installation link. No signing key is uploaded to CI by this project. Publishing a signed binary requires an authenticated owner release upload.


For v14 also run EditorPipelineTest: non-destructive photo pixels/alpha, normalized marks, history branching, clipped/speed-adjusted concatenation, actual Media3 effect composition, muted/audio-only outputs, PCM gain and time-based fades. On a phone check a real H.264/H.265 export, audio/video sync at 0.25/2/4x, mixed portrait/landscape concatenation, caption placement, camera HDR-to-SDR, M4A output, cancellation and all installed local-AI tools. The automated build does not replace these device checks.
