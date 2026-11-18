# ShazamKit setup for Android TV

## Apple SDK

Download **ShazamKit for Android 2.1.1** from [Apple Developer Downloads](https://developer.apple.com/download/all/?q=Android%20ShazamKit) after signing in and completing Apple's download requirements. Put the official AAR at:

```text
app/libs/shazamkit-android-release.aar
```

The AAR is deliberately not checked in. Without it, Gradle selects a fallback that lets the radio app build normally; Identify Song displays “Unable to identify song” and no PCM tap is installed. After adding the AAR, sync the project with Gradle to enable the real implementation and its supporting libraries from [Apple's Android setup documentation](https://developer.apple.com/shazamkit/android/). AGP 9 supplies built-in Kotlin support for the SDK bridge. The two implementations are selected exclusively to avoid duplicate classes.

## Apple Developer credentials and token endpoint

1. In your Apple Developer account, [create a Media ID and Media Services private key](https://developer.apple.com/help/account/capabilities/create-a-media-identifier-and-private-key/), enable ShazamKit, and associate the key with the Media ID.
2. On your backend, generate short-lived Apple developer JWTs following Apple's linked developer-token instructions. Keep the signing private key on the server; do not put a private key or token in this repository, Gradle properties, or the APK.
3. Provide a direct HTTPS GET endpoint returning a fresh token as JSON, with no redirect and a response below 16 KiB:

   ```json
   { "token": "<fresh Apple developer JWT>" }
   ```

4. Set the endpoint's public URL in your user-level `~/.gradle/gradle.properties` (or supply the same Gradle property through your build environment):

   ```properties
   SHAZAM_TOKEN_ENDPOINT=https://your-backend.example/shazam/developer-token
   ```

Only the endpoint URL is included in BuildConfig. The app fetches a token on each identification request, does not persist it, and cancels that fetch when recognition is cancelled. An absent endpoint, expired/rejected token, network failure, or unavailable SDK native library produces “Unable to identify song”.

## Integration scope

### Diagnosing Identify Song

Filter Android Studio Logcat by `tag:TvSongIdentification`. The SDK-absent fallback now explicitly reports the missing AAR and whether the endpoint is empty; it does not capture PCM, initialize ShazamKit, or send a match request. In the SDK-enabled implementation, logs distinguish token HTTP status, catalog initialization, first PCM buffer (rate/channels/encoding), mono conversion, captured frame count and peak, signature size/duration, and entry into `Session.match`. A match result proves the SDK completed the request; the invocation log alone does not prove the network request reached Apple. SDK failures retain the actual `internalError`/`matchError`, exception causes, and sanitized stack traces. Audio, signature contents, tokens, and endpoint URLs are not logged.

The source review found `app/libs/shazamkit-android-release.aar` absent and the project endpoint empty, with no user-level Gradle properties file. These prevent the real recognition path from starting. Complete the SDK and backend steps above, then sync Gradle and rebuild on your side. PCM delivery, signature generation, and authentication cannot be confirmed at runtime until that setup is present; no build or test was run during this fix.

Only Android TV exposes the action and installs the PCM tap. The phone's existing renderer, UI, and all screensavers retain their existing behavior. Recognition uses the existing ExoPlayer and MediaSession in RadioPlaybackService. A MediaSession custom command starts each request; session extras retain the result across controller reconnections until a new request or station change.

The TV audio sink decodes to 16-bit PCM from player creation, with compressed passthrough/offload disabled because those paths bypass audio processors. Pressing Identify does not alter playback configuration, URL, position, or play state. Media3's TeeAudioProcessor passes playback audio through unchanged and supplies a read-only buffer. While idle, the recognition sink immediately returns without copying or analyzing audio.

On demand, a bounded non-blocking queue feeds up to 10 seconds of PCM to a background worker, which downmixes to mono and uses the official ShazamKit SignatureGenerator and Session. It supports ShazamKit's 16, 32, 44.1, and 48 kHz sample rates; unsupported formats fail recognition without changing playback. Queue overload, audio discontinuity, playback interruption, and timeouts also fail only the recognition request. PCM is held only in bounded temporary memory, discarded on completion/cancellation, and never written to disk or sent to the token endpoint. ShazamKit performs matching using a generated signature. No microphone permission is added.

No build, emulator run, or tests were performed for this change, as requested.
