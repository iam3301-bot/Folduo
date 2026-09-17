package jp.bunkaich.sukashimotion;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private final Handler handler=new Handler();private TextView diagnostic;private boolean probing;
    private final Shizuku.OnRequestPermissionResultListener permission=(code,result)->{if(result==0)BridgeConnection.connect(this);};
    private final IAngleSink diagnosticSink=new IAngleSink.Stub(){public void angle(float a,long t,int kind){}};
    private ControlPanel ui;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);BridgeConnection.init(this);Shizuku.addRequestPermissionResultListener(permission);
        ui=new ControlPanel(this,saved==null?0:saved.getInt("tab",0));handler.post(refresh);
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration config){
        super.onConfigurationChanged(config);ui=new ControlPanel(this,ui.selected());
    }
    @Override public void onSaveInstanceState(Bundle saved){super.onSaveInstanceState(saved);saved.putInt("tab",ui.selected());}
    void openGuide(){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/iam3301-bot/Folduo/blob/main/docs/安装指南.md")));}
    void openShizuku(){Intent launch=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");if(launch!=null)startActivity(launch);else startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/zh-hans/guide/setup/")));}
    void connectShizuku(){
        if(!Shizuku.pingBinder()){
            GlassStyle.dialog(new AlertDialog.Builder(this).setMessage(R.string.shizuku_not_running).setPositiveButton(R.string.official_guide,(d,w)->openShizuku()).setNegativeButton(R.string.close,null).create());return;
        }
        if(BridgeConnection.permitted())BridgeConnection.connect(this);else Shizuku.requestPermission(7);
    }
    void allowOverlay(){startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
    void batterySettings(){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
    void defaultHome(){
        android.app.role.RoleManager roles=getSystemService(android.app.role.RoleManager.class);
        if(roles.isRoleHeld(android.app.role.RoleManager.ROLE_HOME))openHome();
        else startActivityForResult(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME),9);
    }
    void stopMotion(){MotionSettings.setEnabled(this,false);stopService(new Intent(this,MotionService.class));if(!MotionService.running)BridgeConnection.disconnect();refreshState();}
    void startMotion(){
        if(!Settings.canDrawOverlays(this)){Toast.makeText(this,getString(R.string.need_overlay),Toast.LENGTH_LONG).show();return;}
        if(!BridgeConnection.permitted()){Toast.makeText(this,getString(R.string.need_shizuku),Toast.LENGTH_LONG).show();return;}
        if(!DeviceSupport.eligible(Build.MODEL)){Toast.makeText(this,getString(R.string.unsupported_device),Toast.LENGTH_LONG).show();return;}
        if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},8);
        BridgeConnection.connect(this);MotionSettings.setEnabled(this,true);startForegroundService(new Intent(this,MotionService.class).setAction(MotionService.running?"restart":"start"));
        Toast.makeText(this,getString(R.string.close_to_prepare),Toast.LENGTH_LONG).show();finish();
    }
    void probe(int attempt){
        diagnostic=ui.diagnostic; if(diagnostic==null||probing)return;
        BridgeConnection.connect(this);IShellBridge bridge=BridgeConnection.bridge;
        if(bridge==null){diagnostic.setText(BridgeConnection.status.resolve(this));if(attempt<30&&BridgeConnection.permitted())handler.postDelayed(()->probe(attempt+1),300);return;}
        probing=true;diagnostic.setText(getString(R.string.probe_running));
        boolean alreadyRunning=MotionService.running;
        BridgeConnection.work.execute(()->{try{
            if(!alreadyRunning)bridge.startAngles(diagnosticSink);
            handler.postDelayed(()->BridgeConnection.work.execute(()->{try{
                Bundle report=bridge.inspect();
                if(!alreadyRunning&&!MotionService.running)bridge.stopAngles();
                String formatted=formatReport(this,report);handler.post(()->{probing=false;diagnostic.setText(formatted);});
            }catch(Exception e){handler.post(()->{probing=false;diagnostic.setText(UiText.error(e).resolve(this));});}}),5000);
        }catch(Exception e){handler.post(()->{probing=false;diagnostic.setText(UiText.error(e).resolve(this));});}});
    }
    static String formatReport(Context c,Bundle b){
        StringBuilder text=new StringBuilder(c.getString(R.string.probe_permissions,b.getInt("uid"),c.getString(b.getBoolean("samsungPermission")?R.string.yes:R.string.no)));
        ArrayList<Bundle> rows=b.getParcelableArrayList("sensors",Bundle.class);boolean gyro=false,sub=false;
        if(rows!=null)for(Bundle r:rows){
            long count=r.getLong("events");int type=r.getInt("type");
            if(type==4&&count>0)gyro=true;if((type==65689||type==65690)&&count>0)sub=true;
            text.append('\n').append(c.getString(R.string.probe_row,c.getString(R.string.diagnostic_sensor,type),type,c.getString(r.getBoolean("registered")?R.string.success:R.string.unavailable),count));
            if(type==36||type==65686)text.append(c.getString(R.string.resolution,java.text.NumberFormat.getNumberInstance(c.getResources().getConfiguration().getLocales().get(0)).format(r.getFloat("resolution"))));
            if(r.containsKey("error"))text.append('\n').append(UiText.raw(r.getString("error")).resolve(c));text.append('\n');
        }
        text.append('\n').append(c.getString(R.string.gyro_result,c.getString(gyro&&sub?R.string.both_gyros:R.string.missing_gyro)));
        String display=b.getString("display", "");
        var states=java.util.regex.Pattern.compile("inner=(\\d+) / cover=(\\d+) / current=(\\d+) / base=(\\d+)").matcher(display);
        String displayText=states.matches()?c.getString(R.string.diagnostic_display_states,Integer.parseInt(states.group(1)),Integer.parseInt(states.group(2)),Integer.parseInt(states.group(3)),Integer.parseInt(states.group(4))):UiText.raw(display).resolve(c);
        text.append("\n\n").append(c.getString(R.string.display_result,displayText));
        if(!b.getString("error","").isEmpty())text.append('\n').append(UiText.raw(b.getString("error")).resolve(c));return text.toString();
    }
    void openHome(){
        if(getDisplay().getDisplayId()==1&&MotionSettings.enabled(this)&&getSystemService(android.app.role.RoleManager.class).isRoleHeld(android.app.role.RoleManager.ROLE_HOME)){
            IShellBridge bridge=BridgeConnection.bridge;
            BridgeConnection.work.execute(()->{
                try{
                    if(bridge==null)throw new IllegalStateException(getString(R.string.bridge_missing));
                    Bundle result=bridge.navigate(1,KeyEvent.KEYCODE_HOME,-1);
                    if(!result.getBoolean("ok"))throw new IllegalStateException(result.getString("error"));
                }catch(Exception error){handler.post(()->{if(!isDestroyed())Toast.makeText(this,UiText.error(error).resolve(this),Toast.LENGTH_LONG).show();});}
            });
        }else startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(new ComponentName(this,HomeActivity.class)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    String languageName(){
        LocaleList locales=getSystemService(LocaleManager.class).getApplicationLocales();
        if(locales.isEmpty())return getString(R.string.language_system);
        return getString(switch(locales.get(0).getLanguage()){case "zh" -> R.string.language_chinese;case "ja" -> R.string.language_japanese;default -> R.string.language_english;});
    }
    void chooseLanguage(){
        LocaleManager manager=getSystemService(LocaleManager.class);LocaleList locales=manager.getApplicationLocales();
        int selected=locales.isEmpty()?0:switch(locales.get(0).getLanguage()){case "zh" -> 3;case "ja" -> 2;default -> 1;};
        String[] names={getString(R.string.language_system),getString(R.string.language_english),getString(R.string.language_japanese),getString(R.string.language_chinese)};
        GlassStyle.dialog(new AlertDialog.Builder(this).setTitle(R.string.language_title).setSingleChoiceItems(names,selected,(dialog,index)->{
            dialog.dismiss();String tags=new String[]{"","en","ja","zh-Hans"}[index];
            if(!manager.getApplicationLocales().toLanguageTags().equals(tags))manager.setApplicationLocales(LocaleList.forLanguageTags(tags));
        }).setNegativeButton(R.string.close,null).create());
    }
    void refreshState(){
        if(ui==null||ui.state==null)return;
        String recovery=MotionSettings.recovery(MainActivity.this);
        String status=MotionService.running?MotionService.status.resolve(MainActivity.this):getString(R.string.state_stopped,BridgeConnection.status.resolve(MainActivity.this));
        ui.state.setText(getString(R.string.state_details,status,getString(MotionSettings.enabled(MainActivity.this)?R.string.on:R.string.off),getString(Settings.canDrawOverlays(MainActivity.this)?R.string.allowed:R.string.not_allowed))+(recovery.isEmpty()?"":"\n"+getString(R.string.last_recovery,recovery)));
    }
    private final Runnable refresh=new Runnable(){public void run(){refreshState();handler.postDelayed(this,600);}};
    @Override protected void onResume(){super.onResume();if(MotionSettings.enabled(this)&&!MotionService.running&&Settings.canDrawOverlays(this))startForegroundService(new Intent(this,MotionService.class).setAction("restore"));}
    @Override protected void onDestroy(){handler.removeCallbacks(refresh);Shizuku.removeRequestPermissionResultListener(permission);super.onDestroy();}
}
