package jp.bunkaich.sukashimotion;

import java.util.regex.*;

/** Returns geometry and draw state only; window titles, app names and content are discarded. */
final class WindowReadiness {
    record State(boolean ready,String geometry){}
    static State parse(String dump,int displayId){
        return parse(dump,displayId,-1,false);
    }
    static State parse(String dump,int displayId,int taskId,boolean requireWallpaper){
        boolean wallpaperReady=!requireWallpaper;
        if(requireWallpaper)for(String window:dump.split("(?m)^  Window #")){
            if(onDisplay(window,displayId)&&window.contains("ty=WALLPAPER")&&drawn(window))wallpaperReady=true;
        }
        for(String window:dump.split("(?m)^  Window #")){
            if(!onDisplay(window,displayId)||!window.contains("ty=BASE_APPLICATION"))continue;
            if(taskId>=0&&!Pattern.compile("\\btaskId="+taskId+"(?:\\s|$)").matcher(window).find())continue;
            if(!window.contains("mViewVisibility=0x0")||!window.contains("isOnScreen=true"))continue;
            Matcher frame=Pattern.compile("Frames:.*?frame=(\\[[^\\n]+?) last=").matcher(window);
            String geometry=frame.find()?frame.group(1):"";
            boolean ready=!geometry.isEmpty()&&drawn(window)&&window.contains("insetsChanged=false")&&wallpaperReady;
            return new State(ready,geometry);
        }
        return new State(false,"");
    }
    private static boolean onDisplay(String window,int displayId){return Pattern.compile("mDisplayId="+displayId+"(?:\\s|$)").matcher(window).find();}
    private static boolean drawn(String window){
        return window.contains("mViewVisibility=0x0")&&window.contains("isOnScreen=true")
            &&window.contains("mHasSurface=true")&&window.contains("isReadyForDisplay()=true")
            &&window.contains("shown=true")&&window.contains("mDrawState=HAS_DRAWN")
            &&!window.contains("mAnimatingExit=true")&&!window.contains("mAppFreezing=true");
    }
}
