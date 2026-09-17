package jp.bunkaich.sukashimotion;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** A resizable, scrollable catalog; app launches remain owned by HomeActivity. */
final class AppDrawer extends Dialog {
    final EditText search;
    final GridView grid;
    private final LinearLayout panel;
    private final TextView count, empty;
    private final CatalogAdapter adapter = new CatalogAdapter();
    private List<AppCatalog.App> apps;
    private final List<AppCatalog.App> filtered = new ArrayList<>();
    private boolean loading;

    AppDrawer(Context context, int slot, List<AppCatalog.App> apps, boolean loading,
              Consumer<AppCatalog.App> select, Consumer<AppCatalog.App> pin) {
        super(context);
        this.apps = List.copyOf(apps); this.loading = loading;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        panel = new LinearLayout(context); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setFocusableInTouchMode(true); HomeTheme.backdrop(panel);
        panel.setPadding(dp(20), dp(20), dp(20), dp(12));

        LinearLayout header = new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout heading = new LinearLayout(context); heading.setOrientation(LinearLayout.VERTICAL);
        TextView title = GlassStyle.text(context, context.getString(slot < 0 ? R.string.home_drawer_title : R.string.home_choose), 26, HomeTheme.ink(context));
        title.setAccessibilityHeading(true); heading.addView(title);
        count = GlassStyle.text(context, "", 13, HomeTheme.secondary(context)); count.setPadding(0, dp(7), 0, 0); heading.addView(count);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = GlassStyle.button(context, context.getString(R.string.close), false, this::dismiss);
        close.setMinWidth(0); close.setMinimumWidth(0); close.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams closeSize = new LinearLayout.LayoutParams(-2, dp(48)); closeSize.setMarginStart(dp(12)); header.addView(close, closeSize);
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));

        search = new EditText(context); search.setSingleLine(); search.setHint(R.string.home_search);
        search.setTextColor(HomeTheme.ink(context)); search.setHintTextColor(HomeTheme.secondary(context)); search.setTextSize(16);
        search.setPadding(dp(18), dp(12), dp(18), dp(12)); HomeTheme.panel(search, 24);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT); search.setImeOptions(EditorInfo.IME_ACTION_DONE);
        LinearLayout.LayoutParams searchSize = new LinearLayout.LayoutParams(-1, dp(52)); searchSize.topMargin = dp(20); panel.addView(search, searchSize);
        TextView hint = GlassStyle.text(context, slot < 0 ? context.getString(R.string.drawer_browse_hint)
                : context.getString(R.string.drawer_choose_hint, slot + 1), 12, HomeTheme.secondary(context));
        hint.setPadding(dp(4), dp(10), dp(4), dp(12)); panel.addView(hint);

        FrameLayout body = new FrameLayout(context);
        grid = new GridView(context); grid.setNumColumns(GridView.AUTO_FIT); grid.setColumnWidth(dp(70));
        grid.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
            int columns=Math.max(3,Math.min(8,(right-left+dp(8))/dp(78)));
            if(grid.getNumColumns()!=columns)grid.setNumColumns(columns);
        });
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH); grid.setHorizontalSpacing(dp(8)); grid.setVerticalSpacing(dp(12));
        grid.setPadding(0, dp(4), 0, dp(16)); grid.setClipToPadding(false); grid.setVerticalScrollBarEnabled(true);
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT)); grid.setAdapter(adapter);
        body.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        empty = GlassStyle.text(context, "", 16, HomeTheme.secondary(context)); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(24), dp(24), dp(24), dp(24));
        body.addView(empty, new FrameLayout.LayoutParams(-1, -1)); grid.setEmptyView(empty);
        panel.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        panel.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            panel.setPadding(dp(20) + safe.left, dp(20) + safe.top, dp(20) + safe.right, dp(12) + safe.bottom);
            return insets;
        });
        grid.setOnItemClickListener((parent, view, position, id) -> { AppCatalog.App app = filtered.get(position); dismiss(); select.accept(app); });
        if (slot < 0) grid.setOnItemLongClickListener((parent, view, position, id) -> { pin.accept(filtered.get(position)); return true; });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void afterTextChanged(Editable text) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { filter(); grid.setSelection(0); }
        });
        search.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            context.getSystemService(InputMethodManager.class).hideSoftInputFromWindow(search.getWindowToken(), 0);
            panel.requestFocus(); return true;
        });
        setContentView(panel); panel.requestFocus(); filter();
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setDecorFitsSystemWindows(false); window.setStatusBarColor(Color.TRANSPARENT); window.setNavigationBarColor(Color.TRANSPARENT);
        window.setNavigationBarContrastEnforced(false); window.setDimAmount(0);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
    }
    @Override protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.getDecorView().requestApplyInsets();
            window.getInsetsController().setSystemBarsAppearance(HomeTheme.palette(getContext())==1?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
    }
    void updateApps(List<AppCatalog.App> loaded) {
        apps = List.copyOf(loaded); loading = false; filter();
    }
    private void filter() {
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        filtered.clear();
        for (AppCatalog.App app : apps) if (query.isEmpty() || app.label().toLowerCase(Locale.ROOT).contains(query)
                || app.component().getPackageName().toLowerCase(Locale.ROOT).contains(query)) filtered.add(app);
        count.setText(loading ? getContext().getString(R.string.drawer_loading) : getContext().getString(R.string.drawer_count, filtered.size()));
        empty.setText(loading ? R.string.drawer_loading : query.isEmpty() ? R.string.drawer_empty : R.string.drawer_no_results);
        adapter.notifyDataSetChanged();
    }
    private int dp(float value) { return GlassStyle.dp(getContext(), value); }
    private final class CatalogAdapter extends BaseAdapter {
        public int getCount() { return filtered.size(); }
        public AppCatalog.App getItem(int position) { return filtered.get(position); }
        public long getItemId(int position) { return getItem(position).component().flattenToString().hashCode(); }
        public View getView(int position, View recycled, ViewGroup parent) {
            LinearLayout tile;
            if (recycled instanceof LinearLayout row) tile = row;
            else {
                tile = new LinearLayout(getContext()); tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                tile.setPadding(dp(4), dp(8), dp(4), dp(6));
                tile.setBackground(new RippleDrawable(ColorStateList.valueOf(0x220866ce), GlassStyle.solid(0x35ffffff, dp(22)), null));
                tile.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                ImageView icon = new ImageView(getContext()); icon.setScaleType(ImageView.ScaleType.FIT_CENTER); icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                tile.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
                TextView label = GlassStyle.text(getContext(), "", 13, HomeTheme.ink(getContext())); label.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                label.setLines(2); label.setEllipsize(TextUtils.TruncateAt.END); label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                LinearLayout.LayoutParams labelSize = new LinearLayout.LayoutParams(-1, -2); labelSize.topMargin = dp(9); tile.addView(label, labelSize);
                tile.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
            }
            AppCatalog.App app = getItem(position);
            ((ImageView)tile.getChildAt(0)).setImageDrawable(app.icon()); ((TextView)tile.getChildAt(1)).setText(app.label());
            tile.setContentDescription(app.label()); return tile;
        }
    }
}
