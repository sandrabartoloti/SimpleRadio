package com.sandra.radiotv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** TV-only picker. Single OK selects; double OK toggles a user-owned pin. */
final class TvScreensaverPicker {
    static final String AUTO_MINUTES = "tv_screensaver_auto_minutes";
    private static final String PINS = "tv_screensaver_pins";
    private static final int[] DELAYS = {0, 1, 3, 5, 10, 15, 30};
    private static final String[] GROUPS = {"Clocks", "Nature & Space", "Halloween", "Celebrations", "Abstract & Ambient"};
    interface Selection { void select(int mode); }
    private final Activity activity;
    private final String[] names;
    private final int current;
    private final SharedPreferences prefs;
    private final Set<String> pins;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Button> buttons = new ArrayList<>();
    private final LinearLayout list;
    private final ScrollView scroll;
    private final AlertDialog dialog;
    private final Selection selection;
    private int pending = -1;
    private final Runnable launch = () -> launchPending();

    TvScreensaverPicker(Activity activity, String[] names, int current, SharedPreferences prefs,
            Selection selection, Runnable dismissed) {
        this.activity = activity;
        this.names = names;
        this.current = current;
        this.prefs = prefs;
        this.selection = selection;
        pins = new HashSet<>(prefs.getStringSet(PINS, new HashSet<>()));
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(20), dp(24), dp(20));
        panel.setBackgroundColor(Color.rgb(8, 11, 14));
        TextView heading = label("Choose screensaver", 24);
        panel.addView(heading);
        TextView hint = label("OK: start • Double OK: pin / unpin • Back: close", 14);
        hint.setTextColor(0xFFA9B4BC);
        panel.addView(hint);
        scroll = new ScrollView(activity);
        list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, 0, 1);
        params.topMargin = dp(16);
        panel.addView(scroll, params);
        dialog = new AlertDialog.Builder(activity).setView(panel).create();
        dialog.setOnDismissListener(ignored -> {
            cancelPending();
            dismissed.run();
        });
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) dialog.getWindow().setLayout(
                    Math.min(dp(680), (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.8f)),
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.85f));
            rebuild(current);
        });
    }

    void show() { dialog.show(); }
    void dismiss() { dialog.dismiss(); }

    private void cancelPending() {
        handler.removeCallbacks(launch);
        pending = -1;
    }

    private void launchPending() {
        int mode = pending;
        pending = -1;
        if (mode < 0 || !dialog.isShowing()) return;
        dialog.dismiss();
        selection.select(mode);
    }

    private void choose(int mode) {
        if (pending == mode) {
            cancelPending();
            String key = Integer.toString(mode);
            if (!pins.remove(key)) pins.add(key);
            prefs.edit().putStringSet(PINS, new HashSet<>(pins)).apply();
            rebuild(mode);
        } else {
            cancelPending();
            pending = mode;
            handler.postDelayed(launch, ViewConfiguration.getDoubleTapTimeout() + 100L);
        }
    }

    private void rebuild(int focusMode) {
        cancelPending();
        for (Button row : buttons) row.setOnFocusChangeListener(null);
        list.removeAllViews();
        buttons.clear();
        Button timer = button(timerLabel());
        timer.setTag(-1);
        timer.setOnClickListener(v -> {
            int minutes = prefs.getInt(AUTO_MINUTES, 0);
            int next = 0;
            for (int i = 0; i < DELAYS.length; i++) if (minutes == DELAYS[i]) next = (i + 1) % DELAYS.length;
            prefs.edit().putInt(AUTO_MINUTES, DELAYS[next]).apply();
            timer.setText(timerLabel());
        });
        boolean anyPins = false;
        for (int i = 0; i < names.length; i++) if (pins.contains(Integer.toString(i))) anyPins = true;
        if (anyPins) {
            list.addView(label("Pinned", 18));
            for (int i = 0; i < names.length; i++) if (pins.contains(Integer.toString(i))) modeButton(i, true);
        }
        for (int group = 0; group < GROUPS.length; group++) {
            boolean heading = false;
            for (int i = 0; i < names.length; i++) {
                if (group(i) != group || pins.contains(Integer.toString(i))) continue;
                if (!heading) { list.addView(label(GROUPS[group], 18)); heading = true; }
                modeButton(i, false);
            }
        }
        for (int i = 0; i < buttons.size(); i++) {
            final int position = i;
            Button row = buttons.get(i);
            row.setOnKeyListener((v, key, event) -> {
                if (key != KeyEvent.KEYCODE_DPAD_UP && key != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    cancelPending();
                    int target = (position + (key == KeyEvent.KEYCODE_DPAD_DOWN ? 1 : -1) + buttons.size()) % buttons.size();
                    focus(buttons.get(target));
                }
                return true;
            });
        }
        scroll.post(() -> {
            if (!dialog.isShowing()) return;
            for (Button row : buttons) if (Integer.valueOf(focusMode).equals(row.getTag())) { focus(row); return; }
            focus(timer);
        });
    }

    private void modeButton(int mode, boolean pinned) {
        Button row = button((pinned ? "★ " : "") + (mode == current ? "• " : "") + names[mode]);
        row.setTag(mode);
        row.setOnClickListener(v -> choose(mode));
    }

    private void focus(Button row) {
        row.requestFocus();
        scroll.scrollTo(0, Math.max(0, row.getTop() - (scroll.getHeight() - row.getHeight()) / 2));
    }

    private Button button(String text) {
        Button row = new Button(activity);
        row.setText(text);
        row.setTextSize(18);
        row.setAllCaps(false);
        row.setTextColor(Color.WHITE);
        row.setFocusable(true);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setBackgroundResource(R.drawable.control_button_background);
        row.setId(View.generateViewId());
        row.setNextFocusLeftId(row.getId());
        row.setNextFocusRightId(row.getId());
        row.setOnFocusChangeListener((v, focused) -> { if (!focused) cancelPending(); });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.bottomMargin = dp(6);
        list.addView(row, params);
        buttons.add(row);
        return row;
    }

    private String timerLabel() {
        int minutes = prefs.getInt(AUTO_MINUTES, 0);
        return "Auto-start: " + (minutes == 0 ? "Off" : minutes + " min") + "  • OK to change";
    }

    private TextView label(String text, int size) {
        TextView label = new TextView(activity);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(0xFFD9AE6F);
        label.setPadding(0, dp(8), 0, dp(8));
        return label;
    }

    private static int group(int mode) {
        if (mode == 41 || (mode >= 63 && mode <= 70)) return 0;
        if (mode >= 71 && mode <= 76) return 2;
        if (mode == 10 || mode == 11 || (mode >= 18 && mode <= 23) || mode == 32 || mode == 77 || mode == 78 || mode == 79 || mode == 80 || mode == 81 || mode == 82 || mode == 83) return 3;
        if (mode == 5 || mode == 9 || (mode >= 12 && mode <= 15) || (mode >= 24 && mode <= 27)
                || mode == 30 || (mode >= 33 && mode <= 35) || mode == 39
                || (mode >= 42 && mode <= 52) || mode == 58 || (mode >= 59 && mode <= 62)) return 1;
        return 4;
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
}
