import android.app.*;
import android.app.wallpaper.WallpaperDescription;
import android.content.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.Properties;

/** Explicit Fold8 HOME setup, preserving Samsung snapshots for both panels. */
public final class Fold8WallpaperSetup {
 private static final File STATE=new File("/data/local/tmp/folduo-fold8-wallpaper-state");
 private static final String RESOURCE="com.samsung.android.wallpaper.res";
 private static final ComponentName LIVE=new ComponentName("com.samsung.android.wallpaper.live","com.samsung.android.wallpaper.live.fold.FoldInteractive");
 private static final int[] HOMES={5,17},ALL={5,6,17,18};
 private static Context context;private static WallpaperManager wm;
 private static Object call(String name,Class<?>[] types,Object... args)throws Exception{return WallpaperManager.class.getMethod(name,types).invoke(wm,args);}
 private static int id(int which)throws Exception{return (int)call("getWallpaperId",new Class[]{int.class},which);}
 private static String component(int which)throws Exception{
  WallpaperInfo info=(WallpaperInfo)call("getWallpaperInfo",new Class[]{int.class,int.class},which,0);
  return info==null?"static":info.getComponent().flattenToString();
 }
 private static String effectiveComponent(int which)throws Exception{
  int home=(which&~2)|1;
  // One UI can retain a lock ID after restoring a shared live wallpaper.
  // Its lock WallpaperInfo is null because the HOME engine serves both surfaces.
  return (which&2)!=0&&(id(which)<0||paired(home))?component(home):component(which);
 }
 private static boolean paired(int home)throws Exception{return (boolean)call("isSystemAndLockPaired",new Class[]{int.class},home);}
 private static void verifyRestored(Properties state,int home)throws Exception{
  for(int w:new int[]{home,home+1}){
   if(!state.getProperty("originalComponent."+w).equals(effectiveComponent(w)))throw new IllegalStateException("恢复组件不匹配，保留记录。屏幕="+w);
   int original=Integer.parseInt(state.getProperty("originalId."+w));
   // A previously shared lock may be materialized with the original HOME ID.
   if(id(w)!=original&&!(w==home+1&&original<0&&paired(home)&&id(w)==Integer.parseInt(state.getProperty("originalId."+home))))throw new IllegalStateException("恢复壁纸编号不匹配，保留记录。屏幕="+w);
  }
  String originalPaired=state.getProperty("originalPaired."+home);
  if(originalPaired!=null&&Boolean.parseBoolean(originalPaired)!=paired(home))throw new IllegalStateException("桌面与锁屏的关联状态不匹配，保留记录。");
 }
 private static void save(Properties p)throws Exception{
  File temp=new File(STATE+".new");temp.createNewFile();temp.setReadable(false,false);temp.setWritable(false,false);temp.setReadable(true,true);temp.setWritable(true,true);
  try(var out=new FileOutputStream(temp)){p.store(out,"Folduo SM-F9760 original wallpaper snapshots");out.getFD().sync();}
  if(!temp.renameTo(STATE))throw new IOException("无法保存恢复记录，保留现有文件。");
 }
 private static boolean validSnapshot(int key)throws Exception{return key>0&&(boolean)call("isValidSnapshot",new Class[]{int.class},key);}
 private static int snapshot(Properties state,int home){return Integer.parseInt(state.getProperty("snapshot."+home,"-1"));}
 private static void requirePrepared(Properties state)throws Exception{
  if(!"true".equals(state.getProperty("prepared")))throw new IllegalStateException("请先运行 prepare，完成内外屏备份。");
  for(int home:HOMES)if(!validSnapshot(snapshot(state,home)))throw new IllegalStateException("系统备份不可用，未修改壁纸。屏幕="+home);
 }
 private static void requireMonitorStopped()throws Exception{
  java.lang.Process process=new ProcessBuilder("dumpsys","activity","services","io.github.iam3301.folduo").start();
  try{
   if(!process.waitFor(3,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("无法确认动画已停止，未修改壁纸。");
   String dump=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
   if(process.exitValue()!=0||dump.contains("jp.bunkaich.sukashimotion.MotionService"))throw new IllegalStateException("请先在折叠流光中点击“停止并释放屏幕控制”，再设置或恢复壁纸。");
  }finally{process.destroy();}
 }
 private static Bundle stockSettings()throws Exception{
  Context stock=context.createPackageContext(RESOURCE,0);
  int res=stock.getResources().getIdentifier("resources_info","raw",RESOURCE);
  if(res==0)throw new IllegalStateException("找不到本机壁纸清单。");
  JSONObject catalog;try(var in=stock.getResources().openRawResource(res)){catalog=new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
  JSONObject chosen=null;JSONArray items=catalog.getJSONArray("phone");
  for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);JSONObject type=item.optJSONObject("type_params");
   if(item.optInt("type")==10&&item.optBoolean("isDefault")&&type!=null&&LIVE.getPackageName().equals(type.optString("service_package_name"))&&LIVE.getClassName().equals(type.optString("service_class_name")))chosen=item;
  }
  if(chosen==null)throw new IllegalStateException("未找到本机默认折叠交互壁纸。");
  JSONObject settings=chosen.getJSONObject("type_params").getJSONObject("service_settings");
  if(settings.optString("filename").isBlank()||settings.optString("angle_frame_mapping").isBlank())throw new IllegalStateException("本机交互资源参数不完整。");
  Bundle service=new Bundle();for(var keys=settings.keys();keys.hasNext();){String k=keys.next();Object value=settings.get(k);if(value instanceof Number n)service.putInt(k,n.intValue());else service.putString(k,String.valueOf(value));}
  Bundle extras=new Bundle();extras.putBundle("serviceSettings",service);extras.putBoolean("isPreloaded",true);return extras;
 }
 private static void setHome(int which,Bundle extras)throws Exception{
  IBinder binder=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"wallpaper");
  Object remote=Class.forName("android.app.IWallpaperManager$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
  Method setter=null;for(Method m:Class.forName("android.app.IWallpaperManager").getMethods())if(m.getName().equals("setWallpaperComponentChecked")&&m.getParameterCount()==5)setter=m;
  if(setter==null)throw new IllegalStateException("系统壁纸接口不匹配。");
  WallpaperDescription.Builder builder=new WallpaperDescription.Builder();builder.getClass().getMethod("setComponent",ComponentName.class).invoke(builder,LIVE);
  setter.invoke(remote,builder.build(),context.getPackageName(),which,0,extras);
 }
 public static void main(String[] args){try{run(args);System.exit(0);}catch(Throwable e){while(e.getCause()!=null)e=e.getCause();System.err.println(e.getClass().getSimpleName()+": "+e.getMessage());System.exit(1);}}
 private static void run(String[] args)throws Exception{
  if(android.os.Process.myUid()!=2000||!"SM-F9760".equals(Build.MODEL))throw new IllegalStateException("仅支持通过 ADB 在 SM-F9760 上运行。");
  Looper.prepareMainLooper();Class<?> at=Class.forName("android.app.ActivityThread");Object thread=at.getMethod("systemMain").invoke(null);
  context=((Context)at.getMethod("getSystemContext").invoke(thread)).createPackageContext("com.android.shell",0);wm=WallpaperManager.getInstance(context);
  String action=args.length==0?"status":args[0];Properties state=new Properties();if(STATE.exists())try(var in=new FileInputStream(STATE)){state.load(in);}
  if(state.containsKey("snapshot"))throw new IllegalStateException("检测到旧版单屏恢复记录，请先保留并检查该记录。");
  Bundle extras=stockSettings();
  if(action.equals("prepare")){
   requireMonitorStopped();
   if("true".equals(state.getProperty("restored.5"))&&"true".equals(state.getProperty("restored.17"))){
    for(int w:ALL)if(id(w)!=Integer.parseInt(state.getProperty("restoredId."+w)))throw new IllegalStateException("恢复后壁纸再次变化，请保留旧记录并人工检查。");
    File history=new File(STATE+".restored."+state.getProperty("created"));
    if(history.exists()||!STATE.renameTo(history))throw new IOException("无法保留上次恢复记录，未开始新设置。");
    state=new Properties();
   }
   if("true".equals(state.getProperty("prepared"))){requirePrepared(state);System.out.println("已有有效备份，无需重复创建。");return;}
   if(state.isEmpty()){
    state.setProperty("model",Build.MODEL);state.setProperty("created",String.valueOf(System.currentTimeMillis()));
    for(int w:ALL){state.setProperty("originalId."+w,String.valueOf(id(w)));state.setProperty("originalComponent."+w,effectiveComponent(w));}
    for(int home:HOMES)state.setProperty("originalPaired."+home,String.valueOf(paired(home)));
    save(state);
   }
   for(int w:ALL)if(id(w)!=Integer.parseInt(state.getProperty("originalId."+w)))throw new IllegalStateException("壁纸已变化，未继续备份。屏幕="+w);
   for(int home:HOMES){
    if(state.containsKey("snapshot."+home)){if(!validSnapshot(snapshot(state,home)))throw new IllegalStateException("已有备份无效，保留记录。");continue;}
    int key=(int)call("semMakeBackupWallpaper",new Class[]{int.class},home|2);
    if(!validSnapshot(key))throw new IllegalStateException("系统备份失败，未修改壁纸。屏幕="+home);
    state.setProperty("snapshot."+home,String.valueOf(key));save(state);
    call("setSnapshotSource",new Class[]{int.class,String.class},key,"folduo_sm_f9760_original_"+home);
   }
   state.setProperty("prepared","true");save(state);System.out.println("内外屏桌面及锁屏均已创建系统备份。");
  }else if(action.equals("apply")){
   requireMonitorStopped();
   requirePrepared(state);
   for(int w:ALL){String expected=state.getProperty("appliedId."+w,state.getProperty("originalId."+w));if(id(w)!=Integer.parseInt(expected))throw new IllegalStateException("壁纸已发生其他变化，未覆盖。屏幕="+w);}
   for(int home:HOMES){
    if(state.containsKey("appliedId."+home)){if(!LIVE.flattenToString().equals(component(home)))throw new IllegalStateException("交互壁纸组件已变化。");continue;}
    setHome(home,extras);SystemClock.sleep(1000);
    state.setProperty("appliedId."+home,String.valueOf(id(home)));state.setProperty("appliedId."+(home+1),String.valueOf(id(home+1)));save(state);
    if(!LIVE.flattenToString().equals(component(home)))throw new IllegalStateException("系统未确认交互壁纸，保留备份。");
    if(!state.getProperty("originalComponent."+(home+1)).equals(effectiveComponent(home+1)))throw new IllegalStateException("锁屏组件变化，请先恢复备份再检查。");
    System.out.println((home==5?"内屏":"外屏")+"桌面已设置为本机交互壁纸，锁屏组件保持原样。");
   }
  }else if(action.equals("restore")){
   requireMonitorStopped();
   if(!"true".equals(state.getProperty("prepared")))throw new IllegalStateException("未找到完整备份记录。");
   if(!state.containsKey("appliedId.5")&&!state.containsKey("appliedId.17")){System.out.println("尚未应用交互壁纸，无需恢复。");return;}
   for(int home:HOMES)if(state.containsKey("appliedId."+home)){
    boolean restored="true".equals(state.getProperty("restored."+home));
    if("true".equals(state.getProperty("restoreApplied."+home))&&!restored){verifyRestored(state,home);continue;}
    if(!restored&&!validSnapshot(snapshot(state,home)))throw new IllegalStateException("系统备份不可用，未恢复。屏幕="+home);
    for(int w:new int[]{home,home+1})if(id(w)!=Integer.parseInt(state.getProperty((restored?"restoredId.":"appliedId.")+w)))throw new IllegalStateException("壁纸已再次变化，请保留现状并人工选择恢复。屏幕="+w);
   }
   for(int home:HOMES)if(state.containsKey("appliedId."+home)){
    if("true".equals(state.getProperty("restored."+home)))continue;
    if(!"true".equals(state.getProperty("restoreApplied."+home))){
     if(!(boolean)call("semRestoreBackupWallpaper",new Class[]{int.class},snapshot(state,home)))throw new IllegalStateException("系统未确认恢复，保留备份。");
     // Samsung consumes the snapshot on success. Persist that before verification,
     // so a verification retry never tries to restore a consumed snapshot again.
     state.setProperty("restoreApplied."+home,"true");save(state);SystemClock.sleep(700);
    }
    verifyRestored(state,home);
    for(int w:new int[]{home,home+1})state.setProperty("restoredId."+w,String.valueOf(id(w)));
    state.setProperty("restored."+home,"true");save(state);
   }
   System.out.println("已恢复本次设置前的壁纸；恢复记录保留。");
  }else if(!action.equals("status"))throw new IllegalArgumentException("操作应为 status、prepare、apply 或 restore。");
  for(int w:ALL)System.out.println("which="+w+" id="+id(w)+" component="+effectiveComponent(w));
  for(int home:HOMES)System.out.println("snapshot."+home+"="+snapshot(state,home)+" valid="+validSnapshot(snapshot(state,home)));
  System.out.println("交互资源="+extras.getBundle("serviceSettings").getString("filename"));
 }
}
