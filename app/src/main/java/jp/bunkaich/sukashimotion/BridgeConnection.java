package jp.bunkaich.sukashimotion;

import android.content.*;
import android.os.*;
import java.util.concurrent.*;
import rikka.shizuku.Shizuku;

final class BridgeConnection {
    static volatile IShellBridge bridge;
    static volatile UiText status=UiText.of(R.string.bridge_waiting);
    static final ExecutorService work=Executors.newSingleThreadExecutor();
    private static final ScheduledExecutorService pulse=Executors.newSingleThreadScheduledExecutor();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final BridgeRecovery recovery=new BridgeRecovery();
    private static boolean initialized;
    private static Shizuku.UserServiceArgs args;
    private static ServiceConnection connection;

    static synchronized void init(Context context){
        if(initialized)return;initialized=true;
        args=new Shizuku.UserServiceArgs(new ComponentName(context,ShellBridge.class)).daemon(false).processNameSuffix("motion_bridge").debuggable(BuildConfig.DEBUG).version(BuildConfig.VERSION_CODE);
        Shizuku.addBinderDeadListener(()->{synchronized(BridgeConnection.class){bridge=null;recovery.lost();status=UiText.of(R.string.bridge_start_shizuku);}});
        pulse.scheduleWithFixedDelay(()->{IShellBridge b=bridge;if(b!=null)try{b.heartbeat();}catch(Exception e){synchronized(BridgeConnection.class){if(bridge==b){bridge=null;recovery.lost();status=UiText.of(R.string.bridge_reconnecting);}}}},0,1,TimeUnit.SECONDS);
    }
    static boolean permitted(){try{return Shizuku.pingBinder()&&Shizuku.checkSelfPermission()==0;}catch(Exception e){return false;}}
    static synchronized void connect(Context context){
        init(context);if(bridge!=null)return;
        boolean permitted=permitted();
        BridgeRecovery.Attempt attempt=recovery.poll(SystemClock.elapsedRealtime(),permitted);
        if(!permitted)status=UiText.of(R.string.bridge_permission);
        if(attempt==null)return;
        status=UiText.of(R.string.bridge_connecting);
        ServiceConnection previous=connection;
        ServiceConnection next=new ServiceConnection(){
            @Override public void onServiceConnected(ComponentName name,IBinder binder){
                synchronized(BridgeConnection.class){
                    if(!recovery.current(attempt.id()))return;
                    if(binder==null||!binder.isBinderAlive()){
                        recovery.failed(attempt.id());status=UiText.of(R.string.bridge_reconnecting);return;
                    }
                    recovery.connected(attempt.id());bridge=IShellBridge.Stub.asInterface(binder);status=UiText.of(R.string.bridge_connected);
                    android.util.Log.i("FolduoBridge","Helper connected");
                }
            }
            @Override public void onServiceDisconnected(ComponentName name){
                synchronized(BridgeConnection.class){
                    if(!recovery.current(attempt.id()))return;
                    bridge=null;recovery.lost();status=UiText.of(R.string.bridge_reconnecting);
                }
            }
        };
        connection=next;Context application=context.getApplicationContext();
        // Shizuku's SDK callback collections are also read on the main thread.
        // Keep unbind/bind ordered there, including stop followed by an immediate restart.
        main.post(()->{
            synchronized(BridgeConnection.class){
                if(!recovery.current(attempt.id()))return;
                removeService(previous);
                if(attempt.wakeManager()){
                    android.util.Log.i("FolduoBridge","Helper connection timed out; requesting manager receiver");
                    Intent wake=new Intent("rikka.shizuku.intent.action.REQUEST_BINDER")
                        .setComponent(new ComponentName("moe.shizuku.privileged.api","moe.shizuku.manager.receiver.ShizukuReceiver"));
                    try{
                        // Explicitly starting the exported receiver recreates the manager's
                        // provider before its helper must return a binder. No Activity or
                        // INCLUDE_STOPPED_PACKAGES: a deliberately stopped manager stays stopped.
                        application.sendOrderedBroadcast(wake,null,new BroadcastReceiver(){
                            @Override public void onReceive(Context ignored,Intent intent){
                                android.util.Log.i("FolduoBridge","Manager wake request finished; retrying helper");
                                bind(attempt,next);
                            }
                        },main,0,null,null);
                        return;
                    }catch(RuntimeException ignored){/* Retry binding even if this manager lacks the receiver. */}
                }
                bind(attempt,next);
            }
        });
    }

    private static synchronized void bind(BridgeRecovery.Attempt attempt,ServiceConnection next){
        if(!recovery.current(attempt.id()))return;
        try{Shizuku.bindUserService(args,next);}
        catch(Exception e){if(recovery.failed(attempt.id()))status=UiText.error(e);}
    }

    /** Remove this app's stale starting record, then the SDK's cached callback list. */
    private static void removeService(ServiceConnection previous){
        if(args==null||previous==null)return;
        try{Shizuku.unbindUserService(args,previous,true);}catch(Exception ignored){}
        try{Shizuku.unbindUserService(args,previous,false);}catch(Exception ignored){}
    }

    static synchronized void disconnect(){
        recovery.stop();bridge=null;status=UiText.of(R.string.bridge_waiting);
        ServiceConnection previous=connection;connection=null;
        // Invalidate callbacks synchronously; queued cleanup precedes any later bind.
        main.post(()->{synchronized(BridgeConnection.class){removeService(previous);}});
    }
}
