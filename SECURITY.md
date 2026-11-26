# Security policy

Never commit signing keys, credentials, personal media, model caches, local.properties or app databases. Release APKs must be non-debuggable and signed outside the repository. A public signing certificate is not a secret.

For a non-sensitive bug, open an issue with Android/app version and steps using public sample media. Do not attach face vectors, GPS, private photos, tokens or keys. For a sensitive vulnerability use GitHub private reporting if enabled by the owner; otherwise contact the owner privately before disclosing exploit details. No independent certification or staffed response SLA is claimed.

## Boundaries

- Inference and automatic organisation are local. Explicit model installation uses HTTPS, pinned digests/sizes and restricted redirect hosts.
- MediaPipe's stats factory is instrumented to its upstream dummy logger. The transform fails if the expected call changes. Vendor transport scheduling/discovery components are removed. Review on every dependency update.
- AES-GCM/Android Keystore protects album metadata and face profiles/covers. Other derived data and the recovery bin rely on app-private Android storage. UI authentication does not encrypt shared MediaStore files.
- Only the launcher Activity is public. App job services require BIND_JOB_SERVICE; the boot receiver is not exported. The transitive AndroidX profile receiver requires the system DUMP permission. No custom public content provider, WebView or accessibility service is present.
- Cleanup is opt-in and scoped to SAF grants, with a verified recoverable backup. A faulty/hostile document provider or concurrent source modification is outside a transactional filesystem guarantee.
- A rooted/compromised OS remains outside these protections. See PRIVACY.md.

Run lint and tests, verify signed non-debuggable release APKs, and check native/ZIP 16KB alignment. Android Keystore, device authentication, OEM scheduling, video codecs and full AI inference still require real-device testing.
