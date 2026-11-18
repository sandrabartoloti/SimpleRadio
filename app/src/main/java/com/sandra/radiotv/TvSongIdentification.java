package com.sandra.radiotv;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;

/** TV-only MediaSession command and state contract. No audio or credentials cross the session. */
final class TvSongIdentification {
    static final String IDENTIFY = "com.sandra.radiotv.IDENTIFY_SONG";
    static final String EXTRAS = "com.sandra.radiotv.SONG_IDENTIFICATION";
    static final String MEDIA_ID = "media_id";
    static final String STATE = "state";
    static final String TITLE = "title";
    static final String ARTIST = "artist";
    static final String IDLE = "idle";
    static final String LISTENING = "listening";
    static final String MATCH = "match";
    static final String NO_MATCH = "no_match";
    static final String ERROR = "error";

    private TvSongIdentification() {}

    static boolean isTelevision(Context context) {
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK;
        return mode == Configuration.UI_MODE_TYPE_TELEVISION
                || context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }
}
