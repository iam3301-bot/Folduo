package jp.bunkaich.sukashimotion;

import android.app.Activity;
import android.content.*;
import android.os.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ServiceLifecycleTest {
 Context context;Activity activity;FakeBridge bridge;MotionService nativeService;
 static class FakeBridge extends IShellBridge.Stub {
  volatile IAngleSink sink;volatile int captures,holds,releases,moves,starts;volatile int frameWidth=400,frameHeight=500;volatile Boolean heldInner;CountDownLatch holdEntered,allowHold,stopEntered,allowStop,statusEntered,allowStatus;
  public Bundle inspect(){return new Bundle();}
  public Bundle capture(int id){captures++;Bundle b=new Bundle();b.putParcelable("frame",PreviewActivity.sample(frameWidth,frameHeight));return b;}
  public Bundle captureBehind(int id,android.view.SurfaceControl[] exclude){return capture(id);}
  public Bundle windowState(int id){Bundle b=new Bundle();b.putBoolean("ready",true);b.putString("geometry","test");return b;}
  public Bundle taskWindowState(int id,int task,boolean wallpaper){return windowState(id);}
  public Bundle moveApp(int source,int target,boolean idle){moves++;Bundle b=new Bundle();b.putBoolean("ok",true);return b;}
  public Bundle statusIcons(boolean hidden){if(statusEntered!=null){statusEntered.countDown();try{allowStatus.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}Bundle b=new Bundle();b.putBoolean("ok",true);return b;}
  public Bundle navigate(int displayId,int action,int taskId){Bundle b=new Bundle();b.putBoolean("ok",true);return b;}
  public Bundle launchApp(int displayId,String component){Bundle b=new Bundle();b.putBoolean("ok",true);b.putBoolean("handled",true);return b;}
  public Bundle hold(boolean inner,int previousOwner){heldInner=inner;holds++;if(holdEntered!=null){holdEntered.countDown();try{allowHold.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}Bundle b=new Bundle();b.putBoolean("ok",true);return b;}
  public void release(){releases++;}public void heartbeat(){}public void startAngles(IAngleSink sink){this.sink=sink;starts++;}public void stopAngles(){if(stopEntered!=null){stopEntered.countDown();try{allowStop.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}sink=null;}public void destroy(){}
 }
 interface Check { boolean ok(); }
 void waitFor(Check check)throws Exception{long end=SystemClock.elapsedRealtime()+5000;while(!check.ok()&&SystemClock.elapsedRealtime()<end)Thread.sleep(20);assertTrue("Condition reached before timeout",check.ok());}
 @Before public void start()throws Exception{
  context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  MotionSettings.setEnabled(context,false);
  activity=InstrumentationRegistry.getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  bridge=new FakeBridge();BridgeConnection.bridge=bridge;
  context.startForegroundService(new Intent(context,MotionService.class));waitFor(()->MotionService.running&&bridge.sink!=null);
 }
 @After public void stop()throws Exception{
  if(bridge.allowHold!=null)bridge.allowHold.countDown();if(bridge.allowStop!=null)bridge.allowStop.countDown();if(bridge.allowStatus!=null)bridge.allowStatus.countDown();MotionSettings.setEnabled(context,false);context.stopService(new Intent(context,MotionService.class));waitFor(()->!MotionService.running);waitFor(()->bridge.sink==null);
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->activity.finish());Thread.sleep(100);
 }
 @Test public void coarseAnglesDoNotCaptureOrInventIntermediateValues()throws Exception{
  for(float angle:new float[]{180,90,0,90,180})bridge.sink.angle(angle,SystemClock.elapsedRealtime(),0);
  Thread.sleep(250);assertEquals(0,bridge.captures);assertEquals(0,bridge.holds);
 }
 private void useNativeDisplaySwitch(){
  locateService();
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{
   java.lang.reflect.Field nativeMode=MotionService.class.getDeclaredField("nativeEndpoints");nativeMode.setAccessible(true);nativeMode.setBoolean(nativeService,true);
  }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
 }
 private void locateService(){
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{
   Class<?> thread=Class.forName("android.app.ActivityThread");
   Object current=thread.getMethod("currentActivityThread").invoke(null);
   java.lang.reflect.Field services=thread.getDeclaredField("mServices");services.setAccessible(true);
   for(Object service:((java.util.Map<?,?>)services.get(current)).values())if(service instanceof MotionService){
    nativeService=(MotionService)service;return;
   }
   throw new AssertionError("MotionService missing");
  }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
 }
 private java.lang.reflect.Field serviceField(String name)throws ReflectiveOperationException{
  var field=MotionService.class.getDeclaredField(name);field.setAccessible(true);return field;
 }
 private FoldPolicy prepareStaleFixedLayout(boolean transitioning)throws Exception{
  locateService();FoldPolicy policy=new FoldPolicy(false);policy.active=transitioning;
  android.view.Display display=context.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0);
  android.graphics.Point size=new android.graphics.Point();display.getRealSize(size);
  Class<?> panelClass=Class.forName(MotionService.class.getName()+"$Panel");
  var constructor=panelClass.getDeclaredConstructor(android.view.Display.class,boolean.class,int.class,int.class);constructor.setAccessible(true);
  // The fake bridge cannot create a second physical screen. Two descriptors keep
  // the layout recovery path idle while exercising the real subscription lifecycle.
  Object outer=constructor.newInstance(display,false,size.x,size.y),inner=constructor.newInstance(display,true,size.x,size.y);
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{
   ((Handler)serviceField("main").get(nativeService)).removeCallbacks((Runnable)serviceField("health").get(nativeService));
   @SuppressWarnings("unchecked") java.util.List<Object> panels=(java.util.List<Object>)serviceField("panels").get(nativeService);
   panels.clear();panels.add(outer);panels.add(inner);
   serviceField("nativeEndpoints").setBoolean(nativeService,false);
   serviceField("layoutPrepared").setBoolean(nativeService,true);serviceField("fixedPrimaryInner").setBoolean(nativeService,false);
   serviceField("policy").set(nativeService,policy);serviceField("busy").setBoolean(nativeService,transitioning);
   serviceField("target").setFloat(nativeService,transitioning?60:0);serviceField("smoothed").setFloat(nativeService,transitioning?60:0);
   serviceField("source").setInt(nativeService,1);
   long now=SystemClock.elapsedRealtime();serviceField("angleStartedAt").setLong(nativeService,now-6000);serviceField("measuredAt").setLong(nativeService,now-2000);
  }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
  return policy;
 }
 private void checkHealthNow(){
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{
   Runnable health=(Runnable)serviceField("health").get(nativeService);
   ((Handler)serviceField("main").get(nativeService)).removeCallbacks(health);health.run();
  }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
 }
 @Test public void idleAngleResubscriptionPreservesPreparedLayoutAndRejectsOldSink()throws Exception{
  FoldPolicy policy=prepareStaleFixedLayout(false);var instrumentation=InstrumentationRegistry.getInstrumentation();
  int starts=bridge.starts,holds=bridge.holds,releases=bridge.releases;IAngleSink previous=bridge.sink;
  Object[] anchors={null};int[] generation={0},session={0};
  instrumentation.runOnMainSync(()->{try{
   anchors[0]=serviceField("anchors").get(nativeService);generation[0]=serviceField("generation").getInt(nativeService);session[0]=serviceField("sessionSerial").getInt(nativeService);
  }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
  checkHealthNow();waitFor(()->bridge.starts>starts);assertNotSame(previous,bridge.sink);
  previous.angle(90,SystemClock.elapsedRealtime(),1);instrumentation.runOnMainSync(()->{try{
   assertTrue(serviceField("layoutPrepared").getBoolean(nativeService));assertSame(policy,serviceField("policy").get(nativeService));
   assertSame("Idle reader recovery must keep wallpaper anchors",anchors[0],serviceField("anchors").get(nativeService));
   assertEquals(generation[0],serviceField("generation").getInt(nativeService));assertEquals(session[0],serviceField("sessionSerial").getInt(nativeService));
   assertEquals("Old subscription cannot start a new transition",0,serviceField("target").getFloat(nativeService),0);
  }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
  assertEquals(releases,bridge.releases);assertEquals(holds,bridge.holds);
  // Stop before capture: only angle routing and the existing policy are under test.
  bridge.statusEntered=new CountDownLatch(1);bridge.allowStatus=new CountDownLatch(1);
  try{
   bridge.sink.angle(30,SystemClock.elapsedRealtime(),1);assertTrue(bridge.statusEntered.await(2,TimeUnit.SECONDS));
   instrumentation.runOnMainSync(()->{try{
    assertSame(policy,serviceField("policy").get(nativeService));assertTrue(policy.active);assertTrue(policy.open);
    assertTrue(serviceField("layoutPrepared").getBoolean(nativeService));assertEquals(30,serviceField("target").getFloat(nativeService),0);
    // Cancel the queued capture without releasing the layout during the assertion.
    serviceField("generation").setInt(nativeService,serviceField("generation").getInt(nativeService)+1);
   }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
   assertEquals(releases,bridge.releases);assertEquals(holds,bridge.holds);
  }finally{bridge.allowStatus.countDown();}
 }
 @Test public void angleLossDuringTransitionStillReleasesTheInterruptedSession()throws Exception{
  prepareStaleFixedLayout(true);int starts=bridge.starts,releases=bridge.releases;
  checkHealthNow();waitFor(()->bridge.starts>starts&&bridge.releases>releases);
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{
   assertFalse(serviceField("layoutPrepared").getBoolean(nativeService));assertFalse(serviceField("busy").getBoolean(nativeService));
   assertTrue(((java.util.List<?>)serviceField("layers").get(nativeService)).isEmpty());
   assertTrue(Float.isNaN(serviceField("target").getFloat(nativeService)));
  }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
 }
 @Test public void reversalKeepsTheAlreadyPresentedPreparedDestination()throws Exception{
  locateService();var instrumentation=InstrumentationRegistry.getInstrumentation();
  android.view.Display display=context.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0);
  android.graphics.Point size=new android.graphics.Point();display.getRealSize(size);
  android.graphics.Bitmap source=android.graphics.Bitmap.createBitmap(320,400,android.graphics.Bitmap.Config.ARGB_8888);source.eraseColor(android.graphics.Color.BLUE);
  android.graphics.Bitmap destination=android.graphics.Bitmap.createBitmap(320,400,android.graphics.Bitmap.Config.ARGB_8888);destination.eraseColor(android.graphics.Color.MAGENTA);
  FrameTexture sourceFrame=FrameTexture.prepare(source,1,()->false),destinationFrame=FrameTexture.prepare(destination,1,()->false);
  java.lang.reflect.Field panels=MotionService.class.getDeclaredField("panels"),frozen=MotionService.class.getDeclaredField("frozen"),layers=MotionService.class.getDeclaredField("layers"),generation=MotionService.class.getDeclaredField("generation");
  panels.setAccessible(true);frozen.setAccessible(true);layers.setAccessible(true);generation.setAccessible(true);
  Class<?> panelClass=Class.forName(MotionService.class.getName()+"$Panel");
  var constructor=panelClass.getDeclaredConstructor(android.view.Display.class,boolean.class,int.class,int.class);constructor.setAccessible(true);
  // One real overlay is enough: the cached source never needs an overlay in this
  // phase, so source/destination panel descriptors may share the test display.
  Object sourcePanel=constructor.newInstance(display,false,size.x,size.y),destinationPanel=constructor.newInstance(display,true,size.x,size.y);
  var addLayer=MotionService.class.getDeclaredMethod("addLayer",panelClass,FrameTexture.class,boolean.class,int.class,Runnable.class);addLayer.setAccessible(true);
  var captureSource=MotionService.class.getDeclaredMethod("captureSource",int.class,boolean.class);captureSource.setAccessible(true);
  CountDownLatch presented=new CountDownLatch(1);SnapshotView[] existing=new SnapshotView[1];
  instrumentation.runOnMainSync(()->{try{
   @SuppressWarnings("unchecked") java.util.List<Object> descriptors=(java.util.List<Object>)panels.get(nativeService);
   descriptors.clear();descriptors.add(sourcePanel);descriptors.add(destinationPanel);
   @SuppressWarnings("unchecked") java.util.Map<Boolean,FrameTexture> cached=(java.util.Map<Boolean,FrameTexture>)frozen.get(nativeService);cached.put(false,sourceFrame);
   addLayer.invoke(nativeService,destinationPanel,destinationFrame,false,generation.getInt(nativeService),(Runnable)presented::countDown);
  }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
  assertTrue("Existing destination has actually been presented",presented.await(3,TimeUnit.SECONDS));
  instrumentation.runOnMainSync(()->{try{
   Object layer=((java.util.List<?>)layers.get(nativeService)).get(0);var image=layer.getClass().getDeclaredField("view");image.setAccessible(true);existing[0]=(SnapshotView)image.get(layer);
  }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
  bridge.statusEntered=new CountDownLatch(1);bridge.allowStatus=new CountDownLatch(1);
  try{
   instrumentation.runOnMainSync(()->{try{captureSource.invoke(nativeService,generation.getInt(nativeService),true);}catch(ReflectiveOperationException error){throw new AssertionError(error);}});
   // Pause the normal prepared-frame phase, after the early-cover main callback.
   assertTrue(bridge.statusEntered.await(3,TimeUnit.SECONDS));
   instrumentation.runOnMainSync(()->{
    assertTrue("The original overlay stays attached",existing[0].isAttachedToWindow());
    assertSame("Reversing must retain the actual prepared destination, not replace it with the source's sharp image",destinationFrame,existing[0].frame);
    assertTrue(existing[0].frame.prepared);
   });
  }finally{
   instrumentation.runOnMainSync(()->{try{generation.setInt(nativeService,generation.getInt(nativeService)+1);}catch(ReflectiveOperationException error){throw new AssertionError(error);}});
   bridge.allowStatus.countDown();
  }
 }
 @Test public void nativeEndpointsNeverKeepBothScreensOn()throws Exception{
  useNativeDisplaySwitch();
  for(float angle:new float[]{180,0,180,0})bridge.sink.angle(angle,SystemClock.elapsedRealtime(),3);
  Thread.sleep(250);assertEquals(0,bridge.holds);assertEquals(0,bridge.moves);assertEquals(0,bridge.captures);
 }
 @Test public void nativeTransitionCommitsSourceAndRequestsDestinationWithoutMovingTheCurrentApp()throws Exception{
  useNativeDisplaySwitch();
  android.view.Display display=context.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0);
  android.view.Display.Mode mode=display.getMode();int rotation=display.getRotation();
  boolean rotated=rotation==android.view.Surface.ROTATION_90||rotation==android.view.Surface.ROTATION_270;
  bridge.frameWidth=rotated?mode.getPhysicalHeight():mode.getPhysicalWidth();
  bridge.frameHeight=rotated?mode.getPhysicalWidth():mode.getPhysicalHeight();
  boolean sourceInner=Math.min(bridge.frameWidth,bridge.frameHeight)/(float)Math.max(bridge.frameWidth,bridge.frameHeight)>.7f;
  bridge.sink.angle(sourceInner?180:0,SystemClock.elapsedRealtime(),3);
  bridge.sink.angle(sourceInner?150:30,SystemClock.elapsedRealtime(),3);
  waitFor(()->bridge.holds>0);assertTrue(bridge.captures>0);assertEquals(Boolean.valueOf(!sourceInner),bridge.heldInner);assertEquals(0,bridge.moves);
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try{
   java.lang.reflect.Field field=MotionService.class.getDeclaredField("layers");field.setAccessible(true);boolean committed=false;
   for(Object layer:(java.util.List<?>)field.get(nativeService)){
    java.lang.reflect.Field id=layer.getClass().getDeclaredField("displayId"),drawn=layer.getClass().getDeclaredField("committed");id.setAccessible(true);drawn.setAccessible(true);
    committed|=id.getInt(layer)==0&&drawn.getBoolean(layer);
   }
   assertTrue("Source overlay must have committed before requesting the destination",committed);
  }catch(ReflectiveOperationException error){throw new AssertionError(error);}});
  // Pausing and reversing mid-fold must animate the existing two panels. A new
  // hold here would make Samsung power both displays OFF during remapping.
  Thread.sleep(150);int initialHolds=bridge.holds;
  for(float angle:sourceInner?new float[]{100,100,130,130,100}:new float[]{80,80,50,50,80})
   bridge.sink.angle(angle,SystemClock.elapsedRealtime(),3);
  InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{});Thread.sleep(300);
  assertEquals("Partial reversals must not request a new physical display mapping",initialHolds,bridge.holds);
  int beforeStop=bridge.releases;context.stopService(new Intent(context,MotionService.class));
  waitFor(()->!MotionService.running&&bridge.releases>beforeStop&&bridge.sink==null);assertEquals(0,bridge.moves);
 }
 @Test public void stopWhileWaitingForClosureDoesNotMoveApps()throws Exception{
  bridge.sink.angle(120,SystemClock.elapsedRealtime(),3);Thread.sleep(200);
  context.stopService(new Intent(context,MotionService.class));waitFor(()->!MotionService.running);waitFor(()->bridge.releases>0);
  assertEquals(0,bridge.captures);assertEquals(0,bridge.moves);assertEquals(0,bridge.holds);
 }
 @Test public void innerDisplayNeverArmsFromAngleAlone()throws Exception{
  // This test device has an inner-shaped physical mode. Even an angle of zero must
  // not arm OUTER_DEFAULT until the normal cover mapping really exists.
  for(float angle:new float[]{180,120,0,0,90,180})bridge.sink.angle(angle,SystemClock.elapsedRealtime(),3);
  Thread.sleep(250);assertEquals(0,bridge.captures);assertEquals(0,bridge.moves);assertEquals(0,bridge.holds);
  assertTrue(MotionService.status.is(R.string.close_to_prepare));
 }
 @Test public void transparentAnchorBecomesWallpaperTargetBehindOpaqueApp()throws Exception{
  Thread.sleep(150);
  ParcelFileDescriptor fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand("dumpsys window");
  String dump;try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){dump=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
  boolean target=dump.lines().anyMatch(line->line.contains("mWallpaperTarget")&&line.contains("Folduo angle anchor"));
  assertTrue("Wallpaper must stay active while a normal opaque activity is in front",target);
 }
 @Test public void screenOffSuspendsAnglesAndWakeRestartsThem()throws Exception{
  try{
   shell("input keyevent KEYCODE_SLEEP");waitFor(()->bridge.sink==null);
   shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard");waitFor(()->bridge.sink!=null);
  }finally{shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard");}
 }
 @Test public void newBridgeAutomaticallyRestartsAngleSubscription()throws Exception{
  FakeBridge previous=bridge;IAngleSink oldSink=previous.sink;
  bridge=new FakeBridge();BridgeConnection.bridge=bridge;
  waitFor(()->bridge.sink!=null&&previous.sink==null);
  oldSink.angle(99,SystemClock.elapsedRealtime(),1);Thread.sleep(100);
  assertFalse("Old callbacks must not feed the new connection",MotionService.status.resolve(context).contains("99°"));
  bridge.sink.angle(120,SystemClock.elapsedRealtime(),1);waitFor(()->MotionService.status.is(R.string.close_to_prepare));
 }
 @Test public void stoppedAngleReaderRecoversWithoutRestartingApplication()throws Exception{
  int initial=bridge.starts;bridge.sink.angle(120,SystemClock.elapsedRealtime(),1);
  waitFor(()->MotionService.status.is(R.string.close_to_prepare));
  long deadline=SystemClock.elapsedRealtime()+8000;
  while(bridge.starts==initial&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);
  assertTrue("A live binder with a stalled reader must be re-subscribed",bridge.starts>initial);
  assertTrue(MotionService.running);assertTrue(MotionSettings.recovery(context).equals(context.getString(R.string.angle_recovery)));
 }
 @Test public void slowScreenOffCleanupCannotStopNewWakeSubscription()throws Exception{
  bridge.stopEntered=new CountDownLatch(1);bridge.allowStop=new CountDownLatch(1);
  try{
   shell("input keyevent KEYCODE_SLEEP");assertTrue(bridge.stopEntered.await(2,TimeUnit.SECONDS));
   int before=bridge.starts;shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard");Thread.sleep(250);
   bridge.allowStop.countDown();waitFor(()->bridge.starts>before&&bridge.sink!=null);Thread.sleep(200);assertNotNull(bridge.sink);
  }finally{bridge.allowStop.countDown();shell("input keyevent KEYCODE_WAKEUP");shell("wm dismiss-keyguard");}
 }
 @Test public void explicitStopDisablesAutomaticRestore()throws Exception{
  assertTrue(MotionSettings.enabled(context));
  context.startService(new Intent(context,MotionService.class).setAction("stop"));waitFor(()->!MotionService.running);
  assertFalse(MotionSettings.enabled(context));
  new RestartReceiver().onReceive(context,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));Thread.sleep(200);
  assertFalse("A user stop must never resurrect itself",MotionService.running);
 }
 @Test public void packageUpdateRestoresOnlyPreviouslyEnabledMonitor()throws Exception{
  context.stopService(new Intent(context,MotionService.class));waitFor(()->!MotionService.running);waitFor(()->bridge.sink==null);Thread.sleep(150);
  assertTrue(MotionSettings.enabled(context));BridgeConnection.bridge=bridge;
  new RestartReceiver().onReceive(context,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));waitFor(()->MotionService.running&&bridge.sink!=null);
 }
 private void shell(String command)throws Exception{
  try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command))){in.readAllBytes();}
 }
}
