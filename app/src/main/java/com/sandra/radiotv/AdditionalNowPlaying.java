package com.sandra.radiotv;

import org.json.JSONObject;
import android.text.Html;
import android.util.Xml;
import org.json.JSONArray;
import org.xmlpull.v1.XmlPullParser;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** TV metadata provider; polling and cancellation are owned by the shared poller. */
final class AdditionalNowPlaying extends TvNowPlayingPoller {
    AdditionalNowPlaying(Listener listener) {
        super(listener);
    }

    void setStation(String id, String stationName) {
        setSource(id, endpoint(stationName));
    }

    private static String endpoint(String name) {
        if (name == null) return null;
        switch (name) {
            case "CMC": return cmc(9011);
            case "CMC Jugoton": return cmc(9012);
            case "CMC Lagano": return cmc(9013);
            case "CMC Rock": return cmc(9016);
            case "CMC Festival": return cmc(9017);
            case "CMC Zabavno": return cmc(9018);
            case "Naxi": return "https://www.naxi.rs/live";
            case "Naxi ExYu": return "https://www.naxi.rs/exyu";
            case "Radio S ExYu": return "https://www.radios.rs/includes/get/now-playing-json.php?radio=s_ex_yu";
            case "HRT 2": return triton("PROGRAM2AAC");
            case "Osijek": return triton("OSIJEKAAC");
            default: return null;
        }
    }

    private static String cmc(int port) {
        return "https://radio-stream.cmc.com.hr:" + port + "/status-json.xsl";
    }

    private static String triton(String mount) {
        return "https://np.tritondigital.com/public/nowplaying?mountName=" + mount
                + "&numberToFetch=1&eventType=track";
    }

    @Override
    protected String parseSong(String body, String sourceUrl) throws Exception {
        String song = parseMetadata(body, sourceUrl);
        return song.isEmpty() ? null : song;
    }

    private static String parseMetadata(String body, String sourceUrl) throws Exception {
        if (sourceUrl.startsWith("https://radio-stream.cmc.com.hr:")) {
            Object source = new JSONObject(body).getJSONObject("icestats").opt("source");
            if (sourceUrl.equals(cmc(9011))) {
                source = source instanceof JSONArray ? ((JSONArray) source).opt(0) : null;
            }
            if (!(source instanceof JSONObject)) return "";
            String song = string((JSONObject) source, "title");
            int separator = song.indexOf(" - ");
            return separator < 0 ? "" : format(song.substring(0, separator), song.substring(separator + 3));
        }
        if (sourceUrl.startsWith("https://www.radios.rs/")) {
            JSONObject song = new JSONObject(body);
            return format(string(song, "artist"), string(song, "song"));
        }
        if (sourceUrl.startsWith("https://www.naxi.rs/")) {
            // Bound both fields to the current program's data div, never the song history.
            Matcher block = CURRENT_PROGRAM.matcher(body);
            if (!block.find()) return "";
            String current = block.group(1);
            return format(htmlField(current, "artist-name"), htmlField(current, "song-title"));
        }
        XmlPullParser xml = Xml.newPullParser();
        xml.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
        xml.setInput(new StringReader(body));
        String artist = "";
        String title = "";
        while (xml.next() != XmlPullParser.END_DOCUMENT) {
            if (xml.getEventType() != XmlPullParser.START_TAG || !"property".equals(xml.getName())) continue;
            String name = xml.getAttributeValue(null, "name");
            if ("track_artist_name".equals(name)) artist = xml.nextText();
            else if ("cue_title".equals(name)) title = xml.nextText();
            if (!artist.trim().isEmpty() && !title.trim().isEmpty()) return format(artist, title);
        }
        return "";
    }

    private static final Pattern CURRENT_PROGRAM = Pattern.compile(
            "<div\\b[^>]*\\bclass\\s*=\\s*[\"']current-program__data[\"'][^>]*>(.*?)</div\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static String htmlField(String block, String className) {
        Matcher field = Pattern.compile("<p\\b[^>]*\\bclass\\s*=\\s*[\"']" + className
                + "[\"'][^>]*>(.*?)</p\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(block);
        return field.find() ? Html.fromHtml(field.group(1)).toString() : "";
    }

    private static String string(JSONObject object, String key) {
        Object value = object.opt(key);
        return value instanceof String ? (String) value : "";
    }

    private static String format(String artist, String title) {
        artist = artist.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        title = title.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
        if (artist.isEmpty() || title.isEmpty() || "null".equalsIgnoreCase(artist)
                || "null".equalsIgnoreCase(title)) return "";
        return artist + " — " + title;
    }
}
