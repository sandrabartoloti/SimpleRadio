package com.sandra.radiotv;

import android.content.Context;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.audio.AudioOffloadSupport;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.audio.ForwardingAudioSink;
import androidx.media3.exoplayer.audio.TeeAudioProcessor;

/** Installed once, only on TV. Identification never reconfigures or restarts the player. */
final class TvAudioRenderersFactory extends DefaultRenderersFactory {
    private final TeeAudioProcessor.AudioBufferSink recognitionSink;

    TvAudioRenderersFactory(Context context, TeeAudioProcessor.AudioBufferSink recognitionSink) {
        super(context);
        this.recognitionSink = recognitionSink;
    }

    @Override
    protected AudioSink buildAudioSink(Context context, boolean enableFloatOutput,
            boolean enableAudioOutputPlaybackParams) {
        AudioSink sink = new DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(false)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .setAudioProcessors(new AudioProcessor[] { new TeeAudioProcessor(recognitionSink) })
                .build();
        // Offload and compressed passthrough bypass AudioProcessors. Decode to PCM from the
        // outset, so pressing Identify never changes the stream, audio route, or player state.
        return new ForwardingAudioSink(sink) {
            @Override
            public boolean supportsFormat(Format format) {
                return getFormatSupport(format) != SINK_FORMAT_UNSUPPORTED;
            }

            @Override
            public int getFormatSupport(Format format) {
                return MimeTypes.AUDIO_RAW.equals(format.sampleMimeType)
                        ? super.getFormatSupport(format) : SINK_FORMAT_UNSUPPORTED;
            }

            @Override
            public AudioOffloadSupport getFormatOffloadSupport(Format format) {
                return AudioOffloadSupport.DEFAULT_UNSUPPORTED;
            }
        };
    }
}
