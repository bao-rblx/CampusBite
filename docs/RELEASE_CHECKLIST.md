# CampusBite — Release Packaging & Verification Checklist (v1.0.0)

- [x] Version code: 1, Version name: 1.0.0
- [x] R8 / ProGuard shrinking & obfuscation enabled in `build.gradle.kts`
- [x] Release keystore generated (`scripts/generate_keystore.ps1`)
- [x] Release APK signed (`apksigner verify` confirms CN=CampusBite, OU=Group02)
- [x] LeakCanary excluded from release APK (debugImplementation only)
- [x] Cloud backup rules configured to exclude sensitive user session data
- [x] All instrumented tests and unit tests pass
