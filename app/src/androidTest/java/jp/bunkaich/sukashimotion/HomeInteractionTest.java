package jp.bunkaich.sukashimotion;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class HomeInteractionTest {
    @Test public void innerIconsKeepTapAndLongPressSeparate() {
        var instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();
        instrumentation.runOnMainSync(() -> {
            int[] launches = {0}, selected = {-1};
            AppCatalog.App app = new AppCatalog.App("Calculator", new ComponentName("calculator", "calculator.Main"), new ColorDrawable(0xff00aa44));
            HomeScene scene = new HomeScene(context, new HomeScene.Actions() {
                public void launch(AppCatalog.App chosen) { assertEquals(app, chosen); launches[0]++; }
                public void choose(int slot) { selected[0] = slot; }
                public void drawer() {}
                public void settings() {}
                public void note() {}
            });
            scene.updateApps(List.of(app));
            for (boolean inner : new boolean[]{false, true, false, true}) {
                int width = inner ? 1968 : 1080;
                scene.setFold(inner, 0, false);
                scene.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(2184, View.MeasureSpec.EXACTLY));
                scene.layout(0, 0, width, 2184);
                int before = launches[0];
                assertTrue(scene.primary.tiles[0].performLongClick());
                assertEquals(0, selected[0]); assertEquals(before, launches[0]);
                assertTrue(scene.primary.tiles[0].performClick());
                assertEquals(before + 1, launches[0]);
                assertEquals(inner ? width / 2 : 0, scene.primary.getLeft());
            }
        });
    }
    @Test public void installedAppsCanBePagedAndPinnedWithoutSearching() {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        instrumentation.runOnMainSync(()->{
            Context context=instrumentation.getTargetContext();
            java.util.ArrayList<AppCatalog.App> apps=new java.util.ArrayList<>();
            for(int i=0;i<35;i++)apps.add(new AppCatalog.App("应用"+i,new ComponentName("app"+i,"app"+i+".Main"),new ColorDrawable(0xff557799)));
            AppCatalog.App[] launched={null},pinned={null};int[] searches={0};
            HomeScene scene=new HomeScene(context,new HomeScene.Actions(){
                public void launch(AppCatalog.App app){launched[0]=app;}
                public void choose(int slot){} public void drawer(){searches[0]++;} public void settings(){} public void note(){}
                public void pin(AppCatalog.App app){pinned[0]=app;}
            });
            scene.setCatalog(apps);scene.updateApps(apps.subList(0,16));
            layout(scene,false);assertEquals(3,scene.pageCount());
            swipe(scene,-1);assertEquals(1,scene.currentPage());assertEquals(0,searches[0]);
            assertEquals("Favorites must not repeat on later pages",apps.get(16),scene.primary.apps.get(0));
            scene.primary.tiles[0].performClick();assertEquals(apps.get(16),launched[0]);
            scene.primary.tiles[0].performLongClick();assertEquals(apps.get(16),pinned[0]);
            layout(scene,true);assertEquals("Unfolding retains the browsed page",1,scene.currentPage());
            swipe(scene,-1);assertEquals(2,scene.currentPage());
            assertEquals(apps.get(34),scene.primary.apps.get(2));assertEquals(View.INVISIBLE,scene.primary.tiles[3].getVisibility());
            swipe(scene,-1);assertEquals("The final page cannot become empty",2,scene.currentPage());
            scene.setCatalog(apps.subList(0,17));assertEquals("Uninstalling apps clamps to a real page",1,scene.currentPage());
            swipe(scene,1);assertEquals(0,scene.currentPage());assertEquals(apps.get(0),scene.primary.apps.get(0));
        });
    }
    @Test public void pinningExistingFavoriteSwapsSlotsWithoutDuplicatingIt(){
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        var prefs=context.getSharedPreferences("favorite-slot-regression",0);prefs.edit().clear().commit();
        ComponentName first=new ComponentName("first","first.Main"),second=new ComponentName("second","second.Main");
        try{
            prefs.edit().putString("slot_0",first.flattenToString()).putString("slot_1",second.flattenToString()).commit();
            AppCatalog.pin(prefs,first,1);
            assertEquals(second.flattenToString(),prefs.getString("slot_0",null));assertEquals(first.flattenToString(),prefs.getString("slot_1",null));
            AppCatalog.pin(prefs,first,5);
            assertEquals("An emptied position must not repopulate automatically","",prefs.getString("slot_1",null));
            assertEquals(first.flattenToString(),prefs.getString("slot_5",null));
        }finally{prefs.edit().clear().commit();}
    }
    private static void layout(HomeScene scene,boolean inner){
        int width=inner?1968:1080;scene.setFold(inner,0,false);
        scene.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2184,View.MeasureSpec.EXACTLY));
        scene.layout(0,0,width,2184);
    }
    private static void swipe(HomeScene scene,int direction){
        long time=android.os.SystemClock.uptimeMillis();float start=scene.getWidth()*.5f,end=start+direction*scene.getWidth()*.38f,y=scene.getHeight()*.82f;
        for(int i=0;i<3;i++){
            int action=i==0?android.view.MotionEvent.ACTION_DOWN:i==1?android.view.MotionEvent.ACTION_MOVE:android.view.MotionEvent.ACTION_UP;
            android.view.MotionEvent event=android.view.MotionEvent.obtain(time,time+i*40,action,i==0?start:end,y,0);
            scene.dispatchTouchEvent(event);event.recycle();
        }
    }
}
