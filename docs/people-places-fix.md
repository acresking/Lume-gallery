# People and places correction draft

This branch is not a signed release. The v15 release receipt describes the previous APK, not these changes. Local execution/signing is unavailable in this session; validation must come from the repository's existing PR checks and later physical-device testing.

## Changes
- Keep up to eight diverse face exemplars per identity, retain the original anchor, and compare new faces against all examples. Preserve the configured similarity threshold; do not lower it to manufacture fewer groups. Reject ambiguous matches and prevent two distinct detections in one photo from being assigned to the same identity during that pass.
- Persist exemplars and representative-cover quality encrypted with the existing metadata key. One incremental schema migration rechecks existing photos using installed models, preserving groups/names until reprocessed. This does not guarantee recognition across glasses or extreme pose; real-library validation remains necessary.
- Use padded, unwarped face crops for covers, choose a larger representative when available, and load covers off the UI thread. Add explicit, confirmed group merging with encrypted exemplar/member union for existing duplicate identities.
- Save an actual representative GPS point per place instead of reverse-geocoding a 0.1-degree rounded grid center. New place cells are 0.01 degrees, not city boundaries. Incrementally rebuild old place membership without deleting source media.
- Cache up to 200 successful place names encrypted for 90 days; cache lookup works without a currently available geocoder and still respects place-name privacy settings. Increase bounded resolver queue capacity. No new geocoder provider is introduced. Missing GPS and unresolved city/country remain different states.
- Group map markers by place, show local representative thumbnails/counts, and open a dedicated gallery containing all indexed photos from that place within the current filtered scope. Swipe navigation stays in that subset. Up to 500 places are drawn, with all indexed members of each included place; no 500-photo cutoff within one place.
- Preserve map-network opt-out, tile attribution, archive/filter scoping, local media and ordinary screenshot policy. No model download, new permission, paid API or image upload is introduced.

## Validation
Six synthetic regression tests added in PeoplePlaceRegressionTest cover multi-exemplar matching, ambiguous matches, bounded persistence/pruning, grouped GPS/membership/scope, map grouping/scoped viewers, and cached names/privacy opt-out. They do not validate learned-model accuracy.
Existing CI should compile and run all app/buildSrc tests and lint on this draft PR.
Before a signed release: inspect real glasses/pose examples and erroneous merges; scan interrupted/resumed migrations; merge named/unnamed groups; test encrypted metadata across process restarts; test permission-restricted GPS/name fallback; verify crowded maps, hundreds of photos at one place, marker images, grid/swipe navigation and map-network disabled behavior.
No official APK or new passing-test count is claimed until those checks complete.
