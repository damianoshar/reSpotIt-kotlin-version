# Tests & CI — Kotlin client

```bash
cd android
./gradlew :app:testDebugUnitTest          # 17 JVM unit-test files
./gradlew :app:connectedDebugAndroidTest   # 3 instrumented Compose/Espresso tests (needs a device/emulator)
```

Unit tests cover the API contract (`DashboardLlmContractTest`, `SaveAlbumsRequestTest`, `AddTracksRequestTest`, envelope deserializers), data helpers (`CSVHelperTest`, `DuplicateFinderTest`, `ActiveQueuePlannerTest`, `QueueSnapshotTest`, `QueueSnapshotQueueTailTest`, `PlaylistListOptionsTest`, `PlaylistRetentionStoreTest`, `SyncHistoryTest`, `AiChatTest`, `ViewModeTest`, `PlaybackDeviceTest`, `ConcertCalendarDatesTest`).

CI (`.github/workflows/`):

| Workflow | Trigger | What it does |
|---|---|---|
| `tests.yml` | every push / PR | JDK 17 + Android 34: runs `:app:testDebugUnitTest`, assembles the debug APK, and asserts the merged manifest keeps a single OAuth callback handler; plus a non-blocking instrumented-test job on an API 34 emulator |
| `release.yml` | `v*` tags or manual | decodes the release keystore, runs `:app:assembleRelease`, publishes the APK to the GitHub release |
