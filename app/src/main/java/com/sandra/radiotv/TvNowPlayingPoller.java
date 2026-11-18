package com.sandra.radiotv;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Small, activity-owned TV metadata client, independent of all playback state/commands. */
abstract class TvNowPlayingPoller {
    interface Listener {
        void onSong(String song);
    }

    private static final long FALLBACK_MS = 10_000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Listener listener;
    private final Runnable refresh = this::fetch;
    private Future<?> request;
    private volatile long generation;
    private String stationId;
    private String stream;
    private boolean active;
    private long nextFetchAt;

    TvNowPlayingPoller(Listener listener) {
        this.listener = listener;
    }

    // All lifecycle/state methods are called on the activity's main looper.
    final void setSource(String id, String providerStream) {
        if (java.util.Objects.equals(stationId, id)
                && java.util.Objects.equals(stream, providerStream)) return;
        cancel();
        stationId = id;
        stream = providerStream;
        nextFetchAt = 0;
        listener.onSong("");
        schedule();
    }

    void setActive(boolean value) {
        if (active == value) return;
        active = value;
        if (!active) cancel();
        else schedule();
    }

    void release() {
        active = false;
        cancel();
        worker.shutdownNow();
    }

    private void cancel() {
        generation++;
        handler.removeCallbacks(refresh);
        if (request != null) request.cancel(true);
        request = null;
    }

    private void schedule() {
        handler.removeCallbacks(refresh);
        if (active && stream != null && request == null) {
            handler.postDelayed(refresh, Math.max(0, nextFetchAt - SystemClock.elapsedRealtime()));
        }
    }

    private void fetch() {
        if (!active || stream == null || request != null) return;
        final long ticket = generation;
        final String requestedStream = stream;
        nextFetchAt = SystemClock.elapsedRealtime() + FALLBACK_MS;
        request = worker.submit(() -> {
            if (ticket != generation) return;
            String song = null;
            HttpURLConnection connection = null;
            long startedAt = SystemClock.elapsedRealtime();
            try {
                // requestedStream is from the fixed station mapping, never arbitrary user input.
                URL url = new URL(requestedStream);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5_000);
                connection.setReadTimeout(5_000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0");
                connection.setRequestProperty("Pragma", "no-cache");
                connection.setRequestProperty("Accept", "application/json, text/html, application/xml, text/xml, text/plain");
                if (ticket != generation) return;
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Metadata request failed");
                }
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                try (InputStream input = connection.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (ticket != generation || Thread.currentThread().isInterrupted()) return;
                        if (body.size() + count > 524_288
                                || SystemClock.elapsedRealtime() - startedAt > 15_000) {
                            throw new IOException("Metadata response limit exceeded");
                        }
                        body.write(buffer, 0, count);
                    }
                }
                String text = new String(body.toByteArray(), StandardCharsets.UTF_8);
                song = parseSong(text, requestedStream);
            } catch (Exception ignored) {
                // Metadata is optional: retain this station's last result and retry later.
            } finally {
                // Every completion, including parser/network failures, rearms the same loop.
                final String result = song;
                try {
                    if (connection != null) connection.disconnect();
                } finally {
                    handler.post(() -> {
                        if (ticket != generation || !active) return;
                        request = null;
                        try {
                            if (result != null) listener.onSong(result);
                        } finally {
                            // Fixed 10-second cadence from the previous fetch, without overlap.
                            schedule();
                        }
                    });
                }
            }
        });
    }

    protected abstract String parseSong(String body, String sourceUrl) throws Exception;
}
