# SimpleRadio

Android phone and Android TV radio app with built-in Local and International stations, favorites, Now Playing metadata, and a shared Media3 playback service.

Android TV includes D-pad navigation, a categorized screensaver picker with favorites, automatic screensavers, and procedural modes including Christmas Village Animated with its bundled illustration.

## Project setup

The project includes the Gradle wrapper and application sources/assets. Local SDK paths, generated APKs/build caches, and signing credentials are not committed.

ShazamKit is optional. See [SHAZAMKIT_SETUP.md](SHAZAMKIT_SETUP.md) for Apple's SDK and token endpoint configuration. Radio builds work without the SDK. Never commit Apple keys or tokens.

## Open in Android Studio

1. Open Android Studio.
2. Choose **Open**.
3. Select this project's folder, `SimpleRadio-Android`.
4. In **Settings > Build, Execution, Deployment > Build Tools > Gradle**, use Android Studio's bundled JDK as the Gradle JDK. This Mac's system Java 8 cannot run the project's Gradle version.
5. In **Tools > SDK Manager**, install **Android SDK Platform 36** and **Android SDK Build-Tools 36.0.0**, then sync the project with Gradle.
6. Connect your Sony Bravia TV with ADB debugging enabled, then run the `app` configuration. Building an APK does not require a connected device.

If the TV does not appear, enable Developer Options on the TV and turn on USB debugging or network debugging.

## Build from Terminal on macOS

From this project's folder, use Android Studio's bundled Java runtime:

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
Keep `local.properties` machine-specific; its `sdk.dir` must point to your installed Android SDK.
