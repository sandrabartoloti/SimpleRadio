package com.sandra.radiotv;

import android.os.Handler;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

/** TV-only, single-player recovery. Never resumes a user-paused or replaced station. */
final class TvPlaybackRecovery implements Player.Listener {
    interface Status { void changed(String stationId, boolean reconnecting); }
    private final ExoPlayer player;
    private final Status status;
    private final Handler handler;
    private Runnable pending;
    private boolean bufferingWatchdog;
    private String stationId = "";
    private int attempts;
    private boolean reconnecting;
    private boolean released;

    TvPlaybackRecovery(ExoPlayer player, Status status) {
        this.player = player;
        this.status = status;
        handler = new Handler(player.getApplicationLooper());
        player.addListener(this);
    }

    @Override
    public void onEvents(Player ignored, Player.Events events) {
        if (released) return;
        MediaItem item = player.getCurrentMediaItem();
        String id = item == null ? "" : item.mediaId;
        if (!stationId.equals(id) || events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
            cancel();
            attempts = 0;
            stationId = id;
            publish(false);
        }
        if (item == null || !player.getPlayWhenReady()) {
            cancel();
            attempts = 0;
            publish(false);
            return;
        }
        if (player.getPlaybackState() == Player.STATE_READY) {
            cancel();
            attempts = 0;
            publish(false);
            return;
        }
        PlaybackException error = player.getPlayerError();
        if (error != null) {
            if (retryable(error)) {
                if (bufferingWatchdog) cancel();
                publish(true);
                schedule(Math.min(30_000L, 2_000L << Math.min(attempts, 4)), false);
            } else {
                cancel();
                publish(false);
            }
        } else if (player.getPlaybackState() == Player.STATE_BUFFERING) {
            // Give Media3's own loading retries time to recover before reopening a stalled stream.
            schedule(45_000L, true);
        } else if (player.getPlaybackState() == Player.STATE_ENDED) {
            publish(true);
            schedule(Math.min(30_000L, 2_000L << Math.min(attempts, 4)), false);
        } else {
            // Explicit stop, including controller commands, must never trigger a restart.
            cancel();
            publish(false);
        }
    }

    private boolean retryable(PlaybackException error) {
        return error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                || error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                || error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                || error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                || error.errorCode == PlaybackException.ERROR_CODE_TIMEOUT;
    }

    private void schedule(long delay, boolean watchdog) {
        if (pending != null) return;
        bufferingWatchdog = watchdog;
        String requestedStation = stationId;
        pending = () -> {
            pending = null;
            MediaItem current = player.getCurrentMediaItem();
            if (released || current == null || !requestedStation.equals(current.mediaId)
                    || !player.getPlayWhenReady() || player.getPlaybackState() == Player.STATE_READY) return;
            PlaybackException error = player.getPlayerError();
            int state = player.getPlaybackState();
            if (error != null && !retryable(error)) return;
            if (error == null && state != Player.STATE_BUFFERING && state != Player.STATE_ENDED) return;
            attempts++;
            publish(true);
            // Reuse the same item/player. Only stalled buffering needs an explicit stop.
            if (state == Player.STATE_BUFFERING) player.stop();
            player.seekToDefaultPosition();
            player.prepare();
        };
        handler.postDelayed(pending, delay);
    }

    private void publish(boolean value) {
        if (reconnecting == value) return;
        reconnecting = value;
        status.changed(stationId, value);
    }

    private void cancel() {
        if (pending != null) handler.removeCallbacks(pending);
        pending = null;
        bufferingWatchdog = false;
    }

    void release() {
        released = true;
        cancel();
        player.removeListener(this);
    }
}
