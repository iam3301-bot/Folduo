package jp.bunkaich.sukashimotion;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.View;

/** Launcher-only palettes; the wallpaper angle source is unchanged. */
final class HomeTheme {
    private HomeTheme() {}
    static int palette(Context context){return Math.max(0,Math.min(2,context.getSharedPreferences("launcher",0).getInt("home_palette",0)));}
    static int ink(Context context){return palette(context)==1?0xfff4f7fc:GlassStyle.INK;}
    static int secondary(Context context){return palette(context)==1?0xffbbc9da:GlassStyle.SECONDARY;}
    static void panel(View view,int radius){view.setBackground(new GlassStyle.Material(view,radius,false,palette(view.getContext())));}
    static void backdrop(View view){view.setBackground(new GlassStyle.Material(view,0,true,palette(view.getContext())));}
    static void choose(Activity activity,Runnable changed){
        CharSequence[] labels={activity.getString(R.string.home_theme_pearl),activity.getString(R.string.home_theme_graphite),activity.getString(R.string.home_theme_sky)};
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(R.string.home_theme_title)
            .setSingleChoiceItems(labels,palette(activity),(choice,index)->{
                activity.getSharedPreferences("launcher",0).edit().putInt("home_palette",index).apply();choice.dismiss();changed.run();
            }).setNegativeButton(R.string.close,null).create();
        GlassStyle.dialog(dialog);
    }
}
