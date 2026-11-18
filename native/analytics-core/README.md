# analytics-core

Pure Kotlin calculation library: interval union and civil-day splitting, disjoint app/surface time breakdown, replay deduplication, interaction intents versus observed outcomes, network byte totals, calendar rollup previews and time-equivalent arithmetic. It has no Android, storage or network code and all test input is synthetic.

It is linked into the Android build (`android/analytics-core`) but not yet called by app code; Today and Insights queries currently run in `android/app/.../diary/DiaryEngine.kt`.

```powershell
python tools/android_build.py :analytics-core:verifyAnalytics   # through Gradle
python tools/test_analytics.py [--offline] [--example]           # standalone, pinned compiler from tools/kotlin-toolchain.json
```

The standalone runner downloads the pinned Kotlin compiler once into `.cache/analytics-tools`, verifies its size and SHA-256, and fails on compiler warnings. See [docs/ARCHITECTURE.md](../../docs/ARCHITECTURE.md).
