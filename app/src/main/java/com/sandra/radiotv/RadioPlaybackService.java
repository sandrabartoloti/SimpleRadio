package com.sandra.radiotv;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.extractor.metadata.icy.IcyInfo;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionError;
import androidx.media3.session.SessionResult;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

public class RadioPlaybackService extends MediaSessionService {
    public static final String ACTION_SET_SLEEP_TIMER = "com.sandra.radiotv.SET_SLEEP_TIMER";
    public static final String ACTION_CANCEL_SLEEP_TIMER = "com.sandra.radiotv.CANCEL_SLEEP_TIMER";
    public static final String ACTION_RESET_LISTENING_ACTIVITY = "com.sandra.radiotv.RESET_LISTENING_ACTIVITY";
    public static final String EXTRA_SLEEP_TIMER_END_TIME = "sleep_timer_end_time";
    public static final String PREFS_NAME = "simple_radio_prefs";
    public static final String PREF_SLEEP_TIMER_END_TIME = "sleep_timer_end_time";
    public static final String PREF_LISTENING_ALL_TIME_MS = "listening_all_time_ms";
    public static final String PREF_LISTENING_DAILY_MS = "listening_daily_ms";
    public static final String PREF_LISTENING_STATIONS_MS = "listening_stations_ms";
    public static final String PREF_ACTIVE_LISTENING_STATION_ID = "active_listening_station_id";
    public static final String PREF_ACTIVE_LISTENING_STATION_NAME = "active_listening_station_name";
    public static final String PREF_ACTIVE_LISTENING_START_MS = "active_listening_start_ms";

    static final String EXTRA_ICY_STATION = "tv_icy_station";
    static final String EXTRA_ICY_SONG = "tv_icy_song";

    static final String EXTRA_RECONNECTING_STATION = "tv_reconnecting_station";
    private TvPlaybackRecovery tvRecovery;
    private ExoPlayer player;
    private MediaSession mediaSession;
    private TvSongIdentifier songIdentifier;
    private final Handler sleepTimerHandler = new Handler(Looper.getMainLooper());
    private final Runnable sleepTimerRunnable = this::expireSleepTimer;
    private long listeningSessionStartMs = 0L;
    private String listeningStationId = "";
    private String listeningStationName = "";

    @Override
    public void onCreate() {
        super.onCreate();

        if (TvSongIdentification.isTelevision(this)) {
            songIdentifier = new TvSongIdentifier(this::publishIdentification);
            ExoPlayer.Builder playerBuilder = BuildConfig.SHAZAM_SDK_AVAILABLE
                    ? new ExoPlayer.Builder(this, new TvAudioRenderersFactory(this, songIdentifier))
                    : new ExoPlayer.Builder(this);
            player = playerBuilder.build();
        } else {
            player = new ExoPlayer.Builder(this).build();
        }
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build();
        player.setAudioAttributes(audioAttributes, true);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                if (songIdentifier != null) songIdentifier.playbackUnavailable();
                // Keep the service alive so the UI can reconnect and choose another station.
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    startListeningSession();
                } else {
                    finishListeningSession();
                    if (songIdentifier != null) songIdentifier.playbackUnavailable();
                }
            }

            @Override
            public void onMediaItemTransition(MediaItem mediaItem, int reason) {
                publishExtraSong("", "");
                if (songIdentifier != null) songIdentifier.reset();
                finishListeningSession();
                if (player != null && player.isPlaying()) {
                    startListeningSession();
                }
            }

            @Override
            public void onMetadata(Metadata metadata) {
                // Consume ICY for both player UIs from the existing stream connection.
                MediaItem current = player.getCurrentMediaItem();
                if (current == null || current.localConfiguration == null
                        || !"https://audio.social3.hr/listen/extra_aac/stream".equals(
                                current.localConfiguration.uri.toString())) return;
                for (int i = 0; i < metadata.length(); i++) {
                    if (!(metadata.get(i) instanceof IcyInfo)) continue;
                    String title = ((IcyInfo) metadata.get(i)).title;
                    if (title == null) continue;
                    int separator = title.indexOf(" - ");
                    if (separator < 0) continue;
                    String artist = title.substring(0, separator).trim();
                    String song = title.substring(separator + 3).trim();
                    if (!artist.isEmpty() && !song.isEmpty()) {
                        publishExtraSong(current.mediaId, artist + " — " + song);
                    }
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                    finishListeningSession();
                }
            }
        });

        mediaSession = new MediaSession.Builder(this, player)
                .setId("simple_radio_session")
                .setCallback(new MediaSession.Callback() {
                    @Override
                    public ListenableFuture<MediaSession.ConnectionResult> onConnectAsync(
                            MediaSession session, MediaSession.ControllerInfo controller) {
                        MediaSession.ConnectionResult defaults =
                                new MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build();
                        if (songIdentifier == null || !getPackageName().equals(controller.getPackageName())) {
                            return Futures.immediateFuture(defaults);
                        }
                        return Futures.immediateFuture(
                                new MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                                        .setAvailableSessionCommands(defaults.availableSessionCommands.buildUpon()
                                                .add(new SessionCommand(TvSongIdentification.IDENTIFY, Bundle.EMPTY))
                                                .build())
                                        .build());
                    }

                    @Override
                    public ListenableFuture<SessionResult> onCustomCommand(MediaSession session,
                            MediaSession.ControllerInfo controller, SessionCommand command, Bundle args) {
                        if (!TvSongIdentification.IDENTIFY.equals(command.customAction)
                                || songIdentifier == null
                                || !getPackageName().equals(controller.getPackageName())) {
                            return Futures.immediateFuture(new SessionResult(SessionError.ERROR_NOT_SUPPORTED));
                        }
                        MediaItem current = player.getCurrentMediaItem();
                        String mediaId = current == null ? "" : current.mediaId;
                        // Ignore commands that arrived after a station switch.
                        if (!mediaId.equals(args.getString(TvSongIdentification.MEDIA_ID, ""))) {
                            TvIdentificationLog.info("request: rejected because the station changed");
                            return Futures.immediateFuture(new SessionResult(SessionError.ERROR_INVALID_STATE));
                        }
                        if (!player.isPlaying()) {
                            TvIdentificationLog.info("request: no playing PCM source; playbackState="
                                    + player.getPlaybackState());
                            publishIdentification(mediaId, TvSongIdentification.ERROR, "", "");
                        } else {
                            songIdentifier.start(mediaId);
                        }
                        return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
                    }
                })
                .build();
        if (TvSongIdentification.isTelevision(this)) {
            tvRecovery = new TvPlaybackRecovery(player, (stationId, reconnecting) -> {
                if (mediaSession == null) return;
                Bundle extras = new Bundle(mediaSession.getSessionExtras());
                extras.putString(EXTRA_RECONNECTING_STATION, reconnecting ? stationId : "");
                mediaSession.setSessionExtras(extras);
            });
        }
        restoreSleepTimer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_SET_SLEEP_TIMER.equals(intent.getAction())) {
            long endTime = intent.getLongExtra(EXTRA_SLEEP_TIMER_END_TIME, 0L);
            setSleepTimer(endTime);
        } else if (intent != null && ACTION_CANCEL_SLEEP_TIMER.equals(intent.getAction())) {
            cancelSleepTimer();
        } else if (intent != null && ACTION_RESET_LISTENING_ACTIVITY.equals(intent.getAction())) {
            resetListeningActivity();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (player == null || !player.isPlaying()) {
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        sleepTimerHandler.removeCallbacks(sleepTimerRunnable);
        if (tvRecovery != null) tvRecovery.release();
        if (songIdentifier != null) {
            songIdentifier.release();
            songIdentifier = null;
        }
        finishListeningSession();
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    private void publishExtraSong(String stationId, String song) {
        if (mediaSession == null) return;
        Bundle extras = new Bundle(mediaSession.getSessionExtras());
        if (stationId.equals(extras.getString(EXTRA_ICY_STATION, ""))
                && song.equals(extras.getString(EXTRA_ICY_SONG, ""))) return;
        extras.putString(EXTRA_ICY_STATION, stationId);
        extras.putString(EXTRA_ICY_SONG, song);
        mediaSession.setSessionExtras(extras);
    }

    private void publishIdentification(String mediaId, String state, String title, String artist) {
        Bundle identificationState = new Bundle();
        identificationState.putString(TvSongIdentification.MEDIA_ID, mediaId);
        identificationState.putString(TvSongIdentification.STATE, state);
        identificationState.putString(TvSongIdentification.TITLE, title);
        identificationState.putString(TvSongIdentification.ARTIST, artist);
        if (mediaSession != null) {
            Bundle extras = new Bundle(mediaSession.getSessionExtras());
            extras.putBundle(TvSongIdentification.EXTRAS, identificationState);
            mediaSession.setSessionExtras(extras);
        }
    }

    private void restoreSleepTimer() {
        long endTime = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getLong(PREF_SLEEP_TIMER_END_TIME, 0L);
        if (endTime > System.currentTimeMillis()) {
            scheduleSleepTimer(endTime);
        } else if (endTime > 0L) {
            cancelSleepTimer();
        }
    }

    private void setSleepTimer(long endTime) {
        if (endTime <= System.currentTimeMillis()) {
            cancelSleepTimer();
            return;
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putLong(PREF_SLEEP_TIMER_END_TIME, endTime)
                .apply();
        scheduleSleepTimer(endTime);
    }

    private void scheduleSleepTimer(long endTime) {
        sleepTimerHandler.removeCallbacks(sleepTimerRunnable);
        long delayMs = Math.max(0L, endTime - System.currentTimeMillis());
        sleepTimerHandler.postDelayed(sleepTimerRunnable, delayMs);
    }

    private void cancelSleepTimer() {
        sleepTimerHandler.removeCallbacks(sleepTimerRunnable);
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .remove(PREF_SLEEP_TIMER_END_TIME)
                .apply();
    }

    private void expireSleepTimer() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long endTime = prefs.getLong(PREF_SLEEP_TIMER_END_TIME, 0L);
        if (endTime > 0L && endTime <= System.currentTimeMillis()) {
            if (player != null) {
                player.pause();
            }
            prefs.edit().remove(PREF_SLEEP_TIMER_END_TIME).apply();
        } else if (endTime > System.currentTimeMillis()) {
            scheduleSleepTimer(endTime);
        }
    }

    private void resetListeningActivity() {
        listeningSessionStartMs = 0L;
        listeningStationId = "";
        listeningStationName = "";
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .remove(PREF_LISTENING_ALL_TIME_MS)
                .remove(PREF_LISTENING_DAILY_MS)
                .remove(PREF_LISTENING_STATIONS_MS)
                .remove(PREF_ACTIVE_LISTENING_STATION_ID)
                .remove(PREF_ACTIVE_LISTENING_STATION_NAME)
                .remove(PREF_ACTIVE_LISTENING_START_MS)
                .apply();
        if (player != null && player.isPlaying()) {
            startListeningSession();
        }
    }

    private void startListeningSession() {
        if (player == null || listeningSessionStartMs > 0L) {
            return;
        }
        MediaItem item = player.getCurrentMediaItem();
        if (item == null || item.mediaId == null || item.mediaId.trim().isEmpty()) {
            return;
        }
        listeningStationId = item.mediaId;
        CharSequence title = item.mediaMetadata.title;
        listeningStationName = title == null ? item.mediaId : title.toString();
        listeningSessionStartMs = System.currentTimeMillis();
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_ACTIVE_LISTENING_STATION_ID, listeningStationId)
                .putString(PREF_ACTIVE_LISTENING_STATION_NAME, listeningStationName)
                .putLong(PREF_ACTIVE_LISTENING_START_MS, listeningSessionStartMs)
                .apply();
    }

    private void finishListeningSession() {
        if (listeningSessionStartMs <= 0L || listeningStationId == null || listeningStationId.isEmpty()) {
            clearActiveListeningSession();
            return;
        }
        long elapsedMs = Math.max(0L, System.currentTimeMillis() - listeningSessionStartMs);
        if (elapsedMs >= 1000L) {
            addListeningDuration(listeningStationId, listeningStationName, elapsedMs);
        }
        listeningSessionStartMs = 0L;
        listeningStationId = "";
        listeningStationName = "";
        clearActiveListeningSession();
    }

    private void clearActiveListeningSession() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .remove(PREF_ACTIVE_LISTENING_STATION_ID)
                .remove(PREF_ACTIVE_LISTENING_STATION_NAME)
                .remove(PREF_ACTIVE_LISTENING_START_MS)
                .apply();
    }

    private void addListeningDuration(String stationId, String stationName, long elapsedMs) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putLong(PREF_LISTENING_ALL_TIME_MS, prefs.getLong(PREF_LISTENING_ALL_TIME_MS, 0L) + elapsedMs)
                .putString(PREF_LISTENING_DAILY_MS, addDurationToJsonObject(prefs.getString(PREF_LISTENING_DAILY_MS, "{}"), getTodayKey(), null, elapsedMs))
                .putString(PREF_LISTENING_STATIONS_MS, addDurationToJsonObject(prefs.getString(PREF_LISTENING_STATIONS_MS, "{}"), stationId, stationName, elapsedMs))
                .apply();
    }

    private String addDurationToJsonObject(String rawJson, String key, String name, long elapsedMs) {
        try {
            org.json.JSONObject root = new org.json.JSONObject(rawJson);
            if (name == null) {
                root.put(key, root.optLong(key, 0L) + elapsedMs);
            } else {
                org.json.JSONObject station = root.optJSONObject(key);
                if (station == null) {
                    station = new org.json.JSONObject();
                }
                station.put("name", name);
                station.put("ms", station.optLong("ms", 0L) + elapsedMs);
                root.put(key, station);
            }
            return root.toString();
        } catch (org.json.JSONException error) {
            return "{}";
        }
    }

    private String getTodayKey() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(new java.util.Date());
    }
}
