package com.ansub.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import java.io.InputStream;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.GridLayout;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import android.app.ActivityManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.SeekBar;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.TreeMap;
import java.util.List;

public class MainActivity extends Activity {

    // Top-row apps, in this order of preference:
    //  1. a list saved at install time:  am start -n com.ansub.tvhome/.MainActivity --es apps "pkg1,pkg2"
    //     (--es apps auto  goes back to 2; add --es stamp <unique> so it's applied only once, see saveConfig)
    //  2. apps.txt (built in as Apps.LIST), topped up with other TV apps installed on this TV.
    // Missing apps are skipped. Topping up stops at six apps, but a chosen list can be any length:
    // apps wrap into rows of six, like Apple TV, and the page scrolls down once they don't all fit.
    // On the TV, hold OK on an app (or press Menu) to move or remove it, or add another; that saves list 1.
    // A network tile always sits at the top right, level with the clock. Settings shortcuts can join
    // it there, off until picked the same way:
    //   --es quick "bluetooth,sound,picture"
    // (--es quick none  takes them away again; "wifi" and "ethernet" are the network tile now).
    private static final String PREFS = "config";
    private static final List<String> NOT_APPS = Arrays.asList("com.android.vending", "com.android.tv.settings",
            "com.android.tv", "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher");
    private static final String PLAY_STORE = "com.android.vending";
    private static final String SETTINGS = "settings";
    private static final String ADD = "add";
    private static final String[] EDIT_ITEMS = {"Move", "Replace", "Remove from home", "Uninstall"};
    private static final String[] EDIT_ITEMS_SYSTEM = {"Move", "Replace", "Remove from home", "App info"};
    private static final int PER_ROW = 5, MAX_INPUTS = 4;

    // Settings shortcuts: name, label, icon. Tile keys are QUICK + name.
    private static final String QUICK = "quick:";
    private static final String[] QUICK_NAMES = {"bluetooth", "sound", "picture"};
    private static final String[] QUICK_LABELS = {"Bluetooth", "Sound", "Picture"};
    private static final int[][] QUICK_ICONS = {LineIcon.BLUETOOTH, LineIcon.SOUND, LineIcon.PICTURE};
    private static final String NETWORK = "network";
    private static final int OFFLINE = 0, ETHERNET = 4; // network states; 1 to 3 are Wi-Fi bars
    // TCL keeps its own Picture and Sound panels in a service; Android's Sound page is missing there.
    private static final String TCL_SHOW_WINDOW = "com.tcl.settings.SHOW_WINDOW";

    // Layout is designed at 1920x1080 and scaled to the TV's real UI size by px().
    private static final int MARGIN = 96, GAP = 36, RADIUS = 22;
    private static final int ROW1_TOP = 430, ROW_STEP = 250;
    private static final int GRID_MIN_TOP = 400; // more rows push the grid up, but not into the clock
    private static final int ICON_TOP = 76, ICON_BUTTON = 72, ICON_SIZE = 40, ICON_GAP = 12; // level with the date
    private float scale = 1f;

    private static Typeface regular, semibold;

    private FrameLayout root; // the page, inside a vertical scroller
    private int tileW, tileH, gridTop; // app grid geometry, set by buildTiles
    private final List<View> tiles = new ArrayList<>();
    private final List<View> watchChrome = new ArrayList<>();
    private boolean dirty = true;
    private java.util.Locale shownLocale; // the TV's language (and region) when the page was built
    private String focusedKey;
    private final List<String> rowApps = new ArrayList<>(); // the top row as shown, in order
    private String movingKey;   // the app being moved with left/right, if any
    private TextView moveHint;  // shown above the apps while moving
    private boolean longPressed; // OK was held: swallow the key-up so the app doesn't open
    private int appPage;
    private boolean motionOn = true;
    private int customMinutes = 15;
    private boolean lateDismissed;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable goAmbient = this::showAmbient;
    private Runnable sleepTask;
    private FrameLayout stage;
    private TextView weatherView, pageDots;
    private View settingsLayer, wallpaperLayer, ambientLayer, nightLayer, powerLayer;
    private TextView wallpaperRow;
    private static final int PICK_WALL = 41;

    // Apps installed, removed or updated. This often lands once Home is already back in front (an app
    // uninstalled from the menu is gone only after Android's prompt has closed), so catch up then.
    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            dirty = true;
            refreshApps();
        }
    };

    // The network tile follows the connection live: Wi-Fi bars, Ethernet or Offline. Only its icon and
    // label change, so nothing is rebuilt.
    private ImageView netIcon;
    private int netState = -1;
    private final ConnectivityManager.NetworkCallback networkChanged = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) { runOnUiThread(MainActivity.this::refreshNetwork); }
        @Override public void onLost(Network n) { runOnUiThread(MainActivity.this::refreshNetwork); }
        // Also fires when the Wi-Fi signal gets stronger or weaker.
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
            runOnUiThread(MainActivity.this::refreshNetwork);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        saveConfig(getIntent());
        loadFonts();
        scale = getResources().getDisplayMetrics().widthPixels / 1920f;

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setClipChildren(false);
        root = new FrameLayout(this);
        root.setClipChildren(false);
        scroller.addView(root, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        shownLocale = java.util.Locale.getDefault();
        String day = Strings.datePattern();
        TextClock date = clock(day, day, px(30), 0xA8FFFFFF, regular);
        TextClock time = clock("h:mm", "H:mm", px(176), Color.WHITE, semibold);
        time.setLetterSpacing(-0.028f);
        time.setFontFeatureSettings("tnum");
        time.setIncludeFontPadding(false);
        clock.addView(date);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = px(-20); // pull the big digits up under the date
        clock.addView(time, tp);
        root.addView(clock, at(px(MARGIN), px(92), -2, -2));

        moveHint = text(px(24), 0xA8FFFFFF, regular);
        moveHint.setText(Strings.t("Use the arrows to move it, then press OK"));
        moveHint.setVisibility(View.GONE);
        root.addView(moveHint, at(px(MARGIN), px(ROW1_TOP - 64), -2, -2));

        stage = new FrameLayout(this);
        stage.addView(scroller, new FrameLayout.LayoutParams(-1, -1));
        setContentView(stage);

        SharedPreferences prefs = getSharedPreferences(PREFS, 0);
        motionOn = prefs.getBoolean("motion", true);
        customMinutes = prefs.getInt("sleepCustom", 15);
        applyBrightness(prefs.getInt("bright", 100));
        applyWallpaper();
        weatherView = text(px(28), 0xE6FFFFFF, regular);
        weatherView.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        weatherView.setText("…");
        root.addView(weatherView, at(0, px(ICON_TOP), px(280), px(ICON_BUTTON)));
        pageDots = text(px(22), 0x99FFFFFF, regular);
        pageDots.setGravity(Gravity.END);
        root.addView(pageDots, at(0, 0, -2, -2));
        refreshWeather();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(packagesChanged, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(packagesChanged, f);

        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) try {
            if (android.os.Build.VERSION.SDK_INT >= 24) cm.registerDefaultNetworkCallback(networkChanged);
            else cm.registerNetworkCallback(new NetworkRequest.Builder().build(), networkChanged);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        unregisterReceiver(packagesChanged);
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) try { cm.unregisterNetworkCallback(networkChanged); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (saveConfig(intent)) buildTiles();
    }

    // Saves an app list passed with --es apps "..." and shortcuts passed with --es quick "...".
    // Returns true if it changed.
    // When Android kills the home screen in the background, it can start it again with the same old
    // intent; the stamp install.sh sends stops that from undoing changes made on the TV since.
    private boolean saveConfig(Intent intent) {
        String apps = intent == null ? null : intent.getStringExtra("apps");
        String quick = intent == null ? null : intent.getStringExtra("quick");
        if (apps == null && quick == null) return false;
        SharedPreferences prefs = getSharedPreferences(PREFS, 0);
        String stamp = intent.getStringExtra("stamp");
        if (stamp != null && stamp.equals(prefs.getString("stamp", null))) return false;
        SharedPreferences.Editor e = prefs.edit();
        if (stamp != null) e.putString("stamp", stamp);
        if (apps != null) {
            apps = apps.trim();
            if (apps.isEmpty() || apps.equalsIgnoreCase("auto")) e.remove("apps");
            else e.putString("apps", apps);
        }
        if (quick != null) {
            quick = quick.trim();
            if (quick.isEmpty() || quick.equalsIgnoreCase("none")) e.remove("quick");
            else e.putString("quick", quick);
        }
        e.apply();
        dirty = true;
        return true;
    }

    private List<String> appList(PackageManager pm) {
        List<String> out = new ArrayList<>();
        String saved = getSharedPreferences(PREFS, 0).getString("apps", null);
        String[] wanted = saved != null ? saved.split("[,\\s]+") : Apps.LIST;
        for (String p : wanted) if (!p.isEmpty() && !out.contains(p) && launchIntent(pm, p) != null) out.add(p);
        if (saved == null) {
            // Top up with other TV apps people installed (or updated), skipping launchers and system tools.
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                if (out.size() >= PER_ROW * 2) break;
                String p = ri.activityInfo.packageName;
                int flags = ri.activityInfo.applicationInfo.flags;
                boolean stockSystem = (flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                if (stockSystem || out.contains(p) || NOT_APPS.contains(p) || p.equals(getPackageName())) continue;
                out.add(p);
            }
        }
        return out;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rebuild only when apps were installed, removed or updated.
        dirty = false;
        buildTiles();
        refreshNetwork();
        refreshWeather();
        considerNight();
        bumpIdle();
        View f = getCurrentFocus();
        if (f == null && !tiles.isEmpty()) tiles.get(0).requestFocus();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        bumpIdle();
    }

    @Override
    public void onBackPressed() {
        if (wallpaperLayer != null) { hideWallpaper(); return; }
        if (settingsLayer != null) { hideSettings(); return; }
        if (nightLayer != null) { lateDismissed = true; hideNight(); return; }
        if (powerLayer != null) { hidePower(); return; }
        if (ambientLayer != null) { hideAmbient(); return; }
    }

    // Back from a menu or a window on top (like the uninstall prompt): catch up on apps that changed.
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) refreshApps();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_WALL || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        getSharedPreferences(PREFS, 0).edit()
                .putString("wallpaper", "custom")
                .putString("wallpaperUri", uri.toString())
                .apply();
        applyWallpaper();
        hideWallpaper();
        if (wallpaperRow != null) wallpaperRow.setText(wallpaperLabel());
    }

    // Rebuilds the tiles if apps changed, but only while Home is in front with no menu open and no
    // app being moved; otherwise the next onResume, focus change or end of the move does it.
    private void refreshApps() {
        if (!dirty || !hasWindowFocus() || movingKey != null) return;
        dirty = false;
        buildTiles();
    }

    // The TV's language changed: start over so the labels and the date follow it.
    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        if (!java.util.Locale.getDefault().equals(shownLocale)) recreate();
    }

    // Static warm gradient, drawn once by the window: no extra view, no animation.
    private Drawable background() {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFF15100F, 0xFF0A0809, 0xFF050405});
        GradientDrawable warm = radial(0x36FF8048, 0.08f, -0.12f, px(1250));
        GradientDrawable plum = radial(0x2EAA3C8C, 1.0f, 1.1f, px(1000));
        for (GradientDrawable g : new GradientDrawable[] {base, warm, plum}) g.setDither(true);
        return new LayerDrawable(new Drawable[] {base, warm, plum});
    }

    private static GradientDrawable radial(int color, float cx, float cy, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setColors(new int[] {color, color & 0x00FFFFFF});
        g.setGradientCenter(cx, cy);
        g.setGradientRadius(r);
        return g;
    }

    private void buildTiles() {
        // While tiles are swapped out, removing the focused one would hand focus down the row (and
        // overwrite focusedKey), so hold focus off until the new tiles are in.
        String want = focusedKey;
        int wantAt = rowApps.indexOf(want); // where it was, in case the app is gone now
        root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        for (View t : tiles) {
            View wrap = (View) t.getParent();
            ((ViewGroup) wrap.getParent()).removeView(wrap);
        }
        tiles.clear();
        for (View v : watchChrome) {
            ViewGroup parent = (ViewGroup) v.getParent();
            if (parent != null) parent.removeView(v);
        }
        watchChrome.clear();
        rowApps.clear();
        movingKey = null;
        moveHint.setVisibility(View.GONE);

        PackageManager pm = getPackageManager();
        int m = px(MARGIN), g = px(GAP);
        int w = (getResources().getDisplayMetrics().widthPixels - 2 * m - g * (PER_ROW - 1)) / PER_ROW;
        int h = w * 9 / 16;
        tileW = w;
        tileH = h;

        List<String> apps = appList(pm);
        gridTop = px(ROW1_TOP);
        ((FrameLayout.LayoutParams) moveHint.getLayoutParams()).topMargin = gridTop - px(64);

        for (String pkg : apps) {
            if (launchIntent(pm, pkg) == null) continue;
            rowApps.add(pkg);
        }
        int pages = Math.max(1, (rowApps.size() + PER_ROW - 1) / PER_ROW);
        if (appPage >= pages) appPage = pages - 1;
        if (appPage < 0) appPage = 0;
        int start = appPage * PER_ROW;
        int end = Math.min(rowApps.size(), start + PER_ROW);
        for (int i = start; i < end; i++) {
            String pkg = rowApps.get(i);
            Intent li = launchIntent(pm, pkg);
            Drawable banner = null;
            try {
                banner = pm.getActivityBanner(li);
                if (banner == null) banner = pm.getApplicationBanner(pkg);
            } catch (Exception ignored) {}
            String label = labelOf(pkg);
            Drawable icon = null;
            if (banner == null) try { icon = pm.getApplicationIcon(pkg); } catch (Exception ignored) {}
            addTile(root, pkg, label, banner, null, icon, gridX(i), gridTop, w, h);
        }
        StringBuilder dots = new StringBuilder();
        for (int p = 0; p < pages; p++) {
            if (p > 0) dots.append("  ");
            dots.append(p == appPage ? "●" : "○");
        }
        pageDots.setText(pages > 1 ? dots.toString() : "");
        FrameLayout.LayoutParams dp = (FrameLayout.LayoutParams) pageDots.getLayoutParams();
        dp.leftMargin = getResources().getDisplayMetrics().widthPixels - m - px(160);
        dp.topMargin = gridTop + h + px(18);
        dp.width = px(160);
        pageDots.setLayoutParams(dp);

        List<WatchNext.Item> watching = WatchNext.load(this);
        int bottomTop = gridTop + px(ROW_STEP);
        if (!watching.isEmpty()) {
            int shown = Math.min(4, watching.size());
            int cw = Math.min(w, (getResources().getDisplayMetrics().widthPixels - 2 * m - g * (shown - 1)) / shown);
            int ch = cw * 9 / 16;
            int titleTop = gridTop + h + px(64);
            TextView heading = text(px(28), 0xE6FFFFFF, semibold);
            heading.setText(de("Weiter gucken", "Continue watching"));
            root.addView(heading, at(m, titleTop, -2, -2));
            watchChrome.add(heading);
            int cardTop = titleTop + px(48);
            for (int i = 0; i < shown; i++)
                addWatch(watching.get(i), m + i * (cw + g), cardTop, cw, ch);
            bottomTop = cardTop + ch + px(78);
        }
        root.setMinimumHeight(bottomTop + h + px(MARGIN));

        // Bottom row: Play Store, HDMI inputs and Add app. Settings live in the gear at the top.
        int col = 0;
        boolean store = !apps.contains(PLAY_STORE) && launchIntent(pm, PLAY_STORE) != null;
        List<TvInputInfo> inputs = hdmiInputs();
        int room = PER_ROW - 1 - (store ? 1 : 0);
        if (inputs.size() > room) inputs = inputs.subList(0, room);
        if (store)
            addTile(root, PLAY_STORE, "Play Store", null, LineIcon.PLAY, null, m + col++ * (w + g), bottomTop, w, h);
        for (TvInputInfo in : inputs)
            addTile(root, in.getId(), inputLabel(in), null, LineIcon.HDMI, null, m + col++ * (w + g), bottomTop, w, h);
        addTile(root, ADD, Strings.t("Add app"), null, LineIcon.PLUS, null, m + col * (w + g), bottomTop, w, h);

        // Plain icons at the top right, level with the date: any settings shortcuts picked, then the
        // network icon. Added last, so the first app keeps first focus; Up from the apps reaches them.
        List<Integer> quick = quickList();
        int ib = px(ICON_BUTTON), ig = px(ICON_GAP);
        // The last icon's right edge lines up with the apps' (its focus circle sticks out past them).
        int qx = getResources().getDisplayMetrics().widthPixels - m + (ib - px(ICON_SIZE)) / 2
                - (quick.size() + 2) * ib - (quick.size() + 1) * ig;
        for (int q : quick) {
            addIcon(QUICK + QUICK_NAMES[q], Strings.t(QUICK_LABELS[q]), new LineIcon(QUICK_ICONS[q]), qx);
            qx += ib + ig;
        }
        addIcon("launcher-settings", de("Einstellungen", "Settings"), new LineIcon(LineIcon.SETTINGS), qx);
        qx += ib + ig;
        netIcon = addIcon(NETWORK, Strings.t("Network"), null, qx);
        FrameLayout.LayoutParams wp = (FrameLayout.LayoutParams) weatherView.getLayoutParams();
        wp.leftMargin = qx - px(300);
        weatherView.setLayoutParams(wp);
        netState = -1;
        refreshNetwork();

        root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        // An app that was uninstalled hands focus to the one now in its place, as Remove does.
        if (wantAt >= 0 && !rowApps.contains(want))
            want = rowApps.isEmpty() ? ADD : rowApps.get(Math.min(wantAt, rowApps.size() - 1));
        View restore = null;
        for (View t : tiles) if (t.getTag().equals(want)) restore = t;
        if (restore != null) restore.requestFocus();
    }

    private void addTile(ViewGroup parent, final String key, String label, Drawable banner, int[] lineIcon, Drawable appIcon,
                         int x, int y, int w, int h) {
        FrameLayout card = new FrameLayout(this);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(RADIUS));
        if (banner != null) {
            bg.setColor(0xFF1E1A1C);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(fit(banner, w, h));
            card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            // Quiet "glass" tile: icon above label.
            bg.setColor(0x13FFFFFF);
            bg.setStroke(Math.max(1, px(2)), 0x17FFFFFF);
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setGravity(Gravity.CENTER);
            ImageView iv = new ImageView(this);
            int size = lineIcon != null ? px(44) : px(64);
            if (lineIcon != null) iv.setImageDrawable(new LineIcon(lineIcon));
            else if (appIcon != null) iv.setImageDrawable(fit(appIcon, size, size));
            inner.addView(iv, new LinearLayout.LayoutParams(size, size));
            TextView t = text(px(24), 0xE0FFFFFF, regular);
            t.setText(label);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setPadding(px(16), 0, px(16), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = px(12);
            inner.addView(t, lp);
            card.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        }
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setFocusable(true);
        card.setTag(key);

        // Banner tiles show their name underneath when focused.
        final TextView caption = banner == null ? null : text(px(24), Color.WHITE, regular);
        if (caption != null) {
            caption.setText(label);
            caption.setGravity(Gravity.CENTER_HORIZONTAL);
            caption.setVisibility(View.INVISIBLE);
        }

        final boolean glass = banner == null;
        card.setOnFocusChangeListener((v, has) -> {
            if (has) focusedKey = key;
            if (glass) bg.setColor(has ? 0x38FFFFFF : 0x13FFFFFF);
            v.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f)
                    .setDuration(motionOn ? 140 : 0).withLayer().start();
            if (caption != null) caption.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
        });
        final boolean editable = rowApps.contains(key);
        card.setOnClickListener(v -> open(key));
        if (editable) card.setOnLongClickListener(v -> { showEditMenu(key); return true; });
        card.setOnKeyListener((v, code, ev) -> {
            boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
            boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
            if (key.equals(movingKey)) return moveKey(key, code, ok, down);
            if (down && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                int idx = rowApps.indexOf(key);
                if (idx >= 0) {
                    int slot = idx % PER_ROW;
                    int page = idx / PER_ROW;
                    int pages = Math.max(1, (rowApps.size() + PER_ROW - 1) / PER_ROW);
                    boolean back = code == KeyEvent.KEYCODE_DPAD_LEFT && slot == 0 && page > 0;
                    boolean forward = code == KeyEvent.KEYCODE_DPAD_RIGHT
                            && (slot == PER_ROW - 1 || idx == rowApps.size() - 1) && page + 1 < pages;
                    if (back || forward) {
                        appPage = page + (forward ? 1 : -1);
                        int next = forward ? appPage * PER_ROW : Math.min(rowApps.size() - 1, appPage * PER_ROW + PER_ROW - 1);
                        focusedKey = rowApps.get(next);
                        buildTiles();
                        return true;
                    }
                }
            }
            if (editable && code == KeyEvent.KEYCODE_MENU) {
                if (!down) showEditMenu(key);
                return true;
            }
            if (!ok) return false;
            if (down) {
                if (ev.getRepeatCount() == 0) longPressed = false;
                else if (editable && !longPressed && (ev.isLongPress()
                        || ev.getEventTime() - ev.getDownTime() >= ViewConfiguration.getLongPressTimeout())) {
                    longPressed = true;
                    showEditMenu(key);
                }
            } else if (!longPressed) {
                open(key);
            }
            return true;
        });

        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.addView(card, new FrameLayout.LayoutParams(w, h));
        if (caption != null) {
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(w, -2);
            cp.topMargin = h + px(22);
            wrap.addView(caption, cp);
        }
        parent.addView(wrap, at(x, y, w, -2));
        tiles.add(card);
    }

    // One program an app reported as in progress. Hidden entirely when the TV shares no such list.
    private void addWatch(WatchNext.Item item, int x, int y, int w, int h) {
        String key = "watch\n" + (item.pkg == null ? "" : item.pkg) + "\n" + item.intent;
        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(RADIUS));
        bg.setColor(0xFF1E1A1C);
        card.setBackground(bg);
        card.setClipToOutline(true);
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(poster, new FrameLayout.LayoutParams(-1, -1));
        if (item.poster != null) loadPoster(poster, item.poster, w, h);

        String app = item.pkg == null || item.pkg.isEmpty() ? "" : labelOf(item.pkg);
        if (!app.isEmpty()) {
            TextView badge = text(px(18), Color.WHITE, regular);
            badge.setText(app);
            badge.setPadding(px(12), px(6), px(12), px(6));
            GradientDrawable pill = new GradientDrawable();
            pill.setColor(0xCC000000);
            pill.setCornerRadius(px(20));
            badge.setBackground(pill);
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2);
            bp.leftMargin = px(12);
            bp.topMargin = px(12);
            card.addView(badge, bp);
        }
        if (item.duration > 0 && item.position > 0) {
            View track = new View(this);
            track.setBackgroundColor(0x66FFFFFF);
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, px(6), Gravity.BOTTOM);
            tp.leftMargin = px(12);
            tp.rightMargin = px(12);
            tp.bottomMargin = px(12);
            card.addView(track, tp);
            View bar = new View(this);
            bar.setBackgroundColor(Color.WHITE);
            int width = (int) ((w - px(24)) * Math.min(item.position, item.duration) / item.duration);
            FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(Math.max(px(4), width), px(6), Gravity.BOTTOM);
            fp.leftMargin = px(12);
            fp.bottomMargin = px(12);
            card.addView(bar, fp);
        }
        card.setFocusable(true);
        card.setTag(key);
        card.setContentDescription(item.title);
        card.setOnFocusChangeListener((v, has) -> {
            if (has) focusedKey = key;
            v.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f)
                    .setDuration(motionOn ? 140 : 0).withLayer().start();
        });
        card.setOnClickListener(v -> open(key));
        card.setOnKeyListener((v, code, ev) -> {
            boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
            if (!ok) return false;
            if (ev.getAction() == KeyEvent.ACTION_UP) open(key);
            return true;
        });

        TextView caption = text(px(22), 0xB3FFFFFF, regular);
        caption.setText(item.title);
        caption.setSingleLine(true);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView sub = text(px(18), 0x80FFFFFF, regular);
        sub.setText(watchSub(item));
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);

        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.addView(card, new FrameLayout.LayoutParams(w, h));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(w, -2);
        cp.topMargin = h + px(12);
        wrap.addView(caption, cp);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(w, -2);
        sp.topMargin = h + px(40);
        wrap.addView(sub, sp);
        root.addView(wrap, at(x, y, w, -2));
        tiles.add(card);
    }

    private String watchSub(WatchNext.Item item) {
        StringBuilder s = new StringBuilder();
        if (item.season != null && item.season.length() > 0 && item.episode != null && item.episode.length() > 0)
            s.append("S").append(item.season).append(" · E").append(item.episode);
        else if (item.episodeTitle != null && item.episodeTitle.length() > 0)
            s.append(item.episodeTitle);
        if (item.duration > item.position && item.position >= 0 && item.duration > 0) {
            int mins = (int) ((item.duration - item.position + 59999) / 60000);
            if (mins > 0) {
                if (s.length() > 0) s.append(" · ");
                if (mins >= 60) s.append(de("noch ", "")).append(mins / 60).append(de(" Std ", " h "))
                        .append(mins % 60).append(de(" Min", " min"));
                else s.append(de("noch " + mins + " Min", mins + " min left"));
            }
        }
        return s.toString();
    }

    private void loadPoster(ImageView iv, String uri, int w, int h) {
        new Thread(() -> {
            Bitmap bmp = null;
            java.io.InputStream in = null;
            HttpURLConnection conn = null;
            try {
                Uri u = Uri.parse(uri);
                if ("http".equals(u.getScheme()) || "https".equals(u.getScheme())) {
                    conn = (HttpURLConnection) new URL(uri).openConnection();
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    in = conn.getInputStream();
                } else {
                    in = getContentResolver().openInputStream(u);
                }
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                bmp = BitmapFactory.decodeStream(in, null, opts);
            } catch (Exception ignored) {
            } finally {
                try { if (in != null) in.close(); } catch (Exception ignored) {}
                if (conn != null) conn.disconnect();
            }
            Bitmap shown = bmp;
            if (shown == null) return;
            runOnUiThread(() -> { if (iv.isAttachedToWindow()) iv.setImageBitmap(shown); });
        }).start();
    }

    // An icon on its own, no tile or label: a glass circle shows behind it only while it's focused.
    private ImageView addIcon(final String key, String label, Drawable icon, int x) {
        int b = px(ICON_BUTTON), size = px(ICON_SIZE);
        FrameLayout card = new FrameLayout(this);
        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.TRANSPARENT);
        card.setBackground(bg);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(icon);
        card.addView(iv, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        card.setFocusable(true);
        card.setTag(key);
        card.setContentDescription(label);
        card.setOnFocusChangeListener((v, has) -> {
            if (has) focusedKey = key;
            bg.setColor(has ? 0x38FFFFFF : Color.TRANSPARENT);
            v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(140).withLayer().start();
        });
        card.setOnClickListener(v -> open(key));
        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.addView(card, new FrameLayout.LayoutParams(b, b));
        root.addView(wrap, at(x, px(ICON_TOP), b, b));
        tiles.add(card);
        return iv;
    }

    private void open(String key) {
        Intent i;
        if (key.startsWith("watch\n")) {
            String[] parts = key.split("\n", 3);
            openWatch(parts.length > 1 ? parts[1] : "", parts.length > 2 ? parts[2] : "");
            return;
        } else if (ADD.equals(key)) {
            showAddMenu(-1);
            return;
        } else if (SETTINGS.equals(key) || "launcher-settings".equals(key)) {
            showSettings();
            return;
        } else if (isInput(key)) {
            openInput(key);
            return;
        } else if (NETWORK.equals(key)) {
            openNetworkSettings();
            return;
        } else if (key.startsWith(QUICK)) {
            openQuick(key.substring(QUICK.length()));
            return;
        } else {
            i = launchIntent(getPackageManager(), key);
            if (i == null) return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) {}
    }

    // Opens the episode the app reported, and the app itself when that link is gone.
    private void openWatch(String pkg, String intentUri) {
        if (intentUri != null && !intentUri.isEmpty()) {
            try {
                Intent i = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return;
            } catch (Exception ignored) {}
        }
        Intent i = pkg == null || pkg.isEmpty() ? null : launchIntent(getPackageManager(), pkg);
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) {}
    }

    // Live TV apps an HDMI tile can open, in order. Droidlogic (Amlogic) TVs ship Live TV under their
    // own package, with TvActivity as its entry point.
    private static final String[][] LIVE_TV = {{"com.android.tv", "com.android.tv.MainActivity"},
            {"com.droidlogic.android.tv", "com.android.tv.TvActivity"}};
    private static final String INPUT_PLAYER = "com.google.android.tv.inputplayer";

    // Opens an HDMI input: the stock Live TV app first, then Droidlogic's, then whatever the TV picks.
    // Each one is tried only when the one before it can't start, so a TV where the stock app already
    // worked takes exactly the same path as before. The one exception: when the TV itself sends HDMI to
    // Google TV's input player, that goes first, because some Google TVs ship com.android.tv as a stub
    // that starts fine but only says "Hello World!" (issue #5).
    private void openInput(String id) {
        Intent view = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
        List<Intent> tries = new ArrayList<>();
        ResolveInfo def = getPackageManager().resolveActivity(view, PackageManager.MATCH_DEFAULT_ONLY);
        if (def != null && def.activityInfo != null && INPUT_PLAYER.equals(def.activityInfo.packageName))
            tries.add(new Intent(view).setClassName(INPUT_PLAYER, def.activityInfo.name));
        for (String[] live : LIVE_TV) {
            Intent t = new Intent(view).setClassName(live[0], live[1]);
            if (t.resolveActivity(getPackageManager()) != null) tries.add(t);
        }
        tries.add(view);
        for (Intent t : tries) {
            t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(t);
                return;
            } catch (Exception ignored) {}
        }
    }

    // The shortcuts picked with --es quick, in that order, as indexes into QUICK_NAMES. A shortcut
    // this TV has no screen for is left out, so it never shows a tile that does nothing.
    private List<Integer> quickList() {
        List<Integer> out = new ArrayList<>();
        String saved = getSharedPreferences(PREFS, 0).getString("quick", null);
        if (saved == null) return out;
        for (String name : saved.split("[,\\s]+")) {
            int q = Arrays.asList(QUICK_NAMES).indexOf(name.toLowerCase(java.util.Locale.ROOT));
            if (q < 0 || out.contains(q) || quickIntents(QUICK_NAMES[q]).isEmpty()) continue;
            out.add(q);
        }
        return out;
    }

    // OFFLINE, 1 to 3 for Wi-Fi with that many bars, or ETHERNET (any other link, which on a TV is a cable).
    private int networkState() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return OFFLINE;
        try {
            int rssi = Integer.MIN_VALUE;
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                NetworkCapabilities c = cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (c == null) return OFFLINE;
                if (!c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return ETHERNET;
                if (android.os.Build.VERSION.SDK_INT >= 29) rssi = c.getSignalStrength();
            } else {
                android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
                if (ni == null || !ni.isConnected()) return OFFLINE;
                if (ni.getType() != ConnectivityManager.TYPE_WIFI) return ETHERNET;
            }
            if (rssi == Integer.MIN_VALUE) { // Android 9 and older: ask the Wi-Fi service
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null && wm.getConnectionInfo() != null) rssi = wm.getConnectionInfo().getRssi();
            }
            return 1 + WifiManager.calculateSignalLevel(rssi, 3);
        } catch (Exception e) {
            return OFFLINE;
        }
    }

    private void refreshNetwork() {
        if (netIcon == null) return;
        int state = networkState();
        if (state == netState) return;
        netState = state;
        if (state == ETHERNET) {
            netIcon.setImageDrawable(new LineIcon(LineIcon.ETHERNET));
        } else {
            // The whole Wi-Fi fan dim, with the bars it has bright on top. Offline is a slash instead.
            LineIcon fan = new LineIcon(LineIcon.WIFI);
            fan.setAlpha(0x47);
            int[] top = state == OFFLINE ? LineIcon.SLASH : state == 1 ? LineIcon.WIFI_1
                    : state == 2 ? LineIcon.WIFI_2 : LineIcon.WIFI;
            netIcon.setImageDrawable(new LayerDrawable(new Drawable[] {fan, new LineIcon(top)}));
        }
        ((View) netIcon.getParent()).setContentDescription(
                state == OFFLINE ? Strings.t("Offline") : state == ETHERNET ? "Ethernet" : "Wi-Fi");
    }

    // Wi-Fi settings (on Android TV that page lists Ethernet too), else the TV's network page, else Settings.
    private void openNetworkSettings() {
        for (String a : new String[] {Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS,
                Settings.ACTION_SETTINGS}) {
            try {
                startActivity(new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {}
        }
    }

    // Screens a shortcut can open, best first, keeping only those this TV has.
    private List<Intent> quickIntents(String name) {
        List<Intent> all = new ArrayList<>();
        String tclType = name.equals("sound") ? "SoundPreset" : name.equals("picture") ? "picture" : null;
        if (tclType != null)
            all.add(new Intent(TCL_SHOW_WINDOW).setClassName("com.tcl.settings", "com.tcl.settings.ShowWindowService")
                    .putExtra("Type", tclType));
        switch (name) {
            case "bluetooth":
                all.add(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                all.add(new Intent("com.google.android.intent.action.CONNECT_INPUT")); // Android TV's pairing screen
                break;
            case "sound": all.add(new Intent(Settings.ACTION_SOUND_SETTINGS)); break;
            case "picture": all.add(new Intent(Settings.ACTION_DISPLAY_SETTINGS)); break;
        }
        PackageManager pm = getPackageManager();
        List<Intent> out = new ArrayList<>();
        for (Intent i : all) {
            boolean service = TCL_SHOW_WINDOW.equals(i.getAction());
            if (service ? pm.resolveService(i, 0) != null : i.resolveActivity(pm) != null) out.add(i);
        }
        return out;
    }

    // Opens a shortcut's screen: the first one that starts wins.
    private void openQuick(String name) {
        for (Intent i : quickIntents(name)) {
            try {
                if (TCL_SHOW_WINDOW.equals(i.getAction())) startService(i);
                else startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception ignored) {}
        }
    }

    interface Pick { void at(int which); }

    // A side panel in the home screen's own look: a title, then one row per choice, with the same
    // glass focus as the tiles. Back closes it. iconPkgs (or null) puts each app's icon by its name.
    private void panel(String title, final List<String> labels, final List<String> iconPkgs, final Pick pick) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(48), px(92), px(48), px(48));

        TextView head = text(px(40), Color.WHITE, semibold);
        head.setText(title);
        head.setSingleLine(true);
        head.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.setPadding(px(24), 0, px(24), 0);
        box.addView(head);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        GradientDrawable focus = new GradientDrawable();
        focus.setCornerRadius(px(RADIUS));
        focus.setColor(0x38FFFFFF);
        list.setSelector(focus);
        // Icons load as rows appear, drawn small to save RAM.
        final PackageManager pm = getPackageManager();
        final int size = px(48);
        final Drawable[] icons = new Drawable[labels.size()];
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return labels.size(); }
            @Override public Object getItem(int pos) { return labels.get(pos); }
            @Override public long getItemId(int pos) { return pos; }
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                TextView t = convert != null ? (TextView) convert : text(px(30), 0xE6FFFFFF, regular);
                t.setText(labels.get(pos));
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setMinHeight(px(88));
                t.setPadding(px(24), 0, px(24), 0);
                Drawable icon = null;
                if (iconPkgs != null) {
                    if (icons[pos] == null) {
                        try { icons[pos] = fit(pm.getApplicationIcon(iconPkgs.get(pos)), size, size); }
                        catch (Exception e) { icons[pos] = new ColorDrawable(Color.TRANSPARENT); }
                        icons[pos].setBounds(0, 0, size, size);
                    }
                    icon = icons[pos];
                }
                t.setCompoundDrawablesRelative(icon, null, null, null);
                t.setCompoundDrawablePadding(px(24));
                return t;
            }
        });
        list.setOnItemClickListener((parent, v, pos, id) -> {
            d.dismiss();
            pick.at(pos);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = px(36);
        box.addView(list, lp);

        d.setContentView(box);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(0xF7161113));
        w.setLayout(px(640), -1);
        w.setGravity(Gravity.END);
        w.setDimAmount(0.5f);
        d.show();
    }

    // A small menu that drops down under a tile, in the same glass as the tiles. Opens above the
    // tile when there's no room below. Back closes it.
    private void dropdown(View anchor, String[] items, final Pick pick) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = px(10), rowH = px(64);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(RADIUS));
        bg.setColor(0xFF231D1F);
        bg.setStroke(Math.max(1, px(2)), 0x17FFFFFF);
        box.setBackground(bg);
        float widest = 0; // some languages need more room than the tile is wide
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            final TextView t = text(px(26), 0xD9FFFFFF, regular);
            t.setText(Strings.t(items[i]));
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(px(22), 0, px(22), 0);
            widest = Math.max(widest, t.getPaint().measureText(t.getText().toString()) + 2 * px(22) + 2 * pad);
            final GradientDrawable focus = new GradientDrawable();
            focus.setCornerRadius(px(RADIUS - pad));
            focus.setColor(Color.TRANSPARENT);
            t.setBackground(focus);
            t.setFocusable(true);
            t.setOnFocusChangeListener((v, has) -> {
                focus.setColor(has ? 0x38FFFFFF : Color.TRANSPARENT);
                t.setTextColor(has ? Color.WHITE : 0xD9FFFFFF);
            });
            t.setOnClickListener(v -> {
                d.dismiss();
                pick.at(which);
            });
            box.addView(t, new LinearLayout.LayoutParams(-1, rowH));
        }
        d.setContentView(box);

        // Line it up with the tile as drawn (focused tiles are scaled up), below its name.
        int[] at = new int[2];
        anchor.getLocationOnScreen(at);
        int tileBottom = at[1] + Math.round(anchor.getHeight() * anchor.getScaleY());
        int w = Math.max(Math.max(Math.round(anchor.getWidth() * anchor.getScaleX()), px(320)), (int) Math.ceil(widest));
        int h = items.length * rowH + 2 * pad;
        int y = tileBottom + px(64);
        if (y + h > getResources().getDisplayMetrics().heightPixels - px(24)) y = at[1] - h - px(16);
        Window win = d.getWindow();
        win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        win.setGravity(Gravity.TOP | Gravity.START);
        android.view.WindowManager.LayoutParams lp = win.getAttributes();
        lp.x = at[0];
        lp.y = y;
        lp.width = w;
        lp.height = h;
        win.setAttributes(lp);
        d.show();
    }

    // Hold OK (or Menu) on a top-row app: move it, swap it for another, or take it off the home
    // screen. Removing only takes the tile away: the app stays installed and can be added back.
    // Apps the user installed can also be uninstalled; Android asks to confirm, and the tile goes
    // once the app is gone. Apps that came with the TV can't be, so they get App info instead,
    // where Android offers to turn them off.
    private void showEditMenu(final String key) {
        final int index = rowApps.indexOf(key);
        if (index < 0) return;
        final boolean user = userInstalled(key);
        dropdown(tile(key), user ? EDIT_ITEMS : EDIT_ITEMS_SYSTEM, which -> {
            if (which == 0) startMove(key);
            else if (which == 1) showAddMenu(index);
            else if (which == 3) {
                Uri pkg = Uri.parse("package:" + key);
                try { startActivity(user ? new Intent(Intent.ACTION_DELETE, pkg)
                        : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)); }
                catch (Exception ignored) {}
            } else {
                rowApps.remove(index);
                saveRow();
                focusedKey = rowApps.isEmpty() ? ADD : rowApps.get(Math.min(index, rowApps.size() - 1));
                buildTiles();
            }
        });
    }

    // Every launchable app that isn't on the top row yet, by name. The pick is added at the end,
    // or takes the place of the app at replaceIndex (-1 to add).
    private void showAddMenu(final int replaceIndex) {
        final PackageManager pm = getPackageManager();
        TreeMap<String, String> byLabel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String p = ri.activityInfo.packageName;
                if (rowApps.contains(p) || byLabel.containsValue(p) || p.equals(getPackageName())
                        || (NOT_APPS.contains(p) && !p.equals(PLAY_STORE))) continue;
                String label = labelOf(p);
                if (!byLabel.containsKey(label)) byLabel.put(label, p);
            }
        }
        if (byLabel.isEmpty()) {
            panel(Strings.t("Every app is already here"), Collections.singletonList("OK"), null, which -> {});
            return;
        }
        final List<String> pkgs = new ArrayList<>(byLabel.values());
        String title = replaceIndex < 0 ? Strings.t("Add an app")
                : String.format(Strings.t("Replace %s"), labelOf(rowApps.get(replaceIndex)));
        panel(title, new ArrayList<>(byLabel.keySet()), pkgs, which -> {
            if (replaceIndex < 0) rowApps.add(pkgs.get(which));
            else rowApps.set(replaceIndex, pkgs.get(which));
            saveRow();
            focusedKey = pkgs.get(which);
            buildTiles();
        });
    }

    private String labelOf(String pkg) {
        PackageManager pm = getPackageManager();
        try { return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }

    // Saves the top row as the app list, so it no longer follows apps.txt.
    private void saveRow() {
        getSharedPreferences(PREFS, 0).edit().putString("apps", android.text.TextUtils.join(",", rowApps)).apply();
    }

    // Where app number i sits in the grid.
    private int gridX(int i) {
        return px(MARGIN) + (i % PER_ROW) * (tileW + px(GAP));
    }

    private int gridY(int i) {
        return gridTop + (i / PER_ROW) * px(ROW_STEP);
    }

    private View tile(String key) {
        for (View t : tiles) if (t.getTag().equals(key)) return t;
        return null;
    }

    // Move mode: the tile gets an outline, the arrows move it through the grid, OK or Back finishes.
    private void startMove(String key) {
        View card = tile(key);
        if (card == null) return;
        movingKey = key;
        GradientDrawable outline = new GradientDrawable();
        outline.setCornerRadius(px(RADIUS));
        outline.setStroke(px(4), Color.WHITE);
        ((FrameLayout) card).setForeground(outline);
        moveHint.setVisibility(View.VISIBLE);
        card.requestFocus();
    }

    private boolean moveKey(String key, int code, boolean ok, boolean down) {
        int step = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1
                : code == KeyEvent.KEYCODE_DPAD_UP ? -PER_ROW : code == KeyEvent.KEYCODE_DPAD_DOWN ? PER_ROW : 0;
        if (step != 0) {
            int from = rowApps.indexOf(key), to = from + step;
            if (down && to >= 0 && to < rowApps.size()) {
                rowApps.add(to, rowApps.remove(from));
                if (to / PER_ROW != appPage) {
                    appPage = to / PER_ROW;
                    focusedKey = key;
                    buildTiles();
                    startMove(key);
                    return true;
                }
                int a = appPage * PER_ROW, b = Math.min(rowApps.size(), a + PER_ROW);
                for (int i = a; i < b; i++) {
                    View shown = tile(rowApps.get(i));
                    if (shown == null) continue;
                    View wrap = (View) shown.getParent();
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) wrap.getLayoutParams();
                    lp.leftMargin = gridX(i);
                    lp.topMargin = gridTop;
                    wrap.setLayoutParams(lp);
                }
                final View card = tile(key);
                if (card != null) card.post(() -> card.requestRectangleOnScreen(
                        new android.graphics.Rect(0, 0, card.getWidth(), card.getHeight())));
            }
            return true;
        }
        if (ok || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
            if (!down) {
                movingKey = null;
                longPressed = false;
                ((FrameLayout) tile(key)).setForeground(null);
                moveHint.setVisibility(View.GONE);
                saveRow();
                refreshApps();
            }
            return true;
        }
        return true; // up/down and everything else stay put while moving
    }

    private static boolean isInput(String key) {
        return key.indexOf('/') > 0;
    }

    // The TV's own HDMI inputs (not devices behind them), in order, skipping hidden ones.
    private List<TvInputInfo> hdmiInputs() {
        List<TvInputInfo> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        for (TvInputInfo in : tim.getTvInputList()) {
            if (in.getType() == TvInputInfo.TYPE_HDMI && in.getParentId() == null && !in.isHidden(this)) out.add(in);
        }
        Collections.sort(out, (a, b) -> a.getId().compareTo(b.getId()));
        return out.size() > MAX_INPUTS ? out.subList(0, MAX_INPUTS) : out;
    }

    // The name set in Settings > Inputs (like "Bedroom"), else the TV's own name (like "HDMI 1").
    private String inputLabel(TvInputInfo in) {
        CharSequence custom = in.loadCustomLabel(this);
        if (custom != null && custom.length() > 0) return custom.toString();
        CharSequence label = in.loadLabel(this);
        String name = label != null && label.length() > 0 ? label.toString() : "HDMI";
        String plain = name.toLowerCase(java.util.Locale.ROOT).replace(" ", "");
        if (plain.equals("hdmi1")) return "PS5";
        if (plain.equals("hdmi2")) return "Switch";
        if (plain.equals("hdmi3")) return "Receiver";
        return name;
    }

    private boolean userInstalled(String pkg) {
        try { return (getPackageManager().getApplicationInfo(pkg, 0).flags & ApplicationInfo.FLAG_SYSTEM) == 0; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    // Xiaomi TVs ship MiLan Pro but don't register it by name; every other TV gets Roboto.
    private static void loadFonts() {
        if (regular != null) return;
        regular = fromFile("/system/fonts/MILanPro_Normal.ttf", Typeface.create("sans-serif", Typeface.NORMAL));
        semibold = fromFile("/system/fonts/MILanPro_SemiBold.ttf", Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    private static Typeface fromFile(String path, Typeface fallback) {
        try { return new File(path).exists() ? Typeface.createFromFile(path) : fallback; }
        catch (Exception e) { return fallback; }
    }

    // Draws a banner once into a tile-sized bitmap (center-cropped). Some apps ship huge banners
    // that Android 9 refuses to draw ("Canvas: trying to draw too large bitmap"); this also saves RAM.
    private Drawable fit(Drawable d, int w, int h) {
        try {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) { iw = w; ih = h; }
            float s = Math.max((float) w / iw, (float) h / ih);
            int dw = Math.round(iw * s), dh = Math.round(ih * s);
            d.setBounds((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh);
            d.draw(new Canvas(b));
            return new BitmapDrawable(getResources(), b);
        } catch (Throwable t) {
            return d;
        }
    }

    private int px(int v) {
        return Math.round(v * scale);
    }

    private TextClock clock(String f12, String f24, int px, int color, Typeface tf) {
        TextClock c = new TextClock(this);
        c.setFormat12Hour(f12);
        c.setFormat24Hour(f24);
        c.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        c.setTextColor(color);
        c.setTypeface(tf);
        return c;
    }

    private TextView text(int px, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    private static FrameLayout.LayoutParams at(int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        return lp;
    }

    private static String de(String german, String english) {
        return "de".equals(Strings.lang()) ? german : english;
    }

    private void bumpIdle() {
        ui.removeCallbacks(goAmbient);
        if (ambientLayer == null && settingsLayer == null && wallpaperLayer == null
                && nightLayer == null && powerLayer == null)
            ui.postDelayed(goAmbient, 3L * 60L * 1000L);
    }

    private void applyBrightness(int percent) {
        int p = Math.max(25, Math.min(100, percent));
        Window w = getWindow();
        android.view.WindowManager.LayoutParams lp = w.getAttributes();
        lp.screenBrightness = p / 100f;
        w.setAttributes(lp);
        getSharedPreferences(PREFS, 0).edit().putInt("bright", p).apply();
    }

    private void showSettings() {
        if (settingsLayer != null) return;
        hideAmbient();
        FrameLayout layer = new FrameLayout(this);
        layer.setOnClickListener(v -> hideSettings());
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF12100F);
        bg.setCornerRadius(px(RADIUS));
        menu.setBackground(bg);
        menu.setPadding(px(28), px(24), px(28), px(24));
        TextView title = text(px(28), Color.WHITE, semibold);
        title.setText(de("Einstellungen", "Settings"));
        menu.addView(title);

        TextView brightLabel = text(px(22), 0xB3FFFFFF, regular);
        brightLabel.setText(de("Helligkeit", "Brightness"));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-1, -2);
        gap.topMargin = px(22);
        menu.addView(brightLabel, gap);
        SeekBar bar = new SeekBar(this);
        bar.setMax(75);
        bar.setProgress(getSharedPreferences(PREFS, 0).getInt("bright", 100) - 25);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser) applyBrightness(progress + 25);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        menu.addView(bar, gap);

        menu.addView(settingsRow(motionOn ? de("Animationen an", "Animations on")
                : de("Animationen aus", "Animations off"), () -> {
            motionOn = !motionOn;
            getSharedPreferences(PREFS, 0).edit().putBoolean("motion", motionOn).apply();
            hideSettings();
            buildTiles();
            showSettings();
        }), gap);

        wallpaperRow = settingsRow(wallpaperLabel(), this::showWallpaper);
        menu.addView(wallpaperRow, gap);

        TextView sleep = text(px(22), 0xB3FFFFFF, regular);
        sleep.setText(de("TV ausschalten nach", "Turn the TV off after"));
        menu.addView(sleep, gap);
        int[] minutes = {10, 20, 30, 45, 60, 0};
        for (int min : minutes) {
            String label = min == 0 ? de("Aus", "Off") : min + " Min";
            menu.addView(settingsRow(label, () -> armSleep(min)), new LinearLayout.LayoutParams(-1, -2));
        }
        menu.addView(settingsRow(de("Eigene Zeit: ", "Custom: ") + customMinutes + " Min", () -> armSleep(customMinutes)), gap);
        TextView hint = text(px(18), 0x80FFFFFF, regular);
        hint.setText(de("Links und rechts ändern die eigene Zeit", "Left and right change the custom time"));
        menu.addView(hint);
        View custom = menu.getChildAt(menu.getChildCount() - 2);
        custom.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
            int step = code == KeyEvent.KEYCODE_DPAD_RIGHT ? 5 : code == KeyEvent.KEYCODE_DPAD_LEFT ? -5 : 0;
            if (step == 0) return false;
            customMinutes = Math.max(1, Math.min(480, customMinutes + step));
            getSharedPreferences(PREFS, 0).edit().putInt("sleepCustom", customMinutes).apply();
            ((TextView) v).setText(de("Eigene Zeit: ", "Custom: ") + customMinutes + " Min");
            return true;
        });

        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (am != null) am.getMemoryInfo(info);
        long free = info.availMem / (1024 * 1024);
        menu.addView(settingsRow(de("Speicher freigeben", "Free up memory") + " · " + free + " MB", () -> {
            android.widget.Toast.makeText(this, free + " MB " + de("frei. Android räumt selbst auf.",
                    "free. Android clears memory on its own."), android.widget.Toast.LENGTH_LONG).show();
        }), gap);
        menu.addView(settingsRow(de("Android-Einstellungen", "Android settings"), () -> {
            hideSettings();
            try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception ignored) {}
        }), gap);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(menu);
        scroll.setClickable(true);
        int width = px(520);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(width, -2);
        mp.gravity = Gravity.TOP | Gravity.END;
        mp.topMargin = px(150);
        mp.rightMargin = px(84);
        layer.addView(scroll, mp);
        stage.addView(layer, new FrameLayout.LayoutParams(-1, -1));
        settingsLayer = layer;
        menu.getChildAt(2).requestFocus();
        bumpIdle();
    }

    private TextView settingsRow(String label, Runnable action) {
        TextView row = text(px(24), Color.WHITE, regular);
        row.setText(label);
        row.setFocusable(true);
        row.setMinHeight(px(64));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(16), 0, px(16), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(12));
        row.setBackground(bg);
        row.setOnFocusChangeListener((v, has) -> bg.setColor(has ? 0x33FFFFFF : Color.TRANSPARENT));
        row.setOnClickListener(v -> action.run());
        return row;
    }

    private void hideSettings() {
        if (settingsLayer == null) return;
        stage.removeView(settingsLayer);
        settingsLayer = null;
        wallpaperRow = null;
        bumpIdle();
    }

    // id, German name, English name. Null colours means the original warm background.
    private static final String[][] WALLS = {
            {"warm", "Warm", "Warm"},
            {"dusk", "Dämmerung", "Dusk"},
            {"forest", "Wald", "Forest"},
            {"sea", "See", "Sea"},
            {"night", "Nacht", "Night"},
            {"ink", "Tinte", "Ink"},
            {"graphite", "Graphit", "Graphite"},
            {"mist", "Nebel", "Mist"},
            {"stone", "Stein", "Stone"},
            {"sand", "Sand", "Sand"},
    };
    private static final int[][] WALL_COLORS = {
            null,
            {0xFF1A1030, 0xFF3A1D3A, 0xFFC46A3A, 0xFF1A0C10},
            {0xFF10241C, 0xFF1A3830, 0xFF234838, 0xFF0C1612},
            {0xFF0C1C3A, 0xFF1A3A5C, 0xFFC47A4A, 0xFF1A120C},
            {0xFF070814, 0xFF12182E, 0xFF05060C},
            {0xFF121316, 0xFF070708},
            {0xFF23262B, 0xFF121316},
            {0xFF2A343C, 0xFF15191D},
            {0xFF322E2B, 0xFF161412},
            {0xFF3C352C, 0xFF1B1713},
    };

    private String wallpaperId() {
        String id = getSharedPreferences(PREFS, 0).getString("wallpaper", "warm");
        for (String[] wall : WALLS) if (wall[0].equals(id)) return id;
        return "custom".equals(id) ? id : "warm";
    }

    private int wallIndex(String id) {
        for (int i = 0; i < WALLS.length; i++) if (WALLS[i][0].equals(id)) return i;
        return -1;
    }

    private String wallName(String id) {
        int i = wallIndex(id);
        if (i < 0) return de("Eigenes Bild", "Your picture");
        return de(WALLS[i][1], WALLS[i][2]);
    }

    private String wallpaperLabel() {
        return de("Hintergrund", "Wallpaper") + " · " + wallName(wallpaperId());
    }

    private void applyWallpaper() {
        getWindow().setBackgroundDrawable(wallpaperDrawable(wallpaperId()));
    }

    private Drawable wallpaperDrawable(String id) {
        if ("custom".equals(id)) {
            Drawable photo = customWallpaper();
            if (photo != null) return photo;
        }
        int i = wallIndex(id);
        if (i <= 0) return background();
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, WALL_COLORS[i]);
        fill.setDither(true);
        return fill;
    }

    private Drawable customWallpaper() {
        String raw = getSharedPreferences(PREFS, 0).getString("wallpaperUri", null);
        if (raw == null) return null;
        InputStream in = null;
        InputStream in2 = null;
        try {
            Uri uri = Uri.parse(raw);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            in = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, bounds);
            int wide = Math.max(1, bounds.outWidth);
            int target = Math.max(1280, getResources().getDisplayMetrics().widthPixels);
            int sample = 1;
            while (wide / sample > target * 2) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            in2 = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(in2, null, opts);
            if (bmp == null) return null;
            BitmapDrawable photo = new BitmapDrawable(getResources(), bmp);
            photo.setGravity(Gravity.FILL);
            GradientDrawable scrim = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {0x48000000, 0x14000000, 0x80000000});
            return new LayerDrawable(new Drawable[] {photo, scrim});
        } catch (Exception ignored) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (in2 != null) in2.close(); } catch (Exception ignored) {}
        }
    }

    private void selectWallpaper(String id) {
        getSharedPreferences(PREFS, 0).edit().putString("wallpaper", id).apply();
        applyWallpaper();
        hideWallpaper();
        if (wallpaperRow != null) wallpaperRow.setText(wallpaperLabel());
    }

    private void pickWallpaper() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, PICK_WALL);
        } catch (Exception ignored) {
            android.widget.Toast.makeText(this, de("Dieser Fernseher hat keine Bildauswahl.",
                    "This TV has no picture picker."), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void showWallpaper() {
        if (wallpaperLayer != null) return;
        FrameLayout layer = new FrameLayout(this);
        layer.setBackgroundColor(0x9E000000);
        layer.setOnClickListener(v -> hideWallpaper());
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFF161311);
        cardBg.setCornerRadius(px(28));
        card.setBackground(cardBg);
        card.setPadding(px(36), px(32), px(36), px(36));
        TextView kicker = text(px(18), 0x8FFFFFFF, regular);
        kicker.setText(de("Hintergrund", "Wallpaper"));
        card.addView(kicker);
        TextView title = text(px(36), Color.WHITE, semibold);
        title.setText("Wallpaper");
        LinearLayout.LayoutParams titleGap = new LinearLayout.LayoutParams(-2, -2);
        titleGap.topMargin = px(4);
        card.addView(title, titleGap);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        String current = wallpaperId();
        View focus = null;
        for (int i = 0; i < WALLS.length; i++) {
            final String id = WALLS[i][0];
            View cell = wallCell(wallName(id), swatch(i), () -> selectWallpaper(id));
            if (id.equals(current)) focus = cell;
            grid.addView(cell);
        }
        View file = wallCell(de("Eigenes Bild", "Your picture"), swatch(-1), this::pickWallpaper);
        if ("custom".equals(current)) focus = file;
        grid.addView(file);
        LinearLayout.LayoutParams gridGap = new LinearLayout.LayoutParams(-2, -2);
        gridGap.topMargin = px(22);
        card.addView(grid, gridGap);

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(px(980), -2);
        cp.gravity = Gravity.CENTER;
        layer.addView(card, cp);
        stage.addView(layer, new FrameLayout.LayoutParams(-1, -1));
        wallpaperLayer = layer;
        (focus != null ? focus : grid.getChildAt(0)).requestFocus();
        bumpIdle();
    }

    private Drawable swatch(int index) {
        GradientDrawable fill = index < 0
                ? new GradientDrawable()
                : new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        index == 0 ? new int[] {0xFF15100F, 0xFF0A0809, 0xFF050405} : WALL_COLORS[index]);
        if (index < 0) fill.setColor(0xFF1A1816);
        fill.setCornerRadius(px(10));
        fill.setDither(true);
        return fill;
    }

    private LinearLayout wallCell(String name, Drawable swatch, Runnable action) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setPadding(px(8), px(8), px(8), px(8));
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = px(210);
        lp.setMargins(px(8), px(8), px(8), px(8));
        cell.setLayoutParams(lp);
        View chip = new View(this);
        chip.setBackground(swatch);
        cell.addView(chip, new LinearLayout.LayoutParams(-1, px(84)));
        TextView label = text(px(18), 0xCFFFFFFF, regular);
        label.setText(name);
        label.setPadding(0, px(8), 0, 0);
        cell.addView(label);
        GradientDrawable hi = new GradientDrawable();
        hi.setCornerRadius(px(12));
        cell.setBackground(hi);
        cell.setOnFocusChangeListener((v, has) -> hi.setColor(has ? 0x33FFFFFF : Color.TRANSPARENT));
        cell.setOnClickListener(v -> action.run());
        return cell;
    }

    private void hideWallpaper() {
        if (wallpaperLayer == null) return;
        stage.removeView(wallpaperLayer);
        wallpaperLayer = null;
        bumpIdle();
    }

    private void armSleep(int minutes) {
        if (sleepTask != null) ui.removeCallbacks(sleepTask);
        sleepTask = null;
        hideSettings();
        if (minutes <= 0) {
            android.widget.Toast.makeText(this, de("Ausschalten aus", "Sleep timer off"),
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        sleepTask = this::showPower;
        ui.postDelayed(sleepTask, minutes * 60L * 1000L);
        android.widget.Toast.makeText(this, de("TV aus in ", "TV off in ") + minutes + " Min",
                android.widget.Toast.LENGTH_SHORT).show();
    }

    private void showPower() {
        if (powerLayer != null) return;
        hideSettings();
        hideAmbient();
        hideNight();
        FrameLayout black = new FrameLayout(this);
        black.setBackgroundColor(Color.BLACK);
        TextView t = text(px(42), Color.WHITE, semibold);
        t.setText(de("Fernseher aus", "TV off"));
        t.setGravity(Gravity.CENTER);
        black.addView(t, new FrameLayout.LayoutParams(-1, -1));
        black.setClickable(true);
        black.setFocusable(true);
        black.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() == KeyEvent.ACTION_DOWN) { hidePower(); bumpIdle(); }
            return true;
        });
        black.setOnClickListener(v -> { hidePower(); bumpIdle(); });
        stage.addView(black, new FrameLayout.LayoutParams(-1, -1));
        powerLayer = black;
        black.requestFocus();
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            java.lang.reflect.Method go = PowerManager.class.getMethod("goToSleep", long.class);
            go.invoke(pm, SystemClock.uptimeMillis());
        } catch (Exception e) {
            android.widget.Toast.makeText(this, de("Zum Ausschalten die TV-Taste drücken.",
                    "Press the TV button on the remote to turn it off."), android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void hidePower() {
        if (powerLayer == null) return;
        stage.removeView(powerLayer);
        powerLayer = null;
    }

    private void showAmbient() {
        if (ambientLayer != null || settingsLayer != null || wallpaperLayer != null
                || nightLayer != null || powerLayer != null) return;
        FrameLayout layer = new FrameLayout(this);
        layer.setBackgroundColor(0xF0050405);
        layer.setClickable(true);
        java.util.Calendar now = java.util.Calendar.getInstance();
        int hour = now.get(java.util.Calendar.HOUR_OF_DAY);
        String hello = hour < 11 ? de("Guten Morgen", "Good morning")
                : hour < 18 ? de("Guten Tag", "Good afternoon") : de("Guten Abend", "Good evening");
        TextView greet = text(px(36), 0xD9FFFFFF, regular);
        greet.setText(hello);
        greet.setGravity(Gravity.CENTER_HORIZONTAL);
        layer.addView(greet, at(0, px(140), -1, -2));
        TextClock date = clock(Strings.datePattern(), Strings.datePattern(), px(32), 0xB3FFFFFF, regular);
        date.setGravity(Gravity.CENTER_HORIZONTAL);
        layer.addView(date, at(0, px(210), -1, -2));
        TextView outside = text(px(30), 0xE6FFFFFF, regular);
        outside.setGravity(Gravity.CENTER_HORIZONTAL);
        outside.setText(weatherView.getText());
        layer.addView(outside, at(0, px(270), -1, -2));
        TextClock time = clock("h:mm", "H:mm", px(210), Color.WHITE, semibold);
        time.setGravity(Gravity.CENTER);
        time.setLetterSpacing(-0.028f);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, -2);
        tp.gravity = Gravity.CENTER;
        layer.addView(time, tp);
        layer.setOnClickListener(v -> { hideAmbient(); considerNight(); });
        layer.setOnKeyListener((v, code, ev) -> {
            if (ev.getAction() != KeyEvent.ACTION_DOWN) return true;
            hideAmbient();
            considerNight();
            return true;
        });
        layer.setFocusable(true);
        stage.addView(layer, new FrameLayout.LayoutParams(-1, -1));
        ambientLayer = layer;
        layer.requestFocus();
    }

    private void hideAmbient() {
        if (ambientLayer == null) return;
        stage.removeView(ambientLayer);
        ambientLayer = null;
        bumpIdle();
    }

    private void considerNight() {
        java.util.Calendar now = java.util.Calendar.getInstance();
        int hour = now.get(java.util.Calendar.HOUR_OF_DAY);
        boolean quiet = hour >= 22 || hour < 6;
        if (quiet && !lateDismissed && ambientLayer == null && powerLayer == null) showNight();
    }

    private void showNight() {
        if (nightLayer != null) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF012100F);
        bg.setCornerRadius(px(RADIUS));
        box.setBackground(bg);
        box.setPadding(px(48), px(40), px(48), px(40));
        TextView q = text(px(32), Color.WHITE, semibold);
        q.setText(de("Gute Nacht?", "Good night?"));
        q.setGravity(Gravity.CENTER);
        box.addView(q);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2);
        rp.topMargin = px(28);
        TextView yes = settingsRow(de("Ausschalten", "Turn off"), () -> {
            lateDismissed = true;
            hideNight();
            showPower();
        });
        TextView no = settingsRow(de("Weiter", "Stay on"), () -> {
            lateDismissed = true;
            hideNight();
            bumpIdle();
        });
        LinearLayout.LayoutParams yp = new LinearLayout.LayoutParams(px(220), -2);
        yp.rightMargin = px(16);
        row.addView(yes, yp);
        row.addView(no, new LinearLayout.LayoutParams(px(220), -2));
        box.addView(row, rp);
        FrameLayout layer = new FrameLayout(this);
        layer.setBackgroundColor(0x99000000);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(px(640), -2);
        bp.gravity = Gravity.CENTER;
        layer.addView(box, bp);
        stage.addView(layer, new FrameLayout.LayoutParams(-1, -1));
        nightLayer = layer;
        yes.requestFocus();
    }

    private void hideNight() {
        if (nightLayer == null) return;
        stage.removeView(nightLayer);
        nightLayer = null;
    }

    private void refreshWeather() {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String url = "https://api.open-meteo.com/v1/forecast?latitude=52.52&longitude=13.41"
                        + "&current=temperature_2m,weather_code&timezone=auto";
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder body = new StringBuilder();
                for (String line; (line = reader.readLine()) != null; ) body.append(line);
                reader.close();
                JSONObject current = new JSONObject(body.toString()).getJSONObject("current");
                int temp = (int) Math.round(current.getDouble("temperature_2m"));
                String sky = sky(current.getInt("weather_code"));
                String line = temp + "°  " + sky;
                runOnUiThread(() -> weatherView.setText(line));
            } catch (Exception ignored) {
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private String sky(int code) {
        if (code == 0) return de("klar", "clear");
        if (code <= 3) return de("bewölkt", "cloudy");
        if (code <= 48) return de("neblig", "fog");
        if (code <= 67) return de("Regen", "rain");
        if (code <= 77) return de("Schnee", "snow");
        if (code <= 82) return de("Schauer", "showers");
        return de("Gewitter", "storm");
    }
}
