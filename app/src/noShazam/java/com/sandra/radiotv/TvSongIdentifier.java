package com.sandra.radiotv;

import androidx.media3.exoplayer.audio.TeeAudioProcessor;
import java.nio.ByteBuffer;

/** Fallback when Apple's separately downloaded SDK has not been installed. */
public final class TvSongIdentifier implements TeeAudioProcessor.AudioBufferSink {
    public interface Listener {
        void onState(String mediaId, String state, String title, String artist);
    }

    private final Listener listener;

    public TvSongIdentifier(Listener listener) {
        this.listener = listener;
    }

    public void start(String mediaId) {
        TvIdentificationLog.error("configuration", new IllegalStateException(
                "SDK missing at app/libs/shazamkit-android-release.aar; "
                + "install Apple's official SDK and sync Gradle. PCM capture and matching were not started."));
        if (BuildConfig.SHAZAM_TOKEN_ENDPOINT.trim().isEmpty()) {
            TvIdentificationLog.info("setup: SHAZAM_TOKEN_ENDPOINT is empty; configure your HTTPS "
                    + "Apple developer-token backend in ~/.gradle/gradle.properties.");
        }
        listener.onState(mediaId, TvSongIdentification.ERROR, "", "");
    }

    public void reset() {
        listener.onState("", TvSongIdentification.IDLE, "", "");
    }

    public void playbackUnavailable() {
        // No recognition work exists in this implementation. Preserve the displayed result.
    }

    public void release() {}

    @Override
    public void flush(int sampleRateHz, int channelCount, int encoding) {}

    @Override
    public void handleBuffer(ByteBuffer buffer) {}
}
