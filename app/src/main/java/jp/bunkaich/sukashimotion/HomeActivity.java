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
    private AlertDialog folderEditor;
    private FolderPanel folderPanel;
    private int openFolderSlot=-1;
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
            if (folderPanel != null) folderPanel.dismiss();
            else if (drawer != null) drawer.dismiss();
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
        if (MotionSettings.enabled(this)) {
            BridgeConnection.connect(this);
            // A package update may reopen Home before its restore broadcast runs.
            // Resume the user's enabled monitor here as the settings activity does.
            if (!MotionService.running && android.provider.Settings.canDrawOverlays(this))
                startForegroundService(new Intent(this, MotionService.class).setAction("restore"));
        }
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
        scene.setCatalog(apps);refreshHome();
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
    @Override public void edit(int slot) {
        HomeFolders.Folder folder=HomeFolders.read(prefs).get(slot);
        AppCatalog.App app=AppCatalog.favorites(apps,prefs).get(slot);
        if(folder==null&&app==null){choose(slot);return;}
        if(folder!=null){
            showFolderEditor(new AlertDialog.Builder(this).setTitle(folder.name()).setItems(new String[]{getString(R.string.folder_open),getString(R.string.folder_rename),getString(R.string.folder_organize),getString(R.string.folder_dissolve)},(d,which)->{
                if(which==0)folder(slot);else if(which==1)renameFolder(slot);else if(which==2)selectFolderMembers(slot,null);else dissolveFolder(slot);
            }).setNegativeButton(R.string.close,null).create());
        }else{
            showFolderEditor(new AlertDialog.Builder(this).setTitle(app.label()).setItems(new String[]{getString(R.string.folder_create),getString(R.string.folder_replace_app),getString(R.string.folder_remove_shortcut)},(d,which)->{
                if(which==0)selectFolderMembers(slot,app);else if(which==1)choose(slot);else{prefs.edit().putString("slot_"+slot,"").apply();refreshHome();}
            }).setNegativeButton(R.string.close,null).create());
        }
    }
    @Override public void folder(int slot) {
        HomeFolders.Folder folder=HomeFolders.read(prefs).get(slot);if(folder==null)return;
        if(folderPanel!=null)folderPanel.dismiss();
        FolderPanel opened=new FolderPanel(this,folder,apps,this::launch,()->renameFolder(slot),()->selectFolderMembers(slot,null),()->dissolveFolder(slot));
        folderPanel=opened;openFolderSlot=slot;
        opened.setOnDismissListener(d->{if(folderPanel==opened){folderPanel=null;openFolderSlot=-1;}});opened.show();
    }
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
        Map<Integer,HomeFolders.Folder> folders=HomeFolders.read(prefs);
        String[] positions = new String[16];
        for (int slot = 0; slot < positions.length; slot++) {
            AppCatalog.App current = favorites.get(slot);
            positions[slot] = folders.containsKey(slot)?getString(R.string.folder_slot,slot+1,folders.get(slot).name()):current == null ? getString(R.string.drawer_slot_empty, slot + 1)
                    : getString(R.string.drawer_slot_occupied, slot + 1, current.label());
        }
        pinPicker = new AlertDialog.Builder(this).setTitle(getString(R.string.drawer_pin_title, app.label()))
                .setItems(positions, (dialog, slot) -> { pin(app, slot); if (drawer != null) drawer.dismiss(); })
                .setNegativeButton(R.string.close, null).create();
        pinPicker.setOnDismissListener(dialog -> pinPicker = null);
        GlassStyle.dialog(pinPicker);
    }
    private void pin(AppCatalog.App app, int slot) {
        HomeFolders.Folder folder=HomeFolders.read(prefs).get(slot);
        AppCatalog.pin(prefs,app.component(),slot);
        refreshHome();
        Toast.makeText(this, folder==null?getString(R.string.drawer_pinned, app.label(), slot + 1):getString(R.string.folder_added,app.label(),folder.name()), Toast.LENGTH_SHORT).show();
    }
    private void refreshHome(){
        List<AppCatalog.App> favorites=AppCatalog.favorites(apps,prefs);
        Map<Integer,HomeFolders.Folder> folders=HomeFolders.read(prefs);
        scene.updateFolders(folders);scene.updateApps(favorites);
        if(folderPanel!=null){HomeFolders.Folder current=folders.get(openFolderSlot);if(current==null)folderPanel.dismiss();else folderPanel.update(current,apps);}
    }
    private void showFolderEditor(AlertDialog dialog){
        if(folderEditor!=null)folderEditor.dismiss();folderEditor=dialog;
        dialog.setOnDismissListener(d->{if(folderEditor==dialog)folderEditor=null;});GlassStyle.dialog(dialog);
    }
    private void renameFolder(int slot){
        HomeFolders.Folder folder=HomeFolders.read(prefs).get(slot);if(folder==null)return;
        EditText name=new EditText(this);name.setSingleLine();name.setText(folder.name());name.setSelectAllOnFocus(true);name.setHint(R.string.folder_name_hint);
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        showFolderEditor(new AlertDialog.Builder(this).setTitle(R.string.folder_rename).setView(name).setPositiveButton(R.string.home_save,(d,w)->{HomeFolders.rename(prefs,slot,name.getText().toString());refreshHome();}).setNegativeButton(R.string.close,null).create());
    }
    private void selectFolderMembers(int slot,AppCatalog.App seed){
        if(!appsLoaded){Toast.makeText(this,R.string.drawer_loading,Toast.LENGTH_SHORT).show();return;}
        HomeFolders.Folder existing=HomeFolders.read(prefs).get(slot);
        if(seed==null&&existing==null)return;
        List<AppCatalog.App> available=List.copyOf(apps);
        LinkedHashSet<String> selected=new LinkedHashSet<>();
        if(existing!=null)selected.addAll(existing.components());else selected.add(seed.component().flattenToString());
        Set<String> installed=new HashSet<>();for(AppCatalog.App app:available)installed.add(app.component().flattenToString());
        // Keep temporarily unavailable members in storage, while only installed apps appear in this selector.
        List<String> unavailable=new ArrayList<>(selected);unavailable.removeAll(installed);selected.retainAll(installed);
        String[] labels=new String[available.size()];boolean[] checked=new boolean[available.size()];
        for(int i=0;i<available.size();i++){labels[i]=available.get(i).label();checked[i]=selected.contains(available.get(i).component().flattenToString());}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(existing==null?R.string.folder_create_title:R.string.folder_organize_title)
                .setMultiChoiceItems(labels,checked,(d,index,isChecked)->{String component=available.get(index).component().flattenToString();if(isChecked)selected.add(component);else selected.remove(component);((AlertDialog)d).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(selected.size()>=(existing==null?2:1));})
                .setPositiveButton(existing==null?R.string.folder_create:R.string.home_save,(d,w)->{
                    List<String> members=new ArrayList<>();
                    if(existing!=null)for(String component:existing.components())if(selected.contains(component)||unavailable.contains(component))members.add(component);
                    for(String component:selected)if(!members.contains(component))members.add(component);
                    if(existing==null)HomeFolders.create(prefs,slot,getString(R.string.folder_default_name),members);else HomeFolders.update(prefs,slot,members);
                    refreshHome();if(existing==null)folder(slot);
                }).setNegativeButton(R.string.close,null).create();
        showFolderEditor(dialog);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(selected.size()>=(existing==null?2:1));
    }
    private void dissolveFolder(int slot){
        if(!HomeFolders.read(prefs).containsKey(slot))return;
        HomeFolders.dissolve(prefs,slot);refreshHome();Toast.makeText(this,R.string.folder_dissolved,Toast.LENGTH_LONG).show();
    }
    private void closeDrawers() {
        if (folderEditor != null) folderEditor.dismiss();
        if (folderPanel != null) folderPanel.dismiss();
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
