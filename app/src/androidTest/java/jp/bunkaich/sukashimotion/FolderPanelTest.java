package jp.bunkaich.sukashimotion;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.*;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class FolderPanelTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private Activity activity;
    private FolderPanel panel;
    @Before public void start(){activity=instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    @After public void stop(){instrumentation.runOnMainSync(()->{if(panel!=null)panel.dismiss();if(activity!=null)activity.finish();});}
    @Test public void folderShowsOrderedMembersAndTapUsesTheProvidedLauncherOnce(){
        AppCatalog.App first=app(0),second=app(1),third=app(2);AtomicReference<AppCatalog.App> launched=new AtomicReference<>();int[] calls={0};
        instrumentation.runOnMainSync(()->{
            panel=new FolderPanel(activity,new HomeFolders.Folder("test","工具",List.of(third.component().flattenToString(),first.component().flattenToString())),List.of(first,second,third),chosen->{launched.set(chosen);calls[0]++;},()->{},()->{},()->{});panel.show();
        });
        awaitGrid();
        instrumentation.runOnMainSync(()->{
            assertEquals("工具",panel.title.getText().toString());assertEquals(2,panel.grid.getAdapter().getCount());
            assertSame(third,panel.grid.getAdapter().getItem(0));assertSame(first,panel.grid.getAdapter().getItem(1));
            View tile=panel.grid.getChildAt(0);float x=tile.getLeft()+tile.getWidth()/2f,y=tile.getTop()+tile.getHeight()/2f;long now=SystemClock.uptimeMillis();
            MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0);panel.grid.dispatchTouchEvent(down);down.recycle();
            MotionEvent up=MotionEvent.obtain(now,now+40,MotionEvent.ACTION_UP,x,y,0);panel.grid.dispatchTouchEvent(up);up.recycle();
        });
        SystemClock.sleep(ViewConfiguration.getPressedStateDuration()+60L);instrumentation.waitForIdleSync();
        assertSame(third,launched.get());assertEquals(1,calls[0]);instrumentation.runOnMainSync(()->assertFalse(panel.isShowing()));
    }
    @Test public void openFolderRefreshesAfterRenameAndMembershipChange(){
        AppCatalog.App first=app(0),second=app(1),third=app(2);
        instrumentation.runOnMainSync(()->{
            panel=new FolderPanel(activity,new HomeFolders.Folder("test","旧名称",List.of(first.component().flattenToString(),second.component().flattenToString())),List.of(first,second,third),chosen->{},()->{},()->{},()->{});panel.show();
            panel.update(new HomeFolders.Folder("test","常用工具",List.of(third.component().flattenToString(),first.component().flattenToString())),List.of(first,second,third));
            assertTrue(panel.isShowing());assertEquals("常用工具",panel.title.getText().toString());assertSame(third,panel.grid.getAdapter().getItem(0));assertSame(first,panel.grid.getAdapter().getItem(1));
        });
    }
    @Test public void resizingKeepsMembersAndBackDismissesOnlyTheFolder(){
        List<AppCatalog.App> apps=new ArrayList<>();List<String> members=new ArrayList<>();
        for(int i=0;i<24;i++){AppCatalog.App app=app(i);apps.add(app);members.add(app.component().flattenToString());}
        instrumentation.runOnMainSync(()->{
            panel=new FolderPanel(activity,new HomeFolders.Folder("resize","工具",members),apps,chosen->{},()->{},()->{},()->{});panel.show();
        });awaitGrid();
        instrumentation.runOnMainSync(()->{
            ViewGroup root=(ViewGroup)panel.grid.getParent();
            for(int width:new int[]{1080,1968,1080}){
                root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2184,View.MeasureSpec.EXACTLY));root.layout(0,0,width,2184);
                assertEquals(24,panel.grid.getAdapter().getCount());assertEquals("工具",panel.title.getText().toString());
                View controls=root.getChildAt(root.getChildCount()-1);
                assertTrue("Folder controls must stay within the resized surface",controls.getBottom()<=root.getHeight());
                assertTrue("App grid must not overlap the folder controls",panel.grid.getBottom()<=controls.getTop());
                assertSame(apps.get(23),panel.grid.getAdapter().getItem(23));
            }
            panel.onBackPressed();assertFalse(panel.isShowing());assertFalse("Closing a folder must leave its activity running",activity.isFinishing());
        });
    }
    private void awaitGrid(){
        long end=SystemClock.uptimeMillis()+3000;boolean[] ready={false};
        while(SystemClock.uptimeMillis()<end){instrumentation.runOnMainSync(()->ready[0]=panel.grid.getChildCount()>0);if(ready[0])return;SystemClock.sleep(20);}fail("Folder grid did not lay out");
    }
    private AppCatalog.App app(int i){return new AppCatalog.App("应用"+i,new ComponentName("folder.launch"+i,"folder.launch"+i+".Main"),new ColorDrawable(0xff2288cc));}
}
