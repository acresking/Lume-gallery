# Lume Gallery 13.0.0 — verification

## Changes checked

The first private reference recording was re-inspected, including the initial dense-grid pinch sequence. The implementation now keeps all photos visible in 2/3/5-column grids, 9-column month mosaics and 15-column year mosaics rather than automatically replacing them with cover cards. Explicit cover-card modes remain available.

Photo chrome is a full-span item inside RecyclerView, not permanent rows above it. The default is one compact control row and a memory expand action. Expanded memories also scroll away. The header/dock are more compact and the default unconfigured appearance follows the black reference style; explicitly selected light/system preferences remain intact. Filters, date jumps, grouping, selection and scope are retained; filters also remain reachable from title overflow.

Pinch feedback scales the canvas while keeping touch bounds stable, anchors the focused photo vertically and morphs shared cells into new bounds. It does not fade the whole grid. Recycling cancels stale morph transforms and thumbnail requests. Dense thumbnails use smaller requests, upgrade on zoom-in and cannot replace a larger cached result with a late small result. Density/grouping are remembered across a cold Activity start.

## Results

- Release build and all 88 app tests passed. Android Lint: zero errors and 39 warnings.
- Twelve added tests cover viewport share and scrolling chrome; explicit memory expansion and expanded chrome scrolling; real-photo month/year mosaics; density boundaries; vertical focal anchoring; canvas-only feedback without alpha/touch-bound changes; filtered scope and manual covers; explicit light preference; cancellation of recycled/rebound thumbnails; cache resolution protection; cold-start grouping persistence; and stale morph reset on rebind.
- The compact-layout fixture checks that the grid exceeds 72% of a 320x720dp viewport and the collapsed chrome is at most 56dp, then verifies it is absent after scrolling. Expanded chrome is separately checked to scroll out of view. These are narrow-screen fixtures, not a universal percentage guarantee for every accessibility/font/window setting.
- Earlier assertions for fixed filter rows, the header adapter offset, cover-card pinch boundaries and the former purple-tinted light background were updated for the intentional UI changes. The prior functional tests also pass.
- Test classes run in isolated JVMs; the mixed EXIF suite uses native graphics consistently to avoid Robolectric 4.14.1 font-filesystem initialization interference. No tests were disabled or skipped. Upstream issue: https://github.com/robolectric/robolectric/issues/10116 .
- Signature verified against the previous certificate. ZIP alignment passed with 16KB pages, and all three ARM64 ELF LOAD alignments are at least 16KB. Packaged DEX still invokes MediaPipe's upstream DummyLogger rather than remote ProtoLogger.
- Android previews use synthetic sample media. The private reference recording and all extracted frames remain outside the source repository; no user faces, media, locations, accounts or signing keys are committed.

## Artifact

File: `LumeGallery-v13.0.0.apk`  
Bytes: 134542158  
SHA-256: `43af139cc1375b93a5fa8fffbaa3d9c8d840b27c6ada0b166f9db167dbe3b95c`  
Certificate SHA-256: `79f748d439cfe5c39ca391d7ca146a85d6d84666b4114d45a8cc698b5877fcb7`

## Limits

No physical-device test is claimed. Real OnePlus gesture feel, 165Hz frame pacing, long-library throughput, provider cancellation, OEM authentication/background scheduling, codecs, hardware AI inference and MediaStore export require phone testing. Focal anchoring is vertical; horizontal positions follow the new grid column arrangement. Cell morphs are capped at 128 visible shared cells for bounded animation work. This is reference-inspired behavior, not proprietary OEM animation code or a guarantee of identical physics.

No new model, network service or permission was introduced. Existing local-AI setup, subject-mask correction limits, map tile privacy and vault credential/cache behavior remain documented in PRIVACY.md. Original media, archive membership, face groups, cleanup rules and other features are not changed by presentation controls.
