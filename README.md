# Fanlet

Fanlet puts invisible activation bars on the left and right edges of an Android
screen. Press a bar and a half-ring pie of app shortcuts fans out under your finger;
slide to an app and let go to open it. Hold still on a slice to switch it to a second
app.

**Status:** early development. The repository currently contains the project
skeleton; the features above are being built.

## Privacy

Fanlet draws on top of other apps, so it has to be trustworthy. It is designed so
that anyone can check what it can and cannot do:

- **No internet access.** Fanlet does not request the `INTERNET` permission, so
  Android does not let it open network connections. It cannot send data anywhere.
- **No Accessibility service.** Fanlet cannot read the content of other apps or of
  your screen, and cannot act on other apps on your behalf.
- **No screen capture and no usage access.**
- **No analytics, ads or crash reporting.** The only dependencies are the Kotlin
  standard library, kotlinx and AndroidX.
- **No cloud backup.** Fanlet's data is never included in Google's cloud backup.
  On Android 12 and newer it is copied only by a direct device-to-device transfer
  (for example when setting up a new phone with a cable). On Android 11 and older,
  Fanlet is excluded from backup and transfer entirely. The rules are in
  [`data_extraction_rules.xml`](app/src/main/res/xml/data_extraction_rules.xml) and
  [`backup_rules.xml`](app/src/main/res/xml/backup_rules.xml).

### Permissions

Every permission in the app's final manifest is listed here.

| Permission | Why |
|---|---|
| `dev.johncastles.fanlet.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Added automatically by the AndroidX Core library. It is private to Fanlet and only protects Fanlet's own internal broadcast receivers on older Android versions. It grants no access to anything outside the app. |

Upcoming features will add the "Display over other apps" permission (called "Appear
on top" on Samsung phones), a foreground service with its notification, and
vibration. Each one is listed here when it is added.

### How this is enforced

The build checks the merged release manifest, which includes everything added by
libraries, against an explicit allowlist in [`app/build.gradle.kts`](app/build.gradle.kts).
Any permission that is not on the list fails the build, and `INTERNET` fails it even
if someone adds it to the list. The check runs as part of `./gradlew check` and on
every push in CI.

To inspect an APK yourself, open it in Android Studio (**Build > Analyze APK**) or run
`apkanalyzer manifest permissions app-debug.apk` from the Android SDK command-line
tools.

## Requirements

Android 8.0 (API level 26) or newer.

## Building

You need:

- JDK 17 or newer to start Gradle. The build runs on JDK 25, which Gradle downloads
  automatically if it is not installed.
- The Android SDK with platform 37. Android Studio installs it; otherwise point
  `ANDROID_HOME` or `sdk.dir` in `local.properties` at your SDK.

```sh
# Lint, unit tests, permission check and debug APK
./gradlew lint testDebugUnitTest check assembleDebug

# Install on a connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

## License

Fanlet is free software, released under the GNU General Public License version 3
or (at your option) any later version. See [LICENSE](LICENSE).
