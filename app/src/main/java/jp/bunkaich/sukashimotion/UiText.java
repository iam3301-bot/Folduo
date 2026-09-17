package jp.bunkaich.sukashimotion;

import android.content.Context;
import android.content.res.Resources;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.regex.Pattern;

/** Resolve at the UI boundary so cached statuses also follow the current app language. */
final class UiText {
    static Context localizedWindow(Context window) {
        var locales=window.getSystemService(android.app.LocaleManager.class).getApplicationLocales();
        if(locales.isEmpty())return window;
        var config=new android.content.res.Configuration(window.getResources().getConfiguration());
        config.setLocales(locales);
        return window.createConfigurationContext(config);
    }
    private static final Pattern TOKEN=Pattern.compile("@folduo/([a-z_]+)");
    private final int resource;
    private final String raw;
    private final Object[] args;
    private UiText(int resource,String raw,Object[] args){this.resource=resource;this.raw=raw;this.args=args.clone();}
    static UiText of(int resource,Object... args){return new UiText(resource,"",args);}
    static UiText raw(String text){return new UiText(0,text==null?"":text,new Object[0]);}
    static UiText error(Throwable error){return raw(ShellBridge.message(error));}
    boolean is(int id){return resource==id;}
    String resolve(Context context){
        if(resource==0){
            if ("zh".equals(context.getResources().getConfiguration().getLocales().get(0).getLanguage())) {
                var token = TOKEN.matcher(raw);
                if (token.find()) {
                    int id=shellMessage(token.group(1));
                    return context.getString(id==0?R.string.unknown_error:id);
                }
                // Keep technical exception messages out of the Chinese user-facing surfaces.
                // The original raw payload remains available to diagnostic logs and storage.
                if (!raw.isBlank() && !raw.matches("(?s).*[\\p{IsHan}].*") && raw.matches("(?s).*[A-Za-z].*"))
                    return context.getString(R.string.diagnostic_system_error);
            }
            var matcher=TOKEN.matcher(raw);StringBuffer result=new StringBuffer();
            while(matcher.find()){
                int id=shellMessage(matcher.group(1));
                String text=context.getString(id==0?R.string.unknown_error:id);
                matcher.appendReplacement(result,java.util.regex.Matcher.quoteReplacement(text));
            }
            matcher.appendTail(result);return result.toString();
        }
        Object[] rendered=new Object[args.length];
        for(int i=0;i<args.length;i++)rendered[i]=args[i] instanceof UiText text?text.resolve(context):args[i];
        return context.getString(resource,rendered);
    }
    // Only known, argument-free helper messages may cross the shell/UI boundary.
    private static int shellMessage(String key){return switch(key){
        case "capture_failed" -> R.string.capture_failed;
        case "destination_capture_failed" -> R.string.destination_capture_failed;
        case "destination_resizing" -> R.string.destination_resizing;
        case "err_angle_stopped" -> R.string.err_angle_stopped;
        case "err_app_finished" -> R.string.err_app_finished;
        case "err_app_moved" -> R.string.err_app_moved;
        case "err_back_rejected" -> R.string.err_back_rejected;
        case "err_control_stopped" -> R.string.err_control_stopped;
        case "err_display_conflict" -> R.string.err_display_conflict;
        case "err_distinct_displays" -> R.string.err_distinct_displays;
        case "err_empty_frame" -> R.string.err_empty_frame;
        case "err_home_missing" -> R.string.err_home_missing;
        case "err_inner_unavailable" -> R.string.err_inner_unavailable;
        case "err_launch_target" -> R.string.home_launch_missing;
        case "err_launch_unconfirmed" -> R.string.home_launch_unconfirmed;
        case "err_monitor_inactive" -> R.string.err_monitor_inactive;
        case "err_no_frame" -> R.string.err_no_frame;
        case "err_protected_frame" -> R.string.err_protected_frame;
        case "err_settings_missing" -> R.string.err_settings_missing;
        case "err_source_home_missing" -> R.string.err_source_home_missing;
        case "err_states_unavailable" -> R.string.err_states_unavailable;
        case "err_system_screen" -> R.string.err_system_screen;
        case "err_unexpected_caller" -> R.string.err_unexpected_caller;
        case "err_unsupported_action" -> R.string.err_unsupported_action;
        case "err_wrong_model" -> R.string.err_wrong_model;
        default -> R.string.unknown_error;
    };}
    // Save names rather than integer resource IDs, which can change in a later APK.
    String encode(Context context){try{return json(context).toString();}catch(Exception e){return "";}}
    private JSONObject json(Context context)throws Exception{
        JSONObject value=new JSONObject();
        if(resource==0)return value.put("raw",raw);
        value.put("resource",context.getResources().getResourceEntryName(resource));JSONArray values=new JSONArray();
        for(Object arg:args)values.put(arg instanceof UiText text?text.json(context):arg);
        return value.put("args",values);
    }
    static UiText decode(Context context,String encoded){
        if(encoded.isEmpty())return raw("");
        try{return fromJson(context,new JSONObject(encoded),0);}catch(Exception e){return of(R.string.previous_recovery);}
    }
    private static UiText fromJson(Context context,JSONObject value,int depth)throws Exception{
        if(depth>8)throw new IllegalArgumentException("Nested message limit");
        if(value.has("raw"))return raw(value.getString("raw"));
        int id=context.getResources().getIdentifier(value.getString("resource"),"string",context.getPackageName());
        if(id==0)throw new Resources.NotFoundException();
        JSONArray values=value.getJSONArray("args");Object[] args=new Object[values.length()];
        for(int i=0;i<args.length;i++){Object arg=values.get(i);args[i]=arg instanceof JSONObject child?fromJson(context,child,depth+1):arg;}
        return of(id,args);
    }
}
