package jp.bunkaich.sukashimotion;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.FileDescriptor;
import java.io.PrintWriter;

/** Keeps the user-enabled monitor alive; capture is suspended while locked. */
public final class MotionService extends Service implements DisplayManager.DisplayListener,Choreographer.FrameCallback {
    static volatile boolean running;static volatile UiText status=UiText.of(R.string.stopped);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService jobs=Executors.newSingleThreadExecutor();
    private final ExecutorService controls=Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService poller=Executors.newSingleThreadScheduledExecutor();
    private final List<Panel> panels=new ArrayList<>();private final List<Layer> layers=new ArrayList<>();
    private final Map<Boolean,FrameTexture> frozen=new HashMap<>();
    private volatile List<Anchor> anchors=List.of();
    private volatile int generation,sessionSerial;private volatile boolean stopped;private boolean paused=true,busy,frameScheduled,blockedUntilEndpoint,finishing;private String panelSignature="";
    private long angleStartedAt,angleSession,retryAt;private int recoveries;private UiText lastRecovery=UiText.raw("");private String notificationText="";
    private long nonInteractiveSince;
    private boolean layoutPrepared,layoutPreparing,layoutRecovering,fixedPrimaryInner;
    private final boolean nativeEndpoints=DeviceSupport.nativeEndpoints(Build.MODEL);
    private boolean nativeAnimating,nativeCapturePending,nativeArming,nativeHeld,nativeHeldInner,nativeControlsReady,movingHome;
    private final Map<Integer,String> nativePresented=new HashMap<>();
    private final Set<Integer> nativeCoverPending=new HashSet<>();
    private int movingTask=-1;
    private IShellBridge bound;private FoldPolicy policy;private float target=Float.NaN,smoothed=Float.NaN;
    private LocaleList uiLocales;private InnerNavigation navigation;private String navigationError="";
    private long measuredAt,lastFrame;private int source=-1;private DisplayManager displays;
    private final ArrayDeque<String> angleHistory=new ArrayDeque<>();
    private long layerSerial;private long acceptedAngles;private String anchorError="",stage="idle";
    private final ArrayDeque<String> handoffs=new ArrayDeque<>();
    private final BroadcastReceiver power=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){
        if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction()))pauseIfNeeded();
        else if(Intent.ACTION_USER_PRESENT.equals(intent.getAction()))resume();
    }};
    private record Panel(Display display,boolean inner,int w,int h){}
    private record Anchor(int displayId,WindowManager wm,View view,WallpaperManager wallpaper){}
    private static final class Layer {
        final WindowManager wm;final SnapshotView view;final SnapshotSurface root;final int displayId;long serial;boolean committed,submitted;
        Layer(WindowManager wm,SnapshotView view,SnapshotSurface root,int displayId,long serial){this.wm=wm;this.view=view;this.root=root;this.displayId=displayId;this.serial=serial;}
    }
    private void trace(String message){stage=message;if(handoffs.size()>=32)handoffs.removeFirst();handoffs.addLast(SystemClock.elapsedRealtime()+":"+message);}
    private SurfaceControl[] excluded(){return layers.stream().map(l->l.root.getSurfaceControl()).filter(c->c!=null&&c.isValid()).toArray(SurfaceControl[]::new);}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onCreate(){
        super.onCreate();uiLocales=getResources().getConfiguration().getLocales();running=true;status=UiText.of(R.string.preparing);
        NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("motion",getString(R.string.notification_channel),NotificationManager.IMPORTANCE_LOW));
        startForeground(7,notification(getString(R.string.starting)));
        displays=getSystemService(DisplayManager.class);displays.registerDisplayListener(this,main);
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_USER_PRESENT);registerReceiver(power,filter,Context.RECEIVER_NOT_EXPORTED);
        BridgeConnection.init(this);
        poller.scheduleWithFixedDelay(()->{for(Anchor a:anchors)try{IBinder token=a.view.getWindowToken();if(token!=null)a.wallpaper.sendWallpaperCommand(token,BuildConfig.APPLICATION_ID+".READ_ANGLE",0,0,0,null);}catch(Exception ignored){}},0,16,TimeUnit.MILLISECONDS);
        main.post(health);
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration config){
        super.onConfigurationChanged(config);
        if(config.getLocales().equals(uiLocales))return;
        uiLocales=config.getLocales();
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("motion",getString(R.string.notification_channel),NotificationManager.IMPORTANCE_LOW));
        notificationText="";updateNotification();
        removeNavigation();updateNavigation();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?"restore":intent.getAction();
        if("stop".equals(action)){MotionSettings.setEnabled(this,false);stopSelf();return START_NOT_STICKY;}
        if("restore".equals(action)&&!MotionSettings.enabled(this)){stopSelf();return START_NOT_STICKY;}
        if(!DeviceSupport.homeReady(this)){MotionSettings.setEnabled(this,false);stopSelf();return START_NOT_STICKY;}
        MotionSettings.setEnabled(this,true);MotionSettings.recovery(this,"");
        if("restart".equals(action)){pause();recordRecovery(UiText.of(R.string.manual_restart));}
        resume();return START_STICKY;
    }
    private Notification notification(String text){
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,MotionService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent restart=PendingIntent.getService(this,3,new Intent(this,MotionService.class).setAction("restart"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"motion").setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle(getString(R.string.app_name)).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setOnlyAlertOnce(true).setOngoing(true).setContentIntent(open).addAction(new Notification.Action.Builder(null,getString(R.string.resume),restart).build()).addAction(new Notification.Action.Builder(null,getString(R.string.stop),stop).build()).build();
    }
    private void updateNotification(){
        String text=(status.is(R.string.angle_status)?UiText.of(layoutPrepared||nativeEndpoints?R.string.active:R.string.close_fully_to_prepare):status).resolve(this);
        if(!text.equals(notificationText)){notificationText=text;getSystemService(NotificationManager.class).notify(7,notification(text));}
    }
    private void recordRecovery(UiText reason){recoveries++;lastRecovery=reason;MotionSettings.recovery(this,reason);}
    private boolean unlocked(){return getSystemService(PowerManager.class).isInteractive()&&!getSystemService(KeyguardManager.class).isKeyguardLocked();}
    private void pauseIfNeeded(){
        if(unlocked()){nonInteractiveSince=0;return;}
        if(getSystemService(KeyguardManager.class).isKeyguardLocked()||!nativeEndpoints||!nativeAnimating){pause();return;}
        // Samsung briefly reports non-interactive while replacing the physical
        // panel behind display 0. A real lock still suspends capture immediately.
        long now=SystemClock.elapsedRealtime();if(nonInteractiveSince==0)nonInteractiveSince=now;
        if(now-nonInteractiveSince>=400)pause();
    }
    private void resume(){
        if(stopped||!unlocked()) {paused=true;status=UiText.of(R.string.waiting_unlock);return;}
        if(!paused)return;
        paused=false;++sessionSerial;layoutPrepared=layoutPreparing=layoutRecovering=false;blockedUntilEndpoint=false;rebuildPanels();Panel primary=panelById(0);fixedPrimaryInner=primary!=null&&primary.inner;policy=new FoldPolicy(fixedPrimaryInner);target=smoothed=Float.NaN;source=-1;measuredAt=lastFrame=0;status=UiText.of(R.string.preparing_connection);
    }
    private void pause(){paused=true;++angleSession;cancelSession();removeAnchors();IShellBridge b=bound;bound=null;if(b!=null)controls.execute(()->{try{b.stopAngles();b.release();}catch(Exception ignored){}});status=UiText.of(R.string.waiting_unlock);}
    private final Runnable health=new Runnable(){public void run(){
        if(stopped)return;
        if(!paused)pauseIfNeeded();
        // Fold display changes can send SCREEN_OFF without a lock/unlock broadcast pair.
        if(paused&&unlocked())resume();
        if(!paused)BridgeConnection.connect(MotionService.this);
        IShellBridge available=BridgeConnection.bridge;
        if(!paused&&bound!=available){
            if(bound!=null){cancelSession();IShellBridge old=bound;controls.execute(()->{try{old.stopAngles();}catch(Exception ignored){}});recordRecovery(UiText.of(R.string.helper_recovery));}
            bound=available;++angleSession;source=-1;target=smoothed=Float.NaN;measuredAt=0;
            if(available!=null)startAngles(available);
        }
        if(available==null&&!paused)status=BridgeConnection.status;
        long now=SystemClock.elapsedRealtime();
        // Wallpaper reports are continuous; a dead log reader or heartbeat expiry can
        // stop them while the binder itself remains alive. Re-register, not just wait.
        if(!paused&&bound!=null&&now-angleStartedAt>5000&&((source==1&&now-measuredAt>1500)||source<1)){
            boolean idleFixedLayout=!nativeEndpoints&&layoutPrepared&&!layoutPreparing&&!layoutRecovering&&!blockedUntilEndpoint
                &&!busy&&!finishing&&layers.isEmpty()&&policy!=null&&!policy.active&&Float.isFinite(target)
                &&(policy.open?target>=176:target<=3)&&findPanel(true,true)!=null&&findPanel(false,true)!=null;
            if(idleFixedLayout){
                // A quiet wallpaper reader must not tear down a healthy idle layout.
                // The next angle can arrive after the hinge has already started moving.
                trace("resubscribing-idle-angles");
            }else{
                cancelSession();source=-1;target=smoothed=Float.NaN;measuredAt=0;blockedUntilEndpoint=false;
                removeAnchors();rebuildPanels();
            }
            recordRecovery(UiText.of(R.string.angle_recovery));status=UiText.of(R.string.angle_retry);startAngles(bound);
        }
        if(!paused&&layoutPrepared)recoverLayoutIfNeeded();
        if(!paused&&layoutPrepared&&policy!=null&&policy.active&&Float.isFinite(target))handle(policy.update(target,SystemClock.elapsedRealtime()));
        updateNavigation();updateNotification();main.postDelayed(this,100);
    }};
    private void startAngles(IShellBridge bridge){
        angleStartedAt=SystemClock.elapsedRealtime();long session=++angleSession;
        IAngleSink sink=new IAngleSink.Stub(){public void angle(float value,long at,int kind){main.post(()->{if(session==angleSession&&bound==bridge)accept(value,at,kind);});}};
        // Serialize with pause/stop. Otherwise a late stopAngles from screen-off can
        // run AFTER wake-up's startAngles and silently leave a live binder with no sink.
        controls.execute(()->{try{bridge.startAngles(sink);}catch(Exception e){main.post(()->{if(!stopped&&bound==bridge){recordRecovery(UiText.of(R.string.angle_start_failed));status=UiText.of(R.string.angle_retry_error,UiText.error(e));}});}});
    }
    private void accept(float value,long at,int kind){
        if(stopped||paused||bound==null||!Float.isFinite(value)||at>SystemClock.elapsedRealtime()+50||SystemClock.elapsedRealtime()-at>600)return;
        if(kind==0){if(source<1)status=UiText.of(R.string.coarse_angles);return;}
        // Direct fine sensors take priority while active; wallpaper is the fallback.
        if(kind==1&&source>=2)return;
        if(kind==source&&at<measuredAt)return;
        source=kind;measuredAt=at;target=value;
        acceptedAngles++;
        synchronized(angleHistory){if(angleHistory.size()>=160)angleHistory.removeFirst();angleHistory.addLast(at+":"+value+":"+kind);}
        if(blockedUntilEndpoint){if(SystemClock.elapsedRealtime()<retryAt||value>3&&value<177)return;blockedUntilEndpoint=false;policy=new FoldPolicy(value>=177);}if(!Float.isFinite(smoothed))smoothed=value;
        status=UiText.of(R.string.angle_status,Math.round(value),UiText.of(kind==1?R.string.source_wallpaper:kind==2?R.string.source_samsung:R.string.source_standard));
        if(nativeEndpoints){acceptNative(value);return;}
        if(!layoutPrepared){
            Panel primary=panelById(0);
            // Samsung cancels INNER_DEFAULT on complete closure. Arm OUTER_DEFAULT only
            // once the normal closed layout is present, avoiding a primary-panel swap.
            if(primary==null||primary.inner||primary.display.getState()!=Display.STATE_ON||value>3){status=UiText.of(R.string.close_to_prepare);return;}
            if(!layoutPreparing){fixedPrimaryInner=false;prepareLayout();}return;
        }
        if(policy!=null)handle(policy.update(value,SystemClock.elapsedRealtime()));scheduleFrame();
    }
    private void acceptNative(float value){
        if(finishing){
            if(value>3&&value<174)finishing=false;
            else if(policy!=null&&(policy.open&&value<=3||!policy.open&&value>=176)){
                cancelSession();policy=new FoldPolicy(value>=176);smoothed=value;return;
            }else return;
        }
        Panel primary=panelById(0);
        if(primary==null)return;
        if(policy==null)policy=new FoldPolicy(primary.inner);
        FoldPolicy.Change change=policy.update(value,SystemClock.elapsedRealtime());
        if(change==FoldPolicy.Change.OPEN||change==FoldPolicy.Change.CLOSE){
            int ticket=++generation;nativeAnimating=true;nativeCapturePending=nativeControlsReady=false;nativeCoverPending.clear();nativePresented.clear();busy=true;
            // Reversals reuse the frozen frames and their foreground metadata.
            // In particular HOME must still wait for its wallpaper at the endpoint.
            for(Layer layer:layers){layer.root.animate().cancel();layer.root.setAlpha(1);}
            trace(change==FoldPolicy.Change.OPEN?"native-opening":"native-closing");
            IShellBridge bridge=bound;
            controls.execute(()->{try{
                if(ticket!=generation||stopped||bridge==null)return;
                Bundle result=bridge.statusIcons(true);
                if(!result.getBoolean("ok"))throw new IllegalStateException(result.getString("error"));
                main.postDelayed(()->{if(ticket==generation){nativeControlsReady=true;refreshNativeDisplay(ticket);}},34);
            }catch(Exception e){main.post(()->{if(ticket==generation)fail(UiText.of(R.string.status_icons_failed,UiText.error(e)));});}});
        }else if(change==FoldPolicy.Change.FINISH_OPEN||change==FoldPolicy.Change.FINISH_CLOSED){
            finishNative(generation,0);
        }
        if(nativeAnimating)refreshNativeDisplay(generation);
        scheduleFrame();
    }
    private void refreshNativeDisplay(int ticket){
        if(stopped||paused||ticket!=generation||!nativeAnimating||!nativeControlsReady||finishing)return;
        Panel primary=panelById(0);
        if(primary==null||primary.display.getState()!=Display.STATE_ON)return;
        for(Panel panel:new ArrayList<>(panels)){
            if(panel.display.getState()!=Display.STATE_ON)continue;
            FrameTexture actual=frozen.get(panel.inner),cover=actual;
            if(cover==null)cover=frozen.get(!panel.inner);
            int id=panel.display.getDisplayId();String key=panel.inner+":"+panel.w+":"+panel.h+":"+System.identityHashCode(cover);
            if(cover!=null&&!key.equals(nativePresented.get(id))){
                nativePresented.put(id,key);nativeCoverPending.add(id);
                addLayer(panel,cover,false,ticket,()->{nativeCoverPending.remove(id);linkFrames();trace(actual==null?"native-resize-covered":"native-frame-committed");refreshNativeDisplay(ticket);});
            }
        }
        if(frozen.get(primary.inner)==null&&!nativeCapturePending&&!nativeCoverPending.contains(0))captureNativeDisplay(ticket,primary);
        // Start the destination panel near the first hinge movement, instead of
        // waiting for Samsung's normal half-open threshold. The live app ALWAYS
        // stays on display 0; only its physical panel changes, like a normal fold.
        boolean primaryCovered=layers.stream().anyMatch(l->l.displayId==0&&l.committed);
        // Switching CONCURRENT_INNER/OUTER_DEFAULT powers both panels off briefly.
        // Keep the chosen mapping throughout a partial fold, including reversals;
        // both snapshots can follow the hinge without remapping the live app.
        if(primaryCovered&&frozen.get(primary.inner)!=null&&!nativeArming&&!nativeHeld)armNativeDestination(ticket,policy.open);
    }
    private void armNativeDestination(int ticket,boolean inner){
        nativeArming=true;int session=sessionSerial;IShellBridge bridge=bound;
        controls.execute(()->{try{
            if(session!=sessionSerial||stopped||!nativeAnimating||bridge==null)return;
            Bundle result=bridge.hold(inner,MotionSettings.ownerPid(this));
            if(!result.getBoolean("ok"))throw new IllegalStateException(result.getString("error"));
            MotionSettings.ownerPid(this,result.getInt("ownerPid"));
            // A hinge reversal changes image generation, not this pending mapping.
            main.post(()->{if(session==sessionSerial&&!stopped&&nativeAnimating){nativeArming=false;nativeHeld=true;nativeHeldInner=inner;trace("native-destination-lit");rebuildPanels();refreshNativeDisplay(generation);}});
        }catch(Exception e){main.post(()->{if(session==sessionSerial&&nativeAnimating)fail(UiText.of(R.string.prepare_failed,UiText.error(e)));});}});
    }
    private void captureNativeDisplay(int ticket,Panel panel){
        nativeCapturePending=true;
        IShellBridge bridge=bound;float density=getResources().getDisplayMetrics().density;
        jobs.execute(()->{try{
            long deadline=SystemClock.elapsedRealtime()+1500;String previous="";int consecutive=0;Bundle state=null;
            while(ticket==generation&&!stopped&&SystemClock.elapsedRealtime()<deadline){
                state=bridge.taskWindowState(0,-2,false);
                if(state.containsKey("error"))throw new IllegalStateException(state.getString("error"));
                String geometry=state.getInt("taskId",-1)+":"+state.getString("geometry","");
                consecutive=state.getBoolean("ready")?(geometry.equals(previous)?consecutive+1:1):0;previous=geometry;
                if(consecutive>=2)break;
                Thread.sleep(24);
            }
            if(ticket!=generation||stopped)return;
            if(consecutive<2)throw new IllegalStateException("@folduo/err_no_frame");
            NativeCaptureMask mask=nativeCaptureMask(ticket);if(mask==null)return;
            Bundle shot=bridge.captureBehind(0,mask.exclude);Bitmap bitmap=shot.getParcelable("frame",Bitmap.class);
            if(bitmap==null)throw new IllegalStateException(shot.getString("error","@folduo/capture_failed"));
            NativeCaptureMask after=nativeCaptureMask(ticket);if(after==null)return;
            // Logical display 0 can change dimensions while capture is in flight.
            // Retry on the new panel; never label an inner frame as a cover frame.
            if(mask.serial!=after.serial||bitmap.getWidth()!=panel.w||bitmap.getHeight()!=panel.h){
                main.post(()->{if(ticket==generation){nativeCapturePending=false;rebuildPanels();refreshNativeDisplay(ticket);}});return;
            }
            int task=state.getInt("taskId",-1);boolean home=state.getBoolean("home");
            // Light the destination as soon as the real source image is covered.
            // CPU blur preparation continues independently of that display request.
            FrameTexture sharp=FrameTexture.sharp(bitmap);
            main.post(()->{
                if(ticket!=generation||stopped)return;
                frozen.put(panel.inner,sharp);movingTask=task;movingHome=home;
                trace("native-source-captured");refreshNativeDisplay(ticket);
            });
            FrameTexture texture=FrameTexture.prepare(bitmap,density,()->ticket!=generation||stopped);
            main.post(()->{
                if(ticket!=generation||stopped||texture==null)return;
                frozen.put(panel.inner,texture);movingTask=task;movingHome=home;nativeCapturePending=false;busy=false;
                trace(panel.inner?"native-inner-captured":"native-cover-captured");refreshNativeDisplay(ticket);scheduleFrame();
            });
        }catch(Exception e){main.post(()->{if(ticket==generation)fail(UiText.of(R.string.transition_cancelled,UiText.error(e)));});}});
    }
    private record NativeCaptureMask(SurfaceControl[] exclude,long serial){}
    private NativeCaptureMask nativeCaptureMask(int ticket)throws Exception{
        CompletableFuture<NativeCaptureMask> result=new CompletableFuture<>();
        main.post(()->result.complete(ticket==generation&&!stopped?new NativeCaptureMask(excluded(),layerSerial):null));
        return result.get(500,TimeUnit.MILLISECONDS);
    }
    private void finishNative(int ticket,int attempt){
        if(ticket!=generation||stopped||!nativeAnimating||finishing)return;
        Panel primary=panelById(0);boolean inner=policy.open;
        // A reversal that reaches the opposite endpoint needs one final handoff.
        // Do this only at that endpoint, never whenever hinge direction changes.
        if(nativeHeld&&nativeHeldInner!=inner&&!nativeArming)armNativeDestination(ticket,inner);
        Layer latest=layers.stream().filter(l->l.displayId==0).max(Comparator.comparingLong(l->l.serial)).orElse(null);
        boolean committed=primary!=null&&latest!=null&&latest.committed&&latest.view.inner==primary.inner
            &&latest.root.getWidth()==primary.w&&latest.root.getHeight()==primary.h;
        if(primary==null||primary.inner!=inner||primary.display.getState()!=Display.STATE_ON||nativeArming||nativeCapturePending||nativeCoverPending.contains(0)||frozen.get(inner)==null||!committed){
            if(attempt>=60){fail(UiText.of(R.string.app_destination_unconfirmed));return;}
            refreshNativeDisplay(ticket);main.postDelayed(()->finishNative(ticket,attempt+1),30);return;
        }
        finishing=true;busy=false;IShellBridge bridge=bound;
        controls.execute(()->{try{
            if(ticket!=generation||stopped||bridge==null)return;
            bridge.release();
            main.post(()->{if(ticket==generation){nativeHeld=false;trace("native-display-released");revealNativeEndpoint(ticket,inner,0);}});
        }catch(Exception e){main.post(()->{if(ticket==generation)fail(UiText.of(R.string.handoff_failed,UiText.error(e)));});}});
    }
    private void revealNativeEndpoint(int ticket,boolean inner,int attempt){
        if(ticket!=generation||stopped)return;
        rebuildPanels();Panel primary=panelById(0);
        if(primary==null||primary.inner!=inner||primary.display.getState()!=Display.STATE_ON){
            if(attempt>=40){fail(UiText.of(R.string.app_destination_unconfirmed));return;}
            main.postDelayed(()->revealNativeEndpoint(ticket,inner,attempt+1),30);return;
        }
        awaitApp(ticket,primary,()->{
            if(!nativeAnimating)return;
            if(inner&&target<174||!inner&&target>3){finishing=false;acceptNative(target);return;}
            Panel current=panelById(0);
            if(current==null||current.inner!=inner||current.display.getState()!=Display.STATE_ON){revealNativeEndpoint(ticket,inner,attempt+1);return;}
            trace("native-endpoint-ready");
            for(Layer layer:layers){layer.view.setAngle(inner?180:0);layer.root.animate().alpha(0).setDuration(100).start();}
            main.postDelayed(()->{
                if(ticket!=generation||stopped)return;
                removeLayers();frozen.clear();movingTask=-1;movingHome=false;nativeAnimating=false;nativePresented.clear();nativeCoverPending.clear();finishing=false;
                fixedPrimaryInner=inner;policy=new FoldPolicy(inner);smoothed=target;lastFrame=0;
                restoreStatusIcons();trace("native-idle");
            },120);
        });
    }
    private void prepareLayout(){
        int ticket=++generation;layoutPreparing=true;trace("prepare-stable-panels");IShellBridge bridge=bound;
        // Retain the currently active physical mapping. Adding the other panel does not swap
        // logical display IDs, which otherwise forces Samsung's mapper through DISPLAY_OFF.
        controls.execute(()->{try{
            if(ticket!=generation||stopped||bridge==null)return;
            Bundle result=bridge.hold(fixedPrimaryInner,MotionSettings.ownerPid(this));if(!result.getBoolean("ok"))throw new IllegalStateException(result.getString("error"));MotionSettings.ownerPid(this,result.getInt("ownerPid"));
            main.post(()->awaitLayout(ticket,0));
        }catch(Exception e){main.post(()->{if(ticket==generation)fail(UiText.of(R.string.prepare_failed,UiText.error(e)));});}});
    }
    private void awaitLayout(int ticket,int attempt){
        if(stopped||ticket!=generation)return;rebuildPanels();Panel primary=panelById(0);
        if(findPanel(true,true)==null||findPanel(false,true)==null||primary==null||primary.inner!=fixedPrimaryInner){
            if(attempt>=35){fail(UiText.of(R.string.prepare_unconfirmed));return;}
            main.postDelayed(()->awaitLayout(ticket,attempt+1),40);return;
        }
        layoutPrepared=true;layoutPreparing=false;trace("stable-panels-ready");
        policy=new FoldPolicy(Float.isFinite(target)?target>=90:fixedPrimaryInner);
    }
    private void recoverLayoutIfNeeded(){
        if(nativeEndpoints||stopped||paused||!layoutPrepared||layoutRecovering||bound==null)return;
        if(findPanel(true,true)!=null&&findPanel(false,true)!=null)return;
        Panel primary=panelById(0);
        // Full closure cancels even OUTER_DEFAULT on this firmware. Re-add the inner
        // panel while the cover still owns display 0; never swap a lit primary panel.
        if(primary==null||primary.inner||primary.display.getState()!=Display.STATE_ON)return;
        layoutRecovering=true;int session=sessionSerial;IShellBridge bridge=bound;trace("rearming-after-display-release");
        controls.execute(()->{try{
            if(stopped||session!=sessionSerial)return;
            Bundle result=bridge.hold(false,MotionSettings.ownerPid(this));if(!result.getBoolean("ok"))throw new IllegalStateException(result.getString("error"));MotionSettings.ownerPid(this,result.getInt("ownerPid"));
            main.post(()->awaitRecoveredLayout(session,0));
        }catch(Exception e){main.post(()->{if(session==sessionSerial&&!stopped){layoutRecovering=false;fail(UiText.of(R.string.reprepare_failed,UiText.error(e)));}});}});
    }
    private void awaitRecoveredLayout(int session,int attempt){
        if(stopped||session!=sessionSerial)return;rebuildPanels();Panel primary=panelById(0);
        if(primary!=null&&!primary.inner&&findPanel(true,true)!=null&&findPanel(false,true)!=null){layoutRecovering=false;trace("display-request-rearmed");return;}
        if(attempt>=35){layoutRecovering=false;fail(UiText.of(R.string.relight_unconfirmed));return;}
        main.postDelayed(()->awaitRecoveredLayout(session,attempt+1),40);
    }
    private void handle(FoldPolicy.Change change){
        switch(change){case OPEN -> transition(true);case CLOSE -> transition(false);case FINISH_OPEN,FINISH_CLOSED -> finish();default -> {}}
    }
    private void transition(boolean opening){
        removeNavigation();
        int ticket=++generation;busy=true;finishing=false;for(Layer layer:layers){layer.root.animate().cancel();layer.root.setAlpha(1);}
        trace(opening?"opening-capture":"closing-capture");
        // Hide only icons (not inset sources), before taking the frozen image. A pair of
        // frames lets both SystemUI and the dismissed controls leave composition.
        IShellBridge bridge=bound;
        controls.execute(()->{try{
            if(stopped||ticket!=generation||bridge==null)return;
            Bundle result=bridge.statusIcons(true);
            if(!result.getBoolean("ok"))throw new IllegalStateException(result.getString("error"));
            main.post(()->main.postDelayed(()->captureSource(ticket,opening),34));
        }catch(Exception e){main.post(()->{if(ticket==generation)fail(UiText.of(R.string.status_icons_failed,UiText.error(e)));});}});
    }
    private void captureSource(int ticket,boolean opening){
        if(stopped||ticket!=generation)return;
        boolean outgoingInner=!opening;Panel outgoing=findPanel(outgoingInner,false);FrameTexture cached=frozen.get(outgoingInner);
        if(outgoing==null){fail(UiText.of(R.string.source_missing));return;}
        IShellBridge bridge=bound;if(bridge==null){fail(UiText.of(R.string.bridge_missing));return;}
        int displayId=outgoing.display.getDisplayId();SurfaceControl[] exclude=excluded();float density=getResources().getDisplayMetrics().density;
        jobs.execute(()->{
            try{
                if(stopped||ticket!=generation)return;
                FrameTexture frame=cached;
                if(frame==null){Bundle result=bridge.captureBehind(displayId,exclude);Bitmap bitmap=result.getParcelable("frame",Bitmap.class);if(bitmap==null)throw new IllegalStateException(result.getString("error","@folduo/capture_failed"));frame=FrameTexture.sharp(bitmap);}
                FrameTexture ready=frame;
                main.post(()->{
                    if(ticket!=generation||stopped)return;
                    if(!ready.prepared)addLayer(outgoing,ready,true,ticket,()->trace("source-frame-committed"));
                    // The other panel is already lit in fixed dual-screen mode. Hide its old
                    // desktop immediately, before CPU blur or the later application move.
                    Panel incoming=findPanel(opening,true);
                    if(incoming!=null){
                        // A reversal already has the correctly deformed image on this panel.
                        // Keep it: replacing it with a flat sharp hold would visibly jump.
                        boolean covered=layers.stream().anyMatch(layer->layer.displayId==incoming.display.getDisplayId()&&layer.submitted
                            &&layer.view.inner==incoming.inner&&layer.root.isAttachedToWindow()
                            &&layer.root.getWidth()==incoming.w&&layer.root.getHeight()==incoming.h);
                        if(!covered)addLayer(incoming,ready.transferSharp(outgoingInner),true,ticket,()->trace("destination-sharp-covered"));
                    }
                });
                FrameTexture texture=frame.prepared?frame:FrameTexture.prepare(frame.sharp,density,()->stopped||ticket!=generation);
                main.post(()->{
                    if(ticket!=generation||stopped||texture==null)return;frozen.put(outgoingInner,texture);
                    // The source starts deforming only after real blur levels are available.
                    controls.execute(()->{try{
                        if(ticket!=generation||stopped)return;
                        Bundle hidden=bridge.statusIcons(true);if(!hidden.getBoolean("ok"))throw new IllegalStateException(hidden.getString("error"));
                        main.post(()->{if(ticket==generation&&!stopped)addLayer(outgoing,texture,false,ticket,()->{trace("source-frost-committed");requestDisplays(ticket,opening);});});
                    }catch(Exception e){main.post(()->{if(ticket==generation)fail(UiText.of(R.string.status_icons_failed,UiText.error(e)));});}});
                });
            }catch(Exception e){main.post(()->{if(ticket==generation)fail(UiText.of(R.string.transition_cancelled,UiText.error(e)));});}
        });
    }
    private void requestDisplays(int ticket,boolean opening){requestDisplays(ticket,opening,0);}
    private void requestDisplays(int ticket,boolean opening,int attempt){
        if(ticket!=generation||stopped)return;IShellBridge bridge=bound;trace("source-covered-move-app");
        Panel source=findPanel(!opening,true),destination=findPanel(opening,true);
        if(source==null||destination==null){
            recoverLayoutIfNeeded();
            if(attempt>=35){fail(UiText.of(R.string.reprepare_timeout));return;}
            main.postDelayed(()->requestDisplays(ticket,opening,attempt+1),40);return;
        }
        FrameTexture cached=frozen.get(opening),outgoing=frozen.get(!opening);
        if(outgoing==null||!outgoing.prepared){fail(UiText.of(R.string.blur_unconfirmed));return;}
        // Cover the destination BEFORE moving the real app. Never expose its sharp
        // resized layout during capture/blur preparation, even for a single frame.
        jobs.execute(()->{try{
            if(ticket!=generation||stopped)return;
            FrameTexture cover=cached!=null?cached:outgoing.transfer(!opening,destination.w,destination.h);
            main.post(()->{
                if(ticket!=generation||stopped)return;
                addLayer(destination,cover,false,ticket,()->{
                    trace("destination-covered-move-app");moveCoveredApp(ticket,opening,source,destination);
                });
            });
        }catch(Exception e){main.post(()->{if(ticket==generation)fail(UiText.of(R.string.cover_failed,UiText.error(e)));});}});
    }
    private void moveCoveredApp(int ticket,boolean opening,Panel source,Panel destination){
        IShellBridge bridge=bound;
        controls.execute(()->{try{
            if(ticket!=generation||stopped||bridge==null)return;
            Bundle result=bridge.moveApp(source.display.getDisplayId(),destination.display.getDisplayId(),false);
            if(!result.getBoolean("ok"))throw new IllegalStateException(result.getString("error"));
            main.post(()->{if(ticket==generation){movingTask=result.getInt("taskId",-1);movingHome=result.getBoolean("home");trace("app-moved-without-display-swap");awaitPanels(ticket,opening,0);}});
        }catch(Exception e){main.post(()->{if(ticket==generation)fail(UiText.of(R.string.handoff_failed,UiText.error(e)));});}});
    }
    private void awaitPanels(int ticket,boolean opening,int attempt){
        if(stopped||ticket!=generation)return;
        rebuildPanels();Panel outgoing=findPanel(!opening,true),incoming=findPanel(opening,true);Panel primary=panelById(0);
        if(outgoing==null||incoming==null||primary==null||primary.inner!=fixedPrimaryInner){
            if(attempt>=35){fail(UiText.of(R.string.displays_unconfirmed));return;}
            main.postDelayed(()->awaitPanels(ticket,opening,attempt+1),40);return;
        }
        trace("both-panels-ready");
        // Both panels already have an opaque, frosted cover. Capture excludes those
        // owned surfaces without hiding them. A reversal can reuse the session's frame.
        if(frozen.get(opening)!=null){linkFrames();busy=false;trace("paired-frames-ready");return;}
        awaitApp(ticket,incoming,()->captureDestination(ticket,opening));
    }
    private void awaitApp(int ticket,Panel panel,Runnable ready){
        IShellBridge bridge=bound;
        int expectedTask=movingTask;boolean wallpaperRequired=nativeEndpoints&&movingHome;
        jobs.execute(()->{try{
            long deadline=SystemClock.elapsedRealtime()+1800;String previous="";int consecutive=0;
            while(!stopped&&ticket==generation&&SystemClock.elapsedRealtime()<deadline){
                Bundle state=bridge.taskWindowState(panel.display.getDisplayId(),expectedTask,wallpaperRequired);String geometry=state.getString("geometry","");
                consecutive=state.getBoolean("ready")&&!geometry.isEmpty()?(geometry.equals(previous)?consecutive+1:1):0;previous=geometry;
                if(consecutive>=2){main.post(()->{if(ticket==generation&&!stopped){trace("app-frame-ready");ready.run();}});return;}
                Thread.sleep(32);
            }
            main.post(()->{if(ticket==generation)fail(UiText.of(R.string.app_ready_timeout));});
        }catch(Exception e){main.post(()->{if(ticket==generation)fail(UiText.of(R.string.readiness_failed,UiText.error(e)));});}});
    }
    private void captureDestination(int ticket,boolean opening){
        Panel incoming=findPanel(opening,true);if(incoming==null){fail(UiText.of(R.string.destination_missing));return;}
        int id=incoming.display.getDisplayId();IShellBridge bridge=bound;SurfaceControl[] exclude=excluded();float density=getResources().getDisplayMetrics().density;
        jobs.execute(()->{try{
            if(stopped||ticket!=generation)return;
            // Our freeze stays visible. Exclude its owned surfaces from this capture only.
            Bundle result=bridge.captureBehind(id,exclude);Bitmap bitmap=result.getParcelable("frame",Bitmap.class);
            if(bitmap==null)throw new IllegalStateException(result.getString("error","@folduo/destination_capture_failed"));
            if(bitmap.getWidth()!=incoming.w||bitmap.getHeight()!=incoming.h)throw new IllegalStateException("@folduo/destination_resizing");
            FrameTexture texture=FrameTexture.prepare(bitmap,density,()->ticket!=generation||stopped);
            main.post(()->{if(ticket!=generation||stopped||texture==null)return;frozen.put(opening,texture);Panel destination=findPanel(opening,true);if(destination!=null)addLayer(destination,texture,false,ticket,()->{linkFrames();busy=false;trace("paired-frames-ready");scheduleFrame();});});
        }catch(Exception e){main.post(()->{if(ticket==generation)fail(UiText.of(R.string.destination_failed,UiText.error(e)));});}});
    }
    private void addLayer(Panel panel,FrameTexture frame,boolean sharpHold,int ticket,Runnable ready){
        if(frame==null)return;
        try{
            Layer current=layers.stream().filter(l->l.displayId==panel.display.getDisplayId()).max(Comparator.comparingLong(l->l.serial)).orElse(null);
            if(current!=null&&current.submitted&&current.view.inner==panel.inner
                &&current.root.isAttachedToWindow()&&current.root.getWidth()==panel.w&&current.root.getHeight()==panel.h){
                // Keep the submitted surface while sharp/blur textures or direction change.
                // Replacing the overlay window can briefly expose the native wallpaper.
                long revision=++layerSerial;current.serial=revision;current.committed=false;
                current.view.setFrame(frame);current.view.setSharpHold(sharpHold);
                current.view.setAngle(Float.isFinite(smoothed)?smoothed:target);
                if(!panel.inner)current.view.setRearFrame(frozen.get(true),false);
                current.view.afterFrame(()->main.post(()->{
                    if(ticket!=generation||stopped||!layers.contains(current)||current.serial!=revision)return;
                    current.committed=current.submitted=true;ready.run();
                }));
                main.postDelayed(()->{if(ticket==generation&&layers.contains(current)&&current.serial==revision&&!current.committed)fail(UiText.of(R.string.frozen_draw_failed));},1400);
                return;
            }
            Context context=createDisplayContext(panel.display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null);
            WindowManager wm=context.getSystemService(WindowManager.class);
            SnapshotView view=new SnapshotView(context,frame,panel.inner,false);view.logicalWidth=panel.w;view.setSharpHold(sharpHold);
            if(!panel.inner)view.setRearFrame(frozen.get(true),false);
            Layer[] created=new Layer[1];SnapshotSurface root=new SnapshotSurface(context,view,()->main.post(()->{
                Layer layer=created[0];if(ticket!=generation||stopped||!layers.contains(layer))return;
                // A late callback from an older frame must not remove its successor.
                if(layers.stream().anyMatch(l->l.displayId==layer.displayId&&l.serial>layer.serial&&l.committed)){removeLayer(layer);return;}
                layer.committed=layer.submitted=true;
                for(Layer old:new ArrayList<>(layers))if(old.displayId==layer.displayId&&old.serial<layer.serial)removeLayer(old);
                ready.run();
            }));
            WindowManager.LayoutParams lp=snapshotLayout();
            Layer layer=new Layer(wm,view,root,panel.display.getDisplayId(),++layerSerial);created[0]=layer;layers.add(layer);
            wm.addView(root,lp);view.setAngle(Float.isFinite(smoothed)?smoothed:target);
            // A missing frame callback must never leave a permanent frozen screen.
            long initialRevision=layer.serial;
            main.postDelayed(()->{if(ticket==generation&&layers.contains(layer)&&layer.serial==initialRevision&&!layer.committed)fail(UiText.of(R.string.frozen_draw_failed));},1400);
        }catch(Exception e){fail(UiText.of(R.string.overlay_failed,UiText.error(e)));}
    }
    private void linkFrames(){
        FrameTexture inner=frozen.get(true);
        if(inner!=null)for(Layer layer:layers)if(!layer.view.inner)layer.view.setRearFrame(inner,true);
    }
    static WindowManager.LayoutParams snapshotLayout(){
        WindowManager.LayoutParams lp=layout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);
        // Android caps non-touchable APPLICATION_OVERLAY windows to alpha 0.8. A
        // frozen image must instead consume touches until removal, keeping alpha 1.
        // The angle-only anchors remain non-touchable and transparent below.
        lp.alpha=1;lp.setTitle("Folduo fold snapshot");lp.preferredRefreshRate=120;lp.windowAnimations=0;return lp;
    }
    private static WindowManager.LayoutParams layout(int w,int h){
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.LEFT;lp.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;lp.setFitInsetsTypes(0);return lp;
    }
    private void scheduleFrame(){if(!frameScheduled&&!stopped&&!paused){frameScheduled=true;Choreographer.getInstance().postFrameCallback(this);}}
    @Override public void doFrame(long nanos){
        frameScheduled=false;if(stopped||paused||finishing||!Float.isFinite(target))return;
        float dt=lastFrame==0?1/80f:(nanos-lastFrame)/1e9f;lastFrame=nanos;
        smoothed=FoldPolicy.smooth(smoothed,target,dt);if(Math.abs(smoothed-target)<.015f)smoothed=target;
        for(Layer layer:layers)layer.view.setAngle(smoothed);
        if(smoothed!=target)scheduleFrame();
    }
    private void finish(){finishWhenReady(generation,0);}
    private void finishWhenReady(int expected,int attempt){
        if(stopped||expected!=generation)return;
        if(busy){if(attempt>=30){fail(UiText.of(R.string.move_timeout));return;}main.postDelayed(()->finishWhenReady(expected,attempt+1),40);return;}
        int ticket=++generation;busy=false;finishing=true;boolean inner=policy.open;float endpoint=inner?180:0;
        trace("endpoint-covering");
        for(Layer layer:layers){layer.view.setSharpHold(false);layer.view.setAngle(endpoint);}
        Layer cover=layers.stream().filter(l->l.committed&&l.view.inner==inner).findFirst().orElse(null);
        java.util.concurrent.atomic.AtomicBoolean revealed=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable reveal=()->{if(!revealed.compareAndSet(false,true))return;awaitFinal(ticket,inner,0);};
        if(cover!=null)cover.view.afterFrame(()->main.post(()->{if(ticket==generation){trace("endpoint-frame-committed");reveal.run();}}));
        else reveal.run();
        main.postDelayed(()->{if(ticket==generation)reveal.run();},500);
    }
    private void awaitFinal(int ticket,boolean inner,int attempt){
        if(stopped||ticket!=generation)return;rebuildPanels();Panel destination=findPanel(inner,true);
        if(destination==null){if(attempt>=35){fail(UiText.of(R.string.app_destination_unconfirmed));return;}main.postDelayed(()->awaitFinal(ticket,inner,attempt+1),40);return;}
        awaitApp(ticket,destination,()->{
            trace("handoff-with-stable-panels");
            for(Layer layer:layers)layer.root.animate().alpha(0).setDuration(180).setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()).start();
            main.postDelayed(()->{if(ticket!=generation)return;removeLayers();frozen.clear();finishing=false;smoothed=target;restoreStatusIcons();trace("idle");updateNavigation();},200);
        });
    }
    private void cancelSession(){
        ++generation;++sessionSerial;busy=false;finishing=false;layoutPrepared=layoutPreparing=layoutRecovering=false;removeNavigation();removeLayers();frozen.clear();
        nativeAnimating=nativeCapturePending=nativeArming=nativeHeld=nativeControlsReady=false;nativePresented.clear();nativeCoverPending.clear();movingTask=-1;movingHome=false;
        if(policy!=null)policy.active=false;
        IShellBridge bridge=bound;if(bridge!=null)controls.execute(()->{try{bridge.release();}catch(Exception ignored){}});
    }
    private void restoreStatusIcons(){IShellBridge bridge=bound;if(bridge!=null)controls.execute(()->{try{bridge.statusIcons(false);}catch(Exception ignored){}});}
    private void updateNavigation(){
        if(nativeEndpoints){removeNavigation();return;}
        Panel inner=findPanel(true,true);
        boolean show=!stopped&&!paused&&layoutPrepared&&!busy&&!finishing&&policy!=null&&!policy.active&&target>=176&&inner!=null&&inner.display.getDisplayId()==1;
        if(!show){removeNavigation();return;}
        if(navigation!=null&&navigation.width==inner.w&&navigation.height==inner.h)return;
        removeNavigation();
        try{
            Context context=createDisplayContext(inner.display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null);
            navigation=new InnerNavigation(context,1,inner.w,inner.h,this::navigate);
        }catch(Exception e){navigationError=ShellBridge.message(e);}
    }
    private void navigate(int action,int taskId){
        IShellBridge bridge=bound;InnerNavigation owner=navigation;int session=sessionSerial;
        if(bridge==null||owner==null||busy||finishing)return;
        controls.execute(()->{try{
            if(stopped||session!=sessionSerial)return;
            Bundle result=bridge.navigate(owner.displayId,action,taskId);
            main.post(()->{
                if(stopped||session!=sessionSerial||navigation!=owner)return;
                if(!result.getBoolean("ok")){navigationError=result.getString("error");android.widget.Toast.makeText(this,getString(R.string.navigation_failed,UiText.raw(navigationError).resolve(this)),android.widget.Toast.LENGTH_SHORT).show();return;}
                navigationError="";
                if(action==KeyEvent.KEYCODE_APP_SWITCH){
                    ArrayList<Bundle> apps=result.getParcelableArrayList("apps",Bundle.class);
                    owner.showRecent(apps==null?List.of():apps);
                    if(apps!=null)loadRecentPreviews(owner,apps,session);
                }
            });
        }catch(Exception e){main.post(()->{navigationError=ShellBridge.message(e);});}});
    }
    private void loadRecentPreviews(InnerNavigation owner,List<Bundle> apps,int session){
        IShellBridge bridge=bound;
        controls.execute(()->{
            for(Bundle app:apps){
                if(stopped||session!=sessionSerial||navigation!=owner||!owner.showingRecents())return;
                try{
                    int task=app.getInt("taskId",-1);
                    Bundle result=bridge.navigate(1,InnerNavigation.PREVIEW,task);
                    Bitmap image=result.getParcelable("preview",Bitmap.class);
                    if(image!=null)main.post(()->{if(!stopped&&session==sessionSerial&&navigation==owner)owner.setPreview(task,image);});
                }catch(Exception ignored){} // An unavailable/protected preview leaves the app icon.
            }
        });
    }
    private void removeNavigation(){if(navigation!=null){try{navigation.close();}catch(Exception ignored){}navigation=null;}}
    private void fail(UiText reason){trace("cancelled: "+reason.resolve(this));cancelSession();blockedUntilEndpoint=true;retryAt=SystemClock.elapsedRealtime()+2000;recordRecovery(reason);status=UiText.of(R.string.failure_retry,reason);}
    private Panel panelById(int id){for(Panel p:panels)if(p.display.getDisplayId()==id)return p;return null;}
    private Panel findPanel(boolean inner,boolean on){for(Panel p:panels)if(p.inner==inner&&(!on||p.display.getState()==Display.STATE_ON))return p;return null;}
    private void rebuildPanels(){
        if(stopped||paused)return;
        List<Panel> discovered=new ArrayList<>();StringBuilder signature=new StringBuilder();
        for(Display display:displays.getDisplays()){
            if(display.getDisplayId()>1)continue; // Fold7 built-in logical displays only.
            // getRealSize can inherit the process activity's max bounds after that activity
            // moves to the secondary panel. Mode dimensions remain tied to this display.
            Display.Mode mode=display.getMode();int rotation=display.getRotation();
            boolean rotated=rotation==Surface.ROTATION_90||rotation==Surface.ROTATION_270;
            Point size=new Point(rotated?mode.getPhysicalHeight():mode.getPhysicalWidth(),rotated?mode.getPhysicalWidth():mode.getPhysicalHeight());if(size.x<=0||size.y<=0)continue;
            boolean inner=Math.min(size.x,size.y)/(float)Math.max(size.x,size.y)>.7f;
            discovered.add(new Panel(display,inner,size.x,size.y));signature.append(display.getDisplayId()).append(':').append(size.x).append(':').append(size.y).append(':').append(display.getState()).append(';');
        }
        panels.clear();panels.addAll(discovered);
        if(panelSignature.equals(signature.toString())&&!anchors.isEmpty())return;
        panelSignature=signature.toString();
        // A live logical display keeps its wallpaper target through resize/remapping.
        // Removing every anchor first can hide the wallpaper between replacements.
        List<Anchor> previous=anchors;List<Anchor> next=new ArrayList<>();
        if(Settings.canDrawOverlays(this))for(Panel panel:panels){
            Anchor existing=previous.stream().filter(a->a.displayId==panel.display.getDisplayId()&&a.view.isAttachedToWindow()).findFirst().orElse(null);
            if(existing!=null){next.add(existing);continue;}
            // A discovered logical display can briefly go OFF during a physical
            // swap. Keep its attached target; only a new anchor requires it ON.
            if(panel.display.getState()!=Display.STATE_ON)continue;
            try{
                Context context=createDisplayContext(panel.display).createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null);
                WindowManager wm=context.getSystemService(WindowManager.class);View view=new View(context);view.setBackgroundColor(Color.TRANSPARENT);
                WindowManager.LayoutParams lp=layout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);lp.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;lp.alpha=.01f;lp.setTitle("Folduo angle anchor");
                wm.addView(view,lp);next.add(new Anchor(panel.display.getDisplayId(),wm,view,WallpaperManager.getInstance(context)));
            }catch(Exception e){anchorError=ShellBridge.message(e);}
        }
        anchors=List.copyOf(next);
        for(Anchor old:previous)if(!next.contains(old))try{old.wm.removeViewImmediate(old.view);}catch(Exception ignored){}
    }
    private void removeAnchors(){List<Anchor> previous=anchors;anchors=List.of();for(Anchor a:previous)try{a.wm.removeViewImmediate(a.view);}catch(Exception ignored){}}
    private void removeLayer(Layer layer){layers.remove(layer);layer.root.animate().cancel();try{layer.wm.removeViewImmediate(layer.root);}catch(Exception ignored){}}
    private void removeLayers(){for(Layer layer:new ArrayList<>(layers))removeLayer(layer);}
    @Override protected void dump(FileDescriptor fd,PrintWriter out,String[] args){
        out.println("running="+running+" paused="+paused+" unlocked="+unlocked()+" status="+status.resolve(this));
        out.println("enabled="+MotionSettings.enabled(this)+" recoveries="+recoveries+" lastRecovery="+lastRecovery.resolve(this));
        out.println("source="+source+" target="+target+" smoothed="+smoothed+" ageMs="+(SystemClock.elapsedRealtime()-measuredAt)+" accepted="+acceptedAngles);
        out.println("busy="+busy+" blocked="+blockedUntilEndpoint+" panels="+panelSignature+" anchors="+anchors.size()+" layers="+layers.size()+" anchorError="+anchorError);
        out.println("fixedPrimaryInner="+fixedPrimaryInner+" layoutPrepared="+layoutPrepared+" layoutPreparing="+layoutPreparing+" layoutRecovering="+layoutRecovering);
        out.println("innerNavigation="+(navigation!=null)+" navigationError="+navigationError);
        out.println("nativeEndpoints="+nativeEndpoints+" nativeAnimating="+nativeAnimating+" nativeCapturePending="+nativeCapturePending+" nativeHeld="+nativeHeld+" nativeHeldInner="+nativeHeldInner+" movingTask="+movingTask+" movingHome="+movingHome);
        out.println("stage="+stage+" history="+String.join(",",handoffs));
        synchronized(angleHistory){out.println("angles="+String.join(",",angleHistory));}
        IShellBridge bridge=bound;if(bridge!=null)try{Bundle report=bridge.inspect();out.println("statusIconsHidden="+report.getBoolean("statusIconsHidden"));out.println(MainActivity.formatReport(this,report));}catch(Exception e){out.println(ShellBridge.message(e));}
    }
    private void displayChanged(){rebuildPanels();if(nativeEndpoints)refreshNativeDisplay(generation);else recoverLayoutIfNeeded();}
    public void onDisplayAdded(int id){displayChanged();}public void onDisplayRemoved(int id){displayChanged();}public void onDisplayChanged(int id){displayChanged();}
    @Override public void onDestroy(){
        stopped=true;running=false;status=UiText.of(R.string.stopped);main.removeCallbacksAndMessages(null);Choreographer.getInstance().removeFrameCallback(this);
        displays.unregisterDisplayListener(this);unregisterReceiver(power);cancelSession();removeAnchors();poller.shutdownNow();
        IShellBridge bridge=bound;bound=null;controls.execute(()->{try{if(bridge!=null){bridge.stopAngles();bridge.release();}}catch(Exception ignored){}});jobs.shutdown();controls.shutdown();BridgeConnection.disconnect();super.onDestroy();
    }
}
