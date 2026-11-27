# Lume Gallery 12.0.0 — verification

## Changes checked

Popup dialogs use shared rounded sheet styling, light/dark themes, RTL layout, motion preference and stacked long action labels. Caller OnShow/choice listeners remain intact. Photo and video studios share larger controls and a clear primary copy-export action; video preview adds explicit play/pause and mute feedback. Existing editing/export algorithms and the original files are preserved.

Favorites immediately change persisted membership, label, selected state and icon fill/color. Pinch density now transitions into actual month/year cover-card presentations derived from the filtered scope; card taps move toward the selected date. Manual view-type/density settings, selection and date navigation remain available.

Maps use native osmdroid 6.1.20 and HTTPS OpenStreetMap tiles inside the app. No external-map intent is sent. The visible map region and IP reach the tile provider; image data and marker filenames stay local. Attribution links, app User-Agent, bounded private cache and a network opt-out are configured. There is no live location permission or bulk tile download. Additional markers read GPS from up to 500 photos in the current filtered collection, preserving archive/filter scope.

People grouping can run in small foreground batches after model setup, with existing Android jobs as fallback. It observes user toggles, explicit charging preference and battery constraints; the open people list refreshes from encrypted metadata changes. A one-time existing Lume AI bundle installation is required, without a new remote inference model.

Holding an image point triggers local portrait segmentation and connected-component selection near that point. An erase/restore preview is mandatory before explicit PNG save/share. Sticker output is a transparent 512x512 PNG with white outline. Source files/EXIF are not modified or copied into the output. Touching people may stay in the same component and require manual correction; this is not a per-person instance-segmentation model or native WhatsApp sticker pack integration.

## Results

- Release build and all 76 app tests passed. Android Lint: zero errors, 39 warnings. New advisories include the deliberately programmatic SubjectBoard constructor and existing localization/redraw/touch recommendations.
- Fifteen added tests cover favorite toggling/persistence and filled pixels; real calendar presentation transitions/counts/scopes; cutout component isolation and known touching-person behavior; transparent sticker dimensions; held-point letterboxing; erase/restore source preservation and brush selection; native map center/resource closure without an external intent; retained dialog callbacks/stacked long actions; automatic-people setup; and narrow/large-font photo/video/subject controls. The earlier grid-limit test was updated for the intended month-overview boundary. All prior tests pass.
- Editor previews use synthetic sample media only. No user recording frames, faces, locations, account identifiers or signing keys are committed.
- Signature verified against the prior certificate. ZIP 16KB alignment and all three ARM64 ELF LOAD alignments passed. Packaged DEX still invokes MediaPipe's upstream DummyLogger instead of remote ProtoLogger.

## Artifact

File: `LumeGallery-v12.0.0.apk`  
Bytes: 134525770  
SHA-256: `d0c61312f30b4553b1fb6d11d8542438d2ea54e5cc7f45a9cd848275ad68d9cf`  
Certificate SHA-256: `79f748d439cfe5c39ca391d7ca146a85d6d84666b4114d45a8cc698b5877fcb7`

## Limits

No physical-device test is claimed. Hardware inference quality/performance, real gesture feel, map tile availability, Android background scheduling, video codecs and MediaStore export/sharing need phone testing. The tests exercise synthetic masks and UI/state, not a full on-device segmentation/face-inference benchmark. Map tests disable network; successful network tile delivery is not claimed from unit tests. osmdroid is pinned and its source/attribution links are in NOTICE.

People require installed models and permissions; Android controls background timing. Preview masks can merge touching people or omit disconnected body regions; users must inspect/correct before sharing. PNG assets may need import into a sticker-capable app and are not guaranteed to arrive as native stickers in every recipient app. Existing vault credential and temporary private-preview-cache limitations remain documented in PRIVACY.md.
