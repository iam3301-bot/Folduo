package jp.bunkaich.sukashimotion;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Interactive home; MotionService alone owns folding and display control. */
public final class HomeActivity extends Activity implements HomeScene.Actions {
    static final float FRAME_RATE = 60f;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private HomeScene scene;
    private List<AppCatalog.App> apps = List.of();
    private AppDrawer drawer;
    private AlertDialog pinPicker;
    private boolean started, launching, appsLoaded;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        GlassStyle.configureWindow(this);
        prefs = getSharedPreferences("launcher", MODE_PRIVATE);
        BridgeConnection.init(this);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        FrameLayout root = new FrameLayout(this);
        HomeTheme.backdrop(root);
        scene = new HomeScene(this, this);
        root.addView(scene, new FrameLayout.LayoutParams(-1, -1));
        Button settings = GlassStyle.button(this, getString(R.string.nav_settings), false, this::settings);
        settings.setAllCaps(false);
        settings.setContentDescription(getString(R.string.nav_settings));
        settings.setTextColor(GlassStyle.INK);
        settings.setOnClickListener(v -> settings());
        FrameLayout.LayoutParams button = new FrameLayout.LayoutParams(dp(92), dp(48), Gravity.BOTTOM | Gravity.END);
        button.setMargins(dp(16), 0, dp(16), dp(16));
        root.addView(settings, button);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.navigationBars() | WindowInsets.Type.displayCutout());
            root.setPadding(safe.left, 0, safe.right, safe.bottom);
            return insets;
        });
        setContentView(root);
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> updatePanel());
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
            if (drawer != null) drawer.dismiss();
        });
    }

    @Override protected void onStart() {
        super.onStart(); started = true;
        refreshApps(); main.post(clock);
    }
    @Override protected void onResume() {
        super.onResume();
        getWindow().getInsetsController().hide(WindowInsets.Type.statusBars());
        getWindow().getInsetsController().setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        if (MotionSettings.enabled(this)) BridgeConnection.connect(this);
        updatePanel();applyHomeBars();
    }
    @Override protected void onStop() { started = false; main.removeCallbacks(clock); super.onStop(); }
    @Override protected void onDestroy() { closeDrawers(); worker.shutdownNow(); super.onDestroy(); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); closeDrawers(); }
    @Override public void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); updatePanel(); }

    private void updatePanel() {
        Display display = getDisplay();
        if (display == null) return;
        android.graphics.Rect bounds=getWindowManager().getCurrentWindowMetrics().getBounds();
        float ratio=Math.min(bounds.width(),bounds.height())/(float)Math.max(1,Math.max(bounds.width(),bounds.height()));
        scene.setFold(ratio > .68f && !isInMultiWindowMode(), 0, false);
    }
    private void refreshApps() {
        worker.execute(() -> {
            List<AppCatalog.App> loaded = AppCatalog.load(this);
            main.post(() -> { if (!isDestroyed()) catalogLoaded(loaded); });
        });
    }
    private void catalogLoaded(List<AppCatalog.App> loaded) {
        apps = loaded; appsLoaded = true;
        scene.setCatalog(apps);scene.updateApps(AppCatalog.favorites(apps, prefs));
        if (drawer != null && drawer.isShowing()) drawer.updateApps(apps);
    }
    private final Runnable clock = new Runnable() {
        public void run() {
            if (!started) return;
            Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int level = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery == null ? 100 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            scene.tick(level < 0 ? -1 : Math.round(level * 100f / Math.max(1, scale)), prefs.getString("note", ""));
            main.postDelayed(this, 1000);
        }
    };

    @Override public void launch(AppCatalog.App app) { launchComponent(app.component()); }
    private void launchComponent(ComponentName component) {
        if (launching) return;
        launching = true;
        int display = getDisplay().getDisplayId();
        IShellBridge bridge = BridgeConnection.bridge;
        worker.execute(() -> {
            boolean handled = false;
            String failure = null;
            try {
                if (display == 1 && MotionSettings.enabled(this)) {
                    if (bridge == null) throw new IllegalStateException(getString(R.string.bridge_missing));
                    Bundle result = bridge.launchApp(display, component.flattenToString());
                    if (!result.getBoolean("ok")) throw new IllegalStateException(result.getString("error"));
                    handled = result.getBoolean("handled");
                }
            } catch (Exception e) { failure = UiText.error(e).resolve(this); }
            boolean routed = handled;
            String error = failure;
            main.post(() -> {
                launching = false;
                if (isDestroyed()) return;
                if (error != null) { showLaunchError(error); return; }
                if (routed || !started) return;
                try {
                    startActivity(TaskDisplayRouter.launchIntent(component));
                } catch (ActivityNotFoundException | SecurityException e) { showLaunchError(e.getLocalizedMessage()); }
            });
        });
    }
    private void showLaunchError(String message) { Toast.makeText(this, getString(R.string.home_launch_failed, message), Toast.LENGTH_LONG).show(); }
    @Override public void choose(int slot) { showApps(slot); }
    @Override public void drawer() { showApps(-1); }
    @Override public void pin(AppCatalog.App app) { showPinPositions(app); }
    @Override public void appearance() { HomeTheme.choose(this,()->{scene.applyTheme();HomeTheme.backdrop((View)scene.getParent());applyHomeBars();}); }
    private void applyHomeBars(){
        int light=WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS|WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
        getWindow().getDecorView().post(()->getWindow().getInsetsController().setSystemBarsAppearance(HomeTheme.palette(this)==1?0:light,light));
    }
    @Override public void settings() { launchComponent(new ComponentName(this, MainActivity.class)); }
    private void showApps(int slot) {
        if (drawer != null && drawer.isShowing()) return;
        AppDrawer opened = new AppDrawer(this, slot, apps, !appsLoaded,
                app -> { if (slot < 0) launch(app); else pin(app, slot); }, this::showPinPositions);
        drawer = opened;
        opened.setOnDismissListener(dialog -> { if (drawer == opened) drawer = null; });
        opened.show();
    }
    private void showPinPositions(AppCatalog.App app) {
        if (pinPicker != null && pinPicker.isShowing()) return;
        List<AppCatalog.App> favorites = AppCatalog.favorites(apps, prefs);
        String[] positions = new String[16];
        for (int slot = 0; slot < positions.length; slot++) {
            AppCatalog.App current = favorites.get(slot);
            positions[slot] = current == null ? getString(R.string.drawer_slot_empty, slot + 1)
                    : getString(R.string.drawer_slot_occupied, slot + 1, current.label());
        }
        pinPicker = new AlertDialog.Builder(this).setTitle(getString(R.string.drawer_pin_title, app.label()))
                .setItems(positions, (dialog, slot) -> { pin(app, slot); if (drawer != null) drawer.dismiss(); })
                .setNegativeButton(R.string.close, null).create();
        pinPicker.setOnDismissListener(dialog -> pinPicker = null);
        GlassStyle.dialog(pinPicker);
    }
    private void pin(AppCatalog.App app, int slot) {
        AppCatalog.pin(prefs,app.component(),slot);
        scene.updateApps(AppCatalog.favorites(apps, prefs));
        Toast.makeText(this, getString(R.string.drawer_pinned, app.label(), slot + 1), Toast.LENGTH_SHORT).show();
    }
    private void closeDrawers() {
        if (pinPicker != null) pinPicker.dismiss();
        if (drawer != null) drawer.dismiss();
    }
    @Override public void note() {
        EditText entry = new EditText(this); entry.setText(prefs.getString("note", "")); entry.setHint(R.string.home_note_hint); entry.setMinLines(4);
        GlassStyle.dialog(new AlertDialog.Builder(this).setTitle(R.string.home_note_title).setView(entry).setPositiveButton(R.string.home_save, (d,w) -> {
            prefs.edit().putString("note", entry.getText().toString()).apply(); main.removeCallbacks(clock); main.post(clock);
        }).setNegativeButton(R.string.close, null).create());
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
