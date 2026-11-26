# Contributing

Issues and pull requests are welcome. Explain the problem, add reproducible steps with public sample media and keep changes focused. Never include private photos, location/face data, signing keys or credentials.

Use JDK 17+ and SDK 35:

```sh
./gradlew -p buildSrc test
./gradlew assembleDebug lintDebug testDebugUnitTest
```

Keep Hebrew RTL/accessibility working, follow DESIGN.md, and check 320dp width plus enlarged text. Respect animation preferences. Editing must preview and save a new copy. Background automation must be local, incremental, cancellable and optional. Cleanup requires explicit scoped access and a verified backup.

Model updates need pinned source revision, size, SHA-256, license details and realistic memory measurements. Never describe host tests as successful device tests. Add focused tests for behavior changes and update PRIVACY.md for data-use changes. Contributions are Apache-2.0; third-party materials retain their licenses.
