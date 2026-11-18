# reels-core

Pure Kotlin counting and detection domain used by the Android accessibility service: per-app adapters (`detection/social/<app>`), shared viewer/feed locators and fingerprinting (`detection/shared`), the encounter tracker (`reels/CounterReducer.kt`) and conservative playback evidence. No Android imports; `tools/check_architecture.py` enforces the boundaries.

```powershell
python tools/android_build.py :reels-core:verifyReels
```

Tests include sanitised real screen layouts under `src/test/resources/fixtures/<app>/`. How detection works, the counting rules and how to add a fixture or an app: [docs/DETECTION.md](../../docs/DETECTION.md).
