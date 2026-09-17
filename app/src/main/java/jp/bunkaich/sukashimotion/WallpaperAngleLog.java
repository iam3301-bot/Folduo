package jp.bunkaich.sukashimotion;

import java.util.regex.Pattern;

/** Samsung uses bracket fields on One UI 8 and equals fields on One UI 9. */
final class WallpaperAngleLog {
    record Reading(float angle,long wallTime) {}
    private final Pattern line;
    WallpaperAngleLog(String applicationId) {
        String command=Pattern.quote(applicationId+".READ_ANGLE");
        String old="action\\["+command+"\\], mCurrentAngle\\[([0-9.]+)\\], isVisible\\[true\\]";
        String current="action="+command+", mCurrentAngle=([0-9.]+), isVisible=true";
        line=Pattern.compile("^\\s*([0-9.]+)\\s+\\d+\\s+\\d+\\s+[VDIWE]\\s+SprWallpaper\\|FoldInteractive:\\s+onCommand: (?:"+old+"|"+current+")\\s*$");
    }
    Reading parse(String text,long started,long now) {
        var match=line.matcher(text);if(!match.matches())return null;
        try {
            long wall=(long)(Double.parseDouble(match.group(1))*1000);
            float angle=Float.parseFloat(match.group(2)!=null?match.group(2):match.group(3));
            long age=now-wall;
            if(wall<started||age< -50||age>600||!Float.isFinite(angle)||angle<0||angle>180)return null;
            return new Reading(angle,wall);
        } catch(NumberFormatException invalid) {return null;}
    }
}
