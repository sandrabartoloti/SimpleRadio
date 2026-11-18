package com.sandra.radiotv;

import android.content.Context;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Reads the small build-generated manifest; audio remains streamed from APK assets. */
final class LocalPlaylist {
    static final String PENTATONIX = "local-playlist://pentatonix";

    static List<MediaItem> load(Context context, String stationId, MediaMetadata stationMetadata)
            throws Exception {
        JSONObject manifest;
        try (InputStream input = context.getAssets().open("playlists/pentatonix/playlist.json");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            manifest = new JSONObject(output.toString("UTF-8"));
        }
        JSONArray tracks = manifest.getJSONArray("tracks");
        List<MediaItem> items = new ArrayList<>(tracks.length());
        for (int i = 0; i < tracks.length(); i++) {
            JSONObject track = tracks.getJSONObject(i);
            items.add(new MediaItem.Builder().setMediaId(stationId)
                    .setUri(track.getString("uri"))
                    .setMediaMetadata(stationMetadata.buildUpon()
                            .setTitle(track.getString("title")).setArtist("Pentatonix").build())
                    .build());
        }
        if (items.isEmpty()) throw new IllegalStateException("Local playlist is empty");
        return items;
    }

    private LocalPlaylist() { }
}
