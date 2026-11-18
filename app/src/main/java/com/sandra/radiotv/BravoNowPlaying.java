package com.sandra.radiotv;

import org.json.JSONObject;
import android.text.Html;

/** TV metadata provider; polling and cancellation are owned by the shared poller. */
final class BravoNowPlaying extends TvNowPlayingPoller {
    BravoNowPlaying(Listener listener) {
        super(listener);
    }

    void setStation(String id, String stationName) {
        setSource(id, endpoint(stationName));
    }

    private static String endpoint(String name) {
        if (name == null) return null;
        switch (name) {
            case "Bravo": return "https://relay1.social3.hr/radio/8310/status-json.xsl?mount=%2Fradio.mp3";
            case "Bravo Gym": return "http://c5.hostingcentar.com:8360/7.html";
            case "Bravo Lounge": return "https://relay1.social3.hr/radio/8340/status-json.xsl?mount=%2Fradio.mp3";
            case "Bravo Icons": return "http://c5.hostingcentar.com:8354/7.html";
            default: return null;
        }
    }

    @Override
    protected String parseSong(String body, String sourceUrl) throws Exception {
        if (sourceUrl.contains("status-json.xsl")) {
            Object value = new JSONObject(body).getJSONObject("icestats").getJSONObject("source").opt("title");
            // Main Bravo sends TITLE - ARTIST; Lounge sends ARTIST - TITLE.
            return value instanceof String
                    ? formatSong((String) value, sourceUrl.equals(endpoint("Bravo"))) : "";
        }
        // Gym and Icons expose Shoutcast stats with the song in the final field.
        String text = Html.fromHtml(body).toString().replace('\u00a0', ' ').trim();
        int separator = text.lastIndexOf(',');
        return separator >= 0 ? formatSong(text.substring(separator + 1), false) : "";
    }

    private static String formatSong(String value, boolean titleFirst) {
        int separator = value.indexOf(" - ");
        if (separator < 0) return "";
        String first = value.substring(0, separator).trim();
        String second = value.substring(separator + 3).trim();
        if (first.isEmpty() || second.isEmpty()) return "";
        return titleFirst ? second + " — " + first : first + " — " + second;
    }
}
