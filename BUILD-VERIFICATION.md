# Lume Gallery 15.0.0 — verification

## Scope

Editor settings use inset cards with title/value badges, 48dp sliders and adaptive two-column choices on narrow screens or enlarged text. Existing editing recipes and exports remain intact. General settings retain search/categories, with more compact rows.

The map is an internal full-screen canvas with floating navigation, privacy information, attribution, center/zoom controls and a selected-photo card. Media details promote a map card when GPS is available and close before opening the map. Places has a collection-map entry using an existing indexed place as the initial center, then reads up to 500 geotagged photos from the currently filtered collection. No live location is used. The stale external-maps disclosure was corrected.

Subject extraction retains the existing local segmentation model and connected-component selection. It adds reselection from the already segmented foreground, adjustable erase/restore brush, six-step undo/redo and reset, plus scrollable controls. Source bitmaps and media remain unchanged. This does not separate touching people with a new instance-segmentation model.

## Automated results

- Signed release built successfully. 111 app tests passed, zero skipped. Lint: 0 errors and 43 warnings.
- Seven added tests verify correction undo/redo/reset without modifying foreground, reselection of a second separate subject with undo, variable brush size and disabled editing, compact editor choices and card structure, full-screen internal map with network disabled and detach on dismissal, extraction controls at narrow width/large text, and preventing archived-only groups from becoming the collection map center. Native-graphics synthetic previews support layout review.
- Existing gallery, editing pipeline, privacy, vault, grouping, cleanup, maps/metadata and pinch tests pass. No assertions were disabled. Tests use synthetic inputs, not private reference media.
- Previous signing certificate verified, application is not debuggable. ZIP alignment passes with 16KB pages. Three ARM64 ELF libraries have LOAD alignment >=16KB. Packaged DEX retains upstream MediaPipe DummyLogger with no ProtoLogger invocation.

## Artifact

File: `LumeGallery-v15.0.0.apk`  
Bytes: 135414646  
SHA-256: `a1a000c5307d4d941ed7fcf2aab55606f61d15775891fe50a975e62917980645`  
Certificate SHA-256: `79f748d439cfe5c39ca391d7ca146a85d6d84666b4114d45a8cc698b5877fcb7`

## Limits

Physical OnePlus interaction, map tile rendering over a real network, large GPS collections, accessibility services, encoder hardware and model inference require device testing. Network-off map tests exercise layout and policy without contacting a tile server. No new model or permission was introduced. Map providers still receive requested regions and IP when enabled; images remain local. Subject corrections are session-only, bounded in-memory masks. The map still uses up to 500 ordinary markers, without clustering or offline-region downloads. City/country resolution depends on the existing Android geocoder settings and availability. See PRIVACY.md.
