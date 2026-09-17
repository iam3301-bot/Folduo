package jp.bunkaich.sukashimotion;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

final class AppCatalog {
    record App(String label, ComponentName component, Drawable icon) {}
    static List<App> load(Context context) {
        List<App> result=new ArrayList<>(); HashSet<String> seen=new HashSet<>();
        Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for(ResolveInfo r:context.getPackageManager().queryIntentActivities(query,0)) {
            if(r.activityInfo==null || r.activityInfo.packageName.equals(context.getPackageName()))continue;
            ComponentName component=new ComponentName(r.activityInfo.packageName,r.activityInfo.name);
            if(seen.add(component.flattenToString()))result.add(new App(r.loadLabel(context.getPackageManager()).toString(),component,r.loadIcon(context.getPackageManager())));
        }
        Collator collator=Collator.getInstance(context.getResources().getConfiguration().getLocales().get(0));
        result.sort((a,b)->collator.compare(a.label,b.label)); return result;
    }
    static void pin(SharedPreferences prefs,ComponentName component,int slot){
        if(slot<0||slot>=16)throw new IllegalArgumentException("Invalid favorite slot");
        if(HomeFolders.read(prefs).containsKey(slot)){HomeFolders.add(prefs,slot,component.flattenToString());return;}
        HomeFolders.removeApp(prefs,component.flattenToString());
        String chosen=component.flattenToString(),displaced=prefs.getString("slot_"+slot,"");
        SharedPreferences.Editor edit=prefs.edit();boolean swapped=false;
        for(int other=0;other<16;other++)if(other!=slot&&chosen.equals(prefs.getString("slot_"+other,null))){
            edit.putString("slot_"+other,!swapped&&!chosen.equals(displaced)?displaced:"");swapped=true;
        }
        edit.putString("slot_"+slot,chosen).apply();
    }
    static List<App> favorites(List<App> all, SharedPreferences prefs) {
        List<App> picks=new ArrayList<>();
        // Stable slot ids, including gaps: uninstalling an app must not reorder the home screen.
        String[] preferred={"camera","chrome","gallery","calendar","messaging","gmail","maps","youtube","clock","settings","notes","calculator","photos","music","files","kotobamado"};
        HashSet<String> used=new HashSet<>();
        for(HomeFolders.Folder folder:HomeFolders.read(prefs).values())used.addAll(folder.components());
        for(int slot=0;slot<16;slot++) {
            String saved=prefs.getString("slot_"+slot,null); App found=null;
            if(saved!=null) { for(App app:all)if(app.component.flattenToString().equals(saved)){found=app;break;} }
            else {
                for(App app:all)if(!used.contains(app.component.flattenToString())&&app.component.getPackageName().toLowerCase(Locale.ROOT).contains(preferred[slot])){found=app;break;}
                if(found==null)for(App app:all)if(!used.contains(app.component.flattenToString())){found=app;break;}
                if(found!=null)prefs.edit().putString("slot_"+slot,found.component.flattenToString()).apply();
            }
            picks.add(found);if(found!=null)used.add(found.component.flattenToString());
        }
        return picks;
    }
}
