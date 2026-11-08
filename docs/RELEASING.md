# Releasing

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Configure signing locally:
   ```bash
   cp keystore.properties.example keystore.properties
   # fill in storeFile / storePassword / keyAlias / keyPassword
   ```
3. Build and verify:
   ```bash
   ./gradlew :app:assembleRelease
   $ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs \
     app/build/outputs/apk/release/app-release.apk
   shasum -a 256 app/build/outputs/apk/release/app-release.apk
   ```
4. Copy the APK to a local path outside git (the `dist/` directory is ignored):
   ```bash
   mkdir -p dist
   cp app/build/outputs/apk/release/app-release.apk dist/AppLens-<version>.apk
   ```
5. Create the GitHub release and attach the APK:
   ```bash
   gh release create v<version> dist/AppLens-<version>.apk \
     --title "AppLens <version>" \
     --notes-file dist/RELEASE_NOTES_v<version>.md \
     --target main
   ```

Never commit `keystore.properties`, `*.keystore`, `*.jks`, `local.properties`, or generated APKs.
The repository's CI builds an unsigned debug APK and uploads it as a workflow artifact.
