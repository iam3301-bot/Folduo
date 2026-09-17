package jp.bunkaich.sukashimotion;
import org.junit.Test;
import static org.junit.Assert.*;
public class WallpaperAngleLogTest {
 private final WallpaperAngleLog log=new WallpaperAngleLog("io.github.iam3301.folduo");
 private String line(String body){return "1700000000.250 1234 5678 I SprWallpaper|FoldInteractive: onCommand: "+body;}
 private WallpaperAngleLog.Reading read(String body){return log.parse(line(body),1700000000000L,1700000000300L);}
 @Test public void acceptsOneUi8BracketFormat(){assertEquals(76.25f,read("action[io.github.iam3301.folduo.READ_ANGLE], mCurrentAngle[76.25], isVisible[true]").angle(),.001f);}
 @Test public void acceptsOneUi9EqualsFormat(){assertEquals(112.5f,read("action=io.github.iam3301.folduo.READ_ANGLE, mCurrentAngle=112.5, isVisible=true").angle(),.001f);}
 @Test public void rejectsOtherCommandsInvisibleWallpapersAndInvalidAngles(){
  assertNull(read("action=other.app.READ_ANGLE, mCurrentAngle=110, isVisible=true"));
  assertNull(read("action=io.github.iam3301.folduo.READ_ANGLE, mCurrentAngle=110, isVisible=false"));
  assertNull(read("action=io.github.iam3301.folduo.READ_ANGLE, mCurrentAngle=181, isVisible=true"));
  assertNull(read("action=io.github.iam3301.folduo.READ_ANGLE, mCurrentAngle=.., isVisible=true"));
 }
 @Test public void rejectsOldSessionsStaleAndFutureReadings(){
  String s=line("action=io.github.iam3301.folduo.READ_ANGLE, mCurrentAngle=110, isVisible=true");
  assertNull(log.parse(s,1700000000260L,1700000000300L));
  assertNull(log.parse(s,1700000000000L,1700000000900L));
  assertNull(log.parse(s,1700000000000L,1700000000100L));
 }
}
