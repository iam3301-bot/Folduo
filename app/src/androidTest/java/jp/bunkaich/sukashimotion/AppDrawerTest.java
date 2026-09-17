package jp.bunkaich.sukashimotion;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Exercises the real dialog, its recycled grid, and tap/long-press dispatch. */
public class AppDrawerTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final AtomicInteger selections = new AtomicInteger(), pins = new AtomicInteger();
    private final AtomicReference<AppCatalog.App> selected = new AtomicReference<>(), pinned = new AtomicReference<>();
    private Activity activity;
    private AppDrawer drawer;

    @Before public void start() {
        activity = instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @After public void stop() {
        ui(() -> {
            if (drawer != null) drawer.dismiss();
            if (activity != null) activity.finish();
        });
    }

    @Test public void openingShowsCatalogIconsWithoutFocusingSearchOrOpeningKeyboard() {
        List<AppCatalog.App> apps = catalog(80);
        open(-1, apps, false);
        await("The visible catalog must be laid out", () -> drawer.grid.getChildCount() > 0);
        ui(() -> {
            assertEquals("Opening must not require a query", "", drawer.search.getText().toString());
            assertEquals("All apps are available immediately", apps.size(), drawer.grid.getAdapter().getCount());
            assertFalse("Search must not take focus on opening", drawer.search.hasFocus());
            WindowInsets insets = drawer.getWindow().getDecorView().getRootWindowInsets();
            assertNotNull("The dialog has received window insets", insets);
            assertFalse("The catalog must not be covered by an automatic keyboard", insets.isVisible(WindowInsets.Type.ime()));
            assertEquals(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN,
                    drawer.getWindow().getAttributes().softInputMode & WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE);
            for (int child = 0; child < drawer.grid.getChildCount(); child++) {
                int position = drawer.grid.getFirstVisiblePosition() + child;
                ViewGroup tile = (ViewGroup) drawer.grid.getChildAt(child);
                assertSame(apps.get(position).icon(), ((ImageView) tile.getChildAt(0)).getDrawable());
                assertEquals(apps.get(position).label(), ((TextView) tile.getChildAt(1)).getText().toString());
            }
        });
    }

    @Test public void asynchronousLoadRefreshesTheOpenGridAndPreservesTheQuery() {
        open(-1, List.of(), true);
        AppCatalog.App first = app("计算器", "calculator.one"), second = app("科学计算", "calculator.two");
        ui(() -> {
            drawer.search.setText("计算");
            assertEquals(0, drawer.grid.getAdapter().getCount());
            drawer.updateApps(List.of(first, app("相机", "camera"), second));
            assertTrue("Completing the load must not close the catalog", drawer.isShowing());
            assertEquals("计算", drawer.search.getText().toString());
            assertEquals(2, drawer.grid.getAdapter().getCount());
            assertSame(first, drawer.grid.getAdapter().getItem(0));
            assertSame(second, drawer.grid.getAdapter().getItem(1));
        });
        await("Loaded matches must become visible without reopening", () -> drawer.grid.getChildCount() == 2);
        ui(() -> {
            assertEquals("计算器", drawer.grid.getChildAt(0).getContentDescription());
            assertEquals("科学计算", drawer.grid.getChildAt(1).getContentDescription());
            drawer.search.setText("");
            assertEquals("Clearing the optional query restores the full catalog", 3, drawer.grid.getAdapter().getCount());
        });
    }

    @Test public void itemTapSelectsOnceAndDismissesTheCatalog() {
        List<AppCatalog.App> apps = catalog(12);
        open(-1, apps, false);
        await("The first tile must be visible", () -> drawer.grid.getChildCount() > 0);
        touchItem(0, false);
        await("A tile tap selects its app", () -> selections.get() == 1);
        ui(() -> assertFalse(drawer.isShowing()));
        assertSame(apps.get(0), selected.get());
        assertEquals(0, pins.get());
        instrumentation.waitForIdleSync();
        assertEquals("One tap must never launch twice", 1, selections.get());
    }

    @Test public void longPressPinsOnceWithoutLaunchingOnRelease() {
        List<AppCatalog.App> apps = catalog(12);
        open(-1, apps, false);
        await("The first tile must be visible", () -> drawer.grid.getChildCount() > 0);
        touchItem(0, true);
        assertEquals(1, pins.get());
        assertSame(apps.get(0), pinned.get());
        assertEquals("Releasing a long press must not also launch", 0, selections.get());
        ui(() -> assertTrue("Pin selection leaves the drawer available", drawer.isShowing()));
        touchItem(0, false);
        await("A subsequent ordinary tap still works", () -> selections.get() == 1);
        assertEquals(1, pins.get());
        assertEquals(1, selections.get());
        assertSame(apps.get(0), selected.get());
    }

    @Test public void largeCatalogCanScrollToAndLaunchItsLastAppWithoutSearching() {
        List<AppCatalog.App> apps = catalog(240);
        open(-1, apps, false);
        await("The catalog must fill its viewport", () -> drawer.grid.getChildCount() > 0);
        ui(() -> assertTrue("A large catalog must scroll", drawer.grid.canScrollVertically(1)));
        for (int scroll = 0; scroll < 100 && onUi(() -> drawer.grid.canScrollVertically(1)); scroll++) {
            ui(() -> drawer.grid.scrollListBy(Math.max(1, drawer.grid.getHeight() * 3 / 4)));
            instrumentation.waitForIdleSync();
        }
        ui(() -> {
            assertFalse("Scrolling reaches the end rather than trapping later apps", drawer.grid.canScrollVertically(1));
            assertEquals(apps.size() - 1, drawer.grid.getLastVisiblePosition());
            assertEquals("", drawer.search.getText().toString());
            View last = drawer.grid.getChildAt(apps.size() - 1 - drawer.grid.getFirstVisiblePosition());
            assertNotNull(last);
            Rect visible = new Rect();
            assertTrue("The last app is onscreen and touchable", last.getLocalVisibleRect(visible));
            assertTrue(visible.height() > last.getHeight() / 2);
            assertEquals(apps.get(apps.size() - 1).label(), last.getContentDescription());
        });
        touchItem(apps.size() - 1, false);
        await("The last tile selects the last app after recycling", () -> selections.get() == 1);
        assertSame(apps.get(apps.size() - 1), selected.get());
        assertEquals(0, pins.get());
    }

    @Test public void choosingAFavoriteUsesSelectionWithoutOpeningAnotherPinPicker() {
        List<AppCatalog.App> apps = catalog(12);
        open(3, apps, false);
        await("The favorite picker must be laid out", () -> drawer.grid.getChildCount() > 0);
        ui(() -> assertNull("A slot is already chosen, so long press must not ask for another", drawer.grid.getOnItemLongClickListener()));
        touchItem(0, false);
        await("Picking a favorite returns the chosen app", () -> selections.get() == 1);
        assertSame(apps.get(0), selected.get());
        assertEquals(0, pins.get());
    }

    private void open(int slot, List<AppCatalog.App> apps, boolean loading) {
        ui(() -> {
            drawer = new AppDrawer(activity, slot, apps, loading,
                    app -> { selected.set(app); selections.incrementAndGet(); },
                    app -> { pinned.set(app); pins.incrementAndGet(); });
            drawer.show();
        });
        instrumentation.waitForIdleSync();
    }

    private void touchItem(int position, boolean hold) {
        float[] point = new float[2];
        ui(() -> {
            View tile = drawer.grid.getChildAt(position - drawer.grid.getFirstVisiblePosition());
            assertNotNull("The requested tile is visible", tile);
            point[0] = tile.getLeft() + tile.getWidth() / 2f;
            point[1] = tile.getTop() + tile.getHeight() / 2f;
        });
        long down = SystemClock.uptimeMillis();
        motion(MotionEvent.ACTION_DOWN, point, down);
        SystemClock.sleep(hold ? ViewConfiguration.getLongPressTimeout() + 150L : 40);
        motion(MotionEvent.ACTION_UP, point, down);
        SystemClock.sleep(ViewConfiguration.getPressedStateDuration() + 30L);
        instrumentation.waitForIdleSync();
    }

    private void motion(int action, float[] point, long down) {
        ui(() -> {
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point[0], point[1], 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try { drawer.grid.dispatchTouchEvent(event); } finally { event.recycle(); }
        });
    }

    private void ui(Runnable action) { instrumentation.runOnMainSync(action); }

    private boolean onUi(BooleanSupplier condition) {
        boolean[] result = new boolean[1];
        ui(() -> result[0] = condition.getAsBoolean());
        return result[0];
    }

    private void await(String message, BooleanSupplier condition) {
        long deadline = SystemClock.uptimeMillis() + 3000;
        do {
            if (onUi(condition)) return;
            SystemClock.sleep(20);
        } while (SystemClock.uptimeMillis() < deadline);
        fail(message);
    }

    private static List<AppCatalog.App> catalog(int count) {
        List<AppCatalog.App> apps = new ArrayList<>();
        for (int index = 0; index < count; index++) apps.add(app("应用 " + index, "catalog.app" + index));
        return apps;
    }

    private static AppCatalog.App app(String label, String packageName) {
        return new AppCatalog.App(label, new ComponentName(packageName, packageName + ".Main"), new ColorDrawable(0xff557799));
    }
}
