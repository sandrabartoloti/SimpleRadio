package com.sandra.radiotv;

import android.util.Log;
import java.io.PrintWriter;
import java.io.StringWriter;

/** Recognition diagnostics only. Never log PCM, signatures, token bodies, or endpoint URLs. */
final class TvIdentificationLog {
    private static final String TAG = "TvSongIdentification";

    private TvIdentificationLog() {}

    static void info(String message) {
        Log.i(TAG, message);
    }

    static void error(String stage, Throwable error) {
        // Preserve the actual exception/cause/stack while scrubbing potential credential text
        // from SDK/network exception messages. Do not pass the raw Throwable to Log.e.
        // Log.getStackTraceString suppresses UnknownHostException chains; retain DNS failures too.
        StringWriter stack = new StringWriter();
        error.printStackTrace(new PrintWriter(stack));
        String detail = (stage + ": " + error.getClass().getSimpleName() + "\n" + stack)
                .replaceAll("[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}", "[JWT redacted]")
                .replaceAll("(?i)Bearer\\s+[^\\s\\\"']+", "Bearer [redacted]")
                .replaceAll("(?i)(token|authorization)\\s*[\\\"']?\\s*[:=]\\s*[^\\r\\n]+", "$1=[redacted]")
                .replaceAll("https?://[^\\s\\\"')]+", "[URL redacted]");
        Log.e(TAG, detail);
    }
}
