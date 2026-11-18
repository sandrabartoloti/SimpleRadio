package com.sandra.radiotv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String CATEGORY_LOCAL = "Local";
    private static final String CATEGORY_INTERNATIONAL = "International";
    private static final String COLOR_BACKGROUND = "#050607";
    private static final String COLOR_CARD = "#1C2329";
    private static final String COLOR_CARD_DARK = "#12171B";
    private static final String COLOR_CARD_FOCUSED = "#2A3035";
    private static final String COLOR_ACCENT = "#D9AE6F";
    private static final String COLOR_TEXT_PRIMARY = "#F3F6F8";
    private static final String COLOR_TEXT_SECONDARY = "#A9B4BC";
    private static final String CATEGORY_CUSTOM = "Custom";
    private static final String PREFS_NAME = "simple_radio_prefs";
    private static final String PREF_CUSTOM_STATIONS = "custom_stations";
    private static final String PREF_FAVORITES = "favorite_station_ids";
    private static final String PREF_FAVORITE_ORDER = "favorite_station_order";
    private static final String PREF_SLEEP_TIMER_END_TIME = "sleep_timer_end_time";
    private static final String PREF_SCREENSAVER_MODE = "screensaver_mode";
    private static final int PHONE_CARD_HEIGHT_DP = 66;
    private static final int PHONE_CARD_RADIUS_DP = 18;
    private static final int PHONE_ROW_GAP_DP = 10;
    private static final int PHONE_ICON_SIZE_DP = 42;
    private static final int PHONE_CONTROL_SLOT_DP = 42;

    private final List<RadioStation> allStations = new ArrayList<>();
    private final List<RadioStation> customStations = new ArrayList<>();
    private final List<RadioStation> visibleStations = new ArrayList<>();
    private final List<StationRowViews> stationRows = new ArrayList<>();
    private final Set<String> favoriteStationIds = new HashSet<>();
    private final List<String> favoriteStationOrder = new ArrayList<>();

    private MediaController mediaController;
    private ListenableFuture<MediaController> controllerFuture;
    private int selectedStationIndex = 0;
    private int selectedCategoryIndex = 0;
    private RadioStation loadedStation;
    private RadioStation activeStation;
    private RadioStation pendingPlayStation;
    private boolean playerPrepared = false;
    private ScrollView stationScroll;
    private LinearLayout stationList;
    private TextView stationTitle;
    private TextView tvNowPlayingSong;
    private OtvoreniNowPlaying otvoreniNowPlaying;
    private BravoNowPlaying bravoNowPlaying;
    private AdditionalNowPlaying additionalNowPlaying;
    private String tvMetadataStationId;
    private String tvMetadataSong = "";
    private TextView statusText;
    private Button playbackButton;
    private Button screensaverButton;
    private Button localTabButton;
    private Button internationalTabButton;
    private Button tvFavoritesTabButton;
    private Button surpriseButton;
    private TextView nowPlayingLabel;
    private Button identifySongButton;
    private TextView songIdentificationText;
    private TextView songIdentificationArtist;
    private boolean identificationPending;
    private boolean identificationListening;
    private int identificationUiGeneration;
    private FrameLayout phoneContent;
    private LinearLayout phoneMiniPlayer;
    private TextView phoneMiniTitle;
    private TextView phoneMiniStatus;
    private TextView phoneMiniSong;
    private TextView phoneFullSong;
    private Button phoneMiniPlayButton;
    private TextView sleepTimerStateText;
    private TextView nowPlayingSleepText;
    private long sleepTimerEndTimeMs = 0L;
    private boolean phoneChildScreenActive = false;
    private RadioStation draggingStation;
    private View draggingRow;
    private float dragStartRawY;
    private float dragStartTranslationY;
    private int dragStartIndex = -1;
    private int dragCurrentIndex = -1;
    private boolean dragStarted = false;
    private final Handler sleepTimerUiHandler = new Handler(Looper.getMainLooper());
    private final Runnable sleepTimerUiTicker = new Runnable() {
        @Override
        public void run() {
            refreshSleepTimerUi();
            if (sleepTimerEndTimeMs > System.currentTimeMillis()) {
                sleepTimerUiHandler.postDelayed(this, 30000);
            }
        }
    };
    private final Button[] phoneNavButtons = new Button[5];
    private final int[] phoneNavIcons = {
            R.drawable.ic_nav_home,
            R.drawable.ic_nav_heart,
            R.drawable.ic_nav_add_box,
            R.drawable.ic_nav_search,
            R.drawable.ic_nav_more_horiz
    };
    private int selectedPhoneTab = 0;
    private boolean phoneNowPlayingActive = false;
    private View mainContentView;
    private TextView tvClock;
    private boolean activityResumed;
    private final Handler tvClockHandler = new Handler(Looper.getMainLooper());
    private final Runnable tvClockTicker = this::updateTvClock;
    private ScreensaverView screensaverView;
    private boolean screensaverActive = false;
    private int screensaverMode = 0;
    private TvScreensaverPicker tvScreensaverPicker;
    private final Handler tvIdleHandler = new Handler(Looper.getMainLooper());
    private final Runnable tvIdleScreensaver = () -> {
        if (activityResumed && !screensaverActive && tvScreensaverPicker == null
                && mainContentView != null && mainContentView.isAttachedToWindow()
                && hasWindowFocus() && mediaController != null && mediaController.isConnected()
                && mediaController.isPlaying()) {
            showScreensaver();
        } else {
            resetTvScreensaverIdle();
        }
    };
    private final Player.Listener controllerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            syncPlaybackUi(false);
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            syncPlaybackUi(false);
            resetTvScreensaverIdle();
        }

        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            resetTvSongIdentification();
            syncPlaybackUi(true);
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            RadioStation station = loadedStation != null ? loadedStation : getSelectedStation();
            if (station != null && statusText != null) {
                statusText.setText("Could not play " + station.name);
            }
            syncPlaybackUi(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        seedStations();
        loadCustomStations();
        loadFavorites();
        loadSleepTimer();
        loadScreensaverMode();
        buildLayout();
        updateSelection(0);
        connectPlaybackController();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }

        int keyCode = event.getKeyCode();
        if (screensaverActive && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            changeScreensaverMode(-1);
            return true;
        }
        if (screensaverActive && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            changeScreensaverMode(1);
            return true;
        }
        if (screensaverActive && (keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER)) {
            hideScreensaver();
            return true;
        }
        if (phoneNowPlayingActive && keyCode == KeyEvent.KEYCODE_BACK) {
            hidePhoneNowPlaying();
            return true;
        }
        if (phoneChildScreenActive && keyCode == KeyEvent.KEYCODE_BACK) {
            hidePhoneChildScreen();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_BACK && isStationRowFocused()) {
            if (event.getRepeatCount() == 0 && !focusActiveStation()) {
                focusCurrentCategoryTab();
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            playSelectedStation();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            pausePlayback();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            togglePlayback();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        resetTvScreensaverIdle();
        updateTvClock();
        syncTvNowPlayingStation();
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        tvIdleHandler.removeCallbacks(tvIdleScreensaver);
        if (tvScreensaverPicker != null) tvScreensaverPicker.dismiss();
        if (otvoreniNowPlaying != null) otvoreniNowPlaying.setActive(false);
        if (bravoNowPlaying != null) bravoNowPlaying.setActive(false);
        if (additionalNowPlaying != null) additionalNowPlaying.setActive(false);
        tvClockHandler.removeCallbacks(tvClockTicker);
        super.onPause();
    }

    private void updateTvClock() {
        tvClockHandler.removeCallbacks(tvClockTicker);
        if (!activityResumed || tvClock == null || !tvClock.isAttachedToWindow()) return;
        long now = System.currentTimeMillis();
        tvClock.setText(android.text.format.DateFormat.getTimeFormat(this).format(new Date(now)));
        tvClockHandler.postDelayed(tvClockTicker, 60_000L - (System.currentTimeMillis() % 60_000L));
    }

    @Override
    protected void onDestroy() {
        tvIdleHandler.removeCallbacks(tvIdleScreensaver);
        if (tvScreensaverPicker != null) tvScreensaverPicker.dismiss();
        if (otvoreniNowPlaying != null) otvoreniNowPlaying.release();
        if (bravoNowPlaying != null) bravoNowPlaying.release();
        if (additionalNowPlaying != null) additionalNowPlaying.release();
        tvClockHandler.removeCallbacks(tvClockTicker);
        sleepTimerUiHandler.removeCallbacks(sleepTimerUiTicker);
        releasePlaybackController();
        super.onDestroy();
    }

    private void seedStations() {
        addStation("Bravo", "https://relay1.social3.hr/radio/8310/radio.mp3", CATEGORY_LOCAL);
        addStation("Bravo Lounge", "https://relay1.social3.hr/radio/8340/radio.mp3?mp=/stream", CATEGORY_LOCAL);
        addStation("Bravo Gym", "http://c5.hostingcentar.com:8360/stream", CATEGORY_LOCAL);
        addStation("Bravo Icons", "http://c5.hostingcentar.com:8354/stream", CATEGORY_LOCAL);
        addStation("CMC", "https://radio-stream.cmc.com.hr:9011/live", CATEGORY_LOCAL);
        addStation("CMC Festival", "https://radio-stream.cmc.com.hr:9017/autodj", CATEGORY_LOCAL);
        addStation("CMC Jugoton", "https://radio-stream.cmc.com.hr:9012/autodj", CATEGORY_LOCAL);
        addStation("CMC Lagano", "https://radio-stream.cmc.com.hr:9013/autodj", CATEGORY_LOCAL);
        addStation("CMC Rock", "https://radio-stream.cmc.com.hr:9016/autodj", CATEGORY_LOCAL);
        addStation("CMC Zabavno", "https://radio-stream.cmc.com.hr:9018/autodj", CATEGORY_LOCAL);
        addStation("Extra FM", "https://audio.social3.hr/listen/extra_aac/stream", CATEGORY_LOCAL);
        addStation("Hit FM", "http://streaming.hitfm.rs:8000/hit", CATEGORY_LOCAL);
        addStation("HRT 2","https://28503.live.streamtheworld.com/PROGRAM2_SC", CATEGORY_LOCAL);
        addStation("Naxi", "https://naxi128ssl.streaming.rs:9152/;", CATEGORY_LOCAL);
        addStation("Naxi ExYu", "https://naxidigital-exyu128ssl.streaming.rs:8242/;stream.nsv", CATEGORY_LOCAL);
        addStation("Osijek", "https://25683.live.streamtheworld.com/OSIJEKAAC.aac", CATEGORY_LOCAL);
        addStation("Otvoreni", "https://stream.otvoreni.hr/otvoreni", CATEGORY_LOCAL);
        addStation("Otvoreni Chill", "https://stream.otvoreni.hr/chill", CATEGORY_LOCAL);
        addStation("Otvoreni Fitness", "https://stream.otvoreni.hr/fitness", CATEGORY_LOCAL);
        addStation("Otvoreni Hot", "https://stream.otvoreni.hr/hot", CATEGORY_LOCAL);
        addStation("Otvoreni Love", "https://stream.otvoreni.hr/love", CATEGORY_LOCAL);
        addStation("Radio S ExYu", "https://stream.radios.rs:9042/stream", CATEGORY_LOCAL);

        addStation("Ambient", "https://s2.we4stream.com/listen/loca_ambient/live", CATEGORY_INTERNATIONAL);
        addStation("Cadena Dial", "https://playerservices.streamtheworld.com/api/livestream-redirect/CADENADIAL.mp3", CATEGORY_INTERNATIONAL);
        addStation("Chill", "https://ice5.somafm.com/groovesalad-256-mp3", CATEGORY_INTERNATIONAL);
        addStation("Christmas 1", "https://radio1.streamserver.link/radio/8070/hcr-aac-hq", CATEGORY_INTERNATIONAL);
        addStation("Christmas 2", "https://stream.musiquose.fr/proxy/merrychristmas?mp=/stream", CATEGORY_INTERNATIONAL);
        addStation("Christmas 3", "https://streams.90s90s.de/xmas/mp3-128/streams.90s90s.de/", CATEGORY_INTERNATIONAL);
        addStation("Christmas 4", "https://listen.181fm.com/181-xmix_128k.mp3", CATEGORY_INTERNATIONAL);
        addStation("Dance", "https://ice5.somafm.com/poptron-128-mp3", CATEGORY_INTERNATIONAL);
        addStation("Dial Baladas", "https://playerservices.streamtheworld.com/api/livestream-redirect/CADENADIAL_03.mp3", CATEGORY_INTERNATIONAL);
        addStation("Dial Latino", "https://playerservices.streamtheworld.com/api/livestream-redirect/CADENADIAL_02.mp3", CATEGORY_INTERNATIONAL);
        addStation("Heart 80s", "https://media-ssl.musicradio.com/Heart80sMP3", CATEGORY_INTERNATIONAL);
        addStation("Heart 90s", "https://media-ssl.musicradio.com/Heart90sMP3", CATEGORY_INTERNATIONAL);
        addStation("Ibiza", "https://cdn-peer012.streaming-pro.com:8025/ibizaglobalradio.mp3", CATEGORY_INTERNATIONAL);
        addStation("Indie/Alternative", "https://ice5.somafm.com/indiepop-128-mp3", CATEGORY_INTERNATIONAL);
        addStation("Kiss FM España", "https://kissfm.kissfmradio.cires21.com/kissfm.mp3", CATEGORY_INTERNATIONAL);
        addStation("LOCA 80's", "https://s2.we4stream.com/listen/loca_80s/live", CATEGORY_INTERNATIONAL);
        addStation("LOCA 90's", "https://s2.we4stream.com/listen/loca_90s_/live", CATEGORY_INTERNATIONAL);
        addStation("LOCA Big Room", "https://s2.we4stream.com/listen/loca_big_room/live", CATEGORY_INTERNATIONAL);
        addStation("LOCA Chill Out", "https://s2.we4stream.com/listen/loca_chill_out/live", CATEGORY_INTERNATIONAL);
        addStation("LOCA Dance", "https://s2.we4stream.com/listen/loca_dance/live", CATEGORY_INTERNATIONAL);
        addStation("LOCA Deep House", "https://s2.we4stream.com/listen/loca_deep_house/live", CATEGORY_INTERNATIONAL);
        addStation("LOCA House", "https://s2.we4stream.com/listen/loca_house/live", CATEGORY_INTERNATIONAL);
        addStation("LOCA Remember", "https://s2.we4stream.com/listen/loca_remember/live", CATEGORY_INTERNATIONAL);
        addStation("Lofi", "https://live.lofiradio.ru/lofi_mp3_128", CATEGORY_INTERNATIONAL);
        addStation("Lofi Café", "https://radio.loficafe.net/listen/chilling/radio.mp3", CATEGORY_INTERNATIONAL);
        addStation("Lofi Mix", "https://stream.laut.fm/lofi-radio", CATEGORY_INTERNATIONAL);
        addStation("Lofi Study", "https://stream.laut.fm/lofi", CATEGORY_INTERNATIONAL);
        addStation("LOS40", "https://playerservices.streamtheworld.com/api/livestream-redirect/Los40.mp3", CATEGORY_INTERNATIONAL);
        addStation("LOS40 Classic", "https://playerservices.streamtheworld.com/api/livestream-redirect/LOS40_CLASSIC.mp3", CATEGORY_INTERNATIONAL);
        addStation("LOS40 Dance", "https://playerservices.streamtheworld.com/api/livestream-redirect/LOS40_DANCE.mp3", CATEGORY_INTERNATIONAL);
        addStation("LOS40 Urban", "https://playerservices.streamtheworld.com/api/livestream-redirect/LOS40_URBAN.mp3", CATEGORY_INTERNATIONAL);
        addStation("PopTron", "https://ice5.somafm.com/poptron-128-mp3", CATEGORY_INTERNATIONAL);
        addStation("Radio Paradise", "https://stream-uk1.radioparadise.com/mp3-192", CATEGORY_INTERNATIONAL);
        addStation("Spanish", "https://playerservices.streamtheworld.com/api/livestream-redirect/RADIOLE.mp3", CATEGORY_INTERNATIONAL);
        addStation("Halloween Main", "https://radio1.streamserver.link/radio/8000/hrm-aac", CATEGORY_INTERNATIONAL);
        addStation("Halloween Soundtracks", "https://radio1.streamserver.link/radio/8050/hrs-aac", CATEGORY_INTERNATIONAL);
        addStation("Halloween Atmosphere", "https://radio1.streamserver.link:8040/hra-aac", CATEGORY_INTERNATIONAL);
    }

    private void addStation(String name, String url, String category) {
        allStations.add(new RadioStation(makeBuiltinStationId(name), name, url, category, false));
    }

    private void buildLayout() {
        if (!isTelevisionLayout()) {
            buildPhoneLayout();
            return;
        }

        boolean compactLayout = false;
        int listWidth = dp(Math.round((getResources().getConfiguration().screenWidthDp - 112) * 0.50f));
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(color(COLOR_BACKGROUND));

        ImageView backgroundImage = new ImageView(this);
        backgroundImage.setImageResource(R.drawable.radio_background);
        backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backgroundImage.setAlpha(compactLayout ? 0.16f : 0.30f);
        root.addView(backgroundImage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        View backgroundOverlay = new View(this);
        backgroundOverlay.setBackgroundColor(color(compactLayout ? "#DB000000" : "#C8000000"));
        root.addView(backgroundOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(compactLayout ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        content.setGravity(compactLayout ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        content.setPadding(
                dp(compactLayout ? 18 : 56),
                dp(compactLayout ? 22 : 40),
                dp(compactLayout ? 18 : 56),
                dp(compactLayout ? 18 : 40)
        );

        LinearLayout stationPanel = new LinearLayout(this);
        stationPanel.setOrientation(LinearLayout.VERTICAL);

        TextView listHeader = new TextView(this);
        listHeader.setText(compactLayout ? "Simple Radio" : "Radio stations");
        listHeader.setTextColor(color(compactLayout ? COLOR_ACCENT : COLOR_TEXT_PRIMARY));
        listHeader.setTextSize(compactLayout ? 34 : 24);
        listHeader.setGravity(Gravity.START);
        stationPanel.addView(listHeader, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        if (compactLayout) {
            TextView subtitle = new TextView(this);
            subtitle.setText("Radio stations");
            subtitle.setTextColor(color(COLOR_TEXT_SECONDARY));
            subtitle.setTextSize(15);
            LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            subtitleParams.topMargin = dp(2);
            stationPanel.addView(subtitle, subtitleParams);
        }

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tabRowParams = new LinearLayout.LayoutParams(
                listWidth,
                dp(38)
        );
        tabRowParams.topMargin = dp(12);
        stationPanel.addView(tabRow, tabRowParams);

        localTabButton = makeCategoryButton(CATEGORY_LOCAL);
        localTabButton.setOnClickListener(view -> switchCategory(0, true));
        localTabButton.setOnKeyListener((view, keyCode, event) -> handleCategoryKey(0, keyCode, event));
        tabRow.addView(localTabButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));

        internationalTabButton = makeCategoryButton("Global");
        internationalTabButton.setOnClickListener(view -> switchCategory(1, true));
        internationalTabButton.setOnKeyListener((view, keyCode, event) -> handleCategoryKey(1, keyCode, event));
        LinearLayout.LayoutParams internationalParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
        internationalParams.leftMargin = dp(8);
        tabRow.addView(internationalTabButton, internationalParams);

        tvFavoritesTabButton = makeCategoryButton("♡ Fav");
        tvFavoritesTabButton.setOnClickListener(view -> switchCategory(2, true));
        tvFavoritesTabButton.setOnKeyListener((view, keyCode, event) -> handleCategoryKey(2, keyCode, event));
        LinearLayout.LayoutParams favoritesParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
        favoritesParams.leftMargin = dp(8);
        tabRow.addView(tvFavoritesTabButton, favoritesParams);

        TextView favoriteHint = new TextView(this);
        favoriteHint.setText("Hold OK on a station to add/remove favourites");
        favoriteHint.setTextSize(12);
        favoriteHint.setTextColor(color(COLOR_TEXT_SECONDARY));
        favoriteHint.setPadding(0, dp(6), 0, 0);
        stationPanel.addView(favoriteHint);

        surpriseButton = makeCategoryButton("Surprise me");
        surpriseButton.setOnFocusChangeListener((view, hasFocus) -> updateCategoryButtons());
        surpriseButton.setOnClickListener(view -> playRandomStation());
        surpriseButton.setOnKeyListener((view, keyCode, event) -> handleSurpriseKey(keyCode, event));
        LinearLayout.LayoutParams surpriseParams = new LinearLayout.LayoutParams(listWidth, dp(44));
        surpriseParams.topMargin = dp(8);
        stationPanel.addView(surpriseButton, surpriseParams);

        stationScroll = new ScrollView(this);
        if (compactLayout) {
            stationScroll.setClipToPadding(false);
            stationScroll.setPadding(0, 0, 0, dp(120));
        }
        stationList = new LinearLayout(this);
        stationList.setOrientation(LinearLayout.VERTICAL);
        stationScroll.addView(stationList);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(listWidth, 0, 1);
        scrollParams.topMargin = dp(12);
        stationPanel.addView(stationScroll, scrollParams);

        switchCategory(0, false);

        LinearLayout playerPanel = new LinearLayout(this);
        playerPanel.setOrientation(LinearLayout.VERTICAL);
        playerPanel.setGravity(compactLayout ? Gravity.START : Gravity.CENTER_VERTICAL);
        playerPanel.setPadding(dp(48), dp(120), 0, 0);

        TextView appTitle = new TextView(this);
        appTitle.setText("Simple Radio");
        appTitle.setTextColor(color(COLOR_ACCENT));
        appTitle.setTextSize(20);
        if (!compactLayout) {
            playerPanel.addView(appTitle);
        }

        stationTitle = new TextView(this);
        stationTitle.setTextColor(color(COLOR_TEXT_PRIMARY));
        stationTitle.setTextSize(34);
        stationTitle.setMaxLines(2);
        stationTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        stationTitle.setGravity(Gravity.START);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(8);
        playerPanel.addView(stationTitle, titleParams);

        tvNowPlayingSong = new TextView(this);
        tvNowPlayingSong.setTextSize(16);
        tvNowPlayingSong.setTextColor(color(COLOR_TEXT_SECONDARY));
        tvNowPlayingSong.setGravity(Gravity.START);
        tvNowPlayingSong.setSingleLine(true);
        tvNowPlayingSong.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvNowPlayingSong.setFocusable(false);
        tvNowPlayingSong.setClickable(false);
        tvNowPlayingSong.setVisibility(View.GONE);
        LinearLayout.LayoutParams songParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        songParams.topMargin = dp(4);
        playerPanel.addView(tvNowPlayingSong, songParams);
        otvoreniNowPlaying = new OtvoreniNowPlaying(song -> {
            tvMetadataSong = song;
            renderTvNowPlayingSong();
        });
        bravoNowPlaying = new BravoNowPlaying(song -> {
            tvMetadataSong = song;
            renderTvNowPlayingSong();
        });
        additionalNowPlaying = new AdditionalNowPlaying(song -> {
            tvMetadataSong = song;
            renderTvNowPlayingSong();
        });
        tvNowPlayingSong.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                syncTvNowPlayingStation();
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                otvoreniNowPlaying.setActive(false);
                bravoNowPlaying.setActive(false);
                additionalNowPlaying.setActive(false);
            }
        });

        statusText = new TextView(this);
        statusText.setTextColor(color(COLOR_TEXT_SECONDARY));
        statusText.setTextSize(compactLayout ? 14 : 22);
        statusText.setGravity(Gravity.START);
        statusText.setVisibility(View.GONE);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = dp(compactLayout ? 2 : 12);
        playerPanel.addView(statusText, statusParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.START);

        playbackButton = makeControlButton("Play");
        playbackButton.setTextSize(16);
        playbackButton.setOnClickListener(view -> togglePlayback());
        controls.addView(playbackButton, makeButtonParams());

        screensaverButton = makeControlButton("Screensaver");
        screensaverButton.setTextSize(16);
        screensaverButton.setOnClickListener(view -> showScreensaverPicker());
        LinearLayout.LayoutParams screensaverParams = makeButtonParams();
        screensaverParams.leftMargin = dp(14);
        controls.addView(screensaverButton, screensaverParams);

        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        controlsParams.topMargin = dp(compactLayout ? 18 : 24);
        playerPanel.addView(controls, controlsParams);
        // Song identification UI is hidden; retain addTvSongIdentification and its backend for future use.

        View footerSpacer = new View(this);
        playerPanel.addView(footerSpacer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        playerPanel.addView(makeAppFooter(), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        if (compactLayout) {
            content.addView(stationPanel, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1
            ));
        } else {
            content.addView(stationPanel, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
            content.addView(playerPanel, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        }
        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        nowPlayingLabel = new TextView(this);
        nowPlayingLabel.setTextColor(color("#F3F6F8"));
        nowPlayingLabel.setTextSize(16);
        nowPlayingLabel.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        nowPlayingLabel.setSingleLine(true);
        nowPlayingLabel.setPadding(0, 0, 0, 0);
        nowPlayingLabel.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams nowPlayingParams = new FrameLayout.LayoutParams(
                compactLayout ? FrameLayout.LayoutParams.MATCH_PARENT : dp(330),
                dp(42),
                Gravity.TOP | Gravity.END
        );
        nowPlayingParams.topMargin = dp(compactLayout ? 8 : 32);
        nowPlayingParams.leftMargin = dp(compactLayout ? 18 : 0);
        nowPlayingParams.rightMargin = dp(compactLayout ? 18 : 56);
        root.addView(nowPlayingLabel, nowPlayingParams);

        tvClock = new TextView(this);
        tvClock.setTextSize(14);
        tvClock.setTextColor(color(COLOR_TEXT_SECONDARY));
        tvClock.setSingleLine(true);
        tvClock.setFocusable(false);
        tvClock.setFocusableInTouchMode(false);
        tvClock.setClickable(false);
        tvClock.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
                updateTvClock();
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                tvClockHandler.removeCallbacks(tvClockTicker);
            }
        });
        FrameLayout.LayoutParams clockParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.RIGHT);
        clockParams.rightMargin = dp(56);
        clockParams.bottomMargin = dp(24);
        root.addView(tvClock, clockParams);

        mainContentView = root;
        setContentView(mainContentView);
        updateStationMarkers();
        updateNowPlayingLabel();
        localTabButton.requestFocus();
    }

    private void buildPhoneLayout() {
        TvNowPlayingPoller.Listener metadataListener = song -> {
            tvMetadataSong = song;
            renderPhoneNowPlayingSong();
        };
        otvoreniNowPlaying = new OtvoreniNowPlaying(metadataListener);
        bravoNowPlaying = new BravoNowPlaying(metadataListener);
        additionalNowPlaying = new AdditionalNowPlaying(metadataListener);
        stationTitle = new TextView(this);
        statusText = new TextView(this);
        playbackButton = new Button(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(color(COLOR_BACKGROUND));

        ImageView backgroundImage = new ImageView(this);
        backgroundImage.setImageResource(R.drawable.radio_background);
        backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backgroundImage.setAlpha(0.13f);
        root.addView(backgroundImage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        View backgroundOverlay = new View(this);
        backgroundOverlay.setBackgroundColor(color("#E6000000"));
        root.addView(backgroundOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(16), dp(18), dp(16), dp(10));
        final int shellBottomPadding = shell.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int navigationBottom;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                navigationBottom = insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;
            } else {
                // Exclude keyboard height on older versions; decor-fitted insets are already consumed.
                navigationBottom = Math.min(insets.getSystemWindowInsetBottom(), insets.getStableInsetBottom());
            }
            shell.setPadding(shell.getPaddingLeft(), shell.getPaddingTop(), shell.getPaddingRight(),
                    shellBottomPadding + navigationBottom);
            return insets;
        });

        phoneContent = new FrameLayout(this);
        shell.addView(phoneContent, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        LinearLayout bottomArea = new LinearLayout(this);
        bottomArea.setOrientation(LinearLayout.VERTICAL);
        bottomArea.setPadding(0, dp(8), 0, 0);

        phoneMiniPlayer = makePhoneMiniPlayer();
        LinearLayout.LayoutParams miniParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(72)
        );
        bottomArea.addView(phoneMiniPlayer, miniParams);

        LinearLayout nav = makePhoneBottomNavigation();
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(66)
        );
        navParams.topMargin = dp(8);
        bottomArea.addView(nav, navParams);

        shell.addView(bottomArea, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        root.addView(shell, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        mainContentView = root;
        setContentView(mainContentView);
        showPhoneTab(0);
        root.requestApplyInsets();
        updatePhoneBottomUi();
    }

    private LinearLayout makePhoneMiniPlayer() {
        LinearLayout mini = new LinearLayout(this);
        mini.setOrientation(LinearLayout.HORIZONTAL);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setPadding(dp(12), 0, dp(10), 0);
        mini.setBackground(makeCardBackground(COLOR_CARD, false, dp(PHONE_CARD_RADIUS_DP)));
        mini.setVisibility(View.GONE);
        mini.setClickable(true);
        mini.setOnClickListener(view -> showPhoneNowPlaying());

        ImageView icon = makeStationIcon(PHONE_ICON_SIZE_DP, 30);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(PHONE_ICON_SIZE_DP), dp(PHONE_ICON_SIZE_DP));
        iconParams.rightMargin = dp(12);
        mini.addView(icon, iconParams);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(Gravity.CENTER_VERTICAL);

        phoneMiniTitle = new TextView(this);
        phoneMiniTitle.setTextColor(color(COLOR_TEXT_PRIMARY));
        phoneMiniTitle.setTextSize(16);
        phoneMiniTitle.setSingleLine(true);
        phoneMiniTitle.setGravity(Gravity.START);
        phoneMiniTitle.setIncludeFontPadding(false);

        phoneMiniStatus = new TextView(this);
        phoneMiniStatus.setTextColor(color(COLOR_TEXT_SECONDARY));
        phoneMiniStatus.setTextSize(13);
        phoneMiniStatus.setSingleLine(true);
        phoneMiniStatus.setGravity(Gravity.START);
        phoneMiniStatus.setIncludeFontPadding(false);

        textColumn.addView(phoneMiniTitle, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        phoneMiniSong = makePhoneSongText(13);
        textColumn.addView(phoneMiniSong, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        textColumn.addView(phoneMiniStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        mini.addView(textColumn, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        ));

        phoneMiniPlayButton = makeCircleButton("▶", 22);
        phoneMiniPlayButton.setOnClickListener(view -> toggleCurrentPlayback());
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        playParams.leftMargin = dp(10);
        mini.addView(phoneMiniPlayButton, playParams);

        return mini;
    }

    private LinearLayout makePhoneBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(5), dp(5), dp(5), dp(5));
        nav.setBackground(makeCardBackground("#101418", false, dp(24)));

        addPhoneNavButton(nav, 0, "Home");
        addPhoneNavButton(nav, 1, "Favorites");
        addPhoneNavButton(nav, 2, "Custom");
        addPhoneNavButton(nav, 3, "Search");
        addPhoneNavButton(nav, 4, "More");
        updatePhoneNavButtons();
        return nav;
    }

    private void addPhoneNavButton(LinearLayout nav, int index, String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(11);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setCompoundDrawablePadding(dp(3));
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setIncludeFontPadding(false);
        button.setOnClickListener(view -> showPhoneTab(index));
        phoneNavButtons[index] = button;
        nav.addView(button, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
    }

    private void showPhoneTab(int tabIndex) {
        if (mainContentView != null) {
            phoneNowPlayingActive = false;
            phoneChildScreenActive = false;
            setContentView(mainContentView);
        }
        selectedPhoneTab = tabIndex;
        stationRows.clear();
        if (phoneContent == null) {
            return;
        }

        phoneContent.removeAllViews();
        if (tabIndex == 0) {
            showPhoneHome();
        } else if (tabIndex == 1) {
            showPhoneFavorites();
        } else if (tabIndex == 2) {
            showPhoneCustom();
        } else if (tabIndex == 3) {
            showPhoneSearch();
        } else {
            showPhoneMore();
        }
        updatePhoneNavButtons();
        updateStationMarkers();
        updatePhoneBottomUi();
    }

    private void showPhoneHome() {
        LinearLayout content = makePhoneScreenContent("Simple Radio", "Radio stations");

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabRow.setBackground(makeCardBackground("#101418", false, dp(22)));
        LinearLayout.LayoutParams tabRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        tabRowParams.topMargin = dp(18);
        content.addView(tabRow, tabRowParams);

        localTabButton = makePhoneSegmentButton(CATEGORY_LOCAL, 0);
        internationalTabButton = makePhoneSegmentButton(CATEGORY_INTERNATIONAL, 1);
        tabRow.addView(localTabButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        tabRow.addView(internationalTabButton, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));

        surpriseButton = null;
        LinearLayout phoneSurpriseButton = makePhoneSurpriseButton();
        LinearLayout.LayoutParams surpriseParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        surpriseParams.topMargin = dp(14);
        content.addView(phoneSurpriseButton, surpriseParams);

        stationScroll = makePhoneScroll();
        stationList = new LinearLayout(this);
        stationList.setOrientation(LinearLayout.VERTICAL);
        stationScroll.addView(stationList);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        );
        scrollParams.topMargin = dp(10);
        content.addView(stationScroll, scrollParams);

        phoneContent.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        switchCategory(selectedCategoryIndex, false);
    }

    private void showPhoneFavorites() {
        LinearLayout content = makePhoneScreenContent("Favorites", "Your favorite stations");
        stationScroll = makePhoneScroll();
        stationList = new LinearLayout(this);
        stationList.setOrientation(LinearLayout.VERTICAL);
        stationScroll.addView(stationList);
        LinearLayout.LayoutParams favoritesListParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        );
        favoritesListParams.topMargin = dp(18);
        content.addView(stationScroll, favoritesListParams);
        phoneContent.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        visibleStations.clear();
        visibleStations.addAll(getFavoriteStationsInOrder());
        selectedStationIndex = Math.min(selectedStationIndex, Math.max(visibleStations.size() - 1, 0));
        renderStationRows();
        if (visibleStations.isEmpty()) {
            addPhoneEmptyState("No favorite stations yet.");
        }
    }

    private void showPhoneCustom() {
        LinearLayout content = makePhoneScreenContent("Custom Stations", "Add your own radio stations");
        Button addButton = makePhoneGoldButton("+  Add Station");
        addButton.setOnClickListener(view -> showCustomStationEditor(null));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        addParams.topMargin = dp(18);
        content.addView(addButton, addParams);

        stationScroll = makePhoneScroll();
        stationList = new LinearLayout(this);
        stationList.setOrientation(LinearLayout.VERTICAL);
        stationScroll.addView(stationList);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        );
        listParams.topMargin = dp(12);
        content.addView(stationScroll, listParams);
        phoneContent.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        visibleStations.clear();
        visibleStations.addAll(customStations);
        selectedStationIndex = Math.min(selectedStationIndex, Math.max(visibleStations.size() - 1, 0));
        renderStationRows();
        if (visibleStations.isEmpty()) {
            addPhoneEmptyState("No custom stations yet.");
        }
    }

    private void showPhoneSearch() {
        LinearLayout content = makePhoneScreenContent("Search", "Search all stations");

        EditText search = new EditText(this);
        search.setHint("Search stations");
        search.setSingleLine(true);
        search.setTextColor(color(COLOR_TEXT_PRIMARY));
        search.setHintTextColor(color(COLOR_TEXT_SECONDARY));
        search.setTextSize(16);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setIncludeFontPadding(false);
        search.setPadding(dp(16), 0, dp(16), 0);
        search.setBackground(makeCardBackground(COLOR_CARD, false, dp(18)));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
        );
        searchParams.topMargin = dp(18);
        content.addView(search, searchParams);

        TextView section = makePhoneSecondaryText("Recently Played Stations");
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        sectionParams.topMargin = dp(18);
        content.addView(section, sectionParams);

        stationScroll = makePhoneScroll();
        stationList = new LinearLayout(this);
        stationList.setOrientation(LinearLayout.VERTICAL);
        stationScroll.addView(stationList);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        );
        listParams.topMargin = dp(10);
        content.addView(stationScroll, listParams);
        phoneContent.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                renderPhoneSearchResults(text.toString());
            }
            @Override public void afterTextChanged(Editable editable) { }
        });
        renderPhoneSearchResults("");

        search.requestFocus();
        search.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 250);
    }

    private void showPhoneMore() {
        LinearLayout content = makePhoneScreenContent("More", "");
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(makeCardBackground(COLOR_CARD, false, dp(18)));
        group.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        groupParams.topMargin = dp(18);
        content.addView(group, groupParams);

        LinearLayout sleepRow = makeMoreRow("◴", "Sleep Timer", getSleepTimerLabel());
        sleepRow.setClickable(true);
        sleepRow.setOnClickListener(view -> showSleepTimerDialog());
        group.addView(sleepRow);
        group.addView(makeDivider());
        LinearLayout activityRow = makeMoreRow("▤", "Listening Activity", "");
        activityRow.setClickable(true);
        activityRow.setOnClickListener(view -> showListeningActivityScreen());
        group.addView(activityRow);

        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        footerParams.topMargin = dp(56);
        content.addView(makeAppFooter(), footerParams);
        phoneContent.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
    }

    private LinearLayout makePhoneScreenContent(String title, String subtitle) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, 0);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(color(COLOR_ACCENT));
        titleView.setTextSize(34);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleView.setIncludeFontPadding(false);
        content.addView(titleView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView subtitleView = makePhoneSecondaryText(subtitle);
            LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            subtitleParams.topMargin = dp(2);
            content.addView(subtitleView, subtitleParams);
        }
        return content;
    }

    private ScrollView makePhoneScroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(16));
        return scroll;
    }

    private Button makePhoneSegmentButton(String label, int categoryIndex) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setIncludeFontPadding(false);
        button.setOnClickListener(view -> switchCategory(categoryIndex, false));
        return button;
    }

    private Button makePhoneGoldButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(color(COLOR_ACCENT));
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setIncludeFontPadding(false);
        button.setBackground(makeRoundedButtonBackground(COLOR_CARD, false));
        return button;
    }

    private LinearLayout makePhoneSurpriseButton() {
        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setPadding(0, 0, 0, 0);
        button.setBackground(makeRoundedButtonBackground(COLOR_CARD, false));
        button.setOnClickListener(view -> playRandomStation());

        ImageView icon = new ImageView(this);
        Drawable shuffle = getResources().getDrawable(R.drawable.ic_shuffle).mutate();
        shuffle.setTint(color(COLOR_ACCENT));
        icon.setImageDrawable(shuffle);
        icon.setColorFilter(color(COLOR_ACCENT));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(26), dp(26));
        iconParams.rightMargin = dp(10);
        button.addView(icon, iconParams);

        TextView label = new TextView(this);
        label.setText("Surprise Me");
        label.setTextColor(color(COLOR_ACCENT));
        label.setTextSize(16);
        label.setGravity(Gravity.CENTER);
        label.setIncludeFontPadding(false);
        button.addView(label, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        return button;
    }

    private TextView makePhoneSecondaryText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color(COLOR_TEXT_SECONDARY));
        view.setTextSize(15);
        view.setIncludeFontPadding(false);
        return view;
    }

    private ImageView makeStationIcon(int sizeDp, int iconSizeDp) {
        ImageView stationIcon = new ImageView(this);
        stationIcon.setBackground(makeCardBackground(COLOR_CARD_DARK, false, dp(sizeDp >= 100 ? 26 : 10)));
        int padding = Math.max(0, (sizeDp - iconSizeDp) / 2);
        stationIcon.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        stationIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable icon = getResources().getDrawable(R.drawable.ic_station_radio).mutate();
        icon.setTint(color(COLOR_ACCENT));
        stationIcon.setImageDrawable(icon);
        return stationIcon;
    }

    private Button makeCircleButton(String text, int textSize) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(textSize);
        button.setTextColor(color(COLOR_BACKGROUND));
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setIncludeFontPadding(false);
        button.setBackground(makeCardBackground(COLOR_ACCENT, false, dp(999)));
        return button;
    }

    private ImageButton makeIconButton(int iconRes, int tintColor, int sizeDp, String backgroundColor) {
        ImageButton button = new ImageButton(this);
        int padding = Math.max(0, (48 - sizeDp) / 2);
        button.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setBackground(makeCardBackground(backgroundColor, false, dp(999)));
        Drawable icon = getResources().getDrawable(iconRes).mutate();
        icon.setTint(tintColor);
        button.setImageDrawable(icon);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setColorFilter(tintColor);
        return button;
    }

    private void setButtonStartIcon(Button button, int iconRes, int tintColor, int sizeDp) {
        Drawable icon = getResources().getDrawable(iconRes).mutate();
        icon.setTint(tintColor);
        icon.setBounds(0, 0, dp(sizeDp), dp(sizeDp));
        button.setCompoundDrawablePadding(dp(8));
        button.setCompoundDrawables(icon, null, null, null);
    }


    private Button makeControlButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(20);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setFocusable(true);
        button.setBackgroundResource(R.drawable.control_button_background);
        return button;
    }

    private void addTvSongIdentification(LinearLayout playerPanel, Button screensaverButton) {
        identifySongButton = makeControlButton("Identify Song");
        identifySongButton.setId(View.generateViewId());
        identifySongButton.setTextColor(color(COLOR_ACCENT));
        identifySongButton.setOnClickListener(view -> identifyTvSong());
        identifySongButton.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.getAction() == KeyEvent.ACTION_DOWN) {
                if (!stationRows.isEmpty()) {
                    stationRows.get(Math.min(selectedStationIndex, stationRows.size() - 1)).row.requestFocus();
                } else {
                    localTabButton.requestFocus();
                }
                return true;
            }
            return false;
        });
        playbackButton.setId(View.generateViewId());
        screensaverButton.setId(View.generateViewId());
        playbackButton.setNextFocusDownId(identifySongButton.getId());
        screensaverButton.setNextFocusDownId(identifySongButton.getId());
        identifySongButton.setNextFocusUpId(playbackButton.getId());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                playbackButton.getLayoutParams().width, playbackButton.getLayoutParams().height);
        actionParams.topMargin = dp(12);
        playerPanel.addView(identifySongButton, actionParams);
        // Also match the measured width when the existing controls use horizontal weights.
        playbackButton.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = right - left;
            int height = bottom - top;
            if (width > 0 && height > 0
                    && (actionParams.width != width || actionParams.height != height)) {
                actionParams.width = width;
                actionParams.height = height;
                identifySongButton.setLayoutParams(actionParams);
            }
        });

        songIdentificationText = new TextView(this);
        songIdentificationText.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        songIdentificationText.setGravity(Gravity.LEFT | Gravity.TOP);
        songIdentificationText.setPadding(0, songIdentificationText.getPaddingTop(),
                songIdentificationText.getPaddingRight(), songIdentificationText.getPaddingBottom());
        songIdentificationText.setTextSize(18);
        songIdentificationText.setTextColor(color(COLOR_ACCENT));
        songIdentificationText.setSingleLine(true);
        songIdentificationText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        songIdentificationText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(28));
        titleParams.topMargin = dp(8);
        playerPanel.addView(songIdentificationText, titleParams);

        songIdentificationArtist = new TextView(this);
        songIdentificationArtist.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
        songIdentificationArtist.setGravity(Gravity.LEFT | Gravity.TOP);
        songIdentificationArtist.setPadding(0, songIdentificationArtist.getPaddingTop(),
                songIdentificationArtist.getPaddingRight(), songIdentificationArtist.getPaddingBottom());
        songIdentificationArtist.setTextSize(16);
        songIdentificationArtist.setTextColor(color(COLOR_TEXT_SECONDARY));
        songIdentificationArtist.setSingleLine(true);
        songIdentificationArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        playerPanel.addView(songIdentificationArtist, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(24)));
    }

    private void identifyTvSong() {
        if (identifySongButton == null || identificationPending || identificationListening) return;
        if (mediaController == null || !mediaController.isConnected()) {
            TvIdentificationLog.info("request: MediaController is not connected");
            showTvIdentificationError();
            return;
        }
        MediaController controller = mediaController;
        MediaItem item = controller.getCurrentMediaItem();
        String mediaId = item == null ? "" : item.mediaId;
        int requestGeneration = ++identificationUiGeneration;
        identificationPending = true;
        songIdentificationText.setText("Listening…");
        songIdentificationArtist.setText("");
        identifySongButton.setText("Listening…");
        // Remain focusable while busy so a D-pad user can still navigate away.
        Bundle args = new Bundle();
        args.putString(TvSongIdentification.MEDIA_ID, mediaId);
        try {
            ListenableFuture<SessionResult> result = controller.sendCustomCommand(
                    new SessionCommand(TvSongIdentification.IDENTIFY, Bundle.EMPTY), args);
            result.addListener(() -> {
                if (mediaController != controller || requestGeneration != identificationUiGeneration) return;
                identificationPending = false;
                try {
                    int resultCode = result.get().resultCode;
                    if (resultCode != SessionResult.RESULT_SUCCESS) {
                        TvIdentificationLog.info("request: MediaSession rejected command; resultCode=" + resultCode);
                        showTvIdentificationError();
                    } else {
                        updateTvSongIdentification(controller.getSessionExtras());
                    }
                } catch (ExecutionException | java.util.concurrent.CancellationException error) {
                    TvIdentificationLog.error("MediaSession identification command", error);
                    showTvIdentificationError();
                } catch (InterruptedException error) {
                    TvIdentificationLog.error("MediaSession identification command interrupted", error);
                    Thread.currentThread().interrupt();
                    showTvIdentificationError();
                }
            }, getMainExecutor());
        } catch (RuntimeException error) {
            TvIdentificationLog.error("Sending identification command", error);
            showTvIdentificationError();
        }
    }

    private void updateTvSongIdentification(Bundle extras) {
        if (identifySongButton == null || mediaController == null) return;
        Bundle result = extras.getBundle(TvSongIdentification.EXTRAS);
        MediaItem current = mediaController.getCurrentMediaItem();
        String mediaId = current == null ? "" : current.mediaId;
        if (result == null || !mediaId.equals(result.getString(TvSongIdentification.MEDIA_ID, ""))) {
            if (!identificationPending) resetTvSongIdentification();
            return;
        }
        String state = result.getString(TvSongIdentification.STATE, TvSongIdentification.IDLE);
        identificationListening = TvSongIdentification.LISTENING.equals(state);
        identifySongButton.setText(identificationListening ? "Listening…" : "Identify Song");
        songIdentificationArtist.setText("");
        switch (state) {
            case TvSongIdentification.LISTENING:
                songIdentificationText.setText("Listening…");
                break;
            case TvSongIdentification.MATCH:
                songIdentificationText.setText(result.getString(TvSongIdentification.TITLE, ""));
                songIdentificationArtist.setText(result.getString(TvSongIdentification.ARTIST, ""));
                break;
            case TvSongIdentification.NO_MATCH:
                songIdentificationText.setText("Song not recognized");
                break;
            case TvSongIdentification.ERROR:
                showTvIdentificationError();
                break;
            default:
                songIdentificationText.setText("");
        }
    }

    private void showTvIdentificationError() {
        if (identifySongButton == null) return;
        identificationPending = false;
        identificationListening = false;
        identifySongButton.setText("Identify Song");
        songIdentificationText.setText("Unable to identify song");
        songIdentificationArtist.setText("");
    }

    private void resetTvSongIdentification() {
        if (identifySongButton == null) return;
        identificationUiGeneration++;
        identificationPending = false;
        identificationListening = false;
        identifySongButton.setText("Identify Song");
        songIdentificationText.setText("");
        songIdentificationArtist.setText("");
    }

    private LinearLayout makeAppFooter() {
        boolean television = isTelevisionLayout();
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(20), 0, 0);
        if (television) footer.setAlpha(0.8f);

        TextView name = new TextView(this);
        name.setText("SimpleRadio");
        name.setTextColor(color(COLOR_ACCENT));
        name.setTextSize(television ? 14 : 16);
        name.setGravity(Gravity.CENTER);

        TextView credit = new TextView(this);
        credit.setText("Made by Sandra Bartoloti");
        credit.setTextColor(color(COLOR_TEXT_SECONDARY));
        credit.setTextSize(television ? 12 : 14);
        credit.setGravity(Gravity.CENTER);

        TextView version = new TextView(this);
        version.setText("Version " + BuildConfig.VERSION_NAME);
        version.setTextColor(color("#78A9B4BC"));
        version.setTextSize(television ? 11 : 12);
        version.setGravity(Gravity.CENTER);

        footer.addView(name, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        footer.addView(credit, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        footer.addView(version, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        return footer;
    }

    private Button makeCategoryButton(String label) {
        Button button = makeControlButton(label);
        button.setTextSize(15);
        button.setOnFocusChangeListener((view, hasFocus) -> updateCategoryButtons());
        return button;
    }

    private LinearLayout.LayoutParams makeButtonParams() {
        if (isTelevisionLayout()) {
            return new LinearLayout.LayoutParams(dp(140), dp(40));
        }
        if (isCompactLayout()) {
            return new LinearLayout.LayoutParams(0, dp(52), 1);
        }
        return new LinearLayout.LayoutParams(dp(170), dp(52));
    }

    private boolean isCompactLayout() {
        return getResources().getConfiguration().screenWidthDp < 600;
    }

    private void switchCategory(int categoryIndex, boolean focusFirstStation) {
        selectedCategoryIndex = categoryIndex;
        visibleStations.clear();

        if (isTelevisionLayout() && selectedCategoryIndex == 2) {
            visibleStations.addAll(getFavoriteStationsInOrder());
        } else {
            String category = selectedCategoryIndex == 0 ? CATEGORY_LOCAL : CATEGORY_INTERNATIONAL;
            for (RadioStation station : allStations) {
                if (category.equals(station.category)) visibleStations.add(station);
            }
        }

        selectedStationIndex = 0;
        renderStationRows();
        updateCategoryButtons();
        if (stationTitle != null && !visibleStations.isEmpty()) {
            updateSelection(0);
        }
        if (focusFirstStation && !stationRows.isEmpty()) {
            stationRows.get(0).row.requestFocus();
        }
    }

    private void renderStationRows() {
        if (isTelevisionLayout()) {
            // The station data may already belong to the new category. Removing a
            // focused old row must not update markers using its obsolete index.
            for (StationRowViews oldRow : stationRows) {
                oldRow.row.setOnFocusChangeListener(null);
            }
        }
        stationList.removeAllViews();
        stationRows.clear();
        if (!isTelevisionLayout()) {
            renderPhoneStationRows();
            return;
        }

        boolean compactLayout = false;
        if (selectedCategoryIndex == 2 && visibleStations.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No favorites yet.\nHold OK on a station in Local or International to add it.");
            empty.setTextSize(16);
            empty.setTextColor(color(COLOR_TEXT_SECONDARY));
            empty.setPadding(dp(12), dp(20), dp(12), dp(20));
            stationList.addView(empty);
        }

        for (int i = 0; i < visibleStations.size(); i++) {
            int index = i;
            LinearLayout stationRow = new LinearLayout(this);
            stationRow.setOrientation(LinearLayout.HORIZONTAL);
            stationRow.setGravity(Gravity.CENTER_VERTICAL);
            stationRow.setFocusable(true);
            stationRow.setClickable(true);
            stationRow.setBackground(makeStationCardBackground(false, false));
            stationRow.setPadding(dp(compactLayout ? 12 : 14), 0, dp(12), 0);
            stationRow.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus) {
                    updateSelection(index);
                }
                updateStationMarkers();
            });
            stationRow.setOnClickListener(view -> {
                onStationPlayPause(visibleStations.get(index));
                updateStationMarkers();
            });

            ImageView stationIcon = makeStationIcon(30, 18);
            stationIcon.setFocusable(false);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(30), dp(30));
            iconParams.rightMargin = dp(12);
            stationRow.addView(stationIcon, iconParams);

            TextView stationName = new TextView(this);
            RadioStation rowStation = visibleStations.get(i);
            stationName.setText((favoriteStationIds.contains(rowStation.id) ? "★ " : "") + rowStation.name);
            stationRow.setOnLongClickListener(view -> {
                toggleFavorite(rowStation);
                boolean saved = favoriteStationIds.contains(rowStation.id);
                Toast.makeText(this, saved ? "Added to Favorites" : "Removed from Favorites", Toast.LENGTH_SHORT).show();
                if (selectedCategoryIndex == 2) {
                    switchCategory(2, false);
                    if (stationRows.isEmpty()) {
                        tvFavoritesTabButton.requestFocus();
                    } else {
                        stationRows.get(Math.min(index, stationRows.size() - 1)).row.requestFocus();
                    }
                } else {
                    stationName.setText((saved ? "★ " : "") + rowStation.name);
                }
                return true;
            });
            stationName.setTextColor(color(COLOR_TEXT_PRIMARY));
            stationName.setTextSize(compactLayout ? 17 : 18);
            stationName.setGravity(Gravity.CENTER_VERTICAL);
            stationName.setSingleLine(true);

            TextView playIndicator = new TextView(this);
            playIndicator.setText("▶");
            playIndicator.setTextColor(color(COLOR_TEXT_SECONDARY));
            playIndicator.setTextSize(compactLayout ? 25 : 22);
            playIndicator.setGravity(Gravity.CENTER);

            stationRow.addView(stationName, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1
            ));
            stationRow.addView(playIndicator, new LinearLayout.LayoutParams(dp(compactLayout ? 42 : 34), LinearLayout.LayoutParams.MATCH_PARENT));
            stationRows.add(new StationRowViews(stationRow, playIndicator));

            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(compactLayout ? 70 : 42)
            );
            itemParams.topMargin = dp(compactLayout ? 9 : 5);
            stationList.addView(stationRow, itemParams);
        }
        updateStationMarkers();
    }

    private void renderPhoneStationRows() {
        for (int i = 0; i < visibleStations.size(); i++) {
            int index = i;
            RadioStation station = visibleStations.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setClickable(true);
            row.setTag(station);
            row.setPadding(dp(12), 0, dp(8), 0);
            row.setBackground(makeStationCardBackground(station == activeStation, false));
            row.setOnClickListener(view -> {
                selectedStationIndex = index;
                updateSelection(index);
                onStationPlayPause(station);
            });

            ImageView icon = makeStationIcon(PHONE_ICON_SIZE_DP, 30);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(PHONE_ICON_SIZE_DP), dp(PHONE_ICON_SIZE_DP));
            iconParams.rightMargin = dp(12);
            row.addView(icon, iconParams);

            TextView name = new TextView(this);
            name.setText(station.name);
            name.setTextColor(color(COLOR_TEXT_PRIMARY));
            name.setTextSize(16);
            name.setSingleLine(true);
            name.setGravity(Gravity.CENTER_VERTICAL);
            name.setIncludeFontPadding(false);
            row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView playIndicator = new TextView(this);
            playIndicator.setText("▶");
            playIndicator.setTextColor(color(COLOR_TEXT_SECONDARY));
            playIndicator.setTextSize(22);
            playIndicator.setGravity(Gravity.CENTER);
            playIndicator.setIncludeFontPadding(false);
            playIndicator.setClickable(true);
            playIndicator.setOnClickListener(view -> {
                selectedStationIndex = index;
                updateSelection(index);
                onStationPlayPause(station);
            });
            LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(
                    dp(PHONE_CONTROL_SLOT_DP),
                    LinearLayout.LayoutParams.MATCH_PARENT
            );
            playParams.leftMargin = dp(6);
            row.addView(playIndicator, playParams);

            ImageButton favorite = makeFavoriteButton(station, 28);
            favorite.setOnClickListener(view -> {
                toggleFavorite(station);
                animateFavoriteToggle(favorite);
                if (selectedPhoneTab == 1) {
                    showPhoneTab(1);
                } else {
                    updateFavoriteButtonIcon(favorite, station, 28);
                }
            });
            row.addView(favorite, new LinearLayout.LayoutParams(dp(PHONE_CONTROL_SLOT_DP), LinearLayout.LayoutParams.MATCH_PARENT));

            if (selectedPhoneTab == 2 && station.custom) {
                TextView more = new TextView(this);
                more.setText("⋮");
                more.setTextColor(color(COLOR_TEXT_SECONDARY));
                more.setTextSize(24);
                more.setGravity(Gravity.CENTER);
                more.setIncludeFontPadding(false);
                more.setOnClickListener(view -> showCustomStationActions(station));
                row.addView(more, new LinearLayout.LayoutParams(dp(30), LinearLayout.LayoutParams.MATCH_PARENT));

                ImageButton drag = makeIconButton(R.drawable.ic_drag_handle, color(COLOR_TEXT_SECONDARY), 28, "#00101418");
                attachPhoneReorderHandle(drag, row, station);
                row.addView(drag, new LinearLayout.LayoutParams(dp(46), LinearLayout.LayoutParams.MATCH_PARENT));
            } else if (selectedPhoneTab == 1) {
                ImageButton drag = makeIconButton(R.drawable.ic_drag_handle, color(COLOR_TEXT_SECONDARY), 28, "#00101418");
                attachPhoneReorderHandle(drag, row, station);
                row.addView(drag, new LinearLayout.LayoutParams(dp(46), LinearLayout.LayoutParams.MATCH_PARENT));
            }

            stationRows.add(new StationRowViews(row, playIndicator));
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(PHONE_CARD_HEIGHT_DP)
            );
            itemParams.topMargin = dp(PHONE_ROW_GAP_DP);
            stationList.addView(row, itemParams);
        }
        updateStationMarkers();
    }

    private void renderPhoneSearchResults(String query) {
        if (stationList == null) {
            return;
        }
        visibleStations.clear();
        String normalized = query == null ? "" : query.trim().toLowerCase();
        for (RadioStation station : allStations) {
            if (normalized.isEmpty() || station.name.toLowerCase().contains(normalized)) {
                visibleStations.add(station);
            }
            if (normalized.isEmpty() && visibleStations.size() >= 5) {
                break;
            }
        }
        selectedStationIndex = 0;
        renderStationRows();
        if (visibleStations.isEmpty()) {
            addPhoneEmptyState("No stations found.");
        }
    }

    private void showCustomStationEditor(RadioStation editingStation) {
        LinearLayout screen = makePhoneScreenContent(editingStation == null ? "Add Station" : "Edit Station", "Custom stream URL");
        screen.setPadding(dp(16), dp(18), dp(16), dp(18));

        EditText nameField = makeCustomField("Station Name");
        EditText urlField = makeCustomField("Stream URL");
        TextView message = makePhoneSecondaryText("");
        message.setTextColor(color(COLOR_TEXT_SECONDARY));

        if (editingStation != null) {
            nameField.setText(editingStation.name);
            urlField.setText(editingStation.url);
        }

        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
        );
        fieldParams.topMargin = dp(18);
        screen.addView(nameField, fieldParams);

        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(54)
        );
        urlParams.topMargin = dp(12);
        screen.addView(urlField, urlParams);

        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        messageParams.topMargin = dp(12);
        screen.addView(message, messageParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        actionsParams.topMargin = dp(20);
        screen.addView(actions, actionsParams);

        Button testButton = makePhoneGoldButton("Test Stream");
        testButton.setOnClickListener(view -> {
            String name = nameField.getText().toString().trim();
            String url = urlField.getText().toString().trim();
            if (!validateCustomInput(name, url, editingStation, message, false)) {
                return;
            }
            message.setText("Testing...");
            RadioStation testStation = new RadioStation("test:" + SystemClock.uptimeMillis(), name, url, CATEGORY_CUSTOM, true);
            playStationDirectly(testStation);
            message.setText("Stream works if you hear audio. If not, try another URL.");
        });
        actions.addView(testButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
        ));

        Button saveButton = makePhoneGoldButton("Save");
        saveButton.setOnClickListener(view -> {
            String name = nameField.getText().toString().trim();
            String url = urlField.getText().toString().trim();
            if (!validateCustomInput(name, url, editingStation, message, true)) {
                return;
            }
            saveCustomStation(editingStation, name, url);
            showPhoneTab(2);
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        saveParams.topMargin = dp(10);
        actions.addView(saveButton, saveParams);

        Button cancelButton = makePhoneGoldButton("Cancel");
        cancelButton.setOnClickListener(view -> showPhoneTab(2));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        cancelParams.topMargin = dp(10);
        actions.addView(cancelButton, cancelParams);

        setContentView(screen);
        nameField.requestFocus();
    }

    private EditText makeCustomField(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setTextColor(color(COLOR_TEXT_PRIMARY));
        field.setHintTextColor(color(COLOR_TEXT_SECONDARY));
        field.setTextSize(16);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setIncludeFontPadding(false);
        field.setPadding(dp(16), 0, dp(16), 0);
        field.setBackground(makeCardBackground(COLOR_CARD, false, dp(18)));
        return field;
    }

    private boolean validateCustomInput(String name, String url, RadioStation editingStation, TextView message, boolean checkDuplicates) {
        if (name.isEmpty() || url.isEmpty()) {
            message.setText("Station name and stream URL are required.");
            return false;
        }
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            message.setText("Enter a valid http or https stream URL.");
            return false;
        }
        if (checkDuplicates && customUrlExists(url, editingStation)) {
            message.setText("This stream URL is already saved as a custom station.");
            return false;
        }
        return true;
    }

    private boolean customUrlExists(String url, RadioStation editingStation) {
        for (RadioStation station : customStations) {
            if ((editingStation == null || !station.id.equals(editingStation.id))
                    && station.url.equalsIgnoreCase(url.trim())) {
                return true;
            }
        }
        return false;
    }

    private void saveCustomStation(RadioStation editingStation, String name, String url) {
        if (editingStation == null) {
            RadioStation station = new RadioStation("custom:" + SystemClock.uptimeMillis(), name, url, CATEGORY_CUSTOM, true);
            customStations.add(station);
            allStations.add(station);
        } else {
            editingStation.name = name;
            editingStation.url = url;
            if (activeStation != null && activeStation.id.equals(editingStation.id)) {
                activeStation = editingStation;
            }
            if (loadedStation != null && loadedStation.id.equals(editingStation.id)) {
                loadedStation = editingStation;
            }
        }
        saveCustomStations();
        updatePhoneBottomUi();
    }

    private void showCustomStationActions(RadioStation station) {
        new AlertDialog.Builder(this)
                .setTitle(station.name)
                .setItems(new String[]{"Edit", "Delete"}, (dialog, which) -> {
                    if (which == 0) {
                        showCustomStationEditor(station);
                    } else {
                        confirmDeleteCustomStation(station);
                    }
                })
                .show();
    }

    private void confirmDeleteCustomStation(RadioStation station) {
        new AlertDialog.Builder(this)
                .setTitle("Delete station?")
                .setMessage("Remove " + station.name + " from Custom Stations?")
                .setPositiveButton("Delete", (dialog, which) -> deleteCustomStation(station))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCustomStation(RadioStation station) {
        customStations.remove(station);
        allStations.remove(station);
        favoriteStationIds.remove(station.id);
        favoriteStationOrder.remove(station.id);
        saveCustomStations();
        saveFavorites();
        if (activeStation != null && activeStation.id.equals(station.id)) {
            pausePlayback();
            activeStation = null;
            loadedStation = null;
        }
        showPhoneTab(2);
    }

    private void showSleepTimerDialog() {
        String[] options = {"Off", "15 minutes", "30 minutes", "45 minutes", "60 minutes", "Custom"};
        new AlertDialog.Builder(this)
                .setTitle("Sleep Timer")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        cancelSleepTimer();
                    } else if (which == 5) {
                        showCustomSleepTimerDialog();
                    } else {
                        int[] minutes = {0, 15, 30, 45, 60};
                        startSleepTimer(minutes[which]);
                    }
                })
                .show();
    }

    private void showCustomSleepTimerDialog() {
        EditText input = makeCustomField("Minutes");
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this)
                .setTitle("Custom Sleep Timer")
                .setView(input)
                .setPositiveButton("Start", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) {
                        Toast.makeText(this, "Enter minutes.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int minutes;
                    try {
                        minutes = Integer.parseInt(value);
                    } catch (NumberFormatException error) {
                        Toast.makeText(this, "Enter a valid number of minutes.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (minutes <= 0) {
                        Toast.makeText(this, "Enter more than 0 minutes.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    startSleepTimer(minutes);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startSleepTimer(int minutes) {
        sleepTimerEndTimeMs = System.currentTimeMillis() + minutes * 60_000L;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putLong(PREF_SLEEP_TIMER_END_TIME, sleepTimerEndTimeMs)
                .apply();
        Intent intent = new Intent(this, RadioPlaybackService.class);
        intent.setAction(RadioPlaybackService.ACTION_SET_SLEEP_TIMER);
        intent.putExtra(RadioPlaybackService.EXTRA_SLEEP_TIMER_END_TIME, sleepTimerEndTimeMs);
        startService(intent);
        refreshSleepTimerUi();
        sleepTimerUiHandler.removeCallbacks(sleepTimerUiTicker);
        sleepTimerUiHandler.post(sleepTimerUiTicker);
        Toast.makeText(this, "Sleep Timer " + getSleepTimerLabel(), Toast.LENGTH_SHORT).show();
    }

    private void cancelSleepTimer() {
        sleepTimerEndTimeMs = 0L;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .remove(PREF_SLEEP_TIMER_END_TIME)
                .apply();
        Intent intent = new Intent(this, RadioPlaybackService.class);
        intent.setAction(RadioPlaybackService.ACTION_CANCEL_SLEEP_TIMER);
        startService(intent);
        sleepTimerUiHandler.removeCallbacks(sleepTimerUiTicker);
        refreshSleepTimerUi();
    }

    private void showListeningActivityScreen() {
        phoneChildScreenActive = true;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(color(COLOR_BACKGROUND));
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroll.addView(screen, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton close = makeIconButton(R.drawable.ic_chevron_down, color(COLOR_TEXT_PRIMARY), 28, COLOR_CARD);
        close.setOnClickListener(view -> hidePhoneChildScreen());
        top.addView(close, new LinearLayout.LayoutParams(dp(52), dp(48)));

        TextView title = new TextView(this);
        title.setText("Listening Activity");
        title.setTextColor(color(COLOR_ACCENT));
        title.setTextSize(30);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleParams.leftMargin = dp(12);
        top.addView(title, titleParams);
        screen.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        ListeningStats stats = readListeningStats();
        LinearLayout summaries = new LinearLayout(this);
        summaries.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams summariesParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        summariesParams.topMargin = dp(24);
        screen.addView(summaries, summariesParams);
        summaries.addView(makeListeningSummaryCard("Today", formatDuration(stats.todayMs)));
        summaries.addView(makeListeningSummaryCard("This Week", formatDuration(stats.weekMs)));
        summaries.addView(makeListeningSummaryCard("All Time", formatDuration(stats.allTimeMs)));

        TextView section = makePhoneSecondaryText("Most Listened Stations");
        section.setTextColor(color(COLOR_TEXT_PRIMARY));
        section.setTextSize(18);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        sectionParams.topMargin = dp(26);
        screen.addView(section, sectionParams);

        if (stats.stationStats.isEmpty()) {
            TextView empty = makePhoneSecondaryText("No listening activity yet.");
            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            emptyParams.topMargin = dp(16);
            screen.addView(empty, emptyParams);
        } else {
            for (StationListeningStat stat : stats.stationStats) {
                screen.addView(makeListeningStationRow(stat));
            }
        }

        Button reset = makePhoneGoldButton("Reset Listening Activity");
        reset.setTextColor(color(COLOR_TEXT_SECONDARY));
        reset.setOnClickListener(view -> confirmResetListeningActivity());
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        resetParams.topMargin = dp(30);
        screen.addView(reset, resetParams);

        setContentView(scroll);
    }

    private void hidePhoneChildScreen() {
        phoneChildScreenActive = false;
        setContentView(mainContentView);
        updatePhoneBottomUi();
    }

    private LinearLayout makeListeningSummaryCard(String label, String value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(makeCardBackground(COLOR_CARD, false, dp(18)));

        TextView title = makePhoneSecondaryText(label);
        TextView duration = new TextView(this);
        duration.setText(value);
        duration.setTextColor(color(COLOR_TEXT_PRIMARY));
        duration.setTextSize(24);
        duration.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        duration.setIncludeFontPadding(false);

        card.addView(title);
        LinearLayout.LayoutParams durationParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        durationParams.topMargin = dp(4);
        card.addView(duration, durationParams);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = dp(10);
        card.setLayoutParams(cardParams);
        return card;
    }

    private LinearLayout makeListeningStationRow(StationListeningStat stat) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(14), 0);
        row.setBackground(makeStationCardBackground(false, false));

        ImageView icon = makeStationIcon(PHONE_ICON_SIZE_DP, 30);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(PHONE_ICON_SIZE_DP), dp(PHONE_ICON_SIZE_DP));
        iconParams.rightMargin = dp(12);
        row.addView(icon, iconParams);

        TextView name = new TextView(this);
        name.setText(stat.name);
        name.setTextColor(color(COLOR_TEXT_PRIMARY));
        name.setTextSize(16);
        name.setSingleLine(true);
        name.setIncludeFontPadding(false);
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView duration = makePhoneSecondaryText(formatDuration(stat.ms));
        duration.setGravity(Gravity.END);
        row.addView(duration, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(PHONE_CARD_HEIGHT_DP)
        );
        rowParams.topMargin = dp(PHONE_ROW_GAP_DP);
        row.setLayoutParams(rowParams);
        return row;
    }

    private void confirmResetListeningActivity() {
        new AlertDialog.Builder(this)
                .setTitle("Reset all listening activity?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (dialog, which) -> {
                    Intent intent = new Intent(this, RadioPlaybackService.class);
                    intent.setAction(RadioPlaybackService.ACTION_RESET_LISTENING_ACTIVITY);
                    startService(intent);
                    showListeningActivityScreen();
                })
                .show();
    }

    private void loadSleepTimer() {
        sleepTimerEndTimeMs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getLong(PREF_SLEEP_TIMER_END_TIME, 0L);
        if (sleepTimerEndTimeMs > 0L && sleepTimerEndTimeMs <= System.currentTimeMillis()) {
            sleepTimerEndTimeMs = 0L;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .remove(PREF_SLEEP_TIMER_END_TIME)
                    .apply();
        }
        if (sleepTimerEndTimeMs > System.currentTimeMillis()) {
            sleepTimerUiHandler.post(sleepTimerUiTicker);
        }
    }

    private void loadScreensaverMode() {
        screensaverMode = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(PREF_SCREENSAVER_MODE, 0);
        if (screensaverMode < 0 || screensaverMode >= ScreensaverView.MODE_COUNT) {
            screensaverMode = 0;
        }
    }

    private void refreshSleepTimerUi() {
        if (sleepTimerEndTimeMs > 0L && sleepTimerEndTimeMs <= System.currentTimeMillis()) {
            sleepTimerEndTimeMs = 0L;
        }
        String label = getSleepTimerLabel();
        if (sleepTimerStateText != null) {
            sleepTimerStateText.setText(label + "  ›");
        }
        if (nowPlayingSleepText != null) {
            nowPlayingSleepText.setText("Off".equals(label) ? "" : label);
        }
    }

    private String getSleepTimerLabel() {
        if (sleepTimerEndTimeMs <= System.currentTimeMillis()) {
            return "Off";
        }
        long remainingMs = sleepTimerEndTimeMs - System.currentTimeMillis();
        long remainingMinutes = Math.max(1L, (remainingMs + 59_999L) / 60_000L);
        return remainingMinutes + " min";
    }

    private ListeningStats readListeningStats() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        ListeningStats stats = new ListeningStats();
        stats.allTimeMs = prefs.getLong(RadioPlaybackService.PREF_LISTENING_ALL_TIME_MS, 0L);
        String todayKey = formatDateKey(new Date());
        Set<String> weekKeys = getCurrentWeekKeys();

        try {
            JSONObject daily = new JSONObject(prefs.getString(RadioPlaybackService.PREF_LISTENING_DAILY_MS, "{}"));
            stats.todayMs = daily.optLong(todayKey, 0L);
            for (String key : weekKeys) {
                stats.weekMs += daily.optLong(key, 0L);
            }
        } catch (JSONException ignored) {
        }

        try {
            JSONObject stationRoot = new JSONObject(prefs.getString(RadioPlaybackService.PREF_LISTENING_STATIONS_MS, "{}"));
            JSONArray names = stationRoot.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String stationId = names.optString(i);
                    JSONObject item = stationRoot.optJSONObject(stationId);
                    if (item == null) {
                        continue;
                    }
                    String name = item.optString("name", stationId);
                    RadioStation station = findStationById(stationId);
                    if (station != null) {
                        name = station.name;
                    }
                    stats.stationStats.add(new StationListeningStat(stationId, name, item.optLong("ms", 0L)));
                }
            }
        } catch (JSONException ignored) {
        }

        long activeStart = prefs.getLong(RadioPlaybackService.PREF_ACTIVE_LISTENING_START_MS, 0L);
        String activeId = prefs.getString(RadioPlaybackService.PREF_ACTIVE_LISTENING_STATION_ID, "");
        if (activeStart > 0L && activeId != null && !activeId.isEmpty()) {
            long elapsed = Math.max(0L, System.currentTimeMillis() - activeStart);
            stats.allTimeMs += elapsed;
            stats.todayMs += elapsed;
            stats.weekMs += elapsed;
            addActiveElapsedToStationStats(stats.stationStats, activeId,
                    prefs.getString(RadioPlaybackService.PREF_ACTIVE_LISTENING_STATION_NAME, activeId),
                    elapsed);
        }

        Collections.sort(stats.stationStats, (first, second) -> Long.compare(second.ms, first.ms));
        return stats;
    }

    private void addActiveElapsedToStationStats(List<StationListeningStat> stationStats, String stationId, String stationName, long elapsedMs) {
        for (StationListeningStat stat : stationStats) {
            if (stat.id.equals(stationId)) {
                stat.ms += elapsedMs;
                return;
            }
        }
        RadioStation station = findStationById(stationId);
        stationStats.add(new StationListeningStat(stationId, station != null ? station.name : stationName, elapsedMs));
    }

    private Set<String> getCurrentWeekKeys() {
        Set<String> keys = new HashSet<>();
        Calendar calendar = Calendar.getInstance();
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        for (int i = 0; i < 7; i++) {
            keys.add(formatDateKey(calendar.getTime()));
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        return keys;
    }

    private String formatDateKey(Date date) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(date);
    }

    private String formatDuration(long durationMs) {
        long totalMinutes = Math.max(0L, durationMs / 60_000L);
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private void attachPhoneReorderHandle(ImageButton handle, View row, RadioStation station) {
        handle.setOnTouchListener((view, event) -> handlePhoneReorderTouch(row, station, event));
    }

    private boolean handlePhoneReorderTouch(View row, RadioStation station, MotionEvent event) {
        if (selectedPhoneTab != 1 && selectedPhoneTab != 2) {
            return false;
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                draggingStation = station;
                draggingRow = row;
                dragStartRawY = event.getRawY();
                dragStartTranslationY = row.getTranslationY();
                dragStartIndex = getCurrentReorderIndex(station);
                dragCurrentIndex = dragStartIndex;
                dragStarted = false;
                if (row.getParent() != null) {
                    row.getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (draggingStation == null || draggingRow == null || !draggingStation.id.equals(station.id)) {
                    return true;
                }
                float deltaY = event.getRawY() - dragStartRawY;
                if (!dragStarted && Math.abs(deltaY) < ViewConfiguration.get(this).getScaledTouchSlop()) {
                    return true;
                }
                if (!dragStarted) {
                    dragStarted = true;
                    draggingRow.animate().cancel();
                    draggingRow.setScaleX(1.02f);
                    draggingRow.setScaleY(1.02f);
                    draggingRow.setAlpha(0.9f);
                    draggingRow.setElevation(dp(8));
                }
                if (draggingRow != null) {
                    draggingRow.setTranslationY(dragStartTranslationY + deltaY);
                }
                maybeMoveDraggedStation(deltaY);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                finishPhoneReorderDrag();
                return true;
            default:
                return true;
        }
    }

    private void resetPhoneRowDragFeedback() {
        for (StationRowViews rowViews : stationRows) {
            rowViews.row.animate().cancel();
            rowViews.row.setScaleX(1f);
            rowViews.row.setScaleY(1f);
            rowViews.row.setAlpha(1f);
            rowViews.row.setTranslationY(0f);
            rowViews.row.setElevation(0f);
        }
    }

    private int getCurrentReorderIndex(RadioStation station) {
        if (selectedPhoneTab == 1) {
            syncFavoriteOrder();
            return favoriteStationOrder.indexOf(station.id);
        }
        return customStations.indexOf(station);
    }

    private void maybeMoveDraggedStation(float deltaY) {
        if (draggingStation == null || !dragStarted || dragCurrentIndex < 0) {
            return;
        }
        int rowStep = dp(PHONE_CARD_HEIGHT_DP + PHONE_ROW_GAP_DP);
        int offset = Math.round(deltaY / rowStep);
        int targetIndex = dragStartIndex + offset;
        int maxIndex = selectedPhoneTab == 1 ? favoriteStationOrder.size() - 1 : customStations.size() - 1;
        targetIndex = Math.max(0, Math.min(maxIndex, targetIndex));
        int currentIndex = getCurrentReorderIndex(draggingStation);
        if (targetIndex == currentIndex || currentIndex < 0) {
            return;
        }
        if (selectedPhoneTab == 1) {
            moveFavoriteStation(currentIndex, targetIndex);
        } else {
            moveCustomStation(currentIndex, targetIndex);
        }
        dragCurrentIndex = targetIndex;
        animateRowsForDrag(rowStep);
    }

    private void animateRowsForDrag(int rowStep) {
        for (int i = 0; i < stationRows.size(); i++) {
            StationRowViews rowViews = stationRows.get(i);
            if (rowViews.row == draggingRow) {
                continue;
            }
            Object tag = rowViews.row.getTag();
            if (!(tag instanceof RadioStation)) {
                continue;
            }
            int newIndex = getCurrentReorderIndex((RadioStation) tag);
            if (newIndex < 0) {
                continue;
            }
            rowViews.row.animate()
                    .translationY((newIndex - i) * rowStep)
                    .setDuration(120)
                    .start();
        }
    }

    private void finishPhoneReorderDrag() {
        boolean shouldCommit = dragStarted;
        if (draggingRow != null && draggingRow.getParent() != null) {
            draggingRow.getParent().requestDisallowInterceptTouchEvent(false);
        }
        if (shouldCommit) {
            if (selectedPhoneTab == 1) {
                saveFavorites();
            } else if (selectedPhoneTab == 2) {
                saveCustomStations();
            }
        }
        resetPhoneRowDragFeedback();
        draggingStation = null;
        draggingRow = null;
        dragStartIndex = -1;
        dragCurrentIndex = -1;
        dragStarted = false;
        if (shouldCommit && stationList != null) {
            stationList.post(this::renderStationRows);
        }
    }

    private void moveCustomStation(int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= customStations.size() || to >= customStations.size()) {
            return;
        }
        RadioStation station = customStations.remove(from);
        customStations.add(to, station);
        visibleStations.clear();
        visibleStations.addAll(customStations);
        selectedStationIndex = to;
    }

    private void moveFavoriteStation(int from, int to) {
        syncFavoriteOrder();
        if (from == to || from < 0 || to < 0 || from >= favoriteStationOrder.size() || to >= favoriteStationOrder.size()) {
            return;
        }
        String stationId = favoriteStationOrder.remove(from);
        favoriteStationOrder.add(to, stationId);
        visibleStations.clear();
        visibleStations.addAll(getFavoriteStationsInOrder());
        selectedStationIndex = to;
    }

    private void loadCustomStations() {
        customStations.clear();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String stored = prefs.getString(PREF_CUSTOM_STATIONS, "[]");
        try {
            JSONArray stations = new JSONArray(stored);
            for (int i = 0; i < stations.length(); i++) {
                JSONObject item = stations.getJSONObject(i);
                String id = item.optString("id", "custom:" + SystemClock.uptimeMillis() + ":" + i);
                String name = item.optString("name", "").trim();
                String url = item.optString("url", "").trim();
                if (!name.isEmpty() && !url.isEmpty()) {
                    RadioStation station = new RadioStation(id, name, url, CATEGORY_CUSTOM, true);
                    customStations.add(station);
                    allStations.add(station);
                }
            }
        } catch (JSONException ignored) {
            prefs.edit().remove(PREF_CUSTOM_STATIONS).apply();
        }
    }

    private void saveCustomStations() {
        JSONArray stations = new JSONArray();
        for (RadioStation station : customStations) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", station.id);
                item.put("name", station.name);
                item.put("url", station.url);
                stations.put(item);
            } catch (JSONException ignored) {
            }
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_CUSTOM_STATIONS, stations.toString())
                .apply();
    }

    private void loadFavorites() {
        favoriteStationIds.clear();
        favoriteStationOrder.clear();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        favoriteStationIds.addAll(prefs.getStringSet(PREF_FAVORITES, new HashSet<>()));
        String storedOrder = prefs.getString(PREF_FAVORITE_ORDER, "[]");
        try {
            JSONArray ids = new JSONArray(storedOrder);
            for (int i = 0; i < ids.length(); i++) {
                String id = ids.optString(i, "");
                if (favoriteStationIds.contains(id) && findStationById(id) != null) {
                    favoriteStationOrder.add(id);
                }
            }
        } catch (JSONException ignored) {
        }
        syncFavoriteOrder();
    }

    private void saveFavorites() {
        syncFavoriteOrder();
        JSONArray ids = new JSONArray();
        for (String id : favoriteStationOrder) {
            ids.put(id);
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putStringSet(PREF_FAVORITES, new HashSet<>(favoriteStationIds))
                .putString(PREF_FAVORITE_ORDER, ids.toString())
                .apply();
    }

    private void syncFavoriteOrder() {
        favoriteStationOrder.removeIf(id -> !favoriteStationIds.contains(id) || findStationById(id) == null);
        for (RadioStation station : allStations) {
            if (favoriteStationIds.contains(station.id) && !favoriteStationOrder.contains(station.id)) {
                favoriteStationOrder.add(station.id);
            }
        }
    }

    private List<RadioStation> getFavoriteStationsInOrder() {
        syncFavoriteOrder();
        List<RadioStation> stations = new ArrayList<>();
        for (String id : favoriteStationOrder) {
            RadioStation station = findStationById(id);
            if (station != null) {
                stations.add(station);
            }
        }
        return stations;
    }

    private RadioStation findStationById(String id) {
        for (RadioStation station : allStations) {
            if (station.id.equals(id)) {
                return station;
            }
        }
        return null;
    }

    private String makeBuiltinStationId(String name) {
        return "builtin:" + name.trim().toLowerCase().replaceAll("[^a-z0-9]+", "_");
    }

    private void addPhoneEmptyState(String text) {
        TextView empty = makePhoneSecondaryText(text);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(36), 0, dp(36));
        stationList.addView(empty, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
    }

    private void toggleFavorite(RadioStation station) {
        if (favoriteStationIds.contains(station.id)) {
            favoriteStationIds.remove(station.id);
            favoriteStationOrder.remove(station.id);
        } else {
            favoriteStationIds.add(station.id);
            favoriteStationOrder.remove(station.id);
            favoriteStationOrder.add(station.id);
        }
        saveFavorites();
    }

    private LinearLayout makeMoreRow(String iconText, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));

        TextView icon = new TextView(this);
        icon.setText(iconText);
        icon.setTextColor(color(COLOR_ACCENT));
        icon.setTextSize(22);
        icon.setGravity(Gravity.CENTER);
        row.addView(icon, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView title = new TextView(this);
        title.setText(label);
        title.setTextColor(color(COLOR_TEXT_PRIMARY));
        title.setTextSize(16);
        row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView state = new TextView(this);
        state.setText((value == null || value.isEmpty()) ? "›" : value + "  ›");
        state.setTextColor(color(COLOR_TEXT_SECONDARY));
        state.setTextSize(15);
        state.setGravity(Gravity.END);
        if ("Sleep Timer".equals(label)) {
            sleepTimerStateText = state;
        }
        row.addView(state, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return row;
    }

    private View makeDivider() {
        View divider = new View(this);
        divider.setBackgroundColor(color("#1FFFFFFF"));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
        ));
        return divider;
    }

    private void updatePhoneNavButtons() {
        for (int i = 0; i < phoneNavButtons.length; i++) {
            Button button = phoneNavButtons[i];
            if (button == null) {
                continue;
            }
            boolean selected = i == selectedPhoneTab;
            int tint = color(selected ? COLOR_ACCENT : COLOR_TEXT_PRIMARY);
            button.setTextColor(tint);
            button.setBackground(makeCardBackground(selected ? COLOR_CARD_FOCUSED : "#00101418", false, dp(18)));
            Drawable icon = getResources().getDrawable(phoneNavIcons[i]).mutate();
            icon.setTint(tint);
            icon.setBounds(0, 0, dp(28), dp(28));
            button.setCompoundDrawables(null, icon, null, null);
        }
    }

    private void updatePhoneBottomUi() {
        if (phoneMiniPlayer == null) {
            return;
        }
        RadioStation station = activeStation != null ? activeStation : loadedStation;
        if (station == null) {
            phoneMiniPlayer.setVisibility(View.GONE);
            return;
        }

        phoneMiniPlayer.setVisibility(View.VISIBLE);
        phoneMiniTitle.setText(station.name);
        phoneMiniSong.setTag(station.id);
        renderPhoneNowPlayingSong();
        phoneMiniStatus.setText(mediaController != null && mediaController.isPlaying() ? "Live" : "Paused");
        phoneMiniPlayButton.setText(mediaController != null && mediaController.isPlaying() ? "Ⅱ" : "▶");
    }

    private void toggleCurrentPlayback() {
        if (mediaController != null && mediaController.isPlaying()) {
            pausePlayback();
            return;
        }
        if (loadedStation != null) {
            playStationDirectly(loadedStation);
            return;
        }
        playSelectedStation();
    }

    private void showPhoneNowPlaying() {
        RadioStation station = activeStation != null ? activeStation : loadedStation;
        if (station == null) {
            return;
        }

        phoneNowPlayingActive = true;
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setGravity(Gravity.CENTER_HORIZONTAL);
        screen.setPadding(dp(18), dp(18), dp(18), dp(28));
        screen.setBackgroundColor(color(COLOR_BACKGROUND));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton close = makeIconButton(R.drawable.ic_chevron_down, color(COLOR_TEXT_PRIMARY), 28, COLOR_CARD);
        close.setOnClickListener(view -> hidePhoneNowPlaying());
        top.addView(close, new LinearLayout.LayoutParams(dp(52), dp(48)));

        TextView source = new TextView(this);
        source.setText("PLAYING FROM " + getPhoneSourceLabel(station));
        source.setTextColor(color(COLOR_TEXT_SECONDARY));
        source.setTextSize(12);
        source.setGravity(Gravity.CENTER);
        top.addView(source, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView menu = new TextView(this);
        menu.setText("⋯");
        menu.setTextColor(color(COLOR_TEXT_PRIMARY));
        menu.setTextSize(28);
        menu.setGravity(Gravity.CENTER);
        top.addView(menu, new LinearLayout.LayoutParams(dp(52), dp(48)));
        screen.addView(top, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        ImageView artwork = makeStationIcon(220, 118);
        LinearLayout.LayoutParams artworkParams = new LinearLayout.LayoutParams(dp(220), dp(220));
        artworkParams.topMargin = dp(52);
        screen.addView(artwork, artworkParams);

        TextView title = new TextView(this);
        title.setText(station.name);
        title.setTextColor(color(COLOR_TEXT_PRIMARY));
        title.setTextSize(30);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = dp(32);
        screen.addView(title, titleParams);
        phoneFullSong = makePhoneSongText(16);
        phoneFullSong.setGravity(Gravity.CENTER);
        phoneFullSong.setTag(station.id);
        screen.addView(phoneFullSong, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        renderPhoneNowPlayingSong();

        TextView status = makePhoneSecondaryText(mediaController != null && mediaController.isPlaying() ? "Live" : "Paused");
        status.setGravity(Gravity.CENTER);
        screen.addView(status, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        controlsParams.topMargin = dp(34);
        screen.addView(controls, controlsParams);

        LinearLayout sleepControl = new LinearLayout(this);
        sleepControl.setOrientation(LinearLayout.VERTICAL);
        sleepControl.setGravity(Gravity.CENTER);
        ImageButton sleep = makeIconButton(R.drawable.ic_timer, color(COLOR_TEXT_SECONDARY), 25, "#00101418");
        sleep.setOnClickListener(view -> showSleepTimerDialog());
        sleepControl.addView(sleep, new LinearLayout.LayoutParams(dp(48), dp(48)));
        nowPlayingSleepText = makePhoneSecondaryText(getSleepTimerLabel().equals("Off") ? "" : getSleepTimerLabel());
        nowPlayingSleepText.setGravity(Gravity.CENTER);
        nowPlayingSleepText.setTextSize(11);
        sleepControl.addView(nowPlayingSleepText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        controls.addView(sleepControl, new LinearLayout.LayoutParams(dp(54), LinearLayout.LayoutParams.WRAP_CONTENT));

        ImageButton previous = makeIconButton(R.drawable.ic_skip_previous, color(COLOR_TEXT_PRIMARY), 30, "#00101418");
        LinearLayout.LayoutParams previousParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        previousParams.leftMargin = dp(8);
        controls.addView(previous, previousParams);

        Button play = makeCircleButton(mediaController != null && mediaController.isPlaying() ? "Ⅱ" : "▶", 28);
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(74), dp(74));
        playParams.leftMargin = dp(12);
        playParams.rightMargin = dp(12);
        controls.addView(play, playParams);

        ImageButton next = makeIconButton(R.drawable.ic_skip_next, color(COLOR_TEXT_PRIMARY), 30, "#00101418");
        controls.addView(next, new LinearLayout.LayoutParams(dp(48), dp(48)));

        ImageButton favorite = makeFavoriteButton(station, 28);
        LinearLayout.LayoutParams favoriteParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        favoriteParams.leftMargin = dp(8);
        controls.addView(favorite, favoriteParams);

        previous.setOnClickListener(view -> {
            playAdjacentPhoneStation(-1);
            refreshPhoneNowPlayingText(source, title, status, play, favorite);
        });
        next.setOnClickListener(view -> {
            playAdjacentPhoneStation(1);
            refreshPhoneNowPlayingText(source, title, status, play, favorite);
        });
        play.setOnClickListener(view -> {
            toggleCurrentPlayback();
            refreshPhoneNowPlayingText(source, title, status, play, favorite);
        });
        favorite.setOnClickListener(view -> {
            RadioStation current = activeStation != null ? activeStation : loadedStation;
            if (current != null) {
                toggleFavorite(current);
                animateFavoriteToggle(favorite);
                refreshPhoneNowPlayingText(source, title, status, play, favorite);
                if (selectedPhoneTab == 1) {
                    showPhoneTab(1);
                    showPhoneNowPlaying();
                }
            }
        });

        setContentView(screen);
    }

    private void hidePhoneNowPlaying() {
        phoneNowPlayingActive = false;
        setContentView(mainContentView);
        updatePhoneBottomUi();
    }

    private void playAdjacentPhoneStation(int direction) {
        RadioStation current = activeStation != null ? activeStation : loadedStation;
        List<RadioStation> sourceStations = getCurrentPhoneSourceStations(current);
        if (sourceStations.isEmpty()) {
            return;
        }

        int currentIndex = current == null ? selectedStationIndex : indexOfStation(sourceStations, current);
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        int nextIndex = (currentIndex + direction + sourceStations.size()) % sourceStations.size();
        RadioStation nextStation = sourceStations.get(nextIndex);

        if (selectedPhoneTab == 0 && !nextStation.category.equals(getCurrentHomeCategory())) {
            int categoryIndex = CATEGORY_LOCAL.equals(nextStation.category) ? 0 : 1;
            switchCategory(categoryIndex, false);
        }
        selectedStationIndex = indexOfStation(visibleStations, nextStation);
        if (selectedStationIndex < 0) {
            selectedStationIndex = 0;
        }
        updateSelection(selectedStationIndex);
        playStationDirectly(nextStation);
    }

    private List<RadioStation> getCurrentPhoneSourceStations(RadioStation current) {
        if (!visibleStations.isEmpty() && (current == null || indexOfStation(visibleStations, current) >= 0)) {
            return new ArrayList<>(visibleStations);
        }

        List<RadioStation> stations = new ArrayList<>();
        if (selectedPhoneTab == 1) {
            stations.addAll(getFavoriteStationsInOrder());
        } else if (selectedPhoneTab == 2) {
            stations.addAll(customStations);
        } else if (current != null && CATEGORY_CUSTOM.equals(current.category)) {
            stations.addAll(customStations);
        } else {
            String category = current != null ? current.category : getCurrentHomeCategory();
            for (RadioStation station : allStations) {
                if (category.equals(station.category)) {
                    stations.add(station);
                }
            }
        }
        if (stations.isEmpty()) {
            stations.addAll(allStations);
        }
        return stations;
    }

    private String getCurrentHomeCategory() {
        return selectedCategoryIndex == 0 ? CATEGORY_LOCAL : CATEGORY_INTERNATIONAL;
    }

    private int indexOfStation(List<RadioStation> stations, RadioStation station) {
        if (station == null) {
            return -1;
        }
        for (int i = 0; i < stations.size(); i++) {
            if (stations.get(i).id.equals(station.id)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isSameStation(RadioStation first, RadioStation second) {
        return first != null && second != null && first.id.equals(second.id);
    }

    private void refreshPhoneNowPlayingText(TextView source, TextView title, TextView status, Button play, ImageButton favorite) {
        RadioStation station = activeStation != null ? activeStation : loadedStation;
        if (station == null) {
            return;
        }
        source.setText("PLAYING FROM " + getPhoneSourceLabel(station));
        title.setText(station.name);
        if (phoneFullSong != null) phoneFullSong.setTag(station.id);
        renderPhoneNowPlayingSong();
        status.setText(mediaController != null && mediaController.isPlaying() ? "Live" : "Paused");
        play.setText(mediaController != null && mediaController.isPlaying() ? "Ⅱ" : "▶");
        boolean isFavorite = favoriteStationIds.contains(station.id);
        setIconOnlyButtonIcon(favorite, isFavorite ? R.drawable.ic_heart : R.drawable.ic_heart_outline,
                color(isFavorite ? COLOR_ACCENT : COLOR_TEXT_SECONDARY), 27);
    }

    private String getPhoneSourceLabel(RadioStation station) {
        if (selectedPhoneTab == 1) {
            return "FAVORITES";
        }
        if (selectedPhoneTab == 2 || CATEGORY_CUSTOM.equals(station.category)) {
            return "CUSTOM";
        }
        if (selectedPhoneTab == 3) {
            return "SEARCH";
        }
        return station.category.toUpperCase();
    }

    private void setIconOnlyButtonIcon(ImageButton button, int iconRes, int tintColor, int sizeDp) {
        Drawable icon = getResources().getDrawable(iconRes).mutate();
        icon.setTint(tintColor);
        button.setImageDrawable(icon);
        button.setColorFilter(tintColor);
    }

    private ImageButton makeFavoriteButton(RadioStation station, int iconSizeDp) {
        ImageButton button = makeIconButton(R.drawable.ic_heart_outline, color(COLOR_TEXT_SECONDARY), iconSizeDp, "#00101418");
        updateFavoriteButtonIcon(button, station, iconSizeDp);
        return button;
    }

    private void updateFavoriteButtonIcon(ImageButton button, RadioStation station, int iconSizeDp) {
        boolean isFavorite = favoriteStationIds.contains(station.id);
        setIconOnlyButtonIcon(
                button,
                isFavorite ? R.drawable.ic_heart : R.drawable.ic_heart_outline,
                color(isFavorite ? COLOR_ACCENT : COLOR_TEXT_SECONDARY),
                iconSizeDp
        );
    }

    private void animateFavoriteToggle(View view) {
        view.animate().cancel();
        view.setScaleX(0.9f);
        view.setScaleY(0.9f);
        view.animate()
                .scaleX(1.12f)
                .scaleY(1.12f)
                .setDuration(90)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(90)
                        .start())
                .start();
    }

    private void updateCategoryButtons() {
        if (localTabButton == null || internationalTabButton == null) {
            return;
        }

        localTabButton.setText("Local");
        internationalTabButton.setText(isTelevisionLayout() ? "Global" : "International");

        updateCategoryButtonStyle(localTabButton, selectedCategoryIndex == 0);
        updateCategoryButtonStyle(internationalTabButton, selectedCategoryIndex == 1);
        if (tvFavoritesTabButton != null) updateCategoryButtonStyle(tvFavoritesTabButton, selectedCategoryIndex == 2);
        if (surpriseButton != null) {
            surpriseButton.setBackground(makeRoundedButtonBackground(surpriseButton.hasFocus() ? COLOR_CARD_FOCUSED : COLOR_CARD, surpriseButton.hasFocus()));
            surpriseButton.setTextColor(color(COLOR_ACCENT));
        }
    }

    private void updateCategoryButtonStyle(Button button, boolean selected) {
        boolean focused = button.hasFocus();
        if (!isTelevisionLayout()) {
            button.setBackground(makeCardBackground(selected ? COLOR_CARD_FOCUSED : "#00101418", false, dp(18)));
            button.setTextColor(color(selected ? COLOR_ACCENT : COLOR_TEXT_PRIMARY));
            return;
        }
        if (selected) {
            button.setBackground(makeRoundedButtonBackground(COLOR_CARD_FOCUSED, focused));
            button.setTextColor(color(COLOR_ACCENT));
        } else if (focused) {
            button.setBackground(makeRoundedButtonBackground(COLOR_CARD_FOCUSED, true));
            button.setTextColor(color(COLOR_TEXT_PRIMARY));
        } else {
            button.setBackground(makeRoundedButtonBackground(COLOR_CARD, false));
            button.setTextColor(color(COLOR_TEXT_PRIMARY));
        }
    }

    private GradientDrawable makeRoundedButtonBackground(String fillColor, boolean focused) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(color(fillColor));
        background.setCornerRadius(dp(22));
        if (focused) {
            background.setStroke(dp(2), color(COLOR_ACCENT));
        }
        return background;
    }

    private boolean handleCategoryKey(int categoryIndex, int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            int target = Math.max(0, categoryIndex - 1);
            switchCategory(target, false);
            (target == 0 ? localTabButton : internationalTabButton).requestFocus();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (categoryIndex == 2) {
                surpriseButton.requestFocus();
            } else {
                int target = categoryIndex + 1;
                switchCategory(target, false);
                (target == 1 ? internationalTabButton : tvFavoritesTabButton).requestFocus();
            }
            updateCategoryButtons();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (selectedCategoryIndex != categoryIndex) switchCategory(categoryIndex, false);
            if (!stationRows.isEmpty()) {
                stationRows.get(0).row.requestFocus();
            }
            updateCategoryButtons();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (selectedCategoryIndex != categoryIndex) {
                switchCategory(categoryIndex, false);
            }
            if (!stationRows.isEmpty()) {
                stationRows.get(0).row.requestFocus();
            }
            return true;
        }

        return false;
    }

    private boolean handleSurpriseKey(int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            tvFavoritesTabButton.requestFocus();
            updateCategoryButtons();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (!stationRows.isEmpty()) {
                stationRows.get(0).row.requestFocus();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            playRandomStation();
            return true;
        }

        return false;
    }

    private void playRandomStation() {
        List<RadioStation> playableStations = new ArrayList<>();
        for (RadioStation station : allStations) {
            if (!station.url.trim().isEmpty()) {
                playableStations.add(station);
            }
        }

        if (playableStations.isEmpty()) {
            Toast.makeText(this, "No playable stations found.", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioStation station = playableStations.get(new Random().nextInt(playableStations.size()));
        if (station.custom && !isTelevisionLayout()) {
            showPhoneTab(2);
            selectedStationIndex = visibleStations.indexOf(station);
            if (selectedStationIndex < 0) {
                selectedStationIndex = 0;
            }
            updateSelection(selectedStationIndex);
            playStationDirectly(station);
            return;
        }
        int categoryIndex = CATEGORY_LOCAL.equals(station.category) ? 0 : 1;
        switchCategory(categoryIndex, false);
        selectedStationIndex = visibleStations.indexOf(station);
        updateSelection(selectedStationIndex);
        playSelectedStation();
    }

    private boolean isStationRowFocused() {
        if (!isTelevisionLayout()) {
            return false;
        }
        View focusedView = getCurrentFocus();
        for (StationRowViews stationRow : stationRows) {
            if (stationRow.row == focusedView) {
                return true;
            }
        }
        return false;
    }

    private boolean focusActiveStation() {
        if (!isTelevisionLayout() || mediaController == null || !mediaController.isConnected()) return false;
        RadioStation current = findStationByMediaId(mediaController.getCurrentMediaItem());
        if (current == null) return false;
        int category;
        if (selectedCategoryIndex == 2 && favoriteStationIds.contains(current.id)) category = 2;
        else if (CATEGORY_LOCAL.equals(current.category)) category = 0;
        else if (CATEGORY_INTERNATIONAL.equals(current.category)) category = 1;
        else return false;
        if (selectedCategoryIndex != category) switchCategory(category, false);
        for (int i = 0; i < visibleStations.size() && i < stationRows.size(); i++) {
            if (isSameStation(visibleStations.get(i), current)) {
                View row = stationRows.get(i).row;
                if (row.hasFocus()) return false;
                return row.requestFocus();
            }
        }
        return false;
    }

    private void focusCurrentCategoryTab() {
        if (!isTelevisionLayout()) {
            return;
        }
        if (stationScroll != null) {
            stationScroll.smoothScrollTo(0, 0);
        }

        if (selectedCategoryIndex == 0) {
            localTabButton.requestFocus();
        } else if (selectedCategoryIndex == 2) {
            tvFavoritesTabButton.requestFocus();
        } else {
            internationalTabButton.requestFocus();
        }
    }

    private void updateSelection(int index) {
        if (visibleStations.isEmpty() || index < 0 || index >= visibleStations.size()) {
            return;
        }
        selectedStationIndex = index;
        RadioStation station = visibleStations.get(index);
        stationTitle.setText(station.name);
        renderTvNowPlayingSong();

        if (station.url.trim().isEmpty()) {
            statusText.setText("Waiting for stream URL");
        } else if (isSameStation(loadedStation, station) && mediaController != null && mediaController.isPlaying()) {
            statusText.setText("Playing " + station.name);
        } else if (isSameStation(loadedStation, station) && playerPrepared) {
            statusText.setText("Paused " + station.name);
        } else {
            statusText.setText("Ready to play");
        }
        updatePlaybackButton();
        updatePhoneBottomUi();
    }

    private void playSelectedStation() {
        if (visibleStations.isEmpty() || selectedStationIndex < 0 || selectedStationIndex >= visibleStations.size()) {
            return;
        }
        RadioStation station = visibleStations.get(selectedStationIndex);
        playStationDirectly(station);
    }

    private void onStationPlayPause(RadioStation station) {
        if (station == null) {
            return;
        }

        int index = indexOfStation(visibleStations, station);
        if (index >= 0) {
            selectedStationIndex = index;
            updateSelection(index);
        }

        boolean isCurrentStation = isSameStation(loadedStation, station) || isSameStation(activeStation, station);
        if (isCurrentStation && mediaController != null
                && (mediaController.isPlaying() || isTvReconnecting())) {
            pausePlayback();
            return;
        }

        if (isCurrentStation && mediaController != null && playerPrepared) {
            mediaController.play();
            activeStation = station;
            statusText.setText("Playing " + station.name);
            updateStationMarkers();
            updatePlaybackButton();
            updateScreensaverText();
            updateNowPlayingLabel();
            updatePhoneBottomUi();
            return;
        }

        playStationDirectly(station);
    }

    private void playStationDirectly(RadioStation station) {
        if (station.url.trim().isEmpty()) {
            statusText.setText("No stream URL added for " + station.name);
            Toast.makeText(this, "Add this station stream URL first.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mediaController == null) {
            statusText.setText("Connecting playback service...");
            pendingPlayStation = station;
            connectPlaybackController();
            return;
        }

        if (playerPrepared && isSameStation(loadedStation, station)) {
            mediaController.play();
            activeStation = station;
            updateStationMarkers();
            updatePlaybackButton();
            updateScreensaverText();
            updateNowPlayingLabel();
            updatePhoneBottomUi();
            statusText.setText("Playing " + station.name);
            return;
        }

        statusText.setText("Loading " + station.name + "...");
        resetTvSongIdentification();
        if (otvoreniNowPlaying != null) {
            tvMetadataStationId = null;
            otvoreniNowPlaying.setStation(null, null);
            bravoNowPlaying.setStation(null, null);
            additionalNowPlaying.setStation(null, null);
        }

        loadedStation = station;
        activeStation = station;
        playerPrepared = false;
        updateStationMarkers();
        updatePlaybackButton();
        updateScreensaverText();
        updateNowPlayingLabel();
        updatePhoneBottomUi();

        try {
            mediaController.setMediaItem(createMediaItem(station));
            syncTvNowPlayingStation();
            mediaController.prepare();
            mediaController.play();
        } catch (IllegalArgumentException | IllegalStateException error) {
            statusText.setText("Invalid stream URL for " + station.name);
        }
    }

    private void pausePlayback() {
        if (mediaController != null && mediaController.isPlaying()) {
            mediaController.pause();
            RadioStation station = loadedStation != null ? loadedStation : visibleStations.get(selectedStationIndex);
            statusText.setText("Paused " + station.name);
            updatePlaybackButton();
            updateScreensaverText();
            updateNowPlayingLabel();
            updatePhoneBottomUi();
        }
    }

    private void togglePlayback() {
        if (mediaController != null && (mediaController.isPlaying() || isTvReconnecting())) {
            pausePlayback();
        } else {
            playSelectedStation();
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        resetTvScreensaverIdle();
    }

    private void resetTvScreensaverIdle() {
        tvIdleHandler.removeCallbacks(tvIdleScreensaver);
        if (!isTelevisionLayout() || !activityResumed || screensaverActive || tvScreensaverPicker != null
                || mediaController == null || !mediaController.isConnected() || !mediaController.isPlaying()) return;
        int minutes = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(TvScreensaverPicker.AUTO_MINUTES, 0);
        if (minutes > 0) tvIdleHandler.postDelayed(tvIdleScreensaver, minutes * 60_000L);
    }

    private void showScreensaverPicker() {
        if (!isTelevisionLayout() || tvScreensaverPicker != null) return;
        tvIdleHandler.removeCallbacks(tvIdleScreensaver);
        tvScreensaverPicker = new TvScreensaverPicker(this, MODE_NAMES, screensaverMode,
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE), index -> {
                    screensaverMode = index;
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                            .putInt(PREF_SCREENSAVER_MODE, index).apply();
                    showScreensaver();
                }, () -> {
                    tvScreensaverPicker = null;
                    resetTvScreensaverIdle();
                });
        tvScreensaverPicker.show();
    }

    private void showScreensaver() {
        if (mediaController == null || !mediaController.isPlaying() || activeStation == null) {
            Toast.makeText(this, "Start a radio station first.", Toast.LENGTH_SHORT).show();
            return;
        }

        tvIdleHandler.removeCallbacks(tvIdleScreensaver);
        screensaverActive = true;
        screensaverView = new ScreensaverView(this);
        screensaverView.setMode(screensaverMode);
        updateScreensaverText();
        setContentView(screensaverView);
        screensaverView.requestFocus();
    }

    private void hideScreensaver() {
        screensaverActive = false;
        setContentView(mainContentView);
        resetTvScreensaverIdle();
        if (screensaverButton != null) {
            screensaverButton.requestFocus();
        }
    }

    private void changeScreensaverMode(int direction) {
        screensaverMode = (screensaverMode + direction + ScreensaverView.MODE_COUNT) % ScreensaverView.MODE_COUNT;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(PREF_SCREENSAVER_MODE, screensaverMode)
                .apply();
        if (screensaverView != null) {
            screensaverView.setMode(screensaverMode);
            updateScreensaverText();
        }
    }

    private void updateScreensaverText() {
        if (screensaverView == null) {
            return;
        }

        String title = activeStation != null ? activeStation.name : "Simple Radio";
        String state = isTvReconnecting() ? "Reconnecting…"
                : mediaController != null && mediaController.isPlaying() ? "Playing" : "Paused";
        screensaverView.setStationText(title, state);
    }

    private void updateStationMarkers() {
        for (int i = 0; i < stationRows.size(); i++) {
            RadioStation station = visibleStations.get(i);
            StationRowViews rowViews = stationRows.get(i);
            boolean isActive = isSameStation(station, activeStation);
            boolean isPlaying = isActive && mediaController != null && mediaController.isPlaying();
            boolean isFocused = rowViews.row.hasFocus();
            int left = rowViews.row.getPaddingLeft();
            int top = rowViews.row.getPaddingTop();
            int right = rowViews.row.getPaddingRight();
            int bottom = rowViews.row.getPaddingBottom();
            rowViews.row.setBackground(makeStationCardBackground(isActive, isFocused));
            // Replacing a background must not change the row's content alignment.
            rowViews.row.setPadding(left, top, right, bottom);
            rowViews.playIndicator.setText(isPlaying ? "Ⅱ" : "▶");
            rowViews.playIndicator.setTextColor(color(isActive ? COLOR_ACCENT : COLOR_TEXT_SECONDARY));
        }
        updatePhoneBottomUi();
    }

    private boolean isTvReconnecting() {
        if (!isTelevisionLayout() || mediaController == null || !mediaController.isConnected()
                || !mediaController.getPlayWhenReady()) return false;
        MediaItem current = mediaController.getCurrentMediaItem();
        return current != null && current.mediaId.equals(mediaController.getSessionExtras()
                .getString(RadioPlaybackService.EXTRA_RECONNECTING_STATION, ""));
    }

    private void updateNowPlayingLabel() {
        if (nowPlayingLabel == null) {
            return;
        }

        RadioStation current = mediaController == null || !mediaController.isConnected()
                ? null : findStationByMediaId(mediaController.getCurrentMediaItem());
        if (current != null && isTvReconnecting()) {
            nowPlayingLabel.setText("Reconnecting: " + current.name + "…");
            nowPlayingLabel.setVisibility(View.VISIBLE);
        } else if (current != null && mediaController.getPlayerError() != null) {
            nowPlayingLabel.setText("Unable to play: " + current.name);
            nowPlayingLabel.setVisibility(View.VISIBLE);
        } else if (activeStation != null && mediaController != null && mediaController.isPlaying()) {
            nowPlayingLabel.setText("Playing: " + activeStation.name);
            nowPlayingLabel.setVisibility(View.VISIBLE);
        } else {
            nowPlayingLabel.setVisibility(View.INVISIBLE);
        }
    }

    private void updatePlaybackButton() {
        if (playbackButton == null) {
            return;
        }

        RadioStation selectedStation = visibleStations.isEmpty() ? null : visibleStations.get(selectedStationIndex);
        if (mediaController != null && (mediaController.isPlaying() || isTvReconnecting())
                && isSameStation(loadedStation, selectedStation)) {
            playbackButton.setText("Pause");
        } else {
            playbackButton.setText("Play");
        }
    }

    private GradientDrawable makeStationCardBackground(boolean active, boolean focused) {
        GradientDrawable background = makeCardBackground(
                focused ? COLOR_CARD_FOCUSED : COLOR_CARD,
                false,
                dp(isTelevisionLayout() ? 14 : PHONE_CARD_RADIUS_DP)
        );
        int strokeColor;
        int strokeWidth;
        if (active) {
            strokeColor = color(COLOR_ACCENT);
            strokeWidth = dp(1);
        } else if (focused) {
            strokeColor = color("#66D9AE6F");
            strokeWidth = dp(2);
        } else {
            strokeColor = color("#14FFFFFF");
            strokeWidth = dp(1);
        }
        background.setStroke(strokeWidth, strokeColor);
        return background;
    }

    private GradientDrawable makeCardBackground(String fillColor, boolean focused, int radius) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(color(fillColor));
        background.setCornerRadius(radius);
        if (focused) {
            background.setStroke(dp(2), color(COLOR_ACCENT));
        }
        return background;
    }

    private void connectPlaybackController() {
        if (controllerFuture != null) {
            return;
        }

        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, RadioPlaybackService.class));
        MediaController.Builder controllerBuilder = new MediaController.Builder(this, sessionToken);
        {
            controllerBuilder.setListener(new MediaController.Listener() {
                @Override
                public void onExtrasChanged(MediaController controller, Bundle extras) {
                    if (mediaController == controller) {
                        if (isTelevisionLayout()) {
                            updateTvSongIdentification(extras);
                            updateNowPlayingLabel();
                            updatePlaybackButton();
                            updateScreensaverText();
                        }
                        syncTvNowPlayingStation();
                    }
                }

                @Override
                public void onDisconnected(MediaController controller) {
                    if (mediaController == controller && otvoreniNowPlaying != null) {
                        tvMetadataStationId = null;
                        otvoreniNowPlaying.setStation(null, null);
                        bravoNowPlaying.setStation(null, null);
                        additionalNowPlaying.setStation(null, null);
                    }
                    if (mediaController == controller && (identificationPending || identificationListening)) {
                        identificationUiGeneration++;
                        showTvIdentificationError();
                    }
                }
            });
        }
        controllerFuture = controllerBuilder.buildAsync();
        controllerFuture.addListener(() -> {
            try {
                mediaController = controllerFuture.get();
                mediaController.addListener(controllerListener);
                syncPlaybackUi(true);
                resetTvScreensaverIdle();
                updateTvSongIdentification(mediaController.getSessionExtras());
                if (pendingPlayStation != null) {
                    RadioStation station = pendingPlayStation;
                    pendingPlayStation = null;
                    showStationInCurrentUi(station);
                    playSelectedStation();
                }
            } catch (ExecutionException | InterruptedException error) {
                if (statusText != null) {
                    statusText.setText("Playback service unavailable");
                }
                if (error instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }, getMainExecutor());
    }

    private void releasePlaybackController() {
        if (mediaController != null) {
            mediaController.removeListener(controllerListener);
            mediaController = null;
        }
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
        }
    }

    private MediaItem createMediaItem(RadioStation station) {
        MediaMetadata metadata = new MediaMetadata.Builder()
                .setTitle(station.name)
                .setArtist("Simple Radio")
                .setArtworkUri(Uri.parse("android.resource://" + getPackageName() + "/" + R.drawable.radio_background))
                .build();
        return new MediaItem.Builder()
                .setMediaId(station.id)
                .setUri(station.url)
                .setMediaMetadata(metadata)
                .build();
    }

    private void syncPlaybackUi(boolean selectCurrentStation) {
        if (mediaController == null) {
            return;
        }

        syncTvNowPlayingStation();

        RadioStation sessionStation = findStationByMediaId(mediaController.getCurrentMediaItem());
        if (sessionStation != null) {
            loadedStation = sessionStation;
            activeStation = sessionStation;
            if (selectCurrentStation) {
                showStationInCurrentUi(sessionStation);
            }
        }

        playerPrepared = mediaController.getPlaybackState() == Player.STATE_READY
                || mediaController.getPlaybackState() == Player.STATE_BUFFERING;

        if (mediaController.getPlaybackState() == Player.STATE_BUFFERING && loadedStation != null) {
            statusText.setText("Loading " + loadedStation.name + "...");
        } else if (loadedStation != null && mediaController.isPlaying()) {
            statusText.setText("Playing " + loadedStation.name);
        } else if (loadedStation != null && playerPrepared) {
            statusText.setText("Paused " + loadedStation.name);
        } else if (getSelectedStation() != null) {
            updateSelection(selectedStationIndex);
        }

        updateStationMarkers();
        updatePlaybackButton();
        updateScreensaverText();
        updateNowPlayingLabel();
        updatePhoneBottomUi();
    }

    private void syncTvNowPlayingStation() {
        if (otvoreniNowPlaying == null) return;
        RadioStation current = mediaController == null || !mediaController.isConnected()
                ? null : findStationByMediaId(mediaController.getCurrentMediaItem());
        String stream = null;
        if (current != null && !current.custom) {
            switch (current.name) {
                case "Otvoreni": stream = "otvoreni"; break;
                case "Otvoreni Fitness": stream = "fitness"; break;
                case "Otvoreni Chill": stream = "chill"; break;
                case "Otvoreni Hot": stream = "hot"; break;
                case "Otvoreni Love": stream = "love"; break;
            }
        }
        tvMetadataStationId = current == null ? null : current.id;
        otvoreniNowPlaying.setStation(tvMetadataStationId, stream);
        bravoNowPlaying.setStation(tvMetadataStationId,
                current != null && !current.custom ? current.name : null);
        additionalNowPlaying.setStation(tvMetadataStationId,
                current != null && !current.custom ? current.name : null);
        boolean metadataActive = activityResumed
                && (tvNowPlayingSong == null || tvNowPlayingSong.isAttachedToWindow());
        otvoreniNowPlaying.setActive(metadataActive);
        bravoNowPlaying.setActive(metadataActive);
        additionalNowPlaying.setActive(metadataActive);
        if (current != null && !current.custom && "Extra FM".equals(current.name)
                && metadataActive) {
            Bundle extras = mediaController.getSessionExtras();
            // Session metadata is tagged with its originating station, never the focused row.
            tvMetadataSong = current.id.equals(extras.getString(RadioPlaybackService.EXTRA_ICY_STATION))
                    ? extras.getString(RadioPlaybackService.EXTRA_ICY_SONG, "") : "";
        }
        renderTvNowPlayingSong();
    }

    private TextView makePhoneSongText(int size) {
        TextView text = new TextView(this);
        text.setTextSize(size);
        text.setTextColor(color(COLOR_TEXT_SECONDARY));
        text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.setIncludeFontPadding(false);
        text.setVisibility(View.GONE);
        return text;
    }

    private void renderPhoneNowPlayingSong() {
        if (phoneMiniSong == null) return;
        MediaItem current = mediaController == null || !mediaController.isConnected()
                ? null : mediaController.getCurrentMediaItem();
        for (TextView text : new TextView[] {phoneMiniSong, phoneFullSong}) {
            if (text == null) continue;
            boolean visible = current != null && current.mediaId.equals(tvMetadataStationId)
                    && current.mediaId.equals(text.getTag()) && !tvMetadataSong.isEmpty();
            text.setText(visible ? tvMetadataSong : "");
            text.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void renderTvNowPlayingSong() {
        if (tvNowPlayingSong == null) {
            renderPhoneNowPlayingSong();
            return;
        }
        RadioStation selected = getSelectedStation();
        MediaItem current = mediaController == null ? null : mediaController.getCurrentMediaItem();
        // The existing station title previews focused rows. Never put the playing station's
        // song under a different station's preview, or display a late previous-station result.
        boolean visible = current != null && selected != null && !tvMetadataSong.isEmpty()
                && current.mediaId.equals(tvMetadataStationId) && selected.id.equals(current.mediaId);
        tvNowPlayingSong.setText(visible ? tvMetadataSong : "");
        tvNowPlayingSong.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private RadioStation findStationByMediaId(MediaItem mediaItem) {
        if (mediaItem == null || mediaItem.mediaId == null || mediaItem.mediaId.trim().isEmpty()) {
            return null;
        }

        for (RadioStation station : allStations) {
            if (station.id.equals(mediaItem.mediaId)) {
                return station;
            }
        }
        return null;
    }

    private RadioStation getSelectedStation() {
        if (visibleStations.isEmpty() || selectedStationIndex < 0 || selectedStationIndex >= visibleStations.size()) {
            return null;
        }
        return visibleStations.get(selectedStationIndex);
    }

    private void showStationInCurrentUi(RadioStation station) {
        if (!isTelevisionLayout() && selectedPhoneTab != 0) {
            return;
        }
        int categoryIndex = isTelevisionLayout() && selectedCategoryIndex == 2
                && favoriteStationIds.contains(station.id) ? 2 : CATEGORY_LOCAL.equals(station.category) ? 0 : 1;
        if (selectedCategoryIndex != categoryIndex) {
            switchCategory(categoryIndex, false);
        }

        int stationIndex = visibleStations.indexOf(station);
        if (stationIndex >= 0) {
            selectedStationIndex = stationIndex;
            updateSelection(stationIndex);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private boolean isTelevisionLayout() {
        int modeType = getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK;
        return modeType == Configuration.UI_MODE_TYPE_TELEVISION
                || getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    private int color(String value) {
        return Color.parseColor(value);
    }

    private static final String[] MODE_NAMES = {
            "Bars", "Waves", "Orbit", "Glow", "Pulse", "Rain", "Grid", "Radar", "Ribbon", "Stars",
            "Christmas Snow", "Christmas Fireplace", "Starfield", "Aurora", "Rainy Window", "Ocean",
            "Digital Rain", "Retro Equalizer", "Christmas Tree", "Christmas Snow", "Christmas Lights",
            "Cozy Fireplace", "Snow Globe", "Winter Cabin", "Galaxy", "Northern Lights",
            "Floating Bubbles", "Fireflies", "Neon Waves", "Lava Lamp", "Starfield", "Retro Gold Equalizer",
            "Fireworks", "Meteor Shower", "Floating Lanterns", "Underwater", "Plasma", "Vinyl",
            "City Lights", "Fireflies", "Geometric Tunnel", "Analog Clock", "Jellyfish",
            "Butterflies at Night", "Moon & Clouds", "Saturn / Planets", "Comets", "Constellations",
            "Floating Dust", "Bokeh Lights", "Koi Pond", "Ripples", "Spiral Galaxy", "Hypnotic Rings",
            "Hexagon Flow", "Neon Grid Horizon", "Hourglass", "Pendulum", "Solar System",
            "Snowy Mountains", "Campfire", "Thunderstorm", "Falling Leaves",
            "Minimal Digital Clock", "Analog Gold Clock", "Flip Clock", "Dot Matrix Clock",
            "Neon Clock", "Orbit Clock", "Word Clock", "Ambient Clock", "Haunted Night",
            "Pumpkin Graveyard", "Ghostly Mansion", "Witching Hour", "Skeleton Party", "Pumpkin King", "Happy Birthday Ana", "Christmas Village", "Christmas Village Animated"
    };

    private class ScreensaverView extends View {
        static final int MODE_COUNT = 80;
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint statusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF barBounds = new RectF();
        private android.graphics.Bitmap villageBackground;
        private final Paint villagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Path villagePath = new Path();
        private final RectF villageBounds = new RectF();
        private final Runnable villageFrame = () -> {
            if (isAttachedToWindow() && this.mode == 78) invalidate();
        };
        private android.graphics.Bitmap animatedVillageBackground;
        private final Paint animatedVillagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final float[] animatedTreeLights = {
                842,222, 863,249, 819,274, 878,280, 829,311, 889,328,
                790,350, 854,355, 912,380, 778,398, 830,418, 879,429,
                938,451, 761,473, 809,490, 870,503, 926,525, 974,548, 734,546, 842,549};
        private final float[] animatedLamps = {277,613, 540,711, 763,729, 1124,709, 1480,614, 1070,488};
        private final float[] animatedWindows = {255,286, 299,296, 548,330, 599,341, 1070,315, 1108,316, 1241,317};
        private final float[] animatedChimneys = {638,220, 1020,255, 1290,174};
        private final Runnable animatedVillageFrame = () -> {
            if (isAttachedToWindow() && this.mode == 79) invalidate();
        };
        private final Paint birthdayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint birthdayLettering = makeBirthdayLettering();
        private final Path birthdayPath = new Path();
        private long birthdayStartedAt;
        private final Paint hauntedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path hauntedPath = new Path();
        private final android.graphics.Shader hauntedMist = new android.graphics.RadialGradient(
                0, 0, 1, 0xFFC1CBD7, 0x00C1CBD7, android.graphics.Shader.TileMode.CLAMP);
        private final android.graphics.Shader hauntedWarmth = new android.graphics.RadialGradient(
                0, 0, 1, 0xFFFFAB42, 0x00FFAB42, android.graphics.Shader.TileMode.CLAMP);
        private final long startedAt = SystemClock.uptimeMillis();
        private long textShownAt = SystemClock.uptimeMillis();
        private int mode = 0;
        private String title = "Simple Radio";
        private String state = "Playing";

        ScreensaverView(Activity activity) {
            super(activity);
            setFocusable(true);
            backgroundPaint.setColor(color("#080B0E"));
            circlePaint.setStyle(Paint.Style.FILL);
            barPaint.setStyle(Paint.Style.FILL);
            titlePaint.setColor(color("#F3F6F8"));
            titlePaint.setTextAlign(Paint.Align.CENTER);
            titlePaint.setFakeBoldText(true);
            statusPaint.setColor(color("#A9B4BC"));
            statusPaint.setTextAlign(Paint.Align.CENTER);
        }

        void setStationText(String title, String state) {
            this.title = title;
            this.state = state;
            invalidate();
        }

        void setMode(int mode) {
            removeCallbacks(villageFrame);
            removeCallbacks(animatedVillageFrame);
            this.mode = mode;
            if (mode == 78) prepareChristmasVillage();
            else villageBackground = null;
            if (mode == 79) prepareAnimatedVillage();
            else animatedVillageBackground = null;
            if (mode == 77) birthdayStartedAt = SystemClock.uptimeMillis();
            textShownAt = SystemClock.uptimeMillis();
            invalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (mode == 78) prepareChristmasVillage();
            if (mode == 79) prepareAnimatedVillage();
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(villageFrame);
            villageBackground = null;
            removeCallbacks(animatedVillageFrame);
            animatedVillageBackground = null;
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int width = getWidth();
            int height = getHeight();
            long now = SystemClock.uptimeMillis();
            float time = now / 1000f;

            canvas.drawRect(0, 0, width, height, backgroundPaint);
            if (mode == 0) {
                drawMovingCircles(canvas, width, height, time);
                drawBars(canvas, width, height, time);
            } else if (mode == 1) {
                drawWaves(canvas, width, height, time);
            } else if (mode == 2) {
                drawOrbit(canvas, width, height, time);
            } else if (mode == 3) {
                drawGlow(canvas, width, height, time);
            } else if (mode == 4) {
                drawPulse(canvas, width, height, time);
            } else if (mode == 5) {
                drawRain(canvas, width, height, time);
            } else if (mode == 6) {
                drawGrid(canvas, width, height, time);
            } else if (mode == 7) {
                drawRadar(canvas, width, height, time);
            } else if (mode == 8) {
                drawRibbon(canvas, width, height, time);
            } else if (mode == 9) {
                drawStars(canvas, width, height, time);
            } else if (mode == 10) {
                drawChristmasSnow(canvas, width, height, time);
            } else if (mode == 11) {
                drawChristmasFireplace(canvas, width, height, time);
            } else if (mode == 12) {
                drawStarfield(canvas, width, height, time);
            } else if (mode == 13) {
                drawAurora(canvas, width, height, time);
            } else if (mode == 14) {
                drawRainyWindow(canvas, width, height, time);
            } else if (mode == 15) {
                drawOcean(canvas, width, height, time);
            } else if (mode == 16) {
                drawDigitalRain(canvas, width, height, time);
            } else if (mode == 17) {
                drawRetroEqualizer(canvas, width, height, time);
            } else if (mode == 18) {
                drawChristmasTree(canvas, width, height, time);
            } else if (mode == 19) {
                drawChristmasSnowLandscape(canvas, width, height, time);
            } else if (mode == 20) {
                drawChristmasLights(canvas, width, height, time);
            } else if (mode == 21) {
                drawCozyFireplace(canvas, width, height, time);
            } else if (mode == 22) {
                drawSnowGlobe(canvas, width, height, time);
            } else if (mode == 23) {
                drawWinterCabin(canvas, width, height, time);
            } else if (mode == 24) {
                drawGalaxy(canvas, width, height, time);
            } else if (mode == 25) {
                drawNorthernLights(canvas, width, height, time);
            } else if (mode == 26) {
                drawFloatingBubbles(canvas, width, height, time);
            } else if (mode == 27) {
                drawFireflies(canvas, width, height, time);
            } else if (mode == 28) {
                drawNeonWaves(canvas, width, height, time);
            } else if (mode == 29) {
                drawLavaLamp(canvas, width, height, time);
            } else if (mode == 30) {
                drawDeepStarfield(canvas, width, height, time);
            } else if (mode == 31) {
                drawRetroGoldEqualizer(canvas, width, height, time);
            } else if (mode == 32) {
                drawFireworks(canvas, width, height, time);
            } else if (mode == 33) {
                drawMeteorShower(canvas, width, height, time);
            } else if (mode == 34) {
                drawFloatingLanterns(canvas, width, height, time);
            } else if (mode == 35) {
                drawUnderwater(canvas, width, height, time);
            } else if (mode == 36) {
                drawPlasma(canvas, width, height, time);
            } else if (mode == 37) {
                drawVinyl(canvas, width, height, time);
            } else if (mode == 38) {
                drawCityLights(canvas, width, height, time);
            } else if (mode == 39) {
                drawForestFireflies(canvas, width, height, time);
            } else if (mode == 40) {
                drawGeometricTunnel(canvas, width, height, time);
            } else if (mode == 41) {
                drawAnalogClock(canvas, width, height, time);
            } else if (mode == 42) {
                drawJellyfish(canvas, width, height, time);
            } else if (mode == 43) {
                drawButterfliesAtNight(canvas, width, height, time);
            } else if (mode == 44) {
                drawMoonAndClouds(canvas, width, height, time);
            } else if (mode == 45) {
                drawSaturnPlanets(canvas, width, height, time);
            } else if (mode == 46) {
                drawComets(canvas, width, height, time);
            } else if (mode == 47) {
                drawConstellations(canvas, width, height, time);
            } else if (mode == 48) {
                drawFloatingDust(canvas, width, height, time);
            } else if (mode == 49) {
                drawBokehLights(canvas, width, height, time);
            } else if (mode == 50) {
                drawKoiPond(canvas, width, height, time);
            } else if (mode == 51) {
                drawRipples(canvas, width, height, time);
            } else if (mode == 52) {
                drawSpiralGalaxy(canvas, width, height, time);
            } else if (mode == 53) {
                drawHypnoticRings(canvas, width, height, time);
            } else if (mode == 54) {
                drawHexagonFlow(canvas, width, height, time);
            } else if (mode == 55) {
                drawNeonGridHorizon(canvas, width, height, time);
            } else if (mode == 56) {
                drawHourglass(canvas, width, height, time);
            } else if (mode == 57) {
                drawPendulum(canvas, width, height, time);
            } else if (mode == 58) {
                drawSolarSystem(canvas, width, height, time);
            } else if (mode == 59) {
                drawSnowyMountains(canvas, width, height, time);
            } else if (mode == 60) {
                drawCampfire(canvas, width, height, time);
            } else if (mode == 61) {
                drawThunderstorm(canvas, width, height, time);
            } else if (mode == 62) {
                drawFallingLeaves(canvas, width, height, time);
            } else if (mode == 63) {
                drawMinimalDigitalClock(canvas, width, height, time);
            } else if (mode == 64) {
                drawAnalogGoldClock(canvas, width, height, time);
            } else if (mode == 65) {
                drawFlipClock(canvas, width, height, time);
            } else if (mode == 66) {
                drawDotMatrixClock(canvas, width, height, time);
            } else if (mode == 67) {
                drawNeonClock(canvas, width, height, time);
            } else if (mode == 68) {
                drawOrbitClock(canvas, width, height, time);
            } else if (mode == 69) {
                drawWordClock(canvas, width, height, time);
            } else if (mode == 79) {
                drawAnimatedVillage(canvas, width, height, (now - startedAt) / 1000f);
            } else if (mode == 78) {
                drawChristmasVillage(canvas, width, height, (now - startedAt) / 1000f);
            } else if (mode == 77) {
                drawBirthdayAna(canvas, width, height, (now - birthdayStartedAt) / 1000f);
            } else if (mode == 76) {
                drawPumpkinKing(canvas, width, height, (now - startedAt) / 1000f);
            } else if (mode >= 72 && mode <= 75) {
                drawHalloweenScene(canvas, width, height, (now - startedAt) / 1000f, mode);
            } else if (mode == 71) {
                drawHauntedNight(canvas, width, height, (now - startedAt) / 1000f);
            } else {
                drawAmbientClock(canvas, width, height, time);
            }
            drawText(canvas, width, height, now);

            if (mode == 79) {
                removeCallbacks(animatedVillageFrame);
                if (isAttachedToWindow()) postDelayed(animatedVillageFrame, 33);
            } else if (mode == 78) {
                removeCallbacks(villageFrame);
                if (isAttachedToWindow()) postDelayed(villageFrame, 33);
            } else {
                postInvalidateDelayed(33);
            }
        }

        // All coordinates use a fixed scene space; no per-frame bitmaps, shaders or objects.
        private void hauntedGlow(Canvas canvas, float x, float y, float rx, float ry,
                int alpha, boolean warm) {
            canvas.save();
            canvas.translate(x, y);
            canvas.scale(rx, ry);
            hauntedPaint.setShader(warm ? hauntedWarmth : hauntedMist);
            hauntedPaint.setAlpha(alpha);
            canvas.drawCircle(0, 0, 1, hauntedPaint);
            hauntedPaint.setShader(null);
            hauntedPaint.setAlpha(255);
            canvas.restore();
        }

        private void drawHauntedNight(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(0xFF070A13);
            canvas.save();
            canvas.scale(width / 1000f, height / 600f);
            for (int i = 0; i < 35; i++) {
                float x = (i * 137.3f + time * 0.16f) % 1000;
                float y = 24 + (i * 73 % 290);
                hauntedPaint.setColor(0xFFBBC6D6);
                hauntedPaint.setAlpha(40 + (int) (18 * Math.sin(time * 0.17f + i * 2.7f)));
                canvas.drawCircle(x, y, i % 4 == 0 ? 1.1f : 0.65f, hauntedPaint);
            }
            hauntedGlow(canvas, 600, 205, 190, 190, 60, false);
            hauntedGlow(canvas, 600, 205, 135, 135, 70, false);
            hauntedPaint.setColor(0xFFD4D7D4);
            canvas.drawCircle(600, 205, 91, hauntedPaint);
            // Faint lunar surface shading, softened with the same cached gradient.
            hauntedGlow(canvas, 572, 175, 42, 28, 35, false);
            hauntedGlow(canvas, 623, 231, 31, 23, 40, false);
            for (int i = 0; i < 4; i++) {
                float x = ((time * (1.4f + i * 0.24f) + i * 319) % 1500) - 250;
                hauntedGlow(canvas, x, 142 + i * 39, 240, 24, 22, false);
            }

            hauntedPaint.setColor(0xFF04070C);
            hauntedPath.rewind();
            hauntedPath.moveTo(0, 487);
            hauntedPath.cubicTo(220, 480, 320, 438, 480, 450);
            hauntedPath.cubicTo(700, 449, 820, 494, 1000, 470);
            hauntedPath.lineTo(1000, 600);
            hauntedPath.lineTo(0, 600);
            hauntedPath.close();
            canvas.drawPath(hauntedPath, hauntedPaint);
            // Asymmetric steep gables, a narrow turret, and a crooked chimney.
            hauntedPath.rewind();
            hauntedPath.moveTo(390, 465);
            hauntedPath.lineTo(390, 345);
            hauntedPath.lineTo(363, 350);
            hauntedPath.lineTo(431, 266);
            hauntedPath.lineTo(478, 322);
            hauntedPath.lineTo(478, 277);
            hauntedPath.lineTo(463, 279);
            hauntedPath.lineTo(507, 205);
            hauntedPath.lineTo(550, 279);
            hauntedPath.lineTo(535, 277);
            hauntedPath.lineTo(535, 334);
            hauntedPath.lineTo(565, 304);
            hauntedPath.lineTo(579, 319);
            hauntedPath.lineTo(575, 284);
            hauntedPath.lineTo(591, 281);
            hauntedPath.lineTo(595, 336);
            hauntedPath.lineTo(623, 366);
            hauntedPath.lineTo(602, 362);
            hauntedPath.lineTo(602, 467);
            hauntedPath.close();
            canvas.drawPath(hauntedPath, hauntedPaint);
            for (int i = 0; i < 5; i++) {
                float x = i == 0 ? 502 : 412 + (i - 1) * 48;
                float y = i == 0 ? 284 : 370 + (i % 2) * 8;
                hauntedPaint.setColor(0xFF8B632F);
                hauntedPaint.setAlpha(85 + (int) (12 * Math.sin(time * 0.37f + i)));
                canvas.drawRect(x, y, x + 10, y + 24, hauntedPaint);
                hauntedPaint.setColor(0xFF04070C);
                canvas.drawRect(x + 4, y, x + 6, y + 24, hauntedPaint);
                canvas.drawRect(x, y + 10, x + 10, y + 13, hauntedPaint);
            }

            // Separate long cycles leave quiet sky between small flocks.
            for (int flock = 0; flock < 2; flock++) {
                float phase = (time + flock * 31) % (flock == 0 ? 73 : 109);
                if (phase < 16 || phase > 35) continue;
                float progress = (phase - 16) / 19;
                for (int i = 0; i < 3; i++) {
                    float x = -70 + progress * 1160 - i * 24;
                    float y = 166 + flock * 65 + i * 12 + (float) Math.sin(time * 0.5f + i) * 10;
                    float wing = (float) Math.sin(time * (3.2f + i * 0.13f) + i) * 6;
                    hauntedPaint.setColor(0xFF080B12);
                    hauntedPath.rewind();
                    hauntedPath.moveTo(x, y + 3);
                    hauntedPath.quadTo(x - 6, y - 6, x - 14, y - 5 - wing);
                    hauntedPath.quadTo(x - 8, y + 4, x - 3, y + 3);
                    hauntedPath.lineTo(x, y + 6);
                    hauntedPath.lineTo(x + 3, y + 3);
                    hauntedPath.quadTo(x + 8, y + 4, x + 14, y - 5 - wing);
                    hauntedPath.quadTo(x + 6, y - 6, x, y + 3);
                    canvas.drawPath(hauntedPath, hauntedPaint);
                }
            }

            float ghostPhase = time % 173;
            if (ghostPhase > 81 && ghostPhase < 109) {
                float p = (ghostPhase - 81) / 28;
                float x = 690 + p * 138;
                float y = 393 + (float) Math.sin(p * 4) * 12;
                int alpha = (int) (25 * Math.sin(p * Math.PI));
                hauntedGlow(canvas, x, y + 18, 36, 60, alpha, false);
                hauntedPaint.setColor(0xFFC3CED6);
                hauntedPaint.setAlpha(alpha);
                hauntedPath.rewind();
                hauntedPath.moveTo(x - 13, y + 40);
                hauntedPath.cubicTo(x - 13, y + 8, x - 18, y - 16, x, y - 18);
                hauntedPath.cubicTo(x + 22, y - 15, x + 12, y + 14, x + 19, y + 40);
                hauntedPath.quadTo(x + 7, y + 28, x, y + 39);
                hauntedPath.quadTo(x - 7, y + 31, x - 13, y + 40);
                canvas.drawPath(hauntedPath, hauntedPaint);
            }

            for (int i = 0; i < 3; i++) {
                float x = i == 0 ? 297 : i == 1 ? 340 : 663;
                float y = i == 0 ? 501 : i == 1 ? 515 : 499;
                float r = i == 1 ? 17 : 23;
                float flicker = 0.8f + 0.09f * (float) Math.sin(time * 1.1f + i * 2.3f)
                        + 0.06f * (float) Math.sin(time * 1.73f + i * 4.1f);
                hauntedGlow(canvas, x, y + 5, r * 3, r * 1.7f, (int) (50 * flicker), true);
                hauntedPaint.setColor(0xFF472414);
                canvas.drawOval(x - r, y - r * 0.8f, x + r, y + r * 0.8f, hauntedPaint);
                hauntedPaint.setColor(0xFF61331A);
                canvas.drawOval(x - r * 0.55f, y - r * 0.8f, x + r * 0.55f, y + r * 0.8f, hauntedPaint);
                hauntedPaint.setColor(0xFF1C2018);
                canvas.drawRect(x - 2, y - r - 4, x + 2, y - r * 0.7f, hauntedPaint);
                hauntedPaint.setColor(0xFFFFB654);
                hauntedPaint.setAlpha((int) (220 * flicker));
                hauntedPath.rewind();
                hauntedPath.moveTo(x - r * 0.65f, y - 2);
                hauntedPath.lineTo(x - r * 0.22f, y - 7);
                hauntedPath.lineTo(x - r * 0.2f, y + 1);
                hauntedPath.close();
                hauntedPath.moveTo(x + r * 0.65f, y - 2);
                hauntedPath.lineTo(x + r * 0.22f, y - 7);
                hauntedPath.lineTo(x + r * 0.2f, y + 1);
                hauntedPath.close();
                hauntedPath.moveTo(x - r * 0.6f, y + 5);
                hauntedPath.lineTo(x - r * 0.18f, y + 8);
                hauntedPath.lineTo(x, y + 5);
                hauntedPath.lineTo(x + r * 0.22f, y + 8);
                hauntedPath.lineTo(x + r * 0.6f, y + 5);
                hauntedPath.quadTo(x, y + r * 0.8f, x - r * 0.6f, y + 5);
                canvas.drawPath(hauntedPath, hauntedPaint);
            }
            for (int i = 0; i < 9; i++) {
                float x = ((time * (2.1f + i * 0.27f) + i * 193) % 1600) - 300;
                float y = 480 + (i % 3) * 27 + (float) Math.sin(time * 0.12f + i) * 8;
                hauntedGlow(canvas, x, y, 270, 30 + i % 3 * 8, 25, false);
            }
            hauntedPaint.setAlpha(255);
            canvas.restore();
        }

        // Shared only by the four new modes; existing scenes keep their original drawing paths.
        private void drawHalloweenScene(Canvas c, int width, int height, float t, int scene) {
            c.drawColor(0xFF060910);
            c.save();
            c.scale(width / 1000f, height / 600f);
            c.translate((float) Math.sin(t * 0.017f) * 5, (float) Math.cos(t * 0.021f) * 3);
            hauntedPaint.setStyle(Paint.Style.FILL);
            hauntedPaint.setShader(null);
            float moonX = scene == 74 ? 510 : scene == 73 ? 645 : 730;
            float moonY = scene == 74 ? 225 : 155;
            float radius = scene == 74 ? 140 : 78;
            for (int i = 0; i < 30; i++) {
                hauntedPaint.setColor(0xFFADBACB);
                hauntedPaint.setAlpha(35 + (int) (12 * Math.sin(t * 0.13f + i * 2.3f)));
                c.drawCircle((i * 137 + t * 0.12f) % 1000, 25 + i * 61 % 320, 0.9f, hauntedPaint);
            }
            hauntedGlow(c, moonX, moonY, radius * 1.9f, radius * 1.9f, 48, false);
            hauntedPaint.setColor(0xFFC2C8CD);
            hauntedPaint.setAlpha(155 + (int) (15 * Math.sin(t * 0.061f)));
            c.drawCircle(moonX, moonY, radius, hauntedPaint);
            // Broad translucent cloud silhouettes drift completely offscreen before wrapping.
            for (int i = 0; i < 5; i++) {
                float x = (t * (1.7f + i * 0.31f) + i * 337) % 1800 - 400;
                hauntedPaint.setColor(0xFF111725);
                hauntedPaint.setAlpha(105);
                c.drawOval(x - 220, 95 + i * 37, x + 220, 125 + i * 37, hauntedPaint);
                hauntedGlow(c, x, 119 + i * 37, 260, 28, 16, false);
            }
            if (scene == 72) drawPumpkinGraveyard(c, t);
            else if (scene == 73) drawGhostlyMansion(c, t);
            else if (scene == 74) drawWitchingHour(c, t);
            else drawSkeletonParty(c, t);
            hauntedPaint.setStyle(Paint.Style.FILL);
            hauntedPaint.setStrokeCap(Paint.Cap.BUTT);
            hauntedPaint.setAlpha(255);
            c.restore();
        }

        private void halloweenFog(Canvas c, float t, int alpha) {
            for (int i = 0; i < 8; i++) {
                float x = (t * (2 + i * 0.29f) + i * 233) % 1700 - 350;
                hauntedGlow(c, x, 485 + i % 3 * 25 + (float) Math.sin(t * 0.14f + i) * 7,
                        290, 32 + i % 3 * 7, alpha, false);
            }
        }

        private void halloweenTree(Canvas c, float x, float y, float scale, boolean mirror) {
            c.save();
            c.translate(x, y);
            c.scale(mirror ? -scale : scale, scale);
            hauntedPaint.setColor(0xFF03060A);
            hauntedPaint.setStyle(Paint.Style.STROKE);
            hauntedPaint.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 4; i++) {
                float h = -i * 49;
                hauntedPaint.setStrokeWidth(16 - i * 3);
                c.drawLine(i * 6, h, (i + 1) * 6, h - 55, hauntedPaint);
                hauntedPaint.setStrokeWidth(5 - i);
                float direction = i % 2 == 0 ? -1 : 1;
                c.drawLine(i * 6, h - 25, i * 6 + direction * 55, h - 67, hauntedPaint);
                c.drawLine(i * 6 + direction * 55, h - 67, i * 6 + direction * 67, h - 108, hauntedPaint);
                c.drawLine(i * 6 + direction * 35, h - 52, i * 6 + direction * 77, h - 58, hauntedPaint);
            }
            hauntedPaint.setStyle(Paint.Style.FILL);
            hauntedPaint.setStrokeCap(Paint.Cap.BUTT);
            c.restore();
        }

        private void halloweenPumpkin(Canvas c, float x, float y, float r, float t, int seed) {
            float light = 0.78f + 0.10f * (float) Math.sin(t * 0.81f + seed * 2.7f)
                    + 0.06f * (float) Math.sin(t * 1.37f + seed * 4.3f);
            hauntedGlow(c, x, y + 4, r * 3, r * 1.8f, (int) (45 * light), true);
            hauntedPaint.setColor(0xFF492516);
            c.drawOval(x - r, y - r * 0.8f, x + r, y + r * 0.8f, hauntedPaint);
            hauntedPaint.setColor(0xFF63361C);
            c.drawOval(x - r * 0.5f, y - r * 0.8f, x + r * 0.5f, y + r * 0.8f, hauntedPaint);
            hauntedPaint.setColor(0xFF202419);
            c.drawRect(x - 2, y - r - 3, x + 2, y - r * 0.65f, hauntedPaint);
            hauntedPaint.setColor(0xFFFFB75D);
            hauntedPaint.setAlpha((int) (205 * light));
            hauntedPath.rewind();
            for (int side = -1; side <= 1; side += 2) {
                hauntedPath.moveTo(x + side * r * 0.7f, y - r * 0.1f);
                hauntedPath.lineTo(x + side * r * 0.22f, y - r * 0.38f);
                hauntedPath.lineTo(x + side * r * 0.2f, y + r * 0.03f);
                hauntedPath.close();
            }
            hauntedPath.moveTo(x - r * 0.6f, y + r * 0.22f);
            hauntedPath.lineTo(x - r * 0.15f, y + r * 0.4f);
            hauntedPath.lineTo(x, y + r * 0.23f);
            hauntedPath.lineTo(x + r * 0.18f, y + r * 0.4f);
            hauntedPath.lineTo(x + r * 0.6f, y + r * 0.22f);
            hauntedPath.quadTo(x, y + r * 0.9f, x - r * 0.6f, y + r * 0.22f);
            c.drawPath(hauntedPath, hauntedPaint);
        }

        private void halloweenGhost(Canvas c, float x, float y, float size, int alpha) {
            hauntedPaint.setColor(0xFFD3DCE0);
            hauntedPaint.setAlpha(alpha);
            hauntedPath.rewind();
            hauntedPath.moveTo(x - size, y + size * 2);
            hauntedPath.cubicTo(x - size, y, x - size, y - size, x, y - size);
            hauntedPath.cubicTo(x + size, y - size, x + size, y, x + size, y + size * 2);
            hauntedPath.quadTo(x + size * 0.3f, y + size, x, y + size * 1.8f);
            hauntedPath.quadTo(x - size * 0.4f, y + size, x - size, y + size * 2);
            c.drawPath(hauntedPath, hauntedPaint);
        }

        private void halloweenBats(Canvas c, float t, float period, float offset) {
            float phase = (t + offset) % period;
            if (phase < 22 || phase > 44) return;
            for (int i = 0; i < 3; i++) {
                float x = -60 + (phase - 22) / 22 * 1180 - i * 24;
                float y = 150 + i * 16 + (float) Math.sin(t * 0.34f + i) * 13;
                float wing = (float) Math.sin(t * 2.8f + i * 1.9f) * 7;
                hauntedPaint.setColor(0xFF050810);
                hauntedPath.rewind();
                hauntedPath.moveTo(x, y + 4);
                hauntedPath.quadTo(x - 8, y - 8, x - 17, y - wing);
                hauntedPath.lineTo(x - 7, y + 4);
                hauntedPath.lineTo(x, y + 7);
                hauntedPath.lineTo(x + 7, y + 4);
                hauntedPath.lineTo(x + 17, y - wing);
                hauntedPath.quadTo(x + 8, y - 8, x, y + 4);
                c.drawPath(hauntedPath, hauntedPaint);
            }
        }

        private void drawPumpkinGraveyard(Canvas c, float t) {
            halloweenTree(c, 110, 494, 1.2f, false);
            halloweenTree(c, 907, 495, 1.05f, true);
            hauntedPaint.setColor(0xFF10131B);
            hauntedPaint.setStrokeWidth(3);
            for (int i = 0; i < 25; i++) {
                float x = i * 45;
                float top = 387 + (float) Math.sin(i * 1.7f) * 10;
                c.drawLine(x, 485, x + i % 3 * 3 - 3, top, hauntedPaint);
                c.drawLine(x, top, x - 4, top + 10, hauntedPaint);
                c.drawLine(x, top, x + 4, top + 10, hauntedPaint);
            }
            c.drawLine(0, 425, 1000, 435, hauntedPaint);
            c.drawLine(0, 456, 1000, 447, hauntedPaint);
            float phase = t % 137;
            if (phase > 55 && phase < 80) {
                float p = (phase - 55) / 25;
                halloweenGhost(c, 487 + (float) Math.sin(p * 3) * 12, 435 - p * 85,
                        14, (int) (37 * Math.sin(p * Math.PI)));
            }
            for (int i = 0; i < 7; i++) {
                float x = 200 + i * 100;
                float y = 470 + i % 3 * 17;
                c.save();
                c.rotate(i % 2 == 0 ? -7 : 5, x, y);
                hauntedPaint.setColor(i % 2 == 0 ? 0xFF161B24 : 0xFF10151E);
                c.drawRoundRect(x - 25, y - 80, x + 25, y + 8, 20, 20, hauntedPaint);
                hauntedPaint.setColor(0xFF080B11);
                c.drawRect(x - 2, y - 64, x + 2, y - 31, hauntedPaint);
                c.drawRect(x - 12, y - 54, x + 12, y - 50, hauntedPaint);
                c.restore();
                if (i % 2 == 0) halloweenPumpkin(c, x - 33, y + 9, 18 + i, t, i);
            }
            halloweenFog(c, t, 28);
        }

        private void drawGhostlyMansion(Canvas c, float t) {
            hauntedPaint.setColor(0xFF05070D);
            c.drawRect(280, 310, 730, 505, hauntedPaint);
            for (int i = 0; i < 3; i++) {
                float x = 300 + i * 190;
                float peak = i == 1 ? 200 : 245;
                c.drawRect(x - 38, peak + 70, x + 38, 505, hauntedPaint);
                hauntedPath.rewind();
                hauntedPath.moveTo(x - 57, peak + 78);
                hauntedPath.lineTo(x + 5, peak);
                hauntedPath.lineTo(x + 56, peak + 78);
                hauntedPath.close();
                c.drawPath(hauntedPath, hauntedPaint);
                c.drawRect(x + 3, peak - 15, x + 5, peak + 5, hauntedPaint);
            }
            c.drawRect(595, 268, 612, 325, hauntedPaint);
            c.drawRect(440, 440, 556, 505, hauntedPaint);
            for (int i = 0; i < 12; i++) {
                float x = 311 + i % 6 * 70;
                float y = 337 + i / 6 * 68;
                float cycle = 43 + i * 7.13f;
                float phase = (t + i * 19.7f) % cycle / cycle;
                float light = Math.max(0, (float) Math.sin(phase * Math.PI * 2));
                hauntedGlow(c, x + 10, y + 16, 24, 33, (int) (24 * light), true);
                hauntedPaint.setColor(0xFFD39A53);
                hauntedPaint.setAlpha(12 + (int) (95 * light));
                c.drawRoundRect(x, y, x + 20, y + 34, 8, 8, hauntedPaint);
                // One window apparition, clipped behind its frame and only during its lit phase.
                if (i == 4 && ((int) (t / cycle)) % 3 == 1 && phase > 0.12f && phase < 0.38f) {
                    float p = (phase - 0.12f) / 0.26f;
                    c.save();
                    c.clipRect(x, y, x + 20, y + 34);
                    halloweenGhost(c, x - 7 + p * 34, y + 13, 6, (int) (65 * Math.sin(p * Math.PI)));
                    c.restore();
                }
                hauntedPaint.setColor(0xFF05070D);
                c.drawRect(x + 9, y, x + 11, y + 34, hauntedPaint);
                c.drawRect(x, y + 15, x + 20, y + 18, hauntedPaint);
            }
            halloweenBats(c, t, 97, 11);
            halloweenFog(c, t, 25);
        }

        private void drawWitchingHour(Canvas c, float t) {
            hauntedGlow(c, 510, 355, 450, 110, 20, false);
            halloweenBats(c, t, 113, 0);
            float phase = t % 191;
            if (phase > 93 && phase < 126) {
                float p = (phase - 93) / 33;
                c.save();
                c.translate(-90 + p * 1180, 227 + (float) Math.sin(p * 4) * 13);
                c.scale(0.75f, 0.75f);
                hauntedPaint.setColor(0xFF05070B);
                hauntedPaint.setStrokeWidth(3);
                c.drawLine(-40, 17, 43, 8, hauntedPaint);
                for (int i = 0; i < 6; i++) c.drawLine(-28, 15, -53, 10 + i * 3, hauntedPaint);
                c.drawCircle(3, -20, 6, hauntedPaint);
                hauntedPath.rewind();
                hauntedPath.moveTo(-8, -25);
                hauntedPath.lineTo(0, -46);
                hauntedPath.lineTo(9, -28);
                hauntedPath.lineTo(17, -25);
                hauntedPath.close();
                hauntedPath.moveTo(1, -14);
                hauntedPath.quadTo(-12, -8, -23, 13);
                hauntedPath.lineTo(7, 9);
                hauntedPath.lineTo(18, 19);
                hauntedPath.lineTo(23, 16);
                hauntedPath.lineTo(11, 0);
                hauntedPath.close();
                c.drawPath(hauntedPath, hauntedPaint);
                c.drawLine(1, -8, 18, 3, hauntedPaint);
                c.drawLine(18, 3, 26, 1, hauntedPaint);
                c.restore();
            }
            halloweenTree(c, 105, 610, 2, false);
            halloweenTree(c, 915, 610, 1.8f, true);
            halloweenFog(c, t * 0.7f, 18);
        }

        private void drawSkeletonParty(Canvas c, float t) {
            hauntedGlow(c, 490, 460, 440, 160, 25, false);
            for (int i = 0; i < 4; i++) {
                c.save();
                float phase = t * (0.36f + i * 0.027f) + i * 2.1f;
                float sway = (float) Math.sin(phase);
                c.translate(285 + i * 145 + sway * 5, 430 + (float) Math.cos(phase * 0.7f) * 3);
                c.rotate(sway * 4);
                hauntedPaint.setColor(0xFF8E999F);
                hauntedPaint.setAlpha(110 + (int) (12 * Math.sin(phase * 0.4f)));
                c.drawOval(-14, -114, 14, -82, hauntedPaint);
                c.drawRoundRect(-9, -89, 9, -76, 3, 3, hauntedPaint);
                hauntedPaint.setStyle(Paint.Style.STROKE);
                hauntedPaint.setStrokeWidth(3);
                hauntedPaint.setStrokeCap(Paint.Cap.ROUND);
                c.drawLine(0, -77, 0, -15, hauntedPaint);
                for (int rib = 0; rib < 4; rib++) {
                    float y = -66 + rib * 9;
                    hauntedPath.rewind();
                    hauntedPath.moveTo(-17 + rib, y - 3);
                    hauntedPath.quadTo(-15, y + 10, 0, y + 5);
                    hauntedPath.quadTo(15, y + 10, 17 - rib, y - 3);
                    c.drawPath(hauntedPath, hauntedPaint);
                }
                c.drawOval(-13, -22, 13, -5, hauntedPaint);
                for (int side = -1; side <= 1; side += 2) {
                    float elbowY = -40 + side * sway * 12;
                    c.drawLine(side * 5, -70, side * 30, elbowY, hauntedPaint);
                    c.drawLine(side * 30, elbowY, side * 43, -63 + side * sway * 9, hauntedPaint);
                    c.drawCircle(side * 44, -65 + side * sway * 9, 3, hauntedPaint);
                    c.drawLine(side * 8, -6, side * 19 + sway * 4, 26, hauntedPaint);
                    c.drawLine(side * 19 + sway * 4, 26, side * 24, 61, hauntedPaint);
                    c.drawLine(side * 24, 61, side * 34, 63, hauntedPaint);
                }
                hauntedPaint.setStyle(Paint.Style.FILL);
                hauntedPaint.setStrokeCap(Paint.Cap.BUTT);
                hauntedPaint.setColor(0xFF11161E);
                c.drawOval(-9, -103, -2, -94, hauntedPaint);
                c.drawOval(2, -103, 9, -94, hauntedPaint);
                c.drawCircle(0, -90, 2, hauntedPaint);
                c.restore();
            }
            for (int i = 0; i < 5; i++) {
                float x = 180 + i * 158;
                float y = 513 + i % 2 * 13;
                if (i % 2 == 0) halloweenPumpkin(c, x, y, 21, t, i + 8);
                else {
                    float flicker = 0.85f + 0.10f * (float) Math.sin(t * 1.3f + i * 3);
                    hauntedGlow(c, x, y - 24, 30, 45, (int) (55 * flicker), true);
                    hauntedPaint.setColor(0xFF8B8170);
                    c.drawRect(x - 4, y - 18, x + 4, y + 4, hauntedPaint);
                    hauntedPaint.setColor(0xFFFFC275);
                    hauntedPaint.setAlpha((int) (185 * flicker));
                    float lean = (float) Math.sin(t * 0.8f + i) * 2;
                    c.drawOval(x - 3 + lean, y - 31, x + 3 + lean, y - 18, hauntedPaint);
                }
            }
            for (int i = 0; i < 23; i++) {
                float p = (t * (0.003f + i % 4 * 0.0006f) + i * 0.073f) % 1;
                hauntedPaint.setColor(0xFFD99746);
                hauntedPaint.setAlpha((int) (65 * Math.sin(p * Math.PI)));
                c.drawCircle(100 + i * 137 % 800 + (float) Math.sin(t * 0.2f + i) * 13,
                        540 - p * 300, 1.2f, hauntedPaint);
            }
            halloweenFog(c, t, 12);
        }

        private void drawPumpkinKing(Canvas c, int width, int height, float t) {
            c.drawColor(0xFF080B15);
            c.save();
            c.scale(width / 1000f, height / 600f);
            c.translate((float) Math.sin(t * 0.019f) * 5, (float) Math.cos(t * 0.023f) * 3);
            hauntedPaint.setStyle(Paint.Style.FILL);
            hauntedPaint.setShader(null);
            for (int i = 0; i < 27; i++) {
                hauntedPaint.setColor(0xFFB7C3D4);
                hauntedPaint.setAlpha(35 + (int) (14 * Math.sin(t * 0.15f + i * 2.1f)));
                c.drawCircle((i * 149 + t * 0.1f) % 1000, 22 + i * 67 % 315, 0.8f, hauntedPaint);
            }
            hauntedGlow(c, 550, 225, 290, 280, 60, false);
            hauntedPaint.setColor(0xFFD9DCD3);
            hauntedPaint.setAlpha(195 + (int) (12 * Math.sin(t * 0.043f)));
            c.drawCircle(550, 225, 187, hauntedPaint);
            for (int i = 0; i < 4; i++) {
                float x = (t * (1.1f + i * 0.43f) + i * 421) % 1800 - 400;
                hauntedPaint.setColor(0xFF1B2232);
                hauntedPaint.setAlpha(35 + i * 9);
                c.drawOval(x - 260, 78 + i * 83, x + 260, 95 + i * 83, hauntedPaint);
                hauntedGlow(c, x, 94 + i * 83, 290, 17, 12, false);
            }
            halloweenBats(c, t, 127, 7);
            halloweenTree(c, 105, 540, 1.35f, false);
            halloweenTree(c, 906, 555, 1.55f, true);

            // A broad hillside narrows into an open, asymmetric spiral crest.
            hauntedPaint.setColor(0xFF04060B);
            hauntedPath.rewind();
            hauntedPath.moveTo(-20, 590);
            hauntedPath.cubicTo(210, 565, 325, 460, 427, 358);
            hauntedPath.cubicTo(488, 294, 593, 277, 650, 331);
            hauntedPath.cubicTo(705, 385, 661, 440, 609, 418);
            hauntedPath.cubicTo(578, 404, 589, 372, 612, 374);
            hauntedPath.cubicTo(594, 384, 606, 405, 626, 400);
            hauntedPath.cubicTo(658, 393, 651, 356, 621, 345);
            hauntedPath.cubicTo(577, 328, 545, 366, 555, 421);
            hauntedPath.cubicTo(565, 485, 717, 543, 1020, 586);
            hauntedPath.lineTo(1020, 620);
            hauntedPath.lineTo(-20, 620);
            hauntedPath.close();
            c.drawPath(hauntedPath, hauntedPaint);

            // Original long-limbed figure: offset stance, angular frock coat and narrow oval eyes.
            c.save();
            c.translate(540, 301);
            float sway = (float) Math.sin(t * 0.22f);
            c.rotate(sway * 1.7f);
            hauntedPaint.setColor(0xFF0A0D14);
            hauntedPaint.setStrokeCap(Paint.Cap.ROUND);
            hauntedPaint.setStrokeWidth(5);
            c.drawLine(-8, -86, -16, -42, hauntedPaint);
            c.drawLine(-16, -42, -22, 2, hauntedPaint);
            c.drawLine(8, -86, 19, -44, hauntedPaint);
            c.drawLine(19, -44, 25, 4, hauntedPaint);
            c.drawLine(-23, 2, -34, 5, hauntedPaint);
            c.drawLine(25, 4, 37, 7, hauntedPaint);
            hauntedPath.rewind();
            hauntedPath.moveTo(-13, -151);
            hauntedPath.lineTo(12, -150);
            hauntedPath.lineTo(10, -113);
            hauntedPath.lineTo(21, -65);
            hauntedPath.lineTo(4, -79);
            hauntedPath.lineTo(-4, -76);
            hauntedPath.lineTo(-22, -64);
            hauntedPath.lineTo(-9, -113);
            hauntedPath.close();
            c.drawPath(hauntedPath, hauntedPaint);
            hauntedPaint.setColor(0xFF47505B);
            hauntedPaint.setAlpha(110);
            hauntedPaint.setStrokeWidth(0.7f);
            for (int i = -2; i <= 2; i++) {
                c.drawLine(i * 4, -145, i * 3, -111, hauntedPaint);
                c.drawLine(i * 3, -111, i * 6, -84, hauntedPaint);
            }
            c.drawLine(-8, -82, -20, -3, hauntedPaint);
            c.drawLine(9, -82, 24, -2, hauntedPaint);
            float gesturePhase = t % 139;
            float reach = gesturePhase > 62 && gesturePhase < 88
                    ? (float) Math.pow(Math.sin((gesturePhase - 62) / 26 * Math.PI), 2) : 0;
            for (int side = -1; side <= 1; side += 2) {
                float elbowX = side * (29 + reach * 20);
                float elbowY = -118 - reach * 43 + sway * side * 3;
                float handX = side * (39 + reach * 43);
                float handY = -92 - reach * 98 + (float) Math.sin(t * 0.31f + side) * 4;
                hauntedPaint.setColor(0xFF0A0D14);
                hauntedPaint.setStrokeWidth(4);
                c.drawLine(side * 12, -146, elbowX, elbowY, hauntedPaint);
                c.drawLine(elbowX, elbowY, handX, handY, hauntedPaint);
                hauntedPaint.setColor(0xFFAEB7B9);
                hauntedPaint.setStrokeWidth(1.3f);
                c.drawLine(handX, handY, handX + side * 5, handY - 3, hauntedPaint);
                for (int finger = 0; finger < 3; finger++) {
                    c.drawLine(handX + side * 5, handY - 3,
                            handX + side * (10 + finger * 2), handY - 7 + finger * 3, hauntedPaint);
                }
            }
            hauntedPaint.setColor(0xFFBFC8C7);
            c.drawRect(-2, -162, 2, -151, hauntedPaint);
            c.save();
            float headPhase = t % 71;
            float glance = headPhase > 39 && headPhase < 54
                    ? (float) Math.sin((headPhase - 39) / 15 * Math.PI) * 9 : 0;
            c.rotate(glance + sway, 0, -176);
            hauntedPaint.setColor(0xFFD9DDD4);
            hauntedPaint.setAlpha(205 + (int) (12 * Math.sin(t * 0.09f)));
            c.drawCircle(0, -178, 16, hauntedPaint);
            hauntedPaint.setColor(0xFF17202A);
            c.drawOval(-10, -182, -4, -174, hauntedPaint);
            c.drawOval(4, -183, 9, -175, hauntedPaint);
            c.drawCircle(0, -171, 1.6f, hauntedPaint);
            // A short, quiet jaw mark rather than an exaggerated grin.
            hauntedPaint.setStrokeWidth(1);
            c.drawLine(-4, -167, 5, -166, hauntedPaint);
            c.restore();
            hauntedPaint.setColor(0xFF060910);
            hauntedPath.rewind();
            hauntedPath.moveTo(0, -150);
            hauntedPath.lineTo(-17, -157);
            hauntedPath.lineTo(-13, -146);
            hauntedPath.lineTo(-6, -148);
            hauntedPath.lineTo(0, -145);
            hauntedPath.lineTo(6, -148);
            hauntedPath.lineTo(13, -146);
            hauntedPath.lineTo(17, -157);
            hauntedPath.close();
            c.drawPath(hauntedPath, hauntedPaint);
            c.restore();

            for (int i = 0; i < 6; i++) {
                float x = 172 + i * 132;
                float y = 536 + (i % 2) * 19;
                float phase = (t + i * 31) % (157 + i * 13);
                if (phase > 73 && phase < 98) {
                    float visibility = (float) Math.sin((phase - 73) / 25 * Math.PI);
                    // The grave occludes the rising figure's lower body.
                    float headY = y - 40 - visibility * 35;
                    hauntedPaint.setColor(0xFFA2AEB6);
                    hauntedPaint.setAlpha((int) (65 * visibility));
                    c.drawCircle(x + 8, headY, 8, hauntedPaint);
                    hauntedPaint.setStrokeWidth(2);
                    c.drawLine(x + 8, headY + 8, x + 8, headY + 48, hauntedPaint);
                    c.drawLine(x - 8, headY + 19, x + 25, headY + 22, hauntedPaint);
                    for (int rib = 0; rib < 3; rib++) {
                        c.drawLine(x, headY + 23 + rib * 5, x + 16, headY + 25 + rib * 5, hauntedPaint);
                    }
                }
                c.save();
                c.rotate(i % 2 == 0 ? -11 : 8, x, y);
                hauntedPaint.setColor(0xFF141924);
                c.drawRoundRect(x - 20, y - 53, x + 20, y + 8, 12, 12, hauntedPaint);
                hauntedPaint.setColor(0xFF070A11);
                c.drawRect(x - 1.5f, y - 42, x + 1.5f, y - 20, hauntedPaint);
                c.drawRect(x - 9, y - 35, x + 9, y - 32, hauntedPaint);
                c.restore();
                if (i % 2 == 0) halloweenPumpkin(c, x + 30, y + 8, 20, t, i + 17);
            }
            halloweenFog(c, t * 0.8f, 23);
            hauntedPaint.setAlpha(255);
            hauntedPaint.setStrokeCap(Paint.Cap.BUTT);
            hauntedPaint.setStyle(Paint.Style.FILL);
            c.restore();
        }

        private Paint makeBirthdayLettering() {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setTypeface(android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL));
            paint.setTextAlign(Paint.Align.CENTER);
            return paint;
        }

        private void birthdayHeart(Canvas c, float x, float y, float size, int alpha) {
            birthdayPaint.setColor(0xFFBD4666);
            birthdayPaint.setAlpha(alpha);
            birthdayPath.rewind();
            birthdayPath.moveTo(x, y + size);
            birthdayPath.cubicTo(x - size * 2, y - size * 0.2f, x - size, y - size * 1.5f, x, y - size * 0.4f);
            birthdayPath.cubicTo(x + size, y - size * 1.5f, x + size * 2, y - size * 0.2f, x, y + size);
            c.drawPath(birthdayPath, birthdayPaint);
        }

        private void drawBirthdayAna(Canvas c, int width, int height, float t) {
            c.drawColor(0xFF050A19);
            c.save();
            c.scale(width / 1000f, height / 600f);
            float driftX = (float) Math.sin(t * 0.031f) * 5;
            float driftY = (float) Math.cos(t * 0.027f) * 3;
            c.translate(driftX, driftY);
            float cycle = t % 96;
            // A gentle first reveal; subsequent cycles dim only slightly to retain readability.
            float reveal = Math.min(1, t / 4);
            float brightness = reveal * (0.92f + 0.08f * (float) Math.sin(cycle / 96 * Math.PI));
            hauntedGlow(c, 500, 280, 420, 220, 22, true);
            for (int i = 0; i < 11; i++) {
                double angle = -Math.PI + i * Math.PI / 10 + Math.sin(t * 0.05f) * 0.02;
                float x = 500 + (float) Math.cos(angle) * 410;
                float y = 305 + (float) Math.sin(angle) * 255;
                birthdayPaint.setColor(0xFFECC078);
                birthdayPaint.setAlpha(4 + i % 3);
                birthdayPath.rewind();
                birthdayPath.moveTo(500, 305);
                birthdayPath.lineTo(x - 14, y);
                birthdayPath.lineTo(x + 14, y);
                birthdayPath.close();
                c.drawPath(birthdayPath, birthdayPaint);
            }
            // Sparse sparkles and confetti stay outside the central lettering rectangle.
            for (int i = 0; i < 75; i++) {
                float x = 65 + i * 137 % 870 + (float) Math.sin(t * 0.14f + i) * 10;
                float y = 50 + (t * (2 + i % 5 * 0.4f) + i * 83) % 500;
                if (x > 95 && x < 905 && y > 219 && y < 350) continue;
                float pulse = 0.5f + 0.5f * (float) Math.sin(t * 0.6f + i * 2.7f);
                birthdayPaint.setColor(i % 3 == 0 ? 0xFFD890AF : 0xFFEAC67F);
                birthdayPaint.setAlpha(35 + (int) (85 * pulse));
                if (i % 3 == 0) {
                    c.save();
                    c.rotate(t * (3 + i % 4) + i * 23, x, y);
                    c.drawRect(x - 1.5f, y - 3, x + 1.5f, y + 3, birthdayPaint);
                    c.restore();
                } else {
                    c.drawCircle(x, y, 1.2f, birthdayPaint);
                    if (i % 7 == 0) {
                        birthdayPaint.setStrokeWidth(0.7f);
                        c.drawLine(x - 3, y, x + 3, y, birthdayPaint);
                        c.drawLine(x, y - 3, x, y + 3, birthdayPaint);
                    }
                }
            }
            for (int i = 0; i < 8; i++) {
                float p = (t * (0.004f + i % 3 * 0.0007f) + i * 0.137f) % 1;
                float x = i % 2 == 0 ? 75 + i * 7 : 910 - i * 6;
                x += (float) Math.sin(t * 0.14f + i) * 13;
                float y = 685 - p * 820;
                birthdayPaint.setColor(i % 4 == 0 ? 0xFFA43E64 : i % 4 == 1 ? 0xFFAA8547
                        : i % 4 == 2 ? 0xFF55578D : 0xFF397B86);
                c.drawOval(x - 21, y - 29, x + 21, y + 29, birthdayPaint);
                birthdayPaint.setColor(0xFFEEE4DC);
                birthdayPaint.setAlpha(55);
                c.drawOval(x - 13, y - 20, x - 7, y - 5, birthdayPaint);
                birthdayPaint.setColor(0xFFAC9B8C);
                birthdayPaint.setAlpha(85);
                birthdayPaint.setStyle(Paint.Style.STROKE);
                birthdayPaint.setStrokeWidth(0.8f);
                birthdayPath.rewind();
                birthdayPath.moveTo(x, y + 29);
                birthdayPath.cubicTo(x - 8, y + 48, x + 9, y + 62, x + 2, y + 84);
                c.drawPath(birthdayPath, birthdayPaint);
                birthdayPaint.setStyle(Paint.Style.FILL);
            }
            // Slow, fading bursts confined above the greeting, staggered on unequal cycles.
            for (int burst = 0; burst < 3; burst++) {
                float phase = (t + burst * 19) % (41 + burst * 13);
                if (phase < 15 || phase > 23) continue;
                float p = (phase - 15) / 8;
                float x = 280 + burst * 220;
                float y = 129 + burst % 2 * 13;
                birthdayPaint.setColor(burst == 0 ? 0xFFE9BC72 : burst == 1 ? 0xFFCF7C9F : 0xFF83A6D1);
                birthdayPaint.setAlpha((int) (135 * Math.sin(p * Math.PI)));
                birthdayPaint.setStrokeWidth(1.4f);
                for (int ray = 0; ray < 24; ray++) {
                    double a = ray * Math.PI / 12;
                    float r = 9 + p * 62;
                    float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
                    c.drawLine(x + dx * r * 0.8f, y + dy * r + p * p * 12,
                            x + dx * r, y + dy * r + p * p * 12, birthdayPaint);
                }
            }
            for (int i = 0; i < 9; i++) {
                float p = (t * 0.0035f + i * 0.117f) % 1;
                float x = i % 2 == 0 ? 190 + i * 8 : 790 + i * 7;
                float y = 560 - p * 520;
                if (y > 215 && y < 350) continue;
                x += (float) Math.sin(t * 0.17f + i) * 12;
                int alpha = (int) (100 * Math.sin(p * Math.PI));
                hauntedGlow(c, x, y, 19, 19, alpha / 4, true);
                birthdayHeart(c, x, y, 5 + i % 3, alpha);
            }
            // Tiered burgundy cake with warm icing and five continuously glowing candles.
            hauntedGlow(c, 500, 461, 145, 80, 28, true);
            birthdayPaint.setColor(0xFF957646);
            c.drawOval(405, 490, 595, 505, birthdayPaint);
            birthdayPaint.setColor(0xFF63263A);
            c.drawRoundRect(420, 446, 580, 494, 9, 9, birthdayPaint);
            birthdayPaint.setColor(0xFF8E4256);
            c.drawRoundRect(441, 407, 559, 451, 8, 8, birthdayPaint);
            birthdayPaint.setColor(0xFFE5C99A);
            c.drawRoundRect(441, 407, 559, 418, 7, 7, birthdayPaint);
            c.drawRoundRect(420, 446, 580, 455, 7, 7, birthdayPaint);
            for (int i = 0; i < 9; i++) {
                birthdayPaint.setColor(0xFFC99C5C);
                c.drawCircle(433 + i * 17, 473 + (float) Math.sin(i * 0.8f) * 3, 2, birthdayPaint);
            }
            for (int i = 0; i < 5; i++) {
                float x = 460 + i * 20;
                float flicker = 0.8f + 0.08f * (float) Math.sin(t * 1.4f + i * 2.1f)
                        + 0.06f * (float) Math.sin(t * 2.1f + i);
                birthdayPaint.setColor(i % 2 == 0 ? 0xFFE5CFAB : 0xFFB9798C);
                c.drawRoundRect(x - 2.5f, 384, x + 2.5f, 408, 2, 2, birthdayPaint);
                hauntedGlow(c, x, 378, 19, 24, (int) (65 * flicker), true);
                birthdayPaint.setColor(0xFFFFD993);
                birthdayPaint.setAlpha((int) (230 * flicker));
                float lean = (float) Math.sin(t * 0.9f + i) * 1.1f;
                c.drawOval(x - 3 + lean, 369 + (1 - flicker) * 4, x + 3 + lean, 384, birthdayPaint);
            }
            for (int i = 0; i < 4; i++) {
                float x = i < 2 ? 220 + i * 60 : 715 + (i - 2) * 60;
                float y = 493 + (float) Math.sin(t * 0.3f + i * 2) * 3;
                c.save();
                c.rotate((float) Math.sin(t * 0.19f + i) * 2, x, y);
                birthdayPaint.setColor(i % 2 == 0 ? 0xFF71344D : 0xFF354B6D);
                c.drawRoundRect(x - 24, y - 39, x + 24, y + 4, 3, 3, birthdayPaint);
                birthdayPaint.setColor(0xFFC4A46B);
                c.drawRect(x - 3, y - 39, x + 3, y + 4, birthdayPaint);
                c.drawRect(x - 25, y - 33, x + 25, y - 28, birthdayPaint);
                birthdayPaint.setStyle(Paint.Style.STROKE);
                birthdayPaint.setStrokeWidth(2);
                c.drawOval(x - 17, y - 52, x, y - 38, birthdayPaint);
                c.drawOval(x, y - 52, x + 17, y - 38, birthdayPaint);
                birthdayPaint.setStyle(Paint.Style.FILL);
                c.restore();
            }
            // Text is drawn last in a reserved, overscan-safe area, above every decoration.
            birthdayLettering.setTextSize(51);
            float measured = birthdayLettering.measureText("Happy Birthday, ANA! ❤️");
            if (measured > 790) birthdayLettering.setTextSize(51 * 790 / measured);
            birthdayLettering.setColor(0xFF5B1B35);
            birthdayLettering.setAlpha((int) (230 * brightness));
            c.drawText("Happy Birthday, ANA! ❤️", 502, 279, birthdayLettering);
            birthdayLettering.setColor(0xFFF0CD83);
            birthdayLettering.setAlpha((int) (255 * brightness));
            c.drawText("Happy Birthday, ANA! ❤️", 500, 277, birthdayLettering);
            birthdayLettering.setTextSize(23);
            birthdayLettering.setColor(0xFFDABAB5);
            c.drawText("Ever thine, ever mine, ever ours. ❤️", 500, 322, birthdayLettering);
            c.restore();
        }

        private void prepareAnimatedVillage() {
            if (animatedVillageBackground != null) return;
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inScaled = false;
            animatedVillageBackground = android.graphics.BitmapFactory.decodeResource(
                    getResources(), R.drawable.christmas_village, options);
        }

        private void animatedVillageGlow(Canvas canvas, float x, float y, float rx, float ry,
                int alpha, boolean warm) {
            canvas.save();
            canvas.translate(x, y);
            canvas.scale(rx, ry);
            animatedVillagePaint.setShader(warm ? hauntedWarmth : hauntedMist);
            animatedVillagePaint.setAlpha(alpha);
            canvas.drawCircle(0, 0, 1, animatedVillagePaint);
            animatedVillagePaint.setShader(null);
            animatedVillagePaint.setAlpha(255);
            canvas.restore();
        }

        private void drawAnimatedVillage(Canvas canvas, float width, float height, float seconds) {
            if (animatedVillageBackground == null) return;
            // Artwork and overlays share the same center-crop transform, in original image pixels.
            float scale = Math.max(width / animatedVillageBackground.getWidth(),
                    height / animatedVillageBackground.getHeight());
            canvas.save();
            canvas.translate((width - animatedVillageBackground.getWidth() * scale) * .5f,
                    (height - animatedVillageBackground.getHeight() * scale) * .5f);
            canvas.scale(scale, scale);
            animatedVillagePaint.setColor(Color.WHITE);
            canvas.drawBitmap(animatedVillageBackground, 0, 0, animatedVillagePaint);
            canvas.scale(animatedVillageBackground.getWidth() / 1672f,
                    animatedVillageBackground.getHeight() / 941f);

            for (int i = 0; i < animatedTreeLights.length; i += 2) {
                float pulse = .5f + .5f * (float) Math.sin(seconds * (.65f + i * .011f) + i * 2.39f);
                animatedVillageGlow(canvas, animatedTreeLights[i], animatedTreeLights[i + 1],
                        12, 15, (int) (18 + 100 * pulse * pulse), true);
                animatedVillagePaint.setColor(0xFFFFE4A0);
                animatedVillagePaint.setAlpha((int) (35 + 140 * pulse * pulse));
                canvas.drawCircle(animatedTreeLights[i], animatedTreeLights[i + 1],
                        1.5f + pulse * .7f, animatedVillagePaint);
                animatedVillagePaint.setAlpha(255);
            }
            float sparkle = (float) Math.pow(Math.max(0, Math.sin(seconds * .24f)), 12);
            animatedVillageGlow(canvas, 850, 155, 39, 43, (int) (16 + 55 * sparkle), true);
            for (int i = 0; i < animatedLamps.length; i += 2) {
                animatedVillageGlow(canvas, animatedLamps[i], animatedLamps[i + 1], 30, 36,
                        (int) (24 + 8 * Math.sin(seconds * .37f + i)), true);
            }
            for (int i = 0; i < animatedWindows.length; i += 2) {
                animatedVillageGlow(canvas, animatedWindows[i], animatedWindows[i + 1], 12, 20,
                        (int) (24 + 15 * Math.sin(seconds * .43f + i * 1.7f)), true);
            }
            // Soft overlapping puffs rise from each chimney, then fade before looping.
            for (int chimney = 0; chimney < animatedChimneys.length; chimney += 2) {
                for (int puff = 0; puff < 10; puff++) {
                    float age = (seconds * .055f + puff / 10f + chimney * .19f) % 1f;
                    float x = animatedChimneys[chimney] + age * 30
                            + 7 * (float) Math.sin(age * 5 + seconds * .13f + chimney);
                    float y = animatedChimneys[chimney + 1] - 5 - age * 145;
                    animatedVillageGlow(canvas, x, y, 8 + age * 22, 14 + age * 25,
                            (int) (48 * Math.sin(age * Math.PI)), false);
                }
            }
            // Only three additional, small skaters; all paths stay within the painted ice.
            for (int i = 0; i < 3; i++) {
                float angle = seconds * (.035f + i * .009f) + i * 2.1f;
                float x = 875 + (280 + i * 35) * (float) Math.cos(angle);
                float y = 704 + (32 + i * 6) * (float) Math.sin(angle);
                float stride = (float) Math.sin(seconds * 1.1f + i * 2);
                canvas.save();
                canvas.translate(x, y);
                canvas.scale(.78f + (y - 660) * .002f, .78f + (y - 660) * .002f);
                animatedVillagePaint.setColor(0x303A3340);
                canvas.drawOval(-11, -1, 13, 3, animatedVillagePaint);
                animatedVillagePaint.setColor(0xE6C49C7C);
                canvas.drawCircle(1, -33, 3.2f, animatedVillagePaint);
                animatedVillagePaint.setColor(i == 1 ? 0xEF733E35 : 0xEF394653);
                animatedVillagePaint.setStrokeWidth(8);
                animatedVillagePaint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(0, -27, -2, -15, animatedVillagePaint);
                animatedVillagePaint.setStrokeWidth(3);
                canvas.drawLine(0, -25, -10, -19 - stride * 2, animatedVillagePaint);
                canvas.drawLine(1, -25, 10, -21 + stride * 2, animatedVillagePaint);
                animatedVillagePaint.setColor(0xEF33303A);
                canvas.drawLine(-2, -15, -7 - stride * 3, -2, animatedVillagePaint);
                canvas.drawLine(0, -15, 7 + stride * 3, -3, animatedVillagePaint);
                animatedVillagePaint.setStrokeWidth(1);
                animatedVillagePaint.setStrokeCap(Paint.Cap.BUTT);
                canvas.restore();
            }
            for (int i = 0; i < 270; i++) {
                int layer = i % 3;
                float speed = 14 + layer * 10 + (i % 7);
                float y = ((i * 137.31f + seconds * speed) % 1001) - 30;
                float x = ((i * 317.73f + seconds * (4 + layer * 2) +
                        12 * (float) Math.sin(seconds * .16f + i)) % 1752 + 1752) % 1752 - 40;
                animatedVillagePaint.setColor(Color.WHITE);
                animatedVillagePaint.setAlpha(70 + layer * 40 + i % 20);
                canvas.drawCircle(x, y, .85f + layer * .85f + (i % 4) * .12f, animatedVillagePaint);
            }
            animatedVillagePaint.setAlpha(255);
            canvas.restore();
        }

        private void prepareChristmasVillage() {
            if (villageBackground != null) return;
            // Fixed, bounded ~6 MB illustration cache, independent of a TV's 4K resolution.
            villageBackground = android.graphics.Bitmap.createBitmap(1600, 960, android.graphics.Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(villageBackground);
            c.scale(1.6f, 1.6f);
            villagePaint.setShader(new android.graphics.LinearGradient(0, 0, 0, 600,
                    new int[]{0xFF0A142C, 0xFF34465E, 0xFF91A8BD}, null, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, 1000, 600, villagePaint);
            villagePaint.setShader(null);
            // Distant roofline and a slender church spire behind the square.
            villagePaint.setColor(0xFF28384E);
            for (int i = 0; i < 16; i++) {
                float x = i * 70;
                float y = 240 + i % 3 * 17;
                c.drawRect(x, y, x + 62, 390, villagePaint);
                villagePath.rewind();
                villagePath.moveTo(x - 5, y);
                villagePath.lineTo(x + 31, y - 35);
                villagePath.lineTo(x + 67, y);
                villagePath.close();
                c.drawPath(villagePath, villagePaint);
            }
            c.drawRect(620, 175, 649, 335, villagePaint);
            villagePath.rewind();
            villagePath.moveTo(615, 180);
            villagePath.lineTo(635, 95);
            villagePath.lineTo(654, 180);
            villagePath.close();
            c.drawPath(villagePath, villagePaint);
            villagePaint.setColor(0xFFBDD0DF);
            villagePath.rewind();
            villagePath.moveTo(0, 375);
            villagePath.cubicTo(260, 350, 690, 350, 1000, 378);
            villagePath.lineTo(1000, 600);
            villagePath.lineTo(0, 600);
            villagePath.close();
            c.drawPath(villagePath, villagePaint);
            villagePaint.setColor(0xFF9FB5CA);
            for (int i = 0; i < 15; i++) {
                float y = 390 + i * 15;
                c.drawOval(-80, y, 1080, y + 2, villagePaint);
            }
            for (int i = 0; i < 6; i++) drawVillageHouse(c, i);
            // Snowy spruce layers with irregular branches and small ornaments.
            villagePaint.setColor(0xFF4C3D30);
            c.drawRect(493, 338, 507, 395, villagePaint);
            for (int tier = 5; tier >= 0; tier--) {
                float top = 139 + tier * 32;
                float radius = 29 + tier * 15;
                villagePaint.setColor(tier % 2 == 0 ? 0xFF173C37 : 0xFF1D4941);
                villagePath.rewind();
                villagePath.moveTo(500, top - 32);
                villagePath.cubicTo(500 - radius * 0.4f, top + 10, 500 - radius * 0.7f, top + 32, 500 - radius, top + 45);
                villagePath.quadTo(500 - radius * 0.4f, top + 37, 500, top + 49);
                villagePath.quadTo(500 + radius * 0.4f, top + 37, 500 + radius, top + 45);
                villagePath.cubicTo(500 + radius * 0.7f, top + 32, 500 + radius * 0.4f, top + 10, 500, top - 32);
                c.drawPath(villagePath, villagePaint);
                villagePaint.setColor(0xFFB4C9CE);
                villagePaint.setStyle(Paint.Style.STROKE);
                villagePaint.setStrokeWidth(3);
                c.drawLine(500 - radius * 0.83f, top + 39, 500 - radius * 0.28f, top + 30, villagePaint);
                c.drawLine(500 + radius * 0.2f, top + 31, 500 + radius * 0.8f, top + 39, villagePaint);
                villagePaint.setStyle(Paint.Style.FILL);
            }
            for (int i = 0; i < 34; i++) {
                float y = 175 + i * 41 % 174;
                float half = (y - 110) * 0.36f;
                float x = 500 + ((i * 73 % 101) / 50f - 1) * half;
                villagePaint.setColor(i % 3 == 0 ? 0xFFBD6A55 : 0xFFB3945A);
                c.drawCircle(x, y, 2.6f, villagePaint);
            }
            // Broad oval rink, snowbank, ice reflections and fine curved skate marks.
            villagePaint.setColor(0xFF738B9E);
            c.drawOval(252, 396, 748, 548, villagePaint);
            villagePaint.setColor(0xFFE1E8E9);
            c.drawOval(258, 397, 742, 541, villagePaint);
            villagePaint.setShader(new android.graphics.LinearGradient(0, 408, 0, 530,
                    0xFF789CAC, 0xFFBCD5DB, android.graphics.Shader.TileMode.CLAMP));
            c.drawOval(272, 407, 728, 530, villagePaint);
            villagePaint.setShader(null);
            villagePaint.setStyle(Paint.Style.STROKE);
            villagePaint.setStrokeWidth(0.6f);
            villagePaint.setColor(0x55E8F1F1);
            for (int i = 0; i < 12; i++) {
                villageBounds.set(299 + i * 7, 423 + i * 3, 696 - i * 5, 515 - i);
                c.drawArc(villageBounds, i * 29, 125, false, villagePaint);
            }
            villagePaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 4; i++) {
                float x = villageLampX(i), y = villageLampY(i);
                villagePaint.setColor(0xFF273A45);
                c.drawRoundRect(x - 3, y - 91, x + 3, y + 3, 2, 2, villagePaint);
                c.drawOval(x - 12, y, x + 12, y + 7, villagePaint);
                c.drawRect(x - 9, y - 117, x + 9, y - 91, villagePaint);
                villagePaint.setColor(0xFFF3CE8D);
                c.drawRect(x - 6, y - 113, x + 6, y - 94, villagePaint);
                villagePaint.setColor(0xFF263846);
                c.drawRect(x - 1, y - 114, x + 1, y - 93, villagePaint);
                villagePath.rewind();
                villagePath.moveTo(x - 13, y - 117);
                villagePath.lineTo(x, y - 129);
                villagePath.lineTo(x + 13, y - 117);
                villagePath.close();
                c.drawPath(villagePath, villagePaint);
                villagePaint.setColor(0xFFDFE8EB);
                c.drawLine(x - 12, y - 118, x, y - 130, villagePaint);
            }
            villagePaint.setColor(0xFFDCE5EB);
            villagePath.rewind();
            villagePath.moveTo(0, 560);
            villagePath.cubicTo(190, 535, 245, 582, 385, 579);
            villagePath.cubicTo(640, 559, 780, 547, 1000, 572);
            villagePath.lineTo(1000, 600);
            villagePath.lineTo(0, 600);
            villagePath.close();
            c.drawPath(villagePath, villagePaint);
        }

        private float villageHouseX(int i) { return i < 3 ? 15 + i * 115 : 655 + (i - 3) * 115; }
        private float villageHouseTop(int i) { return 220 + (i * 23 % 49); }
        private float villageLampX(int i) { return i == 0 ? 220 : i == 1 ? 780 : i == 2 ? 100 : 900; }
        private float villageLampY(int i) { return i < 2 ? 440 : 537; }

        private void drawVillageHouse(Canvas c, int i) {
            float x = villageHouseX(i), top = villageHouseTop(i);
            villagePaint.setColor(i % 3 == 0 ? 0xFF795C58 : i % 3 == 1 ? 0xFF687784 : 0xFF9D866C);
            c.drawRect(x, top, x + 107, 391, villagePaint);
            villagePaint.setColor(0xFF384456);
            villagePath.rewind();
            villagePath.moveTo(x - 7, top + 3);
            villagePath.lineTo(x + 53, top - 59);
            villagePath.lineTo(x + 114, top + 3);
            villagePath.close();
            c.drawPath(villagePath, villagePaint);
            villagePaint.setColor(0xFFDDDFDB);
            villagePaint.setStrokeWidth(6);
            c.drawLine(x - 8, top, x + 53, top - 60, villagePaint);
            c.drawLine(x + 53, top - 60, x + 114, top, villagePaint);
            villagePaint.setColor(0xFF544A49);
            c.drawRect(x + 76, top - 55, x + 90, top - 22, villagePaint);
            villagePaint.setColor(0xFFEDF0E9);
            c.drawRect(x + 73, top - 58, x + 93, top - 53, villagePaint);
            villagePaint.setColor(0xFFB1A28F);
            c.drawRect(x + 2, top + 3, x + 6, 390, villagePaint);
            c.drawRect(x + 101, top + 3, x + 105, 390, villagePaint);
            c.drawRect(x, top + 70, x + 107, top + 74, villagePaint);
            for (int w = 0; w < 6; w++) {
                float wx = x + 15 + w % 3 * 30, wy = top + 18 + w / 3 * 54;
                villagePaint.setColor(0xFF293443);
                c.drawRoundRect(wx - 3, wy - 3, wx + 20, wy + 31, 3, 3, villagePaint);
                villagePaint.setColor(w % 3 == i % 3 ? 0xFFC69660 : 0xFFEBC68B);
                c.drawRect(wx, wy, wx + 17, wy + 27, villagePaint);
                villagePaint.setColor(0xFF4C4641);
                c.drawRect(wx + 7, wy, wx + 9, wy + 27, villagePaint);
                c.drawRect(wx, wy + 12, wx + 17, wy + 14, villagePaint);
                villagePaint.setColor(0xFFDDE5E5);
                c.drawRect(wx - 4, wy + 29, wx + 21, wy + 32, villagePaint);
            }
            villagePaint.setColor(0xFF293B3B);
            c.drawRoundRect(x + 40, 348, x + 68, 394, 12, 12, villagePaint);
            villagePaint.setColor(0xFFBAA27E);
            c.drawCircle(x + 62, 377, 1.6f, villagePaint);
            // Wreath and draped garland with tiny red bows.
            villagePaint.setStyle(Paint.Style.STROKE);
            villagePaint.setStrokeWidth(4);
            villagePaint.setColor(0xFF254F41);
            c.drawCircle(x + 54, 357, 8, villagePaint);
            villagePath.rewind();
            villagePath.moveTo(x + 5, 331);
            villagePath.quadTo(x + 29, 345, x + 53, 332);
            villagePath.quadTo(x + 79, 345, x + 102, 331);
            c.drawPath(villagePath, villagePaint);
            villagePaint.setStyle(Paint.Style.FILL);
            villagePaint.setColor(0xFFA75652);
            c.drawCircle(x + 54, 365, 3, villagePaint);
            for (int j = 0; j < 6; j++) {
                villagePaint.setColor(j % 2 == 0 ? 0xFFCEAF74 : 0xFF974E4B);
                c.drawCircle(x + 10 + j * 17, 334 + (j % 3 == 1 ? 4 : 1), 1.6f, villagePaint);
            }
        }

        private void villageGlow(Canvas c, float x, float y, float rx, float ry, int alpha, boolean warm) {
            c.save();
            c.translate(x, y);
            c.scale(rx, ry);
            villagePaint.setShader(warm ? hauntedWarmth : hauntedMist);
            villagePaint.setAlpha(alpha);
            c.drawCircle(0, 0, 1, villagePaint);
            villagePaint.setShader(null);
            villagePaint.setAlpha(255);
            c.restore();
        }

        private void drawChristmasVillage(Canvas c, int width, int height, float t) {
            if (villageBackground == null) return;
            c.save();
            c.scale(width / 1000f, height / 600f);
            // Oversized canvas keeps the edge covered during the tiny whole-scene drift.
            c.translate(500 + (float) Math.sin(t * 0.012f) * 2, 300 + (float) Math.cos(t * 0.015f));
            c.scale(1.012f, 1.012f);
            c.translate(-500, -300);
            villageBounds.set(0, 0, 1000, 600);
            c.drawBitmap(villageBackground, null, villageBounds, villagePaint);
            for (int i = 0; i < 23; i++) {
                villagePaint.setColor(0xFFD7E3EE);
                villagePaint.setAlpha(35 + (int) (16 * Math.sin(t * 0.21f + i * 2.7f)));
                c.drawCircle(35 + i * 139 % 920, 25 + i * 31 % 90, 0.8f, villagePaint);
            }
            for (int i = 0; i < 6; i++) {
                float x = villageHouseX(i), top = villageHouseTop(i);
                for (int w = 0; w < 6; w++) {
                    villageGlow(c, x + 23 + w % 3 * 30, top + 31 + w / 3 * 54, 20, 27,
                            17 + (int) (3 * Math.sin(t * 0.16f + i * 1.9f + w)), true);
                }
                for (int puff = 0; puff < 4; puff++) {
                    float p = (t * (0.018f + i * 0.001f) + puff * 0.25f + i * 0.13f) % 1;
                    float sx = x + 83 + p * 23 + (float) Math.sin(t * 0.18f + puff + i) * p * 8;
                    villageGlow(c, sx, top - 60 - p * 95, 4 + p * 13, 8 + p * 20,
                            (int) (27 * Math.sin(p * Math.PI)), false);
                }
            }
            for (int i = 0; i < 68; i++) {
                float y = 145 + i * 43 % 198;
                float half = (y - 110) * 0.37f;
                float x = 500 + ((i * 73 % 101) / 50f - 1) * half;
                float pulse = 0.75f + 0.17f * (float) Math.sin(t * (0.41f + i % 7 * 0.039f) + i * 2.4f);
                villageGlow(c, x, y, 6, 6, (int) (60 * pulse), true);
                villagePaint.setColor(0xFFFFDB8E);
                villagePaint.setAlpha((int) (210 * pulse));
                c.drawCircle(x, y, 1.25f, villagePaint);
            }
            villageGlow(c, 500, 111, 26, 26, 75 + (int) (10 * Math.sin(t * 0.37f)), true);
            villagePaint.setColor(0xFFF3D69B);
            villagePath.rewind();
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI / 5 - Math.PI / 2;
                float r = i % 2 == 0 ? 11 : 4.5f;
                float x = 500 + (float) Math.cos(a) * r, y = 111 + (float) Math.sin(a) * r;
                if (i == 0) villagePath.moveTo(x, y); else villagePath.lineTo(x, y);
            }
            villagePath.close();
            c.drawPath(villagePath, villagePaint);
            float sparkle = t % 19;
            if (sparkle < 3) {
                villagePaint.setAlpha((int) (95 * Math.sin(sparkle / 3 * Math.PI)));
                villagePaint.setStrokeWidth(0.7f);
                c.drawLine(487, 99, 498, 99, villagePaint);
                c.drawLine(492, 94, 492, 105, villagePaint);
            }
            // Draw the back half of the rink first, then the foreground skaters.
            for (int pass = 0; pass < 2; pass++) for (int i = 0; i < 9; i++) {
                double angle = t * (0.032 + i * 0.0037) + i * 2.399;
                float depth = (float) Math.sin(angle);
                if ((pass == 0) != (depth < 0)) continue;
                float x = 500 + (float) Math.cos(angle) * (155 + i % 3 * 17);
                float y = 468 + depth * (31 + i % 3 * 6);
                float scale = 0.7f + (depth + 1) * 0.14f;
                villagePaint.setColor(0x223A5971);
                c.drawOval(x - 6, y, x + 8, y + 3, villagePaint);
                c.save();
                c.translate(x, y);
                c.scale(scale, scale);
                float glide = (float) Math.sin(t * 0.65f + i) * 3;
                villagePaint.setColor(0xFF273C50);
                villagePaint.setStrokeWidth(1.5f);
                c.drawLine(0, -9, -3 - glide, -1, villagePaint);
                c.drawLine(1, -9, 5 + glide, 0, villagePaint);
                villagePaint.setColor(i % 3 == 0 ? 0xFF9B4F50 : i % 3 == 1 ? 0xFF365C6D : 0xFF6B5677);
                c.drawRoundRect(-3, -21, 4, -8, 2, 2, villagePaint);
                c.drawLine(-2, -18, -8, -12 + glide, villagePaint);
                c.drawLine(3, -18, 9, -16 - glide, villagePaint);
                villagePaint.setColor(0xFFD5B7A1);
                c.drawCircle(0, -25, 3, villagePaint);
                villagePaint.setColor(0xFFE3D1B5);
                c.drawRect(-3, -28, 3, -25, villagePaint);
                c.drawLine(-5 - glide, 0, -1 - glide, 0, villagePaint);
                c.drawLine(3 + glide, 1, 7 + glide, 1, villagePaint);
                c.restore();
            }
            for (int i = 0; i < 4; i++) {
                float x = villageLampX(i), y = villageLampY(i);
                int light = 53 + (int) (4 * Math.sin(t * 0.19f + i * 1.8f));
                villageGlow(c, x, y - 104, 43, 54, light, true);
                villageGlow(c, x, y + 7, 57, 13, light / 2, true);
            }
            villageGlow(c, 500 + (float) Math.sin(t * 0.021f) * 100, 385, 470, 40, 12, false);
            for (int layer = 0; layer < 3; layer++) for (int i = 0; i < 53; i++) {
                float speed = 5 + layer * 5 + i % 7 * 0.7f;
                float y = (i * 127 + t * speed) % 650 - 25;
                float x = (i * 193 + layer * 71 + t * (1.7f + layer * 0.9f)
                        + (float) Math.sin(t * 0.17f + i) * (3 + layer * 2)) % 1100 - 50;
                villagePaint.setColor(0xFFECF2F5);
                villagePaint.setAlpha(65 + layer * 40 + i % 4 * 7);
                c.drawCircle(x, y, 0.65f + layer * 0.65f + i % 3 * 0.2f, villagePaint);
            }
            villagePaint.setAlpha(255);
            c.restore();
        }

        private void drawMovingCircles(Canvas canvas, int width, int height, float time) {
            int[] colors = {color("#2F5F8F"), color("#D9AE6F"), color("#3FA778")};
            for (int i = 0; i < colors.length; i++) {
                float x = width * (0.2f + i * 0.28f) + (float) Math.sin(time * 0.35f + i) * width * 0.06f;
                float y = height * (0.34f + i * 0.12f) + (float) Math.cos(time * 0.28f + i) * height * 0.08f;
                float radius = Math.min(width, height) * (0.16f + i * 0.035f);
                circlePaint.setColor(colors[i]);
                circlePaint.setAlpha(42);
                canvas.drawCircle(x, y, radius, circlePaint);
            }
            circlePaint.setAlpha(255);
        }

        private void drawBars(Canvas canvas, int width, int height, float time) {
            int barCount = 38;
            float gap = dp(8);
            float availableWidth = width * 0.72f;
            float barWidth = (availableWidth - gap * (barCount - 1)) / barCount;
            float startX = (width - availableWidth) / 2f;
            float centerY = height * 0.64f;

            for (int i = 0; i < barCount; i++) {
                float wave = (float) Math.sin(time * 2.2f + i * 0.48f);
                float secondWave = (float) Math.sin(time * 0.8f + i * 0.2f);
                float normalized = (wave + secondWave + 2f) / 4f;
                float barHeight = dp(34) + normalized * height * 0.18f;
                float left = startX + i * (barWidth + gap);
                float top = centerY - barHeight / 2f;
                barBounds.set(left, top, left + barWidth, top + barHeight);

                if (i % 3 == 0) {
                    barPaint.setColor(color("#D9AE6F"));
                } else if (i % 3 == 1) {
                    barPaint.setColor(color("#2F5F8F"));
                } else {
                    barPaint.setColor(color("#3FA778"));
                }
                barPaint.setAlpha(170);
                canvas.drawRoundRect(barBounds, dp(4), dp(4), barPaint);
            }
            barPaint.setAlpha(255);
        }

        private void drawWaves(Canvas canvas, int width, int height, float time) {
            int[] colors = {color("#D9AE6F"), color("#9B6D48"), color("#3FA778")};
            for (int layer = 0; layer < 4; layer++) {
                barPaint.setStyle(Paint.Style.STROKE);
                barPaint.setStrokeWidth(dp(3 + layer));
                barPaint.setColor(colors[layer % colors.length]);
                barPaint.setAlpha(75 + layer * 28);

                float previousX = 0;
                float previousY = height * (0.42f + layer * 0.08f);
                for (int x = dp(24); x <= width; x += dp(24)) {
                    float y = height * (0.42f + layer * 0.08f)
                            + (float) Math.sin(time * (0.85f + layer * 0.18f) + x * 0.008f) * height * 0.055f
                            + (float) Math.cos(time * 0.45f + x * 0.004f + layer) * height * 0.035f;
                    canvas.drawLine(previousX, previousY, x, y, barPaint);
                    previousX = x;
                    previousY = y;
                }
            }
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
        }

        private void drawOrbit(Canvas canvas, int width, int height, float time) {
            float centerX = width / 2f;
            float centerY = height / 2f;
            int[] colors = {color("#D9AE6F"), color("#F3F6F8"), color("#3FA778")};

            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint.setStrokeWidth(dp(2));
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(55);
            for (int i = 0; i < 5; i++) {
                float radius = Math.min(width, height) * (0.12f + i * 0.08f);
                canvas.drawCircle(centerX, centerY, radius, circlePaint);
            }

            circlePaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 12; i++) {
                float angle = time * (0.35f + (i % 4) * 0.08f) + i * 0.72f;
                float radius = Math.min(width, height) * (0.17f + (i % 5) * 0.06f);
                float x = centerX + (float) Math.cos(angle) * radius;
                float y = centerY + (float) Math.sin(angle * 1.15f) * radius * 0.72f;
                circlePaint.setColor(colors[i % colors.length]);
                circlePaint.setAlpha(120 + (i % 3) * 35);
                canvas.drawCircle(x, y, dp(7 + (i % 4) * 3), circlePaint);
            }
            circlePaint.setAlpha(255);
        }

        private void drawGlow(Canvas canvas, int width, int height, float time) {
            int[] colors = {color("#D9AE6F"), color("#5A4030"), color("#3FA778"), color("#F3F6F8")};
            for (int i = 0; i < 18; i++) {
                float x = width * (0.08f + (i % 6) * 0.17f)
                        + (float) Math.sin(time * 0.22f + i * 1.3f) * width * 0.055f;
                float y = height * (0.18f + (i / 6) * 0.25f)
                        + (float) Math.cos(time * 0.27f + i) * height * 0.08f;
                float radius = dp(34 + (i % 5) * 18);
                circlePaint.setColor(colors[i % colors.length]);
                circlePaint.setAlpha(26 + (i % 4) * 13);
                canvas.drawCircle(x, y, radius, circlePaint);
            }
            circlePaint.setAlpha(255);
        }

        private void drawPulse(Canvas canvas, int width, int height, float time) {
            float centerX = width / 2f;
            float centerY = height / 2f;
            circlePaint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 9; i++) {
                float progress = ((time * 0.22f + i * 0.13f) % 1f);
                float radius = Math.min(width, height) * (0.08f + progress * 0.48f);
                int alpha = Math.max(0, 145 - (int) (progress * 145));
                circlePaint.setColor(i % 2 == 0 ? color("#D9AE6F") : color("#9B6D48"));
                circlePaint.setAlpha(alpha);
                circlePaint.setStrokeWidth(dp(2 + (i % 3)));
                canvas.drawCircle(centerX, centerY, radius, circlePaint);
            }
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setAlpha(255);
        }

        private void drawRain(Canvas canvas, int width, int height, float time) {
            int drops = 42;
            float laneWidth = width / (float) drops;
            barPaint.setStrokeWidth(dp(3));
            for (int i = 0; i < drops; i++) {
                float speed = 0.14f + (i % 7) * 0.025f;
                float progress = (time * speed + i * 0.071f) % 1f;
                float x = laneWidth * i + laneWidth * 0.5f + (float) Math.sin(i * 1.7f) * dp(6);
                float y = progress * (height + dp(180)) - dp(90);
                float length = dp(42 + (i % 5) * 12);
                if (i % 3 == 0) {
                    barPaint.setColor(color("#D9AE6F"));
                } else if (i % 3 == 1) {
                    barPaint.setColor(color("#5A4030"));
                } else {
                    barPaint.setColor(color("#3FA778"));
                }
                barPaint.setAlpha(80 + (i % 4) * 25);
                canvas.drawLine(x, y, x - dp(18), y + length, barPaint);
            }
            barPaint.setAlpha(255);
        }

        private void drawGrid(Canvas canvas, int width, int height, float time) {
            float spacing = dp(74);
            float offset = (time * dp(18)) % spacing;
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeWidth(dp(1));
            barPaint.setColor(color("#D9AE6F"));
            barPaint.setAlpha(64);

            for (float x = -spacing + offset; x < width + spacing; x += spacing) {
                canvas.drawLine(x, 0, x + width * 0.12f, height, barPaint);
            }
            for (float y = -spacing + offset; y < height + spacing; y += spacing) {
                canvas.drawLine(0, y, width, y + height * 0.08f, barPaint);
            }

            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(color("#3FA778"));
            circlePaint.setAlpha(90);
            for (int i = 0; i < 12; i++) {
                float x = width * (0.1f + (i % 4) * 0.25f);
                float y = height * (0.18f + (i / 4) * 0.28f);
                float pulse = 0.6f + 0.4f * (float) Math.sin(time * 1.4f + i);
                canvas.drawCircle(x, y, dp(4 + (int) (pulse * 5)), circlePaint);
            }

            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
            circlePaint.setAlpha(255);
        }

        private void drawRadar(Canvas canvas, int width, int height, float time) {
            float centerX = width * 0.5f;
            float centerY = height * 0.52f;
            float maxRadius = Math.min(width, height) * 0.46f;

            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint.setStrokeWidth(dp(2));
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(54);
            for (int i = 1; i <= 5; i++) {
                canvas.drawCircle(centerX, centerY, maxRadius * i / 5f, circlePaint);
            }

            float angle = time * 0.75f;
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeWidth(dp(4));
            barPaint.setColor(color("#3FA778"));
            barPaint.setAlpha(150);
            canvas.drawLine(centerX, centerY,
                    centerX + (float) Math.cos(angle) * maxRadius,
                    centerY + (float) Math.sin(angle) * maxRadius,
                    barPaint);

            circlePaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 10; i++) {
                float pointAngle = i * 1.21f;
                float pointRadius = maxRadius * (0.18f + (i % 5) * 0.15f);
                float pulse = 0.45f + 0.55f * (float) Math.sin(time * 1.2f + i);
                circlePaint.setColor(i % 2 == 0 ? color("#D9AE6F") : color("#F3F6F8"));
                circlePaint.setAlpha(70 + (int) (pulse * 90));
                canvas.drawCircle(
                        centerX + (float) Math.cos(pointAngle) * pointRadius,
                        centerY + (float) Math.sin(pointAngle) * pointRadius,
                        dp(4 + (i % 3)),
                        circlePaint
                );
            }

            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
            circlePaint.setAlpha(255);
        }

        private void drawRibbon(Canvas canvas, int width, int height, float time) {
            int[] colors = {color("#D9AE6F"), color("#5A4030"), color("#3FA778")};
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeCap(Paint.Cap.ROUND);

            for (int layer = 0; layer < 6; layer++) {
                barPaint.setStrokeWidth(dp(10 - layer));
                barPaint.setColor(colors[layer % colors.length]);
                barPaint.setAlpha(58 + layer * 22);

                float previousX = -dp(40);
                float previousY = height * 0.5f;
                for (int x = -dp(40); x <= width + dp(40); x += dp(18)) {
                    float y = height * (0.36f + layer * 0.055f)
                            + (float) Math.sin(time * (0.65f + layer * 0.08f) + x * 0.009f) * height * 0.13f
                            + (float) Math.cos(time * 0.42f + x * 0.005f + layer) * height * 0.045f;
                    canvas.drawLine(previousX, previousY, x, y, barPaint);
                    previousX = x;
                    previousY = y;
                }
            }

            barPaint.setStrokeCap(Paint.Cap.BUTT);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
        }

        private void drawStars(Canvas canvas, int width, int height, float time) {
            int starCount = 72;
            circlePaint.setStyle(Paint.Style.FILL);

            for (int i = 0; i < starCount; i++) {
                float baseX = ((i * 97) % 1000) / 1000f;
                float baseY = ((i * 193) % 1000) / 1000f;
                float drift = (time * (0.012f + (i % 5) * 0.004f)) % 1f;
                float x = ((baseX + drift) % 1f) * width;
                float y = baseY * height;
                float pulse = 0.5f + 0.5f * (float) Math.sin(time * 1.7f + i);
                circlePaint.setColor(i % 4 == 0 ? color("#D9AE6F") : color("#F3F6F8"));
                circlePaint.setAlpha(45 + (int) (pulse * 120));
                canvas.drawCircle(x, y, dp(1 + (i % 3)), circlePaint);
            }

            circlePaint.setColor(color("#3FA778"));
            circlePaint.setAlpha(46);
            canvas.drawCircle(width * 0.78f, height * 0.32f, Math.min(width, height) * 0.18f, circlePaint);
            circlePaint.setAlpha(255);
        }

        private void drawChristmasSnow(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#050607"));
            drawSubtleLights(canvas, width, height, time, height * 0.1f);

            circlePaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 120; i++) {
                float baseX = ((i * 83) % 1000) / 1000f;
                float speed = 0.028f + (i % 9) * 0.007f;
                float progress = (time * speed + i * 0.037f) % 1f;
                float sway = (float) Math.sin(time * 0.45f + i * 0.72f) * width * 0.018f;
                float x = baseX * width + sway;
                float y = progress * (height + dp(80)) - dp(40);
                float pulse = 0.65f + 0.35f * (float) Math.sin(time * 1.1f + i);
                circlePaint.setColor(i % 8 == 0 ? color("#D9AE6F") : color("#F3F6F8"));
                circlePaint.setAlpha(72 + (int) (pulse * 95));
                canvas.drawCircle(x, y, dp(1 + (i % 3)), circlePaint);
            }
            circlePaint.setAlpha(255);
        }

        private void drawChristmasFireplace(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#080605"));
            drawSubtleLights(canvas, width, height, time, height * 0.12f);

            float hearthLeft = width * 0.22f;
            float hearthRight = width * 0.78f;
            float hearthTop = height * 0.5f;
            float hearthBottom = height * 0.78f;
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(color("#12100E"));
            barPaint.setAlpha(230);
            barBounds.set(hearthLeft, hearthTop, hearthRight, hearthBottom);
            canvas.drawRoundRect(barBounds, dp(22), dp(22), barPaint);

            barPaint.setColor(color("#3A2115"));
            barPaint.setAlpha(245);
            for (int i = 0; i < 3; i++) {
                float y = hearthBottom - dp(52 - i * 12);
                barBounds.set(width * (0.32f + i * 0.08f), y, width * (0.64f + i * 0.06f), y + dp(18));
                canvas.drawRoundRect(barBounds, dp(9), dp(9), barPaint);
            }

            for (int i = 0; i < 11; i++) {
                float phase = time * (1.05f + (i % 4) * 0.24f) + i * 0.8f;
                float flameHeight = height * (0.17f + (i % 5) * 0.018f)
                        + (float) Math.sin(phase) * height * 0.025f;
                float flameWidth = width * (0.035f + (i % 4) * 0.009f);
                float x = width * (0.38f + i * 0.024f) + (float) Math.sin(phase * 0.7f) * dp(7);
                float y = hearthBottom - dp(62);
                int flameColor = i % 3 == 0 ? color("#F7D37A") : (i % 3 == 1 ? color("#D9AE6F") : color("#C05B2B"));
                circlePaint.setColor(flameColor);
                circlePaint.setAlpha(115 + (i % 4) * 24);
                barBounds.set(x - flameWidth, y - flameHeight, x + flameWidth, y + dp(20));
                canvas.drawOval(barBounds, circlePaint);
            }

            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(34);
            canvas.drawCircle(width * 0.5f, height * 0.63f, Math.min(width, height) * 0.34f, circlePaint);
            circlePaint.setAlpha(255);
            barPaint.setAlpha(255);
        }

        private void drawStarfield(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020304"));
            circlePaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 180; i++) {
                float baseX = ((i * 137) % 1000) / 1000f;
                float baseY = ((i * 251) % 1000) / 1000f;
                float speed = 0.01f + (i % 6) * 0.004f;
                float y = ((baseY + time * speed) % 1f) * height;
                float x = baseX * width + (float) Math.sin(time * 0.08f + i) * width * 0.012f;
                float twinkle = 0.45f + 0.55f * (float) Math.sin(time * 1.4f + i * 0.7f);
                circlePaint.setColor(i % 11 == 0 ? color("#D9AE6F") : color("#F3F6F8"));
                circlePaint.setAlpha(45 + (int) (twinkle * 130));
                canvas.drawCircle(x, y, dp(1 + (i % 4 == 0 ? 1 : 0)), circlePaint);
            }
            circlePaint.setAlpha(255);
        }

        private void drawAurora(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020708"));
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeCap(Paint.Cap.ROUND);
            int[] colors = {color("#3FA778"), color("#2F5F8F"), color("#D9AE6F")};
            for (int layer = 0; layer < 7; layer++) {
                barPaint.setColor(colors[layer % colors.length]);
                barPaint.setAlpha(34 + layer * 12);
                barPaint.setStrokeWidth(dp(22 + layer * 5));
                float previousX = -dp(60);
                float previousY = height * (0.24f + layer * 0.035f);
                for (int x = -dp(60); x <= width + dp(80); x += dp(28)) {
                    float y = height * (0.24f + layer * 0.035f)
                            + (float) Math.sin(time * (0.18f + layer * 0.018f) + x * 0.006f + layer) * height * 0.08f
                            + (float) Math.cos(time * 0.12f + x * 0.003f) * height * 0.04f;
                    canvas.drawLine(previousX, previousY, x, y, barPaint);
                    previousX = x;
                    previousY = y;
                }
            }
            barPaint.setStrokeCap(Paint.Cap.BUTT);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
        }

        private void drawRainyWindow(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#04080B"));
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeWidth(dp(2));
            barPaint.setColor(color("#2F5F8F"));
            barPaint.setAlpha(62);
            canvas.drawLine(width * 0.5f, 0, width * 0.5f, height, barPaint);
            canvas.drawLine(0, height * 0.48f, width, height * 0.48f, barPaint);

            for (int i = 0; i < 86; i++) {
                float baseX = ((i * 47) % 1000) / 1000f;
                float speed = 0.05f + (i % 8) * 0.013f;
                float progress = (time * speed + i * 0.043f) % 1f;
                float x = baseX * width + (float) Math.sin(i) * dp(6);
                float y = progress * (height + dp(120)) - dp(60);
                float length = dp(26 + (i % 6) * 10);
                barPaint.setColor(i % 7 == 0 ? color("#D9AE6F") : color("#A9B4BC"));
                barPaint.setAlpha(45 + (i % 5) * 18);
                barPaint.setStrokeWidth(dp(1 + (i % 3)));
                canvas.drawLine(x, y, x - dp(7), y + length, barPaint);
            }

            circlePaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 18; i++) {
                float x = width * (((i * 71) % 1000) / 1000f);
                float y = height * (((i * 113) % 1000) / 1000f);
                circlePaint.setColor(color("#F3F6F8"));
                circlePaint.setAlpha(22 + (i % 4) * 9);
                canvas.drawCircle(x, y, dp(8 + (i % 4) * 5), circlePaint);
            }
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
            circlePaint.setAlpha(255);
        }

        private void drawOcean(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#02070B"));
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(48);
            canvas.drawCircle(width * 0.78f, height * 0.22f, Math.min(width, height) * 0.075f, circlePaint);

            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeCap(Paint.Cap.ROUND);
            int[] waveColors = {color("#2F5F8F"), color("#183447"), color("#D9AE6F")};
            for (int layer = 0; layer < 8; layer++) {
                barPaint.setColor(waveColors[layer % waveColors.length]);
                barPaint.setAlpha(layer == 7 ? 110 : 42 + layer * 8);
                barPaint.setStrokeWidth(dp(3 + layer));
                float baseY = height * (0.48f + layer * 0.055f);
                float previousX = -dp(40);
                float previousY = baseY;
                for (int x = -dp(40); x <= width + dp(60); x += dp(24)) {
                    float y = baseY
                            + (float) Math.sin(time * (0.26f + layer * 0.025f) + x * 0.009f) * height * 0.032f
                            + (float) Math.cos(time * 0.16f + x * 0.004f + layer) * height * 0.018f;
                    canvas.drawLine(previousX, previousY, x, y, barPaint);
                    previousX = x;
                    previousY = y;
                }
            }
            barPaint.setStrokeCap(Paint.Cap.BUTT);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
            circlePaint.setAlpha(255);
        }

        private void drawDigitalRain(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020504"));
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setTextAlign(Paint.Align.CENTER);
            barPaint.setTextSize(dp(18));
            int columns = Math.max(24, width / dp(34));
            float columnWidth = width / (float) columns;
            float rowHeight = dp(26);
            int rows = (int) (height / rowHeight) + 3;
            for (int col = 0; col < columns; col++) {
                float speed = 0.55f + (col % 7) * 0.08f;
                float head = (time * speed + col * 0.19f) % 1f;
                int headRow = (int) (head * rows);
                for (int tail = 0; tail < 9; tail++) {
                    int row = (headRow - tail + rows) % rows;
                    int alpha = Math.max(0, 118 - tail * 13);
                    barPaint.setColor(tail == 0 ? color("#D9AE6F") : color("#3FA778"));
                    barPaint.setAlpha(alpha);
                    String character = ((col + row + tail) % 2 == 0) ? "0" : "1";
                    canvas.drawText(character, col * columnWidth + columnWidth / 2f, row * rowHeight, barPaint);
                }
            }
            barPaint.setTextAlign(Paint.Align.LEFT);
            barPaint.setAlpha(255);
        }

        private void drawRetroEqualizer(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#050607"));
            int barCount = 48;
            float gap = dp(8);
            float availableWidth = width * 0.76f;
            float barWidth = (availableWidth - gap * (barCount - 1)) / barCount;
            float startX = (width - availableWidth) / 2f;
            float bottom = height * 0.72f;
            float maxHeight = height * 0.36f;

            for (int i = 0; i < barCount; i++) {
                float wave = (float) Math.sin(time * 1.6f + i * 0.38f);
                float bounce = (float) Math.sin(time * 3.4f + i * 0.81f);
                float level = (wave + bounce + 2f) / 4f;
                float barHeight = dp(22) + level * maxHeight;
                float left = startX + i * (barWidth + gap);
                barBounds.set(left, bottom - barHeight, left + barWidth, bottom);
                barPaint.setColor(i % 6 == 0 ? color("#F3F6F8") : color("#D9AE6F"));
                barPaint.setAlpha(125 + (int) (level * 95));
                canvas.drawRoundRect(barBounds, dp(5), dp(5), barPaint);
            }

            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint.setStrokeWidth(dp(2));
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(46);
            for (int i = 0; i < 5; i++) {
                canvas.drawCircle(width * 0.5f, height * 0.52f, Math.min(width, height) * (0.16f + i * 0.065f), circlePaint);
            }
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setAlpha(255);
            barPaint.setAlpha(255);
        }

        private void drawChristmasTree(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020304"));
            drawSnowLayers(canvas, width, height, time, 92);

            float cx = width * 0.5f;
            float baseY = height * 0.78f;
            drawPineTree(canvas, cx, baseY, height * 0.54f, width * 0.28f, color("#12301F"), 245);

            drawStar(canvas, cx, baseY - height * 0.55f, dp(34), color("#D9AE6F"), 210);
            int[] lightColors = {color("#D9AE6F"), color("#C94735"), color("#3FA778")};
            circlePaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 34; i++) {
                float level = i / 34f;
                float treeWidth = width * (0.08f + (1f - level) * 0.18f);
                float x = cx + (float) Math.sin(i * 2.4f) * treeWidth;
                float y = baseY - height * (0.06f + level * 0.43f);
                float pulse = 0.45f + 0.55f * (float) Math.sin(time * 1.25f + i * 0.7f);
                circlePaint.setColor(lightColors[i % lightColors.length]);
                circlePaint.setAlpha(95 + (int) (pulse * 130));
                canvas.drawCircle(x, y, dp(4 + (i % 3)), circlePaint);
            }

            for (int i = 0; i < 5; i++) {
                float left = cx - width * 0.17f + i * width * 0.085f;
                float top = baseY + dp(12 + (i % 2) * 8);
                barPaint.setStyle(Paint.Style.FILL);
                barPaint.setColor(i % 2 == 0 ? color("#5A2430") : color("#6D552A"));
                barPaint.setAlpha(235);
                barBounds.set(left, top, left + dp(58), top + dp(42));
                canvas.drawRoundRect(barBounds, dp(5), dp(5), barPaint);
                barPaint.setColor(color("#D9AE6F"));
                barPaint.setAlpha(190);
                canvas.drawRect(left + dp(25), top, left + dp(31), top + dp(42), barPaint);
            }
            circlePaint.setAlpha(255);
            barPaint.setAlpha(255);
        }

        private void drawChristmasSnowLandscape(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020712"));
            drawDeepStarfield(canvas, width, height, time * 0.35f);
            drawShootingStar(canvas, width, height, time);
            drawPineTree(canvas, width * 0.12f, height * 0.72f, height * 0.24f, width * 0.09f, color("#07130E"), 235);
            drawPineTree(canvas, width * 0.82f, height * 0.73f, height * 0.28f, width * 0.1f, color("#081A12"), 245);
            drawPineTree(canvas, width * 0.9f, height * 0.76f, height * 0.18f, width * 0.07f, color("#07130E"), 220);
            drawSubtleLights(canvas, width, height, time, height * 0.62f);

            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(color("#DDE7EC"));
            barPaint.setAlpha(42);
            barBounds.set(-dp(40), height * 0.78f, width + dp(40), height + dp(80));
            canvas.drawOval(barBounds, barPaint);
            drawSnowLayers(canvas, width, height, time, 170);
            barPaint.setAlpha(255);
        }

        private void drawChristmasLights(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#030303"));
            for (int row = 0; row < 3; row++) {
                drawSubtleLights(canvas, width, height, time + row * 0.9f, height * (0.13f + row * 0.18f));
            }
            drawSnowLayers(canvas, width, height, time * 0.8f, 70);
        }

        private void drawCozyFireplace(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#060403"));
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(28);
            canvas.drawCircle(width * 0.5f, height * 0.66f, Math.min(width, height) * 0.48f, circlePaint);
            drawSubtleLights(canvas, width, height, time, height * 0.16f);

            float left = width * 0.23f;
            float right = width * 0.77f;
            float top = height * 0.42f;
            float bottom = height * 0.78f;
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(color("#17100C"));
            barPaint.setAlpha(250);
            barBounds.set(left, top, right, bottom);
            canvas.drawRoundRect(barBounds, dp(18), dp(18), barPaint);
            barPaint.setColor(color("#3A2115"));
            for (int i = 0; i < 4; i++) {
                float y = bottom - dp(46 + i * 11);
                barBounds.set(width * (0.34f + i * 0.025f), y, width * (0.66f - i * 0.015f), y + dp(16));
                canvas.drawRoundRect(barBounds, dp(8), dp(8), barPaint);
            }
            drawLayeredFlames(canvas, width, height, time, width * 0.5f, bottom - dp(48), height * 0.28f);
            drawEmbers(canvas, width, height, time, bottom);
        }

        private void drawSnowGlobe(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#030405"));
            float cx = width * 0.5f;
            float cy = height * 0.5f;
            float radius = Math.min(width, height) * 0.29f;
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(color("#10212A"));
            circlePaint.setAlpha(120);
            canvas.drawCircle(cx, cy, radius, circlePaint);
            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint.setStrokeWidth(dp(4));
            circlePaint.setColor(color("#F3F6F8"));
            circlePaint.setAlpha(82);
            canvas.drawCircle(cx, cy, radius, circlePaint);

            drawPineTree(canvas, cx, cy + radius * 0.45f, radius * 1.25f, radius * 0.48f, color("#12301F"), 245);
            drawStar(canvas, cx, cy - radius * 0.32f, dp(18), color("#D9AE6F"), 190);
            circlePaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 78; i++) {
                float angle = i * 2.17f;
                float r = radius * (0.08f + ((i * 37) % 100) / 130f);
                float drift = (time * (0.03f + (i % 7) * 0.006f) + i * 0.031f) % 1f;
                float x = cx + (float) Math.cos(angle) * r * 0.88f;
                float y = cy - radius * 0.75f + drift * radius * 1.5f;
                float localX = x - cx;
                float localY = y - cy;
                if (localX * localX + localY * localY < radius * radius * 0.88f) {
                    circlePaint.setColor(i % 9 == 0 ? color("#D9AE6F") : color("#F3F6F8"));
                    circlePaint.setAlpha(75 + (i % 4) * 28);
                    canvas.drawCircle(x, y, dp(2), circlePaint);
                }
            }
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(color("#2A2018"));
            barBounds.set(cx - radius * 0.46f, cy + radius * 0.88f, cx + radius * 0.46f, cy + radius * 1.08f);
            canvas.drawRoundRect(barBounds, dp(10), dp(10), barPaint);
            circlePaint.setAlpha(255);
            barPaint.setAlpha(255);
        }

        private void drawWinterCabin(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020713"));
            drawDeepStarfield(canvas, width, height, time * 0.25f);
            drawSnowLayers(canvas, width, height, time, 120);
            for (int i = 0; i < 8; i++) {
                drawPineTree(canvas, width * (0.08f + i * 0.12f), height * (0.76f + (i % 2) * 0.025f),
                        height * (0.18f + (i % 3) * 0.035f), width * 0.065f, color("#07140F"), 220);
            }
            drawCabin(canvas, width, height, time);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(color("#F3F6F8"));
            barPaint.setAlpha(54);
            barBounds.set(-dp(50), height * 0.79f, width + dp(50), height + dp(80));
            canvas.drawOval(barBounds, barPaint);
        }

        private void drawGalaxy(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020207"));
            float cx = width * 0.5f;
            float cy = height * 0.5f;
            circlePaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 260; i++) {
                float arm = i % 4;
                float distance = ((i * 37) % 1000) / 1000f;
                float angle = distance * 5.8f + arm * 1.57f + time * 0.035f;
                float r = Math.min(width, height) * (0.04f + distance * 0.42f);
                float x = cx + (float) Math.cos(angle) * r * 1.45f;
                float y = cy + (float) Math.sin(angle) * r * 0.58f;
                circlePaint.setColor(i % 9 == 0 ? color("#D9AE6F") : (i % 5 == 0 ? color("#2F5F8F") : color("#F3F6F8")));
                circlePaint.setAlpha(35 + (int) ((1f - distance) * 155));
                canvas.drawCircle(x, y, dp(1 + (i % 11 == 0 ? 2 : 0)), circlePaint);
            }
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(36);
            canvas.drawCircle(cx, cy, Math.min(width, height) * 0.16f, circlePaint);
            circlePaint.setAlpha(255);
        }

        private void drawNorthernLights(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#01080A"));
            drawDeepStarfield(canvas, width, height, time * 0.12f);
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeCap(Paint.Cap.ROUND);
            int[] colors = {color("#2FE49B"), color("#2F5F8F"), color("#8A6BE8"), color("#D9AE6F")};
            for (int layer = 0; layer < 10; layer++) {
                barPaint.setColor(colors[layer % colors.length]);
                barPaint.setAlpha(26 + layer * 8);
                barPaint.setStrokeWidth(dp(28 + layer * 4));
                float previousX = -dp(80);
                float previousY = height * (0.18f + layer * 0.028f);
                for (int x = -dp(80); x <= width + dp(100); x += dp(24)) {
                    float y = height * (0.18f + layer * 0.028f)
                            + (float) Math.sin(time * (0.13f + layer * 0.012f) + x * 0.006f + layer) * height * 0.1f
                            + (float) Math.sin(time * 0.08f + x * 0.002f) * height * 0.06f;
                    canvas.drawLine(previousX, previousY, x, y, barPaint);
                    previousX = x;
                    previousY = y;
                }
            }
            barPaint.setStrokeCap(Paint.Cap.BUTT);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
        }

        private void drawFloatingBubbles(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#02080B"));
            circlePaint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 54; i++) {
                float size = dp(12 + (i % 9) * 7);
                float x = width * (((i * 71) % 1000) / 1000f) + (float) Math.sin(time * 0.25f + i) * width * 0.025f;
                float y = height - ((time * (0.026f + (i % 8) * 0.006f) + i * 0.041f) % 1f) * (height + dp(90));
                circlePaint.setColor(i % 6 == 0 ? color("#D9AE6F") : color("#F3F6F8"));
                circlePaint.setAlpha(38 + (i % 5) * 14);
                circlePaint.setStrokeWidth(dp(1 + (i % 2)));
                canvas.drawCircle(x, y, size, circlePaint);
            }
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setAlpha(255);
        }

        private void drawFireflies(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020503"));
            drawPineTree(canvas, width * 0.12f, height * 0.83f, height * 0.28f, width * 0.1f, color("#06110A"), 180);
            drawPineTree(canvas, width * 0.88f, height * 0.84f, height * 0.25f, width * 0.09f, color("#06110A"), 180);
            circlePaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 64; i++) {
                float x = width * (((i * 103) % 1000) / 1000f)
                        + (float) Math.sin(time * (0.18f + (i % 5) * 0.03f) + i) * width * 0.055f;
                float y = height * (0.25f + (((i * 67) % 620) / 1000f))
                        + (float) Math.cos(time * (0.2f + (i % 4) * 0.04f) + i) * height * 0.04f;
                float pulse = 0.5f + 0.5f * (float) Math.sin(time * 1.8f + i * 0.83f);
                circlePaint.setColor(i % 5 == 0 ? color("#3FA778") : color("#D9AE6F"));
                circlePaint.setAlpha(35 + (int) (pulse * 185));
                canvas.drawCircle(x, y, dp(3 + (i % 3)), circlePaint);
            }
            circlePaint.setAlpha(255);
        }

        private void drawNeonWaves(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#030308"));
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeCap(Paint.Cap.ROUND);
            int[] colors = {color("#D9AE6F"), color("#2F5F8F"), color("#E24AA0"), color("#3FA778")};
            for (int layer = 0; layer < 9; layer++) {
                barPaint.setColor(colors[layer % colors.length]);
                barPaint.setAlpha(68 + layer * 12);
                barPaint.setStrokeWidth(dp(3 + (layer % 3)));
                float previousX = -dp(40);
                float previousY = height * 0.5f;
                for (int x = -dp(40); x <= width + dp(40); x += dp(16)) {
                    float y = height * (0.3f + layer * 0.055f)
                            + (float) Math.sin(time * (0.75f + layer * 0.07f) + x * 0.012f) * height * 0.075f
                            + (float) Math.cos(time * 0.32f + x * 0.006f + layer) * height * 0.04f;
                    canvas.drawLine(previousX, previousY, x, y, barPaint);
                    previousX = x;
                    previousY = y;
                }
            }
            barPaint.setStrokeCap(Paint.Cap.BUTT);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
        }

        private void drawLavaLamp(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#050304"));
            circlePaint.setStyle(Paint.Style.FILL);
            int[] colors = {color("#D9AE6F"), color("#C94735"), color("#7B3F98"), color("#3FA778")};
            for (int i = 0; i < 18; i++) {
                float x = width * (0.16f + (i % 6) * 0.14f)
                        + (float) Math.sin(time * (0.16f + i * 0.008f) + i) * width * 0.055f;
                float y = height * (0.18f + (i / 6) * 0.24f)
                        + (float) Math.cos(time * (0.19f + i * 0.01f) + i * 1.7f) * height * 0.12f;
                float radius = Math.min(width, height) * (0.045f + (i % 5) * 0.018f);
                circlePaint.setColor(colors[i % colors.length]);
                circlePaint.setAlpha(58 + (i % 4) * 20);
                canvas.drawCircle(x, y, radius, circlePaint);
            }
            circlePaint.setAlpha(255);
        }

        private void drawDeepStarfield(Canvas canvas, int width, int height, float time) {
            circlePaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 230; i++) {
                float x = width * (((i * 149) % 1000) / 1000f);
                float y = height * ((((i * 263) % 1000) / 1000f + time * (0.002f + (i % 4) * 0.001f)) % 1f);
                float pulse = 0.45f + 0.55f * (float) Math.sin(time * 1.1f + i * 0.53f);
                circlePaint.setColor(i % 13 == 0 ? color("#D9AE6F") : color("#F3F6F8"));
                circlePaint.setAlpha(32 + (int) (pulse * 145));
                canvas.drawCircle(x, y, dp(1 + (i % 9 == 0 ? 2 : 0)), circlePaint);
            }
            circlePaint.setAlpha(255);
        }

        private void drawRetroGoldEqualizer(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#040404"));
            int columns = 56;
            float gap = dp(7);
            float availableWidth = width * 0.8f;
            float barWidth = (availableWidth - gap * (columns - 1)) / columns;
            float startX = (width - availableWidth) / 2f;
            float centerY = height * 0.62f;
            for (int i = 0; i < columns; i++) {
                float level = ((float) Math.sin(time * 1.35f + i * 0.32f)
                        + (float) Math.sin(time * 2.6f + i * 0.71f) + 2f) / 4f;
                int blocks = 3 + (int) (level * 11);
                for (int b = 0; b < blocks; b++) {
                    float blockHeight = dp(12);
                    float top = centerY - b * dp(17);
                    barBounds.set(startX + i * (barWidth + gap), top, startX + i * (barWidth + gap) + barWidth, top + blockHeight);
                    barPaint.setColor(b > 8 ? color("#F3F6F8") : color("#D9AE6F"));
                    barPaint.setAlpha(105 + b * 10);
                    canvas.drawRoundRect(barBounds, dp(3), dp(3), barPaint);
                }
            }
            barPaint.setAlpha(255);
        }

        private void drawSnowLayers(Canvas canvas, int width, int height, float time, int count) {
            circlePaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < count; i++) {
                float depth = 0.45f + (i % 5) * 0.14f;
                float x = width * (((i * 89) % 1000) / 1000f)
                        + (float) Math.sin(time * 0.32f + i) * width * 0.018f * depth;
                float y = ((time * (0.018f + (i % 8) * 0.004f) * depth + i * 0.037f) % 1f) * (height + dp(70)) - dp(35);
                circlePaint.setColor(color("#F3F6F8"));
                circlePaint.setAlpha(45 + (int) (depth * 72));
                canvas.drawCircle(x, y, dp(1 + (i % 3)), circlePaint);
            }
            circlePaint.setAlpha(255);
        }

        private void drawPineTree(Canvas canvas, float cx, float baseY, float treeHeight, float treeWidth, int fillColor, int alpha) {
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(fillColor);
            barPaint.setAlpha(alpha);
            for (int layer = 0; layer < 4; layer++) {
                float top = baseY - treeHeight + layer * treeHeight * 0.2f;
                float bottom = baseY - treeHeight * (0.48f - layer * 0.12f);
                float half = treeWidth * (0.42f + layer * 0.22f);
                Path path = new Path();
                path.moveTo(cx, top);
                path.lineTo(cx - half, bottom);
                path.lineTo(cx + half, bottom);
                path.close();
                canvas.drawPath(path, barPaint);
            }
            barPaint.setColor(color("#3A2115"));
            barBounds.set(cx - treeWidth * 0.08f, baseY - treeHeight * 0.08f, cx + treeWidth * 0.08f, baseY);
            canvas.drawRoundRect(barBounds, dp(3), dp(3), barPaint);
            barPaint.setAlpha(255);
        }

        private void drawStar(Canvas canvas, float cx, float cy, float radius, int fillColor, int alpha) {
            Path path = new Path();
            for (int i = 0; i < 10; i++) {
                double angle = -Math.PI / 2d + i * Math.PI / 5d;
                float r = i % 2 == 0 ? radius : radius * 0.43f;
                float x = cx + (float) Math.cos(angle) * r;
                float y = cy + (float) Math.sin(angle) * r;
                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            path.close();
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(fillColor);
            barPaint.setAlpha(alpha);
            canvas.drawPath(path, barPaint);
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(fillColor);
            circlePaint.setAlpha(38);
            canvas.drawCircle(cx, cy, radius * 1.8f, circlePaint);
            barPaint.setAlpha(255);
            circlePaint.setAlpha(255);
        }

        private void drawShootingStar(Canvas canvas, int width, int height, float time) {
            float progress = (time * 0.08f) % 1f;
            if (progress > 0.26f) {
                return;
            }
            float t = progress / 0.26f;
            float x = width * (0.12f + t * 0.42f);
            float y = height * (0.18f + t * 0.16f);
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeWidth(dp(3));
            barPaint.setColor(color("#D9AE6F"));
            barPaint.setAlpha(180 - (int) (t * 120));
            canvas.drawLine(x, y, x - width * 0.09f, y - height * 0.035f, barPaint);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
        }

        private void drawLayeredFlames(Canvas canvas, int width, int height, float time, float cx, float baseY, float maxHeight) {
            int[] colors = {color("#F7D37A"), color("#D9AE6F"), color("#C05B2B"), color("#7A2418")};
            for (int i = 0; i < 18; i++) {
                float phase = time * (1.15f + (i % 5) * 0.18f) + i * 0.66f;
                float flameHeight = maxHeight * (0.42f + (i % 6) * 0.055f)
                        + (float) Math.sin(phase) * maxHeight * 0.09f;
                float flameWidth = width * (0.018f + (i % 5) * 0.006f);
                float x = cx + (i - 8.5f) * width * 0.019f + (float) Math.sin(phase * 0.7f) * dp(9);
                circlePaint.setColor(colors[i % colors.length]);
                circlePaint.setAlpha(95 + (i % 5) * 28);
                barBounds.set(x - flameWidth, baseY - flameHeight, x + flameWidth, baseY + dp(20));
                canvas.drawOval(barBounds, circlePaint);
            }
            circlePaint.setAlpha(255);
        }

        private void drawEmbers(Canvas canvas, int width, int height, float time, float baseY) {
            circlePaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 30; i++) {
                float x = width * (0.32f + ((i * 37) % 360) / 1000f);
                float y = baseY - ((time * (0.02f + (i % 5) * 0.008f) + i * 0.053f) % 1f) * height * 0.22f;
                float glow = 0.45f + 0.55f * (float) Math.sin(time * 1.4f + i);
                circlePaint.setColor(i % 3 == 0 ? color("#F7D37A") : color("#D9AE6F"));
                circlePaint.setAlpha(30 + (int) (glow * 100));
                canvas.drawCircle(x, y, dp(2 + (i % 3)), circlePaint);
            }
            circlePaint.setAlpha(255);
        }

        private void drawCabin(Canvas canvas, int width, int height, float time) {
            float left = width * 0.36f;
            float top = height * 0.56f;
            float right = width * 0.64f;
            float bottom = height * 0.78f;
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(color("#2A1710"));
            barPaint.setAlpha(245);
            barBounds.set(left, top, right, bottom);
            canvas.drawRoundRect(barBounds, dp(5), dp(5), barPaint);

            Path roof = new Path();
            roof.moveTo(left - dp(30), top + dp(8));
            roof.lineTo(width * 0.5f, top - dp(78));
            roof.lineTo(right + dp(30), top + dp(8));
            roof.close();
            barPaint.setColor(color("#17100C"));
            canvas.drawPath(roof, barPaint);

            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(210);
            barBounds.set(left + dp(38), top + dp(48), left + dp(86), top + dp(92));
            canvas.drawRoundRect(barBounds, dp(5), dp(5), circlePaint);
            barBounds.set(right - dp(86), top + dp(48), right - dp(38), top + dp(92));
            canvas.drawRoundRect(barBounds, dp(5), dp(5), circlePaint);

            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeWidth(dp(5));
            barPaint.setColor(color("#A9B4BC"));
            barPaint.setAlpha(42);
            float smokeX = right - dp(38);
            float smokeBase = top - dp(66);
            for (int i = 0; i < 3; i++) {
                float y = smokeBase - i * dp(30) - (time * dp(8) % dp(30));
                canvas.drawCircle(smokeX + (float) Math.sin(time * 0.4f + i) * dp(18), y, dp(14 + i * 5), barPaint);
            }
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
            circlePaint.setAlpha(255);
        }

        private void drawFireworks(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#02030A"));
            drawDeepStarfield(canvas, width, height, time * 0.2f);
            int[] colors = {color("#D9AE6F"), color("#C94735"), color("#2F5F8F"), color("#3FA778"), color("#F3F6F8")};

            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 5; i++) {
                float cycle = (time * 0.13f + i * 0.19f) % 1f;
                float launchX = width * (0.18f + ((i * 17) % 64) / 100f);
                float explodeY = height * (0.2f + (i % 3) * 0.1f);
                if (cycle < 0.35f) {
                    float p = cycle / 0.35f;
                    float y = height * 0.82f - p * (height * 0.62f - explodeY);
                    barPaint.setColor(colors[i % colors.length]);
                    barPaint.setAlpha(125);
                    barPaint.setStrokeWidth(dp(2));
                    canvas.drawLine(launchX, y + dp(44), launchX, y, barPaint);
                    circlePaint.setStyle(Paint.Style.FILL);
                    circlePaint.setColor(colors[i % colors.length]);
                    circlePaint.setAlpha(180);
                    canvas.drawCircle(launchX, y, dp(3), circlePaint);
                } else {
                    float p = (cycle - 0.35f) / 0.65f;
                    float radius = Math.min(width, height) * (0.04f + p * (0.12f + (i % 3) * 0.025f));
                    int alpha = Math.max(0, 190 - (int) (p * 185));
                    int particles = 22 + (i % 3) * 10;
                    barPaint.setStrokeWidth(dp(2));
                    for (int j = 0; j < particles; j++) {
                        double angle = j * Math.PI * 2d / particles + i * 0.31d;
                        float x1 = launchX + (float) Math.cos(angle) * radius * 0.72f;
                        float y1 = explodeY + (float) Math.sin(angle) * radius * 0.72f + p * p * dp(36);
                        float x2 = launchX + (float) Math.cos(angle) * radius;
                        float y2 = explodeY + (float) Math.sin(angle) * radius + p * p * dp(42);
                        barPaint.setColor(colors[(i + j) % colors.length]);
                        barPaint.setAlpha(alpha);
                        canvas.drawLine(x1, y1, x2, y2, barPaint);
                    }
                }
            }
            barPaint.setStrokeCap(Paint.Cap.BUTT);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
            circlePaint.setAlpha(255);
        }

        private void drawMeteorShower(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#01030A"));
            drawDeepStarfield(canvas, width, height, time * 0.45f);
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 9; i++) {
                float cycle = (time * (0.035f + (i % 4) * 0.009f) + i * 0.17f) % 1f;
                if (cycle > 0.42f) {
                    continue;
                }
                float p = cycle / 0.42f;
                float startX = width * (0.15f + ((i * 109) % 80) / 100f);
                float startY = height * (0.06f + ((i * 67) % 32) / 100f);
                float x = startX - p * width * 0.32f;
                float y = startY + p * height * 0.22f;
                int alpha = 170 - (int) (p * 120);
                barPaint.setColor(i % 3 == 0 ? color("#D9AE6F") : color("#F3F6F8"));
                barPaint.setAlpha(alpha);
                barPaint.setStrokeWidth(dp(2 + (i % 3)));
                canvas.drawLine(x, y, x + width * 0.12f, y - height * 0.08f, barPaint);
            }
            barPaint.setStrokeCap(Paint.Cap.BUTT);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
        }

        private void drawFloatingLanterns(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#030305"));
            circlePaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 18; i++) {
                float depth = 0.6f + (i % 5) * 0.12f;
                float x = width * (((i * 137) % 1000) / 1000f) + (float) Math.sin(time * 0.16f + i) * width * 0.025f;
                float y = height - ((time * (0.018f + (i % 4) * 0.006f) + i * 0.071f) % 1f) * (height + dp(130));
                float size = dp(28 + (i % 4) * 8) * depth;
                circlePaint.setColor(color("#D9AE6F"));
                circlePaint.setAlpha(24 + (i % 3) * 10);
                canvas.drawCircle(x, y, size * 1.8f, circlePaint);
                barPaint.setStyle(Paint.Style.FILL);
                barPaint.setColor(color("#8A4A22"));
                barPaint.setAlpha(210);
                barBounds.set(x - size * 0.44f, y - size * 0.48f, x + size * 0.44f, y + size * 0.56f);
                canvas.drawRoundRect(barBounds, dp(6), dp(6), barPaint);
                circlePaint.setColor(color("#F7D37A"));
                circlePaint.setAlpha(145 + (int) (50 * Math.sin(time * 1.5f + i)));
                canvas.drawCircle(x, y + size * 0.1f, size * 0.22f, circlePaint);
            }
            circlePaint.setAlpha(255);
            barPaint.setAlpha(255);
        }

        private void drawUnderwater(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#02111D"));
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 7; i++) {
                float x = width * (0.12f + i * 0.13f) + (float) Math.sin(time * 0.12f + i) * width * 0.04f;
                barPaint.setColor(color("#7FB5C8"));
                barPaint.setAlpha(22 + i * 3);
                barPaint.setStrokeWidth(dp(18 + i * 2));
                canvas.drawLine(x, 0, x - width * 0.13f, height, barPaint);
            }

            circlePaint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 64; i++) {
                float size = dp(5 + (i % 7) * 4);
                float x = width * (((i * 79) % 1000) / 1000f) + (float) Math.sin(time * 0.28f + i) * width * 0.022f;
                float y = height - ((time * (0.032f + (i % 6) * 0.007f) + i * 0.047f) % 1f) * (height + dp(70));
                circlePaint.setColor(i % 8 == 0 ? color("#D9AE6F") : color("#A9B4BC"));
                circlePaint.setAlpha(42 + (i % 4) * 15);
                circlePaint.setStrokeWidth(dp(1));
                canvas.drawCircle(x, y, size, circlePaint);
            }

            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(color("#061017"));
            barPaint.setAlpha(135);
            for (int i = 0; i < 7; i++) {
                float x = width * (((i * 151) % 1000) / 1000f);
                float y = height * (0.28f + (i % 4) * 0.12f) + (float) Math.sin(time * 0.22f + i) * height * 0.025f;
                float fish = dp(22 + (i % 3) * 8);
                Path path = new Path();
                path.moveTo(x - fish, y);
                path.quadTo(x, y - fish * 0.45f, x + fish, y);
                path.quadTo(x, y + fish * 0.45f, x - fish, y);
                path.close();
                canvas.drawPath(path, barPaint);
                path.reset();
                path.moveTo(x - fish, y);
                path.lineTo(x - fish * 1.45f, y - fish * 0.42f);
                path.lineTo(x - fish * 1.45f, y + fish * 0.42f);
                path.close();
                canvas.drawPath(path, barPaint);
            }
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setAlpha(255);
            barPaint.setStrokeCap(Paint.Cap.BUTT);
            barPaint.setAlpha(255);
        }

        private void drawPlasma(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#040307"));
            int[] colors = {color("#D9AE6F"), color("#6E4AA8"), color("#2F5F8F"), color("#3FA778")};
            circlePaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 22; i++) {
                float x = width * (0.08f + (i % 6) * 0.18f)
                        + (float) Math.sin(time * (0.16f + i * 0.006f) + i * 0.7f) * width * 0.09f;
                float y = height * (0.12f + (i / 6) * 0.22f)
                        + (float) Math.cos(time * (0.18f + i * 0.007f) + i * 1.2f) * height * 0.09f;
                float r = Math.min(width, height) * (0.07f + (i % 5) * 0.022f);
                circlePaint.setColor(colors[i % colors.length]);
                circlePaint.setAlpha(28 + (i % 4) * 10);
                canvas.drawCircle(x, y, r, circlePaint);
            }
            circlePaint.setAlpha(255);
        }

        private void drawVinyl(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#030303"));
            float cx = width * 0.5f;
            float cy = height * 0.54f;
            float radius = Math.min(width, height) * 0.34f;
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(color("#070707"));
            canvas.drawCircle(cx, cy, radius, circlePaint);

            circlePaint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 14; i++) {
                circlePaint.setStrokeWidth(dp(i % 4 == 0 ? 2 : 1));
                circlePaint.setColor(i % 5 == 0 ? color("#D9AE6F") : color("#303030"));
                circlePaint.setAlpha(i % 5 == 0 ? 70 : 120);
                canvas.drawCircle(cx, cy, radius * (0.2f + i * 0.055f), circlePaint);
            }

            float angle = time * 0.28f;
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeWidth(dp(4));
            barPaint.setColor(color("#F3F6F8"));
            barPaint.setAlpha(54);
            canvas.drawLine(cx, cy,
                    cx + (float) Math.cos(angle) * radius * 0.9f,
                    cy + (float) Math.sin(angle) * radius * 0.9f,
                    barPaint);

            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(230);
            canvas.drawCircle(cx, cy, radius * 0.18f, circlePaint);
            circlePaint.setColor(color("#050607"));
            canvas.drawCircle(cx, cy, radius * 0.045f, circlePaint);
            circlePaint.setAlpha(255);
            barPaint.setAlpha(255);
        }

        private void drawCityLights(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#02040A"));
            drawDeepStarfield(canvas, width, height, time * 0.15f);
            drawShootingStar(canvas, width, height, time * 0.55f);

            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(color("#12171B"));
            barPaint.setAlpha(92);
            for (int i = 0; i < 6; i++) {
                float cloudX = ((time * (0.01f + i * 0.002f) + i * 0.19f) % 1f) * (width + dp(180)) - dp(90);
                float cloudY = height * (0.16f + (i % 3) * 0.08f);
                barBounds.set(cloudX, cloudY, cloudX + dp(190), cloudY + dp(32));
                canvas.drawOval(barBounds, barPaint);
            }

            for (int b = 0; b < 14; b++) {
                float buildingWidth = width / 14.5f;
                float left = b * buildingWidth;
                float top = height * (0.46f + ((b * 37) % 22) / 100f);
                barPaint.setColor(color("#080B0E"));
                barPaint.setAlpha(245);
                canvas.drawRect(left, top, left + buildingWidth * 0.86f, height, barPaint);
                for (int row = 0; row < 6; row++) {
                    for (int col = 0; col < 3; col++) {
                        boolean lit = ((b * 13 + row * 7 + col * 5 + (int) (time * 0.35f)) % 5) == 0;
                        barPaint.setColor(lit ? color("#D9AE6F") : color("#1C2329"));
                        barPaint.setAlpha(lit ? 170 : 90);
                        float wx = left + buildingWidth * (0.18f + col * 0.22f);
                        float wy = top + dp(22 + row * 28);
                        canvas.drawRect(wx, wy, wx + dp(8), wy + dp(14), barPaint);
                    }
                }
            }
            barPaint.setAlpha(255);
        }

        private void drawForestFireflies(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#010402"));
            for (int i = 0; i < 9; i++) {
                drawPineTree(canvas, width * (0.05f + i * 0.12f), height * 0.88f,
                        height * (0.24f + (i % 3) * 0.05f), width * 0.08f, color("#05100A"), 165);
            }
            circlePaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 95; i++) {
                float x = width * (((i * 97) % 1000) / 1000f)
                        + (float) Math.sin(time * (0.12f + (i % 5) * 0.024f) + i) * width * 0.045f;
                float y = height * (0.22f + ((i * 61) % 650) / 1000f)
                        + (float) Math.cos(time * (0.14f + (i % 4) * 0.026f) + i) * height * 0.04f;
                float pulse = 0.45f + 0.55f * (float) Math.sin(time * 1.45f + i * 0.91f);
                circlePaint.setColor(i % 6 == 0 ? color("#3FA778") : color("#D9AE6F"));
                circlePaint.setAlpha(22 + (int) (pulse * 160));
                canvas.drawCircle(x, y, dp(2 + (i % 4)), circlePaint);
            }
            circlePaint.setAlpha(255);
        }

        private void drawGeometricTunnel(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020202"));
            float cx = width * 0.5f;
            float cy = height * 0.52f;
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeWidth(dp(2));
            for (int i = 0; i < 18; i++) {
                float progress = ((time * 0.08f + i * 0.075f) % 1f);
                float size = Math.min(width, height) * (0.08f + progress * 0.72f);
                float alpha = 1f - progress;
                barPaint.setColor(i % 3 == 0 ? color("#D9AE6F") : color("#2A3035"));
                barPaint.setAlpha((int) (42 + alpha * 130));
                float rotation = time * 0.08f + i * 0.09f;
                drawRotatedDiamond(canvas, cx, cy, size, rotation);
            }
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
        }

        private void drawAnalogClock(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#030303"));
            drawDeepStarfield(canvas, width, height, time * 0.08f);
            float cx = width * 0.5f;
            float cy = height * 0.53f;
            float radius = Math.min(width, height) * 0.31f;

            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint.setStrokeWidth(dp(3));
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(185);
            canvas.drawCircle(cx, cy, radius, circlePaint);
            for (int i = 0; i < 60; i++) {
                double angle = -Math.PI / 2d + i * Math.PI * 2d / 60d;
                float inner = radius * (i % 5 == 0 ? 0.86f : 0.92f);
                float outer = radius * 0.98f;
                barPaint.setStyle(Paint.Style.STROKE);
                barPaint.setStrokeWidth(dp(i % 5 == 0 ? 3 : 1));
                barPaint.setColor(i % 5 == 0 ? color("#D9AE6F") : color("#A9B4BC"));
                barPaint.setAlpha(i % 5 == 0 ? 180 : 95);
                canvas.drawLine(
                        cx + (float) Math.cos(angle) * inner,
                        cy + (float) Math.sin(angle) * inner,
                        cx + (float) Math.cos(angle) * outer,
                        cy + (float) Math.sin(angle) * outer,
                        barPaint
                );
            }

            Calendar calendar = Calendar.getInstance();
            float second = calendar.get(Calendar.SECOND) + calendar.get(Calendar.MILLISECOND) / 1000f;
            float minute = calendar.get(Calendar.MINUTE) + second / 60f;
            float hour = (calendar.get(Calendar.HOUR) % 12) + minute / 60f;
            drawClockHand(canvas, cx, cy, radius * 0.48f, hour / 12f, dp(6), color("#D9AE6F"), 230);
            drawClockHand(canvas, cx, cy, radius * 0.68f, minute / 60f, dp(4), color("#F3F6F8"), 210);
            drawClockHand(canvas, cx, cy, radius * 0.76f, second / 60f, dp(2), color("#C94735"), 160);
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(230);
            canvas.drawCircle(cx, cy, dp(7), circlePaint);
            circlePaint.setAlpha(255);
            barPaint.setAlpha(255);
        }

        private void drawJellyfish(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#02101A"));
            drawUnderwaterLightRays(canvas, width, height, time);
            drawUnderwaterBubbles(canvas, width, height, time, 42);
            int[] colors = {color("#D9AE6F"), color("#7FB5C8"), color("#8A6BE8")};
            for (int i = 0; i < 7; i++) {
                float x = width * (0.14f + (i % 4) * 0.23f) + (float) Math.sin(time * 0.18f + i) * width * 0.035f;
                float y = height * (0.28f + (i / 4) * 0.28f) + (float) Math.cos(time * 0.15f + i) * height * 0.05f;
                float size = Math.min(width, height) * (0.05f + (i % 3) * 0.018f);
                circlePaint.setStyle(Paint.Style.FILL);
                circlePaint.setColor(colors[i % colors.length]);
                circlePaint.setAlpha(52);
                barBounds.set(x - size, y - size * 0.56f, x + size, y + size * 0.66f);
                canvas.drawOval(barBounds, circlePaint);
                barPaint.setStyle(Paint.Style.STROKE);
                barPaint.setStrokeCap(Paint.Cap.ROUND);
                barPaint.setStrokeWidth(dp(2));
                barPaint.setColor(colors[i % colors.length]);
                barPaint.setAlpha(90);
                for (int t = 0; t < 7; t++) {
                    float tx = x - size * 0.72f + t * size * 0.24f;
                    float prevX = tx;
                    float prevY = y + size * 0.42f;
                    for (int s = 1; s <= 8; s++) {
                        float yy = y + size * 0.42f + s * size * 0.22f;
                        float xx = tx + (float) Math.sin(time * 0.8f + s * 0.5f + t + i) * size * 0.11f;
                        canvas.drawLine(prevX, prevY, xx, yy, barPaint);
                        prevX = xx;
                        prevY = yy;
                    }
                }
            }
            barPaint.setStrokeCap(Paint.Cap.BUTT);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
            circlePaint.setAlpha(255);
        }

        private void drawButterfliesAtNight(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020306"));
            drawForestFireflies(canvas, width, height, time * 0.75f);
            int[] colors = {color("#D9AE6F"), color("#8A6BE8"), color("#C94735"), color("#3FA778")};
            for (int i = 0; i < 16; i++) {
                float x = width * (((i * 79) % 1000) / 1000f)
                        + (float) Math.sin(time * (0.13f + (i % 4) * 0.025f) + i) * width * 0.08f;
                float y = height * (0.2f + ((i * 53) % 600) / 1000f)
                        + (float) Math.cos(time * (0.15f + (i % 5) * 0.018f) + i) * height * 0.06f;
                drawButterfly(canvas, x, y, dp(10 + (i % 4) * 4), colors[i % colors.length], time + i);
            }
        }

        private void drawMoonAndClouds(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020611"));
            drawDeepStarfield(canvas, width, height, time * 0.08f);
            float moonX = width * 0.72f;
            float moonY = height * 0.24f;
            float moonR = Math.min(width, height) * 0.12f;
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(color("#F3F6F8"));
            circlePaint.setAlpha(218);
            canvas.drawCircle(moonX, moonY, moonR, circlePaint);
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(24);
            canvas.drawCircle(moonX, moonY, moonR * 1.65f, circlePaint);
            for (int i = 0; i < 8; i++) {
                float depth = 0.6f + (i % 3) * 0.2f;
                float cloudX = ((time * (0.018f + i * 0.004f) + i * 0.21f) % 1f) * (width + dp(260)) - dp(130);
                float cloudY = height * (0.14f + (i % 4) * 0.12f);
                drawCloud(canvas, cloudX, cloudY, dp(78 + (i % 4) * 22) * depth, color("#12171B"), 130 - (i % 3) * 22);
            }
            circlePaint.setAlpha(255);
        }

        private void drawSaturnPlanets(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020208"));
            drawDeepStarfield(canvas, width, height, time * 0.12f);
            float cx = width * 0.5f;
            float cy = height * 0.52f;
            float r = Math.min(width, height) * 0.18f;
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(220);
            canvas.drawCircle(cx, cy, r, circlePaint);
            circlePaint.setColor(color("#9B6D48"));
            circlePaint.setAlpha(70);
            canvas.drawCircle(cx - r * 0.28f, cy - r * 0.18f, r * 0.72f, circlePaint);
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeWidth(dp(7));
            barPaint.setColor(color("#F3F6F8"));
            barPaint.setAlpha(105);
            barBounds.set(cx - r * 1.75f, cy - r * 0.45f, cx + r * 1.75f, cy + r * 0.45f);
            canvas.drawOval(barBounds, barPaint);
            barPaint.setColor(color("#D9AE6F"));
            barPaint.setAlpha(130);
            barBounds.set(cx - r * 1.95f, cy - r * 0.56f, cx + r * 1.95f, cy + r * 0.56f);
            canvas.drawOval(barBounds, barPaint);
            int[] colors = {color("#2F5F8F"), color("#C94735"), color("#3FA778")};
            for (int i = 0; i < 5; i++) {
                float orbit = Math.min(width, height) * (0.28f + i * 0.055f);
                float a = time * (0.08f + i * 0.018f) + i * 1.3f;
                circlePaint.setColor(colors[i % colors.length]);
                circlePaint.setAlpha(170);
                canvas.drawCircle(cx + (float) Math.cos(a) * orbit * 1.45f, cy + (float) Math.sin(a) * orbit * 0.55f, dp(6 + i % 3 * 3), circlePaint);
            }
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
            circlePaint.setAlpha(255);
        }

        private void drawComets(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#010208"));
            drawDeepStarfield(canvas, width, height, time * 0.18f);
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeCap(Paint.Cap.ROUND);
            int[] colors = {color("#D9AE6F"), color("#7FB5C8"), color("#F3F6F8")};
            for (int i = 0; i < 6; i++) {
                float cycle = (time * (0.025f + i * 0.006f) + i * 0.23f) % 1f;
                if (cycle > 0.55f) continue;
                float p = cycle / 0.55f;
                float x = width * (1.12f - p * 1.35f);
                float y = height * (0.12f + (i % 5) * 0.14f + p * 0.18f);
                barPaint.setColor(colors[i % colors.length]);
                for (int tail = 0; tail < 8; tail++) {
                    barPaint.setStrokeWidth(dp(Math.max(1, 5 - tail / 2)));
                    barPaint.setAlpha(Math.max(0, 150 - tail * 17));
                    canvas.drawLine(x + tail * dp(20), y - tail * dp(8), x + (tail + 1) * dp(20), y - (tail + 1) * dp(8), barPaint);
                }
                circlePaint.setStyle(Paint.Style.FILL);
                circlePaint.setColor(colors[i % colors.length]);
                circlePaint.setAlpha(190);
                canvas.drawCircle(x, y, dp(5 + i % 3), circlePaint);
            }
            barPaint.setStrokeCap(Paint.Cap.BUTT);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
            circlePaint.setAlpha(255);
        }

        private void drawConstellations(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#01040A"));
            float fade = 0.45f + 0.55f * (float) Math.sin(time * 0.28f);
            circlePaint.setStyle(Paint.Style.FILL);
            float[] xs = new float[18];
            float[] ys = new float[18];
            for (int i = 0; i < 18; i++) {
                xs[i] = width * (0.12f + ((i * 137) % 760) / 1000f) + (float) Math.sin(time * 0.04f + i) * width * 0.012f;
                ys[i] = height * (0.18f + ((i * 211) % 590) / 1000f);
                circlePaint.setColor(i % 4 == 0 ? color("#D9AE6F") : color("#F3F6F8"));
                circlePaint.setAlpha(90 + (i % 5) * 22);
                canvas.drawCircle(xs[i], ys[i], dp(2 + (i % 3)), circlePaint);
            }
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeWidth(dp(1));
            barPaint.setColor(color("#D9AE6F"));
            barPaint.setAlpha((int) (35 + fade * 105));
            for (int i = 0; i < xs.length - 1; i++) {
                if (i % 4 != 3) canvas.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1], barPaint);
            }
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
            circlePaint.setAlpha(255);
        }

        private void drawFloatingDust(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#050403"));
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(24);
            canvas.drawCircle(width * 0.18f, height * 0.16f, Math.min(width, height) * 0.38f, circlePaint);
            for (int i = 0; i < 160; i++) {
                float depth = 0.35f + (i % 7) * 0.12f;
                float x = width * (((i * 83) % 1000) / 1000f) + (float) Math.sin(time * 0.08f + i) * width * 0.035f * depth;
                float y = ((height * (((i * 197) % 1000) / 1000f)) + time * dp(8) * depth) % height;
                circlePaint.setColor(i % 9 == 0 ? color("#D9AE6F") : color("#F3F6F8"));
                circlePaint.setAlpha((int) (22 + depth * 70));
                canvas.drawCircle(x, y, dp(1 + (i % 4 == 0 ? 2 : 0)), circlePaint);
            }
            circlePaint.setAlpha(255);
        }

        private void drawBokehLights(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#050303"));
            int[] colors = {color("#D9AE6F"), color("#9B6D48"), color("#C94735"), color("#F3F6F8")};
            circlePaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 30; i++) {
                float x = width * (((i * 101) % 1000) / 1000f) + (float) Math.sin(time * 0.11f + i) * width * 0.04f;
                float y = height * (((i * 173) % 1000) / 1000f) + (float) Math.cos(time * 0.1f + i) * height * 0.035f;
                float r = Math.min(width, height) * (0.035f + (i % 8) * 0.013f);
                float pulse = 0.45f + 0.55f * (float) Math.sin(time * 0.42f + i);
                circlePaint.setColor(colors[i % colors.length]);
                circlePaint.setAlpha(18 + (int) (pulse * 58));
                canvas.drawCircle(x, y, r, circlePaint);
            }
            circlePaint.setAlpha(255);
        }

        private void drawKoiPond(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#02100E"));
            drawWaterRipples(canvas, width, height, time, 8);
            int[] colors = {color("#D9AE6F"), color("#F3F6F8"), color("#C94735")};
            for (int i = 0; i < 9; i++) {
                float path = (time * (0.025f + i * 0.004f) + i * 0.12f) % 1f;
                float x = width * (0.12f + ((float) Math.sin(path * Math.PI * 2d + i) * 0.36f + 0.42f));
                float y = height * (0.18f + ((float) Math.cos(path * Math.PI * 2d * 0.8d + i) * 0.28f + 0.38f));
                drawKoi(canvas, x, y, dp(32 + (i % 3) * 8), colors[i % colors.length], time + i);
            }
        }

        private void drawRipples(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020B10"));
            drawWaterRipples(canvas, width, height, time, 18);
        }

        private void drawSpiralGalaxy(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#010107"));
            float cx = width * 0.5f;
            float cy = height * 0.5f;
            circlePaint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 620; i++) {
                float d = ((i * 37) % 1000) / 1000f;
                float arm = (i % 5) * 1.256f;
                float angle = arm + d * 7.2f + time * 0.045f;
                float r = Math.min(width, height) * (0.02f + d * 0.43f);
                float jitter = (float) Math.sin(i * 3.1f) * 0.04f;
                float x = cx + (float) Math.cos(angle + jitter) * r * 1.55f;
                float y = cy + (float) Math.sin(angle + jitter) * r * 0.62f;
                circlePaint.setColor(i % 11 == 0 ? color("#D9AE6F") : color("#F3F6F8"));
                circlePaint.setAlpha(28 + (int) ((1f - d) * 170));
                canvas.drawCircle(x, y, dp(1 + (i % 29 == 0 ? 2 : 0)), circlePaint);
            }
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(70);
            canvas.drawCircle(cx, cy, Math.min(width, height) * 0.08f, circlePaint);
            circlePaint.setAlpha(255);
        }

        private void drawHypnoticRings(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020202"));
            float cx = width * 0.5f;
            float cy = height * 0.52f;
            circlePaint.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 26; i++) {
                float p = ((time * 0.08f + i * 0.045f) % 1f);
                float r = Math.min(width, height) * (0.04f + p * 0.54f);
                circlePaint.setColor(i % 3 == 0 ? color("#D9AE6F") : (i % 3 == 1 ? color("#2F5F8F") : color("#8A6BE8")));
                circlePaint.setAlpha(34 + (int) ((1f - p) * 145));
                circlePaint.setStrokeWidth(dp(1 + (i % 4)));
                canvas.drawCircle(cx, cy, r * (0.9f + 0.08f * (float) Math.sin(time + i)), circlePaint);
            }
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setAlpha(255);
        }

        private void drawHexagonFlow(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020506"));
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeWidth(dp(1));
            float size = dp(34);
            float rowHeight = size * 1.48f;
            int rows = (int) (height / rowHeight) + 3;
            int cols = (int) (width / (size * 1.72f)) + 3;
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    float x = col * size * 1.72f + (row % 2) * size * 0.86f - size;
                    float y = row * rowHeight - size;
                    float wave = 0.5f + 0.5f * (float) Math.sin(time * 0.65f + col * 0.45f + row * 0.36f);
                    barPaint.setColor(wave > 0.82f ? color("#D9AE6F") : color("#2F5F8F"));
                    barPaint.setAlpha(28 + (int) (wave * 105));
                    drawHexagon(canvas, x, y, size);
                }
            }
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
        }

        private void drawNeonGridHorizon(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020209"));
            float horizon = height * 0.46f;
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(54);
            canvas.drawCircle(width * 0.5f, horizon, Math.min(width, height) * 0.12f, circlePaint);
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeWidth(dp(2));
            barPaint.setColor(color("#D9AE6F"));
            barPaint.setAlpha(120);
            for (int i = -12; i <= 12; i++) {
                float x = width * 0.5f + i * width * 0.055f;
                canvas.drawLine(width * 0.5f, horizon, x, height, barPaint);
            }
            int lines = 22;
            for (int i = 0; i < lines; i++) {
                float p = ((time * 0.18f + i / (float) lines) % 1f);
                float y = horizon + p * p * (height - horizon);
                barPaint.setAlpha(40 + (int) (p * 120));
                canvas.drawLine(0, y, width, y, barPaint);
            }
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
            circlePaint.setAlpha(255);
        }

        private void drawHourglass(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#030303"));
            float cx = width * 0.5f;
            float top = height * 0.25f;
            float bottom = height * 0.78f;
            float half = Math.min(width, height) * 0.14f;
            float cycle = (time * 0.055f) % 1f;
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeWidth(dp(4));
            barPaint.setColor(color("#D9AE6F"));
            barPaint.setAlpha(175);
            Path glass = new Path();
            glass.moveTo(cx - half, top);
            glass.lineTo(cx + half, top);
            glass.lineTo(cx - half * 0.8f, bottom);
            glass.lineTo(cx + half * 0.8f, bottom);
            canvas.drawPath(glass, barPaint);

            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(180);
            float upper = (1f - cycle) * (bottom - top) * 0.32f;
            barBounds.set(cx - half * 0.58f, top + dp(26), cx + half * 0.58f, top + dp(26) + upper);
            canvas.drawOval(barBounds, circlePaint);
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeWidth(dp(2));
            canvas.drawLine(cx, height * 0.5f - dp(15), cx, height * 0.5f + dp(64), barPaint);
            float lower = cycle * (bottom - top) * 0.28f;
            barBounds.set(cx - half * 0.62f, bottom - dp(20) - lower, cx + half * 0.62f, bottom - dp(20));
            canvas.drawOval(barBounds, circlePaint);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
            circlePaint.setAlpha(255);
        }

        private void drawPendulum(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#030303"));
            drawFloatingDust(canvas, width, height, time * 0.35f);
            float pivotX = width * 0.5f;
            float pivotY = height * 0.22f;
            float length = height * 0.46f;
            float angle = (float) Math.sin(time * 0.55f) * 0.38f;
            float bobX = pivotX + (float) Math.sin(angle) * length;
            float bobY = pivotY + (float) Math.cos(angle) * length;
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeCap(Paint.Cap.ROUND);
            barPaint.setStrokeWidth(dp(4));
            barPaint.setColor(color("#D9AE6F"));
            barPaint.setAlpha(180);
            canvas.drawLine(pivotX, pivotY, bobX, bobY, barPaint);
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(225);
            canvas.drawCircle(bobX, bobY, dp(34), circlePaint);
            circlePaint.setAlpha(34);
            canvas.drawCircle(bobX, bobY, dp(72), circlePaint);
            barPaint.setStrokeCap(Paint.Cap.BUTT);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
            circlePaint.setAlpha(255);
        }

        private void drawSolarSystem(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#010107"));
            drawDeepStarfield(canvas, width, height, time * 0.05f);
            float cx = width * 0.5f;
            float cy = height * 0.52f;
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(235);
            canvas.drawCircle(cx, cy, dp(38), circlePaint);
            circlePaint.setAlpha(42);
            canvas.drawCircle(cx, cy, dp(90), circlePaint);
            int[] colors = {color("#2F5F8F"), color("#C94735"), color("#3FA778"), color("#F3F6F8"), color("#9B6D48")};
            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint.setStrokeWidth(dp(1));
            for (int i = 0; i < 7; i++) {
                float orbit = Math.min(width, height) * (0.12f + i * 0.052f);
                circlePaint.setColor(color("#2A3035"));
                circlePaint.setAlpha(95);
                canvas.drawCircle(cx, cy, orbit, circlePaint);
                float a = time * (0.06f + i * 0.018f) + i * 0.9f;
                circlePaint.setStyle(Paint.Style.FILL);
                circlePaint.setColor(colors[i % colors.length]);
                circlePaint.setAlpha(190);
                canvas.drawCircle(cx + (float) Math.cos(a) * orbit, cy + (float) Math.sin(a) * orbit, dp(5 + (i % 4) * 2), circlePaint);
                circlePaint.setStyle(Paint.Style.STROKE);
            }
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setAlpha(255);
        }

        private void drawSnowyMountains(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020713"));
            drawDeepStarfield(canvas, width, height, time * 0.08f);
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(color("#F3F6F8"));
            circlePaint.setAlpha(180);
            canvas.drawCircle(width * 0.82f, height * 0.18f, dp(44), circlePaint);
            drawMountainLayer(canvas, width, height, height * 0.58f, color("#101820"), 250, time * 0.01f);
            drawMountainLayer(canvas, width, height, height * 0.68f, color("#18222A"), 250, time * 0.015f);
            drawMountainLayer(canvas, width, height, height * 0.78f, color("#0A0E12"), 255, time * 0.02f);
            drawSnowLayers(canvas, width, height, time, 130);
        }

        private void drawCampfire(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#040302"));
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(32);
            canvas.drawCircle(width * 0.5f, height * 0.68f, Math.min(width, height) * 0.32f, circlePaint);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(color("#3A2115"));
            barBounds.set(width * 0.39f, height * 0.75f, width * 0.61f, height * 0.78f);
            canvas.drawRoundRect(barBounds, dp(12), dp(12), barPaint);
            barBounds.set(width * 0.42f, height * 0.78f, width * 0.66f, height * 0.81f);
            canvas.drawRoundRect(barBounds, dp(12), dp(12), barPaint);
            drawLayeredFlames(canvas, width, height, time, width * 0.5f, height * 0.75f, height * 0.28f);
            drawEmbers(canvas, width, height, time, height * 0.72f);
            circlePaint.setAlpha(255);
        }

        private void drawThunderstorm(Canvas canvas, int width, int height, float time) {
            int flash = ((int) (time * 3.7f)) % 47 == 0 ? 28 : 0;
            canvas.drawColor(color(flash > 0 ? "#10151B" : "#03070B"));
            for (int i = 0; i < 7; i++) {
                drawCloud(canvas, width * (0.04f + i * 0.15f), height * (0.12f + (i % 2) * 0.05f), dp(125 + (i % 3) * 28), color("#101820"), 185);
            }
            if (flash > 0) {
                barPaint.setStyle(Paint.Style.STROKE);
                barPaint.setStrokeWidth(dp(4));
                barPaint.setColor(color("#D9AE6F"));
                barPaint.setAlpha(120);
                Path bolt = new Path();
                bolt.moveTo(width * 0.63f, height * 0.22f);
                bolt.lineTo(width * 0.57f, height * 0.42f);
                bolt.lineTo(width * 0.64f, height * 0.42f);
                bolt.lineTo(width * 0.55f, height * 0.65f);
                canvas.drawPath(bolt, barPaint);
            }
            drawRain(canvas, width, height, time * 1.3f);
            barPaint.setAlpha(255);
        }

        private void drawFallingLeaves(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#050302"));
            int[] colors = {color("#D9AE6F"), color("#9B6D48"), color("#C94735"), color("#6D552A")};
            for (int i = 0; i < 62; i++) {
                float depth = 0.6f + (i % 5) * 0.12f;
                float x = width * (((i * 73) % 1000) / 1000f) + (float) Math.sin(time * 0.25f + i) * width * 0.06f * depth;
                float y = ((time * (0.018f + (i % 7) * 0.005f) + i * 0.041f) % 1f) * (height + dp(70)) - dp(35);
                float size = dp(12 + (i % 5) * 4) * depth;
                drawLeaf(canvas, x, y, size, colors[i % colors.length], time + i);
            }
        }

        private void drawMinimalDigitalClock(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020202"));
            float[] drift = clockDrift(time);
            Calendar calendar = Calendar.getInstance();
            titlePaint.setTextAlign(Paint.Align.CENTER);
            titlePaint.setFakeBoldText(true);
            titlePaint.setColor(color("#D9AE6F"));
            titlePaint.setAlpha(230);
            titlePaint.setTextSize(Math.min(width, height) * 0.18f);
            canvas.drawText(clockText(calendar), width * 0.5f + drift[0], height * 0.5f + drift[1], titlePaint);
            statusPaint.setTextAlign(Paint.Align.CENTER);
            statusPaint.setColor(color("#A9B4BC"));
            statusPaint.setAlpha(150);
            statusPaint.setTextSize(dp(24));
            canvas.drawText(dateText(calendar), width * 0.5f + drift[0], height * 0.5f + drift[1] + dp(52), statusPaint);
            titlePaint.setAlpha(255);
            statusPaint.setAlpha(255);
        }

        private void drawAnalogGoldClock(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020202"));
            drawFloatingDust(canvas, width, height, time * 0.2f);
            float[] drift = clockDrift(time);
            float cx = width * 0.5f + drift[0];
            float cy = height * 0.53f + drift[1];
            float radius = Math.min(width, height) * 0.3f;
            Calendar calendar = Calendar.getInstance();

            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint.setStrokeWidth(dp(2));
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(180);
            canvas.drawCircle(cx, cy, radius, circlePaint);
            for (int i = 0; i < 12; i++) {
                double a = -Math.PI / 2d + i * Math.PI * 2d / 12d;
                barPaint.setStyle(Paint.Style.STROKE);
                barPaint.setStrokeWidth(dp(3));
                barPaint.setColor(color("#D9AE6F"));
                barPaint.setAlpha(160);
                canvas.drawLine(cx + (float) Math.cos(a) * radius * 0.88f, cy + (float) Math.sin(a) * radius * 0.88f,
                        cx + (float) Math.cos(a) * radius * 0.97f, cy + (float) Math.sin(a) * radius * 0.97f, barPaint);
            }
            float second = calendar.get(Calendar.SECOND) + calendar.get(Calendar.MILLISECOND) / 1000f;
            float minute = calendar.get(Calendar.MINUTE) + second / 60f;
            float hour = (calendar.get(Calendar.HOUR) % 12) + minute / 60f;
            drawClockHand(canvas, cx, cy, radius * 0.48f, hour / 12f, dp(5), color("#D9AE6F"), 230);
            drawClockHand(canvas, cx, cy, radius * 0.7f, minute / 60f, dp(3), color("#D9AE6F"), 210);
            drawClockHand(canvas, cx, cy, radius * 0.78f, second / 60f, dp(1), color("#C94735"), 150);
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(color("#D9AE6F"));
            circlePaint.setAlpha(230);
            canvas.drawCircle(cx, cy, dp(6), circlePaint);
            circlePaint.setAlpha(255);
            barPaint.setAlpha(255);
        }

        private void drawFlipClock(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#030303"));
            float[] drift = clockDrift(time);
            Calendar calendar = Calendar.getInstance();
            String text = clockText(calendar);
            float cardW = Math.min(width * 0.16f, dp(150));
            float cardH = Math.min(height * 0.22f, dp(180));
            float gap = dp(14);
            float totalW = cardW * 4f + gap * 3f + dp(34);
            float startX = width * 0.5f - totalW / 2f + drift[0];
            float y = height * 0.45f - cardH / 2f + drift[1];
            int digitIndex = 0;
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == ':') {
                    titlePaint.setTextSize(dp(70));
                    titlePaint.setColor(color("#D9AE6F"));
                    titlePaint.setAlpha(210);
                    titlePaint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(":", startX + digitIndex * (cardW + gap) + dp(17), y + cardH * 0.62f, titlePaint);
                    startX += dp(34);
                } else {
                    float x = startX + digitIndex * (cardW + gap);
                    drawFlipDigit(canvas, ch, x, y, cardW, cardH, calendar.get(Calendar.SECOND) + calendar.get(Calendar.MILLISECOND) / 1000f);
                    digitIndex++;
                }
            }
            titlePaint.setAlpha(255);
        }

        private void drawDotMatrixClock(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020202"));
            float[] drift = clockDrift(time);
            Calendar calendar = Calendar.getInstance();
            String text = clockText(calendar);
            float dot = Math.max(dp(5), Math.min(width, height) * 0.012f);
            float gap = dot * 1.65f;
            float charW = gap * 5f;
            float totalW = charW * 5f + gap * 4f;
            float startX = width * 0.5f - totalW / 2f + drift[0];
            float startY = height * 0.39f + drift[1];
            for (int i = 0; i < text.length(); i++) {
                drawDotMatrixChar(canvas, text.charAt(i), startX + i * (charW + gap), startY, dot, gap);
            }
            statusPaint.setTextAlign(Paint.Align.CENTER);
            statusPaint.setColor(color("#A9B4BC"));
            statusPaint.setAlpha(140);
            statusPaint.setTextSize(dp(22));
            canvas.drawText(dateText(calendar), width * 0.5f + drift[0], startY + gap * 8f + dp(46), statusPaint);
            statusPaint.setAlpha(255);
        }

        private void drawNeonClock(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020208"));
            drawFloatingDust(canvas, width, height, time * 0.28f);
            float[] drift = clockDrift(time);
            Calendar calendar = Calendar.getInstance();
            titlePaint.setTextAlign(Paint.Align.CENTER);
            titlePaint.setFakeBoldText(false);
            titlePaint.setTextSize(Math.min(width, height) * 0.16f);
            titlePaint.setStyle(Paint.Style.STROKE);
            titlePaint.setStrokeWidth(dp(3));
            titlePaint.setColor(color("#D9AE6F"));
            titlePaint.setAlpha(70);
            canvas.drawText(clockText(calendar), width * 0.5f + drift[0], height * 0.52f + drift[1], titlePaint);
            titlePaint.setStyle(Paint.Style.FILL);
            titlePaint.setColor(color("#F3F6F8"));
            titlePaint.setAlpha(210);
            canvas.drawText(clockText(calendar), width * 0.5f + drift[0], height * 0.52f + drift[1], titlePaint);
            titlePaint.setFakeBoldText(true);
            titlePaint.setAlpha(255);
        }

        private void drawOrbitClock(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#010107"));
            drawDeepStarfield(canvas, width, height, time * 0.1f);
            float[] drift = clockDrift(time);
            float cx = width * 0.5f + drift[0];
            float cy = height * 0.5f + drift[1];
            Calendar calendar = Calendar.getInstance();
            for (int i = 0; i < 18; i++) {
                float r = Math.min(width, height) * (0.17f + (i % 5) * 0.038f);
                float a = time * (0.08f + (i % 4) * 0.018f) + i * 0.72f;
                circlePaint.setStyle(Paint.Style.FILL);
                circlePaint.setColor(i % 3 == 0 ? color("#D9AE6F") : color("#2F5F8F"));
                circlePaint.setAlpha(85 + (i % 4) * 25);
                canvas.drawCircle(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r * 0.62f, dp(3 + (i % 3)), circlePaint);
            }
            titlePaint.setTextAlign(Paint.Align.CENTER);
            titlePaint.setFakeBoldText(true);
            titlePaint.setTextSize(Math.min(width, height) * 0.13f);
            titlePaint.setColor(color("#D9AE6F"));
            titlePaint.setAlpha(225);
            canvas.drawText(clockText(calendar), cx, cy + dp(22), titlePaint);
            titlePaint.setAlpha(255);
            circlePaint.setAlpha(255);
        }

        private void drawWordClock(Canvas canvas, int width, int height, float time) {
            canvas.drawColor(color("#020202"));
            float[] drift = clockDrift(time);
            Calendar calendar = Calendar.getInstance();
            String words = hourWord(calendar.get(Calendar.HOUR_OF_DAY)) + " " + minuteWords(calendar.get(Calendar.MINUTE));
            titlePaint.setTextAlign(Paint.Align.CENTER);
            titlePaint.setFakeBoldText(true);
            titlePaint.setTextSize(Math.min(width, height) * 0.08f);
            titlePaint.setColor(color("#D9AE6F"));
            titlePaint.setAlpha(230);
            String[] pieces = words.split(" ");
            StringBuilder line = new StringBuilder();
            float y = height * 0.43f + drift[1];
            for (String piece : pieces) {
                if (line.length() + piece.length() > 12) {
                    canvas.drawText(line.toString(), width * 0.5f + drift[0], y, titlePaint);
                    y += dp(62);
                    line = new StringBuilder();
                }
                if (line.length() > 0) line.append(' ');
                line.append(piece);
            }
            if (line.length() > 0) {
                canvas.drawText(line.toString(), width * 0.5f + drift[0], y, titlePaint);
            }
            titlePaint.setAlpha(255);
        }

        private void drawAmbientClock(Canvas canvas, int width, int height, float time) {
            Calendar calendar = Calendar.getInstance();
            int hour = calendar.get(Calendar.HOUR_OF_DAY);
            if (hour >= 7 && hour < 17) {
                canvas.drawColor(color("#101820"));
                circlePaint.setStyle(Paint.Style.FILL);
                circlePaint.setColor(color("#D9AE6F"));
                circlePaint.setAlpha(80);
                canvas.drawCircle(width * 0.78f, height * 0.22f, dp(70), circlePaint);
            } else if (hour >= 17 && hour < 21) {
                canvas.drawColor(color("#10080A"));
                circlePaint.setStyle(Paint.Style.FILL);
                circlePaint.setColor(color("#C94735"));
                circlePaint.setAlpha(70);
                canvas.drawCircle(width * 0.5f, height * 0.5f, Math.min(width, height) * 0.34f, circlePaint);
            } else {
                canvas.drawColor(color("#020611"));
                drawDeepStarfield(canvas, width, height, time * 0.08f);
                circlePaint.setStyle(Paint.Style.FILL);
                circlePaint.setColor(color("#F3F6F8"));
                circlePaint.setAlpha(170);
                canvas.drawCircle(width * 0.78f, height * 0.2f, dp(48), circlePaint);
            }
            for (int i = 0; i < 7; i++) {
                float cloudX = ((time * (0.012f + i * 0.003f) + i * 0.2f) % 1f) * (width + dp(240)) - dp(120);
                drawCloud(canvas, cloudX, height * (0.18f + (i % 3) * 0.09f), dp(72 + (i % 3) * 24), color("#12171B"), 92);
            }
            float[] drift = clockDrift(time);
            titlePaint.setTextAlign(Paint.Align.CENTER);
            titlePaint.setFakeBoldText(true);
            titlePaint.setTextSize(Math.min(width, height) * 0.14f);
            titlePaint.setColor(color("#D9AE6F"));
            titlePaint.setAlpha(228);
            canvas.drawText(clockText(calendar), width * 0.5f + drift[0], height * 0.56f + drift[1], titlePaint);
            titlePaint.setAlpha(255);
            circlePaint.setAlpha(255);
        }

        private void drawRotatedDiamond(Canvas canvas, float cx, float cy, float size, float rotation) {
            Path path = new Path();
            for (int i = 0; i < 4; i++) {
                double angle = rotation + Math.PI / 4d + i * Math.PI / 2d;
                float x = cx + (float) Math.cos(angle) * size;
                float y = cy + (float) Math.sin(angle) * size * 0.62f;
                if (i == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            path.close();
            canvas.drawPath(path, barPaint);
        }

        private void drawClockHand(Canvas canvas, float cx, float cy, float length, float unit, int strokeWidth, int handColor, int alpha) {
            double angle = -Math.PI / 2d + unit * Math.PI * 2d;
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeCap(Paint.Cap.ROUND);
            barPaint.setStrokeWidth(strokeWidth);
            barPaint.setColor(handColor);
            barPaint.setAlpha(alpha);
            canvas.drawLine(cx, cy, cx + (float) Math.cos(angle) * length, cy + (float) Math.sin(angle) * length, barPaint);
            barPaint.setStrokeCap(Paint.Cap.BUTT);
        }

        private float[] clockDrift(float time) {
            return new float[] {
                    (float) Math.sin(time * 0.035f) * dp(10),
                    (float) Math.cos(time * 0.027f) * dp(7)
            };
        }

        private String clockText(Calendar calendar) {
            return String.format("%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
        }

        private String dateText(Calendar calendar) {
            String[] days = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
            String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
            return days[calendar.get(Calendar.DAY_OF_WEEK) - 1] + ", "
                    + months[calendar.get(Calendar.MONTH)] + " " + calendar.get(Calendar.DAY_OF_MONTH);
        }

        private void drawFlipDigit(Canvas canvas, char digit, float x, float y, float w, float h, float second) {
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(color("#12171B"));
            barPaint.setAlpha(245);
            barBounds.set(x, y, x + w, y + h);
            canvas.drawRoundRect(barBounds, dp(12), dp(12), barPaint);
            barPaint.setColor(color("#050607"));
            barPaint.setAlpha(140);
            canvas.drawRect(x, y + h * 0.5f - dp(2), x + w, y + h * 0.5f + dp(2), barPaint);
            if (second < 0.18f) {
                barPaint.setColor(color("#2A3035"));
                barPaint.setAlpha((int) (120 * (1f - second / 0.18f)));
                canvas.drawRect(x, y, x + w, y + h * 0.5f, barPaint);
            }
            titlePaint.setStyle(Paint.Style.FILL);
            titlePaint.setTextAlign(Paint.Align.CENTER);
            titlePaint.setFakeBoldText(true);
            titlePaint.setColor(color("#D9AE6F"));
            titlePaint.setAlpha(225);
            titlePaint.setTextSize(h * 0.62f);
            canvas.drawText(String.valueOf(digit), x + w * 0.5f, y + h * 0.7f, titlePaint);
            barPaint.setAlpha(255);
            titlePaint.setAlpha(255);
        }

        private void drawDotMatrixChar(Canvas canvas, char character, float x, float y, float dot, float gap) {
            String[] pattern = dotPattern(character);
            circlePaint.setStyle(Paint.Style.FILL);
            for (int row = 0; row < pattern.length; row++) {
                for (int col = 0; col < pattern[row].length(); col++) {
                    if (pattern[row].charAt(col) == '1') {
                        circlePaint.setColor(color("#D9AE6F"));
                        circlePaint.setAlpha(210);
                        canvas.drawCircle(x + col * gap, y + row * gap, dot, circlePaint);
                        circlePaint.setAlpha(34);
                        canvas.drawCircle(x + col * gap, y + row * gap, dot * 2.3f, circlePaint);
                    }
                }
            }
            circlePaint.setAlpha(255);
        }

        private String[] dotPattern(char character) {
            switch (character) {
                case '0': return new String[]{"111","101","101","101","101","101","111"};
                case '1': return new String[]{"010","110","010","010","010","010","111"};
                case '2': return new String[]{"111","001","001","111","100","100","111"};
                case '3': return new String[]{"111","001","001","111","001","001","111"};
                case '4': return new String[]{"101","101","101","111","001","001","001"};
                case '5': return new String[]{"111","100","100","111","001","001","111"};
                case '6': return new String[]{"111","100","100","111","101","101","111"};
                case '7': return new String[]{"111","001","001","010","010","010","010"};
                case '8': return new String[]{"111","101","101","111","101","101","111"};
                case '9': return new String[]{"111","101","101","111","001","001","111"};
                default: return new String[]{"0","1","0","0","1","0","0"};
            }
        }

        private String hourWord(int hour) {
            String[] words = {"TWELVE", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN", "ELEVEN"};
            return words[hour % 12];
        }

        private String minuteWords(int minute) {
            if (minute == 0) return "O CLOCK";
            String[] ones = {"", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE"};
            String[] teens = {"TEN", "ELEVEN", "TWELVE", "THIRTEEN", "FOURTEEN", "FIFTEEN", "SIXTEEN", "SEVENTEEN", "EIGHTEEN", "NINETEEN"};
            String[] tens = {"", "", "TWENTY", "THIRTY", "FORTY", "FIFTY"};
            if (minute < 10) return "OH " + ones[minute];
            if (minute < 20) return teens[minute - 10];
            int ten = minute / 10;
            int one = minute % 10;
            return one == 0 ? tens[ten] : tens[ten] + " " + ones[one];
        }

        private void drawUnderwaterLightRays(Canvas canvas, int width, int height, float time) {
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeCap(Paint.Cap.ROUND);
            for (int i = 0; i < 7; i++) {
                float x = width * (0.08f + i * 0.15f) + (float) Math.sin(time * 0.12f + i) * width * 0.035f;
                barPaint.setColor(color("#7FB5C8"));
                barPaint.setAlpha(18 + i * 4);
                barPaint.setStrokeWidth(dp(18 + i * 3));
                canvas.drawLine(x, 0, x - width * 0.16f, height, barPaint);
            }
            barPaint.setStrokeCap(Paint.Cap.BUTT);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
        }

        private void drawUnderwaterBubbles(Canvas canvas, int width, int height, float time, int count) {
            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint.setStrokeWidth(dp(1));
            for (int i = 0; i < count; i++) {
                float size = dp(4 + (i % 8) * 4);
                float x = width * (((i * 97) % 1000) / 1000f) + (float) Math.sin(time * 0.25f + i) * width * 0.02f;
                float y = height - ((time * (0.028f + (i % 6) * 0.006f) + i * 0.047f) % 1f) * (height + dp(80));
                circlePaint.setColor(i % 8 == 0 ? color("#D9AE6F") : color("#A9B4BC"));
                circlePaint.setAlpha(42 + (i % 4) * 16);
                canvas.drawCircle(x, y, size, circlePaint);
            }
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setAlpha(255);
        }

        private void drawButterfly(Canvas canvas, float x, float y, float size, int fillColor, float time) {
            float flap = 0.65f + 0.35f * (float) Math.sin(time * 2.0f);
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(fillColor);
            circlePaint.setAlpha(112);
            barBounds.set(x - size * 1.25f, y - size * flap, x, y + size * 0.42f);
            canvas.drawOval(barBounds, circlePaint);
            barBounds.set(x, y - size * flap, x + size * 1.25f, y + size * 0.42f);
            canvas.drawOval(barBounds, circlePaint);
            circlePaint.setAlpha(78);
            barBounds.set(x - size, y, x - size * 0.12f, y + size * 0.92f);
            canvas.drawOval(barBounds, circlePaint);
            barBounds.set(x + size * 0.12f, y, x + size, y + size * 0.92f);
            canvas.drawOval(barBounds, circlePaint);
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeWidth(dp(2));
            barPaint.setColor(color("#F3F6F8"));
            barPaint.setAlpha(105);
            canvas.drawLine(x, y - size * 0.35f, x, y + size * 0.7f, barPaint);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
            circlePaint.setAlpha(255);
        }

        private void drawCloud(Canvas canvas, float x, float y, float size, int fillColor, int alpha) {
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(fillColor);
            circlePaint.setAlpha(alpha);
            canvas.drawCircle(x, y, size * 0.36f, circlePaint);
            canvas.drawCircle(x + size * 0.32f, y - size * 0.12f, size * 0.44f, circlePaint);
            canvas.drawCircle(x + size * 0.74f, y, size * 0.35f, circlePaint);
            barBounds.set(x - size * 0.3f, y - size * 0.1f, x + size * 1.08f, y + size * 0.36f);
            canvas.drawRoundRect(barBounds, size * 0.18f, size * 0.18f, circlePaint);
            circlePaint.setAlpha(255);
        }

        private void drawKoi(Canvas canvas, float x, float y, float size, int fillColor, float time) {
            float angle = (float) Math.sin(time * 0.38f) * 0.55f;
            float tail = (float) Math.sin(time * 2.0f) * size * 0.18f;
            Path body = new Path();
            body.moveTo(x - size, y);
            body.quadTo(x, y - size * 0.5f, x + size, y);
            body.quadTo(x, y + size * 0.5f, x - size, y);
            body.close();
            canvas.save();
            canvas.rotate((float) Math.toDegrees(angle), x, y);
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(fillColor);
            barPaint.setAlpha(185);
            canvas.drawPath(body, barPaint);
            Path tailPath = new Path();
            tailPath.moveTo(x - size * 0.88f, y);
            tailPath.lineTo(x - size * 1.35f, y - size * 0.42f + tail);
            tailPath.lineTo(x - size * 1.35f, y + size * 0.42f + tail);
            tailPath.close();
            canvas.drawPath(tailPath, barPaint);
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setColor(color("#050607"));
            circlePaint.setAlpha(120);
            canvas.drawCircle(x + size * 0.34f, y - size * 0.12f, size * 0.08f, circlePaint);
            canvas.restore();
            barPaint.setAlpha(255);
            circlePaint.setAlpha(255);
        }

        private void drawWaterRipples(Canvas canvas, int width, int height, float time, int count) {
            circlePaint.setStyle(Paint.Style.STROKE);
            circlePaint.setStrokeWidth(dp(2));
            for (int i = 0; i < count; i++) {
                float p = (time * (0.06f + (i % 4) * 0.012f) + i * 0.073f) % 1f;
                float x = width * (((i * 113) % 1000) / 1000f);
                float y = height * (((i * 197) % 1000) / 1000f);
                float r = Math.min(width, height) * (0.025f + p * 0.13f);
                circlePaint.setColor(i % 5 == 0 ? color("#D9AE6F") : color("#7FB5C8"));
                circlePaint.setAlpha(Math.max(0, 120 - (int) (p * 120)));
                canvas.drawCircle(x, y, r, circlePaint);
            }
            circlePaint.setStyle(Paint.Style.FILL);
            circlePaint.setAlpha(255);
        }

        private void drawHexagon(Canvas canvas, float cx, float cy, float r) {
            Path path = new Path();
            for (int i = 0; i < 6; i++) {
                double angle = Math.PI / 6d + i * Math.PI / 3d;
                float x = cx + (float) Math.cos(angle) * r;
                float y = cy + (float) Math.sin(angle) * r;
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            path.close();
            canvas.drawPath(path, barPaint);
        }

        private void drawMountainLayer(Canvas canvas, int width, int height, float baseY, int fillColor, int alpha, float offset) {
            Path path = new Path();
            path.moveTo(-dp(80), height);
            path.lineTo(-dp(80), baseY);
            for (int i = 0; i <= 8; i++) {
                float x = -dp(80) + i * (width + dp(160)) / 8f;
                float peak = baseY - height * (0.08f + ((i * 37) % 5) * 0.025f);
                path.lineTo(x + offset * width, peak);
                path.lineTo(x + (width + dp(160)) / 16f + offset * width, baseY);
            }
            path.lineTo(width + dp(80), height);
            path.close();
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(fillColor);
            barPaint.setAlpha(alpha);
            canvas.drawPath(path, barPaint);
            barPaint.setAlpha(255);
        }

        private void drawLeaf(Canvas canvas, float x, float y, float size, int fillColor, float time) {
            canvas.save();
            canvas.rotate((time * 35f) % 360f, x, y);
            Path leaf = new Path();
            leaf.moveTo(x, y - size);
            leaf.quadTo(x + size * 0.8f, y - size * 0.25f, x, y + size);
            leaf.quadTo(x - size * 0.8f, y - size * 0.25f, x, y - size);
            leaf.close();
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setColor(fillColor);
            barPaint.setAlpha(155);
            canvas.drawPath(leaf, barPaint);
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeWidth(dp(1));
            barPaint.setColor(color("#D9AE6F"));
            barPaint.setAlpha(75);
            canvas.drawLine(x, y - size * 0.78f, x, y + size * 0.72f, barPaint);
            canvas.restore();
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
        }

        private void drawSubtleLights(Canvas canvas, int width, int height, float time, float y) {
            barPaint.setStyle(Paint.Style.STROKE);
            barPaint.setStrokeWidth(dp(2));
            barPaint.setColor(color("#2A2018"));
            barPaint.setAlpha(160);
            float previousX = 0;
            float previousY = y;
            for (int x = 0; x <= width; x += dp(38)) {
                float waveY = y + (float) Math.sin(x * 0.012f) * dp(14);
                canvas.drawLine(previousX, previousY, x, waveY, barPaint);
                previousX = x;
                previousY = waveY;
            }

            circlePaint.setStyle(Paint.Style.FILL);
            int lightCount = Math.max(14, width / dp(76));
            int[] colors = {color("#D9AE6F"), color("#C94735"), color("#3FA778"), color("#F3F6F8")};
            for (int i = 0; i < lightCount; i++) {
                float x = (i + 0.5f) * width / lightCount;
                float lightY = y + (float) Math.sin(x * 0.012f) * dp(14) + dp(12);
                float glow = 0.55f + 0.45f * (float) Math.sin(time * 1.2f + i * 0.9f);
                circlePaint.setColor(colors[i % colors.length]);
                circlePaint.setAlpha(70 + (int) (glow * 120));
                canvas.drawCircle(x, lightY, dp(4 + (int) (glow * 3)), circlePaint);
            }
            barPaint.setStyle(Paint.Style.FILL);
            barPaint.setAlpha(255);
            circlePaint.setAlpha(255);
        }

        private void drawText(Canvas canvas, int width, int height, long now) {
            long visibleDuration = 7000;
            long fadeDuration = 1000;
            long elapsed = now - textShownAt;
            if (elapsed >= visibleDuration + fadeDuration) {
                return;
            }

            int alpha = 255;
            if (elapsed > visibleDuration) {
                float fadeProgress = (elapsed - visibleDuration) / (float) fadeDuration;
                alpha = Math.max(0, 255 - (int) (255 * fadeProgress));
            }

            titlePaint.setAlpha(alpha);
            statusPaint.setAlpha(alpha);

            statusPaint.setTextSize(dp(24));
            canvas.drawText(state, width / 2f, height * 0.22f, statusPaint);

            titlePaint.setTextSize(dp(54));
            canvas.drawText(title, width / 2f, height * 0.32f, titlePaint);

            statusPaint.setTextSize(dp(17));
            canvas.drawText("Left / Right changes screensaver • OK or Back returns", width / 2f, height * 0.86f, statusPaint);

            statusPaint.setTextSize(dp(15));
            canvas.drawText(MODE_NAMES[mode], width / 2f, height * 0.91f, statusPaint);

            titlePaint.setAlpha(255);
            statusPaint.setAlpha(255);
        }
    }

    private static class RadioStation {
        final String id;
        String name;
        String url;
        final String category;
        final boolean custom;

        RadioStation(String id, String name, String url, String category, boolean custom) {
            this.id = id;
            this.name = name;
            this.url = url;
            this.category = category;
            this.custom = custom;
        }
    }

    private static class StationRowViews {
        final LinearLayout row;
        final TextView playIndicator;

        StationRowViews(LinearLayout row, TextView playIndicator) {
            this.row = row;
            this.playIndicator = playIndicator;
        }
    }

    private static class ListeningStats {
        long todayMs;
        long weekMs;
        long allTimeMs;
        final List<StationListeningStat> stationStats = new ArrayList<>();
    }

    private static class StationListeningStat {
        final String id;
        final String name;
        long ms;

        StationListeningStat(String id, String name, long ms) {
            this.id = id;
            this.name = name;
            this.ms = ms;
        }
    }
}
