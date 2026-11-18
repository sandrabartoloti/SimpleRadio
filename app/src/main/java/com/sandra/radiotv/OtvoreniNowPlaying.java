package com.sandra.radiotv;

import org.json.JSONObject;

/** TV metadata provider; polling and cancellation are owned by the shared poller. */
final class OtvoreniNowPlaying extends TvNowPlayingPoller {
    OtvoreniNowPlaying(Listener listener) {
        super(listener);
    }

    void setStation(String id, String stream) {
        setSource(id, stream == null ? null
                : "https://www.otvoreni.hr/umbraco/Surface/NowPlayingSurface/GetNowPlaying?stream=" + stream);
    }

    @Override
    protected String parseSong(String body, String sourceUrl) throws Exception {
        JSONObject json = new JSONObject(body);
        Object artist = json.opt("artist");
        Object title = json.opt("title");
        return artist instanceof String && title instanceof String
                && !((String) artist).trim().isEmpty() && !((String) title).trim().isEmpty()
                ? ((String) artist).trim() + " — " + ((String) title).trim() : "";
    }
}
