package jp.bunkaich.sukashimotion;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** A folder uses the same app-launch callback as ordinary home icons. */
final class FolderPanel extends Dialog {
    final GridView grid;
    final TextView title;
    private final TextView count;
    private List<AppCatalog.App> apps = List.of();
    private final FolderAdapter adapter = new FolderAdapter();
    FolderPanel(Context context, HomeFolders.Folder folder, List<AppCatalog.App> catalog,
                Consumer<AppCatalog.App> launch, Runnable rename, Runnable organize, Runnable dissolve) {
        super(context); requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(context); panel.setOrientation(LinearLayout.VERTICAL); HomeTheme.backdrop(panel);
        panel.setPadding(dp(24), dp(24), dp(24), dp(20));
        LinearLayout heading = new LinearLayout(context); heading.setGravity(Gravity.CENTER_VERTICAL);
        title = GlassStyle.text(context, "", 28, HomeTheme.ink(context)); title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END);
        title.setAccessibilityHeading(true); title.setOnClickListener(v -> rename.run());
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = GlassStyle.button(context, context.getString(R.string.close), false, this::dismiss);
        heading.addView(close, new LinearLayout.LayoutParams(-2, dp(48))); panel.addView(heading);
        count = GlassStyle.text(context, "", 13, HomeTheme.secondary(context)); count.setPadding(0, dp(10), 0, dp(22)); panel.addView(count);
        grid = new GridView(context); grid.setNumColumns(3); grid.setColumnWidth(dp(86));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH); grid.setHorizontalSpacing(dp(12)); grid.setVerticalSpacing(dp(22));
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT)); grid.setClipToPadding(false); grid.setPadding(0, dp(8), 0, dp(20));
        grid.setAdapter(adapter); grid.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob) -> {
            int columns = Math.max(3, Math.min(6, (r-l+dp(12))/dp(102)));
            if (grid.getNumColumns()!=columns) grid.setNumColumns(columns);
        });
        grid.setOnItemClickListener((parent, view, position, id) -> { AppCatalog.App app = apps.get(position); dismiss(); launch.accept(app); });
        panel.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout controls = new LinearLayout(context); controls.setGravity(Gravity.CENTER);
        Runnable[] callbacks = {rename, organize, dissolve}; int[] labels = {R.string.folder_rename, R.string.folder_organize, R.string.folder_dissolve};
        for (int index = 0; index < labels.length; index++) {
            Button button = GlassStyle.button(context, context.getString(labels[index]), false, callbacks[index]);
            button.setMinWidth(0); button.setMinimumWidth(0); button.setTextSize(13); button.setPadding(dp(6), dp(6), dp(6), dp(6));
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, dp(52), 1); size.setMargins(dp(3),0,dp(3),0); controls.addView(button,size);
        }
        panel.addView(controls, new LinearLayout.LayoutParams(-1,-2));
        panel.setOnApplyWindowInsetsListener((v,insets) -> {
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            panel.setPadding(dp(24)+safe.left,dp(24)+safe.top,dp(24)+safe.right,dp(20)+safe.bottom); return insets;
        });
        setContentView(panel); update(folder, catalog);
    }
    void update(HomeFolders.Folder folder, List<AppCatalog.App> catalog) {
        apps = folder.apps(catalog); title.setText(folder.name()); title.setContentDescription(getContext().getString(R.string.folder_rename_description, folder.name()));
        count.setText(getContext().getString(R.string.folder_count, apps.size())); adapter.notifyDataSetChanged();
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); Window window = getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); window.setDecorFitsSystemWindows(false);
        window.setStatusBarColor(Color.TRANSPARENT); window.setNavigationBarColor(Color.TRANSPARENT); window.setNavigationBarContrastEnforced(false); window.setDimAmount(0);
        window.getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
    }
    @Override protected void onStart() {
        super.onStart(); Window window = getWindow();
        if (window == null) return;
        window.setLayout(-1,-1); window.getDecorView().requestApplyInsets();
        int light=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        window.getInsetsController().setSystemBarsAppearance(HomeTheme.palette(getContext())==1?0:light,light);
    }
    private int dp(float value) { return GlassStyle.dp(getContext(),value); }
    private final class FolderAdapter extends BaseAdapter {
        public int getCount(){return apps.size();}
        public AppCatalog.App getItem(int position){return apps.get(position);}
        public long getItemId(int position){return getItem(position).component().flattenToString().hashCode();}
        public View getView(int position,View recycled,ViewGroup parent){
            LinearLayout tile;
            if(recycled instanceof LinearLayout existing)tile=existing;
            else{
                tile=new LinearLayout(getContext());tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER_HORIZONTAL);tile.setPadding(dp(4),dp(8),dp(4),dp(8));
                ImageView icon=new ImageView(getContext());icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                tile.addView(icon,new LinearLayout.LayoutParams(dp(56),dp(56)));
                TextView label=GlassStyle.text(getContext(),"",13,HomeTheme.ink(getContext()));label.setGravity(Gravity.CENTER);label.setLines(2);label.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.topMargin=dp(8);tile.addView(label,size);
                tile.setLayoutParams(new AbsListView.LayoutParams(-1,-2));
            }
            AppCatalog.App app=getItem(position);((ImageView)tile.getChildAt(0)).setImageDrawable(app.icon());((TextView)tile.getChildAt(1)).setText(app.label());tile.setContentDescription(app.label());return tile;
        }
    }
}
