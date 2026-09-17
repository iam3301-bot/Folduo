package jp.bunkaich.sukashimotion;
import android.app.Activity;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.util.List;
import java.util.ArrayList;
import static org.junit.Assert.*;
public class InnerNavigationTest {
 Activity activity;InnerNavigation nav;volatile int action=-1,task=-1,actionCount;
 void ui(Runnable task){InstrumentationRegistry.getInstrumentation().runOnMainSync(task);}
 View root()throws Exception{var f=InnerNavigation.class.getDeclaredField("root");f.setAccessible(true);return (View)f.get(nav);}
 View find(View v,String label){if(label.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),label);if(found!=null)return found;}return null;}
 @Before public void start(){Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();activity=InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));ui(()->nav=new InnerNavigation(activity.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null),0,1968,2184,(a,t)->{action=a;task=t;actionCount++;}));}
 @After public void stop(){ui(()->{nav.close();activity.finish();});}
 @Test public void controlsAreImmediatelyAvailableAndSettingsIsDistinct()throws Exception{
  View row=root();assertNotNull(find(row,activity.getString(R.string.nav_back)));assertNotNull(find(row,activity.getString(R.string.nav_home)));assertNotNull(find(row,activity.getString(R.string.nav_recents)));ui(()->find(row,activity.getString(R.string.nav_settings)).performClick());assertEquals(InnerNavigation.SETTINGS,action);
 }
 @Test public void cardSelectsExistingTaskAndClosesOnlyThePanel()throws Exception{
  Bundle app=new Bundle();app.putInt("taskId",27);app.putString("label","電卓");ui(()->nav.showRecent(List.of(app)));View row=root();ui(()->find(row,"電卓").performClick());assertEquals(0,action);assertEquals(27,task);assertFalse(nav.showingRecents());assertNotNull(find(root(),activity.getString(R.string.nav_back)));
 }
 @Test public void backInRecentsDoesNotCloseUnderlyingApp()throws Exception{
  ui(()->nav.showRecent(List.of()));View row=root();ui(()->find(row,activity.getString(R.string.nav_back)).performClick());assertEquals(-1,action);assertFalse(nav.showingRecents());
 }
 @Test public void controlsNeverRequestAppResizingOrKeyboardFocus()throws Exception{
  WindowManager.LayoutParams p=(WindowManager.LayoutParams)root().getLayoutParams();assertTrue((p.flags&WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)!=0);assertEquals(0,p.getFitInsetsTypes());assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,p.type);
 }
 @Test public void latePreviewCannotReopenDismissedPanel()throws Exception{
  ui(()->nav.showRecent(List.of()));View row=root();ui(()->find(row,activity.getString(R.string.nav_back)).performClick());ui(()->nav.setPreview(4,Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888)));assertFalse(nav.showingRecents());
 }
 @Test public void closingRemovesTouchableWindow()throws Exception{View before=root();ui(()->nav.close());assertFalse(before.isAttachedToWindow());assertNull(root());}
 private float dp(float value){return value*activity.getResources().getDisplayMetrics().density;}
 private void useGestures(){
  ui(()->{
   nav.close();Display display=activity.getDisplay();Display.Mode mode=display.getMode();boolean rotated=display.getRotation()==Surface.ROTATION_90||display.getRotation()==Surface.ROTATION_270;
   nav=new InnerNavigation(activity.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null),1,rotated?mode.getPhysicalHeight():mode.getPhysicalWidth(),rotated?mode.getPhysicalWidth():mode.getPhysicalHeight(),(a,t)->{action=a;task=t;actionCount++;},true);
  });
  InstrumentationRegistry.getInstrumentation().waitForIdleSync();
 }
 @SuppressWarnings("unchecked") private List<View> gestureWindows()throws Exception{
  var field=InnerNavigation.class.getDeclaredField("edges");field.setAccessible(true);List<View> windows=new ArrayList<>((List<View>)field.get(nav));windows.add(root());return windows;
 }
 private void motion(View view,int type,float x,float y,long down,long at){ui(()->{MotionEvent event=MotionEvent.obtain(down,at,type,x,y,0);try{view.dispatchTouchEvent(event);}finally{event.recycle();}});}
 @Test public void gesturesAreSelectedOnlyForFold8SecondaryDisplay(){
  assertTrue(InnerNavigation.gesturesFor("SM-F9760",1));assertFalse(InnerNavigation.gesturesFor("SM-F9760",0));assertFalse(InnerNavigation.gesturesFor("SM-F966Z",1));
 }
 @Test public void upwardSwipeGoesHomeWithoutShowingPersistentButtons()throws Exception{
  useGestures();View bottom=root();assertNull(find(bottom,activity.getString(R.string.nav_home)));
  long time=SystemClock.uptimeMillis();motion(bottom,MotionEvent.ACTION_DOWN,dp(100),dp(12),time,time);motion(bottom,MotionEvent.ACTION_MOVE,dp(100),-dp(60),time,time+50);motion(bottom,MotionEvent.ACTION_UP,dp(100),-dp(60),time,time+100);
  assertEquals(KeyEvent.KEYCODE_HOME,action);assertEquals(1,actionCount);assertFalse(nav.showingRecents());
 }
 @Test public void upwardHoldOpensRecentAppsOnlyOnce()throws Exception{
  useGestures();View bottom=root();long time=SystemClock.uptimeMillis();motion(bottom,MotionEvent.ACTION_DOWN,dp(100),dp(12),time,time);motion(bottom,MotionEvent.ACTION_MOVE,dp(100),-dp(60),time,time+50);
  Thread.sleep(420);InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertEquals(KeyEvent.KEYCODE_APP_SWITCH,action);
  motion(bottom,MotionEvent.ACTION_UP,dp(100),-dp(60),time,time+500);assertEquals(1,actionCount);
 }
 @Test public void bothSideEdgesSwipeBackButVerticalMotionDoesNot()throws Exception{
  useGestures();List<View> windows=gestureWindows();
  for(int side=0;side<2;side++){
   View edge=windows.get(side);long time=SystemClock.uptimeMillis();motion(edge,MotionEvent.ACTION_DOWN,dp(6),dp(100),time,time);motion(edge,MotionEvent.ACTION_MOVE,dp(6)+(side==0?dp(50):-dp(50)),dp(100),time,time+40);motion(edge,MotionEvent.ACTION_UP,dp(6),dp(100),time,time+80);
   assertEquals(KeyEvent.KEYCODE_BACK,action);assertEquals(side+1,actionCount);
  }
  View edge=windows.get(0);long time=SystemClock.uptimeMillis();motion(edge,MotionEvent.ACTION_DOWN,dp(6),dp(100),time,time);motion(edge,MotionEvent.ACTION_MOVE,dp(8),dp(180),time,time+40);motion(edge,MotionEvent.ACTION_UP,dp(8),dp(180),time,time+80);assertEquals(2,actionCount);
 }
 @Test public void cancelAndClosePreventDelayedNavigationAndRemoveEveryZone()throws Exception{
  useGestures();View bottom=root();long time=SystemClock.uptimeMillis();motion(bottom,MotionEvent.ACTION_DOWN,dp(100),dp(12),time,time);motion(bottom,MotionEvent.ACTION_MOVE,dp(100),-dp(60),time,time+40);motion(bottom,MotionEvent.ACTION_CANCEL,dp(100),-dp(60),time,time+80);
  Thread.sleep(400);assertEquals(0,actionCount);
  time=SystemClock.uptimeMillis();motion(bottom,MotionEvent.ACTION_DOWN,dp(100),dp(12),time,time);motion(bottom,MotionEvent.ACTION_MOVE,dp(100),-dp(60),time,time+40);List<View> windows=gestureWindows();ui(()->nav.close());Thread.sleep(400);
  assertEquals(0,actionCount);for(View view:windows)assertFalse(view.isAttachedToWindow());assertNull(root());ui(()->nav.showRecent(List.of()));assertNull(root());
 }
 @Test public void gestureTapOffersAccessibleRecentCardsAndReturnsToThinBar()throws Exception{
  useGestures();View bottom=root();assertTrue(bottom.isFocusable());long time=SystemClock.uptimeMillis();motion(bottom,MotionEvent.ACTION_DOWN,dp(100),dp(12),time,time);motion(bottom,MotionEvent.ACTION_UP,dp(100),dp(12),time,time+70);assertEquals(KeyEvent.KEYCODE_APP_SWITCH,action);
  Bundle app=new Bundle();app.putInt("taskId",27);app.putString("label","计算器");ui(()->nav.showRecent(List.of(app)));View panel=root();ui(()->find(panel,"计算器").performClick());
  assertEquals(0,action);assertEquals(27,task);assertFalse(nav.showingRecents());assertEquals(3,gestureWindows().size());assertNull(find(root(),activity.getString(R.string.nav_home)));
 }
 @Test public void gestureZonesLeaveTheAppCenterTouchable()throws Exception{
  java.util.concurrent.atomic.AtomicInteger touches=new java.util.concurrent.atomic.AtomicInteger();View background=new View(activity);
  ui(()->{background.setBackgroundColor(android.graphics.Color.GRAY);background.setOnTouchListener((v,e)->{touches.incrementAndGet();return true;});activity.setContentView(background);});useGestures();
  List<View> windows=gestureWindows();for(int i=0;i<windows.size();i++){
   WindowManager.LayoutParams p=(WindowManager.LayoutParams)windows.get(i).getLayoutParams();assertTrue((p.flags&WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)!=0);assertTrue((p.flags&WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)!=0);assertEquals(0,p.getFitInsetsTypes());
   assertTrue("Only thin edges may intercept app input",i==2?p.height<=dp(25):p.width<=dp(13));
  }
  int[] center=new int[2];ui(()->{background.getLocationOnScreen(center);center[0]+=background.getWidth()/2;center[1]+=background.getHeight()/2;});
  long time=SystemClock.uptimeMillis();var automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();
  MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,center[0],center[1],0),up=MotionEvent.obtain(time,time+40,MotionEvent.ACTION_UP,center[0],center[1],0);
  down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
  try{automation.injectInputEvent(down,true);automation.injectInputEvent(up,true);}finally{down.recycle();up.recycle();}
  InstrumentationRegistry.getInstrumentation().waitForIdleSync();assertTrue("App receives normal central touches through the surrounding gesture windows",touches.get()>0);assertEquals(0,actionCount);
 }
}
